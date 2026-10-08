package uz.installment.client;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.client.ClientDtos.ClientDetails;
import uz.installment.client.ClientDtos.ClientListItem;
import uz.installment.client.ClientDtos.ClientsSummary;
import uz.installment.client.ClientDtos.ClientRequest;
import uz.installment.client.ClientDtos.ClientResponse;
import uz.installment.client.ClientDtos.ContractBrief;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.common.PageResponse;
import uz.installment.scoring.CreditLimit;
import uz.installment.scoring.ScoringEngine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository repo;
    private final NamedParameterJdbcTemplate jdbc;

    /** Mijoz bo'yicha nasiya ko'rsatkichlari (ro'yxat uchun, bitta so'rov bilan). */
    private record Stats(long openContracts, BigDecimal activeDebt, BigDecimal monthlyObligation,
                         boolean hasLate, Integer lastScore, String lastRisk) {
        static final Stats EMPTY = new Stats(0, BigDecimal.ZERO, BigDecimal.ZERO, false, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientListItem> search(String q, int page, int size) {
        String query = q == null ? "" : q.trim();
        var p = repo.search(query, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id")));
        List<Long> ids = p.getContent().stream().map(Client::getId).toList();
        Map<Long, Stats> stats = ids.isEmpty() ? Map.of() : statsFor(ids);
        return PageResponse.of(p, c -> {
            Stats s = stats.getOrDefault(c.getId(), Stats.EMPTY);
            ScoringEngine.RiskCategory risk = s.lastRisk() == null ? null : ScoringEngine.RiskCategory.valueOf(s.lastRisk());
            BigDecimal limit = c.isBlacklisted() ? BigDecimal.ZERO
                    : CreditLimit.of(c.getMonthlyIncome(), risk, s.monthlyObligation()).limit();
            return new ClientListItem(c.getId(), c.getFullName(), c.getPinfl(), c.getPhone(), c.getRegion(),
                    c.getDistrict(), c.getWorkplace(), c.getMonthlyIncome(), c.isBlacklisted(), c.getCreatedAt(),
                    s.openContracts(), s.activeDebt(), s.monthlyObligation(), s.hasLate(), s.lastScore(), s.lastRisk(),
                    limit);
        });
    }

    private Map<Long, Stats> statsFor(List<Long> clientIds) {
        return jdbc.query("""
                        select c.client_id,
                               count(*) filter (where c.status in ('ACTIVE','LATE'))                    as open_cnt,
                               coalesce(sum(d.remaining) filter (where c.status in ('ACTIVE','LATE')), 0) as debt,
                               coalesce(sum(c.monthly_payment) filter (where c.status in ('ACTIVE','LATE')), 0) as monthly,
                               bool_or(c.status = 'LATE')                                               as has_late,
                               (array_agg(c.score_total order by c.id desc))[1]                         as last_score,
                               (array_agg(c.risk_category order by c.id desc))[1]                       as last_risk
                        from contracts c
                        left join (select contract_id, sum(amount - paid_amount) as remaining
                                   from schedule_items group by contract_id) d on d.contract_id = c.id
                        where c.client_id in (:ids) and c.status <> 'CANCELLED'
                        group by c.client_id
                        """, Map.of("ids", clientIds),
                (rs, i) -> Map.entry(rs.getLong("client_id"), new Stats(rs.getLong("open_cnt"),
                        rs.getBigDecimal("debt"), rs.getBigDecimal("monthly"), rs.getBoolean("has_late"),
                        (Integer) rs.getObject("last_score"), rs.getString("last_risk"))))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Transactional(readOnly = true)
    public ClientsSummary summary() {
        return jdbc.queryForObject("""
                with last as (
                  select distinct on (client_id) client_id, score_total, risk_category
                  from contracts where status <> 'CANCELLED' order by client_id, id desc
                ), open as (
                  select c.client_id, bool_or(c.status = 'LATE') as late,
                         sum(s.amount - s.paid_amount) as debt
                  from contracts c join schedule_items s on s.contract_id = c.id
                  where c.status in ('ACTIVE','LATE') group by c.client_id
                )
                select (select count(*) from clients)                                     as total,
                       (select count(*) from open)                                        as with_debt,
                       (select count(*) from open where late)                             as late_clients,
                       (select round(avg(score_total), 1) from last)                      as avg_score,
                       (select round(100.0 * count(*) filter (where risk_category = 'A')
                                     / nullif(count(*), 0), 1) from last)                 as grade_a_pct,
                       (select coalesce(sum(debt), 0) from open)                          as total_debt
                """, Map.of(), (rs, i) -> new ClientsSummary(rs.getLong("total"), rs.getLong("with_debt"),
                rs.getLong("late_clients"), rs.getBigDecimal("avg_score"), rs.getBigDecimal("grade_a_pct"),
                rs.getBigDecimal("total_debt")));
    }

    @Transactional(readOnly = true)
    public Client get(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Mijoz", id));
    }

    @Transactional(readOnly = true)
    public ClientDetails details(Long id) {
        Client c = get(id);
        List<ContractBrief> contracts = jdbc.query("""
                        select c.id, c.contract_no, c.product_name, c.sale_price, c.status, c.risk_category,
                               c.down_payment,
                               coalesce(sum(s.paid_amount), 0)                as paid,
                               coalesce(sum(s.amount - s.paid_amount), 0)     as remaining,
                               min(s.due_date) filter (where s.status <> 'PAID') as next_due
                        from contracts c
                        left join schedule_items s on s.contract_id = c.id
                        where c.client_id = :id
                        group by c.id
                        order by c.id desc
                        """, Map.of("id", id),
                (rs, i) -> new ContractBrief(rs.getLong("id"), rs.getString("contract_no"),
                        rs.getString("product_name"), rs.getBigDecimal("sale_price"),
                        rs.getBigDecimal("paid").add(rs.getBigDecimal("down_payment")),
                        rs.getBigDecimal("remaining"), rs.getString("status"), rs.getString("risk_category"),
                        rs.getObject("next_due", java.time.LocalDate.class)));
        BigDecimal debt = contracts.stream()
                .filter(x -> "ACTIVE".equals(x.status()) || "LATE".equals(x.status()))
                .map(ContractBrief::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ClientDetails(ClientResponse.of(c), debt, contracts);
    }

    @Transactional
    public ClientResponse create(ClientRequest req) {
        if (repo.existsByPinfl(req.pinfl())) {
            throw new BusinessException("DUPLICATE_PINFL", "Bu JShShIR bilan mijoz allaqachon mavjud");
        }
        Client c = new Client();
        apply(c, req);
        return ClientResponse.of(repo.save(c));
    }

    @Transactional
    public ClientResponse update(Long id, ClientRequest req) {
        Client c = get(id);
        if (repo.existsByPinflAndIdNot(req.pinfl(), id)) {
            throw new BusinessException("DUPLICATE_PINFL", "Bu JShShIR bilan boshqa mijoz mavjud");
        }
        apply(c, req);
        return ClientResponse.of(c);
    }

    private static void apply(Client c, ClientRequest r) {
        c.setFullName(r.fullName().trim());
        c.setPinfl(r.pinfl());
        c.setPassportSeries(blankToNull(r.passportSeries()));
        c.setPassportExpiry(r.passportExpiry());
        c.setBirthDate(r.birthDate());
        c.setPhone(normalizePhone(r.phone()));
        c.setExtraPhone(blankToNull(r.extraPhone()) == null ? null : normalizePhone(r.extraPhone()));
        c.setRegion(blankToNull(r.region()));
        c.setDistrict(blankToNull(r.district()));
        c.setAddress(blankToNull(r.address()));
        c.setWorkplace(blankToNull(r.workplace()));
        c.setMonthlyIncome(r.monthlyIncome());
        c.setFamilyStatus(blankToNull(r.familyStatus()));
        c.setTelegramChatId(r.telegramChatId());
        c.setBlacklisted(r.blacklisted());
        c.setNote(blankToNull(r.note()));
    }

    /** Barcha telefonlar bir xil formatda saqlanadi: +998XXXXXXXXX */
    static String normalizePhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        return "+" + digits;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

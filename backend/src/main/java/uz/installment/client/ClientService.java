package uz.installment.client;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.client.ClientDtos.ClientDetails;
import uz.installment.client.ClientDtos.ClientRequest;
import uz.installment.client.ClientDtos.ClientResponse;
import uz.installment.client.ClientDtos.ContractBrief;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.common.PageResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository repo;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> search(String q, int page, int size) {
        String query = q == null ? "" : q.trim();
        var p = repo.search(query, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(p, ClientResponse::of);
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

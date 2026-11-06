package uz.installment.collection;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Muddati o'tgan qarzlar va undirish bosqichlari.
 * Murobaha qoidasi: kechikish uchun foiz, jarima yoki penya hisoblanmaydi — barcha summalar
 * faqat to'lanmagan savdo narxi (jadvaldagi to'lovlar) asosida.
 */
@Service
@RequiredArgsConstructor
public class CollectionService {

    public enum Stage {
        REMINDER, SOFT, HARD, LEGAL;

        /** 1–3 kun: eslatma, 4–30: soft (SMS, qo'ng'iroq), 31–90: hard (talabnoma, tashrif), 90+: sud/MIB. */
        public static Stage of(int daysLate) {
            if (daysLate <= 3) return REMINDER;
            if (daysLate <= 30) return SOFT;
            if (daysLate <= 90) return HARD;
            return LEGAL;
        }
    }

    /** So'nggi to'lov va'dasining holati: muddati kelmagan, bajarilgan, buzilgan. */
    public enum PromiseStatus { PENDING, KEPT, BROKEN }

    /**
     * stage — kechikish kuniga ko'ra; ochiq sud ishi bo'lsa ham LEGAL bo'ladi.
     * lettersSent — chiqarilgan talabnomalar soni.
     */
    public record OverdueRow(Long contractId, String contractNo, Long clientId, String clientName, String phone,
                             BigDecimal overdueAmount, int daysLate, Stage stage, LocalDate lastActionDate,
                             CollectionAction.Type lastActionType, String lastResult,
                             BigDecimal remaining, BigDecimal monthlyPayment, int overdueInstallments,
                             Long branchId, String branchName, Long collectorId, String collectorName,
                             LocalDate promisedDate, BigDecimal promisedAmount, PromiseStatus promiseStatus,
                             LocalDate plannedDate, CollectionAction.Type plannedType,
                             int lettersSent, LocalDate lastLetterDate,
                             Long legalCaseId, LegalCase.Status legalStatus) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public List<OverdueRow> overdue(Long branchId) {
        LocalDate today = LocalDate.now(clock);
        var p = new MapSqlParameterSource().addValue("today", today).addValue("branch", branchId);
        return jdbc.query("""
                with od as (
                  select c.id, sum(s.amount - s.paid_amount) as overdue_amount,
                         (:today::date - min(s.due_date)) as days_late, count(*) as overdue_cnt
                  from contracts c
                  join schedule_items s on s.contract_id = c.id
                  where c.status in ('ACTIVE','LATE') and s.status <> 'PAID' and s.due_date < :today::date
                    and (cast(:branch as bigint) is null or c.branch_id = :branch)
                  group by c.id
                )
                select c.id as contract_id, c.contract_no, c.monthly_payment, cl.id as client_id, cl.full_name, cl.phone,
                       od.overdue_amount, od.days_late, od.overdue_cnt,
                       (select sum(s.amount - s.paid_amount) from schedule_items s where s.contract_id = c.id) as remaining,
                       b.id as branch_id, b.name as branch_name, u.id as collector_id, u.full_name as collector_name,
                       la.created_at::date as last_action, la.action_type as last_type, la.result as last_result,
                       pr.promised_date, pr.promised_amount,
                       coalesce((select sum(py.amount) from payments py
                                  where py.contract_id = c.id and py.paid_at >= pr.created_at), 0) as paid_since_promise,
                       pl.planned_date, pl.action_type as planned_type,
                       (select count(*) from document_log d where d.contract_id = c.id and d.code = 'DEMAND') as letters,
                       (select max(d.created_at)::date from document_log d where d.contract_id = c.id and d.code = 'DEMAND') as last_letter,
                       lc.id as legal_id, lc.status as legal_status
                from od
                join contracts c on c.id = od.id
                join clients cl on cl.id = c.client_id
                left join branches b on b.id = c.branch_id
                left join users u on u.id = c.collector_id
                left join lateral (select a.created_at, a.action_type, a.result from collection_actions a
                                    where a.contract_id = c.id order by a.id desc limit 1) la on true
                left join lateral (select a.created_at, a.promised_date, a.promised_amount from collection_actions a
                                    where a.contract_id = c.id and a.promised_date is not null
                                    order by a.id desc limit 1) pr on true
                left join lateral (select a.planned_date, a.action_type from collection_actions a
                                    where a.contract_id = c.id and a.planned_date >= :today::date
                                    order by a.planned_date, a.id limit 1) pl on true
                left join legal_cases lc on lc.contract_id = c.id and lc.status <> 'CLOSED'
                order by od.days_late desc, od.overdue_amount desc
                """, p, (rs, i) -> {
            int days = rs.getInt("days_late");
            LocalDate promised = rs.getObject("promised_date", LocalDate.class);
            BigDecimal promisedAmount = rs.getBigDecimal("promised_amount");
            PromiseStatus ps = null;
            if (promised != null) {
                BigDecimal paid = rs.getBigDecimal("paid_since_promise");
                boolean kept = promisedAmount != null ? paid.compareTo(promisedAmount) >= 0 : paid.signum() > 0;
                ps = kept ? PromiseStatus.KEPT : promised.isBefore(today) ? PromiseStatus.BROKEN : PromiseStatus.PENDING;
            }
            String lastType = rs.getString("last_type");
            String plannedType = rs.getString("planned_type");
            String legal = rs.getString("legal_status");
            Stage stage = legal != null ? Stage.LEGAL : Stage.of(days);
            return new OverdueRow(rs.getLong("contract_id"), rs.getString("contract_no"), rs.getLong("client_id"),
                    rs.getString("full_name"), rs.getString("phone"), rs.getBigDecimal("overdue_amount"), days,
                    stage, rs.getObject("last_action", LocalDate.class),
                    lastType == null ? null : CollectionAction.Type.valueOf(lastType), rs.getString("last_result"),
                    rs.getBigDecimal("remaining"), rs.getBigDecimal("monthly_payment"), rs.getInt("overdue_cnt"),
                    rs.getObject("branch_id", Long.class), rs.getString("branch_name"),
                    rs.getObject("collector_id", Long.class), rs.getString("collector_name"),
                    promised, promisedAmount, ps,
                    rs.getObject("planned_date", LocalDate.class),
                    plannedType == null ? null : CollectionAction.Type.valueOf(plannedType),
                    rs.getInt("letters"), rs.getObject("last_letter", LocalDate.class),
                    rs.getObject("legal_id", Long.class), legal == null ? null : LegalCase.Status.valueOf(legal));
        });
    }
}

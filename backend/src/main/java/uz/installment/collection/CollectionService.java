package uz.installment.collection;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Muddati o'tgan qarzlar ro'yxati va bosqichlari (prototipdagi "Undirish" sahifasi). */
@Service
@RequiredArgsConstructor
public class CollectionService {

    public enum Stage {
        REMINDER, SOFT, HARD, LEGAL;

        /** 1–3 kun: eslatma, 4–14: soft, 15–30: hard, 30+: sud/huquqiy. */
        public static Stage of(int daysLate) {
            if (daysLate <= 3) return REMINDER;
            if (daysLate <= 14) return SOFT;
            if (daysLate <= 30) return HARD;
            return LEGAL;
        }
    }

    public record OverdueRow(Long contractId, String contractNo, Long clientId, String clientName, String phone,
                             BigDecimal overdueAmount, int daysLate, Stage stage, LocalDate lastActionDate) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public List<OverdueRow> overdue() {
        LocalDate today = LocalDate.now(clock);
        return jdbc.query("""
                select c.id as contract_id, c.contract_no, cl.id as client_id, cl.full_name, cl.phone,
                       sum(s.amount - s.paid_amount)        as overdue_amount,
                       (:today::date - min(s.due_date))           as days_late,
                       (select max(a.created_at)::date from collection_actions a where a.contract_id = c.id) as last_action
                from contracts c
                join clients cl on cl.id = c.client_id
                join schedule_items s on s.contract_id = c.id
                where c.status in ('ACTIVE','LATE')
                  and s.status <> 'PAID'
                  and s.due_date < :today::date
                group by c.id, cl.id
                order by days_late desc, overdue_amount desc
                """, Map.of("today", today), (rs, i) -> {
            int days = rs.getInt("days_late");
            return new OverdueRow(rs.getLong("contract_id"), rs.getString("contract_no"), rs.getLong("client_id"),
                    rs.getString("full_name"), rs.getString("phone"), rs.getBigDecimal("overdue_amount"), days,
                    Stage.of(days), rs.getObject("last_action", LocalDate.class));
        });
    }
}

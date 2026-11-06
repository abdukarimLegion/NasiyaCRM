package uz.installment.branch;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/** Filiallar kesimidagi ko'rsatkichlar: portfel, muddati o'tgan qarz, oy rejasi va tushum. */
@Service
@RequiredArgsConstructor
public class BranchService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /**
     * planPct — oy davomida berilgan nasiya (moliyalashtirilgan summa) oylik rejaning necha foizi;
     * collectionRate — shu oyda to'lanishi kerak bo'lganning yig'ilgan ulushi.
     */
    public record BranchStats(Long id, String code, String name, String address, String phone, String managerName,
                              boolean active, BigDecimal monthlyTarget, long staff, long openContracts,
                              long lateContracts, BigDecimal debt, BigDecimal overdue, long issuedCount,
                              BigDecimal issued, BigDecimal collected, BigDecimal collectionRate,
                              BigDecimal planPct) {
    }

    public List<BranchStats> stats(YearMonth month, Long onlyBranch) {
        ZoneId zone = clock.getZone();
        YearMonth m = month != null ? month : YearMonth.now(clock);
        LocalDate today = LocalDate.now(clock);
        LocalDate from = m.atDay(1);
        LocalDate to = m.plusMonths(1).atDay(1);
        LocalDate dueTo = today.isBefore(to) ? today : to.minusDays(1);
        var p = new MapSqlParameterSource()
                .addValue("from", from).addValue("to", to).addValue("today", today).addValue("dueTo", dueTo)
                .addValue("fromTs", from.atStartOfDay(zone).toOffsetDateTime())
                .addValue("toTs", to.atStartOfDay(zone).toOffsetDateTime())
                .addValue("branch", onlyBranch);
        return jdbc.query("""
                select b.id, b.code, b.name, b.address, b.phone, b.manager_name, b.active, b.monthly_target,
                  (select count(*) from users u where u.branch_id = b.id and u.active)                   as staff,
                  (select count(*) from contracts c where c.branch_id = b.id and c.status in ('ACTIVE','LATE')) as open_cnt,
                  (select count(*) from contracts c where c.branch_id = b.id and c.status = 'LATE')     as late_cnt,
                  coalesce((select sum(s.amount - s.paid_amount) from schedule_items s
                             join contracts c on c.id = s.contract_id
                            where c.branch_id = b.id and c.status in ('ACTIVE','LATE')), 0)              as debt,
                  coalesce((select sum(s.amount - s.paid_amount) from schedule_items s
                             join contracts c on c.id = s.contract_id
                            where c.branch_id = b.id and c.status in ('ACTIVE','LATE')
                              and s.status <> 'PAID' and s.due_date < :today), 0)                        as overdue,
                  (select count(*) from contracts c where c.branch_id = b.id and c.status <> 'CANCELLED'
                      and c.created_at >= :fromTs and c.created_at < :toTs)                              as issued_cnt,
                  coalesce((select sum(c.cost_price - c.down_payment) from contracts c
                            where c.branch_id = b.id and c.status <> 'CANCELLED'
                              and c.created_at >= :fromTs and c.created_at < :toTs), 0)                  as issued,
                  coalesce((select sum(p.amount) from payments p join contracts c on c.id = p.contract_id
                            where c.branch_id = b.id and p.paid_at >= :fromTs and p.paid_at < :toTs), 0) as collected,
                  (select case when sum(s.amount) = 0 then null
                               else round(100.0 * sum(s.paid_amount) / sum(s.amount), 1) end
                     from schedule_items s join contracts c on c.id = s.contract_id
                    where c.branch_id = b.id and c.status <> 'CANCELLED'
                      and s.due_date >= :from and s.due_date <= :dueTo)                                  as collection_rate
                from branches b
                where (cast(:branch as bigint) is null or b.id = :branch)
                order by b.active desc, b.name
                """, p, (rs, i) -> {
            BigDecimal target = rs.getBigDecimal("monthly_target");
            BigDecimal issued = rs.getBigDecimal("issued");
            BigDecimal plan = target != null && target.signum() > 0
                    ? issued.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP) : null;
            return new BranchStats(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("address"), rs.getString("phone"), rs.getString("manager_name"),
                    rs.getBoolean("active"), target, rs.getLong("staff"), rs.getLong("open_cnt"),
                    rs.getLong("late_cnt"), rs.getBigDecimal("debt"), rs.getBigDecimal("overdue"),
                    rs.getLong("issued_cnt"), issued, rs.getBigDecimal("collected"),
                    rs.getBigDecimal("collection_rate"), plan);
        });
    }
}

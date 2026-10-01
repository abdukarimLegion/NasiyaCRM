package uz.installment.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Bosh sahifa KPI'lari — to'g'ridan-to'g'ri SQL agregatlar. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public record Kpis(BigDecimal totalDebt, BigDecimal overdue, BigDecimal collectedThisMonth,
                       BigDecimal issuedThisMonth, long todayPayments, long tomorrowPayments,
                       long activeContracts, long lateContracts, BigDecimal collectionRate) {
    }

    public record MonthPoint(String month, BigDecimal collected, BigDecimal issued) {
    }

    public record RiskSlice(String category, long count) {
    }

    public record Dashboard(Kpis kpis, List<MonthPoint> monthly, List<RiskSlice> riskMix) {
    }

    public Dashboard get() {
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);
        OffsetDateTime monthStartTs = monthStart.atStartOfDay(clock.getZone()).toOffsetDateTime();
        Map<String, Object> p = Map.of("today", today, "tomorrow", today.plusDays(1),
                "monthStart", monthStart, "monthStartTs", monthStartTs,
                "sixMonthsAgo", monthStart.minusMonths(5));

        Kpis kpis = jdbc.queryForObject("""
                select
                  coalesce((select sum(s.amount - s.paid_amount) from schedule_items s
                            join contracts c on c.id = s.contract_id
                            where c.status in ('ACTIVE','LATE')), 0)                                  as total_debt,
                  coalesce((select sum(s.amount - s.paid_amount) from schedule_items s
                            join contracts c on c.id = s.contract_id
                            where c.status in ('ACTIVE','LATE') and s.status <> 'PAID'
                              and s.due_date < :today::date), 0)                                            as overdue,
                  coalesce((select sum(amount) from payments where paid_at >= :monthStartTs::timestamptz), 0)      as collected,
                  coalesce((select sum(cost_price - down_payment) from contracts
                            where status <> 'CANCELLED' and created_at >= :monthStartTs::timestamptz), 0)          as issued,
                  (select count(*) from schedule_items s join contracts c on c.id = s.contract_id
                    where c.status in ('ACTIVE','LATE') and s.status <> 'PAID' and s.due_date = :today::date)    as today_cnt,
                  (select count(*) from schedule_items s join contracts c on c.id = s.contract_id
                    where c.status in ('ACTIVE','LATE') and s.status <> 'PAID' and s.due_date = :tomorrow::date) as tomorrow_cnt,
                  (select count(*) from contracts where status = 'ACTIVE')                            as active_cnt,
                  (select count(*) from contracts where status = 'LATE')                              as late_cnt,
                  -- shu oyda to'lanishi kerak bo'lganning qancha qismi to'langan
                  (select case when sum(s.amount) = 0 then null
                               else round(100.0 * sum(s.paid_amount) / sum(s.amount), 1) end
                     from schedule_items s join contracts c on c.id = s.contract_id
                    where c.status <> 'CANCELLED' and s.due_date >= :monthStart::date and s.due_date <= :today::date) as collection_rate
                """, p, (rs, i) -> new Kpis(rs.getBigDecimal("total_debt"), rs.getBigDecimal("overdue"),
                rs.getBigDecimal("collected"), rs.getBigDecimal("issued"), rs.getLong("today_cnt"),
                rs.getLong("tomorrow_cnt"), rs.getLong("active_cnt"), rs.getLong("late_cnt"),
                rs.getBigDecimal("collection_rate")));

        List<MonthPoint> monthly = jdbc.query("""
                with months as (
                  select generate_series(:sixMonthsAgo::date, :monthStart::date, interval '1 month')::date as m
                )
                select to_char(m.m, 'YYYY-MM') as month,
                       coalesce((select sum(p.amount) from payments p
                                  where date_trunc('month', p.paid_at at time zone 'Asia/Tashkent')::date = m.m), 0) as collected,
                       coalesce((select sum(c.cost_price - c.down_payment) from contracts c
                                  where c.status <> 'CANCELLED'
                                    and date_trunc('month', c.created_at at time zone 'Asia/Tashkent')::date = m.m), 0) as issued
                from months m
                order by m.m
                """, p, (rs, i) -> new MonthPoint(rs.getString("month"), rs.getBigDecimal("collected"),
                rs.getBigDecimal("issued")));

        List<RiskSlice> risk = jdbc.query("""
                select risk_category, count(*) as cnt from contracts
                where status in ('ACTIVE','LATE') and risk_category is not null
                group by risk_category order by risk_category
                """, p, (rs, i) -> new RiskSlice(rs.getString("risk_category"), rs.getLong("cnt")));

        return new Dashboard(kpis, monthly, risk);
    }
}

package uz.installment.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bosh sahifa KPI'lari — to'g'ridan-to'g'ri SQL agregatlar. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** npl90Debt — 90 kundan ortiq kechikkan shartnomalarning qolgan qarzi (NPL). */
    public record Kpis(BigDecimal totalDebt, BigDecimal overdue, BigDecimal collectedThisMonth,
                       BigDecimal issuedThisMonth, long todayPayments, long tomorrowPayments,
                       long activeContracts, long lateContracts, BigDecimal collectionRate,
                       BigDecimal npl90Debt) {
    }

    public record MonthPoint(String month, BigDecimal collected, BigDecimal issued) {
    }

    public record RiskSlice(String category, long count) {
    }

    /** Faol portfelning mahsulot toifalari bo'yicha taqsimoti (qolgan qarz bo'yicha). */
    public record CategorySlice(String code, String nameUz, String nameRu, long contracts, BigDecimal debt) {
    }

    /** Portfel va murobaha foydasi (ustama) bo'yicha umumiy ko'rsatkichlar. */
    public record Portfolio(long contracts, long contractsThisMonth, long closedContracts,
                            BigDecimal financed, BigDecimal expectedProfit, BigDecimal earnedProfit,
                            BigDecimal remainingProfit, BigDecimal avgMarkupPct,
                            BigDecimal avgTermMonths, BigDecimal avgTicket) {
    }

    /** Jadval bo'yicha kelgusi oylarda tushishi kutilayotgan ustama. */
    public record ProfitPoint(String month, BigDecimal profit) {
    }

    public record Dashboard(Kpis kpis, Portfolio portfolio, List<MonthPoint> monthly,
                            List<ProfitPoint> profitForecast, List<RiskSlice> riskMix,
                            List<CategorySlice> categoryMix) {
    }

    public Dashboard get(Long branchId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);
        OffsetDateTime monthStartTs = monthStart.atStartOfDay(clock.getZone()).toOffsetDateTime();
        Map<String, Object> p = new HashMap<>();
        p.put("today", today);
        p.put("tomorrow", today.plusDays(1));
        p.put("monthStart", monthStart);
        p.put("monthStartTs", monthStartTs);
        p.put("yearAgo", monthStart.minusMonths(11));
        p.put("branch", branchId);

        Kpis kpis = jdbc.queryForObject("""
                select
                  coalesce((select sum(s.amount - s.paid_amount) from schedule_items s
                            join contracts c on c.id = s.contract_id
                            where c.status in ('ACTIVE','LATE') and (cast(:branch as bigint) is null or c.branch_id = :branch)), 0)                                  as total_debt,
                  coalesce((select sum(s.amount - s.paid_amount) from schedule_items s
                            join contracts c on c.id = s.contract_id
                            where c.status in ('ACTIVE','LATE') and s.status <> 'PAID' and (cast(:branch as bigint) is null or c.branch_id = :branch)
                              and s.due_date < :today::date), 0)                                            as overdue,
                  coalesce((select sum(p.amount) from payments p join contracts c on c.id = p.contract_id
                            where p.paid_at >= :monthStartTs::timestamptz and (cast(:branch as bigint) is null or c.branch_id = :branch)), 0)      as collected,
                  coalesce((select sum(c.cost_price - c.down_payment) from contracts c
                            where c.status <> 'CANCELLED' and c.created_at >= :monthStartTs::timestamptz
                              and (cast(:branch as bigint) is null or c.branch_id = :branch)), 0)          as issued,
                  (select count(*) from schedule_items s join contracts c on c.id = s.contract_id
                    where c.status in ('ACTIVE','LATE') and s.status <> 'PAID' and s.due_date = :today::date
                      and (cast(:branch as bigint) is null or c.branch_id = :branch))    as today_cnt,
                  (select count(*) from schedule_items s join contracts c on c.id = s.contract_id
                    where c.status in ('ACTIVE','LATE') and s.status <> 'PAID' and s.due_date = :tomorrow::date
                      and (cast(:branch as bigint) is null or c.branch_id = :branch)) as tomorrow_cnt,
                  (select count(*) from contracts c where c.status = 'ACTIVE' and (cast(:branch as bigint) is null or c.branch_id = :branch))            as active_cnt,
                  (select count(*) from contracts c where c.status = 'LATE' and (cast(:branch as bigint) is null or c.branch_id = :branch))              as late_cnt,
                  -- shu oyda to'lanishi kerak bo'lganning qancha qismi to'langan
                  (select case when sum(s.amount) = 0 then null
                               else round(100.0 * sum(s.paid_amount) / sum(s.amount), 1) end
                     from schedule_items s join contracts c on c.id = s.contract_id
                    where c.status <> 'CANCELLED' and s.due_date >= :monthStart::date and s.due_date <= :today::date
                      and (cast(:branch as bigint) is null or c.branch_id = :branch)) as collection_rate,
                  coalesce((select sum(s.amount - s.paid_amount) from schedule_items s
                            join contracts c on c.id = s.contract_id
                            where c.status in ('ACTIVE','LATE') and s.status <> 'PAID' and (cast(:branch as bigint) is null or c.branch_id = :branch)
                              and exists (select 1 from schedule_items x
                                          where x.contract_id = c.id and x.status <> 'PAID'
                                            and x.due_date < :today::date - 90)), 0)                       as npl90
                """, p, (rs, i) -> new Kpis(rs.getBigDecimal("total_debt"), rs.getBigDecimal("overdue"),
                rs.getBigDecimal("collected"), rs.getBigDecimal("issued"), rs.getLong("today_cnt"),
                rs.getLong("tomorrow_cnt"), rs.getLong("active_cnt"), rs.getLong("late_cnt"),
                rs.getBigDecimal("collection_rate"), rs.getBigDecimal("npl90")));

        List<MonthPoint> monthly = jdbc.query("""
                with months as (
                  select generate_series(:yearAgo::date, :monthStart::date, interval '1 month')::date as m
                )
                select to_char(m.m, 'YYYY-MM') as month,
                       coalesce((select sum(p.amount) from payments p join contracts c on c.id = p.contract_id
                                  where date_trunc('month', p.paid_at at time zone 'Asia/Tashkent')::date = m.m
                                    and (cast(:branch as bigint) is null or c.branch_id = :branch)), 0) as collected,
                       coalesce((select sum(c.cost_price - c.down_payment) from contracts c
                                  where c.status <> 'CANCELLED' and (cast(:branch as bigint) is null or c.branch_id = :branch)
                                    and date_trunc('month', c.created_at at time zone 'Asia/Tashkent')::date = m.m), 0) as issued
                from months m
                order by m.m
                """, p, (rs, i) -> new MonthPoint(rs.getString("month"), rs.getBigDecimal("collected"),
                rs.getBigDecimal("issued")));

        Portfolio portfolio = jdbc.queryForObject("""
                select
                  count(*)                                                                    as contracts,
                  count(*) filter (where created_at >= :monthStartTs::timestamptz)            as contracts_month,
                  count(*) filter (where status = 'CLOSED')                                   as closed_cnt,
                  coalesce(sum(cost_price - down_payment), 0)                                 as financed,
                  coalesce(sum(markup_amount), 0)                                             as expected_profit,
                  case when sum(cost_price - down_payment) > 0
                       then round(100.0 * sum(markup_amount) / sum(cost_price - down_payment), 1) end as avg_markup_pct,
                  round(avg(term_months)::numeric, 1)                                         as avg_term,
                  coalesce(round(avg(sale_price)), 0)                                         as avg_ticket,
                  -- to'langan qismga to'g'ri keladigan ustama (ulush bo'yicha)
                  coalesce((select round(sum(s.markup_part * s.paid_amount / nullif(s.amount, 0)))
                              from schedule_items s join contracts c2 on c2.id = s.contract_id
                             where c2.status <> 'CANCELLED' and (cast(:branch as bigint) is null or c2.branch_id = :branch)), 0)                              as earned_profit
                from contracts c
                where c.status <> 'CANCELLED' and (cast(:branch as bigint) is null or c.branch_id = :branch)
                """, p, (rs, i) -> {
            BigDecimal expected = rs.getBigDecimal("expected_profit");
            BigDecimal earned = rs.getBigDecimal("earned_profit");
            return new Portfolio(rs.getLong("contracts"), rs.getLong("contracts_month"), rs.getLong("closed_cnt"),
                    rs.getBigDecimal("financed"), expected, earned, expected.subtract(earned),
                    rs.getBigDecimal("avg_markup_pct"), rs.getBigDecimal("avg_term"), rs.getBigDecimal("avg_ticket"));
        });

        List<ProfitPoint> profitForecast = jdbc.query("""
                with months as (
                  select generate_series(:monthStart::date, :monthStart::date + interval '5 month',
                                         interval '1 month')::date as m
                )
                select to_char(m.m, 'YYYY-MM') as month,
                       coalesce((select sum(s.markup_part) from schedule_items s
                                  join contracts c on c.id = s.contract_id
                                 where c.status in ('ACTIVE','LATE') and (cast(:branch as bigint) is null or c.branch_id = :branch)
                                   and date_trunc('month', s.due_date)::date = m.m), 0) as profit
                from months m
                order by m.m
                """, p, (rs, i) -> new ProfitPoint(rs.getString("month"), rs.getBigDecimal("profit")));

        List<RiskSlice> risk = jdbc.query("""
                select risk_category, count(*) as cnt from contracts c
                where status in ('ACTIVE','LATE') and risk_category is not null and (cast(:branch as bigint) is null or c.branch_id = :branch)
                group by risk_category order by risk_category
                """, p, (rs, i) -> new RiskSlice(rs.getString("risk_category"), rs.getLong("cnt")));

        List<CategorySlice> categories = jdbc.query("""
                select cat.code, cat.name_uz, cat.name_ru, count(distinct c.id) as cnt,
                       coalesce(sum(s.amount - s.paid_amount), 0) as debt
                from contracts c
                join products pr on pr.id = c.product_id
                join categories cat on cat.id = pr.category_id
                join schedule_items s on s.contract_id = c.id
                where c.status in ('ACTIVE','LATE') and (cast(:branch as bigint) is null or c.branch_id = :branch)
                group by cat.code, cat.name_uz, cat.name_ru
                order by debt desc
                """, p, (rs, i) -> new CategorySlice(rs.getString("code"), rs.getString("name_uz"),
                rs.getString("name_ru"), rs.getLong("cnt"), rs.getBigDecimal("debt")));

        return new Dashboard(kpis, portfolio, monthly, profitForecast, risk, categories);
    }
}

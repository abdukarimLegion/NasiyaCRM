package uz.installment.forecast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.common.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    /**
     * O'zbekiston chakana savdosi uchun boshlang'ich mavsumiy koeffitsiyentlar (1.0 — o'rtacha oy):
     * mart (Navro'z), may–iyun (to'ylar, konditsionerlar), avgust–sentyabr (o'quv mavsumi),
     * dekabr (yil yakuni). Sozlamalarda o'zgartiriladi yoki tarixdan hisoblanadi.
     */
    public static final double[] DEFAULT_SEASONALITY =
            {0.90, 0.94, 1.12, 1.05, 1.15, 1.18, 1.08, 1.22, 1.25, 1.06, 1.10, 1.28};

    /** Ehtimoliy yo'qotish zaxirasi stavkalari (kechikish bo'yicha, qolgan qarzga nisbatan). */
    private static final double[][] ECL_BUCKETS = {{0, 0.01}, {1, 0.05}, {31, 0.25}, {91, 0.50}};

    public record Assumptions(BigDecimal onTimeRatePct, BigDecimal recoveryRatePct, BigDecimal baseMonthlySales,
                              BigDecimal avgMarkupPct, int avgTermMonths, BigDecimal openDebt, BigDecimal arrears,
                              int historyMonths) {
    }

    public record ScenarioTotals(String key, BigDecimal salesGrowthPct, BigDecimal collectionAdjPct,
                                 ForecastCalculator.Totals totals) {
    }

    public record EclBucket(String key, BigDecimal ratePct, BigDecimal exposure, BigDecimal reserve) {
    }

    public record Insight(String code, String level, String value) {
    }

    public record Forecast(Assumptions assumptions, List<ForecastCalculator.Point> points,
                           ForecastCalculator.Totals totals, List<ScenarioTotals> scenarios,
                           List<EclBucket> ecl, BigDecimal eclTotal, List<Insight> insights,
                           double[] seasonality, boolean customSeasonality, double[] suggestedSeasonality) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public Forecast forecast(int horizon, ForecastCalculator.Scenario sc, Long branchId) {
        int h = Math.max(3, Math.min(horizon, 24));
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        YearMonth start = YearMonth.from(today);
        var p = new MapSqlParameterSource().addValue("today", today).addValue("branch", branchId)
                .addValue("start", start.atDay(1)).addValue("end", start.plusMonths(h).atDay(1))
                .addValue("histFrom", start.minusMonths(6).atDay(1))
                .addValue("histFromTs", start.minusMonths(6).atDay(1).atStartOfDay(zone).toOffsetDateTime())
                .addValue("recFromTs", start.minusMonths(3).atDay(1).atStartOfDay(zone).toOffsetDateTime())
                .addValue("monthStartTs", start.atDay(1).atStartOfDay(zone).toOffsetDateTime())
                .addValue("yearAgoTs", start.minusMonths(12).atDay(1).atStartOfDay(zone).toOffsetDateTime())
                .addValue("suggestFromTs", start.minusMonths(24).atDay(1).atStartOfDay(zone).toOffsetDateTime())
                .addValue("tz", zone.getId());
        String branch = "(cast(:branch as bigint) is null or c.branch_id = :branch)";

        // 1) jadval bo'yicha kelgusi to'lovlar (bugundan keyingi), oyma-oy
        Map<String, BigDecimal[]> byMonth = new HashMap<>();
        jdbc.query("""
                select to_char(date_trunc('month', s.due_date), 'YYYY-MM') as m,
                       sum(s.amount - s.paid_amount) as due,
                       sum(s.markup_part * (s.amount - s.paid_amount) / nullif(s.amount, 0)) as markup
                from schedule_items s join contracts c on c.id = s.contract_id
                where c.status in ('ACTIVE','LATE') and s.status <> 'PAID'
                  and s.due_date >= :today::date and s.due_date < :end::date and %s
                group by 1
                """.formatted(branch), p, rs -> {
            byMonth.put(rs.getString("m"), new BigDecimal[]{rs.getBigDecimal("due"), nz(rs.getBigDecimal("markup"))});
        });
        List<BigDecimal> scheduled = new ArrayList<>();
        List<BigDecimal> scheduledMarkup = new ArrayList<>();
        for (int i = 0; i < h; i++) {
            BigDecimal[] v = byMonth.get(start.plusMonths(i).toString());
            scheduled.add(v == null ? BigDecimal.ZERO : v[0]);
            scheduledMarkup.add(v == null ? BigDecimal.ZERO : v[1]);
        }

        // 2) qoldiq va muddati o'tgan qarz
        Map<String, Object> debt = jdbc.queryForMap("""
                select coalesce(sum(s.amount - s.paid_amount), 0) as open_debt,
                       coalesce(sum(case when s.status <> 'PAID' and s.due_date < :today::date
                                         then s.amount - s.paid_amount else 0 end), 0) as arrears
                from schedule_items s join contracts c on c.id = s.contract_id
                where c.status in ('ACTIVE','LATE') and %s
                """.formatted(branch), p);
        BigDecimal openDebt = (BigDecimal) debt.get("open_debt");
        BigDecimal arrears = (BigDecimal) debt.get("arrears");

        // 3) o'z vaqtida yig'ilish ulushi: oxirgi 6 to'liq oyda muddati kelgan to'lovlarning
        //    muddatidan keyin 30 kun ichida to'langan qismi
        Map<String, Object> hist = jdbc.queryForMap("""
                select coalesce(sum(s.amount), 0) as due,
                       coalesce(sum((select coalesce(sum(pa.amount), 0) from payment_allocations pa
                                      join payments py on py.id = pa.payment_id
                                     where pa.schedule_item_id = s.id
                                       and (py.paid_at at time zone :tz)::date <= s.due_date + 30)), 0) as paid
                from schedule_items s join contracts c on c.id = s.contract_id
                where c.status <> 'CANCELLED' and s.due_date >= :histFrom::date and s.due_date < :start::date
                  and s.due_date <= :today::date - 30 and %s
                """.formatted(branch), p);
        BigDecimal histDue = (BigDecimal) hist.get("due");
        double onTime = histDue.signum() > 0
                ? ((BigDecimal) hist.get("paid")).doubleValue() / histDue.doubleValue() : 0.9;
        onTime = Math.max(0.3, Math.min(1, onTime));

        // 4) muddati o'tgan qarzdan oylik undirish: oxirgi 3 oyda muddati o'tgan to'lovlarga tushgan
        //    summaning oylik o'rtachasi / hozirgi muddati o'tgan qarz
        BigDecimal recovered = jdbc.queryForObject("""
                select coalesce(sum(pa.amount), 0) from payment_allocations pa
                join payments py on py.id = pa.payment_id
                join schedule_items s on s.id = pa.schedule_item_id
                join contracts c on c.id = py.contract_id
                where py.paid_at >= :recFromTs and py.paid_at < :monthStartTs
                  and s.due_date < (py.paid_at at time zone :tz)::date and %s
                """.formatted(branch), p, BigDecimal.class);
        double recovery = arrears.signum() > 0 ? nz(recovered).doubleValue() / 3 / arrears.doubleValue() : 0.2;
        recovery = Math.max(0.05, Math.min(0.6, recovery));

        // 5) savdo tarixi (moliyalashtirilgan summa) — oxirgi 24 oy
        List<YearMonth> months = new ArrayList<>();
        List<BigDecimal> sales = new ArrayList<>();
        jdbc.query("""
                with months as (
                  select generate_series(date_trunc('month', cast(:suggestFromTs as timestamptz) at time zone :tz),
                                         date_trunc('month', cast(:monthStartTs as timestamptz) at time zone :tz) - interval '1 month',
                                         interval '1 month')::date as m
                )
                select to_char(m.m, 'YYYY-MM') as month,
                       coalesce((select sum(c.cost_price - c.down_payment) from contracts c
                                  where c.status <> 'CANCELLED' and %s
                                    and date_trunc('month', c.created_at at time zone :tz)::date = m.m), 0) as issued
                from months m order by m.m
                """.formatted(branch), p, rs -> {
            months.add(YearMonth.parse(rs.getString("month")));
            sales.add(rs.getBigDecimal("issued"));
        });
        // birinchi savdodan oldingi bo'sh oylar hisobga olinmaydi
        int first = 0;
        while (first < sales.size() && sales.get(first).signum() == 0) first++;
        List<YearMonth> activeMonths = months.subList(first, months.size());
        List<BigDecimal> activeSales = sales.subList(first, sales.size());

        double[] season = seasonality();
        boolean custom = customSeasonality();
        int n6 = Math.min(6, activeMonths.size());
        BigDecimal baseSales = ForecastCalculator.deseasonalizedAverage(
                activeMonths.subList(activeMonths.size() - n6, activeMonths.size()),
                activeSales.subList(activeSales.size() - n6, activeSales.size()), season);
        double[] suggestion = ForecastCalculator.suggestSeasonality(activeMonths, activeSales);

        Map<String, Object> avg = jdbc.queryForMap("""
                select case when sum(c.cost_price - c.down_payment) > 0
                            then round(100.0 * sum(c.markup_amount) / sum(c.cost_price - c.down_payment), 1) end as markup,
                       round(avg(c.term_months)) as term
                from contracts c
                where c.status <> 'CANCELLED' and c.created_at >= :yearAgoTs and %s
                """.formatted(branch), p);
        BigDecimal avgMarkup = avg.get("markup") != null ? (BigDecimal) avg.get("markup") : BigDecimal.valueOf(20);
        int avgTerm = avg.get("term") != null ? ((Number) avg.get("term")).intValue() : 12;

        var in = new ForecastCalculator.Inputs(start, h, scheduled, scheduledMarkup, arrears, openDebt,
                BigDecimal.valueOf(onTime), BigDecimal.valueOf(recovery), baseSales, avgMarkup, avgTerm, season);
        List<ForecastCalculator.Point> points = ForecastCalculator.project(in, sc);
        ForecastCalculator.Totals totals = ForecastCalculator.totals(points);

        List<ScenarioTotals> scenarios = List.of(
                scenario("pessimistic", in, sc, -15, -10),
                new ScenarioTotals("base", sc.salesGrowthPct(), sc.collectionAdjPct(), totals),
                scenario("optimistic", in, sc, 10, 5));

        List<EclBucket> ecl = ecl(p, branch);
        BigDecimal eclTotal = ecl.stream().map(EclBucket::reserve).reduce(BigDecimal.ZERO, BigDecimal::add);

        Assumptions a = new Assumptions(pct(onTime), pct(recovery), baseSales, avgMarkup, avgTerm, openDebt, arrears,
                activeMonths.size());
        return new Forecast(a, points, totals, scenarios, ecl, eclTotal, insights(a, points), season, custom,
                suggestion);
    }

    // ------------------------------------------------------------------ mavsumiylik sozlamasi

    public double[] seasonality() {
        double[] v = readSeasonality();
        return v != null ? v : DEFAULT_SEASONALITY.clone();
    }

    public boolean customSeasonality() {
        return readSeasonality() != null;
    }

    /** null — standart qiymatlarga qaytarish. */
    @Transactional
    public double[] saveSeasonality(double[] values) {
        String v = null;
        if (values != null) {
            if (values.length != 12 || Arrays.stream(values).anyMatch(x -> x < 0.1 || x > 5)) {
                throw new IllegalArgumentException("12 ta koeffitsiyent (0.1 – 5.0) kerak");
            }
            try {
                v = json.writeValueAsString(values);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        var p = new MapSqlParameterSource().addValue("v", v).addValue("t", TenantContext.currentTenantId());
        jdbc.update("update tenant_settings set seasonality = cast(:v as jsonb), updated_at = now() where tenant_id = :t", p);
        return seasonality();
    }

    private double[] readSeasonality() {
        String raw = jdbc.queryForObject("select seasonality::text from tenant_settings where tenant_id = :t",
                Map.of("t", TenantContext.currentTenantId()), String.class);
        if (raw == null) {
            return null;
        }
        try {
            double[] v = json.readValue(raw, double[].class);
            return v.length == 12 ? v : null;
        } catch (JsonProcessingException e) {
            log.warn("seasonality o'qilmadi: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ yordamchilar

    private static ScenarioTotals scenario(String key, ForecastCalculator.Inputs in, ForecastCalculator.Scenario base,
                                           int growthDelta, int collectionDelta) {
        BigDecimal g = base.salesGrowthPct().add(BigDecimal.valueOf(growthDelta));
        BigDecimal c = base.collectionAdjPct().add(BigDecimal.valueOf(collectionDelta));
        var sc = new ForecastCalculator.Scenario(g, c, base.includeNewSales());
        return new ScenarioTotals(key, g, c, ForecastCalculator.totals(ForecastCalculator.project(in, sc)));
    }

    private List<EclBucket> ecl(MapSqlParameterSource p, String branch) {
        Map<Integer, BigDecimal> exposure = new HashMap<>();
        jdbc.query("""
                select coalesce(:today::date - min(s.due_date) filter (where s.status <> 'PAID' and s.due_date < :today::date), 0) as days,
                       sum(s.amount - s.paid_amount) as rem
                from contracts c join schedule_items s on s.contract_id = c.id
                where c.status in ('ACTIVE','LATE') and %s
                group by c.id
                """.formatted(branch), p, rs -> {
            int days = rs.getInt("days");
            int bucket = 0;
            for (int i = ECL_BUCKETS.length - 1; i >= 0; i--) {
                if (days >= ECL_BUCKETS[i][0]) {
                    bucket = i;
                    break;
                }
            }
            exposure.merge(bucket, nz(rs.getBigDecimal("rem")), BigDecimal::add);
        });
        String[] keys = {"current", "d1_30", "d31_90", "d90"};
        List<EclBucket> out = new ArrayList<>();
        for (int i = 0; i < ECL_BUCKETS.length; i++) {
            BigDecimal exp = exposure.getOrDefault(i, BigDecimal.ZERO);
            BigDecimal rate = BigDecimal.valueOf(ECL_BUCKETS[i][1]);
            out.add(new EclBucket(keys[i], rate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP),
                    exp, exp.multiply(rate).setScale(0, RoundingMode.HALF_UP)));
        }
        return out;
    }

    private static List<Insight> insights(Assumptions a, List<ForecastCalculator.Point> points) {
        List<Insight> out = new ArrayList<>();
        if (a.historyMonths() == 0) {
            out.add(new Insight("NO_HISTORY", "info", null));
        }
        double onTime = a.onTimeRatePct().doubleValue();
        out.add(new Insight("ON_TIME_RATE", onTime < 75 ? "danger" : onTime < 85 ? "warn" : "success",
                a.onTimeRatePct().toPlainString()));
        BigDecimal arrearsShare = ForecastCalculator.pct(a.arrears(), a.openDebt());
        if (arrearsShare != null) {
            double v = arrearsShare.doubleValue();
            out.add(new Insight("ARREARS_SHARE", v > 20 ? "danger" : v > 10 ? "warn" : "success",
                    arrearsShare.toPlainString()));
        }
        long negative = points.stream().filter(pt -> pt.netCash().signum() < 0).count();
        if (negative > 0) {
            out.add(new Insight("NEGATIVE_CASH", "warn", String.valueOf(negative)));
        }
        points.stream().max(Comparator.comparing(ForecastCalculator.Point::inflow))
                .ifPresent(pt -> out.add(new Insight("PEAK_MONTH", "info", pt.month())));
        points.stream().filter(pt -> pt.newSales().signum() > 0)
                .max(Comparator.comparing(ForecastCalculator.Point::seasonality))
                .ifPresent(pt -> out.add(new Insight("HIGH_SEASON", "info", pt.month())));
        return out;
    }

    private static BigDecimal pct(double v) {
        return BigDecimal.valueOf(v * 100).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

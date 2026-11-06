package uz.installment.kpi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.common.TenantContext;
import uz.installment.kpi.KpiCalculator.Metric;
import uz.installment.security.Role;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Operator KPI: haqiqiy ma'lumotlardan (shartnomalar, to'lovlar, undirish harakatlari) hisoblanadi.
 * <ul>
 *   <li>CONTRACTS / SALES — xodim rasmiylashtirgan shartnomalar soni va moliyalashtirilgan summa;</li>
 *   <li>COLLECTED — xodim kassada qabul qilgan to'lovlar;</li>
 *   <li>RECOVERED — muddati o'tgan to'lovlardan undirilgani: xodim mas'ul bo'lgan yoki 30 kun ichida
 *       ishlagan shartnomalar bo'yicha;</li>
 *   <li>ACTIONS — qo'ng'iroq, SMS, tashrif, talabnoma va sud harakatlari soni;</li>
 *   <li>QUALITY — xodim rasmiylashtirgan ochiq shartnomalar portfelining sifati (joriy holat).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiService {

    public record Targets(int contractsCount, BigDecimal salesVolume, BigDecimal collectedAmount,
                         BigDecimal recoveredAmount, int actionsCount, BigDecimal bonusAmount) {
        static Targets empty() {
            return new Targets(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO);
        }
    }

    public record OperatorKpi(Long userId, String fullName, Role role, Long branchId, String branchName,
                              Targets targets, List<KpiCalculator.MetricResult> metrics, BigDecimal score,
                              KpiCalculator.Grade grade, BigDecimal bonusPayout, long openContracts,
                              long lateContracts, long promisesMade, long promisesKept, int rank) {
    }

    public record Summary(String month, int operators, BigDecimal avgScore, BigDecimal totalBonus,
                          Map<KpiCalculator.Grade, Integer> grades, BigDecimal totalSales, BigDecimal totalCollected,
                          BigDecimal totalRecovered) {
    }

    public record Report(Summary summary, KpiCalculator.Config config, List<OperatorKpi> operators) {
    }

    public record HistoryPoint(String month, BigDecimal score, KpiCalculator.Grade grade, BigDecimal sales,
                               BigDecimal collected, BigDecimal recovered, long contracts, long actions) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public Report report(YearMonth month, Long branchId, Long onlyUser) {
        YearMonth m = month != null ? month : YearMonth.now(clock);
        KpiCalculator.Config cfg = config();
        List<OperatorKpi> rows = compute(m, branchId, onlyUser, cfg);
        rows.sort(Comparator.comparing((OperatorKpi o) -> o.score() == null ? BigDecimal.valueOf(-1) : o.score())
                .reversed().thenComparing(OperatorKpi::fullName));
        List<OperatorKpi> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            OperatorKpi o = rows.get(i);
            ranked.add(new OperatorKpi(o.userId(), o.fullName(), o.role(), o.branchId(), o.branchName(), o.targets(),
                    o.metrics(), o.score(), o.grade(), o.bonusPayout(), o.openContracts(), o.lateContracts(),
                    o.promisesMade(), o.promisesKept(), o.score() == null ? 0 : i + 1));
        }
        return new Report(summary(m, ranked), cfg, ranked);
    }

    public List<HistoryPoint> history(Long userId, int months) {
        KpiCalculator.Config cfg = config();
        YearMonth now = YearMonth.now(clock);
        List<HistoryPoint> out = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth m = now.minusMonths(i);
            List<OperatorKpi> r = compute(m, null, userId, cfg);
            OperatorKpi o = r.isEmpty() ? null : r.get(0);
            out.add(o == null ? new HistoryPoint(m.toString(), null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0, 0)
                    : new HistoryPoint(m.toString(), o.score(), o.grade(), actual(o, Metric.SALES),
                    actual(o, Metric.COLLECTED), actual(o, Metric.RECOVERED),
                    actual(o, Metric.CONTRACTS).longValue(), actual(o, Metric.ACTIONS).longValue()));
        }
        return out;
    }

    // ------------------------------------------------------------------ sozlamalar

    public KpiCalculator.Config config() {
        String raw = jdbc.queryForObject("select kpi_config::text from tenant_settings where tenant_id = :t",
                Map.of("t", TenantContext.currentTenantId()), String.class);
        KpiCalculator.Config def = KpiCalculator.Config.defaults();
        if (raw == null) {
            return def;
        }
        try {
            KpiCalculator.Config c = json.readValue(raw, KpiCalculator.Config.class);
            Map<Metric, Integer> w = new EnumMap<>(Metric.class);
            for (Metric m : Metric.values()) {
                w.put(m, c.weights() != null && c.weights().containsKey(m) ? c.weights().get(m) : def.weight(m));
            }
            return new KpiCalculator.Config(w, c.minScoreForBonus(), c.bonusCapPct(),
                    c.achievementCapPct() > 0 ? c.achievementCapPct() : def.achievementCapPct());
        } catch (JsonProcessingException e) {
            log.warn("kpi_config o'qilmadi, standart qiymatlar ishlatiladi: {}", e.getMessage());
            return def;
        }
    }

    @Transactional
    public KpiCalculator.Config saveConfig(KpiCalculator.Config cfg) {
        try {
            jdbc.update("update tenant_settings set kpi_config = cast(:c as jsonb), updated_at = now() where tenant_id = :t",
                    Map.of("c", json.writeValueAsString(cfg), "t", TenantContext.currentTenantId()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return config();
    }

    @Transactional
    public void saveTargets(Long userId, Targets t) {
        var p = new MapSqlParameterSource().addValue("t", TenantContext.currentTenantId()).addValue("u", userId)
                .addValue("cc", t.contractsCount()).addValue("sv", nz(t.salesVolume()))
                .addValue("ca", nz(t.collectedAmount())).addValue("ra", nz(t.recoveredAmount()))
                .addValue("ac", t.actionsCount()).addValue("ba", nz(t.bonusAmount()));
        jdbc.update("""
                insert into kpi_targets (tenant_id, user_id, contracts_count, sales_volume, collected_amount,
                                         recovered_amount, actions_count, bonus_amount, updated_at)
                values (:t, :u, :cc, :sv, :ca, :ra, :ac, :ba, now())
                on conflict (tenant_id, user_id) do update set
                  contracts_count = excluded.contracts_count, sales_volume = excluded.sales_volume,
                  collected_amount = excluded.collected_amount, recovered_amount = excluded.recovered_amount,
                  actions_count = excluded.actions_count, bonus_amount = excluded.bonus_amount, updated_at = now()
                """, p);
    }

    // ------------------------------------------------------------------ hisob

    private List<OperatorKpi> compute(YearMonth m, Long branchId, Long onlyUser, KpiCalculator.Config cfg) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        var p = new MapSqlParameterSource()
                .addValue("f", m.atDay(1).atStartOfDay(zone).toOffsetDateTime())
                .addValue("to", m.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime())
                .addValue("today", today).addValue("tz", zone.getId())
                .addValue("branch", branchId).addValue("onlyUser", onlyUser);
        return jdbc.query("""
                with u as (
                  select id, full_name, role, branch_id from users
                  where active
                    and (cast(:branch as bigint) is null or branch_id = :branch)
                    and (cast(:onlyUser as bigint) is null or id = :onlyUser)
                ),
                sales as (
                  select officer_id as uid, count(*) as cnt, sum(cost_price - down_payment) as vol
                  from contracts where status <> 'CANCELLED' and created_at >= :f and created_at < :to
                  group by officer_id
                ),
                coll as (
                  select cashier_id as uid, sum(amount) as amt from payments
                  where paid_at >= :f and paid_at < :to group by cashier_id
                ),
                act as (
                  select user_id as uid, count(*) as cnt from collection_actions
                  where created_at >= :f and created_at < :to
                    and action_type in ('CALL','SMS','VISIT','LETTER','LEGAL')
                  group by user_id
                ),
                rec as (
                  select u2.id as uid, sum(pa.amount) as amt
                  from payment_allocations pa
                  join payments py on py.id = pa.payment_id
                  join schedule_items s on s.id = pa.schedule_item_id
                  join contracts c on c.id = py.contract_id
                  join u u2 on (u2.id = c.collector_id
                                or exists (select 1 from collection_actions a
                                           where a.contract_id = c.id and a.user_id = u2.id
                                             and a.created_at <= py.paid_at
                                             and a.created_at >= py.paid_at - interval '30 days'))
                  where py.paid_at >= :f and py.paid_at < :to
                    and s.due_date < (py.paid_at at time zone :tz)::date
                  group by u2.id
                ),
                qual as (
                  select c.officer_id as uid,
                         sum(s.amount - s.paid_amount) as rem,
                         sum(case when s.status <> 'PAID' and s.due_date < :today::date
                                  then s.amount - s.paid_amount else 0 end) as od,
                         count(distinct c.id) as open_cnt,
                         count(distinct c.id) filter (where c.status = 'LATE') as late_cnt
                  from contracts c join schedule_items s on s.contract_id = c.id
                  where c.status in ('ACTIVE','LATE')
                  group by c.officer_id
                ),
                prom as (
                  select a.user_id as uid, count(*) as made,
                         count(*) filter (where (select coalesce(sum(x.amount), 0) from payments x
                                                  where x.contract_id = a.contract_id and x.paid_at >= a.created_at
                                                    and x.paid_at < ((a.promised_date + 1)::timestamp at time zone :tz))
                                                >= coalesce(a.promised_amount, 0.01)) as kept
                  from collection_actions a
                  where a.promised_date is not null and a.created_at >= :f and a.created_at < :to
                  group by a.user_id
                )
                select u.id, u.full_name, u.role, u.branch_id, b.name as branch_name,
                       t.contracts_count, t.sales_volume, t.collected_amount, t.recovered_amount,
                       t.actions_count, t.bonus_amount,
                       coalesce(sales.cnt, 0) as a_contracts, coalesce(sales.vol, 0) as a_sales,
                       coalesce(coll.amt, 0) as a_collected, coalesce(rec.amt, 0) as a_recovered,
                       coalesce(act.cnt, 0) as a_actions,
                       qual.rem, qual.od, coalesce(qual.open_cnt, 0) as open_cnt, coalesce(qual.late_cnt, 0) as late_cnt,
                       coalesce(prom.made, 0) as made, coalesce(prom.kept, 0) as kept
                from u
                left join branches b on b.id = u.branch_id
                left join kpi_targets t on t.user_id = u.id
                left join sales on sales.uid = u.id
                left join coll on coll.uid = u.id
                left join act on act.uid = u.id
                left join rec on rec.uid = u.id
                left join qual on qual.uid = u.id
                left join prom on prom.uid = u.id
                """, p, (rs, i) -> {
            Targets t = rs.getObject("contracts_count") == null ? Targets.empty()
                    : new Targets(rs.getInt("contracts_count"), rs.getBigDecimal("sales_volume"),
                    rs.getBigDecimal("collected_amount"), rs.getBigDecimal("recovered_amount"),
                    rs.getInt("actions_count"), rs.getBigDecimal("bonus_amount"));
            Map<Metric, BigDecimal> actual = new EnumMap<>(Metric.class);
            actual.put(Metric.CONTRACTS, rs.getBigDecimal("a_contracts"));
            actual.put(Metric.SALES, rs.getBigDecimal("a_sales"));
            actual.put(Metric.COLLECTED, rs.getBigDecimal("a_collected"));
            actual.put(Metric.RECOVERED, rs.getBigDecimal("a_recovered"));
            actual.put(Metric.ACTIONS, rs.getBigDecimal("a_actions"));
            Map<Metric, BigDecimal> target = new EnumMap<>(Metric.class);
            target.put(Metric.CONTRACTS, BigDecimal.valueOf(t.contractsCount()));
            target.put(Metric.SALES, t.salesVolume());
            target.put(Metric.COLLECTED, t.collectedAmount());
            target.put(Metric.RECOVERED, t.recoveredAmount());
            target.put(Metric.ACTIONS, BigDecimal.valueOf(t.actionsCount()));
            BigDecimal quality = KpiCalculator.quality(rs.getBigDecimal("rem"), rs.getBigDecimal("od"));
            KpiCalculator.Result r = KpiCalculator.calculate(actual, target, quality, t.bonusAmount(), cfg);
            return new OperatorKpi(rs.getLong("id"), rs.getString("full_name"), Role.valueOf(rs.getString("role")),
                    rs.getObject("branch_id", Long.class), rs.getString("branch_name"), t, r.metrics(), r.score(),
                    r.grade(), r.bonusPayout(), rs.getLong("open_cnt"), rs.getLong("late_cnt"),
                    rs.getLong("made"), rs.getLong("kept"), 0);
        }).stream()
                // Administrator faqat faoliyati yoki rejasi bo'lsa ko'rinadi
                .filter(o -> o.role() != Role.ADMIN || o.score() != null || hasActivity(o))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static boolean hasActivity(OperatorKpi o) {
        return o.metrics().stream().anyMatch(mr -> mr.metric() != Metric.QUALITY
                && mr.actual() != null && mr.actual().signum() > 0);
    }

    private static Summary summary(YearMonth m, List<OperatorKpi> rows) {
        List<OperatorKpi> scored = rows.stream().filter(o -> o.score() != null).toList();
        BigDecimal avg = scored.isEmpty() ? null : scored.stream().map(OperatorKpi::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scored.size()), 1, RoundingMode.HALF_UP);
        Map<KpiCalculator.Grade, Integer> grades = new EnumMap<>(KpiCalculator.Grade.class);
        for (KpiCalculator.Grade g : KpiCalculator.Grade.values()) {
            grades.put(g, 0);
        }
        scored.forEach(o -> grades.merge(o.grade(), 1, Integer::sum));
        return new Summary(m.toString(), rows.size(), avg,
                rows.stream().map(OperatorKpi::bonusPayout).reduce(BigDecimal.ZERO, BigDecimal::add), grades,
                sum(rows, Metric.SALES), sum(rows, Metric.COLLECTED), sum(rows, Metric.RECOVERED));
    }

    private static BigDecimal sum(List<OperatorKpi> rows, Metric m) {
        return rows.stream().map(o -> actual(o, m)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal actual(OperatorKpi o, Metric m) {
        return o.metrics().stream().filter(x -> x.metric() == m).findFirst()
                .map(KpiCalculator.MetricResult::actual).map(v -> v == null ? BigDecimal.ZERO : v)
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

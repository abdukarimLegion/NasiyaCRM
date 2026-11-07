package uz.installment.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.forecast.ForecastService;
import uz.installment.settings.AdvancedSettingsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Chuqur portfel tahlili (faqat o'qish). Hamma hisob — SQL agregatlar:
 * <ul>
 *   <li>kategoriya bo'yicha hajm, ustama, muddati o'tgan va NPL — savat qatorlari bo'yicha ulushlab taqsimlanadi;</li>
 *   <li>muddat guruhlari, kechikish yoshi (aging), filial va menejer reytinglari;</li>
 *   <li>oylik dinamika: berilgan nasiya, tushum, foyda (to'lovdagi ustama ulushi), hamkor hajmi va komissiyasi;</li>
 *   <li>foyda hisoboti: foyda − OPEX; ECL zaxirasi alohida ko'rsatiladi.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    public record CategoryRow(String code, String nameUz, String nameRu, double contracts, BigDecimal volume,
                              BigDecimal avgTermMonths, BigDecimal avgMarkupPct, BigDecimal expectedProfit,
                              BigDecimal earnedProfit, BigDecimal debt, BigDecimal overdue, BigDecimal overduePct,
                              BigDecimal nplDebt, BigDecimal nplPct, BigDecimal sharePct) {
    }

    public record TermRow(String bucket, long contracts, BigDecimal volume, BigDecimal avgMarkupPct,
                          BigDecimal debt, BigDecimal overduePct, BigDecimal nplPct) {
    }

    public record AgingRow(String key, long contracts, BigDecimal debt, BigDecimal sharePct) {
    }

    public record BranchRow(Long id, String name, long contracts, BigDecimal volume, BigDecimal collected,
                            BigDecimal debt, BigDecimal overduePct, BigDecimal target, BigDecimal planPct) {
    }

    public record ManagerRow(Long id, String name, String branch, long contracts, BigDecimal volume,
                             BigDecimal avgScore, BigDecimal debt, BigDecimal overduePct) {
    }

    public record MonthRow(String month, BigDecimal issued, BigDecimal collected, BigDecimal profit,
                           BigDecimal partnerVolume, BigDecimal partnerCommission) {
    }

    public record PartnerRow(String code, String name, long deals, BigDecimal volume, BigDecimal commission) {
    }

    public record Pnl(BigDecimal profit, BigDecimal opex, BigDecimal operatingProfit, BigDecimal eclProvision,
                      BigDecimal profitAfterProvision, BigDecimal partnerCommission, int months) {
    }

    public record Report(int months, List<CategoryRow> categories, List<TermRow> terms, List<AgingRow> aging,
                         List<BranchRow> branches, List<ManagerRow> managers, List<MonthRow> monthly,
                         List<PartnerRow> partners, Pnl pnl) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ForecastService forecast;
    private final AdvancedSettingsService settings;
    private final Clock clock;

    /** Har bir shartnoma bo'yicha yig'ma ko'rsatkichlar (barcha so'rovlar shu CTE'dan foydalanadi). */
    private static final String CM = """
            cm as (
              select c.id, c.branch_id, c.officer_id, c.term_months, c.markup_amount, c.markup_pct,
                     c.cost_price - c.down_payment as financed, c.status, c.created_at, c.score_total,
                     coalesce(sum(s.amount - s.paid_amount) filter (where c.status in ('ACTIVE','LATE')), 0) as rem,
                     coalesce(sum(s.amount - s.paid_amount) filter (where c.status in ('ACTIVE','LATE')
                              and s.status <> 'PAID' and s.due_date < :today::date), 0) as od,
                     coalesce(max(:today::date - s.due_date) filter (where c.status in ('ACTIVE','LATE')
                              and s.status <> 'PAID' and s.due_date < :today::date), 0) as days,
                     coalesce(sum(s.markup_part * s.paid_amount / nullif(s.amount, 0)), 0) as earned
              from contracts c left join schedule_items s on s.contract_id = c.id
              where c.status <> 'CANCELLED' and c.created_at >= :from
                and (cast(:branch as bigint) is null or c.branch_id = :branch)
              group by c.id
            )
            """;

    @Transactional(readOnly = true)
    public Report report(int months, Long branchId) {
        int n = Math.max(1, Math.min(months, 24));
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        YearMonth start = YearMonth.from(today).minusMonths(n - 1L);
        var p = new MapSqlParameterSource().addValue("today", today).addValue("branch", branchId)
                .addValue("from", start.atDay(1).atStartOfDay(zone).toOffsetDateTime())
                .addValue("tz", zone.getId());

        // ---- kategoriyalar: savat qatorlari ulushi bo'yicha (items yo'q eski shartnomalar — product_id bo'yicha)
        List<CategoryRow> cats = new ArrayList<>();
        BigDecimal totalDebt = BigDecimal.ZERO;
        jdbc.query("""
                with %s,
                lines as (
                  select ci.contract_id, p.category_id, ci.line_total::numeric / nullif(sum(ci.line_total) over (partition by ci.contract_id), 0) as w
                  from contract_items ci left join products p on p.id = ci.product_id
                  union all
                  select c.id, p.category_id, 1
                  from contracts c left join products p on p.id = c.product_id
                  where not exists (select 1 from contract_items x where x.contract_id = c.id)
                )
                select cat.code, cat.name_uz, cat.name_ru,
                       sum(l.w) as cnt, sum(cm.financed * l.w) as vol, sum(cm.term_months * l.w) / nullif(sum(l.w), 0) as term,
                       sum(cm.markup_amount * l.w) as markup, sum(cm.earned * l.w) as earned,
                       sum(cm.rem * l.w) as debt, sum(cm.od * l.w) as od,
                       sum(case when cm.days >= 90 then cm.rem * l.w else 0 end) as npl
                from lines l join cm on cm.id = l.contract_id
                left join categories cat on cat.id = l.category_id
                group by cat.code, cat.name_uz, cat.name_ru
                order by vol desc
                """.formatted(CM), p, rs -> {
            BigDecimal vol = nz(rs.getBigDecimal("vol"));
            BigDecimal debt = nz(rs.getBigDecimal("debt"));
            cats.add(new CategoryRow(rs.getString("code") == null ? "—" : rs.getString("code"),
                    rs.getString("name_uz") == null ? "—" : rs.getString("name_uz"),
                    rs.getString("name_ru") == null ? "—" : rs.getString("name_ru"),
                    rs.getDouble("cnt"), round0(vol), scale1(rs.getBigDecimal("term")),
                    vol.signum() > 0 ? pct(nz(rs.getBigDecimal("markup")), vol) : null,
                    round0(nz(rs.getBigDecimal("markup"))), round0(nz(rs.getBigDecimal("earned"))), round0(debt),
                    round0(nz(rs.getBigDecimal("od"))), pct(nz(rs.getBigDecimal("od")), debt),
                    round0(nz(rs.getBigDecimal("npl"))), pct(nz(rs.getBigDecimal("npl")), debt), null));
        });
        BigDecimal volTotal = cats.stream().map(CategoryRow::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategoryRow> withShare = cats.stream().map(c -> new CategoryRow(c.code(), c.nameUz(), c.nameRu(),
                c.contracts(), c.volume(), c.avgTermMonths(), c.avgMarkupPct(), c.expectedProfit(), c.earnedProfit(),
                c.debt(), c.overdue(), c.overduePct(), c.nplDebt(), c.nplPct(), pct(c.volume(), volTotal))).toList();

        // ---- muddat guruhlari
        List<TermRow> terms = jdbc.query("""
                with %s
                select case when term_months <= 3 then '1-3' when term_months <= 6 then '4-6'
                            when term_months <= 9 then '7-9' when term_months <= 12 then '10-12'
                            when term_months <= 18 then '13-18' else '19+' end as bucket,
                       min(term_months) as ord, count(*) as cnt, sum(financed) as vol, sum(markup_amount) as markup,
                       sum(rem) as debt, sum(od) as od, sum(case when days >= 90 then rem else 0 end) as npl
                from cm group by 1 order by ord
                """.formatted(CM), p, (rs, i) -> new TermRow(rs.getString("bucket"), rs.getLong("cnt"),
                round0(nz(rs.getBigDecimal("vol"))), pct(nz(rs.getBigDecimal("markup")), nz(rs.getBigDecimal("vol"))),
                round0(nz(rs.getBigDecimal("debt"))), pct(nz(rs.getBigDecimal("od")), nz(rs.getBigDecimal("debt"))),
                pct(nz(rs.getBigDecimal("npl")), nz(rs.getBigDecimal("debt")))));

        // ---- aging: ochiq portfelning kechikish yoshi bo'yicha qoldig'i
        List<AgingRow> agingRaw = jdbc.query("""
                with %s
                select case when days = 0 then 'current' when days <= 30 then 'd1_30' when days <= 60 then 'd31_60'
                            when days <= 90 then 'd61_90' else 'd90' end as k,
                       count(*) as cnt, sum(rem) as debt
                from cm where status in ('ACTIVE','LATE') group by 1
                """.formatted(CM), p, (rs, i) -> new AgingRow(rs.getString("k"), rs.getLong("cnt"),
                round0(nz(rs.getBigDecimal("debt"))), null));
        BigDecimal agingTotal = agingRaw.stream().map(AgingRow::debt).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AgingRow> aging = new ArrayList<>();
        for (String k : List.of("current", "d1_30", "d31_60", "d61_90", "d90")) {
            AgingRow r = agingRaw.stream().filter(a -> a.key().equals(k)).findFirst()
                    .orElse(new AgingRow(k, 0, BigDecimal.ZERO, null));
            aging.add(new AgingRow(k, r.contracts(), r.debt(), pct(r.debt(), agingTotal)));
        }

        // ---- filiallar reytingi
        List<BranchRow> branches = jdbc.query("""
                with %s,
                coll as (
                  select c.branch_id, sum(py.amount) as amt from payments py join contracts c on c.id = py.contract_id
                  where py.paid_at >= :from group by c.branch_id
                )
                select b.id, b.name, b.monthly_target, count(cm.id) as cnt, coalesce(sum(cm.financed), 0) as vol,
                       coalesce(sum(cm.rem), 0) as debt, coalesce(sum(cm.od), 0) as od, coalesce(max(coll.amt), 0) as collected,
                       coalesce(sum(cm.financed) filter (where cm.created_at >= date_trunc('month', now())), 0) as month_vol
                from branches b left join cm on cm.branch_id = b.id left join coll on coll.branch_id = b.id
                where cast(:branch as bigint) is null or b.id = :branch
                group by b.id, b.name, b.monthly_target order by vol desc
                """.formatted(CM), p, (rs, i) -> {
            BigDecimal target = rs.getBigDecimal("monthly_target");
            return new BranchRow(rs.getLong("id"), rs.getString("name"), rs.getLong("cnt"), round0(rs.getBigDecimal("vol")),
                    round0(rs.getBigDecimal("collected")), round0(rs.getBigDecimal("debt")),
                    pct(rs.getBigDecimal("od"), rs.getBigDecimal("debt")), target,
                    target == null || target.signum() == 0 ? null : pct(rs.getBigDecimal("month_vol"), target));
        });

        // ---- menejerlar (shartnomani rasmiylashtirgan xodimlar) reytingi
        List<ManagerRow> managers = jdbc.query("""
                with %s
                select u.id, u.full_name, b.name as branch, count(cm.id) as cnt, coalesce(sum(cm.financed), 0) as vol,
                       avg(cm.score_total) as score, coalesce(sum(cm.rem), 0) as debt, coalesce(sum(cm.od), 0) as od
                from cm join users u on u.id = cm.officer_id left join branches b on b.id = u.branch_id
                group by u.id, u.full_name, b.name order by vol desc limit 20
                """.formatted(CM), p, (rs, i) -> new ManagerRow(rs.getLong("id"), rs.getString("full_name"),
                rs.getString("branch"), rs.getLong("cnt"), round0(rs.getBigDecimal("vol")), scale1(rs.getBigDecimal("score")),
                round0(rs.getBigDecimal("debt")), pct(rs.getBigDecimal("od"), rs.getBigDecimal("debt"))));

        // ---- oylik dinamika
        p.addValue("histFromTs", start.atDay(1).atStartOfDay(zone).toOffsetDateTime());
        List<MonthRow> monthly = jdbc.query("""
                with months as (
                  select generate_series(cast(:fromDate as date), cast(:toDate as date), interval '1 month')::date as m
                )
                select to_char(m.m, 'YYYY-MM') as month,
                       coalesce((select sum(c.cost_price - c.down_payment) from contracts c
                                 where c.status <> 'CANCELLED' and (cast(:branch as bigint) is null or c.branch_id = :branch)
                                   and date_trunc('month', c.created_at at time zone :tz)::date = m.m), 0) as issued,
                       coalesce((select sum(py.amount) from payments py join contracts c on c.id = py.contract_id
                                 where (cast(:branch as bigint) is null or c.branch_id = :branch)
                                   and date_trunc('month', py.paid_at at time zone :tz)::date = m.m), 0) as collected,
                       coalesce((select sum(pa.amount * s.markup_part / nullif(s.amount, 0))
                                 from payment_allocations pa join payments py on py.id = pa.payment_id
                                 join schedule_items s on s.id = pa.schedule_item_id
                                 join contracts c on c.id = py.contract_id
                                 where (cast(:branch as bigint) is null or c.branch_id = :branch)
                                   and date_trunc('month', py.paid_at at time zone :tz)::date = m.m), 0) as profit,
                       coalesce((select sum(a.cost_price - a.down_payment) from applications a
                                 where a.status = 'PARTNER' and (cast(:branch as bigint) is null or a.branch_id = :branch)
                                   and date_trunc('month', a.partner_handover_at at time zone :tz)::date = m.m), 0) as pvol,
                       coalesce((select sum(a.partner_commission) from applications a
                                 where a.status = 'PARTNER' and (cast(:branch as bigint) is null or a.branch_id = :branch)
                                   and date_trunc('month', a.partner_handover_at at time zone :tz)::date = m.m), 0) as pcom
                from months m order by m.m
                """, p.addValue("fromDate", start.atDay(1)).addValue("toDate", YearMonth.from(today).atDay(1)),
                (rs, i) -> new MonthRow(rs.getString("month"), round0(rs.getBigDecimal("issued")),
                        round0(rs.getBigDecimal("collected")), round0(rs.getBigDecimal("profit")),
                        round0(rs.getBigDecimal("pvol")), round0(rs.getBigDecimal("pcom"))));

        // ---- hamkorlar bo'yicha
        List<PartnerRow> partners = jdbc.query("""
                select coalesce(pt.code, a.provider) as code, coalesce(pt.name, a.provider) as name, count(*) as deals,
                       sum(a.cost_price - a.down_payment) as vol, coalesce(sum(a.partner_commission), 0) as com
                from applications a left join partners pt on pt.id = a.partner_id
                where a.status = 'PARTNER' and a.partner_handover_at >= :from
                  and (cast(:branch as bigint) is null or a.branch_id = :branch)
                group by 1, 2 order by vol desc
                """, p, (rs, i) -> new PartnerRow(rs.getString("code"), rs.getString("name"), rs.getLong("deals"),
                round0(rs.getBigDecimal("vol")), round0(rs.getBigDecimal("com"))));

        // ---- foyda hisoboti
        BigDecimal profit = monthly.stream().map(MonthRow::profit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commission = monthly.stream().map(MonthRow::partnerCommission).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal opex = settings.get().monthlyOpex().multiply(BigDecimal.valueOf(n));
        BigDecimal ecl = forecast.eclFor(branchId).stream().map(ForecastService.EclBucket::reserve)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal operating = profit.subtract(opex);
        Pnl pnl = new Pnl(profit, opex, operating, ecl, operating.subtract(ecl), commission, n);

        return new Report(n, withShare, terms, aging, branches, managers, monthly, partners, pnl);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal round0(BigDecimal v) {
        return nz(v).setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale1(BigDecimal v) {
        return v == null ? null : v.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return null;
        }
        return nz(part).multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }
}

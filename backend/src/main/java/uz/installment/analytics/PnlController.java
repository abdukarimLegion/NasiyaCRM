package uz.installment.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.BusinessException;
import uz.installment.common.TenantContext;
import uz.installment.forecast.ForecastService;
import uz.installment.security.AuthUser;
import uz.installment.settings.AdvancedSettingsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Foyda va zarar hisoboti (P&L) ixtiyoriy davr uchun — kassa usulida: daromad to'lov tushgan kuni tan olinadi.
 *
 * <p>Taxminiy moddalar: jalb mablag' narxi (joriy portfelning o'z kapitalidan ortgan qismi × yillik stavka × davr),
 * ekvayring komissiyasi (naqdsiz to'lovlardan {@link #ACQUIRING_PCT}%) va OPEX (oylik OPEX × davr). Kechikish uchun
 * jarima/penya hisoblanmaydi, shuning uchun bunday daromad moddasi yo'q.
 */
@RestController
@RequestMapping("/api/analytics/pnl")
@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
@RequiredArgsConstructor
public class PnlController {

    /** Karta va to'lov tizimlari orqali tushgan to'lovlardan ushlanadigan o'rtacha komissiya, %. */
    static final BigDecimal ACQUIRING_PCT = new BigDecimal("0.9");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final NamedParameterJdbcTemplate jdbc;
    private final AdvancedSettingsService settings;
    private final ForecastService forecast;
    private final Clock clock;

    public record Pnl(LocalDate from, LocalDate to, int days,
                      BigDecimal principalCollected, BigDecimal markupCollected, BigDecimal otherReceipts,
                      BigDecimal grossReceipts,
                      BigDecimal fundingCost, BigDecimal acquiringFee, BigDecimal partnerCommission,
                      BigDecimal financingCosts, BigDecimal grossMargin,
                      BigDecimal opex, BigDecimal netProfit, BigDecimal marginPct,
                      BigDecimal eclReserve, BigDecimal afterProvision,
                      BigDecimal issuedVolume, long issuedContracts, BigDecimal partnerVolume,
                      BigDecimal fundingCostPct, BigDecimal acquiringPct) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Pnl get(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                   @RequestParam(required = false) Long branchId, @AuthenticationPrincipal AuthUser me) {
        LocalDate today = LocalDate.now(clock);
        LocalDate t = to != null ? to : today;
        LocalDate f = from != null ? from : t.withDayOfMonth(1);
        if (f.isAfter(t) || ChronoUnit.DAYS.between(f, t) > 3660) {
            throw new BusinessException("BAD_PERIOD", "Davr noto'g'ri tanlangan");
        }
        Long scope = me.branchScope(branchId);
        int days = (int) ChronoUnit.DAYS.between(f, t) + 1;
        var p = new MapSqlParameterSource("t", TenantContext.currentTenantId()).addValue("b", scope)
                .addValue("from", Timestamp.from(f.atStartOfDay(clock.getZone()).toInstant()))
                .addValue("to", Timestamp.from(t.plusDays(1).atStartOfDay(clock.getZone()).toInstant()));

        record Receipts(BigDecimal total, BigDecimal principal, BigDecimal markup, BigDecimal nonCash) {
        }
        Receipts r = jdbc.queryForObject("""
                select coalesce(sum(p.amount), 0) as total,
                       coalesce(sum((select sum(a.amount * s.principal_part / nullif(s.amount, 0))
                                      from payment_allocations a join schedule_items s on s.id = a.schedule_item_id
                                     where a.payment_id = p.id)), 0) as principal,
                       coalesce(sum((select sum(a.amount * s.markup_part / nullif(s.amount, 0))
                                      from payment_allocations a join schedule_items s on s.id = a.schedule_item_id
                                     where a.payment_id = p.id)), 0) as markup,
                       coalesce(sum(p.amount) filter (where p.method in ('CARD','PAYME','CLICK','UZUM')), 0) as non_cash
                from payments p join contracts c on c.id = p.contract_id
                where p.tenant_id = :t and p.paid_at >= :from and p.paid_at < :to
                  and (cast(:b as bigint) is null or c.branch_id = :b)
                """, p, (rs, i) -> new Receipts(rs.getBigDecimal("total"), rs.getBigDecimal("principal"),
                rs.getBigDecimal("markup"), rs.getBigDecimal("non_cash")));

        record Issued(BigDecimal volume, long contracts) {
        }
        Issued issued = jdbc.queryForObject("""
                select coalesce(sum(c.sale_price), 0), count(*) from contracts c
                where c.tenant_id = :t and c.status <> 'CANCELLED' and c.created_at >= :from and c.created_at < :to
                  and (cast(:b as bigint) is null or c.branch_id = :b)
                """, p, (rs, i) -> new Issued(rs.getBigDecimal(1), rs.getLong(2)));
        record Partner(BigDecimal volume, BigDecimal commission) {
        }
        Partner partner = jdbc.queryForObject("""
                select coalesce(sum(a.cost_price - a.down_payment), 0), coalesce(sum(a.partner_commission), 0)
                from applications a
                where a.tenant_id = :t and a.status = 'PARTNER' and a.partner_handover_at >= :from
                  and a.partner_handover_at < :to and (cast(:b as bigint) is null or a.branch_id = :b)
                """, p, (rs, i) -> new Partner(rs.getBigDecimal(1), rs.getBigDecimal(2)));
        BigDecimal portfolio = jdbc.queryForObject("""
                select coalesce(sum(s.amount - s.paid_amount), 0) from schedule_items s
                join contracts c on c.id = s.contract_id
                where c.tenant_id = :t and c.status in ('ACTIVE','LATE')
                  and (cast(:b as bigint) is null or c.branch_id = :b)
                """, p, BigDecimal.class);

        var cfg = settings.get();
        BigDecimal principal = som(r.principal());
        BigDecimal markup = som(r.markup());
        BigDecimal other = som(r.total()).subtract(principal).subtract(markup).max(BigDecimal.ZERO);
        BigDecimal gross = principal.add(markup).add(other);

        BigDecimal borrowed = portfolio.subtract(cfg.ownCapital() == null ? BigDecimal.ZERO : cfg.ownCapital())
                .max(BigDecimal.ZERO);
        BigDecimal rate = cfg.fundingCostPct() == null ? BigDecimal.ZERO : cfg.fundingCostPct();
        BigDecimal funding = som(borrowed.multiply(rate).multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(36500), 2, RoundingMode.HALF_UP));
        BigDecimal acquiring = som(r.nonCash().multiply(ACQUIRING_PCT).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        BigDecimal commission = som(partner.commission());
        BigDecimal financing = funding.add(acquiring).add(commission);
        BigDecimal margin = markup.subtract(financing);

        BigDecimal opex = som((cfg.monthlyOpex() == null ? BigDecimal.ZERO : cfg.monthlyOpex())
                .multiply(BigDecimal.valueOf(days)).divide(new BigDecimal("30.4"), 2, RoundingMode.HALF_UP));
        BigDecimal net = margin.subtract(opex);
        BigDecimal marginPct = gross.signum() == 0 ? BigDecimal.ZERO
                : net.multiply(HUNDRED).divide(gross, 1, RoundingMode.HALF_UP);
        BigDecimal ecl = som(forecast.eclFor(scope).stream().map(ForecastService.EclBucket::reserve)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return new Pnl(f, t, days, principal, markup, other, gross, funding, acquiring, commission, financing, margin,
                opex, net, marginPct, ecl, net.subtract(ecl), som(issued.volume()), issued.contracts(),
                som(partner.volume()), rate, ACQUIRING_PCT);
    }

    private static BigDecimal som(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(0, RoundingMode.HALF_UP);
    }
}

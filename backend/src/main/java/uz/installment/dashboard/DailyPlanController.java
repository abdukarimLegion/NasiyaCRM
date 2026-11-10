package uz.installment.dashboard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.audit.AuditService;
import uz.installment.common.TenantContext;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Kunlik reja va bugungi bajarilish (boshqaruv paneli): nasiya savdo hajmi, shartnomalar soni va kassa tushumi.
 * Reja filial bo'yicha saqlanadi; filial uchun alohida reja bo'lmasa kompaniya rejasi ko'rsatiladi.
 */
@RestController
@RequestMapping("/api/dashboard/daily")
@RequiredArgsConstructor
public class DailyPlanController {

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    public record Plan(BigDecimal sales, int contracts, BigDecimal collection) {
    }

    public record PlanRequest(@NotNull @PositiveOrZero BigDecimal sales, @Min(0) int contracts,
                              @NotNull @PositiveOrZero BigDecimal collection, Long branchId) {
    }

    /** dayProgressPct — ish kunining (09:00–20:00) qancha qismi o'tgani: reja bilan solishtirish uchun. */
    public record Daily(LocalDate date, Long branchId, Plan plan, Plan actual, int dayProgressPct) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Transactional(readOnly = true)
    public Daily get(@RequestParam(required = false) Long branchId, @AuthenticationPrincipal AuthUser me) {
        Long scope = me.branchScope(branchId);
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        MapSqlParameterSource p = new MapSqlParameterSource("t", TenantContext.currentTenantId())
                .addValue("b", scope)
                .addValue("from", Timestamp.from(today.atStartOfDay(clock.getZone()).toInstant()))
                .addValue("to", Timestamp.from(today.plusDays(1).atStartOfDay(clock.getZone()).toInstant()));

        Plan actual = jdbc.queryForObject("""
                select
                  (select coalesce(sum(c.sale_price), 0) from contracts c
                    where c.tenant_id = :t and c.status <> 'CANCELLED' and c.created_at >= :from and c.created_at < :to
                      and (cast(:b as bigint) is null or c.branch_id = :b))
                  + (select coalesce(sum(a.cost_price), 0) from applications a
                    where a.tenant_id = :t and a.status = 'PARTNER' and a.partner_handover_at >= :from
                      and a.partner_handover_at < :to and (cast(:b as bigint) is null or a.branch_id = :b)) as sales,
                  (select count(*) from contracts c
                    where c.tenant_id = :t and c.status <> 'CANCELLED' and c.created_at >= :from and c.created_at < :to
                      and (cast(:b as bigint) is null or c.branch_id = :b))
                  + (select count(*) from applications a
                    where a.tenant_id = :t and a.status = 'PARTNER' and a.partner_handover_at >= :from
                      and a.partner_handover_at < :to and (cast(:b as bigint) is null or a.branch_id = :b)) as contracts,
                  (select coalesce(sum(p.amount), 0) from payments p join contracts c on c.id = p.contract_id
                    where p.tenant_id = :t and p.paid_at >= :from and p.paid_at < :to
                      and (cast(:b as bigint) is null or c.branch_id = :b)) as collection
                """, p, (rs, i) -> new Plan(rs.getBigDecimal("sales"), rs.getInt("contracts"), rs.getBigDecimal("collection")));

        // Filial rejasi bo'lmasa — kompaniya rejasi
        Plan plan = jdbc.query("""
                select sales_target, contracts_target, collection_target from daily_plans
                where tenant_id = :t and (branch_id = :b or branch_id is null)
                order by branch_id nulls last limit 1
                """, p, (rs, i) -> new Plan(rs.getBigDecimal(1), rs.getInt(2), rs.getBigDecimal(3)))
                .stream().findFirst().orElse(new Plan(BigDecimal.ZERO, 0, BigDecimal.ZERO));

        LocalTime t = now.toLocalTime();
        int minutes = Math.max(0, Math.min(11 * 60, (t.getHour() - 9) * 60 + t.getMinute()));
        return new Daily(today, scope, plan, actual, Math.round(minutes * 100f / (11 * 60)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    @Transactional
    public Daily save(@Valid @RequestBody PlanRequest r, @AuthenticationPrincipal AuthUser me) {
        Long scope = me.branchScope(r.branchId());
        MapSqlParameterSource p = new MapSqlParameterSource("t", TenantContext.currentTenantId())
                .addValue("b", scope).addValue("s", r.sales()).addValue("c", r.contracts())
                .addValue("k", r.collection());
        int updated = jdbc.update("""
                update daily_plans set sales_target = :s, contracts_target = :c, collection_target = :k, updated_at = now()
                where tenant_id = :t and coalesce(branch_id, 0) = coalesce(cast(:b as bigint), 0)
                """, p);
        if (updated == 0) {
            jdbc.update("""
                    insert into daily_plans (tenant_id, branch_id, sales_target, contracts_target, collection_target)
                    values (:t, :b, :s, :c, :k)
                    """, p);
        }
        audit.record("DAILY_PLAN_UPDATE", "DAILY_PLAN", scope, Map.of(
                "sales", r.sales(), "contracts", r.contracts(), "collection", r.collection()));
        return get(r.branchId(), me);
    }
}

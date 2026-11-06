package uz.installment.kpi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.NotFoundException;
import uz.installment.security.AuthUser;
import uz.installment.security.Permission;
import uz.installment.security.UserRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Operator KPI: hamma o'z ko'rsatkichini ko'radi, KPI_VIEW_ALL — barcha xodimlarnikini. */
@RestController
@RequestMapping("/api/kpi")
@RequiredArgsConstructor
public class KpiController {

    private final KpiService service;
    private final UserRepository users;

    public record TargetsRequest(@PositiveOrZero int contractsCount, @NotNull @PositiveOrZero BigDecimal salesVolume,
                                 @NotNull @PositiveOrZero BigDecimal collectedAmount,
                                 @NotNull @PositiveOrZero BigDecimal recoveredAmount,
                                 @PositiveOrZero int actionsCount, @NotNull @PositiveOrZero BigDecimal bonusAmount) {
    }

    public record ConfigRequest(@NotNull Map<KpiCalculator.Metric, @Min(0) @Max(100) Integer> weights,
                                @Min(0) @Max(200) int minScoreForBonus, @Min(50) @Max(300) int bonusCapPct,
                                @Min(100) @Max(300) int achievementCapPct) {
    }

    @GetMapping
    public KpiService.Report report(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                    @RequestParam(required = false) Long branchId,
                                    @AuthenticationPrincipal AuthUser me) {
        if (!me.has(Permission.KPI_VIEW_ALL)) {
            return service.report(month, null, me.id());
        }
        return service.report(month, me.branchScope(branchId), null);
    }

    @GetMapping("/users/{userId}/history")
    public List<KpiService.HistoryPoint> history(@PathVariable Long userId,
                                                 @RequestParam(defaultValue = "6") int months,
                                                 @AuthenticationPrincipal AuthUser me) {
        if (!me.id().equals(userId) && !me.has(Permission.KPI_VIEW_ALL)) {
            throw new NotFoundException("Xodim", userId);
        }
        return service.history(userId, Math.max(1, Math.min(months, 12)));
    }

    @PutMapping("/targets/{userId}")
    @PreAuthorize("hasAuthority('KPI_MANAGE')")
    public void saveTargets(@PathVariable Long userId, @Valid @RequestBody TargetsRequest req) {
        if (!users.existsById(userId)) {
            throw new NotFoundException("Xodim", userId);
        }
        service.saveTargets(userId, new KpiService.Targets(req.contractsCount(), req.salesVolume(),
                req.collectedAmount(), req.recoveredAmount(), req.actionsCount(), req.bonusAmount()));
    }

    @GetMapping("/config")
    public KpiCalculator.Config config() {
        return service.config();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('KPI_MANAGE')")
    public KpiCalculator.Config saveConfig(@Valid @RequestBody ConfigRequest req) {
        int total = req.weights().values().stream().mapToInt(v -> v == null ? 0 : v).sum();
        if (total <= 0) {
            throw new IllegalArgumentException("Kamida bitta ko'rsatkich vazni 0 dan katta bo'lishi kerak");
        }
        return service.saveConfig(new KpiCalculator.Config(req.weights(), req.minScoreForBonus(),
                req.bonusCapPct(), req.achievementCapPct()));
    }
}

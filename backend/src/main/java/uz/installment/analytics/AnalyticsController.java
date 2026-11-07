package uz.installment.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.security.AuthUser;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService service;

    @GetMapping
    public AnalyticsService.Report get(@RequestParam(defaultValue = "12") int months,
                                       @RequestParam(required = false) Long branchId,
                                       @AuthenticationPrincipal AuthUser me) {
        return service.report(months, me.branchScope(branchId));
    }
}

package uz.installment.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import uz.installment.security.AuthUser;

@RestController
@PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping
    public DashboardService.Dashboard get(@RequestParam(required = false) Long branchId,
                                          @AuthenticationPrincipal AuthUser me) {
        return service.get(me.branchScope(branchId));
    }
}

package uz.installment.forecast;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/forecast")
@PreAuthorize("hasAuthority('FORECAST_VIEW')")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService service;

    public record SeasonalityRequest(double[] values) {
    }

    /**
     * @param growth        yangi savdo o'sishi, % (−50…100)
     * @param collectionAdj yig'ilish ulushiga tuzatma, % (−30…20)
     */
    @GetMapping
    public ForecastService.Forecast get(@RequestParam(defaultValue = "12") int horizon,
                                        @RequestParam(defaultValue = "0") BigDecimal growth,
                                        @RequestParam(defaultValue = "0") BigDecimal collectionAdj,
                                        @RequestParam(defaultValue = "true") boolean newSales,
                                        @RequestParam(required = false) Long branchId,
                                        @AuthenticationPrincipal AuthUser me) {
        BigDecimal g = growth.max(BigDecimal.valueOf(-50)).min(BigDecimal.valueOf(100));
        BigDecimal c = collectionAdj.max(BigDecimal.valueOf(-30)).min(BigDecimal.valueOf(20));
        return service.forecast(horizon, new ForecastCalculator.Scenario(g, c, newSales), me.branchScope(branchId));
    }

    @PutMapping("/seasonality")
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    public double[] saveSeasonality(@RequestBody SeasonalityRequest req) {
        return service.saveSeasonality(req.values());
    }

    @DeleteMapping("/seasonality")
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    public double[] resetSeasonality() {
        return service.saveSeasonality(null);
    }
}

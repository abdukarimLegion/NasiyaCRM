package uz.installment.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.settings.AdvancedSettingsService.Advanced;
import uz.installment.settings.AdvancedSettingsService.VerificationMode;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/settings/advanced")
@RequiredArgsConstructor
public class AdvancedSettingsController {

    private final AdvancedSettingsService service;

    public record AdvancedDto(@NotNull VerificationMode verificationMode,
                              @Min(30) @Max(900) int otpExpirySeconds, @Min(50) @Max(100) int minFaceMatchPct,
                              @NotNull @PositiveOrZero BigDecimal monthlyOpex,
                              @NotNull @PositiveOrZero BigDecimal ownCapital,
                              @NotNull @PositiveOrZero @DecimalMax("100") BigDecimal fundingCostPct) {
    }

    @GetMapping
    public Advanced get() {
        return service.get();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    public Advanced save(@Valid @RequestBody AdvancedDto d) {
        return service.save(new Advanced(d.verificationMode(), d.otpExpirySeconds(), d.minFaceMatchPct(),
                d.monthlyOpex(), d.ownCapital(), d.fundingCostPct()));
    }
}

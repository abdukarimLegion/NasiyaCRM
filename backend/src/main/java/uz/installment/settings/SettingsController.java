package uz.installment.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService service;

    public record SettingsDto(
            @NotBlank @Size(max = 200) String companyName,
            @NotBlank @Size(max = 100) String brandName,
            @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String primaryColor,
            @Size(max = 500) String logoUrl,
            @NotBlank @Pattern(regexp = "^[A-Z]{1,10}$") String contractPrefix,
            @NotBlank @Pattern(regexp = "^(uz|ru)$") String defaultLang,
            @Min(1) @Max(100_000) int roundingStep,
            @Min(1) @Max(50) int maxActiveContracts,
            @Size(max = 20) String smsSender) {

        static SettingsDto of(TenantSettings s) {
            return new SettingsDto(s.getCompanyName(), s.getBrandName(), s.getPrimaryColor(), s.getLogoUrl(),
                    s.getContractPrefix(), s.getDefaultLang(), s.getRoundingStep(), s.getMaxActiveContracts(),
                    s.getSmsSender());
        }
    }

    /** Brend ma'lumotlari login sahifasida ham kerak emas — faqat tizimga kirgandan keyin. */
    @GetMapping
    public SettingsDto get() {
        return SettingsDto.of(service.current());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto update(@Valid @RequestBody SettingsDto dto) {
        return SettingsDto.of(service.update(dto));
    }
}

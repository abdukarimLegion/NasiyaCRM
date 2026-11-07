package uz.installment.verification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.security.AuthUser;
import uz.installment.settings.AdvancedSettingsService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/verifications")
@PreAuthorize("hasAuthority('CONTRACTS_CREATE')")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService service;
    private final AdvancedSettingsService settings;

    public record ConfirmRequest(@NotNull Long id, @NotNull @Size(min = 4, max = 8) String code) {
    }

    public record BiometricRequest(@NotNull Long clientId, @Min(0) @Max(100) int matchPct, boolean livenessPassed,
                                   @Size(max = 100) String reference) {
    }

    /** Mijoz uchun joriy holat: rejim va oxirgi 30 daqiqada tasdiqlangan usullar. */
    public record Status(AdvancedSettingsService.VerificationMode mode, int minFaceMatchPct, Set<String> verified,
                         boolean satisfied) {
    }

    @GetMapping("/clients/{clientId}/status")
    public Status status(@PathVariable Long clientId) {
        var cfg = settings.get();
        Set<String> got = service.recentMethods(clientId);
        boolean ok = switch (cfg.verificationMode()) {
            case NONE -> true;
            case OTP -> got.contains("OTP");
            case BIOMETRIC -> got.contains("BIOMETRIC");
            case ANY -> !got.isEmpty();
            case BOTH -> got.contains("OTP") && got.contains("BIOMETRIC");
        };
        return new Status(cfg.verificationMode(), cfg.minFaceMatchPct(), got, ok);
    }

    @GetMapping("/clients/{clientId}")
    public List<VerificationService.Result> history(@PathVariable Long clientId) {
        return service.history(clientId);
    }

    @PostMapping("/clients/{clientId}/otp")
    public VerificationService.OtpSent send(@PathVariable Long clientId, @AuthenticationPrincipal AuthUser me) {
        return service.sendOtp(clientId, me.id());
    }

    @PostMapping("/otp/confirm")
    public VerificationService.Result confirm(@Valid @RequestBody ConfirmRequest req) {
        return service.confirmOtp(req.id(), req.code());
    }

    @PostMapping("/biometric")
    public VerificationService.Result biometric(@Valid @RequestBody BiometricRequest req,
                                                @AuthenticationPrincipal AuthUser me) {
        return service.recordBiometric(req.clientId(), req.matchPct(), req.livenessPassed(), req.reference(), me.id());
    }
}

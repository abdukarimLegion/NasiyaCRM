package uz.installment.mib;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
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

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/mib")
@RequiredArgsConstructor
public class MibController {

    private final MibService service;

    public record ManualRequest(boolean hasDebt, @PositiveOrZero BigDecimal totalDebt, @Min(0) int caseCount,
                                @Size(max = 500) String note) {
    }

    @GetMapping("/clients/{clientId}")
    @PreAuthorize("hasAnyAuthority('CLIENTS_VIEW','CONTRACTS_CREATE')")
    public MibService.State state(@PathVariable Long clientId) {
        return service.state(clientId);
    }

    /** Mijozni MIB'dan JShShIR bo'yicha tekshiradi (natija saqlanadi). */
    @PostMapping("/clients/{clientId}/check")
    @PreAuthorize("hasAuthority('CONTRACTS_CREATE')")
    public MibService.Check check(@PathVariable Long clientId, @AuthenticationPrincipal AuthUser me) {
        return service.runCheck(clientId, me.id());
    }

    /** Avtomatik xizmat ishlamasa: natijani MIB portalidan ko'rib qo'lda kiritish (tasdiqlash huquqi bilan). */
    @PostMapping("/clients/{clientId}/manual")
    @PreAuthorize("hasAuthority('CONTRACTS_APPROVE')")
    public MibService.Check manual(@PathVariable Long clientId, @Valid @RequestBody ManualRequest req,
                                   @AuthenticationPrincipal AuthUser me) {
        return service.recordManual(clientId, req.hasDebt(), req.totalDebt(), req.caseCount(), req.note(), me.id());
    }
}

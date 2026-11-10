package uz.installment.contract;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.application.Application;
import uz.installment.application.ApplicationService;
import uz.installment.common.BusinessException;
import uz.installment.common.PageResponse;
import uz.installment.contract.ContractDtos.ContractDetails;
import uz.installment.contract.ContractDtos.ContractListItem;
import uz.installment.contract.ContractDtos.CreateContractRequest;
import uz.installment.contract.ContractDtos.QuoteRequest;
import uz.installment.scoring.ScoringEngine;
import uz.installment.security.AuthUser;

@RestController
@PreAuthorize("hasAnyAuthority('CONTRACTS_VIEW','PAYMENTS_ACCEPT')")
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService service;
    private final ApplicationService applications;

    @GetMapping
    public PageResponse<ContractListItem> list(@RequestParam(required = false) ContractStatus status,
                                               @RequestParam(required = false) ScoringEngine.RiskCategory risk,
                                               @RequestParam(defaultValue = "false") boolean open,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) Long branchId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @AuthenticationPrincipal AuthUser me) {
        return service.search(status, risk, open, q, me.branchScope(branchId), page, size);
    }

    @GetMapping("/{id}")
    public ContractDetails get(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return service.details(id, me);
    }

    @PostMapping("/quote")
    @PreAuthorize("hasAuthority('CONTRACTS_CREATE')")
    public MurabahaCalculator.Quote quote(@Valid @RequestBody QuoteRequest req) {
        return service.quote(req);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTRACTS_CREATE')")
    public ContractDetails create(@Valid @RequestBody CreateContractRequest req,
                                  @AuthenticationPrincipal AuthUser me) {
        // Eski bir qadamli yo'l: ariza yozib qo'yiladi (rad etilsa ham), keyin shartnoma tuziladi
        Application a = applications.submit(req, me, true);
        return switch (a.getStatus()) {
            case CONTRACTED -> service.details(a.getContract().getId(), me);
            case REJECTED -> throw new BusinessException("SCORING_REJECTED", "Nasiya rad etildi: " + a.getRejectionReason());
            default -> throw new BusinessException("REVIEW_REQUIRED",
                    "Risk toifasi C — ariza tasdiqlash uchun yuborildi (" + a.getApplicationNo() + ")");
        };
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CONTRACTS_CANCEL')")
    public ContractDetails cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return service.cancel(id, me);
    }
}

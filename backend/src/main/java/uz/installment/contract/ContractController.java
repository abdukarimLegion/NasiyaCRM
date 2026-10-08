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
import uz.installment.common.PageResponse;
import uz.installment.contract.ContractDtos.ContractDetails;
import uz.installment.contract.ContractDtos.ContractListItem;
import uz.installment.contract.ContractDtos.CreateContractRequest;
import uz.installment.contract.ContractDtos.QuoteRequest;
import uz.installment.scoring.ScoringEngine;
import uz.installment.security.AuthUser;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService service;

    @GetMapping
    public PageResponse<ContractListItem> list(@RequestParam(required = false) ContractStatus status,
                                               @RequestParam(required = false) ScoringEngine.RiskCategory risk,
                                               @RequestParam(defaultValue = "false") boolean open,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.search(status, risk, open, q, page, size);
    }

    @GetMapping("/{id}")
    public ContractDetails get(@PathVariable Long id) {
        return service.details(id);
    }

    @PostMapping("/quote")
    public MurabahaCalculator.Quote quote(@Valid @RequestBody QuoteRequest req) {
        return service.quote(req);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CREDIT_OFFICER')")
    public ContractDetails create(@Valid @RequestBody CreateContractRequest req,
                                  @AuthenticationPrincipal AuthUser me) {
        return service.create(req, me);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ContractDetails cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}

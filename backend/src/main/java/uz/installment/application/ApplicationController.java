package uz.installment.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import uz.installment.contract.ContractDtos.CreateContractRequest;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService service;

    public record RejectRequest(@Size(max = 500) String reason) {
    }

    /** smsCode — hamkor mijozga yuborgan tasdiqlash kodi; serials — topshirilgan tovarlarning seriya raqami / IMEI. */
    public record HandoverRequest(@NotBlank String partnerCode, @Size(max = 100) String transactionId,
                                  @Size(max = 10) String smsCode, @Size(max = 500) String notes,
                                  @Valid @Size(max = 60) java.util.List<SerialItem> serials) {
    }

    public record SerialItem(@NotBlank @Size(max = 200) String name, @Size(max = 60) String serial) {
    }

    public record PartnerOrderRequest(@NotBlank String partnerCode) {
    }

    public record ApplicationView(Long id, String applicationNo, Long clientId, String clientName, String clientPhone,
                                  String itemsSummary, BigDecimal costPrice, BigDecimal downPayment,
                                  BigDecimal markupPct, BigDecimal salePrice, BigDecimal installmentTotal,
                                  int termMonths, BigDecimal monthlyPayment, Integer scoreTotal, String riskCategory,
                                  String decision, ApplicationStatus status, String rejectionReason,
                                  Long officerId, String officerName, Long branchId, String branchName,
                                  String decidedByName, Instant decidedAt, Long contractId, String contractNo,
                                  Instant createdAt, Map<String, Object> scoreDetails, String provider, String partnerName,
                                  String partnerTxId, BigDecimal partnerCommission, Instant partnerHandoverAt,
                                  String partnerNotes, java.util.List<Map<String, Object>> partnerSerials) {
        static ApplicationView of(Application a) {
            return new ApplicationView(a.getId(), a.getApplicationNo(), a.getClient().getId(),
                    a.getClient().getFullName(), a.getClient().getPhone(), a.getItemsSummary(), a.getCostPrice(),
                    a.getDownPayment(), a.getMarkupPct(), a.getSalePrice(), a.getInstallmentTotal(),
                    a.getTermMonths(), a.getMonthlyPayment(), a.getScoreTotal(),
                    a.getRiskCategory() == null ? null : a.getRiskCategory().name(),
                    a.getDecision() == null ? null : a.getDecision().name(), a.getStatus(), a.getRejectionReason(),
                    a.getOfficer() == null ? null : a.getOfficer().getId(),
                    a.getOfficer() == null ? null : a.getOfficer().getFullName(),
                    a.getBranch() == null ? null : a.getBranch().getId(),
                    a.getBranch() == null ? null : a.getBranch().getName(),
                    a.getDecidedBy() == null ? null : a.getDecidedBy().getFullName(), a.getDecidedAt(),
                    a.getContract() == null ? null : a.getContract().getId(),
                    a.getContract() == null ? null : a.getContract().getContractNo(),
                    a.getCreatedAt(), a.getScoreDetails(), a.getProvider(),
                    a.getPartner() == null ? null : a.getPartner().getName(), a.getPartnerTxId(),
                    a.getPartnerCommission(), a.getPartnerHandoverAt(), a.getPartnerNotes(), a.getPartnerSerials());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTRACTS_VIEW')")
    public PageResponse<ApplicationView> list(@RequestParam(required = false) ApplicationStatus status,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) Long branchId,
                                              @RequestParam(required = false) Long officerId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @AuthenticationPrincipal AuthUser me) {
        var p = service.search(status, q, me.branchScope(branchId), officerId, page, size);
        return new PageResponse<>(p.content().stream().map(ApplicationView::of).toList(),
                p.page(), p.size(), p.totalElements(), p.totalPages());
    }

    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('CONTRACTS_VIEW')")
    public Map<ApplicationStatus, Long> counts(@RequestParam(required = false) Long branchId,
                                               @AuthenticationPrincipal AuthUser me) {
        return service.counts(me.branchScope(branchId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTRACTS_VIEW')")
    public ApplicationView get(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return ApplicationView.of(service.get(id, me));
    }

    /** Yangi ariza. contractNow=true — tasdiqlangan bo'lsa shartnoma darhol tuziladi. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTRACTS_CREATE')")
    public ApplicationView submit(@Valid @RequestBody CreateContractRequest req,
                                  @RequestParam(defaultValue = "false") boolean contractNow,
                                  @AuthenticationPrincipal AuthUser me) {
        return ApplicationView.of(service.reload(service.submit(req, me, contractNow).getId()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('CONTRACTS_APPROVE')")
    public ApplicationView approve(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return ApplicationView.of(service.reload(service.approve(id, me).getId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('CONTRACTS_APPROVE')")
    public ApplicationView reject(@PathVariable Long id, @Valid @RequestBody(required = false) RejectRequest req,
                                  @AuthenticationPrincipal AuthUser me) {
        return ApplicationView.of(service.reload(service.reject(id, req == null ? null : req.reason(), me).getId()));
    }

    @PostMapping("/{id}/handover")
    @PreAuthorize("hasAuthority('CONTRACTS_CREATE')")
    public ApplicationView handover(@PathVariable Long id, @Valid @RequestBody HandoverRequest req,
                                    @AuthenticationPrincipal AuthUser me) {
        return ApplicationView.of(service.reload(
                service.handover(id, req.partnerCode(), req.transactionId(), req.smsCode(), req.notes(),
                        req.serials() == null ? null : req.serials().stream()
                                .filter(s -> s.serial() != null && !s.serial().isBlank())
                                .map(s -> Map.<String, Object>of("name", s.name(), "serial", s.serial().trim()))
                                .toList(), me).getId()));
    }

    /** Hamkor tizimida buyurtma ochadi — hamkor mijozga tasdiqlash kodi yuboradi (keyin /handover). */
    @PostMapping("/{id}/partner-order")
    @PreAuthorize("hasAuthority('CONTRACTS_CREATE')")
    public ApplicationService.PartnerOrder partnerOrder(@PathVariable Long id, @Valid @RequestBody PartnerOrderRequest req,
                                                        @AuthenticationPrincipal AuthUser me) {
        return service.partnerOrder(id, req.partnerCode(), me);
    }

    @PostMapping("/{id}/contract")
    @PreAuthorize("hasAuthority('CONTRACTS_CREATE')")
    public ApplicationView toContract(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return ApplicationView.of(service.reload(service.toContract(id, me).getId()));
    }
}

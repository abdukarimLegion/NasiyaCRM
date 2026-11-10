package uz.installment.limits;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.audit.AuditService;
import uz.installment.client.Client;
import uz.installment.client.ClientRepository;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.partner.Partner;
import uz.installment.partner.PartnerRepository;
import uz.installment.partner.gateway.PartnerGateway;
import uz.installment.partner.gateway.PartnerGateways;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Mijozning hamkor nasiya platformalaridagi limiti (Uzum, Anor, Alif, Payme ...) — JShShIR bo'yicha, faqat
 * ma'lumot sifatida: natija saqlanmaydi va skoring yoki ichki limitga ta'sir qilmaydi. Platformalar ro'yxati —
 * faol hamkorlar ({@code partners}); har biri o'z {@link PartnerGateway}'i orqali so'raladi.
 */
@RestController
@RequestMapping("/api/external-limits")
@RequiredArgsConstructor
public class ExternalLimitController {

    private final PartnerRepository partners;
    private final PartnerGateways gateways;
    private final ClientRepository clients;
    private final AuditService audit;

    /** mock — kamida bitta platforma javobi namoyish (soxta) bo'lsa true. */
    public record Report(Long clientId, boolean mock, Instant checkedAt, BigDecimal totalAvailable,
                         List<ExternalLimit> items) {
    }

    /** POST — har chaqiruv hamkor platformalarga so'rov yuboradi va jurnalga yoziladi. */
    @PostMapping("/clients/{clientId}/check")
    @PreAuthorize("hasAnyAuthority('CLIENTS_VIEW','CONTRACTS_CREATE')")
    @Transactional
    public Report check(@PathVariable Long clientId) {
        Client c = clients.findById(clientId).orElseThrow(() -> new NotFoundException("Mijoz", clientId));
        if (c.getPinfl() == null || c.getPinfl().isBlank()) {
            throw new BusinessException("PINFL_REQUIRED", "Limitni tekshirish uchun mijozning JShShIR'i kiritilgan bo'lishi kerak");
        }
        List<ExternalLimit> items = partners.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(Partner::isActive)
                .map(p -> one(p, c.getPinfl()))
                .toList();
        BigDecimal total = items.stream().map(ExternalLimit::available).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean mock = items.stream().anyMatch(ExternalLimit::mock);
        audit.record("EXTERNAL_LIMIT_CHECK", "CLIENT", clientId, Map.of("mock", mock, "platforms", items.size()));
        return new Report(clientId, mock, Instant.now(), total, items);
    }

    private ExternalLimit one(Partner p, String pinfl) {
        PartnerGateway g = gateways.forCode(p.getCode());
        PartnerGateway.LimitResult r;
        try {
            r = g.checkLimit(p.getCode(), pinfl);
        } catch (RuntimeException e) {
            r = PartnerGateway.LimitResult.of(PartnerGateway.LimitStatus.ERROR, e.getMessage());
        }
        return new ExternalLimit(p.getCode(), p.getName(), r.status(), r.total(), r.used(), r.available(),
                r.maxTermMonths(), g.isMock(), r.message());
    }
}

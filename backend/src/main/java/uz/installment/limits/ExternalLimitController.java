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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Mijozning tashqi nasiya platformalaridagi limiti (Uzum, Anor, Alif, Payme ...) — JShShIR bo'yicha, faqat
 * ma'lumot sifatida: natija saqlanmaydi va skoring yoki ichki limitga ta'sir qilmaydi.
 */
@RestController
@RequestMapping("/api/external-limits")
@RequiredArgsConstructor
public class ExternalLimitController {

    private final ExternalLimitClient client;
    private final ClientRepository clients;
    private final AuditService audit;

    public record Report(Long clientId, boolean mock, Instant checkedAt, BigDecimal totalAvailable,
                         List<ExternalLimit> items) {
    }

    /** POST — har chaqiruv tashqi platformalarga so'rov yuboradi va jurnalga yoziladi. */
    @PostMapping("/clients/{clientId}/check")
    @PreAuthorize("hasAnyAuthority('CLIENTS_VIEW','CONTRACTS_CREATE')")
    @Transactional
    public Report check(@PathVariable Long clientId) {
        Client c = clients.findById(clientId).orElseThrow(() -> new NotFoundException("Mijoz", clientId));
        if (c.getPinfl() == null || c.getPinfl().isBlank()) {
            throw new BusinessException("PINFL_REQUIRED", "Limitni tekshirish uchun mijozning JShShIR'i kiritilgan bo'lishi kerak");
        }
        List<ExternalLimit> items = client.check(c.getPinfl());
        BigDecimal total = items.stream().map(ExternalLimit::available).reduce(BigDecimal.ZERO, BigDecimal::add);
        audit.record("EXTERNAL_LIMIT_CHECK", "CLIENT", clientId, Map.of("mock", client.isMock()));
        return new Report(clientId, client.isMock(), Instant.now(), total, items);
    }
}

package uz.installment.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.PageResponse;

import java.time.Instant;
import java.util.Map;

/** Audit yozuvlarini ko'rish (faqat administrator). */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository repo;

    public record AuditRow(Long id, Long userId, String username, String action, String entity, Long entityId,
                           Map<String, Object> details, Instant createdAt) {
        static AuditRow of(AuditLog log) {
            return new AuditRow(log.getId(), log.getUserId(), log.getUsername(), log.getAction(), log.getEntity(),
                    log.getEntityId(), log.getDetails(), log.getCreatedAt());
        }
    }

    @GetMapping
    public PageResponse<AuditRow> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        var p = repo.findAllByOrderByIdDesc(
                PageRequest.of(page, Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(p, AuditRow::of);
    }
}

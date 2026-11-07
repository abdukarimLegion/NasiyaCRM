package uz.installment.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uz.installment.security.AuthUser;

import java.util.Map;

/**
 * Audit yozuvlarini saqlaydi. Joriy foydalanuvchi SecurityContext'dan olinadi,
 * shuning uchun servis metodlarini chaqiruvchilar faqat amal ma'lumotini beradi.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;

    public void record(String action, String entity, Long entityId, Map<String, Object> details) {
        AuditLog log = new AuditLog();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            log.setUserId(user.id());
            log.setUsername(user.username());
        }
        log.setAction(action);
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setDetails(details == null || details.isEmpty() ? null : details);
        repo.save(log);
    }

    public void record(String action, String entity, Long entityId) {
        record(action, entity, entityId, null);
    }
}

package uz.installment.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.NotFoundException;
import uz.installment.common.PageResponse;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationTemplateRepository templates;
    private final NotificationLogRepository logs;
    private final NotificationService service;

    public record TemplateDto(Long id, String eventKey, NotificationChannel channel, boolean enabled,
                              int offsetDays, String textUz, String textRu) {
        static TemplateDto of(NotificationTemplate t) {
            return new TemplateDto(t.getId(), t.getEventKey(), t.getChannel(), t.isEnabled(), t.getOffsetDays(),
                    t.getTextUz(), t.getTextRu());
        }
    }

    public record TemplateUpdate(@NotNull NotificationChannel channel, boolean enabled,
                                 @NotBlank @Size(max = 500) String textUz, @NotBlank @Size(max = 500) String textRu) {
    }

    public record LogDto(Long id, String eventKey, NotificationChannel channel, String recipient, String message,
                         NotificationLog.Status status, String error, Instant createdAt) {
        static LogDto of(NotificationLog l) {
            return new LogDto(l.getId(), l.getEventKey(), l.getChannel(), l.getRecipient(), l.getMessage(),
                    l.getStatus(), l.getError(), l.getCreatedAt());
        }
    }

    @GetMapping("/templates")
    public List<TemplateDto> templates() {
        return templates.findAllByOrderByOffsetDaysAsc().stream().map(TemplateDto::of).toList();
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public TemplateDto update(@PathVariable Long id, @Valid @RequestBody TemplateUpdate req) {
        NotificationTemplate t = templates.findById(id).orElseThrow(() -> new NotFoundException("Shablon", id));
        t.setChannel(req.channel());
        t.setEnabled(req.enabled());
        t.setTextUz(req.textUz());
        t.setTextRu(req.textRu());
        return TemplateDto.of(t);
    }

    @GetMapping("/log")
    public PageResponse<LogDto> log(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(logs.findAllByOrderByIdDesc(PageRequest.of(page, Math.min(size, 200))), LogDto::of);
    }

    /** Qo'lda ishga tushirish (sinov uchun). */
    @PostMapping("/run-reminders")
    @PreAuthorize("hasRole('ADMIN')")
    public NotificationService.ReminderStats runNow() {
        return service.sendDailyReminders();
    }
}

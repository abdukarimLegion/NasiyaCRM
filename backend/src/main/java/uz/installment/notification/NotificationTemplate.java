package uz.installment.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.installment.common.TenantEntity;

import java.time.Instant;

@Entity
@Table(name = "notification_templates")
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplate extends TenantEntity {

    /** CONTRACT, BEFORE_3, PAYDAY, LATE_1, LATE_7, LATE_30 */
    @Column(name = "event_key", nullable = false, length = 30, updatable = false)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(nullable = false)
    private boolean enabled;

    /** Manfiy: to'lov sanasidan oldin, 0: to'lov kuni, musbat: kechikishdan keyin. */
    @Column(name = "offset_days", nullable = false)
    private int offsetDays;

    @Column(name = "text_uz", nullable = false, length = 500)
    private String textUz;

    @Column(name = "text_ru", nullable = false, length = 500)
    private String textRu;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean isScheduleEvent() {
        return !"CONTRACT".equals(eventKey);
    }
}

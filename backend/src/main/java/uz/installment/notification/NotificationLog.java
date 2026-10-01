package uz.installment.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.installment.common.TenantEntity;

import java.time.Instant;

@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog extends TenantEntity {

    public enum Status { SENT, FAILED, SKIPPED }

    @Column(name = "event_key", nullable = false, length = 30)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "schedule_item_id")
    private Long scheduleItemId;

    @Column(nullable = false, length = 50)
    private String recipient;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(length = 500)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        assignTenant();
        createdAt = Instant.now();
    }
}

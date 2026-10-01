package uz.installment.collection;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "collection_actions")
@Getter
@Setter
@NoArgsConstructor
public class CollectionAction extends TenantEntity {

    public enum Type { CALL, SMS, VISIT, LETTER, LEGAL, NOTE }

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private Type actionType;

    @Column(length = 30)
    private String result;

    @Column(length = 1000)
    private String note;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Column(name = "promised_amount", precision = 18, scale = 2)
    private BigDecimal promisedAmount;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        assignTenant();
        createdAt = Instant.now();
    }
}

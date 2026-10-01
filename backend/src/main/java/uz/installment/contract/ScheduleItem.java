package uz.installment.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.installment.common.TenantEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "schedule_items")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleItem extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(nullable = false)
    private int seq;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "principal_part", nullable = false, precision = 18, scale = 2)
    private BigDecimal principalPart;

    @Column(name = "markup_part", nullable = false, precision = 18, scale = 2)
    private BigDecimal markupPart;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleStatus status = ScheduleStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    public BigDecimal remaining() {
        return amount.subtract(paidAmount);
    }

    public boolean isOverdue(LocalDate today) {
        return status != ScheduleStatus.PAID && dueDate.isBefore(today);
    }

    /** To'lov qo'shilgandan keyin holatni yangilaydi. */
    public void applyPayment(BigDecimal part, LocalDate today) {
        paidAmount = paidAmount.add(part);
        if (paidAmount.compareTo(amount) >= 0) {
            status = ScheduleStatus.PAID;
            paidAt = Instant.now();
        } else if (dueDate.isBefore(today)) {
            status = ScheduleStatus.LATE;
        } else {
            status = ScheduleStatus.PARTIAL;
        }
    }
}

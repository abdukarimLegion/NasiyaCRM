package uz.installment.warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.installment.common.AuditedEntity;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends AuditedEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    /** Buyurtmadan yetkazib berishgacha o'rtacha kun */
    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays = 7;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private boolean active = true;
}

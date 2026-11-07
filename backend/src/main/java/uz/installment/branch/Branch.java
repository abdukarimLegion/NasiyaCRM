package uz.installment.branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.installment.common.AuditedEntity;

import java.math.BigDecimal;

/** Filial (do'kon / savdo nuqtasi). Xodimlar va shartnomalar filialga bog'lanadi. */
@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
public class Branch extends AuditedEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(name = "manager_name", length = 150)
    private String managerName;

    @Column(name = "monthly_target", precision = 18, scale = 2)
    private BigDecimal monthlyTarget;

    @Column(nullable = false)
    private boolean active = true;
}

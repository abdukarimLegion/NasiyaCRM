package uz.installment.partner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.installment.common.AuditedEntity;

import java.math.BigDecimal;

@Entity
@Table(name = "partners")
@Getter
@Setter
@NoArgsConstructor
public class Partner extends AuditedEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "commission_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPct;

    @Column(name = "max_term", nullable = false)
    private int maxTerm;

    @Column(name = "min_down_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal minDownPct;

    @Column(name = "dti_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal dtiPct;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}

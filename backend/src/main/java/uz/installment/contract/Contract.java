package uz.installment.contract;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.installment.client.Client;
import uz.installment.common.AuditedEntity;
import uz.installment.product.Product;
import uz.installment.scoring.ScoringEngine.Decision;
import uz.installment.scoring.ScoringEngine.RiskCategory;
import uz.installment.security.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
public class Contract extends AuditedEntity {

    @Column(name = "contract_no", nullable = false, length = 30, updatable = false)
    private String contractNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "cost_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "down_payment", nullable = false, precision = 18, scale = 2)
    private BigDecimal downPayment;

    @Column(name = "markup_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal markupPct;

    @Column(name = "markup_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal markupAmount;

    @Column(name = "sale_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "installment_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal installmentTotal;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "monthly_payment", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(name = "first_due_date", nullable = false)
    private LocalDate firstDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status;

    @Column(name = "score_total")
    private Integer scoreTotal;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "risk_category", length = 1)
    private RiskCategory riskCategory;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Decision decision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_details", columnDefinition = "jsonb")
    private Map<String, Object> scoreDetails;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_id")
    private User officer;

    @Column(name = "guarantor_name", length = 150)
    private String guarantorName;

    @Column(name = "guarantor_phone", length = 20)
    private String guarantorPhone;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<ScheduleItem> schedule = new ArrayList<>();

    public void addScheduleItem(ScheduleItem item) {
        item.setContract(this);
        schedule.add(item);
    }

    /** Jadvalga qarab holatni qayta hisoblaydi (to'lov yoki kunlik job'dan keyin). */
    public void refreshStatus(LocalDate today) {
        if (status == ContractStatus.CANCELLED || status == ContractStatus.DRAFT) {
            return;
        }
        boolean allPaid = schedule.stream().allMatch(s -> s.getStatus() == ScheduleStatus.PAID);
        if (allPaid) {
            status = ContractStatus.CLOSED;
            if (closedAt == null) {
                closedAt = Instant.now();
            }
            return;
        }
        boolean anyLate = schedule.stream().anyMatch(s -> s.isOverdue(today));
        status = anyLate ? ContractStatus.LATE : ContractStatus.ACTIVE;
        closedAt = null;
    }
}

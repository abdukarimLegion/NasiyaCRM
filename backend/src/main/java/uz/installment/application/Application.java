package uz.installment.application;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.installment.branch.Branch;
import uz.installment.client.Client;
import uz.installment.common.AuditedEntity;
import uz.installment.contract.Contract;
import uz.installment.scoring.ScoringEngine.Decision;
import uz.installment.scoring.ScoringEngine.RiskCategory;
import uz.installment.security.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
public class Application extends AuditedEntity {

    @Column(name = "application_no", nullable = false, length = 30, updatable = false)
    private String applicationNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_id")
    private User officer;

    /** To'liq so'rov (CreateContractRequest) — shartnoma shu asosda tuziladi. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> request;

    @Column(name = "items_summary", nullable = false, length = 300)
    private String itemsSummary;

    @Column(name = "cost_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "down_payment", nullable = false, precision = 18, scale = 2)
    private BigDecimal downPayment;

    @Column(name = "markup_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal markupPct;

    @Column(name = "sale_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "installment_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal installmentTotal;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "monthly_payment", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyPayment;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;
}

package uz.installment.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.installment.common.AuditedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sud / MIB ishi. Da'vo summasi — faqat to'lanmagan savdo narxi:
 * murobaha qoidasiga ko'ra kechikish uchun foiz yoki jarima qo'shilmaydi.
 */
@Entity
@Table(name = "legal_cases")
@Getter
@Setter
@NoArgsConstructor
public class LegalCase extends AuditedEntity {

    /** Sudgacha → da'vo berildi → sud majlisi → qaror → MIB ijrosi → yopildi */
    public enum Status { PRETRIAL, FILED, HEARING, DECIDED, ENFORCEMENT, CLOSED }

    @Column(name = "contract_id", nullable = false, updatable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PRETRIAL;

    @Column(name = "claim_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal claimAmount;

    @Column(name = "court_name", length = 200)
    private String courtName;

    @Column(name = "case_no", length = 60)
    private String caseNo;

    @Column(name = "filed_at")
    private LocalDate filedAt;

    @Column(name = "hearing_at")
    private LocalDate hearingAt;

    @Column(name = "decision_at")
    private LocalDate decisionAt;

    @Column(name = "enforcement_no", length = 60)
    private String enforcementNo;

    @Column(length = 1000)
    private String note;

    @Column(name = "assigned_user_id")
    private Long assignedUserId;
}

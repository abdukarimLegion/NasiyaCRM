package uz.installment.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.installment.common.AuditedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client extends AuditedEntity {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 14)
    private String pinfl;

    @Column(name = "passport_series", length = 20)
    private String passportSeries;

    @Column(name = "passport_expiry")
    private LocalDate passportExpiry;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "extra_phone", length = 20)
    private String extraPhone;

    @Column(length = 100)
    private String region;

    @Column(length = 100)
    private String district;

    @Column(length = 300)
    private String address;

    @Column(length = 200)
    private String workplace;

    @Column(name = "monthly_income", precision = 18, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "family_status", length = 50)
    private String familyStatus;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(nullable = false)
    private boolean blacklisted;

    @Column(columnDefinition = "text")
    private String note;
}

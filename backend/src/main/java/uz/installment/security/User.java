package uz.installment.security;

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
import uz.installment.common.AuditedEntity;

import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditedEntity {

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** Shaxsiy rol (ruxsatlar to'plami). null — tizim roli ruxsatlari ishlatiladi. */
    @Column(name = "custom_role_id")
    private Long customRoleId;

    @Column(length = 14)
    private String pinfl;

    @Column(name = "passport_series", length = 20)
    private String passportSeries;

    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    @Column(name = "home_address", length = 300)
    private String homeAddress;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    /** Xodim o'zi nasiya olishi mumkinmi (standart: yo'q — komplayens cheklovi). */
    @Column(name = "allow_nasiya", nullable = false)
    private boolean allowNasiya = false;

    @Column(name = "nasiya_note", length = 300)
    private String nasiyaNote;

    /** Shaxsiy ruxsatlar. null — rolning ruxsatlari ishlatiladi. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", columnDefinition = "jsonb")
    private List<String> permissions;
}

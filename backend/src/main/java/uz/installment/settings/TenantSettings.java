package uz.installment.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Kompaniya (tenant) sozlamalari: brend, shartnoma prefiksi, yaxlitlash, limitlar. */
@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
@NoArgsConstructor
public class TenantSettings {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "brand_name", nullable = false, length = 100)
    private String brandName;

    @Column(name = "primary_color", nullable = false, length = 20)
    private String primaryColor;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "contract_prefix", nullable = false, length = 10)
    private String contractPrefix;

    @Column(name = "default_lang", nullable = false, length = 5)
    private String defaultLang;

    @Column(name = "rounding_step", nullable = false)
    private int roundingStep;

    @Column(name = "max_active_contracts", nullable = false)
    private int maxActiveContracts;

    @Column(name = "sms_sender", length = 20)
    private String smsSender;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

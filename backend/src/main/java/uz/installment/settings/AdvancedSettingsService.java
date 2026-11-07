package uz.installment.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.common.TenantContext;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Qo'shimcha sozlamalar: mijoz verifikatsiyasi qoidalari va moliyaviy parametrlar (OPEX, o'z kapitali,
 * jalb qilingan mablag' narxi). Asosiy {@link TenantSettings} dan alohida saqlanadi.
 */
@Service
@RequiredArgsConstructor
public class AdvancedSettingsService {

    /** NONE — talab yo'q; OTP / BIOMETRIC — bittasi majburiy; ANY — ikkalasidan biri; BOTH — ikkalasi. */
    public enum VerificationMode { NONE, OTP, BIOMETRIC, ANY, BOTH }

    /** MIB tekshiruvi: OFF — yo'q; OPTIONAL — xizmat ishlamasa davom etadi; REQUIRED — natijasiz ariza yuborilmaydi. */
    public enum MibMode { OFF, OPTIONAL, REQUIRED }

    public record Advanced(VerificationMode verificationMode, int otpExpirySeconds, int minFaceMatchPct,
                           BigDecimal monthlyOpex, BigDecimal ownCapital, BigDecimal fundingCostPct,
                           MibMode mibMode, int mibValidityDays) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Advanced get() {
        return jdbc.queryForObject("""
                select verification_mode, otp_expiry_seconds, min_face_match_pct, monthly_opex, own_capital,
                       funding_cost_pct, mib_mode, mib_validity_days from tenant_settings where tenant_id = :t
                """, Map.of("t", TenantContext.currentTenantId()), (rs, i) -> new Advanced(
                VerificationMode.valueOf(rs.getString("verification_mode")), rs.getInt("otp_expiry_seconds"),
                rs.getInt("min_face_match_pct"), rs.getBigDecimal("monthly_opex"), rs.getBigDecimal("own_capital"),
                rs.getBigDecimal("funding_cost_pct"), MibMode.valueOf(rs.getString("mib_mode")),
                rs.getInt("mib_validity_days")));
    }

    @Transactional
    public Advanced save(Advanced a) {
        jdbc.update("""
                update tenant_settings set verification_mode = :vm, otp_expiry_seconds = :oe,
                       min_face_match_pct = :fm, monthly_opex = :ox, own_capital = :oc, funding_cost_pct = :fc,
                       mib_mode = :mm, mib_validity_days = :mv,
                       updated_at = now()
                where tenant_id = :t
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("vm", a.verificationMode().name()).addValue("oe", a.otpExpirySeconds())
                .addValue("fm", a.minFaceMatchPct()).addValue("ox", a.monthlyOpex()).addValue("oc", a.ownCapital())
                .addValue("fc", a.fundingCostPct()).addValue("mm", a.mibMode().name())
                .addValue("mv", a.mibValidityDays()).addValue("t", TenantContext.currentTenantId()));
        return get();
    }
}

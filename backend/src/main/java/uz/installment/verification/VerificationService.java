package uz.installment.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.client.Client;
import uz.installment.client.ClientRepository;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.common.TenantContext;
import uz.installment.notification.SmsSender;
import uz.installment.settings.AdvancedSettingsService;
import uz.installment.settings.AdvancedSettingsService.VerificationMode;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mijozni tasdiqlash: SMS OTP (mijoz telefoniga kod yuboriladi) va biometrik natija (tashqi tizim — MyID,
 * Face ID — natijasi xodim tomonidan kiritiladi: moslik foizi va jonlilik). Arizada sozlamadagi
 * rejimga mos tasdiqlash bo'lishi shart.
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    /** Tasdiqlash arizaga shuncha vaqt ichida bog'lanishi kerak. */
    static final Duration VALID_FOR = Duration.ofMinutes(30);
    static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record OtpSent(Long id, String maskedPhone, Instant expiresAt, String devCode) {
    }

    public record Result(Long id, String method, String status, Integer matchPct, Instant verifiedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ClientRepository clients;
    private final SmsSender sms;
    private final PasswordEncoder encoder;
    private final AdvancedSettingsService settings;
    private final Environment env;
    private final Clock clock;

    @Transactional
    public OtpSent sendOtp(Long clientId, Long userId) {
        Client c = clients.findById(clientId).orElseThrow(() -> new NotFoundException("Mijoz", clientId));
        var cfg = settings.get();
        // Spam'ga qarshi: bir mijozga 1 daqiqada bir martadan ko'p kod yuborilmaydi
        Integer recent = jdbc.queryForObject("""
                select count(*) from client_verifications
                where client_id = :c and method = 'OTP' and created_at > :since
                """, Map.of("c", clientId, "since", java.sql.Timestamp.from(Instant.now(clock).minusSeconds(60))),
                Integer.class);
        if (recent != null && recent > 0) {
            throw new BusinessException("OTP_TOO_SOON", "Kod yaqinda yuborilgan, 1 daqiqadan keyin qayta urinib ko'ring");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expires = Instant.now(clock).plusSeconds(cfg.otpExpirySeconds());
        Long id = jdbc.queryForObject("""
                insert into client_verifications (tenant_id, client_id, method, status, phone, code_hash, expires_at, created_by)
                values (:t, :c, 'OTP', 'PENDING', :p, :h, :e, :u) returning id
                """, new MapSqlParameterSource().addValue("t", TenantContext.currentTenantId())
                .addValue("c", clientId).addValue("p", c.getPhone()).addValue("h", encoder.encode(code))
                .addValue("e", java.sql.Timestamp.from(expires)).addValue("u", userId), Long.class);
        var res = sms.send(c.getPhone(), "Tasdiqlash kodi: " + code + ". Kodni hech kimga bermang.");
        if (!res.success()) {
            throw new BusinessException("SMS_FAILED", "SMS yuborilmadi: " + res.error());
        }
        // Faqat dev profilida kod javobda qaytariladi (SMS provayder yo'q muhitda sinash uchun)
        String dev = env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev")) ? code : null;
        return new OtpSent(id, mask(c.getPhone()), expires, dev);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public Result confirmOtp(Long id, String code) {
        Map<String, Object> row = jdbc.queryForMap("""
                select id, status, code_hash, expires_at, attempts from client_verifications
                where id = :id and method = 'OTP' for update
                """, Map.of("id", id));
        String status = (String) row.get("status");
        if (!"PENDING".equals(status)) {
            throw new BusinessException("OTP_STATE", "Bu kod allaqachon ishlatilgan yoki muddati tugagan");
        }
        Instant exp = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        int attempts = (Integer) row.get("attempts");
        if (Instant.now(clock).isAfter(exp)) {
            jdbc.update("update client_verifications set status = 'EXPIRED' where id = :id", Map.of("id", id));
            throw new BusinessException("OTP_EXPIRED", "Kod muddati tugagan, yangi kod yuboring");
        }
        if (!encoder.matches(code == null ? "" : code.trim(), (String) row.get("code_hash"))) {
            boolean lock = attempts + 1 >= MAX_ATTEMPTS;
            jdbc.update("update client_verifications set attempts = attempts + 1, status = :s where id = :id",
                    Map.of("id", id, "s", lock ? "FAILED" : "PENDING"));
            throw new BusinessException("OTP_WRONG", lock ? "Urinishlar tugadi, yangi kod yuboring" : "Kod noto'g'ri");
        }
        jdbc.update("update client_verifications set status = 'VERIFIED', verified_at = now(), attempts = attempts + 1 where id = :id",
                Map.of("id", id));
        return load(id);
    }

    /**
     * Tashqi biometrik tizim natijasini yozadi. Moslik foizi sozlamadagi minimumdan past yoki jonlilik
     * tekshiruvi o'tmagan bo'lsa — FAILED.
     */
    @Transactional
    public Result recordBiometric(Long clientId, int matchPct, boolean livenessPassed, String reference, Long userId) {
        clients.findById(clientId).orElseThrow(() -> new NotFoundException("Mijoz", clientId));
        boolean ok = livenessPassed && matchPct >= settings.get().minFaceMatchPct();
        Long id = jdbc.queryForObject("""
                insert into client_verifications (tenant_id, client_id, method, status, match_pct, reference, verified_at, created_by)
                values (:t, :c, 'BIOMETRIC', :s, :m, :r, case when :ok then now() end, :u) returning id
                """, new MapSqlParameterSource().addValue("t", TenantContext.currentTenantId())
                .addValue("c", clientId).addValue("s", ok ? "VERIFIED" : "FAILED").addValue("m", matchPct)
                .addValue("r", reference).addValue("ok", ok).addValue("u", userId), Long.class);
        return load(id);
    }

    /** Mijozning oxirgi 30 daqiqadagi tasdiqlangan usullari. */
    @Transactional(readOnly = true)
    public Set<String> recentMethods(Long clientId) {
        return new TreeSet<>(jdbc.queryForList("""
                select distinct method from client_verifications
                where client_id = :c and status = 'VERIFIED' and verified_at > :since
                """, Map.of("c", clientId, "since", java.sql.Timestamp.from(Instant.now(clock).minus(VALID_FOR))),
                String.class));
    }

    /**
     * Sozlamadagi rejim talab qilgan tasdiqlash bormi? Bor bo'lsa — usullar ro'yxatini qaytaradi (arizaga yoziladi).
     */
    @Transactional(readOnly = true)
    public String requireFor(Long clientId) {
        VerificationMode mode = settings.get().verificationMode();
        Set<String> got = recentMethods(clientId);
        boolean ok = switch (mode) {
            case NONE -> true;
            case OTP -> got.contains("OTP");
            case BIOMETRIC -> got.contains("BIOMETRIC");
            case ANY -> !got.isEmpty();
            case BOTH -> got.contains("OTP") && got.contains("BIOMETRIC");
        };
        if (!ok) {
            throw new BusinessException("VERIFICATION_REQUIRED",
                    "Mijoz tasdiqlanmagan: talab — " + mode + " (oxirgi 30 daqiqa ichida)");
        }
        return got.isEmpty() ? null : String.join(",", got);
    }

    @Transactional(readOnly = true)
    public List<Result> history(Long clientId) {
        List<Result> out = new ArrayList<>();
        jdbc.query("""
                select id, method, status, match_pct, verified_at from client_verifications
                where client_id = :c order by id desc limit 20
                """, Map.of("c", clientId), rs -> {
            out.add(new Result(rs.getLong("id"), rs.getString("method"), rs.getString("status"),
                    (Integer) rs.getObject("match_pct"),
                    rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant()));
        });
        return out;
    }

    private Result load(Long id) {
        return jdbc.queryForObject("""
                select id, method, status, match_pct, verified_at from client_verifications where id = :id
                """, Map.of("id", id), (rs, i) -> new Result(rs.getLong("id"), rs.getString("method"),
                rs.getString("status"), (Integer) rs.getObject("match_pct"),
                rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant()));
    }

    static String mask(String phone) {
        if (phone == null || phone.length() < 6) {
            return "***";
        }
        return phone.substring(0, phone.length() - 5) + "***" + phone.substring(phone.length() - 2);
    }
}

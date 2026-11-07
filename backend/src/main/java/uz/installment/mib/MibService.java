package uz.installment.mib;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import uz.installment.audit.AuditService;
import uz.installment.client.Client;
import uz.installment.client.ClientRepository;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.common.TenantContext;
import uz.installment.settings.AdvancedSettingsService;
import uz.installment.settings.AdvancedSettingsService.MibMode;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MIB tekshiruvi: JShShIR bo'yicha qarzdorlikni aniqlaydi, natijani saqlaydi va skoringga (stop-faktor MIB_DEBT) uzatadi.
 * Tekshiruv natijasi sozlamadagi muddat (standart 7 kun) davomida amal qiladi; muddati o'tsa, ariza yuborilganda
 * qayta tekshiriladi. Natija mijozning rozilik/ma'lumot himoyasi qoidalariga muvofiq ishlatilishi kerak.
 */
@Service
@RequiredArgsConstructor
public class MibService {

    public record Check(Long id, String status, String source, BigDecimal totalDebt, int caseCount,
                        List<Map<String, Object>> cases, String message, String checkedBy, Instant createdAt) {
    }

    /** latestDecisive — oxirgi aniq (CLEAR/DEBT) natija; fresh — amal qilish muddati o'tmaganmi. */
    public record State(MibMode mode, int validityDays, String provider, boolean providerConfigured,
                        Check latest, Check decisive, boolean fresh, boolean needsCheck, List<Check> history) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ClientRepository clients;
    private final MibClient client;
    private final AdvancedSettingsService settings;
    private final AuditService audit;
    private final PlatformTransactionManager txm;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional(readOnly = true)
    public State state(Long clientId) {
        clients.findById(clientId).orElseThrow(() -> new NotFoundException("Mijoz", clientId));
        var cfg = settings.get();
        List<Check> history = history(clientId, 10);
        Check latest = history.isEmpty() ? null : history.get(0);
        Check decisive = history.stream().filter(c -> !"ERROR".equals(c.status())).findFirst().orElse(null);
        boolean fresh = decisive != null && isFresh(decisive, cfg.mibValidityDays());
        return new State(cfg.mibMode(), cfg.mibValidityDays(), client.name(), client.isConfigured(), latest, decisive,
                fresh, cfg.mibMode() != MibMode.OFF && !fresh, history);
    }

    /** MIB xizmatiga so'rov yuboradi va natijani alohida tranzaksiyada saqlaydi (chaqiruvchi rollback qilsa ham qoladi). */
    public Check runCheck(Long clientId, Long userId) {
        Client c = clients.findById(clientId).orElseThrow(() -> new NotFoundException("Mijoz", clientId));
        MibOutcome out = client.check(c.getPinfl());
        TransactionTemplate tx = new TransactionTemplate(txm);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Check saved = tx.execute(s -> save(c, out, "API", userId));
        audit.record("MIB_CHECK", "CLIENT", clientId, Map.of("status", saved.status(), "source", "API",
                "provider", client.name()));
        return saved;
    }

    /**
     * Qo'lda kiritish: xodim MIB portalini ko'rib natijani yozadi (avtomatik xizmat ishlamaganda).
     * Faqat tasdiqlash huquqi bor xodimga ruxsat beriladi (controller).
     */
    @Transactional
    public Check recordManual(Long clientId, boolean hasDebt, BigDecimal totalDebt, int caseCount, String note,
                              Long userId) {
        Client c = clients.findById(clientId).orElseThrow(() -> new NotFoundException("Mijoz", clientId));
        BigDecimal amount = hasDebt && totalDebt != null ? totalDebt.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        MibOutcome out = hasDebt
                ? new MibOutcome(MibOutcome.Status.DEBT, amount, Math.max(caseCount, 1), List.of(), note)
                : MibOutcome.clear(note);
        Check saved = save(c, out, "MANUAL", userId);
        audit.record("MIB_CHECK", "CLIENT", clientId, Map.of("status", saved.status(), "source", "MANUAL"));
        return saved;
    }

    /**
     * Ariza yuborishdan oldin: rejim OFF bo'lmasa va amaldagi natija bo'lmasa — MIB'dan so'raydi.
     * REQUIRED rejimda natija olinmasa (xizmat ishlamasa) ariza yuborilmaydi; OPTIONAL'da davom etadi.
     */
    public void ensureForApplication(Client c, Long userId) {
        var cfg = settings.get();
        if (cfg.mibMode() == MibMode.OFF) {
            return;
        }
        if (decisiveFresh(c.getId(), cfg.mibValidityDays()).isPresent()) {
            return;
        }
        Check res = runCheck(c.getId(), userId);
        if ("ERROR".equals(res.status()) && cfg.mibMode() == MibMode.REQUIRED) {
            throw new BusinessException("MIB_UNAVAILABLE", "MIB tekshiruvi o'tmadi: " + java.util.Objects.toString(res.message(), "xizmat javob bermadi").replaceAll("[.\\s]+$", "")
                    + ". Natijani tasdiqlash huquqi bor xodim qo'lda kiritishi kerak.");
        }
    }

    /** Skoring uchun: amaldagi natija "qarz bor" bo'lsa true (rejim OFF bo'lsa doim false). */
    @Transactional(readOnly = true)
    public boolean hasFreshDebt(Long clientId) {
        var cfg = settings.get();
        if (cfg.mibMode() == MibMode.OFF) {
            return false;
        }
        return decisiveFresh(clientId, cfg.mibValidityDays()).map(c -> "DEBT".equals(c.status())).orElse(false);
    }

    private Optional<Check> decisiveFresh(Long clientId, int validityDays) {
        return history(clientId, 10).stream().filter(c -> !"ERROR".equals(c.status())).findFirst()
                .filter(c -> isFresh(c, validityDays));
    }

    private boolean isFresh(Check c, int validityDays) {
        return c.createdAt().isAfter(Instant.now(clock).minusSeconds(validityDays * 86_400L));
    }

    private Check save(Client c, MibOutcome out, String source, Long userId) {
        String casesJson = null;
        try {
            casesJson = out.cases() == null || out.cases().isEmpty() ? null : json.writeValueAsString(out.cases());
        } catch (JsonProcessingException e) {
            // ishlar ro'yxati saqlanmasa ham asosiy natija saqlanadi
        }
        Long id = jdbc.queryForObject("""
                insert into mib_checks (tenant_id, client_id, pinfl, status, source, total_debt, case_count, cases, message, checked_by)
                values (:t, :c, :p, :s, :src, :d, :n, cast(:cases as jsonb), :m, :u) returning id
                """, new MapSqlParameterSource().addValue("t", TenantContext.currentTenantId())
                .addValue("c", c.getId()).addValue("p", c.getPinfl()).addValue("s", out.status().name())
                .addValue("src", source).addValue("d", out.totalDebt()).addValue("n", out.caseCount())
                .addValue("cases", casesJson).addValue("m", out.message()).addValue("u", userId), Long.class);
        return history(c.getId(), 1).stream().filter(h -> h.id().equals(id)).findFirst().orElseThrow();
    }

    private List<Check> history(Long clientId, int limit) {
        List<Check> out = new ArrayList<>();
        jdbc.query("""
                select m.id, m.status, m.source, m.total_debt, m.case_count, m.cases::text as cases, m.message,
                       m.created_at, u.full_name
                from mib_checks m left join users u on u.id = m.checked_by
                where m.client_id = :c order by m.created_at desc, m.id desc limit :l
                """, Map.of("c", clientId, "l", limit), rs -> {
            List<Map<String, Object>> cases = List.of();
            String raw = rs.getString("cases");
            if (raw != null) {
                try {
                    cases = json.readValue(raw, new TypeReference<>() {
                    });
                } catch (JsonProcessingException e) {
                    cases = List.of();
                }
            }
            Timestamp ts = rs.getTimestamp("created_at");
            out.add(new Check(rs.getLong("id"), rs.getString("status"), rs.getString("source"),
                    rs.getBigDecimal("total_debt"), rs.getInt("case_count"), cases, rs.getString("message"),
                    rs.getString("full_name"), ts.toInstant()));
        });
        return out;
    }
}

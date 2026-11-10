package uz.installment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.NotFoundException;
import uz.installment.common.TenantContext;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ariza qoralamalari: operator konveyerning istalgan qadamida ishni saqlab, keyin davom ettiradi.
 * payload — konveyer holati (tanlangan mijoz, skoring javoblari, savatcha, shartlar); server uni
 * tahlil qilmaydi, ariza yuborilganda odatdagi tekshiruvlardan o'tadi.
 */
@RestController
@RequestMapping("/api/applications/drafts")
@PreAuthorize("hasAuthority('CONTRACTS_CREATE')")
@RequiredArgsConstructor
public class ApplicationDraftController {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public record DraftRequest(@NotNull Long clientId, @Min(0) @Max(10) int step, @Size(max = 300) String summary,
                               @PositiveOrZero BigDecimal amount, @NotNull Map<String, Object> payload) {
    }

    public record Draft(Long id, Long clientId, String clientName, String clientPhone, String clientPinfl, int step,
                        String summary, BigDecimal amount, Long officerId, String officerName, Long branchId,
                        String branchName, Instant createdAt, Instant updatedAt, Map<String, Object> payload) {
    }

    private static final String SELECT = """
            select d.id, d.client_id, c.full_name as client_name, c.phone as client_phone, c.pinfl as client_pinfl,
                   d.step, d.summary, d.amount, d.officer_id, u.full_name as officer_name, d.branch_id,
                   b.name as branch_name, d.created_at, d.updated_at, d.payload::text as payload
            from application_drafts d
            join clients c on c.id = d.client_id
            join users u on u.id = d.officer_id
            left join branches b on b.id = d.branch_id
            where d.tenant_id = :t
            """;

    @GetMapping
    public List<Draft> list(@RequestParam(required = false) Long branchId, @AuthenticationPrincipal AuthUser me) {
        return jdbc.query(SELECT + " and (cast(:b as bigint) is null or d.branch_id = :b) order by d.updated_at desc limit 200",
                params().addValue("b", me.branchScope(branchId)), (rs, i) -> map(rs, false));
    }

    @GetMapping("/{id}")
    public Draft get(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return load(id, me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Draft create(@Valid @RequestBody DraftRequest r, @AuthenticationPrincipal AuthUser me) {
        Long id = jdbc.queryForObject("""
                insert into application_drafts (tenant_id, client_id, officer_id, branch_id, step, summary, amount, payload)
                values (:t, :c, :u, :b, :step, :summary, :amount, cast(:p as jsonb)) returning id
                """, values(r).addValue("u", me.id()).addValue("b", me.branchId()), Long.class);
        return load(id, me);
    }

    @PutMapping("/{id}")
    @Transactional
    public Draft update(@PathVariable Long id, @Valid @RequestBody DraftRequest r, @AuthenticationPrincipal AuthUser me) {
        load(id, me);
        jdbc.update("""
                update application_drafts set client_id = :c, step = :step, summary = :summary, amount = :amount,
                       payload = cast(:p as jsonb), updated_at = now()
                where id = :id and tenant_id = :t
                """, values(r).addValue("id", id));
        return load(id, me);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        load(id, me);
        jdbc.update("delete from application_drafts where id = :id and tenant_id = :t", params().addValue("id", id));
    }

    /** Boshqa filial qoralamasi — faqat ALL_BRANCHES ruxsati bilan. */
    private Draft load(Long id, AuthUser me) {
        Draft d = jdbc.query(SELECT + " and d.id = :id", params().addValue("id", id), (rs, i) -> map(rs, true))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Qoralama", id));
        Long scope = me.branchScope(null);
        if (scope != null && !scope.equals(d.branchId())) {
            throw new NotFoundException("Qoralama", id);
        }
        return d;
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource("t", TenantContext.currentTenantId());
    }

    private MapSqlParameterSource values(DraftRequest r) {
        try {
            return params().addValue("c", r.clientId()).addValue("step", r.step())
                    .addValue("summary", r.summary()).addValue("amount", r.amount())
                    .addValue("p", json.writeValueAsString(r.payload()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Qoralama ma'lumoti noto'g'ri");
        }
    }

    private Draft map(ResultSet rs, boolean withPayload) throws SQLException {
        Map<String, Object> payload = null;
        if (withPayload) {
            try {
                payload = json.readValue(rs.getString("payload"), new TypeReference<>() {
                });
            } catch (JsonProcessingException e) {
                payload = Map.of();
            }
        }
        return new Draft(rs.getLong("id"), rs.getLong("client_id"), rs.getString("client_name"),
                rs.getString("client_phone"), rs.getString("client_pinfl"), rs.getInt("step"),
                rs.getString("summary"), rs.getBigDecimal("amount"), rs.getLong("officer_id"),
                rs.getString("officer_name"), (Long) rs.getObject("branch_id", Long.class),
                rs.getString("branch_name"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), payload);
    }
}

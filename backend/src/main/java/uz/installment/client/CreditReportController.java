package uz.installment.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.NotFoundException;
import uz.installment.common.TenantContext;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Mijozning kredit tarixi (KATM yoki boshqa kredit byurosi hisobotidan). Byuro bilan avtomatik integratsiya yo'q:
 * xodim hisobot ma'lumotlarini qo'lda kiritadi; oxirgi yozuv mijoz kartasida va nasiya wizard'ida ko'rinadi.
 */
@RestController
@RequestMapping("/api/clients/{clientId}/credit-reports")
@RequiredArgsConstructor
public class CreditReportController {

    private final NamedParameterJdbcTemplate jdbc;
    private final ClientRepository clients;

    public record Report(Long id, String source, LocalDate reportDate, Integer score, String rating, int activeLoans,
                         BigDecimal totalDebt, BigDecimal monthlyObligation, boolean overdueFlag, int maxOverdueDays,
                         String note, String enteredBy, Instant createdAt) {
    }

    public record ReportRequest(@Size(max = 30) String source, @NotNull LocalDate reportDate,
                                @Min(0) @Max(1000) Integer score, @Pattern(regexp = "^[A-E]$") String rating,
                                @Min(0) int activeLoans, @NotNull @PositiveOrZero BigDecimal totalDebt,
                                @NotNull @PositiveOrZero BigDecimal monthlyObligation, boolean overdueFlag,
                                @Min(0) int maxOverdueDays, @Size(max = 500) String note) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENTS_VIEW')")
    @Transactional(readOnly = true)
    public List<Report> list(@PathVariable Long clientId) {
        return jdbc.query("""
                select r.*, u.full_name from credit_reports r left join users u on u.id = r.entered_by
                where r.client_id = :c order by r.report_date desc, r.id desc limit 20
                """, Map.of("c", clientId), (rs, i) -> new Report(rs.getLong("id"), rs.getString("source"),
                rs.getDate("report_date").toLocalDate(), (Integer) rs.getObject("score"), rs.getString("rating"),
                rs.getInt("active_loans"), rs.getBigDecimal("total_debt"), rs.getBigDecimal("monthly_obligation"),
                rs.getBoolean("overdue_flag"), rs.getInt("max_overdue_days"), rs.getString("note"),
                rs.getString("full_name"), rs.getTimestamp("created_at").toInstant()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENTS_EDIT')")
    @Transactional
    public Report add(@PathVariable Long clientId, @Valid @RequestBody ReportRequest r,
                      @AuthenticationPrincipal AuthUser me) {
        clients.findById(clientId).orElseThrow(() -> new NotFoundException("Mijoz", clientId));
        jdbc.update("""
                insert into credit_reports (tenant_id, client_id, source, report_date, score, rating, active_loans,
                    total_debt, monthly_obligation, overdue_flag, max_overdue_days, note, entered_by)
                values (:t, :c, :s, :d, :sc, :rt, :al, :td, :mo, :of, :md, :n, :u)
                """, new MapSqlParameterSource().addValue("t", TenantContext.currentTenantId()).addValue("c", clientId)
                .addValue("s", r.source() == null || r.source().isBlank() ? "KATM" : r.source().trim())
                .addValue("d", r.reportDate()).addValue("sc", r.score()).addValue("rt", r.rating())
                .addValue("al", r.activeLoans()).addValue("td", r.totalDebt()).addValue("mo", r.monthlyObligation())
                .addValue("of", r.overdueFlag()).addValue("md", r.maxOverdueDays()).addValue("n", r.note())
                .addValue("u", me.id()));
        return list(clientId).get(0);
    }
}

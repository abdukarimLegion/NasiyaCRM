package uz.installment.client;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Kredit tarixi monitoringi: har bir mijozning oxirgi kredit byurosi (KATM) hisoboti, ichki nasiya yuki va
 * qarz yuki ko'rsatkichi (DTI = (tashqi oylik to'lov + ichki oylik to'lov) / oylik daromad).
 */
@RestController
@RequestMapping("/api/clients/credit-monitor")
@PreAuthorize("hasAuthority('CLIENTS_VIEW')")
@RequiredArgsConstructor
public class CreditMonitorController {

    /** Qarz yuki me'yori: oylik to'lovlar daromadning shu foizidan oshmasligi kerak. */
    static final int DTI_NORM_PCT = 50;

    private final NamedParameterJdbcTemplate jdbc;

    public record Row(Long clientId, String fullName, String pinfl, String phone, boolean blacklisted,
                      BigDecimal monthlyIncome, LocalDate reportDate, String source, Integer score, String rating,
                      int activeLoans, BigDecimal externalDebt, BigDecimal externalMonthly, BigDecimal internalMonthly,
                      BigDecimal internalDebt, BigDecimal dtiPct, boolean overdueFlag, int maxOverdueDays, String note) {
    }

    public record Monitor(int dtiNormPct, long clients, BigDecimal avgScore, BigDecimal avgDtiPct, long cleanClients,
                          long flaggedClients, List<Row> rows) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Monitor get() {
        List<Row> rows = jdbc.query("""
                select c.id, c.full_name, c.pinfl, c.phone, c.blacklisted, c.monthly_income,
                       r.report_date, r.source, r.score, r.rating, r.active_loans, r.total_debt, r.monthly_obligation,
                       r.overdue_flag, r.max_overdue_days, r.note,
                       coalesce((select sum(k.monthly_payment) from contracts k
                                  where k.client_id = c.id and k.status in ('ACTIVE','LATE')), 0) as internal_monthly,
                       coalesce((select sum(s.amount - s.paid_amount) from schedule_items s join contracts k on k.id = s.contract_id
                                  where k.client_id = c.id and k.status in ('ACTIVE','LATE')), 0) as internal_debt
                from clients c
                join lateral (select * from credit_reports cr where cr.client_id = c.id
                              order by cr.report_date desc, cr.id desc limit 1) r on true
                where c.tenant_id = :t
                order by r.overdue_flag desc, r.max_overdue_days desc, c.full_name
                limit 500
                """, Map.of("t", TenantContext.currentTenantId()), (rs, i) -> {
            BigDecimal income = rs.getBigDecimal("monthly_income");
            BigDecimal ext = rs.getBigDecimal("monthly_obligation");
            BigDecimal internal = rs.getBigDecimal("internal_monthly");
            BigDecimal dti = income == null || income.signum() <= 0 ? null
                    : ext.add(internal).multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP);
            return new Row(rs.getLong("id"), rs.getString("full_name"), rs.getString("pinfl"), rs.getString("phone"),
                    rs.getBoolean("blacklisted"), income, rs.getObject("report_date", LocalDate.class),
                    rs.getString("source"), (Integer) rs.getObject("score"), rs.getString("rating"),
                    rs.getInt("active_loans"), rs.getBigDecimal("total_debt"), ext, internal,
                    rs.getBigDecimal("internal_debt"), dti, rs.getBoolean("overdue_flag"),
                    rs.getInt("max_overdue_days"), rs.getString("note"));
        });
        List<Integer> scores = rows.stream().map(Row::score).filter(s -> s != null).toList();
        List<BigDecimal> dtis = rows.stream().map(Row::dtiPct).filter(d -> d != null).toList();
        BigDecimal avgScore = scores.isEmpty() ? null : BigDecimal.valueOf(scores.stream().mapToInt(Integer::intValue)
                .average().orElse(0)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal avgDti = dtis.isEmpty() ? null : dtis.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dtis.size()), 1, RoundingMode.HALF_UP);
        long clean = rows.stream().filter(r -> !r.overdueFlag() && r.maxOverdueDays() == 0 && !r.blacklisted()).count();
        long flagged = rows.stream().filter(r -> r.blacklisted() || r.overdueFlag()
                || (r.dtiPct() != null && r.dtiPct().compareTo(BigDecimal.valueOf(DTI_NORM_PCT)) > 0)).count();
        return new Monitor(DTI_NORM_PCT, rows.size(), avgScore, avgDti, clean, flagged, rows);
    }
}

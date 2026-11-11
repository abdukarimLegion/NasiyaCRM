package uz.installment.collection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.contract.Contract;
import uz.installment.contract.ContractRepository;
import uz.installment.contract.ContractService;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Sud va MIB jarayoni (Legal bosqichi). */
@RestController
@RequestMapping("/api/collection/legal")
@PreAuthorize("hasAuthority('COLLECTION_VIEW')")
@RequiredArgsConstructor
public class LegalController {

    private final LegalCaseRepository cases;
    private final ContractRepository contracts;
    private final CollectionActionRepository actions;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final CourtFilingGateway courtFiling;

    public record CreateCase(@NotNull Long contractId, @Size(max = 200) String courtName,
                             @Size(max = 1000) String note) {
    }

    public record UpdateCase(@NotNull LegalCase.Status status, @Size(max = 200) String courtName,
                             @Size(max = 60) String caseNo, LocalDate filedAt, LocalDate hearingAt,
                             LocalDate decisionAt, @Size(max = 60) String enforcementNo,
                             @Size(max = 1000) String note, Long assignedUserId) {
    }

    /** remaining — hozirgi qolgan qarz; paidSinceFiled — ish ochilgandan beri tushgan to'lovlar. */
    public record CaseRow(Long id, Long contractId, String contractNo, Long clientId, String clientName,
                          String phone, String pinfl, LegalCase.Status status, BigDecimal claimAmount,
                          BigDecimal remaining, BigDecimal paidSinceOpened, int daysLate, String courtName,
                          String caseNo, LocalDate filedAt, LocalDate hearingAt, LocalDate decisionAt,
                          String enforcementNo, String note, Long assignedUserId, String assignedUserName,
                          String branchName, Instant createdAt, Instant updatedAt, BigDecimal stateFee,
                          LocalDate arrestRequestedAt, String arrestNote, Instant esudSubmittedAt, String esudRef) {
    }

    /** Da'vo bo'yicha davlat boji stavkasi (da'vo summasidan). */
    static final BigDecimal STATE_FEE_RATE = new BigDecimal("0.04");

    public record ArrestRequest(@Size(max = 500) String note) {
    }

    public record EsudResult(String reference, boolean mock, String message) {
    }

    @GetMapping
    public List<CaseRow> list(@RequestParam(defaultValue = "false") boolean includeClosed,
                              @RequestParam(required = false) Long branchId,
                              @AuthenticationPrincipal AuthUser me) {
        var p = new MapSqlParameterSource().addValue("today", LocalDate.now(clock))
                .addValue("closed", includeClosed).addValue("branch", me.branchScope(branchId));
        return jdbc.query("""
                select lc.*, c.contract_no, cl.id as client_id, cl.full_name, cl.phone, cl.pinfl,
                       b.name as branch_name, u.full_name as assigned_name,
                       (select coalesce(sum(s.amount - s.paid_amount), 0) from schedule_items s
                         where s.contract_id = c.id) as remaining,
                       coalesce((select :today::date - min(s.due_date) from schedule_items s
                                  where s.contract_id = c.id and s.status <> 'PAID' and s.due_date < :today::date), 0) as days_late,
                       coalesce((select sum(py.amount) from payments py
                                  where py.contract_id = c.id and py.paid_at >= lc.created_at), 0) as paid_since
                from legal_cases lc
                join contracts c on c.id = lc.contract_id
                join clients cl on cl.id = c.client_id
                left join branches b on b.id = c.branch_id
                left join users u on u.id = lc.assigned_user_id
                where (:closed or lc.status <> 'CLOSED')
                  and (cast(:branch as bigint) is null or c.branch_id = :branch)
                order by case lc.status when 'CLOSED' then 1 else 0 end, lc.updated_at desc
                """, p, (rs, i) -> new CaseRow(rs.getLong("id"), rs.getLong("contract_id"),
                rs.getString("contract_no"), rs.getLong("client_id"), rs.getString("full_name"),
                rs.getString("phone"), rs.getString("pinfl"), LegalCase.Status.valueOf(rs.getString("status")),
                rs.getBigDecimal("claim_amount"), rs.getBigDecimal("remaining"), rs.getBigDecimal("paid_since"),
                rs.getInt("days_late"), rs.getString("court_name"), rs.getString("case_no"),
                rs.getObject("filed_at", LocalDate.class), rs.getObject("hearing_at", LocalDate.class),
                rs.getObject("decision_at", LocalDate.class), rs.getString("enforcement_no"), rs.getString("note"),
                rs.getObject("assigned_user_id", Long.class), rs.getString("assigned_name"),
                rs.getString("branch_name"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getBigDecimal("claim_amount").multiply(STATE_FEE_RATE).setScale(0, java.math.RoundingMode.HALF_UP),
                rs.getObject("arrest_requested_at", LocalDate.class), rs.getString("arrest_note"),
                rs.getTimestamp("esud_submitted_at") == null ? null : rs.getTimestamp("esud_submitted_at").toInstant(),
                rs.getString("esud_ref")));
    }

    /** Sud ishini ochish: da'vo summasi — hozirgi qolgan qarz (jarimasiz). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COLLECTION_LEGAL')")
    @Transactional
    public LegalCase open(@Valid @RequestBody CreateCase req, @AuthenticationPrincipal AuthUser me) {
        Contract c = contracts.findWithSchedule(req.contractId())
                .orElseThrow(() -> new NotFoundException("Shartnoma", req.contractId()));
        ContractService.checkScope(c, me);
        if (cases.findFirstByContractIdAndStatusNot(c.getId(), LegalCase.Status.CLOSED).isPresent()) {
            throw new BusinessException("LEGAL_CASE_EXISTS", "Bu shartnoma bo'yicha ochiq sud ishi bor");
        }
        BigDecimal remaining = remaining(c);
        if (remaining.signum() <= 0) {
            throw new BusinessException("NO_DEBT", "Shartnoma bo'yicha qarz yo'q");
        }
        LegalCase lc = new LegalCase();
        lc.setContractId(c.getId());
        lc.setClaimAmount(remaining);
        lc.setCourtName(blank(req.courtName()));
        lc.setNote(blank(req.note()));
        lc.setAssignedUserId(me.id());
        LegalCase saved = cases.save(lc);
        log(c.getId(), "PRETRIAL", req.note(), me);
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COLLECTION_LEGAL')")
    @Transactional
    public LegalCase update(@PathVariable Long id, @Valid @RequestBody UpdateCase req,
                            @AuthenticationPrincipal AuthUser me) {
        LegalCase lc = cases.findById(id).orElseThrow(() -> new NotFoundException("Sud ishi", id));
        Contract c = contracts.findWithSchedule(lc.getContractId()).orElseThrow();
        ContractService.checkScope(c, me);
        LegalCase.Status prev = lc.getStatus();
        // Da'vo berilganda summa shu kundagi qoldiq bo'yicha yangilanadi
        if (prev == LegalCase.Status.PRETRIAL && req.status() == LegalCase.Status.FILED) {
            lc.setClaimAmount(remaining(c));
        }
        lc.setStatus(req.status());
        lc.setCourtName(blank(req.courtName()));
        lc.setCaseNo(blank(req.caseNo()));
        lc.setFiledAt(req.filedAt());
        lc.setHearingAt(req.hearingAt());
        lc.setDecisionAt(req.decisionAt());
        lc.setEnforcementNo(blank(req.enforcementNo()));
        lc.setNote(blank(req.note()));
        lc.setAssignedUserId(req.assignedUserId());
        if (prev != req.status()) {
            log(c.getId(), req.status().name(), req.note(), me);
        }
        return lc;
    }

    /** Qarzdorning bank hisoblariga taqiq (arest) qo'yish so'rovi yuborilganini qayd etadi. */
    @PostMapping("/{id}/arrest")
    @PreAuthorize("hasAuthority('COLLECTION_LEGAL')")
    @Transactional
    public void arrest(@PathVariable Long id, @Valid @RequestBody ArrestRequest req, @AuthenticationPrincipal AuthUser me) {
        LegalCase lc = scopedCase(id, me);
        jdbc.update("update legal_cases set arrest_requested_at = :d, arrest_note = :n, updated_at = now() where id = :id",
                new MapSqlParameterSource().addValue("d", LocalDate.now(clock)).addValue("n", blank(req.note()))
                        .addValue("id", id));
        log(lc.getContractId(), "ARREST", req.note(), me);
    }

    /** Da'vo arizasini elektron sud (E-SUD) orqali topshiradi — hozircha {@link MockCourtFilingGateway}. */
    @PostMapping("/{id}/esud")
    @PreAuthorize("hasAuthority('COLLECTION_LEGAL')")
    @Transactional
    public EsudResult esud(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        LegalCase lc = scopedCase(id, me);
        Contract c = contracts.findWithSchedule(lc.getContractId()).orElseThrow();
        CourtFilingGateway.Filing f = courtFiling.submit(c.getContractNo(), c.getClient().getPinfl(),
                lc.getClaimAmount(), lc.getCourtName());
        if (!f.accepted()) {
            throw new BusinessException("ESUD_FAILED", f.message() == null ? "E-SUD arizani qabul qilmadi" : f.message());
        }
        jdbc.update("update legal_cases set esud_submitted_at = now(), esud_ref = :r, updated_at = now() where id = :id",
                new MapSqlParameterSource().addValue("r", f.reference()).addValue("id", id));
        log(lc.getContractId(), "ESUD", f.reference(), me);
        return new EsudResult(f.reference(), courtFiling.isMock(), f.message());
    }

    private LegalCase scopedCase(Long id, AuthUser me) {
        LegalCase lc = cases.findById(id).orElseThrow(() -> new NotFoundException("Sud ishi", id));
        Contract c = contracts.findWithSchedule(lc.getContractId()).orElseThrow();
        ContractService.checkScope(c, me);
        if (lc.getStatus() == LegalCase.Status.CLOSED) {
            throw new BusinessException("LEGAL_CASE_CLOSED", "Sud ishi yopilgan");
        }
        return lc;
    }

    private static BigDecimal remaining(Contract c) {
        return c.getSchedule().stream().map(s -> s.getAmount().subtract(s.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void log(Long contractId, String result, String note, AuthUser me) {
        CollectionAction a = new CollectionAction();
        a.setContractId(contractId);
        a.setActionType(CollectionAction.Type.LEGAL);
        a.setResult(result);
        a.setNote(blank(note));
        a.setUserId(me.id());
        actions.save(a);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

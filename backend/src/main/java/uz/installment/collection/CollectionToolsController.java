package uz.installment.collection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.common.TenantContext;
import uz.installment.contract.Contract;
import uz.installment.contract.ContractRepository;
import uz.installment.contract.ContractService;
import uz.installment.contract.ScheduleStatus;
import uz.installment.notification.EmailSender;
import uz.installment.notification.NotificationLog;
import uz.installment.notification.NotificationService;
import uz.installment.payment.PaymentLinkProvider;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Undirish vositalari: roll-rate migratsiya matritsasi, elektron xat va tezkor to'lov havolasi.
 * E-mail va to'lov havolasi hozircha namoyish (mock) adapterlar orqali ishlaydi.
 */
@RestController
@RequestMapping("/api/collection")
@PreAuthorize("hasAuthority('COLLECTION_VIEW')")
@RequiredArgsConstructor
public class CollectionToolsController {

    static final List<String> BUCKETS = List.of("CURRENT", "D1_30", "D31_60", "D61_90", "D90_PLUS");
    static final int WINDOW_DAYS = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final ContractRepository contracts;
    private final CollectionActionRepository actions;
    private final EmailSender email;
    private final PaymentLinkProvider payLinks;
    private final NotificationService notifications;
    private final Clock clock;

    /** to — hozirgi holat: BUCKETS dan biri yoki PAID (shartnoma to'liq yopilgan). */
    public record Cell(String from, String to, int count, BigDecimal amount) {
    }

    public record RollRate(LocalDate fromDate, LocalDate toDate, List<String> buckets, List<Cell> cells) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 150) String to, @NotBlank @Size(max = 200) String subject,
                               @NotBlank @Size(max = 4000) String text) {
    }

    public record EmailResult(boolean sent, boolean mock, String error) {
    }

    public record PayLinkRequest(boolean sendSms) {
    }

    public record PayLink(String url, BigDecimal amount, boolean mock, NotificationLog.Status smsStatus) {
    }

    /**
     * Roll-rate: 30 kun oldin har bir shartnoma qaysi kechikish oralig'ida bo'lgan va hozir qayerda.
     * Kechikish — to'liq yopilmagan eng eski to'lov sanasidan boshlab hisoblanadi.
     */
    @GetMapping("/roll-rate")
    @Transactional(readOnly = true)
    public RollRate rollRate(@RequestParam(required = false) Long branchId, @AuthenticationPrincipal AuthUser me) {
        LocalDate today = LocalDate.now(clock);
        LocalDate then = today.minusDays(WINDOW_DAYS);
        var p = new MapSqlParameterSource("t", TenantContext.currentTenantId())
                .addValue("b", me.branchScope(branchId)).addValue("today", today).addValue("then", then)
                .addValue("thenTs", Timestamp.from(then.plusDays(1).atStartOfDay(clock.getZone()).toInstant()));
        record Row(String status, LocalDate oldThen, LocalDate oldNow, BigDecimal remaining) {
        }
        List<Row> rows = jdbc.query("""
                select c.status,
                       (select min(s.due_date) from schedule_items s
                         where s.contract_id = c.id and s.due_date < :then
                           and not (s.status = 'PAID' and s.paid_at is not null and s.paid_at < :thenTs)) as old_then,
                       (select min(s.due_date) from schedule_items s
                         where s.contract_id = c.id and s.due_date < :today and s.status <> 'PAID') as old_now,
                       (select coalesce(sum(s.amount - s.paid_amount), 0) from schedule_items s
                         where s.contract_id = c.id) as remaining
                from contracts c
                where c.tenant_id = :t and c.status in ('ACTIVE','LATE','CLOSED') and c.created_at < :thenTs
                  and not (c.status = 'CLOSED' and c.closed_at is not null and c.closed_at < :thenTs)
                  and (cast(:b as bigint) is null or c.branch_id = :b)
                """, p, (rs, i) -> new Row(rs.getString("status"), rs.getObject("old_then", LocalDate.class),
                rs.getObject("old_now", LocalDate.class), rs.getBigDecimal("remaining")));

        List<String> to = new ArrayList<>(BUCKETS);
        to.add("PAID");
        int[][] count = new int[BUCKETS.size()][to.size()];
        BigDecimal[][] amount = new BigDecimal[BUCKETS.size()][to.size()];
        for (Row r : rows) {
            int f = bucket(r.oldThen() == null ? 0 : ChronoUnit.DAYS.between(r.oldThen(), then));
            int tIdx = "CLOSED".equals(r.status()) ? to.size() - 1
                    : bucket(r.oldNow() == null ? 0 : ChronoUnit.DAYS.between(r.oldNow(), today));
            count[f][tIdx]++;
            amount[f][tIdx] = (amount[f][tIdx] == null ? BigDecimal.ZERO : amount[f][tIdx]).add(r.remaining());
        }
        List<Cell> cells = new ArrayList<>();
        for (int f = 0; f < BUCKETS.size(); f++) {
            for (int j = 0; j < to.size(); j++) {
                if (count[f][j] > 0) {
                    cells.add(new Cell(BUCKETS.get(f), to.get(j), count[f][j], amount[f][j]));
                }
            }
        }
        return new RollRate(then, today, BUCKETS, cells);
    }

    static int bucket(long daysLate) {
        return daysLate <= 0 ? 0 : daysLate <= 30 ? 1 : daysLate <= 60 ? 2 : daysLate <= 90 ? 3 : 4;
    }

    /** Qarzdorga elektron xat: natija undirish tarixiga LETTER harakati bo'lib yoziladi. */
    @PostMapping("/contracts/{contractId}/email")
    @PreAuthorize("hasAuthority('COLLECTION_WORK')")
    @Transactional
    public EmailResult sendEmail(@PathVariable Long contractId, @Valid @RequestBody EmailRequest req,
                                 @AuthenticationPrincipal AuthUser me) {
        scoped(contractId, me);
        var res = email.send(req.to().trim(), req.subject().trim(), req.text().trim());
        log(contractId, CollectionAction.Type.LETTER, res.success() ? "EMAIL_SENT" : "EMAIL_FAILED",
                req.to().trim() + " · " + req.subject().trim(), me);
        return new EmailResult(res.success(), email.isMock(), res.error());
    }

    /** Muddati o'tgan summa uchun tezkor to'lov havolasi; sendSms — havola mijozga SMS bilan yuboriladi. */
    @PostMapping("/contracts/{contractId}/pay-link")
    @PreAuthorize("hasAuthority('COLLECTION_WORK')")
    @Transactional
    public PayLink payLink(@PathVariable Long contractId, @RequestBody PayLinkRequest req,
                           @AuthenticationPrincipal AuthUser me) {
        Contract c = scoped(contractId, me);
        LocalDate today = LocalDate.now(clock);
        BigDecimal overdue = c.getSchedule().stream()
                .filter(s -> s.getStatus() != ScheduleStatus.PAID && s.getDueDate().isBefore(today))
                .map(s -> s.getAmount().subtract(s.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (overdue.signum() <= 0) {
            throw new BusinessException("NO_OVERDUE", "Shartnoma bo'yicha muddati o'tgan qarz yo'q");
        }
        String url = payLinks.link(c.getContractNo(), overdue);
        NotificationLog.Status sms = null;
        if (req.sendSms()) {
            sms = notifications.sendManualSms(c, "To'lov havolasi (" + c.getContractNo() + "): " + url);
            log(contractId, CollectionAction.Type.SMS, sms.name(), "PAY_LINK " + url, me);
        } else {
            log(contractId, CollectionAction.Type.NOTE, "PAY_LINK", url, me);
        }
        return new PayLink(url, overdue, payLinks.isMock(), sms);
    }

    private void log(Long contractId, CollectionAction.Type type, String result, String note, AuthUser me) {
        CollectionAction a = new CollectionAction();
        a.setContractId(contractId);
        a.setActionType(type);
        a.setResult(result);
        a.setNote(note);
        a.setUserId(me.id());
        actions.save(a);
    }

    private Contract scoped(Long contractId, AuthUser me) {
        Contract c = contracts.findWithSchedule(contractId)
                .orElseThrow(() -> new NotFoundException("Shartnoma", contractId));
        ContractService.checkScope(c, me);
        return c;
    }
}

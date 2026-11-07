package uz.installment.collection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import uz.installment.notification.NotificationLog;
import uz.installment.notification.NotificationService;
import uz.installment.notification.TemplateRenderer;
import uz.installment.security.AuthUser;
import uz.installment.security.User;
import uz.installment.security.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/collection")
@PreAuthorize("hasAuthority('COLLECTION_VIEW')")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService service;
    private final CollectionActionRepository actions;
    private final ContractRepository contracts;
    private final UserRepository users;
    private final NotificationService notifications;

    public record SmsRequest(@NotBlank @Size(max = 500) String text) {
    }

    public record SmsResult(NotificationLog.Status status, ActionDto action) {
    }

    /** Matnda {ism} {summa} {shartnoma} {kun} o'zgaruvchilari har bir qarzdor uchun almashtiriladi. */
    public record BulkSmsRequest(@NotEmpty @Size(max = 200) List<Long> contractIds,
                                 @NotBlank @Size(max = 500) String text, String lang) {
    }

    public record BulkSmsResult(int total, Map<NotificationLog.Status, Integer> byStatus) {
    }

    public record ActionRequest(@NotNull CollectionAction.Type actionType, @Size(max = 30) String result,
                                @Size(max = 1000) String note, LocalDate promisedDate,
                                @PositiveOrZero BigDecimal promisedAmount, LocalDate plannedDate) {
    }

    public record AssignRequest(Long userId) {
    }

    public record ActionDto(Long id, CollectionAction.Type actionType, String result, String note,
                            LocalDate promisedDate, BigDecimal promisedAmount, LocalDate plannedDate,
                            Long userId, String userName, Instant createdAt) {
        static ActionDto of(CollectionAction a, String userName) {
            return new ActionDto(a.getId(), a.getActionType(), a.getResult(), a.getNote(), a.getPromisedDate(),
                    a.getPromisedAmount(), a.getPlannedDate(), a.getUserId(), userName, a.getCreatedAt());
        }
    }

    @GetMapping("/overdue")
    public List<CollectionService.OverdueRow> overdue(@RequestParam(required = false) Long branchId,
                                                      @AuthenticationPrincipal AuthUser me) {
        return service.overdue(me.branchScope(branchId));
    }

    @GetMapping("/contracts/{contractId}/actions")
    @Transactional(readOnly = true)
    public List<ActionDto> actions(@PathVariable Long contractId, @AuthenticationPrincipal AuthUser me) {
        scoped(contractId, me);
        List<CollectionAction> list = actions.findByContractIdOrderByIdDesc(contractId);
        Set<Long> ids = new HashSet<>();
        list.forEach(a -> {
            if (a.getUserId() != null) ids.add(a.getUserId());
        });
        Map<Long, String> names = new HashMap<>();
        users.findAllById(ids).forEach(u -> names.put(u.getId(), u.getFullName()));
        return list.stream().map(a -> ActionDto.of(a, names.get(a.getUserId()))).toList();
    }

    @PostMapping("/contracts/{contractId}/actions")
    @PreAuthorize("hasAuthority('COLLECTION_WORK')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ActionDto addAction(@PathVariable Long contractId, @Valid @RequestBody ActionRequest req,
                               @AuthenticationPrincipal AuthUser me) {
        scoped(contractId, me);
        CollectionAction a = new CollectionAction();
        a.setContractId(contractId);
        a.setActionType(req.actionType());
        a.setResult(req.result());
        a.setNote(req.note());
        a.setPromisedDate(req.promisedDate());
        a.setPromisedAmount(req.promisedAmount());
        a.setPlannedDate(req.plannedDate());
        a.setUserId(me.id());
        return ActionDto.of(actions.save(a), null);
    }

    /** Qarzdorga SMS: provayder orqali yuboriladi, natija undirish tarixiga SMS harakati bo'lib yoziladi. */
    @PostMapping("/contracts/{contractId}/sms")
    @PreAuthorize("hasAuthority('COLLECTION_WORK')")
    @Transactional
    public SmsResult sendSms(@PathVariable Long contractId, @Valid @RequestBody SmsRequest req,
                             @AuthenticationPrincipal AuthUser me) {
        Contract contract = scoped(contractId, me);
        NotificationLog.Status status = notifications.sendManualSms(contract, req.text().trim());
        return new SmsResult(status, ActionDto.of(logSms(contractId, req.text().trim(), status, me), null));
    }

    /** Soft bosqich: tanlangan qarzdorlarga bitta shablon bilan SMS. */
    @PostMapping("/sms/bulk")
    @PreAuthorize("hasAuthority('COLLECTION_WORK')")
    @Transactional
    public BulkSmsResult bulkSms(@Valid @RequestBody BulkSmsRequest req, @AuthenticationPrincipal AuthUser me) {
        Set<Long> wanted = new HashSet<>(req.contractIds());
        String lang = "ru".equals(req.lang()) ? "ru" : "uz";
        Map<NotificationLog.Status, Integer> counts = new EnumMap<>(NotificationLog.Status.class);
        int total = 0;
        for (CollectionService.OverdueRow row : service.overdue(me.branchScope(null))) {
            if (!wanted.contains(row.contractId())) {
                continue;
            }
            Contract contract = contracts.findWithSchedule(row.contractId()).orElseThrow();
            String text = TemplateRenderer.render(req.text(), row.clientName(), row.overdueAmount(), null,
                    row.contractNo(), row.daysLate(), lang);
            NotificationLog.Status st = notifications.sendManualSms(contract, text);
            logSms(row.contractId(), text, st, me);
            counts.merge(st, 1, Integer::sum);
            total++;
        }
        return new BulkSmsResult(total, counts);
    }

    /** Undirish bo'yicha mas'ul xodimni biriktirish (userId null — olib tashlash). */
    @PutMapping("/contracts/{contractId}/collector")
    @PreAuthorize("hasAuthority('COLLECTION_ASSIGN')")
    @Transactional
    public void assign(@PathVariable Long contractId, @RequestBody AssignRequest req,
                       @AuthenticationPrincipal AuthUser me) {
        Contract c = scoped(contractId, me);
        if (req.userId() == null) {
            c.setCollector(null);
            return;
        }
        User u = users.findById(req.userId()).orElseThrow(() -> new NotFoundException("Xodim", req.userId()));
        if (!u.isActive()) {
            throw new BusinessException("USER_INACTIVE", "Xodim faol emas");
        }
        c.setCollector(u);
        CollectionAction a = new CollectionAction();
        a.setContractId(contractId);
        a.setActionType(CollectionAction.Type.NOTE);
        a.setResult("ASSIGNED");
        a.setNote(u.getFullName());
        a.setUserId(me.id());
        actions.save(a);
    }

    private CollectionAction logSms(Long contractId, String text, NotificationLog.Status status, AuthUser me) {
        CollectionAction a = new CollectionAction();
        a.setContractId(contractId);
        a.setActionType(CollectionAction.Type.SMS);
        a.setResult(status.name());
        a.setNote(text);
        a.setUserId(me.id());
        return actions.save(a);
    }

    private Contract scoped(Long contractId, AuthUser me) {
        Contract c = contracts.findWithSchedule(contractId)
                .orElseThrow(() -> new NotFoundException("Shartnoma", contractId));
        ContractService.checkScope(c, me);
        return c;
    }
}

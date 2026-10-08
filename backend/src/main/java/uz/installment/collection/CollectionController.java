package uz.installment.collection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.NotFoundException;
import uz.installment.contract.Contract;
import uz.installment.contract.ContractRepository;
import uz.installment.notification.NotificationLog;
import uz.installment.notification.NotificationService;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/collection")
@PreAuthorize("hasAnyRole('ADMIN','COLLECTOR','CREDIT_OFFICER')")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService service;
    private final CollectionActionRepository actions;
    private final ContractRepository contracts;
    private final NotificationService notifications;

    public record SmsRequest(@NotBlank @Size(max = 500) String text) {
    }

    public record SmsResult(NotificationLog.Status status, ActionDto action) {
    }

    public record ActionRequest(@NotNull CollectionAction.Type actionType, @Size(max = 30) String result,
                                @Size(max = 1000) String note, LocalDate promisedDate,
                                @PositiveOrZero BigDecimal promisedAmount) {
    }

    public record ActionDto(Long id, CollectionAction.Type actionType, String result, String note,
                            LocalDate promisedDate, BigDecimal promisedAmount, Long userId, Instant createdAt) {
        static ActionDto of(CollectionAction a) {
            return new ActionDto(a.getId(), a.getActionType(), a.getResult(), a.getNote(), a.getPromisedDate(),
                    a.getPromisedAmount(), a.getUserId(), a.getCreatedAt());
        }
    }

    @GetMapping("/overdue")
    public List<CollectionService.OverdueRow> overdue() {
        return service.overdue();
    }

    @GetMapping("/contracts/{contractId}/actions")
    public List<ActionDto> actions(@PathVariable Long contractId) {
        return actions.findByContractIdOrderByIdDesc(contractId).stream().map(ActionDto::of).toList();
    }

    @PostMapping("/contracts/{contractId}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    public ActionDto addAction(@PathVariable Long contractId, @Valid @RequestBody ActionRequest req,
                               @AuthenticationPrincipal AuthUser me) {
        if (!contracts.existsById(contractId)) {
            throw new NotFoundException("Shartnoma", contractId);
        }
        CollectionAction a = new CollectionAction();
        a.setContractId(contractId);
        a.setActionType(req.actionType());
        a.setResult(req.result());
        a.setNote(req.note());
        a.setPromisedDate(req.promisedDate());
        a.setPromisedAmount(req.promisedAmount());
        a.setUserId(me.id());
        return ActionDto.of(actions.save(a));
    }

    /** Qarzdorga SMS: provayder orqali yuboriladi, natija undirish tarixiga SMS harakati bo'lib yoziladi. */
    @PostMapping("/contracts/{contractId}/sms")
    public SmsResult sendSms(@PathVariable Long contractId, @Valid @RequestBody SmsRequest req,
                             @AuthenticationPrincipal AuthUser me) {
        Contract contract = contracts.findWithSchedule(contractId)
                .orElseThrow(() -> new NotFoundException("Shartnoma", contractId));
        NotificationLog.Status status = notifications.sendManualSms(contract, req.text().trim());
        CollectionAction a = new CollectionAction();
        a.setContractId(contractId);
        a.setActionType(CollectionAction.Type.SMS);
        a.setResult(status.name());
        a.setNote(req.text().trim());
        a.setUserId(me.id());
        return new SmsResult(status, ActionDto.of(actions.save(a)));
    }
}

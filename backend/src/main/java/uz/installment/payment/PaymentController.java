package uz.installment.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.PageResponse;
import uz.installment.payment.PaymentService.PaymentDto;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    public record PaymentRequest(@NotNull Long contractId, @NotNull @Positive BigDecimal amount,
                                 @NotNull PaymentMethod method, @Size(max = 100) String externalId,
                                 @Size(max = 500) String note) {
    }

    @GetMapping
    public PageResponse<PaymentDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.list(from, to, page, size);
    }

    /** Kassada (naqd/karta) qabul qilingan to'lov. Payme/Click uchun alohida webhook controller keyin qo'shiladi. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER','CREDIT_OFFICER')")
    public PaymentDto register(@Valid @RequestBody PaymentRequest req, @AuthenticationPrincipal AuthUser me) {
        return service.register(new PaymentService.RegisterPayment(req.contractId(), req.amount(), req.method(),
                req.externalId(), req.note()), me);
    }
}

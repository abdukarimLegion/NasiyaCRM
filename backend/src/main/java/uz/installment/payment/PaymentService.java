package uz.installment.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.common.PageResponse;
import uz.installment.contract.Contract;
import uz.installment.contract.ContractRepository;
import uz.installment.contract.ContractStatus;
import uz.installment.contract.ScheduleItem;
import uz.installment.contract.ScheduleStatus;
import uz.installment.security.AuthUser;
import uz.installment.security.UserRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository payments;
    private final ContractRepository contracts;
    private final UserRepository users;
    private final Clock clock;

    public record RegisterPayment(Long contractId, BigDecimal amount, PaymentMethod method, String externalId,
                                  String note) {
    }

    public record PaymentDto(Long id, Long contractId, String contractNo, String clientName, BigDecimal amount,
                             PaymentMethod method, Instant paidAt, String externalId, String note,
                             String cashierName) {
        static PaymentDto of(Payment p) {
            return new PaymentDto(p.getId(), p.getContract().getId(), p.getContract().getContractNo(),
                    p.getContract().getClient().getFullName(), p.getAmount(), p.getMethod(), p.getPaidAt(),
                    p.getExternalId(), p.getNote(), p.getCashier() != null ? p.getCashier().getFullName() : null);
        }
    }

    /**
     * To'lovni qabul qiladi. Payme/Click callback'lari takrorlansa (bir xil externalId),
     * yangi yozuv yaratilmaydi — avvalgisi qaytariladi (idempotent).
     */
    @Transactional
    public PaymentDto register(RegisterPayment req, AuthUser cashier) {
        if (req.externalId() != null && !req.externalId().isBlank()) {
            var existing = payments.findByMethodAndExternalId(req.method(), req.externalId());
            if (existing.isPresent()) {
                return PaymentDto.of(existing.get());
            }
        }

        Contract contract = contracts.findForUpdate(req.contractId())
                .orElseThrow(() -> new NotFoundException("Shartnoma", req.contractId()));
        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.LATE) {
            throw new BusinessException("CONTRACT_NOT_OPEN", "Shartnoma yopilgan yoki bekor qilingan");
        }

        List<ScheduleItem> open = contract.getSchedule().stream()
                .filter(s -> s.getStatus() != ScheduleStatus.PAID).toList();
        List<PaymentAllocator.Allocation> allocations;
        try {
            allocations = PaymentAllocator.allocate(req.amount(), open.stream()
                    .map(s -> new PaymentAllocator.Due(s.getId(), s.getSeq(), s.getAmount(), s.getPaidAmount()))
                    .toList());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_AMOUNT", e.getMessage());
        }

        LocalDate today = LocalDate.now(clock);
        Map<Long, ScheduleItem> byId = open.stream().collect(Collectors.toMap(ScheduleItem::getId, Function.identity()));

        Payment p = new Payment();
        p.setContract(contract);
        p.setAmount(req.amount());
        p.setMethod(req.method());
        p.setExternalId(blankToNull(req.externalId()));
        p.setNote(blankToNull(req.note()));
        if (cashier != null) {
            p.setCashier(users.getReferenceById(cashier.id()));
        }
        for (PaymentAllocator.Allocation a : allocations) {
            ScheduleItem item = byId.get(a.scheduleItemId());
            item.applyPayment(a.amount(), today);
            PaymentAllocationEntity e = new PaymentAllocationEntity();
            e.setPayment(p);
            e.setScheduleItem(item);
            e.setAmount(a.amount());
            p.getAllocations().add(e);
        }
        contract.refreshStatus(today);
        return PaymentDto.of(payments.save(p));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentDto> list(LocalDate from, LocalDate to, int page, int size) {
        ZoneId zone = clock.getZone();
        LocalDate f = from != null ? from : LocalDate.now(clock).withDayOfMonth(1);
        LocalDate t = to != null ? to : LocalDate.now(clock);
        var p = payments.findInPeriod(f.atStartOfDay(zone).toInstant(), t.plusDays(1).atStartOfDay(zone).toInstant(),
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "paidAt")));
        return PageResponse.of(p, PaymentDto::of);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

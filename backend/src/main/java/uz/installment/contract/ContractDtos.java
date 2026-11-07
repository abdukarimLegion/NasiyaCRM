package uz.installment.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import uz.installment.scoring.ScoringEngine;
import uz.installment.scoring.ScoringService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ContractDtos {

    private ContractDtos() {
    }

    /** Hisob-kitobni oldindan ko'rish (wizard'ning "Murobaha" qadami). */
    /** Savatdagi bitta mahsulot. unitPrice null bo'lsa — mahsulotning joriy narxi. */
    public record CartItem(@NotNull Long productId, @Min(1) @Max(1000) int quantity,
                           @Positive BigDecimal unitPrice) {
    }

    /**
     * Savat: {@code items} berilsa — shu mahsulotlar; aks holda eski bitta mahsulotli shakl
     * ({@code productId} + ixtiyoriy {@code price}).
     */
    public record QuoteRequest(
            Long productId,
            @Positive BigDecimal price,                 // faqat productId bilan: null bo'lsa mahsulot narxi
            @Valid @Size(max = 30) List<CartItem> items,
            @PositiveOrZero BigDecimal downPayment,
            @DecimalMin("0") @DecimalMax("100") BigDecimal markupPct, // null bo'lsa mahsulot ustamasi
            @Min(1) @Max(60) int termMonths,
            LocalDate firstDueDate) {
    }

    public record CreateContractRequest(
            @NotNull Long clientId,
            @NotNull @Valid QuoteRequest terms,
            @NotNull @Valid ScoringService.ScoringRequest scoring,
            @Size(max = 150) String guarantorName,
            @Size(max = 20) String guarantorPhone,
            Long branchId) {
    }

    public record ItemDto(Long productId, String productName, BigDecimal unitPrice, int quantity,
                          BigDecimal lineTotal) {
        static ItemDto of(ContractItem i) {
            return new ItemDto(i.getProduct() != null ? i.getProduct().getId() : null, i.getProductName(),
                    i.getUnitPrice(), i.getQuantity(), i.getLineTotal());
        }
    }

    public record ScheduleItemDto(Long id, int seq, LocalDate dueDate, BigDecimal amount, BigDecimal principalPart,
                                  BigDecimal markupPart, BigDecimal paidAmount, ScheduleStatus status,
                                  Instant paidAt) {
        static ScheduleItemDto of(ScheduleItem s) {
            return new ScheduleItemDto(s.getId(), s.getSeq(), s.getDueDate(), s.getAmount(), s.getPrincipalPart(),
                    s.getMarkupPart(), s.getPaidAmount(), s.getStatus(), s.getPaidAt());
        }
    }

    public record ContractListItem(Long id, String contractNo, Long clientId, String clientName, String clientPhone,
                                   String productName, BigDecimal salePrice, BigDecimal downPayment,
                                   BigDecimal installmentTotal, BigDecimal monthlyPayment, int termMonths,
                                   ContractStatus status, ScoringEngine.RiskCategory riskCategory,
                                   Integer scoreTotal, BigDecimal paidTotal, BigDecimal remaining,
                                   LocalDate nextDue, Instant createdAt, Long branchId, String branchName) {
        static ContractListItem of(Contract c, ScheduleItemRepository.ScheduleTotals t) {
            return new ContractListItem(c.getId(), c.getContractNo(), c.getClient().getId(),
                    c.getClient().getFullName(), c.getClient().getPhone(), c.getProductName(), c.getSalePrice(),
                    c.getDownPayment(), c.getInstallmentTotal(), c.getMonthlyPayment(), c.getTermMonths(),
                    c.getStatus(), c.getRiskCategory(), c.getScoreTotal(),
                    t != null ? t.getPaid() : BigDecimal.ZERO,
                    t != null ? t.getRemaining() : c.getInstallmentTotal(),
                    t != null ? t.getNextDue() : null, c.getCreatedAt(),
                    c.getBranch() != null ? c.getBranch().getId() : null,
                    c.getBranch() != null ? c.getBranch().getName() : null);
        }
    }

    /** V5 dan oldingi (yoki to'g'ridan-to'g'ri SQL bilan kiritilgan) shartnomalarda qator bo'lmasa — bitta sintetik qator. */
    static List<ItemDto> itemsOf(Contract c) {
        if (!c.getItems().isEmpty()) {
            return c.getItems().stream().map(ItemDto::of).toList();
        }
        return List.of(new ItemDto(c.getProduct() != null ? c.getProduct().getId() : null, c.getProductName(),
                c.getCostPrice(), 1, c.getCostPrice()));
    }

    public record ContractDetails(Long id, String contractNo, Long clientId, String clientName, String clientPhone,
                                  String productName, BigDecimal costPrice, BigDecimal downPayment,
                                  BigDecimal markupPct, BigDecimal markupAmount, BigDecimal salePrice,
                                  BigDecimal installmentTotal, int termMonths, BigDecimal monthlyPayment,
                                  ContractStatus status, Integer scoreTotal,
                                  ScoringEngine.RiskCategory riskCategory, ScoringEngine.Decision decision,
                                  String guarantorName, String guarantorPhone, BigDecimal paidTotal,
                                  BigDecimal remaining, Instant createdAt, Instant closedAt,
                                  List<ScheduleItemDto> schedule, Long branchId, String branchName,
                                  String officerName, Long collectorId, String collectorName,
                                  List<ItemDto> items, Long applicationId) {
        static ContractDetails of(Contract c) {
            BigDecimal paid = c.getSchedule().stream().map(ScheduleItem::getPaidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ContractDetails(c.getId(), c.getContractNo(), c.getClient().getId(),
                    c.getClient().getFullName(), c.getClient().getPhone(), c.getProductName(), c.getCostPrice(),
                    c.getDownPayment(), c.getMarkupPct(), c.getMarkupAmount(), c.getSalePrice(),
                    c.getInstallmentTotal(), c.getTermMonths(), c.getMonthlyPayment(), c.getStatus(),
                    c.getScoreTotal(), c.getRiskCategory(), c.getDecision(), c.getGuarantorName(),
                    c.getGuarantorPhone(), paid, c.getInstallmentTotal().subtract(paid), c.getCreatedAt(),
                    c.getClosedAt(), c.getSchedule().stream().map(ScheduleItemDto::of).toList(),
                    c.getBranch() != null ? c.getBranch().getId() : null,
                    c.getBranch() != null ? c.getBranch().getName() : null,
                    c.getOfficer() != null ? c.getOfficer().getFullName() : null,
                    c.getCollector() != null ? c.getCollector().getId() : null,
                    c.getCollector() != null ? c.getCollector().getFullName() : null,
                    itemsOf(c), c.getApplicationId());
        }
    }
}

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
    public record QuoteRequest(
            @NotNull Long productId,
            @Positive BigDecimal price,                 // null bo'lsa mahsulot narxi
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
            @Size(max = 20) String guarantorPhone) {
    }

    public record ScheduleItemDto(Long id, int seq, LocalDate dueDate, BigDecimal amount, BigDecimal principalPart,
                                  BigDecimal markupPart, BigDecimal paidAmount, ScheduleStatus status,
                                  Instant paidAt) {
        static ScheduleItemDto of(ScheduleItem s) {
            return new ScheduleItemDto(s.getId(), s.getSeq(), s.getDueDate(), s.getAmount(), s.getPrincipalPart(),
                    s.getMarkupPart(), s.getPaidAmount(), s.getStatus(), s.getPaidAt());
        }
    }

    public record ContractListItem(Long id, String contractNo, Long clientId, String clientName, String productName,
                                   BigDecimal salePrice, BigDecimal installmentTotal, BigDecimal monthlyPayment,
                                   int termMonths, ContractStatus status, ScoringEngine.RiskCategory riskCategory,
                                   Instant createdAt) {
        static ContractListItem of(Contract c) {
            return new ContractListItem(c.getId(), c.getContractNo(), c.getClient().getId(),
                    c.getClient().getFullName(), c.getProductName(), c.getSalePrice(), c.getInstallmentTotal(),
                    c.getMonthlyPayment(), c.getTermMonths(), c.getStatus(), c.getRiskCategory(), c.getCreatedAt());
        }
    }

    public record ContractDetails(Long id, String contractNo, Long clientId, String clientName, String clientPhone,
                                  String productName, BigDecimal costPrice, BigDecimal downPayment,
                                  BigDecimal markupPct, BigDecimal markupAmount, BigDecimal salePrice,
                                  BigDecimal installmentTotal, int termMonths, BigDecimal monthlyPayment,
                                  ContractStatus status, Integer scoreTotal,
                                  ScoringEngine.RiskCategory riskCategory, ScoringEngine.Decision decision,
                                  String guarantorName, String guarantorPhone, BigDecimal paidTotal,
                                  BigDecimal remaining, Instant createdAt, Instant closedAt,
                                  List<ScheduleItemDto> schedule) {
        static ContractDetails of(Contract c) {
            BigDecimal paid = c.getSchedule().stream().map(ScheduleItem::getPaidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ContractDetails(c.getId(), c.getContractNo(), c.getClient().getId(),
                    c.getClient().getFullName(), c.getClient().getPhone(), c.getProductName(), c.getCostPrice(),
                    c.getDownPayment(), c.getMarkupPct(), c.getMarkupAmount(), c.getSalePrice(),
                    c.getInstallmentTotal(), c.getTermMonths(), c.getMonthlyPayment(), c.getStatus(),
                    c.getScoreTotal(), c.getRiskCategory(), c.getDecision(), c.getGuarantorName(),
                    c.getGuarantorPhone(), paid, c.getInstallmentTotal().subtract(paid), c.getCreatedAt(),
                    c.getClosedAt(), c.getSchedule().stream().map(ScheduleItemDto::of).toList());
        }
    }
}

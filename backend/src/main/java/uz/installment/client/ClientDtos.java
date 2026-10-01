package uz.installment.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ClientDtos {

    private ClientDtos() {
    }

    public record ClientRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Pattern(regexp = "^[0-9]{14}$", message = "JShShIR 14 ta raqam bo'lishi kerak") String pinfl,
            @Size(max = 20) String passportSeries,
            LocalDate passportExpiry,
            LocalDate birthDate,
            @NotBlank @Pattern(regexp = "^\\+?998[0-9]{9}$", message = "Telefon +998XXXXXXXXX formatida") String phone,
            @Pattern(regexp = "^$|^\\+?998[0-9]{9}$", message = "Telefon +998XXXXXXXXX formatida") String extraPhone,
            @Size(max = 100) String region,
            @Size(max = 100) String district,
            @Size(max = 300) String address,
            @Size(max = 200) String workplace,
            @PositiveOrZero BigDecimal monthlyIncome,
            @Size(max = 50) String familyStatus,
            Long telegramChatId,
            boolean blacklisted,
            String note) {
    }

    public record ClientResponse(
            Long id, String fullName, String pinfl, String passportSeries, LocalDate passportExpiry,
            LocalDate birthDate, String phone, String extraPhone, String region, String district, String address,
            String workplace, BigDecimal monthlyIncome, String familyStatus, Long telegramChatId,
            boolean blacklisted, String note, Instant createdAt) {

        public static ClientResponse of(Client c) {
            return new ClientResponse(c.getId(), c.getFullName(), c.getPinfl(), c.getPassportSeries(),
                    c.getPassportExpiry(), c.getBirthDate(), c.getPhone(), c.getExtraPhone(), c.getRegion(),
                    c.getDistrict(), c.getAddress(), c.getWorkplace(), c.getMonthlyIncome(), c.getFamilyStatus(),
                    c.getTelegramChatId(), c.isBlacklisted(), c.getNote(), c.getCreatedAt());
        }
    }

    /** Mijoz kartochkasi: ma'lumot + qisqa shartnomalar ro'yxati + qoldiq qarz. */
    public record ClientDetails(ClientResponse client, BigDecimal activeDebt, List<ContractBrief> contracts) {
    }

    public record ContractBrief(Long id, String contractNo, String productName, BigDecimal salePrice,
                                BigDecimal paid, BigDecimal remaining, String status, String riskCategory,
                                LocalDate nextDue) {
    }
}

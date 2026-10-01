package uz.installment.contract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Murobaha hisob-kitobi (sof funksiya, bazaga bog'liq emas).
 *
 * <p>Sxema: sotuvchi tovarni {@code costPrice} ga sotib oladi va mijozga
 * ustama bilan sotadi. Ustama faqat moliyalashtirilgan qismga (tannarx
 * minus boshlang'ich to'lov) qo'yiladi, muddat davomida o'zgarmaydi,
 * kechikish uchun foiz qo'shilmaydi.
 *
 * <pre>
 *   financed          = costPrice - downPayment
 *   markupAmount      = round(financed * markupPct / 100)
 *   salePrice         = costPrice + markupAmount
 *   installmentTotal  = financed + markupAmount     (= salePrice - downPayment)
 *   monthly           = round(installmentTotal / term, roundingStep)
 *   last              = installmentTotal - monthly * (term - 1)
 * </pre>
 * downPayment = 0 bo'lsa, prototipdagi {@code murabaha()} bilan bir xil natija beradi.
 */
public final class MurabahaCalculator {

    private MurabahaCalculator() {
    }

    public record Quote(
            BigDecimal costPrice,
            BigDecimal downPayment,
            BigDecimal markupPct,
            BigDecimal markupAmount,
            BigDecimal salePrice,
            BigDecimal installmentTotal,
            int termMonths,
            BigDecimal monthlyPayment,
            BigDecimal lastPayment,
            List<Installment> schedule) {
    }

    public record Installment(int seq, LocalDate dueDate, BigDecimal amount,
                              BigDecimal principalPart, BigDecimal markupPart) {
    }

    public static Quote calculate(BigDecimal costPrice, BigDecimal downPayment, BigDecimal markupPct,
                                  int termMonths, LocalDate firstDueDate, int roundingStep) {
        if (costPrice == null || costPrice.signum() <= 0) {
            throw new IllegalArgumentException("costPrice > 0 bo'lishi kerak");
        }
        BigDecimal down = downPayment == null ? BigDecimal.ZERO : downPayment;
        if (down.signum() < 0 || down.compareTo(costPrice) >= 0) {
            throw new IllegalArgumentException("downPayment 0 dan katta yoki teng va costPrice dan kichik bo'lishi kerak");
        }
        if (markupPct == null || markupPct.signum() < 0 || markupPct.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("markupPct 0..100 oralig'ida bo'lishi kerak");
        }
        if (termMonths < 1 || termMonths > 60) {
            throw new IllegalArgumentException("termMonths 1..60 oralig'ida bo'lishi kerak");
        }
        if (roundingStep < 1) {
            throw new IllegalArgumentException("roundingStep >= 1 bo'lishi kerak");
        }
        if (firstDueDate == null) {
            throw new IllegalArgumentException("firstDueDate majburiy");
        }

        BigDecimal financed = costPrice.subtract(down);
        BigDecimal markupAmount = som(financed.multiply(markupPct).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
        BigDecimal salePrice = costPrice.add(markupAmount);
        BigDecimal total = financed.add(markupAmount);

        BigDecimal step = BigDecimal.valueOf(roundingStep);
        BigDecimal monthly = total.divide(BigDecimal.valueOf(termMonths), 10, RoundingMode.HALF_UP)
                .divide(step, 0, RoundingMode.HALF_UP).multiply(step);
        BigDecimal last = total.subtract(monthly.multiply(BigDecimal.valueOf(termMonths - 1L)));

        // Katta yaxlitlash qadamida oxirgi to'lov manfiy/nol bo'lib qolmasin
        if (last.signum() <= 0) {
            monthly = total.divide(BigDecimal.valueOf(termMonths), 0, RoundingMode.DOWN);
            last = total.subtract(monthly.multiply(BigDecimal.valueOf(termMonths - 1L)));
        }

        List<Installment> schedule = new ArrayList<>(termMonths);
        BigDecimal principalLeft = financed;
        BigDecimal markupLeft = markupAmount;
        for (int i = 1; i <= termMonths; i++) {
            BigDecimal amount = (i == termMonths) ? last : monthly;
            BigDecimal principalPart;
            BigDecimal markupPart;
            if (i == termMonths) {
                principalPart = principalLeft;
                markupPart = markupLeft;
            } else {
                // Har bir to'lovda asosiy qarz / ustama ulushi bir xil nisbatda (buxgalteriya uchun)
                principalPart = som(amount.multiply(financed).divide(total, 10, RoundingMode.HALF_UP));
                markupPart = amount.subtract(principalPart);
            }
            principalLeft = principalLeft.subtract(principalPart);
            markupLeft = markupLeft.subtract(markupPart);
            schedule.add(new Installment(i, firstDueDate.plusMonths(i - 1L), amount, principalPart, markupPart));
        }

        return new Quote(som(costPrice), som(down), markupPct, markupAmount, som(salePrice), som(total),
                termMonths, monthly, last, List.copyOf(schedule));
    }

    private static BigDecimal som(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }
}

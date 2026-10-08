package uz.installment.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ichki nasiya limiti (sof funksiya).
 *
 * <pre>
 *   maxMonthly = daromad × DTI(risk)          — oylik to'lovlarga ketishi mumkin bo'lgan qism
 *   freeMonthly = max(0, maxMonthly − mavjud oylik to'lovlar)
 *   limit = freeMonthly × 12                  — 12 oylik bo'lib to'lanadigan summa chegarasi
 * </pre>
 * DTI: A — 50%, B — 40%, C — 30%, D — 0. Skoring hali bo'lmasa B (40%) olinadi.
 */
public final class CreditLimit {

    public static final int LIMIT_MONTHS = 12;

    private CreditLimit() {
    }

    public record Result(BigDecimal dtiPct, BigDecimal maxMonthly, BigDecimal usedMonthly,
                         BigDecimal freeMonthly, BigDecimal limit) {
    }

    public static BigDecimal dtiPct(ScoringEngine.RiskCategory risk) {
        if (risk == null) {
            return BigDecimal.valueOf(40);
        }
        return switch (risk) {
            case A -> BigDecimal.valueOf(50);
            case B -> BigDecimal.valueOf(40);
            case C -> BigDecimal.valueOf(30);
            case D -> BigDecimal.ZERO;
        };
    }

    public static Result of(BigDecimal monthlyIncome, ScoringEngine.RiskCategory risk, BigDecimal usedMonthly) {
        BigDecimal income = monthlyIncome == null ? BigDecimal.ZERO : monthlyIncome.max(BigDecimal.ZERO);
        BigDecimal used = usedMonthly == null ? BigDecimal.ZERO : usedMonthly;
        BigDecimal dti = dtiPct(risk);
        BigDecimal max = income.multiply(dti).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        BigDecimal free = max.subtract(used).max(BigDecimal.ZERO);
        return new Result(dti, max, used, free, free.multiply(BigDecimal.valueOf(LIMIT_MONTHS)));
    }
}

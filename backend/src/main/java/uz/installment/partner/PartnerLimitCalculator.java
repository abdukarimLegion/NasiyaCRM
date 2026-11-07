package uz.installment.partner;

import uz.installment.scoring.ScoringEngine.RiskCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Hamkor provayderlar bo'yicha mijoz limiti va eng qulay variantni tavsiya qilish (sof funksiya).
 *
 * <pre>
 *   maxMonthly = daromad × hamkor DTI × xavf koeffitsiyenti      (A 1.0, B 0.8, C 0.5, D 0)
 *   limit      = maxMonthly × hamkorning maksimal muddati
 * </pre>
 * Limit hamkorning o'z skoringi emas, balki bizning daromad va risk toifamizga asoslangan taxmin:
 * yakuniy qaror hamkor tizimida qabul qilinadi.
 */
public final class PartnerLimitCalculator {

    private PartnerLimitCalculator() {
    }

    public record Terms(String code, String name, BigDecimal commissionPct, int maxTerm, BigDecimal minDownPct,
                        BigDecimal dtiPct) {
    }

    /** reason: OK, TERM (muddat uzun), DOWN (boshlang'ich to'lov kam), LIMIT (limit yetmaydi), RISK (D toifa). */
    public record Offer(String code, String name, BigDecimal limit, BigDecimal maxMonthly, int maxTerm,
                        BigDecimal commissionPct, BigDecimal minDownPct, boolean eligible, String reason,
                        BigDecimal commission) {
    }

    /** provider: INTERNAL / ALIF / ...; reason: INTERNAL_COVERS, PARTNER_LOWEST_COMMISSION, NO_LIMIT. */
    public record Recommendation(String provider, String reason) {
    }

    public static BigDecimal riskFactor(RiskCategory risk) {
        if (risk == null) {
            return new BigDecimal("0.8");
        }
        return switch (risk) {
            case A -> BigDecimal.ONE;
            case B -> new BigDecimal("0.8");
            case C -> new BigDecimal("0.5");
            case D -> BigDecimal.ZERO;
        };
    }

    /**
     * @param goodsTotal   savatdagi mahsulotlar narxi (komissiya shundan hisoblanadi)
     * @param financed     hamkor moliyalashtiradigan summa (narx − boshlang'ich to'lov)
     * @param downPayment  boshlang'ich to'lov
     * @param termMonths   so'ralgan muddat
     */
    public static Offer offer(Terms p, BigDecimal monthlyIncome, RiskCategory risk, BigDecimal goodsTotal,
                              BigDecimal financed, BigDecimal downPayment, int termMonths) {
        BigDecimal income = monthlyIncome == null ? BigDecimal.ZERO : monthlyIncome.max(BigDecimal.ZERO);
        BigDecimal maxMonthly = income.multiply(p.dtiPct()).multiply(riskFactor(risk))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        BigDecimal limit = maxMonthly.multiply(BigDecimal.valueOf(p.maxTerm()));
        BigDecimal minDown = goodsTotal.multiply(p.minDownPct()).divide(BigDecimal.valueOf(100), 0, RoundingMode.UP);
        BigDecimal commission = goodsTotal.multiply(p.commissionPct()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        String reason = "OK";
        if (risk == RiskCategory.D || maxMonthly.signum() == 0) {
            reason = "RISK";
        } else if (termMonths > p.maxTerm()) {
            reason = "TERM";
        } else if (downPayment.compareTo(minDown) < 0) {
            reason = "DOWN";
        } else if (financed.compareTo(limit) > 0) {
            reason = "LIMIT";
        }
        return new Offer(p.code(), p.name(), limit, maxMonthly, p.maxTerm(), p.commissionPct(), p.minDownPct(),
                "OK".equals(reason), reason, commission);
    }

    /**
     * Ichki limit yetsa — ichki murobaha (komissiya yo'q, ustama do'konga qoladi); aks holda eng arzon komissiyali
     * mos hamkor; hech biri mos kelmasa — NO_LIMIT.
     */
    public static Recommendation recommend(boolean internalCovers, List<Offer> offers) {
        if (internalCovers) {
            return new Recommendation("INTERNAL", "INTERNAL_COVERS");
        }
        return offers.stream().filter(Offer::eligible)
                .min(Comparator.comparing(Offer::commissionPct))
                .map(o -> new Recommendation(o.code(), "PARTNER_LOWEST_COMMISSION"))
                .orElse(new Recommendation(null, "NO_LIMIT"));
    }
}

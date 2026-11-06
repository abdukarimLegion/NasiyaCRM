package uz.installment.kpi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Operator KPI balini hisoblash (sof funksiya — testlanadi).
 *
 * <ul>
 *   <li>Har bir ko'rsatkich bo'yicha bajarilish = haqiqiy / reja × 100, {@code achievementCapPct} bilan cheklanadi.</li>
 *   <li>Reja qo'yilmagan (0) ko'rsatkich hisobga olinmaydi.</li>
 *   <li>Sifat (QUALITY) — portfel sifati foizi (0–100), rejasiz; faqat xodimda ochiq shartnoma bo'lsa.</li>
 *   <li>Yakuniy ball — hisobga olingan ko'rsatkichlarning vaznli o'rtachasi.</li>
 *   <li>Bonus = bonus fondi × min(ball, bonusCapPct) / 100; ball {@code minScoreForBonus} dan past bo'lsa — 0.</li>
 * </ul>
 */
public final class KpiCalculator {

    public enum Metric { CONTRACTS, SALES, COLLECTED, RECOVERED, ACTIONS, QUALITY }

    public enum Grade { A, B, C, D }

    public record Config(Map<Metric, Integer> weights, int minScoreForBonus, int bonusCapPct, int achievementCapPct) {

        public static Config defaults() {
            Map<Metric, Integer> w = new EnumMap<>(Metric.class);
            w.put(Metric.CONTRACTS, 20);
            w.put(Metric.SALES, 25);
            w.put(Metric.COLLECTED, 20);
            w.put(Metric.RECOVERED, 15);
            w.put(Metric.ACTIONS, 10);
            w.put(Metric.QUALITY, 10);
            return new Config(w, 60, 120, 150);
        }

        public int weight(Metric m) {
            Integer v = weights == null ? null : weights.get(m);
            return v == null ? 0 : Math.max(0, v);
        }
    }

    public record MetricResult(Metric metric, BigDecimal actual, BigDecimal target, BigDecimal achievementPct,
                               int weight, boolean included) {
    }

    public record Result(List<MetricResult> metrics, BigDecimal score, Grade grade, BigDecimal bonusPayout) {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private KpiCalculator() {
    }

    /**
     * @param actual     haqiqiy qiymatlar (QUALITY dan tashqari)
     * @param target     rejalar (QUALITY dan tashqari)
     * @param qualityPct portfel sifati (null — hisobga olinmaydi)
     * @param bonusBase  xodimning oylik bonus fondi
     */
    public static Result calculate(Map<Metric, BigDecimal> actual, Map<Metric, BigDecimal> target,
                                   BigDecimal qualityPct, BigDecimal bonusBase, Config cfg) {
        List<MetricResult> out = new ArrayList<>();
        BigDecimal weighted = BigDecimal.ZERO;
        int weightSum = 0;
        BigDecimal cap = BigDecimal.valueOf(cfg.achievementCapPct());
        for (Metric m : Metric.values()) {
            int w = cfg.weight(m);
            if (m == Metric.QUALITY) {
                boolean inc = qualityPct != null && w > 0;
                BigDecimal ach = qualityPct == null ? null : clamp(qualityPct, BigDecimal.ZERO, HUNDRED);
                out.add(new MetricResult(m, qualityPct, HUNDRED, ach, w, inc));
                if (inc) {
                    weighted = weighted.add(ach.multiply(BigDecimal.valueOf(w)));
                    weightSum += w;
                }
                continue;
            }
            BigDecimal a = nz(actual.get(m));
            BigDecimal t = nz(target.get(m));
            boolean inc = t.signum() > 0 && w > 0;
            BigDecimal ach = t.signum() > 0
                    ? a.multiply(HUNDRED).divide(t, 1, RoundingMode.HALF_UP).min(cap)
                    : null;
            out.add(new MetricResult(m, a, t, ach, w, inc));
            if (inc) {
                weighted = weighted.add(ach.multiply(BigDecimal.valueOf(w)));
                weightSum += w;
            }
        }
        if (weightSum == 0) {
            return new Result(out, null, null, BigDecimal.ZERO);
        }
        BigDecimal score = weighted.divide(BigDecimal.valueOf(weightSum), 1, RoundingMode.HALF_UP);
        BigDecimal bonus = BigDecimal.ZERO;
        BigDecimal base = nz(bonusBase);
        if (base.signum() > 0 && score.compareTo(BigDecimal.valueOf(cfg.minScoreForBonus())) >= 0) {
            bonus = base.multiply(score.min(BigDecimal.valueOf(cfg.bonusCapPct())))
                    .divide(HUNDRED, 0, RoundingMode.HALF_UP);
        }
        return new Result(out, score, grade(score), bonus);
    }

    public static Grade grade(BigDecimal score) {
        if (score.compareTo(HUNDRED) >= 0) return Grade.A;
        if (score.compareTo(BigDecimal.valueOf(80)) >= 0) return Grade.B;
        if (score.compareTo(BigDecimal.valueOf(60)) >= 0) return Grade.C;
        return Grade.D;
    }

    /**
     * Portfel sifati: 100 − 2 × (muddati o'tgan qarz / qolgan qarz, %). 0 dan kam bo'lmaydi.
     * Masalan, qarzning 10% muddati o'tgan bo'lsa — 80%.
     */
    public static BigDecimal quality(BigDecimal remaining, BigDecimal overdue) {
        if (remaining == null || remaining.signum() <= 0) {
            return null;
        }
        BigDecimal share = nz(overdue).multiply(HUNDRED).divide(remaining, 4, RoundingMode.HALF_UP);
        return HUNDRED.subtract(share.multiply(BigDecimal.valueOf(2))).max(BigDecimal.ZERO)
                .setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal clamp(BigDecimal v, BigDecimal min, BigDecimal max) {
        return v.max(min).min(max);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

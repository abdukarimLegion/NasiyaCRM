package uz.installment.scoring;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Scoring va stop-faktor qoidalari (sof funksiya, prototipdagi
 * computeScore / riskCat / wizard mantiqi bilan bir xil).
 */
public final class ScoringEngine {

    private ScoringEngine() {
    }

    public enum Factor {
        INCOME(30), TENURE(15), FAMILY(10), HISTORY(20), DISCIPLINE(15), GUARANTOR(10);

        private final int max;

        Factor(int max) {
            this.max = max;
        }

        public int max() {
            return max;
        }
    }

    public enum StopFactor {
        BLACKLIST, FRAUD, DUP_PINFL, PASSPORT_EXPIRED, INCOME_NOT_VERIFIED, COURT, OVERDUE, OVERLIMIT
    }

    public enum RiskCategory {
        A, B, C, D;

        public static RiskCategory of(int score) {
            if (score >= 80) return A;
            if (score >= 60) return B;
            if (score >= 40) return C;
            return D;
        }
    }

    public enum Decision { APPROVED, REVIEW, REJECTED }

    /** Guarantor bo'lmasa shu balldan oshmaydi. */
    static final int GUARANTOR_CAP_WITHOUT_GUARANTOR = 5;

    public record Input(Map<Factor, Integer> points, boolean incomeVerified, boolean guarantorPresent,
                        Set<StopFactor> stopFactors) {
    }

    public record Result(Map<Factor, Integer> points, int total, RiskCategory risk,
                         Set<StopFactor> stopFactors, Decision decision) {
    }

    public static Result evaluate(Input in) {
        Map<Factor, Integer> pts = new EnumMap<>(Factor.class);
        for (Factor f : Factor.values()) {
            int v = in.points() == null ? 0 : in.points().getOrDefault(f, 0);
            if (v < 0 || v > f.max()) {
                throw new IllegalArgumentException(f + " bali 0.." + f.max() + " oralig'ida bo'lishi kerak: " + v);
            }
            pts.put(f, v);
        }

        Set<StopFactor> stops = EnumSet.noneOf(StopFactor.class);
        if (in.stopFactors() != null) {
            stops.addAll(in.stopFactors());
        }

        if (!in.guarantorPresent()) {
            pts.put(Factor.GUARANTOR, Math.min(pts.get(Factor.GUARANTOR), GUARANTOR_CAP_WITHOUT_GUARANTOR));
        }
        if (!in.incomeVerified()) {
            pts.put(Factor.INCOME, Math.round(pts.get(Factor.INCOME) * 0.5f));
            stops.add(StopFactor.INCOME_NOT_VERIFIED);
        }

        int total = pts.values().stream().mapToInt(Integer::intValue).sum();
        RiskCategory risk = RiskCategory.of(total);
        Decision decision;
        if (!stops.isEmpty() || risk == RiskCategory.D) {
            decision = Decision.REJECTED;
        } else if (risk == RiskCategory.C) {
            decision = Decision.REVIEW;
        } else {
            decision = Decision.APPROVED;
        }
        return new Result(Collections.unmodifiableMap(pts), total, risk, Collections.unmodifiableSet(stops), decision);
    }
}

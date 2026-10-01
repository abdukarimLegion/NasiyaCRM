package uz.installment.scoring;

import org.junit.jupiter.api.Test;
import uz.installment.scoring.ScoringEngine.Decision;
import uz.installment.scoring.ScoringEngine.Factor;
import uz.installment.scoring.ScoringEngine.RiskCategory;
import uz.installment.scoring.ScoringEngine.StopFactor;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoringEngineTest {

    // Prototipdagi "Akmal Karimov": 27+13+9+18+14+9 = 90 -> A
    private static final Map<Factor, Integer> AKMAL = Map.of(
            Factor.INCOME, 27, Factor.TENURE, 13, Factor.FAMILY, 9,
            Factor.HISTORY, 18, Factor.DISCIPLINE, 14, Factor.GUARANTOR, 9);

    @Test
    void approvesGoodClient() {
        var r = ScoringEngine.evaluate(new ScoringEngine.Input(AKMAL, true, true, Set.of()));
        assertEquals(90, r.total());
        assertEquals(RiskCategory.A, r.risk());
        assertEquals(Decision.APPROVED, r.decision());
    }

    @Test
    void noGuarantorCapsGuarantorPoints() {
        var r = ScoringEngine.evaluate(new ScoringEngine.Input(AKMAL, true, false, Set.of()));
        assertEquals(5, r.points().get(Factor.GUARANTOR).intValue());
        assertEquals(86, r.total());
    }

    @Test
    void unverifiedIncomeHalvesAndStops() {
        var r = ScoringEngine.evaluate(new ScoringEngine.Input(AKMAL, false, true, Set.of()));
        assertEquals(14, r.points().get(Factor.INCOME).intValue()); // round(13.5)
        assertTrue(r.stopFactors().contains(StopFactor.INCOME_NOT_VERIFIED));
        assertEquals(Decision.REJECTED, r.decision());
    }

    @Test
    void anyStopFactorRejects() {
        var r = ScoringEngine.evaluate(new ScoringEngine.Input(AKMAL, true, true, Set.of(StopFactor.OVERDUE)));
        assertEquals(Decision.REJECTED, r.decision());
    }

    @Test
    void riskBoundaries() {
        assertEquals(RiskCategory.A, RiskCategory.of(80));
        assertEquals(RiskCategory.B, RiskCategory.of(79));
        assertEquals(RiskCategory.B, RiskCategory.of(60));
        assertEquals(RiskCategory.C, RiskCategory.of(59));
        assertEquals(RiskCategory.C, RiskCategory.of(40));
        assertEquals(RiskCategory.D, RiskCategory.of(39));
    }

    @Test
    void categoryCGoesToReview() {
        // Prototipdagi "Gulnora": 12+11+5+10+11+4 = 53 -> C
        var pts = Map.of(Factor.INCOME, 12, Factor.TENURE, 11, Factor.FAMILY, 5,
                Factor.HISTORY, 10, Factor.DISCIPLINE, 11, Factor.GUARANTOR, 4);
        var r = ScoringEngine.evaluate(new ScoringEngine.Input(pts, true, true, Set.of()));
        assertEquals(53, r.total());
        assertEquals(Decision.REVIEW, r.decision());
    }

    @Test
    void rejectsOutOfRangePoints() {
        assertThrows(IllegalArgumentException.class, () -> ScoringEngine.evaluate(
                new ScoringEngine.Input(Map.of(Factor.INCOME, 31), true, true, Set.of())));
    }
}

package uz.installment.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CreditLimitTest {

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void riskA_halfOfIncome_minusExistingPayments_times12() {
        var r = CreditLimit.of(bd(10_000_000), ScoringEngine.RiskCategory.A, bd(1_500_000));
        assertThat(r.maxMonthly()).isEqualByComparingTo(bd(5_000_000));
        assertThat(r.freeMonthly()).isEqualByComparingTo(bd(3_500_000));
        assertThat(r.limit()).isEqualByComparingTo(bd(42_000_000));
    }

    @Test
    void obligationsAboveCapacity_giveZeroLimit() {
        var r = CreditLimit.of(bd(4_000_000), ScoringEngine.RiskCategory.C, bd(2_000_000));
        assertThat(r.maxMonthly()).isEqualByComparingTo(bd(1_200_000));
        assertThat(r.limit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void riskD_andMissingIncome_giveZero() {
        assertThat(CreditLimit.of(bd(9_000_000), ScoringEngine.RiskCategory.D, null).limit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(CreditLimit.of(null, ScoringEngine.RiskCategory.A, null).limit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void noScoringYet_usesRiskB() {
        assertThat(CreditLimit.of(bd(5_000_000), null, null).maxMonthly()).isEqualByComparingTo(bd(2_000_000));
    }
}

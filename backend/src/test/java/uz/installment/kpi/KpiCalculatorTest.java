package uz.installment.kpi;

import org.junit.jupiter.api.Test;
import uz.installment.kpi.KpiCalculator.Config;
import uz.installment.kpi.KpiCalculator.Grade;
import uz.installment.kpi.KpiCalculator.Metric;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KpiCalculatorTest {

    private static Map<Metric, BigDecimal> map(Object... kv) {
        Map<Metric, BigDecimal> m = new EnumMap<>(Metric.class);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Metric) kv[i], new BigDecimal(String.valueOf(kv[i + 1])));
        }
        return m;
    }

    @Test
    void onlyMetricsWithTargetsAreWeighted() {
        // CONTRACTS: 8/10 = 80% (vazn 20), SALES: 30/20 = 150% (vazn 25), qolganlarida reja yo'q
        var r = KpiCalculator.calculate(
                map(Metric.CONTRACTS, 8, Metric.SALES, 30_000_000, Metric.COLLECTED, 5_000_000),
                map(Metric.CONTRACTS, 10, Metric.SALES, 20_000_000),
                null, BigDecimal.ZERO, Config.defaults());
        // (80*20 + 150*25) / 45 = 118.9
        assertThat(r.score()).isEqualByComparingTo("118.9");
        assertThat(r.grade()).isEqualTo(Grade.A);
        assertThat(r.metrics().stream().filter(KpiCalculator.MetricResult::included)).hasSize(2);
    }

    @Test
    void achievementIsCapped() {
        var r = KpiCalculator.calculate(map(Metric.CONTRACTS, 50), map(Metric.CONTRACTS, 10), null,
                BigDecimal.ZERO, Config.defaults());
        assertThat(r.score()).isEqualByComparingTo("150.0");
    }

    @Test
    void qualityIncludedWhenPresent() {
        // CONTRACTS 50% (vazn 20), QUALITY 80 (vazn 10) → (50*20 + 80*10) / 30 = 60
        var r = KpiCalculator.calculate(map(Metric.CONTRACTS, 5), map(Metric.CONTRACTS, 10),
                new BigDecimal("80"), BigDecimal.ZERO, Config.defaults());
        assertThat(r.score()).isEqualByComparingTo("60.0");
        assertThat(r.grade()).isEqualTo(Grade.C);
    }

    @Test
    void noTargetsMeansNoScore() {
        var r = KpiCalculator.calculate(map(Metric.CONTRACTS, 5), map(), null, new BigDecimal("1000000"),
                Config.defaults());
        assertThat(r.score()).isNull();
        assertThat(r.bonusPayout()).isEqualByComparingTo("0");
    }

    @Test
    void bonusRespectsThresholdAndCap() {
        Config cfg = Config.defaults(); // min 60, cap 120
        var low = KpiCalculator.calculate(map(Metric.CONTRACTS, 5), map(Metric.CONTRACTS, 10), null,
                new BigDecimal("2000000"), cfg);
        assertThat(low.bonusPayout()).isEqualByComparingTo("0");

        var mid = KpiCalculator.calculate(map(Metric.CONTRACTS, 9), map(Metric.CONTRACTS, 10), null,
                new BigDecimal("2000000"), cfg);
        assertThat(mid.bonusPayout()).isEqualByComparingTo("1800000");

        var high = KpiCalculator.calculate(map(Metric.CONTRACTS, 20), map(Metric.CONTRACTS, 10), null,
                new BigDecimal("2000000"), cfg);
        assertThat(high.bonusPayout()).isEqualByComparingTo("2400000");
    }

    @Test
    void qualityFormula() {
        assertThat(KpiCalculator.quality(new BigDecimal("1000"), new BigDecimal("100"))).isEqualByComparingTo("80.0");
        assertThat(KpiCalculator.quality(new BigDecimal("1000"), new BigDecimal("700"))).isEqualByComparingTo("0.0");
        assertThat(KpiCalculator.quality(BigDecimal.ZERO, BigDecimal.ZERO)).isNull();
    }
}

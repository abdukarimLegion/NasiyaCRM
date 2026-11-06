package uz.installment.forecast;

import org.junit.jupiter.api.Test;
import uz.installment.forecast.ForecastCalculator.Inputs;
import uz.installment.forecast.ForecastCalculator.Scenario;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ForecastCalculatorTest {

    private static final double[] FLAT = new double[12];

    static {
        Arrays.fill(FLAT, 1.0);
    }

    private static List<BigDecimal> list(long... v) {
        List<BigDecimal> out = new ArrayList<>();
        for (long x : v) out.add(BigDecimal.valueOf(x));
        return out;
    }

    private static Scenario base() {
        return new Scenario(BigDecimal.ZERO, BigDecimal.ZERO, true);
    }

    @Test
    void scheduledInflowUsesCollectionRateAndUnpaidGoesToArrears() {
        var in = new Inputs(YearMonth.of(2026, 1), 2, list(1000, 1000), list(200, 200),
                BigDecimal.ZERO, BigDecimal.valueOf(2000), new BigDecimal("0.9"), new BigDecimal("0.5"),
                BigDecimal.ZERO, BigDecimal.valueOf(20), 10, FLAT);
        var pts = ForecastCalculator.project(in, base());
        assertThat(pts.get(0).fromSchedule()).isEqualByComparingTo("900");
        assertThat(pts.get(0).fromArrears()).isEqualByComparingTo("0");
        assertThat(pts.get(0).arrearsEnd()).isEqualByComparingTo("100");
        // 2-oy: muddati o'tgan 100 ning yarmi undiriladi
        assertThat(pts.get(1).fromArrears()).isEqualByComparingTo("50");
        // foyda — ustama ulushi (20%)
        assertThat(pts.get(0).profit()).isEqualByComparingTo("180");
    }

    @Test
    void newSalesPayBackFromNextMonthWithMarkupAndNoPenalty() {
        var in = new Inputs(YearMonth.of(2026, 1), 3, list(0, 0, 0), list(0, 0, 0),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.valueOf(1200), BigDecimal.valueOf(20), 12, FLAT);
        var pts = ForecastCalculator.project(in, base());
        // 1-oyda faqat chiqim: 1200 savdo
        assertThat(pts.get(0).newSales()).isEqualByComparingTo("1200");
        assertThat(pts.get(0).fromNewSales()).isEqualByComparingTo("0");
        assertThat(pts.get(0).netCash()).isEqualByComparingTo("-1200");
        // 2-oy: 1200 × 1.2 / 12 = 120
        assertThat(pts.get(1).fromNewSales()).isEqualByComparingTo("120");
        // 3-oy: ikki oylik savdo to'lovlari
        assertThat(pts.get(2).fromNewSales()).isEqualByComparingTo("240");
        // qarz faqat savdo narxi bo'yicha o'sadi (jarima yo'q)
        assertThat(pts.get(0).debtEnd()).isEqualByComparingTo("1440");
    }

    @Test
    void seasonalityAndGrowthScaleSales() {
        double[] s = FLAT.clone();
        s[11] = 1.5; // dekabr
        var in = new Inputs(YearMonth.of(2026, 12), 1, list(0), list(0), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.valueOf(1000), BigDecimal.TEN, 6, s);
        var pts = ForecastCalculator.project(in, new Scenario(BigDecimal.valueOf(10), BigDecimal.ZERO, true));
        assertThat(pts.get(0).newSales()).isEqualByComparingTo("1650");
    }

    @Test
    void deseasonalizedAverage() {
        double[] s = FLAT.clone();
        s[11] = 2.0;
        var avg = ForecastCalculator.deseasonalizedAverage(
                List.of(YearMonth.of(2025, 11), YearMonth.of(2025, 12)), list(100, 200), s);
        assertThat(avg).isEqualByComparingTo("100");
    }

    @Test
    void suggestSeasonalityNeedsFullYear() {
        List<YearMonth> months = new ArrayList<>();
        List<BigDecimal> sales = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            months.add(YearMonth.of(2026, i + 1));
            sales.add(BigDecimal.valueOf(100));
        }
        assertThat(ForecastCalculator.suggestSeasonality(months, sales)).isNull();

        months.clear();
        sales.clear();
        for (int i = 0; i < 12; i++) {
            months.add(YearMonth.of(2025, i + 1));
            sales.add(BigDecimal.valueOf(i == 11 ? 200 : 100));
        }
        double[] s = ForecastCalculator.suggestSeasonality(months, sales);
        assertThat(s).isNotNull();
        assertThat(s[11]).isGreaterThan(s[0]);
        assertThat(Arrays.stream(s).average().orElse(0)).isCloseTo(1.0, within(0.02));
    }
}

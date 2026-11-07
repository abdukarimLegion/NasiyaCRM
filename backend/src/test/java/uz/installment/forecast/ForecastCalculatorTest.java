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

    private static Inputs flat() {
        return new Inputs(YearMonth.of(2026, 1), 3, list(1000, 1000, 1000), list(200, 200, 200),
                BigDecimal.valueOf(500), BigDecimal.valueOf(5000), new BigDecimal("0.9"), new BigDecimal("0.5"),
                BigDecimal.valueOf(1000), BigDecimal.valueOf(20), 4, FLAT_ONES);
    }

    private static final double[] FLAT_ONES = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

    @Test
    void stressReducesInflowAndProfit() {
        var low = ForecastCalculator.totals(ForecastCalculator.project(flat(), base()));
        var high = ForecastCalculator.totals(ForecastCalculator.project(flat(),
                base().withStress(ForecastCalculator.Stress.HIGH)));
        assertThat(high.inflow()).isLessThan(low.inflow());
        assertThat(high.profit()).isLessThan(low.profit());
        assertThat(high.arrearsEnd()).isGreaterThan(low.arrearsEnd());
    }

    @Test
    void partnerShareMovesSalesToPartnersAndAddsMargin() {
        var sc = new Scenario(BigDecimal.ZERO, BigDecimal.ZERO, true, null, null, 1.0,
                ForecastCalculator.Stress.LOW, BigDecimal.valueOf(40), BigDecimal.valueOf(15));
        var own = ForecastCalculator.totals(ForecastCalculator.project(flat(), base()));
        var withPartner = ForecastCalculator.totals(ForecastCalculator.project(flat(), sc));
        // yangi savdoning 40% hamkorga o'tadi: o'z savdosi 60%, hamkor hajmi 40%
        assertThat(withPartner.newSales().doubleValue()).isCloseTo(own.newSales().doubleValue() * 0.6, within(3.0));
        assertThat(withPartner.partnerVolume().doubleValue()).isCloseTo(own.newSales().doubleValue() * 0.4, within(3.0));
        // hamkor marjasi 15%: hajm × 0.15
        assertThat(withPartner.partnerProfit().doubleValue())
                .isCloseTo(withPartner.partnerVolume().doubleValue() * 0.15, within(3.0));
        assertThat(own.partnerVolume()).isEqualByComparingTo("0");
    }

    @Test
    void markupOverrideRaisesProfit() {
        var def = ForecastCalculator.totals(ForecastCalculator.project(flat(), base()));
        var higher = ForecastCalculator.totals(ForecastCalculator.project(flat(),
                new Scenario(BigDecimal.ZERO, BigDecimal.ZERO, true, BigDecimal.valueOf(40), null, 1.0,
                        ForecastCalculator.Stress.LOW, BigDecimal.ZERO, BigDecimal.valueOf(15))));
        assertThat(higher.profit()).isGreaterThan(def.profit());
    }
}

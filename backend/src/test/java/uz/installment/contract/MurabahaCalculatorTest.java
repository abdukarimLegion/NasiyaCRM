package uz.installment.contract;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MurabahaCalculatorTest {

    private static final LocalDate FIRST = LocalDate.of(2026, 11, 10);

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void matchesPrototypeWithoutDownPayment() {
        // Prototip: iPhone 15, 11 500 000, 20%, 12 oy -> sale 13 800 000, oylik 1 150 000
        var q = MurabahaCalculator.calculate(bd(11_500_000), BigDecimal.ZERO, bd(20), 12, FIRST, 1);
        assertEquals(0, q.salePrice().compareTo(bd(13_800_000)));
        assertEquals(0, q.markupAmount().compareTo(bd(2_300_000)));
        assertEquals(0, q.monthlyPayment().compareTo(bd(1_150_000)));
        assertEquals(0, q.lastPayment().compareTo(bd(1_150_000)));
        assertEquals(12, q.schedule().size());
    }

    @Test
    void scheduleSumsToInstallmentTotal() {
        var q = MurabahaCalculator.calculate(bd(7_900_000), bd(1_000_000), bd(19), 9, FIRST, 1000);
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal markup = BigDecimal.ZERO;
        for (var i : q.schedule()) {
            sum = sum.add(i.amount());
            principal = principal.add(i.principalPart());
            markup = markup.add(i.markupPart());
            assertEquals(0, i.amount().compareTo(i.principalPart().add(i.markupPart())));
            assertTrue(i.principalPart().signum() >= 0 && i.markupPart().signum() >= 0);
        }
        assertEquals(0, sum.compareTo(q.installmentTotal()));
        assertEquals(0, principal.compareTo(bd(6_900_000)));
        assertEquals(0, markup.compareTo(q.markupAmount()));
        // markup faqat moliyalashtirilgan qismga: 6 900 000 * 19% = 1 311 000
        assertEquals(0, q.markupAmount().compareTo(bd(1_311_000)));
        assertEquals(0, q.salePrice().compareTo(bd(9_211_000)));
        // oylik 1000 ga yaxlitlangan
        assertEquals(0, q.monthlyPayment().remainder(bd(1000)).signum());
    }

    @Test
    void dueDatesAreMonthly() {
        var q = MurabahaCalculator.calculate(bd(4_200_000), BigDecimal.ZERO, bd(22), 3, LocalDate.of(2026, 1, 31), 1);
        assertEquals(LocalDate.of(2026, 1, 31), q.schedule().get(0).dueDate());
        assertEquals(LocalDate.of(2026, 2, 28), q.schedule().get(1).dueDate());
        assertEquals(LocalDate.of(2026, 3, 31), q.schedule().get(2).dueDate());
    }

    @Test
    void hugeRoundingStepNeverProducesNonPositiveLastPayment() {
        var q = MurabahaCalculator.calculate(bd(1_000_000), BigDecimal.ZERO, bd(10), 12, FIRST, 100_000);
        assertTrue(q.lastPayment().signum() > 0);
        BigDecimal sum = q.schedule().stream().map(MurabahaCalculator.Installment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(q.installmentTotal()));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> MurabahaCalculator.calculate(bd(1000), bd(1000), bd(10), 3, FIRST, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MurabahaCalculator.calculate(bd(1000), BigDecimal.ZERO, bd(101), 3, FIRST, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MurabahaCalculator.calculate(bd(1000), BigDecimal.ZERO, bd(10), 0, FIRST, 1));
    }
}

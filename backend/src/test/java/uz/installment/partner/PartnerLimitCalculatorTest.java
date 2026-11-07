package uz.installment.partner;

import org.junit.jupiter.api.Test;
import uz.installment.partner.PartnerLimitCalculator.Offer;
import uz.installment.partner.PartnerLimitCalculator.Terms;
import uz.installment.scoring.ScoringEngine.RiskCategory;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartnerLimitCalculatorTest {

    private static final Terms ALIF = new Terms("ALIF", "Alif", bd(12), 12, bd(0), bd(45));
    private static final Terms TBC = new Terms("TBC", "TBC", bd(9), 18, bd(20), bd(35));

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void limitIsIncomeTimesDtiTimesRiskTimesTerm() {
        // 10 mln × 45% × 1.0 = 4.5 mln oylik; × 12 oy = 54 mln
        Offer o = PartnerLimitCalculator.offer(ALIF, bd(10_000_000), RiskCategory.A, bd(8_000_000), bd(8_000_000),
                BigDecimal.ZERO, 12);
        assertEquals(0, bd(4_500_000).compareTo(o.maxMonthly()));
        assertEquals(0, bd(54_000_000).compareTo(o.limit()));
        assertTrue(o.eligible());
        // komissiya 12% × 8 mln = 960 ming
        assertEquals(0, bd(960_000).compareTo(o.commission()));
    }

    @Test
    void riskLowersLimitAndDIsRejected() {
        Offer b = PartnerLimitCalculator.offer(ALIF, bd(10_000_000), RiskCategory.B, bd(1_000_000), bd(1_000_000), BigDecimal.ZERO, 6);
        assertEquals(0, bd(3_600_000).compareTo(b.maxMonthly()));
        Offer d = PartnerLimitCalculator.offer(ALIF, bd(10_000_000), RiskCategory.D, bd(1_000_000), bd(1_000_000), BigDecimal.ZERO, 6);
        assertFalse(d.eligible());
        assertEquals("RISK", d.reason());
    }

    @Test
    void termDownPaymentAndLimitChecks() {
        assertEquals("TERM", PartnerLimitCalculator.offer(ALIF, bd(10_000_000), RiskCategory.A, bd(5_000_000),
                bd(5_000_000), BigDecimal.ZERO, 18).reason());
        // TBC: minimal boshlang'ich to'lov 20%
        assertEquals("DOWN", PartnerLimitCalculator.offer(TBC, bd(10_000_000), RiskCategory.A, bd(5_000_000),
                bd(5_000_000), BigDecimal.ZERO, 12).reason());
        assertEquals("OK", PartnerLimitCalculator.offer(TBC, bd(10_000_000), RiskCategory.A, bd(5_000_000),
                bd(4_000_000), bd(1_000_000), 12).reason());
        assertEquals("LIMIT", PartnerLimitCalculator.offer(ALIF, bd(1_000_000), RiskCategory.A, bd(9_000_000),
                bd(9_000_000), BigDecimal.ZERO, 12).reason());
    }

    @Test
    void recommendsInternalThenCheapestPartnerThenNothing() {
        Offer cheap = new Offer("TBC", "TBC", bd(1), bd(1), 18, bd(9), bd(20), true, "OK", bd(0));
        Offer pricey = new Offer("ALIF", "Alif", bd(1), bd(1), 12, bd(12), bd(0), true, "OK", bd(0));
        Offer bad = new Offer("UZUM", "Uzum", bd(1), bd(1), 12, bd(5), bd(0), false, "LIMIT", bd(0));
        assertEquals("INTERNAL", PartnerLimitCalculator.recommend(true, List.of(cheap)).provider());
        assertEquals("TBC", PartnerLimitCalculator.recommend(false, List.of(pricey, cheap, bad)).provider());
        var none = PartnerLimitCalculator.recommend(false, List.of(bad));
        assertNull(none.provider());
        assertEquals("NO_LIMIT", none.reason());
    }
}

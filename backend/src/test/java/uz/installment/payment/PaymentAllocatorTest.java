package uz.installment.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentAllocatorTest {

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private final List<PaymentAllocator.Due> dues = List.of(
            new PaymentAllocator.Due(3, 3, bd(1000), bd(0)),
            new PaymentAllocator.Due(1, 1, bd(1000), bd(1000)),
            new PaymentAllocator.Due(2, 2, bd(1000), bd(400)));

    @Test
    void paysOldestFirstAndSpillsOver() {
        var a = PaymentAllocator.allocate(bd(900), dues);
        assertEquals(2, a.size());
        assertEquals(2L, a.get(0).scheduleItemId());
        assertEquals(0, a.get(0).amount().compareTo(bd(600)));
        assertEquals(3L, a.get(1).scheduleItemId());
        assertEquals(0, a.get(1).amount().compareTo(bd(300)));
    }

    @Test
    void rejectsOverpayment() {
        assertThrows(IllegalArgumentException.class, () -> PaymentAllocator.allocate(bd(1601), dues));
    }

    @Test
    void exactPayoff() {
        var a = PaymentAllocator.allocate(bd(1600), dues);
        assertEquals(2, a.size());
    }
}

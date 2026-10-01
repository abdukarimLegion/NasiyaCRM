package uz.installment.payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * To'lovni jadval bo'yicha taqsimlash (sof funksiya).
 * Qoida: eng eski to'lanmagan oydan boshlab yopiladi. Ortiqcha summa
 * keyingi oylarga o'tadi; butun qarzdan ortiq to'lov qabul qilinmaydi.
 */
public final class PaymentAllocator {

    private PaymentAllocator() {
    }

    public record Due(long scheduleItemId, int seq, BigDecimal amount, BigDecimal paidAmount) {
        BigDecimal remaining() {
            return amount.subtract(paidAmount);
        }
    }

    public record Allocation(long scheduleItemId, BigDecimal amount) {
    }

    public static List<Allocation> allocate(BigDecimal payment, List<Due> dues) {
        if (payment == null || payment.signum() <= 0) {
            throw new IllegalArgumentException("To'lov summasi musbat bo'lishi kerak");
        }
        BigDecimal outstanding = dues.stream().map(Due::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (payment.compareTo(outstanding) > 0) {
            throw new IllegalArgumentException("To'lov qolgan qarzdan (" + outstanding.toPlainString() + ") katta");
        }
        List<Due> ordered = new ArrayList<>(dues);
        ordered.sort(Comparator.comparingInt(Due::seq));

        List<Allocation> result = new ArrayList<>();
        BigDecimal left = payment;
        for (Due d : ordered) {
            if (left.signum() == 0) break;
            BigDecimal rem = d.remaining();
            if (rem.signum() <= 0) continue;
            BigDecimal part = left.min(rem);
            result.add(new Allocation(d.scheduleItemId(), part));
            left = left.subtract(part);
        }
        return result;
    }
}

package uz.installment.limits;

import java.math.BigDecimal;

/**
 * Bitta platformadagi limit holati. Faqat ma'lumot uchun — skoring va ichki kredit limitiga ta'sir qilmaydi.
 *
 * @param available {@code total - used}; limit bo'lmasa 0
 */
public record ExternalLimit(LimitProvider provider, String providerName, Status status, BigDecimal total,
                            BigDecimal used, BigDecimal available, Integer maxTermMonths, String message) {

    public enum Status {
        /** Limit ochilgan. */
        AVAILABLE,
        /** Mijoz platformada bor, lekin limit berilmagan yoki to'xtatilgan. */
        NO_LIMIT,
        /** Mijoz platformada ro'yxatdan o'tmagan. */
        NOT_REGISTERED,
        /** Platforma javob bermadi. */
        ERROR
    }

    public static ExternalLimit available(LimitProvider p, BigDecimal total, BigDecimal used, int maxTermMonths) {
        return new ExternalLimit(p, p.title(), Status.AVAILABLE, total, used, total.subtract(used).max(BigDecimal.ZERO),
                maxTermMonths, null);
    }

    public static ExternalLimit of(LimitProvider p, Status status, String message) {
        return new ExternalLimit(p, p.title(), status, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, message);
    }
}

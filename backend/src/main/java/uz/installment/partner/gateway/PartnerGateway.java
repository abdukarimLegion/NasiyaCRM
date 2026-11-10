package uz.installment.partner.gateway;

import java.math.BigDecimal;
import java.util.List;

/**
 * Nasiya beruvchi hamkor (Uzum, Alif, Anor, Payme ...) bilan integratsiya nuqtasi.
 *
 * <p><b>Haqiqiy integratsiyani ulash:</b> shu interfeysni amalga oshiruvchi Spring bean yozing va
 * {@link #supports(String)} da hamkor kodini ({@code partners.code}, masalan {@code "ALIF"}) qaytaring.
 * {@link PartnerGateways} uni avtomatik topadi; shu hamkor uchun {@link MockPartnerGateway} endi ishlatilmaydi.
 * Controller, servis va interfeys (UI) kodi o'zgarmaydi.
 *
 * <p>Metodlar exception tashlamasligi kerak: xatolik natijaning holatida qaytariladi.
 */
public interface PartnerGateway {

    /** Shu gateway qaysi hamkor kodiga xizmat qiladi. */
    boolean supports(String partnerCode);

    /** Mijozning hamkordagi limiti (JShShIR bo'yicha). */
    LimitResult checkLimit(String partnerCode, String pinfl);

    /** Hamkor tizimida savdo buyurtmasini ochadi; hamkor mijozga tasdiqlash kodi (SMS) yuboradi. */
    OrderResult createOrder(OrderRequest request);

    /** Mijoz aytgan SMS kod bilan buyurtmani tasdiqlaydi. Tasdiqlangach tovar topshiriladi. */
    OrderResult confirmOrder(String partnerCode, String transactionId, String smsCode);

    /** true — javoblar soxta (namoyish), haqiqiy hamkor tizimidan olinmagan. */
    boolean isMock();

    enum LimitStatus {
        /** Limit ochilgan. */
        AVAILABLE,
        /** Mijoz hamkorda bor, lekin limit berilmagan yoki to'xtatilgan. */
        NO_LIMIT,
        /** Mijoz hamkorda ro'yxatdan o'tmagan. */
        NOT_REGISTERED,
        /** Hamkor javob bermadi. */
        ERROR
    }

    record LimitResult(LimitStatus status, BigDecimal total, BigDecimal used, Integer maxTermMonths, String message) {

        public static LimitResult of(LimitStatus status, String message) {
            return new LimitResult(status, BigDecimal.ZERO, BigDecimal.ZERO, null, message);
        }

        public BigDecimal available() {
            return total.subtract(used).max(BigDecimal.ZERO);
        }
    }

    record OrderItem(String name, int quantity, BigDecimal unitPrice) {
    }

    record OrderRequest(String partnerCode, String pinfl, String phone, String applicationNo, BigDecimal goodsTotal,
                        BigDecimal downPayment, int termMonths, List<OrderItem> items) {
    }

    enum OrderStatus {
        /** Buyurtma ochildi, mijozga tasdiqlash kodi yuborildi. */
        CODE_SENT,
        /** Kod to'g'ri — hamkor savdoni tasdiqladi. */
        CONFIRMED,
        /** Hamkor rad etdi (limit yetmaydi, kod noto'g'ri ...). */
        REJECTED,
        /** Hamkor javob bermadi. */
        ERROR
    }

    record OrderResult(OrderStatus status, String transactionId, String message) {
    }
}

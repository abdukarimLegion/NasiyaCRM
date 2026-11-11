package uz.installment.payment;

import java.math.BigDecimal;

/**
 * Mijozga yuboriladigan tezkor to'lov havolasi (Click / Payme / Uzum ...). Hozircha
 * {@link MockPaymentLinkProvider}; haqiqiy to'lov tizimi uchun shu interfeysni amalga oshirib,
 * bean'ni {@code @Primary} qiling.
 */
public interface PaymentLinkProvider {

    String link(String contractNo, BigDecimal amount);

    /** true — havola namoyish uchun, u orqali to'lov qabul qilinmaydi. */
    boolean isMock();
}

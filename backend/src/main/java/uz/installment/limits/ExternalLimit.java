package uz.installment.limits;

import uz.installment.partner.gateway.PartnerGateway.LimitStatus;

import java.math.BigDecimal;

/**
 * Bitta hamkor platformadagi limit holati. Faqat ma'lumot uchun — skoring va ichki kredit limitiga ta'sir qilmaydi.
 *
 * @param provider  hamkor kodi ({@code partners.code})
 * @param available {@code total - used}; limit bo'lmasa 0
 * @param mock      true — namoyish javobi (shu hamkor uchun haqiqiy integratsiya hali ulanmagan)
 */
public record ExternalLimit(String provider, String providerName, LimitStatus status, BigDecimal total,
                            BigDecimal used, BigDecimal available, Integer maxTermMonths, boolean mock,
                            String message) {
}

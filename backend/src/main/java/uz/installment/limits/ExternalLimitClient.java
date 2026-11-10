package uz.installment.limits;

import java.util.List;

/**
 * Tashqi nasiya platformalaridan mijoz limitini JShShIR bo'yicha so'rovchi adapter.
 * Hozircha yagona implementatsiya — {@link MockExternalLimitClient}; haqiqiy integratsiya qo'shilganda
 * shu interfeysning yangi implementatsiyasi yoziladi, qolgan kod o'zgarmaydi.
 */
public interface ExternalLimitClient {

    /** Har bir platforma uchun bittadan natija. Exception tashlamaydi: xatolik {@code ERROR} holati bilan qaytadi. */
    List<ExternalLimit> check(String pinfl);

    /** true — natijalar soxta (namoyish), haqiqiy platformalardan olinmagan. */
    boolean isMock();
}

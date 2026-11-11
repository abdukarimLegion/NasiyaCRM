package uz.installment.collection;

import java.math.BigDecimal;

/**
 * Da'vo arizasini elektron sud tizimiga (E-SUD) topshirish. Hozircha {@link MockCourtFilingGateway};
 * haqiqiy integratsiya uchun shu interfeysni amalga oshirib, bean'ni {@code @Primary} qiling.
 */
public interface CourtFilingGateway {

    record Filing(boolean accepted, String reference, String message) {
    }

    Filing submit(String contractNo, String debtorPinfl, BigDecimal claimAmount, String courtName);

    /** true — javob namoyish (soxta), haqiqiy E-SUD'ga yuborilmagan. */
    boolean isMock();
}

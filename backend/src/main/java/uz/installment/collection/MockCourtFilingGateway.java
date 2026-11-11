package uz.installment.collection;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Namoyish: hech qayerga yubormaydi, shartli ro'yxat raqamini qaytaradi. */
@Component
public class MockCourtFilingGateway implements CourtFilingGateway {

    @Override
    public Filing submit(String contractNo, String debtorPinfl, BigDecimal claimAmount, String courtName) {
        return new Filing(true, "MOCK-ESUD-" + contractNo,
                "DEMO: ariza haqiqiy E-SUD tizimiga yuborilmadi (integratsiya hali ulanmagan)");
    }

    @Override
    public boolean isMock() {
        return true;
    }
}

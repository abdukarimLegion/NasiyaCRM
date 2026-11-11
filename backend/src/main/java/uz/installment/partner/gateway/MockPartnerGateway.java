package uz.installment.partner.gateway;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Namoyish gateway'i: hech qaysi hamkorga murojaat qilmaydi. Haqiqiy gateway'i yo'q har bir hamkor uchun
 * ishlatiladi. Limit JShShIR va hamkor kodidan hisoblanadi (bir mijoz uchun har safar bir xil chiqadi);
 * buyurtma har doim ochiladi; tasdiqlash kodi sifatida 4–6 xonali istalgan raqam qabul qilinadi,
 * {@code 0000} — rad etishni sinash uchun.
 */
@Component
public class MockPartnerGateway implements PartnerGateway {

    public static final String MOCK_NOTE = "DEMO: namoyish javobi (haqiqiy hamkor tizimi emas)";
    private static final BigDecimal STEP = BigDecimal.valueOf(500_000);
    private static final int[] TERMS = {6, 9, 12, 18, 24};

    @Override
    public boolean supports(String partnerCode) {
        return true;
    }

    @Override
    public LimitResult checkLimit(String partnerCode, String pinfl) {
        long h = hash(pinfl + "|" + partnerCode);
        int bucket = (int) (h % 100);
        if (bucket < 20) {
            return LimitResult.of(LimitStatus.NOT_REGISTERED, MOCK_NOTE);
        }
        if (bucket < 32) {
            return LimitResult.of(LimitStatus.NO_LIMIT, MOCK_NOTE);
        }
        // 2 – 30 mln so'm, 500 ming qadam bilan; 0 – 80 % ishlatilgan
        BigDecimal total = STEP.multiply(BigDecimal.valueOf(4 + (h / 100) % 57));
        long usedSteps = total.divide(STEP).longValue() * ((h / 10_000) % 5) / 5;
        return new LimitResult(LimitStatus.AVAILABLE, total, STEP.multiply(BigDecimal.valueOf(usedSteps)),
                TERMS[(int) ((h / 100_000) % TERMS.length)], MOCK_NOTE);
    }

    @Override
    public OrderResult createOrder(OrderRequest r) {
        String tx = "MOCK-" + r.partnerCode() + "-" + Long.toString(hash(r.applicationNo() + "|" + r.pinfl()), 36)
                .toUpperCase();
        return new OrderResult(OrderStatus.CODE_SENT, tx, MOCK_NOTE + ". Istalgan 4–6 xonali kod qabul qilinadi.");
    }

    @Override
    public OrderResult confirmOrder(String partnerCode, String transactionId, String smsCode) {
        String code = smsCode == null ? "" : smsCode.trim();
        if (!code.matches("\\d{4,6}") || code.matches("0+")) {
            return new OrderResult(OrderStatus.REJECTED, transactionId, "Tasdiqlash kodi noto'g'ri");
        }
        return new OrderResult(OrderStatus.CONFIRMED, transactionId, MOCK_NOTE);
    }

    @Override
    public boolean isMock() {
        return true;
    }

    private static long hash(String s) {
        CRC32 crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}

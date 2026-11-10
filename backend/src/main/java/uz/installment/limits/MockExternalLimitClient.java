package uz.installment.limits;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Namoyish uchun: hech qaysi platformaga murojaat qilmaydi. Natija JShShIR va platformadan hisoblanadi,
 * shuning uchun bir mijoz uchun har safar bir xil chiqadi. Haqiqiy integratsiya ulanganda
 * {@code APP_LIMITS_PROVIDER} boshqa qiymatga o'zgartiriladi va bu sinf o'chadi.
 */
@Component
@ConditionalOnProperty(name = "app.limits.provider", havingValue = "mock", matchIfMissing = true)
public class MockExternalLimitClient implements ExternalLimitClient {

    private static final BigDecimal STEP = BigDecimal.valueOf(500_000);
    private static final int[] TERMS = {6, 9, 12, 18, 24};

    @Override
    public List<ExternalLimit> check(String pinfl) {
        List<ExternalLimit> out = new ArrayList<>();
        for (LimitProvider p : LimitProvider.values()) {
            out.add(one(pinfl, p));
        }
        return out;
    }

    @Override
    public boolean isMock() {
        return true;
    }

    private static ExternalLimit one(String pinfl, LimitProvider p) {
        long h = hash(pinfl + "|" + p.name());
        int bucket = (int) (h % 100);
        if (bucket < 20) {
            return ExternalLimit.of(p, ExternalLimit.Status.NOT_REGISTERED, null);
        }
        if (bucket < 32) {
            return ExternalLimit.of(p, ExternalLimit.Status.NO_LIMIT, null);
        }
        // 2 – 30 mln so'm, 500 ming qadam bilan; 0 – 80 % ishlatilgan
        BigDecimal total = STEP.multiply(BigDecimal.valueOf(4 + (h / 100) % 57));
        long usedSteps = total.divide(STEP).longValue() * ((h / 10_000) % 5) / 5;
        BigDecimal used = STEP.multiply(BigDecimal.valueOf(usedSteps));
        return ExternalLimit.available(p, total, used, TERMS[(int) ((h / 100_000) % TERMS.length)]);
    }

    private static long hash(String s) {
        CRC32 crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}

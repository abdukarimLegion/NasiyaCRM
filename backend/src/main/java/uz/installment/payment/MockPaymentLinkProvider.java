package uz.installment.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Namoyish havolasi: APP_PAY_LINK_BASE manziliga shartnoma raqami va summa qo'shiladi; to'lov qabul qilinmaydi. */
@Component
public class MockPaymentLinkProvider implements PaymentLinkProvider {

    private final String base;

    public MockPaymentLinkProvider(@Value("${app.pay-link-base:https://pay.example.uz}") String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override
    public String link(String contractNo, BigDecimal amount) {
        return base + "/c/" + URLEncoder.encode(contractNo, StandardCharsets.UTF_8) + "?amount="
                + amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    public boolean isMock() {
        return true;
    }
}

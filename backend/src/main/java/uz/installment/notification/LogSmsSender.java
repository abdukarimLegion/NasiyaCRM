package uz.installment.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Dev/test uchun: SMS yuborilmaydi, faqat logga yoziladi. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {

    @Override
    public Result send(String recipient, String text) {
        log.info("[SMS-LOG] {} <- {}", recipient, text);
        return Result.ok("log-" + UUID.randomUUID());
    }

    @Override
    public boolean isConfigured() {
        return true;
    }
}

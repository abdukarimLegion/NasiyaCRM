package uz.installment.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Namoyish: xat yuborilmaydi, faqat logga yoziladi. */
@Slf4j
@Component
public class LogEmailSender implements EmailSender {

    @Override
    public MessageSender.Result send(String to, String subject, String text) {
        log.info("[EMAIL-DEMO] to={} subject={} ({} belgi)", to, subject, text == null ? 0 : text.length());
        return MessageSender.Result.ok("demo");
    }

    @Override
    public boolean isMock() {
        return true;
    }
}

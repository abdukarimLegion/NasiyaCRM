package uz.installment.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.config.AppProperties;

/** Bazada birorta ham foydalanuvchi bo'lmasa, .env dagi login/parol bilan ADMIN yaratadi. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        var cfg = props.bootstrapAdmin();
        if (cfg == null || cfg.password() == null || cfg.password().isBlank()) {
            log.warn("Foydalanuvchilar yo'q va APP_ADMIN_PASSWORD berilmagan — admin yaratilmadi.");
            return;
        }
        User admin = new User();
        admin.setUsername(cfg.username());
        admin.setPasswordHash(passwordEncoder.encode(cfg.password()));
        admin.setFullName("Administrator");
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.info("Boshlang'ich admin yaratildi: {}", cfg.username());
    }
}

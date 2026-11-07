package uz.installment.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.config.AppProperties;

/** Bazada birorta ham administrator bo'lmasa, .env dagi login/parol bilan ADMIN yaratadi. */
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
        // Birorta ham administrator bo'lmasa yaratiladi (demo xodimlar oldin qo'shilgan bo'lsa ham)
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        var cfg = props.bootstrapAdmin();
        if (cfg == null || cfg.password() == null || cfg.password().isBlank()) {
            log.warn("Administrator yo'q va APP_ADMIN_PASSWORD berilmagan — admin yaratilmadi.");
            return;
        }
        if (users.existsByUsername(cfg.username())) {
            log.warn("Administrator yo'q, lekin '{}' logini band — admin yaratilmadi.", cfg.username());
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

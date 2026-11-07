package uz.installment.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login brute-force himoyasi (bitta instansiya uchun xotirada).
 *
 * <p>Bitta IP + login uchun 15 daqiqada 5 urinish, bitta IP uchun jami 30 urinish.
 * Muvaffaqiyatli kirish hisoblagichni tozalaydi. Ko'p instansiyali SaaS'da
 * bu qiymat Redis'ga ko'chirilishi kerak.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS_PER_USER = 5;
    static final int MAX_ATTEMPTS_PER_IP = 30;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void check(String ip, String username) {
        Instant now = Instant.now(clock);
        if (limited(userKey(ip, username), MAX_ATTEMPTS_PER_USER, now)
                || limited(ipKey(ip), MAX_ATTEMPTS_PER_IP, now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Juda ko'p kirish urinishi. 15 daqiqadan keyin qayta urinib ko'ring");
        }
        prune(now);
    }

    public void failure(String ip, String username) {
        Instant now = Instant.now(clock);
        increment(userKey(ip, username), now);
        increment(ipKey(ip), now);
    }

    public void success(String ip, String username) {
        attempts.remove(userKey(ip, username));
    }

    private boolean limited(String key, int max, Instant now) {
        Attempt a = attempts.get(key);
        return a != null && !now.isAfter(a.windowStart.plus(WINDOW)) && a.count >= max;
    }

    private void increment(String key, Instant now) {
        attempts.compute(key, (k, old) -> {
            if (old == null || now.isAfter(old.windowStart.plus(WINDOW))) {
                Attempt fresh = new Attempt(now);
                fresh.count = 1;
                return fresh;
            }
            old.count++;
            return old;
        });
    }

    /** Xotira cheksiz o'smasligi uchun eskirgan yozuvlarni vaqti-vaqti bilan tozalaymiz. */
    private void prune(Instant now) {
        if (attempts.size() < 1000) {
            return;
        }
        attempts.entrySet().removeIf(e -> now.isAfter(e.getValue().windowStart.plus(WINDOW)));
    }

    private static String userKey(String ip, String username) {
        return "u|" + ipKey(ip) + "|" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private static final class Attempt {
        private final Instant windowStart;
        private int count;

        private Attempt(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}

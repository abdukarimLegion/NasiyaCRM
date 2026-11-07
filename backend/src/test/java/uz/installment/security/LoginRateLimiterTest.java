package uz.installment.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    @Test
    void blocksAfterFiveFailuresForSameUser() {
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_USER; i++) {
            limiter.failure("10.0.0.1", "admin");
        }
        assertThrows(ResponseStatusException.class, () -> limiter.check("10.0.0.1", "admin"));
    }

    @Test
    void successfulLoginClearsUserCounter() {
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_USER; i++) {
            limiter.failure("10.0.0.2", "admin");
        }
        limiter.success("10.0.0.2", "admin");
        assertDoesNotThrow(() -> limiter.check("10.0.0.2", "admin"));
    }

    @Test
    void differentUsersFromOneIpHaveSeparateCounters() {
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_USER; i++) {
            limiter.failure("10.0.0.3", "admin");
        }
        assertDoesNotThrow(() -> limiter.check("10.0.0.3", "cashier"));
    }
}

package uz.installment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        List<String> corsOrigins,
        Jwt jwt,
        BootstrapAdmin bootstrapAdmin,
        Sms sms,
        Telegram telegram,
        Jobs jobs) {

    public record Jwt(String secret, int ttlHours) {
    }

    public record BootstrapAdmin(String username, String password) {
    }

    public record Sms(String provider, Eskiz eskiz) {
    }

    public record Eskiz(String baseUrl, String email, String password, String from) {
    }

    public record Telegram(String botToken) {
    }

    public record Jobs(String dailyCron) {
    }
}

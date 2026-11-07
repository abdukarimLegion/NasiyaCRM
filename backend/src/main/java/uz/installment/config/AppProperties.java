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
        Jobs jobs,
        Mib mib) {

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

    /**
     * MIB (Majburiy ijro byurosi) integratsiyasi. provider: none (sozlanmagan — faqat qo'lda kiritish),
     * stub (demo: JShShIR oxirgi raqami 9 bo'lsa qarz bor) yoki http (umumiy HTTP adapter).
     * http uchun: url ({pinfl} o'rniga JShShIR qo'yiladi), method (GET/POST; POST tanasi {"pinfl":"..."}),
     * token va authHeader/authScheme, hamda javobdagi maydonlar yo'li (nuqtali, masalan "data.totalDebt").
     */
    public record Mib(String provider, String url, String method, String token, String authHeader,
                      String authScheme, Integer timeoutSeconds, String debtFlagPath, String amountPath,
                      String casesPath, String messagePath, Boolean notFoundMeansClear) {
    }
}

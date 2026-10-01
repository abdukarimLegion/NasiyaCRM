package uz.installment.notification;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uz.installment.config.AppProperties;

/**
 * Eskiz.uz SMS shlyuzi (https://notify.eskiz.uz).
 * DIQQAT: Eskiz'da SMS matnlari oldindan moderatsiyadan o'tgan bo'lishi kerak —
 * notification_templates dagi matnlarni Eskiz kabinetida tasdiqlating.
 * Token ~30 kun amal qiladi; 401 kelsa qayta login qilinadi.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "eskiz")
public class EskizSmsSender implements SmsSender {

    private final AppProperties.Eskiz cfg;
    private final RestClient http;
    private volatile String token;

    public EskizSmsSender(AppProperties props, RestClient.Builder builder) {
        this.cfg = props.sms().eskiz();
        this.http = builder.baseUrl(cfg.baseUrl()).build();
    }

    @Override
    public boolean isConfigured() {
        return notBlank(cfg.email()) && notBlank(cfg.password());
    }

    @Override
    public Result send(String recipient, String text) {
        if (!isConfigured()) {
            return Result.fail("Eskiz sozlanmagan (ESKIZ_EMAIL/ESKIZ_PASSWORD)");
        }
        try {
            return doSend(recipient, text, true);
        } catch (RestClientException e) {
            log.warn("Eskiz xatosi: {}", e.getMessage());
            return Result.fail(truncate(e.getMessage()));
        }
    }

    private Result doSend(String recipient, String text, boolean retryOn401) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mobile_phone", recipient.replaceAll("[^0-9]", ""));
        form.add("message", text);
        form.add("from", cfg.from());
        try {
            JsonNode res = http.post().uri("/message/sms/send")
                    .header("Authorization", "Bearer " + token())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String id = res != null && res.hasNonNull("id") ? res.get("id").asText() : null;
            return Result.ok(id);
        } catch (HttpClientErrorException.Unauthorized e) {
            if (!retryOn401) {
                throw e;
            }
            token = null;
            return doSend(recipient, text, false);
        }
    }

    private String token() {
        String t = token;
        if (t != null) {
            return t;
        }
        synchronized (this) {
            if (token == null) {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("email", cfg.email());
                form.add("password", cfg.password());
                JsonNode res = http.post().uri("/auth/login")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
                if (res == null || !res.path("data").hasNonNull("token")) {
                    throw new RestClientException("Eskiz login javobida token yo'q");
                }
                token = res.path("data").get("token").asText();
            }
            return token;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 500 ? s.substring(0, 500) : s);
    }
}

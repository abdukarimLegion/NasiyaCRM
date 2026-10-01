package uz.installment.notification;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uz.installment.config.AppProperties;

import java.util.Map;

/**
 * Telegram Bot API orqali xabar. recipient — mijozning chat_id si
 * (mijoz botga /start bosib, telefon raqamini ulashganda saqlanadi — bot keyingi bosqichda).
 */
@Slf4j
@Component
public class TelegramSender implements MessageSender {

    private final String botToken;
    private final RestClient http;

    public TelegramSender(AppProperties props, RestClient.Builder builder) {
        this.botToken = props.telegram() == null ? null : props.telegram().botToken();
        this.http = builder.baseUrl("https://api.telegram.org").build();
    }

    @Override
    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank();
    }

    @Override
    public Result send(String chatId, String text) {
        if (!isConfigured()) {
            return Result.fail("Telegram bot token berilmagan");
        }
        try {
            JsonNode res = http.post().uri("/bot{token}/sendMessage", botToken)
                    .body(Map.of("chat_id", chatId, "text", text))
                    .retrieve()
                    .body(JsonNode.class);
            String id = res != null ? res.path("result").path("message_id").asText(null) : null;
            return Result.ok(id);
        } catch (RestClientException e) {
            log.warn("Telegram xatosi: {}", e.getMessage());
            return Result.fail("Telegram: " + e.getClass().getSimpleName());
        }
    }
}

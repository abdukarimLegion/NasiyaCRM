package uz.installment.mib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import uz.installment.config.AppProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Umumiy HTTP adapter: sozlangan manzilga JShShIR bilan so'rov yuboradi va javobni
 * {@link MibResponseParser} orqali tahlil qiladi. MIB bilan shartnoma/kirish ma'lumotlari (manzil, token,
 * javob maydonlari) olingach, kod o'zgartirilmasdan faqat APP_MIB_* sozlamalari kiritiladi.
 * Tarmoq yoki server xatosi qarz yo'q deb talqin qilinmaydi — ERROR qaytariladi.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mib.provider", havingValue = "http")
public class HttpMibClient implements MibClient {

    private final AppProperties.Mib cfg;
    private final RestClient http;
    private final MibResponseParser.Paths paths;

    public HttpMibClient(AppProperties props, RestClient.Builder builder) {
        this.cfg = props.mib();
        var factory = new SimpleClientHttpRequestFactory();
        Duration t = Duration.ofSeconds(cfg.timeoutSeconds() == null ? 10 : cfg.timeoutSeconds());
        factory.setConnectTimeout(t);
        factory.setReadTimeout(t);
        this.http = builder.requestFactory(factory).build();
        this.paths = new MibResponseParser.Paths(cfg.debtFlagPath(), cfg.amountPath(), cfg.casesPath(), cfg.messagePath());
    }

    @Override
    public boolean isConfigured() {
        return cfg.url() != null && !cfg.url().isBlank();
    }

    @Override
    public String name() {
        return "http";
    }

    @Override
    public MibOutcome check(String pinfl) {
        if (!isConfigured()) {
            return MibOutcome.error("MIB manzili sozlanmagan (APP_MIB_URL)");
        }
        try {
            boolean post = "POST".equalsIgnoreCase(cfg.method());
            JsonNode body;
            if (post) {
                ObjectNode req = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("pinfl", pinfl);
                body = http.post().uri(url(pinfl)).headers(this::addAuth).contentType(MediaType.APPLICATION_JSON)
                        .body(req).retrieve().body(JsonNode.class);
            } else {
                body = http.get().uri(url(pinfl)).headers(this::addAuth).retrieve().body(JsonNode.class);
            }
            return MibResponseParser.parse(body, paths);
        } catch (HttpClientErrorException.NotFound e) {
            return cfg.notFoundMeansClear() == null || cfg.notFoundMeansClear()
                    ? MibOutcome.clear("MIB: ijro ishi topilmadi (404)")
                    : MibOutcome.error("MIB 404 qaytardi");
        } catch (RestClientException e) {
            log.warn("MIB so'rovi muvaffaqiyatsiz: {}", e.getMessage());
            return MibOutcome.error("MIB xizmati javob bermadi: " + shorten(e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("MIB javobi o'qilmadi", e);
            return MibOutcome.error("MIB javobini o'qib bo'lmadi: " + shorten(e.getMessage()));
        }
    }

    private URI url(String pinfl) {
        String u = cfg.url();
        if (u.contains("{pinfl}")) {
            return URI.create(u.replace("{pinfl}", pinfl));   // JShShIR — 14 ta raqam (bazada tekshirilgan)
        }
        return UriComponentsBuilder.fromUriString(u).queryParam("pinfl", pinfl).build().toUri();
    }

    private void addAuth(HttpHeaders headers) {
        if (cfg.token() != null && !cfg.token().isBlank()) {
            String header = cfg.authHeader() == null || cfg.authHeader().isBlank() ? "Authorization" : cfg.authHeader();
            String scheme = cfg.authScheme() == null ? "" : cfg.authScheme().trim();
            headers.set(header, scheme.isEmpty() ? cfg.token() : scheme + " " + cfg.token());
        }
    }

    private static String shorten(String s) {
        return s == null ? "" : s.length() > 200 ? s.substring(0, 200) : s;
    }
}

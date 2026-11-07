package uz.installment.mib;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import uz.installment.config.AppProperties;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Haqiqiy HTTP serverga (JDK) qarshi: URL shabloni, autentifikatsiya, 404 va server xatosi. */
class HttpMibClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/debts/", ex -> {
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            lastPath.set(ex.getRequestURI().getPath());
            String pinfl = ex.getRequestURI().getPath().substring("/api/debts/".length());
            int code = 200;
            String body;
            if (pinfl.endsWith("9")) {
                body = "{\"data\":{\"hasDebt\":true,\"totalDebt\":1200000,\"cases\":[{\"caseNo\":\"X-7\"}]}}";
            } else if (pinfl.endsWith("4")) {
                code = 404;
                body = "{}";
            } else if (pinfl.endsWith("5")) {
                code = 500;
                body = "{\"error\":\"boom\"}";
            } else {
                body = "{\"data\":{\"hasDebt\":false,\"totalDebt\":0,\"cases\":[]}}";
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(code, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpMibClient client(boolean notFoundClear) {
        var mib = new AppProperties.Mib("http", "http://127.0.0.1:" + server.getAddress().getPort() + "/api/debts/{pinfl}",
                "GET", "secret-token", "Authorization", "Bearer", 3, "data.hasDebt", "data.totalDebt", "data.cases",
                null, notFoundClear);
        return new HttpMibClient(new AppProperties(null, null, null, null, null, null, null, mib), RestClient.builder());
    }

    @Test
    void debtIsDetectedAndTokenSent() {
        var o = client(true).check("12345678901239");
        assertThat(o.status()).isEqualTo(MibOutcome.Status.DEBT);
        assertThat(o.totalDebt()).isEqualByComparingTo("1200000");
        assertThat(lastAuth.get()).isEqualTo("Bearer secret-token");
        assertThat(lastPath.get()).isEqualTo("/api/debts/12345678901239");
    }

    @Test
    void clearIsDetected() {
        assertThat(client(true).check("12345678901230").status()).isEqualTo(MibOutcome.Status.CLEAR);
    }

    @Test
    void notFoundIsClearOnlyWhenConfigured() {
        assertThat(client(true).check("12345678901234").status()).isEqualTo(MibOutcome.Status.CLEAR);
        assertThat(client(false).check("12345678901234").status()).isEqualTo(MibOutcome.Status.ERROR);
    }

    @Test
    void serverErrorIsNeverTreatedAsClear() {
        assertThat(client(true).check("12345678901235").status()).isEqualTo(MibOutcome.Status.ERROR);
    }

    @Test
    void unreachableServerIsError() {
        server.stop(0);
        assertThat(client(true).check("12345678901230").status()).isEqualTo(MibOutcome.Status.ERROR);
    }
}

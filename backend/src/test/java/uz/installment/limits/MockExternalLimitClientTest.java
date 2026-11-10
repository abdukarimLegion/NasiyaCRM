package uz.installment.limits;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockExternalLimitClientTest {

    private final MockExternalLimitClient client = new MockExternalLimitClient();

    @Test
    void returnsOneRowPerPlatform() {
        List<ExternalLimit> rows = client.check("31912850261934");
        assertThat(rows).extracting(ExternalLimit::provider).containsExactly(LimitProvider.values());
        assertThat(client.isMock()).isTrue();
    }

    @Test
    void sameClientAlwaysGetsSameResult() {
        assertThat(client.check("31912850261934")).isEqualTo(client.check("31912850261934"));
        assertThat(client.check("31912850261934")).isNotEqualTo(client.check("40101900000017"));
    }

    @Test
    void amountsAreConsistent() {
        for (String pinfl : List.of("31912850261934", "40101900000017", "52505800000023", "30000000000000")) {
            for (ExternalLimit l : client.check(pinfl)) {
                assertThat(l.available()).isEqualByComparingTo(l.total().subtract(l.used()));
                assertThat(l.available().signum()).isGreaterThanOrEqualTo(0);
                if (l.status() == ExternalLimit.Status.AVAILABLE) {
                    assertThat(l.total()).isBetween(new BigDecimal("2000000"), new BigDecimal("30000000"));
                    assertThat(l.maxTermMonths()).isPositive();
                } else {
                    assertThat(l.total()).isEqualByComparingTo(BigDecimal.ZERO);
                }
            }
        }
    }
}

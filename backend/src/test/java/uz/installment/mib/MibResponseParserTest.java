package uz.installment.mib;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.installment.mib.MibResponseParser.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class MibResponseParserTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Paths ALL = new Paths("data.hasDebt", "data.totalDebt", "data.cases", "message");

    private static MibOutcome parse(String body, Paths p) throws Exception {
        return MibResponseParser.parse(JSON.readTree(body), p);
    }

    @Test
    void flagTrueMeansDebt() throws Exception {
        var o = parse("{\"data\":{\"hasDebt\":true,\"totalDebt\":0}}", ALL);
        assertThat(o.status()).isEqualTo(MibOutcome.Status.DEBT);
    }

    @Test
    void amountAndCasesMeanDebt() throws Exception {
        var o = parse("{\"data\":{\"hasDebt\":false,\"totalDebt\":\"4 500 000,50\","
                + "\"cases\":[{\"caseNo\":\"A-1\",\"amount\":4500000.5,\"nested\":{\"x\":1}}]},\"message\":\"ok\"}", ALL);
        assertThat(o.status()).isEqualTo(MibOutcome.Status.DEBT);
        assertThat(o.totalDebt()).isEqualByComparingTo("4500000.50");
        assertThat(o.caseCount()).isEqualTo(1);
        // faqat oddiy qiymatli maydonlar saqlanadi
        assertThat(o.cases().get(0)).containsKeys("caseNo", "amount").doesNotContainKey("nested");
        assertThat(o.message()).isEqualTo("ok");
    }

    @Test
    void zeroEverythingMeansClear() throws Exception {
        var o = parse("{\"data\":{\"hasDebt\":false,\"totalDebt\":0,\"cases\":[]}}", ALL);
        assertThat(o.status()).isEqualTo(MibOutcome.Status.CLEAR);
        assertThat(o.totalDebt()).isEqualByComparingTo("0");
    }

    @Test
    void missingConfiguredFieldsIsErrorNotClear() throws Exception {
        assertThat(parse("{\"other\":1}", ALL).status()).isEqualTo(MibOutcome.Status.ERROR);
        assertThat(parse("{\"data\":{\"hasDebt\":false}}", new Paths(null, null, null, null)).status())
                .isEqualTo(MibOutcome.Status.ERROR);
    }

    @Test
    void badAmountAndEmptyBodyAreErrors() throws Exception {
        assertThat(parse("{\"data\":{\"totalDebt\":\"abc\"}}", ALL).status()).isEqualTo(MibOutcome.Status.ERROR);
        assertThat(MibResponseParser.parse(null, ALL).status()).isEqualTo(MibOutcome.Status.ERROR);
    }
}

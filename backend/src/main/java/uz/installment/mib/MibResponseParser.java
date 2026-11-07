package uz.installment.mib;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MIB javobini (JSON) tushunadi. Maydon yo'llari sozlamadan keladi (nuqtali: {@code data.totalDebt});
 * qarz bor deb hisoblanadi, agar: belgi (flag) true, summa 0 dan katta yoki ijro ishlari ro'yxati bo'sh emas.
 * Sozlangan maydonlarning birortasi ham javobda topilmasa — natija ERROR (qarz yo'q deb taxmin qilinmaydi).
 */
public final class MibResponseParser {

    private static final int MAX_CASES = 20;

    private MibResponseParser() {
    }

    public record Paths(String debtFlag, String amount, String cases, String message) {
    }

    public static MibOutcome parse(JsonNode root, Paths p) {
        boolean anyConfigured = notBlank(p.debtFlag()) || notBlank(p.amount()) || notBlank(p.cases());
        if (!anyConfigured) {
            return MibOutcome.error("MIB javob xaritasi sozlanmagan (APP_MIB_DEBT_FLAG_PATH / AMOUNT_PATH / CASES_PATH)");
        }
        if (root == null || root.isNull() || root.isMissingNode()) {
            return MibOutcome.error("MIB bo'sh javob qaytardi");
        }
        JsonNode flag = at(root, p.debtFlag());
        JsonNode amountNode = at(root, p.amount());
        JsonNode casesNode = at(root, p.cases());
        if (flag == null && amountNode == null && casesNode == null) {
            return MibOutcome.error("MIB javobida sozlangan maydonlar topilmadi");
        }
        boolean debtFlag = flag != null && (flag.isBoolean() ? flag.asBoolean() : "true".equalsIgnoreCase(flag.asText()));
        BigDecimal amount = BigDecimal.ZERO;
        if (amountNode != null && !amountNode.isNull()) {
            try {
                amount = new BigDecimal(amountNode.asText().trim().replace(" ", "").replace(',', '.'));
            } catch (NumberFormatException e) {
                return MibOutcome.error("MIB summa maydoni son emas: " + amountNode.asText());
            }
        }
        List<Map<String, Object>> cases = new ArrayList<>();
        int caseCount = 0;
        if (casesNode != null && casesNode.isArray()) {
            caseCount = casesNode.size();
            for (JsonNode c : casesNode) {
                if (cases.size() >= MAX_CASES) {
                    break;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                if (c.isObject()) {
                    c.fields().forEachRemaining(e -> {
                        if (e.getValue().isValueNode()) {
                            m.put(e.getKey(), e.getValue().isNumber() ? e.getValue().numberValue() : e.getValue().asText());
                        }
                    });
                } else {
                    m.put("value", c.asText());
                }
                cases.add(m);
            }
        }
        boolean debt = debtFlag || amount.signum() > 0 || caseCount > 0;
        JsonNode msg = at(root, p.message());
        String message = msg == null || msg.isNull() ? null : truncate(msg.asText());
        return new MibOutcome(debt ? MibOutcome.Status.DEBT : MibOutcome.Status.CLEAR, amount.max(BigDecimal.ZERO),
                caseCount, cases, message);
    }

    /** "a.b.c" yo'li bo'yicha tugunni oladi; topilmasa null. */
    static JsonNode at(JsonNode root, String path) {
        if (!notBlank(path)) {
            return null;
        }
        JsonNode cur = root;
        for (String part : path.trim().split("\\.")) {
            if (cur == null || cur.isNull() || cur.isMissingNode()) {
                return null;
            }
            cur = cur.get(part);
        }
        return cur == null || cur.isMissingNode() ? null : cur;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String truncate(String s) {
        return s.length() > 480 ? s.substring(0, 480) : s;
    }
}

package uz.installment.mib;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Demo/sinov uchun: haqiqiy MIB'ga bormaydi. JShShIR oxirgi raqami 9 bo'lsa — qarz bor (bitta soxta ijro ishi),
 * aks holda qarz yo'q. Ishlab chiqarishda ishlatilmaydi (APP_MIB_PROVIDER=http).
 */
@Component
@ConditionalOnProperty(name = "app.mib.provider", havingValue = "stub")
public class StubMibClient implements MibClient {

    @Override
    public MibOutcome check(String pinfl) {
        if (pinfl != null && pinfl.endsWith("9")) {
            return new MibOutcome(MibOutcome.Status.DEBT, new BigDecimal("4500000"), 1,
                    List.of(Map.of("caseNo", "DEMO-" + pinfl.substring(Math.max(0, pinfl.length() - 4)),
                            "amount", 4_500_000, "basis", "Demo ijro ishi (stub)")),
                    "DEMO: stub provayder (haqiqiy MIB emas)");
        }
        return MibOutcome.clear("DEMO: stub provayder (haqiqiy MIB emas)");
    }

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }
}

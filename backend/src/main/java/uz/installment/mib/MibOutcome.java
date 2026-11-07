package uz.installment.mib;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** MIB xizmatidan olingan natija. ERROR — tekshirib bo'lmadi (qarz yo'q degani emas!). */
public record MibOutcome(Status status, BigDecimal totalDebt, int caseCount, List<Map<String, Object>> cases,
                         String message) {

    public enum Status { CLEAR, DEBT, ERROR }

    public static MibOutcome clear(String message) {
        return new MibOutcome(Status.CLEAR, BigDecimal.ZERO, 0, List.of(), message);
    }

    public static MibOutcome error(String message) {
        return new MibOutcome(Status.ERROR, BigDecimal.ZERO, 0, List.of(), message);
    }
}

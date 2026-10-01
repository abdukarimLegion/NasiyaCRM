package uz.installment.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** {ism} {summa} {sana} {shartnoma} {kun} o'zgaruvchilarini almashtiradi. */
public final class TemplateRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private TemplateRenderer() {
    }

    public static String render(String template, String clientName, BigDecimal amount, LocalDate date,
                                String contractNo, long daysLate, String lang) {
        Map<String, String> vars = Map.of(
                "{ism}", nz(clientName),
                "{summa}", amount == null ? "" : money(amount, lang),
                "{sana}", date == null ? "" : DATE.format(date),
                "{shartnoma}", nz(contractNo),
                "{kun}", String.valueOf(daysLate));
        String out = template;
        for (var e : vars.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    /** 1150000 -> "1 150 000 so'm" (ru: "1 150 000 сум") */
    public static String money(BigDecimal amount, String lang) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("#,##0", sym);
        String n = df.format(amount.setScale(0, RoundingMode.HALF_UP));
        return n + ("ru".equals(lang) ? " сум" : " so'm");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

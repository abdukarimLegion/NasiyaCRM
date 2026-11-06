package uz.installment.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Pul oqimi prognozi (sof funksiya — testlanadi). Asos — haqiqiy to'lov jadvallari:
 * <ol>
 *   <li><b>Jadval bo'yicha tushum</b> = oy uchun jadvaldagi to'lanmagan summa × o'z vaqtida yig'ilish ulushi
 *       (tarixdan) × (1 + stsenariy tuzatmasi).</li>
 *   <li><b>Muddati o'tgan qarzdan undirish</b> = qoldiq muddati o'tgan qarz × oylik undirish ulushi (tarixdan);
 *       yig'ilmay qolgan jadval to'lovlari ham keyingi oyda shu qoldiqqa qo'shiladi.</li>
 *   <li><b>Yangi nasiyalar</b> = mavsumdan tozalangan o'rtacha oylik savdo × oy koeffitsiyenti × (1 + o'sish).
 *       Ular keyingi oydan boshlab o'rtacha muddat davomida (ustama bilan) teng to'lovlar beradi.</li>
 * </ol>
 * Kechikish uchun foiz yoki jarima hisoblanmaydi — qarz faqat savdo narxidan iborat.
 */
public final class ForecastCalculator {

    public record Inputs(YearMonth start, int horizon,
                         List<BigDecimal> scheduled, List<BigDecimal> scheduledMarkup,
                         BigDecimal arrears, BigDecimal openDebt,
                         BigDecimal onTimeRate, BigDecimal recoveryRate,
                         BigDecimal baseMonthlySales, BigDecimal avgMarkupPct, int avgTermMonths,
                         double[] seasonality) {
    }

    public record Scenario(BigDecimal salesGrowthPct, BigDecimal collectionAdjPct, boolean includeNewSales) {
    }

    public record Point(String month, BigDecimal seasonality, BigDecimal scheduled, BigDecimal fromSchedule,
                        BigDecimal fromArrears, BigDecimal newSales, BigDecimal fromNewSales, BigDecimal inflow,
                        BigDecimal profit, BigDecimal netCash, BigDecimal cumulativeNet, BigDecimal debtEnd,
                        BigDecimal arrearsEnd) {
    }

    public record Totals(BigDecimal inflow, BigDecimal profit, BigDecimal newSales, BigDecimal netCash,
                         BigDecimal debtEnd, BigDecimal arrearsEnd) {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ForecastCalculator() {
    }

    public static List<Point> project(Inputs in, Scenario sc) {
        int h = in.horizon();
        double rate = clamp(in.onTimeRate().doubleValue() * (1 + sc.collectionAdjPct().doubleValue() / 100), 0, 1);
        double recovery = clamp(in.recoveryRate().doubleValue(), 0, 1);
        double growth = 1 + sc.salesGrowthPct().doubleValue() / 100;
        double markup = in.avgMarkupPct().doubleValue() / 100;
        int term = Math.max(1, in.avgTermMonths());

        // Yangi nasiyalardan keladigan to'lovlar (oy indeksi bo'yicha)
        double[] newInflowDue = new double[h + term + 1];
        double arrears = in.arrears().doubleValue();
        double debt = in.openDebt().doubleValue();
        double cumulative = 0;
        List<Point> out = new ArrayList<>(h);
        for (int i = 0; i < h; i++) {
            YearMonth ym = in.start().plusMonths(i);
            double season = in.seasonality()[ym.getMonthValue() - 1];
            double scheduled = at(in.scheduled(), i);
            double scheduledMarkup = at(in.scheduledMarkup(), i);

            double fromSchedule = scheduled * rate;
            double fromArrears = arrears * recovery;
            arrears = arrears - fromArrears + (scheduled - fromSchedule);

            double sales = sc.includeNewSales() ? in.baseMonthlySales().doubleValue() * season * growth : 0;
            if (sales > 0) {
                double monthly = sales * (1 + markup) / term;
                for (int k = 1; k <= term; k++) {
                    newInflowDue[i + k] += monthly;
                }
            }
            double fromNew = newInflowDue[i] * rate;
            // yangi nasiyalardan yig'ilmay qolgani ham muddati o'tgan qarzga o'tadi
            arrears += newInflowDue[i] - fromNew;

            double inflow = fromSchedule + fromArrears + fromNew;
            // Foyda — tushumdagi ustama ulushi (jadvalda ajratilgan; yangi savdoda o'rtacha ustama bo'yicha)
            double markupShare = scheduled > 0 ? scheduledMarkup / scheduled : markup / (1 + markup);
            double newShare = markup / (1 + markup);
            double profit = fromSchedule * markupShare + fromArrears * markupShare + fromNew * newShare;
            double net = inflow - sales;
            cumulative += net;
            debt = debt + sales * (1 + markup) - inflow;

            out.add(new Point(ym.toString(), bd(season, 2), money(scheduled), money(fromSchedule), money(fromArrears),
                    money(sales), money(fromNew), money(inflow), money(profit), money(net), money(cumulative),
                    money(Math.max(0, debt)), money(Math.max(0, arrears))));
        }
        return out;
    }

    public static Totals totals(List<Point> points) {
        BigDecimal inflow = BigDecimal.ZERO, profit = BigDecimal.ZERO, sales = BigDecimal.ZERO, net = BigDecimal.ZERO;
        for (Point p : points) {
            inflow = inflow.add(p.inflow());
            profit = profit.add(p.profit());
            sales = sales.add(p.newSales());
            net = net.add(p.netCash());
        }
        Point last = points.isEmpty() ? null : points.get(points.size() - 1);
        return new Totals(inflow, profit, sales, net,
                last == null ? BigDecimal.ZERO : last.debtEnd(), last == null ? BigDecimal.ZERO : last.arrearsEnd());
    }

    /**
     * Mavsumdan tozalangan o'rtacha oylik savdo: har oy savdosi o'z koeffitsiyentiga bo'linadi.
     * Masalan, dekabr (1.28) dagi 128 mln — "oddiy" oyda 100 mln ga teng.
     */
    public static BigDecimal deseasonalizedAverage(List<YearMonth> months, List<BigDecimal> sales, double[] seasonality) {
        double sum = 0;
        int n = 0;
        for (int i = 0; i < months.size(); i++) {
            double s = seasonality[months.get(i).getMonthValue() - 1];
            if (s > 0) {
                sum += sales.get(i).doubleValue() / s;
                n++;
            }
        }
        return n == 0 ? BigDecimal.ZERO : money(sum / n);
    }

    /**
     * Tarixdan mavsumiy koeffitsiyent taklifi: har oy (yanvar..dekabr) o'rtacha savdosi / umumiy o'rtacha.
     * Kamida 12 oylik ma'lumot (10 ta oyda savdo bo'lgan) kerak, aks holda null.
     */
    public static double[] suggestSeasonality(List<YearMonth> months, List<BigDecimal> sales) {
        double[] sum = new double[12];
        int[] cnt = new int[12];
        int nonZero = 0;
        for (int i = 0; i < months.size(); i++) {
            double v = sales.get(i).doubleValue();
            int k = months.get(i).getMonthValue() - 1;
            sum[k] += v;
            cnt[k]++;
            if (v > 0) nonZero++;
        }
        for (int c : cnt) {
            if (c == 0) return null;
        }
        if (nonZero < 10) return null;
        double[] avg = new double[12];
        double total = 0;
        for (int k = 0; k < 12; k++) {
            avg[k] = sum[k] / cnt[k];
            total += avg[k];
        }
        double mean = total / 12;
        if (mean <= 0) return null;
        double[] out = new double[12];
        for (int k = 0; k < 12; k++) {
            out[k] = Math.round(avg[k] / mean * 100) / 100.0;
        }
        return out;
    }

    private static double at(List<BigDecimal> list, int i) {
        return i < list.size() && list.get(i) != null ? list.get(i).doubleValue() : 0;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return null;
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }
}

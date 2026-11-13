package com.tensku.gamblehud;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Profit & loss maths on top of the balance snapshots stored in {@link Config#history}. */
public final class Pnl {
    private Pnl() {}

    public static final long SESSION_START = System.currentTimeMillis();
    public static final String[] RANGES = {"Session", "24h", "7d", "30d", "All"};
    private static final long DAY = 86_400_000L;
    private static final long[] SPAN = {0, DAY, 7 * DAY, 30 * DAY, 0};

    /** {time, balance} points for the range, with a start point and a "now" point so the line spans the whole range. */
    public static List<double[]> points(int range) {
        range = Math.max(0, Math.min(4, range));
        List<double[]> out = new ArrayList<>();
        List<double[]> h = Config.I.history;
        if (h == null || h.isEmpty()) return out;
        long now = System.currentTimeMillis();
        double cutoff = range == 0 ? SESSION_START : (range == 4 ? Double.NEGATIVE_INFINITY : now - SPAN[range]);
        double[] prev = null;
        for (double[] p : h) {
            if (p[0] < cutoff) { prev = p; continue; }
            if (out.isEmpty() && prev != null && range != 0) out.add(new double[] {cutoff, prev[1]});
            out.add(p);
        }
        if (out.isEmpty() && prev != null) out.add(new double[] {cutoff == Double.NEGATIVE_INFINITY ? prev[0] : cutoff, prev[1]});
        if (!out.isEmpty()) {
            double cur = Config.I.balance >= 0 ? Config.I.balance : out.get(out.size() - 1)[1];
            out.add(new double[] {now, cur});
        }
        return out;
    }

    public static double change(List<double[]> pts) {
        if (pts.size() < 2) return 0;
        return pts.get(pts.size() - 1)[1] - pts.get(0)[1];
    }

    public static double percent(List<double[]> pts) {
        if (pts.size() < 2 || pts.get(0)[1] <= 0) return 0;
        return change(pts) / pts.get(0)[1] * 100.0;
    }

    public static double high(List<double[]> pts) {
        double m = Double.NEGATIVE_INFINITY;
        for (double[] p : pts) m = Math.max(m, p[1]);
        return m;
    }

    public static double low(List<double[]> pts) {
        double m = Double.POSITIVE_INFINITY;
        for (double[] p : pts) m = Math.min(m, p[1]);
        return m;
    }

    /** One day's result. */
    public record Day(LocalDate date, double change) {}

    /** {best, worst} day over all recorded history, or null if there is no usable data. */
    public static Day[] bestWorstDay() {
        List<double[]> h = Config.I.history;
        if (h == null || h.size() < 2) return null;
        TreeMap<LocalDate, double[]> days = new TreeMap<>();   // date -> {first, last}
        ZoneId zone = ZoneId.systemDefault();
        for (double[] p : h) {
            LocalDate d = Instant.ofEpochMilli((long) p[0]).atZone(zone).toLocalDate();
            double[] e = days.get(d);
            if (e == null) days.put(d, new double[] {p[1], p[1]});
            else e[1] = p[1];
        }
        Day best = null, worst = null;
        Double prevEnd = null;
        for (var en : days.entrySet()) {
            double start = prevEnd != null ? prevEnd : en.getValue()[0];
            double ch = en.getValue()[1] - start;
            prevEnd = en.getValue()[1];
            Day d = new Day(en.getKey(), ch);
            if (best == null || ch > best.change()) best = d;
            if (worst == null || ch < worst.change()) worst = d;
        }
        if (best == null) return null;
        return new Day[] {best, worst};
    }

    public static String fmtDate(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH));
    }

    /** Time label for a chart: clock time for short spans, date otherwise. */
    public static String fmtTime(double ms, boolean showDate) {
        var z = Instant.ofEpochMilli((long) ms).atZone(ZoneId.systemDefault());
        return z.format(DateTimeFormatter.ofPattern(showDate ? "MMM d HH:mm" : "HH:mm", Locale.ENGLISH));
    }

    public static String signed(double v) {
        return (v >= 0 ? "+" : "-") + Money.format(Math.abs(v));
    }
}

package com.tensku.gamblehud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Line graph of the balance. The area between the line and the starting balance is green where you are
 * up and red where you are down. Detailed mode adds grid, axis labels and a hover read-out.
 */
public final class PnlChart {
    private PnlChart() {}

    private static final int UP = 0x4ADE80, DOWN = 0xFF6B6B;

    private static int alpha(int rgb, float a) {
        int al = Math.max(0, Math.min(255, (int) (a * 255f)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    /** Draws inside (x, y, w, h). In detailed mode the time labels sit just under the box (reserve ~12px). */
    public static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, List<double[]> pts,
                            boolean detailed, int mx, int my, int accent) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, alpha(accent, 0.55f));
        g.fill(x, y, x + w, y + h, 0xEE0B0C12);

        if (pts.size() < 2 || w < 4) {
            String m = "Not enough data yet";
            if (detailed) {
                g.text(font, m, x + (w - font.width(m)) / 2, y + h / 2 - 8, 0xFFB9BDCB, true);
                String m2 = "type /bal to start tracking";
                g.text(font, m2, x + (w - font.width(m2)) / 2, y + h / 2 + 4, 0xFF6A708A, true);
            }
            return;
        }

        double t0 = pts.get(0)[0], t1 = pts.get(pts.size() - 1)[0];
        if (t1 <= t0) t1 = t0 + 1;
        double rawMin = Pnl.low(pts), rawMax = Pnl.high(pts);
        double vmin = rawMin, vmax = rawMax;
        double pad = (vmax - vmin) * 0.12;
        if (vmax - vmin < 1e-9) pad = Math.max(1.0, Math.abs(vmax) * 0.01);
        vmin -= pad;
        vmax += pad;
        final double span = vmax - vmin;
        final double base = pts.get(0)[1];

        if (detailed) {
            for (int i = 1; i < 4; i++) g.fill(x, y + h * i / 4, x + w, y + h * i / 4 + 1, 0x22FFFFFF);
        }
        int by = yOf(base, y, h, vmin, span);
        for (int dx = 0; dx < w; dx += 6) g.fill(x + dx, by, x + Math.min(w, dx + 3), by + 1, 0x88FFFFFF);

        int idx = 0, prevY = -1;
        int[] colY = new int[w];
        double[] colV = new double[w];
        for (int cx = 0; cx < w; cx++) {
            double t = t0 + (t1 - t0) * cx / (double) Math.max(1, w - 1);
            while (idx < pts.size() - 2 && pts.get(idx + 1)[0] <= t) idx++;
            double[] a = pts.get(idx), b = pts.get(idx + 1);
            double f = b[0] == a[0] ? 1.0 : Math.max(0.0, Math.min(1.0, (t - a[0]) / (b[0] - a[0])));
            double v = a[1] + (b[1] - a[1]) * f;
            int yy = yOf(v, y, h, vmin, span);
            colY[cx] = yy;
            colV[cx] = v;
            int col = v >= base ? UP : DOWN;
            g.fill(x + cx, Math.min(yy, by), x + cx + 1, Math.max(yy, by) + 1, alpha(col, 0.20f));
            int lo = prevY < 0 ? yy : Math.min(prevY, yy), hi = prevY < 0 ? yy : Math.max(prevY, yy);
            g.fill(x + cx, lo, x + cx + 1, Math.min(y + h, hi + 2), 0xFF000000 | col);
            prevY = yy;
        }

        if (detailed) {
            int lc = 0xFF9AA0B5;
            g.text(font, Money.format(rawMax), x + 3, y + 2, lc, true);
            g.text(font, Money.format(rawMin), x + 3, y + h - 10, lc, true);
            boolean showDate = (t1 - t0) > 2 * 86_400_000.0;
            g.text(font, Pnl.fmtTime(t0, showDate), x, y + h + 3, 0xFF6A708A, true);
            String end = Pnl.fmtTime(t1, showDate);
            g.text(font, end, x + w - font.width(end), y + h + 3, 0xFF6A708A, true);

            // hover read-out
            if (mx >= x && mx < x + w && my >= y && my < y + h) {
                int cx = mx - x;
                double v = colV[cx];
                int py = colY[cx];
                double t = t0 + (t1 - t0) * cx / (double) Math.max(1, w - 1);
                int col = v >= base ? UP : DOWN;
                g.fill(mx, y, mx + 1, y + h, 0x66FFFFFF);
                g.fill(mx - 2, py - 2, mx + 3, py + 3, 0xFFFFFFFF);
                g.fill(mx - 1, py - 1, mx + 2, py + 2, 0xFF000000 | col);
                String l1 = Money.format(v);
                String l2 = Pnl.fmtTime(t, showDate);
                String l3 = Pnl.signed(v - base);
                int tw = Math.max(font.width(l1), Math.max(font.width(l2), font.width(l3))) + 10;
                int tx = Math.min(x + w - tw - 1, Math.max(x + 1, mx - tw / 2));
                int ty = py - 40 < y ? Math.min(y + h - 38, py + 8) : py - 40;
                g.fill(tx - 1, ty - 1, tx + tw + 1, ty + 37, alpha(col, 0.9f));
                g.fill(tx, ty, tx + tw, ty + 36, 0xF0141620);
                g.text(font, l1, tx + 5, ty + 3, 0xFFFFFFFF, true);
                g.text(font, l3, tx + 5, ty + 14, 0xFF000000 | col, true);
                g.text(font, l2, tx + 5, ty + 25, 0xFF9AA0B5, true);
            }
        }
    }

    private static int yOf(double v, int y, int h, double vmin, double span) {
        int yy = y + (int) ((1.0 - (v - vmin) / span) * (h - 1));
        return Math.max(y, Math.min(y + h - 1, yy));
    }
}

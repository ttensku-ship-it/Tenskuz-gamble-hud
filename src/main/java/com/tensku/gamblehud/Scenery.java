package com.tensku.gamblehud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

/**
 * Animated "cinematic" menu backgrounds: a sky, a glowing sun or moon, hills and one big tree
 * (cherry blossom, maple, pine, acacia, palm) with petals / leaves / snow / fireflies drifting by.
 * Everything is drawn with plain rectangles and clipped to the panel.
 */
public final class Scenery {
    private Scenery() {}

    public static final int SAKURA = 1, MAPLE = 2, PINE = 3, ACACIA = 4, MOONBLOOM = 5, PALM = 6;

    private static int cl, ct, cr, cb; // clip rectangle
    private static float u;            // 1 unit = this many pixels (scales with panel height)

    /** Leaf blobs of a round tree crown: dx, dy, radius (units) and a sway phase. */
    private static final float[][] CANOPY = new float[30][4];
    static {
        Random r = new Random(11);
        for (int i = 0; i < CANOPY.length; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double rr = Math.sqrt(r.nextDouble());
            CANOPY[i][0] = (float) (Math.cos(a) * rr * 66.0);
            CANOPY[i][1] = (float) (Math.sin(a) * rr * 38.0);
            CANOPY[i][2] = 15f + r.nextFloat() * 14f;
            CANOPY[i][3] = r.nextFloat() * 6.28f;
        }
    }

    // ------------------------------------------------------------------ entry

    public static void draw(GuiGraphicsExtractor g, int scene, int x1, int y1, int x2, int y2, long nowMs) {
        cl = x1; ct = y1; cr = x2; cb = y2;
        int W = x2 - x1, H = y2 - y1;
        u = H / 270f;
        float t = (nowMs % 1_000_000L) / 1000f;
        switch (scene) {
            case SAKURA -> sakura(g, x1, y1, W, H, t, false);
            case MOONBLOOM -> sakura(g, x1, y1, W, H, t, true);
            case MAPLE -> maple(g, x1, y1, W, H, t);
            case PINE -> pine(g, x1, y1, W, H, t);
            case ACACIA -> acacia(g, x1, y1, W, H, t);
            case PALM -> palm(g, x1, y1, W, H, t);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ scenes

    private static void sakura(GuiGraphicsExtractor g, int x1, int y1, int W, int H, float t, boolean night) {
        int hz = y1 + (int) (H * 0.72f);
        int gy = y1 + (int) (H * 0.84f);
        int tx = x1 + (int) (W * 0.78f);
        if (night) {
            sky(g, x1, y1, W, H, 0.72f, 0xFF0B0F2E, 0xFF2A2866, 0xFF5B4590);
            stars(g, x1, y1, W, H, t, 46);
            moon(g, x1 + (int) (W * 0.30f), y1 + (int) (H * 0.26f), (int) (20 * u), t);
            hills(g, x1, W, hz - (int) (6 * u), 34 * u, 0.012f, 0.031f, 1.1f, 0xFF2B2A63, y1 + H);
            hills(g, x1, W, hz + (int) (8 * u), 26 * u, 0.017f, 0.043f, 2.4f, 0xFF1F2050, y1 + H);
            ground(g, x1, W, gy, y1 + H, 0xFF141B3C, 0xFF2C4A58);
            trunkRound(g, tx, gy, 0xFF2A1B2E, 0xFF4A3550);
            canopy(g, tx, gy - (int) (112 * u), t, 0xFF8F4A86, 0xFFC27BB4, 0xFFF1C2E2);
            fall(g, t, 46, tx - 20 * u, 70 * u, gy - 112 * u, y1 + H, 120 * u,
                 new int[] {0xFFF6C4E4, 0xFFFFE3F2, 0xFFD98FBF}, 2, 10f, x1, y1, W, H);
            fireflies(g, x1, y1, W, H, gy, t, 14);
        } else {
            sky(g, x1, y1, W, H, 0.72f, 0xFF3A1F5C, 0xFFE8607A, 0xFFFFB36B);
            sun(g, x1 + (int) (W * 0.30f), y1 + (int) (H * 0.62f), (int) (24 * u), 0xFFFFE9A8, 0xFFFFC16B, t);
            hills(g, x1, W, hz - (int) (6 * u), 34 * u, 0.012f, 0.031f, 1.1f, 0xFF8A4A7E, y1 + H);
            hills(g, x1, W, hz + (int) (8 * u), 26 * u, 0.017f, 0.043f, 2.4f, 0xFF5F3470, y1 + H);
            ground(g, x1, W, gy, y1 + H, 0xFF2E3A2C, 0xFF6C8A45);
            trunkRound(g, tx, gy, 0xFF4A2A22, 0xFF7A4A38);
            canopy(g, tx, gy - (int) (112 * u), t, 0xFFD9689A, 0xFFF29BBD, 0xFFFFD0E2);
            fall(g, t, 46, tx - 20 * u, 70 * u, gy - 112 * u, y1 + H, 120 * u,
                 new int[] {0xFFFFC4DA, 0xFFFFE3EE, 0xFFF29BBD}, 2, 10f, x1, y1, W, H);
            birds(g, x1, y1, W, H, t, 3);
        }
    }

    private static void maple(GuiGraphicsExtractor g, int x1, int y1, int W, int H, float t) {
        int hz = y1 + (int) (H * 0.72f);
        int gy = y1 + (int) (H * 0.84f);
        int tx = x1 + (int) (W * 0.78f);
        sky(g, x1, y1, W, H, 0.72f, 0xFF3A1A12, 0xFFC4552A, 0xFFFFB45C);
        sun(g, x1 + (int) (W * 0.28f), y1 + (int) (H * 0.60f), (int) (26 * u), 0xFFFFF0B0, 0xFFFFA84D, t);
        hills(g, x1, W, hz - (int) (6 * u), 34 * u, 0.012f, 0.031f, 0.4f, 0xFF8A4A2A, y1 + H);
        hills(g, x1, W, hz + (int) (8 * u), 26 * u, 0.017f, 0.043f, 1.7f, 0xFF5E2E1A, y1 + H);
        ground(g, x1, W, gy, y1 + H, 0xFF3A2414, 0xFF9A6A2A);
        trunkRound(g, tx, gy, 0xFF3A2216, 0xFF6A3E26);
        canopy(g, tx, gy - (int) (112 * u), t, 0xFFA8321A, 0xFFE8681A, 0xFFFFB347);
        fall(g, t, 40, tx - 10 * u, 130 * u, gy - 110 * u, y1 + H, 150 * u,
             new int[] {0xFFE8681A, 0xFFFFB347, 0xFFC4381A, 0xFFFFD166}, 3, 9f, x1, y1, W, H);
        // leaves lying on the grass
        for (int i = 0; i < 26; i++) {
            int lx = x1 + (int) (frac(i * 0.618f) * W);
            int ly = gy + (int) ((6 + frac(i * 0.755f) * (y1 + H - gy - 8)));
            box(g, lx, ly, lx + 3, ly + 2, a(i % 2 == 0 ? 0xE8681A : 0xFFB347, 0.8f));
        }
    }

    private static void pine(GuiGraphicsExtractor g, int x1, int y1, int W, int H, float t) {
        int hz = y1 + (int) (H * 0.72f);
        int gy = y1 + (int) (H * 0.84f);
        int tx = x1 + (int) (W * 0.80f);
        sky(g, x1, y1, W, H, 0.72f, 0xFF050A24, 0xFF14285A, 0xFF3A5E8A);
        stars(g, x1, y1, W, H, t, 60);
        aurora(g, x1, y1, W, H, t);
        moon(g, x1 + (int) (W * 0.22f), y1 + (int) (H * 0.24f), (int) (16 * u), t);
        hills(g, x1, W, hz - (int) (4 * u), 36 * u, 0.012f, 0.031f, 0.9f, 0xFF5A7396, y1 + H);
        hills(g, x1, W, hz + (int) (8 * u), 26 * u, 0.017f, 0.043f, 2.0f, 0xFF8AA6C4, y1 + H);
        ground(g, x1, W, gy, y1 + H, 0xFFC9D8E8, 0xFFFFFFFF);
        pineTree(g, tx, gy, t);
        pineTree(g, x1 + (int) (W * 0.60f), gy + (int) (4 * u), t, 0.55f);
        fall(g, t, 70, x1 + W / 2f, W * 1.2f, y1 - 4, y1 + H, 40 * u,
             new int[] {0xFFFFFFFF, 0xFFDCE8F5}, 2, 7f, x1, y1, W, H);
    }

    private static void acacia(GuiGraphicsExtractor g, int x1, int y1, int W, int H, float t) {
        int hz = y1 + (int) (H * 0.72f);
        int gy = y1 + (int) (H * 0.84f);
        int tx = x1 + (int) (W * 0.76f);
        sky(g, x1, y1, W, H, 0.72f, 0xFF2B1040, 0xFFE8503A, 0xFFFFC857);
        sun(g, x1 + (int) (W * 0.32f), y1 + (int) (H * 0.64f), (int) (30 * u), 0xFFFFF3B8, 0xFFFF9A3D, t);
        hills(g, x1, W, hz - (int) (2 * u), 22 * u, 0.010f, 0.027f, 0.7f, 0xFF6A2A4A, y1 + H);
        hills(g, x1, W, hz + (int) (8 * u), 16 * u, 0.015f, 0.040f, 2.2f, 0xFF3A1A34, y1 + H);
        ground(g, x1, W, gy, y1 + H, 0xFF1F1020, 0xFF6A4A2A);
        // fork-shaped trunk
        line(g, tx + 2 * u, gy + 4 * u, tx - 4 * u, gy - 50 * u, 8 * u, 0xFF2A1A18);
        line(g, tx - 4 * u, gy - 50 * u, tx - 42 * u, gy - 88 * u, 5 * u, 0xFF2A1A18);
        line(g, tx - 4 * u, gy - 50 * u, tx + 36 * u, gy - 94 * u, 5 * u, 0xFF2A1A18);
        line(g, tx - 5 * u, gy - 52 * u, tx - 3 * u, gy - 84 * u, 4 * u, 0xFF2A1A18);
        float sw = (float) Math.sin(t * 0.6f) * 1.5f * u;
        umbrella(g, tx - 36 * u + sw, gy - 94 * u, 54 * u, 9 * u);
        umbrella(g, tx + 28 * u + sw, gy - 100 * u, 62 * u, 10 * u);
        umbrella(g, tx - 4 * u + sw, gy - 108 * u, 42 * u, 8 * u);
        birds(g, x1, y1, W, H, t, 4);
        // dry grass tufts
        for (int i = 0; i < 40; i++) {
            int gx = x1 + (int) (frac(i * 0.618f) * W);
            int gh = (int) ((3 + (i % 4)) * u);
            int by = gy + (int) (3 * u) + (int) (frac(i * 0.37f) * (y1 + H - gy - 6 * u));
            float bend = (float) Math.sin(t * 1.4f + i) * 1.2f;
            box(g, gx, by - gh, gx + 1 + (int) bend, by, a(0xC49A4A, 0.9f));
        }
    }

    private static void palm(GuiGraphicsExtractor g, int x1, int y1, int W, int H, float t) {
        int hz = y1 + (int) (H * 0.60f);
        int gy = y1 + (int) (H * 0.80f);
        int tx = x1 + (int) (W * 0.76f);
        sky(g, x1, y1, W, H, 0.60f, 0xFF2A1B5C, 0xFFFF6B6B, 0xFFFFC27A);
        int sx = x1 + (int) (W * 0.30f);
        sun(g, sx, hz - (int) (4 * u), (int) (26 * u), 0xFFFFF0B8, 0xFFFFA070, t);
        // sea
        grad(g, x1, hz, x1 + W, y1 + H, 0xFFFF9A6B, 0xFF1F2F6A);
        // sun glitter column
        for (int row = hz; row < y1 + H; row += 3) {
            float k = (row - hz) / (float) Math.max(1, y1 + H - hz);
            float wv = (6 * u + k * 46 * u) * (0.55f + 0.45f * (float) Math.sin(t * 2.5f + row * 0.55f));
            box(g, (int) (sx - wv / 2), row, (int) (sx + wv / 2), row + 1, a(0xFFF0B8, 0.55f * (1f - k * 0.5f)));
        }
        // drifting wave lines
        for (int i = 0; i < 6; i++) {
            int wy = hz + (int) ((10 + i * 22) * u);
            for (int x = 0; x < W; x += 6) {
                if (((x / 6 + (int) (t * 3f) + i * 2) % 4) == 0) box(g, x1 + x, wy, x1 + x + 5, wy + 1, a(0xFFFFFF, 0.14f));
            }
        }
        // far island
        ell(g, x1 + W * 0.12f, hz + 1, 38 * u, 7 * u, 0xFF3A2468);
        // sandy island under the palm
        ell(g, tx, gy + 10 * u, 84 * u, 16 * u, 0xFFC9A36A);
        ell(g, tx, gy + 7 * u, 80 * u, 12 * u, 0xFFE8C98B);
        // trunk: curved quadratic bezier
        float p0x = tx - 4 * u, p0y = gy + 4 * u, p1x = tx - 22 * u, p1y = gy - 60 * u, p2x = tx + 18 * u, p2y = gy - 112 * u;
        float lx = p0x, ly = p0y;
        for (int i = 1; i <= 14; i++) {
            float s = i / 14f, q = 1f - s;
            float px = q * q * p0x + 2 * q * s * p1x + s * s * p2x;
            float py = q * q * p0y + 2 * q * s * p1y + s * s * p2y;
            line(g, lx, ly, px, py, (8f - 3f * s) * u, i % 2 == 0 ? 0xFF6A4A2E : 0xFF7A5A3A);
            lx = px; ly = py;
        }
        float hx = p2x, hy = p2y;
        float[] ang = {-2.95f, -2.4f, -1.95f, -1.35f, -0.85f, -0.3f, 0.2f, 2.95f};
        for (int i = 0; i < ang.length; i++) {
            float aa = ang[i] + (float) Math.sin(t * 1.1f + i) * 0.06f;
            float len = (i == 1 || i == 6 ? 58f : 50f) * u;
            float fx = hx, fy = hy;
            for (int sIdx = 1; sIdx <= 12; sIdx++) {
                float s = sIdx / 12f;
                float nx = hx + (float) Math.cos(aa) * len * s;
                float ny = hy + (float) Math.sin(aa) * len * s * 0.55f + 0.8f * len * s * s * 0.6f;
                line(g, fx, fy, nx, ny, (3.4f * (1f - s) + 1f) * u, i % 2 == 0 ? 0xFF1F5A2E : 0xFF2E7A3C);
                fx = nx; fy = ny;
            }
        }
        disc(g, hx - 3 * u, hy + 5 * u, 3.4f * u, 0xFF4A2A18);
        disc(g, hx + 3 * u, hy + 6 * u, 3.4f * u, 0xFF5A3420);
        birds(g, x1, y1, W, H, t, 3);
    }

    // ------------------------------------------------------------------ pieces

    private static void sky(GuiGraphicsExtractor g, int x1, int y1, int W, int H, float hzFrac, int top, int mid, int bot) {
        int hz = y1 + (int) (H * hzFrac);
        int m = y1 + (int) ((hz - y1) * 0.55f);
        box(g, x1, hz, x1 + W, y1 + H, bot);
        grad(g, x1, y1, x1 + W, m, top, mid);
        grad(g, x1, m, x1 + W, hz, mid, bot);
    }

    private static void sun(GuiGraphicsExtractor g, int sx, int sy, int r, int color, int glow, float t) {
        float pulse = 1f + 0.25f * (float) Math.sin(t * 0.9f);
        for (int k = 6; k >= 1; k--) disc(g, sx, sy, r + k * 8 * u, a(glow, 0.045f * pulse));
        disc(g, sx, sy, r + 3 * u, a(glow, 0.35f));
        disc(g, sx, sy, r, color);
    }

    private static void moon(GuiGraphicsExtractor g, int mx, int my, int r, float t) {
        float pulse = 1f + 0.2f * (float) Math.sin(t * 0.8f);
        for (int k = 5; k >= 1; k--) disc(g, mx, my, r + k * 7 * u, a(0xBFD4FF, 0.04f * pulse));
        disc(g, mx, my, r, 0xFFEFF3FF);
        disc(g, mx - r * 0.3f, my - r * 0.2f, r * 0.22f, 0xFFD3DBF0);
        disc(g, mx + r * 0.28f, my + r * 0.25f, r * 0.30f, 0xFFD8E0F4);
        disc(g, mx + r * 0.1f, my - r * 0.45f, r * 0.14f, 0xFFD3DBF0);
    }

    private static void stars(GuiGraphicsExtractor g, int x1, int y1, int W, int H, float t, int n) {
        for (int i = 0; i < n; i++) {
            int sx = x1 + (int) (frac(i * 0.6180339f) * W);
            int sy = y1 + (int) (frac(i * 0.7548776f) * H * 0.55f);
            float tw = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(t * (1.5f + i % 3) + i));
            int sz = i % 7 == 0 ? 2 : 1;
            box(g, sx, sy, sx + sz, sy + sz, a(0xFFFFFF, tw));
        }
    }

    private static void aurora(GuiGraphicsExtractor g, int x1, int y1, int W, int H, float t) {
        for (int x = 0; x < W; x += 3) {
            float yb = y1 + H * 0.22f + (float) Math.sin(x * 0.035f + t * 0.4f) * 14f * u
                + (float) Math.sin(x * 0.09f - t * 0.7f) * 5f * u;
            int y = (int) yb;
            int top = (int) (16 * u), bot = (int) (22 * u);
            int col = (x / 3 % 2 == 0) ? 0x4DFFB0 : 0x6FD3FF;
            grad(g, x1 + x, y - top, x1 + x + 3, y, a(col, 0f), a(col, 0.30f));
            grad(g, x1 + x, y, x1 + x + 3, y + bot, a(col, 0.30f), a(col, 0f));
        }
    }

    private static void hills(GuiGraphicsExtractor g, int x1, int W, int base, float amp, float f1, float f2, float ph, int color, int bottom) {
        for (int x = 0; x < W; x += 2) {
            float h = (float) (0.6 * Math.sin(x * f1 + ph) + 0.4 * Math.sin(x * f2 + ph * 1.7));
            int y = base - (int) ((h * 0.5f + 0.5f) * amp);
            box(g, x1 + x, y, x1 + x + 2, bottom, color);
        }
    }

    private static void ground(GuiGraphicsExtractor g, int x1, int W, int gy, int bottom, int dark, int edge) {
        box(g, x1, gy, x1 + W, bottom, dark);
        for (int x = 0; x < W; x += 2) {
            int bump = (int) (Math.sin(x * 0.045f) * 2f * u + Math.sin(x * 0.13f) * u);
            box(g, x1 + x, gy + bump, x1 + x + 2, gy + bump + 2, edge);
        }
    }

    private static void trunkRound(GuiGraphicsExtractor g, int tx, int gy, int dark, int light) {
        line(g, tx + 4 * u, gy + 4 * u, tx - 2 * u, gy - 45 * u, 10 * u, dark);
        line(g, tx - 2 * u, gy - 45 * u, tx + 3 * u, gy - 88 * u, 7 * u, dark);
        line(g, tx, gy - 62 * u, tx - 42 * u, gy - 98 * u, 4 * u, dark);
        line(g, tx + 1 * u, gy - 72 * u, tx + 44 * u, gy - 104 * u, 4 * u, dark);
        line(g, tx, gy - 86 * u, tx - 10 * u, gy - 124 * u, 3 * u, dark);
        line(g, tx - 3 * u, gy + 2 * u, tx - 5 * u, gy - 44 * u, 2 * u, light);
    }

    private static void canopy(GuiGraphicsExtractor g, int cx, int cy, float t, int dark, int mid, int light) {
        float sway = (float) Math.sin(t * 0.6f) * 2f * u;
        for (int pass = 0; pass < 3; pass++) {
            for (float[] b : CANOPY) {
                float ox = (float) Math.sin(t * 0.9f + b[3]) * 1.6f * u + sway;
                float oy = (float) Math.cos(t * 0.7f + b[3]) * 0.9f * u;
                float x = cx + b[0] * u + ox, y = cy + b[1] * u + oy, r = b[2] * u;
                if (pass == 0) disc(g, x, y + 4 * u, r, dark);
                else if (pass == 1) disc(g, x, y, r * 0.88f, mid);
                else if (b[0] < 22f && b[1] < 12f) disc(g, x - 5 * u, y - 5 * u, r * 0.55f, light);
            }
        }
    }

    private static void umbrella(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry) {
        ell(g, cx, cy + 2 * u, rx, ry, 0xFF22381A);
        ell(g, cx, cy, rx * 0.97f, ry * 0.85f, 0xFF3F6026);
        ell(g, cx - rx * 0.1f, cy - 2 * u, rx * 0.75f, ry * 0.5f, 0xFF63883A);
    }

    private static void pineTree(GuiGraphicsExtractor g, int tx, int gy, float t) {
        pineTree(g, tx, gy, t, 1f);
    }

    private static void pineTree(GuiGraphicsExtractor g, int tx, int gy, float t, float s) {
        float uu = u * s;
        box(g, (int) (tx - 3 * uu), (int) (gy - 26 * uu), (int) (tx + 3 * uu), (int) (gy + 4 * uu), 0xFF3B2A20);
        for (int k = 0; k < 5; k++) {
            float top = gy - (150f - k * 27f) * uu;
            float hgt = 44f * uu, half = (16f + k * 8f) * uu;
            for (int row = 0; row < (int) hgt; row += 2) {
                float f = row / hgt;
                float hw = half * (0.12f + 0.88f * f);
                float above = (gy - (top + row)) / (150f * uu);
                float dx = (float) Math.sin(t * 0.8f) * 1.8f * uu * above;
                int y = (int) (top + row);
                box(g, (int) (tx + dx - hw), y, (int) (tx + dx + hw), y + 2, 0xFF1B4A3A);
                box(g, (int) (tx + dx + hw * 0.25f), y, (int) (tx + dx + hw), y + 2, 0xFF256052);
                if (row < 9 * uu) box(g, (int) (tx + dx - hw * 0.8f), y, (int) (tx + dx + hw * 0.8f), y + 2, a(0xF2F7FF, 0.92f));
            }
        }
    }

    // ------------------------------------------------------------------ moving things

    /** Petals / leaves / snow drifting down. origin* is where they start, spread* how wide, wind pushes them left. */
    private static void fall(GuiGraphicsExtractor g, float t, int n, float originX, float spreadX, float originY,
                             float endY, float wind, int[] colors, int size, float period,
                             int x1, int y1, int W, int H) {
        for (int i = 0; i < n; i++) {
            float p = period * (0.8f + (i % 5) * 0.12f);
            float k = ((t + i * 1.37f) % p) / p;
            float x = originX + (((i * 37) % 100) / 100f - 0.5f) * spreadX
                + (float) Math.sin(t * 1.3f + i * 2.1f) * 8f * u - k * wind;
            float y = originY + k * (endY - originY);
            float al = k < 0.08f ? k / 0.08f : (k > 0.85f ? (1f - k) / 0.15f : 1f);
            int wdt = size + (Math.sin(t * 5f + i) > 0 ? 1 : 0);
            box(g, (int) x, (int) y, (int) x + wdt, (int) y + size, a(colors[i % colors.length], al));
        }
    }

    private static void fireflies(GuiGraphicsExtractor g, int x1, int y1, int W, int H, int gy, float t, int n) {
        for (int i = 0; i < n; i++) {
            float x = x1 + frac(i * 0.61803f) * W + (float) Math.sin(t * 0.6f + i * 1.7f) * 10f * u;
            float y = gy - (8f + frac(i * 0.37f) * 90f) * u + (float) Math.sin(t * 0.8f + i * 2.3f) * 7f * u;
            float blink = 0.5f + 0.5f * (float) Math.sin(t * 2.0f + i * 1.3f);
            box(g, (int) x - 1, (int) y - 1, (int) x + 2, (int) y + 2, a(0xFFF08A, 0.12f + 0.2f * blink));
            box(g, (int) x, (int) y, (int) x + 1, (int) y + 1, a(0xFFFFC0, 0.4f + 0.6f * blink));
        }
    }

    private static void birds(GuiGraphicsExtractor g, int x1, int y1, int W, int H, float t, int n) {
        for (int i = 0; i < n; i++) {
            float x = x1 + ((t * (14f + i * 3f) + i * 83f) % (W + 60f)) - 30f;
            float y = y1 + H * 0.20f + i * 14f * u + (float) Math.sin(t * 0.9f + i) * 4f * u;
            boolean up = Math.sin(t * 9f + i * 2f) > 0;
            int xi = (int) x, yi = (int) y;
            int c = 0xFF1A1220;
            box(g, xi - 4, yi + (up ? 0 : 1), xi - 1, yi + (up ? 1 : 2), c);
            box(g, xi + 1, yi + (up ? 0 : 1), xi + 4, yi + (up ? 1 : 2), c);
            box(g, xi - 1, yi + 1, xi + 1, yi + 2, c);
        }
    }

    // ------------------------------------------------------------------ primitives (all clipped)

    private static int a(int rgb, float al) {
        int v = Math.max(0, Math.min(255, (int) (al * 255f)));
        return (v << 24) | (rgb & 0xFFFFFF);
    }

    private static float frac(float v) { return v - (float) Math.floor(v); }

    private static void box(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int c) {
        if (x1 < cl) x1 = cl;
        if (y1 < ct) y1 = ct;
        if (x2 > cr) x2 = cr;
        if (y2 > cb) y2 = cb;
        if (x2 <= x1 || y2 <= y1) return;
        g.fill(x1, y1, x2, y2, c);
    }

    private static void grad(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int c1, int c2) {
        if (x1 < cl || x2 > cr || y1 < ct || y2 > cb || x2 <= x1 || y2 <= y1) return;
        g.fillGradient(x1, y1, x2, y2, c1, c2);
    }

    private static void disc(GuiGraphicsExtractor g, float cx, float cy, float r, int c) {
        int ri = (int) Math.ceil(r);
        for (int dy = -ri; dy < ri; dy += 2) {
            float yy = dy + 1;
            int hw = (int) Math.sqrt(Math.max(0f, r * r - yy * yy));
            if (hw <= 0) continue;
            box(g, (int) (cx - hw), (int) (cy + dy), (int) (cx + hw), (int) (cy + dy) + 2, c);
        }
    }

    private static void ell(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, int c) {
        int ri = (int) Math.ceil(ry);
        for (int dy = -ri; dy < ri; dy += 2) {
            float yy = (dy + 1) / Math.max(1f, ry);
            float k = 1f - yy * yy;
            if (k <= 0f) continue;
            int hw = (int) (rx * Math.sqrt(k));
            box(g, (int) (cx - hw), (int) (cy + dy), (int) (cx + hw), (int) (cy + dy) + 2, c);
        }
    }

    private static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float th, int c) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = Math.max(1, (int) Math.max(Math.abs(dx), Math.abs(dy)));
        int h = Math.max(1, (int) th);
        for (int i = 0; i <= steps; i++) {
            float k = i / (float) steps;
            int x = (int) (x0 + dx * k), y = (int) (y0 + dy * k);
            box(g, x - h / 2, y - h / 2, x - h / 2 + h, y - h / 2 + h, c);
        }
    }
}

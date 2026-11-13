package com.tensku.gamblehud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws custom text in a handful of styles on top of Minecraft's own font
 * (outline, 3D shadow, neon, rainbow, wave, bubble, bouncing bubble).
 */
public final class TextFx {
    private TextFx() {}

    public static final int CLASSIC = 0, OUTLINE = 1, SHADOW3D = 2, NEON = 3, RAINBOW = 4, WAVE = 5, BUBBLE = 6, BOUNCE = 7;
    public static final String[] FONTS = {"Classic", "Outline", "3D Shadow", "Neon", "Rainbow", "Wave", "Bubble", "Bubble Bounce"};

    public static final int[] PALETTE = {
        0xFFFFFF, 0xFF4D4D, 0xFF9A3D, 0xFFD93D, 0x7CFF9B, 0x3DDCFF,
        0x4D7CFF, 0xB36BFF, 0xFF4DB8, 0x000000, 0x8A8F9C, 0x6B3F1E,
    };

    private static final String S = "\u00a7";

    private static String codes(TextItem it) {
        StringBuilder sb = new StringBuilder();
        if (it.bold || it.font == BUBBLE || it.font == BOUNCE) sb.append(S).append('l');
        if (it.italic) sb.append(S).append('o');
        if (it.underline) sb.append(S).append('n');
        if (it.strike) sb.append(S).append('m');
        return sb.toString();
    }

    private static float scale(TextItem it) {
        return Math.max(0.5f, Math.min(8f, it.scale));
    }

    private static int pad(TextItem it) {
        return (int) Math.ceil(3 * scale(it));
    }

    /** {x, y, w, h} on screen (position resolved from the 0..1 fractions). */
    public static int[] rect(Font font, TextItem it, int sw, int sh) {
        String txt = it.text == null ? "" : it.text;
        float s = scale(it);
        int p = pad(it);
        int w = (int) Math.ceil(font.width(codes(it) + txt) * s) + 2 * p;
        int h = (int) Math.ceil(9 * s) + 2 * p;
        int x = Math.round(it.x * Math.max(0, sw - w));
        int y = Math.round(it.y * Math.max(0, sh - h));
        x = Math.max(0, Math.min(Math.max(0, sw - w), x));
        y = Math.max(0, Math.min(Math.max(0, sh - h), y));
        return new int[] {x, y, w, h};
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the text with its top-left corner (including padding) at (x, y). */
    public static void draw(GuiGraphicsExtractor g, Font font, TextItem it, float x, float y, long now) {
        String txt = it.text == null ? "" : it.text;
        if (txt.isEmpty()) return;
        float s = scale(it);
        float t = (now % 1_000_000L) / 1000f;
        String cd = codes(it);
        int rgb = it.color & 0xFFFFFF, orgb = it.outline & 0xFFFFFF;
        int fill = 0xFF000000 | rgb, out = 0xFF000000 | orgb;
        int p = pad(it);

        g.pose().pushMatrix();
        g.pose().translate(x + p, y + p);
        g.pose().scale(s, s);

        switch (it.font) {
            case OUTLINE -> {
                for (int[] d : DIR8) tx(g, font, cd + txt, d[0], d[1], out, false);
                tx(g, font, cd + txt, 0, 0, fill, false);
            }
            case SHADOW3D -> {
                for (int i = 3; i >= 1; i--) tx(g, font, cd + txt, i, i, i == 3 ? alpha(orgb, 0.55f) : out, false);
                tx(g, font, cd + txt, 0, 0, fill, false);
            }
            case NEON -> {
                float flick = 0.85f + 0.15f * (float) Math.sin(t * 17f) * (float) Math.sin(t * 5.3f);
                for (int[] d : DIR8) tx(g, font, cd + txt, d[0] * 2, d[1] * 2, alpha(rgb, 0.10f * flick), false);
                for (int[] d : DIR8) tx(g, font, cd + txt, d[0], d[1], alpha(rgb, 0.32f * flick), false);
                tx(g, font, cd + txt, 0, 0, 0xFF000000 | mix(rgb, 0xFFFFFF, 0.65f), false);
            }
            case RAINBOW -> perChar(g, font, cd, txt, t, 3, 0, out, true);
            case WAVE -> perChar(g, font, cd, txt, t, 1, rgb, out, true);
            case BUBBLE -> {
                tx(g, font, cd + txt, 0, 3, 0x66000000, false);           // soft drop shadow
                for (int[] d : RING16) tx(g, font, cd + txt, d[0], d[1], out, false);
                for (int[] d : DIR8) tx(g, font, cd + txt, d[0] / 2f, d[1] / 2f, out, false);
                tx(g, font, cd + txt, 0, 0, fill, false);
            }
            case BOUNCE -> perChar(g, font, cd, txt, t, 2, rgb, out, false);
            default -> tx(g, font, cd + txt, 0, 0, fill, true);
        }
        g.pose().popMatrix();
    }

    /** Text at a fractional position (the GUI text call only takes whole pixels, so shift with the matrix). */
    private static void tx(GuiGraphicsExtractor g, Font font, String str, float x, float y, int color, boolean shadow) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        if (fx == 0f && fy == 0f) {
            g.text(font, str, ix, iy, color, shadow);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(fx, fy);
        g.text(font, str, ix, iy, color, shadow);
        g.pose().popMatrix();
    }

    private static final int[][] DIR8 = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    private static final int[][] RING16 = ring(16, 2f);

    private static int[][] ring(int n, float r) {
        int[][] out = new int[n][2];
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            out[i][0] = Math.round((float) Math.cos(a) * r);
            out[i][1] = Math.round((float) Math.sin(a) * r);
        }
        return out;
    }

    /** mode 1 = wave, 2 = bouncing bubble, 3 = rainbow colours. */
    private static void perChar(GuiGraphicsExtractor g, Font font, String cd, String txt, float t, int mode, int rgb, int outCol, boolean shadow) {
        float cx = 0;
        for (int i = 0; i < txt.length(); i++) {
            String ch = cd + txt.charAt(i);
            float yy = 0;
            int col = 0xFF000000 | rgb;
            switch (mode) {
                case 1 -> yy = (float) Math.sin(t * 5f + i * 0.6f) * 2f;
                case 2 -> yy = -Math.abs((float) Math.sin(t * 4f + i * 0.55f)) * 2.5f;
                default -> col = 0xFF000000 | hsv((t * 0.35f + i * 0.07f) % 1f);
            }
            if (mode == 2) {
                // bubble look for each letter: shadow, ring outline, fill
                tx(g, font, ch, cx, yy + 3, 0x66000000, false);
                for (int[] d : RING16) tx(g, font, ch, cx + d[0], yy + d[1], outCol, false);
                for (int[] d : DIR8) tx(g, font, ch, cx + d[0] / 2f, yy + d[1] / 2f, outCol, false);
                tx(g, font, ch, cx, yy, col, false);
            } else {
                tx(g, font, ch, cx, yy, col, shadow);
            }
            cx += font.width(ch);
        }
    }

    private static int hsv(float h) {
        float s = 0.75f, v = 1f;
        float r = 0, gr = 0, b = 0;
        int i = (int) (h * 6f);
        float f = h * 6f - i, p = v * (1 - s), q = v * (1 - f * s), tt = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; gr = tt; b = p; }
            case 1 -> { r = q; gr = v; b = p; }
            case 2 -> { r = p; gr = v; b = tt; }
            case 3 -> { r = p; gr = q; b = v; }
            case 4 -> { r = tt; gr = p; b = v; }
            default -> { r = v; gr = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (gr * 255) << 8) | (int) (b * 255);
    }

    private static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    static int alpha(int rgb, float a) {
        int al = Math.max(0, Math.min(255, (int) (a * 255f)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }
}

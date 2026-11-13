package com.tensku.gamblehud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * HUD font choice. "Minecraft" = the normal game font. Anything else is a regular font installed on the PC:
 * every letter is drawn with Java's font engine into a small texture (cached) and drawn on the HUD.
 * Understands the usual colour / bold / italic / underline / strike codes.
 */
public final class HudFont {
    static { System.setProperty("java.awt.headless", "true"); }

    private HudFont() {}

    public static final String MINECRAFT = "Minecraft";

    private static final String[] CURATED = {
        "Arial", "Arial Black", "Calibri", "Cambria", "Candara", "Century Gothic", "Comic Sans MS", "Consolas",
        "Courier New", "Georgia", "Impact", "Lucida Console", "Palatino Linotype", "Segoe UI", "Tahoma",
        "Times New Roman", "Trebuchet MS", "Verdana", "Helvetica", "Helvetica Neue", "Menlo", "Monaco",
        "Ubuntu", "DejaVu Sans", "Liberation Sans", "Noto Sans",
    };

    private static final int[] MC_COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    };

    // glyph texture layout, in GUI units: 2 spare on each side, baseline 9 down, 13 tall in total
    private static final int PAD = 2, BASE = 9, GH = 13;
    private static final int SLOTS = 1500;
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private record Glyph(Identifier id, int w, int h, int pw, int ph, int slot) {}

    private static boolean broken = false;
    public static String lastError = "";

    private static final Map<String, java.awt.Font> BASES = new HashMap<>();
    private static final Map<String, java.awt.Font> DERIVED = new HashMap<>();
    private static final Map<String, Float> ADV = new HashMap<>();
    private static final Map<String, Glyph> GLYPHS = new LinkedHashMap<>(256, 0.75f, true);
    private static int nextSlot = 0;
    private static List<String> installed = null;

    // ------------------------------------------------------------------ public API

    /** Same as g.text(...) but in the chosen HUD font. */
    public static void text(GuiGraphicsExtractor g, Font mc, String s, int x, int y, int color, boolean shadow) {
        if (s == null || s.isEmpty()) return;
        if (!active()) {
            g.text(mc, s, x, y, color, shadow);
            return;
        }
        try {
            drawPc(g, s, x, y, color, shadow);
        } catch (Throwable t) {
            fail(t);
            g.text(mc, s, x, y, color, shadow);
        }
    }

    /** Same as font.width(...) but in the chosen HUD font. */
    public static int width(Font mc, String s) {
        if (s == null || s.isEmpty()) return 0;
        if (!active()) return mc.width(s);
        try {
            return (int) Math.ceil(measure(Config.I.hudFont, s));
        } catch (Throwable t) {
            fail(t);
            return mc.width(s);
        }
    }

    /** Shorten the text with ".." so it fits in maxWidth. */
    public static String fit(Font mc, String s, int maxWidth) {
        if (width(mc, s) <= maxWidth) return s;
        while (s.length() > 1 && width(mc, s + "..") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    /** True when a PC font (not the Minecraft font) is selected and usable. */
    public static boolean active() {
        if (broken) return false;
        String n = Config.I.hudFont;
        if (n == null || n.isEmpty() || n.equals(MINECRAFT)) return false;
        return base(n) != null;
    }

    /** All fonts the menu can pick from: Minecraft first, then the common PC fonts that are installed. */
    public static List<String> options() {
        List<String> out = new ArrayList<>();
        out.add(MINECRAFT);
        try {
            List<String> all = installedFamilies();
            Map<String, String> lower = new HashMap<>();
            for (String n : all) lower.put(n.toLowerCase(Locale.ROOT), n);
            for (String n : CURATED) {
                String real = lower.get(n.toLowerCase(Locale.ROOT));
                if (real != null && !out.contains(real)) out.add(real);
            }
            out.add("Sans Serif");
            out.add("Serif");
            out.add("Monospace");
            if (Config.I.hudFontAll) {
                for (String n : all) {
                    if (n.startsWith(".") || out.contains(n)) continue;
                    out.add(n);
                }
            }
        } catch (Throwable t) {
            out.add("Sans Serif");
            out.add("Serif");
            out.add("Monospace");
        }
        return out;
    }

    /** Pick the next (dir = 1) or previous (dir = -1) font and save it. */
    public static void cycle(int dir) {
        List<String> list = options();
        int i = list.indexOf(Config.I.hudFont);
        if (i < 0) i = 0;
        Config.I.hudFont = list.get(((i + dir) % list.size() + list.size()) % list.size());
        Config.save();
    }

    // ------------------------------------------------------------------ fonts

    private static List<String> installedFamilies() {
        if (installed == null) {
            TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String n : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ENGLISH)) set.add(n);
            installed = new ArrayList<>(set);
        }
        return installed;
    }

    private static java.awt.Font base(String name) {
        if (BASES.containsKey(name)) return BASES.get(name);
        java.awt.Font f = null;
        try {
            String n = name;
            boolean logical = true;
            switch (name) {
                case "Sans Serif" -> n = java.awt.Font.SANS_SERIF;
                case "Serif" -> n = java.awt.Font.SERIF;
                case "Monospace" -> n = java.awt.Font.MONOSPACED;
                default -> logical = false;
            }
            java.awt.Font t = new java.awt.Font(n, java.awt.Font.PLAIN, 8);
            // an unknown name silently becomes "Dialog" - only accept the font if it really is the one asked for
            if (logical || t.getFamily(Locale.ENGLISH).equalsIgnoreCase(name) || t.getFontName(Locale.ENGLISH).equalsIgnoreCase(name)) f = t;
        } catch (Throwable e) {
            lastError = String.valueOf(e);
        }
        BASES.put(name, f);
        return f;
    }

    /** The font at 8 GUI units tall, with the style bits (1 bold, 2 italic). Falls back for letters the font lacks. */
    private static java.awt.Font fontFor(String name, int style, char c) {
        String key = name + "|" + style;
        java.awt.Font f = DERIVED.get(key);
        if (f == null) {
            java.awt.Font b = base(name);
            if (b == null) b = base("Sans Serif");
            f = b.deriveFont(style, 8f);
            DERIVED.put(key, f);
        }
        if (!f.canDisplay(c)) {
            String fk = "Sans Serif|" + style;
            java.awt.Font fb = DERIVED.get(fk);
            if (fb == null) {
                java.awt.Font b = base("Sans Serif");
                if (b != null) {
                    fb = b.deriveFont(style, 8f);
                    DERIVED.put(fk, fb);
                }
            }
            if (fb != null) return fb;
        }
        return f;
    }

    private static float advance(String name, int style, char c) {
        String key = name + "|" + style + "|" + (int) c;
        Float v = ADV.get(key);
        if (v == null) {
            v = (float) fontFor(name, style, c).getStringBounds(String.valueOf(c), FRC).getWidth();
            ADV.put(key, v);
        }
        return v;
    }

    private static int codeStyle(int style, char k) {
        switch (k) {
            case 'l': return style | 1;
            case 'o': return style | 2;
            case 'n': return style | 4;
            case 'm': return style | 8;
            case 'k': return style;
            default: return (k == 'r' || Character.digit(k, 16) >= 0) ? 0 : style;
        }
    }

    private static float measure(String name, String s) {
        float w = 0;
        int style = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                style = codeStyle(style, Character.toLowerCase(s.charAt(++i)));
                continue;
            }
            w += advance(name, style & 3, c);
        }
        return w;
    }

    // ------------------------------------------------------------------ drawing

    private static void drawPc(GuiGraphicsExtractor g, String s, int x, int y, int argb, boolean shadow) {
        String name = Config.I.hudFont;
        var m = g.pose();
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        double ps = Math.hypot(m.m00(), m.m01());
        // pixels per GUI unit as the screen will really show it (so letters stay sharp when scaled), in steps of 0.5
        float q = (float) Math.max(0.5, Math.min(10.0, Math.round(gs * ps * 2.0) / 2.0));
        if ((argb >>> 24) == 0) argb |= 0xFF000000;
        if (shadow) run(g, name, s, x + 1, y + 1, argb, q, true);
        run(g, name, s, x, y, argb, q, false);
    }

    private static void run(GuiGraphicsExtractor g, String name, String s, float x, int y, int argb, float q, boolean dark) {
        int alpha = argb >>> 24;
        int baseRgb = argb & 0xFFFFFF;
        int rgb = baseRgb;
        int style = 0;
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                char k = Character.toLowerCase(s.charAt(++i));
                int d = Character.digit(k, 16);
                if (k == 'r') rgb = baseRgb;
                else if (d >= 0) rgb = MC_COLORS[d];
                style = codeStyle(style, k);
                continue;
            }
            float adv = advance(name, style & 3, c);
            int col = dark ? ((rgb & 0xFCFCFC) >> 2) : rgb;
            if (c != ' ' && c != '\t' && !Character.isISOControl(c)) {
                Glyph gl = glyph(name, style & 3, c, q, col, alpha);
                if (gl != null) blit(g, gl, cx - PAD, y - 2);
            }
            if ((style & 4) != 0) line(g, cx, y + 8, adv, col, alpha);
            if ((style & 8) != 0) line(g, cx, y + 4, adv, col, alpha);
            cx += adv;
        }
    }

    private static void line(GuiGraphicsExtractor g, float x, int y, float w, int rgb, int alpha) {
        g.fill((int) Math.floor(x), y, (int) Math.ceil(x + w), y + 1, (alpha << 24) | rgb);
    }

    private static void blit(GuiGraphicsExtractor g, Glyph gl, float lx, float ly) {
        int ix = (int) Math.floor(lx), iy = (int) Math.floor(ly);
        float fx = lx - ix, fy = ly - iy;
        boolean shift = fx > 0.001f || fy > 0.001f;
        if (shift) {
            g.pose().pushMatrix();
            g.pose().translate(fx, fy);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, gl.id(), ix, iy, 0f, 0f, gl.w(), gl.h(), gl.pw(), gl.ph(), gl.pw(), gl.ph());
        if (shift) g.pose().popMatrix();
    }

    private static int quant(int v) {
        return (v & 0xF0) | (v >> 4);
    }

    /** One letter as a texture in the wanted colour (cached). The colour is baked in, so it is rounded a little to keep the cache small. */
    private static Glyph glyph(String name, int style, char c, float q, int rgb, int alpha) {
        int rq = (quant((rgb >> 16) & 255) << 16) | (quant((rgb >> 8) & 255) << 8) | quant(rgb & 255);
        int aq = Math.round(alpha / 255f * 15f);
        if (aq <= 0) return null;
        String key = name + "|" + style + "|" + (int) c + "|" + (int) (q * 2) + "|" + Integer.toHexString(rq) + "|" + aq;
        Glyph gl = GLYPHS.get(key);
        if (gl != null) return gl;
        try {
            int slot;
            if (GLYPHS.size() >= SLOTS) {
                var it = GLYPHS.entrySet().iterator();
                slot = it.next().getValue().slot();   // least recently used one: reuse its texture slot
                it.remove();
            } else {
                slot = nextSlot++;
            }
            gl = make(name, style, c, q, rq, Math.round(aq * 255f / 15f), slot);
            GLYPHS.put(key, gl);
            return gl;
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    private static Glyph make(String name, int style, char c, float q, int rq, int a255, int slot) throws Exception {
        float adv = advance(name, style, c);
        int w = (int) Math.ceil(adv) + 2 * PAD;
        int pw = Math.max(1, Math.round(w * q)), ph = Math.max(1, Math.round(GH * q));

        BufferedImage bi = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.scale(pw / (double) w, ph / (double) GH);
            g2.setFont(fontFor(name, style, c));
            g2.setColor(new Color((rq >> 16) & 255, (rq >> 8) & 255, rq & 255, a255));
            g2.drawString(String.valueOf(c), (float) PAD, (float) BASE);
        } finally {
            g2.dispose();
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", bos);
        NativeImage img = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
        Identifier id = Identifier.fromNamespaceAndPath(TenskuGambleHud.MOD_ID, "hudfont/g" + slot);
        DynamicTexture tex = new DynamicTexture(() -> "tenskugamblehud hudfont", img);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        return new Glyph(id, w, GH, pw, ph, slot);
    }

    private static void fail(Throwable t) {
        broken = true;
        lastError = t.getMessage() == null ? t.toString() : t.getMessage();
    }
}

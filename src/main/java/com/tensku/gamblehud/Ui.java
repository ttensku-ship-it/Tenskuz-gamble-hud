package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Shared panel drawing (background, hills, drifting particles). */
public final class Ui {
    private Ui() {}

    /** Card/panel background: the theme gradient. (Always returns false - kept so the callers did not need changing.) */
    public static boolean backdrop(GuiGraphicsExtractor g, Theme th, int x1, int y1, int x2, int y2) {
        g.fillGradient(x1, y1, x2, y2, th.top(), th.bottom());
        return false;
    }

    public static void panel(GuiGraphicsExtractor g, Theme th, int px, int py, int w, int h) {
        g.fill(px, py, px + w, py + h, 0xFF0E0F16);
        int x1 = px + 2, y1 = py + 2, x2 = px + w - 2, y2 = py + h - 2;
        boolean img = backdrop(g, th, x1, y1, x2, y2);
        boolean scenic = th.scene() != 0 && !img;
        if (scenic) {
            Scenery.draw(g, th.scene(), x1, y1, x2, y2, System.currentTimeMillis());
        } else if (!img) {
            for (int x = x1; x < x2; x += 2) {
                int hh = (int) (22 + 12 * Math.sin(x * 0.03) + 6 * Math.sin(x * 0.08));
                g.fill(x, y2 - hh, x + 2, y2, th.hill());
            }
        }
        long t = System.currentTimeMillis();
        for (int i = 0; i < (scenic ? 0 : 36); i++) {
            int seed = i * 7919;
            float fx = ((seed % 397) + (t * (0.004f + (i % 5) * 0.002f))) % (w - 4);
            float fy = (((seed / 3) % 211) + (t * (0.006f + (i % 3) * 0.003f))) % (h - 4);
            g.fill(x1 + (int) fx, y1 + (int) fy, x1 + (int) fx + 2, y1 + (int) fy + 2, 0x99FFFFFF);
        }
    }

    /** Click sound for any menu button. */
    public static void click() {
        if (!Config.I.clickSounds) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    public static String time(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    public static String fit(net.minecraft.client.gui.Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        while (s.length() > 1 && font.width(s + "..") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "..";
    }
}

package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * "Are you sure?" card for big /pay commands: pops in with a chime, spinning gold coin,
 * glowing amount, hover-lit buttons, and a little coin cascade when you confirm.
 */
public class ConfirmPayScreen extends Screen {
    private static final int GOLD = 0xF2C94C;
    private static final int GREEN = 0x4ADE80;
    private static final int RED = 0xFF6B6B;

    private final String command;
    private final String target;
    private final double amount;
    private final long opened = System.currentTimeMillis();

    private int bx1, by1, bw, bh;      // confirm button
    private int cx1, cy1;               // cancel button (same size)
    private int hover;                  // 0 none, 1 confirm, 2 cancel
    private int lastHover;

    public ConfirmPayScreen(String command, String target, double amount) {
        super(Component.literal("Confirm payment"));
        this.command = command;
        this.target = target;
        this.amount = amount;
    }

    @Override
    protected void init() {
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f);
        later(70, () -> play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
    }

    // ------------------------------------------------------------------ sound

    private static void play(SoundEvent e, float pitch) {
        if (!Config.I.alertSound) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(e, pitch)));
    }

    private static void later(long ms, Runnable r) {
        Thread th = new Thread(() -> {
            try { Thread.sleep(ms); } catch (InterruptedException ignored) { return; }
            r.run();
        }, "tensku-pay-sfx");
        th.setDaemon(true);
        th.start();
    }

    /** Rising coin cascade ending in a level-up ding. */
    private static void coinCascade() {
        float[] pitch = {0.9f, 1.1f, 1.3f, 1.55f, 1.85f};
        for (int i = 0; i < pitch.length; i++) {
            final float p = pitch[i];
            later(i * 75L, () -> play(SoundEvents.EXPERIENCE_ORB_PICKUP, p));
        }
        later(400, () -> play(SoundEvents.PLAYER_LEVELUP, 1.3f));
        later(430, () -> play(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f));
    }

    // ------------------------------------------------------------------ actions

    private void confirm() {
        coinCascade();
        onClose();
        TenskuGambleHud.sendCommandBypass(command);
    }

    private void cancel() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f);
        onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // ignore clicks while the card is still popping in (stops accidental double clicks)
        if (System.currentTimeMillis() - opened < 300) return true;
        double mx = event.x(), my = event.y();
        if (inside(mx, my, bx1, by1)) { confirm(); return true; }
        if (inside(mx, my, cx1, cy1)) { cancel(); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean inside(double mx, double my, int x, int y) {
        return mx >= x && mx < x + bw && my >= y && my < y + bh;
    }

    // ------------------------------------------------------------------ drawing

    private static int alpha(int rgb, float a) {
        int al = Math.max(0, Math.min(255, (int) (a * 255f)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static float easeOutBack(float x) {
        x = Math.max(0f, Math.min(1f, x));
        float c1 = 1.70158f, c3 = c1 + 1f, p = x - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme th = Theme.current();
        long now = System.currentTimeMillis();
        float age = (now - opened) / 1000f;
        float t = (now % 1_000_000L) / 1000f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3f);
        int accent = th.accent() & 0xFFFFFF;

        // dim the world, fading in
        g.fill(0, 0, width, height, alpha(0x000000, 0.68f * Math.min(1f, age / 0.25f)));

        int cx = width / 2, cy = height / 2;
        int pw = 300, ph = 138;
        int x1 = cx - pw / 2, y1 = cy - ph / 2, x2 = cx + pw / 2, y2 = cy + ph / 2;

        // button layout (needed for hover before drawing)
        bw = 128; bh = 22;
        bx1 = cx - bw - 6; cx1 = cx + 6;
        by1 = cy1 = y2 - bh - 12;
        hover = 0;
        if (age > 0.3f) {
            if (inside(mouseX, mouseY, bx1, by1)) hover = 1;
            else if (inside(mouseX, mouseY, cx1, cy1)) hover = 2;
        }
        if (hover != 0 && hover != lastHover) play(SoundEvents.UI_BUTTON_CLICK.value(), 1.9f);
        lastHover = hover;

        // pop-in scale around the card centre
        float sc = easeOutBack(age / 0.38f);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(sc, sc);
        g.pose().translate(-cx, -cy);

        // soft glow behind the card
        for (int i = 6; i >= 1; i--) {
            g.fill(x1 - i * 2, y1 - i * 2, x2 + i * 2, y2 + i * 2, alpha(GOLD, (0.020f + 0.014f * pulse) * (7 - i) / 6f));
        }
        // gold border, dark inner edge, theme gradient body
        g.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, alpha(GOLD, 0.95f));
        g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF14151D);
        g.fillGradient(x1, y1, x2, y2, th.top(), th.bottom());
        g.fillGradient(x1, y1, x2, y1 + 44, alpha(GOLD, 0.20f), alpha(GOLD, 0f));
        // light sweeping along the top edge
        float sweep = (t * 120f) % (pw + 80f) - 40f;
        for (int i = 0; i < 28; i++) {
            int sx = (int) (x1 + sweep + i);
            if (sx > x1 && sx < x2) g.fill(sx, y1 - 2, sx + 1, y1, alpha(0xFFFFFF, 0.8f * (1f - Math.abs(i - 14) / 14f)));
        }
        // corner studs
        int[][] corners = {{x1, y1}, {x2 - 6, y1}, {x1, y2 - 6}, {x2 - 6, y2 - 6}};
        for (int[] c : corners) {
            g.fill(c[0], c[1], c[0] + 6, c[1] + 6, 0xFF000000 | GOLD);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 3, c[1] + 3, 0xFFFFF3B0);
        }

        // rising gold sparkles inside the card
        for (int i = 0; i < 14; i++) {
            float k = (t * (0.16f + (i % 4) * 0.04f) + i * 0.29f) % 1f;
            int sx = x1 + 8 + (int) (((i * 67) % 100) / 100f * (pw - 16));
            int sy = y2 - 4 - (int) (k * (ph - 8));
            int a = (int) (Math.sin(k * Math.PI) * 190f);
            int sz = i % 3 == 0 ? 2 : 1;
            g.fill(sx, sy, sx + sz, sy + sz, (a << 24) | (i % 2 == 0 ? 0xFFE27A : 0xFFFFFF));
        }

        // spinning coin on the left
        coin(g, x1 + 38, y1 + 52, 16, t);

        // title
        int mid = cx + 22;
        String title = "CONFIRM PAYMENT";
        g.text(font, title, mid - font.width(title) / 2, y1 + 9, 0xFF000000 | GOLD, true);
        g.fill(mid - font.width(title) / 2, y1 + 20, mid + font.width(title) / 2, y1 + 21, alpha(GOLD, 0.55f));

        // big glowing amount
        String amt = Money.format(amount);
        float as = 2.6f;
        int aw = (int) (font.width(amt) * as);
        float ax = mid - aw / 2f, ay = y1 + 28;
        for (int i = 3; i >= 1; i--) {
            g.fill((int) ax - i * 3, (int) ay - i, (int) ax + aw + i * 3, (int) ay + (int) (8 * as) + i,
                alpha(GOLD, 0.045f + 0.03f * pulse));
        }
        g.pose().pushMatrix();
        g.pose().translate(ax + as, ay + as);
        g.pose().scale(as, as);
        g.text(font, amt, 0, 0, 0xFF5A4410, false);   // drop shadow
        g.pose().popMatrix();
        g.pose().pushMatrix();
        g.pose().translate(ax, ay);
        g.pose().scale(as, as);
        g.text(font, amt, 0, 0, 0xFFFFE27A, false);
        g.pose().popMatrix();

        // recipient name tag
        String to = "to  " + target;
        int tw = font.width(to) + 14;
        int tx = mid - tw / 2, ty = y1 + 62;
        g.fill(tx - 1, ty - 1, tx + tw + 1, ty + 14, alpha(accent, 0.55f));
        g.fill(tx, ty, tx + tw, ty + 13, 0xDD0B0C12);
        g.fill(tx, ty + 12, tx + tw, ty + 13, 0xFF000000 | accent);
        g.text(font, to, tx + 7, ty + 3, 0xFFFFFFFF, true);

        String warn = "This can't be undone";
        g.text(font, warn, mid - font.width(warn) / 2, ty + 18, alpha(0xFFFFFF, 0.45f), false);

        // buttons
        button(g, "Confirm", bx1, by1, GREEN, hover == 1, pulse);
        button(g, "Cancel", cx1, cy1, RED, hover == 2, 0f);

        g.pose().popMatrix();
    }

    private void button(GuiGraphicsExtractor g, String label, int x, int y, int rgb, boolean hot, float pulse) {
        int lift = hot ? 1 : 0;
        if (hot) g.fill(x - 2, y - 2 - lift, x + bw + 2, y + bh + 2 - lift, alpha(rgb, 0.30f));
        g.fill(x, y - lift, x + bw, y + bh - lift, 0xFF000000 | rgb);
        g.fillGradient(x + 1, y + 1 - lift, x + bw - 1, y + bh - 1 - lift,
            hot ? alpha(rgb, 0.55f) : 0xF0222838, hot ? alpha(rgb, 0.28f) : 0xF00C0E16);
        g.fillGradient(x + 1, y + 1 - lift, x + bw - 1, y + bh / 2 - lift, alpha(0xFFFFFF, hot ? 0.20f : 0.07f), alpha(0xFFFFFF, 0f));
        if (pulse > 0f && !hot) g.fill(x, y + bh - 2, x + bw, y + bh, alpha(rgb, 0.25f + 0.35f * pulse));
        int tc = hot ? 0xFFFFFFFF : (0xFF000000 | rgb);
        g.text(font, label, x + (bw - font.width(label)) / 2, y + (bh - 8) / 2 - lift, tc, true);
    }

    /** Gold coin that spins around its vertical axis (squashed ellipse with a rim and a $ on the faces). */
    private void coin(GuiGraphicsExtractor g, int cx, int cy, int r, float t) {
        float ang = t * 3.2f;
        float c = (float) Math.cos(ang);
        float w = Math.max(2f, Math.abs(c) * r);
        float bob = (float) Math.sin(t * 2.4f) * 2f;
        int y0 = (int) (cy + bob);
        // shadow
        g.fill((int) (cx - r * 0.7f), cy + r + 6, (int) (cx + r * 0.7f), cy + r + 8, 0x55000000);
        // edge thickness when side-on
        int thick = (int) (4 * (1f - Math.abs(c)));
        for (int d = thick; d >= 0; d--) {
            for (int dy = -r; dy <= r; dy++) {
                float rw = (float) Math.sqrt(Math.max(0f, 1f - (dy * dy) / (float) (r * r))) * w;
                int col = d == 0 ? 0xFFFFD34D : 0xFFB8860B;
                g.fill((int) (cx - rw) + d, y0 + dy, (int) (cx + rw) + d, y0 + dy + 1, col);
            }
        }
        // inner rim ring, shine and a $ on the faces
        if (Math.abs(c) > 0.25f) {
            int ir = r - 3;
            float iw = Math.max(1f, Math.abs(c) * ir);
            for (int dy = -ir; dy <= ir; dy++) {
                float rw = (float) Math.sqrt(Math.max(0f, 1f - (dy * dy) / (float) (ir * ir))) * iw;
                g.fill((int) (cx - rw), y0 + dy, (int) (cx + rw), y0 + dy + 1, 0xFFF2B51D);
            }
            g.fill((int) (cx - w * 0.55f), y0 - r + 3, (int) (cx - w * 0.2f), y0 - r + 5, 0xCCFFFFFF);
            if (Math.abs(c) > 0.55f) {
                float s = 1.6f * Math.abs(c);
                g.pose().pushMatrix();
                g.pose().translate(cx, y0);
                g.pose().scale(s, 1.6f);
                g.text(font, "$", -font.width("$") / 2, -4, 0xFF8A5A00, false);
                g.pose().popMatrix();
            }
        }
    }
}

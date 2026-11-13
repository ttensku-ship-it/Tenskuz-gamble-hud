package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Big-payment animations. Four levels, picked by amount (or fixed in Alerts):
 * 1 BIG (coins), 2 HUGE (>= $100M: + fireworks, rings), 3 JACKPOT (>= $1B: + flash, light beams,
 * coin rain, rainbow banner, many fireworks), 4 LEGENDARY (>= $10B: everything, longer and bigger).
 */
public final class PaymentAnimation {
    private PaymentAnimation() {}

    public static final String[] STYLE_NAMES = {"Auto (by amount)", "Classic", "Huge", "Jackpot", "Legendary"};
    private static final String[] TIER_NAMES = {"", "BIG PAYMENT", "HUGE PAYMENT", "JACKPOT", "LEGENDARY"};
    private static final float[] LIFE = {0f, 3.4f, 4.4f, 6.2f, 8.0f};
    private static final float[] AMT_SCALE = {0f, 2f, 2.5f, 3f, 3.4f};
    private static final int[] COINS = {0, 40, 70, 100, 140};
    private static final int[] BURSTS = {0, 0, 2, 6, 10};
    private static final int[] RAIN = {0, 0, 0, 60, 110};
    private static final int[] FW_COLORS = {0xFFF2C94C, 0xFF7CFF9B, 0xFFFF6B9D, 0xFF6BC8FF, 0xFFFFFFFF, 0xFFC08BFF};

    private static final Random RNG = new Random();
    private static final List<Fx> ACTIVE = new ArrayList<>();

    private static final class Fx {
        final long start = System.nanoTime();
        final double amount;
        final String from;
        final int tier;
        final float life;
        final float[] ang, spd;
        final int[] col;
        final float[] bx, by, bt;
        final int[] bcol;
        final float[] rx, rsp, rdel, rph;
        final boolean[] snd = new boolean[3];

        Fx(double amount, String from, int tier) {
            this.amount = amount;
            this.from = from;
            this.tier = tier;
            this.life = LIFE[tier];

            int n = COINS[tier];
            ang = new float[n];
            spd = new float[n];
            col = new int[n];
            for (int i = 0; i < n; i++) {
                ang[i] = (float) (-Math.PI * (0.05 + 0.9 * RNG.nextDouble()));
                spd[i] = 90f + RNG.nextFloat() * (200f + 40f * tier);
                col[i] = (i % 3 == 0) ? 0xFFF2C94C : (i % 3 == 1 ? 0xFFFFE27A : 0xFFE0A526);
            }

            int b = BURSTS[tier];
            bx = new float[b];
            by = new float[b];
            bt = new float[b];
            bcol = new int[b];
            for (int k = 0; k < b; k++) {
                bt[k] = 0.25f + k * (life - 2.2f) / Math.max(1, b) + RNG.nextFloat() * 0.2f;
                bx[k] = 0.12f + 0.76f * RNG.nextFloat();
                by[k] = 0.12f + 0.40f * RNG.nextFloat();
                bcol[k] = FW_COLORS[RNG.nextInt(FW_COLORS.length)];
            }

            int r = RAIN[tier];
            rx = new float[r];
            rsp = new float[r];
            rdel = new float[r];
            rph = new float[r];
            for (int i = 0; i < r; i++) {
                rx[i] = RNG.nextFloat();
                rsp[i] = 110f + RNG.nextFloat() * 170f;
                rdel[i] = RNG.nextFloat() * (life - 1.5f);
                rph[i] = RNG.nextFloat() * 6.28f;
            }
        }

        float t() { return (System.nanoTime() - start) / 1_000_000_000f; }
    }

    private static int tierFor(double amount) {
        int style = Config.I.animStyle;
        if (style >= 1 && style <= 4) return style;
        if (amount >= 1e10) return 4;
        if (amount >= 1e9) return 3;
        if (amount >= 1e8) return 2;
        return 1;
    }

    /** Called for a real payment. */
    public static void trigger(double amount, String from) {
        begin(amount, from, tierFor(amount));
    }

    /** Test button: always plays the chosen level (1-4). */
    public static void preview(double amount, int tier) {
        begin(amount, "Tensku", Math.max(1, Math.min(4, tier)));
    }

    private static void begin(double amount, String from, int tier) {
        synchronized (ACTIVE) {
            ACTIVE.add(new Fx(amount, from, tier));
        }
        if (Config.I.alertSound) playSound(tier >= 3 ? 0.9f : 1.2f);
    }

    private static void playSound(float pitch) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, pitch)));
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float p = x - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }

    private static int rainbow(float h) {
        h -= (float) Math.floor(h);
        float x = h * 6f;
        int i = (int) x;
        float fr = x - i;
        float r, gr, b;
        switch (i) {
            case 0 -> { r = 1; gr = fr; b = 0; }
            case 1 -> { r = 1 - fr; gr = 1; b = 0; }
            case 2 -> { r = 0; gr = 1; b = fr; }
            case 3 -> { r = 0; gr = 1 - fr; b = 1; }
            case 4 -> { r = fr; gr = 0; b = 1; }
            default -> { r = 1; gr = 0; b = 1 - fr; }
        }
        return ((int) (r * 255) << 16) | ((int) (gr * 255) << 8) | (int) (b * 255);
    }

    public static void render(GuiGraphicsExtractor g, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        Theme th = Theme.current();
        synchronized (ACTIVE) {
            ACTIVE.removeIf(f -> f.t() > f.life);
            for (Fx f : ACTIVE) draw(g, mc, th, f, sw, sh);
        }
    }

    private static void waveText(GuiGraphicsExtractor g, Minecraft mc, String text, int cx, int y,
                                 float scale, float t, float fade) {
        int total = HudFont.width(mc.font, text);
        g.pose().pushMatrix();
        g.pose().translate((float) cx, (float) y);
        g.pose().scale(scale, scale);
        int x = -total / 2;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int dy = (int) (Math.sin(t * 7f + i * 0.7f) * 2.5f);
            HudFont.text(g, mc.font, ch, x, dy, Theme.alpha(rainbow(t * 0.7f + i * 0.08f), fade), true);
            x += HudFont.width(mc.font, ch);
        }
        g.pose().popMatrix();
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, Theme th, Fx f, int sw, int sh) {
        final float t = f.t();
        final int tier = f.tier;
        final float fade = t > f.life - 0.8f ? Math.max(0f, (f.life - t) / 0.8f) : 1f;
        final float pulse = (float) (0.5 + 0.5 * Math.sin(t * 8f));

        // extra sounds for the two biggest levels
        if (tier >= 3 && Config.I.alertSound) {
            float[] at = {0.9f, 1.8f, 2.7f};
            float[] pt = {1.5f, 0.8f, 1.9f};
            int n = tier == 4 ? 3 : 2;
            for (int i = 0; i < n; i++) {
                if (!f.snd[i] && t >= at[i]) {
                    f.snd[i] = true;
                    playSound(pt[i]);
                }
            }
        }

        // banner geometry (several effects are centred on it)
        final float amtScale = AMT_SCALE[tier];
        final String amt = "+" + Money.format(f.amount);
        final String sub = tier >= 3 ? "from " + f.from : TIER_NAMES[tier] + "  -  " + f.from;
        final int aw = HudFont.width(mc.font, amt);
        final int bw = Math.max(Math.max(170, HudFont.width(mc.font, sub) + 30), (int) (aw * amtScale) + 40);
        final int bh = (int) (8 * amtScale) + 26;
        final int target = tier >= 3 ? (int) (sh * 0.24f) : 22;
        final float slide = Math.min(1f, t / 0.55f);
        final int by = (int) (-bh + (target + bh) * easeOutBack(slide));
        final int bx = (sw - bw) / 2;
        final float ccx = sw / 2f, ccy = by + bh / 2f;

        // screen flash on the first moments of a jackpot
        if (tier >= 3 && t < 0.35f) {
            g.fill(0, 0, sw, sh, Theme.alpha(tier == 4 ? 0xFFFFFF : 0xFFE9B0, (1f - t / 0.35f) * 0.55f));
        }

        // edge glow
        float glow = fade * (0.18f + 0.07f * tier + 0.12f * pulse) * Math.min(1f, t * 4f);
        int gh = 40 + 20 * tier;
        int gc = tier >= 3 ? Theme.alpha(rainbow(t * 0.6f), glow) : Theme.alpha(th.accent(), glow);
        g.fillGradient(0, 0, sw, gh, gc, 0x00000000);
        g.fillGradient(0, sh - gh, sw, sh, 0x00000000, gc);

        // light beams from the top
        if (tier >= 3) {
            for (int k = 0; k < 7; k++) {
                int x = (int) (sw * (k + 0.5f) / 7f);
                int half = 5 + (int) (3 * Math.sin(t * 2f + k));
                float a = fade * 0.10f * (0.5f + 0.5f * (float) Math.sin(t * 3f + k));
                g.fillGradient(x - half, 0, x + half, (int) (sh * 0.8f), Theme.alpha(0xFFE27A, a), 0x00000000);
            }
        }

        // coin rain
        for (int i = 0; i < f.rx.length; i++) {
            float u = t - f.rdel[i];
            if (u < 0f) continue;
            float y = u * f.rsp[i] - 8f;
            if (y > sh) continue;
            int x = (int) (f.rx[i] * sw);
            int cw = 1 + (int) (Math.abs(Math.cos(t * 5f + f.rph[i])) * 3f);
            g.fill(x, (int) y, x + cw, (int) y + 4, Theme.alpha(0xF2C94C, fade));
            g.fill(x, (int) y, x + 1, (int) y + 4, Theme.alpha(0xFFFFFF, fade * 0.7f));
        }

        // fireworks
        for (int k = 0; k < f.bx.length; k++) {
            float u = t - f.bt[k];
            if (u < 0f || u > 1.5f) continue;
            float cx = f.bx[k] * sw, cy = f.by[k] * sh;
            int col = f.bcol[k] & 0xFFFFFF;
            if (u < 0.12f) {
                g.fill((int) cx - 3, (int) cy - 3, (int) cx + 3, (int) cy + 3, Theme.alpha(0xFFFFFF, fade * (1f - u / 0.12f)));
            }
            final int n = 26;
            for (int j = 0; j < n; j++) {
                float ang = (float) (Math.PI * 2 * j / n) + k * 0.4f;
                float spd = 55f + 65f * (((j * 37) % 11) / 10f);
                for (int m = 0; m < 4; m++) {
                    float uu = Math.max(0f, u - m * 0.04f);
                    float px = cx + (float) Math.cos(ang) * spd * uu * (1f - 0.25f * uu);
                    float py = cy + (float) Math.sin(ang) * spd * uu + 0.5f * 70f * uu * uu;
                    float a = fade * (1f - u / 1.5f) * (1f - m * 0.28f);
                    g.fill((int) px, (int) py, (int) px + 2, (int) py + 2, Theme.alpha(col, a));
                }
            }
        }

        // shockwave rings
        if (tier >= 2) {
            int rings = tier - 1;
            int maxR = Math.max(sw, sh);
            for (int r = 0; r < rings; r++) {
                float u = t - 0.15f * r;
                if (u <= 0f || u >= 1.4f) continue;
                int rad = (int) (u * maxR * 0.55f);
                int c = Theme.alpha(tier >= 3 ? rainbow(t * 0.5f + r * 0.2f) : 0xF2C94C, (1f - u / 1.4f) * 0.6f * fade);
                g.outline((int) ccx - rad, (int) ccy - rad, rad * 2, rad * 2, c);
                g.outline((int) ccx - rad - 1, (int) ccy - rad - 1, rad * 2 + 2, rad * 2 + 2, c);
            }
        }

        // banner
        int border = tier >= 3 ? Theme.alpha(rainbow(t * 0.8f), fade * (0.8f + 0.2f * pulse))
                               : Theme.alpha(0xF2C94C, fade * (0.7f + 0.3f * pulse));
        g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, border);
        if (tier == 4) {
            g.outline(bx - 5, by - 5, bw + 10, bh + 10, Theme.alpha(rainbow(t * 0.8f + 0.5f), fade));
        }
        g.fillGradient(bx, by, bx + bw, by + bh, Theme.alpha(th.top() & 0xFFFFFF, fade * 0.95f),
                       Theme.alpha(th.bottom() & 0xFFFFFF, fade * 0.95f));

        if (tier >= 3) waveText(g, mc, TIER_NAMES[tier], sw / 2, by - 24, 2f, t, fade);

        float pop = 1f + 0.25f * Math.max(0f, 1f - t * 4f);
        float scale = amtScale * pop;
        int amtColor = tier >= 3 ? rainbow(t * 0.5f + 0.15f) : 0x7CFF9B;
        g.pose().pushMatrix();
        g.pose().translate(sw / 2f, by + 6f);
        g.pose().scale(scale, scale);
        HudFont.text(g, mc.font, amt, -aw / 2, 0, Theme.alpha(amtColor, fade), true);
        g.pose().popMatrix();

        int sw2 = HudFont.width(mc.font, sub);
        HudFont.text(g, mc.font, sub, (sw - sw2) / 2, by + 6 + (int) (8 * amtScale) + 5, Theme.alpha(0xFFE9B0, fade), true);

        // shimmer sweep
        float sweep = (t * 1.4f) % 1.6f;
        int sx = bx + (int) (sweep * bw) - 10;
        if (sx > bx && sx < bx + bw - 6) {
            g.fill(sx, by, sx + 6, by + bh, Theme.alpha(0xFFFFFF, fade * 0.18f));
        }

        // coin burst
        for (int i = 0; i < f.ang.length; i++) {
            float vx = (float) Math.cos(f.ang[i]) * f.spd[i];
            float vy = (float) Math.sin(f.ang[i]) * f.spd[i];
            float px = ccx + vx * t;
            float py = ccy + vy * t + 0.5f * 380f * t * t;
            if (py > sh + 8) continue;
            int size = (i % 4 == 0) ? 4 : 3;
            int c = (f.col[i] & 0xFFFFFF) | (Math.max(0, Math.min(255, (int) (fade * 255))) << 24);
            g.fill((int) px, (int) py, (int) px + size, (int) py + size, c);
            g.fill((int) px, (int) py, (int) px + 1, (int) py + size, Theme.alpha(0xFFFFFF, fade * 0.7f));
        }
    }
}

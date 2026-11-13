package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Celebration shown when an auction has a winner: rockets fly up and burst into coloured sparks. */
public final class Fireworks {
    private Fireworks() {}

    private static final class Burst {
        float fx, fy;      // where it explodes, as a fraction of the screen
        long delay;        // ms after launch
        int c1, c2, count;
        boolean big, sounded;
        float spin;
    }

    private static final int[] COLORS = {
        0xFFD24D, 0xFF5A5A, 0x6FD3FF, 0x7CFF9B, 0xFF6FD8, 0xC08BFF, 0xFFFFFF, 0xFF9A47, 0x52F2C8
    };
    private static final float RISE = 0.65f, LIFE = 2.0f;

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final Random RNG = new Random();
    private static long startedAt, endsAt;

    public static void launch() {
        long now = System.currentTimeMillis();
        BURSTS.clear();
        long span = Math.max(4000L, Math.min(8000L, Config.I.winnerBannerSeconds * 1000L + 1500L));
        int n = (int) (span / 330L);
        for (int i = 0; i < n; i++) {
            Burst b = new Burst();
            b.delay = i * span / n + RNG.nextInt(180);
            b.fx = 0.08f + 0.84f * RNG.nextFloat();
            b.fy = 0.10f + 0.38f * RNG.nextFloat();
            b.c1 = COLORS[RNG.nextInt(COLORS.length)];
            b.c2 = COLORS[RNG.nextInt(COLORS.length)];
            b.big = RNG.nextInt(4) == 0;
            b.count = b.big ? 44 : 28 + RNG.nextInt(10);
            b.spin = RNG.nextFloat() * 6.28f;
            BURSTS.add(b);
        }
        startedAt = now;
        endsAt = now + span + (long) ((RISE + LIFE) * 1000f) + 200L;
    }

    private static void sound(boolean big) {
        if (!Config.I.alertSound) return;
        try {
            float pitch = 0.9f + RNG.nextFloat() * 0.35f;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                big ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST : SoundEvents.FIREWORK_ROCKET_BLAST, pitch));
        } catch (Exception ignored) { }
    }

    private static void dot(GuiGraphicsExtractor g, float x, float y, int size, int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        if (a < 6) return;
        g.fill((int) x, (int) y, (int) x + size, (int) y + size, (a << 24) | rgb);
    }

    public static void render(GuiGraphicsExtractor g, int sw, int sh) {
        if (BURSTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now > endsAt) { BURSTS.clear(); return; }
        float sc = Math.max(0.8f, sh / 270f);

        for (Burst b : BURSTS) {
            float t = (now - startedAt - b.delay) / 1000f;
            if (t < 0f) continue;
            float ex = b.fx * sw, ey = b.fy * sh;

            if (t < RISE) { // rocket climbing with a sparkly trail
                float k = t / RISE;
                for (int j = 0; j < 8; j++) {
                    float kk = Math.max(0f, k - j * 0.035f);
                    float e = 1f - (1f - kk) * (1f - kk);
                    float x = ex + (float) Math.sin(kk * 9f + b.spin) * 2f;
                    float y = sh + (ey - sh) * e;
                    dot(g, x, y, j == 0 ? 3 : 2, j < 2 ? 0xFFFFFF : 0xFFC15A, 1f - j / 8f);
                }
                continue;
            }

            float u = t - RISE;
            if (u > LIFE) continue;
            if (!b.sounded) { b.sounded = true; sound(b.big); }
            float life = u / LIFE;

            if (u < 0.14f) { // flash
                float fa = 1f - u / 0.14f;
                int r = (int) (10 * sc * (b.big ? 1.4f : 1f));
                dot(g, ex - r, ey - 1, r * 2, 0xFFFFFF, 0.25f * fa);
                dot(g, ex - 1, ey - r, 2, 0xFFFFFF, 0.5f * fa);
                dot(g, ex - 2, ey - 2, 4, 0xFFFFFF, fa);
            }

            float reach = (b.big ? 1.35f : 1f) * sc;
            for (int i = 0; i < b.count; i++) {
                double ang = b.spin + i * (Math.PI * 2.0 / b.count);
                float speed = (52f + (i % 3) * 22f) * reach;
                int rgb = (i % 2 == 0) ? b.c1 : b.c2;
                for (int tr = 0; tr < 3; tr++) { // head + two trailing sparks
                    float uu = u - tr * 0.045f;
                    if (uu < 0f) continue;
                    float dist = speed * (1f - (float) Math.exp(-2.4f * uu)) / 2.4f;
                    float x = ex + (float) Math.cos(ang) * dist;
                    float y = ey + (float) Math.sin(ang) * dist + 0.5f * 70f * sc * uu * uu;
                    float a = (1f - life) * (1f - life) * (tr == 0 ? 1f : (tr == 1 ? 0.55f : 0.28f));
                    if (((i + (int) (now / 70L)) % 5) == 0) a *= 0.35f; // twinkle
                    dot(g, x, y, tr == 0 && life < 0.55f ? 2 : 1, rgb, a);
                }
            }
        }
    }
}

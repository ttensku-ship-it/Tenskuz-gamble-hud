package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.Random;

/**
 * Winner cutscene: letterbox bars, spotlights, and two real 3D skins - yours and the winner's.
 * You walk in from the left, the winner from the right, you hold the item out, it flies across in an arc,
 * the winner catches it (flash, shockwave, fireworks) and celebrates with it over their head.
 * Both usernames hang over the characters.
 */
public final class Cutscene {
    private Cutscene() {}

    public static final long DURATION_MS = 9400L;

    private static final Identifier STEVE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final int[] CONFETTI = {0xFFD24D, 0xFF5A5A, 0x6FD3FF, 0x7CFF9B, 0xFF6FD8, 0xC08BFF, 0xFFFFFF, 0xFF9A47};

    // key moments (seconds)
    private static final float WALK_END = 1.7f, HOLD_AT = 1.9f, FLY_AT = 3.0f, FLY_LEN = 1.3f, CATCH_AT = FLY_AT + FLY_LEN;

    private static boolean running = false;
    private static long startedAt;
    private static String itemName = "", itemImage = "", winner = "", host = "";
    private static double amount;
    private static boolean fireworksDone, soundDone;

    private static final Model3D.Pose HP = new Model3D.Pose(), WP = new Model3D.Pose();

    // ------------------------------------------------------------------ control

    public static void start(String item, String image, String winnerName, double amt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        itemName = item == null || item.isBlank() ? "Auction item" : item;
        itemImage = image == null ? "" : image;
        winner = winnerName == null ? "Winner" : winnerName;
        host = mc.player.getName().getString();
        amount = amt;
        startedAt = System.currentTimeMillis();
        fireworksDone = false;
        soundDone = false;
        running = true;
    }

    /** Test run from the menu. */
    public static void preview() {
        start("Diamond Sword", "", "Steve", 5_000_000);
    }

    public static boolean running() {
        return running && System.currentTimeMillis() - startedAt < DURATION_MS;
    }

    // ------------------------------------------------------------------ helpers

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float smooth(float v) {
        v = clamp01(v);
        return v * v * (3f - 2f * v);
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private static Identifier skinOf(Minecraft mc, String name) {
        try {
            if (mc.player != null && name.equalsIgnoreCase(mc.player.getName().getString())) {
                return mc.player.getSkin().body().texturePath();
            }
            var conn = mc.getConnection();
            if (conn != null) {
                var info = conn.getPlayerInfo(name);
                if (info != null) return info.getSkin().body().texturePath();
            }
        } catch (Throwable ignored) { }
        return STEVE;
    }

    /** Screen position of a hand with the arm held out forward, relative to the hip. {x, y} in model units. */
    private static float[] handOut(boolean rightArm, float yaw) {
        Model3D.Pose p = new Model3D.Pose();
        p.yaw = yaw;
        if (rightArm) p.armRPitch = 1.35f; else p.armLPitch = 1.35f;
        float[] h = Model3D.armPoint(p, rightArm, 0f, 9f, 0f, true);
        return new float[] {h[0], h[1]};
    }

    // ------------------------------------------------------------------ poses

    private static void poses(float t) {
        HP.reset();
        WP.reset();

        float walk = clamp01((WALK_END + 0.1f - t) / 0.4f);              // 1 while walking in, then 0
        float ph = t * 9f;
        float turn = smooth((t - 0.8f) / 1.0f);                           // profile view -> three-quarter view
        HP.yaw = lerp(1.15f, 0.55f, turn);
        WP.yaw = -lerp(1.15f, 0.55f, turn);

        float swing = (float) Math.sin(ph);
        for (Model3D.Pose p : new Model3D.Pose[] {HP, WP}) {
            p.legRPitch = swing * 0.75f * walk;
            p.legLPitch = -swing * 0.75f * walk;
            p.armRPitch = -swing * 0.5f * walk;
            p.armLPitch = swing * 0.5f * walk;
            p.y = -Math.abs(swing) * 0.8f * walk;
            p.headYaw = (float) Math.sin(t * 1.3f) * 0.08f * (1f - walk);
        }
        // the winner swings the other way so they do not walk in lockstep
        WP.legRPitch = -WP.legRPitch; WP.legLPitch = -WP.legLPitch;
        WP.armRPitch = -WP.armRPitch; WP.armLPitch = -WP.armLPitch;

        // host holds the item out in the right hand, then lowers the arm after the throw
        float hold = smooth((t - HOLD_AT) / 0.7f) * (1f - smooth((t - (FLY_AT + 0.4f)) / 0.5f));
        float wob = (float) Math.sin(t * 5f) * 0.04f;
        HP.armRPitch = lerp(HP.armRPitch, 1.35f + wob, hold);
        HP.headPitch = 0.1f * hold;

        // winner raises the near (left) arm to catch it
        float recv = smooth((t - (FLY_AT - 0.4f)) / 0.6f);
        WP.armLPitch = lerp(WP.armLPitch, 1.35f + wob, recv);

        if (t >= CATCH_AT) {
            float c = t - CATCH_AT;
            float up = smooth(c / 0.35f);
            float s = smooth(c / 0.5f);
            // winner: jumps with both arms up, turns to the camera and sways
            WP.armLPitch = lerp(1.35f, 2.8f, up) + (float) Math.sin(c * 9f) * 0.25f * up;
            WP.armRPitch = lerp(WP.armRPitch, 2.8f, up) + (float) Math.cos(c * 9f) * 0.25f * up;
            WP.y = -Math.abs((float) Math.sin(c * 4.2f)) * 5f * up;
            WP.yaw = lerp(WP.yaw, (float) Math.sin(c * 2.2f) * 0.55f, s);
            WP.headPitch = -0.2f * up;
            WP.legRPitch = 0.25f * (float) Math.sin(c * 8.4f) * up;
            WP.legLPitch = -WP.legRPitch;
            float flash = clamp01(1f - c / 0.3f);
            if (flash > 0f) WP.tint = ((int) (170 * flash) << 24) | 0xFFFFFF;
            // host: fist pump + a little hop
            HP.armRPitch = lerp(HP.armRPitch, 2.8f, up) + (float) Math.sin(c * 9f) * 0.3f * up;
            HP.armLPitch = 0.5f * (float) Math.sin(c * 6f) * up;
            HP.y = -Math.abs((float) Math.sin(c * 4.2f + 0.7f)) * 2.5f * up;
            HP.yaw = lerp(HP.yaw, 0.35f, s);
        }
    }

    // ------------------------------------------------------------------ render

    public static void render(GuiGraphicsExtractor g, Minecraft mc, int sw, int sh) {
        if (!running) return;
        long now = System.currentTimeMillis();
        float t = (now - startedAt) / 1000f;
        float total = DURATION_MS / 1000f;
        if (t >= total) { running = false; return; }

        Theme th = Theme.current();
        int accent = th.accent() & 0xFFFFFF;
        float vis = Math.min(smooth(t / 0.5f), smooth((total - t) / 0.7f));        // overlay in / out
        float exit = smooth((t - (total - 0.9f)) / 0.9f);                           // scene slides away at the end

        poses(t);

        int S = Math.max(2, Math.min(6, Math.round(sh / 90f)));
        float hostX0 = sw * 0.30f, winX0 = sw * 0.70f;
        float walkP = 1f - (float) Math.pow(1f - clamp01(t / WALK_END), 3.0);
        float hostX = lerp(-24f * S, hostX0, walkP);
        float winX = lerp(sw + 24f * S, winX0, walkP);
        float feetY = sh * 0.80f;
        float hipY = feetY - Model3D.FEET_DROP * S;

        // ---- backdrop
        g.fill(0, 0, sw, sh, Theme.alpha(0x05060C, 0.62f * vis));
        g.fillGradient(0, 0, sw, sh / 3, Theme.alpha(0x000000, 0.45f * vis), 0x00000000);
        g.fillGradient(0, sh * 2 / 3, sw, sh, 0x00000000, Theme.alpha(0x000000, 0.5f * vis));

        // ---- shake on the catch
        float shakeX = 0f, shakeY = 0f;
        if (t >= CATCH_AT && t < CATCH_AT + 0.35f) {
            float k = 1f - (t - CATCH_AT) / 0.35f;
            shakeX = (float) Math.sin(t * 95f) * 3.5f * k;
            shakeY = (float) Math.cos(t * 83f) * 2.5f * k;
        }

        g.pose().pushMatrix();
        g.pose().translate(shakeX, shakeY + exit * sh * 0.6f);

        float catchK = t >= CATCH_AT ? smooth((t - CATCH_AT) / 0.6f) : 0f;
        spotlight(g, hostX0, feetY, sw, sh, accent, 0.07f * vis, t, 0f);
        spotlight(g, winX0, feetY, sw, sh, t >= CATCH_AT ? 0xFFD24D : accent, (0.07f + 0.10f * catchK) * vis, t, 1.7f);
        floor(g, sw, sh, feetY, accent, vis, t);
        disc(g, hostX0, feetY, 13f * S, accent, t);
        disc(g, winX0, feetY, 13f * S, t >= CATCH_AT ? 0xFFD24D : accent, t);

        // ---- characters
        DancingSkin.GuiCanvas canvas = new DancingSkin.GuiCanvas(g);
        Identifier hostSkin = skinOf(mc, host), winSkin = skinOf(mc, winner);
        shadow(g, hostX, feetY, 8f * S);
        shadow(g, winX, feetY, 8f * S);
        DancingSkin.outline(canvas, hostSkin, false, HP, hostX, hipY, S);
        Model3D.draw(canvas, hostSkin, true, HP, hostX, hipY, S);
        DancingSkin.outline(canvas, winSkin, false, WP, winX, hipY, S);
        Model3D.draw(canvas, winSkin, true, WP, winX, hipY, S);

        // ---- the item
        float[] hh = handOut(true, 0.55f), wh = handOut(false, -0.55f);
        float sx = hostX0 + hh[0] * S, sy = hipY + hh[1] * S;      // where the host holds it
        float ex = winX0 + wh[0] * S, ey = hipY + wh[1] * S;       // where the winner catches it
        int isz = 8 * S;
        if (t >= HOLD_AT && t < FLY_AT) {
            float pop = smooth((t - HOLD_AT) / 0.4f);
            item(g, mc, th, sx, sy - 4f * S, Math.max(4, (int) (isz * pop)), now, 1f);
            sparkle(g, sx, sy - 4f * S, S, accent, t, 5);
        } else if (t >= FLY_AT && t < CATCH_AT) {
            float u = (t - FLY_AT) / FLY_LEN;
            float arc = 15f * S;
            for (int k = 11; k >= 1; k--) {                          // glowing trail
                float uk = Math.max(0f, u - k * 0.028f);
                float e = uk * uk * (3f - 2f * uk);
                float px = lerp(sx, ex, e), py = lerp(sy - 4f * S, ey - 4f * S, e) - (float) Math.sin(Math.PI * e) * arc;
                int a = (int) (150f * (1f - k / 12f));
                int sz = Math.max(2, (int) (isz * 0.35f * (1f - k / 12f)));
                g.fill((int) px - sz / 2, (int) py - sz / 2, (int) px + sz / 2 + 1, (int) py + sz / 2 + 1, (a << 24) | (k % 2 == 0 ? 0xFFFFFF : accent));
            }
            float e = u * u * (3f - 2f * u);
            float px = lerp(sx, ex, e), py = lerp(sy - 4f * S, ey - 4f * S, e) - (float) Math.sin(Math.PI * e) * arc;
            item(g, mc, th, px, py, isz + (int) (Math.sin(Math.PI * e) * S * 3), now, 3f);
        } else if (t >= CATCH_AT) {
            // held high above the winner's head, spinning
            float c = t - CATCH_AT;
            float rise = smooth(c / 0.5f);
            float hx = winX + WP.x * S;
            float headTop = hipY + WP.y * S - 19f * S;
            float ix = lerp(ex, hx, rise), iy = lerp(ey - 4f * S, headTop - 6.5f * S - 4f, rise);
            item(g, mc, th, ix, iy, (int) (isz * lerp(1f, 1.35f, rise)), now, 1.6f);
            sparkle(g, ix, iy, S, 0xFFD24D, t, 9);
        }

        // ---- catch effects: shockwave rings + rays
        if (t >= CATCH_AT && t < CATCH_AT + 1.0f) {
            float c = t - CATCH_AT;
            for (int ring = 0; ring < 2; ring++) {
                float rc = c - ring * 0.18f;
                if (rc <= 0f || rc > 0.8f) continue;
                float k = rc / 0.8f;
                float rad = (1f - (1f - k) * (1f - k)) * 30f * S;
                int a = (int) (230f * (1f - k));
                for (int i = 0; i < 48; i++) {
                    double ang = i * Math.PI * 2.0 / 48.0;
                    int px = (int) (ex + Math.cos(ang) * rad), py = (int) (ey + Math.sin(ang) * rad * 0.8);
                    g.fill(px, py, px + 2, py + 2, (a << 24) | (ring == 0 ? 0xFFFFFF : 0xFFD24D));
                }
            }
            if (c < 0.5f) {
                float k = c / 0.5f;
                for (int i = 0; i < 14; i++) {
                    double ang = i * Math.PI * 2.0 / 14.0 + 0.2;
                    float r1 = 6f * S * k, r2 = (10f + 26f * k) * S;
                    int a = (int) (200f * (1f - k));
                    for (int s = 0; s < 8; s++) {
                        float rr = lerp(r1, r2, s / 7f);
                        int px = (int) (ex + Math.cos(ang) * rr), py = (int) (ey + Math.sin(ang) * rr);
                        g.fill(px, py, px + 2, py + 2, (a << 24) | 0xFFE9A0);
                    }
                }
            }
        }

        // ---- confetti after the catch
        if (t >= CATCH_AT) confetti(g, sw, sh, t - CATCH_AT);

        // ---- name tags
        float tagH = 30f * S + 10f;   // plates ride along with the characters (they jump), clear of the item above the head
        tag(g, mc, hostX, hipY + HP.y * S - tagH, host, "HOST", accent, 0xFFB9BDCB, vis);
        tag(g, mc, winX, hipY + WP.y * S - tagH, winner, "WINNER", accent, 0xFFFFD24D, vis);

        g.pose().popMatrix();

        // ---- letterbox bars, title, caption
        int bar = Math.round(sh * 0.11f * vis);
        g.fill(0, 0, sw, bar, 0xFF000000);
        g.fill(0, sh - bar, sw, sh, 0xFF000000);
        g.fill(0, bar, sw, bar + 1, Theme.alpha(accent, 0.8f * vis));
        g.fill(0, sh - bar - 1, sw, sh - bar, Theme.alpha(accent, 0.8f * vis));

        float titleIn = smooth((t - 0.4f) / 0.5f) * (1f - exit);
        if (titleIn > 0f) {
            float ts = Math.max(2f, sh / 120f);
            String title = "AUCTION WON";
            float tw = HudFont.width(mc.font, title) * ts;
            float tx = (sw - tw) / 2f;
            float ty = lerp(-20f, bar + 6f, titleIn);
            float shine = 0.5f + 0.5f * (float) Math.sin(t * 3f);
            g.pose().pushMatrix();
            g.pose().translate(tx, ty);
            g.pose().scale(ts, ts);
            HudFont.text(g, mc.font, title, 1, 1, Theme.alpha(0x000000, 0.7f * titleIn), false);
            HudFont.text(g, mc.font, title, 0, 0, Theme.alpha(mixRgb(0xFFD24D, 0xFFFFFF, 0.35f * shine), titleIn), false);
            g.pose().popMatrix();
        }

        float capIn = smooth((t - 0.9f) / 0.5f) * (1f - exit);
        if (capIn > 0f && bar > 6) {
            String cap = HudFont.fit(mc.font, itemName, 150) + "   -   " + Money.format(amount);
            float cs = Math.max(1.3f, sh / 190f);
            float cw = HudFont.width(mc.font, cap) * cs;
            g.pose().pushMatrix();
            g.pose().translate((sw - cw) / 2f, sh - bar / 2f - 4f * cs);
            g.pose().scale(cs, cs);
            HudFont.text(g, mc.font, cap, 0, 0, Theme.alpha(0xFFFFFF, capIn), true);
            g.pose().popMatrix();
        }

        // ---- white flash on the catch + one-shot events
        if (t >= CATCH_AT && t < CATCH_AT + 0.35f) {
            float k = 1f - (t - CATCH_AT) / 0.35f;
            g.fill(0, 0, sw, sh, Theme.alpha(0xFFFFFF, 0.45f * k * k));
        }
        if (t >= CATCH_AT && !fireworksDone) {
            fireworksDone = true;
            Fireworks.launch();
        }
        if (t >= CATCH_AT && !soundDone) {
            soundDone = true;
            if (Config.I.alertSound) {
                try {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.1f));
                } catch (Throwable ignored) { }
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    private static int mixRgb(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int gr = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (gr << 8) | bl;
    }

    /** The auction item: its picture as a spinning slab, or a gold tile with its first letter if there is no picture. */
    private static void item(GuiGraphicsExtractor g, Minecraft mc, Theme th, float cx, float cy, int size, long now, float spin) {
        if (size < 2) return;
        CustomImage.Tex t = CustomImage.auctionIcon(itemName, itemImage);
        if (t != null) {
            Item3D.draw(g, t, cx, cy, size, now, 0xFFD24D, false, spin);
            return;
        }
        int x = (int) cx - size / 2, y = (int) cy - size / 2;
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFFFFD24D);
        g.fill(x, y, x + size, y + size, 0xFF3A2E10);
        String ch = itemName.isBlank() ? "?" : itemName.substring(0, 1).toUpperCase();
        HudFont.text(g, mc.font, ch, x + (size - HudFont.width(mc.font, ch)) / 2, y + (size - 8) / 2, 0xFFFFD24D, true);
    }

    private static void sparkle(GuiGraphicsExtractor g, float cx, float cy, int S, int rgb, float t, int n) {
        for (int i = 0; i < n; i++) {
            float k = (t * (0.9f + (i % 3) * 0.3f) + i * 0.31f) % 1f;
            float ang = i * 2.399f + t * 1.5f;
            float r = (5f + k * 11f) * S * 0.6f;
            int a = (int) (Math.sin(k * Math.PI) * 230f);
            int x = (int) (cx + Math.cos(ang) * r), y = (int) (cy + Math.sin(ang) * r * 0.8f - k * 4f * S);
            int sz = i % 3 == 0 ? 2 : 1;
            g.fill(x, y, x + sz, y + sz, (a << 24) | (i % 2 == 0 ? 0xFFFFFF : rgb));
        }
    }

    private static void confetti(GuiGraphicsExtractor g, int sw, int sh, float c) {
        float fade = 1f - smooth((c - 3.2f) / 0.9f);
        if (fade <= 0f) return;
        Random r = new Random(1234);
        for (int i = 0; i < 90; i++) {
            float x0 = r.nextFloat() * sw, speed = 40f + r.nextFloat() * 60f, off = r.nextFloat() * 120f, wob = r.nextFloat() * 6f;
            int col = CONFETTI[r.nextInt(CONFETTI.length)];
            int sz = 2 + r.nextInt(2);
            float delay = r.nextFloat() * 0.8f;
            float k = c - delay;
            if (k < 0f) continue;
            float y = -10f + k * speed * (sh / 270f) + off * 0f;
            if (y > sh + 10f) continue;
            float x = x0 + (float) Math.sin(k * 3f + wob) * 10f;
            int a = (int) (255f * fade);
            g.fill((int) x, (int) y, (int) x + sz, (int) y + sz + (i % 2), (a << 24) | col);
        }
    }

    /** A soft cone of light from the top of the screen down to the floor. */
    private static void spotlight(GuiGraphicsExtractor g, float x, float feetY, int sw, int sh, int rgb, float strength, float t, float ph) {
        float flick = 0.9f + 0.1f * (float) Math.sin(t * 7f + ph);
        int top = 0, bot = (int) feetY;
        for (int y = top; y < bot; y += 2) {
            float k = (y - top) / (float) Math.max(1, bot - top);
            float half = lerp(sh * 0.03f, sh * 0.30f, k);
            int a = (int) (255f * strength * flick * (0.4f + 0.6f * k));
            if (a < 2) continue;
            g.fill((int) (x - half), y, (int) (x + half), y + 2, (a << 24) | rgb);
        }
    }

    /** The glowing ground line + a faint floor reflection band. */
    private static void floor(GuiGraphicsExtractor g, int sw, int sh, float feetY, int rgb, float vis, float t) {
        int y = (int) feetY;
        g.fillGradient(0, y, sw, (int) (sh * 0.89f), Theme.alpha(rgb, 0.20f * vis), 0x00000000);
        g.fill(0, y, sw, y + 1, Theme.alpha(rgb, (0.45f + 0.15f * (float) Math.sin(t * 2f)) * vis));
    }

    private static void disc(GuiGraphicsExtractor g, float cx, float y, float halfW, int rgb, float t) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.2f);
        for (int i = -5; i <= 5; i++) {
            float k = 1f - (i * i) / 30f;
            float w = halfW * (float) Math.sqrt(Math.max(0f, k));
            g.fill((int) (cx - w), (int) y + i, (int) (cx + w), (int) y + i + 1, Theme.alpha(rgb, 0.10f + 0.07f * pulse));
        }
        for (int i = -4; i <= 4; i += 8) {
            float w = halfW * 0.85f;
            g.fill((int) (cx - w), (int) y + i, (int) (cx + w), (int) y + i + 1, Theme.alpha(rgb, 0.35f + 0.25f * pulse));
        }
    }

    private static void shadow(GuiGraphicsExtractor g, float cx, float y, float halfW) {
        for (int i = -2; i <= 2; i++) {
            float w = halfW * (float) Math.sqrt(1.0 - (i / 3.0) * (i / 3.0));
            g.fill((int) (cx - w), (int) y + i, (int) (cx + w), (int) y + i + 1, 0x66000000);
        }
    }

    /** Username plate floating above a character, with a small label ("HOST" / "WINNER") on top. */
    private static void tag(GuiGraphicsExtractor g, Minecraft mc, float cx, float bottomY, String name, String label,
                            int accent, int labelColor, float vis) {
        String n = HudFont.fit(mc.font, name, 140);
        float s = 1.5f;
        int nw = HudFont.width(mc.font, n);
        int pw = (int) (nw * s) + 18;
        int x = (int) (cx - pw / 2f), y = (int) bottomY - 20;

        g.fill(x - 1, y - 1, x + pw + 1, y + 21, Theme.alpha(accent, 0.75f * vis));
        g.fill(x, y, x + pw, y + 20, 0xE60B0C12);
        g.fill(x, y + 19, x + pw, y + 20, 0xFF000000 | accent);
        g.fill((int) cx, y + 21, (int) cx + 1, y + 26, Theme.alpha(accent, 0.6f * vis));   // little pointer

        g.pose().pushMatrix();
        g.pose().translate(cx - nw * s / 2f, y + 5f);
        g.pose().scale(s, s);
        HudFont.text(g, mc.font, n, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();

        int lw = HudFont.width(mc.font, label);
        HudFont.text(g, mc.font, label, (int) (cx - lw / 2f), y - 11, labelColor, true);
    }
}

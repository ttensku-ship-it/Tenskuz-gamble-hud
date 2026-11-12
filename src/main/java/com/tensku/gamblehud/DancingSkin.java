package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * Left of the menu: your skin as a real 3D character that dances, fights a zombie,
 * has your username on top and a rank badge underneath (hover it for the progress bar).
 */
public final class DancingSkin {
    private DancingSkin() {}

    private static final Identifier ZOMBIE = Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");
    private static final DanceScene SCENE = new DanceScene();

    // badge rectangle from the last frame (for the hover tooltip)
    private static boolean badgeShown;
    private static int badgeX, badgeY, badgeW, badgeH;

    /** Draws the model faces through the GUI's matrix stack (every face is a sheared, rotated texture quad). */
    private static final class GuiCanvas implements Model3D.Canvas {
        private final GuiGraphicsExtractor g;
        GuiCanvas(GuiGraphicsExtractor g) { this.g = g; }

        @Override
        public void face(Object tex, float ox, float oy, float ux, float uy, float vx, float vy,
                         int u, int v, int w, int h, int shade, int tint) {
            g.pose().pushMatrix();
            g.pose().mul(new Matrix3x2f(ux, uy, vx, vy, ox, oy));
            g.blit(RenderPipelines.GUI_TEXTURED, (Identifier) tex, 0, 0, u, v, w, h, w, h, 64, 64);
            if (shade != 0) g.fill(0, 0, w, h, shade);
            if (tint != 0) g.fill(0, 0, w, h, tint);
            g.pose().popMatrix();
        }
    }

    public static void render(GuiGraphicsExtractor g, Font font, Minecraft mc, int panelX, int panelY, int panelH) {
        badgeShown = false;
        Identifier skin = skinTexture(mc);
        if (skin == null) return;

        int areaL = 4, areaR = panelX - 4, avail = areaR - areaL;
        if (avail < 64) return; // no room left of the menu

        int S = avail >= 200 ? 3 : 2;
        float near = 17f, far = 31f;
        boolean withZombie = avail >= (far + 16f) * S + 8;

        float t = (System.currentTimeMillis() % 1_000_000L) / 1000f;
        SCENE.update(t, near, far, withZombie);

        float widthUnits = withZombie ? far + 16f : 16f;
        float playerX = areaL + (avail - widthUnits * S) / 2f + 8f * S;
        float feetY = panelY + panelH / 2f + 40f;
        float hipY = feetY - Model3D.FEET_DROP * S;

        GuiCanvas canvas = new GuiCanvas(g);

        // shadows
        shadow(g, playerX + SCENE.player.x * S, feetY, 8f * S);
        if (withZombie && SCENE.zombieVisible) {
            shadow(g, playerX + SCENE.zombie.x * S, feetY, 8f * S * SCENE.zombie.scale);
        }

        // zombie first so your fists end up in front of it
        if (withZombie && SCENE.zombieVisible) {
            Model3D.draw(canvas, ZOMBIE, false, SCENE.zombie, playerX, hipY, S);
        }
        Model3D.draw(canvas, skin, true, SCENE.player, playerX, hipY, S);

        // smoke puff when the zombie goes down
        if (withZombie && SCENE.poof >= 0f) {
            float cx = playerX + SCENE.poofX * S, cy = hipY + 2f * S;
            float k = SCENE.poof;
            for (int i = 0; i < 14; i++) {
                double ang = i * 0.45 + 0.3;
                float r = k * (10f + (i * 7 % 5) * 3f) * S;
                int px = (int) (cx + Math.cos(ang) * r);
                int py = (int) (cy + Math.sin(ang) * r * 0.8f - k * 8f);
                int size = Math.max(1, Math.round((1f - k) * (i % 2 == 0 ? 5 : 3)));
                int a = (int) ((1f - k) * 230f);
                int rgb = i % 3 == 0 ? 0x8FD18A : (i % 3 == 1 ? 0xC8C8C8 : 0x6BAA67);
                g.fill(px, py, px + size, py + size, (a << 24) | rgb);
            }
        }

        // username on top
        String name = mc.player != null ? mc.player.getName().getString() : mc.getUser().getName();
        int nx = (int) (playerX - font.width(name) / 2f);
        nx = Math.max(areaL, Math.min(areaR - font.width(name), nx));
        g.text(font, name, nx, (int) (hipY - 21 * S - 14), 0xFFFFFFFF, true);

        drawBadge(g, font, areaL, areaR, (int) playerX, (int) feetY + 12, t);
    }

    private static void shadow(GuiGraphicsExtractor g, float cx, float y, float halfW) {
        for (int i = -2; i <= 2; i++) {
            float w = halfW * (float) Math.sqrt(1.0 - (i / 3.0) * (i / 3.0));
            g.fill((int) (cx - w), (int) y + i, (int) (cx + w), (int) y + i + 1, 0x55000000);
        }
    }

    private static Identifier skinTexture(Minecraft mc) {
        if (mc.player == null) return null;
        return mc.player.getSkin().body().texturePath();
    }

    // ------------------------------------------------------------------ rank badge

    private static int rankColor(int idx, float t) {
        if (idx == Rank.ALL.length - 1) { // Grandmaster: living rainbow
            return 0xFF000000 | hsv((t * 0.25f) % 1f, 0.75f, 1f);
        }
        return Rank.ALL[idx].color();
    }

    private static int hsv(float h, float s, float v) {
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

    private static int alpha(int rgb, float a) {
        int al = Math.max(0, Math.min(255, (int) (a * 255f)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static void gem(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
        // highlight
        g.fill(cx - 1, cy - r + 1, cx + 1, cy - r + 3, 0xCCFFFFFF);
    }

    private static void drawBadge(GuiGraphicsExtractor g, Font font, int areaL, int areaR, int centreX, int y, float t) {
        double bal = Config.I.balance;
        int idx = Rank.index(bal);
        Rank.Tier tier = Rank.ALL[idx];
        int col = rankColor(idx, t);
        int rgb = col & 0xFFFFFF;

        int bw = Math.min(areaR - areaL, Math.max(70, font.width(tier.name()) + 30));
        int bh = 22;
        int bx = Math.max(areaL, Math.min(areaR - bw, centreX - bw / 2));
        badgeShown = true; badgeX = bx; badgeY = y; badgeW = bw; badgeH = bh;

        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3f);
        // glow
        g.fill(bx - 2, y - 2, bx + bw + 2, y + bh + 2, alpha(rgb, 0.10f + 0.14f * pulse));
        g.fill(bx - 1, y - 1, bx + bw + 1, y + bh + 1, alpha(rgb, 0.25f + 0.20f * pulse));
        // border + body
        g.fill(bx, y, bx + bw, y + bh, 0xFF000000 | rgb);
        g.fillGradient(bx + 1, y + 1, bx + bw - 1, y + bh - 1, 0xF0222838, 0xF00C0E16);
        g.fillGradient(bx + 1, y + 1, bx + bw - 1, y + bh / 2, alpha(rgb, 0.35f), alpha(rgb, 0.04f));

        // shimmer sweeping across
        float sweep = (t * 38f) % (bw + 40f) - 20f;
        for (int dy = 1; dy < bh - 1; dy++) {
            int sx = (int) (bx + sweep - dy * 0.6f);
            int x1 = Math.max(bx + 1, sx), x2 = Math.min(bx + bw - 1, sx + 4);
            if (x2 > x1) g.fill(x1, y + dy, x2, y + dy + 1, 0x33FFFFFF);
        }

        // gem + name + tier pips
        gem(g, bx + 11, y + bh / 2, 5, 0xFF000000 | rgb);
        int tx = bx + 22 + (bw - 22 - font.width(tier.name())) / 2;
        g.text(font, tier.name(), tx, y + 4, 0xFF000000 | rgb, true);
        int pips = idx + 1;
        int pw = Rank.ALL.length * 6 - 2;
        int px0 = bx + 22 + (bw - 22 - pw) / 2;
        for (int i = 0; i < Rank.ALL.length; i++) {
            int c = i < pips ? (0xFF000000 | rgb) : 0xFF3A4054;
            g.fill(px0 + i * 6, y + 15, px0 + i * 6 + 4, y + 18, c);
        }

        // sparkles for the high ranks
        if (idx >= 3) {
            int n = idx - 1;
            for (int i = 0; i < n; i++) {
                float k = ((t * (0.6f + i * 0.13f)) + i * 0.37f) % 1f;
                int sx = bx + 4 + (int) ((i * 37 + 11) % Math.max(1, bw - 8));
                int sy = y - 2 - (int) (k * 12f);
                g.fill(sx, sy, sx + 2, sy + 2, alpha(0xFFFFFF, 1f - k));
            }
        }
    }

    /** Call last, so the tooltip sits on top of everything. */
    public static void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW) {
        if (!badgeShown) return;
        if (mouseX < badgeX || mouseX >= badgeX + badgeW || mouseY < badgeY || mouseY >= badgeY + badgeH) return;

        float t = (System.currentTimeMillis() % 1_000_000L) / 1000f;
        double bal = Config.I.balance;
        int idx = Rank.index(bal);
        Rank.Tier tier = Rank.ALL[idx];
        int col = rankColor(idx, t), rgb = col & 0xFFFFFF;
        boolean max = Rank.isMax(bal);
        boolean known = bal >= 0;

        int tw = 150, th = 78;
        int tx = Math.max(4, Math.min(screenW - tw - 4, badgeX + badgeW / 2 - tw / 2));
        int ty = badgeY - th - 5;
        if (ty < 4) ty = badgeY + badgeH + 5;

        g.fill(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, 0xFF000000 | rgb);
        g.fillGradient(tx, ty, tx + tw, ty + th, 0xF0181C2A, 0xF00A0C14);
        g.fill(tx, ty, tx + tw, ty + 2, 0xFF000000 | rgb);

        g.text(font, "RANK: " + tier.name(), tx + 6, ty + 7, 0xFF000000 | rgb, true);
        String balText = known ? "Balance: " + Money.format(bal) : "Type /bal to sync balance";
        g.text(font, balText, tx + 6, ty + 18, 0xFFE8E8E8, true);

        // progress bar
        int bx = tx + 6, by = ty + 31, bw = tw - 12, bh = 9;
        double prog = Rank.progress(bal);
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000 | rgb);
        g.fill(bx, by, bx + bw, by + bh, 0xFF0B0D14);
        int fillW = (int) Math.round(bw * prog);
        if (fillW > 0) {
            g.fillGradient(bx, by, bx + fillW, by + bh, 0xFF000000 | rgb, alpha(rgb, 0.55f));
            // moving shine
            int sx = bx + (int) ((t * 45f) % (fillW + 20)) - 10;
            int x1 = Math.max(bx, sx), x2 = Math.min(bx + fillW, sx + 6);
            if (x2 > x1) g.fill(x1, by, x2, by + bh, 0x44FFFFFF);
        }
        for (int i = 1; i < 10; i++) g.fill(bx + bw * i / 10, by + bh - 3, bx + bw * i / 10 + 1, by + bh, 0x66000000);
        String pct = max ? "MAX" : (int) (prog * 100) + "%";
        g.text(font, pct, bx + (bw - font.width(pct)) / 2, by + 1, 0xFFFFFFFF, true);

        String next = max ? "Highest rank reached!"
            : Money.format(Rank.remaining(bal)) + " to " + Rank.ALL[idx + 1].name();
        g.text(font, Ui.fit(font, next, tw - 12), tx + 6, ty + 44, 0xFFB9BDCB, true);

        // roadmap of all ranks
        int gap = (tw - 12) / Rank.ALL.length;
        for (int i = 0; i < Rank.ALL.length; i++) {
            int c = rankColor(i, t) & 0xFFFFFF;
            int cx = tx + 6 + i * gap + gap / 2;
            int cy = ty + 63;
            if (i == idx) g.fill(cx - 7, cy - 7, cx + 8, cy + 8, 0x66FFFFFF);
            gem(g, cx, cy, i <= idx ? 4 : 3, i <= idx ? (0xFF000000 | c) : alpha(c, 0.30f));
        }
    }
}

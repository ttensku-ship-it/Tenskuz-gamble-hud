package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * Left of the menu: your skin as a real 3D character that dances, fights mobs with a netherite sword
 * (and Thor's hammer on the last one),
 * has your username on top and a rank badge underneath (hover it for the progress bar).
 */
public final class DancingSkin {
    private DancingSkin() {}

    private static final Identifier[] MOB_TEX = {
        Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png"),
        Identifier.withDefaultNamespace("textures/entity/zombie/husk.png"),
        Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png"),
        Identifier.withDefaultNamespace("textures/entity/zombie/drowned.png"),
    };
    private static final DanceScene SCENE = new DanceScene();

    // badge rectangle from the last frame (for the hover tooltip)
    private static boolean badgeShown;
    private static int badgeX, badgeY, badgeW, badgeH;

    /** Draws the model faces through the GUI's matrix stack (every face is a sheared, rotated texture quad). */
    static final class GuiCanvas implements Model3D.Canvas {
        private final GuiGraphicsExtractor g;
        /** Height of the texture being drawn (64, or 32 for the skeleton's legacy layout). */
        int texH = 64;
        GuiCanvas(GuiGraphicsExtractor g) { this.g = g; }

        @Override
        public void face(Object tex, float ox, float oy, float ux, float uy, float vx, float vy,
                         int u, int v, int w, int h, int shade, int tint) {
            g.pose().pushMatrix();
            g.pose().mul(new Matrix3x2f(ux, uy, vx, vy, ox, oy));
            g.blit(RenderPipelines.GUI_TEXTURED, (Identifier) tex, 0, 0, u, v, w, h, w, h, 64, texH);
            if (shade != 0) g.fill(0, 0, w, h, shade);
            if (tint != 0) g.fill(0, 0, w, h, tint);
            g.pose().popMatrix();
        }
    }

    public static void render(GuiGraphicsExtractor g, Font font, Minecraft mc, int panelX, int panelY, int panelH) {
        badgeShown = false;
        Identifier skin = skinTexture(mc);
        if (skin == null) return;

        // the panel has 12px of empty padding before the tabs start, so the show may reach 8px into it
        int areaL = 4, areaR = panelX + 8, avail = areaR - areaL;
        if (avail < 64) return; // no room left of the menu

        int S = avail >= 200 ? 3 : 2;
        float near = 17f, far = 29f;
        boolean withZombie = avail >= (far + 16f) * S + 8;

        float t = (System.currentTimeMillis() % 1_000_000L) / 1000f;
        SCENE.update(t, near, far, withZombie);

        float widthUnits = withZombie ? far + 16f : 16f;
        // sit toward the right side of the free space (60% of the slack goes on the left)
        float playerX = areaL + (avail - widthUnits * S) * 0.6f + 8f * S;
        float feetY = panelY + panelH / 2f + 40f;
        float hipY = feetY - Model3D.FEET_DROP * S;

        GuiCanvas canvas = new GuiCanvas(g);

        Theme th = Theme.current();
        int accent = th.accent() & 0xFFFFFF;

        // glowing stage under the player, then the soft shadows on top of it
        stage(g, playerX, feetY, (withZombie ? 13f : 11f) * S, accent, t);
        shadow(g, playerX + SCENE.player.x * S, feetY, 8f * S);
        if (withZombie && SCENE.zombieVisible) {
            shadow(g, playerX + SCENE.zombie.x * S, feetY, 8f * S * SCENE.zombie.scale);
        }

        // zombie first so your fists end up in front of it
        if (withZombie && SCENE.zombieVisible) {
            boolean skel = SCENE.mob == DanceScene.SKELETON;
            canvas.texH = skel ? 32 : 64;
            outline(canvas, MOB_TEX[SCENE.mob], skel, SCENE.zombie, playerX, hipY, S);
            Model3D.draw(canvas, MOB_TEX[SCENE.mob], false, skel, SCENE.zombie, playerX, hipY, S);
            canvas.texH = 64;
        }
        outline(canvas, skin, false, SCENE.player, playerX, hipY, S);
        Model3D.draw(canvas, skin, true, SCENE.player, playerX, hipY, S);

        if (withZombie && SCENE.weaponOut) {
            weapon(g, SCENE.player, SCENE.hammer, playerX, hipY, S, t, accent);
        }

        sparkles(g, playerX, hipY, S, accent, t);

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
                int rgb = DanceScene.POOF[SCENE.mob][i % 3];
                g.fill(px, py, px + size, py + size, (a << 24) | rgb);
            }
        }

        // Thor's lightning strike on the last mob
        if (withZombie && SCENE.bolt >= 0f) {
            float zx = playerX + SCENE.zombie.x * S;
            lightning(g, zx, panelY + 4, hipY + 10f * S, SCENE.bolt, areaL, areaR, panelY, panelH, t);
        }

        // username on top, on a little name tag
        String name = mc.player != null ? mc.player.getName().getString() : mc.getUser().getName();
        int nw = font.width(name) + 10;
        int nx = (int) (playerX - nw / 2f);
        nx = Math.max(areaL, Math.min(areaR - nw, nx));
        int ny = (int) (hipY - 21 * S - 18);
        g.fill(nx - 1, ny - 1, nx + nw + 1, ny + 13, alpha(accent, 0.55f));
        g.fill(nx, ny, nx + nw, ny + 12, 0xCC0B0C12);
        g.fill(nx, ny + 11, nx + nw, ny + 12, 0xFF000000 | accent);
        g.text(font, name, nx + 5, ny + 2, 0xFFFFFFFF, true);

        drawBadge(g, font, areaL, areaR, (int) playerX, (int) feetY + 12, t);
    }

    // ------------------------------------------------------------------ weapons

    /**
     * Holds the netherite sword or Thor's hammer in your right hand. The item is drawn in a small
     * "item space" (a = along the blade/handle, b = across), which is mapped onto the screen using
     * the real 3D direction of the forearm, so it swings with the arm.
     */
    private static void weapon(GuiGraphicsExtractor g, Model3D.Pose pose, boolean hammer,
                               float cx, float cy, int S, float t, int accent) {
        float[] hand = Model3D.armPoint(pose, true, 0f, 9f, 0f, true);
        float hx = cx + hand[0] * S, hy = cy + hand[1] * S;
        // blade points along the arm, tilted a little "up" relative to the fist
        float[] dir = Model3D.armPoint(pose, true, 0f, hammer ? 0.8f : 0.85f, hammer ? 0.6f : 0.5f, false);
        float dx = dir[0], dy = dir[1];
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.001f) { dx = 0.7f; dy = -0.7f; len = 1f; }
        dx /= len; dy /= len;
        float stretch = Math.max(len, 0.5f);
        float k = S * 0.5f * (hammer ? 1.25f : 1f);
        float ax = dx * k * stretch, ay = dy * k * stretch;      // one item unit along the weapon
        float bx = -dy * k, by = dx * k;                          // one item unit across it

        g.pose().pushMatrix();
        g.pose().translate(hx, hy);
        g.pose().mul(new Matrix3x2f(ax, ay, bx, by, 0f, 0f));
        if (hammer) drawHammer(g, t); else drawSword(g);
        g.pose().popMatrix();

        if (hammer) {
            // static crackling around the hammer head
            float hdx = hx + dx * 8f * k * stretch, hdy = hy + dy * 8f * k * stretch;
            for (int i = 0; i < 6; i++) {
                float ph = (t * 9f + i * 1.7f);
                float ang = ph * 1.3f + i * 1.05f;
                float r = (6f + 5f * (float) Math.abs(Math.sin(ph))) * (S / 2f);
                int x = (int) (hdx + Math.cos(ang) * r), y = (int) (hdy + Math.sin(ang) * r);
                int a = (int) (120 + 120 * Math.abs(Math.sin(ph * 2f)));
                g.fill(x, y, x + 2, y + 1, (a << 24) | 0xBFE6FF);
                g.fill(x, y, x + 1, y + 2, (a << 24) | 0xFFFFFF);
            }
        }
    }

    private static void rect(GuiGraphicsExtractor g, int a1, int b1, int a2, int b2, int argb) {
        g.fill(a1, b1, a2, b2, argb);
    }

    /** Netherite sword, 30 item units long (about 15 skin pixels). */
    private static void drawSword(GuiGraphicsExtractor g) {
        // handle + pommel
        rect(g, -7, -1, -1, 1, 0xFF4A2F1A);
        rect(g, -7, -1, -5, 1, 0xFF6B4527);
        rect(g, -9, -2, -7, 2, 0xFF2B2430);
        // crossguard
        rect(g, -1, -5, 2, 5, 0xFF2B2430);
        rect(g, -1, -5, 2, -4, 0xFF6A5A78);
        rect(g, -1, 4, 2, 5, 0xFF1A151E);
        // blade: dark netherite with a lighter edge and a glint
        rect(g, 2, -2, 24, 2, 0xFF3F3A44);
        rect(g, 2, -2, 24, -1, 0xFF6E6478);
        rect(g, 2, 1, 24, 2, 0xFF28242D);
        rect(g, 24, -1, 27, 1, 0xFF3F3A44);
        rect(g, 27, 0, 28, 1, 0xFF6E6478);
        rect(g, 6, -1, 22, 0, 0xFF574F5E);
        rect(g, 14, -1, 17, 0, 0xFFB7A8CC);   // glint
        // warm "ancient debris" trim at the base of the blade
        rect(g, 2, -2, 4, 2, 0xFF6B3F2E);
    }

    /** Mjolnir: stone-grey head with bands, leather-wrapped handle, blue charge glow. */
    private static void drawHammer(GuiGraphicsExtractor g, float t) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 10f);
        int ga = (int) (50 + 50 * pulse);
        // glow
        rect(g, 4, -9, 17, 9, (ga << 24) | 0x5CC8FF);
        // handle
        rect(g, -8, -1, 6, 1, 0xFF6A4A2A);
        rect(g, -8, -1, 6, 0, 0xFF8A6238);
        for (int a = -6; a < 5; a += 3) rect(g, a, -1, a + 1, 1, 0xFF3A2814);   // leather wrap
        rect(g, -10, -2, -8, 2, 0xFFB9BDC4);                                     // pommel
        rect(g, -12, -1, -10, 1, 0xFF8A8F98);                                    // wrist loop
        // head
        rect(g, 6, -7, 15, 7, 0xFF8A9099);
        rect(g, 6, -7, 15, -5, 0xFFC9CED6);                                      // top light edge
        rect(g, 6, 5, 15, 7, 0xFF5A6068);                                        // bottom shade
        rect(g, 6, -7, 8, 7, 0xFF6D737C);                                        // inner band
        rect(g, 13, -7, 15, 7, 0xFF6D737C);                                      // outer band
        rect(g, 9, -4, 12, 4, 0xFFA4AAB3);                                       // face plate
        rect(g, 10, -2, 11, 2, 0xFF5CC8FF);                                      // rune glint
        rect(g, 10, -1, 11, 1, 0xFFFFFFFF);
    }

    /** Jagged bolt from the top of the panel to the target, with a screen flash. */
    private static void lightning(GuiGraphicsExtractor g, float x, float topY, float botY, float k,
                                  int areaL, int areaR, int panelY, int panelH, float t) {
        float fade = 1f - k;
        // flash
        g.fill(areaL - 4, panelY, areaR + 8, panelY + panelH, ((int) (110 * fade * fade) << 24) | 0xCFE8FF);
        long seed = (long) Math.floor(t * 28f);
        java.util.Random r = new java.util.Random(seed * 7919L + 13);
        float cx = x + (r.nextFloat() - 0.5f) * 4f;
        float y = topY;
        int segs = 14;
        float step = (botY - topY) / segs;
        for (int i = 0; i < segs; i++) {
            float nx = (i == segs - 1) ? x : cx + (r.nextFloat() - 0.5f) * 16f;
            int y1 = (int) y, y2 = (int) (y + step) + 1;
            int x1 = (int) Math.min(cx, nx) - 1, x2 = (int) Math.max(cx, nx) + 2;
            g.fill(x1 - 3, y1, x2 + 3, y2, ((int) (70 * fade) << 24) | 0x5CC8FF);   // glow
            g.fill(x1, y1, x2, y2, ((int) (255 * Math.min(1f, fade * 1.6f)) << 24) | 0xFFFFFF);
            // little side branch
            if (i % 4 == 2) {
                int bx = (int) nx + (r.nextBoolean() ? 1 : -1) * (6 + r.nextInt(8));
                g.fill(Math.min((int) nx, bx), y2, Math.max((int) nx, bx) + 1, y2 + 2, ((int) (190 * fade) << 24) | 0xBFE6FF);
            }
            cx = nx;
            y += step;
        }
        // impact burst
        int rr = (int) (6 + 22 * k);
        for (int i = 0; i < 10; i++) {
            double ang = i * 0.628 + seed;
            int px = (int) (x + Math.cos(ang) * rr), py = (int) (botY + Math.sin(ang) * rr * 0.5);
            g.fill(px, py, px + 2, py + 2, ((int) (230 * fade) << 24) | 0xBFE6FF);
        }
    }

    /** Black 1px silhouette around a character so it pops off the panel (skin overlays are skipped). */
    static void outline(GuiCanvas canvas, Identifier tex, boolean skeleton, Model3D.Pose p, float cx, float cy, int S) {
        Model3D.Pose o = new Model3D.Pose();
        o.set(p);
        o.tint = 0xFF08090F;
        float d = 1f;
        Model3D.draw(canvas, tex, false, skeleton, o, cx - d, cy, S);
        Model3D.draw(canvas, tex, false, skeleton, o, cx + d, cy, S);
        Model3D.draw(canvas, tex, false, skeleton, o, cx, cy - d, S);
        Model3D.draw(canvas, tex, false, skeleton, o, cx, cy + d, S);
    }

    /** A pulsing glowing disc the characters stand on. */
    private static void stage(GuiGraphicsExtractor g, float cx, float y, float halfW, int rgb, float t) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.2f);
        for (int i = -5; i <= 5; i++) {
            float k = 1f - (i * i) / 30f;
            float w = halfW * (float) Math.sqrt(Math.max(0f, k));
            g.fill((int) (cx - w - 4), (int) y + i, (int) (cx + w + 4), (int) y + i + 1, alpha(rgb, 0.05f + 0.04f * pulse));
        }
        for (int i = -4; i <= 4; i++) {
            float k = 1f - (i * i) / 20f;
            float w = halfW * (float) Math.sqrt(Math.max(0f, k));
            int x1 = (int) (cx - w), x2 = (int) (cx + w);
            g.fill(x1, (int) y + i, x2, (int) y + i + 1, 0x77000000);
            // bright rim on the edge of the disc
            float rim = Math.abs(i) >= 3 ? 0.40f + 0.25f * pulse : 0.14f;
            g.fill(x1, (int) y + i, x1 + 2, (int) y + i + 1, alpha(rgb, rim));
            g.fill(x2 - 2, (int) y + i, x2, (int) y + i + 1, alpha(rgb, rim));
            if (Math.abs(i) == 4) g.fill(x1, (int) y + i, x2, (int) y + i + 1, alpha(rgb, 0.22f + 0.2f * pulse));
        }
    }

    /** A few little lights drifting up around the player. */
    private static void sparkles(GuiGraphicsExtractor g, float cx, float hipY, int S, int rgb, float t) {
        for (int i = 0; i < 9; i++) {
            float k = (t * (0.18f + (i % 4) * 0.05f) + i * 0.37f) % 1f;
            float side = ((i * 53) % 100) / 100f - 0.5f;
            float x = cx + side * 20f * S + (float) Math.sin(t * 1.6f + i) * 3f;
            float y = hipY + 8f * S - k * 34f * S;
            int a = (int) (Math.sin(k * Math.PI) * 200f);
            int sz = i % 3 == 0 ? 2 : 1;
            g.fill((int) x, (int) y, (int) x + sz, (int) y + sz, (a << 24) | ((i % 2 == 0) ? 0xFFFFFF : rgb));
        }
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

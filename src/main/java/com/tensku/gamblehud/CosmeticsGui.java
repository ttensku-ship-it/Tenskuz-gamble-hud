package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

/** Draws the cosmetics (capes, pets, costumes) through the GUI matrix stack. */
public final class CosmeticsGui {
    private CosmeticsGui() {}

    /** Every box face is a sheared, rotated unit square filled with one colour. */
    static final class SolidGui implements Model3D.SolidCanvas {
        private final GuiGraphicsExtractor g;
        SolidGui(GuiGraphicsExtractor g) { this.g = g; }

        @Override
        public void quad(float ox, float oy, float ux, float uy, float vx, float vy, int argb) {
            g.pose().pushMatrix();
            g.pose().mul(new Matrix3x2f(ux / 64f, uy / 64f, vx / 64f, vy / 64f, ox, oy));
            g.fill(-1, -1, 65, 65, argb); // 1/64 bigger on every side so neighbouring faces leave no seams
            g.pose().popMatrix();
        }
    }

    private static float energy(Model3D.Pose p) {
        float e = (Math.abs(p.armRPitch) + Math.abs(p.armLPitch) + Math.abs(p.lean) * 2f) * 0.5f;
        return Math.max(0f, Math.min(1f, e));
    }

    private static void outlineBoxes(SolidGui sg, List<Model3D.Box> boxes, Model3D.Pose pose, float cx, float cy, float S) {
        Model3D.Pose o = new Model3D.Pose();
        o.set(pose);
        o.tint = 0xFF08090F;
        float d = 1f;
        Model3D.drawBoxes(sg, boxes, o, cx - d, cy, S);
        Model3D.drawBoxes(sg, boxes, o, cx + d, cy, S);
        Model3D.drawBoxes(sg, boxes, o, cx, cy - d, S);
        Model3D.drawBoxes(sg, boxes, o, cx, cy + d, S);
    }

    /**
     * Draws you: your skin or the equipped costume, plus the cape.
     * @return true when a costume replaced the skin (the caller then hides the skin's weapon)
     */
    static boolean drawPlayer(GuiGraphicsExtractor g, DancingSkin.GuiCanvas tc, Identifier skin, Model3D.Pose pose,
                              float cx, float cy, int S, float t) {
        Config c = Config.I;
        SolidGui sg = new SolidGui(g);
        boolean cape = c.cape > 0;
        List<Model3D.Box> capeBoxes = null;
        if (cape) {
            pose.capePitch = Cosmetics.capeSwing(t, energy(pose));
            capeBoxes = Cosmetics.cape(c.cape, t, Theme.current().accent() & 0xFFFFFF);
        }
        if (c.costume > 0) {
            List<Model3D.Box> all = new ArrayList<>(Cosmetics.costume(c.costume, t));
            if (cape) all.addAll(capeBoxes);
            outlineBoxes(sg, all, pose, cx, cy, S);
            Model3D.drawBoxes(sg, all, pose, cx, cy, S);
            return true;
        }
        boolean front = Math.cos(pose.yaw) >= 0f;
        DancingSkin.outline(tc, skin, false, pose, cx, cy, S);
        if (cape && front) Model3D.drawBoxes(sg, capeBoxes, pose, cx, cy, S);
        Model3D.draw(tc, skin, true, pose, cx, cy, S);
        if (cape && !front) Model3D.drawBoxes(sg, capeBoxes, pose, cx, cy, S);
        return false;
    }

    /** The pet stands to the left of you (the mobs come from the right) and looks at you. */
    static void drawMenuPet(GuiGraphicsExtractor g, float playerX, float hipY, int S, float t, int areaLeft) {
        if (Config.I.pet <= 0) return;
        float xUnits = -22f;
        if (playerX + xUnits * S - 10f * S < areaLeft) return; // no room
        drawPet(g, playerX, hipY, S, t, xUnits, true);
    }

    static void drawPet(GuiGraphicsExtractor g, float cx, float hipY, float S, float t, float xUnits, boolean faceRight) {
        int id = Config.I.pet;
        if (id <= 0) return;
        Model3D.Pose p = new Model3D.Pose();
        Cosmetics.animatePet(id, t, p);
        p.x = xUnits;
        float turn = (float) Math.PI / 2f - 0.55f;
        p.yaw = faceRight ? turn : -turn;

        // little shadow on the ground
        float sx = cx + xUnits * S, sy = hipY + Model3D.FEET_DROP * S;
        int hw = (int) (6f * S * Math.max(0.5f, p.scale));
        for (int i = -2; i <= 2; i++) {
            float k = 1f - (i * i) / 6f;
            int w = (int) (hw * Math.sqrt(Math.max(0f, k)));
            g.fill((int) (sx - w), (int) sy + i, (int) (sx + w), (int) sy + i + 1, 0x55000000);
        }
        Model3D.drawBoxes(new SolidGui(g), Cosmetics.pet(id, t), p, cx, hipY, S);
    }

    /** The turntable in the Cosmetics tab. */
    static void drawPreview(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h, Font font) {
        Theme th = Theme.current();
        int accent = th.accent() & 0xFFFFFF;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.alpha(accent, 0.55f));
        g.fill(x, y, x + w, y + h, 0xCC0B0C12);
        g.text(font, "PREVIEW", x + 6, y + 5, th.accent(), true);

        float t = (System.currentTimeMillis() % 1_000_000L) / 1000f;
        Config c = Config.I;
        boolean pet = c.pet > 0;
        int S = pet ? 2 : 3;
        float cx = pet ? x + 32f : x + w / 2f;
        float feetY = y + h - 34f;
        float hipY = feetY - Model3D.FEET_DROP * S;

        // glowing floor under the character (and the pet)
        float floorHalf = (pet ? 36f : 15f) * (S / 2f);
        float floorX = pet ? cx + 9f * S : cx;
        for (int i = -4; i <= 4; i++) {
            float k = 1f - (i * i) / 20f;
            int ww = (int) (floorHalf * Math.sqrt(Math.max(0f, k)));
            g.fill((int) (floorX - ww), (int) feetY + i, (int) (floorX + ww), (int) feetY + i + 1,
                   Theme.alpha(accent, Math.abs(i) >= 3 ? 0.40f : 0.16f));
        }

        if (mc.player == null) return;
        Identifier skin = mc.player.getSkin().body().texturePath();

        Model3D.Pose pose = new Model3D.Pose();
        Cosmetics.animateCostume(c.costume, t, pose);
        pose.yaw = t * 0.9f;

        if (pet) drawPet(g, cx, hipY, S, t, 19f, false);
        drawPlayer(g, new DancingSkin.GuiCanvas(g), skin, pose, cx, hipY, S, t);

        // what is equipped
        int ty = y + h - 22;
        g.text(font, "Cape: " + Cosmetics.CAPES[Math.max(0, Math.min(Cosmetics.CAPES.length - 1, c.cape))], x + 6, ty, 0xFFB9BDCB, true);
        g.text(font, "Pet: " + Cosmetics.PETS[Math.max(0, Math.min(Cosmetics.PETS.length - 1, c.pet))], x + 6, ty + 10, 0xFFB9BDCB, true);
    }
}

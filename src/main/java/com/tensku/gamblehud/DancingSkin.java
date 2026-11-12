package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws the player's own skin as a goofy dancing paper-doll next to the menu,
 * with the username above it. Built from the skin texture (64x64 layout) so it
 * always matches whatever skin you are wearing.
 */
public final class DancingSkin {
    private DancingSkin() {}

    private static final int S = 3;      // gui pixels per skin pixel
    private static final int TEX = 64;

    public static void render(GuiGraphicsExtractor g, Font font, Minecraft mc, int panelX, int panelY, int panelH) {
        Identifier skin = skinTexture(mc);
        if (skin == null) return;

        String name = mc.player != null ? mc.player.getName().getString() : mc.getUser().getName();

        // centre of the free space to the left of the menu (kept on screen on narrow windows)
        int cx = Math.max(34, panelX / 2);
        int hipY = panelY + panelH / 2 + 34;

        // username on top, on the left
        g.text(font, name, cx - font.width(name) / 2, hipY - 12 * S - 8 * S - 22, 0xFFFFFFFF, true);

        float t = (System.currentTimeMillis() % 100000L) / 1000f;
        float beat = t * 7f;                                   // dance speed
        float bounce = -Math.abs((float) Math.sin(beat)) * 4f; // hop on the beat
        float sway = (float) Math.sin(beat * 0.5f) * 0.18f;    // whole body wiggle
        float headTilt = (float) Math.sin(beat * 1.0f + 1f) * 0.35f;
        float armR = -2.4f + (float) Math.sin(beat) * 0.9f;    // arms flung up and flapping
        float armL = 2.4f - (float) Math.sin(beat + 1.6f) * 0.9f;
        float legR = (float) Math.sin(beat) * 0.7f;            // goofy kicks
        float legL = -(float) Math.sin(beat) * 0.7f;
        float squash = 1f + (float) Math.sin(beat * 2f) * 0.05f;

        g.pose().pushMatrix();
        g.pose().translate((float) cx, hipY + bounce * S);
        g.pose().scale(S * (2f - squash), S * squash);
        g.pose().rotate(sway);

        // legs (pivot at the hip)
        part(g, skin, -2, 0, legR, -2, 0, 4, 12, 4, 20);
        part(g, skin, 2, 0, legL, -2, 0, 4, 12, 20, 52);
        // body
        part(g, skin, 0, 0, 0, -4, -12, 8, 12, 20, 20);
        // arms (pivot at the shoulders)
        part(g, skin, -6, -10, armR, -2, -2, 4, 12, 44, 20);
        part(g, skin, 6, -10, armL, -2, -2, 4, 12, 36, 52);
        // head + hat layer (pivot at the neck)
        part(g, skin, 0, -12, headTilt, -4, -8, 8, 8, 8, 8);
        part(g, skin, 0, -12, headTilt, -4, -8, 8, 8, 40, 8);

        g.pose().popMatrix();
    }

    /** One body part: rotate around (pivotX, pivotY), then draw the skin region at (dx, dy). */
    private static void part(GuiGraphicsExtractor g, Identifier skin, float pivotX, float pivotY, float angle,
                             int dx, int dy, int w, int h, int u, int v) {
        g.pose().pushMatrix();
        g.pose().translate(pivotX, pivotY);
        g.pose().rotate(angle);
        g.blit(RenderPipelines.GUI_TEXTURED, skin, dx, dy, u, v, w, h, w, h, TEX, TEX);
        g.pose().popMatrix();
    }

    /** The texture of the skin the local player is wearing. */
    private static Identifier skinTexture(Minecraft mc) {
        if (mc.player == null) return null;
        return mc.player.getSkin().body().texturePath();
    }
}

package com.tensku.gamblehud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * A flat item sprite shown as a thick, slowly spinning 3D slab with a glow and a floor shadow.
 * The turn is faked in 2D: the sprite is squashed sideways and stacked in several layers,
 * so the edge gets real thickness when it is side-on.
 */
public final class Item3D {
    private Item3D() {}

    private static final int LAYERS = 9;

    /** Draws the item centred on (cx, cy) inside a size x size area. */
    public static void draw(GuiGraphicsExtractor g, CustomImage.Tex t, float cx, float cy, int size, long now, int accentRgb) {
        float ang = (now % 4200L) / 4200f * (float) (Math.PI * 2.0);
        float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
        float bob = (float) Math.sin(now / 500.0) * 1.2f;

        int dw = size, dh = size;
        if (t.w() >= t.h()) dh = Math.max(1, size * t.h() / t.w());
        else dw = Math.max(1, size * t.w() / t.h());

        // soft glow behind the item
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 420.0);
        int gr = size / 2 + 1;
        for (int i = -gr; i <= gr; i++) {
            float k = 1f - (i * i) / (float) (gr * gr);
            int w = (int) (gr * Math.sqrt(Math.max(0f, k)));
            int a = (int) ((0.05f + 0.06f * pulse) * 255f);
            g.fill((int) cx - w, (int) (cy + i), (int) cx + w, (int) (cy + i) + 1, (a << 24) | accentRgb);
        }

        // floor shadow that shrinks as the item bobs up
        int sw = (int) (size * 0.38f * (1f - 0.1f * bob));
        int fy = (int) (cy + size / 2f + 1);
        g.fill((int) cx - sw, fy, (int) cx + sw, fy + 1, 0x55000000);
        g.fill((int) cx - sw + 2, fy + 1, (int) cx + sw - 2, fy + 2, 0x33000000);

        float depth = Math.max(3f, size * 0.20f);
        float squash = Math.max(0.10f, Math.abs(c));
        for (int n = 0; n < LAYERS; n++) {
            int i = c >= 0 ? n : LAYERS - 1 - n; // far layers first
            float z = (i / (float) (LAYERS - 1) - 0.5f) * depth;
            g.pose().pushMatrix();
            g.pose().translate(cx + z * s, cy + bob);
            g.pose().scale(squash, 1f);
            g.blit(RenderPipelines.GUI_TEXTURED, t.id(), -dw / 2, -dh / 2, 0, 0, dw, dh, t.w(), t.h(), t.w(), t.h());
            g.pose().popMatrix();
        }
    }
}

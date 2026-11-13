package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws the custom on-screen texts on the HUD. */
public final class TextOverlay {
    private TextOverlay() {}

    public static void render(GuiGraphicsExtractor g, Minecraft mc) {
        Config c = Config.I;
        if (!c.textEnabled || c.texts == null || c.texts.isEmpty()) return;
        if (mc.gui.screen() instanceof TextEditScreen) return; // the editor draws them itself
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();
        for (TextItem it : c.texts) {
            if (it == null || !it.visible || it.text == null || it.text.isEmpty()) continue;
            int[] r = TextFx.rect(mc.font, it, sw, sh);
            TextFx.draw(g, mc.font, it, r[0], r[1], now);
        }
    }
}

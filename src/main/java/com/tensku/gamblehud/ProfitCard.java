package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ProfitCard {
    private ProfitCard() {}

    public static void render(GuiGraphicsExtractor g, Minecraft mc) {
        if (!Config.I.profitCard) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int[] r = Hud.profitRect(sw, sh);
        draw(g, mc, r[0], r[1], Hud.clamp(Config.I.profitScale));
    }

    /** Draws the card with its top-left corner at (x, y) and the given size multiplier. */
    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s) {
        Theme th = Theme.current();
        double net = Config.I.net();
        int w = Hud.PROFIT_W, h = Hud.profitH();

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(s, s);

        g.fill(-1, -1, w + 1, h + 1, th.accent());
        Ui.backdrop(g, th, 0, 0, w, h);
        g.fill(0, 0, w, 13, 0x66000000);
        g.text(mc.font, "PROFIT", 5, 3, th.accent(), true);
        String today = "TODAY";
        g.text(mc.font, today, w - 5 - mc.font.width(today), 3, 0xFFB9BDCB, true);
        String v = (net >= 0 ? "+" : "") + Money.format(net);
        g.text(mc.font, v, 5, 17, net >= 0 ? 0xFF7CFF9B : 0xFFFF6B6B, true);

        if (Config.I.dailyGoal > 0) {
            float p = (float) Math.max(0, Math.min(1, net / Config.I.dailyGoal));
            g.fill(5, h - 8, w - 5, h - 4, 0x99000000);
            g.fill(5, h - 8, 5 + (int) ((w - 10) * p), h - 4, th.accent());
        }

        g.pose().popMatrix();
    }
}

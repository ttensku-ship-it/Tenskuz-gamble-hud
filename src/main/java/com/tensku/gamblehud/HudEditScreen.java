package com.tensku.gamblehud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Move and resize the profit card, auction timer and floating auction item. Click a card to pick it up, click again to drop it. */
public class HudEditScreen extends Screen {
    private int grabbed = -1; // -1 none, 0 profit, 1 auction, 2 floating item
    private int offX, offY;

    public HudEditScreen() {
        super(Component.literal("Edit HUD"));
    }

    private void btn(String label, int x, int y, int w, Runnable r) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, w, 20).build());
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y1 = height - 52, y2 = height - 28;
        int x0 = cx - 154;
        btn("Profit -", x0, y1, 74, () -> { Config.I.profitScale = Hud.clamp(Config.I.profitScale - 0.1f); Config.save(); });
        btn("Profit +", x0 + 78, y1, 74, () -> { Config.I.profitScale = Hud.clamp(Config.I.profitScale + 0.1f); Config.save(); });
        btn("Auction -", x0 + 156, y1, 74, () -> { Config.I.auctionScale = Hud.clamp(Config.I.auctionScale - 0.1f); Config.save(); });
        btn("Auction +", x0 + 234, y1, 74, () -> { Config.I.auctionScale = Hud.clamp(Config.I.auctionScale + 0.1f); Config.save(); });
        int y3 = height - 76;
        btn("Item -", x0, y3, 74, () -> { Config.I.floatScale = Hud.clamp(Config.I.floatScale - 0.1f); Config.save(); });
        btn("Item +", x0 + 78, y3, 74, () -> { Config.I.floatScale = Hud.clamp(Config.I.floatScale + 0.1f); Config.save(); });
        btn("Reset", cx - 104, y2, 100, () -> { grabbed = -1; Config.I.resetHud(); });
        btn("Done", cx + 4, y2, 100, this::onClose);
    }

    private static boolean inside(int[] r, int mx, int my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private void frame(GuiGraphicsExtractor g, int[] r, String name, boolean held) {
        int c = held ? Theme.current().accent() : 0xAAFFFFFF;
        g.outline(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, c);
        g.text(font, name, r[0], r[1] - 13, c, true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme th = Theme.current();
        long now = System.currentTimeMillis();
        g.fill(0, 0, width, height, 0x66000000);

        // a card that is picked up follows the mouse
        if (grabbed >= 0) {
            int[] r = grabbed == 0 ? Hud.profitRect(width, height) : grabbed == 1 ? Hud.auctionRect(width, height) : Hud.floatRect(width, height);
            int nx = Math.max(0, Math.min(width - r[2], mouseX - offX));
            int ny = Math.max(0, Math.min(height - r[3], mouseY - offY));
            if (grabbed == 0) {
                Config.I.profitX = Hud.frac(nx, r[2], width);
                Config.I.profitY = Hud.frac(ny, r[3], height);
            } else if (grabbed == 1) {
                Config.I.auctionX = Hud.frac(nx, r[2], width);
                Config.I.auctionY = Hud.frac(ny, r[3], height);
            } else {
                Config.I.floatX = Hud.frac(nx, r[2], width);
                Config.I.floatY = Hud.frac(ny, r[3], height);
            }
        }

        int[] pr = Hud.profitRect(width, height);
        ProfitCard.draw(g, minecraft, pr[0], pr[1], Hud.clamp(Config.I.profitScale));
        frame(g, pr, "Profit tracker", grabbed == 0);

        int[] ar = Hud.auctionRect(width, height);
        float s = Hud.clamp(Config.I.auctionScale);
        g.pose().pushMatrix();
        g.pose().translate((float) ar[0], (float) ar[1]);
        g.pose().scale(s, s);
        AuctionManager.drawCard(g, minecraft, th, "Diamond Sword", "", 83_000, 120_000,
                                "$5M - Tensku", true, "Host", null,
                                new String[][] {{"Tensku", "$5M"}, {"Steve", "$4.2M"}, {"Alex", "$3M"}}, 3, 0, now);
        g.pose().popMatrix();
        frame(g, ar, "Auction timer (sample)", grabbed == 1);

        int[] fr = Hud.floatRect(width, height);
        float fs = Hud.clamp(Config.I.floatScale);
        g.pose().pushMatrix();
        g.pose().translate((float) fr[0], (float) fr[1]);
        g.pose().scale(fs, fs);
        AuctionManager.drawFloating(g, minecraft, th, "Diamond Sword", "", 83_000, now);
        g.pose().popMatrix();
        frame(g, fr, "Auction item (sample)", grabbed == 2);

        String help = grabbed >= 0 ? "Click to drop it here" : "Click a card to pick it up, click again to drop it";
        g.text(font, help, (width - font.width(help)) / 2, 8, 0xFFFFFFFF, true);
        String sizes = "Profit size " + Math.round(Hud.clamp(Config.I.profitScale) * 100) + "%      Auction size "
            + Math.round(Hud.clamp(Config.I.auctionScale) * 100) + "%      Item size "
            + Math.round(Hud.clamp(Config.I.floatScale) * 100) + "%";
        g.text(font, sizes, (width - font.width(sizes)) / 2, height - 90, 0xFFB9BDCB, true);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true; // a button was clicked
        int mx = (int) event.x(), my = (int) event.y();
        if (grabbed >= 0) {
            Ui.click();
            grabbed = -1;
            Config.save();
            return true;
        }
        int[] pr = Hud.profitRect(width, height);
        int[] ar = Hud.auctionRect(width, height);
        int[] fr = Hud.floatRect(width, height);
        if (inside(fr, mx, my) && !inside(pr, mx, my) && !inside(ar, mx, my)) {
            Ui.click();
            grabbed = 2;
            offX = mx - fr[0];
            offY = my - fr[1];
            return true;
        }
        if (inside(pr, mx, my)) {
            Ui.click();
            grabbed = 0;
            offX = mx - pr[0];
            offY = my - pr[1];
            return true;
        }
        if (inside(ar, mx, my)) {
            Ui.click();
            grabbed = 1;
            offX = mx - ar[0];
            offY = my - ar[1];
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }
}

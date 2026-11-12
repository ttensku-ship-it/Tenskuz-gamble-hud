package com.tensku.gamblehud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AuctionScreen extends Screen {
    private static final int W = 320, H = 312;
    private int px, py;
    private EditBox itemBox, durBox, bidBox, raiseBox, hostBox, imgBox;
    private String status = "";

    // what you typed / picked, kept while the item picker is open
    static String draftItem, draftDur, draftBid, draftRaise, draftImg;

    public AuctionScreen() {
        super(Component.literal("Auction"));
    }

    private EditBox box(int x, int y, int w, String value, int max) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.literal(""));
        b.setMaxLength(max);
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    private void btn(String label, int x, int y, int w, Runnable r) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, w, 20).build());
    }

    private static String money(double v) {
        return v <= 0 ? "" : Money.format(v).replace("$", "");
    }

    private void saveDraft() {
        draftItem = itemBox.getValue();
        draftDur = durBox.getValue();
        draftBid = bidBox.getValue();
        draftRaise = raiseBox.getValue();
        draftImg = imgBox.getValue();
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        int x = px + 12;

        String item = draftItem != null ? draftItem : (AuctionManager.item.equals("Auction item") ? "" : AuctionManager.item);
        String dur = draftDur != null ? draftDur : String.valueOf(Config.I.lastAuctionSeconds);
        String bid = draftBid != null ? draftBid : money(Config.I.lastStartBid);
        String raise = draftRaise != null ? draftRaise : money(Config.I.lastMinRaise);
        String img = draftImg != null ? draftImg : AuctionManager.itemImage;
        draftItem = draftDur = draftBid = draftRaise = draftImg = null;

        itemBox = box(x, py + 44, 98, item, 40);
        durBox = box(x, py + 80, 150, dur, 5);
        bidBox = box(x, py + 116, 150, bid, 16);
        raiseBox = box(x, py + 152, 150, raise, 16);
        hostBox = box(x, py + 188, 150, Config.I.host, 16);
        imgBox = box(x, py + 224, 150, img, 60);

        btn("Pick", x + 102, py + 43, 48, () -> { saveDraft(); minecraft.gui.setScreen(new ItemPickerScreen()); });

        btn("Start", x, py + 250, 74, () -> {
            double minBid = bidBox.getValue().isBlank() ? 0 : Money.parse(bidBox.getValue());
            double raiseBy = raiseBox.getValue().isBlank() ? 0 : Money.parse(raiseBox.getValue());
            int secs;
            try { secs = Integer.parseInt(durBox.getValue().trim()); } catch (NumberFormatException e) { secs = -1; }
            if (minBid < 0 || raiseBy < 0 || secs < 5) { status = "Check seconds (5+) and amounts"; return; }
            Config.I.host = hostBox.getValue().trim();
            secs = Math.min(secs, 3600);
            Config.I.lastAuctionSeconds = secs;
            Config.I.lastStartBid = minBid;
            Config.I.lastMinRaise = raiseBy;
            Config.save();
            AuctionManager.start(itemBox.getValue(), secs, minBid, raiseBy, imgBox.getValue());
            status = "Auction started";
        });
        btn("End Now", x + 78, py + 250, 72, () -> { AuctionManager.endNow(); status = "Ended"; });
        btn("Cancel", x, py + 272, 74, () -> { AuctionManager.cancel(); status = "Cancelled"; });
        btn("Back", x + 78, py + 272, 72, () -> minecraft.gui.setScreen(new HubScreen()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme th = Theme.current();
        g.fill(0, 0, width, height, 0x99000000);
        Ui.panel(g, th, px, py, W, H);
        g.fill(px + 2, py + 2, px + W - 2, py + 26, 0xAA0E0F16);
        g.text(font, TenskuGambleHud.TITLE + " - AUCTION", px + 14, py + 10, th.accent(), true);

        int x = px + 12;
        int lc = Theme.alpha(th.accent() & 0xFFFFFF, 0.85f);
        g.text(font, "ITEM (type or press Pick)", x, py + 33, lc, true);
        g.text(font, "DURATION (SECONDS)", x, py + 69, lc, true);
        g.text(font, "MINIMUM / STARTING BID", x, py + 105, lc, true);
        g.text(font, "MIN RAISE (each bid must beat top by)", x, py + 141, lc, true);
        g.text(font, "HOST (ignored as bidder)", x, py + 177, lc, true);
        g.text(font, "ITEM PICTURE (optional PNG name)", x, py + 213, lc, true);

        int rx = px + 172, rw = 136;

        // live 3D preview of the chosen item
        g.fill(rx, py + 33, rx + rw, py + 97, 0xCC10121A);
        g.text(font, "PREVIEW", rx + 6, py + 38, th.accent(), true);
        CustomImage.Tex pt = CustomImage.auctionIcon(itemBox.getValue(), imgBox.getValue());
        if (pt != null) {
            Item3D.draw(g, pt, rx + rw / 2f, py + 68, 34, System.currentTimeMillis(), th.accent() & 0xFFFFFF);
        } else {
            String none = itemBox.getValue().isBlank() ? "No item picked" : "No icon for that name";
            g.text(font, none, rx + (rw - font.width(none)) / 2, py + 64, 0xFF9AA0B5, true);
        }

        g.fill(rx, py + 103, rx + rw, py + 170, 0xCC10121A);
        g.text(font, "STATUS", rx + 6, py + 108, th.accent(), true);
        long now = System.currentTimeMillis();
        if (AuctionManager.active) {
            g.text(font, "LIVE  " + Ui.time(Math.max(0, AuctionManager.endAt - now)), rx + 6, py + 120, 0xFF7CFF9B, true);
            String top = AuctionManager.topBidder.isEmpty() ? "No bids yet"
                : Money.format(AuctionManager.topBid) + " " + AuctionManager.topBidder;
            g.text(font, Ui.fit(font, top, rw - 12), rx + 6, py + 132, 0xFFFFFFFF, true);
            g.text(font, Ui.fit(font, AuctionManager.item, rw - 12), rx + 6, py + 144, 0xFFB9BDCB, true);
            if (AuctionManager.minRaise > 0) {
                g.text(font, "Raise +" + Money.format(AuctionManager.minRaise), rx + 6, py + 156, 0xFFFFD166, true);
            }
        } else {
            g.text(font, "IDLE", rx + 6, py + 120, 0xFFFF6B6B, true);
            g.text(font, Config.I.auctionEnabled ? "Auction HUD: ON" : "Auction HUD: OFF", rx + 6, py + 132, 0xFFB9BDCB, true);
            g.text(font, "Chat bid: <n> 5M", rx + 6, py + 146, 0xFF9AA0B5, true);
            g.text(font, "or pay you the bid", rx + 6, py + 157, 0xFF9AA0B5, true);
        }

        g.fill(rx, py + 176, rx + rw, py + 244, 0xCC10121A);
        g.text(font, "RECENT BIDS", rx + 6, py + 181, th.accent(), true);
        for (int i = 0; i < Math.min(5, AuctionManager.log.size()); i++) {
            g.text(font, Ui.fit(font, AuctionManager.log.get(i), rw - 12), rx + 6, py + 194 + i * 10, 0xFFE8E8E8, true);
        }

        if (!status.isEmpty()) g.text(font, status, rx + 6, py + 252, 0xFF9DFFB0, true);
        g.text(font, "Empty picture = item icon", rx + 6, py + 266, 0xFF9AA0B5, true);
        g.text(font, "ESC - close", px + 14, py + H - 12, 0xFF9AA0B5, true);
        String credit = TenskuGambleHud.CREDIT;
        g.text(font, credit, px + W - 14 - font.width(credit), py + H - 12, 0xFF9AA0B5, true);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }
}

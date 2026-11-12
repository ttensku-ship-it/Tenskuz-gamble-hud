package com.tensku.gamblehud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chat-driven auction: bids are chat lines that are just an amount, e.g. "<name> 5M" or "name: 5M". */
public final class AuctionManager {
    private AuctionManager() {}

    private static final Pattern BID = Pattern.compile(
        "(?i)^(?:\\[[^\\]]*\\]\\s*)*(?:<(\\w{3,16})>|(\\w{3,16})\\s*[»:>])\\s*(?:bid\\s+)?\\$?(\\d[\\d.,]*[kmbt]?)\\s*$");

    public record Result(String item, String winner, double amount) {}

    public static boolean active = false;
    public static String item = "";
    public static double startBid = 0;
    public static double topBid = 0;
    public static String topBidder = "";
    public static long endAt = 0;
    public static long totalMs = 1;
    public static final List<String> log = new ArrayList<>();

    public static String itemImage = "";
    public static int bids = 0;
    public static long lastBidAt = 0;

    public static Result result = null;
    public static long bannerUntil = 0;
    public static long resultUntil = 0;

    public static void start(String name, int seconds, double minBid) {
        start(name, seconds, minBid, "");
    }

    public static void start(String name, int seconds, double minBid, String image) {
        item = name.isBlank() ? "Auction item" : name.trim();
        itemImage = image == null ? "" : image.trim();
        bids = 0;
        lastBidAt = 0;
        startBid = Math.max(0, minBid);
        topBid = 0;
        topBidder = "";
        totalMs = Math.max(5, seconds) * 1000L;
        endAt = System.currentTimeMillis() + totalMs;
        log.clear();
        active = true;
        result = null;
        bannerUntil = 0;
        resultUntil = 0;
        Config.I.auctionEnabled = true;
        Config.save();
    }

    public static void cancel() {
        active = false;
        result = null;
        bannerUntil = 0;
        resultUntil = 0;
    }

    public static void endNow() {
        if (active) finish();
    }

    private static void finish() {
        active = false;
        String winner = topBidder.isEmpty() ? null : topBidder;
        result = new Result(item, winner, topBid);
        long now = System.currentTimeMillis();
        bannerUntil = winner != null ? now + Config.I.winnerBannerSeconds * 1000L : 0;
        resultUntil = Config.I.resultDisplaySeconds > 0 ? now + Config.I.resultDisplaySeconds * 1000L : 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String msg = winner == null
                ? "[Tensku] Auction ended: no bids for " + item
                : "[Tensku] Auction ended: " + winner + " won " + item + " for " + Money.format(topBid);
            mc.player.sendSystemMessage(Component.literal(msg));
        }
    }

    /** Called for every game chat line. */
    public static void onChat(String text) {
        if (!active || !Config.I.auctionEnabled) return;
        Matcher m = BID.matcher(text.trim());
        if (!m.matches()) return;
        String who = m.group(1) != null ? m.group(1) : m.group(2);
        if (who.equalsIgnoreCase(Config.I.host)) return; // host does not bid on own auction
        double amount = Money.parse(m.group(3));
        placeBid(who, amount);
    }

    /** Called when someone pays you ("X paid you $5M"). During an auction the payment counts as their bid. */
    public static void onPayment(String who, double amount) {
        if (!active || !Config.I.auctionEnabled) return;
        placeBid(who, amount);
    }

    private static void placeBid(String who, double amount) {
        if (amount <= 0 || amount < startBid || amount <= topBid) return;
        topBid = amount;
        topBidder = who;
        bids++;
        lastBidAt = System.currentTimeMillis();
        log.add(0, who + "  " + Money.format(amount));
        while (log.size() > 8) log.remove(log.size() - 1);
    }

    private static void icon(GuiGraphicsExtractor g, Theme th, Minecraft mc, String name, String img, int x, int y, int size) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, th.accent());
        g.fill(x, y, x + size, y + size, 0xFF0B0C12);
        CustomImage.Tex t = CustomImage.auctionIcon(name, img);
        if (t != null) {
            CustomImage.fit(g, t, x + 2, y + 2, size - 4);
        } else {
            String ch = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
            g.text(mc.font, ch, x + (size - mc.font.width(ch)) / 2, y + (size - 8) / 2, th.accent(), true);
        }
    }

    /**
     * The live auction card, drawn with its top-left corner at (0, 0) of the current pose.
     * Size is Hud.AUC_W x Hud.AUC_H. Also used by the HUD editor with sample values.
     */
    public static void drawCard(GuiGraphicsExtractor g, Minecraft mc, Theme th, String name, String img,
                                long left, long total, String topLine, boolean hasBid, String host,
                                int bidCount, long lastBid, long now) {
        final int w = Hud.AUC_W, h = Hud.AUC_H;
        boolean urgent = left <= 10_000;
        boolean warn = left <= 30_000;
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 130.0));
        int border = urgent ? Theme.alpha(0xFF5A5A, 0.55f + 0.45f * pulse) : th.accent();

        g.fill(-1, -1, w + 1, h + 1, border);
        Ui.backdrop(g, th, 0, 0, w, h);

        // header
        g.fill(0, 0, w, 14, 0xAA0B0C12);
        g.fill(0, 14, w, 15, Theme.alpha(border, 0.6f));
        g.fill(6, 5, 10, 9, Theme.alpha(0xFF4D4D, 0.45f + 0.55f * pulse));
        g.text(mc.font, "LIVE AUCTION", 14, 3, th.accent(), true);
        String right = bidCount + (bidCount == 1 ? " bid" : " bids");
        g.text(mc.font, right, w - 5 - mc.font.width(right), 3, 0xFFB9BDCB, true);

        // item picture
        icon(g, th, mc, name, img, 6, 21, 36);

        // item name + big timer
        g.text(mc.font, Ui.fit(mc.font, name, w - 53), 48, 20, 0xFFFFFFFF, true);
        int tc = urgent ? ((now / 400) % 2 == 0 ? 0xFFFF6B6B : 0xFFFFFFFF) : (warn ? 0xFFFFD166 : 0xFFFFFFFF);
        g.pose().pushMatrix();
        g.pose().translate(48f, 30f);
        g.pose().scale(2f, 2f);
        g.text(mc.font, Ui.time(left), 0, 0, tc, true);
        g.pose().popMatrix();

        // top bid row (flashes gold on a new bid)
        long since = now - lastBid;
        boolean fresh = hasBid && lastBid > 0 && since >= 0 && since < 900;
        if (fresh) {
            float a = 1f - since / 900f;
            g.fill(46, 47, w - 3, 58, Theme.alpha(0xF2C94C, a * 0.35f));
        }
        g.text(mc.font, Ui.fit(mc.font, topLine, w - 53), 48, 49, hasBid ? 0xFF7CFF9B : 0xFFB9BDCB, true);
        if (fresh) {
            g.text(mc.font, "NEW BID!", 48, 60, Theme.alpha(0xF2C94C, 0.6f + 0.4f * pulse), true);
        } else if (host != null && !host.isEmpty()) {
            g.text(mc.font, Ui.fit(mc.font, "Host: " + host, w - 53), 48, 60, 0xFFB9BDCB, true);
        }

        // progress bar with ticks and a moving shine
        int bx1 = 5, bx2 = w - 5, by1 = h - 8, by2 = h - 4;
        float frac = Math.max(0f, Math.min(1f, left / (float) Math.max(1L, total)));
        int fw = (int) ((bx2 - bx1) * frac);
        int bc = urgent ? 0xFFFF5A5A : (warn ? 0xFFFFD166 : th.accent());
        g.fill(bx1, by1, bx2, by2, 0xAA000000);
        g.fill(bx1, by1, bx1 + fw, by2, bc);
        g.fill(bx1, by1, bx1 + fw, by1 + 1, Theme.alpha(0xFFFFFF, 0.35f));
        if (fw > 8) {
            int sp = (int) ((now / 14) % (fw + 24)) - 8;
            int sx1 = bx1 + Math.max(0, sp), sx2 = bx1 + Math.min(fw, sp + 6);
            if (sx2 > sx1) g.fill(sx1, by1, sx2, by2, Theme.alpha(0xFFFFFF, 0.3f));
        }
        for (int i = 1; i < 10; i++) {
            int tx = bx1 + (bx2 - bx1) * i / 10;
            g.fill(tx, by1, tx + 1, by2, 0x55000000);
        }
    }

    /** Result card, drawn with its top-left corner at (0, 0) of the current pose. */
    private static void drawResult(GuiGraphicsExtractor g, Minecraft mc, Theme th) {
        final int w = Hud.AUC_W, h = 48;
        int border = result.winner() != null ? 0xFFF2C94C : th.accent();
        g.fill(-1, -1, w + 1, h + 1, border);
        Ui.backdrop(g, th, 0, 0, w, h);
        g.fill(0, 0, w, 14, 0xAA0B0C12);
        g.text(mc.font, "AUCTION RESULT", 6, 3, th.accent(), true);
        icon(g, th, mc, result.item(), itemImage, 6, 18, 26);
        g.text(mc.font, Ui.fit(mc.font, result.item(), w - 46), 38, 20, 0xFFFFFFFF, true);
        String line = result.winner() == null ? "No bids" : result.winner() + " - " + Money.format(result.amount());
        g.text(mc.font, Ui.fit(mc.font, line, w - 46), 38, 32, result.winner() == null ? 0xFFB9BDCB : 0xFF7CFF9B, true);
    }

    public static void render(GuiGraphicsExtractor g, Minecraft mc, int sw, int sh) {
        if (!Config.I.auctionEnabled) return;
        long now = System.currentTimeMillis();
        if (active && now >= endAt) finish();
        Theme th = Theme.current();

        boolean showResult = result != null && Config.I.resultDisplaySeconds > 0 && now < resultUntil;
        if (active || showResult) {
            int[] r = Hud.auctionRect(sw, sh);
            float s = Hud.clamp(Config.I.auctionScale);
            g.pose().pushMatrix();
            g.pose().translate((float) r[0], (float) r[1]);
            g.pose().scale(s, s);
            int yOff = 0;
            if (active) {
                long left = Math.max(0, endAt - now);
                String top = topBidder.isEmpty()
                    ? "No bids - from " + Money.format(startBid)
                    : Money.format(topBid) + " - " + topBidder;
                drawCard(g, mc, th, item, itemImage, left, totalMs, top, !topBidder.isEmpty(),
                         Config.I.host, bids, lastBidAt, now);
                yOff = Hud.AUC_H + 6;
            }
            if (showResult) {
                g.pose().pushMatrix();
                g.pose().translate(0f, (float) yOff);
                drawResult(g, mc, th);
                g.pose().popMatrix();
            }
            g.pose().popMatrix();
        }

        if (result != null && result.winner() != null && now < bannerUntil) {
            float leftS = (bannerUntil - now) / 1000f;
            float fade = Math.min(1f, leftS / 0.5f);
            float pop = Math.min(1f, (Config.I.winnerBannerSeconds - leftS) / 0.35f);
            String l1 = "AUCTION WON";
            String l2 = result.winner() + "  " + Money.format(result.amount());
            String l3 = Ui.fit(mc.font, result.item(), 220);
            int bw = Math.max(200, (int) (mc.font.width(l2) * 1.6f) + 30);
            int bh = 50;
            int bx = (sw - bw) / 2;
            int by = (int) (sh / 4f - 30 + 30 * pop);
            float pulse = (float) (0.5 + 0.5 * Math.sin(now / 120.0));
            g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, Theme.alpha(0xF2C94C, fade * (0.7f + 0.3f * pulse)));
            g.fillGradient(bx, by, bx + bw, by + bh, Theme.alpha(th.top() & 0xFFFFFF, fade * 0.95f),
                           Theme.alpha(th.bottom() & 0xFFFFFF, fade * 0.95f));
            g.text(mc.font, l1, (sw - mc.font.width(l1)) / 2, by + 5, Theme.alpha(th.accent() & 0xFFFFFF, fade), true);
            g.pose().pushMatrix();
            g.pose().translate(sw / 2f, by + 17f);
            g.pose().scale(1.6f, 1.6f);
            g.text(mc.font, l2, -mc.font.width(l2) / 2, 0, Theme.alpha(0x7CFF9B, fade), true);
            g.pose().popMatrix();
            g.text(mc.font, l3, (sw - mc.font.width(l3)) / 2, by + bh - 12, Theme.alpha(0xFFE9B0, fade), true);
        }
    }
}

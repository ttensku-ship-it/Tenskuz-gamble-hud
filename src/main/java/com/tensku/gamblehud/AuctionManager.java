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
    /** Minimum amount a new bid must beat the current top bid by. */
    public static double minRaise = 0;
    public static double topBid = 0;
    public static String topBidder = "";
    public static long endAt = 0;
    public static long totalMs = 1;
    public static final List<String> log = new ArrayList<>();
    /** Best bid of every bidder (for the top 3 board). */
    public static final java.util.Map<String, Double> best = new java.util.HashMap<>();

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
        start(name, seconds, minBid, 0, image);
    }

    public static void start(String name, int seconds, double minBid, double raise, String image) {
        minRaise = Math.max(0, raise);
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
        best.clear();
        active = true;
        result = null;
        bannerUntil = 0;
        resultUntil = 0;
        Config.I.auctionEnabled = true;
        Config.save();
    }

    /** Top 3 bidders as {name, amount}, highest first. */
    public static String[][] board() {
        List<java.util.Map.Entry<String, Double>> rows = new ArrayList<>(best.entrySet());
        rows.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int n = Math.min(3, rows.size());
        String[][] out = new String[n][];
        for (int i = 0; i < n; i++) out[i] = new String[] {rows.get(i).getKey(), Money.format(rows.get(i).getValue())};
        return out;
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
        Minecraft mc = Minecraft.getInstance();
        boolean cut = winner != null && Config.I.winnerCutscene && mc.player != null;
        long bannerMs = Config.I.winnerBannerSeconds * 1000L;
        if (cut && bannerMs > 0) bannerMs += Cutscene.DURATION_MS; // the banner waits until the cutscene is over
        bannerUntil = winner != null ? now + bannerMs : 0;
        resultUntil = Config.I.resultDisplaySeconds > 0 ? now + Config.I.resultDisplaySeconds * 1000L : 0;
        if (winner != null) {
            if (cut) Cutscene.start(item, itemImage, winner, topBid); // it launches the fireworks itself
            else Fireworks.launch();
        }
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
        double need = topBidder.isEmpty() ? startBid : topBid + minRaise;
        if (amount <= 0 || amount < need || amount <= topBid) return;
        topBid = amount;
        topBidder = who;
        bids++;
        lastBidAt = System.currentTimeMillis();
        best.merge(who, amount, Math::max);
        log.add(0, who + "  " + Money.format(amount));
        while (log.size() > 8) log.remove(log.size() - 1);
    }

    private static void icon(GuiGraphicsExtractor g, Theme th, Minecraft mc, String name, String img, int x, int y, int size) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, th.accent());
        g.fill(x, y, x + size, y + size, 0xFF0B0C12);
        CustomImage.Tex t = CustomImage.auctionIcon(name, img);
        if (t != null) {
            Item3D.draw(g, t, x + size / 2f, y + size / 2f, size - 8, System.currentTimeMillis(), th.accent() & 0xFFFFFF);
        } else {
            String ch = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
            HudFont.text(g, mc.font, ch, x + (size - HudFont.width(mc.font, ch)) / 2, y + (size - 8) / 2, th.accent(), true);
        }
    }

    /**
     * The live auction card, drawn with its top-left corner at (0, 0) of the current pose.
     * Size is Hud.AUC_W x Hud.AUC_H. Also used by the HUD editor with sample values.
     */
    public static void drawCard(GuiGraphicsExtractor g, Minecraft mc, Theme th, String name, String img,
                                long left, long total, String topLine, boolean hasBid, String host,
                                int bidCount, long lastBid, long now) {
        drawCard(g, mc, th, name, img, left, total, topLine, hasBid, host, null, bidCount, lastBid, now);
    }

    /** minLine (nullable) replaces the host line, e.g. "Next bid: $6M+". */
    public static void drawCard(GuiGraphicsExtractor g, Minecraft mc, Theme th, String name, String img,
                                long left, long total, String topLine, boolean hasBid, String host, String minLine,
                                int bidCount, long lastBid, long now) {
        drawCard(g, mc, th, name, img, left, total, topLine, hasBid, host, minLine, null, bidCount, lastBid, now);
    }

    /** board (nullable): up to 3 rows of {name, amount text}, shown as the 1-2-3 leaderboard. */
    public static void drawCard(GuiGraphicsExtractor g, Minecraft mc, Theme th, String name, String img,
                                long left, long total, String topLine, boolean hasBid, String host, String minLine,
                                String[][] board, int bidCount, long lastBid, long now) {
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
        HudFont.text(g, mc.font, "LIVE AUCTION", 14, 3, th.accent(), true);
        String right = bidCount + (bidCount == 1 ? " bid" : " bids");
        HudFont.text(g, mc.font, right, w - 5 - HudFont.width(mc.font, right), 3, 0xFFB9BDCB, true);

        // item picture
        icon(g, th, mc, name, img, 6, 21, 36);

        // item name + big timer
        HudFont.text(g, mc.font, HudFont.fit(mc.font, name, w - 53), 48, 20, 0xFFFFFFFF, true);
        int tc = urgent ? ((now / 400) % 2 == 0 ? 0xFFFF6B6B : 0xFFFFFFFF) : (warn ? 0xFFFFD166 : 0xFFFFFFFF);
        g.pose().pushMatrix();
        g.pose().translate(48f, 30f);
        g.pose().scale(2f, 2f);
        HudFont.text(g, mc.font, Ui.time(left), 0, 0, tc, true);
        g.pose().popMatrix();

        // circular countdown ring
        float rf = Math.max(0f, Math.min(1f, left / (float) Math.max(1L, total)));
        int rcol = urgent ? 0xFFFF5A5A : (warn ? 0xFFFFD166 : th.accent());
        for (int i = 0; i < 64; i++) {
            double a = i / 64.0 * Math.PI * 2.0;
            int ax = (int) Math.round(w - 21 + Math.sin(a) * 10.0);
            int ay = (int) Math.round(37 - Math.cos(a) * 10.0);
            g.fill(ax, ay, ax + 2, ay + 2, i / 64f < rf ? rcol : 0x44FFFFFF);
        }

        // top bid row (flashes gold on a new bid)
        long since = now - lastBid;
        boolean fresh = hasBid && lastBid > 0 && since >= 0 && since < 900;
        if (fresh) {
            float a = 1f - since / 900f;
            g.fill(46, 47, w - 3, 58, Theme.alpha(0xF2C94C, a * 0.35f));
        }
        HudFont.text(g, mc.font, HudFont.fit(mc.font, topLine, w - 53), 48, 49, hasBid ? 0xFF7CFF9B : 0xFFB9BDCB, true);
        if (fresh) {
            HudFont.text(g, mc.font, "NEW BID!", 48, 60, Theme.alpha(0xF2C94C, 0.6f + 0.4f * pulse), true);
        } else if (minLine != null && !minLine.isEmpty()) {
            HudFont.text(g, mc.font, HudFont.fit(mc.font, minLine, w - 53), 48, 60, 0xFFFFD166, true);
        } else if (host != null && !host.isEmpty()) {
            HudFont.text(g, mc.font, HudFont.fit(mc.font, "Host: " + host, w - 53), 48, 60, 0xFFB9BDCB, true);
        }

        // progress bar with ticks and a moving shine
        int bx1 = 5, bx2 = w - 5, by1 = 70, by2 = 74;
        float frac = rf;
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

        // top 3 leaderboard
        int[] medal = {0xFFFFD24D, 0xFFC8D0DC, 0xFFCD7F32};
        for (int i = 0; i < 3; i++) {
            int ry = 78 + i * 10;
            g.fill(3, ry, w - 3, ry + 9, (i % 2 == 0) ? 0x99000000 : 0x66000000);
            HudFont.text(g, mc.font, String.valueOf(i + 1), 6, ry + 1, medal[i], true);
            if (board != null && i < board.length) {
                String amt = board[i][1];
                HudFont.text(g, mc.font, HudFont.fit(mc.font, board[i][0], w - 26 - HudFont.width(mc.font, amt) - 6), 16, ry + 1, 0xFFFFFFFF, true);
                HudFont.text(g, mc.font, amt, w - 6 - HudFont.width(mc.font, amt), ry + 1, 0xFF7CFF9B, true);
            } else {
                HudFont.text(g, mc.font, "-", 16, ry + 1, 0xFF6A708A, true);
            }
        }
    }

    /** The item floating and spinning in the middle of the screen, with its name and the timer above it. */
    static void drawFloating(GuiGraphicsExtractor g, Minecraft mc, Theme th, String item, String itemImage, long left, long now) {
        // drawn with the top-left of its area (Hud.floatRect) at (0, 0)
        int cx = Hud.FLOAT_W / 2, cy = 52;
        CustomImage.Tex t = CustomImage.auctionIcon(item, itemImage);
        if (t != null) Item3D.draw(g, t, cx, cy, 44, now, th.accent() & 0xFFFFFF);

        String nm = HudFont.fit(mc.font, item, 160);
        String tm = Ui.time(left);
        int bw = Math.max(HudFont.width(mc.font, nm), HudFont.width(mc.font, tm)) + 14;
        int bx = cx - bw / 2, by = cy - 22 - 30;
        g.fill(bx, by, bx + bw, by + 25, 0xAA0B0C12);
        g.fill(bx, by + 24, bx + bw, by + 25, th.accent());
        HudFont.text(g, mc.font, nm, cx - HudFont.width(mc.font, nm) / 2, by + 3, 0xFFFFFFFF, true);
        int tc = left <= 10_000 ? ((now / 400) % 2 == 0 ? 0xFFFF6B6B : 0xFFFFFFFF) : 0xFFFFA84D;
        HudFont.text(g, mc.font, tm, cx - HudFont.width(mc.font, tm) / 2, by + 14, tc, true);
    }

    /** Result card, drawn with its top-left corner at (0, 0) of the current pose. */
    private static void drawResult(GuiGraphicsExtractor g, Minecraft mc, Theme th) {
        final int w = Hud.AUC_W, h = 48;
        int border = result.winner() != null ? 0xFFF2C94C : th.accent();
        g.fill(-1, -1, w + 1, h + 1, border);
        Ui.backdrop(g, th, 0, 0, w, h);
        g.fill(0, 0, w, 14, 0xAA0B0C12);
        HudFont.text(g, mc.font, "AUCTION RESULT", 6, 3, th.accent(), true);
        icon(g, th, mc, result.item(), itemImage, 6, 18, 26);
        HudFont.text(g, mc.font, HudFont.fit(mc.font, result.item(), w - 46), 38, 20, 0xFFFFFFFF, true);
        String line = result.winner() == null ? "No bids" : result.winner() + " - " + Money.format(result.amount());
        HudFont.text(g, mc.font, HudFont.fit(mc.font, line, w - 46), 38, 32, result.winner() == null ? 0xFFB9BDCB : 0xFF7CFF9B, true);
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
                String minLine = minRaise > 0
                    ? "Next bid: " + Money.format((topBidder.isEmpty() ? startBid : topBid + minRaise)) + "+"
                    : null;
                drawCard(g, mc, th, item, itemImage, left, totalMs, top, !topBidder.isEmpty(),
                         Config.I.host, minLine, board(), bids, lastBidAt, now);
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

        if (active && Config.I.floatingItem) {
            int[] fr = Hud.floatRect(sw, sh);
            float fs = Hud.clamp(Config.I.floatScale);
            g.pose().pushMatrix();
            g.pose().translate((float) fr[0], (float) fr[1]);
            g.pose().scale(fs, fs);
            drawFloating(g, mc, th, item, itemImage, Math.max(0, endAt - now), now);
            g.pose().popMatrix();
        }

        if (result != null && result.winner() != null && now < bannerUntil && !Cutscene.running()) {
            float leftS = (bannerUntil - now) / 1000f;
            float fade = Math.min(1f, leftS / 0.5f);
            float pop = Math.min(1f, (Config.I.winnerBannerSeconds - leftS) / 0.35f);
            String l1 = "AUCTION WON";
            String l2 = result.winner() + "  " + Money.format(result.amount());
            String l3 = HudFont.fit(mc.font, result.item(), 220);
            int bw = Math.max(200, (int) (HudFont.width(mc.font, l2) * 1.6f) + 30);
            int bh = 50;
            int bx = (sw - bw) / 2;
            int by = (int) (sh / 4f - 30 + 30 * pop);
            float pulse = (float) (0.5 + 0.5 * Math.sin(now / 120.0));
            g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, Theme.alpha(0xF2C94C, fade * (0.7f + 0.3f * pulse)));
            g.fillGradient(bx, by, bx + bw, by + bh, Theme.alpha(th.top() & 0xFFFFFF, fade * 0.95f),
                           Theme.alpha(th.bottom() & 0xFFFFFF, fade * 0.95f));
            HudFont.text(g, mc.font, l1, (sw - HudFont.width(mc.font, l1)) / 2, by + 5, Theme.alpha(th.accent() & 0xFFFFFF, fade), true);
            g.pose().pushMatrix();
            g.pose().translate(sw / 2f, by + 17f);
            g.pose().scale(1.6f, 1.6f);
            HudFont.text(g, mc.font, l2, -HudFont.width(mc.font, l2) / 2, 0, Theme.alpha(0x7CFF9B, fade), true);
            g.pose().popMatrix();
            HudFont.text(g, mc.font, l3, (sw - HudFont.width(mc.font, l3)) / 2, by + bh - 12, Theme.alpha(0xFFE9B0, fade), true);
        }
    }
}

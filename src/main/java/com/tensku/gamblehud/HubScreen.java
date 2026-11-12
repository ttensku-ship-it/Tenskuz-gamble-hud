package com.tensku.gamblehud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class HubScreen extends Screen {
    private static final int W = 400, H = 270;
    private static final String[] TABS = {"Overview", "Auction", "Profit", "Pay Confirm", "Alerts", "Themes", "Background", "HUD", "Leaderboard"};

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private int tab = 0;
    private int px, py;
    private EditBox payBox, alertBox, goalBox, winnerBox, resultBox, bgBox;
    private String status = "";
    private SoundInstance music;
    private boolean boardWinners = true;
    private int themePage = 0;

    // open / close animation
    private static final long OPEN_MS = 280, CLOSE_MS = 180;
    private final long openedAt = System.currentTimeMillis();
    private boolean closing = false;
    private long closeAt = 0;

    public HubScreen() {
        super(Component.literal(TenskuGambleHud.TITLE));
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        int cx = px + 124, y0 = py + 36;

        payBox = box(cx, y0 + 46, Money.format(Config.I.payThreshold).replace("$", ""));
        goalBox = box(cx, y0 + 46, Config.I.dailyGoal > 0 ? Money.format(Config.I.dailyGoal).replace("$", "") : "");
        alertBox = box(cx, y0 + 70, Money.format(Config.I.alertThreshold).replace("$", ""));
        winnerBox = box(cx, y0 + 78, String.valueOf(Config.I.winnerBannerSeconds));
        resultBox = box(cx, y0 + 116, String.valueOf(Config.I.resultDisplaySeconds));
        bgBox = box(cx, y0 + 42, Config.I.bgImage);
        bgBox.setMaxLength(60);
        updateBoxes();
        if (Config.I.menuMusic) startMusic();
    }

    private void startMusic() {
        if (music != null && minecraft.getSoundManager().isActive(music)) return;
        music = SimpleSoundInstance.forMusic(TenskuGambleHud.MENU_MUSIC);
        minecraft.getSoundManager().play(music);
    }

    private void stopMusic() {
        if (music != null) minecraft.getSoundManager().stop(music);
        music = null;
    }

    private boolean animating() {
        if (!Config.I.menuAnim) return false;
        return closing || System.currentTimeMillis() - openedAt < OPEN_MS;
    }

    /** 0 = hidden, 1 = fully open (clamped, used for fading). */
    private float fade() {
        if (!Config.I.menuAnim) return 1f;
        long now = System.currentTimeMillis();
        if (closing) {
            float t = Math.min(1f, (now - closeAt) / (float) CLOSE_MS);
            return 1f - t * t;
        }
        float t = Math.min(1f, (now - openedAt) / (float) OPEN_MS);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** Panel scale: pops in with a little overshoot, shrinks away on close. */
    private float popScale() {
        if (!Config.I.menuAnim) return 1f;
        long now = System.currentTimeMillis();
        float v;
        if (closing) {
            v = fade();
        } else {
            float t = Math.min(1f, (now - openedAt) / (float) OPEN_MS);
            float c1 = 1.70158f, c3 = c1 + 1f, p = t - 1f;
            v = 1f + c3 * p * p * p + c1 * p * p; // ease out back
        }
        return 0.78f + 0.22f * v;
    }

    @Override
    public void onClose() {
        if (Config.I.menuAnim) {
            if (!closing) { closing = true; closeAt = System.currentTimeMillis(); }
            return; // the real close happens in tick() once the animation ends
        }
        super.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (closing && System.currentTimeMillis() - closeAt >= CLOSE_MS) {
            super.onClose();
            return;
        }
        // loop the song while the menu is open
        if (Config.I.menuMusic && music != null && !minecraft.getSoundManager().isActive(music)) {
            music = SimpleSoundInstance.forMusic(TenskuGambleHud.MENU_MUSIC);
            minecraft.getSoundManager().play(music);
        }
    }

    @Override
    public void removed() {
        stopMusic();
        super.removed();
    }

    private EditBox box(int x, int y, String value) {
        EditBox b = new EditBox(font, x, y, 110, 18, Component.literal("amount"));
        b.setMaxLength(16);
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    private void updateBoxes() {
        payBox.setVisible(tab == 3);
        goalBox.setVisible(tab == 2);
        alertBox.setVisible(tab == 4);
        winnerBox.setVisible(tab == 1);
        resultBox.setVisible(tab == 1);
        bgBox.setVisible(tab == 6);
    }

    // ---------- drawing helpers ----------

    private void toggle(GuiGraphicsExtractor g, Theme th, String label, boolean on, int y, Runnable r) {
        int x = px + 124, w = 168, h = 20;
        g.fill(x, y, x + w, y + h, 0xCC151824);
        g.text(font, label, x + 6, y + 6, 0xFFE8E8E8, true);
        int sx = x + w - 34, sy = y + 5;
        g.fill(sx, sy, sx + 28, sy + 10, on ? th.accent() : 0xFF555A68);
        int kx = on ? sx + 18 : sx;
        g.fill(kx, sy, kx + 10, sy + 10, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, h, r));
    }

    private void button(GuiGraphicsExtractor g, Theme th, String label, int x, int y, int w, int h, Runnable r) {
        g.fill(x, y, x + w, y + h, 0xCC151824);
        g.fill(x, y, x + w, y + 1, th.accent());
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, 0xFFE8E8E8, true);
        hits.add(new Hit(x, y, w, h, r));
    }

    private static int parseSeconds(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return (v >= 0 && v <= 60) ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void label(GuiGraphicsExtractor g, Theme th, String s, int y) {
        g.text(font, s, px + 124, y, Theme.alpha(th.accent() & 0xFFFFFF, 0.8f), true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        Theme th = Theme.current();
        updateBoxes();

        float fade = fade();
        g.fill(0, 0, width, height, ((int) (0x99 * fade) << 24));

        float cxm = px + W / 2f, cym = py + H / 2f, sc = popScale();
        g.pose().pushMatrix();
        g.pose().translate(cxm, cym + (1f - fade) * 14f);
        g.pose().scale(sc, sc);
        g.pose().translate(-cxm, -cym);

        Ui.panel(g, th, px, py, W, H);
        g.fill(px + 2, py + 2, px + W - 2, py + 28, 0xAA0E0F16);

        g.text(font, TenskuGambleHud.TITLE, px + 14, py + 10, th.accent(), true);
        g.text(font, "ESC - close", px + 14, py + H - 14, 0xFF9AA0B5, true);
        String credit = TenskuGambleHud.CREDIT;
        g.text(font, credit, px + W - 14 - font.width(credit), py + H - 14, 0xFF9AA0B5, true);

        // dancing skin on the left of the menu
        DancingSkin.render(g, font, minecraft, px, py, H);

        // tabs
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            int ty = py + 36 + i * 24;
            boolean sel = i == tab;
            g.fill(px + 12, ty, px + 112, ty + 22, sel ? th.accent() : 0xCC151824);
            g.text(font, TABS[i], px + 20, ty + 7, sel ? 0xFF10121A : 0xFFE8E8E8, !sel);
            hits.add(new Hit(px + 12, ty, 100, 22, () -> { tab = idx; updateBoxes(); status = ""; }));
        }

        int y0 = py + 36;
        Config c = Config.I;
        switch (tab) {
            case 0 -> {
                label(g, th, "OVERVIEW", y0 - 12);
                toggle(g, th, "Auction", c.auctionEnabled, y0, () -> { c.auctionEnabled = !c.auctionEnabled; Config.save(); });
                toggle(g, th, "Payment Alerts", c.paymentAlerts, y0 + 22, () -> { c.paymentAlerts = !c.paymentAlerts; Config.save(); });
                toggle(g, th, "Profit Tracker", c.profitCard, y0 + 44, () -> { c.profitCard = !c.profitCard; Config.save(); });
                toggle(g, th, "Pay Confirm", c.payConfirm, y0 + 66, () -> { c.payConfirm = !c.payConfirm; Config.save(); });
                toggle(g, th, "Menu Music", c.menuMusic, y0 + 88, () -> { c.menuMusic = !c.menuMusic; Config.save(); if (c.menuMusic) startMusic(); else stopMusic(); });
                toggle(g, th, "Click Sounds", c.clickSounds, y0 + 110, () -> { c.clickSounds = !c.clickSounds; Config.save(); });
                toggle(g, th, "Menu Animation", c.menuAnim, y0 + 132, () -> { c.menuAnim = !c.menuAnim; Config.save(); });
                button(g, th, "Open Auction", px + 124, y0 + 158, 82, 20, () -> minecraft.gui.setScreen(new AuctionScreen()));
                button(g, th, "Test Animation", px + 210, y0 + 158, 82, 20, () -> PaymentAnimation.trigger(5_000_000, "Tensku"));
                button(g, th, "Edit HUD (move / resize)", px + 124, y0 + 182, 168, 20, () -> minecraft.gui.setScreen(new HudEditScreen()));
            }
            case 1 -> {
                label(g, th, "AUCTION", y0 - 12);
                toggle(g, th, "Auction (bids + HUD)", c.auctionEnabled, y0, () -> { c.auctionEnabled = !c.auctionEnabled; Config.save(); });
                button(g, th, "Open Auction", px + 124, y0 + 24, 82, 20, () -> minecraft.gui.setScreen(new AuctionScreen()));
                button(g, th, "Favourites", px + 210, y0 + 24, 82, 20, () -> minecraft.gui.setScreen(new FavouritesScreen()));
                g.text(font, "HOST: " + (c.host.isEmpty() ? "-" : c.host), px + 124, y0 + 50, 0xFFE8E8E8, true);
                label(g, th, "WINNER BANNER TIME (0-60s)", y0 + 66);
                button(g, th, "Set", px + 124 + 116, y0 + 78, 52, 18, () -> {
                    int v = parseSeconds(winnerBox.getValue());
                    if (v >= 0) { c.winnerBannerSeconds = v; Config.save(); status = "Banner time saved"; } else status = "Use 0-60";
                });
                label(g, th, "RESULT DISPLAY (0-60s; 0=off)", y0 + 104);
                button(g, th, "Set", px + 124 + 116, y0 + 116, 52, 18, () -> {
                    int v = parseSeconds(resultBox.getValue());
                    if (v >= 0) { c.resultDisplaySeconds = v; Config.save(); status = "Result time saved"; } else status = "Use 0-60";
                });
            }
            case 2 -> {
                label(g, th, "PROFIT", y0 - 12);
                toggle(g, th, "Profit Tracker + Card", c.profitCard, y0, () -> { c.profitCard = !c.profitCard; Config.save(); });
                label(g, th, "DAILY GOAL (bar on the card)", y0 + 34);
                button(g, th, "Set", px + 124 + 116, y0 + 46, 52, 18, () -> {
                    double v = goalBox.getValue().isBlank() ? 0 : Money.parse(goalBox.getValue());
                    if (v >= 0) { c.dailyGoal = v; Config.save(); status = "Goal saved"; } else status = "Invalid amount";
                });
                button(g, th, "Reset Today's Profit", px + 124, y0 + 76, 168, 20, () -> { c.received = 0; c.sent = 0; Config.save(); status = "Reset"; });
            }
            case 3 -> {
                label(g, th, "PAY CONFIRM", y0 - 12);
                toggle(g, th, "Pay Confirm", c.payConfirm, y0, () -> { c.payConfirm = !c.payConfirm; Config.save(); });
                label(g, th, "ASK BEFORE /pay AT OR ABOVE", y0 + 34);
                button(g, th, "Set", px + 124 + 116, y0 + 46, 52, 18, () -> {
                    double v = Money.parse(payBox.getValue());
                    if (v >= 0) { c.payThreshold = v; Config.save(); status = "Saved"; } else status = "Invalid amount";
                });
                g.text(font, "A big /pay opens a Confirm / Cancel", px + 124, y0 + 76, 0xFFB9BDCB, true);
                g.text(font, "screen before anything is sent.", px + 124, y0 + 88, 0xFFB9BDCB, true);
            }
            case 4 -> {
                label(g, th, "ALERTS", y0 - 12);
                toggle(g, th, "Payment Alerts", c.paymentAlerts, y0, () -> { c.paymentAlerts = !c.paymentAlerts; Config.save(); });
                toggle(g, th, "Alert Sound", c.alertSound, y0 + 26, () -> { c.alertSound = !c.alertSound; Config.save(); });
                label(g, th, "BIG PAYMENT AT OR ABOVE", y0 + 58);
                button(g, th, "Set", px + 124 + 116, y0 + 70, 52, 18, () -> {
                    double v = Money.parse(alertBox.getValue());
                    if (v >= 0) { c.alertThreshold = v; Config.save(); status = "Saved"; } else status = "Invalid amount";
                });
                button(g, th, "Style: " + PaymentAnimation.STYLE_NAMES[Math.max(0, Math.min(4, c.animStyle))], px + 124, y0 + 96, 168, 20,
                    () -> { c.animStyle = (c.animStyle + 1) % 5; Config.save(); });
                label(g, th, "PREVIEW (closes this menu)", y0 + 122);
                button(g, th, "Big $5M", px + 124, y0 + 134, 82, 18, () -> { minecraft.gui.setScreen(null); PaymentAnimation.preview(5_000_000, 1); });
                button(g, th, "Huge $150M", px + 210, y0 + 134, 82, 18, () -> { minecraft.gui.setScreen(null); PaymentAnimation.preview(150_000_000, 2); });
                button(g, th, "Jackpot $1.5B", px + 124, y0 + 154, 82, 18, () -> { minecraft.gui.setScreen(null); PaymentAnimation.preview(1_500_000_000, 3); });
                button(g, th, "Legend $20B", px + 210, y0 + 154, 82, 18, () -> { minecraft.gui.setScreen(null); PaymentAnimation.preview(20_000_000_000.0, 4); });
            }
            case 5 -> {
                label(g, th, "GUI THEMES", y0 - 12);
                // two columns, several pages
                final int perPage = 18;
                int pages = (Theme.ALL.length + perPage - 1) / perPage;
                themePage = Math.max(0, Math.min(pages - 1, themePage));
                int start = themePage * perPage;
                for (int n = 0; n < perPage && start + n < Theme.ALL.length; n++) {
                    final int idx = start + n;
                    Theme t = Theme.ALL[idx];
                    int col = n / 9, row = n % 9;
                    int rx = px + 124 + col * 86, ry = y0 + row * 18;
                    boolean sel = c.theme == idx;
                    g.fill(rx, ry, rx + 82, ry + 16, sel ? t.accent() : 0xCC151824);
                    g.fillGradient(rx + 3, ry + 3, rx + 19, ry + 13, t.top(), t.bottom());
                    g.text(font, Ui.fit(font, t.name(), 56), rx + 24, ry + 4, sel ? 0xFF10121A : 0xFFE8E8E8, !sel);
                    hits.add(new Hit(rx, ry, 82, 16, () -> { c.theme = idx; Config.save(); status = "Selected " + t.name() + "."; }));
                }
                final int pageCount = pages;
                button(g, th, "<", px + 124, y0 + 168, 24, 16, () -> themePage = (themePage + pageCount - 1) % pageCount);
                button(g, th, ">", px + 268, y0 + 168, 24, 16, () -> themePage = (themePage + 1) % pageCount);
                String pg = "Page " + (themePage + 1) + "/" + pages;
                g.text(font, pg, px + 208 - font.width(pg) / 2, y0 + 172, 0xFFE8E8E8, true);
            }
            case 6 -> {
                label(g, th, "BACKGROUND", y0 - 12);
                toggle(g, th, "Custom Background", c.bgEnabled, y0, () -> { c.bgEnabled = !c.bgEnabled; Config.save(); });
                label(g, th, "PNG FILE NAME", y0 + 30);
                button(g, th, "Load", px + 124 + 116, y0 + 42, 52, 18, () -> {
                    String name = bgBox.getValue().trim();
                    if (name.isEmpty()) { status = "Type a PNG file name first"; return; }
                    CustomImage.reload(name);
                    c.bgImage = name;
                    c.bgEnabled = true;
                    Config.save();
                    status = CustomImage.get(name) != null ? "Loaded " + name : "Failed: " + CustomImage.lastError;
                });
                g.text(font, "Darken: " + c.bgDim + "%", px + 124 + 30, y0 + 72, 0xFFE8E8E8, true);
                button(g, th, "-", px + 124, y0 + 66, 24, 20, () -> { c.bgDim = Math.max(0, c.bgDim - 5); Config.save(); });
                button(g, th, "+", px + 124 + 144, y0 + 66, 24, 20, () -> { c.bgDim = Math.min(90, c.bgDim + 5); Config.save(); });
                button(g, th, "Clear image", px + 124, y0 + 92, 168, 20, () -> {
                    c.bgImage = ""; c.bgEnabled = false; bgBox.setValue(""); Config.save(); status = "Background cleared";
                });
                g.text(font, "Put PNG files in:", px + 124, y0 + 120, 0xFFB9BDCB, true);
                g.text(font, ".minecraft/config/tenskugamblehud/", px + 124, y0 + 132, 0xFFB9BDCB, true);
                g.text(font, "Used on cards and menus.", px + 124, y0 + 144, 0xFFB9BDCB, true);
            }
            case 7 -> {
                label(g, th, "HUD", y0 - 12);
                toggle(g, th, "Profit Tracker", c.profitCard, y0, () -> { c.profitCard = !c.profitCard; Config.save(); });
                toggle(g, th, "Auction", c.auctionEnabled, y0 + 22, () -> { c.auctionEnabled = !c.auctionEnabled; Config.save(); });
                button(g, th, "Edit HUD (move / resize)", px + 124, y0 + 52, 168, 20, () -> minecraft.gui.setScreen(new HudEditScreen()));
                button(g, th, "Reset positions + size", px + 124, y0 + 76, 168, 20, () -> { c.resetHud(); status = "HUD reset"; });
                g.text(font, "In the editor click a card to pick", px + 124, y0 + 106, 0xFFB9BDCB, true);
                g.text(font, "it up, click again to drop it.", px + 124, y0 + 118, 0xFFB9BDCB, true);
            }
            case 8 -> {
                label(g, th, "LEADERBOARD", y0 - 12);
                button(g, th, "Top Winners", px + 124, y0, 82, 18, () -> boardWinners = true);
                button(g, th, "Top Losers", px + 210, y0, 82, 18, () -> boardWinners = false);
                g.fill(boardWinners ? px + 124 : px + 210, y0 + 16, (boardWinners ? px + 124 : px + 210) + 82, y0 + 18, 0xFFFFFFFF);
                var rows = c.board(boardWinners);
                if (rows.isEmpty()) {
                    g.text(font, "No data yet.", px + 124, y0 + 34, 0xFFB9BDCB, true);
                    g.text(font, "Counts /pay money from now on:", px + 124, y0 + 50, 0xFFB9BDCB, true);
                    g.text(font, boardWinners ? "who took the most from you." : "who lost the most to you.", px + 124, y0 + 62, 0xFFB9BDCB, true);
                } else {
                    // podium: 2nd | 1st | 3rd
                    int[] order = {1, 0, 2};
                    int[] heights = {34, 44, 26};
                    int[] medal = {0xFFC0C8D4, 0xFFFFD24D, 0xFFCD7F32};
                    int base = y0 + 84;
                    for (int k = 0; k < 3; k++) {
                        int rank = order[k];
                        int bx = px + 124 + k * 57, bw = 54, top = base - heights[k];
                        if (rank < rows.size()) {
                            var r = rows.get(rank);
                            String nm = Ui.fit(font, r.getKey(), bw);
                            String am = Ui.fit(font, Money.format(r.getValue()), bw);
                            g.text(font, nm, bx + (bw - font.width(nm)) / 2, top - 20, 0xFFFFFFFF, true);
                            g.text(font, am, bx + (bw - font.width(am)) / 2, top - 10, boardWinners ? 0xFF7CFF9B : 0xFFFF6B6B, true);
                        }
                        g.fill(bx, top, bx + bw, base, rank < rows.size() ? medal[rank] : 0x66151824);
                        String num = String.valueOf(rank + 1);
                        g.text(font, num, bx + (bw - font.width(num)) / 2, top + 6, 0xFF10121A, false);
                    }
                    // ranks 4-10 as a chart
                    for (int i = 3; i < 10; i++) {
                        int ry = y0 + 90 + (i - 3) * 12;
                        g.fill(px + 124, ry, px + 292, ry + 11, (i % 2 == 0) ? 0xAA151824 : 0x88101420);
                        if (i < rows.size()) {
                            var r = rows.get(i);
                            g.text(font, (i + 1) + ".", px + 128, ry + 2, th.accent(), true);
                            g.text(font, Ui.fit(font, r.getKey(), 90), px + 144, ry + 2, 0xFFE8E8E8, true);
                            String am = Money.format(r.getValue());
                            g.text(font, am, px + 288 - font.width(am), ry + 2, boardWinners ? 0xFF7CFF9B : 0xFFFF6B6B, true);
                        } else {
                            g.text(font, (i + 1) + ".", px + 128, ry + 2, 0xFF6A708A, true);
                        }
                    }
                }
                button(g, th, "Reset Leaderboard", px + 124, y0 + 178, 168, 16, () -> { c.ledger.clear(); Config.save(); status = "Leaderboard reset"; });
            }
            default -> { }
        }

        if (!status.isEmpty()) g.text(font, status, px + 124, py + H - 28, 0xFF9DFFB0, true);

        // right side stat card
        int sx = px + 300, sw = 88;
        g.fill(sx, py + 36, sx + sw, py + 122, 0xCC10121A);
        g.text(font, "BALANCE", sx + 6, py + 42, 0xFFFFFFFF, true);
        String bal = c.balance >= 0 ? Money.format(c.balance) : "type /bal";
        g.text(font, bal, sx + 6, py + 53, c.balance >= 0 ? 0xFFFFFFFF : 0xFF9AA0B5, true);
        g.text(font, "TODAY", sx + 6, py + 68, 0xFFFFFFFF, true);
        double net = c.net();
        g.text(font, (net >= 0 ? "+" : "") + Money.format(net), sx + 6, py + 79, net >= 0 ? 0xFF7CFF9B : 0xFFFF6B6B, true);
        g.text(font, "IN  " + Money.format(c.received), sx + 6, py + 93, 0xFFB9BDCB, true);
        g.text(font, "OUT " + Money.format(c.sent), sx + 6, py + 105, 0xFFB9BDCB, true);

        g.fill(sx, py + 128, sx + sw, py + 196, 0xCC10121A);
        g.text(font, "STATUS", sx + 6, py + 133, th.accent(), true);
        statusLine(g, "Auction", c.auctionEnabled, sx + 6, py + 146);
        statusLine(g, "Alerts", c.paymentAlerts, sx + 6, py + 157);
        statusLine(g, "Profit", c.profitCard, sx + 6, py + 168);
        statusLine(g, "Pay guard", c.payConfirm, sx + 6, py + 179);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // fade the whole panel in / out
        if (fade < 1f) g.fill(px, py, px + W, py + H, ((int) (255 * (1f - fade)) << 24));
        DancingSkin.renderTooltip(g, font, mouseX, mouseY, width);
        g.pose().popMatrix();
    }

    private void statusLine(GuiGraphicsExtractor g, String name, boolean on, int x, int y) {
        g.text(font, name, x, y, 0xFFB9BDCB, true);
        String s = on ? "ON" : "OFF";
        g.text(font, s, x + 76 - font.width(s), y, on ? 0xFF7CFF9B : 0xFFFF6B6B, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (animating()) return true;
        double mx = event.x(), my = event.y();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                Ui.click();
                h.action().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}

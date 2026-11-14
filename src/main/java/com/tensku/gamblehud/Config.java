package com.tensku.gamblehud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public class Config {
    public static Config I = new Config();

    public boolean paymentAlerts = true;
    public boolean alertSound = true;
    public boolean profitCard = true;
    public boolean payConfirm = false;
    public boolean menuMusic = true;
    public boolean clickSounds = true;
    public boolean menuAnim = true;

    // cosmetics: indexes into Cosmetics.CAPES / PETS / COSTUMES (0 = none)
    public int cape = 0;
    public int pet = 0;
    public int costume = 0;
    public boolean cosmeticsOnMenu = true; // show them on the skin next to the menu
    public int theme = 3; // Desert
    public double alertThreshold = 1_000_000;
    public double payThreshold = 1_000_000;
    public double dailyGoal = 0;

    // HUD layout: position as a 0..1 fraction of the free screen space (-1 = default spot), plus size
    public float profitX = -1, profitY = -1, auctionX = -1, auctionY = -1;
    public float profitScale = 1f, auctionScale = 1f;

    // HUD font: "Minecraft" or the name of a font installed on the PC; hudFontAll = list every installed font in the picker
    public String hudFont = "Minecraft";
    public boolean hudFontAll = false;

    // big-payment animation style: 0 = auto by amount, 1..4 = fixed
    public int animStyle = 0;

    public boolean auctionEnabled = false;
    public boolean floatingItem = true; // big spinning item in the middle of the screen during an auction
    public float floatX = -1, floatY = -1, floatScale = 1f; // where the floating item sits (-1 = default spot) + size
    public boolean winnerCutscene = true; // your skin hands the item to the winner's skin when an auction ends
    public int winnerBannerSeconds = 5;
    public int resultDisplaySeconds = 20;
    public String host = "";
    // last values used in the auction screen
    public int lastAuctionSeconds = 60;
    public double lastStartBid = 0;
    public double lastMinRaise = 0;
    public java.util.List<String> favourites = new java.util.ArrayList<>();

    // leaderboard ledger: player -> [paid you, you paid them]
    public java.util.Map<String, double[]> ledger = new java.util.HashMap<>();

    // profit & loss: balance snapshots as {epochMillis, balance}, oldest first
    public java.util.List<double[]> history = new java.util.ArrayList<>();
    public int pnlRange = 0;            // 0 session, 1 24h, 2 7d, 3 30d, 4 all
    public boolean profitGraph = false; // small graph under the HUD profit card

    // Spotify: your own app's Client ID (no secret needed), the login token, and the now-playing card
    public String spotifyClientId = "";
    public String spotifyRefresh = "";
    public boolean spotifyCard = true;
    public boolean spotifyLyrics = true;
    public int spotifyOffsetMs = 0;     // nudge the lyrics earlier (-) or later (+)
    public float spotifyX = -1, spotifyY = -1, spotifyScale = 1f;

    // custom on-screen text
    public boolean textEnabled = true;
    public java.util.List<TextItem> texts = new java.util.ArrayList<>();

    public double balance = -1; // -1 = unknown (type /bal once)
    public double received = 0;
    public double sent = 0;
    public String day = LocalDate.now().toString();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("tenskugamblehud.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception ignored) { }
        I.rollDay();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(I));
        } catch (Exception ignored) { }
    }

    /** Remember the current balance for the P&L graph (call after every balance change). */
    public void snapshot() {
        if (balance < 0) return;
        if (history == null) history = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        if (!history.isEmpty()) {
            double[] last = history.get(history.size() - 1);
            if (last[1] == balance && now - last[0] < 600_000L) return;       // nothing new
            if (last[1] != balance && now - last[0] < 1000L) {                // burst of changes: merge
                last[0] = now;
                last[1] = balance;
                return;
            }
        }
        history.add(new double[] {now, balance});
        if (history.size() > 4000) {                                          // thin out the oldest half
            java.util.List<double[]> kept = new java.util.ArrayList<>();
            for (int i = 0; i < history.size(); i++) {
                if (i >= 2000 || i % 2 == 0) kept.add(history.get(i));
            }
            history = kept;
        }
    }

    public void rollDay() {
        String today = LocalDate.now().toString();
        if (!today.equals(day)) {
            day = today;
            received = 0;
            sent = 0;
            save();
        }
    }

    /** Remember a payment for the leaderboards. paidMe = they paid you (you won), else you paid them (they won). */
    public void record(String player, double amount, boolean paidMe) {
        if (ledger == null) ledger = new java.util.HashMap<>();
        double[] e = ledger.computeIfAbsent(player, k -> new double[2]);
        e[paidMe ? 0 : 1] += amount;
    }

    /** Sorted leaderboard. winners=true: people who took the most from you. false: people who lost the most to you. */
    public java.util.List<java.util.Map.Entry<String, Double>> board(boolean winners) {
        java.util.List<java.util.Map.Entry<String, Double>> out = new java.util.ArrayList<>();
        if (ledger == null) return out;
        for (var e : ledger.entrySet()) {
            double[] v = e.getValue();
            if (v == null || v.length < 2) continue;
            double net = winners ? v[1] - v[0] : v[0] - v[1];
            if (net > 0) out.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), net));
        }
        out.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return out;
    }

    public void resetHud() {
        profitX = profitY = auctionX = auctionY = floatX = floatY = spotifyX = spotifyY = -1;
        profitScale = auctionScale = floatScale = spotifyScale = 1f;
        save();
    }

    public double net() {
        rollDay();
        return received - sent;
    }
}

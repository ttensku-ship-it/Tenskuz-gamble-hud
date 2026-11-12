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
    public int theme = 3; // Desert
    public double alertThreshold = 1_000_000;
    public double payThreshold = 1_000_000;
    public double dailyGoal = 0;

    // HUD layout: position as a 0..1 fraction of the free screen space (-1 = default spot), plus size
    public float profitX = -1, profitY = -1, auctionX = -1, auctionY = -1;
    public float profitScale = 1f, auctionScale = 1f;

    // custom background (PNG in config/tenskugamblehud/)
    public boolean bgEnabled = false;
    public String bgImage = "";
    public int bgDim = 35;

    // big-payment animation style: 0 = auto by amount, 1..4 = fixed
    public int animStyle = 0;

    public boolean auctionEnabled = false;
    public int winnerBannerSeconds = 5;
    public int resultDisplaySeconds = 20;
    public String host = "";
    public java.util.List<String> favourites = new java.util.ArrayList<>();

    // leaderboard ledger: player -> [paid you, you paid them]
    public java.util.Map<String, double[]> ledger = new java.util.HashMap<>();

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
        profitX = profitY = auctionX = auctionY = -1;
        profitScale = auctionScale = 1f;
        save();
    }

    public double net() {
        rollDay();
        return received - sent;
    }
}

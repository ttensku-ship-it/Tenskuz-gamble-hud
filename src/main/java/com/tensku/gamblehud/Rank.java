package com.tensku.gamblehud;

/** Rank tiers based on your balance. */
public final class Rank {
    private Rank() {}

    public record Tier(String name, double min, int color) {}

    public static final Tier[] ALL = {
        new Tier("BRONZE",      0,        0xFFCD7F32),
        new Tier("SILVER",      1e9,      0xFFC8D0DC),
        new Tier("GOLD",        2.5e9,    0xFFFFD24D),
        new Tier("PLATINUM",    5e9,      0xFF5CE6D0),
        new Tier("DIAMOND",     1e10,     0xFF6FD3FF),
        new Tier("MASTER",      2.5e10,   0xFFD65CFF),
        new Tier("GRANDMASTER", 5e10,     0xFFFF4D4D),
    };

    /** Index of the highest tier reached. Unknown balance (-1) counts as Bronze. */
    public static int index(double balance) {
        int idx = 0;
        if (balance < 0) return 0;
        for (int i = 0; i < ALL.length; i++) {
            if (balance >= ALL[i].min()) idx = i;
        }
        return idx;
    }

    public static boolean isMax(double balance) {
        return index(balance) == ALL.length - 1;
    }

    /** 0..1 progress from the current tier to the next one (1 at max rank). */
    public static double progress(double balance) {
        int i = index(balance);
        if (i >= ALL.length - 1) return 1.0;
        double lo = ALL[i].min(), hi = ALL[i + 1].min();
        double b = Math.max(0, balance);
        return Math.max(0, Math.min(1, (b - lo) / (hi - lo)));
    }

    /** Money still needed for the next tier (0 at max rank). */
    public static double remaining(double balance) {
        int i = index(balance);
        if (i >= ALL.length - 1) return 0;
        return Math.max(0, ALL[i + 1].min() - Math.max(0, balance));
    }
}

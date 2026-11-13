package com.tensku.gamblehud;

/** Layout helper for the movable HUD cards (profit tracker + auction timer). */
public final class Hud {
    private Hud() {}

    public static final int PROFIT_W = 124;
    public static final int AUC_W = 160, AUC_H = 110;
    /** The floating auction item: name + timer box on top, the spinning item below it. */
    public static final int FLOAT_W = 130, FLOAT_H = 80;

    public static float clamp(float s) {
        return Math.max(0.6f, Math.min(2.0f, s));
    }

    /** Height of the card without the optional graph. */
    public static int profitBaseH() {
        return Config.I.dailyGoal > 0 ? 40 : 30;
    }

    public static int profitH() {
        return profitBaseH() + (Config.I.profitGraph ? 38 : 0);
    }

    /** {x, y, w, h} in GUI pixels, scale already applied. */
    public static int[] profitRect(int sw, int sh) {
        float s = clamp(Config.I.profitScale);
        int w = Math.round(PROFIT_W * s), h = Math.round(profitH() * s);
        return rect(Config.I.profitX, Config.I.profitY, 6, 6, w, h, sw, sh);
    }

    public static int[] auctionRect(int sw, int sh) {
        float s = clamp(Config.I.auctionScale);
        int w = Math.round(AUC_W * s), h = Math.round(AUC_H * s);
        return rect(Config.I.auctionX, Config.I.auctionY, sw - w - 6, 6, w, h, sw, sh);
    }

    public static int[] floatRect(int sw, int sh) {
        float s = clamp(Config.I.floatScale);
        int w = Math.round(FLOAT_W * s), h = Math.round(FLOAT_H * s);
        return rect(Config.I.floatX, Config.I.floatY, (sw - w) / 2, (int) (sh * 0.64f) - Math.round(52 * s), w, h, sw, sh);
    }

    private static int[] rect(float fx, float fy, int dx, int dy, int w, int h, int sw, int sh) {
        int x = fx < 0 ? dx : Math.round(fx * Math.max(0, sw - w));
        int y = fy < 0 ? dy : Math.round(fy * Math.max(0, sh - h));
        x = Math.max(0, Math.min(Math.max(0, sw - w), x));
        y = Math.max(0, Math.min(Math.max(0, sh - h), y));
        return new int[] {x, y, w, h};
    }

    /** Pixel position -> stored 0..1 fraction. */
    public static float frac(int pos, int size, int screen) {
        int range = Math.max(1, screen - size);
        return Math.max(0f, Math.min(1f, pos / (float) range));
    }
}

package com.tensku.gamblehud;

import java.util.ArrayList;
import java.util.List;

import com.tensku.gamblehud.Model3D.Box;
import com.tensku.gamblehud.Model3D.Pose;

/**
 * Capes, pets and full-body costumes. Everything is built from coloured boxes in the same
 * model space as {@link Model3D}: hip at (0,0,0), y points down, the ground is at y = 12, the
 * character looks towards +z. No Minecraft classes in here.
 */
public final class Cosmetics {
    private Cosmetics() {}

    public static final String[] CAPES = {
        "None", "Classic Red", "Royal Purple", "Ocean Blue", "Emerald", "Golden", "Tensku Orange", "Galaxy", "Rainbow"
    };
    public static final int[] CAPE_SWATCH = {
        0x00000000, 0xFFC0392B, 0xFF6C3FB5, 0xFF1E88E5, 0xFF1F9D55, 0xFFF2C94C, 0xFFFFB347, 0xFF7B3CD6, 0xFFFF5AA5
    };
    public static final String[] PETS = {"None", "Dog", "Cat", "Parrot", "Baby Gorilla", "Dragon", "Slime"};
    public static final String[] COSTUMES = {"None", "Gorilla", "Brown Bear", "Panda", "Wolf", "Robot"};

    // limb ids (same as Model3D)
    private static final int BODY = Model3D.BODY, HEAD = Model3D.HEAD, ARM_R = Model3D.ARM_R, ARM_L = Model3D.ARM_L,
        LEG_R = Model3D.LEG_R, LEG_L = Model3D.LEG_L, CAPE = Model3D.CAPE;

    // ------------------------------------------------------------------ helpers

    private static void box(List<Box> l, int limb, float pvx, float pvy, float pvz,
                            float cx, float cy, float cz, float w, float h, float d, int color) {
        l.add(new Box(limb, pvx, pvy, pvz, cx, cy, cz, w, h, d, color));
    }

    /** Same box mirrored left/right (centre x = +cx and -cx), same pivot. */
    private static void sym(List<Box> l, int limb, float pvx, float pvy, float pvz,
                            float cx, float cy, float cz, float w, float h, float d, int color) {
        box(l, limb, pvx, pvy, pvz, cx, cy, cz, w, h, d, color);
        box(l, limb, pvx, pvy, pvz, -cx, cy, cz, w, h, d, color);
    }

    /** A right and a left limb part: right pivot at -|pvx|, left pivot at +|pvx|. */
    private static void pair(List<Box> l, int rightLimb, int leftLimb, float pvx, float pvy, float pvz,
                             float cx, float cy, float cz, float w, float h, float d, int color) {
        float ax = Math.abs(pvx);
        box(l, rightLimb, -ax, pvy, pvz, cx, cy, cz, w, h, d, color);
        box(l, leftLimb, ax, pvy, pvz, cx, cy, cz, w, h, d, color);
    }

    private static int mix(int a, int b, float k) {
        k = Math.max(0f, Math.min(1f, k));
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float c = v * s, x = c * (1 - Math.abs((h * 6f) % 2f - 1f)), m = v - c;
        float r, g, b;
        int sector = (int) (h * 6f);
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return 0xFF000000 | ((int) ((r + m) * 255) << 16) | ((int) ((g + m) * 255) << 8) | (int) ((b + m) * 255);
    }

    // ------------------------------------------------------------------ capes

    /** Cape hanging from the shoulders (pivot on the back of the body, swings with Pose.capePitch). */
    public static List<Box> cape(int style, float t, int accentRgb) {
        List<Box> l = new ArrayList<>();
        if (style <= 0) return l;
        final int cols = 6, rows = 4;
        final float cw = 9f / cols, rh = 4f;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float v = r / (float) (rows - 1);
                int color;
                switch (style) {
                    case 1 -> color = r == rows - 1 ? 0xFFF1C40F : mix(0xFFD04A3A, 0xFF8E2218, v);
                    case 2 -> color = r == rows - 1 ? 0xFFF5D76E : mix(0xFF7E4FC8, 0xFF3E1E78, v);
                    case 3 -> color = mix(0xFF2E9BFF, 0xFF0B2A6B, v);
                    case 4 -> color = mix(0xFF2BC46E, 0xFF0A4A2A, v);
                    case 5 -> color = r == rows - 1 ? 0xFFFFF0A0 : mix(0xFFF7D560, 0xFFB8860B, v);
                    case 6 -> color = mix(0xFF000000 | accentRgb, mix(0xFF000000 | accentRgb, 0xFF000000, 0.65f), v);
                    case 7 -> {
                        float wave = 0.5f + 0.5f * (float) Math.sin(t * 1.6f + c * 0.9f + r * 0.7f);
                        color = mix(0xFF1A0B4A, 0xFF8A4DFF, wave * (1f - v * 0.4f));
                        float star = (float) Math.sin(t * 3f + c * 12.9898f + r * 78.233f);
                        if (star > 0.93f) color = 0xFFFFFFFF;
                    }
                    default -> color = hsv(t * 0.18f + c * 0.08f + r * 0.05f, 0.75f, 1f);
                }
                box(l, CAPE, 0, -12f, -2.7f, (c - (cols - 1) / 2f) * cw, rh / 2f + r * rh, 0f, cw, rh, 1f, color);
            }
        }
        // collar
        box(l, CAPE, 0, -12f, -2.7f, 0, -0.4f, 0.3f, 9.6f, 1.2f, 1.4f, 0xFFFFFFFF & mix(0xFFEDEDED, 0xFFB8B8B8, 0.3f));
        return l;
    }

    /** Pose value for the cape swing (negative lets the bottom hang away from the body). */
    public static float capeSwing(float t, float energy) {
        return -0.07f - (0.05f + 0.16f * energy) * (0.5f + 0.5f * (float) Math.sin(t * 3.1f));
    }

    // ------------------------------------------------------------------ costumes

    public static List<Box> costume(int id, float t) {
        return switch (id) {
            case 1 -> gorilla();
            case 2 -> bearLike(0xFF7B4A2A, 0xFF5E361C, 0xFFB98456, 0xFFC9A27A, false);
            case 3 -> bearLike(0xFFF4F4F2, 0xFF1B1B1F, 0xFFF4F4F2, 0xFFE2E2DE, true);
            case 4 -> wolf();
            case 5 -> robot(t);
            default -> new ArrayList<>();
        };
    }

    private static List<Box> gorilla() {
        List<Box> l = new ArrayList<>();
        final int FUR = 0xFF45454E, FUR2 = 0xFF55555F, DARK = 0xFF2A2A30, SILVER = 0xFF8F8F9E;
        // torso
        box(l, BODY, 0, 0, 0, 0, -5.5f, 0, 14, 15, 8, FUR);
        box(l, BODY, 0, 0, 0, 0, -6f, 4.25f, 9, 9, 0.6f, 0xFF2F2B31);
        box(l, BODY, 0, 0, 0, 0, -0.5f, 3.7f, 10, 4, 0.8f, FUR2);
        box(l, BODY, 0, 0, 0, 0, -8.5f, -4.3f, 11, 7, 0.7f, SILVER);
        box(l, BODY, 0, 0, 0, 0, -1.5f, -4.2f, 9, 4, 0.6f, 0xFF7E7E8C);
        box(l, BODY, 0, 0, 0, 0, -13.2f, -0.5f, 12, 2.5f, 7, FUR2);
        // head
        box(l, HEAD, 0, -13, 0, 0, -3f, 1.5f, 8, 7, 8, FUR);
        box(l, HEAD, 0, -13, 0, 0, -7f, 0.5f, 3, 2.4f, 6, FUR2);
        box(l, HEAD, 0, -13, 0, 0, -4.3f, 5.9f, 8.6f, 1.4f, 2.0f, 0xFF25252B);
        box(l, HEAD, 0, -13, 0, 0, -2.0f, 5.65f, 6.4f, 5f, 0.5f, 0xFF6A5A5C);
        box(l, HEAD, 0, -13, 0, 0, -1.0f, 6.4f, 4.6f, 2.8f, 1.6f, 0xFF6A5C5E);
        box(l, HEAD, 0, -13, 0, 0, -1.6f, 7.25f, 3.2f, 1.2f, 0.4f, 0xFF4A3E40);
        sym(l, HEAD, 0, -13, 0, 0.9f, -1.3f, 7.5f, 0.8f, 0.6f, 0.3f, 0xFF111114);
        box(l, HEAD, 0, -13, 0, 0, -0.2f, 7.1f, 3.4f, 0.35f, 0.3f, 0xFF1B1416);
        sym(l, HEAD, 0, -13, 0, 1.8f, -2.4f, 6.0f, 1.3f, 1.0f, 0.4f, 0xFF1A0F0A);
        sym(l, HEAD, 0, -13, 0, 1.6f, -2.55f, 6.25f, 0.4f, 0.4f, 0.2f, 0xFFF4EBDD);
        sym(l, HEAD, 0, -13, 0, 4.4f, -3f, 1.0f, 1f, 2f, 1.6f, FUR2);
        // long arms
        pair(l, ARM_R, ARM_L, 8.5f, -11, 0, 0, 4.5f, 0, 5.4f, 10, 5.4f, FUR);
        pair(l, ARM_R, ARM_L, 8.5f, -11, 0, 0, 13.5f, 0.4f, 5f, 8, 5f, FUR2);
        pair(l, ARM_R, ARM_L, 8.5f, -11, 0, 0, 18.2f, 0.8f, 5.6f, 3.6f, 5.8f, DARK);
        // legs
        pair(l, LEG_R, LEG_L, 3.8f, 2, 0, 0, 3.5f, 0, 5.4f, 7, 5.6f, FUR);
        pair(l, LEG_R, LEG_L, 3.8f, 2, 0, 0, 8.9f, 1.2f, 5.4f, 2.2f, 7f, DARK);
        return l;
    }

    private static List<Box> bearLike(int fur, int dark, int belly, int muzzle, boolean panda) {
        List<Box> l = new ArrayList<>();
        final int NOSE = 0xFF161214;
        box(l, BODY, 0, 0, 0, 0, -6f, 0, 10, 12, 6, fur);
        box(l, BODY, 0, 0, 0, 0, -5f, 3.1f, 6.5f, 8, 0.5f, belly);
        if (panda) box(l, BODY, 0, 0, 0, 0, -10.2f, 0, 10.6f, 2.6f, 6.6f, dark);
        // head
        box(l, HEAD, 0, -12, 0, 0, -4.2f, 0, 9, 8, 8, fur);
        sym(l, HEAD, 0, -12, 0, 3.6f, -8.8f, -0.5f, 3, 3, 1.6f, panda ? dark : fur);
        if (!panda) sym(l, HEAD, 0, -12, 0, 3.6f, -8.8f, 0.4f, 1.8f, 1.8f, 0.5f, 0xFFC9946B);
        box(l, HEAD, 0, -12, 0, 0, -2.7f, 4.7f, 4.6f, 3.4f, 2f, muzzle);
        box(l, HEAD, 0, -12, 0, 0, -3.9f, 5.8f, 2f, 1.2f, 0.5f, NOSE);
        if (panda) {
            sym(l, HEAD, 0, -12, 0, 2.3f, -5f, 4.05f, 2.2f, 2.6f, 0.5f, dark);
            sym(l, HEAD, 0, -12, 0, 2.3f, -5f, 4.35f, 0.7f, 0.7f, 0.3f, 0xFFFFFFFF);
        } else {
            sym(l, HEAD, 0, -12, 0, 2.2f, -5f, 4.1f, 1.2f, 1.2f, 0.4f, NOSE);
        }
        // arms, legs
        pair(l, ARM_R, ARM_L, 7f, -10, 0, 0, 5f, 0, 4f, 11, 4f, panda ? dark : fur);
        pair(l, ARM_R, ARM_L, 7f, -10, 0, 0, 10.8f, 0.2f, 4.2f, 1.8f, 4.2f, dark);
        pair(l, LEG_R, LEG_L, 2.7f, 0, 0, 0, 6f, 0, 4.6f, 12, 4.6f, panda ? dark : fur);
        pair(l, LEG_R, LEG_L, 2.7f, 0, 0, 0, 11f, 0.9f, 4.8f, 2f, 6.2f, dark);
        return l;
    }

    private static List<Box> wolf() {
        List<Box> l = new ArrayList<>();
        final int G = 0xFF8D939C, G2 = 0xFF6F757E, LIGHT = 0xFFAEB4BC, DARK = 0xFF4C525A;
        box(l, BODY, 0, 0, 0, 0, -6f, 0, 9, 12, 5.5f, G);
        box(l, BODY, 0, 0, 0, 0, -7f, 2.9f, 5.5f, 8, 0.5f, 0xFFE8EAEE);
        box(l, BODY, 0, 0, 0, 0, -1.5f, -5.5f, 3, 3, 7, G);
        box(l, BODY, 0, 0, 0, 0, -1.5f, -9.4f, 3.2f, 3.2f, 1.6f, DARK);
        // head
        box(l, HEAD, 0, -12, 0, 0, -4f, 0.5f, 8, 7, 7.5f, G);
        box(l, HEAD, 0, -12, 0, 0, -2.4f, 5.4f, 3.6f, 3, 4.4f, LIGHT);
        box(l, HEAD, 0, -12, 0, 0, -2.0f, 7.7f, 1.6f, 1.2f, 0.5f, 0xFF111114);
        sym(l, HEAD, 0, -12, 0, 2.8f, -9f, -0.5f, 2, 3.4f, 1.2f, G2);
        sym(l, HEAD, 0, -12, 0, 2.8f, -8.8f, 0.1f, 1f, 2.2f, 0.4f, 0xFF3C2F34);
        sym(l, HEAD, 0, -12, 0, 2.2f, -4.6f, 4.3f, 1.4f, 0.9f, 0.4f, 0xFFF2C94C);
        sym(l, HEAD, 0, -12, 0, 2.2f, -4.6f, 4.55f, 0.5f, 0.7f, 0.3f, 0xFF111114);
        sym(l, HEAD, 0, -12, 0, 4.3f, -3f, 2f, 1f, 2f, 2f, 0xFFE8EAEE);
        pair(l, ARM_R, ARM_L, 6.5f, -10, 0, 0, 5.5f, 0, 4f, 12, 4f, G);
        pair(l, ARM_R, ARM_L, 6.5f, -10, 0, 0, 11.4f, 0.2f, 4.2f, 1.8f, 4.4f, DARK);
        pair(l, LEG_R, LEG_L, 2.5f, 0, 0, 0, 6f, 0, 4.2f, 12, 4.4f, G2);
        pair(l, LEG_R, LEG_L, 2.5f, 0, 0, 0, 11f, 0.9f, 4.4f, 2f, 6f, DARK);
        return l;
    }

    private static List<Box> robot(float t) {
        List<Box> l = new ArrayList<>();
        final int M1 = 0xFFB8C1CC, M2 = 0xFFD1D8E0, M3 = 0xFF8A94A3, DK = 0xFF4A5362;
        float glow = 0.75f + 0.25f * (float) Math.sin(t * 4f);
        int eye = mix(0xFF1B6C80, 0xFF3CE6FF, glow);
        box(l, BODY, 0, 0, 0, 0, -6f, 0, 9, 12, 5, M1);
        box(l, BODY, 0, 0, 0, 0, -7f, 2.6f, 6, 5, 0.5f, 0xFF2B3342);
        box(l, BODY, 0, 0, 0, -1.8f, -7f, 3.0f, 1, 1, 0.3f, 0xFFFF7A1A);
        box(l, BODY, 0, 0, 0, 0f, -7f, 3.0f, 1, 1, 0.3f, 0xFF4DFF88);
        box(l, BODY, 0, 0, 0, 1.8f, -7f, 3.0f, 1, 1, 0.3f, 0xFF4DD2FF);
        box(l, BODY, 0, 0, 0, 0, -2f, 0, 9.6f, 1.2f, 5.6f, DK);
        box(l, HEAD, 0, -12, 0, 0, -4f, 0, 8, 8, 7, M2);
        box(l, HEAD, 0, -12, 0, 0, -4f, 3.6f, 6.6f, 2.6f, 0.6f, 0xFF0F2A36);
        sym(l, HEAD, 0, -12, 0, 1.6f, -4f, 3.95f, 1.6f, 1.2f, 0.3f, eye);
        box(l, HEAD, 0, -12, 0, 0, -9.2f, 0, 0.8f, 2.4f, 0.8f, M3);
        box(l, HEAD, 0, -12, 0, 0, -10.8f, 0, 1.6f, 1.6f, 1.6f, 0xFFFF4D4D);
        sym(l, HEAD, 0, -12, 0, 4.3f, -4f, 0, 1, 2.2f, 2.2f, M3);
        pair(l, ARM_R, ARM_L, 6.3f, -10, 0, 0, 5.5f, 0, 3.6f, 12, 3.6f, M3);
        pair(l, ARM_R, ARM_L, 6.3f, -10, 0, 0, 11.8f, 0, 4.2f, 2f, 4.2f, DK);
        pair(l, LEG_R, LEG_L, 2.2f, 0, 0, 0, 6f, 0, 4f, 12, 4.4f, M3);
        pair(l, LEG_R, LEG_L, 2.2f, 0, 0, 0, 11f, 0.9f, 4.4f, 2f, 6f, DK);
        return l;
    }

    /** Costume animation for the preview stage (menu uses the dance pose instead). */
    public static void animateCostume(int id, float t, Pose p) {
        p.y = (float) Math.sin(t * 2.0f) * 0.15f;
        p.headYaw = (float) Math.sin(t * 0.9f) * 0.25f;
        if (id == 1) {
            float ph = t % 6f;
            if (ph < 2.4f) { // chest beat
                float w = ph * 20f;
                p.armRPitch = 1.15f + 0.5f * (float) Math.sin(w);
                p.armLPitch = 1.15f + 0.5f * (float) Math.sin(w + Math.PI);
                p.armRRoll = -0.45f;
                p.armLRoll = 0.45f;
                p.headPitch = -0.2f;
                p.y += (float) Math.abs(Math.sin(w)) * 0.5f;
                return;
            }
        }
        p.armRPitch = (float) Math.sin(t * 2f) * 0.22f;
        p.armLPitch = -p.armRPitch;
        p.legRPitch = -p.armRPitch * 0.6f;
        p.legLPitch = p.armRPitch * 0.6f;
    }

    // ------------------------------------------------------------------ pets (feet at y = 12, face +z)

    public static List<Box> pet(int id, float t) {
        return switch (id) {
            case 1 -> dog();
            case 2 -> cat();
            case 3 -> parrot();
            case 4 -> gorilla();   // drawn small (Pose.scale)
            case 5 -> dragon();
            case 6 -> slime();
            default -> new ArrayList<>();
        };
    }

    private static void quadLegs(List<Box> l, float px, float py, float pz, float cy, float w, float h, int fur, int paw) {
        // front-right + back-left share one swing, front-left + back-right the other
        box(l, LEG_R, -px, py, pz, 0, cy, 0, w, h, w, fur);
        box(l, LEG_L, px, py, pz, 0, cy, 0, w, h, w, fur);
        box(l, LEG_L, -px, py, -pz, 0, cy, 0, w, h, w, fur);
        box(l, LEG_R, px, py, -pz, 0, cy, 0, w, h, w, fur);
        box(l, LEG_R, -px, py, pz, 0, cy + h / 2 - 0.2f, 0.25f, w + 0.1f, 0.6f, w + 0.4f, paw);
        box(l, LEG_L, px, py, pz, 0, cy + h / 2 - 0.2f, 0.25f, w + 0.1f, 0.6f, w + 0.4f, paw);
        box(l, LEG_L, -px, py, -pz, 0, cy + h / 2 - 0.2f, 0.25f, w + 0.1f, 0.6f, w + 0.4f, paw);
        box(l, LEG_R, px, py, -pz, 0, cy + h / 2 - 0.2f, 0.25f, w + 0.1f, 0.6f, w + 0.4f, paw);
    }

    private static List<Box> dog() {
        List<Box> l = new ArrayList<>();
        final int O = 0xFFD9893B, W = 0xFFF3E6D0, O2 = 0xFFB86A24, K = 0xFF14100E;
        box(l, BODY, 0, 0, 0, 0, 8f, 0, 4.2f, 4.2f, 7.4f, O);
        box(l, BODY, 0, 0, 0, 0, 8.6f, 3.4f, 3f, 2.6f, 0.8f, W);
        quadLegs(l, 1.4f, 9.6f, 2.6f, 1.2f, 1.5f, 2.6f, O, W);
        box(l, HEAD, 0, 7.2f, 3.2f, 0, -1.2f, 1.4f, 3.6f, 3.4f, 3.4f, O);
        box(l, HEAD, 0, 7.2f, 3.2f, 0, -0.8f, 3.6f, 2.2f, 1.6f, 2f, W);
        box(l, HEAD, 0, 7.2f, 3.2f, 0, -0.3f, 4.7f, 0.9f, 0.7f, 0.4f, K);
        sym(l, HEAD, 0, 7.2f, 3.2f, 1.0f, -1.9f, 3.15f, 0.6f, 0.6f, 0.3f, K);
        sym(l, HEAD, 0, 7.2f, 3.2f, 1.3f, -3.2f, 0.8f, 1f, 1.6f, 0.7f, O2);
        box(l, ARM_R, 0, 7.2f, -3.6f, 0, -1.4f, -0.2f, 0.9f, 2.6f, 0.9f, O);
        box(l, ARM_R, 0, 7.2f, -3.6f, 0, -3.0f, -0.2f, 1f, 0.7f, 1f, W);
        return l;
    }

    private static List<Box> cat() {
        List<Box> l = new ArrayList<>();
        final int B = 0xFF222228, B2 = 0xFF3A3A44, PINK = 0xFFE8A0B4;
        box(l, BODY, 0, 0, 0, 0, 8f, 0, 3.4f, 3.8f, 6.8f, B);
        box(l, BODY, 0, 0, 0, 0, 8.4f, 3.3f, 2.2f, 2.4f, 0.7f, 0xFFEDEDED);
        quadLegs(l, 1.1f, 9.8f, 2.3f, 1.1f, 1.2f, 2.4f, B, B2);
        box(l, HEAD, 0, 7.2f, 3f, 0, -1.2f, 1.3f, 3.4f, 3.2f, 3.2f, B);
        sym(l, HEAD, 0, 7.2f, 3f, 1.2f, -3.2f, 0.9f, 1f, 1.5f, 0.8f, B);
        sym(l, HEAD, 0, 7.2f, 3f, 1.2f, -3.1f, 1.35f, 0.5f, 0.9f, 0.2f, PINK);
        sym(l, HEAD, 0, 7.2f, 3f, 0.9f, -1.7f, 2.95f, 0.8f, 0.7f, 0.3f, 0xFF7CFF6B);
        sym(l, HEAD, 0, 7.2f, 3f, 0.9f, -1.7f, 3.15f, 0.25f, 0.6f, 0.2f, 0xFF111114);
        box(l, HEAD, 0, 7.2f, 3f, 0, -0.7f, 2.9f, 1.6f, 1.1f, 0.4f, 0xFFEDEDED);
        box(l, HEAD, 0, 7.2f, 3f, 0, -1.0f, 3.2f, 0.6f, 0.4f, 0.3f, PINK);
        box(l, ARM_R, 0, 7f, -3.4f, 0, -2.2f, -0.4f, 0.9f, 4.4f, 0.9f, B);
        box(l, ARM_R, 0, 7f, -3.4f, 0, -4.8f, 0f, 0.9f, 1.6f, 0.9f, B2);
        return l;
    }

    private static List<Box> parrot() {
        List<Box> l = new ArrayList<>();
        final int R = 0xFFE0342B, Y = 0xFFF2D14A, BL = 0xFF2D6BE0;
        box(l, BODY, 0, 0, 0, 0, 8.5f, 0, 3f, 4.6f, 3f, R);
        box(l, BODY, 0, 0, 0, 0, 8.8f, 1.3f, 2.2f, 3f, 0.5f, 0xFFF2A12E);
        box(l, BODY, 0, 0, 0, 0, 11.4f, -1.8f, 1.4f, 5f, 0.8f, BL);
        box(l, BODY, 0, 0, 0, 0, 14.2f, -1.9f, 1.2f, 1f, 0.7f, Y);
        box(l, HEAD, 0, 6.2f, 0.3f, 0, -1.2f, 0.4f, 2.8f, 2.8f, 2.8f, R);
        box(l, HEAD, 0, 6.2f, 0.3f, 0, -0.8f, 2.2f, 1.2f, 1.1f, 1.2f, Y);
        box(l, HEAD, 0, 6.2f, 0.3f, 0, -0.1f, 2.0f, 0.9f, 0.5f, 0.9f, 0xFF3A3A3A);
        sym(l, HEAD, 0, 6.2f, 0.3f, 1.2f, -1.6f, 0.9f, 0.5f, 0.5f, 0.4f, 0xFFFFFFFF);
        sym(l, HEAD, 0, 6.2f, 0.3f, 1.2f, -1.6f, 1.15f, 0.25f, 0.25f, 0.2f, 0xFF111114);
        box(l, ARM_R, -1.7f, 7f, 0, -0.3f, 1.9f, 0, 0.6f, 3.8f, 2.4f, BL);
        box(l, ARM_L, 1.7f, 7f, 0, 0.3f, 1.9f, 0, 0.6f, 3.8f, 2.4f, BL);
        return l;
    }

    private static List<Box> dragon() {
        List<Box> l = new ArrayList<>();
        final int G = 0xFF2F9E57, G2 = 0xFF3BB36A, GD = 0xFF1F7A42, Y = 0xFFF2D14A, OR = 0xFFF2994A;
        box(l, BODY, 0, 0, 0, 0, 8f, 0, 4.6f, 4.2f, 8f, G);
        box(l, BODY, 0, 0, 0, 0, 9.7f, 0.2f, 3.4f, 0.8f, 6.8f, 0xFFC8E58A);
        box(l, BODY, 0, 0, 0, 0, 5.7f, -2.2f, 0.8f, 1.2f, 1f, OR);
        box(l, BODY, 0, 0, 0, 0, 5.7f, 0f, 0.8f, 1.2f, 1f, OR);
        box(l, BODY, 0, 0, 0, 0, 5.7f, 2.2f, 0.8f, 1.2f, 1f, OR);
        box(l, BODY, 0, 0, 0, 0, 8.2f, -5f, 3f, 2.8f, 3f, G);
        box(l, BODY, 0, 0, 0, 0, 8.6f, -7.6f, 2.1f, 2f, 3f, G);
        box(l, BODY, 0, 0, 0, 0, 9.0f, -9.8f, 1.2f, 1.2f, 2.4f, G);
        box(l, BODY, 0, 0, 0, 0, 9.0f, -11.4f, 0.9f, 1.6f, 1.6f, Y);
        quadLegs(l, 1.6f, 9.8f, 2.6f, 1.1f, 1.6f, 2.4f, G, Y);
        box(l, HEAD, 0, 6.5f, 3.8f, 0, -1.2f, 1.6f, 3.8f, 3.4f, 3.6f, G);
        box(l, HEAD, 0, 6.5f, 3.8f, 0, -0.8f, 3.8f, 2.6f, 1.8f, 2.2f, G2);
        sym(l, HEAD, 0, 6.5f, 3.8f, 0.6f, -1.5f, 4.95f, 0.4f, 0.4f, 0.3f, 0xFF111114);
        sym(l, HEAD, 0, 6.5f, 3.8f, 1.1f, -3.5f, 0.8f, 0.7f, 1.6f, 0.7f, Y);
        sym(l, HEAD, 0, 6.5f, 3.8f, 1.3f, -2.0f, 2.95f, 0.8f, 0.8f, 0.4f, 0xFFFFE066);
        sym(l, HEAD, 0, 6.5f, 3.8f, 1.3f, -2.0f, 3.2f, 0.3f, 0.6f, 0.2f, 0xFF111114);
        // wings: root on the back, turn around the z axis to flap
        box(l, ARM_R, -2.3f, 6.4f, -0.5f, -3.4f, 0, 0, 6.2f, 0.5f, 4.4f, GD);
        box(l, ARM_R, -2.3f, 6.4f, -0.5f, -4.2f, 0.05f, 1.4f, 4.4f, 0.6f, 0.7f, G);
        box(l, ARM_R, -2.3f, 6.4f, -0.5f, -4.2f, 0.05f, -1.4f, 4.4f, 0.6f, 0.7f, G);
        box(l, ARM_L, 2.3f, 6.4f, -0.5f, 3.4f, 0, 0, 6.2f, 0.5f, 4.4f, GD);
        box(l, ARM_L, 2.3f, 6.4f, -0.5f, 4.2f, 0.05f, 1.4f, 4.4f, 0.6f, 0.7f, G);
        box(l, ARM_L, 2.3f, 6.4f, -0.5f, 4.2f, 0.05f, -1.4f, 4.4f, 0.6f, 0.7f, G);
        return l;
    }

    private static List<Box> slime() {
        List<Box> l = new ArrayList<>();
        box(l, BODY, 0, 0, 0, 0, 8.2f, 0, 7.5f, 7.5f, 7.5f, 0xCC6FD96A);
        box(l, BODY, 0, 0, 0, 0, 8.6f, 0, 4f, 4f, 4f, 0xFF3FA84A);
        sym(l, BODY, 0, 0, 0, 1.8f, 7.2f, 3.8f, 1.3f, 1.8f, 0.5f, 0xFF14301A);
        box(l, BODY, 0, 0, 0, 0, 9.4f, 3.8f, 2.2f, 0.5f, 0.4f, 0xFF14301A);
        return l;
    }

    /** Pet animation: faces the player in 3/4 view; (x, scale) are set by the caller afterwards if needed. */
    public static void animatePet(int id, float t, Pose p) {
        p.reset();
        switch (id) {
            case 1 -> { // dog: wags and trots on the spot
                p.y = (float) Math.sin(t * 6f) * 0.2f;
                p.legRPitch = (float) Math.sin(t * 5f) * 0.28f;
                p.legLPitch = -p.legRPitch;
                p.armRRoll = (float) Math.sin(t * 11f) * 0.55f;
                p.headYaw = (float) Math.sin(t * 0.8f) * 0.35f;
            }
            case 2 -> { // cat: slow tail
                p.legRPitch = (float) Math.sin(t * 2f) * 0.1f;
                p.legLPitch = -p.legRPitch;
                p.armRRoll = (float) Math.sin(t * 2.2f) * 0.45f;
                p.headYaw = (float) Math.sin(t * 0.7f) * 0.4f;
            }
            case 3 -> { // parrot: hovers and flaps
                float f = 0.25f + 0.55f * (float) Math.abs(Math.sin(t * 7f));
                p.y = -9f + (float) Math.sin(t * 3f) * 1.2f;
                p.armRRoll = f;
                p.armLRoll = -f;
                p.headYaw = (float) Math.sin(t * 1.3f) * 0.5f;
            }
            case 4 -> { // baby gorilla: tiny chest beat
                p.scale = 0.38f;
                float ph = t % 4f;
                if (ph < 1.6f) {
                    float w = ph * 22f;
                    p.armRPitch = 1.15f + 0.5f * (float) Math.sin(w);
                    p.armLPitch = 1.15f + 0.5f * (float) Math.sin(w + Math.PI);
                    p.armRRoll = -0.45f;
                    p.armLRoll = 0.45f;
                } else {
                    p.armRPitch = (float) Math.sin(t * 3f) * 0.2f;
                    p.armLPitch = -p.armRPitch;
                }
                p.headYaw = (float) Math.sin(t * 1.1f) * 0.3f;
            }
            case 5 -> { // dragon: wing flaps
                float f = 0.2f + 0.5f * (float) Math.sin(t * 6f);
                p.armRRoll = f;
                p.armLRoll = -f;
                p.y = (float) Math.sin(t * 6f) * 0.3f;
                p.legRPitch = (float) Math.sin(t * 3f) * 0.12f;
                p.legLPitch = -p.legRPitch;
                p.headYaw = (float) Math.sin(t * 0.9f) * 0.3f;
            }
            case 6 -> { // slime: bounces
                float k = (float) Math.abs(Math.sin(t * 3f));
                p.y = -k * 3.4f;
                p.scale = 1f + 0.1f * (1f - k);
            }
            default -> { }
        }
    }
}

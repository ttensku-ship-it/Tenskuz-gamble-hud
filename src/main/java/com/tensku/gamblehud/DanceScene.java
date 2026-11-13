package com.tensku.gamblehud;

import com.tensku.gamblehud.Model3D.Pose;

/**
 * The little show next to the menu: you dance (a different move every second or so), a mob shambles over,
 * you cut it down with a netherite sword until it falls over and vanishes, then you dance again.
 * Each 14 second round is a different mob; the fourth mob gets finished off with Thor's hammer
 * and a lightning strike. Cycle: zombie, husk, skeleton, drowned (56 seconds in total).
 */
public final class DanceScene {
    public static final float LOOP = 14f;
    private static final float PUNCH0 = 4.3f, PUNCH_DT = 0.6f;
    private static final float[] SWIPES = {5.2f, 6.4f, 7.6f};

    public static final int MOBS = 4;
    public static final int ZOMBIE = 0, HUSK = 1, SKELETON = 2, DROWNED = 3;
    /** Smoke colours per mob. */
    public static final int[][] POOF = {
        {0x8FD18A, 0xC8C8C8, 0x6BAA67},
        {0xD9C48A, 0xC8B27A, 0xE8DDB0},
        {0xF2F2F2, 0xC8C8C8, 0xA8A8A8},
        {0x6FE0D0, 0x3FA6B8, 0xC8F4F0},
    };

    /** Which mob this round (0..3). The 4th one is the one that meets the hammer. */
    public int mob;
    /** True while you are holding your weapon. */
    public boolean weaponOut;
    /** True when the weapon is Thor's hammer (last mob), false for the netherite sword. */
    public boolean hammer;
    /** 0..1 while the lightning strike hits the last mob, -1 otherwise. */
    public float bolt = -1f;

    public final Pose player = new Pose();
    public final Pose zombie = new Pose();
    public boolean zombieVisible = true;
    /** 0..1 while the zombie turns into a puff of smoke, -1 otherwise. */
    public float poof = -1f;
    /** Where the puff happens (units to the right of the player). */
    public float poofX;

    private final Pose a = new Pose(), b = new Pose();

    public void update(float time, float nearGap, float farGap, boolean withZombie) {
        float s = time % LOOP;
        zombieVisible = withZombie;
        poof = -1f;
        bolt = -1f;
        // the mob is swapped while it is invisible (12.8 s, right when the next one pops in)
        mob = ((int) ((time + (LOOP - 12.8f)) / LOOP)) % MOBS;
        hammer = mob == MOBS - 1;
        weaponOut = withZombie && s >= 3.2f && s < 11.0f;
        if (withZombie && hammer && s >= 8.95f && s < 9.45f) bolt = (s - 8.95f) / 0.5f;

        dance(a, time);
        if (!withZombie) {
            player.set(a);
            return;
        }

        // ---- you
        if (s < 3.4f) {
            player.set(a);
        } else if (s < 4.0f) {
            fight(b, s, hammer);
            Pose.lerp(a, b, smooth((s - 3.4f) / 0.6f), player);
        } else if (s < 8.9f) {
            fight(player, s, hammer);
        } else if (s < 9.2f) {
            fight(a, s, hammer);
            victory(b, s);
            Pose.lerp(a, b, smooth((s - 8.9f) / 0.3f), player);
        } else if (s < 10.8f) {
            victory(player, s);
        } else if (s < 11.3f) {
            victory(a, s);
            dance(b, time);
            Pose.lerp(a, b, smooth((s - 10.8f) / 0.5f), player);
        } else {
            player.set(a);
        }
        // zombie claws land on you
        if (s >= 4.0f && s < 9.0f) {
            float hurt = 0f;
            for (float sw : SWIPES) hurt = Math.max(hurt, bump(s, sw + 0.12f, 0.14f));
            player.x -= 2.0f * hurt;
            if (hurt > 0.05f) player.tint = ((int) (120 * hurt) << 24) | 0xFF3030;
        }

        // ---- zombie
        zombie.reset();
        float gap;
        if (s < 3.8f) gap = lerp(farGap, nearGap, smooth(s / 3.8f));
        else if (s < 12.8f) gap = nearGap;
        else gap = farGap;

        zombieVisible = !(s >= 10.6f && s < 12.8f);
        if (s >= 10.4f && s < 11.2f) {
            poof = (s - 10.4f) / 0.8f;
            poofX = nearGap + 5f;
        }

        float walk = s < 3.8f ? 1f : 0f;
        float t = time;
        zombie.yaw = -0.85f;
        zombie.y = -Math.abs((float) Math.sin(t * 6f)) * 0.6f * (walk > 0 ? 1f : 0.4f);
        zombie.armRPitch = 1.5f + 0.06f * (float) Math.sin(t * 4f);
        zombie.armLPitch = 1.5f + 0.06f * (float) Math.sin(t * 4f + 1f);
        zombie.armRRoll = 0.06f;
        zombie.armLRoll = -0.06f;
        zombie.headRoll = 0.15f * (float) Math.sin(t * 3f);
        zombie.headPitch = 0.2f;
        zombie.headYaw = 0.35f * (float) Math.sin(t * 2f) + 0.4f;
        float legSwing = walk > 0 ? 0.55f : 0.12f;
        zombie.legRPitch = legSwing * (float) Math.sin(t * 6f);
        zombie.legLPitch = -legSwing * (float) Math.sin(t * 6f);

        if (s >= 4.0f && s < 9.0f) {
            // claws swipe at you
            for (float sw : SWIPES) {
                float e = bump(s, sw, 0.22f);
                zombie.armRPitch += 0.55f * e;
                zombie.armLPitch += 0.55f * e;
            }
        }

        // knockback + red flash for every punch that lands
        float recoil = 0f;
        for (int k = 0; k < 8; k++) {
            float th = PUNCH0 + PUNCH_DT * k;
            if (s >= th && s < th + 0.6f) recoil = Math.max(recoil, (float) Math.exp(-(s - th) / 0.13f));
        }
        zombie.x = gap + 3.2f * recoil;
        zombie.leanPivot = Model3D.FEET;
        zombie.lean = 0.28f * recoil;
        if (recoil > 0.04f) zombie.tint = ((int) (125 * recoil) << 24) | 0xFF3030;

        // down he goes
        if (s >= 9.0f && s < 10.6f) {
            float u = clamp01((s - 9.0f) / 1.3f);
            float fall = u * u;
            zombie.lean = 1.5f * fall;
            zombie.x = gap + 5f * fall;
            zombie.armRPitch = 1.2f; zombie.armLPitch = 1.0f;
            zombie.legRPitch = zombie.legLPitch = 0f;
            zombie.tint = ((int) (110 * (1f - fall * 0.5f)) << 24) | 0xFF3030;
        }

        // pops back in at the far end
        if (s >= 12.8f && s < 13.3f) {
            zombie.scale = Math.max(0.01f, easeOutBack((s - 12.8f) / 0.5f));
        }
    }

    // ---------------------------------------------------------------- poses

    // ---------------------------------------------------------------- dance moves

    private static final float MOVE_LEN = 1.5f, MOVE_BLEND = 0.3f;
    /** Order the moves come in. */
    private static final int[] SEQUENCE = {0, 1, 2, 5, 3, 9, 4, 6, 7, 8};
    private static final Pose DA = new Pose(), DB = new Pose();

    /** Whatever move is on at time t, blended into the next one at the end of its slot. */
    private static void dance(Pose p, float t) {
        float slot = t / MOVE_LEN;
        int i = (int) Math.floor(slot);
        float frac = slot - i;
        int n = SEQUENCE.length;
        move(p, SEQUENCE[Math.floorMod(i, n)], t);
        float blendStart = 1f - MOVE_BLEND / MOVE_LEN;
        if (frac > blendStart) {
            move(DB, SEQUENCE[Math.floorMod(i + 1, n)], t);
            DA.set(p);
            Pose.lerp(DA, DB, smooth((frac - blendStart) / (1f - blendStart)), p);
        }
    }

    private static void move(Pose p, int style, float t) {
        p.reset();
        float beat = t * 7f;
        float sb = (float) Math.sin(beat);
        switch (style) {
            case 0 -> { // disco bounce
                p.y = -Math.abs(sb) * 3.5f;
                p.lean = (float) Math.sin(beat * 0.5f) * 0.16f;
                p.yaw = 0.75f * (float) Math.sin(t * 1.7f);
                p.headRoll = 0.35f * (float) Math.sin(beat + 1f);
                p.headPitch = 0.2f * (float) Math.sin(beat * 2f);
                p.headYaw = 0.5f * (float) Math.sin(beat * 0.5f);
                p.armRRoll = 2.3f + 0.8f * sb;
                p.armLRoll = -(2.3f - 0.8f * (float) Math.sin(beat + 1.6f));
                p.armRPitch = 0.5f * (float) Math.sin(beat * 0.5f);
                p.armLPitch = -0.5f * (float) Math.sin(beat * 0.5f + 1f);
                p.legRPitch = 0.8f * sb;
                p.legLPitch = -0.8f * sb;
            }
            case 1 -> { // disco point: one arm to the sky, one hand on the hip, hips swinging
                float sw = (float) Math.sin(beat * 0.5f);
                p.y = -Math.abs(sb) * 1.6f;
                p.lean = 0.22f * sw;
                p.yaw = 0.35f * sw;
                p.armRRoll = 2.9f;
                p.armRPitch = 0.15f * sb;
                p.armLRoll = -0.7f;
                p.armLPitch = 0.6f;
                p.headRoll = 0.2f * sw;
                p.headYaw = -0.3f * sw;
                p.legRPitch = 0.45f * sw;
                p.legLPitch = -0.45f * sw;
            }
            case 2 -> { // robot: snaps between stiff poses
                int ph = ((int) Math.floor(t * 3.2f)) & 3;
                float[][] arms = {{1.6f, 0f, 1.6f, 0f}, {0f, 1.5f, 1.6f, -1.5f}, {1.6f, 1.5f, 0f, -1.5f}, {2.4f, 0f, 2.4f, 0f}};
                p.armRPitch = arms[ph][0]; p.armRRoll = arms[ph][1];
                p.armLPitch = arms[ph][2]; p.armLRoll = arms[ph][3];
                p.headYaw = (ph % 2 == 0 ? 0.5f : -0.5f);
                p.yaw = (ph < 2 ? 0.5f : -0.5f);
                p.legRPitch = ph % 2 == 0 ? 0.35f : 0f;
                p.legLPitch = ph % 2 == 1 ? 0.35f : 0f;
            }
            case 3 -> { // floss: arms swing one way, hips the other
                float f = (float) Math.sin(t * 11f);
                p.armRPitch = 0.55f; p.armLPitch = 0.55f;
                p.armRRoll = 0.55f + 0.85f * f;
                p.armLRoll = 0.55f + 0.85f * f;
                p.lean = -0.18f * f;
                p.x = -2.2f * f;
                p.y = -Math.abs((float) Math.sin(t * 5.5f)) * 1.2f;
                p.legRPitch = 0.2f; p.legLPitch = -0.2f;
                p.headYaw = 0.3f * f;
            }
            case 4 -> { // moonwalk: smooth glide, one arm doffing an imaginary hat
                float g = (float) Math.sin(t * 5f);
                p.x = 3.4f * (float) Math.sin(t * 1.6f);
                p.lean = -0.14f;
                p.yaw = 0.9f;
                p.legRPitch = 0.6f * g;
                p.legLPitch = -0.6f * g;
                p.armRRoll = 2.6f + 0.15f * g;
                p.armRPitch = 0.3f;
                p.armLRoll = -0.6f; p.armLPitch = 1.0f;
                p.headYaw = -0.3f;
            }
            case 5 -> { // spin with arms out
                p.yaw = t * 5.5f;
                p.y = -Math.abs((float) Math.sin(t * 5.5f * 0.5f)) * 2.5f;
                p.armRRoll = 1.5f; p.armLRoll = -1.5f;
                p.armRPitch = 0.1f * sb; p.armLPitch = -0.1f * sb;
                p.legRPitch = 0.5f * sb; p.legLPitch = -0.5f * sb;
                p.headRoll = 0.15f * sb;
            }
            case 6 -> { // Y - M - C - A style arm letters
                int ph = ((int) Math.floor(t * 2.6f)) & 3;
                p.y = -Math.abs(sb) * 1.8f;
                switch (ph) {
                    case 0 -> { p.armRRoll = 2.6f; p.armLRoll = -2.6f; }               // Y
                    case 1 -> { p.armRPitch = 2.0f; p.armRRoll = 0.5f; p.armLPitch = 2.0f; p.armLRoll = -0.5f; } // M
                    case 2 -> { p.armRRoll = 2.8f; p.armLRoll = -0.4f; p.armLPitch = 0.6f; }                     // C
                    default -> { p.armRRoll = 3.0f; p.armLRoll = -3.0f; p.armRPitch = 0.5f; p.armLPitch = 0.5f; } // A
                }
                p.legRPitch = 0.5f * sb; p.legLPitch = -0.5f * sb;
                p.headRoll = 0.2f * sb;
            }
            case 7 -> { // headbang: fists pumping, head nodding hard
                float hb = (float) Math.sin(t * 14f);
                p.y = -Math.abs(hb) * 2.2f;
                p.headPitch = 0.55f + 0.5f * hb;
                p.lean = 0.06f * hb;
                p.armRPitch = 1.3f + 0.6f * hb; p.armRRoll = 0.3f;
                p.armLPitch = 1.3f - 0.6f * hb; p.armLRoll = -0.3f;
                p.legRPitch = -0.35f; p.legLPitch = 0.35f;
            }
            case 8 -> { // the wave: arms ripple overhead
                p.y = -Math.abs(sb) * 2.4f;
                p.armRRoll = 1.7f + 1.1f * sb;
                p.armLRoll = -(1.7f + 1.1f * (float) Math.sin(beat - 1.3f));
                p.armRPitch = 0.3f * (float) Math.sin(beat * 0.5f);
                p.armLPitch = 0.3f * (float) Math.sin(beat * 0.5f + 1.3f);
                p.yaw = 0.5f * (float) Math.sin(t * 2f);
                p.lean = 0.12f * (float) Math.sin(beat - 0.6f);
                p.legRPitch = 0.6f * sb; p.legLPitch = -0.6f * sb;
            }
            default -> { // the dab, with a little pulse
                float pu = 0.5f + 0.5f * (float) Math.sin(t * 9f);
                p.headPitch = 0.55f + 0.15f * pu;
                p.headYaw = 0.65f;
                p.armLPitch = 2.5f; p.armLRoll = -0.45f;
                p.armRRoll = 2.0f + 0.1f * pu; p.armRPitch = 0.0f;
                p.lean = 0.14f;
                p.y = -Math.abs((float) Math.sin(t * 4.5f)) * 1.4f;
                p.legRPitch = 0.3f; p.legLPitch = -0.3f;
            }
        }
    }

    private static void fight(Pose p, float s, boolean hammer) {
        p.reset();
        float e = 0f;
        for (int k = 0; k < 8; k++) e = Math.max(e, bump(s, PUNCH0 + PUNCH_DT * k, 0.17f));
        float beat = s * 9f;
        p.yaw = 0.95f + 0.06f * (float) Math.sin(beat);
        p.y = -Math.abs((float) Math.sin(beat)) * 1.4f;
        p.x = 3.5f * e;
        p.headYaw = -0.4f;
        p.headPitch = 0.1f + (hammer ? 0.2f * e : 0f);
        if (hammer) {
            // Thor: hammer lifted overhead, then slammed down
            p.armRPitch = 2.7f - 1.7f * e;
            p.armRRoll = 0.25f;
            p.armLPitch = 1.0f;
            p.armLRoll = -1.0f;
            p.lean = 0.05f * e;
            p.legRPitch = -0.6f; p.legLPitch = 0.6f;
        } else {
            // netherite sword: raised, then a quick chop
            p.armRPitch = 2.1f - 1.15f * e;
            p.armRRoll = 0.25f;
            p.armLPitch = 0.85f;
            p.armLRoll = -0.7f;
            p.legRPitch = -0.45f + 0.15f * (float) Math.sin(beat);
            p.legLPitch = 0.45f - 0.15f * (float) Math.sin(beat);
        }
    }

    private static void victory(Pose p, float s) {
        p.reset();
        float u = s - 9.0f;
        p.y = -Math.abs((float) Math.sin(u * 8f)) * 5f;
        p.yaw = u >= 1.2f ? 0f : (float) (2 * Math.PI) * smooth(clamp01(u / 1.2f));
        p.armRRoll = 2.7f + 0.2f * (float) Math.sin(u * 16f);
        p.armLRoll = -2.7f - 0.2f * (float) Math.sin(u * 16f + 1f);
        p.headRoll = 0.25f * (float) Math.sin(u * 8f);
        p.legRPitch = 0.5f * (float) Math.sin(u * 8f);
        p.legLPitch = -0.5f * (float) Math.sin(u * 8f);
    }

    // ---------------------------------------------------------------- helpers

    private static float bump(float s, float centre, float halfWidth) {
        return Math.max(0f, 1f - Math.abs(s - centre) / halfWidth);
    }

    private static float clamp01(float x) { return Math.max(0f, Math.min(1f, x)); }

    private static float smooth(float x) {
        x = clamp01(x);
        return x * x * (3f - 2f * x);
    }

    private static float lerp(float a, float b, float k) { return a + (b - a) * k; }

    private static float easeOutBack(float x) {
        x = clamp01(x);
        float c1 = 1.70158f, c3 = c1 + 1f, p = x - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }
}

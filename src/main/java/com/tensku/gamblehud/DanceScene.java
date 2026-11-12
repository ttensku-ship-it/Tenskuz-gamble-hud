package com.tensku.gamblehud;

import com.tensku.gamblehud.Model3D.Pose;

/**
 * The little show next to the menu: you dance, a zombie shambles over,
 * you box it until it falls over and vanishes, then you dance again. Loops every 14 seconds.
 */
public final class DanceScene {
    public static final float LOOP = 14f;
    private static final float PUNCH0 = 4.3f, PUNCH_DT = 0.6f;
    private static final float[] SWIPES = {5.2f, 6.4f, 7.6f};

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

        dance(a, time);
        if (!withZombie) {
            player.set(a);
            return;
        }

        // ---- you
        if (s < 3.4f) {
            player.set(a);
        } else if (s < 4.0f) {
            fight(b, s);
            Pose.lerp(a, b, smooth((s - 3.4f) / 0.6f), player);
        } else if (s < 8.9f) {
            fight(player, s);
        } else if (s < 9.2f) {
            fight(a, s);
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

    private static void dance(Pose p, float t) {
        p.reset();
        float beat = t * 7f;
        p.y = -Math.abs((float) Math.sin(beat)) * 3.5f;
        p.lean = (float) Math.sin(beat * 0.5f) * 0.16f;
        p.yaw = 0.75f * (float) Math.sin(t * 1.7f);
        p.headRoll = 0.35f * (float) Math.sin(beat + 1f);
        p.headPitch = 0.2f * (float) Math.sin(beat * 2f);
        p.headYaw = 0.5f * (float) Math.sin(beat * 0.5f);
        p.armRRoll = 2.3f + 0.8f * (float) Math.sin(beat);
        p.armLRoll = -(2.3f - 0.8f * (float) Math.sin(beat + 1.6f));
        p.armRPitch = 0.5f * (float) Math.sin(beat * 0.5f);
        p.armLPitch = -0.5f * (float) Math.sin(beat * 0.5f + 1f);
        p.legRPitch = 0.8f * (float) Math.sin(beat);
        p.legLPitch = -0.8f * (float) Math.sin(beat);
    }

    private static void fight(Pose p, float s) {
        p.reset();
        float eR = 0f, eL = 0f;
        for (int k = 0; k < 8; k++) {
            float e = bump(s, PUNCH0 + PUNCH_DT * k, 0.17f);
            if (k % 2 == 0) eR = Math.max(eR, e); else eL = Math.max(eL, e);
        }
        float beat = s * 9f;
        p.yaw = 0.95f + 0.06f * (float) Math.sin(beat);
        p.y = -Math.abs((float) Math.sin(beat)) * 1.4f;
        p.x = 3.5f * Math.max(eR, eL);
        p.headYaw = -0.4f;
        p.headPitch = 0.1f;
        p.armRPitch = 1.15f + 0.45f * eR;
        p.armRRoll = 0.25f;
        p.armLPitch = 1.15f + 0.45f * eL;
        p.armLRoll = -0.25f;
        p.legRPitch = -0.45f + 0.15f * (float) Math.sin(beat);
        p.legLPitch = 0.45f - 0.15f * (float) Math.sin(beat);
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

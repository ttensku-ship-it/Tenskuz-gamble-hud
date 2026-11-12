package com.tensku.gamblehud;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny orthographic 3D renderer for Minecraft-style box characters.
 * Every visible face of every box is drawn as a textured parallelogram, so the
 * character really turns around, shows its sides and back, and has depth.
 * No Minecraft classes in here: drawing goes through {@link Canvas}.
 */
public final class Model3D {
    private Model3D() {}

    /** Camera tilt (looking slightly down so the tops of heads show). */
    public static final float CAM = 0.30f;
    /** Feet are 12 units below the hip. */
    public static final float FEET = 12f;
    /** How far below the hip the feet appear on screen, per unit of scale. */
    public static final float FEET_DROP = (float) (FEET * Math.cos(CAM));

    public interface Canvas {
        /**
         * Draw region (u, v, w, h) of a 64x64 texture so that texel (0,0) lands on (ox, oy),
         * one texel step in x moves by (ux, uy) and one texel step in y moves by (vx, vy).
         * shade / tint are ARGB overlays to draw on top of the face (0 = none).
         */
        void face(Object tex, float ox, float oy, float ux, float uy, float vx, float vy,
                  int u, int v, int w, int h, int shade, int tint);
    }

    /** Everything that can move. Angles in radians, distances in model units (1 unit = 1 skin pixel). */
    public static final class Pose {
        public float x, y;                         // root offset (y positive = down)
        public float yaw, lean, leanPivot;         // turn around, roll sideways (around y=leanPivot)
        public float headYaw, headPitch, headRoll;
        public float armRPitch, armRRoll, armLPitch, armLRoll;
        public float legRPitch, legLPitch;
        public float scale = 1f;
        public int tint;                           // ARGB flash on top of the model (hit = red)

        public void reset() {
            x = y = yaw = lean = leanPivot = 0f;
            headYaw = headPitch = headRoll = 0f;
            armRPitch = armRRoll = armLPitch = armLRoll = 0f;
            legRPitch = legLPitch = 0f;
            scale = 1f;
            tint = 0;
        }

        public void set(Pose o) {
            x = o.x; y = o.y; yaw = o.yaw; lean = o.lean; leanPivot = o.leanPivot;
            headYaw = o.headYaw; headPitch = o.headPitch; headRoll = o.headRoll;
            armRPitch = o.armRPitch; armRRoll = o.armRRoll; armLPitch = o.armLPitch; armLRoll = o.armLRoll;
            legRPitch = o.legRPitch; legLPitch = o.legLPitch;
            scale = o.scale; tint = o.tint;
        }

        public static void lerp(Pose a, Pose b, float k, Pose out) {
            out.x = l(a.x, b.x, k); out.y = l(a.y, b.y, k);
            out.yaw = l(a.yaw, b.yaw, k); out.lean = l(a.lean, b.lean, k);
            out.leanPivot = k < 0.5f ? a.leanPivot : b.leanPivot;
            out.headYaw = l(a.headYaw, b.headYaw, k); out.headPitch = l(a.headPitch, b.headPitch, k);
            out.headRoll = l(a.headRoll, b.headRoll, k);
            out.armRPitch = l(a.armRPitch, b.armRPitch, k); out.armRRoll = l(a.armRRoll, b.armRRoll, k);
            out.armLPitch = l(a.armLPitch, b.armLPitch, k); out.armLRoll = l(a.armLRoll, b.armLRoll, k);
            out.legRPitch = l(a.legRPitch, b.legRPitch, k); out.legLPitch = l(a.legLPitch, b.legLPitch, k);
            out.scale = l(a.scale, b.scale, k);
            out.tint = k < 0.5f ? a.tint : b.tint;
        }

        private static float l(float a, float b, float k) { return a + (b - a) * k; }
    }

    // ---------------------------------------------------------------- parts

    private static final int BODY = 0, HEAD = 1, ARM_R = 2, ARM_L = 3, LEG_R = 4, LEG_L = 5;

    private record Part(int limb, float px, float py, float pz, float ox, float oy, float oz,
                        float w, float h, float d, int u0, int v0, float inflate, boolean overlay) {}

    private static final List<Part> PARTS = new ArrayList<>();
    static {
        // base layer
        PARTS.add(new Part(BODY,  0, 0, 0,    0, -6, 0,  8, 12, 4, 16, 16, 0f, false));
        PARTS.add(new Part(HEAD,  0, -12, 0,  0, -4, 0,  8, 8, 8,  0, 0,   0f, false));
        PARTS.add(new Part(ARM_R, -6, -10, 0, 0, 4, 0,   4, 12, 4, 40, 16, 0f, false));
        PARTS.add(new Part(ARM_L, 6, -10, 0,  0, 4, 0,   4, 12, 4, 32, 48, 0f, false));
        PARTS.add(new Part(LEG_R, -2, 0, 0,   0, 6, 0,   4, 12, 4, 0, 16,  0f, false));
        PARTS.add(new Part(LEG_L, 2, 0, 0,    0, 6, 0,   4, 12, 4, 16, 48, 0f, false));
        // second skin layer (jacket, hat, sleeves, pants)
        PARTS.add(new Part(BODY,  0, 0, 0,    0, -6, 0,  8, 12, 4, 16, 32, 0.3f, true));
        PARTS.add(new Part(HEAD,  0, -12, 0,  0, -4, 0,  8, 8, 8,  32, 0,  0.5f, true));
        PARTS.add(new Part(ARM_R, -6, -10, 0, 0, 4, 0,   4, 12, 4, 40, 32, 0.3f, true));
        PARTS.add(new Part(ARM_L, 6, -10, 0,  0, 4, 0,   4, 12, 4, 48, 48, 0.3f, true));
        PARTS.add(new Part(LEG_R, -2, 0, 0,   0, 6, 0,   4, 12, 4, 0, 32,  0.3f, true));
        PARTS.add(new Part(LEG_L, 2, 0, 0,    0, 6, 0,   4, 12, 4, 0, 48,  0.3f, true));
    }

    // normal | corner sign (x,y,z) | U axis | V axis
    private static final int[][] FACES = {
        {  0,  0,  1,   -1, -1,  1,    1, 0,  0,   0, 1,  0 }, // front
        {  0,  0, -1,    1, -1, -1,   -1, 0,  0,   0, 1,  0 }, // back
        { -1,  0,  0,   -1, -1, -1,    0, 0,  1,   0, 1,  0 }, // character's right side
        {  1,  0,  0,    1, -1,  1,    0, 0, -1,   0, 1,  0 }, // character's left side
        {  0, -1,  0,   -1, -1, -1,    1, 0,  0,   0, 0,  1 }, // top
        {  0,  1,  0,   -1,  1,  1,    1, 0,  0,   0, 0, -1 }, // bottom
    };

    private static final class Face {
        Object tex; float depth;
        float ox, oy, ux, uy, vx, vy;
        int u, v, w, h, shade, tint;
    }

    // ---------------------------------------------------------------- maths

    private static void rotX(float[] v, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float y = v[1] * c - v[2] * s, z = v[1] * s + v[2] * c;
        v[1] = y; v[2] = z;
    }

    private static void rotY(float[] v, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float x = v[0] * c + v[2] * s, z = -v[0] * s + v[2] * c;
        v[0] = x; v[2] = z;
    }

    private static void rotZ(float[] v, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float x = v[0] * c - v[1] * s, y = v[0] * s + v[1] * c;
        v[0] = x; v[1] = y;
    }

    /** Local (box) coordinates -> camera space. point=false transforms a direction instead of a position. */
    private static void xform(float[] v, Part pt, Pose p, boolean point) {
        float rx = 0, ry = 0, rz = 0;
        switch (pt.limb()) {
            case HEAD -> { rx = p.headPitch; ry = p.headYaw; rz = p.headRoll; }
            case ARM_R -> { rx = p.armRPitch; rz = p.armRRoll; }
            case ARM_L -> { rx = p.armLPitch; rz = p.armLRoll; }
            case LEG_R -> rx = p.legRPitch;
            case LEG_L -> rx = p.legLPitch;
            default -> { }
        }
        rotZ(v, rz); rotY(v, ry); rotX(v, rx);
        if (point) { v[0] += pt.px(); v[1] += pt.py(); v[2] += pt.pz(); v[1] -= p.leanPivot; }
        rotZ(v, p.lean);
        if (point) v[1] += p.leanPivot;
        rotY(v, p.yaw);
        if (point) {
            v[1] -= FEET;
            v[0] *= p.scale; v[1] *= p.scale; v[2] *= p.scale;
            v[1] += FEET;
            v[0] += p.x; v[1] += p.y;
        }
        // camera tilt
        float c = (float) Math.cos(CAM), s = (float) Math.sin(CAM);
        float y = v[1] * c + v[2] * s, z = -v[1] * s + v[2] * c;
        v[1] = y; v[2] = z;
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Draw one character.
     * @param cx,cy screen position of the hip centre
     * @param S     screen pixels per model unit
     */
    public static void draw(Canvas canvas, Object tex, boolean overlays, Pose p, float cx, float cy, float S) {
        List<Face> out = new ArrayList<>();
        float[] pos = new float[3], dirU = new float[3], dirV = new float[3], nrm = new float[3];

        // light from above-left-front
        float lx = -0.30f, ly = -0.70f, lz = 0.65f;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll; ly /= ll; lz /= ll;

        for (Part pt : PARTS) {
            if (pt.overlay() && !overlays) continue;
            float W = pt.w() + 2 * pt.inflate(), H = pt.h() + 2 * pt.inflate(), D = pt.d() + 2 * pt.inflate();
            float hx = W / 2, hy = H / 2, hz = D / 2;
            for (int f = 0; f < 6; f++) {
                int[] fd = FACES[f];
                nrm[0] = fd[0]; nrm[1] = fd[1]; nrm[2] = fd[2];
                xform(nrm, pt, p, false);
                if (nrm[2] <= 0.0001f) continue; // facing away

                // face size in model units and in texels, and the texture region
                float fw, fh; int tw, th, u, v;
                int w = (int) pt.w(), h = (int) pt.h(), d = (int) pt.d();
                switch (f) {
                    case 0 -> { fw = W; fh = H; tw = w; th = h; u = pt.u0() + d;         v = pt.v0() + d; }
                    case 1 -> { fw = W; fh = H; tw = w; th = h; u = pt.u0() + 2 * d + w; v = pt.v0() + d; }
                    case 2 -> { fw = D; fh = H; tw = d; th = h; u = pt.u0();             v = pt.v0() + d; }
                    case 3 -> { fw = D; fh = H; tw = d; th = h; u = pt.u0() + d + w;     v = pt.v0() + d; }
                    case 4 -> { fw = W; fh = D; tw = w; th = d; u = pt.u0() + d;         v = pt.v0(); }
                    default -> { fw = W; fh = D; tw = w; th = d; u = pt.u0() + d + w;    v = pt.v0(); }
                }

                pos[0] = fd[3] * hx + pt.ox(); pos[1] = fd[4] * hy + pt.oy(); pos[2] = fd[5] * hz + pt.oz();
                xform(pos, pt, p, true);
                dirU[0] = fd[6]; dirU[1] = fd[7]; dirU[2] = fd[8];
                dirV[0] = fd[9]; dirV[1] = fd[10]; dirV[2] = fd[11];
                xform(dirU, pt, p, false);
                xform(dirV, pt, p, false);

                float su = fw / tw * p.scale, sv = fh / th * p.scale;
                Face face = new Face();
                face.tex = tex;
                face.ox = cx + pos[0] * S;
                face.oy = cy + pos[1] * S;
                face.ux = dirU[0] * su * S; face.uy = dirU[1] * su * S;
                face.vx = dirV[0] * sv * S; face.vy = dirV[1] * sv * S;
                face.u = u; face.v = v; face.w = tw; face.h = th;
                face.depth = pos[2] + dirU[2] * fw * p.scale / 2 + dirV[2] * fh * p.scale / 2 + (pt.overlay() ? 0.05f : 0f);

                if (!pt.overlay()) {
                    float dot = Math.max(0f, nrm[0] * lx + nrm[1] * ly + nrm[2] * lz);
                    float bright = 0.50f + 0.50f * dot;
                    face.shade = Math.max(0, Math.min(215, (int) ((1f - bright) * 235f))) << 24;
                    face.tint = p.tint;
                }
                out.add(face);
            }
        }

        out.sort((a, b) -> Float.compare(a.depth, b.depth)); // far to near
        for (Face f : out) {
            canvas.face(f.tex, f.ox, f.oy, f.ux, f.uy, f.vx, f.vy, f.u, f.v, f.w, f.h, f.shade, f.tint);
        }
    }
}

package com.tensku.gamblehud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** The now-playing card (bottom right by default): cover, title, progress bar and the lyrics line that is being sung. */
public final class SpotifyCard {
    private SpotifyCard() {}

    private static final int GREEN = 0xFF1DB954;
    private static final Identifier COVER = Identifier.fromNamespaceAndPath(TenskuGambleHud.MOD_ID, "spotify/cover");

    private static Spotify.Art shown;     // the cover currently loaded as a texture
    private static boolean coverOk;
    private static int coverW, coverH;

    // lyric line transition (so the new line fades in)
    private static int lastLine = -2;
    private static String lastKey = "";

    public static void render(GuiGraphicsExtractor g, Minecraft mc) {
        Config c = Config.I;
        if (!c.spotifyCard) return;
        if (mc.gui.screen() instanceof HudEditScreen) return; // the editor draws a sample
        Spotify.Now n = Spotify.now;
        if (n == null) return;
        long t = System.currentTimeMillis();
        if (!n.playing() && t - Spotify.lastPlayingAt > 90_000L) return; // paused for a long time: hide
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int[] r = Hud.spotifyRect(sw, sh);
        syncCover();
        draw(g, mc, r[0], r[1], Hud.clamp(c.spotifyScale), n, Spotify.lyrics, Spotify.art, t);
    }

    /** Turns a freshly downloaded cover into a texture (render thread only). */
    private static void syncCover() {
        Spotify.Art a = Spotify.art;
        if (a == shown) return;
        shown = a;
        coverOk = false;
        if (a == null) return;
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(a.png()));
            coverW = img.getWidth();
            coverH = img.getHeight();
            DynamicTexture tex = new DynamicTexture(() -> "tenskugamblehud spotify cover", img);
            Minecraft.getInstance().getTextureManager().register(COVER, tex);
            coverOk = true;
        } catch (Exception ignored) { }
    }

    /** Sample data for the HUD editor. */
    public static void drawSample(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s) {
        long t = System.currentTimeMillis();
        Spotify.Now n = new Spotify.Now("sample", "Song Title", "Artist Name", "Album", 40_000, null, t % 40_000, t, true, false);
        List<Lyrics.Line> lines = new ArrayList<>();
        String[] words = {"These are the lyrics", "of the song you play", "scrolling in time", "with the music", "line by line", "on your screen"};
        for (int i = 0; i < 10; i++) lines.add(new Lyrics.Line(i * 4000L, words[i % words.length]));
        draw(g, mc, x, y, s, n, Lyrics.synced("sample", lines), null, t);
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s, Spotify.Now n, Lyrics ly,
                            Spotify.Art art, long nowMs) {
        Theme th = Theme.current();
        boolean showLyrics = Config.I.spotifyLyrics;
        int w = Hud.SPOTIFY_W, h = Hud.spotifyH();

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(s, s);

        g.fill(-1, -1, w + 1, h + 1, th.accent());
        Ui.backdrop(g, th, 0, 0, w, h);
        g.fill(0, 0, w, 46, 0x66000000);

        // cover
        g.fill(4, 4, 42, 42, 0xFF000000);
        if (art != null && coverOk && shown == art) {
            g.blit(RenderPipelines.GUI_TEXTURED, COVER, 5, 5, 0, 0, 36, 36, coverW, coverH, coverW, coverH);
        } else {
            g.fill(5, 5, 41, 41, GREEN);
            HudFont.text(g, mc.font, "~", 21, 14, 0xFF000000, false);
        }

        int tx = 47, tw = w - tx - 5;
        HudFont.text(g, mc.font, HudFont.fit(mc.font, n.title(), tw), tx, 7, 0xFFFFFFFF, true);
        HudFont.text(g, mc.font, HudFont.fit(mc.font, n.artist(), tw), tx, 18, 0xFFB9BDCB, true);

        long prog = n.progress(nowMs);
        String time = n.ad() ? "Ad" : Ui.time(prog).replaceFirst("^0", "") + " / " + Ui.time(n.durationMs()).replaceFirst("^0", "");
        HudFont.text(g, mc.font, time, tx, 29, 0xFF9AA0B5, true);
        if (!n.playing()) {
            String p = "PAUSED";
            HudFont.text(g, mc.font, p, w - 5 - HudFont.width(mc.font, p), 29, th.accent(), true);
        }

        // progress bar
        g.fill(5, 41, w - 5, 44, 0x99000000);
        if (n.durationMs() > 0) {
            int fillW = (int) ((w - 10) * (prog / (double) n.durationMs()));
            g.fill(5, 41, 5 + fillW, 44, GREEN);
            g.fill(5 + fillW - 1, 40, 5 + fillW + 1, 45, 0xFFFFFFFF);
        }

        if (showLyrics) {
            g.fill(0, 46, w, h, 0x44000000);
            g.fill(0, 46, w, 47, 0x33FFFFFF);
            lyricsArea(g, mc, w, n, ly, prog);
        }
        g.pose().popMatrix();
    }

    private static void lyricsArea(GuiGraphicsExtractor g, Minecraft mc, int w, Spotify.Now n, Lyrics ly, long prog) {
        if (n.ad()) { center(g, mc, w, "...", 67, 0xFF70768C); return; }
        if (ly == null || ly.state == Lyrics.State.LOADING) { center(g, mc, w, "Loading lyrics...", 67, 0xFF70768C); return; }
        if (ly.state == Lyrics.State.NONE) { center(g, mc, w, "No lyrics found", 67, 0xFF70768C); return; }
        if (ly.state == Lyrics.State.INSTRUMENTAL) { center(g, mc, w, "Instrumental", 67, 0xFF70768C); return; }

        long at = prog + Config.I.spotifyOffsetMs;
        int idx = ly.indexAt(at, n.durationMs());
        if (!n.key().equals(lastKey) || idx != lastLine) { lastKey = n.key(); lastLine = idx; lineChangedAt = System.currentTimeMillis(); }

        // previous line
        String prev = ly.text(idx - 1);
        if (prev != null) center(g, mc, w, shorten(mc, prev, w - 14), 51, 0xFF70768C);

        // current line (up to two rows), fades in
        String cur = idx < 0 ? "..." : ly.text(idx);
        if (cur == null || cur.isBlank()) cur = "...";
        float fade = Math.max(0.35f, Math.min(1f, (System.currentTimeMillis() - lineChangedAt) / 250f));
        int col = ((int) (255 * fade) << 24) | 0xFFFFFF;
        List<String> rows = wrap(cur, w - 14, s -> HudFont.width(mc.font, s), 2);
        int cy = rows.size() == 1 ? 67 : 62;
        for (int i = 0; i < rows.size(); i++) center(g, mc, w, rows.get(i), cy + i * 10, col);

        // next line
        String next = ly.text(idx + 1);
        if (next != null) center(g, mc, w, shorten(mc, next.isBlank() ? "..." : next, w - 14), 84, 0xFF70768C);
        if (ly.state == Lyrics.State.PLAIN) {
            String note = "not synced";
            HudFont.text(g, mc.font, note, w - 4 - HudFont.width(mc.font, note), 49, 0xFF555B70, false);
        }
    }

    private static long lineChangedAt = 0;

    private static String shorten(Minecraft mc, String s, int maxW) {
        return HudFont.fit(mc.font, s, maxW);
    }

    private static void center(GuiGraphicsExtractor g, Minecraft mc, int w, String s, int y, int color) {
        HudFont.text(g, mc.font, s, (w - HudFont.width(mc.font, s)) / 2, y, color, true);
    }

    /** Word-wraps text into at most maxLines rows no wider than maxW (last row is cut with ".." if the text is longer). */
    public static List<String> wrap(String text, int maxW, ToIntFunction<String> width, int maxLines) {
        List<String> out = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (width.applyAsInt(trial) <= maxW) {
                cur = new StringBuilder(trial);
                continue;
            }
            if (cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder();
            }
            // a single word that is too wide: cut it
            String w2 = word;
            while (width.applyAsInt(w2) > maxW && w2.length() > 1) w2 = w2.substring(0, w2.length() - 1);
            cur = new StringBuilder(w2);
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
        if (out.size() > maxLines) {
            List<String> cut = new ArrayList<>(out.subList(0, maxLines));
            String last = cut.get(maxLines - 1);
            while (width.applyAsInt(last + "..") > maxW && last.length() > 1) last = last.substring(0, last.length() - 1);
            cut.set(maxLines - 1, last + "..");
            return cut;
        }
        return out;
    }
}

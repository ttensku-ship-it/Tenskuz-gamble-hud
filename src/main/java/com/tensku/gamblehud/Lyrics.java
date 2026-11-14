package com.tensku.gamblehud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lyrics for one track, looked up on LRCLIB (free, no key). Synced lines when available, plain text otherwise. */
public final class Lyrics {
    public enum State { LOADING, NONE, INSTRUMENTAL, SYNCED, PLAIN }

    public record Line(long ms, String text) {}

    public final String key;
    public final State state;
    public final List<Line> synced;
    public final List<String> plain;

    private Lyrics(String key, State state, List<Line> synced, List<String> plain) {
        this.key = key;
        this.state = state;
        this.synced = synced;
        this.plain = plain;
    }

    public static Lyrics loading(String key) { return new Lyrics(key, State.LOADING, List.of(), List.of()); }
    public static Lyrics none(String key) { return new Lyrics(key, State.NONE, List.of(), List.of()); }
    public static Lyrics instrumental(String key) { return new Lyrics(key, State.INSTRUMENTAL, List.of(), List.of()); }
    public static Lyrics synced(String key, List<Line> l) { return new Lyrics(key, State.SYNCED, l, List.of()); }
    public static Lyrics plain(String key, List<String> l) { return new Lyrics(key, State.PLAIN, List.of(), l); }

    /** Number of lines to pick from, whichever kind this is. */
    public int size() { return state == State.SYNCED ? synced.size() : plain.size(); }

    public String text(int i) {
        if (i < 0 || i >= size()) return null;
        return state == State.SYNCED ? synced.get(i).text() : plain.get(i);
    }

    /** Index of the line that is being sung at progressMs (-1 = before the first line). Plain lyrics are only estimated. */
    public int indexAt(long progressMs, long durationMs) {
        if (state == State.SYNCED) {
            int lo = 0, hi = synced.size() - 1, ans = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (synced.get(mid).ms() <= progressMs) { ans = mid; lo = mid + 1; } else hi = mid - 1;
            }
            return ans;
        }
        if (state == State.PLAIN && durationMs > 0 && !plain.isEmpty()) {
            return (int) Math.min(plain.size() - 1, Math.max(0, progressMs * plain.size() / durationMs));
        }
        return -1;
    }

    // ------------------------------------------------------------------ LRC parsing

    private static final Pattern TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    /** "[01:23.45] text" lines (several tags per line allowed). Metadata tags like [ar:..] are skipped. */
    public static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null) return out;
        for (String raw : lrc.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.charAt(0) != '[') continue;
            Matcher m = TAG.matcher(line);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2));
                long frac = 0;
                String f = m.group(3);
                if (f != null) {
                    frac = Long.parseLong(f);
                    if (f.length() == 1) frac *= 100;
                    else if (f.length() == 2) frac *= 10;
                }
                times.add(min * 60_000 + sec * 1000 + frac);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = line.substring(end).strip();
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::ms));
        return out;
    }

    // ------------------------------------------------------------------ LRCLIB

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static Lyrics fromJson(String key, JsonObject o) {
        if (o.has("instrumental") && !o.get("instrumental").isJsonNull() && o.get("instrumental").getAsBoolean()) {
            return instrumental(key);
        }
        String s = str(o, "syncedLyrics");
        if (s != null && !s.isBlank()) {
            List<Line> l = parseLrc(s);
            if (!l.isEmpty()) return synced(key, l);
        }
        String p = str(o, "plainLyrics");
        if (p != null && !p.isBlank()) {
            List<String> l = new ArrayList<>();
            for (String ln : p.split("\\R")) l.add(ln.strip());
            return plain(key, l);
        }
        return none(key);
    }

    private static String cleanTitle(String t) {
        return t.replaceAll("\\s*[(\\[].*?[)\\]]", "").replaceAll("\\s+-\\s+.*$", "").strip();
    }

    /** Looks the song up. Blocking: run on a background thread. Never throws; returns NONE if nothing is found. */
    public static Lyrics fetch(String key, String title, String artist, String album, long durationMs) {
        try {
            String base = Spotify.LRCLIB;
            long sec = Math.round(durationMs / 1000.0);
            // 1) exact match
            String url = base + "/api/get?track_name=" + Http.enc(title) + "&artist_name=" + Http.enc(artist)
                + "&album_name=" + Http.enc(album == null ? "" : album) + "&duration=" + sec;
            Http.Resp r = Http.request("GET", url, null, null);
            if (r.code() == 200) {
                Lyrics l = fromJson(key, JsonParser.parseString(r.body()).getAsJsonObject());
                if (l.state != State.NONE) return l;
            }
            // 2) fuzzy search on the cleaned title + first artist
            String firstArtist = artist.contains(",") ? artist.substring(0, artist.indexOf(',')).strip() : artist;
            String clean = cleanTitle(title);
            r = Http.request("GET", base + "/api/search?track_name=" + Http.enc(clean) + "&artist_name=" + Http.enc(firstArtist), null, null);
            if (r.code() == 200) {
                JsonArray arr = JsonParser.parseString(r.body()).getAsJsonArray();
                JsonObject best = null;
                int bestScore = -1;
                for (JsonElement e : arr) {
                    if (!e.isJsonObject()) continue;
                    JsonObject o = e.getAsJsonObject();
                    int score = 0;
                    String sy = str(o, "syncedLyrics");
                    if (sy != null && !sy.isBlank()) score += 4;
                    else if (str(o, "plainLyrics") != null) score += 1;
                    JsonElement d = o.get("duration");
                    if (d != null && !d.isJsonNull() && Math.abs(d.getAsDouble() - durationMs / 1000.0) <= 3) score += 3;
                    if (score > bestScore) { bestScore = score; best = o; }
                }
                if (best != null) return fromJson(key, best);
            }
        } catch (Exception ignored) {
            // offline / LRCLIB down: just show "no lyrics"
        }
        return none(key);
    }
}

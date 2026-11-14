package com.tensku.gamblehud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Spotify link: logs in with the Authorization Code + PKCE flow (you use your own Spotify app's Client ID, no secret needed),
 * then polls "what is playing" every few seconds on a background thread. Lyrics come from {@link Lyrics}.
 * Everything here runs off the render thread; the render thread only reads the volatile fields.
 */
public final class Spotify {
    static { System.setProperty("java.awt.headless", "true"); }

    private Spotify() {}

    public enum Status { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    /** One poll result. Replaced as a whole so the render thread always sees a consistent snapshot. */
    public record Now(String key, String title, String artist, String album, long durationMs, String imageUrl,
                      long progressMs, long sampledAt, boolean playing, boolean ad) {
        /** Where the song is right now (the last poll plus the time that has passed since). */
        public long progress(long nowMs) {
            long p = playing ? progressMs + Math.max(0, nowMs - sampledAt) : progressMs;
            return durationMs > 0 ? Math.max(0, Math.min(durationMs, p)) : Math.max(0, p);
        }
    }

    /** Cover picture of a track, already scaled and encoded as PNG (turned into a texture on the render thread). */
    public record Art(String key, byte[] png) {}

    // ---- values the UI reads ----
    public static volatile Status status = Status.DISCONNECTED;
    public static volatile String message = "";
    public static volatile Now now;
    public static volatile long lastPlayingAt;
    public static volatile Lyrics lyrics;
    public static volatile Art art;

    // ---- endpoints (package-private so a test can point them at a fake server) ----
    static String ACCOUNTS = "https://accounts.spotify.com";
    static String API = "https://api.spotify.com";
    static String LRCLIB = "https://lrclib.net";
    static int port = 8898;
    static Consumer<String> opener = Spotify::openBrowser;

    private static final String SCOPES = "user-read-currently-playing user-read-playback-state";
    private static final SecureRandom RNG = new SecureRandom();

    /** The address to add under "Redirect URIs" in the Spotify dashboard. */
    public static String redirectUri() {
        return "http://127.0.0.1:" + port + "/callback";
    }

    // ------------------------------------------------------------------ lifecycle

    /** Call once at start-up: reconnects by itself if you logged in before. */
    public static void init() {
        if (!token().isEmpty()) {
            status = Status.CONNECTING;
            message = "Reconnecting...";
            startPolling();
        }
    }

    public static boolean connected() {
        return !token().isEmpty();
    }

    private static String token() {
        String t = Config.I.spotifyRefresh;
        return t == null ? "" : t.trim();
    }

    private static String clientId() {
        String t = Config.I.spotifyClientId;
        return t == null ? "" : t.trim();
    }

    private static void fail(String msg) {
        status = Status.ERROR;
        message = msg;
    }

    public static synchronized void disconnect(String why) {
        stopPolling();
        cancelLogin();
        Config.I.spotifyRefresh = "";
        Config.save();
        accessToken = "";
        accessExpiry = 0;
        now = null;
        lyrics = null;
        art = null;
        status = Status.DISCONNECTED;
        message = why == null ? "" : why;
    }

    // ------------------------------------------------------------------ login (PKCE)

    private static volatile ServerSocket loginServer;
    private static volatile int loginGen = 0;

    public static void connect() {
        String cid = clientId();
        if (cid.isEmpty()) {
            fail("Paste your Client ID first");
            return;
        }
        cancelLogin();
        final int gen = ++loginGen;
        status = Status.CONNECTING;
        message = "Approve the login in your browser...";
        final String verifier = random(64), state = random(24);
        final String challenge = challenge(verifier);
        Thread t = new Thread(() -> login(cid, verifier, challenge, state, gen), "tensku-spotify-login");
        t.setDaemon(true);
        t.start();
    }

    private static void cancelLogin() {
        loginGen++;
        ServerSocket s = loginServer;
        loginServer = null;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    private static String random(int n) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(chars.charAt(RNG.nextInt(chars.length())));
        return sb.toString();
    }

    static String challenge(String verifier) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void login(String cid, String verifier, String challenge, String state, int gen) {
        ServerSocket ss;
        try {
            ss = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"));
            ss.setSoTimeout(1000);
        } catch (IOException e) {
            if (gen == loginGen) fail("Port " + port + " is busy - close the other program using it");
            return;
        }
        loginServer = ss;
        try {
            String url = ACCOUNTS + "/authorize?" + Http.form(
                "client_id", cid, "response_type", "code", "redirect_uri", redirectUri(),
                "code_challenge_method", "S256", "code_challenge", challenge, "scope", SCOPES, "state", state);
            try {
                opener.accept(url);
            } catch (Exception e) {
                saveLoginLink(url);
            }
            long deadline = System.currentTimeMillis() + 180_000L;
            while (gen == loginGen && System.currentTimeMillis() < deadline) {
                Socket s;
                try {
                    s = ss.accept();
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    return; // closed by cancelLogin()
                }
                try (s) {
                    if (handleCallback(s, cid, verifier, state, gen)) return;
                } catch (IOException ignored) { }
            }
            if (gen == loginGen && status == Status.CONNECTING) fail("Login timed out - press Connect again");
        } finally {
            try { ss.close(); } catch (IOException ignored) { }
            if (loginServer == ss) loginServer = null;
        }
    }

    /** Reads one browser request. Returns true once the login attempt is finished (good or bad). */
    private static boolean handleCallback(Socket s, String cid, String verifier, String state, int gen) throws IOException {
        s.setSoTimeout(5000);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
        String line = in.readLine();
        if (line == null) return false;
        for (int i = 0; i < 100; i++) {          // skip the headers
            String h = in.readLine();
            if (h == null || h.isEmpty()) break;
        }
        String[] parts = line.split(" ");
        if (parts.length < 2 || !parts[0].equals("GET")) { reply(s, 400, "Bad request"); return false; }
        String target = parts[1];
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q);
        if (!path.equals("/callback")) { reply(s, 404, "Not found"); return false; }
        Map<String, String> p = query(q < 0 ? "" : target.substring(q + 1));
        if (!state.equals(p.get("state"))) { reply(s, 400, "Wrong state - go back to Minecraft and press Connect again"); return false; }
        if (p.containsKey("error")) {
            reply(s, 200, "Login cancelled. You can close this tab.");
            if (gen == loginGen) fail("Login cancelled in Spotify");
            return true;
        }
        String code = p.get("code");
        if (code == null) { reply(s, 400, "No code"); return false; }
        reply(s, 200, "Spotify connected! You can close this tab and go back to Minecraft.");
        if (gen == loginGen) exchange(cid, verifier, code);
        return true;
    }

    private static Map<String, String> query(String qs) {
        Map<String, String> m = new HashMap<>();
        for (String kv : qs.split("&")) {
            if (kv.isEmpty()) continue;
            int i = kv.indexOf('=');
            String k = i < 0 ? kv : kv.substring(0, i), v = i < 0 ? "" : kv.substring(i + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return m;
    }

    private static void reply(Socket s, int code, String text) throws IOException {
        String html = "<!doctype html><meta charset=utf-8><title>Tensku Client</title>"
            + "<body style=\"font-family:sans-serif;background:#121212;color:#fff;text-align:center;padding-top:15vh\">"
            + "<h2 style=\"color:#1DB954\">Tensku Client</h2><p>" + text + "</p>";
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\nContent-Type: text/html; charset=utf-8\r\n"
            + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
        OutputStream os = s.getOutputStream();
        os.write(head.getBytes(StandardCharsets.ISO_8859_1));
        os.write(b);
        os.flush();
    }

    private static void exchange(String cid, String verifier, String code) {
        try {
            Http.Resp r = Http.request("POST", ACCOUNTS + "/api/token", Http.form(
                "client_id", cid, "grant_type", "authorization_code", "code", code,
                "redirect_uri", redirectUri(), "code_verifier", verifier), null);
            if (r.code() != 200) {
                fail("Spotify refused the login: " + errorText(r.body()));
                return;
            }
            storeTokens(JsonParser.parseString(r.body()).getAsJsonObject());
            status = Status.CONNECTED;
            message = "";
            startPolling();
        } catch (Exception e) {
            fail("Login failed: " + e.getMessage());
        }
    }

    private static String errorText(String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            if (o.has("error_description")) return o.get("error_description").getAsString();
            if (o.has("error")) return o.get("error").isJsonObject()
                ? o.getAsJsonObject("error").get("message").getAsString() : o.get("error").getAsString();
        } catch (Exception ignored) { }
        return "unknown error";
    }

    // ------------------------------------------------------------------ tokens

    private static volatile String accessToken = "";
    private static volatile long accessExpiry = 0;

    private static synchronized void storeTokens(JsonObject o) {
        accessToken = o.get("access_token").getAsString();
        long secs = o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600;
        accessExpiry = System.currentTimeMillis() + secs * 1000L;
        if (o.has("refresh_token") && !o.get("refresh_token").isJsonNull()) {
            Config.I.spotifyRefresh = o.get("refresh_token").getAsString();
            Config.save();
        }
    }

    /** True if there is a usable access token afterwards. */
    private static boolean ensureToken() throws IOException {
        if (!accessToken.isEmpty() && System.currentTimeMillis() < accessExpiry - 30_000L) return true;
        Http.Resp r = Http.request("POST", ACCOUNTS + "/api/token", Http.form(
            "grant_type", "refresh_token", "refresh_token", token(), "client_id", clientId()), null);
        if (r.code() == 200) {
            storeTokens(JsonParser.parseString(r.body()).getAsJsonObject());
            return true;
        }
        if (r.code() == 400 || r.code() == 401) {
            disconnect("Spotify login expired - press Connect again");
            return false;
        }
        throw new IOException("token HTTP " + r.code());
    }

    // ------------------------------------------------------------------ polling

    private static volatile Thread pollThread;

    public static synchronized void startPolling() {
        if (pollThread != null && pollThread.isAlive()) return;
        Thread t = new Thread(Spotify::pollLoop, "tensku-spotify");
        t.setDaemon(true);
        pollThread = t;
        t.start();
    }

    private static synchronized void stopPolling() {
        Thread t = pollThread;
        pollThread = null;
        if (t != null) t.interrupt();
    }

    private static void pollLoop() {
        Thread me = Thread.currentThread();
        while (pollThread == me) {
            long wait = 2000;
            try {
                if (token().isEmpty()) break;
                if (Config.I.spotifyCard) wait = pollOnce();
            } catch (IOException e) {
                if (status != Status.DISCONNECTED) fail("Can't reach Spotify - are you online?");
                wait = 8000;
            } catch (Exception e) {
                wait = 8000;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /** One "what is playing" request. Returns how long to wait before the next one. */
    private static long pollOnce() throws IOException {
        if (!ensureToken()) return 3000;
        Http.Resp r = Http.request("GET", API + "/v1/me/player/currently-playing?additional_types=episode", null, accessToken);
        switch (r.code()) {
            case 200 -> {
                status = Status.CONNECTED;
                message = "";
                handle(r.body());
                return 3000;
            }
            case 204 -> {
                status = Status.CONNECTED;
                message = "";
                Now n = now;
                if (n != null && n.playing()) now = new Now(n.key(), n.title(), n.artist(), n.album(), n.durationMs(),
                    n.imageUrl(), n.progress(System.currentTimeMillis()), System.currentTimeMillis(), false, n.ad());
                return 4000;
            }
            case 401 -> {
                accessToken = "";
                return 500;
            }
            case 429 -> {
                fail("Spotify says slow down - retrying");
                return Math.max(3000, Math.min(60_000, r.retryAfterSec() * 1000L));
            }
            case 403 -> {
                fail("Spotify refused (403): add your account under User Management in your Spotify app");
                return 30_000;
            }
            default -> {
                fail("Spotify error " + r.code());
                return 8000;
            }
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static void handle(String body) {
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        long nowMs = System.currentTimeMillis();
        boolean playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();
        long progress = o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0;
        String type = str(o, "currently_playing_type");
        JsonElement itEl = o.get("item");
        if (itEl == null || itEl.isJsonNull()) {
            if ("ad".equals(type)) {
                now = new Now("ad", "Advertisement", "Spotify", "", 0, null, progress, nowMs, playing, true);
                if (playing) lastPlayingAt = nowMs;
            }
            return;
        }
        JsonObject it = itEl.getAsJsonObject();
        String title = str(it, "name");
        long duration = it.has("duration_ms") && !it.get("duration_ms").isJsonNull() ? it.get("duration_ms").getAsLong() : 0;
        String artist = "";
        JsonArray arr = it.has("artists") && it.get("artists").isJsonArray() ? it.getAsJsonArray("artists") : null;
        if (arr != null) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement a : arr) {
                String n = str(a.getAsJsonObject(), "name");
                if (n != null) sb.append(sb.length() > 0 ? ", " : "").append(n);
            }
            artist = sb.toString();
        }
        JsonObject album = it.has("album") && it.get("album").isJsonObject() ? it.getAsJsonObject("album") : null;
        JsonObject show = it.has("show") && it.get("show").isJsonObject() ? it.getAsJsonObject("show") : null;
        String albumName = album != null ? str(album, "name") : (show != null ? str(show, "name") : "");
        if (artist.isEmpty() && show != null) artist = str(show, "publisher") == null ? "" : str(show, "publisher");
        String image = pickImage(album != null ? album : (show != null ? show : it));
        String id = str(it, "id");
        if (title == null) title = "Unknown";
        String key = id != null ? id : title + "|" + artist;

        Now prev = now;
        now = new Now(key, title, artist, albumName == null ? "" : albumName, duration, image, progress, nowMs, playing, false);
        if (playing) lastPlayingAt = nowMs;

        if (prev == null || !prev.key().equals(key)) onNewTrack(key, title, artist, albumName, duration, image);
    }

    /** Smallest cover that is still at least ~100px wide. */
    private static String pickImage(JsonObject holder) {
        if (holder == null || !holder.has("images") || !holder.get("images").isJsonArray()) return null;
        String best = null;
        int bestW = Integer.MAX_VALUE;
        String any = null;
        for (JsonElement e : holder.getAsJsonArray("images")) {
            JsonObject im = e.getAsJsonObject();
            String url = str(im, "url");
            if (url == null) continue;
            any = url;
            int w = im.has("width") && !im.get("width").isJsonNull() ? im.get("width").getAsInt() : 0;
            if (w >= 100 && w < bestW) { bestW = w; best = url; }
        }
        return best != null ? best : any;
    }

    private static void onNewTrack(String key, String title, String artist, String album, long duration, String image) {
        art = null;
        fetchLyrics(key, title, artist, album, duration);
        if (image != null) {
            Thread t = new Thread(() -> {
                try {
                    byte[] raw = Http.bytes(image);
                    BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
                    if (src == null) return;
                    BufferedImage dst = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
                    var g2 = dst.createGraphics();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(src, 0, 0, 96, 96, null);
                    g2.dispose();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(dst, "png", bos);
                    Now n = now;
                    if (n != null && n.key().equals(key)) art = new Art(key, bos.toByteArray());
                } catch (Exception ignored) { }
            }, "tensku-spotify-art");
            t.setDaemon(true);
            t.start();
        }
    }

    private static void fetchLyrics(String key, String title, String artist, String album, long duration) {
        if (!Config.I.spotifyLyrics) {
            lyrics = Lyrics.none(key);
            return;
        }
        lyrics = Lyrics.loading(key);
        Thread t = new Thread(() -> {
            Lyrics l = Lyrics.fetch(key, title, artist, album, duration);
            Now n = now;
            if (n != null && n.key().equals(key)) lyrics = l;
        }, "tensku-lyrics");
        t.setDaemon(true);
        t.start();
    }

    /** Look the lyrics up again for the current song (used when lyrics are switched on mid-song). */
    public static void reloadLyrics() {
        Now n = now;
        if (n != null && !n.ad()) fetchLyrics(n.key(), n.title(), n.artist(), n.album(), n.durationMs());
    }

    // ------------------------------------------------------------------ browser

    /** Opens a link in the default browser (no Minecraft API needed). */
    public static void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            else if (os.contains("mac")) pb = new ProcessBuilder("open", url);
            else pb = new ProcessBuilder("xdg-open", url);
            pb.start();
        } catch (Exception e) {
            saveLoginLink(url);
            throw new RuntimeException(e);
        }
    }

    private static void saveLoginLink(String url) {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("tenskugamblehud");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("spotify-login.txt"), url);
            message = "Couldn't open the browser - open config/tenskugamblehud/spotify-login.txt and paste the link into your browser";
        } catch (Exception ignored) {
            message = "Couldn't open the browser";
        }
    }
}

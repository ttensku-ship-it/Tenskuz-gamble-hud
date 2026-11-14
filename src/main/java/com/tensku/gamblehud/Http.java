package com.tensku.gamblehud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Tiny blocking HTTP helper (plain HttpURLConnection, so it works on any Java runtime). Never call on the render thread. */
final class Http {
    private Http() {}

    record Resp(int code, String body, int retryAfterSec) {}

    static final String UA = "TenskuClient/1.0 (Minecraft Fabric mod; https://github.com/ttensku-ship-it)";

    static Resp request(String method, String url, String formBody, String bearer) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json");
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            if (formBody != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                byte[] b = formBody.getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(b.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(b);
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int retry = 0;
            String ra = c.getHeaderField("Retry-After");
            if (ra != null) {
                try { retry = Integer.parseInt(ra.trim()); } catch (NumberFormatException ignored) { }
            }
            return new Resp(code, text, retry);
        } finally {
            c.disconnect();
        }
    }

    static byte[] bytes(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", UA);
            if (c.getResponseCode() >= 400) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                return in.readAllBytes();
            }
        } finally {
            c.disconnect();
        }
    }

    static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    /** key, value, key, value ... -> "k=v&k=v" (url-encoded). */
    static String form(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(kv[i])).append('=').append(enc(kv[i + 1]));
        }
        return sb.toString();
    }
}

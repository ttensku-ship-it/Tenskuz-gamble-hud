package com.tensku.gamblehud;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads PNG files from config/tenskugamblehud/ as textures and draws them. Also finds vanilla item icons. */
public final class CustomImage {
    private CustomImage() {}

    public record Tex(Identifier id, int w, int h) {}

    private static final Map<String, Tex> CACHE = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final Map<String, Tex> ICONS = new HashMap<>();
    public static String lastError = "";

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("tenskugamblehud");
    }

    private static String safe(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    /** Returns the loaded image, or null (see lastError). Must be called on the render thread. */
    public static Tex get(String file) {
        if (file == null || file.isBlank()) return null;
        final String key = file.trim();
        Tex cached = CACHE.get(key);
        if (cached != null) return cached;
        if (FAILED.contains(key)) return null;
        try {
            Path dir = dir().toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path p = dir.resolve(key).normalize();
            if (!p.startsWith(dir)) throw new IllegalArgumentException("Bad file name");
            if (!Files.isRegularFile(p)) throw new IllegalArgumentException("File not found: " + key);
            NativeImage img;
            try (InputStream in = Files.newInputStream(p)) {
                img = NativeImage.read(in);
            }
            Identifier id = Identifier.fromNamespaceAndPath(TenskuGambleHud.MOD_ID, "custom/" + safe(key));
            DynamicTexture tex = new DynamicTexture(() -> "tenskugamblehud " + key, img);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            Tex t = new Tex(id, img.getWidth(), img.getHeight());
            CACHE.put(key, t);
            lastError = "";
            return t;
        } catch (Exception e) {
            FAILED.add(key);
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            return null;
        }
    }

    /** Forget a file so it is read again (after you replaced the PNG). */
    public static void reload(String file) {
        if (file == null) return;
        String key = file.trim();
        CACHE.remove(key);
        FAILED.remove(key);
        lastError = "";
    }

    /** Vanilla item/block texture from the item name, e.g. "Diamond Sword" -> textures/item/diamond_sword.png. */
    public static Tex vanillaIcon(String name) {
        if (name == null) return null;
        String id = name.toLowerCase(Locale.ROOT).replace("minecraft:", "").trim()
            .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (id.isEmpty()) return null;
        if (ICONS.containsKey(id)) return ICONS.get(id);
        Tex found = null;
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            for (String folder : new String[] {"item", "block"}) {
                Identifier tid = Identifier.withDefaultNamespace("textures/" + folder + "/" + id + ".png");
                if (rm.getResource(tid).isPresent()) {
                    found = new Tex(tid, 16, 16);
                    break;
                }
            }
        } catch (Exception ignored) { }
        ICONS.put(id, found);
        return found;
    }

    /** Picture for an auction: the custom image if one is set and loads, else the vanilla icon for the name. */
    public static Tex auctionIcon(String itemName, String imageFile) {
        Tex t = get(imageFile);
        return t != null ? t : vanillaIcon(itemName);
    }

    /** Fill the rectangle with the image, cropping the edges so nothing is stretched. */
    public static void cover(GuiGraphicsExtractor g, Tex t, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        int rw = t.w(), rh = t.h();
        if (rw * h > rh * w) rw = Math.max(1, rh * w / h);
        else rh = Math.max(1, rw * h / w);
        int u = (t.w() - rw) / 2, v = (t.h() - rh) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, u, v, w, h, rw, rh, t.w(), t.h());
    }

    /** Draw the whole image inside a size x size square, keeping its shape. */
    public static void fit(GuiGraphicsExtractor g, Tex t, int x, int y, int size) {
        int dw = size, dh = size;
        if (t.w() >= t.h()) dh = Math.max(1, size * t.h() / t.w());
        else dw = Math.max(1, size * t.w() / t.h());
        int dx = x + (size - dw) / 2, dy = y + (size - dh) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), dx, dy, 0, 0, dw, dh, t.w(), t.h(), t.w(), t.h());
    }
}

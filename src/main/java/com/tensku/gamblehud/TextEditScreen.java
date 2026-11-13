package com.tensku.gamblehud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Add / edit / move custom on-screen text. Click a text to pick it up, click again to drop it.
 * The panel at the bottom changes the text, font style, size, B/I/U/S and the two colours.
 */
public class TextEditScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private EditBox box;
    private int sel = -1;
    private boolean grabbed;
    private int offX, offY;
    private boolean syncing;

    private static final int PW = 360, PH = 108;
    private int px, py;

    public TextEditScreen() {
        super(Component.literal("On-screen text"));
    }

    private List<TextItem> list() {
        if (Config.I.texts == null) Config.I.texts = new ArrayList<>();
        return Config.I.texts;
    }

    private TextItem cur() {
        List<TextItem> l = list();
        return sel >= 0 && sel < l.size() ? l.get(sel) : null;
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = height - PH - 6;
        box = new EditBox(font, px + 8, py + 8, 220, 18, Component.literal("text"));
        box.setMaxLength(60);
        box.setResponder(v -> {
            if (syncing) return;
            TextItem it = cur();
            if (it != null) it.text = v;
        });
        addRenderableWidget(box);
        if (sel < 0 && !list().isEmpty()) sel = 0;
        syncBox();
    }

    private void syncBox() {
        TextItem it = cur();
        syncing = true;
        box.setValue(it == null ? "" : (it.text == null ? "" : it.text));
        syncing = false;
        box.setVisible(it != null);
    }

    private void add() {
        TextItem it = new TextItem();
        it.y = Math.min(0.8f, 0.25f + 0.08f * list().size());
        list().add(it);
        sel = list().size() - 1;
        grabbed = false;
        syncBox();
        Config.save();
    }

    private void delete() {
        if (cur() == null) return;
        list().remove(sel);
        sel = list().isEmpty() ? -1 : Math.min(sel, list().size() - 1);
        grabbed = false;
        syncBox();
        Config.save();
    }

    private void select(int delta) {
        int n = list().size();
        if (n == 0) return;
        sel = ((sel < 0 ? 0 : sel) + delta + n) % n;
        grabbed = false;
        syncBox();
    }

    // ------------------------------------------------------------------ drawing helpers

    private void btn(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, int mx, int my, boolean on, Runnable r) {
        Theme th = Theme.current();
        boolean hot = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, on ? th.accent() : (hot ? 0xFF2A3045 : 0xCC151824));
        g.fill(x, y, x + w, y + 1, on ? 0xFFFFFFFF : th.accent());
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2 + 1, on ? 0xFF10121A : 0xFFE8E8E8, !on);
        hits.add(new Hit(x, y, w, h, r));
    }

    private void swatches(GuiGraphicsExtractor g, int x, int y, int mx, int my, boolean fill, TextItem it) {
        for (int i = 0; i < TextFx.PALETTE.length; i++) {
            final int rgb = TextFx.PALETTE[i];
            int sx = x + i * 14;
            boolean chosen = ((fill ? it.color : it.outline) & 0xFFFFFF) == rgb;
            g.fill(sx - 1, y - 1, sx + 13, y + 13, chosen ? 0xFFFFFFFF : 0xFF000000);
            g.fill(sx, y, sx + 12, y + 12, 0xFF000000 | rgb);
            hits.add(new Hit(sx, y, 12, 12, () -> { if (fill) it.color = rgb; else it.outline = rgb; Config.save(); }));
        }
    }

    private static boolean in(int[] r, int mx, int my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        Theme th = Theme.current();
        long now = System.currentTimeMillis();
        g.fill(0, 0, width, height, 0x55000000);

        // a text that is picked up follows the mouse
        TextItem c = cur();
        if (grabbed && c != null) {
            int[] r = TextFx.rect(font, c, width, height);
            int nx = Math.max(0, Math.min(width - r[2], mouseX - offX));
            int ny = Math.max(0, Math.min(height - r[3], mouseY - offY));
            c.x = Hud.frac(nx, r[2], width);
            c.y = Hud.frac(ny, r[3], height);
        }

        // the texts themselves
        List<TextItem> l = list();
        for (int i = 0; i < l.size(); i++) {
            TextItem it = l.get(i);
            int[] r = TextFx.rect(font, it, width, height);
            if (it.visible) TextFx.draw(g, font, it, r[0], r[1], now);
            else g.text(font, "(hidden) " + (it.text == null ? "" : it.text), r[0], r[1], 0x88FFFFFF, true);
            if (i == sel) {
                int col = grabbed ? 0xFFFFFFFF : th.accent();
                g.outline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, col);
            }
        }
        if (l.isEmpty()) {
            String m = "No text yet - press Add";
            g.text(font, m, (width - font.width(m)) / 2, height / 3, 0xFFFFFFFF, true);
        }
        String help = grabbed ? "Click to drop it here" : "Click a text to pick it up, click again to drop it";
        g.text(font, help, (width - font.width(help)) / 2, 8, 0xFFFFFFFF, true);

        // control panel
        g.fill(px - 2, py - 2, px + PW + 2, py + PH + 2, 0xFF000000 | (th.accent() & 0xFFFFFF));
        g.fillGradient(px, py, px + PW, py + PH, 0xF0181C2A, 0xF00A0C14);

        // row 1: text box + add / delete / done
        btn(g, "Add", px + 234, py + 8, 38, 18, mouseX, mouseY, false, this::add);
        btn(g, "Del", px + 276, py + 8, 34, 18, mouseX, mouseY, false, this::delete);
        btn(g, "Done", px + 314, py + 8, 38, 18, mouseX, mouseY, false, this::onClose);

        if (c == null) {
            String m = "Add a text to start editing it.";
            g.text(font, m, px + 12, py + 44, 0xFFB9BDCB, true);
        } else {
            // row 2: font, size, B I U S, visible
            int y2 = py + 32;
            btn(g, "<", px + 8, y2, 16, 18, mouseX, mouseY, false, () -> { c.font = (c.font + TextFx.FONTS.length - 1) % TextFx.FONTS.length; Config.save(); });
            String fn = TextFx.FONTS[Math.max(0, Math.min(TextFx.FONTS.length - 1, c.font))];
            g.fill(px + 26, y2, px + 118, y2 + 18, 0xCC10121A);
            g.text(font, fn, px + 72 - font.width(fn) / 2, y2 + 6, 0xFFFFFFFF, true);
            btn(g, ">", px + 120, y2, 16, 18, mouseX, mouseY, false, () -> { c.font = (c.font + 1) % TextFx.FONTS.length; Config.save(); });

            btn(g, "-", px + 146, y2, 16, 18, mouseX, mouseY, false, () -> { c.scale = Math.max(0.5f, Math.round((c.scale - 0.25f) * 4f) / 4f); Config.save(); });
            String sz = "x" + (Math.round(c.scale * 100) / 100f);
            g.fill(px + 164, y2, px + 200, y2 + 18, 0xCC10121A);
            g.text(font, sz, px + 182 - font.width(sz) / 2, y2 + 6, 0xFFFFFFFF, true);
            btn(g, "+", px + 202, y2, 16, 18, mouseX, mouseY, false, () -> { c.scale = Math.min(8f, Math.round((c.scale + 0.25f) * 4f) / 4f); Config.save(); });

            btn(g, "B", px + 228, y2, 20, 18, mouseX, mouseY, c.bold, () -> { c.bold = !c.bold; Config.save(); });
            btn(g, "I", px + 250, y2, 20, 18, mouseX, mouseY, c.italic, () -> { c.italic = !c.italic; Config.save(); });
            btn(g, "U", px + 272, y2, 20, 18, mouseX, mouseY, c.underline, () -> { c.underline = !c.underline; Config.save(); });
            btn(g, "S", px + 294, y2, 20, 18, mouseX, mouseY, c.strike, () -> { c.strike = !c.strike; Config.save(); });
            btn(g, c.visible ? "Shown" : "Hidden", px + 316, y2, 36, 18, mouseX, mouseY, c.visible, () -> { c.visible = !c.visible; Config.save(); });

            // row 3: fill colours + prev / next text
            int y3 = py + 56;
            g.text(font, "Fill", px + 8, y3 + 3, 0xFFB9BDCB, true);
            swatches(g, px + 34, y3, mouseX, mouseY, true, c);
            btn(g, "<", px + 216, y3 - 1, 16, 14, mouseX, mouseY, false, () -> select(-1));
            String ix = (sel + 1) + " / " + l.size();
            g.text(font, ix, px + 278 - font.width(ix) / 2, y3 + 3, 0xFFFFFFFF, true);
            btn(g, ">", px + 324, y3 - 1, 16, 14, mouseX, mouseY, false, () -> select(1));

            // row 4: outline colours
            int y4 = py + 74;
            g.text(font, "Edge", px + 8, y4 + 3, 0xFFB9BDCB, true);
            swatches(g, px + 34, y4, mouseX, mouseY, false, c);

            String tip = "Edge = outline / glow / shadow colour";
            g.text(font, tip, px + 8, py + 94, 0xFF6A708A, true);
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                Ui.click();
                h.action().run();
                return true;
            }
        }
        if (super.mouseClicked(event, doubleClick)) return true; // the text box
        // clicks on the panel never reach the texts behind it
        if (mx >= px - 2 && mx < px + PW + 2 && my >= py - 2 && my < py + PH + 2) return true;

        if (grabbed) {
            Ui.click();
            grabbed = false;
            Config.save();
            return true;
        }
        List<TextItem> l = list();
        for (int i = l.size() - 1; i >= 0; i--) {
            int[] r = TextFx.rect(font, l.get(i), width, height);
            if (in(r, mx, my)) {
                Ui.click();
                sel = i;
                syncBox();
                grabbed = true;
                offX = mx - r[0];
                offY = my - r[1];
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }
}

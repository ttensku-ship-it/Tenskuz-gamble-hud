package com.tensku.gamblehud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Pick the auction item from a grid of icons (with search) instead of typing the name. */
public class ItemPickerScreen extends Screen {
    private static final int W = 300, H = 270;
    private static final int COLS = 9, ROWS = 5, CELL = 28;

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private int px, py;
    private EditBox search;
    private int page = 0;
    private String lastQuery = "";
    private List<String> shown = ItemCatalog.NAMES;
    private String hover = "";

    public ItemPickerScreen() {
        super(Component.literal("Pick an item"));
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        search = new EditBox(font, px + 12, py + 34, W - 24, 18, Component.literal(""));
        search.setMaxLength(30);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.gui.setScreen(new AuctionScreen()))
            .bounds(px + 12, py + H - 34, 70, 20).build());
    }

    private int pages() {
        return Math.max(1, (shown.size() + COLS * ROWS - 1) / (COLS * ROWS));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        Theme th = Theme.current();

        String q = search.getValue();
        if (!q.equals(lastQuery)) {
            lastQuery = q;
            shown = ItemCatalog.search(q);
            page = 0;
        }
        page = Math.max(0, Math.min(pages() - 1, page));

        g.fill(0, 0, width, height, 0x99000000);
        Ui.panel(g, th, px, py, W, H);
        g.fill(px + 2, py + 2, px + W - 2, py + 26, 0xAA0E0F16);
        g.text(font, TenskuGambleHud.TITLE + " - PICK ITEM", px + 14, py + 10, th.accent(), true);

        int gx = px + (W - COLS * CELL) / 2, gy = py + 60;
        hover = "";
        int start = page * COLS * ROWS;
        for (int n = 0; n < COLS * ROWS; n++) {
            int idx = start + n;
            int cx = gx + (n % COLS) * CELL, cy = gy + (n / COLS) * CELL;
            boolean has = idx < shown.size();
            boolean over = has && mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2;
            g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, over ? Theme.alpha(th.accent() & 0xFFFFFF, 0.55f) : 0xAA10121A);
            if (!has) continue;
            final String name = shown.get(idx);
            CustomImage.Tex t = CustomImage.vanillaIcon(name);
            if (t != null) {
                CustomImage.fit(g, t, cx + 3, cy + 3, CELL - 8);
            } else {
                String ch = name.substring(0, 1);
                g.text(font, ch, cx + (CELL - 2 - font.width(ch)) / 2, cy + (CELL - 2 - 8) / 2, 0xFF9AA0B5, true);
            }
            if (over) hover = name;
            hits.add(new Hit(cx, cy, CELL - 2, CELL - 2, () -> {
                AuctionScreen.draftItem = name;
                minecraft.gui.setScreen(new AuctionScreen());
            }));
        }
        if (shown.isEmpty()) g.text(font, "No items match. Go back and type any name.", px + 14, gy + 4, 0xFFB9BDCB, true);

        // pager
        int py2 = py + H - 34;
        final int pc = pages();
        g.fill(px + W - 118, py2, px + W - 94, py2 + 20, 0xCC151824);
        g.text(font, "<", px + W - 118 + 9, py2 + 6, 0xFFE8E8E8, true);
        hits.add(new Hit(px + W - 118, py2, 24, 20, () -> page = (page + pc - 1) % pc));
        g.fill(px + W - 38, py2, px + W - 14, py2 + 20, 0xCC151824);
        g.text(font, ">", px + W - 38 + 9, py2 + 6, 0xFFE8E8E8, true);
        hits.add(new Hit(px + W - 38, py2, 24, 20, () -> page = (page + 1) % pc));
        String pg = (page + 1) + "/" + pc;
        g.text(font, pg, px + W - 66 - font.width(pg) / 2, py2 + 6, 0xFFE8E8E8, true);

        String hint = hover.isEmpty() ? "Click an item to pick it" : hover;
        g.text(font, hint, px + 90, py2 + 6, hover.isEmpty() ? 0xFF9AA0B5 : 0xFFFFFFFF, true);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                Ui.click();
                h.action().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}

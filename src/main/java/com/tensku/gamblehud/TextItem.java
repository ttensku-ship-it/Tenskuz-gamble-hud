package com.tensku.gamblehud;

/** One piece of custom on-screen text (saved in the config). */
public class TextItem {
    public String text = "Your text";
    /** Position as a 0..1 fraction of the free screen space (same scheme as the HUD cards). */
    public float x = 0.5f, y = 0.4f;
    public float scale = 2f;
    public int font = TextFx.BUBBLE;
    public int color = 0xFFFFFF;     // fill colour (RGB)
    public int outline = 0xFF4DB8;   // outline / glow / shadow colour (RGB)
    public boolean bold, italic, underline, strike;
    public boolean visible = true;

    public TextItem() {}
}

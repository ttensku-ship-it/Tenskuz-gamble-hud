package com.tensku.gamblehud;

/** Colour themes (ARGB). Chat colour is the RGB used to tint chat messages. */
public record Theme(String name, int top, int bottom, int hill, int accent, int chat) {

    public static final Theme[] ALL = {
        new Theme("Winter",    0xFF1B2A44, 0xFF3C5E86, 0xFF8FB4D6, 0xFF9AD1FF, 0x9AD1FF),
        new Theme("Aurora",    0xFF0B2A33, 0xFF1D7A6B, 0xFF2BB59A, 0xFF52F2C8, 0x52F2C8),
        new Theme("Galaxy",    0xFF1A0B33, 0xFF4B1D8C, 0xFF7B3CD6, 0xFFC08BFF, 0xC08BFF),
        new Theme("Desert",    0xFF2A1608, 0xFFD9822B, 0xFF3A2410, 0xFFFFB347, 0xFFB347),
        new Theme("Aesthetic", 0xFF8A4E8F, 0xFFF08FB0, 0xFFB0587F, 0xFFFFA6D5, 0xFFA6D5),
        new Theme("Volcano",   0xFF1C0805, 0xFF8A1E0F, 0xFF2A0F0A, 0xFFFF5A36, 0xFF5A36),
        new Theme("Space",     0xFF05060F, 0xFF16264F, 0xFF0B1230, 0xFF5B7CFF, 0x5B7CFF),
        // basic colour themes
        new Theme("Red",       0xFF2A0709, 0xFFB3141F, 0xFF4A0A10, 0xFFFF4D5A, 0xFF4D5A),
        new Theme("Orange",    0xFF2E1405, 0xFFE8681A, 0xFF4A210A, 0xFFFF9A47, 0xFF9A47),
        new Theme("Yellow",    0xFF2E2905, 0xFFE8C81A, 0xFF4A430A, 0xFFFFE347, 0xFFE347),
        new Theme("Green",     0xFF062A10, 0xFF1FA84A, 0xFF0B4A1D, 0xFF4DFF85, 0x4DFF85),
        new Theme("Blue",      0xFF081A33, 0xFF1F64D6, 0xFF0B2F66, 0xFF5AA3FF, 0x5AA3FF),
        new Theme("Purple",    0xFF1E0833, 0xFF8A2BD6, 0xFF3A0F66, 0xFFB86BFF, 0xB86BFF),
        new Theme("Pink",      0xFF33081F, 0xFFE8509A, 0xFF661040, 0xFFFF8CC6, 0xFF8CC6),
        new Theme("Teal",      0xFF05262B, 0xFF1AA6B3, 0xFF0A4A52, 0xFF4DE8F2, 0x4DE8F2),
        new Theme("Gold",      0xFF2B2105, 0xFFC9961A, 0xFF4A380A, 0xFFFFD24D, 0xFFD24D),
        new Theme("Black",     0xFF000000, 0xFF1A1A1F, 0xFF0A0A0D, 0xFFE0E0E0, 0xE0E0E0),
        new Theme("White",     0xFFD8DCE6, 0xFFFFFFFF, 0xFFB8BECC, 0xFF6C7A99, 0x6C7A99),
        new Theme("Gray",      0xFF22252B, 0xFF5A606E, 0xFF30343C, 0xFFB0B6C4, 0xB0B6C4),
        new Theme("Mint",      0xFF0B2B22, 0xFF6FD9B0, 0xFF2E8A6B, 0xFFA8FFDC, 0xA8FFDC),
        new Theme("Sunset",    0xFF2B0B33, 0xFFFF7A4D, 0xFF5A1F4A, 0xFFFFB06B, 0xFFB06B),
    };

    public static Theme current() {
        int i = Config.I.theme;
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }

    public static int alpha(int rgb, float a) {
        int al = Math.max(0, Math.min(255, (int) (a * 255f)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }
}

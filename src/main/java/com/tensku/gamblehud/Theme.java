package com.tensku.gamblehud;

/** Colour themes (ARGB). Chat colour is the RGB used to tint chat messages. */
public record Theme(String name, int top, int bottom, int hill, int accent, int chat, int scene) {

    /** Plain gradient theme (no animated scenery). */
    public Theme(String name, int top, int bottom, int hill, int accent, int chat) {
        this(name, top, bottom, hill, accent, chat, 0);
    }

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
        // nature + sky + galaxy
        new Theme("Forest",    0xFF07200F, 0xFF1F6B2E, 0xFF0F3D1A, 0xFF7BE27F, 0x7BE27F),
        new Theme("Ocean",     0xFF03203A, 0xFF1478B8, 0xFF0A4A78, 0xFF5CD0FF, 0x5CD0FF),
        new Theme("Sunrise",   0xFF3A1A4A, 0xFFFF9E5E, 0xFFB5527A, 0xFFFFD08A, 0xFFD08A),
        new Theme("Twilight",  0xFF14123A, 0xFF8A4F9E, 0xFF3A2466, 0xFFE59AD8, 0xE59AD8),
        new Theme("Nebula",    0xFF12062E, 0xFF5A2A8F, 0xFF8A2A6E, 0xFFFF6FD8, 0xFF6FD8),
        new Theme("Cosmic",    0xFF020410, 0xFF2A1A5E, 0xFF10082A, 0xFF7AF0FF, 0x7AF0FF),
        new Theme("Meadow",    0xFF1B3A1A, 0xFF9ED860, 0xFF4F8A2E, 0xFFE8FF7A, 0xE8FF7A),
        new Theme("Blossom",   0xFF3A1A2A, 0xFFF7B6CF, 0xFFC96A93, 0xFFFFD1E3, 0xFFD1E3),
        new Theme("Storm",     0xFF14181F, 0xFF4A5566, 0xFF232A35, 0xFF9AB4D6, 0x9AB4D6),
        new Theme("Autumn",    0xFF2A1006, 0xFFA83A14, 0xFF5A2208, 0xFFFF8A3D, 0xFF8A3D),
        new Theme("Moonlight", 0xFF0A1226, 0xFF3A4E7A, 0xFF1A2646, 0xFFDDE6FF, 0xDDE6FF),
        new Theme("Coral Reef",0xFF052B3A, 0xFFFF7A6B, 0xFF1A7A8A, 0xFFFFB3A6, 0xFFB3A6),
        new Theme("Tundra",    0xFF1A2A3A, 0xFFCFE3F2, 0xFF8AA8BF, 0xFFFFFFFF, 0xFFFFFF),
        new Theme("Rainforest",0xFF04260F, 0xFF0F8A4A, 0xFF083A22, 0xFF3DFFB0, 0x3DFFB0),
        new Theme("Starry Night",0xFF050A24, 0xFF1F3A8A, 0xFF0A1440, 0xFFFFE066, 0xFFE066),
        new Theme("Dusk",      0xFF1A0F2E, 0xFFD6603A, 0xFF3A1A4A, 0xFFFF9A6B, 0xFF9A6B),
        // cinematic: animated scenes with one big tree
        new Theme("Sakura",    0xFF3A1F5C, 0xFFFFB36B, 0xFF35432F, 0xFFFF9EC8, 0xFF9EC8, Scenery.SAKURA),
        new Theme("Maple",     0xFF3A1A12, 0xFFE8A04A, 0xFF4A2A14, 0xFFFFB347, 0xFFB347, Scenery.MAPLE),
        new Theme("Snowpine",  0xFF0A1230, 0xFF3A5E8A, 0xFFDCE8F5, 0xFFBFE6FF, 0xBFE6FF, Scenery.PINE),
        new Theme("Savanna",   0xFF2B1040, 0xFFFF8A3D, 0xFF1A0E12, 0xFFFFC857, 0xFFC857, Scenery.ACACIA),
        new Theme("Moonbloom", 0xFF0B0F2E, 0xFF4B3A8A, 0xFF141C3A, 0xFFE59AD8, 0xE59AD8, Scenery.MOONBLOOM),
        new Theme("Palms",     0xFF2A1B5C, 0xFFFFC27A, 0xFFE8C98B, 0xFFFFD08A, 0xFFD08A, Scenery.PALM),
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

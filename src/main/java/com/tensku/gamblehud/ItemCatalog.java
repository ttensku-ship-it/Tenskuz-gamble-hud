package com.tensku.gamblehud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Items offered by the auction item picker. Names turn into icons via CustomImage.vanillaIcon. */
public final class ItemCatalog {
    private ItemCatalog() {}

    private static final String IDS = String.join(",",
        // gear
        "netherite_sword", "netherite_axe", "netherite_pickaxe", "netherite_shovel", "netherite_hoe",
        "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
        "diamond_sword", "diamond_axe", "diamond_pickaxe", "diamond_shovel", "diamond_hoe",
        "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
        "golden_sword", "golden_axe", "golden_pickaxe", "golden_helmet", "golden_chestplate",
        "iron_sword", "iron_axe", "iron_pickaxe", "iron_helmet", "iron_chestplate",
        "bow", "crossbow", "trident", "mace", "shield", "totem_of_undying", "elytra", "turtle_helmet",
        "fishing_rod", "flint_and_steel", "shears", "spyglass", "brush", "compass", "clock",
        // valuables
        "netherite_ingot", "netherite_scrap", "ancient_debris", "diamond", "emerald", "gold_ingot", "iron_ingot",
        "copper_ingot", "amethyst_shard", "lapis_lazuli", "redstone", "quartz", "coal", "charcoal",
        "diamond_block", "emerald_block", "gold_block", "iron_block", "netherite_block", "lapis_block",
        "redstone_block", "copper_block", "coal_block", "amethyst_block", "beacon", "conduit",
        "nether_star", "dragon_egg", "dragon_head", "heart_of_the_sea", "nautilus_shell", "echo_shard",
        "enchanted_golden_apple", "golden_apple", "golden_carrot", "enchanted_book", "experience_bottle",
        "name_tag", "saddle", "lead", "end_crystal", "ender_eye", "ender_pearl", "blaze_rod", "ghast_tear",
        "wither_skeleton_skull", "creeper_head", "zombie_head", "skeleton_skull", "player_head",
        // spawn / rare blocks
        "spawner", "trial_spawner", "vault", "ominous_trial_key", "trial_key", "heavy_core", "wind_charge",
        "sponge", "wet_sponge", "sea_lantern", "glowstone", "shroomlight", "respawn_anchor", "lodestone",
        "obsidian", "crying_obsidian", "bedrock", "command_block", "barrier", "light", "structure_block",
        // food
        "cooked_beef", "cooked_porkchop", "cooked_chicken", "bread", "cake", "cookie", "pumpkin_pie",
        "honey_bottle", "golden_carrot", "glistering_melon_slice", "potion", "splash_potion", "milk_bucket",
        // tools / utility
        "water_bucket", "lava_bucket", "tnt", "piston", "sticky_piston", "hopper", "dispenser", "dropper",
        "observer", "daylight_detector", "ender_chest", "chest", "shulker_box", "barrel", "anvil",
        "enchanting_table", "brewing_stand", "furnace", "blast_furnace", "smoker", "crafting_table",
        "firework_rocket", "map", "filled_map", "written_book", "book", "paper", "bundle",
        // music + decor
        "music_disc_13", "music_disc_cat", "music_disc_pigstep", "music_disc_otherside", "music_disc_5",
        "music_disc_relic", "music_disc_creator", "disc_fragment_5", "painting", "item_frame",
        "white_wool", "red_bed", "white_banner", "flower_pot", "jukebox", "note_block", "bell", "lantern",
        "soul_lantern", "campfire", "torch", "candle", "cake", "armor_stand", "totem_of_undying",
        // blocks
        "grass_block", "dirt", "stone", "cobblestone", "deepslate", "netherrack", "end_stone", "oak_log",
        "spruce_log", "cherry_log", "bamboo", "sand", "gravel", "glass", "ice", "packed_ice", "blue_ice",
        "snow_block", "clay", "terracotta", "prismarine", "purpur_block", "bone_block", "slime_block",
        "honey_block", "hay_block", "melon", "pumpkin", "carved_pumpkin", "jack_o_lantern", "cactus",
        // mob drops / misc
        "bone", "string", "feather", "leather", "rabbit_foot", "phantom_membrane", "gunpowder", "slime_ball",
        "magma_cream", "spider_eye", "fermented_spider_eye", "prismarine_shard", "prismarine_crystals",
        "shulker_shell", "scute", "armadillo_scute", "breeze_rod", "ink_sac", "glow_ink_sac",
        "egg", "snowball", "arrow", "spectral_arrow", "tipped_arrow", "wheat", "sugar_cane", "stick"
    );

    /** Item names like "Diamond Sword", de-duplicated, in catalog order. */
    public static final List<String> NAMES = build();

    private static List<String> build() {
        List<String> out = new ArrayList<>();
        for (String id : IDS.split(",")) {
            String name = pretty(id.trim());
            if (!out.contains(name)) out.add(name);
        }
        return out;
    }

    public static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return sb.toString();
    }

    /** Items whose name contains every word of the query. */
    public static List<String> search(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return NAMES;
        String[] words = q.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String n : NAMES) {
            String low = n.toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) if (!low.contains(w)) { all = false; break; }
            if (all) out.add(n);
        }
        return out;
    }
}

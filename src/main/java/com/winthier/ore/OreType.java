package com.winthier.ore;

import org.bukkit.Material;

public enum OreType {
    NONE,
    COAL_ORE,
    IRON_ORE,
    LAPIS_ORE,
    GOLD_ORE,
    DIAMOND_ORE,
    REDSTONE_ORE,
    DEBUG,
    ;

    Material getMaterial() {
        switch (this) {
        case NONE: return null;
        case COAL_ORE: return Material.COAL_ORE;
        case IRON_ORE: return Material.IRON_ORE;
        case LAPIS_ORE: return Material.LAPIS_ORE;
        case GOLD_ORE: return Material.GOLD_ORE;
        case DIAMOND_ORE: return Material.DIAMOND_ORE;
        case REDSTONE_ORE: return Material.REDSTONE_ORE;
        default: return null;
        }
    }
}

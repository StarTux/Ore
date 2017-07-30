package com.winthier.ore;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.material.MaterialData;

@Getter
public enum OreType {
    NONE,
    COAL(Material.COAL_ORE),
    IRON(Material.IRON_ORE),
    LAPIS(Material.LAPIS_ORE),
    GOLD(Material.GOLD_ORE),
    EMERALD(Material.EMERALD_ORE),
    DIAMOND(Material.DIAMOND_ORE),
    REDSTONE(Material.REDSTONE_ORE),
    SLIME(Material.SLIME_BLOCK),
    SEA_LANTERN(Material.SEA_LANTERN),
    DEBUG,
    ;

    public final Material mat;
    public final int data;

    OreType() {
        this.mat = null;
        this.data = 0;
    }

    OreType(Material mat) {
        this.mat = mat;
        this.data = 0;
    }

    OreType(Material mat, int data) {
        this.mat = mat;
        this.data = data;
    }
}

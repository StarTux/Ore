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

    public final MaterialData materialData;

    OreType() {
        this.materialData = null;
    }

    OreType(Material material) {
        this.materialData = new MaterialData(material, (byte)0);
    }

    OreType(Material material, int data) {
        this.materialData = new MaterialData(material, (byte)data);
    }
}

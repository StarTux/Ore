package com.winthier.ore;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.material.MaterialData;

@Getter
public enum OreType {
    NONE,
    COAL_ORE(Material.COAL_ORE),
    IRON_ORE(Material.IRON_ORE),
    LAPIS_ORE(Material.LAPIS_ORE),
    GOLD_ORE(Material.GOLD_ORE),
    DIAMOND_ORE(Material.DIAMOND_ORE),
    REDSTONE_ORE(Material.REDSTONE_ORE),
    GRANITE(Material.STONE, 1),
    DIORITE(Material.STONE, 3),
    ANDESITE(Material.STONE, 5),
    CLAY(Material.CLAY),
    DEBUG,
    ;

    final MaterialData materialData;

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

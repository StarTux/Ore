package com.winthier.ore;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

@Getter
class OreChunk {
    final static int SIZE = 16;
    final int x, y, z;
    final Biome biome;
    final boolean slime;
    final OreType[] ores = new OreType[SIZE * SIZE * SIZE];
    long lastUse = System.currentTimeMillis();
    final List<Vec3> empties = new ArrayList<>();

    OreChunk(int x, int y, int z, Biome biome, boolean slime) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.biome = biome;
        this.slime = slime;
        int sy = y == 0 ? 5 : 0;
        for (int cy = sy; cy < 16; cy += 1) {
            for (int cz = 0; cz < 16; cz += 1) {
                for (int cx = 0; cx < 16; cx += 1) {
                    empties.add(new Vec3(cx, cy, cz));
                }
            }
        }
    }

    static OreChunk of(Block block) {
        return new OreChunk(block.getX() >> 4,
                            block.getY() >> 4,
                            block.getZ() >> 4,
                            block.getBiome(), block.getChunk().isSlimeChunk());
    }

    void set(int x, int y, int z, OreType ore) {
        ores[y * SIZE * SIZE + z * SIZE + x] = ore;
    }

    OreType get(int x, int y, int z) {
        OreType ore = ores[y * SIZE * SIZE + z * SIZE + x];
        if (ore == null) return OreType.NONE;
        return ore;
    }

    OreType at(Block block) {
        int x = block.getX() % SIZE;
        int y = block.getY() % SIZE;
        int z = block.getZ() % SIZE;
        if (x < 0) x = SIZE + x;
        if (y < 0) y = SIZE + y;
        if (z < 0) z = SIZE + z;
        return get(x, y, z);
    }

    void setIfEmpty(int x, int y, int z, OreType ore) {
        if (get(x, y, z) == OreType.NONE) {
            set(x, y, z, ore);
        }
    }

    int getBlockX() {
        return x * SIZE;
    }

    int getBlockY() {
        return y * SIZE;
    }

    int getBlockZ() {
        return z * SIZE;
    }

    ChunkCoordinate getCoordinate() {
        return new ChunkCoordinate(x, y, z);
    }

    void setUsed() {
        lastUse = System.currentTimeMillis();
    }

    boolean isTooOld() {
        long now = System.currentTimeMillis();
        return lastUse + 1000*60*10 < now; // 10 minutes
    }

    Block getBlock(World world) {
        return world.getBlockAt(x * SIZE, y * SIZE, z * SIZE);
    }
}

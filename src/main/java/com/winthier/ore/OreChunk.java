package com.winthier.ore;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

@Getter
@RequiredArgsConstructor
class OreChunk {
    final static int SIZE = 16;
    final int x, y, z;
    final Biome biome;
    final boolean slime;
    final OreType[] ores = new OreType[SIZE * SIZE * SIZE];
    long lastUse = System.currentTimeMillis();

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

package com.winthier.ore;

import lombok.Value;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

@Value
class ChunkCoordinate {
    int x, y, z;
    
    static ChunkCoordinate of(Block block) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        int rx = x < 0 ? (x + 1) / OreChunk.SIZE - 1 : x / OreChunk.SIZE;
        int ry = y < 0 ? (y + 1) / OreChunk.SIZE - 1 : y / OreChunk.SIZE;
        int rz = z < 0 ? (z + 1) / OreChunk.SIZE - 1 : z / OreChunk.SIZE;
        return new ChunkCoordinate(rx, ry, rz);
    }

    static ChunkCoordinate of(Location loc) {
        return of(loc.getBlock());
    }

    Block getBlock(World world) {
        return world.getBlockAt(x * OreChunk.SIZE, y * OreChunk.SIZE, z * OreChunk.SIZE);
    }

    int distanceSquared(ChunkCoordinate other) {
        int dx = other.x - x;
        int dy = other.y - y;
        int dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    ChunkCoordinate getRelative(int dx, int dy, int dz) {
        return new ChunkCoordinate(x + dx, y + dy, z + dz);
    }
}

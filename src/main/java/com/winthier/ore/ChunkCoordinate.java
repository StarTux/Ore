package com.winthier.ore;

import lombok.Value;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

@Value
final class ChunkCoordinate {
    public final int x, y, z;

    static ChunkCoordinate of(Block block) {
        return new ChunkCoordinate(block.getX() >> 4, block.getY() >> 4, block.getZ() >> 4);
    }

    static ChunkCoordinate of(Location loc) {
        return of(loc.getBlock());
    }

    Block getBlock(World world) {
        return world.getBlockAt(x * OreChunk.SIZE, y * OreChunk.SIZE, z * OreChunk.SIZE);
    }

    Block getBlockAtY(int y, World world) {
        return world.getBlockAt(x * OreChunk.SIZE, y, z * OreChunk.SIZE);
    }

    int distanceSquared(ChunkCoordinate other) {
        int dx = other.x - x;
        int dy = other.y - y;
        int dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    int axisDistance(ChunkCoordinate other) {
        return Math.max(
            Math.abs(x - other.x),
            Math.max(
                Math.abs(y - other.y),
                Math.abs(z - other.z)));
    }

    ChunkCoordinate getRelative(int dx, int dy, int dz) {
        return new ChunkCoordinate(x + dx, y + dy, z + dz);
    }

    ChunkCoordinate diff(ChunkCoordinate other) {
        return new ChunkCoordinate(other.x - x, other.y - y, other.z - z);
    }
}

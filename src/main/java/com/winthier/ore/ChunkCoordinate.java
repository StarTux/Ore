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
        return world.getBlockAt(x * 16, y * 16, z * 16);
    }

    Block getBlockAtY(int y, World world) {
        return world.getBlockAt(x * 16, y, z * 16);
    }

    ChunkCoordinate getRelative(int dx, int dy, int dz) {
        return new ChunkCoordinate(x + dx, y + dy, z + dz);
    }
}

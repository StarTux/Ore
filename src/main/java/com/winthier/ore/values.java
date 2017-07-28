package com.winthier.ore;

import lombok.Value;
import org.bukkit.block.Block;

@Value final class Vec3{public final int x, y, z;}
@Value final class Vec2{public final int x, y;}

enum Facing {
    NORTH(new Vec2(0, -1), 4, 2, 2, 2),
    SOUTH(new Vec2(0, 1), 3, 3, 3, 0),
    WEST(new Vec2(-1, 0), 2, 4, 0, 1),
    EAST(new Vec2(1, 0), 1, 5, 1, 3);
    public final Vec2 vector;
    public final int dataTorch;
    public final int dataBlock;
    public final int dataStair;
    public final int dataBed;
    public final int dataFenceGate;
    Facing rotate() {
        switch (this) {
        case NORTH: return EAST;
        case EAST: return SOUTH;
        case SOUTH: return WEST;
        case WEST: default: return NORTH;
        }
    }
    Facing opposite() {
        switch (this) {
        case NORTH: return WEST;
        case EAST: return WEST;
        case SOUTH: return NORTH;
        case WEST: default: return EAST;
        }
    }
    Facing(Vec2 vector, int dataTorch, int dataBlock, int dataStair, int dataBed) {
        this.vector = vector;
        this.dataTorch = dataTorch;
        this.dataBlock = dataBlock; // stair, end rod, banner
        this.dataStair = dataStair;
        this.dataBed = dataBed;
        this.dataFenceGate = dataBed;
    }

    static Facing ofBlockData(int data) {
        for (Facing f: Facing.values()) {
            if (f.dataBlock == data) return f;
        }
        return null;
    }
}

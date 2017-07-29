package com.winthier.ore;

import lombok.Value;
import org.bukkit.block.Block;

@Value final class Vec3{public final int x, y, z;}
@Value final class Vec2{public final int x, y;}

enum Facing {
    NORTH(new Vec2(0, -1), 4, 2, 3, 2, 2, 4),
    EAST (new Vec2(1, 0),  1, 5, 0, 3, 3, 8),
    SOUTH(new Vec2(0, 1),  3, 3, 2, 0, 0, 1),
    WEST (new Vec2(-1, 0), 2, 4, 1, 1, 1, 2);
    public final Vec2 vector;
    public final int dataTorch; // also lever
    public final int dataBlock; // end rod, banner
    public final int dataStair;
    public final int dataBed;
    public final int dataFenceGate;
    public final int dataGlazedTerracotta;
    public final int dataVine;

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

    Facing(Vec2 vector, int dataTorch, int dataBlock, int dataStair, int dataBed, int dataGlazedTerracotta, int dataVine) {
        this.vector = vector;
        this.dataTorch = dataTorch;
        this.dataBlock = dataBlock;
        this.dataStair = dataStair;
        this.dataBed = dataBed;
        this.dataFenceGate = dataBed;
        this.dataGlazedTerracotta = dataGlazedTerracotta;
        this.dataVine = dataVine;
    }

    static Facing ofBlockData(int data) {
        for (Facing f: Facing.values()) {
            if (f.dataBlock == data) return f;
        }
        return null;
    }

    static Facing ofGlazedTerracottsData(int data) {
        for (Facing f: Facing.values()) {
            if (f.dataGlazedTerracotta == data) return f;
        }
        return null;
    }

    static Facing ofVineData(int data) {
        for (Facing f: Facing.values()) {
            if (f.dataVine == data) return f;
        }
        return null;
    }

    static Facing ofBedData(int data) {
        for (Facing f: Facing.values()) {
            if (f.dataBed == data) return f;
        }
        return null;
    }

    static Facing ofStairData(int data) {
        for (Facing f: Facing.values()) {
            if (f.dataStair == data) return f;
        }
        return null;
    }
}

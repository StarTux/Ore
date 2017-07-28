package com.winthier.ore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Attachable;
import org.bukkit.material.Directional;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Mushroom;
import org.bukkit.material.Vine;

@Value
public final class Schematic {
    String name;
    List<String> tags;
    int sizeX, sizeY, sizeZ;
    List<Integer> ids, data;
    int rotation;
    final List<Extra> extras = new ArrayList<>();
    final static BlockFace[] NBORS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    @Value final static class PasteResult {
        Schematic schematic;
        Block sourceBlock;
        List<Chest> chests;
    }

    @Value final static class Extra {
        int x, y, z;
        String value;
    }

    @SuppressWarnings("deprecation")
    static Schematic copy(Block a, Block b, String name, List<String> tags) {
        int xa = Math.min(a.getX(), b.getX());
        int xb = Math.max(a.getX(), b.getX());
        int ya = Math.min(a.getY(), b.getY());
        int yb = Math.max(a.getY(), b.getY());
        int za = Math.min(a.getZ(), b.getZ());
        int zb = Math.max(a.getZ(), b.getZ());
        int sizeX = xb - xa + 1;
        int sizeY = yb - ya + 1;
        int sizeZ = zb - za + 1;
        int size = sizeX * sizeY * sizeZ;
        List<Integer> ids = new ArrayList<>();
        List<Integer> data = new ArrayList<>();
        World world = a.getWorld();
        List<Extra> extras = new ArrayList<>();
        for (int y = ya; y <= yb; ++y) {
            for (int z = za; z <= zb; ++z) {
                for (int x = xa; x <= xb; ++x) {
                    Block block = world.getBlockAt(x, y, z);
                    ids.add(block.getTypeId());
                    data.add((int)block.getData());
                    if (block.getType() == Material.MOB_SPAWNER) {
                        CreatureSpawner cs = (CreatureSpawner)block.getState();
                        Extra extra = new Extra(x - xa, y - ya, z - za, cs.getSpawnedType().name());
                        extras.add(extra);
                    }
                }
            }
        }
        Schematic result = new Schematic(name, tags, sizeX, sizeY, sizeZ, ids, data, 0);
        result.extras.addAll(extras);
        return result;
    }

    PasteResult paste(Block a) {
        return paste(a, false);
    }

    @Getter
    final class BlockSetter implements Comparable<BlockSetter> {
        private final Block block;
        private final Material material;
        private final int data;
        private final int sortValue;

        BlockSetter(Block block, Material material, int data) {
            this.block = block;
            this.material = material;
            this.data = data;
            int order = 0;
            if (material.isSolid()) order -= 10;
            if (material.isOccluding()) order -= 10;
            if (material.isTransparent()) order += 5;
            this.sortValue = order;
        }

        void update(Random rnd) {
            block.setTypeIdAndData(material.getId(), (byte)data, this.sortValue < 0);
            if (material == Material.MOB_SPAWNER) {
                CreatureSpawner state = (CreatureSpawner)block.getState();
                if (tags.contains("spider")) {
                    if (rnd.nextBoolean()) {
                        state.setSpawnedType(EntityType.SPIDER);
                    } else {
                        state.setSpawnedType(EntityType.CAVE_SPIDER);
                    }
                } else if (tags.contains("halloween")) {
                    state.setSpawnedType(EntityType.WITCH);
                } else if (tags.contains("nether")) {
                    state.setSpawnedType(EntityType.BLAZE);
                } else {
                    switch (rnd.nextInt(5)) {
                    case 0: state.setSpawnedType(EntityType.ZOMBIE); break;
                    case 1: state.setSpawnedType(EntityType.SKELETON); break;
                    case 2: state.setSpawnedType(EntityType.SPIDER); break;
                    case 3: state.setSpawnedType(EntityType.CAVE_SPIDER); break;
                    case 4: state.setSpawnedType(EntityType.CREEPER); break;
                    }
                }
                state.update();
            }
        }

        @Override
        public int compareTo(BlockSetter other) {
            return Integer.compare(this.sortValue, other.sortValue);
        }
    }

    PasteResult paste(Block a, boolean force) {
        int xa = a.getX();
        int ya = a.getY();
        int za = a.getZ();
        int xb = xa + sizeX - 1;
        int yb = ya + sizeY - 1;
        int zb = za + sizeZ - 1;
        World world = a.getWorld();
        Random rnd = new Random(a.hashCode());
        List<Chest> chests = new ArrayList<>();
        List<BlockSetter> blockSetters = new ArrayList<>();
        int i = -1;
        for (int y = ya; y <= yb; ++y) {
            for (int z = za; z <= zb; ++z) {
                for (int x = xa; x <= xb; ++x) {
                    i += 1;
                    int id = ids.get(i);
                    Block block = world.getBlockAt(x, y, z);
                    final boolean shouldPaste;
                    if (force) {
                        shouldPaste = true;
                    } else if (!OrePlugin.getInstance().isPlayerPlaced(block)) {
                        Material mat = block.getType();
                        switch (mat) {
                        case STONE:
                        case COBBLESTONE:
                        case MOSSY_COBBLESTONE:
                        case SMOOTH_BRICK:
                        case DIRT:
                        case GRAVEL:
                        case SAND:
                        case SANDSTONE:
                        case OBSIDIAN:
                        case LONG_GRASS:
                            shouldPaste = true;
                            break;
                        case AIR:
                            shouldPaste = mat == Material.MOB_SPAWNER;
                            break;
                        default:
                            shouldPaste = false;
                            break;
                        }
                    } else {
                        shouldPaste = false;
                    }
                    if (shouldPaste) {
                        Material material = Material.getMaterial(id);
                        BlockSetter blockSetter = new BlockSetter(block, material, data.get(i));
                        blockSetters.add(blockSetter);
                    }
                }
            }
        }
        Collections.sort(blockSetters);
        for (BlockSetter blockSetter: blockSetters) {
            blockSetter.update(rnd);
            Material material = blockSetter.getMaterial();
            if (material == Material.CHEST || material == Material.TRAPPED_CHEST) {
                chests.add((Chest)blockSetter.getBlock().getState());
            }
        }
        return new PasteResult(this, a, chests);
    }

    @Value class Foo{int x, z;}
    List<Block> pasteHalloween(WorldGenerator gen, Block a) {
        List<Block> result = new ArrayList<>();
        int xa = a.getX();
        int ya = a.getY();
        int za = a.getZ();
        int xb = xa + sizeX - 1;
        int yb = ya + sizeY - 1;
        int zb = za + sizeZ - 1;
        World world = a.getWorld();
        int i = 0;
        Random rnd = new Random(a.hashCode());
        List<Chest> treasureChests = new ArrayList<>();
        Map<Foo, Integer> hor = new HashMap<>();
        for (int y = ya; y <= yb; ++y) {
            for (int z = za; z <= zb; ++z) {
                for (int x = xa; x <= xb; ++x) {
                    Block block = world.getBlockAt(x, y, z);
                    boolean shouldPaste = true;
                    if (shouldPaste) {
                        int id = ids.get(i);
                        if (id != Material.AIR.getId()) {
                            result.add(block);
                            Foo foo = new Foo(x, z);
                            Integer min = hor.get(foo);
                            if (min == null || min > y) {
                                hor.put(foo, y);
                            }
                            block.setTypeIdAndData(id, data.get(i).byteValue(), false);
                            if (id == Material.MOB_SPAWNER.getId()) {
                                CreatureSpawner state = (CreatureSpawner)block.getState();
                                Extra extra = extraAt(x - xa, y - ya, z - za);
                                EntityType et = null;
                                if (extra != null) {
                                    try {
                                        et = EntityType.valueOf(extra.value);
                                        System.out.println("Found " + et + " spawner"); // DEBUG
                                    } catch (IllegalArgumentException iae) {
                                        iae.printStackTrace();
                                    }
                                }
                                if (et != null && et != EntityType.PIG) {
                                    state.setSpawnedType(et);
                                } else {
                                    if (tags.contains("spider")) {
                                        if (rnd.nextBoolean()) {
                                            state.setSpawnedType(EntityType.SPIDER);
                                        } else {
                                            state.setSpawnedType(EntityType.CAVE_SPIDER);
                                        }
                                    } else if (tags.contains("halloween")) {
                                        state.setSpawnedType(EntityType.WITCH);
                                    } else if (tags.contains("nether")) {
                                        state.setSpawnedType(EntityType.BLAZE);
                                    } else {
                                        if (rnd.nextBoolean()) {
                                            state.setSpawnedType(EntityType.SKELETON);
                                        } else {
                                            state.setSpawnedType(EntityType.ZOMBIE);
                                        }
                                    }
                                }
                                state.update();
                            } else if (id == Material.CHEST.getId() || id == Material.TRAPPED_CHEST.getId()) {
                                treasureChests.add((Chest)block.getState());
                            }
                        }
                    }
                    i += 1;
                }
            }
        }
        i = 0;
        for (int y = ya; y <= yb; ++y) {
            for (int z = za; z <= zb; ++z) {
                for (int x = xa; x <= xb; ++x) {
                    Block block = world.getBlockAt(x, y, z);
                    int id = ids.get(i);
                    if (id == Material.AIR.getId()) {
                        Integer min = hor.get(new Foo(x, z));
                        if (min != null && min < y) {
                            block.setType(Material.AIR, false);
                        }
                    }
                    i += 1;
                }
            }
        }
        if (gen != null) gen.spawnLoot(treasureChests);
        return result;
    }

    Extra extraAt(int x, int y, int z) {
        for (Extra extra: extras) {
            if (extra.x == x && extra.y == y && extra.z == z) {
                return extra;
            }
        }
        return null;
    }

    boolean save(File file) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("tags", tags);
            config.set("size", Arrays.asList(sizeX, sizeY, sizeZ));
            config.set("ids", ids);
            config.set("data", data);
            if (!extras.isEmpty()) {
                List<Object> list = new ArrayList<>();
                for (Extra extra: extras) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("x", extra.x);
                    map.put("y", extra.y);
                    map.put("z", extra.z);
                    map.put("value", extra.value);
                    list.add(map);
                }
                config.set("extras", list);
            }
            config.save(file);
            return true;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    static Schematic load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> tags = config.getStringList("tags");
        int sizeX = config.getIntegerList("size").get(0);
        int sizeY = config.getIntegerList("size").get(1);
        int sizeZ = config.getIntegerList("size").get(2);
        List<Integer> ids = config.getIntegerList("ids");
        List<Integer> data = config.getIntegerList("data");
        String name = file.getName().replace(".yml", "");
        List<Extra> extras = new ArrayList<>();
        for (Map<?, ?> map: config.getMapList("extras")) {
            ConfigurationSection section = config.createSection("tmp", map);
            extras.add(new Extra(section.getInt("x"), section.getInt("y"), section.getInt("z"), section.getString("value")));
        }
        Schematic result = new Schematic(name, tags, sizeX, sizeY, sizeZ, ids, data, 0);
        result.extras.addAll(extras);
        return result;
    }

    @SuppressWarnings("deprecation")
    Schematic rotate() {
        int newSizeX = sizeZ;
        int newSizeZ = sizeX;
        int size = sizeX * sizeY * sizeZ;
        List<Integer> newIds = new ArrayList<>(size);
        List<Integer> newData = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            newIds.add(0);
            newData.add(0);
        }
        for (int y = 0; y < sizeY; ++y) {
            for (int z = 0; z < sizeZ; ++z) {
                for (int x = 0; x < sizeX; ++x) {
                    // Convert coordinates
                    int oldIndex = y*sizeX*sizeZ + z*sizeX + x;
                    int newX = newSizeX - z - 1;
                    int newZ = x;
                    int newIndex = y*sizeX*sizeZ + newZ*newSizeX + newX;
                    // Fetch old values
                    int aId = ids.get(oldIndex);
                    int aData = data.get(oldIndex);
                    // Rotate if necessary
                    Material mat = Material.getMaterial(aId);
                    switch (mat) {
                    case LADDER:
                        Facing facing = Facing.ofBlockData(aData);
                        if (facing != null) {
                            aData = facing.rotate().dataBlock;
                        }
                        break;
                    default:
                        MaterialData matData = mat.getNewData((byte)aData);
                        if (matData instanceof Vine) {
                            Vine vine = (Vine)matData;
                            Vine vine2 = new Vine();
                            for (BlockFace face: NBORS) {
                                if (vine.isOnFace(face)) {
                                    if (needsRotation(face)) {
                                        vine2.putOnFace(rotate(face).getOppositeFace());
                                    } else {
                                        vine2.putOnFace(face);
                                    }
                                }
                            }
                            aData = vine2.getData();
                        } else if (matData instanceof Mushroom) {
                            Mushroom mush = (Mushroom)matData;
                            if (!mush.isStem()) {
                                Mushroom mush2 = new Mushroom(mush.getItemType());
                                for (BlockFace face: mush.getPaintedFaces()) {
                                    if (needsRotation(face)) {
                                        mush2.setFacePainted(rotate(face).getOppositeFace(), true);
                                    } else {
                                        mush2.setFacePainted(face, true);
                                    }
                                }
                                aData = mush2.getData();
                            }
                        } else if (matData instanceof Attachable) {
                            Attachable attach = (Attachable)matData;
                            if (needsRotation(attach.getAttachedFace())) {
                                attach.setFacingDirection(rotate(attach.getAttachedFace()));
                                aData = matData.getData();
                            }
                        } else if (matData instanceof Directional) {
                            Directional direct = (Directional)matData;
                            if (needsRotation(direct.getFacing())) {
                                direct.setFacingDirection(rotate(direct.getFacing()));
                                aData = matData.getData();
                            }
                        }
                    }
                    // Paste
                    newIds.set(newIndex, aId);
                    newData.set(newIndex, aData);
                }
            }
        }
        return new Schematic(name, tags, newSizeX, sizeY, newSizeZ, newIds, newData, rotation + 1);
    }

    private static boolean needsRotation(final BlockFace face) {
        switch (face) {
        case NORTH:
        case EAST:
        case SOUTH:
        case WEST:
            return true;
        default:
            return false;
        }
    }

    private static BlockFace rotate(final BlockFace face) {
        switch (face) {
        case NORTH: return BlockFace.WEST;
        case EAST:  return BlockFace.NORTH;
        case SOUTH: return BlockFace.EAST;
        case WEST:  return BlockFace.SOUTH;
        default: return face;
        }
    }
}

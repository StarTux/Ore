package com.winthier.ore;

import com.winthier.custom.util.Dirty;
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
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Directional;
import org.bukkit.material.Mushroom;
import org.bukkit.material.Rails;
import org.bukkit.material.Vine;
import org.bukkit.material.types.MushroomBlockTexture;
import org.json.simple.JSONValue;

@Value
public final class Schematic {
    String name;
    List<String> tags;
    int sizeX, sizeY, sizeZ;
    List<Entry> blocks;
    int rotation;
    final static BlockFace[] NBORS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    @Value final static class Entry {
        BlockData blockData;
        Map<String, Object> tileData;
    }

    @Value final static class PasteResult {
        Schematic schematic;
        Block sourceBlock;
        List<Chest> chests;
        List<CreatureSpawner> spawners;
    }

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
        List<Entry> blocks = new ArrayList<>();
        World world = a.getWorld();
        for (int y = ya; y <= yb; ++y) {
            for (int z = za; z <= zb; ++z) {
                for (int x = xa; x <= xb; ++x) {
                    Block block = world.getBlockAt(x, y, z);
                    Map<String, Object> tileData = Dirty.getBlockTag(block);
                    if (tileData != null) {
                        tileData.remove("x");
                        tileData.remove("y");
                        tileData.remove("z");
                    }
                    blocks.add(new Entry(block.getBlockData(), tileData));
                }
            }
        }
        Schematic result = new Schematic(name, tags, sizeX, sizeY, sizeZ, blocks, 0);
        return result;
    }

    PasteResult paste(Block a) {
        return paste(a, false);
    }

    @Getter
    final class BlockSetter {
        private final Block block;
        private final BlockData blockData;
        private final Map<String, Object> tileData;
        private final int order;
        private final boolean physics;

        BlockSetter(Block block, BlockData blockData, Map<String, Object> tileData) {
            this.block = block;
            this.blockData = blockData;
            this.tileData = tileData;
            int orderv = 0;
            boolean physicsv = false;
            int y = block.getY();
            Material material = blockData.getMaterial();
            switch (material) {
            case REDSTONE_LAMP:
            case REDSTONE_TORCH:
            case RAIL:
            case ACTIVATOR_RAIL:
            case DETECTOR_RAIL:
            case POWERED_RAIL:
                orderv = 512 + y;
                physicsv = false;
                break;
            case ACACIA_DOOR:
            case BIRCH_DOOR:
            case DARK_OAK_DOOR:
            case IRON_DOOR:
            case JUNGLE_DOOR:
            case SPRUCE_DOOR:
            case OAK_DOOR:
                orderv = 1024 + y;
                physicsv = false;
                break;
            default:
                if (material.hasGravity()) {
                    orderv = 255 + y;
                    physicsv = false;
                } else {
                    orderv = y;
                    if (material.isSolid()) orderv -= 128;
                    if (material.isOccluding()) orderv -= 128;
                    physicsv = true;
                }
            }
            this.order = orderv;
            this.physics = physicsv;
        }

        void update(Random rnd) {
            block.setBlockData(blockData);
            if (tileData != null) {
                tileData.put("x", block.getX());
                tileData.put("y", block.getY());
                tileData.put("z", block.getZ());
                OrePlugin.getInstance().getLogger().info("Pasting tile entity data at " + block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ() + ": " + JSONValue.toJSONString(tileData));
                boolean result = Dirty.setBlockTag(block, tileData);
                if (!result) {
                    OrePlugin.getInstance().getLogger().warning("Could not paste tile entity data at " + block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ());
                }
            }
            Material material = blockData.getMaterial();
            if (material == Material.SPAWNER) {
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
                    switch (rnd.nextInt(4)) {
                    case 0: case 1: state.setSpawnedType(EntityType.BLAZE); break;
                    case 2: state.setSpawnedType(EntityType.WITHER_SKELETON); break;
                    case 3: default: state.setSpawnedType(EntityType.SKELETON);
                    }
                } else {
                    switch (rnd.nextInt(4)) {
                    case 0: state.setSpawnedType(EntityType.ZOMBIE); break;
                    case 1: state.setSpawnedType(EntityType.SKELETON); break;
                    case 2:
                        if (rnd.nextBoolean()) {
                            state.setSpawnedType(EntityType.SPIDER);
                        } else {
                            state.setSpawnedType(EntityType.CAVE_SPIDER);
                        }
                        break;
                    case 3: default: state.setSpawnedType(EntityType.CREEPER);
                    }
                }
                state.update();
            }
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
        List<CreatureSpawner> spawners = new ArrayList<>();
        List<BlockSetter> blockSetters = new ArrayList<>();
        int i = -1;
        for (int y = ya; y <= yb; ++y) {
            for (int z = za; z <= zb; ++z) {
                for (int x = xa; x <= xb; ++x) {
                    i += 1;
                    Entry entry = blocks.get(i);
                    BlockData blockData = entry.blockData;
                    Block block = world.getBlockAt(x, y, z);
                    final boolean shouldPaste;
                    if (force) {
                        shouldPaste = true;
                    } else if (!OrePlugin.getInstance().isPlayerPlaced(block)) {
                        Material mat = block.getType();
                        switch (mat) {
                        case SMOOTH_STONE:
                        case COBBLESTONE:
                        case MOSSY_COBBLESTONE:
                        case STONE_BRICKS:
                        case CHISELED_STONE_BRICKS:
                        case CRACKED_STONE_BRICKS:
                        case DIRT:
                        case COARSE_DIRT:
                        case GRAVEL:
                        case SAND:
                        case SANDSTONE:
                        case OBSIDIAN:
                        case TALL_GRASS:
                            shouldPaste = true;
                            break;
                        case AIR:
                            shouldPaste = mat == Material.SPAWNER;
                            break;
                        default:
                            shouldPaste = false;
                            break;
                        }
                    } else {
                        shouldPaste = false;
                    }
                    if (shouldPaste) {
                        if (blockData.getMaterial() != Material.BARRIER) {
                            BlockSetter blockSetter = new BlockSetter(block, blockData, entry.tileData);
                            blockSetters.add(blockSetter);
                        }
                    }
                }
            }
        }
        Collections.sort(blockSetters, (ia, ib) -> Integer.compare(ia.order, ib.order));
        for (BlockSetter blockSetter: blockSetters) blockSetter.block.setType(Material.AIR, false);
        for (BlockSetter blockSetter: blockSetters) {
            try {
                blockSetter.update(rnd);
            } catch (Exception e) {
                System.err.println("Error setting block " + blockSetter.block);
                e.printStackTrace();
            }
            Material material = blockSetter.getBlockData().getMaterial();
            if (material == Material.CHEST || material == Material.TRAPPED_CHEST) {
                chests.add((Chest)blockSetter.getBlock().getState());
            } else if (material == Material.SPAWNER) {
                spawners.add((CreatureSpawner)blockSetter.getBlock().getState());
            }
        }
        return new PasteResult(this, a, chests, spawners);
    }

    boolean save(File file) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("tags", tags);
            config.set("size", Arrays.asList(sizeX, sizeY, sizeZ));
            config.set("blocks", blocks.stream().map(e -> {
                        if (e.tileData != null) {
                            return e.blockData.getAsString() + " " + JSONValue.toJSONString(e.tileData);
                        } else {
                            return e.blockData.getAsString();
                        }
                    }).collect(Collectors.toList()));
            config.save(file);
            return true;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    static Schematic load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> tags = config.getStringList("tags");
        if (tags.isEmpty()) tags = Arrays.asList("default");
        String name = file.getName().replace(".yml", "");
        int sizeX = config.getIntegerList("size").get(0);
        int sizeY = config.getIntegerList("size").get(1);
        int sizeZ = config.getIntegerList("size").get(2);
        List<Entry> blocks = config.getStringList("blocks").stream().map(e -> {
                String[] split = e.split(" ", 2);
                if (split.length == 2) {
                    Map<String, Object> tileData;
                    try {
                        tileData = (Map<String, Object>)JSONValue.parseWithException(split[1]);
                    } catch (Exception exception) {
                        System.err.println("JSON: " + split[1]);
                        exception.printStackTrace();
                        tileData = null;
                    }
                    return new Entry(Bukkit.getServer().createBlockData(split[0]), tileData);
                } else {
                    return new Entry(Bukkit.getServer().createBlockData(split[0]), null);
                }
            }).collect(Collectors.toList());
        Schematic result = new Schematic(name, tags, sizeX, sizeY, sizeZ, blocks, 0);
        return result;
    }

    @SuppressWarnings("deprecation")
    Schematic rotate() {
        int newSizeX = sizeZ;
        int newSizeZ = sizeX;
        int size = sizeX * sizeY * sizeZ;
        List<Entry> newBlocks = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            newBlocks.add(new Entry(Material.AIR.createBlockData(), null));
        }
        for (int y = 0; y < sizeY; ++y) {
            for (int z = 0; z < sizeZ; ++z) {
                for (int x = 0; x < sizeX; ++x) {
                    // Convert coordinates
                    int oldIndex = y*sizeX*sizeZ + z*sizeX + x;
                    int newX = newSizeX - z - 1;
                    int newZ = x;
                    int newIndex = y*sizeX*sizeZ + newZ*newSizeX + newX;
                    Entry oldEntry = blocks.get(oldIndex);
                    Entry newEntry = new Entry(oldEntry.blockData, oldEntry.tileData);
                    // Fetch old values
                    // TODO: Block Rotation
                    // Paste
                    newBlocks.set(newIndex, newEntry);
                }
            }
        }
        return new Schematic(name, tags, newSizeX, sizeY, newSizeZ, newBlocks, rotation + 1);
    }

    private static boolean needsRotation(final BlockFace face) {
        switch (face) {
        case NORTH:
        case EAST:
        case SOUTH:
        case WEST:
        case NORTH_EAST:
        case SOUTH_EAST:
        case SOUTH_WEST:
        case NORTH_WEST:
            return true;
        default:
            return false;
        }
    }

    private static BlockFace rotate(final BlockFace face) {
        switch (face) {
        case NORTH: return BlockFace.EAST;
        case EAST:  return BlockFace.SOUTH;
        case SOUTH: return BlockFace.WEST;
        case WEST:  return BlockFace.NORTH;
        case NORTH_EAST: return BlockFace.SOUTH_EAST;
        case SOUTH_EAST: return BlockFace.SOUTH_WEST;
        case SOUTH_WEST: return BlockFace.NORTH_WEST;
        case NORTH_WEST: return BlockFace.NORTH_EAST;
        default: return face;
        }
    }
}

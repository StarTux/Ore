package com.winthier.ore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import lombok.Value;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Attachable;
import org.bukkit.material.Directional;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Vine;

@Value
class Schematic {
    String name;
    List<String> tags;
    int sizeX, sizeY, sizeZ;
    List<Integer> ids, data;
    final static BlockFace[] NBORS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

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
        for (int y = ya; y <= yb; ++y) {
            for (int z = za; z <= zb; ++z) {
                for (int x = xa; x <= xb; ++x) {
                    Block block = world.getBlockAt(x, y, z);
                    ids.add(block.getTypeId());
                    data.add((int)block.getData());
                }
            }
        }
        return new Schematic(name, tags, sizeX, sizeY, sizeZ, ids, data);
    }

    void paste(Block a) {
        paste(a, false);
    }

    void paste(Block a, boolean force) {
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
        for (int y = ya; y <= yb; ++y) {
            for (int z = za; z <= zb; ++z) {
                for (int x = xa; x <= xb; ++x) {
                    Block block = world.getBlockAt(x, y, z);
                    boolean shouldPaste = false;
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
                        case LAVA:
                        case STATIONARY_LAVA:
                        case WATER:
                        case STATIONARY_WATER:
                        case OBSIDIAN:
                            shouldPaste = true;
                        }
                    }
                    if (shouldPaste) {
                        int id = ids.get(i);
                        block.setTypeIdAndData(id, data.get(i).byteValue(), false);
                        if (id == Material.MOB_SPAWNER.getId()) {
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
                                if (rnd.nextBoolean()) {
                                    state.setSpawnedType(EntityType.SKELETON);
                                } else {
                                    state.setSpawnedType(EntityType.ZOMBIE);
                                }
                            }
                            state.update();
                        } else if (id == Material.CHEST.getId()) {
                            treasureChests.add((Chest)block.getState());
                        }
                    }
                    i += 1;
                }
            }
        }
        if (!force && treasureChests.size() > 0) {
            int total = 16 + rnd.nextInt(8) - rnd.nextInt(8);
            for (int j = 0; j < total; ++j) {
                ItemStack item = randomTreasure(rnd);
                if (item != null) {
                    Inventory inv = treasureChests.get(rnd.nextInt(treasureChests.size())).getInventory();
                    inv.setItem(rnd.nextInt(inv.getSize()), item);
                }
            }
            for (Chest chest: treasureChests) chest.update();
        }
    }

    @Value static class TreasureItem { int weight; ItemStack item; }
    private static TreasureItem ti(int weight, Material mat, int amount, short damage) {
        return new TreasureItem(weight, new ItemStack(mat, amount, damage));
    }
    private static TreasureItem ti(int weight, Material mat, int amount) {
        return ti(weight, mat, amount, (short)0);
    }
    private static TreasureItem ti(int weight, Material mat) {
        return ti(weight, mat, 1, (short)0);
    }
    final static List<TreasureItem> TREASURES = Arrays.asList(
        ti(50, Material.SULPHUR, 16),
        ti(50, Material.REDSTONE, 16),
        ti(50, Material.BONE, 16),
        ti(50, Material.COAL, 16),
        ti(50, Material.STRING, 16),
        ti(50, Material.ARROW, 16),

        ti(50, Material.BEETROOT_SEEDS, 16),
        ti(50, Material.MELON_SEEDS, 16),
        ti(50, Material.PUMPKIN_SEEDS, 16),
        ti(50, Material.SEEDS, 16),
        ti(50, Material.CARROT, 16),
        ti(50, Material.POTATO, 16),
        ti(50, Material.EGG, 16),

        ti(25, Material.IRON_INGOT, 8),
        ti(25, Material.GOLD_INGOT, 8),
        ti(25, Material.BUCKET),
        ti(25, Material.DIAMOND, 1),
        ti(25, Material.EMERALD, 1),
        ti(25, Material.NAME_TAG),
        ti(25, Material.GOLDEN_APPLE),
        
        ti(10, Material.IRON_BARDING),
        ti(10, Material.GOLD_BARDING),
        ti(10, Material.DIAMOND_BARDING),

        ti(1, Material.GOLDEN_APPLE, 1, (short)1),
        
        ti(10, Material.GOLD_RECORD),
        ti(10, Material.GREEN_RECORD),
        ti(1, Material.RECORD_3),
        ti(1, Material.RECORD_4),
        ti(1, Material.RECORD_5),
        ti(1, Material.RECORD_6),
        ti(1, Material.RECORD_7),
        ti(1, Material.RECORD_8),
        ti(1, Material.RECORD_9),
        ti(1, Material.RECORD_10),
        ti(1, Material.RECORD_11),
        ti(1, Material.RECORD_12)
        );
    ItemStack randomTreasure(Random rnd) {
        int total = 0;
        for (TreasureItem treasureItem: TREASURES) {
            total += treasureItem.getWeight();
        }
        total = rnd.nextInt(total);
        TreasureItem result = null;
        for (TreasureItem treasureItem: TREASURES) {
            total -= treasureItem.getWeight();
            if (total < 0) {
                result = treasureItem;
                break;
            }
        }
        ItemStack item = result.getItem().clone();
        if (item.getAmount() > 1) {
            item.setAmount(1 + rnd.nextInt(item.getAmount() - 1));
        }
        return item;
    }

    boolean save(File file) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("tags", tags);
            config.set("size", Arrays.asList(sizeX, sizeY, sizeZ));
            config.set("ids", ids);
            config.set("data", data);
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
        return new Schematic(name, tags, sizeX, sizeY, sizeZ, ids, data);
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
                    // Paste
                    newIds.set(newIndex, aId);
                    newData.set(newIndex, aData);
                }
            }
        }
        return new Schematic(name, tags, newSizeX, sizeY, newSizeZ, newIds, newData);
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

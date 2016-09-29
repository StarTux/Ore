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
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

@Value
class Schematic {
    String name;
    List<String> tags;
    int sizeX, sizeY, sizeZ;
    List<Integer> ids, data;

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
        if (treasureChests.size() > 0) {
            int total = 5 + rnd.nextInt(5) -  + rnd.nextInt(5);
            for (int j = 0; j < total; ++j) {
                ItemStack item = randomTreasure(rnd);
                if (item != null) {
                    treasureChests.get(rnd.nextInt(treasureChests.size())).getInventory().addItem(item);
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
        ti(100, Material.SULPHUR, 16),
        ti(100, Material.REDSTONE, 16),
        ti(100, Material.BONE, 16),
        ti(100, Material.COAL, 16),
        ti(100, Material.STRING, 16),

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
}

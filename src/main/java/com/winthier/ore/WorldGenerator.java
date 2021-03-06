package com.winthier.ore;

import com.winthier.custom.CustomPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Stray;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldGenerator {
    final OrePlugin plugin;
    final String worldName;
    final int chunkRevealRadius = 2;
    final int chunkRevealRadiusSquared = chunkRevealRadius * chunkRevealRadius;
    final Random random = new Random(System.currentTimeMillis());
    final static BlockFace[] NBORS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
    final long worldSeed;
    final Set<ChunkCoordinate> revealedDungeons = new HashSet<>();
    final Map<Block, Integer> spawnerSpawns = new HashMap<>();

    static public enum Special {
        NONE, MESA, OCEAN, DESERT, JUNGLE, ICE, MUSHROOM, FOREST, SAVANNA, PLAINS;
        static Special of(Biome biome) {
            switch (biome) {
            case BADLANDS:
            case WOODED_BADLANDS_PLATEAU:
            case BADLANDS_PLATEAU:
            case ERODED_BADLANDS:
            case MODIFIED_WOODED_BADLANDS_PLATEAU:
            case MODIFIED_BADLANDS_PLATEAU:
                return MESA;
            case DESERT:
            case DESERT_HILLS:
            case DESERT_LAKES:
                return DESERT;
            case OCEAN:
            case FROZEN_OCEAN:
            case DEEP_OCEAN:
            case WARM_OCEAN:
            case LUKEWARM_OCEAN:
            case COLD_OCEAN:
            case DEEP_WARM_OCEAN:
            case DEEP_LUKEWARM_OCEAN:
            case DEEP_COLD_OCEAN:
            case DEEP_FROZEN_OCEAN:
                return OCEAN;
            case JUNGLE:
            case JUNGLE_HILLS:
            case JUNGLE_EDGE:
            case MODIFIED_JUNGLE:
            case MODIFIED_JUNGLE_EDGE:
                return JUNGLE;
            case ICE_SPIKES:
            case FROZEN_RIVER:
            case TAIGA:
            case TAIGA_HILLS:
            case SNOWY_TAIGA:
            case SNOWY_TAIGA_HILLS:
            case GIANT_TREE_TAIGA:
            case GIANT_TREE_TAIGA_HILLS:
            case TAIGA_MOUNTAINS:
            case SNOWY_TAIGA_MOUNTAINS:
            case GIANT_SPRUCE_TAIGA:
            case GIANT_SPRUCE_TAIGA_HILLS:
                return ICE;
            case MUSHROOM_FIELDS:
            case MUSHROOM_FIELD_SHORE:
                return MUSHROOM;
            case FOREST:
            case BIRCH_FOREST:
            case BIRCH_FOREST_HILLS:
            case DARK_FOREST:
            case FLOWER_FOREST:
            case TALL_BIRCH_FOREST:
            case DARK_FOREST_HILLS:
                return FOREST;
            case SAVANNA:
            case SAVANNA_PLATEAU:
            case SHATTERED_SAVANNA:
            case SHATTERED_SAVANNA_PLATEAU:
                return SAVANNA;
            case PLAINS:
            case SUNFLOWER_PLAINS:
                return PLAINS;
            default:
                return NONE;
            }
        }
    }

    @Value
    static class LootItem {
        ItemStack item;
        int weight;
    }

    private boolean shouldStop = false;
    private int dungeonChance = 0;
    private long seed; // Defaults to world seed
    private List<LootItem> lootItems = null;
    private int lootMedian = 0, lootVariance = 0;
    boolean debug = false;

    // Call once!
    void configure(ConfigurationSection config) {
        dungeonChance = config.getInt("Dungeons", dungeonChance);
        seed = config.getLong("Seed", seed);
        debug = config.getBoolean("Debug", debug);
        Random random = new Random(seed);
        plugin.getLogger().info("Loaded world " + worldName + " Dungeons=" + dungeonChance + " Seed=" + seed + " Debug=" + debug);
    }

    // Async
    final LinkedBlockingQueue<OreChunk> queue = new LinkedBlockingQueue<OreChunk>();

    // Sync
    BukkitRunnable syncTask = null;
    final Map<ChunkCoordinate, OreChunk> generatedChunks = new HashMap<>();
    final Set<ChunkCoordinate> scheduledChunks = new HashSet<>();

    WorldGenerator(OrePlugin plugin, String worldName) {
        this.plugin = plugin;
        this.worldName = worldName;
        worldSeed = Bukkit.getServer().getWorld(worldName).getSeed();
        seed = worldSeed;
    }

    World getWorld() {
        return Bukkit.getServer().getWorld(worldName);
    }

    @Value static class DungeonChunk { int x, z; long seed; }
    /**
     * @return -1 if this chunk does not contain a dungeon,
     * positive number for the y level of the dungeon.
     */
    Vec3 getDungeonOffset(OreChunk chunk, Special special) {
        Random rnd = new Random(new DungeonChunk(chunk.x, chunk.z, seed).hashCode());
        if (dungeonChance < 100 && rnd.nextInt(100) >= dungeonChance) return null;
        return new Vec3(rnd.nextInt(16) - 8,
                        rnd.nextInt(32) + 4,
                        rnd.nextInt(16) - 8);
    }

    boolean generateVein(OreChunk chunk, OreType ore, Random rnd, int size) {
        if (chunk.empties.isEmpty()) return false;
        List<Vec3> todo = new ArrayList<>(size);
        List<Vec3> found = new ArrayList<>(size);
        Set<Vec3> done = new HashSet<>();
        todo.add(chunk.empties.remove(rnd.nextInt(chunk.empties.size())));
        while (!todo.isEmpty() && found.size() < size) {
            Vec3 vec = todo.remove(rnd.nextInt(todo.size()));
            done.add(vec);
            if (chunk.get(vec.x, vec.y, vec.z) != OreType.NONE) continue;
            found.add(vec);
            if (vec.x > 0) {
                Vec3 a = new Vec3(vec.x - 1, vec.y - 0, vec.z - 0);
                if (!done.contains(a)) todo.add(a);
            }
            if (vec.y > 3 || (chunk.y > 0 && vec.y > 0)) {
                Vec3 a = new Vec3(vec.x - 0, vec.y - 1, vec.z - 0);
                if (!done.contains(a)) todo.add(a);
            }
            if (vec.z > 0) {
                Vec3 a = new Vec3(vec.x - 0, vec.y - 0, vec.z - 1);
                if (!done.contains(a)) todo.add(a);
            }
            if (vec.x < 15) {
                Vec3 a = new Vec3(vec.x + 1, vec.y + 0, vec.z + 0);
                if (!done.contains(a)) todo.add(a);
            }
            if (vec.y < 15) {
                Vec3 a = new Vec3(vec.x + 0, vec.y + 1, vec.z + 0);
                if (!done.contains(a)) todo.add(a);
            }
            if (vec.z < 15) {
                Vec3 a = new Vec3(vec.x + 0, vec.y + 0, vec.z + 1);
                if (!done.contains(a)) todo.add(a);
            }
        }
        for (Vec3 vec: found) {
            chunk.set(vec.x, vec.y, vec.z, ore);
        }
        chunk.empties.removeAll(found);
        return true;
    }

    void generateVein(OreChunk chunk, OreType ore, Random rnd, int size, int tries) {
        for (int i = 0; i < tries; i += 1) {
            generateVein(chunk, ore, rnd, size);
        }
    }

    @Value final static class ChunkSeed { final int x, y, z; final long seed; }
    void generate(OreChunk chunk) {
        Special special = Special.of(chunk.getBiome());
        Random rnd = new Random(new ChunkSeed(chunk.x, chunk.y, chunk.z, seed).hashCode());
        int g = special == Special.MESA ? 4 : 2;
        if (chunk.y < 8) generateVein(chunk, OreType.COAL,     rnd, 17,  5);
        if (chunk.y < 4) generateVein(chunk, OreType.IRON,     rnd,  9,  5);
        if (chunk.y < g) generateVein(chunk, OreType.GOLD,     rnd,  9,  1);
        if (chunk.y < 1) generateVein(chunk, OreType.REDSTONE, rnd,  8,  8);
        if (chunk.y < 1) generateVein(chunk, OreType.DIAMOND,  rnd,  8,  1);
        if (chunk.y < 2) generateVein(chunk, OreType.LAPIS,    rnd,  7,  1);
        if (special == Special.OCEAN) {
            if (chunk.y < 2) generateVein(chunk, OreType.SEA_LANTERN, rnd, 5, 1);
        } else if (chunk.slime) {
            if (chunk.y < 2) generateVein(chunk, OreType.SLIME, rnd, 5, 1);
        } else {
            if (chunk.y < 1) generateVein(chunk, OreType.EMERALD, rnd, 3, 1);
        }
    }

    void start() {
        new BukkitRunnable() {
            @Override public void run() {
                asyncRun();
            }
        }.runTaskAsynchronously(plugin);
        syncTask = new BukkitRunnable() {
                @Override public void run() {
                    syncRun();
                }
            };
        syncTask.runTaskTimer(plugin, 1, 1);
    }

    void stop() {
        shouldStop = true;
        try {
            syncTask.cancel();
        } catch (IllegalStateException ise) {}
    }

    void asyncRun() {
        while (!shouldStop) {
            OreChunk chunk = null;
            try {
                chunk = queue.poll(10, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {}
            if (shouldStop) return;
            if (chunk == null) continue;
            generate(chunk);
            final OreChunk finalChunk = chunk;
            new BukkitRunnable() {
                @Override public void run() {
                    syncDidGenerateChunk(finalChunk);
                }
            }.runTask(plugin);
        }
    }

    // Callback from the async thread
    void syncDidGenerateChunk(OreChunk chunk) {
        ChunkCoordinate coord = chunk.getCoordinate();
        long now = System.currentTimeMillis();
        revealChunk(chunk);
        generatedChunks.put(coord, chunk);
        scheduledChunks.remove(coord);
        chunk.setUsed();
    }

    void syncRun() {
        // Garbage collect chunks
        for (Iterator<Map.Entry<ChunkCoordinate, OreChunk> > it = generatedChunks.entrySet().iterator(); it.hasNext();) {
            Map.Entry<ChunkCoordinate, OreChunk> en = it.next();
            if (en.getValue().isTooOld()) {
                it.remove();
            }
        }
        for (Player player: getWorld().getPlayers()) {
            UUID uuid = player.getUniqueId();
            ChunkCoordinate playerLocation = ChunkCoordinate.of(player.getLocation());
            revealPlayerIter(player, playerLocation);
        }
    }

    private void revealPlayerIter(Player player, ChunkCoordinate center) {
        final int R = chunkRevealRadius;
        for (int y = -R; y <= R; ++y) {
            for (int z = -R; z <= R; ++z) {
                for (int x = -R; x <= R; ++x) {
                    OreChunk chunk = getOrGenerate(center.getRelative(x, y, z));
                    if (chunk != null) chunk.setUsed();
                }
            }
        }
    }

    static boolean isExposedToAir(Block block) {
        for (BlockFace dir: NBORS) {
            if (!block.getRelative(dir).getType().isOccluding()) return true;
        }
        return false;
    }

    boolean canReplace(Block block) {
        if (block.getType() != Material.STONE) return false;
        if (plugin.isPlayerPlaced(block)) return false;
        return true;
    }

    OreChunk getOrGenerate(ChunkCoordinate coord) {
        OreChunk result = generatedChunks.get(coord);
        if (result == null) {
            if (!scheduledChunks.contains(coord)) {
                scheduledChunks.add(coord);
                queue.offer(OreChunk.of(coord.getBlock(getWorld())));
            }
        } else {
            result.setUsed();
        }
        return result;
    }

    OreChunk getAndGenerate(ChunkCoordinate coord) {
        OreChunk result = generatedChunks.get(coord);
        if (result == null) {
            result = OreChunk.of(coord.getBlock(getWorld()));
            generate(result);
            scheduledChunks.remove(coord);
            generatedChunks.put(coord, result);
        }
        result.setUsed();
        return result;
    }

    OreType getOreAt(Block block) {
        ChunkCoordinate coord = ChunkCoordinate.of(block);
        OreChunk chunk = generatedChunks.get(coord);
        if (chunk == null) return null;
        return chunk.at(block);
    }

    void revealChunk(OreChunk chunk) {
        World world = getWorld();
        for (int y = 0; y < 16; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    OreType ore = chunk.get(x, y, z);
                    if (ore.mat != null) {
                        Block block = world.getBlockAt(chunk.getBlockX() + x, chunk.getBlockY() + y, chunk.getBlockZ() + z);
                        if (canReplace(block) && isExposedToAir(block)) {
                            block.setType(ore.mat, false);
                        }
                    }
                }
            }
        }
    }

    void reveal(Block block) {
        realize(block);
    }

    void realize(Block block) {
        if (!canReplace(block)) return;
        if (plugin.isPlayerPlaced(block)) return;
        ChunkCoordinate coord = ChunkCoordinate.of(block);
        OreChunk chunk = getAndGenerate(coord);
        OreType ore = chunk.at(block);
        if (ore == null) return;
        if (ore.mat == null) return;
        block.setType(ore.mat, false);
    }

    Schematic.PasteResult revealDungeon(Block block) {
        ChunkCoordinate chunkCoord = new ChunkCoordinate(block.getX() >> 4, 0, block.getZ() >> 4);
        Schematic.PasteResult result = revealDungeon(chunkCoord);
        if (result != null) return result;
        result = revealDungeon(chunkCoord.getRelative(1, 0, 0));
        if (result != null) return result;
        result = revealDungeon(chunkCoord.getRelative(-1, 0, 0));
        if (result != null) return result;
        result = revealDungeon(chunkCoord.getRelative(0, 0, 1));
        if (result != null) return result;
        result = revealDungeon(chunkCoord.getRelative(0, 0, -1));
        if (result != null) return result;
        result = revealDungeon(chunkCoord.getRelative(1, 0, -1));
        if (result != null) return result;
        result = revealDungeon(chunkCoord.getRelative(1, 0, 1));
        if (result != null) return result;
        result = revealDungeon(chunkCoord.getRelative(-1, 0, 1));
        if (result != null) return result;
        result = revealDungeon(chunkCoord.getRelative(-1, 0, -1));
        if (result != null) return result;
        return null;
    }

    Schematic.PasteResult revealDungeon(ChunkCoordinate chunkCoord) {
        if (dungeonChance <= 0) return null;
        if (revealedDungeons.contains(chunkCoord)) return null;
        revealedDungeons.add(chunkCoord);
        Block zeroBlock = chunkCoord.getBlockAtY(0, getWorld());
        if (plugin.isPlayerPlaced(zeroBlock)) return null;
        plugin.setPlayerPlaced(zeroBlock);
        OreChunk oreChunk = generatedChunks.get(chunkCoord);
        if (oreChunk == null) oreChunk = OreChunk.of(chunkCoord.getBlock(getWorld()));
        Special special = Special.of(oreChunk.getBiome());
        Vec3 dungeonOffset = getDungeonOffset(oreChunk, special);
        if (dungeonOffset == null) return null;
        List<Schematic> schematics = new ArrayList<>();
        String searchTag = special.name().toLowerCase();
        // Add schematics with matching tag
        for (Schematic schem: plugin.getDungeonSchematics().values()) {
            if (schem.getTags().contains(searchTag)) schematics.add(schem);
        }
        // If empty, add schematics without the default tags, or without any tags
        if (schematics.size() <= 3) {
            for (Schematic schem: plugin.getDungeonSchematics().values()) {
                if (schem.getTags().isEmpty()) schematics.add(schem);
                else if (schem.getTags().contains("default")) schematics.add(schem);
            }
        }
        if (schematics.isEmpty()) {
            plugin.getLogger().warning("No schematics found!");
            return null;
        }
        DungeonChunk dc = new DungeonChunk(chunkCoord.getX(), chunkCoord.getZ(), seed);
        Random rnd = new Random(dc.hashCode());
        Schematic schem = schematics.get(rnd.nextInt(schematics.size()));
        // TODO fix rotation method and uncomment this.
        // int rotation = rnd.nextInt(4);
        // for (int i = 0; i < rotation; ++i) schem = schem.rotate();
        Block revealBlock = chunkCoord.getBlockAtY(0, getWorld()).getRelative(dungeonOffset.x, dungeonOffset.y, dungeonOffset.z);
        Schematic.PasteResult pasteResult = schem.paste(revealBlock);
        spawnLoot(pasteResult.getChests());
        Block centerBlock = revealBlock.getRelative(schem.getSizeX()/2, schem.getSizeY()/2, schem.getSizeZ()/2);
        centerBlock.getWorld().playSound(centerBlock.getLocation().add(0.5, 0.5, 0.5), Sound.AMBIENT_CAVE, 1.0f, 1.0f);
        return pasteResult;
    }

    @Value static class Slot {
        int chest, slot;
    }
    void spawnLoot(List<Chest> chests) {
        if (chests == null || chests.isEmpty()) return;
        List<Slot> slots = new ArrayList<>();
        for (int j = 0; j < chests.size(); ++j) {
            Chest chest = chests.get(j);
            for (int i = 0; i < chest.getInventory().getSize(); ++i) {
                slots.add(new Slot(j, i));
            }
        }
        Collections.shuffle(slots, random);
        Iterator<Slot> slotIter = slots.iterator();
        if (getLootItems().isEmpty()) return;
        int total = lootMedian + random.nextInt(lootVariance) - random.nextInt(lootVariance);
        for (int j = 0; j < total; ++j) {
            if (slotIter.hasNext()) {
                Slot slot = slotIter.next();
                chests.get(slot.chest).getInventory().setItem(slot.slot, getRandomLootItem());
            }
        }
    }

    private List<LootItem> getLootItems() {
        if (lootItems == null) {
            lootItems = new ArrayList<LootItem>();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "loot.yml"));
            ConfigurationSection section = config.getConfigurationSection(worldName);
            if (section == null) section = config.getConfigurationSection("default");
            if (section != null) {
                lootMedian = section.getInt("amount.Median", 16);
                lootVariance = section.getInt("amount.Variance", 8);
                for (Map<?, ?>map: section.getMapList("items")) {
                    ConfigurationSection tmp = section.createSection("tmp", map);
                    String customId = tmp.getString("CustomID");
                    ItemStack item;
                    if (customId == null) {
                        item = tmp.getItemStack("item");
                    } else {
                        int amount = tmp.getInt("amount", 1);
                        try {
                            item = CustomPlugin.getInstance().getItemManager().spawnItemStack(customId, amount);
                        } catch (IllegalArgumentException iae) {
                            item = null;
                            plugin.getLogger().warning("Custom loot ID not found: " + customId);
                        }
                    }
                    if (item != null) {
                        int weight = tmp.getInt("weight", 1);
                        lootItems.add(new LootItem(item, weight));
                    }
                }
            }
        }
        return lootItems;
    }

    public ItemStack getRandomLootItem() {
        int total = 0;
        for (LootItem lootItem: getLootItems()) {
            total += lootItem.getWeight();
        }
        total = random.nextInt(total);
        LootItem result = null;
        for (LootItem lootItem: getLootItems()) {
            total -= lootItem.getWeight();
            if (total < 0) {
                result = lootItem;
                break;
            }
        }
        ItemStack item = result.getItem().clone();
        if (item.getAmount() > 1) {
            item.setAmount(1 + random.nextInt(item.getAmount() - 1));
        }
        return item;
    }
}

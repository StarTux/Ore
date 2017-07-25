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
import org.bukkit.material.MaterialData;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldGenerator {
    final OrePlugin plugin;
    final String worldName;
    final int chunkRevealRadius = 3;
    final int chunkRevealRadiusSquared = chunkRevealRadius * chunkRevealRadius;
    final Random random = new Random(System.currentTimeMillis());
    final static BlockFace[] NBORS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
    final long worldSeed;
    final Set<ChunkCoordinate> revealedDungeons = new HashSet<>();
    final Map<Block, Integer> spawnerSpawns = new HashMap<>();
    Halloween halloween = null;

    static public enum Special {
        NONE, MESA, OCEAN, DESERT, JUNGLE, ICE, MUSHROOM, FOREST, SAVANNA, PLAINS;
        static Special of(Biome biome) {
            switch (biome) {
            case MESA:
            case MESA_CLEAR_ROCK:
            case MESA_ROCK:
            case MUTATED_MESA:
            case MUTATED_MESA_CLEAR_ROCK:
            case MUTATED_MESA_ROCK:
                return MESA;
            case DESERT:
            case DESERT_HILLS:
            case MUTATED_DESERT:
                return DESERT;
            case DEEP_OCEAN:
            case FROZEN_OCEAN:
            case OCEAN:
                return OCEAN;
            case JUNGLE:
            case JUNGLE_EDGE:
            case JUNGLE_HILLS:
            case MUTATED_JUNGLE:
            case MUTATED_JUNGLE_EDGE:
                return JUNGLE;
            case COLD_BEACH:
            case FROZEN_RIVER:
            case ICE_FLATS:
            case ICE_MOUNTAINS:
            case MUTATED_ICE_FLATS:
            case MUTATED_TAIGA_COLD:
            case TAIGA_COLD:
            case TAIGA_COLD_HILLS:
                return ICE;
            case MUSHROOM_ISLAND:
            case MUSHROOM_ISLAND_SHORE:
                return MUSHROOM;
            case BIRCH_FOREST:
            case BIRCH_FOREST_HILLS:
            case FOREST:
            case FOREST_HILLS:
            case MUTATED_BIRCH_FOREST:
            case MUTATED_BIRCH_FOREST_HILLS:
            case MUTATED_FOREST:
            case MUTATED_ROOFED_FOREST:
            case ROOFED_FOREST:
                return FOREST;
            case SAVANNA:
            case MUTATED_SAVANNA:
            case SAVANNA_ROCK:
            case MUTATED_SAVANNA_ROCK:
                return SAVANNA;
            case PLAINS:
            case MUTATED_PLAINS:
                return PLAINS;
            default:
                return NONE;
            }
        }
    }

    @Value static final class Vec3{final int x, y, z;}
    @Value static final class Vec2{final int x, y;}
    @Value
    static class LootItem {
        ItemStack item;
        int weight;
    }

    private boolean shouldStop = false;
    private int dungeonChance = 0;
    private long seed; // Defaults to world seed
    private int spawnerLimit = 0;
    private List<LootItem> lootItems = null;
    private int lootMedian = 0, lootVariance = 0;
    boolean debug = false;

    // Call once!
    void configure(ConfigurationSection config) {
        dungeonChance = config.getInt("Dungeons", dungeonChance);
        seed = config.getLong("Seed", seed);
        spawnerLimit = config.getInt("SpawnerLimit", spawnerLimit);
        debug = config.getBoolean("Debug", debug);
        Random random = new Random(seed);
        plugin.getLogger().info("Loaded world " + worldName + " Dungeons=" + dungeonChance + " SpawnerLimit=" + spawnerLimit + " Seed=" + seed + " Debug=" + debug);
        if (config.getBoolean("Halloween", false)) {
            halloween = new Halloween(this);
            plugin.getLogger().info("Halloween enabled in " + worldName);
        }
    }

    // Async
    final LinkedBlockingQueue<OreChunk> queue = new LinkedBlockingQueue<OreChunk>();

    // Sync
    static class PlayerData {
        final Map<ChunkCoordinate, Long> shownChunks = new HashMap<>();
    }
    BukkitRunnable syncTask = null;
    final Map<ChunkCoordinate, OreChunk> generatedChunks = new HashMap<>();
    final Set<ChunkCoordinate> scheduledChunks = new HashSet<>();
    final Map<UUID, PlayerData> playerMap = new HashMap<>();

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
        if (special == Special.OCEAN) return null;
        Random rnd = new Random(new DungeonChunk(chunk.x, chunk.z, seed).hashCode());
        if (dungeonChance < 100 && rnd.nextInt(100) >= dungeonChance) return null;
        return new Vec3(rnd.nextInt(16) - 8,
                        rnd.nextInt(32) + 4,
                        rnd.nextInt(16) - 8);
    }

    boolean generateVein(OreChunk chunk, OreType ore, Random rnd, int size) {
        List<Vec3> todo = new ArrayList<>(size);
        List<Vec3> found = new ArrayList<>(size);
        Set<Vec3> done = new HashSet<>();
        if (chunk.y == 0) {
            todo.add(new Vec3(rnd.nextInt(16), 5 + rnd.nextInt(11), rnd.nextInt(16)));
        } else {
            todo.add(new Vec3(rnd.nextInt(16), rnd.nextInt(16), rnd.nextInt(16)));
        }
        while (!todo.isEmpty() && found.size() < size) {
            Collections.shuffle(todo, rnd);
            Vec3 vec = todo.remove(todo.size() - 1);
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
        if (found.size() < size / 2) return false;
        for (Vec3 vec: found) {
            chunk.set(vec.x, vec.y, vec.z, ore);
        }
        return true;
    }

    void generateVein(OreChunk chunk, OreType ore, Random rnd, int size, int tries) {
        for (int i = 0; i < tries; i += 1) {
            if (generateVein(chunk, ore, rnd, size)) return;
        }
    }

    @Value final static class ChunkSeed { final int x, y, z; final long seed; }
    void generate(OreChunk chunk) {
        Special special = Special.of(chunk.getBiome());
        Random rnd = new Random(new ChunkSeed(chunk.x, chunk.y, chunk.z, seed).hashCode());
        int g = special == Special.MESA ? 4 : 2;
        if (chunk.y < 1) generateVein(chunk, OreType.DIAMOND,  rnd,  8,  1);
        if (chunk.y < 4) generateVein(chunk, OreType.IRON,     rnd,  9, 20);
        if (chunk.y < g) generateVein(chunk, OreType.GOLD,     rnd,  9,  2);
        if (chunk.y < 8) generateVein(chunk, OreType.COAL,     rnd, 17, 20);
        if (chunk.y < 1) generateVein(chunk, OreType.REDSTONE, rnd,  8,  8);
        if (chunk.y < 2) generateVein(chunk, OreType.LAPIS,    rnd,  7,  1);
        if (special == Special.OCEAN) {
            if (chunk.y < 2) generateVein(chunk, OreType.SEA_LANTERN, rnd, 5, 1);
        } else if (chunk.slime) {
            if (chunk.y < 2) generateVein(chunk, OreType.SLIME, rnd, 5, 1);
        } else {
            if (chunk.y < 1) generateVein(chunk, OreType.EMERALD, rnd, 5, 1);
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
        if (halloween != null) halloween.cleanup();
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
            }.runTaskLater(plugin, 20L);
        }
    }

    // Callback from the async thread
    void syncDidGenerateChunk(OreChunk chunk) {
        ChunkCoordinate coord = chunk.getCoordinate();
        long now = System.currentTimeMillis();
        for (Player player: getWorld().getPlayers()) {
            if (ChunkCoordinate.of(player.getLocation()).distanceSquared(coord) <= chunkRevealRadiusSquared) {
                getPlayerData(player.getUniqueId()).shownChunks.put(coord, now);
                revealChunkToPlayer(chunk, player);
            }
        }
        generatedChunks.put(coord, chunk);
        scheduledChunks.remove(coord);
        chunk.setUsed();
    }

    PlayerData getPlayerData(UUID uuid) {
        PlayerData playerData = playerMap.get(uuid);
        if (playerData == null) {
            playerData = new PlayerData();
            playerMap.put(uuid, playerData);
        }
        return playerData;
    }

    void syncRun() {
        // Garbage collect chunks
        for (Iterator<Map.Entry<ChunkCoordinate, OreChunk> > it = generatedChunks.entrySet().iterator(); it.hasNext();) {
            Map.Entry<ChunkCoordinate, OreChunk> en = it.next();
            if (en.getValue().isTooOld()) {
                it.remove();
            }
        }
        for (Iterator<UUID> iter = playerMap.keySet().iterator(); iter.hasNext();) {
            Player player = plugin.getServer().getPlayer(iter.next());
            if (player == null || player.getWorld() != getWorld()) {
                iter.remove();
            }
        }
        for (Player player: getWorld().getPlayers()) {
            UUID uuid = player.getUniqueId();
            PlayerData playerData = getPlayerData(uuid);
            ChunkCoordinate playerLocation = ChunkCoordinate.of(player.getLocation());
            revealPlayerIter(playerData, player, playerLocation);
        }
    }

    private void revealPlayerIter(PlayerData playerData, Player player, ChunkCoordinate center) {
        final int R = chunkRevealRadius;
        long now = System.currentTimeMillis();
        boolean dutyDone = false;
        for (int y = -R; y <= R; ++y) {
            for (int z = -R; z <= R; ++z) {
                for (int x = -R; x <= R; ++x) {
                    OreChunk chunk = getOrGenerate(center.getRelative(x, y, z));
                    if (chunk != null) {
                        ChunkCoordinate coord = chunk.getCoordinate();
                        Long shown = playerData.shownChunks.get(coord);
                        if (shown == null || (!dutyDone && shown + 1000 * 30 < now)) {
                            playerData.shownChunks.put(coord, now);
                            revealChunkToPlayer(chunk, player);
                            dutyDone = true;
                        }
                    }
                }
            }
        }
        for (Iterator<ChunkCoordinate> iter = playerData.shownChunks.keySet().iterator(); iter.hasNext(); ) {
            if (iter.next().axisDistance(center) > R) iter.remove();
        }
    }

    static boolean isExposedToAir(Block block) {
        for (BlockFace dir: NBORS) {
            if (!block.getRelative(dir).getType().isOccluding()) return true;
        }
        return false;
    }

    boolean canReplace(Block block) {
        switch (block.getType()) {
        case STONE: break;
        default: return false;
        }
        if (plugin.isPlayerPlaced(block)) return false;
        return true;
    }

    void revealChunkToPlayer(OreChunk chunk, Player player) {
        if (player == null) return;
        World world = getWorld();
        if (!player.getWorld().equals(world)) return;
        for (int y = 0; y < OreChunk.SIZE; ++y) {
            for (int z = 0; z < OreChunk.SIZE; ++z) {
                for (int x = 0; x < OreChunk.SIZE; ++x) {
                    OreType ore = chunk.get(x, y, z);
                    MaterialData mat = ore.getMaterialData();
                    if (mat != null) {
                        Block block = world.getBlockAt(chunk.getBlockX() + x, chunk.getBlockY() + y, chunk.getBlockZ() + z);
                        if (canReplace(block) && isExposedToAir(block)) {
                            player.sendBlockChange(block.getLocation(), mat.getItemType(), mat.getData());
                        }
                    }
                }
            }
        }
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

    void realize(Block block) {
        if (!canReplace(block)) return;
        if (plugin.isPlayerPlaced(block)) return;
        ChunkCoordinate coord = ChunkCoordinate.of(block);
        OreChunk chunk = getAndGenerate(coord);
        OreType ore = chunk.at(block);
        if (ore == null) return;
        MaterialData mat = ore.getMaterialData();
        if (mat != null) {
            block.setTypeIdAndData(mat.getItemTypeId(), mat.getData(), false);
        }
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
        if (schematics.isEmpty()) {
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
        int rotation = rnd.nextInt(4);
        for (int i = 0; i < rotation; ++i) schem = schem.rotate();
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
        for (Chest chest: chests) chest.update();
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

    void reveal(Block block) {
        ChunkCoordinate coord = ChunkCoordinate.of(block);
        OreChunk chunk = getOrGenerate(coord);
        if (chunk == null) return;
        OreType ore = chunk.at(block);
        MaterialData mat = ore.materialData;
        if (mat == null) return;
        if (!canReplace(block)) return;
        if (plugin.isPlayerPlaced(block)) return;
        for (Player player: block.getWorld().getPlayers()) {
            if (ChunkCoordinate.of(player.getLocation()).distanceSquared(coord) <= chunkRevealRadiusSquared) {
                player.sendBlockChange(block.getLocation(), mat.getItemType(), mat.getData());
            }
        }
    }

    void onSpawnerSpawn(final Block block) {
        if (spawnerLimit <= 0) return;
        Integer val = spawnerSpawns.get(block);
        if (val == null) val = 0;
        else val += 1;
        spawnerSpawns.put(block, val);
        int rnd = random.nextInt(spawnerLimit);
        if (rnd < val) {
            new BukkitRunnable() {
                @Override public void run() {
                    block.getWorld().createExplosion(block.getLocation().add(0.5, 0.5, 0.5), 4f, true);
                    block.setType(Material.AIR);
                }
            }.runTask(plugin);
            spawnerSpawns.remove(block);
            if (debug) {
                plugin.getLogger().info(String.format("Exploded spawner in %s at %d %d %d (%d/%d)", block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), val, spawnerLimit));
            }
        }
    }
}

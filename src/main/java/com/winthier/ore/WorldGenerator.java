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
    final MaterialData stoneMat = new MaterialData(Material.STONE);
    final int chunkRevealRadius = 3;
    final int chunkRevealRadiusSquared = chunkRevealRadius * chunkRevealRadius * chunkRevealRadius;
    final Random random = new Random(System.currentTimeMillis());
    final static BlockFace[] NBORS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
    final long worldSeed;
    final Set<ChunkCoordinate> revealedDungeons = new HashSet<>();
    final Map<Block, Integer> spawnerSpawns = new HashMap<>();
    Halloween halloween = null;

    static public enum Noise {
        // Do NOT change the order of this enum!
        DIAMOND, COAL, IRON, GOLD, REDSTONE, LAPIS, SPECIAL, MINI_CAVE, EMERALD, SLIME, DUNGEON;
    }
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

    @Value
    static class LootItem {
        ItemStack item;
        int weight;
    }

    final Map<Noise, OpenSimplexNoise> noises = new EnumMap<>(Noise.class);

    private boolean shouldStop = false;
    private boolean enableHotspots = false;
    private boolean enableSpecialBiomes = false;
    private boolean enableMiniCaves = false;
    private int dungeonChance = 0;
    private long seed; // Defaults to world seed
    private int spawnerLimit = 0;
    private List<LootItem> lootItems = null;
    private int lootMedian = 0, lootVariance = 0;
    boolean debug = false;

    // Call once!
    void configure(ConfigurationSection config) {
        enableHotspots = config.getBoolean("Hotspots", enableHotspots);
        enableSpecialBiomes = config.getBoolean("SpecialBiomes", enableSpecialBiomes);
        enableMiniCaves = config.getBoolean("MiniCaves", enableMiniCaves);
        dungeonChance = config.getInt("Dungeons", dungeonChance);
        seed = config.getLong("Seed", seed);
        spawnerLimit = config.getInt("SpawnerLimit", spawnerLimit);
        debug = config.getBoolean("Debug", debug);
        Random random = new Random(seed);
        for (Noise noise: Noise.values()) {
            noises.put(noise, new OpenSimplexNoise(random.nextLong())); // Not this.random!
        }
        plugin.getLogger().info("Loaded world " + worldName + " Hotspots=" + enableHotspots + " SpecialBiomes=" + enableSpecialBiomes + " MiniCaves=" + enableMiniCaves + " Dungeons=" + dungeonChance + " SpawnerLimit=" + spawnerLimit + " Seed=" + seed + " Debug=" + debug);
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

    private int getHotspotBaseHeight(Noise noise) {
        switch (noise) {
        case DIAMOND: return 32;
        case LAPIS: return 32;
        case IRON: return 64;
        case COAL: return 64;
        case GOLD: return 32;
        case REDSTONE: return 32;
        case EMERALD: return 32;
        default: return 16;
        }
    }

    int getOreLevel(Noise noise, int chunkX, int chunkZ) {
        int base = getHotspotBaseHeight(noise);
        double val = noises.get(noise).at(chunkX, 0, chunkZ, 5.0);
        if (val > 0.5) {
            return base*4;
        } else if (val > 0.4) {
            return base*2;
        } else if (val > 0.0) {
            return base;
        } else {
            return 0;
        }
    }

    boolean isSlimeChunk(OreChunk chunk) {
        int x = chunk.getX();
        int z = chunk.getZ();
        Random tmprnd = new Random(worldSeed + (long) (x * x * 0x4c1906) + (long) (x * 0x5ac0db) + (long) (z * z) * 0x4307a7L + (long) (z * 0x5f24f) ^ 0x3ad8025f);
        return tmprnd.nextInt(10) == 0;
    }

    @Value static class DungeonChunk { int x, z; long seed; }
    /**
     * @return -1 if this chunk does not contain a dungeon,
     * positive number for the y level of the dungeon.
     */
    int getDungeonLevel(OreChunk chunk, Special special) {
        if (chunk.getBiome() == Biome.DEEP_OCEAN) return -1;
        Random rnd = new Random(new DungeonChunk(chunk.x, chunk.z, seed).hashCode());
        if (dungeonChance < 100 && rnd.nextInt(100) >= dungeonChance) return -1;
        int result = 5 + rnd.nextInt(43);
        if (special == Special.OCEAN && result > 16) return -1;
        return result;
    }

    void generate(OreChunk chunk) {
        int cx = chunk.getBlockX();
        int cy = chunk.getBlockY();
        int cz = chunk.getBlockZ();

        int diamondLevel = 16;
        int lapisLevel = 32;
        int redstoneLevel = 16;
        int ironLevel = 64;
        int goldLevel = 32;
        int emeraldLevel = 0;

        if (enableHotspots) {
            int x = chunk.getX();
            int y = chunk.getZ();
            diamondLevel = getOreLevel(Noise.DIAMOND, x, y);
            lapisLevel = getOreLevel(Noise.LAPIS, x, y);
            redstoneLevel = getOreLevel(Noise.REDSTONE, x, y);
            ironLevel = getOreLevel(Noise.IRON, x, y);
            goldLevel = getOreLevel(Noise.GOLD, x, y);
            emeraldLevel = getOreLevel(Noise.EMERALD, x, y);
        }

        Special special = Special.of(chunk.getBiome());
        if (special == Special.OCEAN) diamondLevel = 0;
        boolean isSlimeChunk = isSlimeChunk(chunk);
        int dungeonLevel = dungeonChance > 0 ? getDungeonLevel(chunk, special) : -1;

        for (int dy = 0; dy < OreChunk.SIZE; ++dy) {
            for (int dz = 0; dz < OreChunk.SIZE; ++dz) {
                for (int dx = 0; dx < OreChunk.SIZE; ++dx) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (y <= 0) continue;
                    // Dungeon
                    if (dungeonLevel > -1 && y >= dungeonLevel && y < dungeonLevel + OreChunk.SIZE) {
                        chunk.set(dx, dy, dz, OreType.DUNGEON);
                        continue;
                    }
                    // Special Biomes
                    if (!enableSpecialBiomes) {
                        // Do nothing
                    } else if (special == Special.DESERT || special == Special.JUNGLE || special == Special.SAVANNA) { // Fossils
                        if (y >= 32) {
                            if (noises.get(Noise.SPECIAL).abs(x, y, z, 8.0) > 0.65 &&
                                noises.get(Noise.SPECIAL).at(x, y, z, 1.0) > 0.0) {
                                chunk.set(dx, dy, dz, OreType.FOSSIL);
                            }
                        }
                    } else if (special == Special.OCEAN) { // Prismarine
                        if (y < 64) {
                            double pri = noises.get(Noise.SPECIAL).abs(x, y, z, 6.0);
                            if (pri > 0.80) {
                                chunk.set(dx, dy, dz, OreType.SEA_LANTERN);
                            } else if (pri > 0.68) {
                                chunk.set(dx, dy, dz, OreType.PRISMARINE);
                            }
                        }
                    } else { // Clay
                        if (y <= 64 && y >= 32) {
                            if (noises.get(Noise.SPECIAL).abs(x, y, z, 8.0) > 0.65) {
                                chunk.set(dx, dy, dz, OreType.CLAY);
                            }
                        }
                    }
                    // Slime
                    if (isSlimeChunk && y <= 40) {
                        if (noises.get(Noise.SLIME).abs(x, y, z, 5.0) > 0.79) {
                            chunk.set(dx, dy, dz, OreType.SLIME);
                        }
                    }
                    // Coal
                    if (y <= 128) {
                        if (noises.get(Noise.COAL).abs(x, y, z, 5.0) > 0.66) {
                            chunk.set(dx, dy, dz, OreType.COAL_ORE);
                        }
                    }
                    // Mini Caves
                    if (enableMiniCaves && y <= 32) {
                        double dun = noises.get(Noise.MINI_CAVE).abs(x, y, z, 12.0, 6.0, 12.0);
                        if (dun > 0.63) {
                            chunk.set(dx, dy, dz, OreType.MINI_CAVE);
                        }
                    }
                    // Iron
                    if (y <= ironLevel) {
                        double iro = noises.get(Noise.IRON).abs(x, y, z, 5.0);
                        if (iro  > 0.71) {
                            chunk.set(dx, dy, dz, OreType.IRON_ORE);
                        } else if (iro > 0.58) {
                            chunk.setIfEmpty(dx, dy, dz, OreType.ANDESITE);
                        }
                    }
                    // Gold
                    if (y <= goldLevel ||
                        (special == Special.MESA && y >= 32 && y <= 79)) {
                        double gol = noises.get(Noise.GOLD).abs(x, y, z, 5.0);
                        if (gol > 0.78) {
                            chunk.set(dx, dy, dz, OreType.GOLD_ORE);
                        } else if (gol > 0.58) {
                            chunk.setIfEmpty(dx, dy, dz, OreType.DIORITE);
                        }
                    }
                    // Redstone
                    if (y <= redstoneLevel) {
                        double red = noises.get(Noise.REDSTONE).abs(x, y, z, 5.0);
                        if (red > 0.73) {
                            chunk.set(dx, dy, dz, OreType.REDSTONE_ORE);
                        }
                    }
                    // Lapis
                    if (y <= lapisLevel) {
                        double lap = noises.get(Noise.LAPIS).abs(x, y, z, 4.0);
                        if (lap > 0.79) { // used to be 0.81
                            chunk.set(dx, dy, dz, OreType.LAPIS_ORE);
                        } else if (lap > 0.55) {
                            chunk.setIfEmpty(dx, dy, dz, OreType.DIORITE);
                        }
                    }
                    // Emerald
                    if (y <= emeraldLevel) {
                        double eme = noises.get(Noise.EMERALD).abs(x, y, z, 4.0);
                        if (eme > 0.79) {
                            chunk.set(dx, dy, dz, OreType.EMERALD_ORE);
                        }
                    }
                    // Diamond
                    if (y <= diamondLevel) {
                        double dia = noises.get(Noise.DIAMOND).abs(x, y, z, 4.0);
                        if (dia > 0.79) {
                            chunk.set(dx, dy, dz, OreType.DIAMOND_ORE);
                        } else if (dia > 0.58) {
                            chunk.setIfEmpty(dx, dy, dz, OreType.GRANITE);
                        }
                    }
                }
            }
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

    void revealChunkToPlayer(OreChunk chunk, Player player) {
        if (player == null) return;
        World world = getWorld();
        if (!player.getWorld().equals(world)) return;
        for (int y = 0; y < OreChunk.SIZE; ++y) {
            for (int z = 0; z < OreChunk.SIZE; ++z) {
                for (int x = 0; x < OreChunk.SIZE; ++x) {
                    OreType ore = chunk.get(x, y, z);
                    MaterialData mat = ore.getMaterialData();
                    if (mat != null && !ore.isHidden()) {
                        Block block = world.getBlockAt(chunk.getBlockX() + x, chunk.getBlockY() + y, chunk.getBlockZ() + z);
                        if (block.getType() == Material.STONE &&
                            !plugin.isPlayerPlaced(block) &&
                            isExposedToAir(block)) {
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
        if (block.getType() != Material.STONE) return;
        if (plugin.isPlayerPlaced(block)) return;
        ChunkCoordinate coord = ChunkCoordinate.of(block);
        OreChunk chunk = getAndGenerate(coord);
        OreType ore = chunk.at(block);
        if (ore == null || ore.isHidden()) return;
        MaterialData mat = ore.getMaterialData();
        if (mat != null) {
            block.setTypeIdAndData(mat.getItemTypeId(), mat.getData(), false);
        }
    }

    Schematic.PasteResult revealDungeon(Block block) {
        ChunkCoordinate chunkCoord = ChunkCoordinate.of(block);
        Block zeroBlock = chunkCoord.getBlockAtY(0, getWorld());
        chunkCoord = ChunkCoordinate.of(zeroBlock);
        if (revealedDungeons.contains(chunkCoord)) return null;
        revealedDungeons.add(chunkCoord);
        if (plugin.isPlayerPlaced(zeroBlock)) return null;
        plugin.setPlayerPlaced(zeroBlock);
        OreChunk oreChunk = generatedChunks.get(chunkCoord);
        if (oreChunk == null) oreChunk = OreChunk.of(chunkCoord.getBlock(getWorld()));
        Special special = Special.of(oreChunk.getBiome());
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
        int offsetX = OreChunk.SIZE - schem.getSizeX();
        int offsetY = OreChunk.SIZE - schem.getSizeY();
        int offsetZ = OreChunk.SIZE - schem.getSizeZ();
        if (offsetX > 0) offsetX = rnd.nextInt(offsetX + 1);
        if (offsetY > 0) offsetY = rnd.nextInt(offsetY + 1);
        if (offsetZ > 0) offsetZ = rnd.nextInt(offsetZ + 1);
        int dungeonLevel = getDungeonLevel(oreChunk, special);
        Block revealBlock = chunkCoord.getBlockAtY(dungeonLevel, getWorld()).getRelative(offsetX, 0, offsetZ);
        Schematic.PasteResult pasteResult = schem.paste(revealBlock);
        spawnLoot(pasteResult.getChests());
        Block centerBlock = revealBlock.getRelative(schem.getSizeX()/2, schem.getSizeY()/2, schem.getSizeZ()/2);
        block.getWorld().playSound(centerBlock.getLocation().add(0.5, 0.5, 0.5), Sound.AMBIENT_CAVE, 1.0f, 1.0f);
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

    static final List<MaterialData> FLOOR_OCEAN = Arrays.asList(
                                                                new MaterialData(Material.PRISMARINE, (byte)1),
                                                                new MaterialData(Material.PRISMARINE, (byte)2));
    static final List<MaterialData> FLOOR_DESERT = Arrays.asList(
                                                                 new MaterialData(Material.MAGMA),
                                                                 new MaterialData(Material.RED_SANDSTONE),
                                                                 new MaterialData(Material.HARD_CLAY));
    static final List<MaterialData> FLOOR_ICE = Arrays.asList(
                                                              new MaterialData(Material.ICE),
                                                              new MaterialData(Material.PACKED_ICE));
    static final List<MaterialData> FLOOR_JUNGLE = Arrays.asList(
                                                                 new MaterialData(Material.SMOOTH_BRICK),
                                                                 new MaterialData(Material.SMOOTH_BRICK, (byte)1),
                                                                 new MaterialData(Material.SMOOTH_BRICK, (byte)2),
                                                                 new MaterialData(Material.SMOOTH_BRICK, (byte)3));
    static final List<MaterialData> FLOOR_MUSHROOM = Arrays.asList(
                                                                   new MaterialData(Material.HUGE_MUSHROOM_1, (byte)14), // All sides
                                                                   new MaterialData(Material.HUGE_MUSHROOM_2, (byte)14));
    static final List<MaterialData> FLOOR_DEFAULT = Arrays.asList(
                                                                  new MaterialData(Material.COBBLESTONE),
                                                                  new MaterialData(Material.MOSSY_COBBLESTONE));

    void revealMiniCave(Block block) {
        LinkedList<Block> todo = new LinkedList<>();
        Set<Block> found = new HashSet<>();
        Set<Block> done = new HashSet<>();
        todo.add(block);
        while (!todo.isEmpty() && found.size() < 1000) {
            Block doBlock = todo.removeFirst();
            if ((doBlock.getType() == Material.STONE ||
                 doBlock.getType() == Material.AIR) &&
                !plugin.isPlayerPlaced(doBlock) &&
                getOreAt(doBlock) == OreType.MINI_CAVE) {
                found.add(doBlock);
                for (BlockFace dir: NBORS) {
                    Block nborBlock = doBlock.getRelative(dir);
                    if (!done.contains(nborBlock)) {
                        todo.add(nborBlock);
                        done.add(nborBlock);
                    }
                }
            }
        }
        Special special = Special.of(block.getBiome());
        List<MaterialData> floorBlocks;
        if (special == Special.OCEAN) {
            floorBlocks = FLOOR_OCEAN;
        } else if (special == Special.DESERT || special == Special.MESA || special == Special.SAVANNA) {
            floorBlocks = FLOOR_DESERT;
        } else if (special == Special.ICE) {
            floorBlocks = FLOOR_ICE;
        } else if (special == Special.JUNGLE) {
            floorBlocks = FLOOR_JUNGLE;
        } else if (special == Special.FOREST || special == Special.MUSHROOM) {
            floorBlocks = FLOOR_MUSHROOM;
        } else {
            floorBlocks = FLOOR_DEFAULT;
        }
        Set<Block> addLater = new HashSet<>();
        for (Block foundBlock: found) {
            if (!found.contains(foundBlock.getRelative(BlockFace.DOWN))) {
                // Set floor
                MaterialData mat = floorBlocks.get(random.nextInt(floorBlocks.size()));
                foundBlock.setTypeIdAndData(mat.getItemTypeId(), mat.getData(), true);
                // Try to expand to 3 height
                for (int i = 0; i < 3; ++i) {
                    Block laterBlock = foundBlock.getRelative(BlockFace.UP, i + 1);
                    if (!found.contains(laterBlock) &&
                        (laterBlock.getType() == Material.STONE ||
                         laterBlock.getType() == Material.AIR) &&
                        !plugin.isPlayerPlaced(laterBlock)) {
                        laterBlock.setType(Material.AIR, false);
                        addLater.add(laterBlock);
                    }
                }
            } else {
                foundBlock.setType(Material.AIR, false);
            }
        }
        found.addAll(addLater);
        int spawnerCount = special != Special.ICE ? random.nextInt(3) : 0;
        int mobCount = 1 + random.nextInt(5);
        int chestCount = random.nextInt(2);
        List<Block> blockList = new ArrayList<>(found);
        List<Chest> chests = new ArrayList<>(chestCount);
        Collections.shuffle(blockList, random);
        for (Block foundBlock: blockList) {
            if (!found.contains(foundBlock.getRelative(BlockFace.DOWN)) &&
                found.contains(foundBlock.getRelative(BlockFace.UP, 1)) &&
                found.contains(foundBlock.getRelative(BlockFace.UP, 2))) {
                if (spawnerCount > 0) {
                    spawnerCount -= 1;
                    Block spawnerBlock = foundBlock.getRelative(0, 1 + random.nextInt(2), 0);
                    spawnerBlock.setType(Material.MOB_SPAWNER);
                    CreatureSpawner state = (CreatureSpawner)spawnerBlock.getState();
                    if (special == Special.DESERT || special == Special.MESA || special == Special.SAVANNA) {
                        switch (random.nextInt(2)) {
                        case 0: state.setSpawnedType(EntityType.BLAZE); break;
                        case 1: state.setSpawnedType(EntityType.HUSK); break;
                        }
                    } else if (special == Special.ICE) {
                        switch (random.nextInt(5)) {
                        case 0: state.setSpawnedType(EntityType.ZOMBIE); break;
                        case 1: state.setSpawnedType(EntityType.STRAY); break;
                        case 2: state.setSpawnedType(EntityType.SPIDER); break;
                        case 3: state.setSpawnedType(EntityType.CAVE_SPIDER); break;
                        case 4: state.setSpawnedType(EntityType.CREEPER); break;
                        }
                    } else {
                        switch (random.nextInt(5)) {
                        case 0: state.setSpawnedType(EntityType.ZOMBIE); break;
                        case 1: state.setSpawnedType(EntityType.SKELETON); break;
                        case 2: state.setSpawnedType(EntityType.SPIDER); break;
                        case 3: state.setSpawnedType(EntityType.CAVE_SPIDER); break;
                        case 4: state.setSpawnedType(EntityType.CREEPER); break;
                        }
                    }
                } else if (mobCount > 0) {
                    mobCount -= 1;
                    Block baseBlock = foundBlock.getRelative(0, 1, 0);
                    Location loc = baseBlock.getLocation().add(0.5, 0.0, 0.5);
                    if (special == Special.ICE) {
                            switch (random.nextInt(3)) {
                            case 0:
                                PolarBear polarBear = foundBlock.getWorld().spawn(loc, PolarBear.class);
                                if (miniCaveHasSpaceForFatMob(found, baseBlock)) {
                                    polarBear.setAdult();
                                } else {
                                    polarBear.setBaby();
                                }
                                break;
                            case 1:
                                polarBear = foundBlock.getWorld().spawn(loc, PolarBear.class);
                                polarBear.setBaby();
                                break;
                            case 2:
                                Stray stray = foundBlock.getWorld().spawn(loc, Stray.class);
                                break;
                            }
                    } else {
                        EntityType et = randomEntityType(special);
                        if (et != EntityType.SPIDER || miniCaveHasSpaceForFatMob(found, baseBlock)) {
                            foundBlock.getWorld().spawnEntity(loc, et);
                        }
                    }
                } else if (chestCount > 0) {
                    chestCount -= 1;
                    Block chestBlock = foundBlock.getRelative(0, 1, 0);
                    chestBlock.setType(Material.CHEST);
                    Chest chest = (Chest)chestBlock.getState();
                    chests.add(chest);
                }
            }
        }
        // Reveal walls
        for (Block foundBlock: found) {
            for (BlockFace dir: NBORS) {
                Block nbor = foundBlock.getRelative(dir);
                if (!found.contains(nbor)) reveal(nbor);
            }
        }
        // Fill chests
        spawnLoot(chests);
    }

    final static BlockFace[] HOR = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST};
    private boolean miniCaveHasSpaceForFatMob(Set<Block> found, Block block) {
        for (BlockFace face: HOR) {
            if (!found.contains(block.getRelative(face))) return false;
        }
        return true;
    }

    final static EntityType[] ENT = {
        EntityType.ZOMBIE,
        EntityType.ZOMBIE,
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.SKELETON,
        EntityType.SPIDER,
        EntityType.SPIDER,
        EntityType.CREEPER,
        EntityType.WITCH,
        EntityType.CAVE_SPIDER,
        EntityType.SILVERFISH,
        EntityType.SLIME,
        EntityType.VINDICATOR,
        EntityType.EVOKER,
        EntityType.OCELOT,
        EntityType.WOLF
    };
    final static EntityType[] ENT_DESERT = {
        EntityType.MAGMA_CUBE,
        EntityType.PIG_ZOMBIE,
        EntityType.BLAZE,
        EntityType.HUSK
    };
    EntityType randomEntityType(Special special) {
        if (special == Special.DESERT || special == Special.MESA || special == Special.SAVANNA) {
            return ENT_DESERT[random.nextInt(ENT_DESERT.length)];
        } else {
            return ENT[random.nextInt(ENT.length)];
        }
    }

    void reveal(Block block) {
        ChunkCoordinate coord = ChunkCoordinate.of(block);
        OreChunk chunk = getOrGenerate(coord);
        if (chunk == null) return;
        OreType ore = chunk.at(block);
        if (ore.isHidden()) return;
        MaterialData mat = ore.getMaterialData();
        if (mat == null) return;
        if (block.getType() != Material.STONE) return;
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
                    block.setType(Material.AIR);
                    block.getWorld().createExplosion(block.getLocation().add(0.5, 0.5, 0.5), 4f, true);
                }
            }.runTask(plugin);
            spawnerSpawns.remove(block);
            if (debug) {
                plugin.getLogger().info(String.format("Exploded spawner in %s at %d %d %d (%d/%d)", block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), val, spawnerLimit));
            }
        }
    }
}

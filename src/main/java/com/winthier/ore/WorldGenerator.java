package com.winthier.ore;

import com.winthier.exploits.bukkit.BukkitExploits;
import java.util.Arrays;
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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;
import org.bukkit.scheduler.BukkitRunnable;

class WorldGenerator {
    final String worldName;
    final MaterialData stoneMat = new MaterialData(Material.STONE);
    final int chunkRevealRadius = 3;

    static public enum Noise {
        DIAMOND, COAL, IRON, GOLD, REDSTONE, LAPIS, CLAY;
    }

    final Map<Noise, OpenSimplexNoise> noises = new EnumMap<>(Noise.class);

    private boolean shouldStop = false;
    boolean generateHotspots = true;

    // Async
    final LinkedBlockingQueue<OreChunk> queue = new LinkedBlockingQueue<OreChunk>();

    // Sync
    static class PlayerData {
        ChunkCoordinate revealedLocation;
        final Set<ChunkCoordinate> shownChunks = new HashSet<>();
    }
    BukkitRunnable syncTask = null;
    final Map<ChunkCoordinate, OreChunk> generatedChunks = new HashMap<>();
    final Set<ChunkCoordinate> scheduledChunks = new HashSet<>();
    final LinkedList<UUID> playerList = new LinkedList<>();
    final Map<UUID, PlayerData> playerMap = new HashMap<>();

    void debug(Player player) {
        player.sendMessage("Generated Chunks " + generatedChunks.size());
        player.sendMessage("Scheduled Chunks " + scheduledChunks.size());
        player.sendMessage("Player List " + playerList.size());
        player.sendMessage("Player Map " + playerMap.size());
    }

    WorldGenerator(String worldName) {
        this.worldName = worldName;
        
        Random random = new Random(Bukkit.getServer().getWorld(worldName).getSeed());
        for (Noise noise: Noise.values()) {
            noises.put(noise, new OpenSimplexNoise(random.nextLong()));
        }
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

    void generate(OreChunk chunk) {
        int cx = chunk.getBlockX();
        int cy = chunk.getBlockY();
        int cz = chunk.getBlockZ();

        int diamondLevel = 16;
        int lapisLevel = 32;
        int redstoneLevel = 16;
        int ironLevel = 64;
        int goldLevel = 32;

        if (generateHotspots) {
            int x = chunk.getX();
            int y = chunk.getZ();
            diamondLevel = getOreLevel(Noise.DIAMOND, x, y);
            lapisLevel = getOreLevel(Noise.LAPIS, x, y);
            redstoneLevel = getOreLevel(Noise.REDSTONE, x, y);
            ironLevel = getOreLevel(Noise.IRON, x, y);
            goldLevel = getOreLevel(Noise.GOLD, x, y);
        }

        for (int dy = 0; dy < OreChunk.SIZE; ++dy) {
            for (int dz = 0; dz < OreChunk.SIZE; ++dz) {
                for (int dx = 0; dx < OreChunk.SIZE; ++dx) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (y <= 0) continue;
                    // Clay
                    if (y <= 64 && y > 32) {
                        if (noises.get(Noise.CLAY).abs(x, y, z, 8.0) > 0.65) {
                            chunk.set(dx, dy, dz, OreType.CLAY);
                        }
                    }
                    // Coal
                    if (y <= 128) {
                        if (noises.get(Noise.COAL).abs(x, y, z, 5.0) > 0.66) {
                            chunk.set(dx, dy, dz, OreType.COAL_ORE);
                        }
                    }
                    // Iron
                    if (y <= ironLevel) {
                        double iro = noises.get(Noise.IRON).abs(x, y, z, 5.0);
                        if (iro  > 0.71) {
                            chunk.set(dx, dy, dz, OreType.IRON_ORE);
                        } else if (iro > 0.61) {
                            chunk.setIfEmpty(dx, dy, dz, OreType.ANDESITE);
                        }
                    }
                    // Gold
                    if (y <= goldLevel) {
                        double gol = noises.get(Noise.GOLD).abs(x, y, z, 5.0);
                        if (gol > 0.78) {
                            chunk.set(dx, dy, dz, OreType.GOLD_ORE);
                        } else if (gol > 0.68) {
                            chunk.setIfEmpty(dx, dy, dz, OreType.DIORITE);
                        }
                    }
                    // Redstone
                    if (y <= redstoneLevel) {
                        double red = noises.get(Noise.REDSTONE).abs(x, y, z, 5.0);
                        if (red > 0.72) {
                            chunk.set(dx, dy, dz, OreType.REDSTONE_ORE);
                        } else if (red > 0.62) {
                            chunk.setIfEmpty(dx, dy, dz, OreType.GRANITE);
                        }
                    }
                    // Lapis
                    if (y <= lapisLevel) {
                        double lap = noises.get(Noise.LAPIS).abs(x, y, z, 5.0);
                        if (lap > 0.80) { // used to be 0.81
                            chunk.set(dx, dy, dz, OreType.LAPIS_ORE);
                        } else if (lap > 0.70) {
                            chunk.setIfEmpty(dx, dy, dz, OreType.DIORITE);
                        }
                    }
                    // Diamond
                    if (y <= diamondLevel) {
                        double dia = noises.get(Noise.DIAMOND).abs(x, y, z, 5.0);
                        if (dia > 0.79) {
                            chunk.set(dx, dy, dz, OreType.DIAMOND_ORE);
                        } else if (dia > 0.69) {
                            chunk.setIfEmpty(dx, dy, dz, OreType.GRANITE);
                        } else if (dia > 0.66) {
                            if (noises.get(Noise.DIAMOND).at(x, y, z, 1.0) > 0.2) {
                                chunk.setIfEmpty(dx, dy, dz, OreType.STONE_MONSTER_EGG);
                            }
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
        }.runTaskAsynchronously(OrePlugin.getInstance());
        syncTask = new BukkitRunnable() {
            @Override public void run() {
                syncRun();
            }
        };
        syncTask.runTaskTimer(OrePlugin.getInstance(), 1, 1);
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
            if (chunk == null) continue;
            generate(chunk);
            OrePlugin inst = OrePlugin.getInstance();
            if (inst != null) {
                final OreChunk chunkCopy = chunk; // Not a real copy; must make final
                new BukkitRunnable() {
                    @Override public void run() {
                        syncDidGenerateChunk(chunkCopy);
                    }
                }.runTask(inst);
            }
        }
    }

    // Callback from the async thread
    void syncDidGenerateChunk(OreChunk chunk) {
        ChunkCoordinate coord = chunk.getCoordinate();
        scheduledChunks.remove(coord);
        generatedChunks.put(coord, chunk);
        for (Player player: getWorld().getPlayers()) {
            final int R = chunkRevealRadius * chunkRevealRadius;
            if (ChunkCoordinate.of(player.getLocation()).distanceSquared(coord) <= R) {
                revealChunkToPlayer(chunk, player);
            }
        }
    }

    void syncRun() {
        if (playerList.isEmpty()) {
            // Clean player map
            for (Iterator<Map.Entry<UUID, PlayerData> > it = playerMap.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, PlayerData> en = it.next();
                Player player = Bukkit.getServer().getPlayer(en.getKey());
                if (player == null || !player.getWorld().getName().equals(worldName)) {
                    it.remove();
                }
            }
            // Fill player list
            for (Player player: getWorld().getPlayers()) {
                playerList.add(player.getUniqueId());
            }
            // Garbage collect chunks
            for (Iterator<Map.Entry<ChunkCoordinate, OreChunk> > it = generatedChunks.entrySet().iterator(); it.hasNext();) {
                Map.Entry<ChunkCoordinate, OreChunk> en = it.next();
                if (en.getValue().isTooOld()) {
                    it.remove();
                }
            }
            // TODO: Pause task until a player joins the world?
        } else {
            UUID uuid = playerList.removeFirst();
            Player player = Bukkit.getServer().getPlayer(uuid);
            if (player == null || !player.getWorld().getName().equals(worldName)) {
                playerMap.remove(uuid);
            } else {
                PlayerData playerData = playerMap.get(uuid);
                if (playerData == null) {
                    playerData = new PlayerData();
                    playerMap.put(uuid, playerData);
                }
                if (playerData.revealedLocation == null || playerData.revealedLocation.distanceSquared(ChunkCoordinate.of(player.getLocation())) > 1) {
                    playerData.revealedLocation = ChunkCoordinate.of(player.getLocation());
                    revealToPlayer(playerData, player);
                }
            }
        }
    }

    private void revealToPlayer(PlayerData playerData, Player player) {
        ChunkCoordinate center = ChunkCoordinate.of(player.getLocation());
        final int R = chunkRevealRadius;
        for (int y = -R; y <= R; ++y) {
            for (int z = -R; z <= R; ++z) {
                for (int x = -R; x <= R; ++x) {
                    OreChunk chunk = getOrGenerate(center.getRelative(x, y, z));
                    ChunkCoordinate coord = chunk.getCoordinate();
                    if (chunk != null && !playerData.shownChunks.contains(coord)) {
                        revealChunkToPlayer(chunk, player);
                        playerData.shownChunks.add(coord);
                    }
                }
            }
        }
        final int RR = 5 * 5;
        for (Iterator<ChunkCoordinate> iter = playerData.shownChunks.iterator(); iter.hasNext(); ) {
            if (iter.next().distanceSquared(center) > RR) iter.remove();
        }
    }

    static boolean isExposedToAir(Block block) {
        final List<BlockFace> dirs = Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);
        for (BlockFace dir: dirs) {
            if (block.getRelative(dir).getType() == Material.AIR) return true;
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
                    if (!ore.isHidden()) {
                        MaterialData mat = ore.getMaterialData();
                        if (mat != null) {
                            Block block = world.getBlockAt(chunk.getBlockX() + x, chunk.getBlockY() + y, chunk.getBlockZ() + z);
                            if (block.getType() == Material.STONE &&
                                !BukkitExploits.getInstance().isPlayerPlaced(block) &&
                                isExposedToAir(block)) {
                                player.sendBlockChange(block.getLocation(), mat.getItemType(), mat.getData());
                            }
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
                queue.offer(OreChunk.of(coord));
            }
        } else {
            result.setUsed();
        }
        return result;
    }

    void realize(Block block) {
        ChunkCoordinate coord = ChunkCoordinate.of(block);
        OreChunk chunk = getOrGenerate(coord);
        if (chunk == null) return;
        OreType ore = chunk.at(block);
        MaterialData mat = ore.getMaterialData();
        if (mat == null) return;
        if (block.getType() == Material.STONE &&
            !BukkitExploits.getInstance().isPlayerPlaced(block)) {
            block.setTypeIdAndData(mat.getItemTypeId(), mat.getData(), false);
        }
    }

    void reveal(Block block) {
        ChunkCoordinate coord = ChunkCoordinate.of(block);
        OreChunk chunk = getOrGenerate(coord);
        if (chunk == null) return;
        OreType ore = chunk.at(block);
        MaterialData mat = ore.getMaterialData();
        if (mat == null) return;
        if (block.getType() != Material.STONE) return;
        if (BukkitExploits.getInstance().isPlayerPlaced(block)) return;
        for (Player player: block.getWorld().getPlayers()) {
            if (ChunkCoordinate.of(player.getLocation()).distanceSquared(coord) <= 4) {
                player.sendBlockChange(block.getLocation(), mat.getItemType(), mat.getData());
            }
        }
    }
}

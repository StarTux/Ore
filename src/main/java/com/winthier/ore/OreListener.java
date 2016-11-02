package com.winthier.ore;

import com.winthier.ore.event.DungeonRevealEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

@RequiredArgsConstructor
public class OreListener implements Listener {
    final OrePlugin plugin;
    final Set<Block> placedBlocks = new HashSet<>();
    
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (placedBlocks.contains(block)) return;
        WorldGenerator worldGen = plugin.generators.get(block.getWorld().getName());
        if (worldGen == null) return;
        worldGen.realize(block);
    }

    @RequiredArgsConstructor static class Rel { final int x, y, z; }
    final static List<Rel> NBORS = Arrays.asList(
        new Rel( 1,  0,  0),
        new Rel(-1,  0,  0),
        new Rel( 0,  1,  0),
        new Rel( 0, -1,  0),
        new Rel( 0,  0,  1),
        new Rel( 0,  0, -1)
        );
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockDamage(BlockDamageEvent event) {
        final Block block = event.getBlock();
        if (placedBlocks.contains(block)) return;
        WorldGenerator worldGen = plugin.generators.get(block.getWorld().getName());
        if (worldGen == null) return;
        OreType oreType = worldGen.getOreAt(block);
        if (oreType == OreType.MINI_CAVE) {
            if (block.getType() == Material.STONE && !plugin.isPlayerPlaced(block)) {
                worldGen.revealMiniCave(block);
                block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
            }
        } else if (oreType == OreType.DUNGEON) {
            Schematic.PasteResult pasteResult = worldGen.revealDungeon(block);
            if (pasteResult != null) {
                if (worldGen.debug) {
                    OrePlugin.getInstance().getLogger().info("Dungeon " + pasteResult.getSchematic().getName() + "(" + pasteResult.getChests().size() + " chests) revealed for " + event.getPlayer().getName() + " at " + block.getWorld().getName() + " " + pasteResult.getSourceBlock().getX() + " " + pasteResult.getSourceBlock().getY() + " " + pasteResult.getSourceBlock().getZ() + " rot=" + pasteResult.getSchematic().getRotation());
                }
                DungeonRevealEvent dungeonRevealEvent = new DungeonRevealEvent(event.getPlayer(), pasteResult.getSchematic(), pasteResult.getSourceBlock(), pasteResult.getChests());
                plugin.getServer().getPluginManager().callEvent(dungeonRevealEvent);
            }
        } else {
            worldGen.realize(block);
            for (Rel nbor: NBORS) {
                Block o = block.getRelative(nbor.x, nbor.y, nbor.z);
                if (o.getY() < 0 || o.getY() > 255) continue;
                worldGen.realize(o);
            }
            // Reveal in the direction of the mining
            Block playerBlock = event.getPlayer().getEyeLocation().getBlock();
            int dx = block.getX() - playerBlock.getX();
            int dy = block.getY() - playerBlock.getY();
            int dz = block.getZ() - playerBlock.getZ();
            if (dx < 0) dx = -1;
            if (dx > 0) dx = 1;
            if (dz < 0) dz = -1;
            if (dz > 0) dz = 1;
            if (dx != 0 || dz != 0) {
                dy = 0;
            } else {
                if (dy < 0) dy = -1;
                if (dy > 0) dy = 1;
            }
            Block dirBlock = block.getRelative(dx, dy, dz);
            for (Rel nbor: NBORS) {
                Block o = dirBlock.getRelative(nbor.x, nbor.y, nbor.z);
                if (o.getY() < 0 || o.getY() > 255) continue;
                worldGen.realize(o);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        placedBlocks.add(event.getBlock());
    }

    @EventHandler
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        Block block = event.getSpawner().getBlock();
        WorldGenerator worldGen = plugin.generators.get(block.getWorld().getName());
        if (worldGen == null) return;
        worldGen.onSpawnerSpawn(block);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        final WorldGenerator worldGen = plugin.generators.get(event.getEntity().getWorld().getName());
        if (worldGen == null) return;
        onExplode(worldGen, event.blockList());
        new BukkitRunnable() {
            @Override public void run() {
                onExplode(worldGen, event.blockList());
            }
        }.runTask(plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        final Block block = event.getBlock();
        final WorldGenerator worldGen = plugin.generators.get(block.getWorld().getName());
        if (worldGen == null) return;
        new BukkitRunnable() {
            @Override public void run() {
                onExplode(worldGen, event.blockList());
            }
        }.runTask(plugin);
    }

    private void onExplode(WorldGenerator worldGen, List<Block> blocks) {
        for (Block block: blocks) {
            for (Rel rel: NBORS) {
                Block nbor = block.getRelative(rel.x, rel.y, rel.z);
                if (blocks.contains(nbor)) continue;
                worldGen.reveal(nbor);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getTo() != Material.AIR) return;
        final Block block = event.getBlock();
        final WorldGenerator worldGen = plugin.generators.get(block.getWorld().getName());
        if (worldGen == null) return;
        new BukkitRunnable() {
            @Override public void run() {
                for (Rel rel: NBORS) {
                    worldGen.reveal(block.getRelative(rel.x, rel.y, rel.z));
                }
            }
        }.runTask(plugin);
    }
}

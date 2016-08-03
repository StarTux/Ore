package com.winthier.ore;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

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

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (block == null) return;
        if (placedBlocks.contains(block)) return;
        WorldGenerator worldGen = plugin.generators.get(block.getWorld().getName());
        if (worldGen == null) return;
        worldGen.realize(block);
    }

    @RequiredArgsConstructor static class Rel { final int x, y, z; }
    final static List<Rel> nbors = Arrays.asList(
        // 1 out
        new Rel( 1,  0,  0),
        new Rel(-1,  0,  0),
        new Rel( 0,  1,  0),
        new Rel( 0, -1,  0),
        new Rel( 0,  0,  1),
        new Rel( 0,  0, -1),
        // Diagonal
        new Rel( 0,  1,  1),
        new Rel( 0,  1, -1),
        new Rel( 0, -1,  1),
        new Rel( 0, -1, -1),

        new Rel( 1,  0,  1),
        new Rel( 1,  0, -1),
        new Rel(-1,  0,  1),
        new Rel(-1,  0, -1),

        new Rel( 1,  1,  0),
        new Rel( 1, -1,  0),
        new Rel(-1,  1,  0),
        new Rel(-1, -1,  0)
        );
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        final Block block = event.getBlock();
        if (placedBlocks.contains(block)) return;
        WorldGenerator worldGen = plugin.generators.get(block.getWorld().getName());
        if (worldGen == null) return;
        worldGen.realize(block);
        for (Rel nbor: nbors) {
            Block o = block.getRelative(nbor.x, nbor.y, nbor.z);
            if (block.getY() < 0 || block.getY() > 255) continue;
            worldGen.realize(o);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        placedBlocks.add(event.getBlock());
    }
}

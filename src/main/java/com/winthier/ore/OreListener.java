package com.winthier.ore;

import lombok.RequiredArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

@RequiredArgsConstructor
public class OreListener implements Listener {
    final OrePlugin plugin;
    
    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        WorldGenerator worldGen = plugin.generators.get(block.getWorld().getName());
        if (worldGen == null) return;
        worldGen.realize(block);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        final Block block = event.getBlock();
        WorldGenerator worldGen = plugin.generators.get(block.getWorld().getName());
        if (worldGen == null) return;
        final int R = 2;
        for (int y = -R; y <= R; ++y) {
            for (int z = -R; z <= R; ++z) {
                for (int x = -R; x <= R; ++x) {
                    Block o = block.getRelative(x, y, z);
                    if (block.getY() < 0 || block.getY() > 255) continue;
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) <= 1) {
                        worldGen.realize(o);
                    } else {
                        worldGen.reveal(o);
                    }
                }
            }
        }
    }
}

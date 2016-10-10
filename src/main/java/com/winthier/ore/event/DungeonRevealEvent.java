package com.winthier.ore.event;

import com.winthier.ore.Schematic;
import java.util.List;
import lombok.Value;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Value
public class DungeonRevealEvent extends Event {
    // Boiler Plate

    private static HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    final Player player;
    final Schematic schematic;
    final Block source;
    final List<Chest> treasureChests;
}
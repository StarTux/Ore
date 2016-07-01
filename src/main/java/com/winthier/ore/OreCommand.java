package com.winthier.ore;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OreCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if (player == null) return false;
        if (args.length == 0) return false;
        String firstArg = args[0].toLowerCase();
        if (firstArg.equals("test")) {
            double featureSize = 16.0;
            if (args.length >= 2) featureSize = Double.parseDouble(args[1]);
            double threshold = 0.0;
            if (args.length >= 3) threshold = Double.parseDouble(args[2]);
            int px = player.getLocation().getBlockX();
            int py = player.getLocation().getBlockY();
            int pz = player.getLocation().getBlockZ();
            OpenSimplexNoise noise = new OpenSimplexNoise(player.getWorld().getSeed());
            final int RADIUS = 16;
            int oreCount = 0;
            int totalCount = 0;
            for (int y = py - RADIUS; y <= py + RADIUS; ++y) {
                for (int z = pz - RADIUS; z <= pz + RADIUS; ++z) {
                    for (int x = px - RADIUS; x <= px + RADIUS; ++x) {
                        if (y < 0 || y > 255) continue;
                        totalCount += 1;
                        Location loc = new Location(player.getWorld(), (double)x, (double)y, (double)z);
                        double val = noise.abs(x, y, z, featureSize);
                        if (val >= threshold) {
                            oreCount += 1;
                            player.sendBlockChange(loc, Material.DIAMOND_ORE, (byte)0);
                        } else {
                            player.sendBlockChange(loc, Material.AIR, (byte)0);
                        }
                    }
                }
            }
            double percentage = (double)oreCount / (double)totalCount * 100.0;
            player.sendMessage(String.format("Placed %d/%d blocks (%.02f%%)", oreCount, totalCount, percentage));
        } else if (firstArg.equals("gen")) {
            OreChunk ch = OreChunk.of(player.getLocation().getBlock());
            final int R = 2;
            WorldGenerator worldgen = new WorldGenerator(player.getWorld().getName());
            Map<OreType, Integer> oreCount = new EnumMap<OreType, Integer>(OreType.class);
            int totalCount = 0;
            long now = System.currentTimeMillis();
            for (int y = 0; y <= 15; ++y) {
                for (int z = ch.getZ() - R; z <= ch.getZ() + R; ++z) {
                    for (int x = ch.getX() - R; x <= ch.getX() + R; ++x) {
                        if (y < 0 || y > 255) continue;
                        OreChunk chunk = new OreChunk(x, y, z);
                        worldgen.generate(chunk);
                        for (int dy = 0; dy < 16; ++dy) {
                            for (int dz = 0; dz < 16; ++dz) {
                                for (int dx = 0; dx < 16; ++dx) {
                                    int tx = chunk.getBlockX() + dx;
                                    int ty = chunk.getBlockY() + dy;
                                    int tz = chunk.getBlockZ() + dz;
                                    totalCount += 1;
                                    Block block = player.getWorld().getBlockAt(tx, ty, tz);
                                    Location loc = block.getLocation();
                                    OreType ore = chunk.get(dx, dy, dz);
                                    Integer c = oreCount.get(ore);
                                    if (c == null) c = 0;
                                    oreCount.put(ore, c + 1);
                                    if (ore != OreType.NONE && block.getType() == Material.STONE) {
                                        switch (ore) {
                                        case DIAMOND_ORE:
                                            player.sendBlockChange(loc, Material.DIAMOND_ORE, (byte)0);
                                            break;
                                        case COAL_ORE:
                                            player.sendBlockChange(loc, Material.COAL_ORE, (byte)0);
                                            break;
                                        case IRON_ORE:
                                            player.sendBlockChange(loc, Material.IRON_ORE, (byte)0);
                                            break;
                                        case GOLD_ORE:
                                            player.sendBlockChange(loc, Material.GOLD_ORE, (byte)0);
                                            break;
                                        case REDSTONE_ORE:
                                            player.sendBlockChange(loc, Material.REDSTONE_ORE, (byte)0);
                                            break;
                                        case LAPIS_ORE:
                                            player.sendBlockChange(loc, Material.LAPIS_ORE, (byte)0);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            player.sendMessage("Time: " + (System.currentTimeMillis() - now));
            player.sendMessage(String.format("Total blocks %d", totalCount));
            for (OreType ore: OreType.values()) {
                Integer c = oreCount.get(ore);
                if (c == null) c = 0;
                double percentage = (double)c / (double)totalCount * 100.0;
                player.sendMessage(String.format("%d (%.03f%%) %s", c, percentage, ore.name()));
            }
        }
        return true;
    }
}

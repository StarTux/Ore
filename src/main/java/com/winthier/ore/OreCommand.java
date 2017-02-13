package com.winthier.ore;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.Selection;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;

public class OreCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (args.length == 0) return false;
        String firstArg = args[0].toLowerCase();
        if (firstArg.equals("reload")) {
            OrePlugin.getInstance().loadWorlds();
            sender.sendMessage("Config reloaded");
        } else if (firstArg.equals("test")) {
            double featureSize = 16.0;
            if (args.length >= 2) featureSize = Double.parseDouble(args[1]);
            double exponent = 1.0;
            if (args.length >= 3) exponent = Double.parseDouble(args[2]);
            double threshold = 0.0;
            if (args.length >= 4) threshold = Double.parseDouble(args[3]);
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
                        double val = (
                            noise.abs(x, y, z, featureSize) +
                            noise.abs(x+1, y, z, featureSize) +
                            noise.abs(x-1, y, z, featureSize) +
                            noise.abs(x, y, z+1, featureSize) +
                            noise.abs(x, y, z-1, featureSize)) / 5.0;
                        val = Math.pow(val, exponent);
                        if (val >= threshold) {
                            oreCount += 1;
                            player.sendBlockChange(loc, Material.STAINED_GLASS, (byte)0);
                        } else {
                            player.sendBlockChange(loc, Material.AIR, (byte)0);
                        }
                    }
                }
            }
            double percentage = (double)oreCount / (double)totalCount * 100.0;
            player.sendMessage(String.format("Placed %d/%d blocks (%.02f%%)", oreCount, totalCount, percentage));
        } else if (firstArg.equals("gen")) {
            boolean anywhere = false;
            if (args.length >= 2 && args[1].equals("any")) {
                anywhere = true;
            }
            OreChunk ch = OreChunk.of(player.getLocation().getBlock());
            final int R = 2;
            WorldGenerator worldgen = OrePlugin.getInstance().generators.get(player.getWorld().getName());
            Map<OreType, Integer> oreCount = new EnumMap<OreType, Integer>(OreType.class);
            int totalCount = 0;
            long now = System.currentTimeMillis();
            for (int y = 0; y <= 15; ++y) {
                for (int z = ch.getZ() - R; z <= ch.getZ() + R; ++z) {
                    for (int x = ch.getX() - R; x <= ch.getX() + R; ++x) {
                        OreChunk chunk = OreChunk.of(new ChunkCoordinate(x, y, z).getBlock(player.getWorld()));
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
                                    if (ore == OreType.DEBUG) {
                                        player.sendBlockChange(loc, Material.STAINED_GLASS, (byte)9);
                                    }
                                    if (ore != OreType.NONE && (anywhere || block.getType() == Material.STONE)) {
                                        Integer c = oreCount.get(ore);
                                        if (c == null) c = 0;
                                        oreCount.put(ore, c + 1);
                                        MaterialData mat = ore.getMaterialData();
                                        if (mat != null) {
                                            player.sendBlockChange(loc, mat.getItemType(), mat.getData());
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
        } else if (firstArg.equals("lvl") && args.length == 2) {
            WorldGenerator.Noise noise;
            try {
                noise = WorldGenerator.Noise.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException iae) {
                sender.sendMessage("Invalid Noise: " + args[1]);
                return true;
            }
            ChunkCoordinate coord = ChunkCoordinate.of(player.getLocation());
            int level = OrePlugin.getInstance().generators.get(player.getWorld().getName()).getOreLevel(noise, coord.getX(), coord.getZ());
            player.sendMessage("Level of " + noise + " = " + level);
        } else if (firstArg.equals("star") && args.length == 1) {
            Block center = player.getLocation().getBlock();
            for (OreListener.Rel nbor: OreListener.NBORS) {
                Block block = center.getRelative(nbor.x, nbor.y, nbor.z);
                player.sendBlockChange(block.getLocation(), 1, (byte)0);
            }
        } else if (firstArg.equals("slime") && args.length == 1) {
            WorldGenerator worldGen = OrePlugin.getInstance().generators.get(player.getWorld().getName());
            OreChunk chunk = OreChunk.of(player.getLocation().getBlock());
            boolean result = worldGen.isSlimeChunk(chunk);
            player.sendMessage("Slime=" + result);
        } else if (firstArg.equals("dungeon")) {
            WorldGenerator worldGen = OrePlugin.getInstance().generators.get(player.getWorld().getName());
            OreChunk chunk = OreChunk.of(player.getLocation().getBlock());
            int result = worldGen.getDungeonLevel(chunk);
            player.sendMessage("Dungeon=" + result);
        } else if (firstArg.equals("copydungeon") && args.length >= 2) {
            if (player == null) return false;
            WorldEditPlugin we = getWorldEdit();
            if (we == null) return true;
            Selection sel = we.getSelection(player);
            if (sel == null) {
                player.sendMessage("Make a selection first!");
                return true;
            }
            Block a = sel.getMinimumPoint().getBlock();
            Block b = sel.getMaximumPoint().getBlock();
            List<String> tags = new ArrayList<>();
            for (int i = 2; i < args.length; ++i) tags.add(args[i]);
            String name = args[1];
            Schematic schem = Schematic.copy(a, b, name, tags);
            schem.save(OrePlugin.getInstance().getDungeonSchematicFile(name));
            player.sendMessage("Saved dungeon schematic '" + name + "'");
        } else if (firstArg.equals("pastedungeon") && args.length >= 2) {
            String name = args[1];
            Schematic schem = Schematic.load(OrePlugin.getInstance().getDungeonSchematicFile(name));
            if (schem == null) {
                player.sendMessage("Dungeon schematic not found: " + name);
                return true;
            }
            int rotation = 0;
            if (args.length >= 3) rotation = Math.abs(Integer.parseInt(args[2]));
            for (int i = 0; i < rotation; ++i) schem = schem.rotate();
            WorldEditPlugin we = getWorldEdit();
            if (we == null) return true;
            Selection sel = we.getSelection(player);
            if (sel == null) {
                player.sendMessage("Make a selection first!");
                return true;
            }
            Block a = sel.getMinimumPoint().getBlock();
            schem.paste(a, true);
            player.sendMessage("Dungeon " + name + " pasted at WE selection");
        } else if (firstArg.equals("debug") && args.length == 2) {
            String name = args[1];
            WorldGenerator worldGen = OrePlugin.getInstance().generators.get(name);
            if (worldGen == null) {
                sender.sendMessage("World generator not found: " + name);
                return true;
            }
            worldGen.debug = !worldGen.debug;
            sender.sendMessage("Debug mode in " + worldGen.worldName + (worldGen.debug ? " enabled" : " disabled"));
        } else if (firstArg.equals("mansion")) {
            String subcmd = args[1].toLowerCase();
            String name = args[2];
            WorldEditPlugin we = getWorldEdit();
            if (we == null) return true;
            Selection sel = we.getSelection(player);
            if (sel == null) {
                player.sendMessage("Make a selection first!");
                return true;
            }
            Block a = sel.getMinimumPoint().getBlock();
            Block b = sel.getMaximumPoint().getBlock();
            if (subcmd.equals("save")) {
                Schematic schematic = Schematic.copy(a, b, name, new ArrayList<String>());
                File dir = new File(OrePlugin.getInstance().getDataFolder(), "mansions");
                dir.mkdirs();
                File file = new File(dir, name + ".yml");
                schematic.save(file);
                player.sendMessage("Mansion saved as " + file.getName());
            } else if (subcmd.equals("paste")) {
                File dir = new File(OrePlugin.getInstance().getDataFolder(), "mansions");
                dir.mkdirs();
                File file = new File(dir, name + ".yml");
                Schematic schematic = Schematic.load(file);
                schematic.pasteHalloween(null, a);
                player.sendMessage("Mansion pasted");
            } else {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    WorldEditPlugin getWorldEdit() {
        return (WorldEditPlugin)Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
    }
}

package com.winthier.ore;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.Selection;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

public class OreCommand implements TabExecutor {
    @Override @SuppressWarnings("unchecked")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (args.length == 0) return false;
        String firstArg = args[0].toLowerCase();
        if (firstArg.equals("reload")) {
            OrePlugin.getInstance().loadWorlds();
            sender.sendMessage("Config reloaded");
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
                                        player.sendBlockChange(loc, Material.WHITE_STAINED_GLASS.createBlockData());
                                    }
                                    if (ore != OreType.NONE && (anywhere || worldgen.canReplace(block))) {
                                        Integer c = oreCount.get(ore);
                                        if (c == null) c = 0;
                                        oreCount.put(ore, c + 1);
                                        if (ore.mat != null) {
                                            player.sendBlockChange(loc, ore.mat.createBlockData());
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
        } else if (firstArg.equals("slime") && args.length == 1) {
            player.sendMessage("Slime=" + player.getLocation().getBlock().getChunk().isSlimeChunk());
        } else if (firstArg.equals("dungeon")) {
            WorldGenerator worldGen = OrePlugin.getInstance().generators.get(player.getWorld().getName());
            OreChunk chunk = OreChunk.of(player.getLocation().getBlock());
            Vec3 result = worldGen.getDungeonOffset(chunk, WorldGenerator.Special.of(chunk.getBiome()));
            player.sendMessage("Dungeon=" + result);
        // } else if (firstArg.equals("iam") && args.length <= 2) {
        //     if (args.length == 2) {
        //         String name = args[1];
        //         impostors.put(player.getUniqueId(), name);
        //         player.sendMessage("Saving dungeons under the name " + name);
        //     } else {
        //         String oldname = impostors.remove(player.getUniqueId());
        //         player.sendMessage("No longer saving dungeons under the name " + oldname);
        //     }
        } else if (firstArg.equals("list") && (args.length == 1 || args.length == 2)) {
            File file = new File(OrePlugin.getInstance().getDataFolder(), "dungeons");
            String term = null;
            if (args.length >= 2) term = args[1].toLowerCase();
            List<String> results = new ArrayList<>();
            for (String str: file.list()) {
                if (str.endsWith(".yml") && (term == null || str.toLowerCase().contains(term))) {
                    results.add(str.substring(0, str.length() - 4));
                }
            }
            StringBuilder sb = new StringBuilder("Dungeons (").append(results.size()).append(")");
            for (String result: results) sb.append(" ").append(result);
            player.sendMessage(sb.toString());
        } else if (firstArg.equals("info") && args.length == 2) {
            String name = args[1];
            File file = OrePlugin.getInstance().getDungeonSchematicFile(name);
            if (!file.isFile()) {
                player.sendMessage("Dungeon schematic not found: " + name);
                return true;
            }
            Schematic schem = Schematic.load(file);
            player.sendMessage("Dungeon Info ====");
            player.sendMessage("Name `" + schem.getName() + "`");
            player.sendMessage("Tags " + schem.getTags() + "");
            player.sendMessage("Size " + schem.getSizeX() + "x" + schem.getSizeY() + "x" + schem.getSizeZ());
            player.sendMessage("Blocks " + schem.getBlocks().size());
            player.sendMessage("");
        } else if (firstArg.equals("copy") && args.length >= 2) {
            if (player == null) return false;
            List<Integer> ls = new ArrayList<>();
            for (MetadataValue v: player.getMetadata("SelectionA")) {
                ls.addAll((List<Integer>)v.value());
                break;
            }
            for (MetadataValue v: player.getMetadata("SelectionB")) {
                ls.addAll((List<Integer>)v.value());
                break;
            }
            if (ls.size() != 6) {
                player.sendMessage("" + ls.size());
                player.sendMessage("Make a selection first!");
                return true;
            }
            Block a = player.getWorld().getBlockAt(Math.min(ls.get(0), ls.get(3)), Math.min(ls.get(1), ls.get(4)), Math.min(ls.get(2), ls.get(5)));
            Block b = player.getWorld().getBlockAt(Math.max(ls.get(0), ls.get(3)), Math.max(ls.get(1), ls.get(4)), Math.max(ls.get(2), ls.get(5)));
            List<String> tags = new ArrayList<>();
            boolean force = false;
            for (int i = 2; i < args.length; ++i) {
                String arg = args[i].toLowerCase();
                if (arg.startsWith("-")) {
                    if (arg.equals("-f") || arg.equals("--force")) {
                        force = true;
                    } else {
                        return false;
                    }
                } else {
                    tags.add(arg);
                }
            }
            String name = args[1];
            Schematic schem = Schematic.copy(a, b, name, tags);
            File file = OrePlugin.getInstance().getDungeonSchematicFile(name);
            if (file.isFile() && !force) {
                player.sendMessage(ChatColor.RED + "File named '" + name + "' aleady exists. Use '--force' to overwrite.");
                return true;
            }
            schem.save(file);
            player.sendMessage("Saved dungeon schematic '" + name + "' with tags " + tags);
        } else if (firstArg.equals("paste") && args.length >= 2) {
            String name = args[1];
            File file = OrePlugin.getInstance().getDungeonSchematicFile(name);
            if (!file.isFile()) {
                player.sendMessage("Dungeon schematic not found: " + name);
                return true;
            }
            Schematic schem = Schematic.load(file);
            int rotation = 0;
            if (args.length >= 3) rotation = Math.abs(Integer.parseInt(args[2]));
            for (int i = 0; i < rotation; ++i) schem = schem.rotate();
            Block a = player.getLocation().getBlock();
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
            List<Integer> ls = new ArrayList<>();
            for (MetadataValue v: player.getMetadata("SelectionA")) {
                ls.addAll((List<Integer>)v.value());
                break;
            }
            for (MetadataValue v: player.getMetadata("SelectionB")) {
                ls.addAll((List<Integer>)v.value());
                break;
            }
            if (ls.size() != 6) {
                player.sendMessage("Make a selection first!");
                return true;
            }
            Block a = player.getWorld().getBlockAt(Math.min(ls.get(0), ls.get(3)), Math.min(ls.get(1), ls.get(4)), Math.min(ls.get(2), ls.get(5)));
            Block b = player.getWorld().getBlockAt(Math.max(ls.get(0), ls.get(3)), Math.max(ls.get(1), ls.get(4)), Math.max(ls.get(2), ls.get(5)));
            if (subcmd.equals("save")) {
                Schematic schematic = Schematic.copy(a, b, name, new ArrayList<String>());
                File dir = new File(OrePlugin.getInstance().getDataFolder(), "mansions");
                dir.mkdirs();
                File file = new File(dir, name + ".yml");
                schematic.save(file);
                player.sendMessage("Mansion saved as " + file.getName());
            } else {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String term = args.length > 0 ? args[args.length - 1] : "";
        if (args.length <= 1) {
            return Arrays.asList("reload", "gen", "debug", "star", "slime", "dungeon", "copy", "paste", "list", "info", "mansion").stream().filter(s -> s.startsWith(term)).collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equals("pastedungeon")) {
            return OrePlugin.getInstance().getDungeonSchematics().keySet().stream().filter(s -> s.startsWith(term)).collect(Collectors.toList());
        } else {
            return null;
        }
    }
}

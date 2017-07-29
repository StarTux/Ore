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
import org.bukkit.material.MaterialData;

public class OreCommand implements TabExecutor {
    private final Map<UUID, String> impostors = new HashMap<>();

    @Override
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
                                        player.sendBlockChange(loc, Material.STAINED_GLASS, (byte)9);
                                    }
                                    if (ore != OreType.NONE && (anywhere || worldgen.canReplace(block))) {
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
        } else if (firstArg.equals("star") && args.length == 1) {
            Block center = player.getLocation().getBlock();
            for (OreListener.Rel nbor: OreListener.NBORS) {
                Block block = center.getRelative(nbor.x, nbor.y, nbor.z);
                player.sendBlockChange(block.getLocation(), 1, (byte)0);
            }
        } else if (firstArg.equals("slime") && args.length == 1) {
            player.sendMessage("Slime=" + player.getLocation().getBlock().getChunk().isSlimeChunk());
        } else if (firstArg.equals("dungeon")) {
            WorldGenerator worldGen = OrePlugin.getInstance().generators.get(player.getWorld().getName());
            OreChunk chunk = OreChunk.of(player.getLocation().getBlock());
            Vec3 result = worldGen.getDungeonOffset(chunk, WorldGenerator.Special.of(chunk.getBiome()));
            player.sendMessage("Dungeon=" + result);
        } else if (firstArg.equals("iam") && args.length <= 2) {
            if (args.length == 2) {
                String name = args[1];
                impostors.put(player.getUniqueId(), name);
                player.sendMessage("Saving dungeons under the name " + name);
            } else {
                String oldname = impostors.remove(player.getUniqueId());
                player.sendMessage("No longer saving dungeons under the name " + oldname);
            }
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
            String user = impostors.get(player.getUniqueId());
            if (user == null) user = player.getName();
            String name = user + "." + args[1];
            Schematic schem = Schematic.copy(a, b, name, tags);
            File file = OrePlugin.getInstance().getDungeonSchematicFile(name);
            if (file.isFile() && !force) {
                player.sendMessage(ChatColor.RED + "File named '" + name + "' aleady exists. Use '--force' to overwrite.");
                return true;
            }
            schem.save(file);
            player.sendMessage("Saved dungeon schematic '" + name + "' with tags " + tags);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String term = args.length > 0 ? args[args.length - 1] : "";
        if (args.length <= 1) {
            return Arrays.asList("reload", "gen", "debug", "star", "slime", "dungeon", "iam", "copydungeon", "pastedungeon", "mansion").stream().filter(s -> s.startsWith(term)).collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equals("pastedungeon")) {
            return OrePlugin.getInstance().getDungeonSchematics().keySet().stream().filter(s -> s.startsWith(term)).collect(Collectors.toList());
        } else {
            return null;
        }
    }

    WorldEditPlugin getWorldEdit() {
        return (WorldEditPlugin)Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
    }
}

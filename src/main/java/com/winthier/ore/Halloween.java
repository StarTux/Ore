package com.winthier.ore;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
class Halloween {
    final WorldGenerator worldGen;
    Map<String, Schematic> mansionSchematics = null;
    State state = State.INIT;
    Set<Block> mansionBlocks = new HashSet<>();
    int stateTicks;
    boolean didBreak = false;
    Location mansionLocation = null;
    int COOL_MINUTES = 5;
    boolean cancelled = false;

    enum State {
        INIT,
        LOOKING_FOR_LOCATION,
        MANSION,
        FOUND,
        CLEANUP,
        COOLDOWN;
    }

    Map<String, Schematic> getMansionSchematics() {
        if (mansionSchematics == null) {
            mansionSchematics = new HashMap<>();
            File dir = new File(OrePlugin.getInstance().getDataFolder(), "mansions");
            dir.mkdirs();
            for (File file: dir.listFiles()) {
                String name = file.getName();
                if (name.endsWith(".yml")) {
                    try {
                        Schematic schem = Schematic.load(file);
                        if (schem != null) {
                            mansionSchematics.put(name.substring(0, name.length() - 4), schem);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            OrePlugin.getInstance().getLogger().info("" + mansionSchematics.size() + " mansion schematics loaded");
        }
        return mansionSchematics;
    }

    void onTick() {
        State nextState = state;
        switch (state) {
        case INIT:
            if (!cancelled) {
                nextState = State.LOOKING_FOR_LOCATION;
            }
            break;
        case LOOKING_FOR_LOCATION:
            if (spawnMansion()) {
                nextState = State.MANSION;
            }
            break;
        case MANSION:
            if (didBreak || cancelled) {
                nextState = State.FOUND;
                didBreak = false;
            }
            if (stateTicks % 20 == 0) {
                for (Player player: worldGen.getWorld().getPlayers()) {
                    player.setCompassTarget(mansionLocation);
                }
		if (stateTicks > 0 && stateTicks % (20*60*10) == 0) {
		    reminder();
		}
            }
            break;
        case FOUND:
            if (stateTicks > 20*60*COOL_MINUTES || cancelled) {
                nextState = State.CLEANUP;
            }
            break;
        case CLEANUP:
	    nextState = State.COOLDOWN;
	    cleanup();
            break;
        case COOLDOWN:
            if (cancelled) {
		nextState = State.INIT;
	    } else if (stateTicks > 10*20) {
		nextState = State.LOOKING_FOR_LOCATION;
	    }
            break;
        }
        if (state != nextState) {
            stateTicks = 0;
        } else {
            stateTicks += 1;
        }
        state = nextState;
    }

    Schematic randomSchematic() {
        Map<String, Schematic> schems = getMansionSchematics();
        if (schems.isEmpty()) return null;
        int i = worldGen.random.nextInt(schems.size());
        return new ArrayList<Schematic>(schems.values()).get(i);
    }

    boolean spawnMansion() {
        Schematic schematic = randomSchematic();
        if (schematic == null) {
            System.out.println("HALLOWEEN: No schematic found");
            return false;
        }
        int cx,cz;
        final int RADIUS = 4000 - 32;
        cx = worldGen.random.nextInt(RADIUS*2) - RADIUS;
        cz = worldGen.random.nextInt(RADIUS*2) - RADIUS;
        World world = worldGen.getWorld();

        int min = 255;
        int max = 0;
        for (int z = cz; z < cz + schematic.getSizeZ(); ++z) {
            for (int x = cx; x < cx + schematic.getSizeX(); ++x) {
                Block block = world.getHighestBlockAt(x, z);
                while (!block.getType().isSolid() && block.getY() > 0) {
                    switch (block.getType()) {
                    case WATER: case STATIONARY_WATER: case LAVA: case STATIONARY_LAVA:
                        System.out.println("HALLOWEEN: Water or Lava at " + x + "," + z);
                        return false;
                    }
                    block = block.getRelative(0, -1, 0);
                }
                int y = block.getY();
                if (y < min) min = y;
                if (y > max) max = y;
                if (max - min > 8) {
                    System.out.println("HALLOWEEN: Uneven terrain at " + cx + "," + cz);
                    return false;
                }
            }
        }
        mansionBlocks.clear();
        int rotation = worldGen.random.nextInt(4);
        for (int i = 0; i < rotation; ++i) schematic = schematic.rotate();
        mansionBlocks.addAll(schematic.pasteHalloween(worldGen, world.getBlockAt(cx, min, cz)));
        OrePlugin.getInstance().getLogger().info("HALLOWEEN: " + mansionBlocks.size() + " blocks placed");
        Msg.announce("&dA &dnew &dromantic &dvista &dspawned &din &dthe &dResource &dworld!");
        Msg.announce("&dFollow your compass to find it!");
        OrePlugin.getInstance().getLogger().info("Mansion " + schematic.getName() + " spawned at " + cx + "," + cz);
        Block block = world.getBlockAt(cx + schematic.getSizeX()/2, min, cz+ schematic.getSizeZ()/2);
        mansionLocation = block.getLocation();
        return true;
    }

    void reminder() {
        Msg.announce("&dA &dromantic &dvista &dspawned &din &dthe &dResource &dworld!");
        Msg.announce("&dFollow your compass to find it!");
    }

    void onBlockBreak(Player player, Block block) {
        if (!didBreak && state == State.MANSION && mansionBlocks.contains(block)) {
            didBreak = true;
            Msg.announce("&d&l%s&d found the &dResource &dvista &dfirst!", player.getName());
            Msg.announce("&d%d minutes until the next &dcan &dspawn.", COOL_MINUTES);
            Msg.consoleCommand("titles unlock %s Cupid", player.getName());
            Msg.consoleCommand("titles set %s Cupid", player.getName());
        }
    }

    void cleanup() {
	for (Block block: mansionBlocks) {
	    block.setType(Material.AIR, false);
	}
	mansionBlocks.clear();
	worldGen.plugin.getLogger().info("Halloween ended in " + worldGen.worldName);
    }
}

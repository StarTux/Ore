// package com.winthier.ore;

// import java.io.File;
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.List;
// import java.util.Map;
// import java.util.Set;
// import lombok.RequiredArgsConstructor;
// import org.bukkit.Location;
// import org.bukkit.Material;
// import org.bukkit.World;
// import org.bukkit.block.Block;
// import org.bukkit.entity.Player;

// @RequiredArgsConstructor
// class Halloween {
//     final WorldGenerator worldGen;
//     Map<String, Schematic> mansionSchematics = null;
//     State state = State.INIT;
//     Set<Block> mansionBlocks = new HashSet<>();
//     int stateTicks;
//     boolean didBreak = false;
//     Location mansionLocation = null;
//     int COOL_MINUTES = 5;

//     enum State {
//         INIT,
//         LOOKING_FOR_LOCATION,
//         MANSION,
//         FOUND,
//         CLEANUP,
//         COOLDOWN;
//     }

//     Map<String, Schematic> getMansionSchematics() {
//         if (mansionSchematics == null) {
//             mansionSchematics = new HashMap<>();
//             File dir = new File(OrePlugin.getInstance().getDataFolder(), "mansions");
//             dir.mkdirs();
//             for (File file: dir.listFiles()) {
//                 String name = file.getName();
//                 if (name.endsWith(".yml")) {
//                     try {
//                         Schematic schem = Schematic.load(file);
//                         if (schem != null) {
//                             mansionSchematics.put(name.substring(0, name.length() - 4), schem);
//                         }
//                     } catch (Exception e) {
//                         e.printStackTrace();
//                     }
//                 }
//             }
//             OrePlugin.getInstance().getLogger().info("" + mansionSchematics.size() + " mansion schematics loaded");
//         }
//         return mansionSchematics;
//     }

//     void onTick() {
//         State nextState = state;
//         switch (state) {
//         case INIT:
//             if (OrePlugin.getInstance().halloween) {
//                 nextState = State.LOOKING_FOR_LOCATION;
//             }
//             break;
//         case LOOKING_FOR_LOCATION:
//             if (spawnMansion()) {
//                 nextState = State.MANSION;
//             }
//             break;
//         case MANSION:
//             if (didBreak || !OrePlugin.getInstance().halloween) {
//                 nextState = State.FOUND;
//                 didBreak = false;
//             }
//             if (stateTicks % 20 == 0) {
//                 for (Player player: worldGen.getWorld().getPlayers()) {
//                     player.setCompassTarget(mansionLocation);
//                 }
//             }
//             break;
//         case FOUND:
//             if (stateTicks > 20*60*COOL_MINUTES || !OrePlugin.getInstance().halloween) {
//                 nextState = State.CLEANUP;
//             }
//             break;
//         case CLEANUP:
//             for (Block block: mansionBlocks) {
//                 block.setType(Material.AIR, false);
//             }
//             mansionBlocks.clear();
//             nextState = State.COOLDOWN;
//             break;
//         case COOLDOWN:
//             if (!OrePlugin.getInstance().halloween) nextState = State.INIT;
//             else if (stateTicks > 10*20) nextState = State.LOOKING_FOR_LOCATION;
//             break;
//         }
//         if (state != nextState) {
//             stateTicks = 0;
//         } else {
//             stateTicks += 1;
//         }
//         state = nextState;
//     }

//     Schematic randomSchematic() {
//         Map<String, Schematic> schems = getMansionSchematics();
//         if (schems.isEmpty()) return null;
//         int i = worldGen.random.nextInt(schems.size());
//         return new ArrayList<Schematic>(schems.values()).get(i);
//     }

//     boolean spawnMansion() {
//         Schematic schematic = randomSchematic();
//         if (schematic == null) {
//             System.out.println("HALLOWEEN: No schematic found");
//             return false;
//         }
//         int cx,cz;
//         final int RADIUS = 4000 - 32;
//         cx = worldGen.random.nextInt(RADIUS*2) - RADIUS;
//         cz = worldGen.random.nextInt(RADIUS*2) - RADIUS;
//         World world = worldGen.getWorld();

//         int min = 255;
//         int max = 0;
//         for (int z = cz; z < cz + schematic.getSizeZ(); ++z) {
//             for (int x = cx; x < cx + schematic.getSizeX(); ++x) {
//                 Block block = world.getHighestBlockAt(x, z);
//                 while (!block.getType().isSolid() && block.getY() > 0) {
//                     switch (block.getType()) {
//                     case WATER: case STATIONARY_WATER: case LAVA: case STATIONARY_LAVA:
//                         System.out.println("HALLOWEEN: Water or Lava at " + x + "," + z);
//                         return false;
//                     }
//                     block = block.getRelative(0, -1, 0);
//                 }
//                 int y = block.getY();
//                 if (y < min) min = y;
//                 if (y > max) max = y;
//                 if (max - min > 8) {
//                     System.out.println("HALLOWEEN: Uneven terrain at " + cx + "," + cz);
//                     return false;
//                 }
//             }
//         }
//         mansionBlocks.clear();
//         mansionBlocks.addAll(schematic.pasteHalloween(world.getBlockAt(cx, min, cz)));
//         OrePlugin.getInstance().getLogger().info("HALLOWEEN: " + mansionBlocks.size() + " blocks placed");
//         Msg.announce("&6A &6new &6mansion &6spawned &6in &6the &6Resource &6world!");
//         Msg.announce("&6Follow your compass to find it!");
//         OrePlugin.getInstance().getLogger().info("Mansion " + schematic.getName() + " spawned at " + cx + "," + cz);
//         Block block = world.getBlockAt(cx + schematic.getSizeX()/2, min, cz+ schematic.getSizeZ()/2);
//         mansionLocation = block.getLocation();
//         return true;
//     }

//     void onBlockBreak(Player player, Block block) {
//         if (!didBreak && state == State.MANSION && mansionBlocks.contains(block)) {
//             didBreak = true;
//             Msg.announce("&6&l%s&6 found the Spooky &6Resource &6mansion &6first!", player.getName());
//             Msg.announce("&6%d minutes until the next &6can &6spawn.", COOL_MINUTES);
//             Msg.consoleCommand("titles unlock %s PumpkinHunter", player.getName());
//             Msg.consoleCommand("titles set %s PumpkinHunter", player.getName());
//         }
//     }
// }

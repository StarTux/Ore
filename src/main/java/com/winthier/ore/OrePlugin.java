package com.winthier.ore;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public class OrePlugin extends JavaPlugin {
    @Getter static OrePlugin instance;
    final Map<String, WorldGenerator> generators = new HashMap<>();
    ExploitsHandler exploitsHandler = null;
    Map<String, Schematic> dungeonSchematics = null;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;
        if (getServer().getPluginManager().getPlugin("Exploits") != null) {
            exploitsHandler = new ExploitsHandler();
        } else {
            getLogger().warning("Exploits not found!");
        }
        getCommand("ore").setExecutor(new OreCommand());
        getServer().getPluginManager().registerEvents(new OreListener(this), this);
        loadWorlds();
    }

    void loadWorlds() {
        dungeonSchematics = null;
        reloadConfig();
        unloadWorlds();
        ConfigurationSection worldsSection = getConfig().getConfigurationSection("worlds");
        for (String worldKey: worldsSection.getKeys(false)) {
            ConfigurationSection section = worldsSection.getConfigurationSection(worldKey);
            try {
                if (section.getBoolean("Enabled", false)) {
                    WorldGenerator worldGen = new WorldGenerator(worldKey);
                    generators.put(worldKey, worldGen);
                    worldGen.configure(section);
                    worldGen.start();
                } else {
                    getLogger().info("World " + worldKey + " is disabled.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void unloadWorlds() {
        for (WorldGenerator worldGen: generators.values()) {
            worldGen.stop();
        }
        generators.clear();
    }

    @Override
    public void onDisable() {
        unloadWorlds();
        instance = null;
    }

    boolean isPlayerPlaced(Block block) {
        if (exploitsHandler == null) return false;
        return exploitsHandler.isPlayerPlaced(block);
    }

    void setPlayerPlaced(Block block) {
        if (exploitsHandler == null) return;
        exploitsHandler.setPlayerPlaced(block);
    }

    File getDungeonSchematicFile(String name) {
        File dir = new File(getDataFolder(), "dungeons");
        dir.mkdirs();
        return new File(dir, name + ".yml");
    }

    Map<String, Schematic> getDungeonSchematics() {
        if (dungeonSchematics == null) {
            dungeonSchematics = new HashMap<>();
            File dir = new File(getDataFolder(), "dungeons");
            dir.mkdirs();
            for (File file: dir.listFiles()) {
                String name = file.getName();
                if (name.endsWith(".yml")) {
                    try {
                        Schematic schem = Schematic.load(file);
                        if (schem != null) {
                            dungeonSchematics.put(name.substring(0, name.length() - 4), schem);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            getLogger().info("" + dungeonSchematics.size() + " dungeon schematics loaded");
        }
        return dungeonSchematics;
    }
}

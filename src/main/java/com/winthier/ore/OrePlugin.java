package com.winthier.ore;

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
        reloadConfig();
        unloadWorlds();
        ConfigurationSection worldsSection = getConfig().getConfigurationSection("worlds");
        for (String worldKey: worldsSection.getKeys(false)) {
            ConfigurationSection section = worldsSection.getConfigurationSection(worldKey);
            try {
                if (section.getBoolean("Enabled", true)) {
                    WorldGenerator worldGen = new WorldGenerator(worldKey);
                    generators.put(worldKey, worldGen);
                    worldGen.generateHotspots = section.getBoolean("GenerateHotspots", false);
                    worldGen.specialBiomes = section.getBoolean("SpecialBiomes", true);
                    worldGen.start();
                    getLogger().info("Loaded world " + worldKey + " GenerateHotspots=" + worldGen.generateHotspots + " SpecialBiomes=" + worldGen.specialBiomes);
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
}

package com.winthier.ore;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public class OrePlugin extends JavaPlugin {
    @Getter static OrePlugin instance;
    final Map<String, WorldGenerator> generators = new HashMap<>();
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;
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
                    worldGen.start();
                    getLogger().info("Loaded world " + worldKey + " GenerateHotspots=" + worldGen.generateHotspots);
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
}

package com.winthier.ore;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public class OrePlugin extends JavaPlugin {
    @Getter static OrePlugin instance;
    final Map<String, WorldGenerator> generators = new HashMap<>();
    
    @Override
    public void onEnable() {
        instance = this;
        getCommand("ore").setExecutor(new OreCommand());
        getServer().getPluginManager().registerEvents(new OreListener(this), this);
        
        WorldGenerator worldGen = new WorldGenerator("world");
        generators.put("world", worldGen);
        worldGen.start();
    }

    @Override
    public void onDisable() {
        for (WorldGenerator worldGen: generators.values()) {
            worldGen.stop();
        }
        generators.clear();
        instance = null;
    }
}

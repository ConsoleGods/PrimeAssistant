package me.optimusprimerdc.primeAssistant.CoinFlip;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StreakManager {

    private final PrimeAssistant plugin;
    private final File file;
    private final FileConfiguration cfg;
    private final Map<UUID, Integer> streaks = new ConcurrentHashMap<>();

    public StreakManager(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "streaks.yml");
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        try {
            if (!file.exists()) file.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create streaks.yml: " + e.getMessage());
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadFromConfig();
    }

    private void loadFromConfig() {
        streaks.clear();
        for (String key : cfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                int val = cfg.getInt(key, 0);
                if (val > 0) streaks.put(id, val);
            } catch (IllegalArgumentException ignored) {
                // skip invalid keys
            }
        }
    }

    private void saveToConfig() {
        for (String key : cfg.getKeys(false)) {
            cfg.set(key, null); // clear existing entries
        }
        for (Map.Entry<UUID, Integer> e : streaks.entrySet()) {
            cfg.set(e.getKey().toString(), e.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save streaks.yml: " + ex.getMessage());
        }
    }

    public int get(UUID id) {
        return streaks.getOrDefault(id, 0);
    }

    public int increment(UUID id) {
        int newVal = streaks.compute(id, (k, v) -> (v == null) ? 1 : v + 1);
        saveToConfig();
        return newVal;
    }

    public void reset(UUID id) {
        if (streaks.containsKey(id) && streaks.get(id) > 0) {
            streaks.put(id, 0);
            saveToConfig();
        }
    }

    public void set(UUID id, int value) {
        if (value <= 0) streaks.remove(id);
        else streaks.put(id, value);
        saveToConfig();
    }

    public Map<UUID, Integer> getAll() {
        return streaks;
    }
}
package me.optimusprimerdc.primeAssistant.config;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Level;

/**
 * Centralized config access and migration helpers.
 */
public class ConfigManager {

    private final PrimeAssistant plugin;

    public ConfigManager(PrimeAssistant plugin) {
        this.plugin = plugin;
        // Ensure config is loaded/merged and reload() is actually used
        reload();
    }

    // Generic helpers
    public int getInt(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    public String getString(String path, String def) {
        String v = plugin.getConfig().getString(path);
        return v == null ? def : v;
    }

    public boolean getBoolean(String path, boolean def) {
        return plugin.getConfig().getBoolean(path, def);
    }

    public List<String> getStringList(String path) {
        List<String> l = plugin.getConfig().getStringList(path);
        return l == null ? Collections.emptyList() : l;
    }

    public Map<String, Object> getConfigurationSectionAsMap(String path) {
        if (plugin.getConfig().getConfigurationSection(path) == null) return Collections.emptyMap();
        return plugin.getConfig().getConfigurationSection(path).getValues(false);
    }

    // Top-level / common
    public String getPrefix() {
        return getString("prefix", "&7[&bPrimeAssistant&7]&r ");
    }

    // Redstone
    public double getRedstoneTpsThreshold() {
        return plugin.getConfig().getDouble("redstone-protection.tps-threshold", 15.0);
    }

    public boolean isRedstonePurgeEnabled() {
        return plugin.getConfig().getBoolean("redstone-protection.enable-purge", true);
    }

    // ChatFiltering
    public String getChatWebhookUrl() {
        return getString("chat-filter.webhook-url", "");
    }

    public boolean isChatNotifyStaff() {
        return getBoolean("chat-filter.notify-staff", true);
    }

    public String getChatStaffPermission() {
        return getString("chat-filter.staff-permission", "primeassistant.staff");
    }

    public String getChatDefaultPunishment() {
        return getString("chat-filter.default-punishment", "block");
    }

    public List<String> getChatBasicWords() {
        return getStringList("chat-filter.basic-words");
    }

    public List<String> getChatAdvancedWords() {
        return getStringList("chat-filter.advanced-words");
    }

    public Map<String, String> getChatPunishments() {
        Map<String, Object> section = getConfigurationSectionAsMap("chat-filter.punishments");
        if (section.isEmpty()) return Collections.emptyMap();

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : section.entrySet()) {
            if (e.getKey() == null) continue;
            Object val = e.getValue();
            if (val == null) continue;
            result.put(e.getKey(), String.valueOf(val));
        }
        return Collections.unmodifiableMap(result);
    }

    public String getChatMessage(String key, String def) {
        return getString("chat-filter.messages." + key, def == null ? "" : def);
    }

    // Fixed keys: remove "-seconds" suffix to match config.yml
    public int getChatMuteDuration() {
        return getInt("chat-filter.mute-duration", 300);
    }

    public String getChatBanReason() {
        return getString("chat-filter.ban-reason", "Violation of chat rules");
    }

    public int getChatBanDuration() {
        return getInt("chat-filter.ban-duration", 0);
    }

    public String getChatWebhookMessage() {
        return getString("chat-filter.webhook-message", "%user% triggered chat filter: %message%");
    }

    // AntiStasis EnderPearl
    public boolean isAntiStasisEnabled() {
        return getBoolean("anti-stasis.enabled", true);
    }

    // Updated default to match resources/config.yml (30)
    public int getAntiStasisTimeoutSeconds() {
        return getInt("anti-stasis.timeout-seconds", 30);
    }

    // Use the nested messages.cancel key from config.yml
    public String getAntiStasisCancelMessage() {
        return getString("anti-stasis.messages.cancel", "&cYour pearl was in stasis and has been blocked.");
    }

    public boolean isAntiStasisDebug() {
        return getBoolean("anti-stasis.debug", false);
    }

    // ClearLag
    public boolean isClearlagEnabled() {
        return getBoolean("clearlag.enabled", true);
    }

    public int getClearlagIntervalSeconds() {
        return getInt("clearlag.interval-seconds", 600);
    }

    public int getClearlagWarningSeconds() {
        return getInt("clearlag.warning-seconds", 30);
    }

    public List<String> getClearlagBlocklist() {
        return getStringList("clearlag.blocklist");
    }

    // Gunpowder
    public boolean isGunpowderEnabled() {
        return getBoolean("gunpowder.enabled", true);
    }

    public int getGunpowderAmbientIntervalTicks() {
        return getInt("gunpowder.ambient-interval-ticks", 10);
    }

    public int getGunpowderFuseStepTicks() {
        return getInt("gunpowder.fuse-step-ticks", 3);
    }

    /**
     * Persist gunpowder.enabled to the plugin config and save.
     */
    public synchronized void setGunpowderEnabled(boolean enabled) {
        plugin.getConfig().set("gunpowder.enabled", enabled);
        plugin.saveConfig();
    }

    // CoinFlip / generic helpers used by other components
    public boolean isCoinflipEnabled() {
        return getBoolean("coinflip.enabled", true);
    }

    public int getCoinflipTimeoutSeconds() {
        // default updated to match `config.yml` default (60s)
        return getInt("coinflip.timeout-seconds", 60);
    }

    // New accessors for min/max amounts
    public int getCoinflipMinAmount() {
        return getInt("coinflip.min-amount", 10);
    }

    public int getCoinflipMaxAmount() {
        return getInt("coinflip.max-amount", 100000);
    }

    public String getCoinflipMessage(String key, String def) {
        return getString("coinflip.messages." + key, def == null ? "" : def);
    }

    // Config versioning and migration

    /**
     * Return the stored config version as an int (backwards-compatible).
     * This preserves existing callers expecting an int.
     */
    public int getConfigVersion() {
        return getInt("config-version", 1);
    }

    /**
     * Return the stored config version as a double (supports fractional versions like 5.1).
     */
    public double getConfigVersionDouble() {
        return plugin.getConfig().getDouble("config-version", 1.0);
    }

    /**
     * Persist config version (int overload for compatibility).
     */
    public synchronized void setConfigVersion(int version) {
        plugin.getConfig().set("config-version", version);
        plugin.saveConfig();
    }

    /**
     * Persist config version (double to support fractional versions).
     */
    public synchronized void setConfigVersion(double version) {
        plugin.getConfig().set("config-version", version);
        plugin.saveConfig();
    }

    /**
     * Upgrade the config to the given targetVersion (int overload for compatibility).
     */
    public synchronized void upgradeConfigIfNeeded(int targetVersion) {
        upgradeConfigIfNeeded((double) targetVersion);
    }

    /**
     * Upgrade the config to the given targetVersion by applying incremental migrations.
     * Supports fractional target versions while performing integer migrations.
     */
    public synchronized void upgradeConfigIfNeeded(double targetVersion) {
        double current = getConfigVersionDouble();
        if (current >= targetVersion) return;

        // Run integer migrations for each whole version step between current and target.
        int start = (int) Math.floor(current);
        int end = (int) Math.floor(targetVersion);

        for (int v = start + 1; v <= end; v++) {
            migrateTo(v);
            // update to the integer step so partial upgrades are visible if process restarts mid-migration
            setConfigVersion(v);
        }

        // finally set the precise target (may be fractional)
        setConfigVersion(targetVersion);
    }

    /**
     * Apply migration for a single target version.
     * Add case blocks for each new version and apply minimal changes required.
     */
    private void migrateTo(int version) {
        switch (version) {
            case 2:
                // Example migration: ensure the 'gunpowder' section exists with sane defaults.
                if (plugin.getConfig().getConfigurationSection("gunpowder") == null) {
                    plugin.getConfig().set("gunpowder.enabled", true);
                    plugin.getConfig().set("gunpowder.ambient-interval-ticks", 10);
                    plugin.getConfig().set("gunpowder.fuse-step-ticks", 3);
                } else {
                    // ensure individual keys exist
                    if (!plugin.getConfig().contains("gunpowder.enabled")) {
                        plugin.getConfig().set("gunpowder.enabled", true);
                    }
                    if (!plugin.getConfig().contains("gunpowder.ambient-interval-ticks")) {
                        plugin.getConfig().set("gunpowder.ambient-interval-ticks", 10);
                    }
                    if (!plugin.getConfig().contains("gunpowder.fuse-step-ticks")) {
                        plugin.getConfig().set("gunpowder.fuse-step-ticks", 3);
                    }
                }
                plugin.saveConfig();
                break;

            case 3:
                // Add coinflip section defaults (idempotent, won't overwrite existing values)
                if (plugin.getConfig().getConfigurationSection("coinflip") == null) {
                    plugin.getConfig().set("coinflip.enabled", true);
                    plugin.getConfig().set("coinflip.timeout-seconds", 60);
                    plugin.getConfig().set("coinflip.messages.start", "&6A coinflip has started.");
                    plugin.getConfig().set("coinflip.messages.win", "&aYou won the coinflip!");
                    plugin.getConfig().set("coinflip.messages.lose", "&cYou lost the coinflip.");
                    // ensure min/max defaults exist for older configs
                    plugin.getConfig().set("coinflip.min-amount", 10);
                    plugin.getConfig().set("coinflip.max-amount", 100000);
                } else {
                    if (!plugin.getConfig().contains("coinflip.enabled")) {
                        plugin.getConfig().set("coinflip.enabled", true);
                    }
                    if (!plugin.getConfig().contains("coinflip.timeout-seconds")) {
                        plugin.getConfig().set("coinflip.timeout-seconds", 60);
                    }
                    if (!plugin.getConfig().contains("coinflip.messages.start")) {
                        plugin.getConfig().set("coinflip.messages.start", "&6A coinflip has started.");
                    }
                    if (!plugin.getConfig().contains("coinflip.messages.win")) {
                        plugin.getConfig().set("coinflip.messages.win", "&aYou won the coinflip!");
                    }
                    if (!plugin.getConfig().contains("coinflip.messages.lose")) {
                        plugin.getConfig().set("coinflip.messages.lose", "&cYou lost the coinflip.");
                    }
                    if (!plugin.getConfig().contains("coinflip.min-amount")) {
                        plugin.getConfig().set("coinflip.min-amount", 10);
                    }
                    if (!plugin.getConfig().contains("coinflip.max-amount")) {
                        plugin.getConfig().set("coinflip.max-amount", 100000);
                    }
                }
                plugin.saveConfig();
                break;

            // future migrations:
            // case 4: ...
            default:
                // no-op for unknown/new versions
                break;
        }
    }

    /**
     * Reload the plugin config while preserving the current runtime gunpowder setting
     * if the reloaded/merged config would otherwise omit that key.
     *
     * This prevents the value from flipping to the resource default (e.g. false)
     * when the user's file is missing the key after a merge.
     */
    public synchronized void reload() {
        // preserve current runtime value
        boolean previousGunpowder = plugin.getConfig().getBoolean("gunpowder.enabled", true);

        // reload raw disk config
        plugin.reloadConfig();

        // merge packaged defaults into the in-memory config without overwriting existing keys
        try (InputStream defStream = plugin.getResource("config.yml")) {
            if (defStream != null) {
                InputStreamReader reader = new InputStreamReader(defStream, StandardCharsets.UTF_8);
                FileConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                FileConfiguration cfg = plugin.getConfig();
                cfg.setDefaults(defaults);
                cfg.options().copyDefaults(true); // will only copy missing keys
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed loading default config resource during reload", ex);
        }

        // Ensure gunpowder.enabled is preserved if the merged config does not contain it.
        if (!plugin.getConfig().contains("gunpowder.enabled")) {
            plugin.getConfig().set("gunpowder.enabled", previousGunpowder);
        }

        // If resource provides a newer config-version, run migrations up to that version.
        double resourceVersion = getResourceConfigVersion();
        if (resourceVersion > getConfigVersionDouble()) {
            upgradeConfigIfNeeded(resourceVersion);
        }

        // persist any inserted defaults or preservation fixes
        plugin.saveConfig();
    }


    /**
     * Read `config-version` from the packaged resource `config.yml` (returns 0 on failure).
     */
    private double getResourceConfigVersion() {
        try (InputStream defStream = plugin.getResource("config.yml")) {
            if (defStream != null) {
                InputStreamReader reader = new InputStreamReader(defStream, StandardCharsets.UTF_8);
                FileConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                return defaults.getDouble("config-version", 0.0);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed reading resource config-version", ex);
        }
        return 0.0;
    }

    // Additional generic accessors if needed
    public PrimeAssistant getPlugin() {
        return plugin;
    }
}
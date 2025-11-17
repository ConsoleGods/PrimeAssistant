package me.optimusprimerdc.primeAssistant.clearlag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Logger;
import java.util.Locale;

public class ClearLag {
    private final JavaPlugin plugin;
    private final Logger logger;
    private BukkitTask task;
    private Set<Material> blocklistMaterials = Collections.emptySet();
    private int intervalSeconds = 600;
    private boolean enabled = true;

    private volatile boolean sequenceRunning = false;

    public ClearLag(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        setupDefaults();
        loadConfig();
        if (enabled) start();
    }

    private void setupDefaults() {
        plugin.getConfig().addDefault("clearlag.enabled", true);
        plugin.getConfig().addDefault("clearlag.interval-seconds", 600);
        plugin.getConfig().addDefault("clearlag.blocklist", Collections.emptyList());
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public synchronized void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("clearlag.enabled", true);
        this.intervalSeconds = plugin.getConfig().getInt("clearlag.interval-seconds", 600);
        List<String> raw = plugin.getConfig().getStringList("clearlag.blocklist");
        Set<Material> parsed = new HashSet<>();
        for (String s : raw) {
            if (s == null) continue;
            Material m = Material.matchMaterial(s.trim().toUpperCase(Locale.ROOT));
            if (m != null) parsed.add(m);
            else logger.warning("ClearLag: unknown material in blocklist: " + s);
        }
        this.blocklistMaterials = Collections.unmodifiableSet(parsed);
    }

    public synchronized void start() {
        if (task != null) return;
        if (!enabled) {
            logger.info("ClearLag is disabled in config.");
            return;
        }
        long ticks = Math.max(1, intervalSeconds) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (sequenceRunning) {
                logger.fine("ClearLag: sequence already running, skipping this interval.");
                return;
            }
            sequenceRunning = true;

            Bukkit.broadcastMessage(ChatColor.GOLD + "[ClearLag] " + ChatColor.YELLOW + "Items will be removed in " + ChatColor.RED + ChatColor.BOLD + "30 seconds" + ChatColor.RESET + ChatColor.YELLOW + ".");

            Bukkit.getScheduler().runTaskLater(plugin, () ->
                            Bukkit.broadcastMessage(ChatColor.GOLD + "[ClearLag] " + ChatColor.YELLOW + "Items will be removed in " + ChatColor.RED + ChatColor.BOLD + "10 seconds" + ChatColor.RESET + ChatColor.YELLOW + "."),
                    20L * 20L
            );

            new BukkitRunnable() {
                int secondsLeft = 5;

                @Override
                public void run() {
                    if (secondsLeft < 0) {

                        int removed = performClear();
                        Bukkit.broadcastMessage(ChatColor.GOLD + "[ClearLag] " + ChatColor.GREEN + "Removed " + ChatColor.WHITE + removed + ChatColor.GREEN + " dropped items.");
                        sequenceRunning = false;
                        this.cancel();
                        return;
                    }
                    Bukkit.broadcastMessage(ChatColor.GOLD + "[ClearLag] " + ChatColor.YELLOW + "Items will be removed in " + ChatColor.RED + ChatColor.BOLD + secondsLeft + " seconds" + ChatColor.RESET + ChatColor.YELLOW + ".");
                    secondsLeft--;
                }
            }.runTaskTimer(plugin, 20L * 25L, 20L);
        }, ticks, ticks);
        logger.info("ClearLag started (interval: " + intervalSeconds + "s).");
    }

    /**
     * Performs the actual removal of dropped item entities, respecting the blocklist.
     * Runs on the main server thread (called from scheduled tasks).
     *
     * @return the number of items removed
     */
    private int performClear() {
        int removed = 0;
        try {
            for (World world : Bukkit.getWorlds()) {

                for (Entity ent : new ArrayList<>(world.getEntities())) {
                    try {
                        if (!(ent instanceof Item)) continue;
                        Item itemEntity = (Item) ent;
                        ItemStack stack = itemEntity.getItemStack();
                        if (stack == null) continue; // defensive
                        Material type = stack.getType();
                        if (blocklistMaterials.contains(type)) continue;
                        itemEntity.remove();
                        removed++;
                    } catch (Throwable inner) {
                        logger.warning("ClearLag: failed handling an item entity: " + inner.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            logger.warning("ClearLag: performClear failed: " + t.getMessage());
        }
        if (removed > 0) logger.info("ClearLag: removed " + removed + " dropped items.");
        return removed;
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
            logger.info("ClearLag stopped.");
        }
    }

    public synchronized void reload() {
        stop();
        plugin.reloadConfig();
        loadConfig();
        if (enabled) start();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public Set<Material> getBlocklistMaterials() {
        return blocklistMaterials;
    }
}
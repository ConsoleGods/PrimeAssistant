package me.optimusprimerdc.primeAssistant.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-stasis EnderPearl listener.
 * Reads config from either 'anti-stasis.*' or top-level keys (fallback),
 * records pearl throw timestamps in a map and player metadata, and cancels
 * teleports when a pearl is older than the configured timeout.
 */
public class AntiStasisEnderPearl implements Listener {

    private static final String META_KEY = "primeassistant_lastPearl";

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastPearlTs = new ConcurrentHashMap<>();

    private long timeoutMillis;
    private boolean enabled;
    private boolean debug;
    private String cancelMessage;

    public AntiStasisEnderPearl(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfigValues();
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Read configuration with support for both 'anti-stasis.*' section and top-level keys.
     */
    public void reloadConfigValues() {
        // helper lambdas omitted for brevity; do explicit checks for both paths
        if (plugin.getConfig().contains("anti-stasis.enabled")) {
            this.enabled = plugin.getConfig().getBoolean("anti-stasis.enabled", true);
            this.timeoutMillis = Math.max(0, plugin.getConfig().getInt("anti-stasis.timeout-seconds", 30)) * 1000L;
            this.cancelMessage = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("anti-stasis.messages.cancel", "&cStasis pearls are not allowed."));
            this.debug = plugin.getConfig().getBoolean("anti-stasis.debug", false);
        } else {
            // fallback to top-level keys (your current config layout)
            this.enabled = plugin.getConfig().getBoolean("enabled", true);
            this.timeoutMillis = Math.max(0, plugin.getConfig().getInt("timeout-seconds", 30)) * 1000L;
            this.cancelMessage = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.cancel", "&cStasis pearls are not allowed."));
            this.debug = plugin.getConfig().getBoolean("debug", false);
        }

        if (debug) {
            plugin.getLogger().info("AntiStasis: enabled=" + enabled + " timeoutMs=" + timeoutMillis + " cancelMsg=" + cancelMessage);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!enabled) return;

        if (event.getEntity() instanceof EnderPearl pearl) {
            if (pearl.getShooter() instanceof Player shooter) {
                long now = System.currentTimeMillis();
                lastPearlTs.put(shooter.getUniqueId(), now);
                shooter.setMetadata(META_KEY, new FixedMetadataValue(plugin, now));
                if (debug) plugin.getLogger().info("Recorded pearl throw for " + shooter.getName() + " at " + now);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!enabled) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Long thrownAt = null;

        // prefer metadata (more resilient), then fallback to map
        if (player.hasMetadata(META_KEY)) {
            try {
                Object v = player.getMetadata(META_KEY).get(0).value();
                if (v instanceof Number) thrownAt = ((Number) v).longValue();
            } catch (Exception ignored) { /* fallback below */ }
        }

        if (thrownAt == null) {
            thrownAt = lastPearlTs.get(uuid);
        }

        if (thrownAt == null) {
            if (debug) plugin.getLogger().info("AntiStasis: no timestamp for " + player.getName() + " — allowing teleport");
            return;
        }

        long age = System.currentTimeMillis() - thrownAt;
        if (age > timeoutMillis) {
            event.setCancelled(true);
            player.sendMessage(cancelMessage);
            if (debug) plugin.getLogger().info("AntiStasis: cancelled pearl teleport for " + player.getName() + " (age=" + age + "ms)");
        } else {
            if (debug) plugin.getLogger().info("AntiStasis: allowed pearl teleport for " + player.getName() + " (age=" + age + "ms)");
        }

        // cleanup
        lastPearlTs.remove(uuid);
        try { player.removeMetadata(META_KEY, plugin); } catch (Exception ignored) {}

        // opportunistic cleanup
        if (lastPearlTs.size() > 500) {
            long cutoff = System.currentTimeMillis() - (timeoutMillis * 4L);
            lastPearlTs.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
    }
}
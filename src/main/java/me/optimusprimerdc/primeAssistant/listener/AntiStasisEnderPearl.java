package me.optimusprimerdc.primeAssistant.listener;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import me.optimusprimerdc.primeAssistant.config.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-stasis EnderPearl listener.
 * Tracks a FIFO queue of pearl timestamps per-player so multiple thrown pearls are handled correctly.
 */
public class AntiStasisEnderPearl implements Listener {

    private static final String META_KEY = "primeassistant_lastPearl";

    private final PrimeAssistant plugin;
    private final ConfigManager cfg;
    private final Map<UUID, Deque<Long>> lastPearlQueues = new ConcurrentHashMap<>();

    private long timeoutMillis;
    private boolean enabled;
    private boolean debug;
    private String cancelMessage;

    public AntiStasisEnderPearl(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        reloadConfigValues();
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reloadConfigValues() {
        this.enabled = cfg.isAntiStasisEnabled();
        this.timeoutMillis = Math.max(0, cfg.getAntiStasisTimeoutSeconds()) * 1000L;
        this.cancelMessage = ChatColor.translateAlternateColorCodes('&', cfg.getAntiStasisCancelMessage());
        this.debug = cfg.isAntiStasisDebug();

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
                UUID uuid = shooter.getUniqueId();

                // push timestamp to the player's FIFO queue
                Deque<Long> q = lastPearlQueues.computeIfAbsent(uuid, k -> new ArrayDeque<>());
                q.addLast(now);

                // keep metadata for resilience/fallback (stores latest)
                shooter.setMetadata(META_KEY, new FixedMetadataValue(plugin, now));

                if (debug) plugin.getLogger().info("Recorded pearl throw for " + shooter.getName() + " at " + now + " (queueSize=" + q.size() + ")");
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

        // Prefer per-player queue (FIFO) which matches multiple pearls correctly
        Deque<Long> q = lastPearlQueues.get(uuid);
        if (q != null) {
            thrownAt = q.pollFirst();
            if (q.isEmpty()) {
                lastPearlQueues.remove(uuid);
            }
        }

        // fallback to player metadata if queue had no entry
        if (thrownAt == null && player.hasMetadata(META_KEY)) {
            try {
                Object v = player.getMetadata(META_KEY).get(0).value();
                if (v instanceof Number) thrownAt = ((Number) v).longValue();
            } catch (Exception ignored) { /* fallback below */ }
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

        // cleanup metadata and queue entry already polled above
        try { player.removeMetadata(META_KEY, plugin); } catch (Exception ignored) {}

        // opportunistic cleanup: trim stale timestamps in queues to avoid memory growth
        if (lastPearlQueues.size() > 500) {
            long cutoff = System.currentTimeMillis() - (timeoutMillis * 4L);
            lastPearlQueues.forEach((id, deque) -> {
                synchronized (deque) {
                    while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                        deque.pollFirst();
                    }
                }
            });
            lastPearlQueues.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
    }
}
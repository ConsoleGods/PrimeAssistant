package me.optimusprimerdc.primeAssistant.listener;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import me.optimusprimerdc.primeAssistant.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.*;

public class Redstone implements Listener {

    private final PrimeAssistant plugin;
    private final ConfigManager cfg;
    private double tpsThreshold;
    private boolean enablePurge;
    private boolean redstoneDisabled = false;
    private final Map<String, Integer> chunkRedstoneEvents = new HashMap<>();
    private final Set<Material> redstoneBlocks = EnumSet.of(
            Material.REDSTONE_WIRE, Material.REDSTONE_BLOCK, Material.REDSTONE_TORCH,
            Material.REDSTONE_WALL_TORCH, Material.REPEATER, Material.COMPARATOR,
            Material.OBSERVER, Material.PISTON, Material.STICKY_PISTON,
            Material.DISPENSER, Material.DROPPER, Material.HOPPER
    );

    public Redstone(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        loadConfig();
        startTPSMonitor();
        if (enablePurge) {
            startChunkMonitor();
        }
    }

    public void reload() {
        loadConfig();
    }

    private void loadConfig() {
        this.tpsThreshold = cfg.getRedstoneTpsThreshold(); // expected to exist in ConfigManager
        this.enablePurge = cfg.isRedstonePurgeEnabled();
    }

    private void startTPSMonitor() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double currentTPS = getCurrentTPS();

            if (currentTPS < tpsThreshold && !redstoneDisabled) {
                redstoneDisabled = true;
                plugin.getLogger().warning("TPS dropped below " + tpsThreshold + " (current: " + String.format("%.2f", currentTPS) + "). Disabling Redstone.");
                Bukkit.broadcast(ChatColor.RED + "Redstone has been temporarily disabled due to low TPS (" + String.format("%.2f", currentTPS) + ").", "primeassistant.notify");
            } else if (currentTPS >= tpsThreshold && redstoneDisabled) {
                redstoneDisabled = false;
                plugin.getLogger().info("TPS recovered to " + String.format("%.2f", currentTPS) + ". Re-enabling Redstone.");
                Bukkit.broadcast(ChatColor.GREEN + "Redstone has been re-enabled.", "primeassistant.notify");
            }
        }, 20L, 20L);
    }

    private void startChunkMonitor() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (getCurrentTPS() < tpsThreshold) {
                List<Map.Entry<String, Integer>> sortedChunks = new ArrayList<>(chunkRedstoneEvents.entrySet());
                sortedChunks.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                for (int i = 0; i < Math.min(3, sortedChunks.size()); i++) {
                    String chunkKey = sortedChunks.get(i).getKey();
                    int events = sortedChunks.get(i).getValue();

                    if (events > 50) {
                        purgeChunkRedstone(chunkKey);
                    }
                }
            }
            chunkRedstoneEvents.clear();
        }, 100L, 100L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (enablePurge) {
            String chunkKey = getChunkKey(event.getBlock().getChunk());
            chunkRedstoneEvents.merge(chunkKey, 1, Integer::sum);
        }

        if (redstoneDisabled) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    private void purgeChunkRedstone(String chunkKey) {
        String[] parts = chunkKey.split(":");
        String worldName = parts[0];
        int x = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Chunk chunk = Bukkit.getWorld(worldName).getChunkAt(x, z);
            int removed = 0;

            for (int bx = 0; bx < 16; bx++) {
                for (int bz = 0; bz < 16; bz++) {
                    for (int by = chunk.getWorld().getMinHeight(); by < chunk.getWorld().getMaxHeight(); by++) {
                        Block block = chunk.getBlock(bx, by, bz);
                        if (redstoneBlocks.contains(block.getType())) {
                            block.setType(Material.AIR);
                            removed++;
                        }
                    }
                }
            }

            if (removed > 0) {
                plugin.getLogger().warning("Purged " + removed + " redstone blocks from chunk " + chunkKey);
                Bukkit.broadcast(ChatColor.GOLD + "Removed " + removed + " redstone blocks from a lagging chunk at " +
                        (chunk.getX() * 16) + ", " + (chunk.getZ() * 16), "primeassistant.notify");
            }
        });
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    private double getCurrentTPS() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] recentTps = (double[]) server.getClass().getField("recentTps").get(server);
            double tps = recentTps[0];
            return Math.min(tps, 20.0);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get TPS: " + e.getMessage());
            return 20.0;
        }
    }
}
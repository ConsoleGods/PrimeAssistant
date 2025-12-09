package me.optimusprimerdc.primeAssistant.clearlag;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import me.optimusprimerdc.primeAssistant.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Logger;

@SuppressWarnings("deprecation")
public class ClearLag {
    private final PrimeAssistant plugin;
    private final ConfigManager cfg;
    private final Logger logger;
    private BukkitTask clearTask;
    private BukkitTask countdownTask;

    private Set<Material> blocklistMaterials = Collections.emptySet();
    private int intervalSeconds = 600;
    private int warningSeconds = 30;
    private boolean enabled = true;

    private volatile boolean sequenceRunning = false;

    // logging
    private final Path logFile;
    private final Object writeLock = new Object();
    private final long maxLogSize = 5 * 1024 * 1024L;
    private final DateTimeFormatter tsFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public ClearLag(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        this.logger = plugin.getLogger();

        try {
            Path dir = plugin.getDataFolder().toPath();
            Files.createDirectories(dir);
            this.logFile = dir.resolve("clearlag.log");
            if (!Files.exists(logFile)) Files.createFile(logFile);
        } catch (IOException e) {
            throw new RuntimeException("ClearLag log init failed", e);
        }

        loadConfig();
        if (enabled) start();
    }

    public synchronized void loadConfig() {
        this.enabled = cfg.isClearlagEnabled();
        this.intervalSeconds = cfg.getClearlagIntervalSeconds();
        this.warningSeconds = cfg.getClearlagWarningSeconds();

        List<String> raw = cfg.getClearlagBlocklist();
        Set<Material> parsed = new HashSet<>();
        if (raw != null) {
            for (String s : raw) {
                if (s == null) continue;
                Material m = Material.matchMaterial(s.trim().toUpperCase(Locale.ROOT));
                if (m != null) parsed.add(m);
                else logger.warning("ClearLag: unknown material in blocklist: " + s);
            }
        }
        this.blocklistMaterials = Collections.unmodifiableSet(parsed);
    }

    public synchronized void start() {
        if (clearTask != null) return;
        if (!enabled) {
            logger.info("ClearLag is disabled in config.");
            return;
        }
        long ticks = Math.max(1, intervalSeconds) * 20L;
        clearTask = Bukkit.getScheduler().runTaskTimer(plugin, this::beginSequence, ticks, ticks);
        logger.info("ClearLag started (interval: " + intervalSeconds + "s).");
    }

    private void beginSequence() {
        if (sequenceRunning) {
            logger.fine("ClearLag: sequence already running, skipping this interval.");
            return;
        }
        sequenceRunning = true;

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        new BukkitRunnable() {
            int secondsLeft = warningSeconds;

            @Override
            public void run() {
                if (secondsLeft == warningSeconds || secondsLeft <= 10 || secondsLeft % 60 == 0) {
                    String msg = ChatColor.GOLD + "[ClearLag] " + ChatColor.YELLOW + "Items will be removed in "
                            + ChatColor.RED + ChatColor.BOLD + secondsLeft + ChatColor.RESET + ChatColor.YELLOW + " seconds.";
                    Bukkit.broadcastMessage(msg);
                    logger.fine("Broadcast: " + stripColor(msg));
                }

                if (secondsLeft <= 0) {
                    int removed = performClearAndLog();
                    Bukkit.broadcastMessage(ChatColor.GOLD + "[ClearLag] " + ChatColor.GREEN + "Removed " + ChatColor.WHITE + removed + ChatColor.GREEN + " dropped items.");
                    sequenceRunning = false;
                    this.cancel();
                }
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private int performClearAndLog() {
        List<ItemStack> removedItems = new ArrayList<>();
        int removed = 0;
        try {
            for (World world : Bukkit.getWorlds()) {
                for (Entity ent : new ArrayList<>(world.getEntities())) {
                    try {
                        if (!(ent instanceof Item)) continue;
                        Item itemEntity = (Item) ent;
                        ItemStack stack = itemEntity.getItemStack();
                        if (stack == null) continue;
                        Material type = stack.getType();
                        if (blocklistMaterials.contains(type)) continue;
                        removedItems.add(stack.clone());
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

        if (removed > 0) {
            String summary = "ClearLag: removed " + removed + " dropped items.";
            logger.info(summary);
            writeLog(summary);

            if (!removedItems.isEmpty()) {
                int limit = 100;
                List<String> formatted = removedItems.stream()
                        .limit(limit)
                        .map(this::formatItem)
                        .collect(Collectors.toList());
                String joined = String.join(" ; ", formatted);
                if (removedItems.size() > limit) {
                    joined += " ; (and " + (removedItems.size() - limit) + " more)";
                }
                String detail = "Removed items: " + joined;
                logger.info(detail);
                writeLog(detail);
            }
        }
        return removed;
    }

    public synchronized void stop() {
        if (clearTask != null) {
            clearTask.cancel();
            clearTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        logger.info("ClearLag stopped.");
    }

    public synchronized void reload() {
        stop();
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

    private String formatItem(ItemStack item) {
        if (item == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append(item.getAmount()).append("x ").append(item.getType().toString());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                sb.append(" [Name: '").append(meta.getDisplayName()).append("']");
            }
            if (meta.hasLore()) {
                sb.append(" [Lore: ").append(meta.getLore()).append("]");
            }
            Map<Enchantment, Integer> ench = item.getEnchantments();
            if (!ench.isEmpty()) {
                String enchStr = ench.entrySet().stream()
                        .map(e -> {
                            try {
                                return e.getKey().getKey().getKey() + " " + e.getValue();
                            } catch (Throwable t) {
                                return e.getKey().getName() + " " + e.getValue();
                            }
                        })
                        .collect(Collectors.joining(", "));
                sb.append(" [Enchants: ").append(enchStr).append("]");
            }
        }
        return sb.toString();
    }

    private void writeLog(String msg) {
        String line = tsFormat.format(Instant.now()) + " [INFO] " + msg + System.lineSeparator();
        synchronized (writeLock) {
            try (FileChannel ch = FileChannel.open(logFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                try (FileLock lock = ch.lock()) {
                    ByteBuffer buf = StandardCharsets.UTF_8.encode(line);
                    while (buf.hasRemaining()) ch.write(buf);
                    ch.force(true);
                }
            } catch (IOException e) {
                logger.warning("ClearLag: log write failed: " + e.getMessage());
            }
            tryRotateIfNeeded();
        }
    }

    private void tryRotateIfNeeded() {
        try {
            long size = Files.size(logFile);
            if (size <= maxLogSize) return;
            String rotatedName = "clearlag-" + Instant.now().toString().replace(":", "-") + ".log";
            Path rotated = logFile.resolveSibling(rotatedName);
            try {
                Files.move(logFile, rotated, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(logFile, rotated);
            }
            try {
                Files.createFile(logFile);
            } catch (FileAlreadyExistsException ignored) {
                // fine
            }
        } catch (IOException e) {
            logger.warning("ClearLag: rotate failed: " + e.getMessage());
        }
    }

    private String stripColor(String colored) {
        return colored.replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "");
    }
}
package me.optimusprimerdc.primeAssistant.listener;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FastLeafDecay implements Listener {

    private final PrimeAssistant plugin;
    private final Map<String, Long> playerPlaced = new ConcurrentHashMap<>();
    private final Set<String> processingTrees = ConcurrentHashMap.newKeySet();

    public FastLeafDecay(PrimeAssistant plugin) {
        this.plugin = plugin;
    }

    private String key(Block b) {
        Location l = b.getLocation();
        return Objects.requireNonNull(l.getWorld()).getUID() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private String treeKey(Block b) {
        Location l = b.getLocation();
        return Objects.requireNonNull(l.getWorld()).getUID() + ":" +
                (l.getBlockX() >> 4) + ":" + (l.getBlockY() >> 4) + ":" + (l.getBlockZ() >> 4);
    }

    @EventHandler
    public void onLeafPlace(BlockPlaceEvent e) {
        if (Tag.LEAVES.isTagged(e.getBlock().getType())) {
            playerPlaced.put(key(e.getBlock()), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onLeafDecay(LeavesDecayEvent e) {
        Block leaf = e.getBlock();
        String leafKey = key(leaf);

        if (Math.random() < 0.01) {
            long now = System.currentTimeMillis();
            playerPlaced.entrySet().removeIf(entry -> now - entry.getValue() > 300000);
        }

        if (playerPlaced.containsKey(leafKey)) {
            return;
        }

        e.setCancelled(true);

        String tree = treeKey(leaf);
        if (!processingTrees.add(tree)) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Set<Block> leavesToBreak = new HashSet<>();
                    Queue<Block> toCheck = new LinkedList<>();
                    Set<String> checked = new HashSet<>();

                    toCheck.add(leaf);

                    while (!toCheck.isEmpty() && leavesToBreak.size() < 2000) {
                        Block current = toCheck.poll();
                        String currentKey = key(current);

                        if (!checked.add(currentKey)) continue;
                        if (!Tag.LEAVES.isTagged(current.getType())) continue;
                        if (playerPlaced.containsKey(currentKey)) continue;

                        if (!hasLogNearby(current.getLocation())) {
                            leavesToBreak.add(current);

                            for (int x = -1; x <= 1; x++) {
                                for (int y = -1; y <= 1; y++) {
                                    for (int z = -1; z <= 1; z++) {
                                        if (x == 0 && y == 0 && z == 0) continue;
                                        Block nearby = current.getRelative(x, y, z);
                                        if (Tag.LEAVES.isTagged(nearby.getType())) {
                                            toCheck.add(nearby);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!leavesToBreak.isEmpty()) {
                        List<Block> leafList = new ArrayList<>(leavesToBreak);

                        new BukkitRunnable() {
                            int index = 0;

                            @Override
                            public void run() {
                                int batch = 100;
                                int processed = 0;

                                while (index < leafList.size() && processed < batch) {
                                    Block b = leafList.get(index++);
                                    if (Tag.LEAVES.isTagged(b.getType())) {
                                        b.breakNaturally();
                                        playerPlaced.remove(key(b));
                                    }
                                    processed++;
                                }

                                if (index >= leafList.size()) {
                                    cancel();
                                    processingTrees.remove(tree);
                                }
                            }
                        }.runTaskTimer(plugin, 0L, 1L);
                    } else {
                        processingTrees.remove(tree);
                    }
                } catch (Exception ex) {
                    processingTrees.remove(tree);
                }
            }
        }.runTask(plugin);
    }

    private boolean hasLogNearby(Location leafLoc) {
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block check = leafLoc.getBlock().getRelative(x, y, z);
                    if (Tag.LOGS.isTagged(check.getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

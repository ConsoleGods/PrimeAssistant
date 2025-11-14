package me.optimusprimerdc.primeAssistant.listener;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class FastLeafDecay implements Listener {

    private final PrimeAssistant plugin;
    private final Map<String, Long> playerPlaced = new HashMap<>();

    public FastLeafDecay(PrimeAssistant plugin) {
        this.plugin = plugin;
    }

    private String key(Block b) {
        Location l = b.getLocation();
        return l.getWorld().getUID() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    @EventHandler
    public void onLeafPlace(BlockPlaceEvent e) {
        if (Tag.LEAVES.isTagged(e.getBlock().getType())) {
            playerPlaced.put(key(e.getBlock()), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onLeafDecay(LeavesDecayEvent e) {
        // Protect player-placed leaves
        if (playerPlaced.containsKey(key(e.getBlock()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onLogBreak(BlockBreakEvent e) {
        Block broken = e.getBlock();
        if (!Tag.LOGS.isTagged(broken.getType())) return;

        // STEP 1: Find all connected logs in this tree
        Set<Block> treeLogs = getConnectedLogs(broken);

        // STEP 2: Remove the broken log from the set
        treeLogs.remove(broken);

        // STEP 3: If any logs remain, the tree still stands → do not decay leaves
        if (!treeLogs.isEmpty()) return;

        // STEP 4: Last log broken → collect leaves connected to the tree
        Set<Block> leaves = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        // Start BFS from the broken log's location
        for (Block n : neighbors(broken)) {
            if (Tag.LEAVES.isTagged(n.getType()) && !playerPlaced.containsKey(key(n)) && leaves.add(n)) {
                queue.add(n);
            }
        }

        // BFS through leaves connected to this tree
        while (!queue.isEmpty()) {
            Block leaf = queue.poll();
            for (Block n : neighbors(leaf)) {
                if (Tag.LEAVES.isTagged(n.getType()) && !playerPlaced.containsKey(key(n)) && leaves.add(n)) {
                    queue.add(n);
                }
            }
        }

        if (leaves.isEmpty()) return;

        // STEP 5: Break all collected leaves in batches
        List<Block> leafList = new ArrayList<>(leaves);

        new BukkitRunnable() {
            int i = 0;
            final int batch = 200;

            @Override
            public void run() {
                int processed = 0;
                while (i < leafList.size() && processed < batch) {
                    Block leaf = leafList.get(i++);
                    if (Tag.LEAVES.isTagged(leaf.getType())) {
                        leaf.breakNaturally();
                        playerPlaced.remove(key(leaf));
                    }
                    processed++;
                }
                if (i >= leafList.size()) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // BFS to collect all logs connected in the tree
    private Set<Block> getConnectedLogs(Block start) {
        Queue<Block> queue = new ArrayDeque<>();
        Set<Block> logs = new HashSet<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Block b = queue.poll();
            if (!Tag.LOGS.isTagged(b.getType())) continue;
            if (!logs.add(b)) continue;

            for (Block n : neighbors(b)) {
                if (Tag.LOGS.isTagged(n.getType())) queue.add(n);
            }
        }

        return logs;
    }

    private Block[] neighbors(Block b) {
        return new Block[]{
                b.getRelative(1,0,0),
                b.getRelative(-1,0,0),
                b.getRelative(0,1,0),
                b.getRelative(0,-1,0),
                b.getRelative(0,0,1),
                b.getRelative(0,0,-1)
        };
    }
}

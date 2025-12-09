package me.optimusprimerdc.primeAssistant.listener;

import me.optimusprimerdc.primeAssistant.PrimeAssistant;
import me.optimusprimerdc.primeAssistant.config.ConfigManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.ChatColor;

import java.util.*;

public class Gunpowder implements Listener {

    private final PrimeAssistant plugin;
    private final ConfigManager cfg;
    private final Set<Location> gunpowder = Collections.synchronizedSet(new HashSet<>());
    private final Map<Location, Integer> particleTasks = new HashMap<>();

    private static final EnumSet<Material> DISALLOWED_SUPPORTS = EnumSet.of(
            Material.TALL_GRASS,
            Material.SHORT_GRASS,
            Material.FERN,
            Material.LARGE_FERN,
            Material.DEAD_BUSH,
            Material.VINE,
            Material.SWEET_BERRY_BUSH,
            Material.POPPY,
            Material.DANDELION,
            Material.BLUE_ORCHID,
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.OXEYE_DAISY,
            Material.LILY_OF_THE_VALLEY,
            Material.SUNFLOWER,
            Material.LILAC,
            Material.ROSE_BUSH,
            Material.PEONY,
            Material.CORNFLOWER,
            Material.WITHER_ROSE,
            Material.SPORE_BLOSSOM,
            Material.TORCHFLOWER,
            Material.PITCHER_PLANT,
            Material.CAVE_VINES,
            Material.CAVE_VINES_PLANT,
            Material.LEAF_LITTER,
            Material.MOSS_CARPET,
            Material.AZALEA,
            Material.FLOWERING_AZALEA,
            Material.SUGAR_CANE,
            Material.BAMBOO,
            Material.KELP,
            Material.SEAGRASS,
            Material.TWISTING_VINES,
            Material.WEEPING_VINES,
            Material.NETHER_SPROUTS,
            Material.SHROOMLIGHT
    );

    // Configurable particle & fuse settings (defaults below)
    private int ambientIntervalTicks = 10;
    private int fuseStepTicks = 3;

    private int dustCount = 3;
    private int dustR = 50, dustG = 50, dustB = 50;
    private float dustSize = 1.2f;
    private double dustOffsetX = 0.2, dustOffsetY = 0.02, dustOffsetZ = 0.2;

    private int flameCount = 8;
    private int lavaCount = 2;
    private int smokeCount = 3;
    private double flameOffsetX = 0.15, flameOffsetY = 0.1, flameOffsetZ = 0.15;

    private volatile boolean enabled = true;

    public Gunpowder(PrimeAssistant plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        loadConfig();
    }

    public synchronized void loadConfig() {
        this.enabled = cfg.isGunpowderEnabled();
        this.ambientIntervalTicks = Math.max(1, cfg.getGunpowderAmbientIntervalTicks());
        this.fuseStepTicks = Math.max(1, cfg.getGunpowderFuseStepTicks());

        // If ConfigManager is extended later with particle settings, wire them here.
        // Keep current defaults otherwise.
    }

    public synchronized void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        cfg.setGunpowderEnabled(enabled); // optional: implement setter in ConfigManager to persist
        if (!enabled) {
            for (Integer id : new ArrayList<>(particleTasks.values())) {
                Bukkit.getScheduler().cancelTask(id);
            }
            particleTasks.clear();
            gunpowder.clear();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private Location normalize(Location loc) {
        return loc == null ? null : loc.getBlock().getLocation();
    }

    @EventHandler
    public void onPlace(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.GUNPOWDER) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        BlockFace face = e.getBlockFace();
        Block target = clicked.getRelative(face);
        if (!target.getType().isAir()) return;

        Block below = target.getRelative(BlockFace.DOWN);
        Material belowType = below.getType();
        if (belowType.isAir() || below.isLiquid()) return;

        if (Tag.FLOWERS.isTagged(belowType) || Tag.LEAVES.isTagged(belowType) || DISALLOWED_SUPPORTS.contains(belowType)) {
            return;
        }

        Location loc = normalize(target.getLocation());
        Location aboveLoc = normalize(target.getRelative(BlockFace.UP).getLocation());
        Location belowLoc = normalize(below.getLocation());

        if (gunpowder.contains(aboveLoc) || gunpowder.contains(belowLoc)) {
            return;
        }

        if (gunpowder.contains(loc)) return;

        Player player = e.getPlayer();
        if (!me.optimusprimerdc.primeAssistant.integration.hooks.WorldGuard.WorldGuard.canBuildAt(player, target.getLocation())) {
            player.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "Hey! " + ChatColor.GRAY + "Sorry, but you can't place gunpowder here.");
            return;
        }

        e.setCancelled(true);

        target.setType(Material.TRIPWIRE);
        Tripwire data = (Tripwire) target.getBlockData();
        data.setAttached(false);
        target.setBlockData(data);

        gunpowder.add(loc);

        startAmbientParticles(loc);

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        target.getWorld().playSound(loc, Sound.BLOCK_SAND_PLACE, 0.5f, 0.8f);
    }

    private void startAmbientParticles(Location loc) {
        if (!enabled) return;
        final Location taskLoc = normalize(loc);

        Integer existing = particleTasks.get(taskLoc);
        if (existing != null) {
            Bukkit.getScheduler().cancelTask(existing);
        }

        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled || !gunpowder.contains(taskLoc)) {
                    cancel();
                    particleTasks.remove(taskLoc);
                    return;
                }

                World world = taskLoc.getWorld();
                if (world == null) {
                    cancel();
                    particleTasks.remove(taskLoc);
                    return;
                }

                world.spawnParticle(Particle.DUST,
                        taskLoc.clone().add(0.5, 0.05, 0.5),
                        dustCount, dustOffsetX, dustOffsetY, dustOffsetZ, 0,
                        new Particle.DustOptions(Color.fromRGB(dustR, dustG, dustB), dustSize));
            }
        }.runTaskTimer(plugin, 0L, ambientIntervalTicks).getTaskId();

        particleTasks.put(taskLoc, taskId);
    }

    @EventHandler
    public void onIgnite(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (e.getItem() == null || e.getItem().getType() != Material.FLINT_AND_STEEL) return;
        if (e.getClickedBlock() == null) return;

        Block b = e.getClickedBlock();
        if (!isGunpowder(b)) return;

        igniteFuse(b);
    }

    private boolean isGunpowder(Block b) {
        if (b == null || b.getType() != Material.TRIPWIRE) return false;
        return gunpowder.contains(normalize(b.getLocation()));
    }

    private void removeGunpowderAt(Location loc, boolean dropItem) {
        loc = normalize(loc);
        if (loc == null) return;

        boolean removed = gunpowder.remove(loc);

        Integer taskId = particleTasks.remove(loc);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        if (removed && dropItem) {
            World world = loc.getWorld();
            if (world != null) {
                world.dropItemNaturally(loc, new ItemStack(Material.GUNPOWDER, 1));
            }

            final Location cleanupLoc = loc.clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeNearbyDroppedStrings(cleanupLoc), 1L);
        }
    }

    private void removeNearbyDroppedStrings(Location loc) {
        if (loc == null) return;
        World world = loc.getWorld();
        if (world == null) return;

        double radius = 0.75;
        for (org.bukkit.entity.Entity ent : world.getNearbyEntities(loc.add(0.5, 0.5, 0.5), radius, radius, radius)) {
            if (ent instanceof Item) {
                Item item = (Item) ent;
                ItemStack stack = item.getItemStack();
                if (stack != null && stack.getType() == Material.STRING) {
                    item.remove();
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!enabled) return;
        Block b = e.getBlock();
        if (isGunpowder(b)) {
            e.setCancelled(true);
            Player p = e.getPlayer();

            removeGunpowderAt(b.getLocation(), false);
            b.setType(Material.AIR);

            if (p.getGameMode() != GameMode.CREATIVE) {
                World world = b.getWorld();
                if (world != null) {
                    world.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.GUNPOWDER));
                }
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!enabled) return;
        for (Block b : new ArrayList<>(e.blockList())) {
            if (isGunpowder(b)) {
                removeGunpowderAt(b.getLocation(), true);
                e.blockList().remove(b);
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!enabled) return;
        for (Block b : new ArrayList<>(e.blockList())) {
            if (isGunpowder(b)) {
                removeGunpowderAt(b.getLocation(), true);
                e.blockList().remove(b);
            }
        }
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent e) {
        if (!enabled) return;
        Block b = e.getBlock();
        Location loc = normalize(b.getLocation());

        if (gunpowder.contains(loc) && b.getType() != Material.TRIPWIRE) {
            removeGunpowderAt(loc, true);
        }
    }

    private void igniteFuse(Block start) {
        if (!enabled) return;
        Queue<Block> frontier = new LinkedList<>();
        Set<Location> visited = new HashSet<>();

        frontier.add(start);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (frontier.isEmpty()) {
                    cancel();
                    return;
                }

                int size = frontier.size();
                for (int i = 0; i < size; i++) {
                    Block b = frontier.poll();
                    if (b == null) continue;

                    Location loc = normalize(b.getLocation());
                    if (visited.contains(loc)) continue;
                    visited.add(loc);

                    World world = b.getWorld();
                    if (flameCount > 0) {
                        world.spawnParticle(Particle.FLAME,
                                loc.clone().add(0.5 + flameOffsetX, 0.2 + flameOffsetY, 0.5 + flameOffsetZ),
                                flameCount, flameOffsetX, flameOffsetY, flameOffsetZ, 0);
                    }
                    if (lavaCount > 0) {
                        world.spawnParticle(Particle.LAVA,
                                loc.clone().add(0.5, 0.2, 0.5),
                                lavaCount, 0.1, 0.1, 0.1, 0);
                    }
                    if (smokeCount > 0) {
                        world.spawnParticle(Particle.SMOKE,
                                loc.clone().add(0.5, 0.2, 0.5),
                                smokeCount, 0.2, 0.2, 0.2, 0);
                    }
                    world.playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 0.7f, 1.4f);
                    world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.2f);

                    removeGunpowderAt(loc, false);
                    b.setType(Material.AIR);

                    for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN, BlockFace.UP)) {
                        Block nb = b.getRelative(face);
                        if (nb == null) continue;
                        if (isGunpowder(nb)) {
                            frontier.add(nb);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, fuseStepTicks);
    }
}
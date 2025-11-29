package me.optimusprimerdc.primeAssistant.listener;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.entity.TNTPrimed;
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

import java.util.*;

public class Gunpowder implements Listener {

    private final JavaPlugin plugin;
    private final Set<Location> gunpowder = Collections.synchronizedSet(new HashSet<>());
    private final Map<Location, Integer> particleTasks = new HashMap<>();

    // Disallowed "soft" supports (flowers, grass, leaf-like blocks, etc.)
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

    public Gunpowder(JavaPlugin plugin) {
        this.plugin = plugin;
        setupDefaults();
        loadConfig();
    }

    private Location normalize(Location loc) {
        return loc == null ? null : loc.getBlock().getLocation();
    }

    private void setupDefaults() {
        // particle defaults
        plugin.getConfig().addDefault("gunpowder.particles.ambient-interval-ticks", ambientIntervalTicks);
        plugin.getConfig().addDefault("gunpowder.particles.dust.count", dustCount);
        plugin.getConfig().addDefault("gunpowder.particles.dust.color.r", dustR);
        plugin.getConfig().addDefault("gunpowder.particles.dust.color.g", dustG);
        plugin.getConfig().addDefault("gunpowder.particles.dust.color.b", dustB);
        plugin.getConfig().addDefault("gunpowder.particles.dust.size", dustSize);
        plugin.getConfig().addDefault("gunpowder.particles.dust.offset.x", dustOffsetX);
        plugin.getConfig().addDefault("gunpowder.particles.dust.offset.y", dustOffsetY);
        plugin.getConfig().addDefault("gunpowder.particles.dust.offset.z", dustOffsetZ);

        plugin.getConfig().addDefault("gunpowder.particles.flame.count", flameCount);
        plugin.getConfig().addDefault("gunpowder.particles.lava.count", lavaCount);
        plugin.getConfig().addDefault("gunpowder.particles.smoke.count", smokeCount);
        plugin.getConfig().addDefault("gunpowder.particles.flame.offset.x", flameOffsetX);
        plugin.getConfig().addDefault("gunpowder.particles.flame.offset.y", flameOffsetY);
        plugin.getConfig().addDefault("gunpowder.particles.flame.offset.z", flameOffsetZ);

        // fuse
        plugin.getConfig().addDefault("gunpowder.fuse-step-ticks", fuseStepTicks);

        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public synchronized void loadConfig() {
        ambientIntervalTicks = Math.max(1, plugin.getConfig().getInt("gunpowder.particles.ambient-interval-ticks", ambientIntervalTicks));
        fuseStepTicks = Math.max(1, plugin.getConfig().getInt("gunpowder.fuse-step-ticks", fuseStepTicks));

        dustCount = Math.max(0, plugin.getConfig().getInt("gunpowder.particles.dust.count", dustCount));
        dustR = clampColor(plugin.getConfig().getInt("gunpowder.particles.dust.color.r", dustR));
        dustG = clampColor(plugin.getConfig().getInt("gunpowder.particles.dust.color.g", dustG));
        dustB = clampColor(plugin.getConfig().getInt("gunpowder.particles.dust.color.b", dustB));
        dustSize = (float) plugin.getConfig().getDouble("gunpowder.particles.dust.size", dustSize);
        dustOffsetX = plugin.getConfig().getDouble("gunpowder.particles.dust.offset.x", dustOffsetX);
        dustOffsetY = plugin.getConfig().getDouble("gunpowder.particles.dust.offset.y", dustOffsetY);
        dustOffsetZ = plugin.getConfig().getDouble("gunpowder.particles.dust.offset.z", dustOffsetZ);

        flameCount = Math.max(0, plugin.getConfig().getInt("gunpowder.particles.flame.count", flameCount));
        lavaCount = Math.max(0, plugin.getConfig().getInt("gunpowder.particles.lava.count", lavaCount));
        smokeCount = Math.max(0, plugin.getConfig().getInt("gunpowder.particles.smoke.count", smokeCount));
        flameOffsetX = plugin.getConfig().getDouble("gunpowder.particles.flame.offset.x", flameOffsetX);
        flameOffsetY = plugin.getConfig().getDouble("gunpowder.particles.flame.offset.y", flameOffsetY);
        flameOffsetZ = plugin.getConfig().getDouble("gunpowder.particles.flame.offset.z", flameOffsetZ);
    }

    private int clampColor(int v) {
        return Math.max(0, Math.min(255, v));
    }

    @EventHandler
    public void onPlace(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.GUNPOWDER) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        BlockFace face = e.getBlockFace();
        Block target = clicked.getRelative(face);
        if (!target.getType().isAir()) return;

        // Prevent placing in midair or above liquid (water/lava) or on top of plants/flowers/leaves
        Block below = target.getRelative(BlockFace.DOWN);
        Material belowType = below.getType();
        if (belowType.isAir() || below.isLiquid()) return;

        // Tag checks (flowers, leaves) plus explicit plant materials
        if (Tag.FLOWERS.isTagged(belowType) || Tag.LEAVES.isTagged(belowType) || DISALLOWED_SUPPORTS.contains(belowType)) {
            return;
        }

        // Normalize and check vertical stacking: do not allow on top of another placed gunpowder
        Location loc = normalize(target.getLocation());

        Location aboveLoc = normalize(target.getRelative(BlockFace.UP).getLocation());
        Location belowLoc = normalize(below.getLocation());

        // If there's already tracked gunpowder above or below, refuse placement
        if (gunpowder.contains(aboveLoc) || gunpowder.contains(belowLoc)) {
            return;
        }

        // Check if gunpowder already exists at this exact location
        if (gunpowder.contains(loc)) return;

        e.setCancelled(true);

        target.setType(Material.TRIPWIRE);
        Tripwire data = (Tripwire) target.getBlockData();
        data.setAttached(false);
        target.setBlockData(data);

        gunpowder.add(loc);

        // Add ambient particles to make the trail visible
        startAmbientParticles(loc);

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        // Play placement sound
        target.getWorld().playSound(loc, Sound.BLOCK_SAND_PLACE, 0.5f, 0.8f);
    }

    private void startAmbientParticles(Location loc) {
        final Location taskLoc = normalize(loc);

        Integer existing = particleTasks.get(taskLoc);
        if (existing != null) {
            Bukkit.getScheduler().cancelTask(existing);
        }

        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gunpowder.contains(taskLoc)) {
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

                // dust particle (configurable)
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

        boolean removed = gunpowder.remove(loc);

        Integer taskId = particleTasks.remove(loc);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        if (removed && dropItem) {
            World world = loc.getWorld();
            if (world != null) {
                world.dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), new ItemStack(Material.GUNPOWDER));
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (isGunpowder(b)) {
            removeGunpowderAt(b.getLocation(), true);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : new ArrayList<>(e.blockList())) {
            if (isGunpowder(b)) {
                removeGunpowderAt(b.getLocation(), true);
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block b : new ArrayList<>(e.blockList())) {
            if (isGunpowder(b)) {
                removeGunpowderAt(b.getLocation(), true);
            }
        }
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        Location loc = normalize(b.getLocation());

        if (gunpowder.contains(loc) && b.getType() != Material.TRIPWIRE) {
            removeGunpowderAt(loc, true);
        }
    }

    private void igniteFuse(Block start) {
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
                    if (!isGunpowder(b)) continue;

                    Location loc = normalize(b.getLocation());
                    if (visited.contains(loc)) continue;
                    visited.add(loc);

                    World world = b.getWorld();
                    // configurable flame / lava / smoke particles & offsets
                    if (flameCount > 0) {
                        world.spawnParticle(Particle.FLAME, loc.clone().add(0.5, flameOffsetY, 0.5),
                                flameCount, flameOffsetX, flameOffsetY, flameOffsetZ, 0);
                    }
                    if (lavaCount > 0) {
                        world.spawnParticle(Particle.LAVA, loc.clone().add(0.5, flameOffsetY, 0.5),
                                lavaCount, flameOffsetX, flameOffsetY, flameOffsetZ, 0);
                    }
                    if (smokeCount > 0) {
                        world.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0.5, flameOffsetY, 0.5),
                                smokeCount, flameOffsetX, flameOffsetY, flameOffsetZ, 0.01f);
                    }
                    world.playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 0.7f, 1.4f);
                    world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.2f);

                    // remove tracking and cancel ambient particles before changing the block
                    removeGunpowderAt(loc, false);
                    b.setType(Material.AIR);

                    // allow propagation horizontally, downward, and upward (one block)
                    for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN, BlockFace.UP)) {
                        Block next = b.getRelative(face);

                        if (next.getType() == Material.TNT) {
                            next.setType(Material.AIR);
                            next.getWorld().spawn(next.getLocation().add(0.5, 0, 0.5), TNTPrimed.class);
                            continue;
                        }

                        Location nextLoc = normalize(next.getLocation());
                        if (isGunpowder(next) && !visited.contains(nextLoc)) {
                            frontier.add(next);
                            continue;
                        }

                        // For horizontal faces, also allow climbing: check the block one up at that face
                        if (face != BlockFace.UP && face != BlockFace.DOWN) {
                            Block upNeighbor = next.getRelative(BlockFace.UP);
                            Location upNeighborLoc = normalize(upNeighbor.getLocation());
                            if (isGunpowder(upNeighbor) && !visited.contains(upNeighborLoc)) {
                                frontier.add(upNeighbor);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, fuseStepTicks);
    }
}
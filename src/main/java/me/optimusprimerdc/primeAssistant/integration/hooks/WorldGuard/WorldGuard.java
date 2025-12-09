package me.optimusprimerdc.primeAssistant.integration.hooks.WorldGuard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public final class WorldGuard {

    // Set to true to deny by default when WG is present but no definitive answer can be obtained.
    private static final boolean CONSERVATIVE = false;

    private WorldGuard() { /* utility */ }

    public static boolean canBuildAt(final Player player, final Location loc) {
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) return true;

        Object regionContainer = null;
        Object regionQuery = null;
        Object adaptedPlayer = null;
        Object adaptedLocation = null;
        Object buildFlag = null;
        Object blockVector3 = null;

        // WG7+ RegionQuery path
        try {
            Class<?> worldguardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Method getInstance = worldguardClass.getMethod("getInstance");
            Object wgInstance = getInstance.invoke(null);
            Method getPlatform = worldguardClass.getMethod("getPlatform");
            Object platform = getPlatform.invoke(wgInstance);

            Method getRegionContainer = platform.getClass().getMethod("getRegionContainer");
            regionContainer = getRegionContainer.invoke(platform);
            Method createQuery = regionContainer.getClass().getMethod("createQuery");
            regionQuery = createQuery.invoke(regionContainer);

            try {
                Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                Method adaptPlayer = bukkitAdapter.getMethod("adapt", org.bukkit.entity.Player.class);
                Method adaptLoc = bukkitAdapter.getMethod("adapt", org.bukkit.Location.class);
                adaptedPlayer = adaptPlayer.invoke(null, player);
                adaptedLocation = adaptLoc.invoke(null, loc);
            } catch (Throwable ignored) {}

            blockVector3 = tryCreateBlockVector(loc);

            try {
                Class<?> flagsCls = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
                Field fld = flagsCls.getField("BUILD");
                buildFlag = fld.get(null);
            } catch (Throwable ex1) {
                try {
                    Class<?> defFlagCls = Class.forName("com.sk89q.worldguard.protection.flags.DefaultFlag");
                    Field fld2 = defFlagCls.getField("BUILD");
                    buildFlag = fld2.get(null);
                } catch (Throwable ignored) {}
            }

            if (regionQuery != null && (adaptedPlayer != null || adaptedLocation != null || blockVector3 != null) && buildFlag != null) {
                for (Method m : regionQuery.getClass().getMethods()) {
                    String name = m.getName();
                    if (!(name.equals("testState") || name.equals("queryState") || name.equals("getFlag") || name.equals("queryFlag")))
                        continue;
                    try {
                        Class<?>[] params = m.getParameterTypes();
                        Object res = null;

                        if (params.length == 3) {
                            try { if (adaptedLocation != null) res = m.invoke(regionQuery, adaptedLocation, adaptedPlayer, buildFlag); } catch (Throwable ignored) {}
                            if (res == null) try { if (adaptedPlayer != null && adaptedLocation != null) res = m.invoke(regionQuery, adaptedPlayer, adaptedLocation, buildFlag); } catch (Throwable ignored) {}
                            if (res == null) try { if (blockVector3 != null) res = m.invoke(regionQuery, blockVector3, adaptedPlayer, buildFlag); } catch (Throwable ignored) {}
                            if (res == null) try { if (adaptedPlayer != null && blockVector3 != null) res = m.invoke(regionQuery, adaptedPlayer, blockVector3, buildFlag); } catch (Throwable ignored) {}
                        } else if (params.length == 2) {
                            try { if (adaptedLocation != null) res = m.invoke(regionQuery, adaptedLocation, buildFlag); } catch (Throwable ignored) {}
                            if (res == null) try { if (blockVector3 != null) res = m.invoke(regionQuery, blockVector3, buildFlag); } catch (Throwable ignored) {}
                            if (res == null) try { if (adaptedPlayer != null) res = m.invoke(regionQuery, adaptedPlayer, buildFlag); } catch (Throwable ignored) {}
                        } else {
                            try { if (adaptedLocation != null && params.length == 1 && params[0].isAssignableFrom(adaptedLocation.getClass())) res = m.invoke(regionQuery, adaptedLocation); } catch (Throwable ignored) {}
                        }

                        Boolean interpreted = interpretResult(res);
                        if (interpreted != null) return interpreted;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // If regionQuery did not provide a definitive result, treat presence of any region as deny
        try {
            if (regionQuery != null) {
                if (tryRegionQueryForRegions(regionQuery, adaptedLocation, blockVector3)) return false;
            }
        } catch (Throwable ignored) {}

        try {
            if (regionContainer != null && (adaptedLocation != null || blockVector3 != null)) {
                World world = loc.getWorld();
                if (world != null && hasAnyRegion(regionContainer, adaptedLocation != null ? adaptedLocation : blockVector3, world)) {
                    return false;
                }
            }
        } catch (Throwable ignored) {}

        // Older WorldGuardPlugin / canBuild(...) path
        try {
            Class<?> wgpClass = null;
            Object wgpInstance = null;
            try {
                wgpClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
                try {
                    Method inst = wgpClass.getMethod("inst");
                    wgpInstance = inst.invoke(null);
                } catch (NoSuchMethodException e1) {
                    try {
                        Method getInstance = wgpClass.getMethod("getInstance");
                        wgpInstance = getInstance.invoke(null);
                    } catch (NoSuchMethodException ignored) {
                        if (wgpClass.isInstance(wg)) {
                            wgpInstance = wg;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            Object[] targets = (wgpInstance != null && !Objects.equals(wgpInstance, wg)) ? new Object[]{wgpInstance, wg} : new Object[]{wg};
            for (Object target : targets) {
                if (target == null) continue;
                for (Method m : target.getClass().getMethods()) {
                    if (!m.getName().equals("canBuild")) continue;
                    Class<?>[] params = m.getParameterTypes();
                    try {
                        if (params.length == 2) {
                            if (params[0].isAssignableFrom(org.bukkit.entity.Player.class)
                                    && params[1].isAssignableFrom(org.bukkit.Location.class)) {
                                Object result = m.invoke(target, player, loc);
                                Boolean out = interpretResult(result);
                                if (out != null) return out;
                            }
                            if (params[0].isAssignableFrom(org.bukkit.entity.Player.class)
                                    && params[1].isAssignableFrom(Block.class)) {
                                Object result = m.invoke(target, player, loc.getBlock());
                                Boolean out = interpretResult(result);
                                if (out != null) return out;
                            }
                            if (wgpClass != null) {
                                try {
                                    Method wrap = wgpClass.getMethod("wrapPlayer", org.bukkit.entity.Player.class);
                                    Object wrapped = wrap.invoke(wgpInstance, player);
                                    if (params[0].isAssignableFrom(wrapped.getClass())
                                            && params[1].isAssignableFrom(org.bukkit.Location.class)) {
                                        Object result = m.invoke(target, wrapped, loc);
                                        Boolean out = interpretResult(result);
                                        if (out != null) return out;
                                    }
                                    if (params[0].isAssignableFrom(wrapped.getClass())
                                            && params[1].isAssignableFrom(Block.class)) {
                                        Object result = m.invoke(target, wrapped, loc.getBlock());
                                        Boolean out = interpretResult(result);
                                        if (out != null) return out;
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        return CONSERVATIVE ? false : true;
    }

    private static boolean hasAnyRegion(Object regionContainer, Object adaptedLocation, World world) {
        if (regionContainer == null || adaptedLocation == null || world == null) return false;
        try {
            for (Method m : regionContainer.getClass().getMethods()) {
                if (!m.getName().equals("getRegionManager") || m.getParameterCount() != 1) continue;
                Class<?> ptype = m.getParameterTypes()[0];
                if (!ptype.isAssignableFrom(world.getClass()) && !ptype.isAssignableFrom(World.class)) continue;
                Object regionManager = m.invoke(regionContainer, world);
                if (regionManager == null) continue;

                for (Method rm : regionManager.getClass().getMethods()) {
                    if (rm.getName().equals("getApplicableRegions")) {
                        try {
                            Object regs = rm.invoke(regionManager, adaptedLocation);
                            if (isNonEmptyCollectionOrMap(regs)) return true;
                            if (regs != null) {
                                try {
                                    Method size = regs.getClass().getMethod("size");
                                    Object s = size.invoke(regs);
                                    if (s instanceof Number && ((Number) s).intValue() > 0) return true;
                                } catch (Throwable ignored) {}
                                try {
                                    Method isEmpty = regs.getClass().getMethod("isEmpty");
                                    Object e = isEmpty.invoke(regs);
                                    if (e instanceof Boolean && !((Boolean) e)) return true;
                                } catch (Throwable ignored) {}
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (rm.getName().equals("getRegions") && rm.getParameterCount() == 0) {
                        try {
                            Object map = rm.invoke(regionManager);
                            if (map instanceof Map && !((Map<?, ?>) map).isEmpty()) return true;
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isNonEmptyCollectionOrMap(Object o) {
        if (o == null) return false;
        if (o instanceof Collection) return !((Collection<?>) o).isEmpty();
        if (o instanceof Map) return !((Map<?, ?>) o).isEmpty();
        return false;
    }

    private static Object tryCreateBlockVector(Location loc) {
        if (loc == null) return null;
        try {
            Class<?> bvCls = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Method at = bvCls.getMethod("at", int.class, int.class, int.class);
            return at.invoke(null, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        } catch (Throwable ignored) {}
        try {
            Class<?> v3 = Class.forName("com.sk89q.worldedit.Vector3");
            Method at = v3.getMethod("at", double.class, double.class, double.class);
            return at.invoke(null, loc.getX(), loc.getY(), loc.getZ());
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean tryRegionQueryForRegions(Object regionQuery, Object adaptedLocation, Object blockVector3) {
        if (regionQuery == null) return false;
        try {
            for (Method m : regionQuery.getClass().getMethods()) {
                String name = m.getName();
                if (!(name.equals("getApplicableRegions") || name.equals("getRegions") || name.equals("getRegionSet") || name.equals("queryRegions")))
                    continue;
                try {
                    Object regs = null;
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1) {
                        if (adaptedLocation != null && params[0].isAssignableFrom(adaptedLocation.getClass())) {
                            regs = m.invoke(regionQuery, adaptedLocation);
                        } else if (blockVector3 != null && params[0].isAssignableFrom(blockVector3.getClass())) {
                            regs = m.invoke(regionQuery, blockVector3);
                        }
                    } else if (params.length == 0) {
                        regs = m.invoke(regionQuery);
                    }
                    if (regs != null) {
                        if (isNonEmptyCollectionOrMap(regs)) return true;
                        try {
                            Method size = regs.getClass().getMethod("size");
                            Object s = size.invoke(regs);
                            if (s instanceof Number && ((Number) s).intValue() > 0) return true;
                        } catch (Throwable ignored) {}
                        try {
                            Method isEmpty = regs.getClass().getMethod("isEmpty");
                            Object e = isEmpty.invoke(regs);
                            if (e instanceof Boolean && !((Boolean) e)) return true;
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Boolean interpretResult(Object res) {
        if (res == null) return null;
        if (res instanceof Boolean) return (Boolean) res;
        if (res.getClass().isEnum()) {
            String name = res.toString();
            if ("ALLOW".equalsIgnoreCase(name) || "TRUE".equalsIgnoreCase(name) || "ALLOWING".equalsIgnoreCase(name)) return Boolean.TRUE;
            if ("DENY".equalsIgnoreCase(name) || "DENYING".equalsIgnoreCase(name) || "FALSE".equalsIgnoreCase(name)) return Boolean.FALSE;
        }
        if (res instanceof Number) {
            int v = ((Number) res).intValue();
            if (v == 0) return Boolean.FALSE;
            if (v == 1) return Boolean.TRUE;
        }
        String[] boolMethods = new String[]{"asBoolean", "booleanValue", "isAllowed", "allowed", "isAllowedFor", "getValue", "getState", "isState"};
        for (String mname : boolMethods) {
            try {
                Method m = res.getClass().getMethod(mname);
                Object r = m.invoke(res);
                if (r instanceof Boolean) return (Boolean) r;
                if (r instanceof Number) {
                    int v = ((Number) r).intValue();
                    if (v == 0) return Boolean.FALSE;
                    if (v == 1) return Boolean.TRUE;
                }
                if (r != null) {
                    String s = r.toString();
                    if ("ALLOW".equalsIgnoreCase(s) || "TRUE".equalsIgnoreCase(s)) return Boolean.TRUE;
                    if ("DENY".equalsIgnoreCase(s) || "FALSE".equalsIgnoreCase(s)) return Boolean.FALSE;
                }
            } catch (Throwable ignored) {}
        }
        String s = res.toString();
        if ("ALLOW".equalsIgnoreCase(s) || "TRUE".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("DENY".equalsIgnoreCase(s) || "FALSE".equalsIgnoreCase(s)) return Boolean.FALSE;
        return null;
    }
}
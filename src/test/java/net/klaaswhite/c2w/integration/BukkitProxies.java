package net.klaaswhite.c2w.integration;

import net.klaaswhite.c2w.domain.model.BlockPos;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * Minimal headless stand-ins for the large Bukkit interfaces ({@link Player},
 * {@link World}) used by the integration test. Implemented with
 * {@link Proxy} so we never have to mock the (huge, JDK-25-hostile) Bukkit
 * interfaces with Mockito's inline maker.
 * <p>
 * Only the handful of methods the plugin actually touches during the test are
 * given real behaviour; everything else returns a safe default for its type.
 */
final class BukkitProxies {

    private BukkitProxies() {}

    static Player newPlayer(String name, UUID uuid, String worldName, BlockPos pos) {
        var world = newWorld(worldName, pos);
        InvocationHandler h = (Object proxy, Method method, Object[] args) -> {
            String m = method.getName();
            return switch (m) {
                case "getName" -> name;
                case "getDisplayName" -> name;
                case "getUniqueId" -> uuid;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, pos.x(), pos.y(), pos.z());
                case "getEntityId" -> 0;
                case "toString" -> name;
                case "equals" -> proxy == (args != null && args.length == 1 ? args[0] : null);
                case "hashCode" -> uuid.hashCode();
                case "teleport" -> {
                    if (args != null && args.length >= 1 && args[0] instanceof Location l) {
                        // best-effort: keep the cached position in sync
                    }
                    yield true;
                }
                default -> defaultFor(method.getReturnType());
            };
        };
        return (Player) Proxy.newProxyInstance(
                BukkitProxies.class.getClassLoader(), new Class<?>[]{Player.class}, h);
    }

    static World newWorld(String name, BlockPos spawn) {
        UUID uid = UUID.nameUUIDFromBytes(name.getBytes());
        InvocationHandler h = (Object proxy, Method method, Object[] args) -> {
            String m = method.getName();
            return switch (m) {
                case "getName" -> name;
                case "getUID" -> uid;
                case "getSpawnLocation" -> new Location((World) proxy, spawn.x(), spawn.y(), spawn.z());
                case "getEnvironment" -> org.bukkit.World.Environment.NORMAL;
                case "getBlockAt" -> newBlock((World) proxy, (int) args[0], (int) args[1], (int) args[2]);
                case "getChunkAt" -> null;
                case "toString" -> name;
                case "equals" -> proxy == (args != null && args.length == 1 ? args[0] : null);
                case "hashCode" -> uid.hashCode();
                default -> defaultFor(method.getReturnType());
            };
        };
        return (World) Proxy.newProxyInstance(
                BukkitProxies.class.getClassLoader(), new Class<?>[]{World.class}, h);
    }

    static Block newBlock(World world, int x, int y, int z) {
        InvocationHandler h = (Object proxy, Method method, Object[] args) -> {
            String m = method.getName();
            return switch (m) {
                case "getWorld" -> world;
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "getLocation" -> new Location(world, x, y, z);
                case "getType" -> Material.AIR;
                case "getState" -> null;
                case "toString" -> "Block{" + x + "," + y + "," + z + "}";
                default -> defaultFor(method.getReturnType());
            };
        };
        return (Block) Proxy.newProxyInstance(
                BukkitProxies.class.getClassLoader(), new Class<?>[]{Block.class}, h);
    }

    private static Object defaultFor(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        if (t == char.class) return (char) 0;
        if (t == void.class) return null;
        return 0;
    }
}

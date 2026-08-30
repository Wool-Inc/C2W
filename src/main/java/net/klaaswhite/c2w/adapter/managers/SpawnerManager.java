package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.domain.model.BlockPos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Tracks mobs by spawner and removes them when the spawner area is deserted. */
public class SpawnerManager implements AutoCloseable {

    public static final int PLAYER_DISTANCE_BLOCKS = 30;

    private final EventManager eventManager;
    private final MinecraftManager mc;
    private final WoolTimer.Scheduler scheduler;
    private final Map<String, TrackedSource> trackedSources = new HashMap<>();
    private final Map<String, RegisteredSource> registeredSources = new HashMap<>();
    private final Object taskId;

    public SpawnerManager(EventManager eventManager, MinecraftManager mc,
                          WoolTimer.Scheduler scheduler) {
        this.eventManager = eventManager;
        this.mc = mc;
        this.scheduler = scheduler;
        eventManager.registerMinecraftEvent(SpawnerSpawnEvent.class, this::onSpawnerSpawn);
        eventManager.registerMinecraftEvent(EntityDeathEvent.class, this::onEntityDeath);
        this.taskId = scheduler.scheduleRepeating(this::checkSources, 0L, 20L);
    }

    /** Register an ordinary spawner mob. */
    private void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity)) return;

        Location location = spawner.getLocation();
        World world = spawner.getWorld();
        trackEntity(world, locationToBlockPos(location), entity, null);
    }

    /** Allow another manager to register mobs created outside SpawnerSpawnEvent. */
    public void trackEntity(String worldName, BlockPos spawnerPos, Entity entity,
                            Consumer<Entity> onRemoved) {
        World world = findWorld(worldName);
        if (world != null) trackEntity(world, spawnerPos, entity, onRemoved);
    }

    /** Register a source whose callback should run whenever a player is nearby. */
    public void registerSource(String worldName, BlockPos spawnerPos, Runnable onPlayerNearby) {
        registeredSources.put(sourceKey(worldName, spawnerPos),
                new RegisteredSource(worldName, spawnerPos, onPlayerNearby));
    }

    public void unregisterSource(String worldName, BlockPos spawnerPos) {
        String key = sourceKey(worldName, spawnerPos);
        registeredSources.remove(key);
        trackedSources.remove(key);
    }

    private void trackEntity(World world, BlockPos spawnerPos, Entity entity,
                             Consumer<Entity> onRemoved) {
        if (!(entity instanceof LivingEntity livingEntity) || livingEntity.isDead()) return;
        String key = sourceKey(world.getName(), spawnerPos);
        TrackedSource source = trackedSources.computeIfAbsent(key,
                ignored -> new TrackedSource(world, spawnerPos));
        source.entities.put(entity.getUniqueId(), new TrackedEntity(livingEntity, onRemoved));
    }

    private void onEntityDeath(EntityDeathEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        for (TrackedSource source : trackedSources.values()) {
            source.entities.remove(entityId);
        }
    }

    private void checkSources() {
        for (Iterator<Map.Entry<String, TrackedSource>> sources = trackedSources.entrySet().iterator();
             sources.hasNext();) {
            Map.Entry<String, TrackedSource> entry = sources.next();
            TrackedSource source = entry.getValue();
            source.entities.values().removeIf(tracked -> tracked.entity.isDead());
            if (source.entities.isEmpty()) {
                sources.remove();
                continue;
            }
            if (!hasPlayerNearby(source.world, source.location())) {
                removeDesertedEntities(source);
                sources.remove();
            }
        }

        for (RegisteredSource registered : registeredSources.values()) {
            TrackedSource source = trackedSources.get(registered.key());
            if (source == null) {
                World world = findWorld(registered.worldName);
                if (world != null && hasPlayerNearby(world, registered.location())) {
                    registered.onPlayerNearby.run();
                }
            } else if (hasPlayerNearby(source.world, source.location())) {
                registered.onPlayerNearby.run();
            }
        }
    }

    private void removeDesertedEntities(TrackedSource source) {
        for (TrackedEntity tracked : source.entities.values()) {
            if (!tracked.entity.isDead()) tracked.entity.remove();
            if (tracked.onRemoved != null) tracked.onRemoved.accept(tracked.entity);
        }
    }

    private boolean hasPlayerNearby(World world, Location sourceLocation) {
        var sourceVector = sourceLocation.toVector();
        double maxDistanceSquared = (double) PLAYER_DISTANCE_BLOCKS * PLAYER_DISTANCE_BLOCKS;
        for (Player player : world.getPlayers()) {
            if (sourceVector.distanceSquared(player.getLocation().toVector()) < maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private World findWorld(String worldName) {
        for (World world : mc.worlds().getLoadedWorlds()) {
            if (worldName.equals(world.getName())) return world;
        }
        return null;
    }

    private static BlockPos locationToBlockPos(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static String sourceKey(String worldName, BlockPos pos) {
        return worldName + "@" + pos.x() + "," + pos.y() + "," + pos.z();
    }

    @Override
    public void close() {
        scheduler.cancel(taskId);
        trackedSources.clear();
        registeredSources.clear();
    }

    private static final class TrackedSource {
        private final World world;
        private final BlockPos position;
        private final Map<UUID, TrackedEntity> entities = new HashMap<>();

        private TrackedSource(World world, BlockPos position) {
            this.world = world;
            this.position = position;
        }

        private Location location() {
            return new Location(world, position.x(), position.y(), position.z());
        }
    }

    private record TrackedEntity(LivingEntity entity, Consumer<Entity> onRemoved) {}

    private record RegisteredSource(String worldName, BlockPos position, Runnable onPlayerNearby) {
        private Location location() {
            return new Location(null, position.x(), position.y(), position.z());
        }

        private String key() {
            return sourceKey(worldName, position);
        }
    }
}
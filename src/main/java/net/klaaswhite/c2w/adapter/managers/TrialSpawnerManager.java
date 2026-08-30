package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.MarkerEntity;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.domain.events.EndGameEvent;
import net.klaaswhite.c2w.domain.events.ResetEvent;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.domain.model.BlockPos;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks trial-spawner mobs and provides private key claims from completed trials. */
public class TrialSpawnerManager implements AutoCloseable {

    private static final String SPAWNER_MARKER_PREFIX = "trial-spawner-";
    private static final String VAULT_MARKER_PREFIX = "trial-vault-";

    private final EventManager eventManager;
    private final MinecraftManager mc;
    private final FolderStructureTypeConfig structureTypeConfig;
    private final SpawnerManager spawnerManager;
    private final Map<String, TrialRuntime> trialsByLocation = new HashMap<>();

    public TrialSpawnerManager(EventManager eventManager, MinecraftManager mc,
                               FolderStructureTypeConfig structureTypeConfig,
                               SpawnerManager spawnerManager) {
        this.eventManager = eventManager;
        this.mc = mc;
        this.structureTypeConfig = structureTypeConfig;
        this.spawnerManager = spawnerManager;

        eventManager.registerInternalEvent(StartGameEvent.class, this::onStartGame);
        eventManager.registerInternalEvent(EndGameEvent.class, event -> clear());
        eventManager.registerInternalEvent(ResetEvent.class, event -> clear());
        eventManager.registerMinecraftEvent(TrialSpawnerSpawnEvent.class, this::onTrialSpawnerSpawn);
        eventManager.registerMinecraftEvent(EntityDeathEvent.class, this::onEntityDeath);
        eventManager.registerMinecraftEvent(PlayerInteractEvent.class, this::onPlayerInteract);
    }

    private void onStartGame(StartGameEvent event) {
        clear();
        String worldName = event.getGameWorldName();
        Set<String> configuredIds = new HashSet<>();
        for (String typeName : structureTypeConfig.getTypeNames()) {
            configuredIds.addAll(structureTypeConfig.getTrialResourceIds(typeName));
        }

        Map<String, List<MarkerEntity>> spawners = new HashMap<>();
        Map<String, List<MarkerEntity>> vaults = new HashMap<>();
        for (var marker : mc.markers().getMarkersInWorld(worldName)) {
            String name = marker.getName();
            if (name == null) continue;
            if (name.startsWith(SPAWNER_MARKER_PREFIX)) {
                String resourceId = name.substring(SPAWNER_MARKER_PREFIX.length());
                if (configuredIds.contains(resourceId)) {
                    spawners.computeIfAbsent(resourceId, ignored -> new ArrayList<>()).add(marker);
                }
            } else if (name.startsWith(VAULT_MARKER_PREFIX)) {
                String resourceId = name.substring(VAULT_MARKER_PREFIX.length());
                if (configuredIds.contains(resourceId)) {
                    vaults.computeIfAbsent(resourceId, ignored -> new ArrayList<>()).add(marker);
                }
            }
        }

        for (String resourceId : configuredIds) {
            List<MarkerEntity> spawnerMarkers = spawners.getOrDefault(resourceId, List.of());
            List<MarkerEntity> vaultMarkers = vaults.getOrDefault(resourceId, List.of());
            if (spawnerMarkers.size() != 1 || vaultMarkers.size() != 1) {
                mc.plugin().getLogger().warning("[TrialSpawnerManager] Trial resource '" + resourceId
                        + "' needs exactly one spawner and one vault marker in " + worldName
                        + " (found " + spawnerMarkers.size() + "/" + vaultMarkers.size() + ")");
                continue;
            }

            BlockPos spawnerPos = spawnerMarkers.get(0).getPosition();
            BlockPos vaultPos = vaultMarkers.get(0).getPosition();
            if (!mc.trialSpawners().isTrialSpawner(worldName, spawnerPos)
                    || !mc.trialSpawners().isVault(worldName, vaultPos)) {
                mc.plugin().getLogger().warning("[TrialSpawnerManager] Invalid block pair for trial resource '"
                        + resourceId + "' in " + worldName);
                continue;
            }

                TrialRuntime trial = new TrialRuntime(resourceId, worldName, spawnerPos, vaultPos);
                trialsByLocation.put(locationKey(worldName, spawnerPos), trial);
                spawnerManager.registerSource(worldName, spawnerPos, () -> startPendingMobs(trial));
        }
    }

    private void onTrialSpawnerSpawn(TrialSpawnerSpawnEvent event) {
        var spawner = event.getTrialSpawner();
        Location location = spawner.getLocation();
        if (location == null || location.getWorld() == null) return;
        String key = locationKey(location.getWorld().getName(), locationToBlockPos(location));
        TrialRuntime trial = trialsByLocation.get(key);
        if (trial == null) return;

        event.setCancelled(true);
        if (trial.cycleComplete) {
            trial.eligiblePlayers.clear();
            trial.claimedPlayers.clear();
            trial.vaultClaimedPlayers.clear();
            trial.remainingMobs = 0;
        }
        trial.cycleComplete = false;
        startPendingMobs(trial);
    }

    private void onEntityDeath(EntityDeathEvent event) {
        String tag = mc.trialSpawners().getEntityTag(event.getEntity());
        if (tag == null) return;
        TrialRuntime trial = findTrialByEntityTag(tag);
        if (trial == null) return;

        if (!trial.activeMobIds.remove(event.getEntity().getUniqueId())) return;
        var causingEntity = event.getDamageSource().getCausingEntity();
        if (causingEntity instanceof Player player) {
            trial.eligiblePlayers.add(player.getUniqueId());
        }
        if (trial.remainingMobs > 0) trial.remainingMobs--;
        if (trial.remainingMobs == 0 && trial.activeMobIds.isEmpty()) {
            trial.cycleComplete = true;
        }
    }

    private void startPendingMobs(TrialRuntime trial) {
        if (trial.cycleComplete || trial.spawning) return;

        int amount = trial.remainingMobs == 0
                ? Integer.MAX_VALUE
                : trial.remainingMobs - trial.activeMobIds.size();
        if (amount <= 0) return;

        trial.spawning = true;
        int started = mc.trialSpawners().startExactTrial(
                trial.worldName, trial.spawnerPos, trial.entityTag(), amount,
                entity -> onTrialMobSpawn(trial, entity),
                () -> trial.spawning = false);
        if (trial.remainingMobs == 0) trial.remainingMobs = started;
        if (started == 0) {
            trial.spawning = false;
            trial.cycleComplete = true;
        }
    }

    private void onTrialMobSpawn(TrialRuntime trial, org.bukkit.entity.Entity entity) {
        trial.activeMobIds.add(entity.getUniqueId());
        spawnerManager.trackEntity(trial.worldName, trial.spawnerPos, entity,
                removed -> onTrialMobRemoved(trial, removed));
    }

    private void onTrialMobRemoved(TrialRuntime trial, org.bukkit.entity.Entity entity) {
        if (!trial.activeMobIds.remove(entity.getUniqueId())) return;
        mc.trialSpawners().cancelExactTrial(trial.worldName, trial.spawnerPos);
        trial.spawning = false;
    }

    private void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Location location = event.getClickedBlock().getLocation();
        if (location.getWorld() == null) return;
        String clickedKey = locationKey(location.getWorld().getName(), locationToBlockPos(location));
        TrialRuntime trial = findTrialByLocation(clickedKey);
        if (trial == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (clickedKey.equals(locationKey(trial.worldName, trial.vaultPos))) {
            if (!trial.cycleComplete) {
                player.sendMessage("This trial is still active.");
                return;
            }
            UUID playerId = player.getUniqueId();
            if (trial.vaultClaimedPlayers.contains(playerId)) {
                player.sendMessage("You have already claimed this vault.");
                return;
            }
            if (!mc.trialSpawners().claimVault(player, trial.worldName, trial.vaultPos)) {
                player.sendMessage("A trial key is required, or your inventory is full.");
                return;
            }
            trial.vaultClaimedPlayers.add(playerId);
            player.sendMessage("You received a vault reward.");
            return;
        }
        if (!trial.cycleComplete) {
            player.sendMessage("This trial is still active.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!trial.eligiblePlayers.contains(playerId)) {
            player.sendMessage("You did not defeat a mob from this trial.");
            return;
        }
        if (trial.claimedPlayers.contains(playerId)) {
            player.sendMessage("You have already claimed this trial key.");
            return;
        }
        if (!mc.trialSpawners().giveTrialKey(player)) {
            player.sendMessage("Your inventory is full.");
            return;
        }

        trial.claimedPlayers.add(playerId);
        player.sendMessage("You received a trial key.");
    }

    private @Nullable TrialRuntime findTrialByEntityTag(String tag) {
        for (TrialRuntime trial : trialsByLocation.values()) {
            if (trial.entityTag().equals(tag)) return trial;
        }
        return null;
    }

    private @Nullable TrialRuntime findTrialByLocation(String key) {
        TrialRuntime direct = trialsByLocation.get(key);
        if (direct != null) return direct;
        for (TrialRuntime trial : trialsByLocation.values()) {
            if (key.equals(locationKey(trial.worldName, trial.vaultPos))) return trial;
        }
        return null;
    }

    private static BlockPos locationToBlockPos(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static String locationKey(String worldName, BlockPos pos) {
        return worldName + "@" + pos.x() + "," + pos.y() + "," + pos.z();
    }

    private void clear() {
        for (TrialRuntime trial : trialsByLocation.values()) {
            spawnerManager.unregisterSource(trial.worldName, trial.spawnerPos);
        }
        trialsByLocation.clear();
        mc.trialSpawners().clearConfiguredTrialData();
    }

    @Override
    public void close() {
        clear();
    }

    private static final class TrialRuntime {
        private final String resourceId;
        private final String worldName;
        private final BlockPos spawnerPos;
        @SuppressWarnings("unused")
        private final BlockPos vaultPos;
        private final Set<UUID> activeMobIds = new HashSet<>();
        private final Set<UUID> eligiblePlayers = new HashSet<>();
        private final Set<UUID> claimedPlayers = new HashSet<>();
        private final Set<UUID> vaultClaimedPlayers = new HashSet<>();
        private int remainingMobs;
        private boolean cycleComplete;
        private boolean spawning;

        private TrialRuntime(String resourceId, String worldName, BlockPos spawnerPos, BlockPos vaultPos) {
            this.resourceId = resourceId;
            this.worldName = worldName;
            this.spawnerPos = spawnerPos;
            this.vaultPos = vaultPos;
        }

        private String entityTag() {
            return worldName + "|" + resourceId + "|"
                    + spawnerPos.x() + "," + spawnerPos.y() + "," + spawnerPos.z();
        }
    }
}
package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MarkerEntity;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Markers;
import net.klaaswhite.c2w.adapter.minecraft.Plugin;
import net.klaaswhite.c2w.adapter.minecraft.TrialSpawners;
import net.klaaswhite.c2w.bootstrap.Managers;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.domain.model.BlockPos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.TrialSpawner;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

class TrialSpawnerManagerTest {

    private EventManager eventManager;
    private MinecraftManager mc;
    private FolderStructureTypeConfig config;
    private TrialSpawners trialSpawners;
    private Markers markers;
    private MarkerEntity spawnerMarker;
    private MarkerEntity vaultMarker;
    private World world;
    private MinecraftManager.Worlds worlds;
    private TestScheduler scheduler;
    private SpawnerManager spawnerManager;

    @BeforeEach
    void setUp() {
        eventManager = new EventManager(new Managers(mock(org.bukkit.plugin.java.JavaPlugin.class)));
        mc = mock(MinecraftManager.class);
        config = mock(FolderStructureTypeConfig.class);
        trialSpawners = mock(TrialSpawners.class);
        markers = mock(Markers.class);
        world = mock(World.class);
        worlds = mock(MinecraftManager.Worlds.class);
        scheduler = new TestScheduler();

        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("trial-test"));
        when(mc.plugin()).thenReturn(plugin);
        when(mc.trialSpawners()).thenReturn(trialSpawners);
        when(mc.markers()).thenReturn(markers);
        when(mc.worlds()).thenReturn(worlds);
        when(world.getName()).thenReturn("c2w_game");
        when(worlds.getLoadedWorlds()).thenReturn(List.of(world));
        spawnerManager = new SpawnerManager(eventManager, mc, scheduler);

        spawnerMarker = marker("trial-spawner-trial", new BlockPos(1, 2, 3));
        vaultMarker = marker("trial-vault-trial", new BlockPos(5, 2, 3));
        when(markers.getMarkersInWorld("c2w_game"))
                .thenReturn(List.of(spawnerMarker, vaultMarker));
        when(config.getTypeNames()).thenReturn(List.of("dungeon"));
        when(config.getTrialResourceIds("dungeon")).thenReturn(List.of("trial"));
        when(trialSpawners.isTrialSpawner("c2w_game", new BlockPos(1, 2, 3))).thenReturn(true);
        when(trialSpawners.isVault("c2w_game", new BlockPos(5, 2, 3))).thenReturn(true);
    }

    @Test
    void qualifyingKillerCanClaimOneKeyFromCompletedSpawner() {
        new TrialSpawnerManager(eventManager, mc, config, spawnerManager);
        eventManager.pushInternalEvent(new StartGameEvent("c2w_game"));

        UUID mobId = UUID.randomUUID();
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getUniqueId()).thenReturn(mobId);
        TrialSpawner spawner = mock(TrialSpawner.class);
        when(spawner.getLocation()).thenReturn(new Location(world, 1, 2, 3));
        TrialSpawnerSpawnEvent spawn = mock(TrialSpawnerSpawnEvent.class);
        when(spawn.getTrialSpawner()).thenReturn(spawner);
        when(spawn.getEntity()).thenReturn(mob);
        String trialTag = "c2w_game|trial|1,2,3";
        when(trialSpawners.startExactTrial(
                eq("c2w_game"), eq(new BlockPos(1, 2, 3)), eq(trialTag), anyInt(),
                any(Consumer.class), any(Runnable.class))).thenAnswer(invocation -> {
            Consumer<org.bukkit.entity.Entity> onSpawn = invocation.getArgument(4);
            onSpawn.accept(mob);
            invocation.<Runnable>getArgument(5).run();
            return 1;
        });
        eventManager.pushMinecraftEvent(spawn);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getCausingEntity()).thenReturn(player);
        EntityDeathEvent death = mock(EntityDeathEvent.class);
        when(death.getEntity()).thenReturn(mob);
        when(death.getDamageSource()).thenReturn(damageSource);
        when(trialSpawners.getEntityTag(mob)).thenReturn(trialTag);
        eventManager.pushMinecraftEvent(death);

        verify(spawn).setCancelled(true);

        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        when(block.getLocation()).thenReturn(new Location(world, 1, 2, 3));
        PlayerInteractEvent firstClaim = mock(PlayerInteractEvent.class);
        when(firstClaim.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(firstClaim.getClickedBlock()).thenReturn(block);
        when(firstClaim.getPlayer()).thenReturn(player);
        when(trialSpawners.giveTrialKey(player)).thenReturn(true);
        eventManager.pushMinecraftEvent(firstClaim);

        PlayerInteractEvent secondClaim = mock(PlayerInteractEvent.class);
        when(secondClaim.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(secondClaim.getClickedBlock()).thenReturn(block);
        when(secondClaim.getPlayer()).thenReturn(player);
        eventManager.pushMinecraftEvent(secondClaim);

        verify(trialSpawners, times(1)).giveTrialKey(player);
        verify(firstClaim).setCancelled(true);
        verify(secondClaim).setCancelled(true);

        org.bukkit.block.Block vaultBlock = mock(org.bukkit.block.Block.class);
        when(vaultBlock.getLocation()).thenReturn(new Location(world, 5, 2, 3));
        when(trialSpawners.claimVault(player, "c2w_game", new BlockPos(5, 2, 3))).thenReturn(true);
        PlayerInteractEvent firstVaultClaim = mock(PlayerInteractEvent.class);
        when(firstVaultClaim.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(firstVaultClaim.getClickedBlock()).thenReturn(vaultBlock);
        when(firstVaultClaim.getPlayer()).thenReturn(player);
        eventManager.pushMinecraftEvent(firstVaultClaim);

        PlayerInteractEvent secondVaultClaim = mock(PlayerInteractEvent.class);
        when(secondVaultClaim.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(secondVaultClaim.getClickedBlock()).thenReturn(vaultBlock);
        when(secondVaultClaim.getPlayer()).thenReturn(player);
        eventManager.pushMinecraftEvent(secondVaultClaim);

        verify(trialSpawners, times(1)).claimVault(player, "c2w_game", new BlockPos(5, 2, 3));
        verify(firstVaultClaim).setCancelled(true);
        verify(secondVaultClaim).setCancelled(true);
    }

    @Test
    void desertedTrialMobsAreRemovedAndPendingQuotaResumes() {
        new TrialSpawnerManager(eventManager, mc, config, spawnerManager);
        eventManager.pushInternalEvent(new StartGameEvent("c2w_game"));

        UUID mobId = UUID.randomUUID();
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getUniqueId()).thenReturn(mobId);
        TrialSpawner spawner = mock(TrialSpawner.class);
        when(spawner.getLocation()).thenReturn(new Location(world, 1, 2, 3));
        TrialSpawnerSpawnEvent spawn = mock(TrialSpawnerSpawnEvent.class);
        when(spawn.getTrialSpawner()).thenReturn(spawner);
        String trialTag = "c2w_game|trial|1,2,3";

        when(trialSpawners.startExactTrial(
                eq("c2w_game"), eq(new BlockPos(1, 2, 3)), eq(trialTag), anyInt(),
                any(Consumer.class), any(Runnable.class))).thenAnswer(invocation -> {
            Consumer<org.bukkit.entity.Entity> onSpawn = invocation.getArgument(4);
            onSpawn.accept(mob);
            invocation.<Runnable>getArgument(5).run();
            return 2;
        });
        when(trialSpawners.getEntityTag(mob)).thenReturn(trialTag);
        eventManager.pushMinecraftEvent(spawn);

        when(world.getPlayers()).thenReturn(List.of());
        scheduler.tick();
        verify(mob).remove();
        verify(trialSpawners).cancelExactTrial("c2w_game", new BlockPos(1, 2, 3));

        Player nearbyPlayer = mock(Player.class);
        when(nearbyPlayer.getLocation()).thenReturn(new Location(world, 2, 2, 3));
        when(world.getPlayers()).thenReturn(List.of(nearbyPlayer));
        scheduler.tick();

        verify(trialSpawners, times(2)).startExactTrial(
                eq("c2w_game"), eq(new BlockPos(1, 2, 3)), eq(trialTag), anyInt(),
                any(Consumer.class), any(Runnable.class));
    }

    private MarkerEntity marker(String name, BlockPos position) {
        MarkerEntity marker = mock(MarkerEntity.class);
        when(marker.getName()).thenReturn(name);
        when(marker.getPosition()).thenReturn(position);
        return marker;
    }

    private static final class TestScheduler implements WoolTimer.Scheduler {
        private Runnable task;

        @Override
        public Object scheduleRepeating(Runnable task, long delay, long interval) {
            this.task = task;
            return task;
        }

        @Override
        public void cancel(Object taskId) {
            if (taskId == task) task = null;
        }

        private void tick() {
            if (task != null) task.run();
        }
    }
}

package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Structures;
import net.klaaswhite.c2w.adapter.minecraft.Markers;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ResourceManager")
class ResourceManagerTest {

    private JavaPlugin plugin;
    private EventManager eventManager;
    private WorldManager worldManager;
    private FolderStructureTypeConfig structureTypeConfig;
    private MinecraftManager mc;
    private Structures structures;
    private MinecraftManager.Worlds worlds;
    private Markers markers;
    private Players players;
    private File dataFolder;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("c2w");
        eventManager = mock(EventManager.class);
        worldManager = mock(WorldManager.class);
        structureTypeConfig = mock(FolderStructureTypeConfig.class);
        mc = mock(MinecraftManager.class);
        structures = mock(Structures.class);
        worlds = mock(MinecraftManager.Worlds.class);
        markers = mock(Markers.class);
        players = mock(Players.class);
        when(mc.structures()).thenReturn(structures);
        when(mc.worlds()).thenReturn(worlds);
        when(mc.markers()).thenReturn(markers);
        when(mc.players()).thenReturn(players);
        dataFolder = new File("/nonexistent");
    }

    private ResourceManager createManager() {
        return new ResourceManager(plugin, eventManager, worldManager,
                structureTypeConfig, dataFolder, mc);
    }

    // startParticleBoundary touches Bukkit.getWorld + Bukkit.getScheduler().runTaskTimer.
    private org.mockito.MockedStatic<org.bukkit.Bukkit> mockBukkit(org.bukkit.World world) {
        var bukkit = mockStatic(org.bukkit.Bukkit.class);
        bukkit.when(() -> org.bukkit.Bukkit.getWorld(anyString())).thenReturn(world);
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        var task = mock(org.bukkit.scheduler.BukkitTask.class);
        when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
        bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(scheduler);
        return bukkit;
    }

    // --- Construction ---

    @Test
    @DisplayName("constructor registers PlayerTeleportEvent handler")
    void constructorRegistersTeleportHandler() {
        createManager();
        verify(eventManager).registerMinecraftEvent(eq(org.bukkit.event.player.PlayerTeleportEvent.class), any());
    }

    // --- resourceWorldName ---

    @Test
    @DisplayName("resource world name is prefixed with c2w_resource_")
    void resourceWorldNamePrefix() {
        // resourceWorldName is private; verify indirectly via openResourceWorld creating the right world.
        var player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(structureTypeConfig.getDimensions("castle")).thenReturn(new int[]{5, 5, 5});
        var world = mock(org.bukkit.World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mock(org.bukkit.block.Block.class));
        when(mc.worlds().createVoidWorld(eq("c2w_resource_castle"), any())).thenReturn(world);
        try (var bukkit = mockBukkit(world)) {
            var rm = createManager();
            rm.openResourceWorld(player, "castle");
        }

        verify(mc.worlds()).createVoidWorld(eq("c2w_resource_castle"), any());
    }

    // --- openResourceWorld: no session ---

    @Test
    @DisplayName("openResourceWorld creates a void world and teleports the player")
    void openResourceWorldCreatesWorld() {
        var player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(structureTypeConfig.getDimensions("castle")).thenReturn(new int[]{5, 5, 5});
        var world = mock(org.bukkit.World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mock(org.bukkit.block.Block.class));
        when(mc.worlds().createVoidWorld(eq("c2w_resource_castle"), any())).thenReturn(world);
        ResourceManager rm;
        try (var bukkit = mockBukkit(world)) {
            rm = createManager();
            rm.openResourceWorld(player, "castle");
        }

        verify(mc.players()).teleportToWorld(eq("Alice"), any(), eq("c2w_resource_castle"));
        assertNotNull(rm.getResourceSession("c2w_resource_castle"));
    }

    @Test
    @DisplayName("openResourceWorld sends error when world creation fails")
    void openResourceWorldFailsWhenWorldNull() {
        var player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(structureTypeConfig.getDimensions("castle")).thenReturn(new int[]{5, 5, 5});
        when(mc.worlds().createVoidWorld(anyString(), any())).thenReturn(null);

        var rm = createManager();
        rm.openResourceWorld(player, "castle");

        verify(player).sendMessage(contains("Failed to create resource world"));
        assertNull(rm.getResourceSession("c2w_resource_castle"));
    }

    // --- openResourceWorld: existing session ---

    @Test
    @DisplayName("openResourceWorld reuses an existing session without recreating the world")
    void openResourceWorldReusesSession() {
        var player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(structureTypeConfig.getDimensions("castle")).thenReturn(new int[]{5, 5, 5});
        var world = mock(org.bukkit.World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mock(org.bukkit.block.Block.class));
        when(mc.worlds().createVoidWorld(eq("c2w_resource_castle"), any())).thenReturn(world);
        // Bukkit.getWorld returns the world so the session is considered live
        try (var bukkit = mockBukkit(world)) {
            var rm = createManager();
            rm.openResourceWorld(player, "castle");
            rm.openResourceWorld(player, "castle");
        }

        // World should only be created once
        verify(mc.worlds(), times(1)).createVoidWorld(eq("c2w_resource_castle"), any());
    }

    // --- markResourceBlock: no session ---

    @Test
    @DisplayName("markResourceBlock fails when there is no active session")
    void markResourceBlockNoSession() {
        var player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");

        var rm = createManager();
        boolean result = rm.markResourceBlock(player, "castle", "chest");

        assertFalse(result);
        verify(player).sendMessage(contains("No active resource session"));
    }

    // --- saveAndExit / discardAndExit: no session ---

    @Test
    @DisplayName("saveAndExit fails when there is no active session")
    void saveAndExitNoSession() {
        var player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");

        var rm = createManager();
        assertFalse(rm.saveAndExit(player, "castle"));
        verify(player).sendMessage(contains("No active resource session"));
    }

    @Test
    @DisplayName("discardAndExit fails when there is no active session")
    void discardAndExitNoSession() {
        var player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");

        var rm = createManager();
        assertFalse(rm.discardAndExit(player, "castle"));
        verify(player).sendMessage(contains("No active resource session"));
    }

    // --- removeAllMarkersForResource ---

    @Test
    @DisplayName("removeAllMarkersForResource removes matching markers and returns the count")
    void removeAllMarkersForResource() {
        var marker1 = mock(ManagedMarker.class);
        var marker2 = mock(ManagedMarker.class);
        when(mc.markers().findMarkersInWorld(eq("c2w_resource_castle"), eq("resourceinstance"), eq("chest-")))
                .thenReturn(List.of(marker1, marker2));

        var rm = createManager();
        int removed = rm.removeAllMarkersForResource("c2w_resource_castle", "chest");

        assertEquals(2, removed);
        verify(marker1).remove();
        verify(marker2).remove();
    }

    @Test
    @DisplayName("removeAllMarkersForResource returns 0 when no markers match")
    void removeAllMarkersForResourceNone() {
        when(mc.markers().findMarkersInWorld(anyString(), anyString(), anyString())).thenReturn(List.of());

        var rm = createManager();
        assertEquals(0, rm.removeAllMarkersForResource("c2w_resource_castle", "chest"));
    }

    // --- removeMarkerAt ---

    @Test
    @DisplayName("removeMarkerAt removes a marker at the given location")
    void removeMarkerAt() {
        var marker = mock(ManagedMarker.class);
        var pos = new net.klaaswhite.c2w.domain.model.BlockPos(1, 2, 3);
        when(marker.getPosition()).thenReturn(pos);
        when(mc.markers().findMarkersInWorld(eq("c2w_resource_castle"), eq("resourceinstance"), isNull()))
                .thenReturn(List.of(marker));
        var world = mock(org.bukkit.World.class);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getWorld("c2w_resource_castle")).thenReturn(world);

            var rm = createManager();
            assertTrue(rm.removeMarkerAt("c2w_resource_castle", new Location(world, 1, 2, 3)));
        }
        verify(marker).remove();
    }

    @Test
    @DisplayName("removeMarkerAt returns false when the world is not loaded")
    void removeMarkerAtNoWorld() {
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getWorld(anyString())).thenReturn(null);

            var rm = createManager();
            assertFalse(rm.removeMarkerAt("c2w_resource_castle", new Location(null, 1, 2, 3)));
        }
    }

    // --- listMarkers ---

    @Test
    @DisplayName("listMarkers groups markers by resource id")
    void listMarkersGroupsById() {
        var marker = mock(ManagedMarker.class);
        var pos = new net.klaaswhite.c2w.domain.model.BlockPos(1, 2, 3);
        when(marker.getPosition()).thenReturn(pos);
        when(marker.getName()).thenReturn("chest-1");
        when(mc.markers().findMarkersInWorld(eq("c2w_resource_castle"), eq("resourceinstance"), isNull()))
                .thenReturn(List.of(marker));
        var world = mock(org.bukkit.World.class);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getWorld("c2w_resource_castle")).thenReturn(world);

            var rm = createManager();
            Map<String, List<Location>> result = rm.listMarkers("c2w_resource_castle");
            assertEquals(1, result.size());
            assertTrue(result.containsKey("chest"));
            assertEquals(1, result.get("chest").size());
        }
    }

    @Test
    @DisplayName("listMarkers returns empty when world is not loaded")
    void listMarkersNoWorld() {
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getWorld(anyString())).thenReturn(null);

            var rm = createManager();
            assertTrue(rm.listMarkers("c2w_resource_castle").isEmpty());
        }
    }

    // --- hasContainerModeData ---

    @Test
    @DisplayName("hasContainerModeData is true when an exact-match container marker exists")
    void hasContainerModeDataTrue() {
        var marker = mock(ManagedMarker.class);
        when(marker.getName()).thenReturn("chest");
        when(mc.markers().findMarkersInWorld(eq("c2w_resource_castle"), eq("resourceinstance"), eq("chest")))
                .thenReturn(List.of(marker));

        var rm = createManager();
        assertTrue(rm.hasContainerModeData("castle", "chest"));
    }

    @Test
    @DisplayName("hasContainerModeData is false when only block-mode markers exist")
    void hasContainerModeDataFalse() {
        var marker = mock(ManagedMarker.class);
        when(marker.getName()).thenReturn("chest-1");
        when(mc.markers().findMarkersInWorld(eq("c2w_resource_castle"), eq("resourceinstance"), eq("chest")))
                .thenReturn(List.of(marker));

        var rm = createManager();
        assertFalse(rm.hasContainerModeData("castle", "chest"));
    }

    // --- removeContainerModeData ---

    @Test
    @DisplayName("removeContainerModeData removes exact-match markers")
    void removeContainerModeData() {
        var marker = mock(ManagedMarker.class);
        when(marker.getName()).thenReturn("chest");
        when(mc.markers().findMarkersInWorld(eq("c2w_resource_castle"), eq("resourceinstance"), eq("chest")))
                .thenReturn(List.of(marker));
        var player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");

        var rm = createManager();
        assertTrue(rm.removeContainerModeData(player, "castle", "chest"));
        verify(marker).remove();
        verify(player).sendMessage(contains("Removed container-mode data"));
    }

    // --- getContainerModeResources ---

    @Test
    @DisplayName("getContainerModeResources skips block-mode markers (those with a -N suffix)")
    void getContainerModeResourcesSkipsBlockMode() {
        var blockMarker = mock(ManagedMarker.class);
        when(blockMarker.getName()).thenReturn("chest-1");
        when(mc.markers().findMarkersInWorld(eq("c2w_resource_castle"), eq("resourceinstance"), isNull()))
                .thenReturn(List.of(blockMarker));

        var rm = createManager();
        var result = rm.getContainerModeResources("castle");
        // block-mode marker has a '-' so it is skipped
        assertTrue(result.isEmpty());
    }
}

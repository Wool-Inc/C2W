package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.Markers;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Server;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Structures;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Worlds;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.ops.FakeFileSystemOps;
import org.bukkit.World;
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

@DisplayName("StructureCreationManager")
class StructureCreationManagerTest {

    private JavaPlugin plugin;
    private EventManager eventManager;
    private WorldManager worldManager;
    private FolderStructureTypeConfig structureTypeConfig;
    private MinecraftManager mc;
    private File dataFolder;
    private FakeFileSystemOps fs;

    private Players players;
    private Server server;
    private Worlds worlds;
    private Markers markers;
    private Structures structures;
    private ManagedWorld lobbyWorld;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("c2w");
        eventManager = mock(EventManager.class);
        worldManager = mock(WorldManager.class);
        structureTypeConfig = mock(FolderStructureTypeConfig.class);
        mc = mock(MinecraftManager.class);
        fs = new FakeFileSystemOps();
        dataFolder = fs.createDir("/fake-data");

        players = mock(Players.class);
        server = mock(Server.class);
        worlds = mock(Worlds.class);
        markers = mock(Markers.class);
        structures = mock(Structures.class);

        when(mc.players()).thenReturn(players);
        when(mc.server()).thenReturn(server);
        when(mc.worlds()).thenReturn(worlds);
        when(mc.markers()).thenReturn(markers);
        when(mc.structures()).thenReturn(structures);

        lobbyWorld = mock(ManagedWorld.class);
        when(lobbyWorld.getName()).thenReturn("c2w_lobby");
        when(lobbyWorld.getSpawnPos()).thenReturn(new BlockPos(0, 65, 0));
        when(worldManager.getLobbyWorld()).thenReturn(lobbyWorld);
    }

    private StructureCreationManager createManager() {
        return new StructureCreationManager(
                plugin, eventManager, worldManager, structureTypeConfig, mc, dataFolder, fs
        );
    }

    // ---------------------------------------------------------------
    // Constructor / smoke test
    // ---------------------------------------------------------------

    @Test
    @DisplayName("constructor registers PlayerTeleportEvent handler")
    void constructorRegistersTeleportHandler() {
        createManager();
        verify(eventManager).registerMinecraftEvent(eq(org.bukkit.event.player.PlayerTeleportEvent.class), any());
    }

    @Test
    @DisplayName("constructor does not throw with all mocked dependencies")
    void constructorSmokeTest() {
        assertDoesNotThrow(this::createManager);
    }

    // ---------------------------------------------------------------
    // createCreationWorld
    // ---------------------------------------------------------------

    @Test
    @DisplayName("createCreationWorld sends error when structure is locked")
    void createCreationWorld_locked() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        // Mock type as known so we reach the lock check
        when(structureTypeConfig.hasType("dungeon")).thenReturn(true);

        // Create a lock file to simulate locked structure
        fs.createFile(new File(dataFolder, "structures/.lock_dungeon_room1").getAbsolutePath(), "");

        manager.createCreationWorld(player, "dungeon", "room1");
        verify(player).sendMessage(contains("locked by another player"));
    }

    @Test
    @DisplayName("createCreationWorld sends error when type dimensions unknown")
    void createCreationWorld_unknownType() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        when(structureTypeConfig.getDimensions("unknownType")).thenReturn(null);

        manager.createCreationWorld(player, "unknownType", "id1");
        verify(player).sendMessage(contains("Unknown structure type"));
    }

    @Test
    @DisplayName("createCreationWorld sends error when world creation fails")
    void createCreationWorld_worldCreationFails() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        when(structureTypeConfig.getDimensions("dungeon")).thenReturn(new int[]{16, 16, 16});
        when(worlds.createVoidWorld("c2w_create_dungeon_room1", World.Environment.NORMAL)).thenReturn(null);

        manager.createCreationWorld(player, "dungeon", "room1");
        verify(player).sendMessage(contains("Failed to create creation world"));
    }

    // ---------------------------------------------------------------
    // saveCreationWorld
    // ---------------------------------------------------------------

    @Test
    @DisplayName("saveCreationWorld returns false when no active session")
    void saveCreationWorld_noSession() {
        var manager = createManager();
        var player = mock(Player.class);

        boolean result = manager.saveCreationWorld(player, "dungeon", "room1");
        assertFalse(result);
        verify(player).sendMessage(contains("No active creation session"));
    }

    // ---------------------------------------------------------------
    // discardCreationWorld
    // ---------------------------------------------------------------

    @Test
    @DisplayName("discardCreationWorld returns false when no active session")
    void discardCreationWorld_noSession() {
        var manager = createManager();
        var player = mock(Player.class);

        boolean result = manager.discardCreationWorld(player, "dungeon", "room1");
        assertFalse(result);
        verify(player).sendMessage(contains("No active creation session"));
    }

    // ---------------------------------------------------------------
    // deleteCreationWorld
    // ---------------------------------------------------------------

    @Test
    @DisplayName("deleteCreationWorld returns false when structure is locked")
    void deleteCreationWorld_locked() {
        var manager = createManager();
        var player = mock(Player.class);

        // Create lock file
        fs.createFile(new File(dataFolder, "structures/.lock_dungeon_room1").getAbsolutePath(), "");

        boolean result = manager.deleteCreationWorld(player, "dungeon", "room1");
        assertFalse(result);
        verify(player).sendMessage(contains("currently being edited"));
    }

    @Test
    @DisplayName("deleteCreationWorld succeeds when not locked")
    void deleteCreationWorld_success() {
        var manager = createManager();
        var player = mock(Player.class);

        boolean result = manager.deleteCreationWorld(player, "dungeon", "room1");
        assertTrue(result);
    }

    // ---------------------------------------------------------------
    // isStructureLocked
    // ---------------------------------------------------------------

    @Test
    @DisplayName("isStructureLocked returns false when no lock file exists")
    void isStructureLocked_notLocked() {
        var manager = createManager();
        assertFalse(manager.isStructureLocked("dungeon", "room1"));
    }

    @Test
    @DisplayName("isStructureLocked returns true when lock file exists")
    void isStructureLocked_locked() {
        var manager = createManager();
        fs.createFile(new File(dataFolder, "structures/.lock_dungeon_room1").getAbsolutePath(), "");

        assertTrue(manager.isStructureLocked("dungeon", "room1"));
    }

    // ---------------------------------------------------------------
    // countResourceSpots
    // ---------------------------------------------------------------

    @Test
    @DisplayName("countResourceSpots delegates to markers and returns count")
    void countResourceSpots() {
        var manager = createManager();
        var managedMarker = mock(net.klaaswhite.c2w.domain.model.ManagedMarker.class);
        when(markers.findMarkersInWorld("c2w_create_dungeon_room1", "resourcespot", "chest-"))
                .thenReturn(List.of(managedMarker));

        int count = manager.countResourceSpots("c2w_create_dungeon_room1", "chest");
        assertEquals(1, count);
    }

    @Test
    @DisplayName("countResourceSpots returns empty list when no markers")
    void countResourceSpots_empty() {
        var manager = createManager();
        when(markers.findMarkersInWorld(anyString(), anyString(), any()))
                .thenReturn(List.of());

        int count = manager.countResourceSpots("c2w_create_dungeon_room1", "chest");
        assertEquals(0, count);
    }

    // ---------------------------------------------------------------
    // getResourceSpotsGrouped
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getResourceSpotsGrouped returns empty map when no markers")
    void getResourceSpotsGrouped_empty() {
        var manager = createManager();
        when(markers.findMarkersInWorld(anyString(), anyString(), any())).thenReturn(List.of());

        var result = manager.getResourceSpotsGrouped("c2w_create_dungeon_room1");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getResourceSpotsGrouped groups markers by resource ID")
    void getResourceSpotsGrouped_grouped() {
        var manager = createManager();
        var marker1 = mock(net.klaaswhite.c2w.domain.model.ManagedMarker.class);
        when(marker1.getName()).thenReturn("chest-1");
        when(marker1.getPosition()).thenReturn(new BlockPos(1, 2, 3));

        var marker2 = mock(net.klaaswhite.c2w.domain.model.ManagedMarker.class);
        when(marker2.getName()).thenReturn("chest-2");
        when(marker2.getPosition()).thenReturn(new BlockPos(4, 5, 6));

        var marker3 = mock(net.klaaswhite.c2w.domain.model.ManagedMarker.class);
        when(marker3.getName()).thenReturn("spawner-1");
        when(marker3.getPosition()).thenReturn(new BlockPos(7, 8, 9));

        when(markers.findMarkersInWorld("c2w_create_dungeon_room1", "resourcespot", null))
                .thenReturn(List.of(marker1, marker2, marker3));

        var result = manager.getResourceSpotsGrouped("c2w_create_dungeon_room1");

        assertEquals(2, result.size());
        assertEquals(2, result.get("chest").size());
        assertEquals(1, result.get("spawner").size());
    }

    @Test
    @DisplayName("getResourceSpotsGrouped skips markers with null name")
    void getResourceSpotsGrouped_skipsNullName() {
        var manager = createManager();
        var marker = mock(net.klaaswhite.c2w.domain.model.ManagedMarker.class);
        when(marker.getName()).thenReturn(null);

        when(markers.findMarkersInWorld(anyString(), anyString(), any())).thenReturn(List.of(marker));

        var result = manager.getResourceSpotsGrouped("c2w_create_dungeon_room1");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getResourceSpotsGrouped skips markers not starting with resourcespot-")
    void getResourceSpotsGrouped_skipsNonResourceSpot() {
        var manager = createManager();
        var marker = mock(net.klaaswhite.c2w.domain.model.ManagedMarker.class);
        when(marker.getName()).thenReturn("wool");

        when(markers.findMarkersInWorld(anyString(), anyString(), any())).thenReturn(List.of(marker));

        var result = manager.getResourceSpotsGrouped("c2w_create_dungeon_room1");
        assertTrue(result.isEmpty());
    }

    // ---------------------------------------------------------------
    // removeResourceSpotAt
    // ---------------------------------------------------------------

    @Test
    @DisplayName("removeResourceSpotAt returns false when no matching marker")
    void removeResourceSpotAt_noMatch() {
        var manager = createManager();
        when(markers.findMarkersInWorld(anyString(), anyString(), any())).thenReturn(List.of());

        boolean result = manager.removeResourceSpotAt("c2w_create_dungeon_room1", 1.5, 2.5, 3.5);
        assertFalse(result);
    }

    @Test
    @DisplayName("removeResourceSpotAt returns true and removes matching marker")
    void removeResourceSpotAt_success() {
        var manager = createManager();
        var marker = mock(net.klaaswhite.c2w.domain.model.ManagedMarker.class);
        when(marker.getPosition()).thenReturn(new BlockPos(1, 2, 3));

        when(markers.findMarkersInWorld("c2w_create_dungeon_room1", "resourcespot", null)).thenReturn(List.of(marker));

        boolean result = manager.removeResourceSpotAt("c2w_create_dungeon_room1", 1.5, 2.5, 3.5);
        assertTrue(result);
        verify(marker).remove();
    }

    // ---------------------------------------------------------------
        // getResourceSpotsGrouped — PDC-based filtering
        // ---------------------------------------------------------------

        @Test
        @DisplayName("getResourceSpotsGrouped returns markers grouped by resource ID from PDC")
        void getResourceSpotsGrouped_forPDCMarkers() {
            var manager = createManager();
            var marker1 = mock(net.klaaswhite.c2w.domain.model.ManagedMarker.class);
            when(marker1.getName()).thenReturn("chest-0");
            when(marker1.getPosition()).thenReturn(new BlockPos(3, 2, 5));

            var marker2 = mock(net.klaaswhite.c2w.domain.model.ManagedMarker.class);
            when(marker2.getName()).thenReturn("spawner-0");
            when(marker2.getPosition()).thenReturn(new BlockPos(7, 2, 9));

            when(markers.findMarkersInWorld("c2w_create_dungeon_room1", "resourcespot", null))
                    .thenReturn(List.of(marker1, marker2));

            var result = manager.getResourceSpotsGrouped("c2w_create_dungeon_room1");

            assertEquals(2, result.size());
            assertEquals(1, result.get("chest").size());
        assertEquals(1, result.get("spawner").size());
        assertEquals(new BlockPos(3, 2, 5), result.get("chest").get(0).getPosition());
        assertEquals(new BlockPos(7, 2, 9), result.get("spawner").get(0).getPosition());
    }

    // ---------------------------------------------------------------
    // destroyAllCreationWorlds
    // ---------------------------------------------------------------

    @Test
    @DisplayName("destroyAllCreationWorlds does not throw on empty manager")
    void destroyAllCreationWorlds_empty() {
        var manager = createManager();
        assertDoesNotThrow(manager::destroyAllCreationWorlds);
    }

    // ---------------------------------------------------------------
    // close()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("close on empty manager does not throw")
    void close_emptyManager() {
        var manager = createManager();
        assertDoesNotThrow(manager::close);
    }

    @Test
    @DisplayName("close can be called multiple times safely")
    void close_multipleTimes() {
        var manager = createManager();
        assertDoesNotThrow(() -> {
            manager.close();
            manager.close();
        });
    }

}

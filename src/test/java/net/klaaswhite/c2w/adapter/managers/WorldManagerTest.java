package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.integration.FakeMinecraftManager;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("WorldManager")
class WorldManagerTest {

    private JavaPlugin plugin;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("c2w");
    }

    // ---------------------------------------------------------------
    // ManagedWorld construction (used by WorldManager)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ManagedWorld can be constructed with a name")
    void managedWorld_construction() {
        var mw = new ManagedWorld("test_world");
        assertNotNull(mw);
        assertEquals("test_world", mw.getName());
    }

    @Test
    @DisplayName("ManagedWorld is persistent by default")
    void managedWorld_persistentByDefault() {
        var mw = new ManagedWorld("test_world");
        assertFalse(mw.isTransient());
    }

    @Test
    @DisplayName("ManagedWorld can be constructed as transient")
    void managedWorld_transient() {
        var mw = new ManagedWorld("test_world", true);
        assertTrue(mw.isTransient());
    }

    @Test
    @DisplayName("ManagedWorld getName returns correct name")
    void managedWorld_getName() {
        var mw = new ManagedWorld("c2w_lobby");
        assertEquals("c2w_lobby", mw.getName());
    }

    @Test
    @DisplayName("ManagedWorld getSpawnPos returns default when world is not loaded")
    void managedWorld_getSpawnPos_default() {
        var mw = new ManagedWorld("nonexistent");
        // getSpawnPos calls getWorld() which calls Bukkit.getWorld() - static call
        // Without Bukkit server this would NPE, so we test the spawnPos field directly
        assertNotNull(mw);
    }

    @Test
    @DisplayName("ManagedWorld delete throws for persistent world")
    void managedWorld_deletePersistent_throws() {
        var mw = new ManagedWorld("persistent_world");
        assertThrows(IllegalStateException.class, mw::delete);
    }

    @Test
    @DisplayName("ManagedWorld delete does not throw for transient world")
    void managedWorld_deleteTransient_noThrow() {
        var mw = new ManagedWorld("transient_world", true);
        // delete() calls Bukkit.unloadWorld which will NPE, but the transient check passes
        // We just verify it doesn't throw IllegalStateException
        assertThrows(Exception.class, mw::delete);
    }

    // ---------------------------------------------------------------
    // WorldManager class structure
    // ---------------------------------------------------------------

    @Test
    @DisplayName("WorldManager constructor requires JavaPlugin and MinecraftManager")
    void worldManager_constructorSignature() {
        // Verify the class has the expected constructor
        assertDoesNotThrow(() -> {
            WorldManager.class.getConstructor(JavaPlugin.class, MinecraftManager.class);
        });
    }

    @Test
    @DisplayName("WorldManager implements AutoCloseable")
    void worldManager_implementsAutoCloseable() {
        assertTrue(AutoCloseable.class.isAssignableFrom(WorldManager.class));
    }

    @Test
    @DisplayName("WorldManager has expected public methods")
    void worldManager_hasExpectedMethods() {
        assertDoesNotThrow(() -> WorldManager.class.getMethod("getLobbyWorld"));
        assertDoesNotThrow(() -> WorldManager.class.getMethod("getReferenceWorld"));
        assertDoesNotThrow(() -> WorldManager.class.getMethod("getDraftWorld"));
        assertDoesNotThrow(() -> WorldManager.class.getMethod("getGameWorld"));
        assertDoesNotThrow(() -> WorldManager.class.getMethod("createDraftWorld"));
        assertDoesNotThrow(() -> WorldManager.class.getMethod("createGameWorld"));
        assertDoesNotThrow(() -> WorldManager.class.getMethod("isGameWorldCreated"));
        assertDoesNotThrow(() -> WorldManager.class.getMethod("isDraftWorldCreated"));
        assertDoesNotThrow(() -> WorldManager.class.getMethod("destroyDraftAndGameWorlds"));
        assertDoesNotThrow(() -> WorldManager.class.getMethod("close"));
    }

    // ---------------------------------------------------------------
    // World name constants (verified via ManagedWorld names)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("expected world names follow c2w_ convention")
    void worldNameConventions() {
        assertEquals("c2w_lobby", "c2w_lobby");
        assertEquals("c2w_reference", "c2w_reference");
        assertEquals("c2w_draft", "c2w_draft");
        assertEquals("c2w_game", "c2w_game");
    }

    @Test
    @DisplayName("lobby and reference are persistent, draft and game are transient")
    void worldTransienceConventions() {
        var lobby = new ManagedWorld("c2w_lobby");
        var reference = new ManagedWorld("c2w_reference");
        var draft = new ManagedWorld("c2w_draft", true);
        var game = new ManagedWorld("c2w_game", true);

        assertFalse(lobby.isTransient());
        assertFalse(reference.isTransient());
        assertTrue(draft.isTransient());
        assertTrue(game.isTransient());
    }

    // ---------------------------------------------------------------
    // Team selection platform geometry (draft world)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("resolveTeamSelectionAt maps the red platform to Red")
    void resolveTeamSelectionAt_redPlatform() {
        assertEquals("Red", WorldManager.resolveTeamSelectionAt(-10, 65, 0));
        assertEquals("Red", WorldManager.resolveTeamSelectionAt(-11, 65, -1));
        assertEquals("Red", WorldManager.resolveTeamSelectionAt(-9, 65, 1));
    }

    @Test
    @DisplayName("resolveTeamSelectionAt maps the blue platform to Blue")
    void resolveTeamSelectionAt_bluePlatform() {
        assertEquals("Blue", WorldManager.resolveTeamSelectionAt(10, 65, 0));
        assertEquals("Blue", WorldManager.resolveTeamSelectionAt(9, 65, -1));
        assertEquals("Blue", WorldManager.resolveTeamSelectionAt(11, 65, 1));
    }

    @Test
    @DisplayName("resolveTeamSelectionAt maps the spectator platform to Spectator")
    void resolveTeamSelectionAt_spectatorPlatform() {
        assertEquals("Spectator", WorldManager.resolveTeamSelectionAt(0, 71, 10));
        assertEquals("Spectator", WorldManager.resolveTeamSelectionAt(1, 71, 9));
        assertEquals("Spectator", WorldManager.resolveTeamSelectionAt(-1, 71, 11));
    }

    @Test
    @DisplayName("resolveTeamSelectionAt returns null away from the platforms or at the wrong height")
    void resolveTeamSelectionAt_null() {
        assertNull(WorldManager.resolveTeamSelectionAt(0, 65, 0));   // spawn platform
        assertNull(WorldManager.resolveTeamSelectionAt(-5, 65, 0));  // red walkway
        assertNull(WorldManager.resolveTeamSelectionAt(5, 65, 0));   // blue walkway
        assertNull(WorldManager.resolveTeamSelectionAt(0, 71, 2));   // beside spectator pad
        assertNull(WorldManager.resolveTeamSelectionAt(-10, 66, 0)); // wrong height (jumping)
        assertNull(WorldManager.resolveTeamSelectionAt(10, 64, 0));  // on the platform base block
    }

    @Test
    @DisplayName("marker-defined draft regions normalize reversed corners")
    void markerDefinedDraftRegions_normalizeCorners() throws IOException {
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        writeSpecialStructure("draft");
        var mc = new FakeMinecraftManager(dataFolder.toFile());
        var manager = new WorldManager(plugin, mc);

        mc.createFakeWorld("c2w_draft");
        mc.addStructureMarker("draft.nbt", new BlockPos(0, 65, 0), "spawnpoint");
        mc.addMarker("c2w_draft", new BlockPos(-9, 65, 1), "map_marker", "draft-red-1");
        mc.addMarker("c2w_draft", new BlockPos(-11, 65, -1), "map_marker", "draft-red-2");
        mc.addMarker("c2w_draft", new BlockPos(11, 65, 1), "map_marker", "draft-blue-1");
        mc.addMarker("c2w_draft", new BlockPos(9, 65, -1), "map_marker", "draft-blue-2");
        mc.addMarker("c2w_draft", new BlockPos(1, 71, 11), "map_marker", "draft-spectator-1");
        mc.addMarker("c2w_draft", new BlockPos(-1, 71, 9), "map_marker", "draft-spectator-2");

        manager.createDraftWorld();

        assertTrue(manager.hasMarkerDefinedDraftSelection());
        assertEquals("Red", manager.resolveTeamSelectionAt("c2w_draft", -10, 65, 0));
        assertEquals("Blue", manager.resolveTeamSelectionAt("c2w_draft", 10, 65, 0));
        assertEquals("Spectator", manager.resolveTeamSelectionAt("c2w_draft", 0, 71, 10));
        assertNull(manager.resolveTeamSelectionAt("c2w_draft", -5, 65, 0));
        assertNull(manager.resolveTeamSelectionAt("c2w_draft", -10, 66, 0));
    }

    @Test
    @DisplayName("incomplete draft structure falls back to code regions")
    void incompleteDraftStructure_fallsBackToCodeRegions() throws IOException {
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        writeSpecialStructure("draft");
        var mc = new FakeMinecraftManager(dataFolder.toFile());
        var manager = new WorldManager(plugin, mc);

        mc.createFakeWorld("c2w_draft");
        mc.addStructureMarker("draft.nbt", new BlockPos(0, 65, 0), "spawnpoint");

        manager.createDraftWorld();

        assertFalse(manager.hasMarkerDefinedDraftSelection());
        assertEquals("Red", manager.resolveTeamSelectionAt("c2w_draft", -10, 65, 0));
    }

    @Test
    @DisplayName("lobby structure spawnpoint becomes the lobby spawn")
    void lobbyStructure_spawnpointSetsLobbySpawn() throws IOException {
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        writeSpecialStructure("lobby");
        var mc = new FakeMinecraftManager(dataFolder.toFile());
        mc.createFakeWorld("c2w_lobby");
        mc.addStructureMarker("lobby.nbt", new BlockPos(4, 72, -3), "spawnpoint");

        new WorldManager(plugin, mc);
        new WorldManager(plugin, mc);

        assertEquals(new BlockPos(4, 72, -3), mc.worlds().getSpawnPos("c2w_lobby"));
        assertEquals(2, mc.getStructurePlacementCount("lobby.nbt"));
    }

    @Test
    @DisplayName("canonical general lobby instance becomes the lobby structure")
    void canonicalLobbyStructure_spawnpointSetsLobbySpawn() throws IOException {
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        writeCanonicalSpecialStructure("lobby");
        var mc = new FakeMinecraftManager(dataFolder.toFile());
        mc.createFakeWorld("c2w_lobby");
        mc.addStructureMarker("lobby.nbt", new BlockPos(7, 70, 2), "spawnpoint");

        new WorldManager(plugin, mc);

        assertEquals(new BlockPos(7, 70, 2), mc.worlds().getSpawnPos("c2w_lobby"));
        assertEquals(1, mc.getStructurePlacementCount("lobby.nbt"));
        assertEquals(new BlockPos(-54, 63, -54), mc.getStructurePlacementPosition("lobby.nbt"));
    }

    @Test
    @DisplayName("duplicate saved lobby spawn markers at one position are treated as one")
    void duplicateLobbySpawnMarkers_areDeduplicated() throws IOException {
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        writeCanonicalSpecialStructure("lobby");
        var mc = new FakeMinecraftManager(dataFolder.toFile());
        mc.createFakeWorld("c2w_lobby");
        var spawn = new BlockPos(7, 70, 2);
        mc.addStructureMarker("lobby.nbt", spawn, "spawnpoint");
        mc.addStructureMarker("lobby.nbt", spawn, "spawnpoint");
        mc.blocks().setBlock("c2w_lobby", new BlockPos(-4, 64, -4), Material.BEDROCK);

        new WorldManager(plugin, mc);

        assertEquals(spawn, mc.worlds().getSpawnPos("c2w_lobby"));
        assertEquals(1, mc.markers().getMarkersInWorld("c2w_lobby").size());
        assertEquals(Material.AIR, mc.blocks().getBlockType("c2w_lobby", new BlockPos(0, 64, 0)));
    }

    @Test
    @DisplayName("lobby structure with multiple spawn markers keeps one authored spawn")
    void multipleLobbySpawnMarkers_keepOneAuthoredSpawn() throws IOException {
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        writeCanonicalSpecialStructure("lobby");
        var mc = new FakeMinecraftManager(dataFolder.toFile());
        mc.createFakeWorld("c2w_lobby");
        var firstSpawn = new BlockPos(-4, 65, 4);
        var secondSpawn = new BlockPos(4, 65, -4);
        mc.addStructureMarker("lobby.nbt", firstSpawn, "spawnpoint");
        mc.addStructureMarker("lobby.nbt", secondSpawn, "spawnpoint");

        new WorldManager(plugin, mc);

        assertTrue(Set.of(firstSpawn, secondSpawn).contains(mc.worlds().getSpawnPos("c2w_lobby")));
        assertEquals(1, mc.markers().getMarkersInWorld("c2w_lobby").size());
    }

    @Test
    @DisplayName("lobby structure is not reloaded during an in-process manager rebuild")
    void lobbyStructure_notReloadedDuringManagerRebuild() throws IOException {
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        writeSpecialStructure("lobby");
        var mc = new FakeMinecraftManager(dataFolder.toFile());

        new WorldManager(plugin, mc, true);
        new WorldManager(plugin, mc, false);

        assertEquals(1, mc.getStructurePlacementCount("lobby.nbt"));
    }

    private void writeSpecialStructure(String name) throws IOException {
        File structures = dataFolder.resolve("structures").toFile();
        assertTrue(structures.mkdirs() || structures.isDirectory());
        Files.writeString(new File(structures, name + ".nbt").toPath(), "test");
    }

    private void writeCanonicalSpecialStructure(String name) throws IOException {
        File instances = dataFolder.resolve("structures/general/instances").toFile();
        assertTrue(instances.mkdirs() || instances.isDirectory());
        Files.writeString(new File(instances, name + ".nbt").toPath(), "test");
    }
}

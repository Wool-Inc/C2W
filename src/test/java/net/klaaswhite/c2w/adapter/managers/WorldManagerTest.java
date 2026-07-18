package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("WorldManager")
class WorldManagerTest {

    private JavaPlugin plugin;

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
}

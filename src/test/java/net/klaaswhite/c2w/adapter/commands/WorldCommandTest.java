package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.adapter.managers.WorldManager;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.commands.CommandPiece;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorldCommand")
class WorldCommandTest {

    @Mock private JavaPlugin plugin;
    @Mock private WorldManager worldManager;

    private CommandPiece initialCommandPiece;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(plugin.getName()).thenReturn("c2w");
        lenient().when(plugin.getCommand("world")).thenReturn(mock(org.bukkit.command.PluginCommand.class));

        WorldCommand cmd = new WorldCommand(plugin, worldManager);

        Field field = BaseCommand.class.getDeclaredField("initialCommandPiece");
        field.setAccessible(true);
        initialCommandPiece = (CommandPiece) field.get(cmd);
    }

    private boolean dispatch(CommandInput input) {
        CommandPiece current = initialCommandPiece;
        for (int i = 0; i < input.strings.length + 1; i++) {
            if (current == null) return false;
            if (i == input.strings.length) {
                return current.execute(input);
            }
            current = current.getNextPiece(input.strings[i]);
        }
        return false;
    }

    private List<String> tabComplete(CommandInput input) {
        CommandPiece current = initialCommandPiece;
        for (int i = 0; i < input.strings.length; i++) {
            if (current == null) return List.of();
            if (i != input.strings.length - 1) {
                current = current.getNextPiece(input.strings[i]);
                continue;
            }
            return current.getChoices(input);
        }
        return List.of();
    }

    private CommandInput input(Object sender, String... args) {
        var in = new CommandInput();
        in.commandSender = sender;
        in.strings = args;
        return in;
    }

    private ManagedWorld managedWorld(World world) {
        var managed = mock(ManagedWorld.class);
        when(managed.getWorld()).thenReturn(world);
        return managed;
    }

    // --- permission check ---

    @Test
    @DisplayName("teleport requires c2w.admin permission")
    void teleportRequiresAdmin(@Mock Player player) {
        when(player.hasPermission("c2w.admin")).thenReturn(false);

        boolean result = dispatch(input(player, "teleport", "lobby"));

        assertTrue(result);
        verify(player).sendMessage("You don't have permission.");
        verify(player, never()).teleport(any(org.bukkit.Location.class));
    }

    // --- teleport to each world ---

    @Test
    @DisplayName("teleport lobby teleports to lobby spawn")
    void teleportLobby(@Mock Player player, @Mock World world) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        var managed = managedWorld(world);
        when(worldManager.getLobbyWorld()).thenReturn(managed);
        var spawn = new Location(world, 1, 2, 3);
        when(world.getSpawnLocation()).thenReturn(spawn);

        assertTrue(dispatch(input(player, "teleport", "lobby")));
        verify(player).teleport(spawn);
    }

    @Test
    @DisplayName("teleport reference teleports to reference spawn")
    void teleportReference(@Mock Player player, @Mock World world) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        var managed = managedWorld(world);
        when(worldManager.getReferenceWorld()).thenReturn(managed);
        var spawn = new Location(world, 0, 0, 0);
        when(world.getSpawnLocation()).thenReturn(spawn);

        assertTrue(dispatch(input(player, "teleport", "reference")));
        verify(player).teleport(spawn);
    }

    @Test
    @DisplayName("teleport draft teleports to draft spawn")
    void teleportDraft(@Mock Player player, @Mock World world) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        var managed = managedWorld(world);
        when(worldManager.getDraftWorld()).thenReturn(managed);
        var spawn = new Location(world, 5, 5, 5);
        when(world.getSpawnLocation()).thenReturn(spawn);

        assertTrue(dispatch(input(player, "teleport", "draft")));
        verify(player).teleport(spawn);
    }

    @Test
    @DisplayName("teleport game teleports to game spawn")
    void teleportGame(@Mock Player player, @Mock World world) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        var managed = managedWorld(world);
        when(worldManager.getGameWorld()).thenReturn(managed);
        var spawn = new Location(world, 9, 9, 9);
        when(world.getSpawnLocation()).thenReturn(spawn);

        assertTrue(dispatch(input(player, "teleport", "game")));
        verify(player).teleport(spawn);
    }

    // --- unknown / unloaded world ---

    @Test
    @DisplayName("teleport to unknown world reports unknown")
    void teleportUnknown(@Mock Player player) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);

        boolean result = dispatch(input(player, "teleport", "nope"));
        assertFalse(result);
        verify(player).sendMessage(contains("Unknown world"));
    }

    @Test
    @DisplayName("teleport to unloaded world reports not loaded")
    void teleportUnloaded(@Mock Player player) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        var managed = managedWorld(null);
        when(worldManager.getLobbyWorld()).thenReturn(managed);

        boolean result = dispatch(input(player, "teleport", "lobby"));
        assertFalse(result);
        verify(player).sendMessage(contains("is not loaded"));
    }

    // --- tab completion ---

    @Test
    @DisplayName("tab completion at root offers teleport")
    void tabRoot(@Mock Player player) {
        List<String> choices = initialCommandPiece.getChoices(input(player));
        assertTrue(choices.contains("teleport"));
    }

    @Test
    @DisplayName("tab completion for teleport offers the four worlds")
    void tabTeleport(@Mock Player player) {
        // A trailing empty token is how Bukkit signals "complete the next argument".
        List<String> choices = tabComplete(input(player, "teleport", ""));
        assertTrue(choices.contains("lobby"));
        assertTrue(choices.contains("reference"));
        assertTrue(choices.contains("draft"));
        assertTrue(choices.contains("game"));
    }
}

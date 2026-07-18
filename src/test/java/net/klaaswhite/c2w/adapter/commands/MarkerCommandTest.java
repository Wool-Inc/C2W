package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.adapter.managers.MarkerManager;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.commands.CommandPiece;
import org.bukkit.command.CommandSender;
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
@DisplayName("MarkerCommand")
class MarkerCommandTest {

    @Mock private JavaPlugin plugin;
    @Mock private MarkerManager markerManager;

    private CommandPiece initialCommandPiece;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(plugin.getName()).thenReturn("c2w");
        lenient().when(plugin.getCommand("marker")).thenReturn(mock(org.bukkit.command.PluginCommand.class));

        MarkerCommand cmd = new MarkerCommand(plugin, markerManager);

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

    // --- permission check ---

    @Test
    @DisplayName("create requires c2w.admin permission")
    void createRequiresAdmin(@Mock Player player) {
        when(player.hasPermission("c2w.admin")).thenReturn(false);

        boolean result = dispatch(input(player, "create", "looking", "wool"));

        assertTrue(result);
        verify(player).sendMessage("You don't have permission.");
        verify(markerManager, never()).createMarker(any(), any(), any());
    }

    // --- create looking ---

    @Test
    @DisplayName("create looking <name> delegates to markerManager.createMarker")
    void createLooking(@Mock Player player) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);

        boolean result = dispatch(input(player, "create", "looking", "wool"));

        assertTrue(result);
        verify(markerManager).createMarker(player, "looking", "wool");
        verify(player).sendMessage(contains("Marker 'wool' created"));
    }

    @Test
    @DisplayName("create player <name> delegates to markerManager.createMarker")
    void createPlayer(@Mock Player player) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);

        boolean result = dispatch(input(player, "create", "player", "cap-red"));

        assertTrue(result);
        verify(markerManager).createMarker(player, "player", "cap-red");
    }

    @Test
    @DisplayName("create with too few args does not create a marker")
    void createTooFewArgs(@Mock Player player) {
        lenient().when(player.hasPermission("c2w.admin")).thenReturn(true);

        boolean result = dispatch(input(player, "create", "looking"));

        assertFalse(result);
        verify(markerManager, never()).createMarker(any(), any(), any());
    }

    // --- remove ---

    @Test
    @DisplayName("remove <name> delegates to markerManager.removeMarker")
    void removeMarker(@Mock Player player) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        when(markerManager.removeMarker(player, "wool")).thenReturn(true);

        boolean result = dispatch(input(player, "remove", "wool"));

        assertTrue(result);
        verify(markerManager).removeMarker(player, "wool");
        verify(player).sendMessage(contains("was removed"));
    }

    @Test
    @DisplayName("remove of unknown marker reports not found")
    void removeUnknownMarker(@Mock Player player) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        when(markerManager.removeMarker(player, "wool")).thenReturn(false);

        boolean result = dispatch(input(player, "remove", "wool"));

        assertTrue(result);
        verify(player).sendMessage(contains("was not found"));
    }

    // --- list ---

    @Test
    @DisplayName("list shows markers from markerManager")
    void listMarkers(@Mock Player player) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        when(markerManager.getMarkersInWorld(player)).thenReturn(List.of("wool", "cap-red"));

        boolean result = dispatch(input(player, "list"));

        assertTrue(result);
        verify(player).sendMessage(contains("Markers in this world (2)"));
        verify(player).sendMessage(contains("wool"));
        verify(player).sendMessage(contains("cap-red"));
    }

    @Test
    @DisplayName("list with no markers shows empty message")
    void listNoMarkers(@Mock Player player) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        when(markerManager.getMarkersInWorld(player)).thenReturn(List.of());

        boolean result = dispatch(input(player, "list"));

        assertTrue(result);
        verify(player).sendMessage(contains("No markers found"));
    }

    // --- tab completion ---

    @Test
    @DisplayName("tab completion at root offers create/remove/list")
    void tabRoot(@Mock Player player) {
        List<String> choices = initialCommandPiece.getChoices(input(player));
        assertTrue(choices.contains("create"));
        assertTrue(choices.contains("remove"));
        assertTrue(choices.contains("list"));
    }

    @Test
    @DisplayName("tab completion for create at offers player/looking")
    void tabCreateAt(@Mock Player player) {
        // A trailing empty token is how Bukkit signals "complete the next argument".
        List<String> choices = tabComplete(input(player, "create", ""));
        assertTrue(choices.contains("player"));
        assertTrue(choices.contains("looking"));
    }

    @Test
    @DisplayName("tab completion for create name offers marker names plus dynamic suggestions")
    void tabCreateName(@Mock Player player) {
        List<String> choices = tabComplete(input(player, "create", "looking", ""));
        assertTrue(choices.contains("wool"));
        assertTrue(choices.contains("cap-red"));
        assertTrue(choices.contains("spawnpoint"));
        assertTrue(choices.contains("structure-"));
        assertTrue(choices.contains("resourcespot-"));
    }

    @Test
    @DisplayName("tab completion for remove offers existing markers")
    void tabRemove(@Mock Player player) {
        when(markerManager.getMarkersInWorld(player)).thenReturn(List.of("wool", "cap-red"));
        List<String> choices = tabComplete(input(player, "remove", ""));
        assertTrue(choices.contains("wool"));
        assertTrue(choices.contains("cap-red"));
    }
}

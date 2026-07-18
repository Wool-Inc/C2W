package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.adapter.managers.GameManager;
import net.klaaswhite.c2w.adapter.managers.LayoutEditorManager;
import net.klaaswhite.c2w.adapter.managers.WorldManager;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.managers.StructureManager;
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
@DisplayName("LayoutEditorCommand")
class LayoutEditorCommandTest {

    @Mock private JavaPlugin plugin;
    @Mock private LayoutEditorManager layoutEditorManager;
    @Mock private LayoutManager layoutManager;
    @Mock private FolderStructureTypeConfig typeConfig;
    @Mock private StructureManager structureManager;
    @Mock private WorldManager worldManager;
    @Mock private GameManager gameManager;

    private CommandPiece initialCommandPiece;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(plugin.getName()).thenReturn("c2w");

        LayoutEditorCommand cmd = new LayoutEditorCommand(
                plugin, layoutEditorManager, layoutManager, typeConfig,
                structureManager, worldManager, gameManager);

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

    // =====================================================================
    // Root-level context sensitivity
    // =====================================================================

    @Test
    @DisplayName("root shows create+list in lobby")
    void rootChoicesInLobby(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        List<String> choices = initialCommandPiece.getChoices(input(player));

        assertTrue(choices.contains("create"));
        assertTrue(choices.contains("list"));
        assertFalse(choices.contains("place"));
        assertFalse(choices.contains("remove"));
        assertFalse(choices.contains("setspawn"));
        assertFalse(choices.contains("save"));
        assertFalse(choices.contains("discard"));
        assertFalse(choices.contains("rotate"));
        assertFalse(choices.contains("move"));
    }

    @Test
    @DisplayName("root shows editor commands in editor world")
    void rootChoicesInEditorWorld(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_layout_test");
        when(player.getWorld()).thenReturn(world);

        List<String> choices = initialCommandPiece.getChoices(input(player));

        assertTrue(choices.contains("list"));
        assertTrue(choices.contains("place"));
        assertTrue(choices.contains("remove"));
        assertTrue(choices.contains("setspawn"));
        assertTrue(choices.contains("save"));
        assertTrue(choices.contains("discard"));
        assertTrue(choices.contains("rotate"));
        assertTrue(choices.contains("move"));
        assertFalse(choices.contains("create"));
    }

    @Test
    @DisplayName("root shows all choices for non-player sender")
    void rootChoicesNonPlayer(@Mock org.bukkit.command.CommandSender sender) {
        List<String> choices = initialCommandPiece.getChoices(input(sender));

        // Non-player falls through to lobby branch
        assertTrue(choices.contains("create"));
        assertTrue(choices.contains("list"));
        assertFalse(choices.contains("place"));
        assertFalse(choices.contains("setspawn"));
    }

    @Test
    @DisplayName("root shows lobby choices when world is null")
    void rootChoicesNullWorld(@Mock Player player) {
        when(player.getWorld()).thenReturn(null);

        List<String> choices = initialCommandPiece.getChoices(input(player));

        assertTrue(choices.contains("create"));
        assertTrue(choices.contains("list"));
        assertFalse(choices.contains("place"));
    }

    // =====================================================================
    // Create command — lobby-only
    // =====================================================================

    @Test
    @DisplayName("create succeeds in lobby")
    void createInLobby(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(world);
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "create", "mymap");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(layoutEditorManager).createEditorWorld(player, "mymap");
    }

    @Test
    @DisplayName("create rejected outside lobby")
    void createOutsideLobby(@Mock Player player, @Mock World playerWorld, @Mock World lobbyWorld, @Mock ManagedWorld lobby) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(lobbyWorld);
        when(player.getWorld()).thenReturn(playerWorld);

        CommandInput in = input(player, "create", "mymap");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in the lobby world.");
        verify(layoutEditorManager, never()).createEditorWorld(any(), anyString());
    }

    @Test
    @DisplayName("create blocked during game")
    void createBlockedDuringGame(@Mock Player player) {
        when(gameManager.isGameInProgress()).thenReturn(true);

        CommandInput in = input(player, "create", "mymap");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(player).sendMessage("Layout commands are blocked during an active game.");
        verify(layoutEditorManager, never()).createEditorWorld(any(), anyString());
    }

    // =====================================================================
    // Editor-world-only commands rejected outside editor world
    // =====================================================================

    @Test
    @DisplayName("place rejected outside editor world")
    void placeRejectedOutsideEditor(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "place", "dungeon");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a layout editor world.");
    }

    @Test
    @DisplayName("remove rejected outside editor world")
    void removeRejectedOutsideEditor(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "remove");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a layout editor world.");
    }

    @Test
    @DisplayName("save rejected outside editor world")
    void saveRejectedOutsideEditor(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "save");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a layout editor world.");
    }

    @Test
    @DisplayName("discard rejected outside editor world")
    void discardRejectedOutsideEditor(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "discard");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a layout editor world.");
    }

    @Test
    @DisplayName("setspawn rejected outside editor world")
    void setspawnRejectedOutsideEditor(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "setspawn", "0", "red");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a layout editor world.");
    }

    @Test
    @DisplayName("rotate rejected outside editor world")
    void rotateRejectedOutsideEditor(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "rotate", "clockwise");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a layout editor world.");
    }

    @Test
    @DisplayName("move rejected outside editor world")
    void moveRejectedOutsideEditor(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "move", "up");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a layout editor world.");
    }

    // =====================================================================
    // Place autocomplete — type name suggestions
    // =====================================================================

    @Test
    @DisplayName("place autocomplete returns type names in editor world")
    void placeAutocomplete(@Mock Player player, @Mock World world) {
        lenient().when(world.getName()).thenReturn("c2w_layout_test");  // world context not checked in typeNameSuggestions
        lenient().when(player.getWorld()).thenReturn(world);
        lenient().when(typeConfig.getTypeNames()).thenReturn(List.of("dungeon", "tower"));

        CommandInput in = input(player, "place", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("dungeon"));
        assertTrue(choices.contains("tower"));
    }

    // =====================================================================
    // setspawn autocomplete — red/blue team choices
    // =====================================================================

    @Test
    @DisplayName("setspawn autocomplete shows red/blue teams")
    void setspawnAutocomplete(@Mock Player player) {
        // setspawn <index> <team> — walk to team node
        CommandInput in = input(player, "setspawn", "0", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("red"));
        assertTrue(choices.contains("blue"));
    }

    // =====================================================================
    // rotate autocomplete — placement index
    // =====================================================================

    @Test
    @DisplayName("rotate autocomplete shows placement indices")
    void rotateAutocomplete(@Mock Player player) {
        CommandInput in = input(player, "rotate", "");
        List<String> choices = tabComplete(in);

        // No placements in session, so empty
        assertTrue(choices.contains("clockwise"));
        assertTrue(choices.contains("counterclockwise"));
    }

    // =====================================================================
    // move autocomplete — direction choices
    // =====================================================================

    @Test
    @DisplayName("move autocomplete shows direction choices")
    void moveAutocomplete(@Mock Player player) {
        CommandInput in = input(player, "move", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("up"));
        assertTrue(choices.contains("down"));
    }

    // =====================================================================
    // list command — works in any world
    // =====================================================================

    @Test
    @DisplayName("list succeeds in any world")
    void listInAnyWorld(@Mock Player player) {
        // No world stubs needed — list doesn't check world
        lenient().when(layoutManager.getLayoutNames()).thenReturn(List.of("map1", "map2"));

        CommandInput in = input(player, "list");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(layoutManager).getLayoutNames();
    }

    // =====================================================================
    // Unknown subcommand
    // =====================================================================

    @Test
    @DisplayName("unknown subcommand returns false")
    void unknownSubcommand(@Mock Player player) {
        CommandInput in = input(player, "nonexistent");
        boolean result = dispatch(in);

        assertFalse(result);
    }
}

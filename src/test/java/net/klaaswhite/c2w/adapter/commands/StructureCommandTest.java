package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.adapter.managers.GameManager;
import net.klaaswhite.c2w.adapter.managers.ResourceManager;
import net.klaaswhite.c2w.adapter.managers.StructureCreationManager;
import net.klaaswhite.c2w.adapter.managers.WorldManager;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.commands.CommandPiece;
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
import java.util.Map;

import org.bukkit.command.CommandSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StructureCommand")
class StructureCommandTest {

    @Mock private JavaPlugin plugin;
    @Mock private GameManager gameManager;
    @Mock private StructureCreationManager creationManager;
    @Mock private ResourceManager resourceManager;
    @Mock private FolderStructureTypeConfig typeConfig;
    @Mock private StructureManager structureManager;
    @Mock private WorldManager worldManager;

    private CommandPiece initialCommandPiece;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(plugin.getName()).thenReturn("c2w");

        StructureCommand cmd = new StructureCommand(
                plugin, gameManager, creationManager, resourceManager,
                typeConfig, structureManager, worldManager);

        // Access the protected initialCommandPiece from BaseCommand
        Field field = BaseCommand.class.getDeclaredField("initialCommandPiece");
        field.setAccessible(true);
        initialCommandPiece = (CommandPiece) field.get(cmd);
    }

    /**
     * Replicates the tree-walk from BaseCommand.onCommand.
     * Walks input.strings through getNextPiece(), then calls execute() on the final piece.
     */
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

    /**
     * Replicates the tree-walk from BaseCommand.onTabComplete.
     * Walks all but the last token via getNextPiece(), then calls getChoices() on the final node.
     */
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
    // 1. Resize command — valid dimensions
    // =====================================================================

    @Test
    @DisplayName("resize calls saveType with correct dimensions")
    void resizeCallsSaveType(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(world);
        when(player.getWorld()).thenReturn(world);
        when(typeConfig.hasType("castle")).thenReturn(true);

        CommandInput in = input(player, "resize", "castle", "10", "20", "30");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(typeConfig).saveType("castle", 10, 20, 30);
        verify(player).sendMessage("Structure type 'castle' resized: 10x20x30");
    }

    // =====================================================================
    // 2. Resize with invalid (non-numeric) dimensions
    // =====================================================================

    @Test
    @DisplayName("resize with non-numeric dimensions sends error")
    void resizeNonNumericDimensions(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(world);
        when(player.getWorld()).thenReturn(world);
        when(typeConfig.hasType("castle")).thenReturn(true);

        CommandInput in = input(player, "resize", "castle", "abc", "20", "30");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("Width, height, and depth must be integers.");
        verify(typeConfig, never()).saveType(anyString(), anyInt(), anyInt(), anyInt());
    }

    // =====================================================================
    // 3. Resize with negative dimensions
    // =====================================================================

    @Test
    @DisplayName("resize with negative dimensions sends error")
    void resizeNegativeDimensions(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(world);
        when(player.getWorld()).thenReturn(world);
        when(typeConfig.hasType("castle")).thenReturn(true);

        CommandInput in = input(player, "resize", "castle", "-5", "20", "30");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("Dimensions must be positive.");
        verify(typeConfig, never()).saveType(anyString(), anyInt(), anyInt(), anyInt());
    }

    // =====================================================================
    // 4. Resize unknown type
    // =====================================================================

    @Test
    @DisplayName("resize with unknown type sends error")
    void resizeUnknownType(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(world);
        when(player.getWorld()).thenReturn(world);
        when(typeConfig.hasType("unknown_type")).thenReturn(false);

        CommandInput in = input(player, "resize", "unknown_type", "10", "20", "30");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("Unknown type 'unknown_type'. Define it first with /structure define.");
        verify(typeConfig, never()).saveType(anyString(), anyInt(), anyInt(), anyInt());
    }

    // =====================================================================
    // 5. Container resource clear — verify removeContainerModeData is called
    // =====================================================================

    @Test
    @DisplayName("resource clear calls removeAllMarkersForResource")
    void resourceClearCallsRemoveAllMarkersForResource(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("c2w_resource_castle");
        when(player.getWorld()).thenReturn(world);
        when(resourceManager.getResourceSession("c2w_resource_castle"))
                .thenReturn(new ResourceManager.ResourceSession("c2w_resource_castle", "castle", null));
        when(resourceManager.removeAllMarkersForResource("c2w_resource_castle", "chest")).thenReturn(3);

        CommandInput in = input(player, "resource", "clear", "chest");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(resourceManager).removeAllMarkersForResource("c2w_resource_castle", "chest");
    }

    // =====================================================================
    // 6. Game in progress blocks commands
    // =====================================================================

    @Test
    @DisplayName("resize blocked when game is in progress")
    void resizeBlockedDuringGame(@Mock Player player) {
        when(gameManager.isGameInProgress()).thenReturn(true);

        CommandInput in = input(player, "resize", "castle", "10", "20", "30");
        boolean result = dispatch(in);

        assertTrue(result); // isGameInProgress returns true, handler returns true (consumed)
        verify(player).sendMessage("Structure commands are blocked during an active game.");
        verify(typeConfig, never()).saveType(anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("define blocked when game is in progress")
    void defineBlockedDuringGame(@Mock Player player) {
        when(gameManager.isGameInProgress()).thenReturn(true);

        CommandInput in = input(player, "define", "castle", "10", "20", "30");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(player).sendMessage("Structure commands are blocked during an active game.");
        verify(typeConfig, never()).saveType(anyString(), anyInt(), anyInt(), anyInt());
    }

    // =====================================================================
    // 7. Lobby check — non-lobby worlds are rejected
    // =====================================================================

    @Test
    @DisplayName("resize rejected when player is not in lobby world")
    void resizeRejectedOutsideLobby(@Mock Player player, @Mock World playerWorld, @Mock World lobbyWorld, @Mock ManagedWorld lobby) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(lobbyWorld);
        when(player.getWorld()).thenReturn(playerWorld);

        CommandInput in = input(player, "resize", "castle", "10", "20", "30");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in the lobby world.");
        verify(typeConfig, never()).saveType(anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("define rejected when player is not in lobby world")
    void defineRejectedOutsideLobby(@Mock Player player, @Mock World playerWorld, @Mock World lobbyWorld, @Mock ManagedWorld lobby) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(lobbyWorld);
        when(player.getWorld()).thenReturn(playerWorld);

        CommandInput in = input(player, "define", "castle", "10", "20", "30");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in the lobby world.");
    }

    @Test
    @DisplayName("resize blocked when lobby world is null")
    void resizeBlockedWhenLobbyNull(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        // When lobby is null, checkLobby returns false, and resizeType returns false
        lenient().when(gameManager.isGameInProgress()).thenReturn(false);
        lenient().when(worldManager.getLobbyWorld()).thenReturn(lobby);
        lenient().when(lobby.getWorld()).thenReturn(null);
        lenient().when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resize", "castle", "10", "20", "30");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(typeConfig, never()).saveType(anyString(), anyInt(), anyInt(), anyInt());
    }

    // =====================================================================
    // Additional edge-case tests
    // =====================================================================

    @Test
    @DisplayName("resize with too few arguments returns false without reaching handler")
    void resizeTooFewArgs(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        // Tree requires 5 args (resize + type + w + h + d) to reach the handler.
        // With only 2 args, dispatch stops at an intermediate node with no handler.
        // These stubs are never reached but are set up for completeness.
        lenient().when(gameManager.isGameInProgress()).thenReturn(false);
        lenient().when(worldManager.getLobbyWorld()).thenReturn(lobby);
        lenient().when(lobby.getWorld()).thenReturn(world);
        lenient().when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resize", "castle");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(typeConfig, never()).saveType(anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("define with valid args calls saveType")
    void defineWithValidArgs(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(world);
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "define", "castle", "10", "20", "30");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(typeConfig).saveType("castle", 10, 20, 30);
        verify(player).sendMessage("Structure type 'castle' defined: 10x20x30");
    }

    @Test
    @DisplayName("define with zero dimensions sends error")
    void defineZeroDimensions(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(world);
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "define", "castle", "0", "20", "30");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("Dimensions must be positive.");
        verify(typeConfig, never()).saveType(anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("list command is blocked during game")
    void listBlockedDuringGame(@Mock Player player) {
        when(gameManager.isGameInProgress()).thenReturn(true);

        CommandInput in = input(player, "list");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(player).sendMessage("Structure commands are blocked during an active game.");
    }

    @Test
    @DisplayName("unknown subcommand returns false")
    void unknownSubcommand(@Mock Player player) {
        CommandInput in = input(player, "nonexistent");
        boolean result = dispatch(in);

        assertFalse(result);
    }

    @Test
    @DisplayName("resource clear rejected outside resource world")
    void resourceClearRejectedOutsideResourceWorld(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("some_other_world");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "clear", "chest");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a resource world.");
        verify(resourceManager, never()).removeAllMarkersForResource(any(), anyString());
    }

    @Test
    @DisplayName("resource clear with too few args returns false without reaching handler")
    void resourceClearTooFewArgs(@Mock Player player, @Mock World world) {
        // Tree requires 3 args (resource + clear + resourceid) to reach the handler.
        // With only 2 args, dispatch stops at an intermediate node with no handler.
        lenient().when(gameManager.isGameInProgress()).thenReturn(false);
        lenient().when(world.getName()).thenReturn("c2w_resource_castle");
        lenient().when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "clear");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(resourceManager, never()).removeAllMarkersForResource(any(), anyString());
    }

    // =====================================================================
    // Tab completion context sensitivity
    // =====================================================================

    @Test
    @DisplayName("tab completion in lobby shows lobby-only commands")
    void tabCompletionLobby(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player);
        List<String> choices = initialCommandPiece.getChoices(in);

        assertTrue(choices.contains("define"));
        assertTrue(choices.contains("resize"));
        assertTrue(choices.contains("create"));
        assertTrue(choices.contains("modify"));
        assertTrue(choices.contains("list"));
        assertTrue(choices.contains("delete"));
        assertTrue(choices.contains("resource"));
        assertFalse(choices.contains("save"));
        assertFalse(choices.contains("discard"));
    }

    @Test
    @DisplayName("tab completion in creation world shows creation-only commands")
    void tabCompletionCreationWorld(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_create_castle_1");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player);
        List<String> choices = initialCommandPiece.getChoices(in);

        assertTrue(choices.contains("resource"));
        assertTrue(choices.contains("save"));
        assertTrue(choices.contains("discard"));
        assertFalse(choices.contains("define"));
        assertFalse(choices.contains("resize"));
        assertFalse(choices.contains("create"));
        assertFalse(choices.contains("modify"));
        assertFalse(choices.contains("delete"));
    }

    @Test
    @DisplayName("tab completion in resource world shows resource-only commands")
    void tabCompletionResourceWorld(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_resource_castle");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player);
        List<String> choices = initialCommandPiece.getChoices(in);

        assertTrue(choices.contains("resource"));
        assertTrue(choices.contains("save"));
        assertTrue(choices.contains("discard"));
        assertFalse(choices.contains("define"));
        assertFalse(choices.contains("resize"));
        assertFalse(choices.contains("create"));
        assertFalse(choices.contains("modify"));
        assertFalse(choices.contains("delete"));
    }

    @Test
    @DisplayName("tab completion for non-player sender shows all choices")
    void tabCompletionNonPlayer(@Mock CommandSender sender) {
        CommandInput in = input(sender);
        List<String> choices = initialCommandPiece.getChoices(in);

        assertTrue(choices.contains("define"));
        assertTrue(choices.contains("resize"));
        assertTrue(choices.contains("create"));
        assertTrue(choices.contains("modify"));
        assertTrue(choices.contains("list"));
        assertTrue(choices.contains("delete"));
        assertTrue(choices.contains("resource"));
        assertTrue(choices.contains("save"));
        assertTrue(choices.contains("discard"));
    }

    // =====================================================================
    // Resource world autocomplete — sub-command args
    // =====================================================================

    @Test
    @DisplayName("resource mark autocomplete returns resource IDs in resource world")
    void resourceMarkAutocompleteInResourceWorld(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_resource_dungeon");
        when(player.getWorld()).thenReturn(world);
        when(resourceManager.getResourceSession("c2w_resource_dungeon"))
                .thenReturn(new ResourceManager.ResourceSession("c2w_resource_dungeon", "dungeon", null));
        when(typeConfig.getResourceRequirements("dungeon"))
                .thenReturn(Map.of("goldchest", 2, "ironchest", 1));

        CommandInput in = input(player, "resource", "mark", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("goldchest"));
        assertTrue(choices.contains("ironchest"));
    }

    @Test
    @DisplayName("resource clear autocomplete returns resource IDs in resource world")
    void resourceClearAutocompleteInResourceWorld(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_resource_dungeon");
        when(player.getWorld()).thenReturn(world);
        when(resourceManager.getResourceSession("c2w_resource_dungeon"))
                .thenReturn(new ResourceManager.ResourceSession("c2w_resource_dungeon", "dungeon", null));
        when(typeConfig.getResourceRequirements("dungeon"))
                .thenReturn(Map.of("goldchest", 2));

        CommandInput in = input(player, "resource", "clear", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("goldchest"));
    }

    @Test
    @DisplayName("resource mark autocomplete returns empty outside creation/resource world")
    void resourceMarkAutocompleteOutsideResourceWorld(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "mark", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.isEmpty());
    }

    @Test
    @DisplayName("resource place autocomplete returns resource IDs in creation world")
    void resourcePlaceAutocompleteInCreationWorld(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_create_dungeon_1");
        when(player.getWorld()).thenReturn(world);
        when(creationManager.getCreationSession("c2w_create_dungeon_1"))
                .thenReturn(new StructureCreationManager.CreationSession("c2w_create_dungeon_1", "dungeon", "1", null, 0f));
        when(typeConfig.getResourceRequirements("dungeon"))
                .thenReturn(Map.of("reward", 3));

        CommandInput in = input(player, "resource", "place", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("reward"));
    }

    @Test
    @DisplayName("resource placehere autocomplete returns resource IDs in creation world")
    void resourcePlaceHereAutocompleteInCreationWorld(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_create_dungeon_1");
        when(player.getWorld()).thenReturn(world);
        when(creationManager.getCreationSession("c2w_create_dungeon_1"))
                .thenReturn(new StructureCreationManager.CreationSession("c2w_create_dungeon_1", "dungeon", "1", null, 0f));
        when(typeConfig.getResourceRequirements("dungeon"))
                .thenReturn(Map.of("reward", 3));

        CommandInput in = input(player, "resource", "placehere", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("reward"));
    }

    // =====================================================================
    // Lobby autocomplete — type name args
    // =====================================================================

    @Test
    @DisplayName("resource world autocomplete returns type names in lobby")
    void resourceWorldAutocompleteInLobby(@Mock Player player, @Mock World world) {
        lenient().when(typeConfig.getTypeNames()).thenReturn(List.of("dungeon", "castle"));

        CommandInput in = input(player, "resource", "world", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("dungeon"));
        assertTrue(choices.contains("castle"));
    }

    @Test
    @DisplayName("create autocomplete returns type names in lobby")
    void createAutocompleteInLobby(@Mock Player player, @Mock World world) {
        lenient().when(typeConfig.getTypeNames()).thenReturn(List.of("dungeon", "castle"));

        CommandInput in = input(player, "create", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("dungeon"));
        assertTrue(choices.contains("castle"));
    }

    @Test
    @DisplayName("modify autocomplete returns type names in lobby")
    void modifyAutocompleteInLobby(@Mock Player player, @Mock World world) {
        lenient().when(typeConfig.getTypeNames()).thenReturn(List.of("dungeon", "castle"));

        CommandInput in = input(player, "modify", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("dungeon"));
        assertTrue(choices.contains("castle"));
    }

    // =====================================================================
    // Resource world — handler rejection outside resource world
    // =====================================================================

    @Test
    @DisplayName("resource mark rejected outside resource world")
    void resourceMarkRejectedOutsideResourceWorld(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("some_other_world");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "mark", "chest");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a resource world.");
    }

    @Test
    @DisplayName("resource clear rejected outside resource world")
    void resourceClearRejectedOutsideResourceWorld2(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("some_other_world");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "clear", "chest");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a resource world.");
    }

    // =====================================================================
    // Mark/clear handler behavior in resource world
    // =====================================================================

    @Test
    @DisplayName("resource mark calls markResourceBlock")
    void resourceMarkCallsMarkResourceBlock(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("c2w_resource_dungeon");
        when(player.getWorld()).thenReturn(world);
        when(resourceManager.getResourceSession("c2w_resource_dungeon"))
                .thenReturn(new ResourceManager.ResourceSession("c2w_resource_dungeon", "dungeon", null));
        when(resourceManager.markResourceBlock(player, "dungeon", "chest")).thenReturn(true);

        CommandInput in = input(player, "resource", "mark", "chest");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(resourceManager).markResourceBlock(player, "dungeon", "chest");
    }

    @Test
    @DisplayName("resource mark with too few args returns false")
    void resourceMarkTooFewArgs(@Mock Player player, @Mock World world) {
        lenient().when(gameManager.isGameInProgress()).thenReturn(false);
        lenient().when(world.getName()).thenReturn("c2w_resource_dungeon");
        lenient().when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "mark");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(resourceManager, never()).markResourceBlock(any(), anyString(), anyString());
    }

    // =====================================================================
    // ResourceContextSensitiveRoot — sub-tree autocomplete per world
    // =====================================================================

    @Test
    @DisplayName("resource sub-tree shows creation commands in creation world")
    void resourceSubtreeAutocompleteInCreationWorld(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_create_castle_1");
        when(player.getWorld()).thenReturn(world);

        // Walk to resource sub-tree with incomplete "" token
        CommandInput in = input(player, "resource", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("place"));
        assertTrue(choices.contains("placehere"));
        assertTrue(choices.contains("list"));
        assertTrue(choices.contains("remove"));
        assertTrue(choices.contains("visualize"));
        assertFalse(choices.contains("mark"));
        assertFalse(choices.contains("clear"));
        assertFalse(choices.contains("world"));
    }

    @Test
    @DisplayName("resource sub-tree shows resource commands in resource world")
    void resourceSubtreeAutocompleteInResourceWorld(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_resource_castle");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "");
        List<String> choices = tabComplete(in);

        assertTrue(choices.contains("mark"));
        assertTrue(choices.contains("clear"));
        assertTrue(choices.contains("list"));
        assertTrue(choices.contains("remove"));
        assertFalse(choices.contains("place"));
        assertFalse(choices.contains("placehere"));
        assertFalse(choices.contains("visualize"));
        assertFalse(choices.contains("world"));
    }

    @Test
    @DisplayName("resource sub-tree shows world command in lobby")
    void resourceSubtreeAutocompleteInLobby(@Mock Player player, @Mock World world) {
        when(world.getName()).thenReturn("c2w_lobby");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "");
        List<String> choices = tabComplete(in);

        // Only "world" should be shown in lobby
        assertTrue(choices.contains("world"));
        assertFalse(choices.contains("place"));
        assertFalse(choices.contains("placehere"));
        assertFalse(choices.contains("list"));
        assertFalse(choices.contains("remove"));
        assertFalse(choices.contains("visualize"));
        assertFalse(choices.contains("mark"));
        assertFalse(choices.contains("clear"));
    }

    @Test
    @DisplayName("resource sub-tree shows all choices for non-player sender")
    void resourceSubtreeAutocompleteNonPlayer(@Mock CommandSender sender) {
        CommandInput in = input(sender, "resource", "");
        List<String> choices = tabComplete(in);

        // Non-player gets all choices from the delegate
        assertTrue(choices.contains("world"));
        assertTrue(choices.contains("place"));
        assertTrue(choices.contains("placehere"));
        assertTrue(choices.contains("mark"));
        assertTrue(choices.contains("list"));
        assertTrue(choices.contains("remove"));
        assertTrue(choices.contains("clear"));
        assertTrue(choices.contains("visualize"));
    }

    @Test
    @DisplayName("resource sub-tree shows all choices when world is null")
    void resourceSubtreeAutocompleteNullWorld(@Mock Player player) {
        when(player.getWorld()).thenReturn(null);

        CommandInput in = input(player, "resource", "");
        List<String> choices = tabComplete(in);

        // Null world gets all choices from the delegate
        assertTrue(choices.contains("world"));
        assertTrue(choices.contains("place"));
        assertTrue(choices.contains("placehere"));
        assertTrue(choices.contains("mark"));
        assertTrue(choices.contains("list"));
        assertTrue(choices.contains("remove"));
        assertTrue(choices.contains("clear"));
        assertTrue(choices.contains("visualize"));
    }

    // =====================================================================
    // Creation-world handler rejection for resource-only commands
    // =====================================================================

    @Test
    @DisplayName("resource mark rejected in creation world")
    void resourceMarkRejectedInCreationWorld(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("c2w_create_castle_1");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "mark", "chest");
        boolean result = dispatch(in);

        assertFalse(result);
        // dispatch still walks through the tree to the handler, which checks context
        verify(player).sendMessage("This command can only be used in a resource world.");
    }

    @Test
    @DisplayName("resource clear rejected in creation world")
    void resourceClearRejectedInCreationWorld(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("c2w_create_castle_1");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "clear", "chest");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a resource world.");
    }

    @Test
    @DisplayName("resource place rejected in resource world")
    void resourcePlaceRejectedInResourceWorld(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("c2w_resource_dungeon");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "place", "chest");
        boolean result = dispatch(in);

        assertFalse(result);
        verify(player).sendMessage("This command can only be used in a creation world.");
    }

    @Test
    @DisplayName("resource list works in resource world")
    void resourceListInResourceWorld(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("c2w_resource_dungeon");
        when(player.getWorld()).thenReturn(world);

        CommandInput in = input(player, "resource", "list");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(player, never()).sendMessage("This command can only be used in a creation world.");
    }

    @Test
    @DisplayName("resource define reaches handler in resource world")
    void resourceDefineInResourceWorld(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("c2w_resource_dungeon");
        when(player.getWorld()).thenReturn(world);
        when(resourceManager.getResourceSession("c2w_resource_dungeon"))
                .thenReturn(mock(ResourceManager.ResourceSession.class));
        when(resourceManager.getResourceSession("c2w_resource_dungeon").typeName()).thenReturn("dungeon");

        CommandInput in = input(player, "resource", "define", "block", "c1");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(typeConfig).addResourceRequirement("dungeon", "c1", 1, "block");
        verify(player).sendMessage("Defined block resource 'c1' for type 'dungeon'.");
    }

    @Test
    @DisplayName("resource define reaches handler in resource world (2)")
    void resourceDefineInResourceWorld2(@Mock Player player, @Mock World world) {
        when(gameManager.isGameInProgress()).thenReturn(false);
        when(world.getName()).thenReturn("c2w_resource_dungeon");
        when(player.getWorld()).thenReturn(world);
        when(resourceManager.getResourceSession("c2w_resource_dungeon"))
                .thenReturn(mock(ResourceManager.ResourceSession.class));
        when(resourceManager.getResourceSession("c2w_resource_dungeon").typeName()).thenReturn("dungeon");

        CommandInput in = input(player, "resource", "define", "container", "chest");
        boolean result = dispatch(in);

        assertTrue(result);
        verify(typeConfig).addResourceRequirement("dungeon", "chest", 1, "container");
        verify(player).sendMessage("Defined container resource 'chest' for type 'dungeon'.");
    }
}

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StructureCommandTest {
    private GameManager gameManager;
    private StructureCreationManager creationManager;
    private ResourceManager resourceManager;
    private FolderStructureTypeConfig typeConfig;
    private StructureManager structureManager;
    private WorldManager worldManager;
    private CommandPiece root;

    @BeforeEach
    void setUp() throws Exception {
        var plugin = mock(JavaPlugin.class);
        gameManager = mock(GameManager.class);
        creationManager = mock(StructureCreationManager.class);
        resourceManager = mock(ResourceManager.class);
        typeConfig = mock(FolderStructureTypeConfig.class);
        structureManager = mock(StructureManager.class);
        worldManager = mock(WorldManager.class);

        var command = new StructureCommand(plugin, gameManager, creationManager, resourceManager,
                typeConfig, structureManager, worldManager);
        Field field = BaseCommand.class.getDeclaredField("initialCommandPiece");
        field.setAccessible(true);
        root = (CommandPiece) field.get(command);
    }

    private CommandInput input(Player player, String... args) {
        var input = new CommandInput();
        input.commandSender = player;
        input.strings = args;
        return input;
    }

    private boolean dispatch(CommandInput input) {
        CommandPiece current = root;
        for (int i = 0; i <= input.strings.length; i++) {
            if (current == null) return false;
            if (i == input.strings.length) return current.execute(input);
            current = current.getNextPiece(input.strings[i]);
        }
        return false;
    }

    @Test
    void lobbyRootContainsOnlyDocumentedCommands(@Mock Player player, @Mock World world,
                                                  @Mock ManagedWorld lobby) {
        when(player.getWorld()).thenReturn(world);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(world);

        var choices = root.getChoices(input(player, ""));

        assertEquals(List.of("define", "resource", "create", "modify"), choices);
    }

    @Test
    void resourceSelectionIsFlat(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        when(player.getWorld()).thenReturn(world);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(world);

        assertTrue(dispatch(input(player, "resource", "house")));
        verify(resourceManager).openResourceWorld(player, "house");
    }

    @Test
    void generalSpecialWorldsAreSuggestedForModify(@Mock Player player) {
        when(typeConfig.getTypeNames()).thenReturn(List.of());
        when(typeConfig.getInstanceIds("general")).thenReturn(List.of());
        when(structureManager.discoverTemplates()).thenReturn(List.of());

        var modify = root.getNextPiece("modify");
        assertEquals(List.of("general"), modify.getChoices(input(player, "modify")));

        var instances = modify.getNextPiece("general");
        assertEquals(List.of("lobby", "draft"),
                instances.getChoices(input(player, "modify", "general")));
    }

    @Test
    void legacyBranchesAreUnavailable(@Mock Player player) {
        assertFalse(root.getChoices(input(player, "")).contains("resize"));
        assertFalse(root.getChoices(input(player, "")).contains("marker"));
        assertFalse(dispatch(input(player, "resize", "house", "1", "1", "1")));
    }
}

package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.adapter.managers.LayoutEditorManager;
import net.klaaswhite.c2w.adapter.managers.WorldManager;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.adapter.managers.GameManager;
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
class LayoutEditorCommandTest {
    private LayoutEditorManager layoutManager;
    private WorldManager worldManager;
    private CommandPiece root;

    @BeforeEach
    void setUp() throws Exception {
        var plugin = mock(JavaPlugin.class);
        layoutManager = mock(LayoutEditorManager.class);
        worldManager = mock(WorldManager.class);
        var layouts = mock(LayoutManager.class);
        var types = mock(FolderStructureTypeConfig.class);
        var structures = mock(StructureManager.class);
        var games = mock(GameManager.class);
        when(plugin.getCommand("layout")).thenReturn(mock(org.bukkit.command.PluginCommand.class));

        var command = new LayoutEditorCommand(plugin, layoutManager, layouts, types, structures, worldManager, games);
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

    @Test
    void lobbyRootContainsOnlyCreateAndModify(@Mock Player player, @Mock World world,
                                               @Mock ManagedWorld lobby) {
        when(player.getWorld()).thenReturn(world);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);
        when(lobby.getWorld()).thenReturn(world);

        assertEquals(List.of("create", "modify"), root.getChoices(input(player, "")));
    }

    @Test
    void rootIsUnavailableOutsideLobby(@Mock Player player, @Mock World world, @Mock ManagedWorld lobby) {
        when(player.getWorld()).thenReturn(world);
        when(lobby.getWorld()).thenReturn(mock(World.class));
        when(worldManager.getLobbyWorld()).thenReturn(lobby);

        assertTrue(root.getChoices(input(player, "")).isEmpty());
    }

    @Test
    void nonPlayersCannotUseRoot(@Mock org.bukkit.command.CommandSender sender) {
        var input = new CommandInput();
        input.commandSender = sender;
        input.strings = new String[]{""};
        assertTrue(root.getChoices(input).isEmpty());
    }
}

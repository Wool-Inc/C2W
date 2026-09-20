package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.adapter.managers.StructureCreationManager;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.commands.CommandPiece;
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
class MarkerCommandTest {
    private StructureCreationManager creationManager;
    private FolderStructureTypeConfig typeConfig;
    private CommandPiece root;

    @BeforeEach
    void setUp() throws Exception {
        var plugin = mock(JavaPlugin.class);
        creationManager = mock(StructureCreationManager.class);
        typeConfig = mock(FolderStructureTypeConfig.class);

        var command = new MarkerCommand(plugin, creationManager, typeConfig);
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
    void markerRootOffersDocumentedRemovalCommands(@Mock Player player, @Mock World world) {
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("c2w_create_type_id");
        when(creationManager.getCreationSession("c2w_create_type_id")).thenReturn(mock(StructureCreationManager.CreationSession.class));

        var choices = root.getChoices(input(player, ""));

        assertTrue(choices.contains("removelooking"));
        assertTrue(choices.contains("removehere"));
        assertTrue(choices.contains("placehere"));
        assertTrue(choices.contains("placelooking"));
        assertFalse(choices.contains("wool"));
    }

    @Test
    void placelookingUsesActionThenMarkerName(@Mock Player player, @Mock World world) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("c2w_create_type_id");
        when(creationManager.getCreationSession("c2w_create_type_id")).thenReturn(mock(StructureCreationManager.CreationSession.class));
        when(creationManager.placeGameMarker(player, "wool")).thenReturn(true);

        assertTrue(dispatch(input(player, "placelooking", "wool")));
        verify(creationManager).placeGameMarker(player, "wool");
    }

    @Test
    void placementAutocompleteIncludesResourceIds(@Mock Player player, @Mock World world) {
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("c2w_create_type_id");
        when(creationManager.getCreationSession("c2w_create_type_id")).thenReturn(mock(StructureCreationManager.CreationSession.class));
        when(typeConfig.getTypeNames()).thenReturn(List.of("type"));
        when(typeConfig.getResourceIds("type")).thenReturn(List.of("iron", "trial"));
        when(typeConfig.getTrialResourceIds("type")).thenReturn(List.of("trial"));

        var placement = root.getNextPiece("placelooking");
        var choices = placement.getChoices(input(player, "placelooking", ""));

        assertTrue(choices.contains("wool"));
        assertTrue(choices.contains("iron"));
        assertTrue(choices.contains("trial"));
        assertTrue(choices.contains("trial-spawner-trial"));
        assertTrue(choices.contains("trial-vault-trial"));
    }
}

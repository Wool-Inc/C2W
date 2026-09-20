package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.adapter.managers.GameManager;
import net.klaaswhite.c2w.adapter.managers.MarkerManager;
import net.klaaswhite.c2w.adapter.managers.PlayerManager;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class C2WCommandTest {
    private CommandPiece root;

    @BeforeEach
    void setUp() throws Exception {
        var command = new C2WCommand(
                mock(JavaPlugin.class),
                mock(GameManager.class),
                mock(MarkerManager.class),
                mock(LayoutManager.class),
                mock(PlayerManager.class),
                ignored -> { },
                mock(WoolTimer.class),
                mock(MinecraftManager.class)
        );

        Field field = BaseCommand.class.getDeclaredField("initialCommandPiece");
        field.setAccessible(true);
        root = (CommandPiece) field.get(command);
    }

    @Test
    void draftRootAllowsReset(@Mock Player player, @Mock World world) {
        when(player.hasPermission("c2w.admin")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("c2w_draft");

        var input = new CommandInput();
        input.commandSender = player;
        input.strings = new String[]{""};

        assertTrue(root.getChoices(input).contains("reset"));
        assertNotNull(root.getNextPiece("reset"));
    }
}

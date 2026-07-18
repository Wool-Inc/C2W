package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.commands.BaseCommand;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CommandManager")
class CommandManagerTest {

    private final Plugin plugin = mock(Plugin.class);

    // --- Constructor tests -------------------------------------------------------

    @Test
    @DisplayName("constructor stores the provided commands")
    void constructorStoresCommands() {
        var cmd1 = mock(BaseCommand.class);
        var cmd2 = mock(BaseCommand.class);

        var mgr = new CommandManager(plugin, cmd1, cmd2);

        mgr.close();
        verify(cmd1).close();
        verify(cmd2).close();
    }

    @Test
    @DisplayName("constructor accepts zero commands")
    void constructorAcceptsNoCommands() {
        var mgr = new CommandManager(plugin);
        assertDoesNotThrow(mgr::close);
    }

    // --- close() -----------------------------------------------------------------

    @Test
    @DisplayName("close calls close on every command")
    void closeCallsCloseOnAllCommands() {
        var cmd1 = mock(BaseCommand.class);
        var cmd2 = mock(BaseCommand.class);

        var mgr = new CommandManager(plugin, cmd1, cmd2);
        mgr.close();

        verify(cmd1).close();
        verify(cmd2).close();
    }

    @Test
    @DisplayName("close does not throw when a command's close throws")
    void closeHandlesCommandException() {
        var goodCmd = mock(BaseCommand.class);
        var badCmd = mock(BaseCommand.class);
        doThrow(new RuntimeException("boom")).when(badCmd).close();

        var mgr = new CommandManager(plugin, goodCmd, badCmd);
        assertDoesNotThrow(mgr::close);

        verify(goodCmd).close();
        verify(badCmd).close();
    }

    @Test
    @DisplayName("close continues closing remaining commands after an exception")
    void closeContinuesAfterException() {
        var cmd1 = mock(BaseCommand.class);
        var cmd2 = mock(BaseCommand.class);
        var cmd3 = mock(BaseCommand.class);
        doThrow(new RuntimeException("boom")).when(cmd2).close();

        var mgr = new CommandManager(plugin, cmd1, cmd2, cmd3);
        assertDoesNotThrow(mgr::close);

        verify(cmd1).close();
        verify(cmd2).close();
        verify(cmd3).close();
    }

    @Test
    @DisplayName("close can be called safely multiple times")
    void closeCalledMultipleTimes() {
        var cmd = mock(BaseCommand.class);

        var mgr = new CommandManager(plugin, cmd);
        assertDoesNotThrow(() -> {
            mgr.close();
            mgr.close();
            mgr.close();
        });

        verify(cmd, times(3)).close();
    }

    @Test
    @DisplayName("close with zero commands does nothing")
    void closeWithNoCommands() {
        var mgr = new CommandManager(plugin);
        assertDoesNotThrow(() -> {
            mgr.close();
            mgr.close();
        });
    }
}

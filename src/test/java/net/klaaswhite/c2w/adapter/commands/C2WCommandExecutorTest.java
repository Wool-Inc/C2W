package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandInput;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("C2WCommandExecutor")
class C2WCommandExecutorTest {

    @Test
    @DisplayName("delegates to handler and returns true")
    void delegatesTrue() {
        Function<CommandInput, Boolean> handler = mock(Function.class);
        when(handler.apply(any())).thenReturn(true);

        var executor = new C2WCommandExecutor(handler);
        var sender = mock(CommandSender.class);
        var command = mock(Command.class);

        assertTrue(executor.onCommand(sender, command, "c2w", new String[]{"arg1"}));
        verify(handler).apply(any(CommandInput.class));
    }

    @Test
    @DisplayName("delegates to handler and returns false")
    void delegatesFalse() {
        Function<CommandInput, Boolean> handler = mock(Function.class);
        when(handler.apply(any())).thenReturn(false);

        var executor = new C2WCommandExecutor(handler);
        var sender = mock(CommandSender.class);
        var command = mock(Command.class);

        assertFalse(executor.onCommand(sender, command, "c2w", new String[]{}));
    }

    @Test
    @DisplayName("passes CommandInput with correct fields")
    void passesCorrectInput() {
        Function<CommandInput, Boolean> handler = mock(Function.class);
        when(handler.apply(any())).thenReturn(true);

        var executor = new C2WCommandExecutor(handler);
        var sender = mock(CommandSender.class);
        var command = mock(Command.class);
        String[] args = {"sub", "arg"};

        executor.onCommand(sender, command, "c2w", args);

        verify(handler).apply(argThat(input -> {
            assertSame(sender, input.commandSender);
            assertSame(command, input.command);
            assertEquals("c2w", input.s);
            assertArrayEquals(args, input.strings);
            return true;
        }));
    }
}

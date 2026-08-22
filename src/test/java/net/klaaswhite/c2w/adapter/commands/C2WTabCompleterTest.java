package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandInput;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("C2WTabCompleter")
class C2WTabCompleterTest {

    @Test
    @DisplayName("delegates to handler and returns its result")
    void delegatesToHandler() {
        Function<CommandInput, List<String>> handler = mock(Function.class);
        when(handler.apply(any())).thenReturn(List.of("opt1", "opt2"));

        var completer = new C2WTabCompleter(handler);
        var sender = mock(CommandSender.class);
        var command = mock(Command.class);

        var result = completer.onTabComplete(sender, command, "c2w", new String[]{"sub"});

        assertEquals(List.of("opt1", "opt2"), result);
        verify(handler).apply(any(CommandInput.class));
    }

    @Test
    @DisplayName("passes CommandInput with correct fields")
    void passesCorrectInput() {
        Function<CommandInput, List<String>> handler = mock(Function.class);
        when(handler.apply(any())).thenReturn(List.of());

        var completer = new C2WTabCompleter(handler);
        var sender = mock(CommandSender.class);
        var command = mock(Command.class);
        String[] args = {"a", "b"};

        completer.onTabComplete(sender, command, "c2w", args);

        verify(handler).apply(argThat(input -> {
            assertSame(sender, input.commandSender);
            assertSame(command, input.command);
            assertEquals("c2w", input.s);
            assertArrayEquals(args, input.strings);
            return true;
        }));
    }

    @Test
    @DisplayName("returns empty list when handler returns empty")
    void returnsEmpty() {
        Function<CommandInput, List<String>> handler = input -> List.of();
        var completer = new C2WTabCompleter(handler);
        var sender = mock(CommandSender.class);
        var command = mock(Command.class);

        var result = completer.onTabComplete(sender, command, "c2w", new String[]{});
        assertTrue(result.isEmpty());
    }
}

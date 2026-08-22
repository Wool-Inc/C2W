package net.klaaswhite.c2w.domain.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CommandPiece")
class CommandPieceTest {

    // --- CommandPiece without handler ---

    @Test
    @DisplayName("execute returns false when handler is null")
    void executeWithoutHandlerReturnsFalse() {
        var nextPiece = new CommandPiece(null, null);
        var piece = new CommandPiece(nextPiece, null);
        var input = new CommandInput();

        assertFalse(piece.execute(input));
    }

    // --- CommandPiece with handler ---

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("execute delegates to handler and returns its result (true)")
    void executeWithHandlerReturnsTrue() {
        Function<CommandInput, Boolean> handler = mock(Function.class);
        var input = new CommandInput();
        when(handler.apply(input)).thenReturn(true);

        var piece = new CommandPiece(null, handler);

        assertTrue(piece.execute(input));
        verify(handler).apply(input);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("execute delegates to handler and returns its result (false)")
    void executeWithHandlerReturnsFalse() {
        Function<CommandInput, Boolean> handler = mock(Function.class);
        var input = new CommandInput();
        when(handler.apply(input)).thenReturn(false);

        var piece = new CommandPiece(null, handler);

        assertFalse(piece.execute(input));
        verify(handler).apply(input);
    }

    // --- CommandPiece.getNextPiece ---

    @Test
    @DisplayName("getNextPiece returns nextPiece regardless of choice string")
    void getNextPieceReturnsNextPiece() {
        var nextPiece = new CommandPiece(null, null);
        var piece = new CommandPiece(nextPiece, null);

        assertSame(nextPiece, piece.getNextPiece("anything"));
        assertSame(nextPiece, piece.getNextPiece(""));
        assertSame(nextPiece, piece.getNextPiece(null));
    }

    @Test
    @DisplayName("getNextPiece returns null when nextPiece is null")
    void getNextPieceReturnsNull() {
        var piece = new CommandPiece(null, null);

        assertNull(piece.getNextPiece("anything"));
    }

    // --- TreeChoiceCommandPiece.addChoice ---

    @Test
    @DisplayName("addChoice adds retrievable entries")
    void addChoiceAddsEntries() {
        var choice1 = new CommandPiece(null, null);
        var choice2 = new CommandPiece(null, null);
        var tree = new TreeChoiceCommandPiece(null);

        tree.addChoice("start", choice1);
        tree.addChoice("stop", choice2);

        assertSame(choice1, tree.getNextPiece("start"));
        assertSame(choice2, tree.getNextPiece("stop"));
    }

    // --- TreeChoiceCommandPiece.getChoices ---

    @Test
    @DisplayName("getChoices returns all added choice names")
    void getChoicesReturnsAllNames() {
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("alpha", new CommandPiece(null, null));
        tree.addChoice("beta", new CommandPiece(null, null));
        tree.addChoice("gamma", new CommandPiece(null, null));

        List<String> choices = tree.getChoices(new CommandInput());

        assertEquals(3, choices.size());
        assertTrue(choices.containsAll(List.of("alpha", "beta", "gamma")));
    }

    @Test
    @DisplayName("getChoices returns empty list when no choices added")
    void getChoicesEmptyWhenNoChoices() {
        var tree = new TreeChoiceCommandPiece(null);

        assertTrue(tree.getChoices(new CommandInput()).isEmpty());
    }

    // --- TreeChoiceCommandPiece.getNextPiece ---

    @Test
    @DisplayName("getNextPiece returns correct piece by name")
    void getNextPieceByExactName() {
        var pieceA = new CommandPiece(null, null);
        var pieceB = new CommandPiece(null, null);
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("A", pieceA);
        tree.addChoice("B", pieceB);

        assertSame(pieceA, tree.getNextPiece("A"));
        assertSame(pieceB, tree.getNextPiece("B"));
    }

    @Test
    @DisplayName("getNextPiece returns null for unknown choice")
    void getNextPieceReturnsNullForUnknown() {
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("known", new CommandPiece(null, null));

        assertNull(tree.getNextPiece("unknown"));
    }

    // --- Null handler + null nextPiece ---

    @Test
    @DisplayName("execute returns false when both nextPiece and handler are null")
    void executeWithAllNullReturnsFalse() {
        var piece = new CommandPiece(null, null);
        var input = new CommandInput();

        assertFalse(piece.execute(input));
    }
}
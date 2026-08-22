package net.klaaswhite.c2w.domain.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommandFramework")
class CommandFrameworkTest {

    // =====================================================================
    // CommandInput
    // =====================================================================

    @Test
    @DisplayName("CommandInput stores args array and string fields")
    void commandInputStoresFields() {
        var input = new CommandInput();
        input.commandSender = "sender";
        input.command = "cmd";
        input.s = "single";
        input.strings = new String[]{"a", "b", "c"};

        assertSame("sender", input.commandSender);
        assertSame("cmd", input.command);
        assertEquals("single", input.s);
        assertArrayEquals(new String[]{"a", "b", "c"}, input.strings);
    }

    @Test
    @DisplayName("CommandInput strings accessor returns correct element")
    void commandInputStringsAccessor() {
        var input = new CommandInput();
        input.strings = new String[]{"first", "second", "third"};

        assertEquals("first", input.strings[0]);
        assertEquals("second", input.strings[1]);
        assertEquals("third", input.strings[2]);
        assertEquals(3, input.strings.length);
    }

    @Test
    @DisplayName("CommandInput can be constructed with empty args")
    void commandInputEmptyArgs() {
        var input = new CommandInput();
        input.commandSender = "player";
        input.strings = new String[]{};

        assertSame("player", input.commandSender);
        assertEquals(0, input.strings.length);
    }

    // =====================================================================
    // CommandPiece — terminal node with handler
    // =====================================================================

    @Test
    @DisplayName("CommandPiece terminal node invokes handler and returns true")
    void terminalPieceInvokesHandler() {
        Function<CommandInput, Boolean> handler = input -> true;
        var piece = new CommandPiece(null, handler);
        var input = new CommandInput();

        assertTrue(piece.execute(input));
    }

    @Test
    @DisplayName("CommandPiece terminal node invokes handler and returns false")
    void terminalPieceHandlerReturnsFalse() {
        Function<CommandInput, Boolean> handler = input -> false;
        var piece = new CommandPiece(null, handler);
        var input = new CommandInput();

        assertFalse(piece.execute(input));
    }

    @Test
    @DisplayName("CommandPiece with null handler returns false")
    void nullHandlerReturnsFalse() {
        var piece = new CommandPiece(null, null);
        var input = new CommandInput();

        assertFalse(piece.execute(input));
    }

    @Test
    @DisplayName("CommandPiece getNextPiece returns null when no nextPiece")
    void getNextPieceReturnsNullWhenNone() {
        var piece = new CommandPiece(null, null);

        assertNull(piece.getNextPiece("anything"));
    }

    @Test
    @DisplayName("CommandPiece getNextPiece returns the next piece regardless of key")
    void getNextPieceReturnsNextPiece() {
        var next = new CommandPiece(null, null);
        var piece = new CommandPiece(next, null);

        assertSame(next, piece.getNextPiece("someKey"));
    }

    @Test
    @DisplayName("CommandPiece getChoices returns empty list by default")
    void baseGetChoicesReturnsEmpty() {
        var piece = new CommandPiece(null, null);

        assertTrue(piece.getChoices(new CommandInput()).isEmpty());
    }

    // =====================================================================
    // CommandPiece — chaining pieces
    // =====================================================================

    @Test
    @DisplayName("CommandPiece chains to next piece via getNextPiece")
    void chainingPieces() {
        Function<CommandInput, Boolean> endHandler = input -> true;
        var terminal = new CommandPiece(null, endHandler);
        var intermediate = new CommandPiece(terminal, null);
        var root = new CommandPiece(intermediate, null);
        var input = new CommandInput();

        assertSame(intermediate, root.getNextPiece("go"));
        assertSame(terminal, intermediate.getNextPiece("go"));
        assertTrue(terminal.execute(input));
    }

    @Test
    @DisplayName("CommandPiece handler receives the input it was given")
    void handlerReceivesCorrectInput() {
        @SuppressWarnings("unchecked")
        Function<CommandInput, Boolean> handler = input -> {
            input.strings = new String[]{"modified"};
            return true;
        };
        var piece = new CommandPiece(null, handler);
        var input = new CommandInput();
        input.strings = new String[]{"original"};

        piece.execute(input);

        assertArrayEquals(new String[]{"modified"}, input.strings);
    }

    // =====================================================================
    // TreeChoiceCommandPiece
    // =====================================================================

    @Test
    @DisplayName("TreeChoiceCommandPiece addChoice stores and retrieves choices")
    void addChoiceStoresAndRetrieves() {
        var pieceA = new CommandPiece(null, null);
        var pieceB = new CommandPiece(null, null);
        var tree = new TreeChoiceCommandPiece(null);

        tree.addChoice("optionA", pieceA);
        tree.addChoice("optionB", pieceB);

        assertSame(pieceA, tree.getNextPiece("optionA"));
        assertSame(pieceB, tree.getNextPiece("optionB"));
    }

    @Test
    @DisplayName("TreeChoiceCommandPiece find matching choice returns correct piece")
    void findMatchingChoice() {
        var handler = (Function<CommandInput, Boolean>) input -> true;
        var targetPiece = new CommandPiece(null, handler);
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("target", targetPiece);
        tree.addChoice("other", new CommandPiece(null, null));

        var result = tree.getNextPiece("target");

        assertSame(targetPiece, result);
        assertNotNull(result);
    }

    @Test
    @DisplayName("TreeChoiceCommandPiece no match returns null")
    void noMatchReturnsNull() {
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("exists", new CommandPiece(null, null));

        assertNull(tree.getNextPiece("doesNotExist"));
    }

    @Test
    @DisplayName("TreeChoiceCommandPiece getChoices returns all registered names")
    void getChoicesReturnsAllNames() {
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("one", new CommandPiece(null, null));
        tree.addChoice("two", new CommandPiece(null, null));
        tree.addChoice("three", new CommandPiece(null, null));

        List<String> choices = tree.getChoices(new CommandInput());

        assertEquals(3, choices.size());
        assertTrue(choices.contains("one"));
        assertTrue(choices.contains("two"));
        assertTrue(choices.contains("three"));
    }

    @Test
    @DisplayName("TreeChoiceCommandPiece getChoices returns empty when no choices added")
    void getChoicesEmptyWhenNoChoices() {
        var tree = new TreeChoiceCommandPiece(null);

        assertTrue(tree.getChoices(new CommandInput()).isEmpty());
    }

    @Test
    @DisplayName("TreeChoiceCommandPiece addChoice overwrites existing key")
    void addChoiceOverwritesExistingKey() {
        var piece1 = new CommandPiece(null, null);
        var piece2 = new CommandPiece(null, null);
        var tree = new TreeChoiceCommandPiece(null);

        tree.addChoice("key", piece1);
        tree.addChoice("key", piece2);

        assertSame(piece2, tree.getNextPiece("key"));
        assertEquals(1, tree.getChoices(new CommandInput()).size());
    }

    @Test
    @DisplayName("TreeChoiceCommandPiece getNextPiece returns null for null key")
    void getNextPieceNullKey() {
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("valid", new CommandPiece(null, null));

        assertNull(tree.getNextPiece(null));
    }

    // =====================================================================
    // ListChoiceCommandPiece
    // =====================================================================

    @Test
    @DisplayName("ListChoiceCommandPiece presents fixed list of choices")
    void presentsFixedChoices() {
        List<String> fixedChoices = List.of("sword", "bow", "shield");
        var next = new CommandPiece(null, null);
        var listPiece = new ListChoiceCommandPiece(next, null, fixedChoices);
        var input = new CommandInput();

        List<String> choices = listPiece.getChoices(input);

        assertEquals(3, choices.size());
        assertTrue(choices.contains("sword"));
        assertTrue(choices.contains("bow"));
        assertTrue(choices.contains("shield"));
    }

    @Test
    @DisplayName("ListChoiceCommandPiece returns same list regardless of input")
    void fixedChoicesSameRegardlessOfInput() {
        List<String> fixedChoices = List.of("alpha", "beta");
        var listPiece = new ListChoiceCommandPiece(null, null, fixedChoices);

        var input1 = new CommandInput();
        input1.strings = new String[]{"test"};
        var input2 = new CommandInput();
        input2.strings = new String[]{};

        assertEquals(listPiece.getChoices(input1), listPiece.getChoices(input2));
    }

    @Test
    @DisplayName("ListChoiceCommandPiece getNextPiece returns the next piece")
    void getNextPieceReturnsNext() {
        var next = new CommandPiece(null, null);
        var listPiece = new ListChoiceCommandPiece(next, null, List.of("a", "b"));

        assertSame(next, listPiece.getNextPiece("a"));
    }

    @Test
    @DisplayName("ListChoiceCommandPiece with empty list returns empty choices")
    void emptyListChoices() {
        var listPiece = new ListChoiceCommandPiece(null, null, List.of());

        assertTrue(listPiece.getChoices(new CommandInput()).isEmpty());
    }

    @Test
    @DisplayName("ListChoiceCommandPiece choices are immutable (fixed)")
    void choicesAreFixed() {
        List<String> fixedChoices = List.of("only");
        var listPiece = new ListChoiceCommandPiece(null, null, fixedChoices);

        List<String> choices = listPiece.getChoices(new CommandInput());
        assertThrows(UnsupportedOperationException.class, () -> choices.add("extra"));
    }

    // =====================================================================
    // DynamicListChoiceCommandPiece
    // =====================================================================

    @Test
    @DisplayName("DynamicListChoiceCommandPiece presents dynamic list from supplier")
    void presentsDynamicChoices() {
        Function<CommandInput, List<String>> supplier = input -> List.of("dyn1", "dyn2", "dyn3");
        var dynPiece = new DynamicListChoiceCommandPiece(null, null, supplier);
        var input = new CommandInput();

        List<String> choices = dynPiece.getChoices(input);

        assertEquals(3, choices.size());
        assertTrue(choices.contains("dyn1"));
        assertTrue(choices.contains("dyn2"));
        assertTrue(choices.contains("dyn3"));
    }

    @Test
    @DisplayName("DynamicListChoiceCommandPiece supplier receives the input")
    void supplierReceivesInput() {
        @SuppressWarnings("unchecked")
        Function<CommandInput, List<String>> supplier = input -> {
            if (input.strings.length > 0) {
                return List.of("arg_" + input.strings[0]);
            }
            return List.of("noargs");
        };
        var dynPiece = new DynamicListChoiceCommandPiece(null, null, supplier);

        var inputWithArg = new CommandInput();
        inputWithArg.strings = new String[]{"hello"};

        var inputNoArg = new CommandInput();
        inputNoArg.strings = new String[]{};

        assertEquals(List.of("arg_hello"), dynPiece.getChoices(inputWithArg));
        assertEquals(List.of("noargs"), dynPiece.getChoices(inputNoArg));
    }

    @Test
    @DisplayName("DynamicListChoiceCommandPiece acts as intermediate node with nextPiece")
    void actsAsIntermediateNode() {
        Function<CommandInput, Boolean> handler = input -> true;
        var terminal = new CommandPiece(null, handler);
        Function<CommandInput, List<String>> supplier = input -> List.of("opt1", "opt2");
        var dynPiece = new DynamicListChoiceCommandPiece(terminal, null, supplier);
        var input = new CommandInput();

        // It presents choices
        assertEquals(2, dynPiece.getChoices(input).size());

        // It delegates to the next piece
        assertSame(terminal, dynPiece.getNextPiece("opt1"));
        assertTrue(terminal.execute(input));
    }

    @Test
    @DisplayName("DynamicListChoiceCommandPiece acts as terminal when nextPiece is null")
    void actsAsTerminal() {
        Function<CommandInput, Boolean> handler = input -> true;
        Function<CommandInput, List<String>> supplier = input -> List.of("run");
        var dynPiece = new DynamicListChoiceCommandPiece(null, handler, supplier);
        var input = new CommandInput();

        // Presents choices
        assertEquals(List.of("run"), dynPiece.getChoices(input));

        // No next piece
        assertNull(dynPiece.getNextPiece("run"));

        // Handler is invoked
        assertTrue(dynPiece.execute(input));
    }

    @Test
    @DisplayName("DynamicListChoiceCommandPiece with empty dynamic list")
    void emptyDynamicList() {
        Function<CommandInput, List<String>> supplier = input -> List.of();
        var dynPiece = new DynamicListChoiceCommandPiece(null, null, supplier);

        assertTrue(dynPiece.getChoices(new CommandInput()).isEmpty());
    }

    // =====================================================================
    // Command tree traversal integration
    // =====================================================================

    @Test
    @DisplayName("TreeChoiceCommandPiece chains into ListChoiceCommandPiece")
    void treeChainsIntoListChoice() {
        var terminal = new CommandPiece(null, null);
        var listPiece = new ListChoiceCommandPiece(terminal, null, List.of("confirm", "cancel"));
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("select", listPiece);
        var input = new CommandInput();

        // Navigate: tree -> "select" -> listPiece
        var next = tree.getNextPiece("select");
        assertSame(listPiece, next);

        // ListPiece shows choices
        assertEquals(List.of("confirm", "cancel"), listPiece.getChoices(input));

        // Navigate: listPiece -> "confirm" -> terminal
        var afterChoice = listPiece.getNextPiece("confirm");
        assertSame(terminal, afterChoice);
    }

    @Test
    @DisplayName("TreeChoiceCommandPiece chains into DynamicListChoiceCommandPiece")
    void treeChainsIntoDynamicListChoice() {
        var terminal = new CommandPiece(null, null);
        Function<CommandInput, List<String>> supplier = input -> List.of("auto1", "auto2");
        var dynPiece = new DynamicListChoiceCommandPiece(terminal, null, supplier);
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("dynamic", dynPiece);
        var input = new CommandInput();

        // Navigate: tree -> "dynamic" -> dynPiece
        var next = tree.getNextPiece("dynamic");
        assertSame(dynPiece, next);

        // DynPiece shows dynamic choices
        assertEquals(2, dynPiece.getChoices(input).size());

        // Navigate: dynPiece -> "auto1" -> terminal
        assertSame(terminal, dynPiece.getNextPiece("auto1"));
    }

    @Test
    @DisplayName("Full tree traversal: root handler -> tree choice -> terminal handler")
    void fullTreeTraversal() {
        // Terminal handler that records invocation
        boolean[] terminalCalled = {false};
        Function<CommandInput, Boolean> terminalHandler = input -> {
            terminalCalled[0] = true;
            return true;
        };
        var terminal = new CommandPiece(null, terminalHandler);

        // Tree with one choice leading to terminal
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("action", terminal);

        var input = new CommandInput();

        // Traverse
        var piece = tree.getNextPiece("action");
        assertNotNull(piece);
        piece.execute(input);

        assertTrue(terminalCalled[0], "Terminal handler should have been invoked");
    }

    @Test
    @DisplayName("Deep chain: CommandPiece -> TreeChoice -> ListChoice -> terminal handler")
    void deepChainTraversal() {
        boolean[] called = {false};
        Function<CommandInput, Boolean> handler = input -> {
            called[0] = true;
            return true;
        };
        var terminal = new CommandPiece(null, handler);
        var listPiece = new ListChoiceCommandPiece(terminal, null, List.of("go"));
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("next", listPiece);
        var root = new CommandPiece(tree, null);
        var input = new CommandInput();

        // root.getNextPiece("anything") -> tree
        var step1 = root.getNextPiece("anything");
        assertSame(tree, step1);

        // tree.getNextPiece("next") -> listPiece
        var step2 = step1.getNextPiece("next");
        assertSame(listPiece, step2);

        // listPiece.getNextPiece("go") -> terminal
        var step3 = step2.getNextPiece("go");
        assertSame(terminal, step3);

        // Execute terminal handler
        assertTrue(step3.execute(input));
        assertTrue(called[0]);
    }

    @Test
    @DisplayName("CommandPiece handler returning false does not affect tree traversal")
    void handlerReturnValueDoesNotAffectTraversal() {
        Function<CommandInput, Boolean> failingHandler = input -> false;
        var terminal = new CommandPiece(null, failingHandler);
        var tree = new TreeChoiceCommandPiece(null);
        tree.addChoice("fail", terminal);
        var input = new CommandInput();

        var piece = tree.getNextPiece("fail");
        assertFalse(piece.execute(input));

        // Traversal still works after failed execution
        assertSame(terminal, tree.getNextPiece("fail"));
    }
}

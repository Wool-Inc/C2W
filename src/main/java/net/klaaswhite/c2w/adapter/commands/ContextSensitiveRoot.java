package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.commands.TreeChoiceCommandPiece;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * A command root whose available choices depend on runtime context (e.g. the
 * sender's current world). The actual dispatch is delegated to a
 * {@link TreeChoiceCommandPiece}; only the top-level choice list is dynamic.
 * <p>
 * Shared by {@code StructureCommand} and {@code LayoutEditorCommand} to avoid
 * duplicating the same delegating wrapper.
 */
public class ContextSensitiveRoot extends CommandPiece {

    private final TreeChoiceCommandPiece delegate;
    private final Function<CommandInput, List<String>> choicesProvider;

    public ContextSensitiveRoot(
            TreeChoiceCommandPiece delegate,
            Function<CommandInput, List<String>> choicesProvider
    ) {
        super(null, null);
        this.delegate = delegate;
        this.choicesProvider = choicesProvider;
    }

    @Override
    public List<String> getChoices(CommandInput input) {
        var choices = choicesProvider.apply(input);
        // A null result means "use the full delegate choice list" (e.g. for
        // non-player senders or when the world is unknown). This preserves the
        // original behaviour where the context-sensitive root fell back to the
        // underlying TreeChoiceCommandPiece's complete choice set.
        return choices != null ? choices : delegate.getChoices(input);
    }

    @Override
    @Nullable
    public CommandPiece getNextPiece(String choice) {
        return delegate.getNextPiece(choice);
    }
}

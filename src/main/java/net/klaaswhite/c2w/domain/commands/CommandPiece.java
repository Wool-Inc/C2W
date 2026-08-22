package net.klaaswhite.c2w.domain.commands;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Function;

public class CommandPiece {

    @Nullable private final CommandPiece nextPiece;
    @Nullable private final Function<CommandInput, Boolean> execute;

    public CommandPiece(@Nullable CommandPiece nextPiece, @Nullable Function<CommandInput, Boolean> execute){
        this.nextPiece = nextPiece;
        this.execute = execute;
    }

    public boolean execute(CommandInput input){
        if (execute == null) return false;

        return this.execute.apply(input);
    }

    /** Whether this piece is terminal (has an execute function). */
    public boolean isTerminal() {
        return execute != null;
    }

    public List<String> getChoices(CommandInput input){
        return List.of();
    }

    @Nullable
    public CommandPiece getNextPiece(String choice){
        return nextPiece;
    }
}

package net.klaaswhite.c2w.commands.CommandPieces;

import net.klaaswhite.c2w.commands.base.CommandInput;

import javax.annotation.Nullable;
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

    public List<String> getChoices(CommandInput input){
        return List.of();
    }

    @Nullable
    public CommandPiece getNextPiece(String choice){
        return nextPiece;
    }
}

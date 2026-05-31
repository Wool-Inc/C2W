package net.klaaswhite.c2w.commands.CommandPieces;

import net.klaaswhite.c2w.commands.base.CommandInput;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class ListChoiceCommandPiece extends CommandPiece {

    @Nullable private final List<String> choices;

    public ListChoiceCommandPiece(@Nullable CommandPiece nextPiece, @Nullable Function<CommandInput, Boolean> execute, List<String> choices) {
        super(nextPiece, execute);

        this.choices = choices;
    }

    @Override
    public List<String> getChoices(CommandInput input){
        return this.choices;
    }
}

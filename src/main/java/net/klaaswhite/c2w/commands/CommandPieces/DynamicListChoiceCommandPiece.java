package net.klaaswhite.c2w.commands.CommandPieces;

import net.klaaswhite.c2w.commands.base.BaseCommand;
import net.klaaswhite.c2w.commands.base.CommandInput;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class DynamicListChoiceCommandPiece extends CommandPiece {

    private final Function<CommandInput, List<String>> getChoices;

    public DynamicListChoiceCommandPiece(@Nullable CommandPiece nextPiece, @Nullable Function<CommandInput, Boolean> execute, Function<CommandInput, List<String>> getChoices) {
        super(nextPiece, execute);

        this.getChoices = getChoices;
    }

    @Override
    public List<String> getChoices(CommandInput input){
        return this.getChoices.apply(input);
    }
}

package net.klaaswhite.c2w.domain.commands;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

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

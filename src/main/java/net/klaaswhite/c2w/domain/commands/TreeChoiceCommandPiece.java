package net.klaaswhite.c2w.domain.commands;

import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class TreeChoiceCommandPiece extends CommandPiece {

    private final HashMap<String, CommandPiece> choices;

    public TreeChoiceCommandPiece(Function<CommandInput, Boolean> execute){
        super(null, execute);
        this.choices = new HashMap<>();
    }

    public void addChoice(String display, CommandPiece nextCommandPiece){
        choices.put(display, nextCommandPiece);
    }

    @Override
    public List<String> getChoices(CommandInput input){
        return choices.keySet().stream().toList();
    }

    @Override
    public @Nullable CommandPiece getNextPiece(String choice) {
        return choices.get(choice);
    }
}

package net.klaaswhite.c2w.commands.CommandPieces;

import net.klaaswhite.c2w.commands.base.CommandInput;

import javax.annotation.Nullable;
import java.util.Hashtable;
import java.util.List;
import java.util.function.Function;

public class TreeChoiceCommandPiece extends CommandPiece {

    private final Hashtable<String, CommandPiece> choices;

    public TreeChoiceCommandPiece(Function<CommandInput, Boolean> execute){
        super(null, execute);
        this.choices = new Hashtable<>();
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

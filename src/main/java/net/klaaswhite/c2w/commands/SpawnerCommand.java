package net.klaaswhite.c2w.commands;

import net.klaaswhite.c2w.commands.CommandPieces.CommandPiece;
import net.klaaswhite.c2w.commands.CommandPieces.TreeChoiceCommandPiece;
import net.klaaswhite.c2w.commands.base.BaseCommand;
import net.klaaswhite.c2w.commands.base.CommandInput;
import net.klaaswhite.c2w.managers.Managers;
import net.klaaswhite.c2w.managers.SpawnerManager;

import java.util.Arrays;
import java.util.List;

public class SpawnerCommand extends BaseCommand {

    private final SpawnerManager spawnerManager;

    public SpawnerCommand(Managers managers) {
        this.spawnerManager = managers.get(SpawnerManager.class).getValue();
        super(managers);
    }

    @Override
    protected void createCommandChain() {
        var setSpecificDistanceCommand = new CommandPiece(null, this::setDistance);
        var setDistanceCommand = new CommandPiece(setSpecificDistanceCommand, null);

        var spawnerCommand = new TreeChoiceCommandPiece(null);
        spawnerCommand.addChoice("setdistance", setDistanceCommand);

        initialCommandPiece = spawnerCommand;
    }

    @Override
    protected String getCommandName() {
        return "spawnertimer";
    }

    private boolean setDistance(CommandInput commandInput){
        if (commandInput.strings.length != 2)
            return false;
        var distance = Integer.parseInt(commandInput.strings[1]);
        this.spawnerManager.setDistance(commandInput, distance);

        return true;
    }
}

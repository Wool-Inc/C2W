package net.klaaswhite.c2w.commands;

import net.klaaswhite.c2w.commands.CommandPieces.CommandPiece;
import net.klaaswhite.c2w.commands.CommandPieces.TreeChoiceCommandPiece;
import net.klaaswhite.c2w.commands.base.BaseCommand;
import net.klaaswhite.c2w.commands.base.CommandInput;
import net.klaaswhite.c2w.managers.Managers;
import net.klaaswhite.c2w.managers.MarkerManager;
import net.klaaswhite.c2w.managers.WorldManager;
import org.bukkit.entity.Player;

import java.util.List;

public class WorldCommand extends BaseCommand {

    private final WorldManager worldManager;

    public WorldCommand(Managers managers) {
        worldManager = managers.get(WorldManager.class).getValue();
        super(managers);
    }

    @Override
    protected void createCommandChain() {

        var testWorldCommand = new CommandPiece(null, this::teleportTestWorld);

        var teleportCommand = new TreeChoiceCommandPiece(null);
        teleportCommand.addChoice("testworld", testWorldCommand);

        var worldCommand = new TreeChoiceCommandPiece(null);
        worldCommand.addChoice("teleport", teleportCommand);

        initialCommandPiece = worldCommand;
    }

    @Override
    protected String getCommandName() {
        return "world";
    }

    private boolean teleportTestWorld(CommandInput input){
        if (!(input.commandSender instanceof Player player))
            return false;

        var testWorld = this.worldManager.getTestWorld();
        player.teleport(testWorld.getWorld().getSpawnLocation());

        return true;
    }
}

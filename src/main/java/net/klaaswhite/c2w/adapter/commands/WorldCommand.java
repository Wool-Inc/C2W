package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.commands.ListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.TreeChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.adapter.managers.WorldManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class WorldCommand extends BaseCommand {

    private final WorldManager worldManager;

    public WorldCommand(JavaPlugin plugin, WorldManager worldManager) {
        super(plugin);
        this.worldManager = worldManager;
        createCommandChain();
        register();
    }

    @Override
    protected void createCommandChain() {
        var worldOptions = new ArrayList<String>();
        worldOptions.add("lobby");
        worldOptions.add("reference");
        worldOptions.add("draft");
        worldOptions.add("game");

        var teleportHandler = new CommandPiece(null, this::teleportTo);
        var teleportChoice = new ListChoiceCommandPiece(teleportHandler, null, worldOptions);

        var worldCommand = new TreeChoiceCommandPiece(null);
        worldCommand.addChoice("teleport", teleportChoice);

        initialCommandPiece = worldCommand;
    }

    @Override
    protected String getCommandName() {
        return "world";
    }

    private boolean checkAdmin(CommandInput input, org.bukkit.command.CommandSender sender) {
        if (!sender.hasPermission("c2w.admin")) {
            sender.sendMessage("You don't have permission.");
            return true;
        }
        return false;
    }

    private boolean teleportTo(CommandInput input) {
        if (!(input.commandSender instanceof org.bukkit.command.CommandSender s))
            return false;
        if (checkAdmin(input, s)) return true;
        if (!(input.commandSender instanceof Player player))
            return false;
        if (input.strings.length < 2) return false;

        var target = input.strings[1];
        var managed = switch (target) {
            case "lobby" -> worldManager.getLobbyWorld();
            case "reference" -> worldManager.getReferenceWorld();
            case "draft" -> worldManager.getDraftWorld();
            case "game" -> worldManager.getGameWorld();
            default -> null;
        };
        if (managed == null) {
            player.sendMessage("Unknown world: " + target);
            return false;
        }
        var w = managed.getWorld();
        if (w == null) {
            player.sendMessage("World '" + target + "' is not loaded.");
            return false;
        }
        player.teleport(w.getSpawnLocation());
        return true;
    }
}

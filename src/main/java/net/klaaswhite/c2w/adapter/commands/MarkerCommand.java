package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.commands.DynamicListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.TreeChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.adapter.managers.MarkerManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class MarkerCommand extends BaseCommand {
    private final MarkerManager markerManager;

    public MarkerCommand(JavaPlugin plugin, MarkerManager markerManager) {
        super(plugin);
        this.markerManager = markerManager;
        createCommandChain();
        register();
    }

    @Override
    protected void createCommandChain() {

        var createAtCommand = new TreeChoiceCommandPiece(null);
        var createCommand = new CommandPiece(null, this::createMarker);
        var createNameCommand = new DynamicListChoiceCommandPiece(createCommand, null, this::getDynamicMarkerSuggestions);
        createAtCommand.addChoice("player", createNameCommand);
        createAtCommand.addChoice("looking", createNameCommand);

        var removeSpecificCommand = new CommandPiece(null, this::removeMarker);
        var removeCommand = new DynamicListChoiceCommandPiece(removeSpecificCommand, null, this::getMarkers);

        var listCommand = new CommandPiece(null, this::listMarkers);

        var markerCommand = new TreeChoiceCommandPiece(null);
        markerCommand.addChoice("create", createAtCommand);
        markerCommand.addChoice("remove", removeCommand);
        markerCommand.addChoice("list", listCommand);

        initialCommandPiece = markerCommand;
    }

    @Override
    protected String getCommandName() {
        return "marker";
    }

    private boolean checkAdmin(CommandInput input, org.bukkit.command.CommandSender sender) {
        if (!sender.hasPermission("c2w.admin")) {
            sender.sendMessage("You don't have permission.");
            return true;
        }
        return false;
    }

    private boolean createMarker(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s))
            return false;
        if (checkAdmin(commandInput, s)) return true;
        if (commandInput.strings.length < 3) {
            s.sendMessage("§cUsage: /marker create <player|looking> <marker-name>");
            s.sendMessage("§eExamples:");
            s.sendMessage("  §7/marker create looking wool §f- Create wool spawn marker (colors assigned on game start)");
            s.sendMessage("  §7/marker create looking cap-red §f- Create capture point marker");
            s.sendMessage("  §7/marker create looking spawnpoint §f- Create generic spawn point (team determined by layout)");
            s.sendMessage("  §7/marker create looking boundary-woolcap-pit-1 §f- Create pit boundary");
            s.sendMessage("§eMarker names: wool, cap-<color>, spawnpoint, boundary-woolcap-pit-<1|2>, boundary-woolcap-elevator-<1|2>");
            return false;
        }

        if (!(commandInput.commandSender instanceof Player player))
            return false;

        this.markerManager.createMarker(player, commandInput.strings[1], commandInput.strings[2]);
        player.sendMessage("§aMarker '" + commandInput.strings[2] + "' created at your " + commandInput.strings[1] + " position.");

        return true;
    }

    private List<String> getMarkers(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof Player player))
            return List.of();

        return this.markerManager.getMarkersInWorld(player);
    }

    private List<String> getDynamicMarkerSuggestions(CommandInput commandInput) {
        String current = commandInput.strings.length > 0
                ? commandInput.strings[commandInput.strings.length - 1]
                : "";

        List<String> suggestions = new ArrayList<>(MarkerManager.MARKER_NAMES);
        suggestions.add("spawnpoint");
        suggestions.add("structure-");
        suggestions.add("resourcespot-");
        return suggestions;
    }

    private boolean listMarkers(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s))
            return false;
        if (checkAdmin(commandInput, s)) return true;

        var markers = getMarkers(commandInput);
        s.sendMessage("§eMarkers in this world (" + markers.size() + "):");
        if (markers.isEmpty()) {
            s.sendMessage("  §7No markers found. Use §f/marker create looking <name> §7to create one.");
            s.sendMessage("§eMarker types: wool, cap-<color>, spawnpoint, boundary-woolcap-pit-<1|2>, boundary-woolcap-elevator-<1|2>");
        } else {
            for (String marker : markers) {
                s.sendMessage("  §7- §f" + marker);
            }
        }
        return true;
    }

    private boolean removeMarker(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s))
            return false;
        if (checkAdmin(commandInput, s)) return true;
        if (commandInput.strings.length < 2) {
            s.sendMessage("§cUsage: /marker remove <marker-name>");
            s.sendMessage("§eUse §7/marker list §eto see all markers in your world.");
            return false;
        }

        if (!(commandInput.commandSender instanceof Player player))
            return false;

        if (this.markerManager.removeMarker(player, commandInput.strings[1])) {
            if (commandInput.commandSender instanceof org.bukkit.command.CommandSender sender) {
                sender.sendMessage("§aMarker '" + commandInput.strings[1] + "' was removed.");
            }
        } else {
            if (commandInput.commandSender instanceof org.bukkit.command.CommandSender sender) {
                sender.sendMessage("§cMarker '" + commandInput.strings[1] + "' was not found.");
            }
        }

        return true;
    }
}

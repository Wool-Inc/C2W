package net.klaaswhite.c2w.commands;

import net.klaaswhite.c2w.commands.CommandPieces.CommandPiece;
import net.klaaswhite.c2w.commands.CommandPieces.DynamicListChoiceCommandPiece;
import net.klaaswhite.c2w.commands.CommandPieces.ListChoiceCommandPiece;
import net.klaaswhite.c2w.commands.CommandPieces.TreeChoiceCommandPiece;
import net.klaaswhite.c2w.commands.base.BaseCommand;
import net.klaaswhite.c2w.commands.base.CommandInput;
import net.klaaswhite.c2w.managers.Managers;
import net.klaaswhite.c2w.managers.MarkerManager;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class MarkerCommand extends BaseCommand {
    private final MarkerManager markerManager;

    public MarkerCommand(Managers managers) {
        markerManager = managers.get(MarkerManager.class).getValue();
        super(managers);
    }

    @Override
    protected void createCommandChain() {

        var createAtCommand = new TreeChoiceCommandPiece(null);
        var createCommand = new CommandPiece(null, this::createMarker);
        var createNameCommand = new ListChoiceCommandPiece(createCommand, null, getKnownMarkers());
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

    private List<String> getCreateOptions(CommandInput commandInput) {
        return Arrays.stream((new String[]{"player", "looking"})).toList();
    }

    private boolean createMarker(CommandInput commandInput) {
        if (commandInput.strings.length < 3) return false;

        if (!(commandInput.commandSender instanceof Player player))
            return false;

        this.markerManager.createMarker(player, commandInput.strings[1], commandInput.strings[2]);

        return true;
    }

    private List<String> getMarkers(CommandInput commandInput){
        if (!(commandInput.commandSender instanceof Player player))
            return List.of();

        return this.markerManager.getMarkersInWorld(player);
    }

    private List<String> getKnownMarkers(){
        return MarkerManager.getKnownMarkers().keySet().stream().toList();
    }

    private boolean listMarkers(CommandInput commandInput){
        var markers = getMarkers(commandInput);
        commandInput.commandSender.sendMessage(String.join("|", markers));
        return true;
    }

    private boolean removeMarker(CommandInput commandInput){
        if (commandInput.strings.length < 2) return false;

        if (!(commandInput.commandSender instanceof Player player))
            return false;

        if (this.markerManager.removeMarker(player, commandInput.strings[1]))
            commandInput.commandSender.sendMessage("Marker " + commandInput.strings[1] + " was removed.");
        else
            commandInput.commandSender.sendMessage("Marker " + commandInput.strings[1] + " was not removed.");

        return true;
    }
}

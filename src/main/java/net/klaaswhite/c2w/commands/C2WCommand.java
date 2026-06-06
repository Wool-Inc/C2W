package net.klaaswhite.c2w.commands;

import net.klaaswhite.c2w.classes.WoolTimer;
import net.klaaswhite.c2w.commands.CommandPieces.CommandPiece;
import net.klaaswhite.c2w.commands.CommandPieces.ListChoiceCommandPiece;
import net.klaaswhite.c2w.commands.CommandPieces.TreeChoiceCommandPiece;
import net.klaaswhite.c2w.commands.base.BaseCommand;
import net.klaaswhite.c2w.commands.base.CommandInput;
import net.klaaswhite.c2w.managers.EventManager;
import net.klaaswhite.c2w.managers.GameManager;
import net.klaaswhite.c2w.managers.Managers;
import net.klaaswhite.c2w.managers.MarkerManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class C2WCommand extends BaseCommand {
    private final GameManager gameManager;
    private final MarkerManager markerManager;

    @Override
    protected void createCommandChain() {
        var initCommand = new CommandPiece(null, this::init);
        var ensureEntityWools = new CommandPiece(null, this::ensureEntityWools);
        var previewCommand = new CommandPiece(null, this::preview);
        var startCommand = new CommandPiece(null, this::start);
        var endCommand = new CommandPiece(null, this::end);
        var reloadCommand = new CommandPiece(null, this::reload);

        var setWoolTimerCommand = new CommandPiece(null, this::setWoolTimer);
        var options = new ArrayList<String>();
        options.add("interval");
        options.add("basecapture");
        options.add("increaseperplayer");
        options.add("decreateperplayer");
        options.add("decreaseoutsidearea");
        var setWoolTimerListCommand = new ListChoiceCommandPiece(setWoolTimerCommand, null, options);

        var getWoolTimerCommand = new CommandPiece(null, this::getWoolTimer);

        var c2wCommand = new TreeChoiceCommandPiece(null);
        c2wCommand.addChoice("init", initCommand);
        c2wCommand.addChoice("ensurewools", ensureEntityWools);
        c2wCommand.addChoice("preview", previewCommand);
        c2wCommand.addChoice("start", startCommand);
        c2wCommand.addChoice("end", endCommand);
        c2wCommand.addChoice("reload", reloadCommand);
        c2wCommand.addChoice("setwooltimer", setWoolTimerListCommand);
        c2wCommand.addChoice("getwooltimer", getWoolTimerCommand);

        initialCommandPiece = c2wCommand;
    }

    public C2WCommand(Managers managers){
        this.gameManager = managers.get(GameManager.class).getValue();
        this.markerManager = managers.get(MarkerManager.class).getValue();
        super(managers);
    }

    public boolean init(CommandInput commandInput){
        this.gameManager.init(commandInput);
        return true;
    }

    public boolean ensureEntityWools(CommandInput commandInput){
        this.markerManager.ensureEntityWools();
        return true;
    }

    public boolean preview(CommandInput commandInput){
        this.gameManager.preview(commandInput);
        return true;
    }

    public boolean start(CommandInput commandInput){
        this.gameManager.start(commandInput);
        return true;
    }

    public boolean end(CommandInput commandInput){
        this.gameManager.end(commandInput);
        return true;
    }

    public boolean reload(CommandInput commandInput){
        this.managers.reload();
        return true;
    }

    public boolean setWoolTimer(CommandInput commandInput){
        if (commandInput.strings.length != 3)
            return false;

        var setting = commandInput.strings[1];
        var value = Integer.parseInt(commandInput.strings[2]);

        switch (setting) {
            case "interval":
                WoolTimer.setInterval(value);
                break;
            case "basecapture":
                WoolTimer.setBaseCapture(value);
                break;
            case "increaseperplayer":
                WoolTimer.setIncreasePerPlayer(value);
                break;
            case "decreateperplayer":
                WoolTimer.setDecreasePerPlayer(value);
                break;
            case "decreaseoutsidearea":
                WoolTimer.setDecreaseOutsideArea(value);
                break;
        }
        return true;
    }

    public boolean getWoolTimer(CommandInput commandInput){
        if (!(commandInput.commandSender instanceof Player player))
            return false;

        player.sendMessage("Current values of the wool timer: ");
        player.sendMessage("Interval: " + WoolTimer.interval.get());
        player.sendMessage("basecapture: " + WoolTimer.baseCapture.get());
        player.sendMessage("increaseperplayer: " + WoolTimer.increasePerPlayer.get());
        player.sendMessage("decreateperplayer: " + WoolTimer.decreasePerPlayer.get());
        player.sendMessage("decreaseoutsidearea: " + WoolTimer.decreaseOutsideArea.get());

        return true;
    }

    @Override
    protected String getCommandName() {
        return "c2w";
    }
}

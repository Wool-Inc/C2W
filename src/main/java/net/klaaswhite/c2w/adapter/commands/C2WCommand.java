package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.commands.DynamicListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.ListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.TreeChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.domain.game.GameStateMachine;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.adapter.managers.GameManager;
import net.klaaswhite.c2w.adapter.managers.MarkerManager;
import net.klaaswhite.c2w.adapter.managers.PlayerManager;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class C2WCommand extends BaseCommand {
    private final GameManager gameManager;
    private final MarkerManager markerManager;
    private final LayoutManager layoutManager;
    private final PlayerManager playerManager;
    private final Consumer<Void> onReset;
    private final WoolTimer woolTimer;
    private final MinecraftManager mc;

    public C2WCommand(
            JavaPlugin plugin,
            GameManager gameManager,
            MarkerManager markerManager,
            LayoutManager layoutManager,
            PlayerManager playerManager,
            Consumer<Void> onReset,
            WoolTimer woolTimer,
            MinecraftManager mc
    ) {
        super(plugin);
        this.gameManager = gameManager;
        this.markerManager = markerManager;
        this.layoutManager = layoutManager;
        this.playerManager = playerManager;
        this.onReset = onReset;
        this.woolTimer = woolTimer;
        this.mc = mc;
        createCommandChain();
        register();
    }

    @Override
    protected void createCommandChain() {
        var initCommand = new CommandPiece(null, this::init);
        var ensureEntityWools = new CommandPiece(null, this::ensureEntityWools);
        var previewCommand = new CommandPiece(null, this::preview);
        var startLayoutHandler = new CommandPiece(null, this::startWithLayout);
        var startLayoutChoice = new DynamicListChoiceCommandPiece(
                startLayoutHandler, null, this::availableLayoutNames);
        var startCommand = new TreeChoiceCommandPiece(this::startDefault);
        startCommand.addChoice("layout", startLayoutChoice);
        var endCommand = new CommandPiece(null, this::end);
        var resetCommand = new CommandPiece(null, this::reset);
        var reloadCommand = new CommandPiece(null, this::reload);

        var setWoolTimerCommand = new CommandPiece(null, this::setWoolTimer);
        var setWoolTimerValue = new CommandPiece(setWoolTimerCommand, null);
        var options = new ArrayList<String>();
        options.add("interval");
        options.add("basecapture");
        options.add("increaseperplayer");
        options.add("decreaseperplayer");
        options.add("decreaseoutsidearea");
        var setWoolTimerListCommand = new ListChoiceCommandPiece(setWoolTimerValue, null, options);

        var getWoolTimerCommand = new CommandPiece(null, this::getWoolTimer);

        var layoutListCommand = new CommandPiece(null, this::layoutList);
        var layoutInfoHandler = new CommandPiece(null, this::layoutInfo);
        var layoutInfoCommand = new DynamicListChoiceCommandPiece(
                layoutInfoHandler, null, this::availableLayoutNames);
        var layoutSelectHandler = new CommandPiece(null, this::layoutSelect);
        var layoutSelectCommand = new DynamicListChoiceCommandPiece(
                layoutSelectHandler, null, this::availableLayoutNames);
        var layoutCommand = new TreeChoiceCommandPiece(null);
        layoutCommand.addChoice("list", layoutListCommand);
        layoutCommand.addChoice("info", layoutInfoCommand);
        layoutCommand.addChoice("select", layoutSelectCommand);

        var teamJoinHandler = new CommandPiece(null, this::teamJoin);
        var teamJoinTeamChoice = new ListChoiceCommandPiece(
                teamJoinHandler, null, java.util.List.of("Red", "Blue", "Spectator"));
        var teamJoinPlayerChoice = new DynamicListChoiceCommandPiece(
                teamJoinTeamChoice, null, this::onlinePlayerNames);
        var teamLeaveHandler = new CommandPiece(null, this::teamLeave);
        var teamLeavePlayerChoice = new DynamicListChoiceCommandPiece(
                teamLeaveHandler, null, this::onlinePlayerNames);
        var teamListCommand = new CommandPiece(null, this::teamList);
        var teamAutoCommand = new CommandPiece(null, this::teamAuto);
        var teamCommand = new TreeChoiceCommandPiece(null);
        teamCommand.addChoice("join", teamJoinPlayerChoice);
        teamCommand.addChoice("leave", teamLeavePlayerChoice);
        teamCommand.addChoice("list", teamListCommand);
        teamCommand.addChoice("auto", teamAutoCommand);

        var statusCommand = new CommandPiece(null, this::status);


        var c2wCommand = new TreeChoiceCommandPiece(null);
        c2wCommand.addChoice("init", initCommand);
        c2wCommand.addChoice("ensurewools", ensureEntityWools);
        c2wCommand.addChoice("preview", previewCommand);
        c2wCommand.addChoice("start", startCommand);
        c2wCommand.addChoice("end", endCommand);
        c2wCommand.addChoice("reset", resetCommand);
        c2wCommand.addChoice("reload", reloadCommand);
        c2wCommand.addChoice("setwooltimer", setWoolTimerListCommand);
        c2wCommand.addChoice("getwooltimer", getWoolTimerCommand);
        c2wCommand.addChoice("layout", layoutCommand);
        c2wCommand.addChoice("team", teamCommand);
        c2wCommand.addChoice("status", statusCommand);

        initialCommandPiece = new ContextSensitiveRoot(c2wCommand, this::contextChoices);
    }

    private boolean checkAdmin(CommandInput input, org.bukkit.command.CommandSender sender) {
        if (!sender.hasPermission("c2w.admin")) {
            sender.sendMessage("You don't have permission.");
            return true;
        }
        return false;
    }

    private List<String> contextChoices(CommandInput input) {
        // Console / non-player senders: show full choice set
        if (!(input.commandSender instanceof Player player)) {
            return null;
        }

        // Non-admin: only status is meaningful (all other /c2w commands require admin)
        if (!player.hasPermission("c2w.admin")) {
            return java.util.List.of("status");
        }

        var state = gameManager.getState();

        // Commands available to admins regardless of state
        var choices = new ArrayList<>(java.util.List.of("status", "reset", "reload", "setwooltimer", "getwooltimer"));

        switch (state) {
            case NOT_STARTED -> {
                choices.add("init");
            }
            case DRAFT_CREATED -> {
                choices.add("start");
                choices.add("layout");
                choices.add("team");
                choices.add("preview");
                choices.add("ensurewools");
            }
            case GAME_IN_PROGRESS -> {
                choices.add("end");
                choices.add("team");
            }
            case GAME_ENDED -> {
                // Only reset/status — already covered by the defaults above
            }
        }

        return choices;
    }

    public boolean init(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        this.gameManager.init(commandInput);
        return true;
    }

    public boolean ensureEntityWools(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        this.markerManager.ensureEntityWools();
        s.sendMessage("Entity wools ensured.");
        return true;
    }

    public boolean preview(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        this.gameManager.preview(commandInput);
        s.sendMessage("Preview requested.");
        return true;
    }

    public boolean startDefault(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        this.gameManager.start(commandInput, null);
        return true;
    }

    public boolean startWithLayout(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (commandInput.strings.length < 3) {
            this.gameManager.start(commandInput, null);
            return true;
        }
        this.gameManager.start(commandInput, commandInput.strings[2]);
        return true;
    }

    public boolean end(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        this.gameManager.end(commandInput);
        return true;
    }

    public boolean reset(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        s.sendMessage("Resetting plugin state...");
        this.onReset.accept(null);
        s.sendMessage("Reset complete. You are in the lobby world.");
        return true;
    }

    public boolean reload(CommandInput commandInput) {
        if (commandInput.commandSender instanceof org.bukkit.command.CommandSender sender) {
            sender.sendMessage("'reload' is an alias for 'reset' in this build.");
        }
        return reset(commandInput);
    }

    public boolean setWoolTimer(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (commandInput.strings.length != 3)
            return false;

        var setting = commandInput.strings[1];
        int value;
        try {
            value = Integer.parseInt(commandInput.strings[2]);
        } catch (NumberFormatException e) {
            s.sendMessage("Value must be an integer.");
            return false;
        }

        switch (setting) {
            case "interval":
                woolTimer.setInterval(value);
                break;
            case "basecapture":
                woolTimer.setBaseCapture(value);
                break;
            case "increaseperplayer":
                woolTimer.setIncreasePerPlayer(value);
                break;
            case "decreaseperplayer":
                woolTimer.setDecreasePerPlayer(value);
                break;
            case "decreaseoutsidearea":
                woolTimer.setDecreaseOutsideArea(value);
                break;
        }
        s.sendMessage("Wool timer '" + setting + "' set to " + value + ".");
        return true;
    }

    public boolean getWoolTimer(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (!(commandInput.commandSender instanceof Player player))
            return false;

        player.sendMessage("Current values of the wool timer: ");
        player.sendMessage("Interval: " + woolTimer.getInterval());
        player.sendMessage("basecapture: " + woolTimer.getBaseCapture());
        player.sendMessage("increaseperplayer: " + woolTimer.getIncreasePerPlayer());
        player.sendMessage("decreaseperplayer: " + woolTimer.getDecreasePerPlayer());
        player.sendMessage("decreaseoutsidearea: " + woolTimer.getDecreaseOutsideArea());

        return true;
    }

    public boolean layoutList(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (!(commandInput.commandSender instanceof Player player))
            return false;
        var names = layoutManager.getLayoutNames();
        player.sendMessage("Configured layouts (" + names.size() + "):");
        for (var name : names) {
            player.sendMessage("  - " + name);
        }
        return true;
    }

    public boolean layoutInfo(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (!(commandInput.commandSender instanceof Player player))
            return false;
        if (commandInput.strings.length < 3) {
            player.sendMessage("Usage: /c2w layout info <name>");
            return false;
        }
        var name = commandInput.strings[2];
        var layout = layoutManager.getLayout(name);
        if (layout == null) {
            player.sendMessage("Layout '" + name + "' not found.");
            return false;
        }
        player.sendMessage("Layout: " + layout.getName());
        player.sendMessage("  tile: " + layout.getTileWidth() + "x" + layout.getTileHeight() + "x" + layout.getTileDepth());
        player.sendMessage("  origin: " + layout.getOrigin());
        player.sendMessage("  cells: " + layout.getCells().size() + " (grid " + layout.getRows() + "x" + layout.getCols() + ")");
        for (var cell : layout.getCells()) {
            player.sendMessage("    [" + cell.row() + "," + cell.col() + "] " + cell.typeName()
                    + " -> " + cell.worldPosition());
        }
        return true;
    }

    public boolean layoutSelect(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (commandInput.strings.length < 3) {
            s.sendMessage("Usage: /c2w layout select <layoutname>");
            return true;
        }
        this.gameManager.selectLayout(commandInput, commandInput.strings[2]);
        return true;
    }

    public boolean status(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (!(commandInput.commandSender instanceof Player player))
            return false;

        var state = gameManager.getState();
        player.sendMessage("=== C2W Status ===");
        player.sendMessage("State: " + state);

        var selectedLayout = gameManager.getSelectedLayoutName();
        if (selectedLayout != null) {
            player.sendMessage("Selected layout: " + selectedLayout);
        } else {
            player.sendMessage("Selected layout: (none - will auto-select on start)");
        }

        if (state == GameStateMachine.State.GAME_IN_PROGRESS || state == GameStateMachine.State.GAME_ENDED) {
            player.sendMessage("Wools captured:");
            player.sendMessage("  Red: " + gameManager.getWoolCount("Red"));
            player.sendMessage("  Blue: " + gameManager.getWoolCount("Blue"));
        }

        return true;
    }

    public boolean teamJoin(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (!(commandInput.commandSender instanceof Player player))
            return false;
        if (commandInput.strings.length < 4) {
            player.sendMessage("Usage: /c2w team join <player> <team>");
            return false;
        }
        var targetName = commandInput.strings[2];
        var teamName = commandInput.strings[3];
        var team = ManagedTeam.teams.get(teamName);
        if (team == null) {
            player.sendMessage("Team '" + teamName + "' not found. Available teams: Red, Blue, Spectator");
            return false;
        }
        var target = playerManager.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("Player '" + targetName + "' not found.");
            return false;
        }
        target.setTeam(team);
        player.sendMessage("Added " + targetName + " to " + teamName + " team.");
        return true;
    }

    public boolean teamLeave(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (!(commandInput.commandSender instanceof Player player))
            return false;
        if (commandInput.strings.length < 3) {
            player.sendMessage("Usage: /c2w team leave <player>");
            return false;
        }
        var targetName = commandInput.strings[2];
        var target = playerManager.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("Player '" + targetName + "' not found.");
            return false;
        }
        var specTeam = ManagedTeam.teams.get(PlayerManager.SPECTATOR_TEAM_NAME);
        if (specTeam != null) {
            target.setTeam(specTeam);
        } else {
            target.removeTeam(target.getTeam());
        }
        player.sendMessage("Moved " + targetName + " to Spectator.");
        return true;
    }

    public boolean teamList(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (!(commandInput.commandSender instanceof Player player))
            return false;
        player.sendMessage("=== Team Rosters ===");
        ManagedTeam.teams.forEach((name, team) -> {
            var names = new ArrayList<String>();
            team.players.forEach(mp -> names.add(mp.getPlayer().getDisplayName()));
            player.sendMessage(team.color + " " + name + " (" + names.size() + "): " + String.join(", ", names));
        });
        return true;
    }

    public boolean teamAuto(CommandInput commandInput) {
        if (!(commandInput.commandSender instanceof org.bukkit.command.CommandSender s)) return false;
        if (checkAdmin(commandInput, s)) return true;
        if (!(commandInput.commandSender instanceof Player player))
            return false;

        var registry = playerManager.getPlayerRegistry();
        registry.balanceTeams();

        var redCount = registry.getPlayerCountByTeam("Red");
        var blueCount = registry.getPlayerCountByTeam("Blue");
        player.sendMessage("Auto-balanced " + (redCount + blueCount) + " players. Red: " + redCount + ", Blue: " + blueCount);
        return true;
    }

    public java.util.List<String> availableLayoutNames(CommandInput commandInput) {
        return layoutManager.getLayoutNames();
    }

    public java.util.List<String> onlinePlayerNames(CommandInput commandInput) {
        return mc.server().getOnlinePlayerNames();
    }

    @Override
    protected String getCommandName() {
        return "c2w";
    }
}

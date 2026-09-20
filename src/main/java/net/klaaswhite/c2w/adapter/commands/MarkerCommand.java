package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.adapter.managers.MarkerManager;
import net.klaaswhite.c2w.adapter.managers.StructureCreationManager;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.commands.DynamicListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.TreeChoiceCommandPiece;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class MarkerCommand extends BaseCommand {
    private final StructureCreationManager creationManager;
    private final FolderStructureTypeConfig typeConfig;

    public MarkerCommand(
            JavaPlugin plugin,
            StructureCreationManager creationManager,
            FolderStructureTypeConfig typeConfig
    ) {
        super(plugin);
        this.creationManager = creationManager;
        this.typeConfig = typeConfig;
        createCommandChain();
        register();
    }

    @Override
    protected void createCommandChain() {
        var markerActions = new TreeChoiceCommandPiece(null);
        markerActions.addChoice("placelooking", markerNameTree(this::placeLooking));
        markerActions.addChoice("placehere", markerNameTree(this::placeHere));

        initialCommandPiece = new MarkerRoot(
                markerActions,
                new CommandPiece(null, this::removeLooking),
                new CommandPiece(null, this::removeHere));
    }

    @Override
    protected String getCommandName() {
        return "marker";
    }

    private boolean checkContext(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;
        if (!p.hasPermission("c2w.admin")) {
            p.sendMessage("You don't have permission.");
            return false;
        }
        if (creationManager.getCreationSession(p.getWorld().getName()) == null) {
            p.sendMessage("This command can only be used in a structure world.");
            return false;
        }
        return true;
    }

    private boolean placeLooking(CommandInput input) {
        if (!checkContext(input)) return false;
        return creationManager.placeGameMarker((Player) input.commandSender, input.strings[1]);
    }

    private boolean placeHere(CommandInput input) {
        if (!checkContext(input)) return false;
        return creationManager.placeGameMarkerHere((Player) input.commandSender, input.strings[1]);
    }

    private boolean removeLooking(CommandInput input) {
        if (!checkContext(input)) return false;
        return creationManager.removeGameMarkerLooking((Player) input.commandSender);
    }

    private boolean removeHere(CommandInput input) {
        if (!checkContext(input)) return false;
        return creationManager.removeGameMarkerHere((Player) input.commandSender);
    }

    private List<String> markerSuggestions(CommandInput input) {
        if (!(input.commandSender instanceof Player p)
                || creationManager.getCreationSession(p.getWorld().getName()) == null) {
            return List.of();
        }
        var suggestions = new ArrayList<>(MarkerManager.MARKER_NAMES);
        if (!suggestions.contains("spawnpoint")) suggestions.add("spawnpoint");
        for (String typeName : typeConfig.getTypeNames()) {
            for (String resourceId : typeConfig.getResourceIds(typeName)) {
                if (!suggestions.contains(resourceId)) suggestions.add(resourceId);
            }
            for (String resourceId : typeConfig.getTrialResourceIds(typeName)) {
                addIfMissing(suggestions, "trial-spawner-" + resourceId);
                addIfMissing(suggestions, "trial-vault-" + resourceId);
            }
        }
        return suggestions;
    }

    private CommandPiece markerNameTree(java.util.function.Function<CommandInput, Boolean> handler) {
        return new DynamicListChoiceCommandPiece(
                new CommandPiece(null, handler), null, this::markerSuggestions);
    }

    private static void addIfMissing(List<String> suggestions, String value) {
        if (!suggestions.contains(value)) suggestions.add(value);
    }

    private final class MarkerRoot extends CommandPiece {
        private final TreeChoiceCommandPiece placementActions;
        private final CommandPiece removeLooking;
        private final CommandPiece removeHere;

        private MarkerRoot(TreeChoiceCommandPiece placementActions,
                           CommandPiece removeLooking,
                           CommandPiece removeHere) {
            super(null, null);
            this.placementActions = placementActions;
            this.removeLooking = removeLooking;
            this.removeHere = removeHere;
        }

        @Override
        public List<String> getChoices(CommandInput input) {
            if (!(input.commandSender instanceof Player p)
                    || creationManager.getCreationSession(p.getWorld().getName()) == null) {
                return List.of();
            }
            return List.of("removelooking", "removehere", "placehere", "placelooking");
        }

        @Override
        public CommandPiece getNextPiece(String choice) {
            if ("removelooking".equalsIgnoreCase(choice)) return removeLooking;
            if ("removehere".equalsIgnoreCase(choice)) return removeHere;
            return placementActions.getNextPiece(choice);
        }
    }
}

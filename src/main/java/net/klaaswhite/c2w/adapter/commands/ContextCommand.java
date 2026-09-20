package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.adapter.managers.GameManager;
import net.klaaswhite.c2w.adapter.managers.LayoutEditorManager;
import net.klaaswhite.c2w.adapter.managers.ResourceManager;
import net.klaaswhite.c2w.adapter.managers.StructureCreationManager;
import net.klaaswhite.c2w.adapter.managers.WorldManager;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.commands.DynamicListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.ListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.TreeChoiceCommandPiece;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Implements the command roots whose availability is determined by world context. */
public class ContextCommand extends BaseCommand {

    private final String commandName;
    private final GameManager gameManager;
    private final StructureCreationManager creationManager;
    private final ResourceManager resourceManager;
    private final LayoutEditorManager layoutEditorManager;
    private final FolderStructureTypeConfig typeConfig;
    private final StructureManager structureManager;
    private final LayoutManager layoutManager;
    private final WorldManager worldManager;

    public ContextCommand(
            JavaPlugin plugin,
            String commandName,
            GameManager gameManager,
            StructureCreationManager creationManager,
            ResourceManager resourceManager,
            LayoutEditorManager layoutEditorManager,
            FolderStructureTypeConfig typeConfig,
            StructureManager structureManager,
            LayoutManager layoutManager,
            WorldManager worldManager
    ) {
        super(plugin);
        this.commandName = commandName;
        this.gameManager = gameManager;
        this.creationManager = creationManager;
        this.resourceManager = resourceManager;
        this.layoutEditorManager = layoutEditorManager;
        this.typeConfig = typeConfig;
        this.structureManager = structureManager;
        this.layoutManager = layoutManager;
        this.worldManager = worldManager;
        createCommandChain();
        register();
    }

    @Override
    protected String getCommandName() {
        return commandName;
    }

    @Override
    protected void createCommandChain() {
        initialCommandPiece = switch (commandName) {
            case "define" -> buildDefineTree();
            case "clear" -> buildResourceIdTree(this::clearResource);
            case "mark" -> buildResourceIdTree(this::markResource);
            case "place" -> new DynamicListChoiceCommandPiece(
                    new CommandPiece(null, this::placeStructure), null, this::structureTypeSuggestions);
            case "move" -> buildMoveTree();
            case "rotate" -> new ListChoiceCommandPiece(
                    new CommandPiece(null, this::rotateStructure), null,
                    List.of("clockwise", "counterclockwise"));
            case "setspawn" -> new ListChoiceCommandPiece(
                    new CommandPiece(null, this::setSpawn), null,
                    List.of("blue", "red", "spectator"));
            default -> new CommandPiece(null, this::executeContextCommand);
        };
    }

    private CommandPiece buildDefineTree() {
        var handler = new CommandPiece(null, this::define);
        var third = new CommandPiece(handler, null);
        var second = new CommandPiece(third, null);
        return new DynamicListChoiceCommandPiece(second, null, this::defineFirstSuggestions);
    }

    private CommandPiece buildResourceIdTree(java.util.function.Function<CommandInput, Boolean> handler) {
        return new DynamicListChoiceCommandPiece(
                new CommandPiece(null, handler), null, this::resourceIdSuggestions);
    }

    private CommandPiece buildMoveTree() {
        var amount = new CommandPiece(null, this::moveStructure);
        return new ListChoiceCommandPiece(amount, null,
                List.of("up", "down", "forward", "back", "left", "right"));
    }

    private boolean executeContextCommand(CommandInput input) {
        return switch (commandName) {
            case "visualizemarkers" -> toggleVisualization(input);
            case "save" -> save(input);
            case "discard" -> discard(input);
            case "removelooking" -> removeResource(input, false);
            case "removehere" -> removeResource(input, true);
            case "list" -> listResources(input);
            case "remove" -> removeStructure(input);
            default -> false;
        };
    }

    private boolean checkAdmin(CommandInput input) {
        if (!(input.commandSender instanceof org.bukkit.command.CommandSender sender)) return false;
        if (!sender.hasPermission("c2w.admin")) {
            sender.sendMessage("You don't have permission.");
            return false;
        }
        return true;
    }

    private Player player(CommandInput input) {
        return input.commandSender instanceof Player p ? p : null;
    }

    private boolean isLobby(Player p) {
        return sameWorld(p, worldManager.getLobbyWorld().getName());
    }

    private boolean isCreationWorld(Player p) {
        return creationManager.getCreationSession(p.getWorld().getName()) != null;
    }

    private boolean isResourceWorld(Player p) {
        return resourceManager.getResourceSession(p.getWorld().getName()) != null;
    }

    private boolean isLayoutWorld(Player p) {
        return p.getWorld().getName().startsWith("c2w_layout_");
    }

    private boolean sameWorld(Player p, String name) {
        return p.getWorld() != null && name != null && name.equals(p.getWorld().getName());
    }

    private boolean require(CommandInput input, java.util.function.Predicate<Player> context, String message) {
        if (!checkAdmin(input)) return false;
        var p = player(input);
        if (p == null) return false;
        if (!context.test(p)) {
            p.sendMessage(message);
            return false;
        }
        return true;
    }

    private List<String> structureTypeSuggestions(CommandInput input) {
        if (!(input.commandSender instanceof Player p) || !isLayoutWorld(p)) return List.of();
        var names = new ArrayList<>(typeConfig.getTypeNames());
        for (var template : structureManager.discoverTemplates()) {
            if (!names.contains(template.getTypeName())) names.add(template.getTypeName());
        }
        return names;
    }

    private List<String> defineFirstSuggestions(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return List.of();
        return isResourceWorld(p) ? List.of("block", "container", "trial-spawner") : List.of();
    }

    private List<String> resourceIdSuggestions(CommandInput input) {
        if (!(input.commandSender instanceof Player p) || !isResourceWorld(p)) return List.of();
        var session = resourceManager.getResourceSession(p.getWorld().getName());
        if (session == null) return List.of();
        var ids = new ArrayList<>(typeConfig.getResourceIds(session.typeName()));
        return ids;
    }

    private boolean define(CommandInput input) {
        var p = player(input);
        if (p == null || !checkAdmin(input)) return false;
        if (!isResourceWorld(p) || input.strings.length < 2) return false;
        var session = resourceManager.getResourceSession(p.getWorld().getName());
        if (session == null) return false;
        String resourceType = input.strings[0].toLowerCase();
        String resourceId = input.strings[1];
        if (!List.of("block", "container", "trial-spawner").contains(resourceType)) {
            p.sendMessage("Resource type must be block, container, or trial-spawner.");
            return false;
        }
        if (typeConfig.getResourceIds(session.typeName()).contains(resourceId)) {
            p.sendMessage("Resource '" + resourceId + "' is already defined.");
            return false;
        }
        if ("trial-spawner".equals(resourceType)) {
            typeConfig.addTrialSpawnerResource(session.typeName(), resourceId,
                    ResourceManager.trialSourceId(resourceId, "eggs"),
                    ResourceManager.trialSourceId(resourceId, "loot"));
        } else {
            typeConfig.addResourceRequirement(session.typeName(), resourceId, 1, resourceType);
        }
        p.sendMessage("Defined resource '" + resourceId + "'.");
        return true;
    }

    private boolean clearResource(CommandInput input) {
        if (!require(input, this::isResourceWorld, "This command can only be used in a resource world.")) return false;
        var p = player(input);
        var session = resourceManager.getResourceSession(p.getWorld().getName());
        if (input.strings.length < 1 || session == null) return false;
        int removed = resourceManager.removeAllMarkersForResource(p.getWorld().getName(), input.strings[0]);
        p.sendMessage("Cleared " + removed + " marker(s) for resource '" + input.strings[0] + "'.");
        return true;
    }

    private boolean markResource(CommandInput input) {
        if (!require(input, this::isResourceWorld, "This command can only be used in a resource world.")) return false;
        var p = player(input);
        var session = resourceManager.getResourceSession(p.getWorld().getName());
        if (input.strings.length < 1 || session == null) return false;
        return resourceManager.markResourceBlock(p, session.typeName(), input.strings[0]);
    }

    private boolean removeResource(CommandInput input, boolean here) {
        if (!require(input, this::isResourceWorld, "This command can only be used in a resource world.")) return false;
        var p = player(input);
        var location = here ? p.getLocation().getBlock().getLocation() : p.getTargetBlockExact(5) == null
                ? null : p.getTargetBlockExact(5).getLocation();
        if (location == null) {
            p.sendMessage("No block targeted. Look at a block within 5 blocks.");
            return false;
        }
        boolean removed = resourceManager.removeMarkerAt(p.getWorld().getName(), location);
        p.sendMessage(removed ? "Removed resource marker." : "No resource marker found here.");
        return true;
    }

    private boolean listResources(CommandInput input) {
        if (!require(input, this::isResourceWorld, "This command can only be used in a resource world.")) return false;
        var p = player(input);
        var markers = resourceManager.listMarkers(p.getWorld().getName());
        p.sendMessage("Resource markers:");
        markers.forEach((name, locations) -> p.sendMessage("  " + name + " (" + locations.size() + ")"));
        return true;
    }

    private boolean toggleVisualization(CommandInput input) {
        if (!require(input, p -> isCreationWorld(p) || isResourceWorld(p),
                "This command can only be used in a structure or resource world.")) return false;
        var p = player(input);
        if (isCreationWorld(p)) {
            boolean enabled = creationManager.toggleVisualization(p.getWorld().getName(), p.getWorld());
            p.sendMessage(enabled ? "Marker visualization enabled." : "Marker visualization disabled.");
            return true;
        }
        boolean enabled = resourceManager.toggleVisualization(p.getWorld().getName(), p.getWorld());
        p.sendMessage(enabled ? "Marker visualization enabled." : "Marker visualization disabled.");
        return true;
    }

    private boolean save(CommandInput input) {
        var p = player(input);
        if (p == null || !checkAdmin(input)) return false;
        if (isCreationWorld(p)) {
            var session = creationManager.getCreationSession(p.getWorld().getName());
            return session != null && creationManager.saveCreationWorld(p, session.typeName(), session.id());
        }
        if (isResourceWorld(p)) {
            var session = resourceManager.getResourceSession(p.getWorld().getName());
            return session != null && resourceManager.saveAndExit(p, session.typeName());
        }
        if (isLayoutWorld(p)) {
            layoutEditorManager.saveLayout(p);
            return true;
        }
        p.sendMessage("This command can only be used in a structure, resource, or layout world.");
        return false;
    }

    private boolean discard(CommandInput input) {
        var p = player(input);
        if (p == null || !checkAdmin(input)) return false;
        if (isCreationWorld(p)) {
            var session = creationManager.getCreationSession(p.getWorld().getName());
            return session != null && creationManager.discardCreationWorld(p, session.typeName(), session.id());
        }
        if (isResourceWorld(p)) {
            var session = resourceManager.getResourceSession(p.getWorld().getName());
            return session != null && resourceManager.discardAndExit(p, session.typeName());
        }
        if (isLayoutWorld(p)) {
            layoutEditorManager.discardEditor(p);
            return true;
        }
        p.sendMessage("This command can only be used in a structure, resource, or layout world.");
        return false;
    }

    private boolean placeStructure(CommandInput input) {
        if (!require(input, this::isLayoutWorld, "This command can only be used in a layout world.")) return false;
        if (input.strings.length < 1) return false;
        layoutEditorManager.placeStructure(player(input), input.strings[0]);
        return true;
    }

    private boolean removeStructure(CommandInput input) {
        if (!require(input, this::isLayoutWorld, "This command can only be used in a layout world.")) return false;
        layoutEditorManager.removeStructure(player(input));
        return true;
    }

    private boolean moveStructure(CommandInput input) {
        if (!require(input, this::isLayoutWorld, "This command can only be used in a layout world.")) return false;
        if (input.strings.length < 2) return false;
        int amount;
        try {
            amount = Integer.parseInt(input.strings[1]);
        } catch (NumberFormatException e) {
            player(input).sendMessage("Amount must be a positive integer.");
            return false;
        }
        if (amount <= 0) {
            player(input).sendMessage("Amount must be a positive integer.");
            return false;
        }
        var p = player(input);
        float yaw = p.getLocation().getYaw();
        int dx = 0, dy = 0, dz = 0;
        switch (input.strings[0].toLowerCase()) {
            case "up" -> dy = amount;
            case "down" -> dy = -amount;
            case "forward" -> { dx = amount * (int) Math.round(-Math.sin(Math.toRadians(yaw))); dz = amount * (int) Math.round(Math.cos(Math.toRadians(yaw))); }
            case "back" -> { dx = amount * (int) Math.round(Math.sin(Math.toRadians(yaw))); dz = amount * (int) Math.round(-Math.cos(Math.toRadians(yaw))); }
            case "left" -> { dx = amount * (int) Math.round(-Math.cos(Math.toRadians(yaw))); dz = amount * (int) Math.round(-Math.sin(Math.toRadians(yaw))); }
            case "right" -> { dx = amount * (int) Math.round(Math.cos(Math.toRadians(yaw))); dz = amount * (int) Math.round(Math.sin(Math.toRadians(yaw))); }
            default -> { return false; }
        }
        layoutEditorManager.moveStructure(p, dx, dy, dz);
        return true;
    }

    private boolean rotateStructure(CommandInput input) {
        if (!require(input, this::isLayoutWorld, "This command can only be used in a layout world.")) return false;
        if (input.strings.length < 1) return false;
        layoutEditorManager.rotateStructure(player(input),
                "clockwise".equalsIgnoreCase(input.strings[0]) ? 90 : -90);
        return true;
    }

    private boolean setSpawn(CommandInput input) {
        if (!require(input, this::isLayoutWorld, "This command can only be used in a layout world.")) return false;
        if (input.strings.length < 1) return false;
        String team = switch (input.strings[0].toLowerCase()) {
            case "red" -> "Red";
            case "blue" -> "Blue";
            case "spectator" -> "Spectator";
            default -> null;
        };
        if (team == null) return false;
        layoutEditorManager.setSpawnTeam(player(input), team);
        return true;
    }
}

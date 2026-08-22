package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.commands.DynamicListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.TreeChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.ListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.adapter.managers.GameManager;
import net.klaaswhite.c2w.adapter.managers.LayoutEditorManager;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.adapter.managers.WorldManager;
import net.klaaswhite.c2w.domain.model.LayoutPlacement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class LayoutEditorCommand extends BaseCommand {

    private final LayoutEditorManager layoutEditorManager;
    private final LayoutManager layoutManager;
    private final FolderStructureTypeConfig typeConfig;
    private final StructureManager structureManager;
    private final WorldManager worldManager;
    private final GameManager gameManager;

    private final TreeChoiceCommandPiece commandTree;

    public LayoutEditorCommand(
            JavaPlugin plugin,
            LayoutEditorManager layoutEditorManager,
            LayoutManager layoutManager,
            FolderStructureTypeConfig typeConfig,
            StructureManager structureManager,
            WorldManager worldManager,
            GameManager gameManager
    ) {
        super(plugin);
        this.layoutEditorManager = layoutEditorManager;
        this.layoutManager = layoutManager;
        this.typeConfig = typeConfig;
        this.structureManager = structureManager;
        this.worldManager = worldManager;
        this.gameManager = gameManager;
        this.commandTree = new TreeChoiceCommandPiece(null);
        createCommandChain();
        register();
    }

    @Override
    protected String getCommandName() {
        return "layout";
    }

    @Override
    protected void createCommandChain() {
        commandTree.addChoice("create", buildCreateTree());
        commandTree.addChoice("modify", buildModifyTree());
        commandTree.addChoice("list", new CommandPiece(null, this::listLayouts));
        commandTree.addChoice("place", buildPlaceTree());
        commandTree.addChoice("remove", new CommandPiece(null, this::removeStructure));
        commandTree.addChoice("rotate", buildRotateTree());
        commandTree.addChoice("move", buildMoveTree());
        commandTree.addChoice("setspawn", buildSetSpawnTree());
        commandTree.addChoice("save", new CommandPiece(null, this::saveLayout));
        commandTree.addChoice("discard", new CommandPiece(null, this::discardLayout));

        initialCommandPiece = new ContextSensitiveRoot(commandTree, this::rootChoices);
    }

    private CommandPiece buildRotateTree() {
        return new ListChoiceCommandPiece(
            new CommandPiece(null, this::rotateStructure), null,
            List.of("clockwise", "counterclockwise"));
    }

    private CommandPiece buildMoveTree() {
        return new ListChoiceCommandPiece(
            new CommandPiece(null, this::moveStructure), null,
            List.of("up", "down", "forward", "back", "left", "right"));
    }

    private CommandPiece buildCreateTree() {
        return new CommandPiece(new CommandPiece(null, this::createLayout), null);
    }

    private CommandPiece buildModifyTree() {
        var handler = new CommandPiece(null, this::modifyLayout);
        return new DynamicListChoiceCommandPiece(handler, null, this::layoutNameSuggestions);
    }

    private List<String> layoutNameSuggestions(CommandInput input) {
        return new ArrayList<>(layoutManager.getLayoutNames());
    }

    private CommandPiece buildPlaceTree() {
        var handler = new CommandPiece(null, this::placeStructure);
        return new DynamicListChoiceCommandPiece(handler, null, this::typeNameSuggestions);
    }

    private CommandPiece buildSetSpawnTree() {
        var handler = new CommandPiece(null, this::setSpawn);
        return new ListChoiceCommandPiece(handler, null, List.of("red", "blue"));
    }

    private List<String> typeNameSuggestions(CommandInput input) {
        var names = new ArrayList<>(typeConfig.getTypeNames());
        var templates = structureManager.discoverTemplates();
        for (var t : templates) {
            if (!names.contains(t.getTypeName())) names.add(t.getTypeName());
        }
        var allByType = structureManager.getAllTemplatesByTypeName();
        for (var typeName : allByType.keySet()) {
            if (!names.contains(typeName)) names.add(typeName);
        }
        return names;
    }

    private boolean checkLobby(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;
        var lobby = worldManager.getLobbyWorld().getWorld();
        if (lobby == null || !p.getWorld().equals(lobby)) {
            sender(input).sendMessage("This command can only be used in the lobby world.");
            return false;
        }
        return true;
    }

    private static boolean isEditorWorld(Player p) {
        var world = p.getWorld();
        return world != null && world.getName().startsWith("c2w_layout_");
    }

    private boolean isGameInProgress(CommandInput input) {
        if (gameManager.isGameInProgress()) {
            sender(input).sendMessage("Layout commands are blocked during an active game.");
            return true;
        }
        return false;
    }

    private static org.bukkit.command.CommandSender sender(CommandInput input) {
        return (org.bukkit.command.CommandSender) input.commandSender;
    }

    private boolean createLayout(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!checkLobby(input)) return false;
        if (input.strings.length < 2) {
            p.sendMessage("Usage: /layout create <name>");
            return false;
        }
        layoutEditorManager.createEditorWorld(p, input.strings[1]);
        return true;
    }

    private boolean modifyLayout(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!checkLobby(input)) return false;
        if (input.strings.length < 2) {
            p.sendMessage("Usage: /layout modify <name>");
            return false;
        }
        layoutEditorManager.modifyEditorWorld(p, input.strings[1]);
        return true;
    }

    private boolean placeStructure(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isEditorWorld(p)) {
            p.sendMessage("This command can only be used in a layout editor world.");
            return false;
        }
        if (input.strings.length < 2) {
            p.sendMessage("Usage: /layout place <type>");
            return false;
        }
        // strings[1] = type
        layoutEditorManager.placeStructure(p, input.strings[1]);
        return true;
    }

    private boolean removeStructure(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isEditorWorld(p)) {
            p.sendMessage("This command can only be used in a layout editor world.");
            return false;
        }
        layoutEditorManager.removeStructure(p);
        return true;
    }

    private boolean saveLayout(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isEditorWorld(p)) {
            p.sendMessage("This command can only be used in a layout editor world.");
            return false;
        }
        layoutEditorManager.saveLayout(p);
        return true;
    }

    private boolean discardLayout(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isEditorWorld(p)) {
            p.sendMessage("This command can only be used in a layout editor world.");
            return false;
        }
        layoutEditorManager.discardEditor(p);
        return true;
    }

    private boolean listLayouts(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;

        var allNames = layoutManager.getLayoutNames();
        if (allNames.isEmpty()) {
            p.sendMessage("No layouts defined.");
        } else {
            p.sendMessage("Layouts:");
            for (var name : allNames) {
                p.sendMessage("  " + name);
            }
        }

        if (isEditorWorld(p)) {
            var placements = layoutEditorManager.getSessionPlacements(p);
            if (!placements.isEmpty()) {
                p.sendMessage("Current session placements:");
                for (int i = 0; i < placements.size(); i++) {
                    LayoutPlacement p2 = placements.get(i);
                    String spawn = p2.spawnTeam() != null ? " [" + p2.spawnTeam() + " spawn]" : "";
                    p.sendMessage("  " + i + ": " + p2.typeName()
                            + " at (" + p2.x() + "," + p2.y() + "," + p2.z() + ")" + spawn);
                }
            }
        }

        return true;
    }

    private boolean setSpawn(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isEditorWorld(p)) {
            p.sendMessage("This command can only be used in a layout editor world.");
            return false;
        }
        if (input.strings.length < 2) {
            p.sendMessage("Usage: /layout setspawn <red|blue>");
            return false;
        }
        String team = input.strings[1];
        String capitalizedTeam = switch (team.toLowerCase()) {
            case "red" -> "Red";
            case "blue" -> "Blue";
            default -> {
                p.sendMessage("Team must be 'red' or 'blue'.");
                yield null;
            }
        };
        if (capitalizedTeam == null) return false;
        layoutEditorManager.setSpawnTeam(p, capitalizedTeam);
        return true;
    }

    private boolean moveStructure(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isEditorWorld(p)) {
            p.sendMessage("This command can only be used in a layout editor world.");
            return false;
        }
        if (input.strings.length < 2) {
            p.sendMessage("Usage: /layout move <up|down|forward|back|left|right>");
            return false;
        }
        // ponytail: direction relative to player's facing
        float yaw = p.getLocation().getYaw();
        int dx = 0, dy = 0, dz = 0;
        switch (input.strings[1].toLowerCase()) {
            case "up" -> dy = 1;
            case "down" -> dy = -1;
            case "forward" -> { dx = (int) Math.round(-Math.sin(Math.toRadians(yaw))); dz = (int) Math.round(Math.cos(Math.toRadians(yaw))); }
            case "back" -> { dx = (int) Math.round(Math.sin(Math.toRadians(yaw))); dz = (int) Math.round(-Math.cos(Math.toRadians(yaw))); }
            case "left" -> { dx = (int) Math.round(-Math.cos(Math.toRadians(yaw))); dz = (int) Math.round(-Math.sin(Math.toRadians(yaw))); }
            case "right" -> { dx = (int) Math.round(Math.cos(Math.toRadians(yaw))); dz = (int) Math.round(Math.sin(Math.toRadians(yaw))); }
            default -> {
                p.sendMessage("Direction must be one of: up, down, forward, back, left, right.");
                return false;
            }
        }
        layoutEditorManager.moveStructure(p, dx, dy, dz);
        return true;
    }

    private boolean rotateStructure(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isEditorWorld(p)) {
            p.sendMessage("This command can only be used in a layout editor world.");
            return false;
        }
        if (input.strings.length < 2) {
            p.sendMessage("Usage: /layout rotate <clockwise|counterclockwise>");
            return false;
        }
        float degrees = switch (input.strings[1].toLowerCase()) {
            case "clockwise" -> 90;
            case "counterclockwise" -> -90;
            default -> {
                p.sendMessage("Direction must be 'clockwise' or 'counterclockwise'.");
                yield 0;
            }
        };
        if (degrees == 0) return false;
        layoutEditorManager.rotateStructure(p, degrees);
        return true;
    }

    private List<String> rootChoices(CommandInput input) {
        boolean editor = input.commandSender instanceof Player p && isEditorWorld(p);
        if (editor) {
            return List.of("list", "place", "remove", "setspawn", "save", "discard", "rotate", "move");
        }
        return List.of("create", "modify", "list");
    }
}

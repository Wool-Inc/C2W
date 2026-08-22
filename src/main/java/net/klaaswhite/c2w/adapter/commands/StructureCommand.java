package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.domain.commands.DynamicListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.ListChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.TreeChoiceCommandPiece;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.adapter.managers.GameManager;
import net.klaaswhite.c2w.adapter.managers.ResourceManager;
import net.klaaswhite.c2w.adapter.managers.StructureCreationManager;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.adapter.managers.WorldManager;
import net.klaaswhite.c2w.adapter.managers.MarkerManager;
import net.klaaswhite.c2w.domain.model.StructureData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StructureCommand extends BaseCommand {

    private final GameManager gameManager;
    private final StructureCreationManager creationManager;
    private final ResourceManager resourceManager;
    private final FolderStructureTypeConfig typeConfig;
    private final StructureManager structureManager;
    private final WorldManager worldManager;

    public StructureCommand(
            JavaPlugin plugin,
            GameManager gameManager,
            StructureCreationManager creationManager,
            ResourceManager resourceManager,
            FolderStructureTypeConfig typeConfig,
            StructureManager structureManager,
            WorldManager worldManager
    ) {
        super(plugin);
        this.gameManager = gameManager;
        this.creationManager = creationManager;
        this.resourceManager = resourceManager;
        this.typeConfig = typeConfig;
        this.structureManager = structureManager;
        this.worldManager = worldManager;
        createCommandChain();
        register();
    }

    @Override
    protected String getCommandName() {
        return "structure";
    }

    @Override
    protected void createCommandChain() {
        var root = new TreeChoiceCommandPiece(null);

        var defineHandler = new CommandPiece(null, this::defineType);
        var d4 = new CommandPiece(defineHandler, null);
        var d3 = new CommandPiece(d4, null);
        var d2 = new CommandPiece(d3, null);
        var d1 = new DynamicListChoiceCommandPiece(
                d2, null, this::typeNameSuggestions);
        root.addChoice("define", d1);

        var resizeHandler = new CommandPiece(null, this::resizeType);
        var r4 = new CommandPiece(resizeHandler, null);
        var r3 = new CommandPiece(r4, null);
        var r2 = new CommandPiece(r3, null);
        var r1 = new DynamicListChoiceCommandPiece(
                r2, null, this::typeNameSuggestions);
        root.addChoice("resize", r1);

        var createHandler = new CommandPiece(null, this::createInstance);
        var c2 = new DynamicListChoiceCommandPiece(
                createHandler, null, this::instanceIdSuggestions);
        var c1 = new DynamicListChoiceCommandPiece(
                c2, null, this::typeNameSuggestions);
        root.addChoice("create", c1);

        var modifyHandler = new CommandPiece(null, this::modifyInstance);
        var m2 = new DynamicListChoiceCommandPiece(
                modifyHandler, null, this::instanceIdSuggestions);
        var m1 = new DynamicListChoiceCommandPiece(
                m2, null, this::typeNameSuggestions);
        root.addChoice("modify", m1);

        root.addChoice("list", new CommandPiece(null, this::listStructures));

        var deleteHandler = new CommandPiece(null, this::deleteInstance);
        var del2 = new DynamicListChoiceCommandPiece(
                deleteHandler, null, this::instanceIdSuggestions);
        var del1 = new DynamicListChoiceCommandPiece(
                del2, null, this::typeNameSuggestions);
        root.addChoice("delete", del1);

        root.addChoice("resource", new ContextSensitiveRoot(buildResourceSubTree(), this::resourceRootChoices));

        root.addChoice("marker", new ContextSensitiveRoot(buildMarkerSubTree(), this::markerRootChoices));

        root.addChoice("save", new CommandPiece(null, this::save));
        root.addChoice("discard", new CommandPiece(null, this::discard));

        initialCommandPiece = new ContextSensitiveRoot(root, this::rootChoices);
    }

    private TreeChoiceCommandPiece buildResourceSubTree() {
        var resourceRoot = new TreeChoiceCommandPiece(null);

        var worldHandler = new CommandPiece(null, this::openResourceWorld);
        var rw1 = new DynamicListChoiceCommandPiece(
                worldHandler, null, this::typeNameSuggestions);
        resourceRoot.addChoice("world", rw1);

        // place <resourceid> - marks at targeted block
        var placeHandler = new CommandPiece(null, this::placeResourceSpot);
        var placeWithId = new DynamicListChoiceCommandPiece(
                placeHandler, null, this::resourceIdSuggestionsForContext);
        resourceRoot.addChoice("place", placeWithId);

        // placehere <resourceid> - marks at player's standing position
        var placeHereHandler = new CommandPiece(null, this::placeResourceSpotHere);
        var placeHereWithId = new DynamicListChoiceCommandPiece(
                placeHereHandler, null, this::resourceIdSuggestionsForContext);
        resourceRoot.addChoice("placehere", placeHereWithId);

        var markHandler = new CommandPiece(null, this::markResource);
        resourceRoot.addChoice("mark", new DynamicListChoiceCommandPiece(
                markHandler, null, this::resourceIdSuggestionsForContext));

        resourceRoot.addChoice("list", new CommandPiece(null, this::listResourceSpots));

        // list-defined - shows defined resources from structures.yml
        resourceRoot.addChoice("list-defined", new CommandPiece(null, this::listResourceDefinitions));

        // undefine - removes a resource definition and its markers
        var undefineHandler = new CommandPiece(null, this::undefineResource);
        resourceRoot.addChoice("undefine", new DynamicListChoiceCommandPiece(
                undefineHandler, null, this::resourceIdSuggestionsForContext));

        resourceRoot.addChoice("remove", new CommandPiece(null, this::removeResourceSpot));

        // define <block|container> <resourceId>
        var defineHandler = new CommandPiece(null, this::defineResource);
        var idChooser = new DynamicListChoiceCommandPiece(
                defineHandler, null, this::resourceIdSuggestionsForContext);
        var typeChooser = new ListChoiceCommandPiece(idChooser, null, List.of("block", "container"));
        resourceRoot.addChoice("define", typeChooser);

        var clearHandler = new CommandPiece(null, this::clearResource);
        resourceRoot.addChoice("clear", new DynamicListChoiceCommandPiece(
                clearHandler, null, this::resourceIdSuggestionsForContext));

        resourceRoot.addChoice("visualize", new CommandPiece(null, this::toggleVisualize));

        return resourceRoot;
    }

    private TreeChoiceCommandPiece buildMarkerSubTree() {
        var markerRoot = new TreeChoiceCommandPiece(null);

        // place <name> - marks at targeted block
        var placeHandler = new CommandPiece(null, this::placeGameMarker);
        var placeWithName = new DynamicListChoiceCommandPiece(
                placeHandler, null, this::gameMarkerNameSuggestions);
        markerRoot.addChoice("place", placeWithName);

        // placehere <name> - marks at player's standing position
        var placeHereHandler = new CommandPiece(null, this::placeGameMarkerHere);
        var placeHereWithName = new DynamicListChoiceCommandPiece(
                placeHereHandler, null, this::gameMarkerNameSuggestions);
        markerRoot.addChoice("placehere", placeHereWithName);

        markerRoot.addChoice("list", new CommandPiece(null, this::listGameMarkers));

        // remove <name> - removes marker at targeted block
        var removeHandler = new CommandPiece(null, this::removeGameMarker);
        var removeWithName = new DynamicListChoiceCommandPiece(
                removeHandler, null, this::gameMarkerNameSuggestions);
        markerRoot.addChoice("remove", removeWithName);

        return markerRoot;
    }

    private List<String> gameMarkerNameSuggestions(CommandInput input) {
        // Only expose the markers relevant to structure editing: wool, spawnpoint
        // and boundary markers (capture markers are managed by the game, not here).
        var suggestions = new ArrayList<String>();
        suggestions.add("wool");
        suggestions.add("spawnpoint");
        for (String name : MarkerManager.MARKER_NAMES) {
            if (name.startsWith("boundary-")) suggestions.add(name);
        }
        return suggestions;
    }

    private List<String> typeNameSuggestions(CommandInput input) {
        var names = new ArrayList<>(typeConfig.getTypeNames());
        var templates = structureManager.discoverTemplates();
        for (var t : templates) {
            if (!names.contains(t.getTypeName())) names.add(t.getTypeName());
        }
        return names;
    }

    private List<String> instanceIdSuggestions(CommandInput input) {
        structureManager.discoverTemplates();
        if (input.strings.length < 2) return List.of();
        return structureManager.getTemplates(input.strings[1]).stream()
                .map(StructureData::getId)
                .toList();
    }

    private List<String> resourceIdSuggestionsForContext(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return List.of();
        String typeName = null;
        var creationParts = parseCreationWorld(p);
        if (creationParts != null) typeName = creationParts[0];
        if (typeName == null) typeName = getResourceWorldType(p);
        if (typeName == null) return List.of();
        // ponytail: resource IDs from structure.yml; mark any TileState in resource world
        return new ArrayList<>(typeConfig.getResourceRequirements(typeName).keySet());
    }

    private List<String> suggestNothing(CommandInput input) {
        return List.of();
    }

    private boolean isGameInProgress(CommandInput input) {
        if (gameManager.isGameInProgress()) {
            if (input.commandSender instanceof org.bukkit.command.CommandSender sender) {
                sender.sendMessage("Structure commands are blocked during an active game.");
            }
            return true;
        }
        return false;
    }

    private boolean checkLobby(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) return false;
        var lobby = worldManager.getLobbyWorld().getWorld();
        if (lobby == null || !p.getWorld().equals(lobby)) {
            if (input.commandSender instanceof org.bukkit.command.CommandSender sender) {
                sender.sendMessage("This command can only be used in the lobby world.");
            }
            return false;
        }
        return true;
    }

    private boolean isCreationWorld(Player p) {
        return p.getWorld().getName().startsWith("c2w_create_");
    }

    private boolean isResourceWorld(Player p) {
        return p.getWorld().getName().startsWith("c2w_resource_");
    }

    private String @Nullable [] parseCreationWorld(Player p) {
        var session = creationManager.getCreationSession(p.getWorld().getName());
        if (session == null) return null;
        return new String[]{session.typeName(), session.id()};
    }

    private @Nullable String getResourceWorldType(Player p) {
        var session = resourceManager.getResourceSession(p.getWorld().getName());
        return session == null ? null : session.typeName();
    }

    private boolean defineType(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!checkLobby(input)) return false;
        Player p = (Player) input.commandSender;

        if (input.strings.length < 5) {
            p.sendMessage("Usage: /structure define <type> <width> <height> <depth>");
            return false;
        }
        try {
            int w = Integer.parseInt(input.strings[2]);
            int h = Integer.parseInt(input.strings[3]);
            int d = Integer.parseInt(input.strings[4]);
            if (w <= 0 || h <= 0 || d <= 0) {
                p.sendMessage("Dimensions must be positive.");
                return false;
            }
            typeConfig.saveType(input.strings[1], w, h, d);
            p.sendMessage("Structure type '" + input.strings[1] + "' defined: " + w + "x" + h + "x" + d);
            return true;
        } catch (NumberFormatException e) {
            p.sendMessage("Width, height, and depth must be integers.");
            return false;
        }
    }

    private boolean resizeType(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!checkLobby(input)) return false;
        Player p = (Player) input.commandSender;

        if (input.strings.length < 5) {
            p.sendMessage("Usage: /structure resize <type> <width> <height> <depth>");
            return false;
        }
        if (!typeConfig.hasType(input.strings[1])) {
            p.sendMessage("Unknown type '" + input.strings[1] + "'. Define it first with /structure define.");
            return false;
        }
        try {
            int w = Integer.parseInt(input.strings[2]);
            int h = Integer.parseInt(input.strings[3]);
            int d = Integer.parseInt(input.strings[4]);
            if (w <= 0 || h <= 0 || d <= 0) {
                p.sendMessage("Dimensions must be positive.");
                return false;
            }
            typeConfig.saveType(input.strings[1], w, h, d);
            p.sendMessage("Structure type '" + input.strings[1] + "' resized: " + w + "x" + h + "x" + d);
            p.sendMessage("§eExisting instances are unchanged. Use /structure modify to update each instance to the new size.");
            return true;
        } catch (NumberFormatException e) {
            p.sendMessage("Width, height, and depth must be integers.");
            return false;
        }
    }

    private boolean createInstance(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!checkLobby(input)) return false;
        Player p = (Player) input.commandSender;

        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure create <type> <id>");
            return false;
        }
        if (!typeConfig.hasType(input.strings[1])) {
            p.sendMessage("Unknown type '" + input.strings[1] + "'. Define it first with /structure define.");
            return false;
        }
        return creationManager.createCreationWorld(p, input.strings[1], input.strings[2]) != null;
    }

    private boolean modifyInstance(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!checkLobby(input)) return false;
        Player p = (Player) input.commandSender;

        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure modify <type> <id>");
            return false;
        }
        return creationManager.modifyCreationWorld(p, input.strings[1], input.strings[2]) != null;
    }

    private boolean listStructures(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;

        var types = typeConfig.getTypeNames();
        if (types.isEmpty()) {
            p.sendMessage("No structure types defined.");
        } else {
            p.sendMessage("Structure types:");
            for (var t : types) {
                var dims = typeConfig.getDimensions(t);
                p.sendMessage("  " + t + (dims != null ? " (" + dims[0] + "x" + dims[1] + "x" + dims[2] + ")" : ""));
            }
        }

        var templates = structureManager.discoverTemplates();
        if (!templates.isEmpty()) {
            p.sendMessage("Saved instances:");
            for (var t : templates) {
                p.sendMessage("  " + t.getTypeName() + "/" + t.getId()
                        + " (" + t.getWidth() + "x" + t.getHeight() + "x" + t.getDepth() + ")");
            }
        }
        return true;
    }

    private boolean deleteInstance(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!checkLobby(input)) return false;
        Player p = (Player) input.commandSender;

        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure delete <type> <id>");
            return false;
        }
        return creationManager.deleteCreationWorld(p, input.strings[1], input.strings[2]);
    }

    private boolean openResourceWorld(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!checkLobby(input)) return false;
        Player p = (Player) input.commandSender;

        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure resource world <type>");
            return false;
        }
        resourceManager.openResourceWorld(p, input.strings[2]);
        return true;
    }

    private boolean save(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;

        if (isCreationWorld(p)) {
            var parts = parseCreationWorld(p);
            return parts != null && creationManager.saveCreationWorld(p, parts[0], parts[1]);
        }
        if (isResourceWorld(p)) {
            String resType = getResourceWorldType(p);
            return resType != null && resourceManager.saveAndExit(p, resType);
        }
        p.sendMessage("This command can only be used in a creation or resource world.");
        return false;
    }

    private boolean discard(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;

        if (isCreationWorld(p)) {
            var parts = parseCreationWorld(p);
            return parts != null && creationManager.discardCreationWorld(p, parts[0], parts[1]);
        }
        if (isResourceWorld(p)) {
            String resType = getResourceWorldType(p);
            return resType != null && resourceManager.discardAndExit(p, resType);
        }
        p.sendMessage("This command can only be used in a creation or resource world.");
        return false;
    }

    private boolean placeResourceSpot(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isCreationWorld(p)) {
            p.sendMessage("This command can only be used in a creation world.");
            return false;
        }

        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure resource place <resourceid>");
            return false;
        }

        var block = p.getTargetBlockExact(5);
        if (block == null) {
            p.sendMessage("No block targeted. Look at a block within 5 blocks.");
            return false;
        }

        String resourceId = input.strings[2];
        var parts = parseCreationWorld(p);
        String typeName = parts != null ? parts[0] : "";
        String id = parts != null ? parts[1] : "";

        int counter = creationManager.countResourceSpots(p.getWorld().getName(), resourceId);
        String markerName = "resourcespot-" + typeName + "-" + id + "-" + resourceId;

        block.getWorld().spawn(
                block.getLocation().add(0.5, 0, 0.5),
                org.bukkit.entity.Marker.class, m -> {
                    m.setCustomName(markerName);
                    m.setCustomNameVisible(false);
                    m.setInvulnerable(true);
                    m.setGravity(false);
                    m.setPersistent(true);
                    var pdc = m.getPersistentDataContainer();
                    pdc.set(new org.bukkit.NamespacedKey(plugin, "resourcespot"),
                            org.bukkit.persistence.PersistentDataType.STRING,
                            resourceId + "-" + counter);
                });

        p.sendMessage("Placed resource spot '" + resourceId + "-" + counter + "' at ("
                + block.getX() + ", " + block.getY() + ", " + block.getZ() + ")");
        return true;
    }

    private boolean placeResourceSpotHere(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isCreationWorld(p)) {
            p.sendMessage("This command can only be used in a creation world.");
            return false;
        }

        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure resource placehere <resourceid>");
            return false;
        }

        var block = p.getLocation().getBlock();
        String resourceId = input.strings[2];
        var parts = parseCreationWorld(p);
        String typeName = parts != null ? parts[0] : "";
        String id = parts != null ? parts[1] : "";

        int counter = creationManager.countResourceSpots(p.getWorld().getName(), resourceId);
        String markerName = "resourcespot-" + typeName + "-" + id + "-" + resourceId;

        block.getWorld().spawn(
                block.getLocation().add(0.5, 0, 0.5),
                org.bukkit.entity.Marker.class, m -> {
                    m.setCustomName(markerName);
                    m.setCustomNameVisible(false);
                    m.setInvulnerable(true);
                    m.setGravity(false);
                    m.setPersistent(true);
                    var pdc = m.getPersistentDataContainer();
                    pdc.set(new org.bukkit.NamespacedKey(plugin, "resourcespot"),
                            org.bukkit.persistence.PersistentDataType.STRING,
                            resourceId + "-" + counter);
                });

        p.sendMessage("Placed resource spot '" + resourceId + "-" + counter + "' at ("
                + block.getX() + ", " + block.getY() + ", " + block.getZ() + ")");
        return true;
    }

    private boolean defineResource(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isResourceWorld(p)) {
            p.sendMessage("This command can only be used in a resource world.");
            return false;
        }
        if (input.strings.length < 4) {
            p.sendMessage("Usage: /structure resource define <block|container> <resourceid>");
            return false;
        }
        String typeName = getResourceWorldType(p);
        if (typeName == null) return false;
        String resourceType = input.strings[2].toLowerCase();
        if (!resourceType.equals("block") && !resourceType.equals("container")) {
            p.sendMessage("Resource type must be 'block' or 'container'.");
            return false;
        }
        String resourceId = input.strings[3];
        // Check for duplicate
        if (typeConfig.getResourceRequirements(typeName).containsKey(resourceId)) {
            p.sendMessage("Resource '" + resourceId + "' is already defined for type '" + typeName + "'. Use /structure resource undefine " + resourceId + " to remove it first.");
            return false;
        }
        typeConfig.addResourceRequirement(typeName, resourceId, 1, resourceType);
        p.sendMessage("Defined " + resourceType + " resource '" + resourceId + "' for type '" + typeName + "'.");
        return true;
    }

    private boolean markResource(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isResourceWorld(p)) {
            p.sendMessage("This command can only be used in a resource world.");
            return false;
        }

        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure resource mark <resourceid>");
            return false;
        }
        String typeName = getResourceWorldType(p);
        if (typeName == null) return false;
        return resourceManager.markResourceBlock(p, typeName, input.strings[2]);
    }

    private boolean listResourceSpots(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        String worldName = p.getWorld().getName();

        if (worldName.startsWith("c2w_resource_")) {
            var markers = resourceManager.listMarkers(worldName);
            if (markers.isEmpty()) {
                p.sendMessage("No resource markers found in this resource world.");
            } else {
                p.sendMessage("Resource markers:");
                for (var entry : markers.entrySet()) {
                    p.sendMessage("  " + entry.getKey() + ":");
                    var list = entry.getValue();
                    for (int i = 0; i < list.size(); i++) {
                        var loc = list.get(i);
                        p.sendMessage("    [" + i + "] (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
                    }
                }
            }
            return true;
        }

        if (!isCreationWorld(p)) {
            p.sendMessage("This command can only be used in a creation or resource world.");
            return false;
        }

        var parts = parseCreationWorld(p);
        if (parts == null) return false;
        String typeName = parts[0];
        String id = parts[1];

        var markers = creationManager.getResourceSpotsGrouped(worldName);
        if (markers.isEmpty()) {
            p.sendMessage("No resource spot markers found in this creation world.");
        } else {
            p.sendMessage("Resource spot markers in " + typeName + "/" + id + ":");
            for (var entry : markers.entrySet()) {
                String resId = entry.getKey();
                p.sendMessage("  " + resId + ":");
                var list = entry.getValue();
                for (int i = 0; i < list.size(); i++) {
                    var marker = list.get(i);
                    var pos = marker.getPosition();
                    p.sendMessage("    [" + i + "] (" + pos.x() + ", " + pos.y() + ", " + pos.z() + ")");
                }
            }
        }

        return true;
    }

    private boolean listResourceDefinitions(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isResourceWorld(p)) {
            p.sendMessage("This command can only be used in a resource world.");
            return false;
        }
        String typeName = getResourceWorldType(p);
        if (typeName == null) return false;

        var requirements = typeConfig.getResourceRequirements(typeName);
        if (requirements.isEmpty()) {
            p.sendMessage("No resources defined for type '" + typeName + "'.");
            return true;
        }

        p.sendMessage("Defined resources for type '" + typeName + "':");
        for (var entry : requirements.entrySet()) {
            String resId = entry.getKey();
            int minSpots = entry.getValue();
            p.sendMessage("  " + resId + " (minSpots: " + minSpots + ")");
        }
        return true;
    }

    private boolean undefineResource(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isResourceWorld(p)) {
            p.sendMessage("This command can only be used in a resource world.");
            return false;
        }
        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure resource undefine <resourceid>");
            return false;
        }
        String typeName = getResourceWorldType(p);
        if (typeName == null) return false;
        String resourceId = input.strings[2];

        var requirements = typeConfig.getResourceRequirements(typeName);
        if (!requirements.containsKey(resourceId)) {
            p.sendMessage("Resource '" + resourceId + "' is not defined for type '" + typeName + "'.");
            return false;
        }

        // Remove all markers from the resource world
        String worldName = p.getWorld().getName();
        int markersRemoved = resourceManager.removeAllMarkersForResource(worldName, resourceId);

        // Remove the definition from structures.yml
        typeConfig.removeResourceRequirement(typeName, resourceId);

        p.sendMessage("Removed resource definition '" + resourceId + "' and cleared " + markersRemoved + " marker(s).");
        return true;
    }

    private boolean removeResourceSpot(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        String worldName = p.getWorld().getName();
        var block = p.getTargetBlockExact(5);
        if (block == null) {
            p.sendMessage("No block targeted. Look at a block within 5 blocks.");
            return false;
        }

        if (worldName.startsWith("c2w_resource_")) {
            boolean removed = resourceManager.removeMarkerAt(worldName, block.getLocation());
            if (removed) {
                p.sendMessage("Removed resource marker at (" + block.getX() + ", " + block.getY() + ", " + block.getZ() + ").");
            } else {
                p.sendMessage("No resource marker found at your targeted block.");
            }
            return true;
        }

        if (!isCreationWorld(p)) {
            p.sendMessage("This command can only be used in a creation or resource world.");
            return false;
        }

        // The marker spawns at blockPos + (0.5, 0, 0.5), so search within 1.5 blocks of that expected position
        double tx = block.getX() + 0.5;
        double ty = block.getY() + 0.0;
        double tz = block.getZ() + 0.5;

        boolean removed = creationManager.removeResourceSpotAt(worldName, tx, ty, tz);
        if (removed) {
            p.sendMessage("Removed resource spot at (" + block.getX() + ", " + block.getY() + ", " + block.getZ() + ").");
        } else {
            p.sendMessage("No resource spot marker found at your targeted block.");
        }
        return true;
    }

    private boolean clearResource(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isResourceWorld(p)) {
            p.sendMessage("This command can only be used in a resource world.");
            return false;
        }

        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure resource clear <resourceid>");
            return false;
        }
        String typeName = getResourceWorldType(p);
        if (typeName == null) return false;
        String resourceId = input.strings[2];
        String worldName = p.getWorld().getName();
        int removed = resourceManager.removeAllMarkersForResource(worldName, resourceId);
        p.sendMessage("Cleared " + removed + " marker(s) for resource '" + resourceId + "'.");
        return true;
    }

    private boolean toggleVisualize(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isCreationWorld(p)) {
            p.sendMessage("This command can only be used in a creation world.");
            return false;
        }

        String worldName = p.getWorld().getName();
        boolean enabled = creationManager.toggleVisualization(worldName, p.getWorld());
        if (enabled) {
            p.sendMessage("Resource spot visualization enabled (glowing armor stands).");
        } else {
            p.sendMessage("Resource spot visualization disabled.");
        }
        return true;
    }

    private boolean placeGameMarker(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isCreationWorld(p)) {
            p.sendMessage("This command can only be used in a creation world.");
            return false;
        }
        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure marker place <name>");
            p.sendMessage("§eMarker names: wool, spawnpoint, boundary-woolcap-pit-<1|2>, boundary-woolcap-elevator-<1|2>");
            return false;
        }
        return creationManager.placeGameMarker(p, input.strings[2]);
    }

    private boolean placeGameMarkerHere(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isCreationWorld(p)) {
            p.sendMessage("This command can only be used in a creation world.");
            return false;
        }
        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure marker placehere <name>");
            p.sendMessage("§eMarker names: wool, spawnpoint, boundary-woolcap-pit-<1|2>, boundary-woolcap-elevator-<1|2>");
            return false;
        }
        return creationManager.placeGameMarkerHere(p, input.strings[2]);
    }

    private boolean listGameMarkers(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isCreationWorld(p)) {
            p.sendMessage("This command can only be used in a creation world.");
            return false;
        }
        String worldName = p.getWorld().getName();
        var parts = parseCreationWorld(p);
        String typeName = parts != null ? parts[0] : "";
        String id = parts != null ? parts[1] : "";

        var markers = creationManager.getGameMarkersGrouped(worldName);
        if (markers.isEmpty()) {
            p.sendMessage("No game markers found in " + typeName + "/" + id + ".");
        } else {
            p.sendMessage("Game markers in " + typeName + "/" + id + ":");
            for (var entry : markers.entrySet()) {
                String name = entry.getKey();
                p.sendMessage("  " + name + ":");
                var list = entry.getValue();
                for (int i = 0; i < list.size(); i++) {
                    var pos = list.get(i).getPosition();
                    p.sendMessage("    [" + i + "] (" + pos.x() + ", " + pos.y() + ", " + pos.z() + ")");
                }
            }
        }
        return true;
    }

    private boolean removeGameMarker(CommandInput input) {
        if (isGameInProgress(input)) return true;
        if (!(input.commandSender instanceof Player p)) return false;
        if (!isCreationWorld(p)) {
            p.sendMessage("This command can only be used in a creation world.");
            return false;
        }
        if (input.strings.length < 3) {
            p.sendMessage("Usage: /structure marker remove <name>");
            return false;
        }
        return creationManager.removeGameMarkerAt(p, input.strings[2]);
    }

    private List<String> rootChoices(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) {
            // Non-player: fall back to the full delegate choice list.
            return null;
        }

        var world = p.getWorld();
        if (world == null) {
            return null;
        }

        var worldName = world.getName();
        if (worldName == null) {
            return null;
        }
        if (isCreationWorld(p) || isResourceWorld(p)) {
            return List.of("resource", "marker", "save", "discard");
        }
        // Lobby/overworld
        return List.of("define", "resize", "create", "modify", "list", "delete", "resource");
    }

    private List<String> resourceRootChoices(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) {
            // Non-player: fall back to the full delegate choice list.
            return null;
        }
        var world = p.getWorld();
        if (world == null) return null;

        var worldName = world.getName();
        if (worldName.startsWith("c2w_create_")) {
            return List.of("place", "placehere", "list", "remove", "visualize");
        }
        if (worldName.startsWith("c2w_resource_")) {
            return List.of("define", "undefine", "mark", "list", "list-defined", "remove", "clear");
        }
        // Lobby/overworld
        return List.of("world");
    }

    private List<String> markerRootChoices(CommandInput input) {
        if (!(input.commandSender instanceof Player p)) {
            return null;
        }
        var world = p.getWorld();
        if (world == null) return null;

        var worldName = world.getName();
        if (worldName.startsWith("c2w_create_")) {
            return List.of("place", "placehere", "list", "remove");
        }
        // Resource worlds and lobby/overworld: markers are only placed in creation worlds.
        return List.of();
    }
}

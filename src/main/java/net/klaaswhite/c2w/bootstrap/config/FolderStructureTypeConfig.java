package net.klaaswhite.c2w.bootstrap.config;

import net.klaaswhite.c2w.domain.config.ConfigAccess;
import net.klaaswhite.c2w.domain.ops.TypeDimensionSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FolderStructureTypeConfig implements TypeDimensionSource {

    public static final String GENERAL_TYPE = "general";

    private final File structuresDir;

    public FolderStructureTypeConfig(ConfigAccess configAccess) {
        this.structuresDir = new File(configAccess.getDataFolder(), "structures");
    }

    @Override
    public @Nullable int[] getDimensions(String typeName) {
        File file = structureFile(typeName);
        if (!file.exists()) return null;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int width = yaml.getInt("width", 0);
        int height = yaml.getInt("height", 0);
        int depth = yaml.getInt("depth", 0);
        if (width <= 0 || height <= 0 || depth <= 0) return null;

        return new int[]{width, height, depth};
    }

    public List<String> getTypeNames() {
        List<String> names = new ArrayList<>();
        if (!structuresDir.exists() || !structuresDir.isDirectory()) return names;

        File[] dirs = structuresDir.listFiles(File::isDirectory);
        if (dirs == null) return names;

        for (File dir : dirs) {
            if (structureFile(dir.getName()).exists()) {
                names.add(dir.getName());
            }
        }
        if (!names.contains(GENERAL_TYPE) && !getInstanceIds(GENERAL_TYPE).isEmpty()) {
            names.add(GENERAL_TYPE);
        }
        return names;
    }

    /** Return saved instance IDs, including the legacy lobby and draft files. */
    public List<String> getInstanceIds(String typeName) {
        List<String> ids = new ArrayList<>();
        File instancesDir = new File(structuresDir, typeName + "/instances");
        if (instancesDir.isDirectory()) {
            File[] files = instancesDir.listFiles((dir, name) -> name.endsWith(".nbt"));
            if (files != null) {
                for (File file : files) {
                    ids.add(file.getName().substring(0, file.getName().length() - 4));
                }
            }
        }
        if (GENERAL_TYPE.equals(typeName)) {
            for (String id : List.of("lobby", "draft")) {
                if (!ids.contains(id) && new File(structuresDir, id + ".nbt").isFile()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    public Map<String, Integer> getResourceRequirements(String typeName) {
        File file = structureFile(typeName);
        if (!file.exists()) return Map.of();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("resources");
        if (section == null) return Map.of();

        Map<String, Integer> result = new HashMap<>();
        for (String key : section.getKeys(false)) {
            int minSpots = section.getInt(key + ".minSpots", 0);
            if (minSpots > 0) {
                result.put(key, minSpots);
            }
        }
        return result;
    }

    /** Get every resource ID defined for a structure type, including trial resources. */
    public List<String> getResourceIds(String typeName) {
        File file = structureFile(typeName);
        if (!file.exists()) return List.of();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("resources");
        if (section == null) return List.of();
        return new ArrayList<>(section.getKeys(false));
    }

    /** Get all trial-spawner resource IDs defined for a structure type. */
    public List<String> getTrialResourceIds(String typeName) {
        return getResourceIds(typeName).stream()
                .filter(resourceId -> "trial-spawner".equals(getResourceType(typeName, resourceId)))
                .toList();
    }

    /** Get the resource type ("block", "container", or "trial-spawner"), or null if undefined. */
    public @Nullable String getResourceType(String typeName, String resourceId) {
        File file = structureFile(typeName);
        if (!file.exists()) return null;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String type = yaml.getString("resources." + resourceId + ".type");
        return (type == null || type.isEmpty()) ? null : type;
    }

    /** Get the configured spawn-egg source ID for a trial resource. */
    public @Nullable String getTrialSpawnEggSource(String typeName, String resourceId) {
        return getResourceField(typeName, resourceId, "spawn-eggs-source");
    }

    /** Get the configured loot source ID for a trial resource. */
    public @Nullable String getTrialLootSource(String typeName, String resourceId) {
        return getResourceField(typeName, resourceId, "loot-source");
    }

    public void saveType(String typeName, int width, int height, int depth) {
        File file = structureFile(typeName);
        try {
            file.getParentFile().mkdirs();
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create structure.yml for type: " + typeName, e);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("width", width);
        yaml.set("height", height);
        yaml.set("depth", depth);
        try {
            yaml.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save structure.yml for type: " + typeName, e);
        }
    }

    public void setResourceRequirements(String typeName, Map<String, Integer> requirements) {
        File file = structureFile(typeName);
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        // Capture existing resource definitions before clearing. Trial resources have
        // no generic resource spots, so they must survive this minSpots rewrite.
        Map<String, String> existingTypes = new HashMap<>();
        Map<String, String> existingEggSources = new HashMap<>();
        Map<String, String> existingLootSources = new HashMap<>();
        var section = yaml.getConfigurationSection("resources");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String type = section.getString(key + ".type");
                if (type != null) existingTypes.put(key, type);
                String eggs = section.getString(key + ".spawn-eggs-source");
                if (eggs != null) existingEggSources.put(key, eggs);
                String loot = section.getString(key + ".loot-source");
                if (loot != null) existingLootSources.put(key, loot);
            }
        }
        yaml.set("resources", null);
        for (var entry : requirements.entrySet()) {
            yaml.set("resources." + entry.getKey() + ".minSpots", entry.getValue());
            String type = existingTypes.get(entry.getKey());
            if (type != null) {
                yaml.set("resources." + entry.getKey() + ".type", type);
                if ("trial-spawner".equals(type)) {
                    String eggs = existingEggSources.get(entry.getKey());
                    if (eggs != null) yaml.set("resources." + entry.getKey() + ".spawn-eggs-source", eggs);
                    String loot = existingLootSources.get(entry.getKey());
                    if (loot != null) yaml.set("resources." + entry.getKey() + ".loot-source", loot);
                }
            }
        }
        for (var entry : existingTypes.entrySet()) {
            String resourceId = entry.getKey();
            if (!"trial-spawner".equals(entry.getValue()) || requirements.containsKey(resourceId)) continue;
            yaml.set("resources." + resourceId + ".minSpots", 0);
            yaml.set("resources." + resourceId + ".type", entry.getValue());
            String eggs = existingEggSources.get(resourceId);
            if (eggs != null) yaml.set("resources." + resourceId + ".spawn-eggs-source", eggs);
            String loot = existingLootSources.get(resourceId);
            if (loot != null) yaml.set("resources." + resourceId + ".loot-source", loot);
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save resource requirements for type: " + typeName, e);
        }
    }

    public void addResourceRequirement(String typeName, String resourceId, int minSpots, String resourceType) {
        File file = structureFile(typeName);
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        yaml.set("resources." + resourceId + ".minSpots", minSpots);
        yaml.set("resources." + resourceId + ".type", resourceType);
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save resource requirement for type: " + typeName, e);
        }
    }

    /** Define a trial-spawner resource with its two source container IDs. */
    public void addTrialSpawnerResource(String typeName, String resourceId,
                                         String spawnEggSource, String lootSource) {
        File file = structureFile(typeName);
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        yaml.set("resources." + resourceId + ".minSpots", 0);
        yaml.set("resources." + resourceId + ".type", "trial-spawner");
        yaml.set("resources." + resourceId + ".spawn-eggs-source", spawnEggSource);
        yaml.set("resources." + resourceId + ".loot-source", lootSource);
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save trial-spawner resource for type: " + typeName, e);
        }
    }

    public void removeResourceRequirement(String typeName, String resourceId) {
        File file = structureFile(typeName);
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("resources." + resourceId, null);
        try {
            yaml.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove resource requirement for type: " + typeName, e);
        }
    }

    public void removeType(String typeName) {
        File dir = new File(structuresDir, typeName);
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        dir.delete();
    }

    public boolean hasType(String typeName) {
        return structureFile(typeName).exists()
                || (GENERAL_TYPE.equals(typeName) && !getInstanceIds(typeName).isEmpty());
    }


    private File structureFile(String typeName) {
        return new File(structuresDir, typeName + "/structure.yml");
    }

    private @Nullable String getResourceField(String typeName, String resourceId, String field) {
        File file = structureFile(typeName);
        if (!file.exists()) return null;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String value = yaml.getString("resources." + resourceId + "." + field);
        return value == null || value.isEmpty() ? null : value;
    }
}

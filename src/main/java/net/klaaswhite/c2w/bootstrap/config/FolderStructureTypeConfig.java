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
        return names;
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

    /** Get the resource type ("block" or "container") for a resource, or null if not defined. */
    public @Nullable String getResourceType(String typeName, String resourceId) {
        File file = structureFile(typeName);
        if (!file.exists()) return null;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String type = yaml.getString("resources." + resourceId + ".type");
        return (type == null || type.isEmpty()) ? null : type;
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
        // capture existing resource types before clearing
        Map<String, String> existingTypes = new HashMap<>();
        var section = yaml.getConfigurationSection("resources");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String type = section.getString(key + ".type");
                if (type != null) existingTypes.put(key, type);
            }
        }
        yaml.set("resources", null);
        for (var entry : requirements.entrySet()) {
            yaml.set("resources." + entry.getKey() + ".minSpots", entry.getValue());
            String type = existingTypes.get(entry.getKey());
            if (type != null) yaml.set("resources." + entry.getKey() + ".type", type);
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
        return structureFile(typeName).exists();
    }


    private File structureFile(String typeName) {
        return new File(structuresDir, typeName + "/structure.yml");
    }
}

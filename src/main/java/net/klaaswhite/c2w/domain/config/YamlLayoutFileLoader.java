package net.klaaswhite.c2w.domain.config;

import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.LayoutCell;
import net.klaaswhite.c2w.domain.model.MapLayout;
import net.klaaswhite.c2w.domain.ops.FileSystemOps;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class YamlLayoutFileLoader implements LayoutFileLoader {

    private static final List<Map<String, Object>> EMPTY_OVERRIDES = List.of();
    private static final Logger log = Logger.getLogger(YamlLayoutFileLoader.class.getName());

    private final FileSystemOps fs;

    public YamlLayoutFileLoader(FileSystemOps fs) {
        this.fs = fs;
    }

    @Override
    public @Nullable MapLayout loadLayout(File file, String name) {
        try (InputStream input = fs.newInputStream(file)) {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(input);
            if (!(raw instanceof Map<?, ?> root)) return null;

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) root;

            int tileWidth = (int) data.getOrDefault("tileWidth", 32);
            int tileHeight = (int) data.getOrDefault("tileHeight", 32);
            int tileDepth = (int) data.getOrDefault("tileDepth", 32);

            @SuppressWarnings("unchecked")
            var originMap = (Map<String, Object>) data.getOrDefault("origin", Map.of());
            int ox = (int) originMap.getOrDefault("x", 0);
            int oy = (int) originMap.getOrDefault("y", 64);
            int oz = (int) originMap.getOrDefault("z", 0);
            var origin = new BlockPos(ox, oy, oz);

            if (data.containsKey("placements")) {
                return loadFromPlacements(data, name, tileWidth, tileHeight, tileDepth, origin);
            }

            String gridStr = (String) data.get("grid");
            if (gridStr == null || gridStr.isBlank()) return null;

            @SuppressWarnings("unchecked")
            var charMap = (Map<String, String>) data.getOrDefault("charMap", Map.of());
            return loadFromGrid(gridStr, charMap, name, tileWidth, tileHeight, tileDepth, origin, data);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private MapLayout loadFromPlacements(Map<String, Object> data, String name,
                                          int tileWidth, int tileHeight, int tileDepth, BlockPos origin) {
        var cells = new ArrayList<LayoutCell>();
        List<Map<String, Object>> rawPlacements = (List<Map<String, Object>>) data.get("placements");
        if (rawPlacements == null) return null;

        int row = 0;
        for (var rp : rawPlacements) {
            Object typeObj = rp.get("type");
            Object xObj = rp.get("x");
            Object yObj = rp.get("y");
            Object zObj = rp.get("z");
            if (typeObj == null || xObj == null || yObj == null || zObj == null) {
                log.warning("Skipping malformed layout placement entry: " + rp);
                continue;
            }
            String type = String.valueOf(typeObj);
            int px = ((Number) xObj).intValue();
            int py = ((Number) yObj).intValue();
            int pz = ((Number) zObj).intValue();
            String spawnTeam = rp.containsKey("spawnTeam") ? String.valueOf(rp.get("spawnTeam")) : null;

            float yaw = rp.containsKey("yaw") ? ((Number) rp.get("yaw")).floatValue() : 0f;
            if (spawnTeam != null) {
                cells.add(new LayoutCell(row, 0, type, new BlockPos(px, py, pz), spawnTeam, yaw));
            } else {
                cells.add(new LayoutCell(row, 0, type, new BlockPos(px, py, pz), null, yaw));
            }
            row++;
        }

        if (cells.isEmpty()) return null;
        return new MapLayout(name, tileWidth, tileHeight, tileDepth, origin, cells, true);
    }

    @SuppressWarnings("unchecked")
    private MapLayout loadFromGrid(String gridStr, Map<String, String> charMap, String name,
                                    int tileWidth, int tileHeight, int tileDepth, BlockPos origin,
                                    Map<String, Object> data) {
        var cellOverrides = (List<Map<String, Object>>) data.getOrDefault("cellOverrides", EMPTY_OVERRIDES);
        var gridLines = gridStr.stripTrailing().split("\n");
        var cells = new ArrayList<LayoutCell>();

        var overrideMap = new LinkedHashMap<String, Map<String, Object>>();
        for (var override : cellOverrides) {
            int row = (int) override.get("row");
            int col = (int) override.get("col");
            overrideMap.put(row + "," + col, override);
        }

        int spawnCounter = 0;
        for (int row = 0; row < gridLines.length; row++) {
            String line = gridLines[row].stripTrailing();
            for (int col = 0; col < line.length(); col++) {
                char ch = line.charAt(col);
                String typeName = charMap.getOrDefault(String.valueOf(ch), "VOID");
                if ("VOID".equalsIgnoreCase(typeName)) continue;

                int x = origin.x() + col * tileWidth;
                int y = origin.y();
                int z = origin.z() + row * tileDepth;

                var override = overrideMap.get(row + "," + col);
                if (override != null) {
                    x = (int) override.getOrDefault("x", x);
                    y = (int) override.getOrDefault("y", y);
                    z = (int) override.getOrDefault("z", z);
                }

                var worldPos = new BlockPos(x, y, z);
                String team = resolveSpawnTeam(String.valueOf(ch), typeName, spawnCounter);
                if (team != null) spawnCounter++;
                cells.add(team != null
                        ? LayoutCell.spawn(row, col, typeName, worldPos, team)
                        : LayoutCell.of(row, col, typeName, worldPos));
            }
        }

        return new MapLayout(name, tileWidth, tileHeight, tileDepth, origin, cells);
    }

    private static @Nullable String resolveSpawnTeam(String ch, String typeName, int spawnIndex) {
        if (!"SPAWN".equalsIgnoreCase(typeName)) return null;
        return switch (ch) {
            case "R", "SR" -> "Red";
            case "B", "SB" -> "Blue";
            default -> spawnIndex % 2 == 0 ? "Red" : "Blue";
        };
    }
}

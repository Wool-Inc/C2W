package net.klaaswhite.c2w.bootstrap.config;

import net.klaaswhite.c2w.domain.model.LayoutPlacement;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LayoutData {

    private final String name;
    private final List<LayoutPlacement> placements;
    private int originX;
    private int originY;
    private int originZ;

    public LayoutData(String name) {
        this.name = name;
        this.placements = new ArrayList<>();
        this.originX = 0;
        this.originY = 64;
        this.originZ = 0;
    }

    public void addPlacement(String typeName, int x, int y, int z) {
        addPlacement(typeName, x, y, z, 0f);
    }

    public void addPlacement(String typeName, int x, int y, int z, float yaw) {
        placements.add(new LayoutPlacement(typeName, x, y, z, null, yaw));
    }

    public void markSpawnTeam(int index, String team) {
        if (index < 0 || index >= placements.size()) return;
        var p = placements.get(index);
        placements.set(index, new LayoutPlacement(p.typeName(), p.x(), p.y(), p.z(), team, p.yaw()));
    }

    public boolean removeNearest(int x, int y, int z, int maxDistance) {
        LayoutPlacement nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (LayoutPlacement p : placements) {
            double dx = p.x() - x;
            double dy = p.y() - y;
            double dz = p.z() - z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist <= maxDistance && dist < nearestDist) {
                nearest = p;
                nearestDist = dist;
            }
        }

        if (nearest != null) {
            placements.remove(nearest);
            return true;
        }
        return false;
    }

    public List<LayoutPlacement> getPlacements() {
        return Collections.unmodifiableList(placements);
    }

    public void setOrigin(int x, int y, int z) {
        this.originX = x;
        this.originY = y;
        this.originZ = z;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    public void save(File layoutsDir) {
        File file = new File(layoutsDir, name + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("name", name);
        config.set("origin.x", originX);
        config.set("origin.y", originY);
        config.set("origin.z", originZ);

        List<java.util.Map<String, Object>> placementMaps = new ArrayList<>();
        for (LayoutPlacement p : placements) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", p.typeName());
            map.put("x", p.x());
            map.put("y", p.y());
            map.put("z", p.z());
            if (p.spawnTeam() != null) map.put("spawnTeam", p.spawnTeam());
            if (p.yaw() != 0f) map.put("yaw", p.yaw());
            placementMaps.add(map);
        }
        config.set("placements", placementMaps);

        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save layout: " + name, e);
        }
    }

    public static LayoutData load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String name = config.getString("name", file.getName().replace(".yml", ""));
        LayoutData data = new LayoutData(name);

        data.originX = config.getInt("origin.x", 0);
        data.originY = config.getInt("origin.y", 64);
        data.originZ = config.getInt("origin.z", 0);

        List<?> rawPlacements = config.getMapList("placements");
        for (Object obj : rawPlacements) {
            if (obj instanceof java.util.Map<?, ?> map) {
                Object typeObj = map.get("type");
                // Skip malformed placements
                if (typeObj == null) continue;
                String type = String.valueOf(typeObj);
                Object xObj = map.get("x");
                Object yObj = map.get("y");
                Object zObj = map.get("z");
                if (!(xObj instanceof Number) || !(yObj instanceof Number) || !(zObj instanceof Number)) continue;
                int px = ((Number) xObj).intValue();
                int py = ((Number) yObj).intValue();
                int pz = ((Number) zObj).intValue();
                Object spawnTeamObj = map.get("spawnTeam");
                String spawnTeam = spawnTeamObj != null ? String.valueOf(spawnTeamObj) : null;
                float yaw = map.containsKey("yaw") && map.get("yaw") instanceof Number
                        ? ((Number) map.get("yaw")).floatValue() : 0f;
                data.placements.add(new LayoutPlacement(type, px, py, pz, spawnTeam, yaw));
            }
        }

        return data;
    }
}

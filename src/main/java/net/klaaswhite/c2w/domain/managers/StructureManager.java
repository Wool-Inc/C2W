package net.klaaswhite.c2w.domain.managers;

import net.klaaswhite.c2w.domain.model.StructureData;
import net.klaaswhite.c2w.domain.model.StructureRotation;
import net.klaaswhite.c2w.domain.model.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the per-type {@link StructureData} registry. Discovery and
 * transport are delegated to {@link NbtStructureSource}; this manager
 * only stores the results.
 */
public class StructureManager implements AutoCloseable {

    private final NbtStructureSource source;
    private final Map<String, List<StructureData>> templatesByTypeName = new HashMap<>();
    private final Map<String, Set<String>> usedInstances = new HashMap<>();

    public StructureManager(NbtStructureSource source) {
        this.source = source;
    }

    public List<StructureData> discoverTemplates() {
        var discovered = source.discover();
        templatesByTypeName.clear();
        for (var t : discovered) {
            templatesByTypeName.computeIfAbsent(t.getTypeName(), k -> new ArrayList<>()).add(t);
        }
        return discovered;
    }

    public List<StructureData> getTemplates(String typeName) {
        return templatesByTypeName.getOrDefault(typeName, List.of());
    }

    public Map<String, List<StructureData>> getAllTemplatesByTypeName() {
        return templatesByTypeName;
    }

    public int getTemplateCount() {
        int total = 0;
        for (var list : templatesByTypeName.values()) {
            total += list.size();
        }
        return total;
    }

    public @Nullable StructureData placeRandom(
            String typeName,
            String worldName,
            BlockPos pos
    ) {
        return placeRandom(typeName, worldName, pos, StructureRotation.NONE);
    }

    public @Nullable StructureData placeRandom(
            String typeName,
            String worldName,
            BlockPos pos,
            StructureRotation rotation
    ) {
        var list = templatesByTypeName.get(typeName);
        if (list == null || list.isEmpty()) return null;

        // Get used instance IDs for this type
        var used = usedInstances.computeIfAbsent(typeName, k -> new HashSet<>());

        // Try to find an unused instance first
        List<StructureData> unused = list.stream()
                .filter(t -> !used.contains(t.getId()))
                .toList();

        StructureData pick;
        if (!unused.isEmpty()) {
            // Pick from unused instances
            pick = unused.get((int) (Math.random() * unused.size()));
        } else {
            // All instances used, allow duplicates
            pick = list.get((int) (Math.random() * list.size()));
        }

        source.transport(pick, worldName, pos, rotation);
        used.add(pick.getId());
        return pick;
    }

    /**
     * Check whether a structure NBT's actual dimensions match the type's
     * current configured dimensions. Returns {@code null} if they match,
     * or a human-readable warning string if they don't.
     */
    public @Nullable String checkDimensions(String typeName, String id) {
        var dims = source.getNbtDimensions(typeName, id);
        if (dims == null) return null;
        var expected = source.getTypeDimensions(typeName);
        if (expected == null) return null;
        if (dims[0] != expected[0] || dims[1] != expected[1] || dims[2] != expected[2]) {
            return "Instance " + typeName + "/" + id + " has size " +
                    dims[0] + "x" + dims[1] + "x" + dims[2] +
                    " but type expects " + expected[0] + "x" + expected[1] + "x" + expected[2];
        }
        return null;
    }

    public boolean hasType(String typeName) {
        var list = templatesByTypeName.get(typeName);
        return list != null && !list.isEmpty();
    }

    /**
     * Clear the tracking of used instances. Call this when starting a new game
     * to allow reusing structures from previous games.
     */
    public void clearUsedInstances() {
        usedInstances.clear();
    }

    @Override
    public void close() {
        templatesByTypeName.clear();
        usedInstances.clear();
    }
}

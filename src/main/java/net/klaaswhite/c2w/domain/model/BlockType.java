package net.klaaswhite.c2w.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * A pure-data block type definition. Replaces Bukkit {@code BlockData} in
 * domain classes so they can be constructed without a running server.
 */
public record BlockType(String material, String state) {

    public BlockType(String material) {
        this(material, "");
    }

    /**
     * Parse from a string like "CHEST" or "CHEST[facing=north]".
     * Returns null if the string is blank or the material is unknown.
     */
    public static @Nullable BlockType parse(String input) {
        if (input == null || input.isBlank()) return null;
        String materialPart = input;
        String statePart = "";
        int bracketIdx = input.indexOf('[');
        if (bracketIdx >= 0) {
            materialPart = input.substring(0, bracketIdx);
            int closing = input.indexOf(']', bracketIdx);
            if (closing > bracketIdx) {
                statePart = input.substring(bracketIdx + 1, closing);
            }
        }
        materialPart = materialPart.trim();
        if (materialPart.isBlank()) return null;
        return new BlockType(materialPart.toUpperCase(), statePart);
    }

    @Override
    public String toString() {
        return state.isEmpty() ? material : material + "[" + state + "]";
    }
}

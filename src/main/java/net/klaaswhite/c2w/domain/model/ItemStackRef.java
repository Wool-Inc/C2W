package net.klaaswhite.c2w.domain.model;

/**
 * A reference to an item stack. In the domain layer this is a simple wrapper
 * around a material name and optional display data. The Bukkit adapter
 * translates this to a real {@code ItemStack}.
 * <p>
 * For wool items, use {@link #wool(WoolColor)}. For no item, use {@link #empty()}.
 */
public record ItemStackRef(String materialName, int count) {

    public static ItemStackRef wool(WoolColor color) {
        return new ItemStackRef(color.name() + "_WOOL", 1);
    }

    public static ItemStackRef empty() {
        return new ItemStackRef("AIR", 0);
    }

    public boolean isEmpty() {
        return count <= 0 || "AIR".equals(materialName);
    }
}

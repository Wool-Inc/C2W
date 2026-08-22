package net.klaaswhite.c2w.domain.ops;

import org.jspecify.annotations.Nullable;

/**
 * Port interface for looking up a structure type's configured dimensions.
 * Implemented by {@code FolderStructureTypeConfig} in the bootstrap layer.
 */
public interface TypeDimensionSource {
    @Nullable int[] getDimensions(String typeName);
}

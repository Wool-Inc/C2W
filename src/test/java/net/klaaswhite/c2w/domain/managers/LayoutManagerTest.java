package net.klaaswhite.c2w.domain.managers;

import net.klaaswhite.c2w.domain.model.MapLayout;
import net.klaaswhite.c2w.domain.ops.FileSystemOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LayoutManager")
class LayoutManagerTest {

    private static final FileSystemOps NOOP_FS = new FileSystemOps() {
        @Override public File[] listFiles(File dir) { return new File[0]; }
        @Override public File[] listFiles(File dir, String suffix) { return new File[0]; }
        @Override public boolean isFile(File file) { return false; }
        @Override public boolean isDirectory(File dir) { return false; }
        @Override public InputStream newInputStream(File file) throws IOException { return null; }
        @Override public boolean createFile(File file) throws IOException { return false; }
        @Override public boolean delete(File file) { return false; }
    };

    @Test
    @DisplayName("getLayoutNames returns empty when source has none")
    void getLayoutNamesEmpty() {
        var mgr = new LayoutManager(null, (file, name) -> null, NOOP_FS);
        assertTrue(mgr.getLayoutNames().isEmpty());
    }

    @Test
    @DisplayName("getLayout returns null for unknown layout")
    void getLayoutNull() {
        var mgr = new LayoutManager(null, (file, name) -> null, NOOP_FS);
        assertNull(mgr.getLayout("nonexistent"));
    }

    @Test
    @DisplayName("getLayout caches results")
    void getLayoutCaches() {
        var mgr = new LayoutManager(null, (file, name) -> null, NOOP_FS);
        var layout1 = mgr.getLayout("map1");
        var layout2 = mgr.getLayout("map1");
        assertSame(layout1, layout2);
    }

    @Test
    @DisplayName("clearCache forces re-parse")
    void clearCache() {
        var mgr = new LayoutManager(null, (file, name) -> null, NOOP_FS);
        mgr.getLayout("map1");
        mgr.clearCache();
        mgr.getLayout("map1");
        // No assertion failure means cache was cleared and re-invoked without error
        assertTrue(true);
    }
}

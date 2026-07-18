package net.klaaswhite.c2w.bootstrap.ops;

import net.klaaswhite.c2w.domain.ops.FileSystemOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BukkitFileSystemOps")
class BukkitFileSystemOpsTest {

    private final FileSystemOps ops = new BukkitFileSystemOps();

    @Test
    @DisplayName("isFile / isDirectory report correctly")
    void fileAndDirChecks(@TempDir File dir) {
        File file = new File(dir, "f.txt");
        assertFalse(ops.isFile(file));
        assertFalse(ops.isDirectory(file));

        assertTrue(ops.isDirectory(dir));
        assertFalse(ops.isFile(dir));

        assertFalse(ops.isFile(null));
        assertFalse(ops.isDirectory(null));
    }

    @Test
    @DisplayName("createFile creates the file and parent dirs")
    void createFile(@TempDir File dir) throws IOException {
        File nested = new File(dir, "a/b/c.txt");
        assertTrue(ops.createFile(nested));
        assertTrue(nested.isFile());
        // second create returns false (already exists)
        assertFalse(ops.createFile(nested));
    }

    @Test
    @DisplayName("createFile throws on null")
    void createFileNull() {
        assertThrows(IOException.class, () -> ops.createFile(null));
    }

    @Test
    @DisplayName("newInputStream reads file contents")
    void newInputStream(@TempDir File dir) throws IOException {
        File file = new File(dir, "data.txt");
        assertTrue(file.createNewFile());
        try (var out = new java.io.FileOutputStream(file)) {
            out.write("hello".getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream in = ops.newInputStream(file)) {
            byte[] buf = in.readAllBytes();
            assertEquals("hello", new String(buf, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("delete removes the file")
    void delete(@TempDir File dir) throws IOException {
        File file = new File(dir, "x.txt");
        assertTrue(file.createNewFile());
        assertTrue(ops.delete(file));
        assertFalse(file.exists());
        assertFalse(ops.delete(null));
    }

    @Test
    @DisplayName("listFiles returns all files in a directory")
    void listFiles(@TempDir File dir) throws IOException {
        assertTrue(new File(dir, "a.txt").createNewFile());
        assertTrue(new File(dir, "b.txt").createNewFile());
        assertTrue(new File(dir, "sub").mkdir());

        File[] all = ops.listFiles(dir);
        assertEquals(3, all.length);

        File[] txts = ops.listFiles(dir, ".txt");
        assertEquals(2, txts.length);
        assertTrue(Arrays.stream(txts).allMatch(f -> f.getName().endsWith(".txt")));
    }

    @Test
    @DisplayName("listFiles returns empty array for non-directory")
    void listFilesNonDir(@TempDir File dir) throws IOException {
        File file = new File(dir, "f.txt");
        assertTrue(file.createNewFile());
        assertEquals(0, ops.listFiles(file).length);
        assertEquals(0, ops.listFiles(file, ".txt").length);
        assertEquals(0, ops.listFiles(null).length);
    }
}

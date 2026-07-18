package net.klaaswhite.c2w.domain.ops;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FakeFileSystemOps")
class FakeFileSystemOpsTest {

    private FakeFileSystemOps ffs;

    @BeforeEach
    void setUp() {
        ffs = new FakeFileSystemOps();
    }

    @Test
    @DisplayName("isFile returns true for created file")
    void isFileReturnsTrue() {
        var file = ffs.createFile("/test/foo.txt", "hello");
        assertTrue(ffs.isFile(file));
    }

    @Test
    @DisplayName("isFile returns false for directory")
    void isFileReturnsFalseForDir() {
        var dir = ffs.createDir("/test/dir");
        assertFalse(ffs.isFile(dir));
    }

    @Test
    @DisplayName("isFile returns false for non-existent path")
    void isFileReturnsFalseForNonExistent() {
        assertFalse(ffs.isFile(new File("/nonexistent/file.txt")));
    }

    @Test
    @DisplayName("isFile returns false for null")
    void isFileReturnsFalseForNull() {
        assertFalse(ffs.isFile(null));
    }

    @Test
    @DisplayName("isDirectory returns true for created directory")
    void isDirectoryReturnsTrue() {
        var dir = ffs.createDir("/test/mydir");
        assertTrue(ffs.isDirectory(dir));
    }

    @Test
    @DisplayName("isDirectory returns false for file")
    void isDirectoryReturnsFalseForFile() {
        var file = ffs.createFile("/test/file.txt", "data");
        assertFalse(ffs.isDirectory(file));
    }

    @Test
    @DisplayName("isDirectory returns false for null")
    void isDirectoryReturnsFalseForNull() {
        assertFalse(ffs.isDirectory(null));
    }

    @Test
    @DisplayName("listFiles returns empty for non-existent directory")
    void listFilesNonExistentDir() {
        var result = ffs.listFiles(new File("/nonexistent"));
        assertEquals(0, result.length);
    }

    @Test
    @DisplayName("listFiles returns files in directory")
    void listFilesReturnsFiles() {
        ffs.createFile("/test/a.txt", "aaa");
        ffs.createFile("/test/b.txt", "bbb");
        var result = ffs.listFiles(new File("/test"));
        assertEquals(2, result.length);
    }

    @Test
    @DisplayName("listFiles does not return nested files but includes subdirs")
    void listFilesNoNested() {
        ffs.createFile("/test/a.txt", "aaa");
        ffs.createFile("/test/sub/b.txt", "bbb");
        var result = ffs.listFiles(new File("/test"));
        // a.txt + sub/ directory
        assertEquals(2, result.length);
    }

    @Test
    @DisplayName("listFiles with suffix filter")
    void listFilesWithSuffix() {
        ffs.createFile("/test/a.txt", "aaa");
        ffs.createFile("/test/b.yml", "bbb");
        ffs.createFile("/test/c.txt", "ccc");
        var result = ffs.listFiles(new File("/test"), ".txt");
        assertEquals(2, result.length);
    }

    @Test
    @DisplayName("listFiles includes subdirectories")
    void listFilesIncludesDirs() {
        ffs.createFile("/test/a.txt", "aaa");
        ffs.createDir("/test/subdir");
        var result = ffs.listFiles(new File("/test"));
        assertEquals(2, result.length);
    }

    @Test
    @DisplayName("newInputStream reads file content")
    void newInputStreamReadsContent() throws IOException {
        ffs.createFile("/test/data.txt", "hello world");
        try (var is = ffs.newInputStream(new File("/test/data.txt"))) {
            assertEquals("hello world", new String(is.readAllBytes()));
        }
    }

    @Test
    @DisplayName("newInputStream throws for non-existent file")
    void newInputStreamThrows() {
        assertThrows(IOException.class, () ->
                ffs.newInputStream(new File("/nonexistent.txt")));
    }

    @Test
    @DisplayName("createFile with binary content")
    void createFileBinary() throws IOException {
        byte[] data = {0x01, 0x02, 0x03};
        var file = ffs.createFile("/test/binary.dat", data);
        try (var is = ffs.newInputStream(file)) {
            assertArrayEquals(data, is.readAllBytes());
        }
    }

    @Test
    @DisplayName("parent directories are auto-created")
    void parentDirsAutoCreated() {
        ffs.createFile("/a/b/c/file.txt", "data");
        assertTrue(ffs.isDirectory(new File("/a")));
        assertTrue(ffs.isDirectory(new File("/a/b")));
        assertTrue(ffs.isDirectory(new File("/a/b/c")));
    }

    @Test
    @DisplayName("createDir auto-creates parent directories")
    void createDirAutoParents() {
        ffs.createDir("/x/y/z");
        assertTrue(ffs.isDirectory(new File("/x")));
        assertTrue(ffs.isDirectory(new File("/x/y")));
        assertTrue(ffs.isDirectory(new File("/x/y/z")));
    }
}

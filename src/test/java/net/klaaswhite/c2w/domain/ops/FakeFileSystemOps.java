package net.klaaswhite.c2w.domain.ops;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link FileSystemOps} for unit tests. Files are backed by a
 * byte-array map keyed by absolute path. No real disk I/O occurs.
 */
public class FakeFileSystemOps implements FileSystemOps {

    private final Map<String, byte[]> files = new HashMap<>();
    private final Map<String, Boolean> directories = new HashMap<>();

    /** Create a virtual file with the given content. */
    public File createFile(String path, String content) {
        var file = new File(path);
        files.put(file.getAbsolutePath(), content.getBytes(StandardCharsets.UTF_8));
        ensureParents(file);
        return file;
    }

    /** Create a virtual file with the given binary content. */
    public File createFile(String path, byte[] content) {
        var file = new File(path);
        files.put(file.getAbsolutePath(), content);
        ensureParents(file);
        return file;
    }

    /** Create a virtual directory. */
    public File createDir(String path) {
        var dir = new File(path);
        ensureParents(dir);
        directories.put(dir.getAbsolutePath(), true);
        return dir;
    }

    private void ensureParents(File file) {
        var parent = file.getParentFile();
        while (parent != null && !directories.containsKey(parent.getAbsolutePath())) {
            directories.put(parent.getAbsolutePath(), true);
            parent = parent.getParentFile();
        }
    }

    @Override
    public File[] listFiles(File dir) {
        if (!isDirectory(dir)) return new File[0];
        var prefix = dir.getAbsolutePath() + File.separator;
        List<File> result = new ArrayList<>();
        for (var entry : files.entrySet()) {
            var path = entry.getKey();
            if (path.startsWith(prefix) && !path.substring(prefix.length()).contains(File.separator)) {
                result.add(new File(path));
            }
        }
        for (var entry : directories.entrySet()) {
            var path = entry.getKey();
            if (path.startsWith(prefix) && !path.substring(prefix.length()).contains(File.separator)) {
                var f = new File(path);
                if (!result.contains(f)) result.add(f);
            }
        }
        return result.toArray(new File[0]);
    }

    @Override
    public File[] listFiles(File dir, String suffix) {
        var all = listFiles(dir);
        List<File> result = new ArrayList<>();
        for (var f : all) {
            if (f.getName().endsWith(suffix)) result.add(f);
        }
        return result.toArray(new File[0]);
    }

    @Override
    public boolean isFile(File file) {
        return file != null && files.containsKey(file.getAbsolutePath());
    }

    @Override
    public boolean isDirectory(File dir) {
        return dir != null && directories.containsKey(dir.getAbsolutePath());
    }

    @Override
    public InputStream newInputStream(File file) throws IOException {
        var data = files.get(file.getAbsolutePath());
        if (data == null) throw new IOException("File not found: " + file);
        return new ByteArrayInputStream(data);
    }

    @Override
    public boolean createFile(File file) throws IOException {
        if (file == null) throw new IOException("file is null");
        var abs = file.getAbsolutePath();
        if (files.containsKey(abs)) return false;
        ensureParents(file);
        files.put(abs, new byte[0]);
        return true;
    }

    @Override
    public boolean delete(File file) {
        if (file == null) return false;
        var abs = file.getAbsolutePath();
        boolean wasFile = files.remove(abs) != null;
        directories.remove(abs);
        return wasFile;
    }
}

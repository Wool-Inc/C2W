package net.klaaswhite.c2w.bootstrap.ops;

import net.klaaswhite.c2w.domain.ops.FileSystemOps;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Production {@link FileSystemOps} implementation backed by real {@link File} APIs.
 */
public class BukkitFileSystemOps implements FileSystemOps {

    @Override
    public File[] listFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return new File[0];
        var files = dir.listFiles();
        return files != null ? files : new File[0];
    }

    @Override
    public File[] listFiles(File dir, String suffix) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return new File[0];
        var files = dir.listFiles((d, name) -> name.endsWith(suffix));
        return files != null ? files : new File[0];
    }

    @Override
    public boolean isFile(File file) {
        return file != null && file.isFile();
    }

    @Override
    public boolean isDirectory(File dir) {
        return dir != null && dir.isDirectory();
    }

    @Override
    public InputStream newInputStream(File file) throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public boolean createFile(File file) throws IOException {
        if (file == null) throw new IOException("file is null");
        var parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        return file.createNewFile();
    }

    @Override
    public boolean delete(File file) {
        if (file == null) return false;
        return file.delete();
    }
}

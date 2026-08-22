package net.klaaswhite.c2w.domain.ops;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction over filesystem operations. Production implementations delegate
 * to real {@link File} APIs; tests use in-memory fakes.
 */
public interface FileSystemOps {

    /** List files in a directory, or empty array if it doesn't exist. */
    File[] listFiles(File dir);

    /** List files in a directory filtered by name suffix, or empty array. */
    File[] listFiles(File dir, String suffix);

    /** Return true if the file exists and is not a directory. */
    boolean isFile(File file);

    /** Return true if the directory exists. */
    boolean isDirectory(File dir);

    /** Open an input stream for reading a file. */
    InputStream newInputStream(File file) throws IOException;

    /** Create a new file. Returns true if created, false if already exists. */
    boolean createFile(File file) throws IOException;

    /** Delete a file. Returns true if deleted, false if it didn't exist. */
    boolean delete(File file);
}

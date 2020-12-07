package net.kibblelands.patcher.utils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class IOUtils {
    public static void delete(File f) throws IOException {
        if (!f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            for (File c : Objects.requireNonNull(f.listFiles())) {
                delete(c);
            }
        }
        if (!f.delete()) {
            throw new IOException("Failed to delete file: " + f);
        }
    }

    public static void mkdirs(File f) throws IOException {
        if (f.isDirectory()) {
            return;
        }
        if (f.isFile()) {
            if (!f.delete()) {
                throw new IOException("Failed to delete file: " + f);
            }
        }
        if (!f.mkdirs()) {
            throw new IOException("Failed to create dir: " + f);
        }
    }
}

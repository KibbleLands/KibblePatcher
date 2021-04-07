package net.kibblelands.patcher.utils;

import net.kibblelands.patcher.KibblePatcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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

    public static List<String> readAllLines(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        LinkedList<String> lines = new LinkedList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        return lines;
    }

    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int nRead;
        baos.reset();
        while ((nRead = inputStream.read(buffer, 0, buffer.length)) != -1) {
            baos.write(buffer, 0, nRead);
        }
        inputStream.close();
        return baos.toByteArray();
    }

    public static Map<String,byte[]> readZIP(final InputStream in) throws IOException {
        ZipInputStream inputStream = new ZipInputStream(in);
        Map<String,byte[]> items = new HashMap<>();
        ZipEntry entry;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int nRead;
        while (null!=(entry=inputStream.getNextEntry())) {
            if (!entry.isDirectory() && !items.containsKey(entry.getName())) {
                baos.reset();
                while ((nRead = inputStream.read(buffer, 0, buffer.length)) != -1) {
                    baos.write(buffer, 0, nRead);
                }
                items.put(entry.getName(), baos.toByteArray());
            }
        }
        in.close();
        return items;
    }

    public static byte[] readResource(String path) throws IOException {
        InputStream inputStream = KibblePatcher.class.getClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            throw new FileNotFoundException(path);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[2048];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            baos.write(data, 0, nRead);
        }
        return baos.toByteArray();
    }

    public static void writeZIP(Map<String, byte[]> items, final OutputStream out) throws IOException {
        final ZipOutputStream zip = new ZipOutputStream(out);
        for (final String path : items.keySet()) {
            final byte[] data = items.get(path);
            final ZipEntry entry = new ZipEntry(path);
            zip.putNextEntry(entry);
            zip.write(data);
        }
        zip.flush();
        zip.close();
    }

    public static String trimJSON(String str) {
        StringBuilder stringBuilder = new StringBuilder();
        int index = 0;
        boolean inString = false, special = false;
        while (index < str.length()) {
            char next = str.charAt(index);
            if (inString) {
                if (special) {
                    special = false;
                } else if (next == '\\') {
                    special = true;
                } else if (next == '\"') {
                    inString = false;
                }
            } else {
                if (next == '\"') {
                    inString = true;
                }
                switch (next) {
                    default:
                        break;
                    case '\"':
                        inString = true;
                        break;
                    case ' ':
                    case '\n':
                    case '\r':
                    case '\t':
                        index++;
                        continue;
                }
            }
            stringBuilder.append(next);
            index++;
        }
        return stringBuilder.toString();
    }
}

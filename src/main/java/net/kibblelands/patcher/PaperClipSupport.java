package net.kibblelands.patcher;

import net.kibblelands.patcher.utils.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class PaperClipSupport {
    private static final File tmp = new File(new File(System.getProperty("java.io.tmpdir")),
            "KibblePatcher-" + UUID.randomUUID().toString()).getAbsoluteFile();
    private static final File javaHome = new File(System.getProperty("java.home")).getAbsoluteFile();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(PaperClipSupport::cleanPaperClip));
    }

    /**
     * @param file is a potential PaperClip.jar
     * @return {@code null} if the jar is not a paperclip jar
     */
    public static String getPaperClipMCVer(File file) {
        boolean paperClip;
        String version = null;
        try(JarFile jarFile = new JarFile(file)) {
            ZipEntry properties = jarFile.getEntry("patch.properties");
            paperClip = properties != null &&
                    jarFile.getEntry("io/papermc/paperclip/Paperclip.class") != null;
            if (paperClip) {
                for (String line :
                        new BufferedReader(new InputStreamReader(jarFile.getInputStream(properties),
                                StandardCharsets.UTF_8)).lines().collect(Collectors.toList())) {
                    if (line.startsWith("version=")) {
                        version = line.substring(8);
                    }
                }
            }
        } catch (IOException ioe) {
            return null;
        }
        return version;
    }

    public static File patchPaperClip(File paperClip,String ver) throws IOException {
        if (ver == null) return paperClip;
        cleanPaperClip();
        IOUtils.mkdirs(tmp);
        int success;
        try {
            File javaEx = new File(javaHome, "bin/java.exe");
            if (!javaEx.exists()) {
                javaEx = new File(javaHome, "bin/java");
            }
            success = new ProcessBuilder(javaEx.getPath(),
                    "-XX:-UseGCOverheadLimit", "-Dpaperclip.patchonly=true",
                    "-jar", paperClip.getAbsolutePath()).directory(tmp)
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start().waitFor();
        } catch (InterruptedException exception) {
            throw new IOException("PaperClipPatchInterrupted!", exception);
        }
        if (success != 0)
            throw new IOException("PaperClipPatchFailed! (Error code: "+success+")");
        return new File(tmp, "cache"+File.separator+"patched_"+ver+".jar");
    }

    public static void cleanPaperClip() {
        try {
            IOUtils.delete(tmp);
        } catch (IOException ignored) {}
    }
}

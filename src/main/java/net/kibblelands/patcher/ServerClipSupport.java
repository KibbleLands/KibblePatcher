package net.kibblelands.patcher;

import net.kibblelands.patcher.utils.ConsoleColors;
import net.kibblelands.patcher.utils.IOUtils;
import net.kibblelands.patcher.utils.logger.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class ServerClipSupport {
    private static final File self = new File(ServerClipSupport.class.getProtectionDomain()
            .getCodeSource().getLocation().getFile()).getAbsoluteFile();
    private static final File tmp = new File(new File(System.getProperty("java.io.tmpdir")),
            "KibblePatcher-" + UUID.randomUUID().toString()).getAbsoluteFile();
    private static final File javaHome = new File(System.getProperty("java.home")).getAbsoluteFile();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ServerClipSupport::cleanServerClip));
    }

    private final String mcVer;
    private final boolean yatoclip;

    private ServerClipSupport(String mcVer, boolean yatoclip) {
        this.mcVer = mcVer;
        this.yatoclip = yatoclip;
    }

    public String getName() {
        return this.yatoclip ? "Yatoclip" : "Paperclip";
    }

    // Secondary main for Yatoclip
    public static void main(String[] args) throws Exception {
        Class.forName("org.yatopiamc.yatoclip.ServerSetup")
                .getDeclaredMethod("setup").invoke(null);
        System.exit(0); // Force exit as soon as possible
    }

    /**
     * @param file is a potential PaperClip.jar
     * @return {@code null} if the jar is not a paperclip jar
     */
    public static ServerClipSupport getServerClipSupport(File file) {
        String version = null;
        try(JarFile jarFile = new JarFile(file)) {
            ZipEntry properties = jarFile.getEntry("patch.properties");
            boolean paperClip = properties != null &&
                    jarFile.getEntry("io/papermc/paperclip/Paperclip.class") != null;
            if (paperClip) {
                for (String line :
                        new BufferedReader(new InputStreamReader(jarFile.getInputStream(properties),
                                StandardCharsets.UTF_8)).lines().collect(Collectors.toList())) {
                    if (line.startsWith("version=")) {
                        version = line.substring(8).trim();
                    }
                }
            }
            if (paperClip && version != null) {
                return new ServerClipSupport(version, false);
            }
            // YatoClip support
            properties = jarFile.getEntry("yatoclip-launch.properties");
            boolean yatoClip = properties != null &&
                    jarFile.getEntry("org/yatopiamc/yatoclip/Yatoclip.class") != null;
            if (yatoClip) {
                for (String line :
                        new BufferedReader(new InputStreamReader(jarFile.getInputStream(properties),
                                StandardCharsets.UTF_8)).lines().collect(Collectors.toList())) {
                    if (line.startsWith("minecraftVersion=")) {
                        version = line.substring(17).trim();
                    }
                }
            }
            if (yatoClip && version != null) {
                return new ServerClipSupport(version, true);
            }
        } catch (IOException ioe) {
            return null;
        }
        return null;
    }

    public File patchServerClip(File paperClip, Logger logger) throws IOException {
        if (mcVer == null) return paperClip;
        cleanServerClip();
        IOUtils.mkdirs(tmp);
        int exitCode;
        try {
            File javaEx = new File(javaHome, "bin/java.exe");
            if (!javaEx.exists()) {
                javaEx = new File(javaHome, "bin/java");
            }
            ProcessBuilder processBuilder;
            if (this.yatoclip) { // Yatoclip doesn't have a patch only mode
                processBuilder = new ProcessBuilder(javaEx.getPath(),
                        "-XX:-UseGCOverheadLimit", "-cp",
                        self.getAbsolutePath() + File.pathSeparator
                                + paperClip.getAbsolutePath(),
                        "net.kibblelands.patcher.ServerClipSupport");
            } else {
                processBuilder = new ProcessBuilder(javaEx.getPath(),
                        "-XX:-UseGCOverheadLimit", "-Dpaperclip.patchonly=true",
                        "-jar", paperClip.getAbsolutePath());
            }
            System.out.println("["+ this.getName()+ " start]");
            final Process process = processBuilder.directory(tmp)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT).start();
            exitCode = process.waitFor();
            System.out.println("["+ this.getName()+ " end]");
        } catch (InterruptedException exception) {
            throw new IOException("PatchInterrupted!", exception);
        }
        if (exitCode != 0)
            throw new IOException("PatchFailed! (Error code: "+exitCode+")");
        if (this.yatoclip) {
            return new File(tmp,"cache" + File.separator + mcVer + File.separator +
                    "Minecraft" + File.separator + mcVer + "-patched.jar");
        } else {
            return new File(tmp, "cache" + File.separator + "patched_" + mcVer + ".jar");
        }
    }

    public static void cleanServerClip() {
        try {
            IOUtils.delete(tmp);
        } catch (IOException ignored) {}
    }
}

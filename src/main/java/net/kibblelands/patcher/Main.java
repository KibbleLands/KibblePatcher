package net.kibblelands.patcher;

import net.kibblelands.patcher.utils.ConsoleColors;
import net.kibblelands.patcher.utils.IOUtils;
import net.kibblelands.patcher.utils.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class Main {
    private static final Logger LOGGER = new Logger("KibblePatcher");

    public static void main(String[] args) throws IOException {
        boolean builtInMode = false;
        boolean builtInModeRewrite = false;
        boolean fullPatch = false;
        String builtInPkg = null;
        if (args.length == 4 && ("-builtin-has-rewrite".equals(args[0]) ||
                (builtInModeRewrite = "-builtin".equals(args[0])))) {
            args = new String[]{args[2], args[3]};
            builtInMode = true;
            builtInPkg = args[1];
        }
        if (args.length == 3 && ("-full".equals(args[0]))) {
            args = new String[]{args[1], args[2]};
            fullPatch = true;
        }
        if (args.length != 2) {
            LOGGER.stdout("Usage: \n" +
                    "    java -jar KibblePatcher.jar <input> <output>\n" +
                    "    java -jar KibblePatcher.jar -patch <file>\n" +
                    "    java -jar KibblePatcher.jar -info <file>\n");
            System.exit(1);
            return;
        }
        if (args[0].equals("-info")) {
            File in = new File(args[1]);
            if (!in.exists()) {
                LOGGER.error("Input file doesn't exists!");
                System.exit(2);
                return;
            }
            ServerClipSupport serverClipSupport = ServerClipSupport.getServerClipSupport(in);
            try (JarFile jarFile = new JarFile(in)) {
                Attributes mainAttributes = jarFile.getManifest().getMainAttributes();
                String version = mainAttributes.getValue("Kibble-Version");
                String versionBuiltIn = mainAttributes.getValue("Kibble-BuiltIn");
                String pluginRewrite = mainAttributes.getValue("Kibble-Rewrite");
                if (pluginRewrite == null && versionBuiltIn != null) {
                    pluginRewrite = "BUILT-IN";
                }
                if (version == null && versionBuiltIn == null) {
                    LOGGER.info("This " + (serverClipSupport != null ?
                            serverClipSupport.getName().toLowerCase() + " " : "") +
                            "server hasn't been patched by KibblePatcher yet.");
                    return;
                }
                LOGGER.info("This server was patched by KibblePatcher" +
                        (version == null ? " built-in mode." : "."));
                if (version != null) {
                    LOGGER.info("Version: " + version);
                }
                if (versionBuiltIn != null) {
                    LOGGER.info("Built-in version: " + versionBuiltIn);
                }
                if (pluginRewrite != null) {
                    LOGGER.info("Plugin rewrite: " + (pluginRewrite.equals("UNSUPPORTED") ?
                            ConsoleColors.YELLOW : ConsoleColors.CYAN) + pluginRewrite);
                }
                JarEntry jarEntry = jarFile.getJarEntry("net/kibblelands/server/changelist.txt");
                if (jarEntry != null) {
                    try {
                        List<String> lines = IOUtils.readAllLines(jarFile.getInputStream(jarEntry));
                        LOGGER.info("Change list: "+ConsoleColors.CYAN+"("+lines.size()+")");
                        for (String line : lines) {
                            LOGGER.info(" " + line);
                        }
                        LOGGER.stdout("");
                    } catch (IOException ignored) {}
                }
            }
            return;
        }
        if (args[0].equals("-patch-full")) {
            args[0] = "-patch";
            fullPatch = true;
        }
        File in = new File(args[args[0].equals("-patch") ? 1 : 0]);
        if (!in.exists()) {
            LOGGER.error("Input file doesn't exists!");
            System.exit(2);
            return;
        }
        KibblePatcher kibblePatcher = new KibblePatcher(LOGGER);
        if (fullPatch) {
            kibblePatcher.featuresPatches = true;
        }
        if (builtInMode) {
            kibblePatcher.compatibilityPatches = false;
            kibblePatcher.featuresPatches = false;
            kibblePatcher.builtInMode = true;
            kibblePatcher.builtInPkg = builtInPkg;
            kibblePatcher.builtInModeRewrite = builtInModeRewrite;
        }
        kibblePatcher.patchServerJar(in, new File(args[1]));
        System.exit(0);
    }
}

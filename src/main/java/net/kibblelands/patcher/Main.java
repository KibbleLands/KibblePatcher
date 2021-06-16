package net.kibblelands.patcher;

import net.kibblelands.patcher.serverclip.ServerClipSupport;
import net.kibblelands.patcher.utils.ConsoleColors;
import net.kibblelands.patcher.utils.IOUtils;
import net.kibblelands.patcher.utils.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class Main {
    private static final Logger LOGGER = new Logger("KibblePatcher");

    public static void main(String[] args) throws IOException {
        boolean fullPatch = false;
        boolean generate = false;
        String xlistFilter = "";
        if (args.length == 3) {
            if (("-full".equals(args[0]))) {
                args = new String[]{args[1], args[2]};
                fullPatch = true;
            } else if (("-generate".equals(args[0]))) {
                args = new String[]{args[1], args[2]};
                generate = true;
            } else if (("-xlist".equals(args[0]))) {
                xlistFilter = args[2];
                args = new String[]{args[0], args[1]};
            }
        }
        if (args.length == 1 && args[0].equals("-links")) {
            KibblePatcher.printSupportLinks(LOGGER);
            return;
        }
        if (args.length != 2) {
            LOGGER.stdout("Usage: \n" +
                    "    java -jar KibblePatcher.jar <input> <output>\n" +
                    "    java -jar KibblePatcher.jar -full <input> <output>\n" +
                    "    java -jar KibblePatcher.jar -patch <file>\n" +
                    "    java -jar KibblePatcher.jar -patch-full <file>\n" +
                    "    java -jar KibblePatcher.jar -generate <input> <output>\n" +
                    "    java -jar KibblePatcher.jar -info <file>\n"+
                    "    java -jar KibblePatcher.jar -links\n");
            System.exit(1);
            return;
        }
        if (generate || args[0].equals("-generate")) {
            File in = new File(args[generate ? 0 : 1]);
            File out;
            if (generate) {
                out = new File(args[1]);
            } else {
                int index = args[1].lastIndexOf('.');
                int index2 = args[1].lastIndexOf(File.separatorChar);
                if (index < index2) index = -1;
                out = new File((index == -1 ? args[1] :
                        args[1].substring(0, index)) + "-generated.jar");
            }
            if (!in.exists()) {
                LOGGER.error("Input file doesn't exists!");
                System.exit(2);
                return;
            }
            if (out.exists()) {
                LOGGER.error("Output file already exists!");
                System.exit(2);
                return;
            }
            ServerClipSupport serverClipSupport =
                    ServerClipSupport.getServerClipSupport(in);
            if (serverClipSupport == null) {
                LOGGER.error("The provided jar is not in a supported server-clip format!");
                System.exit(3);
                return;
            }
            LOGGER.info("Generating " + serverClipSupport.getName().toLowerCase() + " server...");
            File generated = serverClipSupport.patchServerClip(in);
            try {
                Files.move(generated.toPath(), out.toPath());
            } catch (IOException e) {
                LOGGER.error("Failed to write file to output directory!");
                ServerClipSupport.cleanServerClip();
                System.exit(4);
                return;
            }
            LOGGER.info("Finished!");
            ServerClipSupport.cleanServerClip();
            System.exit(0);
            return;
        }
        boolean xlist = false;
        if (args[0].equals("-info") || (xlist = args[0].equals("-xlist"))) {
            File in = new File(args[1]);
            if (!in.exists()) {
                LOGGER.error("Input file doesn't exists!");
                System.exit(2);
                return;
            }
            ServerClipSupport serverClipSupport = ServerClipSupport.getServerClipSupport(in);
            try (JarFile jarFile = new JarFile(in)) {
                {
                    // Try to check zip special files first
                    Enumeration<JarEntry> entryEnumeration = jarFile.entries();
                    Set<String> set = new HashSet<>();
                    while (entryEnumeration.hasMoreElements()) {
                        JarEntry jarEntry = entryEnumeration.nextElement();
                        if (!jarEntry.getName().startsWith(xlistFilter)) continue;
                        boolean dup = !set.add(jarEntry.getName());
                        boolean dataDir = jarEntry.isDirectory() && jarEntry.getSize() != 0;
                        if (dup | dataDir) {
                            LOGGER.warn((dup ? ("Duplicate " + (dataDir ?
                                    "data directory: " : "entry: ")) : "Data directory: ") + jarEntry.getName());
                        } else if (xlist && !jarEntry.isDirectory()) {
                            LOGGER.info("Entry: " + jarEntry.getName());
                        }
                    }
                }
                if (xlist) return;
                Attributes mainAttributes = jarFile.getManifest().getMainAttributes();
                String version = mainAttributes.getValue("Kibble-Version");
                String versionBuiltIn = mainAttributes.getValue("Kibble-BuiltIn");
                String pluginRewrite = mainAttributes.getValue("Kibble-Rewrite");
                if (pluginRewrite == null && versionBuiltIn != null) {
                    pluginRewrite = "BUILT-IN";
                }
                if (version == null && versionBuiltIn == null) {
                    if (pluginRewrite != null && !pluginRewrite.equals("UNSUPPORTED") ) {
                        LOGGER.info("Plugin rewrite: " + ConsoleColors.CYAN + pluginRewrite);
                    }
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
        try {
            kibblePatcher.patchServerJar(in, new File(args[1]));
            System.exit(0);
        } catch (Exception e) {
            LOGGER.error("Oops, something went wrong!");
            KibblePatcher.printSupportLinks(LOGGER);
            throw e;
        }
    }
}

package net.kibblelands.patcher;

import net.kibblelands.patcher.utils.logger.Logger;

import java.io.File;
import java.io.IOException;

public class Main {
    private static final Logger LOGGER = new Logger("KibblePatcher");

    public static void main(String[] args) throws IOException {
        boolean builtInMode = false;
        boolean builtInModeRewrite = false;
        String builtInPkg = null;
        if (args.length == 4 && ("-builtin-has-rewrite".equals(args[0]) ||
                (builtInModeRewrite = "-builtin".equals(args[0])))) {
            args = new String[]{args[2], args[3]};
            builtInMode = true;
            builtInPkg = args[1];
        }
        // Kept for backward compatibility reasons
        // Shortcut for -builtin-has-rewrite org/yatopiamc/yatopia/server/util/
        if (args.length == 3 && ("-yatopia".equals(args[0]))) {
            LOGGER.warn("The \"-yatopia\" argument is deprecated and will be removed in a future release!");
            LOGGER.warn("Please use \"-builtin-has-rewrite org/yatopiamc/yatopia/server/util/\" instead.");
            args = new String[]{args[1], args[2]};
            builtInMode = true;
            builtInPkg = "org/yatopiamc/yatopia/server/util/";
        }
        if (args.length != 2) {
            LOGGER.stdout("Usage: \n" +
                    "    java -jar KibblePatcher.jar <input> <output>\n" +
                    "    java -jar KibblePatcher.jar -patch <file>\n");
            System.exit(1);
            return;
        }
        File in = new File(args[args[0].equals("-patch") ? 1 : 0]);
        if (!in.exists()) {
            LOGGER.error("Input doesn't exists!");
            System.exit(2);
            return;
        }
        KibblePatcher kibblePatcher = new KibblePatcher(LOGGER);
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

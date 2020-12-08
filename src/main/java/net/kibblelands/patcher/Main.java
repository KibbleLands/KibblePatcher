package net.kibblelands.patcher;

import net.kibblelands.patcher.utils.logger.Logger;

import java.io.File;
import java.io.IOException;

public class Main {
    private static final Logger LOGGER = new Logger("KibblePatcher");

    public static void main(String[] args) throws IOException {
        boolean yatopiaMode = false;
        if (args.length == 3 && "-yatopia".equals(args[0])) {
            args = new String[]{args[1], args[2]};
            yatopiaMode = true;
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
        if (yatopiaMode) {
            kibblePatcher.compatibilityPatches = false;
            kibblePatcher.featuresPatches = false;
            kibblePatcher.yatopiaMode = true;
        }
        kibblePatcher.patchServerJar(in, new File(args[1]));
        System.exit(0);
    }
}

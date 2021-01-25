package net.kibblelands.patcher;

import net.kibblelands.patcher.utils.ConsoleColors;
import net.kibblelands.patcher.utils.logger.Logger;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * DynamicGenerator allow patches to generate commonly used code
 *
* */
public class CommonGenerator {
    private final List<String> changes;

    private final String NMS;
    private final boolean features;

    public CommonGenerator(String NMS,boolean features) {
        this.NMS = NMS;
        this.features = features;
        this.changes = new LinkedList<>();
    }

    public String getNMS() {
        return NMS;
    }

    public boolean hasFeatures() {
        return features;
    }

    public void addChangeEntry(String value) {
        this.changes.add(value);
    }

    void generate(Map<String,byte[]> inject, Map<String,byte[]> srv) {
        if (this.features) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(baos);
            for (String change: this.changes) {
                printStream.println(change);
            }
            inject.put("net/kibblelands/server/changelist.txt", baos.toByteArray());
        }
    }

    public void printChanges(Logger logger) {
        logger.info("Change list: "+ ConsoleColors.CYAN + "(" + this.changes.size() + ")");
        for (String change: this.changes) {
            logger.info(" "+change);
        }
        logger.stdout("");
    }
}

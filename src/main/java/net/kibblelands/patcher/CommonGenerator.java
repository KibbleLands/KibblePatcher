package net.kibblelands.patcher;

import net.kibblelands.patcher.mapper.NMSMapper;
import net.kibblelands.patcher.utils.ConsoleColors;
import net.kibblelands.patcher.utils.logger.Logger;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * CommonGenerator allow patches to generate commonly used code
* */
public class CommonGenerator {
    private final List<String> changes;

    private final NMSMapper mapper;
    private final boolean features;

    public CommonGenerator(NMSMapper mapper, boolean features) {
        this.mapper = mapper;
        this.features = features;
        this.changes = new LinkedList<>();
    }

    public String getMapperInfo() {
        return this.mapper.toString();
    }

    public String mapClass(String text) {
        return this.mapper.mapClass(text);
    }

    public String mapDesc(String text) {
        return this.mapper.mapDesc(text);
    }

    public String mapMethodName(String owner,String name) {
        return this.mapper.mapMethodName(owner, name);
    }

    public String mapFieldName(String owner,String name) {
        return this.mapper.mapFieldName(owner, name);
    }

    public MethodInsnNode mapMethodInsn(MethodInsnNode methodInsnNode) {
        methodInsnNode.owner = this.mapper.mapClass(methodInsnNode.owner);
        methodInsnNode.name = this.mapper.mapMethodName(methodInsnNode.owner, methodInsnNode.name);
        methodInsnNode.desc = this.mapper.mapDesc(methodInsnNode.desc);
        return methodInsnNode;
    }

    public boolean hasFeatures() {
        return features;
    }

    public void addChangeEntry(String value) {
        this.changes.add(value);
    }

    void generate(Map<String,byte[]> inject, Map<String,byte[]> srv) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(baos);
        for (String change : this.changes) {
            printStream.println(change.replace(ConsoleColors.CYAN, ""));
        }
        inject.put("net/kibblelands/server/changelist.txt", baos.toByteArray());
    }

    public void printChanges(Logger logger) {
        logger.info("Change list: "+ ConsoleColors.CYAN + "(" + this.changes.size() + ")");
        for (String change: this.changes) {
            logger.info(" "+change);
        }
        logger.stdout("");
    }
}

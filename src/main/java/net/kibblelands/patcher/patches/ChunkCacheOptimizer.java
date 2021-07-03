package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.Map;

public class ChunkCacheOptimizer implements Opcodes {
    private static final String NMS_CHUNK_PROVIDER = "net/minecraft/server/$NMS/ChunkProviderServer";
    private static final String NMS_CHUNK_STATUS = "net/minecraft/server/$NMS/IChunkAccess";
    private static final String NMS_CHUNK_ACCESS = "net/minecraft/server/$NMS/ChunkStatus";

    public static void patch(CommonGenerator commonGenerator,Map<String, byte[]> map) {
        String CHUNK_PROVIDER = commonGenerator.mapClass(NMS_CHUNK_PROVIDER);
        String CHUNK_STATUS = commonGenerator.mapClass(NMS_CHUNK_STATUS);
        String CHUNK_ACCESS = commonGenerator.mapClass(NMS_CHUNK_ACCESS);
        if (map.get(CHUNK_PROVIDER + ".class") == null) {
            return;
        }
        ClassNode classNode = new ClassNode();
        new ClassReader(map.get(CHUNK_PROVIDER + ".class")).accept(classNode, 0);
        String cachePos = commonGenerator.mapFieldName(CHUNK_PROVIDER, "cachePos");
        if (!ASMUtils.hasField(classNode, cachePos) && // hasFieldByDesc fix 1.17 check
                !ASMUtils.hasFieldByDesc(classNode, "[L" + CHUNK_STATUS + ";")) {
            return; // Incompatible version (Like 1.8)
        }
        FieldNode fieldNode1 = ASMUtils.findFieldByDesc(classNode, "[J"); // J -> long
        FieldNode fieldNode2 = ASMUtils.findFieldByDesc(classNode, "[L" + CHUNK_STATUS + ";");
        FieldNode fieldNode3 = ASMUtils.findFieldByDesc(classNode, "[L" + CHUNK_ACCESS + ";");
        if (fieldNode1 == null || fieldNode2 == null || fieldNode3 == null) {
            System.out.println("An optimisation has failed in an unexpected way. please report the issue with your server jar!");
            System.out.println("NMS: "+commonGenerator.getMapperInfo());
            System.out.println("Debug state: "+ Arrays.toString(
                    new boolean[]{fieldNode1 == null, fieldNode2 == null, fieldNode3 == null}));
            return; // Incompatible version!?
        }
        boolean didWork = false;
        boolean didMatch = false;
        for (MethodNode methodNode:classNode.methods) {
            for (AbstractInsnNode abstractInsnNode:methodNode.instructions.toArray()) {
                if (match(abstractInsnNode, CHUNK_PROVIDER, fieldNode1, fieldNode2, fieldNode3)) {
                    didMatch = true;
                    AbstractInsnNode previous = abstractInsnNode.getPrevious();
                    while (previous != null &&
                            !match(previous, CHUNK_PROVIDER, fieldNode1, fieldNode2, fieldNode3)) {
                        if (previous.getOpcode() == ICONST_3) {
                            methodNode.instructions.set(previous, new IntInsnNode(BIPUSH, 15));
                            didWork = true;
                            break;
                        } else if (previous.getOpcode() == ICONST_4) {
                            methodNode.instructions.set(previous, new IntInsnNode(BIPUSH, 16));
                            didWork = true;
                            break;
                        }
                        previous = previous.getPrevious();
                    }
                }
            }
        }
        if (!didWork) {
            System.out.println("An optimisation has failed in an unexpected way. please report the issue with your server jar!");
            System.out.println("NMS: "+commonGenerator.getMapperInfo());
            System.out.println("Debug state: Why it is the way it is" + (didMatch ? "!" : "?"));
            return;
        }
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(CHUNK_PROVIDER + ".class", classWriter.toByteArray());
        commonGenerator.addChangeEntry("Increased Chuck cache size. " + ConsoleColors.CYAN + "(Optimisation)");
    }

    private static boolean match(AbstractInsnNode abstractInsnNode, String asmName,
                                 FieldNode fieldNode1,FieldNode fieldNode2, FieldNode fieldNode3) {
        return (abstractInsnNode.getOpcode() == GETFIELD || abstractInsnNode.getOpcode() == PUTFIELD) &&
                ((FieldInsnNode) abstractInsnNode).owner.equals(asmName) && (
                fieldNode1.desc.equals(((FieldInsnNode) abstractInsnNode).desc) ||
                        fieldNode2.desc.equals(((FieldInsnNode) abstractInsnNode).desc) ||
                        fieldNode3.desc.equals(((FieldInsnNode) abstractInsnNode).desc)
        );
    }
}

package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.Map;

public class ChunkCacheOptimizer implements Opcodes {
    private static final String NMS_CHUNK_PROVIDER = "net/minecraft/server/$NMS/ChunkProviderServer.class";
    private static final String NMS_CHUNK_STATUS = "net/minecraft/server/$NMS/IChunkAccess";
    private static final String NMS_CHUNK_ACCESS = "net/minecraft/server/$NMS/ChunkStatus";
    private static final String NMS_CHUNK = "net/minecraft/server/$NMS/Chunk";

    public static void patch(CommonGenerator commonGenerator,Map<String, byte[]> map, final int[] stats) {
        String CHUNK_PROVIDER = commonGenerator.mapClass(NMS_CHUNK_PROVIDER);
        String CHUNK_STATUS = commonGenerator.mapClass(NMS_CHUNK_STATUS);
        String CHUNK_ACCESS = commonGenerator.mapClass(NMS_CHUNK_ACCESS);
        String CHUNK = commonGenerator.mapClass(NMS_CHUNK);
        if (map.get(CHUNK_PROVIDER) == null) {
            return;
        }
        ClassNode classNode = new ClassNode();
        new ClassReader(map.get(CHUNK_PROVIDER)).accept(classNode, 0);
        String cachePos = commonGenerator.mapFieldName(CHUNK_PROVIDER, "cachePos");
        if (!ASMUtils.hasField(classNode, cachePos) && // hasFieldByDesc fix 1.17 check
                !ASMUtils.hasFieldByDesc(classNode, "[L" + CHUNK_STATUS + ";")) {
            return; // Incompatible version (Like 1.8)
        }
        MethodNode methodNode = ASMUtils.findMethodByDesc(classNode,
                "(JL" + CHUNK_ACCESS +";L" + CHUNK_STATUS + ";)V");
        MethodNode methodNode2 = ASMUtils.findMethodByDesc(classNode,
                "(IIL" + CHUNK_STATUS + ";Z)L" + CHUNK_ACCESS + ";");
        MethodNode methodNode3 = ASMUtils.findMethodByDesc(classNode, "(II)L" + CHUNK + ";");
        MethodNode methodNode4 = ASMUtils.findBaseConstructor(classNode);
        if (methodNode == null || methodNode2 == null || methodNode3 == null || methodNode4 == null) {
            System.out.println("An optimisation has failed in an unexpected way. please report the issue with your server jar!");
            System.out.println("NMS: "+commonGenerator.getMapperInfo());
            System.out.println("Debug state: "+ Arrays.toString(
                    new boolean[]{methodNode == null, methodNode2 == null, methodNode3 == null, methodNode4 == null}));
            return; // Incompatible version!?
        }
        // TODO Change code to a more future proof and generic way
        InsnNode int3 = new InsnNode(ICONST_3);
        InsnNode int4 = new InsnNode(ICONST_4);
        IntInsnNode int31 = new IntInsnNode(BIPUSH, 15);
        IntInsnNode int32 = new IntInsnNode(BIPUSH, 16);
        int opts = 0;
        opts += ASMUtils.replaceInstruction(methodNode, int3, int31);
        opts += ASMUtils.replaceInstruction(methodNode2, int4, int32);
        opts += ASMUtils.replaceInstruction(methodNode3, int4, int32);
        opts += ASMUtils.replaceInstruction(methodNode4, int4, int32);
        if (opts != 5) {
            System.out.println("X -> " + opts);
            return;
        }
        stats[4] += 1;
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(CHUNK_PROVIDER, classWriter.toByteArray());
        commonGenerator.addChangeEntry("Increased Chuck cache size. " + ConsoleColors.CYAN + "(Optimisation)");
    }
}

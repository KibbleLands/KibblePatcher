package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.utils.ASMUtils;
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
    private static final String CHUNK_PROVIDER = "net/minecraft/server/$NMS/ChunkProviderServer.class";

    public static void patch(Map<String, byte[]> map, String mth, final int[] stats) {
        String NMS = mth.substring(21, mth.lastIndexOf('/'));
        String CHUNK_PROVIDER_RESOLVED = CHUNK_PROVIDER.replace("$NMS", NMS);
        if (map.get(CHUNK_PROVIDER_RESOLVED) == null) {
            return;
        }
        ClassNode classNode = new ClassNode();
        new ClassReader(map.get(CHUNK_PROVIDER_RESOLVED)).accept(classNode, 0);
        if (!ASMUtils.hasField(classNode, "cachePos")) {
            return; // Incompatible version (Like 1.8)
        }
        MethodNode methodNode = ASMUtils.findMethodByDesc(classNode,
                "(JLnet/minecraft/server/$NMS/IChunkAccess;Lnet/minecraft/server/$NMS/ChunkStatus;)V"
                        .replace("$NMS", NMS));
        MethodNode methodNode2 = ASMUtils.findMethodByDesc(classNode,
                "(IILnet/minecraft/server/$NMS/ChunkStatus;Z)Lnet/minecraft/server/$NMS/IChunkAccess;"
                        .replace("$NMS", NMS));
        MethodNode methodNode3 = ASMUtils.findMethodByDesc(classNode,
                "(II)Lnet/minecraft/server/$NMS/Chunk;"
                        .replace("$NMS", NMS));
        MethodNode methodNode4 = ASMUtils.findBaseConstructor(classNode);
        if (methodNode == null || methodNode2 == null || methodNode3 == null || methodNode4 == null) {
            System.out.println("An optimisation has failed in an unexpected way. please report the issue with your server jar!");
            System.out.println("NMS: "+NMS);
            System.out.println("Debug state: "+ Arrays.toString(
                    new boolean[]{methodNode == null, methodNode2 == null, methodNode3 == null, methodNode4 == null}));
            return; // Incompatible version!?
        }
        InsnNode int3 = new InsnNode(ICONST_3);
        InsnNode int4 = new InsnNode(ICONST_4);
        IntInsnNode int31 = new IntInsnNode(BIPUSH, 31);
        IntInsnNode int32 = new IntInsnNode(BIPUSH, 32);
        int opts = 0;
        opts += ASMUtils.replaceInstruction(methodNode, int3, int31);
        opts += ASMUtils.replaceInstruction(methodNode2, int4, int32);
        opts += ASMUtils.replaceInstruction(methodNode3, int4, int32);
        opts += ASMUtils.replaceInstruction(methodNode4, int4, int32);
        if (opts != 4) {
            return;
        }
        stats[4] += 1;
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(CHUNK_PROVIDER_RESOLVED, classWriter.toByteArray());
    }
}

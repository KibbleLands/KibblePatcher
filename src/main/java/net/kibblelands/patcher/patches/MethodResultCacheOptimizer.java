package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.ASMUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

public class MethodResultCacheOptimizer implements Opcodes {
    private static final String FURNACE_TILE = "net/minecraft/server/$NMS/TileEntityFurnace.class";

    public static void patch(Map<String, byte[]> map, String mth, final int[] stats) {
        String NMS = mth.substring(21, mth.lastIndexOf('/'));
        byte[] bytes = map.get(FURNACE_TILE.replace("$NMS", NMS));
        if (bytes != null) {
            ClassNode classNode = new ClassNode();
            new ClassReader(bytes).accept(classNode, 0);
            MethodNode methodNode = ASMUtils.findMethodBySignature(classNode, "()Ljava/util/Map<Lnet/minecraft/server/$NMS/Item;Ljava/lang/Integer;>;".replace("$NMS", NMS));
            if (methodNode != null) {
                cacheMethodResult(classNode, methodNode);
                stats[4]++;
            }
            ClassWriter classWriter = new ClassWriter(0);
            classNode.accept(classWriter);
            map.put(FURNACE_TILE.replace("$NMS", NMS), classWriter.toByteArray());
        }
    }

    public static void cacheMethodResult(ClassNode classNode, MethodNode methodNode) {
        String resultDesc = methodNode.desc.substring(methodNode.desc.lastIndexOf(')') + 1);
        if (resultDesc.length() == 1 && "IBS".indexOf(resultDesc.charAt(0)) == -1) throw new IllegalArgumentException("cacheMethodResult Only work with non primitive result methods");
        if (methodNode.instructions == null || methodNode.instructions.size() == 0) throw new IllegalArgumentException("The method need an actual body content!");
        String resultSign = (methodNode.signature == null) ? null :
                methodNode.signature.substring(methodNode.signature.lastIndexOf(')') + 1);
        if (resultDesc.equals(resultSign)) resultSign = null;
        final String fName = methodNode.name + "$cache";
        boolean isStatic = (methodNode.access & ACC_STATIC) != 0;
        classNode.fields.add(new FieldNode(ACC_PUBLIC|(methodNode.access & ACC_STATIC), fName, resultDesc, resultSign, null));
        final int RETURN_OPCODE = resultDesc.length() == 1 ? IRETURN : ARETURN;
        final int UNSET_OPCODE = resultDesc.length() == 1 ? IFEQ : IFNULL;
        InsnList begin = new InsnList();
        if (!isStatic) {
            begin.add(new VarInsnNode(ALOAD, 0));
        }
        begin.add(new FieldInsnNode(isStatic?GETSTATIC:GETFIELD, classNode.name, fName, resultDesc));
        begin.add(new InsnNode(DUP));
        LabelNode labelNode = new LabelNode();
        begin.add(new JumpInsnNode(UNSET_OPCODE, labelNode));
        begin.add(new InsnNode(RETURN_OPCODE));
        begin.add(labelNode);
        for (AbstractInsnNode insn: methodNode.instructions.toArray()) {
            if (insn.getOpcode() == RETURN_OPCODE) {
                InsnList insns = new InsnList();
                insns.add(new InsnNode(DUP));
                if (!isStatic) {
                    insns.add(new VarInsnNode(ALOAD, 0));
                    insns.add(new InsnNode(SWAP));
                }
                insns.add(new FieldInsnNode(isStatic?PUTSTATIC:PUTFIELD, classNode.name, fName, resultDesc));
                methodNode.instructions.insertBefore(insn, insns);
            }
        }
        methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), begin);
    }
}

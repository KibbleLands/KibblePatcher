package fr.kibblesland.patcher;

import org.objectweb.asm.tree.*;

public class ASMUtils {
    public static boolean hasField(ClassNode classNode,String fieldName) {
        for (FieldNode fieldNode:classNode.fields) {
            if (fieldNode.name.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    public static MethodNode findMethodByDesc(ClassNode classNode,String descriptor) {
        for (MethodNode methodNode:classNode.methods) {
            if ((!methodNode.name.startsWith("<")) && methodNode.desc.equals(descriptor)) {
                return methodNode;
            }
        }
        return null;
    }

    public static MethodNode findBaseConstructor(ClassNode classNode) {
        for (MethodNode methodNode:classNode.methods) {
            if (methodNode.name.equals("<init>")) {
                return methodNode;
            }
        }
        return null;
    }

    /**
     * @param methodNode The patched method
     * @param from Original instruction
     * @param to Target instruction
     * @return Number of patched instructions
     */
    public static int replaceInstruction(MethodNode methodNode, AbstractInsnNode from,AbstractInsnNode to) {
        int patches = 0;
        for (AbstractInsnNode current:methodNode.instructions.toArray()) {
            if (!equals(current, from)) {
                continue;
            }
            methodNode.instructions.set(current, to.clone(null));
            patches++;
        }
        return patches;
    }

    public static boolean equals(AbstractInsnNode insn1,AbstractInsnNode insn2) {
        return insn1.getOpcode() == insn2.getOpcode() && insn1.getClass().equals(insn2.getClass())
                && (!(insn1 instanceof IntInsnNode) || ((IntInsnNode) insn1).operand == ((IntInsnNode) insn2).operand);
    }
}

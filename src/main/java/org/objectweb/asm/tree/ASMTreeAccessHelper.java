package org.objectweb.asm.tree;

public class ASMTreeAccessHelper {
    public static void setOpcode(AbstractInsnNode insnNode,int opcode) {
        insnNode.opcode = opcode;
    }
}

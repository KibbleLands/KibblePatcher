package net.kibblelands.patcher;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class Optimizer implements Opcodes {
    static void optimize(MethodNode methodNode,final int[] stats) {
        for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
            AbstractInsnNode previous = insnNode.getPrevious();
            switch (insnNode.getOpcode()) {
                case F2D:
                    if (previous.getOpcode() == D2F) {
                        methodNode.instructions.remove(previous);
                        methodNode.instructions.remove(insnNode);
                    }
                    break;
                case D2F:
                    if (previous.getOpcode() == F2D) {
                        methodNode.instructions.remove(previous);
                        methodNode.instructions.remove(insnNode);
                    }
                    break;
                case POP:
                    if (previous.getOpcode() == DUP) {
                        methodNode.instructions.remove(previous);
                        methodNode.instructions.remove(insnNode);
                    }
                    break;
                case POP2:
                    if (previous.getOpcode() == DUP2) {
                        methodNode.instructions.remove(previous);
                        methodNode.instructions.remove(insnNode);
                    }
                    break;
            }
        }
    }
}

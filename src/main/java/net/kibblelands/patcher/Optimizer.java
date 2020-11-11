package net.kibblelands.patcher;

import net.kibblelands.patcher.utils.ASMUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class Optimizer implements Opcodes {
    static void optimize(MethodNode methodNode,final int[] stats) {
        for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
            AbstractInsnNode previous = insnNode.getPrevious();
            switch (insnNode.getOpcode()) {
                case DDIV:
                    if (previous.getType() == AbstractInsnNode.LDC_INSN && (Double) ((LdcInsnNode) previous).cst == 2.0D) {
                        ASMUtils.setOpcode(insnNode, DMUL);
                        ((LdcInsnNode) previous).cst = 0.5D;
                        stats[2]++;
                    }
                    break;
                case FDIV:
                    if (previous.getType() == AbstractInsnNode.LDC_INSN && (
                            previous.getOpcode() == FCONST_2 || (Float) ((LdcInsnNode) previous).cst == 2.0F)) {
                        ASMUtils.setOpcode(insnNode, FMUL);
                        if (previous.getOpcode() == FCONST_2) {
                            methodNode.instructions.insertBefore(previous, new LdcInsnNode(0.5F));
                            methodNode.instructions.remove(previous);
                        } else {
                            ((LdcInsnNode) previous).cst = 0.5F;
                        }
                        stats[2]++;
                    }
                    break;
                case IDIV: {
                    switch (previous.getOpcode()) {
                        case ICONST_2:
                            ASMUtils.setOpcode(previous, ICONST_1);
                            ASMUtils.setOpcode(insnNode, ISHR);
                            stats[2]++;
                            break;
                        case ICONST_4:
                            ASMUtils.setOpcode(previous, ICONST_2);
                            ASMUtils.setOpcode(insnNode, ISHR);
                            stats[2]++;
                            break;
                    }
                    break;
                }
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
            }
        }
    }
}

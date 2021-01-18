package net.kibblelands.patcher.utils;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ASMUtils implements Opcodes {
    public static final int ASM_BUILD = ASM9;

    public static boolean hasField(ClassNode classNode,String fieldName) {
        for (FieldNode fieldNode:classNode.fields) {
            if (fieldNode.name.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasMethod(ClassNode classNode,String methodName) {
        return findMethod(classNode, methodName) != null;
    }

    public static boolean hasMethod(ClassNode classNode,String methodName, String methodDesc) {
        return findMethod(classNode, methodName, methodDesc) != null;
    }

    public static MethodNode findMethod(ClassNode classNode,String methodName) {
        return findMethod(classNode, methodName, null);
    }

    public static MethodNode findMethod(ClassNode classNode,String methodName, String methodDesc) {
        for (MethodNode methodNode:classNode.methods) {
            if (methodNode.name.equals(methodName)
                    && (methodDesc == null || methodNode.desc.equals(methodDesc))) {
                return methodNode;
            }
        }
        return null;
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

    public static MethodNode findMethodBySignature(ClassNode classNode,String signature) {
        for (MethodNode methodNode:classNode.methods) {
            if ((!methodNode.name.startsWith("<")) && signature.equals(methodNode.signature)) {
                return methodNode;
            }
        }
        return null;
    }

    public static FieldNode findFieldBySignature(ClassNode classNode,String signature) {
        for (FieldNode fieldNode:classNode.fields) {
            if ((!fieldNode.name.startsWith("<")) && signature.equals(fieldNode.signature)) {
                return fieldNode;
            }
        }
        return null;
    }

    public static FieldNode findFieldByTypeOrOptional(ClassNode classNode,String type) {
        FieldNode fieldNode = findFieldBySignature(classNode, "Ljava/util/Optional<L" + type + ";>;");
        return fieldNode != null ? fieldNode : findFieldByDesc(classNode, "L" + type + ";");
    }

    public static FieldNode findFieldByDesc(ClassNode classNode,String desc) {
        for (FieldNode fieldNode:classNode.fields) {
            if ((!fieldNode.name.startsWith("<")) && desc.equals(fieldNode.desc)) {
                return fieldNode;
            }
        }
        return null;
    }

    public static FieldNode findFieldByDescIndex(ClassNode classNode,String desc,int index) {
        for (FieldNode fieldNode:classNode.fields) {
            if ((!fieldNode.name.startsWith("<")) && desc.equals(fieldNode.desc)) {
                if (index == 0) {
                    return fieldNode;
                } else {
                    index--;
                }
            }
        }
        return null;
    }

    public static void setOpcode(AbstractInsnNode insnNode,int opcode) {
        ASMTreeAccessHelper.setOpcode(insnNode, opcode);
    }

    public static boolean isLambda(InvokeDynamicInsnNode dynamicInsnNode) {
        final Handle bsm = dynamicInsnNode.bsm;
        return bsm.getTag() == H_INVOKESTATIC && bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                && bsm.getName().equals("metafactory") && bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
                && dynamicInsnNode.bsmArgs.length >= 2 && dynamicInsnNode.bsmArgs[1] instanceof Handle;
    }

    public static LdcInsnNode findLdc(InsnList insnList,Object value) {
        if (value == null) return null;
        for (AbstractInsnNode insnNode:insnList) {
            if (insnNode instanceof LdcInsnNode &&
                    value.equals(((LdcInsnNode) insnNode).cst)) {
                return (LdcInsnNode) insnNode;
            }
        }
        return null;
    }

    public static InsnList copyInsnList(InsnList insnList) {
        Map<LabelNode, LabelNode> map = new IdentityHashMap<LabelNode, LabelNode>() {
            @Override
            public LabelNode get(Object key) {
                LabelNode labelNode = super.get(key);
                return labelNode == null ? (LabelNode) key : labelNode;
            }
        };
        for (AbstractInsnNode abstractInsnNode : insnList) {
            if (abstractInsnNode instanceof LabelNode) {
                map.put((LabelNode) abstractInsnNode, new LabelNode());
            }
        }
        InsnList copy = new InsnList();
        for (AbstractInsnNode abstractInsnNode : insnList) {
            copy.add(abstractInsnNode.clone(map));
        }
        return copy;
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

    public static void symlinkMethod(ClassNode classNode, String target,String link) {
        int actions = 0;
        boolean isInterface = (classNode.access & ACC_INTERFACE) != 0;
        for (MethodNode methodNode: classNode.methods.toArray(new MethodNode[0])) {
            if (methodNode.name.equals(target)) {
                MethodNode methodNodeSymlink = new MethodNode((methodNode.access|ACC_SYNTHETIC)&(~ACC_ABSTRACT), link,
                        methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(new String[0]));
                boolean isStatic = (methodNode.access&ACC_STATIC) != 0;
                LabelNode labelNode = new LabelNode();
                methodNodeSymlink.instructions.add(labelNode);
                methodNodeSymlink.instructions.add(new LineNumberNode(12345, labelNode));
                if (!isStatic) {
                    methodNodeSymlink.instructions.add(new VarInsnNode(ALOAD, 0));
                }
                Type[] types = Type.getMethodType(methodNode.desc).getArgumentTypes();
                for (int i = 0;i < types.length; i++) {
                    methodNodeSymlink.instructions.add(
                            new VarInsnNode(types[i].getOpcode(ILOAD), i + (isStatic ? 0 : 1)));
                }
                methodNodeSymlink.instructions.add(new MethodInsnNode(isStatic ? INVOKESTATIC : (isInterface ?
                        INVOKEINTERFACE : INVOKEVIRTUAL), classNode.name, methodNode.name, methodNode.desc, isInterface));
                methodNodeSymlink.instructions.add(new InsnNode(
                        Type.getMethodType(methodNode.desc).getReturnType().getOpcode(IRETURN)));
                classNode.methods.add(methodNodeSymlink);
                actions++;
            }
        }
        if (actions != 0 && isInterface && classNode.version < V1_8) {
            classNode.version = V1_8;
        }
    }

    public static void createStub(ClassNode classNode, String name,String desc) {
        createStub(classNode, name, desc, false);
    }

    public static void createStaticStub(ClassNode classNode, String name,String desc) {
        createStub(classNode, name, desc, true);
    }

    public static void createStub(ClassNode classNode, String name,String desc, boolean isStatic) {
        MethodNode methodNodeStub = new MethodNode(ACC_PUBLIC|
                ACC_SYNTHETIC|(isStatic?ACC_STATIC:0), name, desc, null, null);
        LabelNode labelNode = new LabelNode();
        methodNodeStub.instructions.add(labelNode);
        methodNodeStub.instructions.add(new LineNumberNode(12345, labelNode));
        int opcode = Type.getMethodType(desc).getReturnType().getOpcode(IRETURN);
        switch (opcode) {
            default:
                throw new Error("Unsupported opcode "+opcode+" for desc "+desc);
            case RETURN:
                break;
            case IRETURN:
                methodNodeStub.instructions.add(new InsnNode(ICONST_0));
                break;
            case ARETURN:
                if (desc.endsWith(")Lorg/bukkit/inventory/ItemStack;")) {
                    methodNodeStub.instructions.add(new TypeInsnNode(NEW, "org/bukkit/inventory/ItemStack"));
                    methodNodeStub.instructions.add(new InsnNode(DUP));
                    methodNodeStub.instructions.add(new MethodInsnNode(
                            INVOKESPECIAL, "org/bukkit/inventory/ItemStack", "<init>", "()V"));
                } else {
                    methodNodeStub.instructions.add(new InsnNode(ACONST_NULL));
                }
                break;
        }
        methodNodeStub.instructions.add(new InsnNode(opcode));
        classNode.methods.add(methodNodeStub);
        if ((classNode.access & ACC_INTERFACE) != 0 && classNode.version < V1_8) {
            classNode.version = V1_8;
        }
    }

    public static boolean equals(AbstractInsnNode insn1,AbstractInsnNode insn2) {
        return insn1.getOpcode() == insn2.getOpcode()
                && (!(insn1 instanceof IntInsnNode) || ((IntInsnNode) insn1).operand == ((IntInsnNode) insn2).operand);
    }

    public static List<AbstractInsnNode> findNodes(InsnList instructions, Predicate<AbstractInsnNode> check){
        List<AbstractInsnNode> founds = new LinkedList<>();
        for (AbstractInsnNode node : instructions) {
            if(check.test(node)){
                founds.add(node);
            }
        }
        return founds;
    }

    public static AbstractInsnNode getNumberInsn(int number) {
        if (number >= -1 && number <= 5)
            return new InsnNode(number + 3);
        else if (number >= -128 && number <= 127)
            return new IntInsnNode(Opcodes.BIPUSH, number);
        else if (number >= -32768 && number <= 32767)
            return new IntInsnNode(Opcodes.SIPUSH, number);
        else
            return new LdcInsnNode(number);
    }
}

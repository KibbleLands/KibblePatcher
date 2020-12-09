package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.utils.ASMUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

public class PluginRewriteOptimiser implements Opcodes {
    private static final String COMMODORE_MV = "org/bukkit/craftbukkit/$NMS/util/Commodore$1$1.class";

    public static void patch(Map<String, byte[]> map, String mth, final boolean[] plRewrite) {
        String NMS = mth.substring(21, mth.lastIndexOf('/'));
        String COMMODORE_MV = PluginRewriteOptimiser.COMMODORE_MV.replace("$NMS", NMS);
        byte[] methodVisitor = map.get(COMMODORE_MV);
        if (methodVisitor == null) {
            return; // Unsupported
        }
        ClassNode classNode = new ClassNode();
        new ClassReader(methodVisitor).accept(classNode, 0);
        MethodNode methodNode = ASMUtils.findMethod(classNode, "visitMethodInsn");
        if (methodNode == null) {
            return; // Unsupported
        }
        InsnList insnNodes = new InsnList();
        LabelNode skip = new LabelNode();
        LabelNode nextOpcode = new LabelNode();
        /*
        if (opcode == INVOKESTATIC &&
            (owner.equals("java/lang/Math") || owner.equals("java/lang/StrictMath"))
                 && (name.equals("sin") || name.equals("cos"))) {
        * */
        insnNodes.add(new VarInsnNode(ILOAD, 1));
        insnNodes.add(ASMUtils.getNumberInsn(INVOKESTATIC));
        insnNodes.add(new JumpInsnNode(IF_ICMPNE, nextOpcode));
        LabelNode ownerMatch = new LabelNode();
        insnNodes.add(new VarInsnNode(ALOAD, 2));
        insnNodes.add(new LdcInsnNode("java/lang/Math"));
        insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        insnNodes.add(new JumpInsnNode(IFNE, ownerMatch));
        insnNodes.add(new VarInsnNode(ALOAD, 2));
        insnNodes.add(new LdcInsnNode("java/lang/StrictMath"));
        insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        insnNodes.add(new JumpInsnNode(IFEQ, skip));
        insnNodes.add(ownerMatch);
        LabelNode nameMatch = new LabelNode();
        insnNodes.add(new VarInsnNode(ALOAD, 3));
        insnNodes.add(new LdcInsnNode("sin"));
        insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        insnNodes.add(new JumpInsnNode(IFNE, nameMatch));
        insnNodes.add(new VarInsnNode(ALOAD, 3));
        insnNodes.add(new LdcInsnNode("cos"));
        insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        insnNodes.add(new JumpInsnNode(IFNE, nameMatch));
        insnNodes.add(new VarInsnNode(ALOAD, 3));
        insnNodes.add(new LdcInsnNode("tan"));
        insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        insnNodes.add(new JumpInsnNode(IFEQ, skip));
        insnNodes.add(nameMatch);
        // owner = "net/kibblelands/server/FastMath"
        insnNodes.add(new LdcInsnNode("net/kibblelands/server/FastMath"));
        insnNodes.add(new VarInsnNode(ASTORE, 2));
        // }
        insnNodes.add(new JumpInsnNode(GOTO, skip));
        insnNodes.add(nextOpcode);
        /*
        * if (opcode == INVOKEVIRTUAL &&
            owner.equals("java/lang/String") && name.equals("replace") && desc.contains("CharSequence"))
        * */
        insnNodes.add(new VarInsnNode(ILOAD, 1));
        insnNodes.add(ASMUtils.getNumberInsn(INVOKEVIRTUAL));
        insnNodes.add(new JumpInsnNode(IF_ICMPNE, skip));
        insnNodes.add(new VarInsnNode(ALOAD, 2));
        insnNodes.add(new LdcInsnNode("java/lang/String"));
        insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        insnNodes.add(new JumpInsnNode(IFEQ, skip));
        insnNodes.add(new VarInsnNode(ALOAD, 3));
        insnNodes.add(new LdcInsnNode("replace"));
        insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        insnNodes.add(new JumpInsnNode(IFEQ, skip));
        insnNodes.add(new VarInsnNode(ALOAD, 4));
        insnNodes.add(new LdcInsnNode("CharSequence"));
        insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z"));
        insnNodes.add(new JumpInsnNode(IFEQ, skip));
        /*
         opcode = INVOKESTATIC
         owner = "net/kibblelands/server/FastReplace"
         desc = "(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"
        * */
        insnNodes.add(ASMUtils.getNumberInsn(INVOKESTATIC));
        insnNodes.add(new VarInsnNode(ISTORE, 1));
        insnNodes.add(new LdcInsnNode("net/kibblelands/server/FastReplace"));
        insnNodes.add(new VarInsnNode(ASTORE, 2));
        insnNodes.add(new LdcInsnNode(
                "(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"));
        insnNodes.add(new VarInsnNode(ASTORE, 4));
        // }
        insnNodes.add(skip);
        // Inject code
        methodNode.instructions.insert(insnNodes);
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(COMMODORE_MV, classWriter.toByteArray());
        plRewrite[0] = true;
    }
}

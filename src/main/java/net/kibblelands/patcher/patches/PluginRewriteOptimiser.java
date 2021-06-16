package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.Map;

public class PluginRewriteOptimiser implements Opcodes {
    private static final String REWRITE_ETC = "net/kibblelands/patcher/runtime/RewriteEtc.class";
    private static final String COMMODORE_MV = "org/bukkit/craftbukkit/$NMS/util/Commodore$1$1.class";

    public static void patch(CommonGenerator commonGenerator,Map<String, byte[]> map,
                             Map<String, byte[]> inject,String accessPkg, final boolean[] plRewrite) {
        byte[] bytes;
        try {
            bytes = IOUtils.readResource(REWRITE_ETC);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        { ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(0);
        classReader.accept(new ClassRelocator(new ClassRelocator(classWriter
                , new PrefixRemapper("net/kibblelands/patcher/runtime/", accessPkg)),
                new PrefixRemapper("net/kibblelands/server/util/", accessPkg)), 0);
        bytes = classWriter.toByteArray(); }
        String COMMODORE_MV = commonGenerator.mapClass(PluginRewriteOptimiser.COMMODORE_MV);
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
        insnNodes.add(new VarInsnNode(ALOAD, 2));
        insnNodes.add(new VarInsnNode(ALOAD, 3));
        insnNodes.add(new VarInsnNode(ALOAD, 4));
        insnNodes.add(new MethodInsnNode(INVOKESTATIC, accessPkg + "RewriteEtc", "rewriteOwner",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
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
         owner = "net/kibblelands/server/util/FastReplace"
         desc = "(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"
        * */
        insnNodes.add(ASMUtils.getNumberInsn(INVOKESTATIC));
        insnNodes.add(new VarInsnNode(ISTORE, 1));
        insnNodes.add(new LdcInsnNode(accessPkg + "FastReplace"));
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
        inject.put(accessPkg + "RewriteEtc.class", bytes);
        plRewrite[0] = true;
        commonGenerator.addChangeEntry("Installed Plugin sin/cos/tan rewrite." + ConsoleColors.CYAN + " (Optimisation)");
    }
}

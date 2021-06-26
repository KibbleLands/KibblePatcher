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
    private static final String COMMODORE_CV = "org/bukkit/craftbukkit/$NMS/util/Commodore$1";
    private static final String COMMODORE_MV = "org/bukkit/craftbukkit/$NMS/util/Commodore$1$1";
    private static final String visitDesc =
            "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V";

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
        String COMMODORE_CV = commonGenerator.mapClass(PluginRewriteOptimiser.COMMODORE_CV);
        String COMMODORE_MV = commonGenerator.mapClass(PluginRewriteOptimiser.COMMODORE_MV);
        byte[] methodVisitor = map.get(COMMODORE_MV + ".class");
        if (methodVisitor == null) {
            return; // Unsupported
        }
        ClassNode classNode = new ClassNode();
        new ClassReader(methodVisitor).accept(classNode, 0);
        FieldNode cv = ASMUtils.findFieldByDesc(classNode, "L" + COMMODORE_CV + ";");
        MethodNode methodNode = ASMUtils.findMethod(classNode, "visitMethodInsn");
        if (methodNode == null) {
            return; // Unsupported
        }
        InsnList insnNodes = new InsnList();
        LabelNode skip = new LabelNode();

        if (cv != null) {
            insnNodes.add(new VarInsnNode(ALOAD, 0));
            insnNodes.add(new FieldInsnNode(GETFIELD, COMMODORE_MV, cv.name, cv.desc));
            insnNodes.add(new FieldInsnNode(GETFIELD, COMMODORE_CV, "skipRedirects", "Z"));
            insnNodes.add(new JumpInsnNode(IFNE, skip));
        }
        /*
        if (opcode == INVOKESTATIC &&
            (owner.equals("java/lang/Math"))
                 && (name.equals("sin") || name.equals("cos"))) {
        * */
        insnNodes.add(new VarInsnNode(ILOAD, 1));
        insnNodes.add(ASMUtils.getNumberInsn(INVOKESTATIC));
        insnNodes.add(new JumpInsnNode(IF_ICMPNE, skip));
        insnNodes.add(new VarInsnNode(ALOAD, 2));
        insnNodes.add(new VarInsnNode(ALOAD, 3));
        insnNodes.add(new VarInsnNode(ALOAD, 4));
        insnNodes.add(new MethodInsnNode(INVOKESTATIC, accessPkg + "RewriteEtc", "rewriteOwner",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
        insnNodes.add(new VarInsnNode(ASTORE, 2));
        // }
        insnNodes.add(skip);
        // Inject code
        methodNode.instructions.insert(insnNodes);
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(COMMODORE_MV + ".class", classWriter.toByteArray());
        if (cv != null) {
            byte[] classVisitor = map.get(COMMODORE_CV + ".class");
            classNode = new ClassNode();
            new ClassReader(classVisitor).accept(classNode, 0);
            classNode.fields.add(new FieldNode(ACC_PUBLIC, "skipRedirects", "Z", null, null));
            if (!ASMUtils.hasMethod(classNode, "visit", visitDesc))
                ASMUtils.createSuperCaller(classNode, "visit", visitDesc);
            MethodNode visit = ASMUtils.findMethod(classNode, "visit", visitDesc);
            if (visit == null) throw new Error("WTF?"); // Should never happen
            insnNodes = new InsnList();
            LabelNode end = new LabelNode();
            insnNodes.add(new VarInsnNode(ALOAD, 3));
            insnNodes.add(new LdcInsnNode("Math"));
            insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z"));
            insnNodes.add(new JumpInsnNode(IFEQ, end));
            insnNodes.add(new VarInsnNode(ALOAD, 3));
            insnNodes.add(new LdcInsnNode("/math/"));
            insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/String;)Z"));
            insnNodes.add(new JumpInsnNode(IFEQ, end));
            insnNodes.add(new VarInsnNode(ALOAD, 0));
            insnNodes.add(new InsnNode(ICONST_1));
            insnNodes.add(new FieldInsnNode(PUTFIELD, COMMODORE_CV, "skipRedirects", "Z"));
            insnNodes.add(end);
            visit.instructions.insert(insnNodes);
            classWriter = new ClassWriter(0);
            classNode.accept(classWriter);
            map.put(COMMODORE_CV + ".class", classWriter.toByteArray());
        }
        inject.put(accessPkg + "RewriteEtc.class", bytes);
        plRewrite[0] = true;
        commonGenerator.addChangeEntry("Installed Plugin sin/cos/tan rewrite." + ConsoleColors.CYAN + " (Optimisation)");
    }
}

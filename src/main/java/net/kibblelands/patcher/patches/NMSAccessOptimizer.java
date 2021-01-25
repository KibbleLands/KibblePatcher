package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.*;

import java.util.Map;

public class NMSAccessOptimizer implements Opcodes {
    private static final int MASK = ~(ACC_PRIVATE);
    private static final int MASK2 = ~(ACC_PRIVATE|ACC_PROTECTED);

    // C++ JVM Code do less checks on public elements
    public static void patch(CommonGenerator commonGenerator, Map<String, byte[]> zip) {
        for (Map.Entry<String, byte[]> entry:zip.entrySet()) {
            if (((entry.getKey().startsWith("net/minecraft/server/") ||
                    entry.getKey().startsWith("org/bukkit/craftbukkit/v"))
                    && entry.getKey().endsWith(".class"))) {
                ClassWriter classWriter = new ClassWriter(0);
                ClassReader classReader = new ClassReader(entry.getValue());
                classReader.accept(new ClassVisitor(ASM7, classWriter) {
                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        super.visit(version, (access&MASK)|ACC_PUBLIC, name, signature, superName, interfaces);
                    }

                    @Override
                    public void visitInnerClass(String name, String outerName, String innerName, int access) {
                        if (outerName == null && innerName == null) {
                            super.visitInnerClass(name, null, null, access);
                            return;
                        }
                        super.visitInnerClass(name, outerName, innerName, (access&MASK)|ACC_PUBLIC);
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return super.visitMethod((access& ACC_PROTECTED) != 0 ? access : (access&MASK)|ACC_PUBLIC, name, descriptor, signature, exceptions);
                    }

                    @Override
                    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                        return super.visitField((access&(MASK2))|ACC_PUBLIC, name, descriptor, signature, value);
                    }
                }, 0);
                entry.setValue(classWriter.toByteArray());
            }
        }
        commonGenerator.addChangeEntry("Reduced NMS Access restrictions " + ConsoleColors.CYAN + "(Optimisation)");
    }
}
package net.kibblelands.patcher.utils;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * ClassRemapper that add a flag to hide classes in the IDE to
 * avoid them from being used in a development environement
 */
public class ClassRelocator extends ClassRemapper implements Opcodes {
    public ClassRelocator(ClassVisitor classVisitor, Remapper remapper) {
        super(classVisitor, remapper);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access | ACC_SYNTHETIC, name, signature, superName, interfaces);
    }

    @Override
    protected MethodVisitor createMethodRemapper(MethodVisitor methodVisitor) {
        return new MethodVisitor(ASMUtils.ASM_BUILD, super.createMethodRemapper(methodVisitor)) {
            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof String) {
                    value = ClassRelocator.this.remapper.map((String) value);
                }
                super.visitLdcInsn(value);
            }
        };
    }
}

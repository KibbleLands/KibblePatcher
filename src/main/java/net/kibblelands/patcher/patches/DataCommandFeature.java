package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.utils.ASMUtils;
import org.objectweb.asm.*;

import java.util.Map;

public class DataCommandFeature implements Opcodes {
    private static final String dataAccessorEntity = "net/minecraft/server/$NMS/CommandDataAccessorEntity.class";
    private static final String entityHuman = "net/minecraft/server/$NMS/EntityHuman";

    public static void install(Map<String, byte[]> map, String mth, final int[] stats) {
        String NMS = mth.substring(21, mth.lastIndexOf('/'));
        String resolvedDataAccessorEntity = dataAccessorEntity.replace("$NMS", NMS);
        byte[] dataAccessorEntity = map.get(resolvedDataAccessorEntity);
        if (dataAccessorEntity == null) return; // Doesn't exists (Skip)
        final String asmEntityHuman = entityHuman.replace("$NMS", NMS);
        ClassWriter classWriter = new ClassWriter(0);
        new ClassReader(dataAccessorEntity).accept(new ClassVisitor(ASMUtils.ASM_BUILD, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name.startsWith("<")) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                return new MethodVisitor(ASMUtils.ASM_BUILD,super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == INSTANCEOF && type.equals(asmEntityHuman)) {
                            super.visitInsn(ICONST_0);
                        } else {
                            super.visitTypeInsn(opcode, type);
                        }
                    }
                };
            }
        }, 0);
        map.put(resolvedDataAccessorEntity, classWriter.toByteArray());
    }
}

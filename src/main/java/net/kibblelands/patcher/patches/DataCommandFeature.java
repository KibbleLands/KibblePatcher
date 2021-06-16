package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.*;

import java.util.Map;

public class DataCommandFeature implements Opcodes {
    private static final String dataAccessorEntity = "net/minecraft/server/$NMS/CommandDataAccessorEntity.class";
    private static final String entityHuman = "net/minecraft/server/$NMS/EntityHuman";

    public static void install(CommonGenerator commonGenerator,Map<String, byte[]> map, final int[] stats) {
        String resolvedDataAccessorEntity = commonGenerator.mapClass(dataAccessorEntity);
        byte[] dataAccessorEntity = map.get(resolvedDataAccessorEntity);
        final boolean[] didWork = new boolean[]{false};
        if (dataAccessorEntity == null) return; // Doesn't exists (Skip)
        final String asmEntityHuman = commonGenerator.mapClass(entityHuman);
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
                            didWork[0] = true;
                        } else {
                            super.visitTypeInsn(opcode, type);
                        }
                    }
                };
            }
        }, 0);
        if (!didWork[0]) {
            return;
        }
        map.put(resolvedDataAccessorEntity, classWriter.toByteArray());
        commonGenerator.addChangeEntry("Allow data write on players with the /data command. " + ConsoleColors.CYAN + "(Feature)");
    }
}

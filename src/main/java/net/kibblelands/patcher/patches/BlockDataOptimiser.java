package net.kibblelands.patcher.patches;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

import static net.kibblelands.patcher.utils.ASMUtils.ASM_BUILD;

public class BlockDataOptimiser implements Opcodes {
    private static final String NMS_BLOCK      = "net/minecraft/server/$NMS/Block";
    private static final String NMS_BLOCK_DATA = "net/minecraft/server/$NMS/IBlockData";

    // Transform method call to field call to allow JIT to better optimise bytecode
    public static void patch(Map<String, byte[]> map, String mth, final int[] stats) {
        final String NMS = mth.substring(21, mth.lastIndexOf('/'));
        final String BLOCK = NMS_BLOCK.replace("$NMS", NMS);
        final String BLOCK_DATA = NMS_BLOCK_DATA.replace("$NMS", NMS);
        if (!map.containsKey(BLOCK_DATA+".class")) {
            return; // No block data
        }
        final String BLOCK_DATA_DESC = "L" + BLOCK_DATA + ";";
        ClassNode classNode = new ClassNode();
        new ClassReader(map.get(BLOCK+".class")).accept(classNode, 0);
        FieldNode value = null;
        for (FieldNode fieldNode: classNode.fields) {
            if (fieldNode.desc.equals(BLOCK_DATA_DESC) && fieldNode.access == ACC_PRIVATE) {
                if (value == null) {
                    value = fieldNode;
                } else {
                    wtf(NMS, "0x00");
                    return; // WTF ?
                }
            }
        }
        if (value == null) return;
        String getter_desc = "()"+BLOCK_DATA_DESC,
                setter_desc = "("+BLOCK_DATA_DESC+")V";
        MethodNode getter = null, setter = null;
        for (MethodNode methodNode: classNode.methods) {
            if ((methodNode.access & ACC_FINAL) != 0) {
                if (methodNode.desc.equals(getter_desc)) {
                    if (getter == null) {
                        getter = methodNode;
                    } else {
                        wtf(NMS, "0x01");
                        return; // WTF ?
                    }
                } else if (methodNode.desc.equals(setter_desc)) {
                    if (setter == null) {
                        setter = methodNode;
                    } else {
                        wtf(NMS, "0x02");
                        return; // WTF ?
                    }
                }
            }
        }
        if (getter == null || setter == null) {
            int i = 2;
            if (getter == null) i+=1;
            if (setter == null) i+=1;
            wtf(NMS, "0x0"+i);
            return;
        }
        value.access = ACC_PUBLIC;
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(BLOCK+".class", classWriter.toByteArray());
        for (Map.Entry<String, byte[]> entry : map.entrySet()) {
            if (entry.getKey().endsWith(".class") &&
                    (entry.getKey().startsWith("net/minecraft/server/") ||
                    entry.getKey().startsWith("org/bukkit/craftbukkit/"))) {
                classWriter = new ClassWriter(0);
                final MethodNode finalGetter = getter;
                final MethodNode finalSetter = setter;
                final FieldNode finalValue = value;
                new ClassReader(entry.getValue()).accept(new ClassVisitor(ASM_BUILD, classWriter) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(ASM_BUILD, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                if (opcode == INVOKEVIRTUAL && owner.equals(BLOCK)) {
                                    if (name.equals(finalGetter.name) && descriptor.equals(finalGetter.desc)) {
                                        super.visitFieldInsn(GETFIELD, owner, finalValue.name, BLOCK_DATA_DESC);
                                        stats[2]++;
                                        return;
                                    } else if (name.equals(finalSetter.name) && descriptor.equals(finalSetter.desc)) {
                                        super.visitFieldInsn(PUTFIELD, owner, finalValue.name, BLOCK_DATA_DESC);
                                        stats[2]++;
                                        return;
                                    }
                                }
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }
                        };
                    }
                }, 0);
                entry.setValue(classWriter.toByteArray());
            }
        }
    }

    private static void wtf(String NMS, String state) {
        System.out.println("An optimisation has failed in an unexpected way. please report the issue with your server jar!");
        System.out.println("NMS: "+NMS);
        System.out.println("Debug state: "+ state);
    }
}

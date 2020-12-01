package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.utils.ASMUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Map;

public class BukkitVarTypeCompact implements Opcodes {
    private static final String DAMAGEABLE = "org/bukkit/entity/Damageable.class";
    private static final String DAMAGE = "org/bukkit/event/entity/EntityDamageEvent.class";
    private static final String DAMAGE_BLOCK = "org/bukkit/event/entity/EntityDamageByBlockEvent.class";
    private static final String DAMAGE_ENTITY = "org/bukkit/event/entity/EntityDamageByEntityEvent.class";

    public static void check(Map<String, byte[]> map, String mth, final int[] stats) {
        boolean didWork = false;
        byte[] cache = map.get(DAMAGEABLE);
        ClassNode classNode = new ClassNode();
        new ClassReader(cache).accept(classNode, 0);
        if (patch(classNode)) {
            ClassWriter classWriter = new ClassWriter(0);
            classNode.accept(classWriter);
            map.put(DAMAGEABLE, classWriter.toByteArray());
            didWork = true;
        }
        cache = map.get(DAMAGE);
        classNode = new ClassNode();
        new ClassReader(cache).accept(classNode, 0);
        // "|" execute both methods even if the first method return true
        // "||" doesn't execute the second if the first method return true
        if (patch(classNode) | patchCst(classNode)) {
            ClassWriter classWriter = new ClassWriter(0);
            classNode.accept(classWriter);
            map.put(DAMAGE, classWriter.toByteArray());
            didWork = true;
        }
        cache = map.get(DAMAGE_BLOCK);
        if (cache != null) {
            classNode = new ClassNode();
            new ClassReader(cache).accept(classNode, 0);
            if (patchCst(classNode)) {
                ClassWriter classWriter = new ClassWriter(0);
                classNode.accept(classWriter);
                map.put(DAMAGE_BLOCK, classWriter.toByteArray());
                didWork = true;
            }
        }
        cache = map.get(DAMAGE_ENTITY);
        if (cache != null) {
            classNode = new ClassNode();
            new ClassReader(cache).accept(classNode, 0);
            if (patchCst(classNode)) {
                ClassWriter classWriter = new ClassWriter(0);
                classNode.accept(classWriter);
                map.put(DAMAGE_ENTITY, classWriter.toByteArray());
                didWork = true;
            }
        }
        if (didWork) {
            stats[3]++;
        }
    }

    private static boolean patch(ClassNode classNode) {
        boolean didWork = false;
        final boolean isInterface = (classNode.access & ACC_INTERFACE) != 0;
        for (MethodNode methodNode: classNode.methods.toArray(new MethodNode[0])) {
            if ((methodNode.access & ACC_SYNTHETIC) != 0) continue;
            MethodNode newMethod = null;
            boolean isStatic = (methodNode.access & ACC_STATIC) != 0;
            int invoke = isStatic ? INVOKESTATIC :
                    isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL;
            boolean sec = false;
            String desc = methodNode.desc;
            if (desc.endsWith(")L"+classNode.name+";")) {
                desc = desc.substring(0, desc.length() -
                        classNode.name.length() - 2) + "V";
                sec = true;
            }
            String newDesc;
            switch (desc) {
                default:
                    break;
                case "(I)V":
                    newDesc = "(D)" + (sec ? "L" + classNode.name + ";" : "V");
                    if (ASMUtils.hasMethod(classNode, methodNode.name, newDesc)) break;
                    newMethod = new MethodNode((methodNode.access|ACC_SYNTHETIC)
                            &~ACC_ABSTRACT, methodNode.name, newDesc, null, null);
                    if (!isStatic) newMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                    newMethod.instructions.add(new VarInsnNode(DLOAD, isStatic ? 0 : 1));
                    newMethod.instructions.add(new InsnNode(L2I));
                    newMethod.instructions.add(new MethodInsnNode(invoke, classNode.name,
                            methodNode.name, methodNode.desc, isInterface));
                    if (sec) {
                        newMethod.instructions.add(new InsnNode(ARETURN));
                    } else {
                        newMethod.instructions.add(new InsnNode(RETURN));
                    }
                    break;
                case "(D)V":
                    newDesc = "(I)" + (sec ? "L" + classNode.name + ";" : "V");
                    if (ASMUtils.hasMethod(classNode, methodNode.name, newDesc)) break;
                    newMethod = new MethodNode((methodNode.access|ACC_SYNTHETIC)
                            &~ACC_ABSTRACT, methodNode.name, newDesc, null, null);
                    if (!isStatic) newMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                    newMethod.instructions.add(new VarInsnNode(ILOAD, isStatic ? 0 : 1));
                    newMethod.instructions.add(new InsnNode(I2D));
                    newMethod.instructions.add(new MethodInsnNode(invoke, classNode.name,
                            methodNode.name, methodNode.desc, isInterface));
                    if (sec) {
                        newMethod.instructions.add(new InsnNode(ARETURN));
                    } else {
                        newMethod.instructions.add(new InsnNode(RETURN));
                    }
                    break;
                case "()I":
                    if (ASMUtils.hasMethod(classNode, methodNode.name, "()D")) break;
                    newMethod = new MethodNode((methodNode.access|ACC_SYNTHETIC)
                            &~ACC_ABSTRACT, methodNode.name, "()D", null, null);
                    if (!isStatic) newMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                    newMethod.instructions.add(new MethodInsnNode(invoke, classNode.name,
                            methodNode.name, methodNode.desc, isInterface));
                    newMethod.instructions.add(new InsnNode(I2D));
                    newMethod.instructions.add(new InsnNode(DRETURN));
                    break;
                case "()D":
                    if (ASMUtils.hasMethod(classNode, methodNode.name, "()D")) break;
                    newMethod = new MethodNode((methodNode.access|ACC_SYNTHETIC)
                            &~ACC_ABSTRACT, methodNode.name, "()D", null, null);
                    if (!isStatic) newMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                    newMethod.instructions.add(new MethodInsnNode(invoke, classNode.name,
                            methodNode.name, methodNode.desc, isInterface));
                    newMethod.instructions.add(new InsnNode(D2I));
                    newMethod.instructions.add(new InsnNode(IRETURN));
            }
            if (newMethod != null) {
                didWork = true;
                classNode.methods.add(newMethod);
            }
        }
        if (didWork && isInterface &&
                classNode.version < V1_8) {
            classNode.version = V1_8;
        }
        return didWork;
    }

    private static boolean patchCst(ClassNode classNode) {
        boolean didWork = false;
        for (MethodNode methodNode:classNode.methods.toArray(new MethodNode[0])) {
            if (methodNode.name.equals("<init>")) {
                boolean i2d;
                if ((i2d = methodNode.desc.endsWith("D)V")) || methodNode.desc.endsWith("I)V")) {
                    MethodNode newMethodNode = new MethodNode(
                            methodNode.access | ACC_SYNTHETIC, methodNode.name,
                            methodNode.desc.substring(0, methodNode.desc.length() - 3)
                                    + (i2d ? "I)V": "D)V"), null, null);
                    newMethodNode.instructions.add(new VarInsnNode(ALOAD, 0));
                    int i = 1;
                    for (Type arg : Type.getArgumentTypes(newMethodNode.desc)) {
                        newMethodNode.instructions.add(new VarInsnNode(arg.getOpcode(ILOAD), i));
                        i += arg.getSize();
                    }
                    newMethodNode.instructions.add(new InsnNode(i2d ? I2D : D2I));
                    newMethodNode.instructions.add(new MethodInsnNode(
                            INVOKESPECIAL, classNode.name, "<init>", methodNode.desc, false));
                    newMethodNode.instructions.add(new InsnNode(RETURN));
                    classNode.methods.add(newMethodNode);
                    didWork = true;
                }
            }
        }
        return didWork;
    }
}

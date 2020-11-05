package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.ASMUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

/**
 * Make old plugins with incompatible bytecode work with the latest GuavaAPI
 */
public class GuavaVarTypeCompact implements Opcodes {
    private static final String CACHE_BUILDER =
            "com/google/common/cache/CacheBuilder.class";
    private static final String BUILD_CACHE_LEGACY =
            "(Lcom/google/common/cache/CacheLoader;)Lcom/google/common/cache/Cache;";
    private static final String BUILD_CACHE_NEW =
            "(Lcom/google/common/cache/CacheLoader;)Lcom/google/common/cache/LoadingCache;";
    private static final String CACHE =
            "com/google/common/cache/Cache.class";

    public static void check(Map<String, byte[]> map, String mth, final int[] stats) {
        boolean didWork = false;
        if (map.get("com/google/common/cache/LoadingCache.class") != null) {
            byte[] cacheBuilder = map.get(CACHE_BUILDER);
            boolean addedMirror = false;
            ClassNode classNode = new ClassNode();
            new ClassReader(cacheBuilder).accept(classNode, 0);
            if (!ASMUtils.hasMethod(classNode, "build", BUILD_CACHE_NEW)) {
                MethodNode newMethod = new MethodNode(
                        ACC_PUBLIC|ACC_SYNTHETIC|ACC_BRIDGE, "build", BUILD_CACHE_NEW, null, null);
                newMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                newMethod.instructions.add(new VarInsnNode(ALOAD, 1));
                newMethod.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,
                        "com/google/common/cache/CacheBuilder", "build", BUILD_CACHE_LEGACY));
                newMethod.instructions.add(new TypeInsnNode(CHECKCAST, "com/google/common/cache/LoadingCache"));
                newMethod.instructions.add(new InsnNode(ARETURN));
                classNode.methods.add(newMethod);
                addedMirror = true;
            } else if (!ASMUtils.hasMethod(classNode, "build", BUILD_CACHE_LEGACY)) {
                MethodNode newMethod = new MethodNode(
                        ACC_PUBLIC|ACC_SYNTHETIC|ACC_BRIDGE, "build", BUILD_CACHE_LEGACY, null, null);
                newMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                newMethod.instructions.add(new VarInsnNode(ALOAD, 1));
                newMethod.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,
                        "com/google/common/cache/CacheBuilder", "build", BUILD_CACHE_NEW));
                newMethod.instructions.add(new TypeInsnNode(CHECKCAST, "com/google/common/cache/Cache"));
                newMethod.instructions.add(new InsnNode(ARETURN));
                classNode.methods.add(newMethod);
                addedMirror = true;
            }
            if (patch(classNode) || addedMirror) {
                ClassWriter classWriter = new ClassWriter(0);
                classNode.accept(classWriter);
                map.put(CACHE_BUILDER, classWriter.toByteArray());
                didWork = true;
            }
        }
        byte[] cache = map.get(CACHE);
        ClassNode classNode = new ClassNode();
        new ClassReader(cache).accept(classNode, 0);
        boolean addedMirror = false;
        if (ASMUtils.hasMethod(classNode, "getIfPresent", "(Ljava/lang/Object;)Ljava/lang/Object;") &&
                !ASMUtils.hasMethod(classNode, "get", "(Ljava/lang/Object;)Ljava/lang/Object;")) {
            ASMUtils.symlinkMethod(classNode, "getIfPresent", "get");
            addedMirror = true;
        } else if (ASMUtils.hasMethod(classNode, "get", "(Ljava/lang/Object;)Ljava/lang/Object;")) {
            ASMUtils.symlinkMethod(classNode, "get", "getIfPresent");
            addedMirror = true;
        }
        if (patch(classNode) || addedMirror) {
            ClassWriter classWriter = new ClassWriter(0);
            classNode.accept(classWriter);
            map.put(CACHE, classWriter.toByteArray());
            didWork = true;
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
                    newDesc = "(J)" + (sec ? "L" + classNode.name + ";" : "V");
                    if (ASMUtils.hasMethod(classNode, methodNode.name, newDesc)) break;
                    newMethod = new MethodNode((methodNode.access|ACC_SYNTHETIC)
                            &~ACC_ABSTRACT, methodNode.name, newDesc, null, null);
                    if (!isStatic) newMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                    newMethod.instructions.add(new VarInsnNode(LLOAD, isStatic ? 0 : 1));
                    newMethod.instructions.add(new InsnNode(L2I));
                    newMethod.instructions.add(new MethodInsnNode(invoke, classNode.name,
                            methodNode.name, methodNode.desc, isInterface));
                    if (sec) {
                        newMethod.instructions.add(new InsnNode(ARETURN));
                    } else {
                        newMethod.instructions.add(new InsnNode(RETURN));
                    }
                    break;
                case "(J)V":
                    newDesc = "(I)" + (sec ? "L" + classNode.name + ";" : "V");
                    if (ASMUtils.hasMethod(classNode, methodNode.name, newDesc)) break;
                    newMethod = new MethodNode((methodNode.access|ACC_SYNTHETIC)
                            &~ACC_ABSTRACT, methodNode.name, newDesc, null, null);
                    if (!isStatic) newMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                    newMethod.instructions.add(new VarInsnNode(ILOAD, isStatic ? 0 : 1));
                    newMethod.instructions.add(new InsnNode(I2L));
                    newMethod.instructions.add(new MethodInsnNode(invoke, classNode.name,
                            methodNode.name, methodNode.desc, isInterface));
                    if (sec) {
                        newMethod.instructions.add(new InsnNode(ARETURN));
                    } else {
                        newMethod.instructions.add(new InsnNode(RETURN));
                    }
                    break;
                case "()I":
                    if (ASMUtils.hasMethod(classNode, methodNode.name, "()J")) break;
                    newMethod = new MethodNode((methodNode.access|ACC_SYNTHETIC)
                            &~ACC_ABSTRACT, methodNode.name, "()J", null, null);
                    if (!isStatic) newMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                    newMethod.instructions.add(new MethodInsnNode(invoke, classNode.name,
                            methodNode.name, methodNode.desc, isInterface));
                    newMethod.instructions.add(new InsnNode(I2L));
                    newMethod.instructions.add(new InsnNode(LRETURN));
                    break;
                case "()J":
                    if (ASMUtils.hasMethod(classNode, methodNode.name, "()I")) break;
                    newMethod = new MethodNode((methodNode.access|ACC_SYNTHETIC)
                            &~ACC_ABSTRACT, methodNode.name, "()I", null, null);
                    if (!isStatic) newMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                    newMethod.instructions.add(new MethodInsnNode(invoke, classNode.name,
                            methodNode.name, methodNode.desc, isInterface));
                    newMethod.instructions.add(new InsnNode(L2I));
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
}

package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.KibblePatcher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.Map;

public class EntityPropertiesFeature implements Opcodes {
    private static final String[] propertiesApiClasses = new String[]{
            "net/kibblelands/server/properties/EntityProperty.class",
            "net/kibblelands/server/properties/TempEntityProperty.class",
            "net/kibblelands/server/properties/TempEntityPropertyBase.class",
            "net/kibblelands/server/properties/TempSoftEntityProperty.class",
            "net/kibblelands/server/properties/TempWeakEntityProperty.class",
    };

    private static final String ENTITY = "org/bukkit/entity/Entity.class";
    private static final String CRAFT_ENTITY = "org/bukkit/craftbukkit/$NMS/entity/CraftEntity.class";

    private static final String propertiesImpl = "net/kibblelands/server/properties/PropertiesImpl.class";
    private static final String propertiesField = "#properties";
    private static final String getPropertiesArray = "#getPropertiesArrayImpl";
    private static final String getPropertiesArrayDesc = "(I)[Ljava/lang/Object;";

    public static void install(Map<String, byte[]> map, Map<String, byte[]> inject,
                               String mth, final int[] stats) throws IOException {
        String NMS = mth.substring(21, mth.lastIndexOf('/'));
        ClassNode classNode = new ClassNode();
        new ClassReader(map.get(ENTITY)).accept(classNode,0);
        classNode.methods.add(new MethodNode(ACC_PUBLIC|ACC_ABSTRACT|ACC_SYNTHETIC,
                getPropertiesArray, getPropertiesArrayDesc, null, null));
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(ENTITY, classWriter.toByteArray());
        // Assume craft entity exists
        String resolvedCraftEntity = CRAFT_ENTITY.replace("$NMS", NMS);
        String asmCraftEntity = resolvedCraftEntity
                .substring(0, resolvedCraftEntity.length() - 6);
        classNode = new ClassNode();
        new ClassReader(map.get(resolvedCraftEntity)).accept(classNode,0);
        classNode.fields.add(new FieldNode(ACC_PRIVATE|ACC_SYNTHETIC,
                propertiesField, "[Ljava/lang/Object;", null, null));
        MethodNode getPropertiesArrayImpl = new MethodNode(ACC_PUBLIC|ACC_SYNTHETIC,
                getPropertiesArray, getPropertiesArrayDesc, null, null);
        InsnList insnList = getPropertiesArrayImpl.instructions;
        LabelNode ret = new LabelNode();
        LabelNode nonnull = new LabelNode();
        insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new FieldInsnNode(GETFIELD, asmCraftEntity, propertiesField, "[Ljava/lang/Object;"));
        insnList.add(new InsnNode(DUP));
        insnList.add(new JumpInsnNode(IFNONNULL, nonnull));
        insnList.add(new InsnNode(POP));
        insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new VarInsnNode(ILOAD, 1));
        insnList.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
        insnList.add(new InsnNode(DUP_X1));
        insnList.add(new FieldInsnNode(PUTFIELD, asmCraftEntity, propertiesField, "[Ljava/lang/Object;"));
        insnList.add(new JumpInsnNode(GOTO, ret));
        insnList.add(nonnull);
        insnList.add(new InsnNode(DUP));
        insnList.add(new InsnNode(ARRAYLENGTH));
        insnList.add(new VarInsnNode(ILOAD, 1));
        insnList.add(new JumpInsnNode(IF_ICMPGE, ret));
        insnList.add(new VarInsnNode(ILOAD, 1));
        insnList.add(new MethodInsnNode(INVOKESTATIC, "java/util/Arrays",
                "copyOf", "([Ljava/lang/Object;I)[Ljava/lang/Object;", false));
        insnList.add(new InsnNode(DUP));
        insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new InsnNode(SWAP));
        insnList.add(new FieldInsnNode(PUTFIELD, asmCraftEntity, propertiesField, "[Ljava/lang/Object;"));
        insnList.add(ret);
        insnList.add(new InsnNode(ARETURN));
        classNode.methods.add(getPropertiesArrayImpl);
        classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(resolvedCraftEntity,
                classWriter.toByteArray());
        // Add PropertiesImpl
        classNode = new ClassNode();
        new ClassReader(KibblePatcher.readResource(propertiesImpl)).accept(classNode,0);
        classNode.access |= ACC_SYNTHETIC;
        classNode.fields.clear();
        classNode.methods.clear();
        getPropertiesArrayImpl = new MethodNode(ACC_STATIC, "getPropertiesArray",
                "(Lorg/bukkit/entity/Entity;I)[Ljava/lang/Object;", null, null);
        insnList = getPropertiesArrayImpl.instructions;
        insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new VarInsnNode(ILOAD, 1));
        insnList.add(new MethodInsnNode(INVOKEINTERFACE, "org/bukkit/entity/Entity",
                getPropertiesArray, getPropertiesArrayDesc, false));
        insnList.add(new InsnNode(ARETURN));
        classNode.methods.add(getPropertiesArrayImpl);
        classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        inject.put(propertiesImpl,
                classWriter.toByteArray());
        // Install lib
        for (String file:propertiesApiClasses) {
            inject.put(file, KibblePatcher.readResource(file));
        }
    }

    public static void installLib(Map<String, byte[]> map) throws IOException {
        // Default Impl for tests
        ClassNode classNode = new ClassNode();
        new ClassReader(KibblePatcher.readResource(propertiesImpl)).accept(classNode,0);
        // Hide this to most dev tools to help beginners focus
        classNode.access |= ACC_SYNTHETIC;
        for (FieldNode fieldNode:classNode.fields) {
            fieldNode.access |= ACC_SYNTHETIC;
        }
        for (MethodNode methodNode:classNode.methods) {
            if (!methodNode.name.startsWith("<")) {
                methodNode.access |= ACC_SYNTHETIC|ACC_BRIDGE;
            }
        }
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(propertiesImpl,
                classWriter.toByteArray());
        // Install lib
        for (String file:propertiesApiClasses) {
            map.put(file, KibblePatcher.readResource(file));
        }
    }
}

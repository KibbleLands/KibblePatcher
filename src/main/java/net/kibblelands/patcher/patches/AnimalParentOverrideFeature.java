package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

// Requested by Vakea
public class AnimalParentOverrideFeature implements Opcodes {
    private static final String ANIMALS = "org/bukkit/entity/Animals";
    private static final String LIVING_ENTITY = "org/bukkit/entity/LivingEntity";
    private static final String CRAFT_LIVING = "org/bukkit/craftbukkit/$NMS/entity/CraftLivingEntity";
    private static final String CRAFT_ANIMALS = "org/bukkit/craftbukkit/$NMS/entity/CraftAnimals";
    private static final String ENTITY_LIVING = "net/minecraft/server/$NMS/EntityLiving";
    private static final String ENTITY_ANIMAL = "net/minecraft/server/$NMS/EntityAnimal";
    private static final String FOLLOW_PARENT = "net/minecraft/server/$NMS/PathfinderGoalFollowParent";

    public static void install(CommonGenerator commonGenerator, Map<String, byte[]> map) {
        installLib(map);
        String NMS_CRAFT_ANIMALS = commonGenerator.mapClass(CRAFT_ANIMALS);
        String NMS_CRAFT_LIVING = commonGenerator.mapClass(CRAFT_LIVING);
        String NMS_ENTITY_ANIMAL = commonGenerator.mapClass(ENTITY_ANIMAL);
        String NMS_ENTITY_LIVING = commonGenerator.mapClass(ENTITY_LIVING);
        String NMS_FOLLOW_PARENT = commonGenerator.mapClass(FOLLOW_PARENT);
        byte[] followParent = map.get(NMS_FOLLOW_PARENT + ".class");
        if (followParent == null) return;
        ClassNode classNode = new ClassNode();
        new ClassReader(followParent).accept(classNode, 0);

        final String oldDesc = "L" + NMS_ENTITY_ANIMAL + ";";
        final String newDesc = "L" + NMS_ENTITY_LIVING + ";";
        FieldNode overrideRef = new FieldNode(ACC_PUBLIC, "parentOverride", "L" + ASMUtils.WEAK_REFERENCE + ";",
                "L" + ASMUtils.WEAK_REFERENCE + "<" + NMS_ENTITY_LIVING + ">;", null);
        FieldNode target = ASMUtils.findFieldByDesc(classNode, oldDesc);
        FieldNode parent = ASMUtils.findFieldByDescIndex(classNode, oldDesc, 1);
        if (target == null || parent == null) return;
        parent.desc = newDesc;
        FieldInsnNode oldGetField = new FieldInsnNode(GETFIELD, NMS_FOLLOW_PARENT, parent.name, oldDesc);
        FieldInsnNode oldPutField = new FieldInsnNode(PUTFIELD, NMS_FOLLOW_PARENT, parent.name, oldDesc);
        MethodNode setter = null;
        for (MethodNode methodNode:classNode.methods) {
            for (AbstractInsnNode abstractInsnNode:methodNode.instructions.toArray()) {
                if (ASMUtils.equals(abstractInsnNode, oldPutField)) {
                    ((FieldInsnNode) abstractInsnNode).desc = newDesc;
                    if (methodNode.desc.endsWith(")Z"))
                        setter = methodNode;
                } else if (ASMUtils.equals(abstractInsnNode, oldGetField)) {
                    ((FieldInsnNode) abstractInsnNode).desc = newDesc;
                    abstractInsnNode = abstractInsnNode.getNext();
                    if (abstractInsnNode instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;
                        if (methodInsnNode.owner.equals(NMS_ENTITY_ANIMAL)) {
                            methodInsnNode.owner = NMS_ENTITY_LIVING;
                        }
                    }
                }
            }
        }
        if (setter == null) return;
        InsnList insnList = new InsnList();
        insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new FieldInsnNode(GETFIELD, NMS_FOLLOW_PARENT, target.name, target.desc));
        insnList.add(new FieldInsnNode(GETFIELD, NMS_ENTITY_ANIMAL, overrideRef.name, overrideRef.desc));
        LabelNode ifNull = new LabelNode();
        ASMUtils.passUnReferenceOr(insnList, ASMUtils.WEAK_REFERENCE, NMS_ENTITY_ANIMAL, ifNull);
        insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new InsnNode(SWAP));
        insnList.add(new FieldInsnNode(PUTFIELD, NMS_FOLLOW_PARENT, parent.name, parent.desc));
        insnList.add(new InsnNode(ICONST_1));
        insnList.add(new InsnNode(IRETURN));
        insnList.add(ifNull);
        insnList.add(new InsnNode(POP));
        setter.instructions.insert(insnList);
        MethodNode checker = null;
        for (MethodNode methodNode: classNode.methods) {
            if (methodNode != setter && methodNode.desc.endsWith(")Z")) {
                checker = methodNode;
                break;
            }
        }
        if (checker != null) {
            insnList.add(new VarInsnNode(ALOAD, 0));
            insnList.add(new FieldInsnNode(GETFIELD, NMS_FOLLOW_PARENT, target.name, target.desc));
            insnList.add(new FieldInsnNode(GETFIELD, NMS_ENTITY_ANIMAL, overrideRef.name, overrideRef.desc));
            ifNull = new LabelNode();
            ASMUtils.passUnReferenceOr(insnList, ASMUtils.WEAK_REFERENCE, ASMUtils.OBJECT, ifNull);
            insnList.add(new InsnNode(POP));
            insnList.add(new InsnNode(ICONST_1));
            insnList.add(new InsnNode(IRETURN));
            insnList.add(ifNull);
            insnList.add(new InsnNode(POP));
            checker.instructions.insert(insnList);
        }
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(NMS_FOLLOW_PARENT + ".class", classWriter.toByteArray());
        classNode = new ClassNode();
        new ClassReader(map.get(NMS_ENTITY_ANIMAL + ".class")).accept(classNode, 0);
        classNode.fields.add(overrideRef);
        classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(NMS_ENTITY_ANIMAL + ".class", classWriter.toByteArray());
        classNode = new ClassNode();
        new ClassReader(map.get(NMS_CRAFT_ANIMALS + ".class")).accept(classNode, 0);
        // Add getter
        MethodNode methodNode = new MethodNode(ACC_PUBLIC,
                "getParentOverride", "()L" + LIVING_ENTITY + ";", null, null);
        insnList = methodNode.instructions;
        insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new MethodInsnNode(INVOKEVIRTUAL,
                NMS_CRAFT_ANIMALS, "getHandle", "()L" + NMS_ENTITY_ANIMAL + ";"));
        insnList.add(new FieldInsnNode(GETFIELD, NMS_ENTITY_ANIMAL, overrideRef.name, overrideRef.desc));
        ifNull = new LabelNode();
        ASMUtils.passUnReferenceOr(insnList, ASMUtils.WEAK_REFERENCE, NMS_ENTITY_LIVING, ifNull);
        insnList.add(new MethodInsnNode(INVOKEVIRTUAL,
                NMS_ENTITY_LIVING, "getBukkitLivingEntity", "()L" + NMS_CRAFT_LIVING + ";"));
        insnList.add(new InsnNode(ARETURN));
        insnList.add(ifNull);
        insnList.add(new InsnNode(POP));
        insnList.add(new InsnNode(ACONST_NULL));
        insnList.add(new InsnNode(ARETURN));
        classNode.methods.add(methodNode);
        // Add setter
        methodNode = new MethodNode(ACC_PUBLIC,
                "setParentOverride", "(L" + LIVING_ENTITY + ";)V", null, null);
        insnList = methodNode.instructions;
        insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new MethodInsnNode(INVOKEVIRTUAL,
                NMS_CRAFT_ANIMALS, "getHandle", "()L" + NMS_ENTITY_ANIMAL + ";"));
        insnList.add(new VarInsnNode(ALOAD, 1));
        insnList.add(new TypeInsnNode(CHECKCAST, NMS_CRAFT_LIVING));
        insnList.add(new MethodInsnNode(INVOKEVIRTUAL,
                NMS_CRAFT_LIVING, "getHandle", "()L" + NMS_ENTITY_LIVING + ";"));
        ASMUtils.passReference(insnList, ASMUtils.WEAK_REFERENCE);
        insnList.add(new FieldInsnNode(PUTFIELD, NMS_ENTITY_ANIMAL, overrideRef.name, overrideRef.desc));
        insnList.add(new InsnNode(RETURN));
        classNode.methods.add(methodNode);
        classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(NMS_CRAFT_ANIMALS + ".class", classWriter.toByteArray());
        commonGenerator.addChangeEntry("Added AnimalParentOverrideAPI " + ConsoleColors.CYAN + "(Feature)");
    }

    public static void installLib(Map<String, byte[]> map) {
        byte[] fox = map.get(ANIMALS + ".class");
        ClassNode classNode = new ClassNode();
        new ClassReader(fox).accept(classNode, 0);
        classNode.methods.add(new MethodNode(ACC_PUBLIC | ACC_ABSTRACT,
                "getParentOverride", "()L" + LIVING_ENTITY + ";", null, null));
        classNode.methods.add(new MethodNode(ACC_PUBLIC | ACC_ABSTRACT,
                "setParentOverride", "(L" + LIVING_ENTITY + ";)V", null, null));
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(ANIMALS + ".class", classWriter.toByteArray());
    }
}

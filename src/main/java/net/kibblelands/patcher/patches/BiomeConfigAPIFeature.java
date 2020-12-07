package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.KibblePatcher;
import net.kibblelands.patcher.rebuild.ClassDataProvider;
import net.kibblelands.patcher.utils.ASMUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

public class BiomeConfigAPIFeature implements Opcodes {
    private static final String PIGLIN_BRUTE = "org/bukkit/entity/PiglinBrute.class";
    private static final String BUKKIT_BIOME = "org/bukkit/block/Biome";
    private static final String BUKKIT_WORLD = "org/bukkit/World";
    private static final String BUKKIT_PARTICLE = "org/bukkit/Particle";
    private static final String BUKKIT_SOUND = "org/bukkit/Sound";
    private static final String API_BIOME_CONFIG = "net/kibblelands/server/biome/BiomeConfig";
    private static final String API_BIOME_PARTICLES = "net/kibblelands/server/biome/BiomeParticles";
    private static final String NMS_BIOME_BASE = "net/minecraft/server/$NMS/BiomeBase";
    private static final String NMS_BIOME_FOG = "net/minecraft/server/$NMS/BiomeFog";
    private static final String NMS_BIOME_PARTICLES = "net/minecraft/server/$NMS/BiomeParticles";
    private static final String NMS_SOUND_EFFECT = "net/minecraft/server/$NMS/SoundEffect";
    private static final String NMS_PARTICLES_PARAM = "net/minecraft/server/$NMS/ParticleParam";
    private static final String NMS_CRAFT_BLOCK = "org/bukkit/craftbukkit/$NMS/block/CraftBlock";
    private static final String NMS_CRAFT_WORLD = "org/bukkit/craftbukkit/$NMS/CraftWorld";
    private static final String NMS_CRAFT_PARTICLE = "org/bukkit/craftbukkit/$NMS/CraftParticle";
    private static final String NMS_CRAFT_SOUND = "org/bukkit/craftbukkit/$NMS/CraftSound";

    private static boolean isSupported(Map<String, byte[]> map) {
        return map.containsKey(PIGLIN_BRUTE); // Test if at least 1.16.2
    }

    private static final String[] biomeApiClasses = new String[]{
            "net/kibblelands/server/biome/BiomeConfig.class",
            "net/kibblelands/server/biome/BiomeParticles.class",
    };

    private static final String[] intElements = new String[]{
            "FogColor",
            "WaterColor",
            "WaterFogColor",
            "SkyColor",
            "FoliageColor",
            "GrassColor",
    };

    public static void install(Map<String, byte[]> map, Map<String, byte[]> inject,
                               String mth, ClassDataProvider cdp, final int[] stats) throws IOException {
        if (!isSupported(map)) return; // Skip on pre 1.16.2
        String NMS = mth.substring(21, mth.lastIndexOf('/'));
        String BIOME_BASE = NMS_BIOME_BASE.replace("$NMS", NMS);
        String BIOME_FOG = NMS_BIOME_FOG.replace("$NMS", NMS);
        String CRAFT_BLOCK = NMS_CRAFT_BLOCK.replace("$NMS", NMS);
        String CRAFT_WORLD = NMS_CRAFT_WORLD.replace("$NMS", NMS);
        ClassNode craftWorldNode = new ClassNode();
        new ClassReader(map.get(CRAFT_WORLD+".class")).accept(craftWorldNode, 0);
        MethodNode setBiome = ASMUtils.findMethod(craftWorldNode, "setBiome", "(IIIL"+BUKKIT_BIOME+";)V");
        if (setBiome == null) {
            System.out.println("WTF? Err 0x00");
            return;
        }
        InsnList insnNodes = ASMUtils.copyInsnList(setBiome.instructions);
        AbstractInsnNode end = insnNodes.getFirst();
        while (!isB2BBInsn(end, CRAFT_BLOCK)) {
            if (end instanceof VarInsnNode && ((VarInsnNode) end).var == 4) {
                ((VarInsnNode) end).var = 1;
            }
            end = end.getNext();
            if (end == null) {
                System.out.println("WTF? Err 0x01");
                return;
            }
        }
        while (end.getNext() != null) {
            insnNodes.remove(end.getNext());
        }
        insnNodes.add(new InsnNode(DUP));
        insnNodes.add(new VarInsnNode(ALOAD, 1));
        insnNodes.add(new FieldInsnNode(PUTFIELD, BIOME_BASE, "bukkitBiome", "L" + BUKKIT_BIOME + ";"));
        insnNodes.add(new TypeInsnNode(CHECKCAST, API_BIOME_CONFIG));
        insnNodes.add(new InsnNode(ARETURN));
        MethodNode getBiomeConfig = new MethodNode(ACC_PUBLIC, "getBiomeConfig",
                "(L" + BUKKIT_BIOME + ";)L" + API_BIOME_CONFIG + ";", null, null);
        getBiomeConfig.instructions.add(insnNodes);
        craftWorldNode.methods.add(getBiomeConfig);
        ClassWriter classWriter = new ClassWriter(0);
        craftWorldNode.accept(classWriter);
        byte[] newCraftWorld = classWriter.toByteArray();
        // BiomeConfig base Impl
        ClassNode biomeBase = new ClassNode();
        new ClassReader(map.get(BIOME_BASE+".class")).accept(biomeBase, 0);
        ClassNode biomeFog = new ClassNode();
        new ClassReader(map.get(BIOME_FOG+".class")).accept(biomeFog, 0);
        if (biomeBase.interfaces == null) biomeBase.interfaces = new ArrayList<>();
        biomeBase.interfaces.add(API_BIOME_CONFIG);
        biomeBase.fields.add(new FieldNode(ACC_PUBLIC, "bukkitBiome", "L" + BUKKIT_BIOME + ";", null, null));
        MethodNode methodNode = new MethodNode(ACC_PUBLIC, "getTargetBiome", "()L" + BUKKIT_BIOME + ";", null, null);
        methodNode.instructions.add(new VarInsnNode(ALOAD, 0));
        methodNode.instructions.add(new FieldInsnNode(GETFIELD, BIOME_BASE, "bukkitBiome", "L" + BUKKIT_BIOME + ";"));
        methodNode.instructions.add(new InsnNode(ARETURN));
        biomeBase.methods.add(methodNode);
        FieldNode biomeFogField = null;
        for (FieldNode field:biomeBase.fields) {
            if (field.desc.equals("L" + BIOME_FOG + ";")) {
                biomeFogField = field;
            }
        }
        if (biomeFogField == null) {
            return;
        }
        // Color Methods
        int i = 0;
        for (FieldNode fieldNode:biomeFog.fields) {
            if (fieldNode.desc.equals("I") ||
                    (fieldNode.signature != null &&
                            fieldNode.signature.equals("Ljava/util/Optional<Ljava/lang/Integer;>;"))) {
                String name = intElements[i];
                i++;
                addIntMethods(biomeBase, biomeFogField, BIOME_FOG, fieldNode, name);
                if (i == intElements.length) break;
            }
        }
        // BiomeParticle Methods
        String BIOME_PARTICLE = NMS_BIOME_PARTICLES.replace("$NMS", NMS);
        String CRAFT_PARTICLE = NMS_CRAFT_PARTICLE.replace("$NMS", NMS);
        String PARTICLES_PARAM = NMS_PARTICLES_PARAM.replace("$NMS", NMS);
        ClassNode biomeParticlesCL = new ClassNode();
        new ClassReader(map.get(BIOME_PARTICLE+".class")).accept(biomeParticlesCL, 0);
        biomeParticlesCL.fields.add(new FieldNode(ACC_PUBLIC,
                "kibbleBiomeParticles", "L" + API_BIOME_PARTICLES + ";", null, null));
        FieldNode biomeParticle = ASMUtils.findFieldBySignature(biomeFog,
                "Ljava/util/Optional<L" + BIOME_PARTICLE + ";>;");
        if (biomeParticle == null) {
            System.out.println("WTF? Err 0x02");
            return;
        }
        addObjectMethods(biomeBase, biomeFogField, BIOME_FOG,
                biomeParticle, API_BIOME_PARTICLES, "BiomeParticles", b2n -> {
            b2n.add(new InsnNode(DUP));
            b2n.add(new FieldInsnNode(GETFIELD, API_BIOME_PARTICLES, "particle", "L" + BUKKIT_PARTICLE + ";"));
            b2n.add(new InsnNode(SWAP));
            b2n.add(new InsnNode(DUP_X1));
            b2n.add(new FieldInsnNode(GETFIELD, API_BIOME_PARTICLES, "particleData", "Ljava/lang/Object;"));
            b2n.add(new MethodInsnNode(INVOKESTATIC, CRAFT_PARTICLE, "toNMS",
                    "(Lorg/bukkit/Particle;Ljava/lang/Object;)L"+PARTICLES_PARAM+";"));
            b2n.add(new InsnNode(SWAP));
            b2n.add(new InsnNode(DUP_X1));
            b2n.add(new FieldInsnNode(GETFIELD, API_BIOME_PARTICLES, "frequency", "F"));
            b2n.add(new TypeInsnNode(NEW, BIOME_PARTICLE));
            b2n.add(new InsnNode(DUP_X2));
            b2n.add(new InsnNode(DUP_X2));
            b2n.add(new InsnNode(POP));
            b2n.add(new MethodInsnNode(INVOKESPECIAL,
                    BIOME_PARTICLE, "<init>", "(L" + PARTICLES_PARAM + ";F)V"));
            b2n.add(new InsnNode(DUP_X1));
            b2n.add(new InsnNode(SWAP));
            b2n.add(new FieldInsnNode(PUTFIELD, BIOME_PARTICLE,
                    "kibbleBiomeParticles", "L" + API_BIOME_PARTICLES + ";"));
                }, n2b -> {
            // TODO: Conversion if kibbleBiomeParticles is null
            n2b.add(new FieldInsnNode(GETFIELD, BIOME_PARTICLE,
                    "kibbleBiomeParticles", "L" + API_BIOME_PARTICLES + ";"));
                });
        // AmbientSound Methods
        String CRAFT_SOUND = NMS_CRAFT_SOUND.replace("$NMS", NMS);
        String SOUND_EFFECT = NMS_SOUND_EFFECT.replace("$NMS", NMS);
        FieldNode ambientSound = ASMUtils.findFieldBySignature(biomeFog,
                "Ljava/util/Optional<L" + SOUND_EFFECT + ";>;");
        if (ambientSound == null) {
            wtf(NMS, "0x03");
            return;
        }
        addObjectMethods(biomeBase, biomeFogField, BIOME_FOG,
                ambientSound, BUKKIT_SOUND, "AmbientSound",
                b2n -> b2n.add(new MethodInsnNode(INVOKESTATIC, CRAFT_SOUND, "getSoundEffect",
                        "(L" + BUKKIT_SOUND + ";)L"+SOUND_EFFECT+";")),
                n2b -> n2b.add(new MethodInsnNode(INVOKESTATIC, CRAFT_SOUND, "getBukkit",
                        "(L" + SOUND_EFFECT + ";)L"+BUKKIT_SOUND+";")));
        // Finish setup
        classWriter = new ClassWriter(0);
        biomeParticlesCL.accept(classWriter);
        map.put(BIOME_PARTICLE+".class", classWriter.toByteArray());
        classWriter = cdp.newClassWriter();
        biomeBase.accept(classWriter);
        map.put(BIOME_BASE+".class", classWriter.toByteArray());
        classWriter = new ClassWriter(0);
        biomeFog.accept(classWriter);
        map.put(BIOME_FOG+".class", classWriter.toByteArray());
        map.put(CRAFT_WORLD+".class", newCraftWorld);
        installLib(map, inject);
    }

    private static boolean isB2BBInsn(AbstractInsnNode abstractInsnNode,String craftBlock) {
        if (abstractInsnNode.getOpcode() != INVOKESTATIC) return false;
        MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;
        return methodInsnNode.owner.equals(craftBlock) && methodInsnNode.name.equals("biomeToBiomeBase");
    }

    private static void addIntMethods(ClassNode classNode, FieldNode biomeFogAccess,
                                      String BIOME_FOG, FieldNode accessor,String name) {
        accessor.access &= ~(ACC_FINAL|ACC_PRIVATE);
        boolean optional = !accessor.desc.equals("I");
        MethodNode getter = new MethodNode(ACC_PUBLIC, "get" + name, "()I", null, null);
        getter.instructions.add(new VarInsnNode(ALOAD, 0));
        getter.instructions.add(new FieldInsnNode(GETFIELD, classNode.name, biomeFogAccess.name, biomeFogAccess.desc));
        getter.instructions.add(new FieldInsnNode(GETFIELD, BIOME_FOG, accessor.name, accessor.desc));
        if (optional) {
            getter.instructions.add(new InsnNode(DUP));
            LabelNode empty = new LabelNode();
            getter.instructions.add(new JumpInsnNode(IFNULL, empty));
            getter.instructions.add(new InsnNode(DUP));
            getter.instructions.add(new MethodInsnNode(
                    INVOKEVIRTUAL, "java/util/Optional", "isEmpty", "()Z", false));
            getter.instructions.add(new JumpInsnNode(IFNE, empty));
            getter.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,
                    "java/util/Optional", "get", "()Ljava/lang/Object;"));
            getter.instructions.add(new TypeInsnNode(CHECKCAST, "java/lang/Integer"));
            getter.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,
                    "java/lang/Integer", "intValue", "()I"));
            LabelNode end = new LabelNode();
            getter.instructions.add(new JumpInsnNode(GOTO, end));
            getter.instructions.add(empty);
            getter.instructions.add(new InsnNode(POP));
            getter.instructions.add(new InsnNode(ICONST_M1));
            getter.instructions.add(end);
        }
        getter.instructions.add(new InsnNode(IRETURN));
        classNode.methods.add(getter);
        MethodNode setter = new MethodNode(ACC_PUBLIC, "set" + name, "(I)V", null, null);
        setter.instructions.add(new VarInsnNode(ALOAD, 0));
        setter.instructions.add(new FieldInsnNode(GETFIELD, classNode.name, biomeFogAccess.name, biomeFogAccess.desc));
        setter.instructions.add(new VarInsnNode(ILOAD, 1));
        if (optional) {
            setter.instructions.add(new InsnNode(DUP));
            setter.instructions.add(new InsnNode(ICONST_M1));
            LabelNode empty = new LabelNode();
            setter.instructions.add(new JumpInsnNode(IF_ICMPEQ, empty));
            setter.instructions.add(new MethodInsnNode(INVOKESTATIC,
                    "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"));
            setter.instructions.add(new MethodInsnNode(INVOKESTATIC,
                    "java/util/Optional", "of", "(Ljava/lang/Object;)Ljava/util/Optional;"));
            LabelNode end = new LabelNode();
            setter.instructions.add(new JumpInsnNode(GOTO, end));
            setter.instructions.add(empty);
            setter.instructions.add(new InsnNode(POP));
            setter.instructions.add(new MethodInsnNode(INVOKESTATIC,
                    "java/util/Optional", "empty", "()Ljava/util/Optional;"));
            setter.instructions.add(end);
        }
        setter.instructions.add(new FieldInsnNode(PUTFIELD, BIOME_FOG, accessor.name, accessor.desc));
        setter.instructions.add(new InsnNode(RETURN));
        classNode.methods.add(setter);
    }

    private static void addObjectMethods(ClassNode classNode, FieldNode biomeFogAccess,
                                         String BIOME_FOG, FieldNode accessor, String bType, String name,
                                         Consumer<InsnList> b2n,Consumer<InsnList> n2b) {
        accessor.access &= ~(ACC_FINAL|ACC_PRIVATE);
        boolean fog = biomeFogAccess != null;
        boolean optional = accessor.signature != null && accessor.signature.startsWith("Ljava/util/Optional<L");
        String nmsType = optional ?
                accessor.signature.substring(21, accessor.signature.length()-3)
                : accessor.desc.substring(1, accessor.desc.length() - 1);
        {
            MethodNode getter = new MethodNode(ACC_PUBLIC, "get" + name, "()L" + bType + ";", null, null);
            getter.instructions.add(new VarInsnNode(ALOAD, 0));
            if (fog) {
                getter.instructions.add(new FieldInsnNode(GETFIELD,
                        classNode.name, biomeFogAccess.name, biomeFogAccess.desc));
                getter.instructions.add(new FieldInsnNode(
                        GETFIELD, BIOME_FOG, accessor.name, accessor.desc));
            } else {
                getter.instructions.add(new FieldInsnNode(
                        GETFIELD, classNode.name, accessor.name, accessor.desc));
            }
            getter.instructions.add(new InsnNode(DUP));
            LabelNode empty = new LabelNode();
            getter.instructions.add(new JumpInsnNode(IFNULL, empty));
            if (optional) {
                getter.instructions.add(new InsnNode(DUP));
                getter.instructions.add(new MethodInsnNode(
                        INVOKEVIRTUAL, "java/util/Optional", "isEmpty", "()Z", false));
                getter.instructions.add(new JumpInsnNode(IFNE, empty));
                getter.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,
                        "java/util/Optional", "get", "()Ljava/lang/Object;"));
                getter.instructions.add(new TypeInsnNode(CHECKCAST, nmsType));
            }
            n2b.accept(getter.instructions);
            LabelNode end = new LabelNode();
            getter.instructions.add(new JumpInsnNode(GOTO, end));
            getter.instructions.add(empty);
            getter.instructions.add(new InsnNode(POP));
            getter.instructions.add(new InsnNode(ACONST_NULL));
            getter.instructions.add(end);
            getter.instructions.add(new InsnNode(ARETURN));
            classNode.methods.add(getter);
        }
        {
            MethodNode setter = new MethodNode(ACC_PUBLIC, "set" + name, "(L" + bType + ";)V", null, null);
            setter.instructions.add(new VarInsnNode(ALOAD, 0));
            if (fog) {
                setter.instructions.add(new FieldInsnNode(
                        GETFIELD, classNode.name, biomeFogAccess.name, biomeFogAccess.desc));
            }
            setter.instructions.add(new VarInsnNode(ALOAD, 1));
            setter.instructions.add(new InsnNode(DUP));
            LabelNode empty = new LabelNode();
            setter.instructions.add(new JumpInsnNode(IFNULL, empty));
            b2n.accept(setter.instructions);
            LabelNode end = new LabelNode();
            if (optional) {
                setter.instructions.add(new MethodInsnNode(INVOKESTATIC,
                        "java/util/Optional", "ofNullable", "(Ljava/lang/Object;)Ljava/util/Optional;"));
                setter.instructions.add(new JumpInsnNode(GOTO, end));
                setter.instructions.add(empty);
                setter.instructions.add(new InsnNode(POP));
                setter.instructions.add(new InsnNode(ACONST_NULL));
            } else {
                setter.instructions.add(new JumpInsnNode(GOTO, end));
                setter.instructions.add(empty);
                setter.instructions.add(new InsnNode(POP));
                setter.instructions.add(new MethodInsnNode(INVOKESTATIC,
                        "java/util/Optional", "empty", "()Ljava/util/Optional;"));
            }
            setter.instructions.add(end);
            setter.instructions.add(new FieldInsnNode(PUTFIELD,
                    fog ? BIOME_FOG : classNode.name, accessor.name, accessor.desc));
            setter.instructions.add(new InsnNode(RETURN));
            classNode.methods.add(setter);
        }
    }

    public static void installLib(Map<String, byte[]> map, Map<String, byte[]> inject) throws IOException {
        if (!isSupported(map)) return; // Skip on pre 1.16.2
        for (String file:biomeApiClasses) {
            inject.put(file, KibblePatcher.readResource(file));
        }
        byte[] bytes = map.get(BUKKIT_BIOME+".class");
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);
        MethodNode methodNode = new MethodNode(ACC_PUBLIC, "getConfig", "()L" + API_BIOME_CONFIG + ";", null, null);
        methodNode.instructions.add(new MethodInsnNode(INVOKESTATIC,
                "org/bukkit/Bukkit", "getWorlds", "()Ljava/util/List;", false));
        methodNode.instructions.add(new InsnNode(ICONST_0));
        methodNode.instructions.add(new MethodInsnNode(INVOKEINTERFACE,
                "java/util/List", "get", "(I)Ljava/lang/Object;", true));
        methodNode.instructions.add(new TypeInsnNode(CHECKCAST, "org/bukkit/World"));
        methodNode.instructions.add(new VarInsnNode(ALOAD, 0));
        methodNode.instructions.add(new MethodInsnNode(INVOKEINTERFACE,
                "org/bukkit/World", "getBiomeConfig",
                "(L"+ BUKKIT_BIOME +";)L" + API_BIOME_CONFIG + ";", true));
        methodNode.instructions.add(new InsnNode(ARETURN));
        classNode.methods.add(methodNode);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        map.put(BUKKIT_BIOME+".class", classWriter.toByteArray());
        // World
        bytes = map.get(BUKKIT_WORLD+".class");
        classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);
        methodNode = new MethodNode(ACC_PUBLIC | ACC_ABSTRACT, "getBiomeConfig",
                "(L" + BUKKIT_BIOME + ";)L" + API_BIOME_CONFIG + ";", null, null);
        classNode.methods.add(methodNode);
        classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        map.put(BUKKIT_WORLD+".class", classWriter.toByteArray());
    }

    private static void wtf(String NMS, String state) {
        System.out.println("An feature installation has failed in an unexpected way. please report the issue with your server jar!");
        System.out.println("NMS: "+NMS);
        System.out.println("Debug state: "+ state);
    }
}
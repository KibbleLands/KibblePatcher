package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.rebuild.ClassDataProvider;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import net.kibblelands.patcher.utils.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.Map;

public class DimensionConfigAPIFeature implements Opcodes {
    private static final String BUKKIT_WORLD = "org/bukkit/World";
    private static final String API_DIMENSION_CONFIG = "net/kibblelands/server/dimension/DimensionConfig";
    private static final String NMS_DIMENSION_MANAGER = "net/minecraft/server/$NMS/DimensionManager";
    private static final String NMS_WORLD = "net/minecraft/server/$NMS/World";
    private static final String NMS_WORLD_SERVER = "net/minecraft/server/$NMS/WorldServer";
    private static final String NMS_CRAFT_WORLD = "org/bukkit/craftbukkit/$NMS/CraftWorld";

    private static final String[] dimensionApiClasses = new String[]{
            "net/kibblelands/server/dimension/DimensionConfig.class",
    };

    public static void install(CommonGenerator commonGenerator, Map<String, byte[]> map,
                               Map<String, byte[]> inject, ClassDataProvider cdp) throws IOException {
        if (!ASMUtils.supportRemoteDataEdit(map)) return; // Skip on pre 1.16.2
        String DIMENSION_MANAGER = commonGenerator.mapClass(NMS_DIMENSION_MANAGER);
        String WORLD = commonGenerator.mapClass(NMS_WORLD);
        String WORLD_SERVER = commonGenerator.mapClass(NMS_WORLD_SERVER);
        String CRAFT_WORLD = commonGenerator.mapClass(NMS_CRAFT_WORLD);
        ClassNode nmsWorld;
        ClassNode craftWorld;
        ClassNode dimensionManager;
        { // Start - Implement getDimensionConfig
            byte[] bytes = map.get(WORLD + ".class");
            if (bytes == null) {
                wtf("0x00", commonGenerator.getMapperInfo());
                return;
            }
            nmsWorld = new ClassNode();
            new ClassReader(bytes).accept(nmsWorld, 0);
            FieldNode dimensionManagerField = ASMUtils.findFieldByDesc(nmsWorld, "L" + DIMENSION_MANAGER + ";");
            if (dimensionManagerField == null) {
                wtf("0x01", commonGenerator.getMapperInfo());
                return;
            }
            if ((dimensionManagerField.access & ACC_PUBLIC) == 0) {
                dimensionManagerField.access &= ~(ACC_PRIVATE | ACC_PROTECTED);
                dimensionManagerField.access |= ACC_PUBLIC;
            }
            bytes = map.get(CRAFT_WORLD + ".class");
            if (bytes == null) {
                wtf("0x02", commonGenerator.getMapperInfo());
                return;
            }
            craftWorld = new ClassNode();
            new ClassReader(bytes).accept(craftWorld, ClassReader.SKIP_FRAMES);
            FieldNode serverWorldField = ASMUtils.findFieldByDesc(craftWorld, "L" + WORLD_SERVER + ";");
            if (serverWorldField == null) {
                wtf("0x03", commonGenerator.getMapperInfo());
                return;
            }
            MethodNode getDimensionConfig = new MethodNode(ACC_PUBLIC,
                    "getDimensionConfig", "()L" + API_DIMENSION_CONFIG + ";", null, null);
            InsnList insnList = getDimensionConfig.instructions;
            insnList.add(new VarInsnNode(ALOAD, 0));
            insnList.add(new FieldInsnNode(GETFIELD, CRAFT_WORLD,
                    serverWorldField.name, serverWorldField.desc));
            insnList.add(new FieldInsnNode(GETFIELD, WORLD_SERVER,
                    dimensionManagerField.name, dimensionManagerField.desc));
            insnList.add(new TypeInsnNode(CHECKCAST, API_DIMENSION_CONFIG));
            insnList.add(new InsnNode(ARETURN));
            craftWorld.methods.add(getDimensionConfig);
        } // End - Implement getDimensionConfig
        { // Start - Create DimensionConfig Implementation
            byte[] bytes = map.get(DIMENSION_MANAGER + ".class");
            if (bytes == null) {
                wtf("0x04", commonGenerator.getMapperInfo());
                return;
            }
            dimensionManager = new ClassNode();
            new ClassReader(bytes).accept(dimensionManager, ClassReader.SKIP_FRAMES);
            dimensionManager.interfaces.add(API_DIMENSION_CONFIG);
            FieldNode ambientLight = ASMUtils.findFieldByDesc(dimensionManager, "F");
            if (ambientLight == null) {
                wtf("0x05", commonGenerator.getMapperInfo());
                return;
            }
            ASMUtils.createAccessorIfNecessary(dimensionManager, ambientLight, "AmbientLight");
            String[] names = new String[]{"WaterEvaporate", "PiglinSafe",
                    "BedWorking", "RespawnAnchorWorking", "AllowRaids"};
            int[] indexes = new int[]{2, 5, 6, 7, 8};
            for (int i = 0; i < indexes.length; i++) {
                FieldNode zField = ASMUtils.findFieldByDescIndex(dimensionManager, "Z", indexes[i]);
                if (zField == null) {
                    wtf("0x1" + i, commonGenerator.getMapperInfo());
                    return;
                }
                ASMUtils.createAccessorIfNecessary(dimensionManager, zField, names[i]);
            }
        } // End - Create DimensionConfig Implementation
        { // Start - Inject class modifications
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            nmsWorld.accept(classWriter);
            map.put(WORLD+".class", classWriter.toByteArray());
            classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            craftWorld.accept(classWriter);
            map.put(CRAFT_WORLD+".class", classWriter.toByteArray());
            classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            dimensionManager.accept(classWriter);
            map.put(DIMENSION_MANAGER+".class", classWriter.toByteArray());
            installLib(map, inject);
        } // End - Inject class modifications
        commonGenerator.addChangeEntry("Added DimensionConfigAPI. " + ConsoleColors.CYAN + "(Feature)");
    }

    public static void installLib(Map<String, byte[]> map, Map<String, byte[]> inject) throws IOException {
        if (!ASMUtils.supportRemoteDataEdit(map)) return; // Skip on pre 1.16.2
        for (String file:dimensionApiClasses) {
            inject.put(file, IOUtils.readResource(file));
        }
        byte[] bytes = map.get(BUKKIT_WORLD+".class");
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);
        bytes = map.get(BUKKIT_WORLD+".class");
        classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);
        MethodNode methodNode = new MethodNode(ACC_PUBLIC | ACC_ABSTRACT, "getDimensionConfig",
                "()L" + API_DIMENSION_CONFIG + ";", null, null);
        classNode.methods.add(methodNode);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        map.put(BUKKIT_WORLD+".class", classWriter.toByteArray());
    }

    private static void wtf(String NMS, String state) {
        System.out.println("The DimensionConfigAPI installation has failed in an unexpected way. please report the issue with your server jar!");
        System.out.println("NMS: "+NMS);
        System.out.println("Debug state: "+ state);
    }
}

package fr.kibblesland.patcher;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

public class EntityCompact implements Opcodes {
    private static final String VILLAGER_PROFESSION = "org/bukkit/entity/Villager$Profession.class";

    public static void check(Map<String, byte[]> map, String mth, final int[] stats) {
        byte[] inv = map.get(VILLAGER_PROFESSION);
        ClassReader classReader = new ClassReader(inv);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        for (MethodNode methods: classNode.methods) {
            if (methods.name.equals("isZombie")) {
                return; // API already exists in this Spigot (Do nothing)
            }
        }
        if (classNode.version < V1_8) { // For default in interface
            classNode.version = V1_8;
        }
        MethodNode tmp;
        classNode.methods.add(tmp = new MethodNode(ACC_PUBLIC|ACC_SYNTHETIC, "isZombie", "()Z", null, null));
        LabelNode labelNode = new LabelNode();
        tmp.instructions = new InsnList();
        tmp.instructions.add(labelNode);
        tmp.instructions.add(new LineNumberNode(12345, labelNode));
        tmp.instructions.add(new InsnNode(ICONST_0));
        tmp.instructions.add(new InsnNode(IRETURN));
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(VILLAGER_PROFESSION, classWriter.toByteArray());
        stats[3]++;
    }
}

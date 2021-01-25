package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public class PlayerPatcherCompact {
    private static final String INVENTORY = "org/bukkit/inventory/PlayerInventory.class";

    public static void check(CommonGenerator commonGenerator,Map<String, byte[]> map, final int[] stats) {
        byte[] inv = map.get(INVENTORY);
        ClassReader classReader = new ClassReader(inv);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        if (ASMUtils.hasMethod(classNode, "getItemInMainHand")) {
            return; // API already exists in this Spigot (Do nothing)
        }
        ASMUtils.symlinkMethod(classNode, "getItemInHand", "getItemInMainHand");
        ASMUtils.symlinkMethod(classNode, "setItemInHand", "setItemInMainHand");
        ASMUtils.createStub(classNode, "getItemInOffHand", "()Lorg/bukkit/inventory/ItemStack;");
        ASMUtils.createStub(classNode, "setItemInOffHand", "(Lorg/bukkit/inventory/ItemStack;)V");
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        map.put(INVENTORY, classWriter.toByteArray());
        stats[3]++;
        commonGenerator.addChangeEntry("Backported PlayerInventory (get/set)ItemIn(Main/Off)Hand API " + ConsoleColors.CYAN + "(Retro compatibility)");
    }
}

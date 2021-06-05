package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.Map;

public class NPECheckFixes implements Opcodes {
    private static final String BEHAVIOUR_INTERACT = "net/minecraft/server/$NMS/BehaviorInteract.class";
    private static final String BEHAVIOUR_INTERACT_DOOR = "net/minecraft/server/$NMS/BehaviorInteractDoor.class";
    private static final String BEHAVIOUR_CONTROLLER = "net/minecraft/server/$NMS/BehaviorController";
    private static final InsnList INJECT = new InsnList();

    static {
        INJECT.add(new InsnNode(DUP));
        INJECT.add(new MethodInsnNode(INVOKEVIRTUAL, "java/util/Optional", "isEmpty", "()Z", false));
        LabelNode notEmpty = new LabelNode();
        INJECT.add(new JumpInsnNode(IFEQ, notEmpty));
        INJECT.add(new InsnNode(POP));
        INJECT.add(new InsnNode(ICONST_0));
        INJECT.add(new InsnNode(IRETURN));
        INJECT.add(notEmpty);
    }

    public static void patch(CommonGenerator commonGenerator, Map<String, byte[]> map) {
        String BEHAVIOUR_INTERACT_NMS = commonGenerator.nms(BEHAVIOUR_INTERACT);
        String BEHAVIOUR_INTERACT_DOOR_NMS = commonGenerator.nms(BEHAVIOUR_INTERACT_DOOR);
        String BEHAVIOUR_CONTROLLER_NMS = commonGenerator.nms(BEHAVIOUR_CONTROLLER);
        // This should fix logs spam of some servers.
        if (patchBehaviour(map, BEHAVIOUR_INTERACT_NMS, BEHAVIOUR_CONTROLLER_NMS) |
                patchBehaviour(map, BEHAVIOUR_INTERACT_DOOR_NMS, BEHAVIOUR_CONTROLLER_NMS)) {
            commonGenerator.addChangeEntry(
                    "Added \"Optional\" checks on BehaviorInteract. " + ConsoleColors.CYAN + "(Bug Fix)");
        }
    }

    private static boolean patchBehaviour(Map<String, byte[]> map,
                                       String behaviour,String behaviourController) {
        ClassNode classNode = new ClassNode();
        byte[] code = map.get(behaviour);
        if (code == null) return false;
        boolean hasWorked = false;
        new ClassReader(code).accept(classNode, 0);
        for (MethodNode methodNode: classNode.methods) {
            // Scan for methods without hasMemory returning a boolean
            if (!methodNode.desc.endsWith(")Z") ||
                    !ASMUtils.findNodes(methodNode.instructions,
                            node -> node.getOpcode() == INVOKEVIRTUAL &&
                                    ((MethodInsnNode) node).owner.equals(behaviourController) &&
                                    ((MethodInsnNode) node).name.equals("hasMemory")).isEmpty()) continue;
            List<AbstractInsnNode> nodes = ASMUtils.findNodes(methodNode.instructions,
                    node -> node.getOpcode() == INVOKEVIRTUAL &&
                            ((MethodInsnNode) node).owner.equals(behaviourController) &&
                            ((MethodInsnNode) node).name.equals("getMemory") &&
                            ((MethodInsnNode) node).desc.endsWith(")Ljava/util/Optional;"));
            if (nodes.isEmpty()) continue; // Nothing to do
            for (AbstractInsnNode insnNode:nodes) {
                methodNode.instructions.insert(insnNode, ASMUtils.copyInsnList(INJECT));
            }
            hasWorked = true;
        }
        if (hasWorked) {
            ClassWriter classWriter = new ClassWriter(0);
            classNode.accept(classWriter);
            map.put(behaviour, classWriter.toByteArray());
        }
        return hasWorked;
    }
}

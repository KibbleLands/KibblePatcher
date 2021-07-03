package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

public class BookCrashFixer implements Opcodes {
    private static final String NMS_PACKED_BOOK_EDIT = "net/minecraft/server/$NMS/PacketPlayInBEdit.class";
    private static final String NMS_PLAYER_CONNECTION = "net/minecraft/server/$NMS/PlayerConnection";
    private static final String NMS_CUSTOM_PAYLOAD = "net/minecraft/server/$NMS/PacketPlayInCustomPayload";
    private static final String NMS_ITEMS = "net/minecraft/server/$NMS/Items";
    private static final String NMS_ITEM = "net/minecraft/server/$NMS/Item";
    private static final String NMS_ITEM_STACK = "net/minecraft/server/$NMS/ItemStack";
    private static final String WRITABLE_BOOK = "WRITABLE_BOOK";

    public static void patch(CommonGenerator commonGenerator,Map<String, byte[]> map, final int[] stats) {
        if (map.containsKey(commonGenerator.mapClass(NMS_PACKED_BOOK_EDIT))) {
            return; // The bug has already been patched
        }
        final String PLAYER_CONNECTION = commonGenerator.mapClass(NMS_PLAYER_CONNECTION);
        final String CUSTOM_PAYLOAD_PACKET = commonGenerator.mapClass(NMS_CUSTOM_PAYLOAD);
        final String ITEMS = commonGenerator.mapClass(NMS_ITEMS);
        final String ITEM = commonGenerator.mapClass(NMS_ITEM);
        final String ITEM_STACK = commonGenerator.mapClass(NMS_ITEM_STACK);
        ClassNode classNode = new ClassNode();
        new ClassReader(map.get(PLAYER_CONNECTION+".class")).accept(classNode, 0);
        final String METHOD_DESC = "(L"+CUSTOM_PAYLOAD_PACKET+";)V";
        MethodNode packetHandler = null;
        for (MethodNode methodNode: classNode.methods) {
            if (methodNode.desc.equals(METHOD_DESC)) {
                packetHandler = methodNode;
                break;
            }
        }
        if (packetHandler == null) {
            wtf(commonGenerator.getMapperInfo(), "NoCustomPayloadHandler");
            return;
        }
        InsnList getItem = new InsnList();
        for (AbstractInsnNode insnNode:packetHandler.instructions) {
            if (insnNode.getOpcode() != INVOKEVIRTUAL ||
                    !(insnNode instanceof MethodInsnNode)) continue;
            MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
            if (!(methodInsnNode.name.equals("getItemInMainHand") ||
                    methodInsnNode.name.equals("getItemInHand") )) continue;
            while (insnNode.getOpcode() != ALOAD || !(insnNode instanceof VarInsnNode)
                    || ((VarInsnNode) insnNode).var != 0) {
                getItem.insert(insnNode.clone(null));
                insnNode = insnNode.getPrevious();
                if (insnNode == null) {
                    wtf(commonGenerator.getMapperInfo(), "NullPreviousInstruction");
                    return;
                }
            }
            getItem.insert(new VarInsnNode(ALOAD, 0));
            break;
        }
        if (getItem.size() == 0) {
            wtf(commonGenerator.getMapperInfo(), "GetPlayerItemNotFound");
            return;
        }
        getItem.add(new InsnNode(DUP));
        LabelNode nullItem = new LabelNode();
        getItem.add(new JumpInsnNode(IFNULL, nullItem));
        getItem.add(new MethodInsnNode(INVOKEVIRTUAL, ITEM_STACK, "getItem", "()L"+ITEM+";"));
        getItem.add(new FieldInsnNode(GETSTATIC, ITEMS, WRITABLE_BOOK, "L"+ITEM+";"));
        LabelNode validItem = new LabelNode();
        getItem.add(new JumpInsnNode(IF_ACMPEQ, validItem));
        getItem.add(new InsnNode(RETURN));
        getItem.add(nullItem);
        getItem.add(new InsnNode(POP));
        getItem.add(new InsnNode(RETURN));
        getItem.add(validItem);
        for (String channel : new String[]{"MC|BEdit", "MC|BSign"}) {
            AbstractInsnNode insnNode = ASMUtils.findLdc(packetHandler.instructions, channel);
            if (insnNode == null) {
                wtf(commonGenerator.getMapperInfo(), "UnhandledChannel: "+channel);
                return;
            }
            while (insnNode.getOpcode() != IFEQ) {
                insnNode = insnNode.getNext();
            }
            packetHandler.instructions.insert(
                    insnNode, ASMUtils.copyInsnList(getItem));
        }
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(PLAYER_CONNECTION+".class", classWriter.toByteArray());
        commonGenerator.addChangeEntry("Added mitigation against the CustomPacketPayload exploit " + ConsoleColors.CYAN + "(Security)");
        stats[5]++;
    }

    private static void wtf(String NMS, String state) {
        System.out.println("A security patch has failed in an unexpected way. please report the issue with your server jar!");
        System.out.println("NMS: "+NMS);
        System.out.println("Debug state: "+ state);
    }
}

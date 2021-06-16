package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

public class AuthentificationHardening implements Opcodes {
    private static final String LOGIN_LISTENER = "net/minecraft/server/$NMS/LoginListener";
    private static final String PACKET_AUTH = "net/minecraft/server/$NMS/PacketLoginInEncryptionBegin";
    private static final String BASE_COMPONENT = "net/minecraft/server/$NMS/IChatBaseComponent";
    private static final String CHAT_MESSAGE = "net/minecraft/server/$NMS/ChatMessage";
    private static final String INVALID_DATA_TRANSLATE_KEY = "multiplayer.disconnect.unexpected_query_response";
    private static final String INVALID_DATA_MESSAGE = "Unexpected custom data from client";

    public static void patch(CommonGenerator commonGenerator, Map<String, byte[]> map, final int[] stats) {
        String loginListener = commonGenerator.mapClass(LOGIN_LISTENER);
        String authPacket = commonGenerator.mapClass(PACKET_AUTH);
        String baseComponent = commonGenerator.mapClass(BASE_COMPONENT);
        String chatMessage = commonGenerator.mapClass(CHAT_MESSAGE);
        String loginListenerCL = loginListener + ".class";
        if (!map.containsKey(authPacket + ".class")) {
            return;
        }
        boolean useComponentMode =
                map.containsKey(baseComponent + ".class")
                        && map.containsKey(chatMessage + ".class");
        byte[] loginListenerData = map.get(loginListenerCL);
        if (loginListenerData == null) {
            return;
        }
        ClassNode classNode = new ClassNode();
        new ClassReader(loginListenerData).accept(classNode, 0);
        if (useComponentMode) {
            useComponentMode = ASMUtils.hasMethod(classNode, "disconnect", "(L"+baseComponent+";)V");
        }
        MethodNode methodNode = ASMUtils.findMethodByDesc(classNode, "(L"+authPacket+";)V");
        if (methodNode == null) {
            return;
        }
        classNode.fields.add(new FieldNode(ACC_PRIVATE, "login2Received", "Z", null, null));
        InsnList insnList = new InsnList();
        insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new FieldInsnNode(GETFIELD, loginListener, "login2Received", "Z"));
        LabelNode labelNode = new LabelNode();
        insnList.add(new JumpInsnNode(IFEQ, labelNode));
        if (useComponentMode) {
            insnList.add(new VarInsnNode(ALOAD, 0));
            insnList.add(new TypeInsnNode(NEW, chatMessage));
            insnList.add(new InsnNode(DUP));
            insnList.add(new LdcInsnNode(INVALID_DATA_TRANSLATE_KEY));
            insnList.add(new MethodInsnNode(INVOKESPECIAL,
                    chatMessage, "<init>", "(Ljava/lang/String;)V", false));
            insnList.add(new TypeInsnNode(CHECKCAST, baseComponent));
            insnList.add(new MethodInsnNode(INVOKEVIRTUAL,
                    loginListener, "disconnect", "(L"+baseComponent+";)V", false));
        } else {
            insnList.add(new VarInsnNode(ALOAD, 0));
            insnList.add(new LdcInsnNode(INVALID_DATA_MESSAGE));
            insnList.add(new MethodInsnNode(INVOKEVIRTUAL,
                    loginListener, "disconnect", "(Ljava/lang/String;)V", false));
        }
        insnList.add(new InsnNode(RETURN));
        insnList.add(labelNode);
        insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new InsnNode(ICONST_1));
        insnList.add(new FieldInsnNode(PUTFIELD, loginListener, "login2Received", "Z"));
        methodNode.instructions.insert(insnList);
        // Save patched class
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        map.put(loginListenerCL, classWriter.toByteArray());
        commonGenerator.addChangeEntry("Hardened online mode authentification. " + ConsoleColors.CYAN + "(Security)");
        stats[5]++;
    }
}

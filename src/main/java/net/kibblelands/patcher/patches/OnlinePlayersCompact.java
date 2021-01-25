package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

public class OnlinePlayersCompact implements Opcodes {
    private static final String BUKKIT = "org/bukkit/Bukkit.class";
    private static final String SERVER = "org/bukkit/Server.class";
    private static final String GET_ONLINE_PLAYERS = "getOnlinePlayers";
    private static final String DESC_LEGACY = "()[Lorg/bukkit/entity/Player;";
    private static final String DESC_NEW = "()Ljava/util/Collection;";

    public static void check(CommonGenerator commonGenerator,Map<String, byte[]> map, final int[] stats) {
        byte[] bukkit = map.get(BUKKIT);
        boolean[] backport = new boolean[]{false};
        boolean didWork = false;
        ClassReader classReader = new ClassReader(bukkit);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        if (doPatch(classNode, BUKKIT.replace(".class", ""), true, backport)) {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(classWriter);
            map.put(BUKKIT, classWriter.toByteArray());
            didWork = true;
        }

        byte[] server = map.get(SERVER);
        classReader = new ClassReader(server);
        classNode = new ClassNode();
        classReader.accept(classNode, 0);
        if (doPatch(classNode, SERVER.replace(".class", ""), false, backport)) {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(classWriter);
            map.put(SERVER, classWriter.toByteArray());
            didWork = true;
        }

        if (didWork) {
            commonGenerator.addChangeEntry((backport[0] ? "Ported back the recent" :
                    "Added the old array based") + " getOnlinePlayers API " + ConsoleColors.CYAN + "(Retro Compatibility)");
            stats[3]++;
        }
    }

    private static boolean doPatch(ClassNode classNode,String self,boolean isStatic,boolean[] backport) {
        if ((classNode.access & ACC_INTERFACE) != 0 && classNode.version < V1_8) { // For default in interface
            classNode.version = V1_8;
        }
        int access = (isStatic ? ACC_STATIC : 0) | ACC_PUBLIC | ACC_SYNTHETIC | ACC_BRIDGE;
        int invoke = isStatic ? INVOKESTATIC : INVOKEINTERFACE;
        if (!ASMUtils.hasMethod(classNode, GET_ONLINE_PLAYERS, DESC_LEGACY)) {
            MethodNode getOnlinePlayers = new MethodNode(
                    access, GET_ONLINE_PLAYERS, DESC_LEGACY, null, null);
            if (!isStatic) {
                getOnlinePlayers.instructions.add(new VarInsnNode(ALOAD, 0));
            }
            getOnlinePlayers.instructions.add(new MethodInsnNode(invoke, self, GET_ONLINE_PLAYERS, DESC_NEW));
            getOnlinePlayers.instructions.add(new InsnNode(ICONST_0));
            getOnlinePlayers.instructions.add(new TypeInsnNode(ANEWARRAY, "org/bukkit/entity/Player"));
            getOnlinePlayers.instructions.add(new MethodInsnNode(
                    INVOKEINTERFACE, "java/util/Collection", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;", true));
            getOnlinePlayers.instructions.add(new TypeInsnNode(CHECKCAST, "[Lorg/bukkit/entity/Player;"));
            getOnlinePlayers.instructions.add(new InsnNode(ARETURN));
            classNode.methods.add(getOnlinePlayers);
            return true;
        } else if (!ASMUtils.hasMethod(classNode, GET_ONLINE_PLAYERS, DESC_NEW)) {
            MethodNode getOnlinePlayers = new MethodNode(
                    access, GET_ONLINE_PLAYERS, DESC_NEW, null, null);
            if (!isStatic) {
                getOnlinePlayers.instructions.add(new VarInsnNode(ALOAD, 0));
            }
            getOnlinePlayers.instructions.add(new MethodInsnNode(invoke, self, GET_ONLINE_PLAYERS, DESC_LEGACY));
            getOnlinePlayers.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;"));
            getOnlinePlayers.instructions.add(new TypeInsnNode(CHECKCAST, "java/util/Collection"));
            getOnlinePlayers.instructions.add(new InsnNode(ARETURN));
            classNode.methods.add(getOnlinePlayers);
            backport[0] = true;
            return true;
        } else {
            return false;
        }
    }
}

package net.kibblelands.patcher.patches;

import net.kibblelands.patcher.CommonGenerator;
import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.utils.ConsoleColors;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.Map;

public class MethodResultCacheOptimizer implements Opcodes {
    private static final String FURNACE_TILE = "net/minecraft/server/$NMS/TileEntityFurnace.class";
    private static final String BLOCK_POSITION = "net/minecraft/server/$NMS/BlockPosition.class";
    private static final String CRAFT_BLOCK = "org/bukkit/craftbukkit/$NMS/block/CraftBlock.class";
    private static final String LOCATION = "org/bukkit/Location.class";

    public static void patch(CommonGenerator commonGenerator,Map<String, byte[]> map, final int[] stats) {
        String NMS = commonGenerator.getNMS();
        byte[] bytes = map.get(FURNACE_TILE.replace("$NMS", NMS));
        if (bytes != null) {
            ClassNode classNode = new ClassNode();
            new ClassReader(bytes).accept(classNode, 0);
            MethodNode methodNode = ASMUtils.findMethodBySignature(classNode,
                    "()Ljava/util/Map<Lnet/minecraft/server/$NMS/Item;Ljava/lang/Integer;>;".replace("$NMS", NMS));
            if (methodNode != null) {
                cacheMethodResult(classNode, methodNode);
                stats[4]++;
            }
            ClassWriter classWriter = new ClassWriter(0);
            classNode.accept(classWriter);
            map.put(FURNACE_TILE.replace("$NMS", NMS), classWriter.toByteArray());
            commonGenerator.addChangeEntry("Cache furnace fuel list " + ConsoleColors.CYAN + "(Optimisation)");
        }
        bytes = map.get(BLOCK_POSITION.replace("$NMS", NMS));
        if (bytes != null) {
            ClassNode classNode = new ClassNode();
            new ClassReader(bytes).accept(classNode, 0);
            if (cacheHashCode(classNode, true, true)) {
                if (!"java/lang/Object".equals(classNode.superName)) {
                    ClassWriter classWriter = new ClassWriter(0);
                    new ClassReader(map.get(classNode.superName + ".class")).accept(new ClassVisitor(ASMUtils.ASM_BUILD, classWriter) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if (name.equals("hashCode") && descriptor.equals("()I")) {
                                access &= ~ACC_FINAL;
                            }
                            return super.visitMethod(access, name, descriptor, signature, exceptions);
                        }
                    }, 0);
                    map.put(classNode.superName + ".class", classWriter.toByteArray());
                }
                ClassWriter classWriter = new ClassWriter(0);
                classNode.accept(classWriter);
                map.put(BLOCK_POSITION.replace("$NMS", NMS), classWriter.toByteArray());
                commonGenerator.addChangeEntry("Cache BlockPosition hash " + ConsoleColors.CYAN + "(Optimisation)");
            }
        }
        bytes = map.get(CRAFT_BLOCK.replace("$NMS", NMS));
        if (bytes != null) {
            ClassNode classNode = new ClassNode();
            new ClassReader(bytes).accept(classNode, 0);
            if (cacheHashCode(classNode, true, false)) {
                ClassWriter classWriter = new ClassWriter(0);
                classNode.accept(classWriter);
                map.put(CRAFT_BLOCK.replace("$NMS", NMS), classWriter.toByteArray());
                commonGenerator.addChangeEntry("Cache CraftBlock hash " + ConsoleColors.CYAN + "(Optimisation)");
            }
        }
        bytes = map.get(LOCATION);
        if (bytes != null) {
            ClassNode classNode = new ClassNode();
            new ClassReader(bytes).accept(classNode, 0);
            if (cacheHashCode(classNode, false, false)) {
                ClassWriter classWriter = new ClassWriter(0);
                classNode.accept(classWriter);
                map.put(LOCATION, classWriter.toByteArray());
                commonGenerator.addChangeEntry("Cache Location hash " + ConsoleColors.CYAN + "(Optimisation)");
            }
        }
    }

    public static boolean cacheHashCode(ClassNode classNode,boolean immutableHash,boolean finalHash) {
        MethodNode hashCode = ASMUtils.findMethod(classNode, "hashCode", "()I");
        if (hashCode == null) {
            if (immutableHash) {
                String superAsm = classNode.superName;
                if (superAsm.equals("java/lang/Object")) {
                    return false;
                }
                // Create default hashCode implementation
                classNode.methods.add(hashCode = new MethodNode(ACC_PUBLIC, "hashCode", "()I", null, null));
                hashCode.instructions.add(new VarInsnNode(ALOAD, 0));
                hashCode.instructions.add(new MethodInsnNode(INVOKESPECIAL, superAsm, "hashCode", "()I"));
                hashCode.instructions.add(new InsnNode(IRETURN));
            } else {
                return false;
            }
        }
        cacheMethodResult(classNode, hashCode);
        if (immutableHash) return true;
        final String fName = "hashCode$cache";
        final String asmName = classNode.name;
        for (MethodNode methodNode: classNode.methods) {
            InsnList insnList = methodNode.instructions;
            if ((methodNode.access & ACC_STATIC) != 0 || methodNode == hashCode
                    || methodNode.name.equals("<init>") || insnList == null) {
                continue; // This method is not a target for hash reset.
            }
            boolean shouldResetHash = false;
            for (AbstractInsnNode insnNode : insnList) {
                if (insnNode.getOpcode() == PUTFIELD &&
                        ((FieldInsnNode) insnNode).owner.equals(asmName)) {
                    shouldResetHash = true;
                    break;
                }
            }
            if (shouldResetHash) { // prepend hash reset to the method if needed
                insnList.insert(new FieldInsnNode(PUTFIELD, asmName, fName, "I"));
                insnList.insert(new InsnNode(ICONST_0));
                insnList.insert(new VarInsnNode(ALOAD, 0));
            }
        }
        return true;
    }

    public static void cacheMethodResult(ClassNode classNode, MethodNode methodNode) {
        String resultDesc = methodNode.desc.substring(methodNode.desc.lastIndexOf(')') + 1);
        if (resultDesc.length() == 1 && "IBS".indexOf(resultDesc.charAt(0)) == -1) throw new IllegalArgumentException("cacheMethodResult Only work with non primitive result methods");
        if (methodNode.instructions == null || methodNode.instructions.size() == 0) throw new IllegalArgumentException("The method need an actual body content!");
        String resultSign = (methodNode.signature == null) ? null :
                methodNode.signature.substring(methodNode.signature.lastIndexOf(')') + 1);
        if (resultDesc.equals(resultSign)) resultSign = null;
        final String fName = methodNode.name + "$cache";
        boolean isStatic = (methodNode.access & ACC_STATIC) != 0;
        classNode.fields.add(new FieldNode(ACC_PUBLIC|(methodNode.access & ACC_STATIC), fName, resultDesc, resultSign, null));
        final int RETURN_OPCODE = resultDesc.length() == 1 ? IRETURN : ARETURN;
        final int UNSET_OPCODE = resultDesc.length() == 1 ? IFEQ : IFNULL;
        InsnList begin = new InsnList();
        if (!isStatic) {
            begin.add(new VarInsnNode(ALOAD, 0));
        }
        begin.add(new FieldInsnNode(isStatic?GETSTATIC:GETFIELD, classNode.name, fName, resultDesc));
        begin.add(new InsnNode(DUP));
        LabelNode labelNode = new LabelNode();
        begin.add(new JumpInsnNode(UNSET_OPCODE, labelNode));
        begin.add(new InsnNode(RETURN_OPCODE));
        begin.add(labelNode);
        for (AbstractInsnNode insn: methodNode.instructions.toArray()) {
            if (insn.getOpcode() == RETURN_OPCODE) {
                InsnList insns = new InsnList();
                insns.add(new InsnNode(DUP));
                if (!isStatic) {
                    insns.add(new VarInsnNode(ALOAD, 0));
                    insns.add(new InsnNode(SWAP));
                }
                insns.add(new FieldInsnNode(isStatic?PUTSTATIC:PUTFIELD, classNode.name, fName, resultDesc));
                methodNode.instructions.insertBefore(insn, insns);
            }
        }
        methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), begin);
    }
}

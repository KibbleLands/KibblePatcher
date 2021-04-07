/*
  Copyright gudenau 2020
From:
  https://github.com/gudenau/minecraft-gudfps
 */

package net.kibblelands.patcher.ext;

import net.kibblelands.patcher.utils.ASMUtils;
import net.kibblelands.patcher.rebuild.ClassDataProvider;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.List;

public class ForEachRemover implements Opcodes {
    public static void transform(ClassNode classNode,final ClassDataProvider classDataProvider,final int[] stats){
        // AtomicBoolean changed = new AtomicBoolean(false);

        for(MethodNode method : classNode.methods){
            InsnList instructions = method.instructions;
            ASMUtils.findNodes(instructions, (node)->{
                // Get all the dynamic nodes that look right
                if(node instanceof InvokeDynamicInsnNode){
                    InvokeDynamicInsnNode invoke = (InvokeDynamicInsnNode)node;
                    return "accept".equals(invoke.name) && "()Ljava/util/function/Consumer;".equals(invoke.desc);
                }
                return false;
            }).stream()
                    .filter((node)->{
                        // Make sure the consumers go into a foreach
                        AbstractInsnNode next = node.getNext();
                        if(next instanceof MethodInsnNode){
                            MethodInsnNode insn = (MethodInsnNode)next;
                            return "forEach".equals(insn.name) && "(Ljava/util/function/Consumer;)V".equals(insn.desc);
                        }
                        return false;
                    }).forEach((invokeDynamic0)->{
                InvokeDynamicInsnNode invokeDynamic = (InvokeDynamicInsnNode) invokeDynamic0;
                MethodInsnNode methodNode = (MethodInsnNode)invokeDynamic.getNext();

                // Transform them

                // Extra info from existing instructions
                Handle targetHandle = (Handle)invokeDynamic.bsmArgs[1];
                int targetOpcode = ForEachRemover.opcodeFromHandle(targetHandle);
                Type targetType = (Type)invokeDynamic.bsmArgs[2];

                // Figure out our starting indices
                int maxLocals = method.maxLocals;

                List<LocalVariableNode> localVariables = method.localVariables;
                if(localVariables != null){
                    for(LocalVariableNode localVariable : method.localVariables){
                        maxLocals = Math.max(localVariable.index, maxLocals);
                    }
                }

                // Figure out our locals
                int localIterator = maxLocals++;
                //int localObject = maxLocals++;

                // Store the new max
                method.maxLocals = maxLocals;

                // Figure out the collection
                String collection = methodNode.owner;
                boolean isCollectionInterface = classDataProvider.getClassData(collection).isInterface();
                /*try{
                    // Is this really the best way?
                    Boolean bool = LockUtils.withReadLock(INTERFACE_MAP_LOCK, ()->INTERFACE_MAP.get(collection));
                    if(bool == null){
                        Class<?> collectionClass = getClass().getClassLoader().loadClass(collection.replaceAll("/", "."));
                        isCollectionInterface = collectionClass.isInterface();
                        LockUtils.withWriteLock(INTERFACE_MAP_LOCK, ()->INTERFACE_MAP.put(collection, isCollectionInterface));
                    }else{
                        isCollectionInterface = bool;
                    }
                }catch(ClassNotFoundException ignored){
                    stats.incrementStat("failed");
                    return;
                }*/

                // Build the for-each loop
                InsnList patch = new InsnList();
                LabelNode breakNode = new LabelNode();
                LabelNode continueNode = new LabelNode();

                // Iterator iter = collection.iterator();
                patch.add(new MethodInsnNode(
                        isCollectionInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                        collection,
                        "iterator",
                        "()Ljava/util/Iterator;",
                        isCollectionInterface
                ));
                patch.add(new VarInsnNode(ASTORE, localIterator));

                // while(iter.hasNext()){
                patch.add(continueNode);
                patch.add(new FrameNode(
                        F_APPEND,
                        1, new Object[]{"java/util/Iterator"},
                        0, null
                ));
                patch.add(new VarInsnNode(ALOAD, localIterator));
                patch.add(new MethodInsnNode(
                        INVOKEINTERFACE,
                        "java/util/Iterator",
                        "hasNext",
                        "()Z",
                        true
                ));
                patch.add(new JumpInsnNode(IFEQ, breakNode));

                //   lambda(iter.next());
                patch.add(new VarInsnNode(ALOAD, localIterator));
                patch.add(new MethodInsnNode(
                        INVOKEINTERFACE,
                        "java/util/Iterator",
                        "next",
                        "()Ljava/lang/Object;",
                        true
                ));

                Type targetMethod = Type.getMethodType(targetHandle.getDesc());
                Type[] targetArgs = targetMethod.getArgumentTypes();
                boolean passObject = targetArgs.length == targetType.getArgumentTypes().length;

                patch.add(new TypeInsnNode(
                        CHECKCAST,
                        passObject ? targetArgs[targetArgs.length - 1].getInternalName() : targetHandle.getOwner()
                ));
                patch.add(new MethodInsnNode(
                        targetOpcode,
                        targetHandle.getOwner(),
                        targetHandle.getName(),
                        targetHandle.getDesc(),
                        targetOpcode == INVOKEINTERFACE
                ));

                // Original implementation doesn't expect method to have a return value
                switch (Type.getMethodType(targetHandle.getDesc()).getReturnType().getSize()) {
                    case 2:
                        patch.add(new InsnNode(POP2));
                        break;
                    case 1:
                        patch.add(new InsnNode(POP));
                    case 0:
                }

                // }
                patch.add(new JumpInsnNode(GOTO, continueNode));
                patch.add(breakNode);
                patch.add(new FrameNode(F_CHOP, 0, null, 0, null));

                instructions.insertBefore(invokeDynamic, patch);
                instructions.remove(invokeDynamic);
                instructions.remove(methodNode);

                // changed.set(true);
                //stats.incrementStat("success");
                stats[6]++;
            });
        }

        /*if(changed.get()) System.out.println(classNode.name); {
            flags.requestFrames();
            return true;
        }else{
            return false;
        } */
    }

    public static int opcodeFromHandle(Handle handle){
        switch(handle.getTag()){
            case H_GETFIELD: return GETFIELD;
            case H_GETSTATIC: return GETSTATIC;
            case H_PUTFIELD: return PUTFIELD;
            case H_PUTSTATIC: return PUTSTATIC;
            case H_INVOKEVIRTUAL: return INVOKEVIRTUAL;
            case H_INVOKESTATIC: return INVOKESTATIC;
            case H_INVOKESPECIAL: return INVOKESPECIAL;
            case H_NEWINVOKESPECIAL: return NEW;
            case H_INVOKEINTERFACE: return INVOKEINTERFACE;
            default: throw new IllegalArgumentException(String.format(
                    "Unknown handle type: %d",
                    handle.getTag()
            ));
        }
    }
}

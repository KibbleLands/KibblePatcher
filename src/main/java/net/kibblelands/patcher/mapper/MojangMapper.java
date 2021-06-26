package net.kibblelands.patcher.mapper;

import org.objectweb.asm.Type;

import java.util.HashMap;

public class MojangMapper extends NMSMapper {
    private final HashMap<String, String> clMap = new HashMap<>();

    public MojangMapper(String nms) {
        super(nms);
        this.clMap.put("net/minecraft/server/$NMS/BehaviorInteract",
                "net/minecraft/world/entity/ai/behavior/BehaviorInteract");
        this.clMap.put("net/minecraft/server/$NMS/BehaviorInteractDoor",
                "net/minecraft/world/entity/ai/behavior/BehaviorInteractDoor");
        this.clMap.put("net/minecraft/server/$NMS/BehaviorController",
                "net/minecraft/world/entity/ai/BehaviorController");
        this.clMap.put("net/minecraft/server/$NMS/BiomeBase", "net/minecraft/world/level/biome/BiomeBase");
        this.clMap.put("net/minecraft/server/$NMS/BiomeFog", "net/minecraft/world/level/biome/BiomeFog");
        this.clMap.put("net/minecraft/server/$NMS/BiomeFog$GrassColor",
                "net/minecraft/world/level/biome/BiomeFog$GrassColor");
        this.clMap.put("net/minecraft/server/$NMS/BiomeParticles",
                "net/minecraft/world/level/biome/BiomeParticles");
        this.clMap.put("net/minecraft/server/$NMS/Block", "net/minecraft/world/level/block/Block");
        this.clMap.put("net/minecraft/server/$NMS/IBlockData", "net/minecraft/world/level/block/state/IBlockData");
        this.clMap.put("net/minecraft/server/$NMS/BaseBlockPosition", "net/minecraft/core/BaseBlockPosition");
        this.clMap.put("net/minecraft/server/$NMS/BlockPosition", "net/minecraft/core/BlockPosition");
        this.clMap.put("net/minecraft/server/$NMS/BlockPosition$MutableBlockPosition",
                "net/minecraft/core/BlockPosition$MutableBlockPosition");
        this.clMap.put("net/minecraft/server/$NMS/DedicatedServer", "net/minecraft/server/dedicated/DedicatedServer");
        this.clMap.put("net/minecraft/server/$NMS/DimensionManager",
                "net/minecraft/world/level/dimension/DimensionManager");
        this.clMap.put("net/minecraft/server/$NMS/IChatBaseComponent",
                "net/minecraft/network/chat/IChatBaseComponent");
        this.clMap.put("net/minecraft/server/$NMS/ChatMessage", "net/minecraft/network/chat/ChatMessage");
        this.clMap.put("net/minecraft/server/$NMS/Chunk", "net/minecraft/world/level/chunk/Chunk");
        this.clMap.put("net/minecraft/server/$NMS/IChunkAccess", "net/minecraft/world/level/chunk/IChunkAccess");
        this.clMap.put("net/minecraft/server/$NMS/ChunkProviderServer",
                "net/minecraft/server/level/ChunkProviderServer");
        this.clMap.put("net/minecraft/server/$NMS/ChunkStatus", "net/minecraft/world/level/chunk/ChunkStatus");
        this.clMap.put("net/minecraft/server/$NMS/CommandDataAccessorEntity",
                "net/minecraft/server/commands/data/CommandDataAccessorEntity");
        this.clMap.put("net/minecraft/server/$NMS/EntityHuman",
                "net/minecraft/world/entity/player/EntityHuman");
        this.clMap.put("net/minecraft/server/$NMS/LoginListener", "net/minecraft/server/network/LoginListener");
        this.clMap.put("net/minecraft/server/$NMS/MathHelper", "net/minecraft/util/MathHelper");
        this.clMap.put("net/minecraft/server/$NMS/MinecraftServer", "net/minecraft/server/MinecraftServer");
        this.clMap.put("net/minecraft/server/$NMS/Music", "net/minecraft/sounds/Music");
        this.clMap.put("net/minecraft/server/$NMS/PacketLoginInEncryptionBegin",
                "net/minecraft/network/protocol/login/PacketLoginInEncryptionBegin");
        this.clMap.put("net/minecraft/server/$NMS/PacketPlayInBEdit",
                "net/minecraft/network/protocol/game/PacketPlayInBEdit");
        this.clMap.put("net/minecraft/server/$NMS/ParticleParam", "net/minecraft/core/particles/ParticleParam");
        this.clMap.put("net/minecraft/server/$NMS/SoundEffect", "net/minecraft/sounds/SoundEffect");
        this.clMap.put("net/minecraft/server/$NMS/TileEntityFurnace",
                "net/minecraft/world/level/block/entity/TileEntityFurnace");
        this.clMap.put("net/minecraft/server/$NMS/World", "net/minecraft/world/level/World");
        this.clMap.put("net/minecraft/server/$NMS/WorldServer", "net/minecraft/server/level/WorldServer");
    }

    @Override
    public String mapClass(String text) {
        if (text.startsWith("net/minecraft/")) {
            boolean signature = text.endsWith(">");
            boolean dotClass = text.endsWith(".class");
            if (dotClass) text = text.substring(0, text.length() - 6);
            String end = null;
            if (signature) {
                int index = text.indexOf('<');
                end = this.mapDesc("(" + text.substring(index + 1, text.length() - 1) + ")V");
                end = end.substring(1, end.length() - 2);
                text = text.substring(0, index);
            }
            String result = this.clMap.get(text);
            if (result == null) throw new Error("unable to find mapping for " + text);
            return signature ? text + end : (dotClass ? result + ".class" : result);
        }
        return super.mapClass(text);
    }

    public String mapDesc(String text) {
        if (text.length() < 3) return text;
        StringBuilder stringBuilder = new StringBuilder(text.length() + 8);
        final Type lastType;
        if (text.indexOf(0) == '(') {
            stringBuilder.append('(');
            for (Type type : Type.getArgumentTypes(text)) {
                if (type.getSort() == Type.OBJECT ||
                        type.getSort() == Type.ARRAY) {
                    String rawType = type.getDescriptor();
                    int i = rawType.indexOf('L');
                    if (i == -1) {
                        stringBuilder.append(rawType);
                        continue;
                    }
                    stringBuilder.append(rawType, 0, i + 1).append(
                            this.mapClass(rawType.substring(i + 1, rawType.length() - 1))).append(';');
                } else {
                    stringBuilder.append(type.getDescriptor());
                }
            }
            stringBuilder.append(')');
            lastType = Type.getReturnType(text);
        } else {
            lastType = Type.getType(text);
        }
        if (lastType.getSort() == Type.OBJECT ||
                lastType.getSort() == Type.ARRAY) {
            String rawType = lastType.getDescriptor();
            int i = rawType.indexOf('L');
            if (i == -1) {
                stringBuilder.append(rawType);
            } else {
                stringBuilder.append(rawType, 0, i + 1).append(
                        this.mapClass(rawType.substring(i + 1, rawType.length() - 1))).append(';');
            }
        } else {
            stringBuilder.append(lastType.getDescriptor());
        }
        return stringBuilder.toString();
    }

    @Override
    public String mapMethodName(String owner,String name) {
        return name;
    }

    @Override
    public String toString() {
        return "Mojang:" + this.nms;
    }
}

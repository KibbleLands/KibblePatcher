package net.kibblelands.patcher.mapper;

import org.objectweb.asm.Type;

import java.util.HashMap;

public class MojangMapper extends NMSMapper {
    private final HashMap<String, String> clMap = new HashMap<>();

    public MojangMapper(String nms) {
        super(nms);
        this.clMap.put("net/minecraft/server/$NMS/MathHelper", "net/minecraft/util/MathHelper");
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

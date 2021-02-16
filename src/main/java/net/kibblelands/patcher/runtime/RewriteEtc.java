package net.kibblelands.patcher.runtime;

import net.kibblelands.server.util.FastCollections;

import java.util.Map;

public class RewriteEtc {
    private static final Map<String, String> methodsRedirects = FastCollections.newMap();

    static {
        methodsRedirects.put("java/lang/Math.sin(D)D", "net/kibblelands/server/util/FastMath");
        methodsRedirects.put("java/lang/Math.cos(D)D", "net/kibblelands/server/util/FastMath");
        methodsRedirects.put("java/lang/Math.tan(D)D", "net/kibblelands/server/util/FastMath");
    }

    public static String rewriteOwner(String owner,String name,String desc) {
        return methodsRedirects.getOrDefault(owner + "." + name + desc, owner);
    }
}

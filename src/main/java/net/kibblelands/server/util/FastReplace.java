package net.kibblelands.server.util;

/**
 * This class is edited automatically by the patcher
 * to use Apache common lang implementation of replace
 */
public final class FastReplace {
    private FastReplace() {}

    public static String replace(String string,CharSequence from,CharSequence to) {
        return replace(string, from.toString(), to.toString());
    }

    public static String replace(String string,String from,String to) {
        return string.replace(from, to);
    }
}

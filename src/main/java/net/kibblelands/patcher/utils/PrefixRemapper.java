package net.kibblelands.patcher.utils;

import org.objectweb.asm.commons.Remapper;

public class PrefixRemapper extends Remapper {
    private final String from, to;
    private final int fromLen;

    public PrefixRemapper(String from, String to) {
        this.fromLen = from.length();
        this.from = from;
        this.to = to;
    }

    @Override
    public String map(String internalName) {
        return internalName.startsWith(this.from) ?
                this.to + internalName.substring(this.fromLen) : internalName;
    }

}

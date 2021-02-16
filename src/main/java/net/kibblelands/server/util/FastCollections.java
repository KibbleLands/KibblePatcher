package net.kibblelands.server.util;

import java.util.*;

/**
 * This class is edited automatically by the patcher
 * to use fastutil implementation of Set and Map if
 * available
 */
public final class FastCollections {
    private FastCollections() {}

    public static <T> Set<T> newSet() {
        return new HashSet<>();
    }

    public static <K, V> Map<K, V> newMap() {
        return new HashMap<>();
    }
}

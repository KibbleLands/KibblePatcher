package net.kibblelands.server.properties;

import org.bukkit.entity.Entity;

import java.util.Arrays;
import java.util.IdentityHashMap;

/**
 * Note: This implementation is not the patched server implementation
 * This implementation has for goal to emulate the entity properties
 * APIs for tests without the servers integration
 *
 * See {@link net.kibblelands.patcher.patches.EntityPropertiesFeature}
 * to see the better and faster implementation of the entity properties
 * APIs with server integration
 */
final class PropertiesImpl {
    /**
     * Note: The final implementation don't use Maps because map are slower
     * than how the patcher implement this feature with server integration
     * */
    private static final IdentityHashMap<Entity, Object[]> map = new IdentityHashMap<>();

    /**
     * Note: This implementation is not the server implementation
     *
     * See {@link net.kibblelands.patcher.patches.EntityPropertiesFeature}
     * if you want to se the real code executed on the server
     * */
    static Object[] getPropertiesArray(Entity entity,int size) {
        if (entity == null) {
            // For tests if patched in lib mode
            throw new NullPointerException();
        }
        // Note: The patcher implementation don't use Maps
        Object[] objects = map.get(entity);
        if (objects == null) {
            objects = new Object[size];
            map.put(entity, objects);
        } else if (objects.length < size) {
            objects = Arrays.copyOf(objects, size);
            map.put(entity, objects);
        }
        return objects;
    }

    static {
        // Warn the user if properties API is used without server integration
        System.err.println("/!\\ PropertiesAPI used without server integration /!\\");
    }
}

package net.kibblelands.server.properties;

import org.bukkit.entity.Entity;

import java.lang.ref.SoftReference;
import java.util.Objects;

/**
 * Implementation of {@link TempEntityProperty} with {@link java.lang.ref.SoftReference}
 * SoftReference is similar to WeakReferences but only cleared if the VM require more memory
 * Useful to create caches
 */
public class TempSoftEntityProperty<T> extends TempEntityPropertyBase<T> {
    private final Class<T> type;

    public TempSoftEntityProperty(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type must not be null!");
    }

    @Override
    public T get(Entity entity) {
        Object obj = this.getRawValue(entity);
        if (obj == null) return null;
        return this.type.cast(((SoftReference<?>) obj).get());
    }

    @Override
    public void set(Entity entity, T property) {
        this.setRawValue(entity, property == null ? null : new SoftReference<>(type.cast(property)));
    }
}

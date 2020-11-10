package net.kibblelands.server.properties;

import org.bukkit.entity.Entity;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * Implementation of {@link TempEntityProperty} with {@link WeakReference}
 * If all references of an object are weak the object will be cleared
 * Useful to create link between entities to avoid them to stay in memory if unloaded
 */
public class TempWeakEntityProperty<T> extends TempEntityPropertyBase<T> {
    private final Class<T> type;

    public TempWeakEntityProperty(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type must not be null!");
    }

    @Override
    public T get(Entity entity) {
        Object obj = this.getRawValue(entity);
        if (obj == null) return null;
        return this.type.cast(((WeakReference<?>) obj).get());
    }

    @Override
    public void set(Entity entity, T property) {
        this.setRawValue(entity, property == null ? null : new WeakReference<>(type.cast(property)));
    }
}

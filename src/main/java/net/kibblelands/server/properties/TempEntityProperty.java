package net.kibblelands.server.properties;

import org.bukkit.entity.Entity;

import java.util.Objects;

/**
 * This class has for goal to be a more CPU/RAM efficient way of storing
 * values inside of players/entities than most plugin side implementations
 * or the ones using HashMaps
 *
 * These properties are not stored on disk so they are cleared on each
 * entity load/unload or on player join/left
 */
public class TempEntityProperty<T> extends TempEntityPropertyBase<T> {
    private final Class<T> type;

    public TempEntityProperty(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type must not be null!");
    }

    @Override
    public T get(Entity entity) {
        return this.type.cast(this.getRawValue(entity));
    }

    @Override
    public void set(Entity entity, T property) {
        this.setRawValue(entity, type.cast(property));
    }
}

package net.kibblelands.server.properties;

import org.bukkit.entity.Entity;

public abstract class TempEntityPropertyBase<T> implements EntityProperty<T> {
    private static int size = 0;

    private final int id;

    public TempEntityPropertyBase() {
        this.id = size++;
    }

    protected final Object getRawValue(Entity entity) {
        return PropertiesImpl.getPropertiesArray(entity, size)[this.id];
    }

    protected final void setRawValue(Entity entity,Object value) {
        PropertiesImpl.getPropertiesArray(entity, size)[this.id] = value;
    }
}

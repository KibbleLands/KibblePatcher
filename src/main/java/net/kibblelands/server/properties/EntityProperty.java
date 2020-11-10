package net.kibblelands.server.properties;

import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.function.Function;

public interface EntityProperty<T> {
    T get(Entity player);

    void set(Entity player, T property);

    default void clear(Entity entity) {
        this.set(entity, null);
    }

    // Java 8 style method to help API transition

    default T getOrDefault(Entity entity, T def) {
        T t = get(entity);
        return t == null ? def : t;
    }

    default T computeIfAbsent(Entity entity,
                              Function<? super Entity, ? extends T> function) {
        Objects.requireNonNull(function, "function must not be null!");
        T v;
        if ((v = get(entity)) == null) {
            if ((v = function.apply(entity)) != null) {
                set(entity, v);
            }
        }
        return v;
    }
}

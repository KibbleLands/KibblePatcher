package net.kibblelands.server.biome;

import org.bukkit.Sound;

import java.util.Objects;

public final class BiomeMusic {
    public final Sound sound;
    public final int minDelay;
    public final int maxDelay;
    public final boolean replace;

    public BiomeMusic(Sound sound, int minDelay, int maxDelay, boolean replace) {
        this.sound = Objects.requireNonNull(sound, "sound must be a non null value");
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.replace = replace;

        if (this.minDelay < 0) {
            throw new IllegalArgumentException("minDelay must be positive (Found: "+minDelay+")");
        }
        if (this.maxDelay < 0) {
            throw new IllegalArgumentException("maxDelay must be positive (Found: "+minDelay+")");
        }
        if (this.minDelay > this.maxDelay) {
            throw new IllegalArgumentException("minDelay must be lesser or equals to maxDelay ("+minDelay +" > "+maxDelay+")");
        }
    }

    public BiomeMusic withSound(Sound sound) {
        return new BiomeMusic(sound, minDelay, maxDelay, replace);
    }

    public BiomeMusic withDelay(int minDelay, int maxDelay) {
        return new BiomeMusic(sound, minDelay, maxDelay, replace);
    }

    public BiomeMusic withReplace(boolean replace) {
        return new BiomeMusic(sound, minDelay, maxDelay, replace);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BiomeMusic that = (BiomeMusic) o;
        return minDelay == that.minDelay &&
                maxDelay == that.maxDelay &&
                replace == that.replace &&
                sound == that.sound;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sound, minDelay, maxDelay, replace);
    }
}

package net.kibblelands.server.biome;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class BiomeParticles {
    public final Particle particle;
    public final Object particleData;
    public final float frequency;

    public BiomeParticles(Particle particle, float frequency) {
        this(particle, null, frequency);
    }

    public BiomeParticles(Particle particle, Object particleData, float frequency) {
        // Check integrity
        if (particle == null) {
            throw new NullPointerException("Particle must not be null!");
        }
        if (frequency <= 0F) {
            throw new IllegalArgumentException("Frequency must be positive!");
        }
        Class<?> cl = particle.getDataType();
        // Set default particleData if none provided
        if (particleData == null) {
            switch (cl.getSimpleName()) {
                default:
                case "Void":
                    break;
                case "DustOptions":
                    particleData = new Particle.DustOptions(Color.WHITE, 1F);
                    break;
                case "ItemStack":
                    particleData = new ItemStack(Material.STONE);
                    break;
                case "BlockData":
                    particleData = Bukkit.createBlockData(Material.STONE);
                    break;
            }
        }
        if (particleData != null && (cl != Void.class && !cl.isInstance(particleData))) {
            throw new IllegalArgumentException("Invalid particleData type! (Found "
                    + particleData.getClass().getName() + ", expected "+ cl.getName() +")");
        }
        // Apply values
        this.particle = particle;
        this.particleData = particleData;
        this.frequency = frequency;
    }

    public BiomeParticles withData(Object particleData) {
        return new BiomeParticles(particle, particleData, frequency);
    }

    public BiomeParticles withFrequency(float frequency) {
        return new BiomeParticles(particle, particleData, frequency);
    }

    public BiomeParticles withParticle(Particle particle) {
        return new BiomeParticles(particle,
                particle.getDataType().isInstance(particleData)
                        ? particleData : null, frequency);
    }

    public BiomeParticles withParticle(Particle particle,Object particleData) {
        return new BiomeParticles(particle, particleData, frequency);
    }

    public BiomeParticles withParticleData(Object particleData) {
        return new BiomeParticles(particle, particleData, frequency);
    }

    public void spawnAt(Location location) {
        this.spawnAt(location, 1);
    }

    public void spawnAt(Location location,int count) {
        World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(this.particle, location, count, this.particleData);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BiomeParticles that = (BiomeParticles) o;
        return Float.compare(that.frequency, frequency) == 0 &&
                particle == that.particle &&
                Objects.equals(particleData, that.particleData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(particle, particleData, frequency);
    }
}

package net.kibblelands.server.biome;

import org.bukkit.Sound;
import org.bukkit.block.Biome;

public interface BiomeConfig {
    Biome getTargetBiome();

    BiomeParticles getBiomeParticles();

    void setBiomeParticles(BiomeParticles particle);

    Sound getAmbientSound();

    void setAmbientSound(Sound sound);

    BiomeMusic getBiomeMusic();

    void setBiomeMusic(BiomeMusic music);

    int getFogColor();

    void setFogColor(int fogColor);

    int getWaterColor();

    void setWaterColor(int fogColor);

    int getWaterFogColor();

    void setWaterFogColor(int fogColor);

    int getSkyColor();

    void setSkyColor(int fogColor);

    int getFoliageColor();

    void setFoliageColor(int fogColor);

    int getGrassColor();

    void setGrassColor(int fogColor);
}

package net.kibblelands.server.dimension;

public interface DimensionConfig {
    boolean isWaterEvaporate();

    void setWaterEvaporate(boolean waterEvaporate);

    boolean isPiglinSafe();

    void setPiglinSafe(boolean piglinSafe);

    boolean isBedWorking();

    void setBedWorking(boolean bedWorking);

    boolean isRespawnAnchorWorking();

    void setRespawnAnchorWorking(boolean respawnAnchorWorking);

    boolean isAllowRaids();

    void setAllowRaids(boolean allowRaids);

    float getAmbientLight();

    void setAmbientLight(float ambientLight);
}

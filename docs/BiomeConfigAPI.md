# BiomeConfigAPI (1.16.2+)

This api allow to change a Biome properties such as color or particles

Note: BiomeConfig is sent to the player only when they join the server,
    changing the configuration will only update when a new player joins

---------------------------------------

Example of API use:

```Java
Biome.PLAINS.getConfig().setBiomeParticles(new BiomeParticles(Particle.ENCHANTMENT_TABLE, 1F));
```

Will make the plain biome spawn enchantment particles

Note: These particles a spawned client side, so they do not use any server performances


This api allow to change a Biome properties such as color or particles

Note: DimensionConfig is sent to the client only when they join the server,
changing the configuration will only update the client behaviour when on join

Note: Multiple world of the same type share the same DimensionConfig,
meaning if you change a properties in the DimensionConfig it will apply 
to all world of its dimension type

---------------------------------------

Example of API use:

```Java
Bukkit.getWorlds().get(0).getDimensionConfig().setBedWorking(false);
```

Will make beds explode when right clicked instead of sleeping in them in the overworld.

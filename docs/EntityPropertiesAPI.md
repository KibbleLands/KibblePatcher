# EntityPropertiesAPI

This api allow linking to an `Entity` without the use of `Map` by
directly storing the data into the entity

**Note:** The dev Api use a `Map` but the patcher replace the implementation when patching
the server to store directly the data into the corresponding `Entity`

This api is usually **faster** than using `Map<Entity,Data>` as it directly access the data
into the entity and **safer** to use at it doesn't require to clear the data if the
entity despawn or get removed 

The `EntityProperty` interface has 2 important methods:
- `T get(Entity entity)` to get the entity data
- `T set(Entity entity)` to set the entity data

As `EntityProperty` is an interface you will need to choose an implementation

The actual three builtins implementation are:

- `TempEntityProperty`  
  - store a value to an entity, only cleared when the entity is unloaded  
  - useful to store `Player` <-> `???`(Object) that are constantly required  

- `TempSoftEntityProperty` (See [SoftReference](https://docs.oracle.com/javase/8/docs/api/java/lang/ref/SoftReference.html))  
  - store a value to an entity, only cleared when the Garbage Collector is about
  to run out of memory  
  - useful for caches and to store more large data as it can be cleared by the
   garbage collector if the server is about to run out of memory  
   
- `TempWeakEntityProperty` (See [WeakReference](https://docs.oracle.com/javase/8/docs/api/java/lang/ref/WeakReference.html))  
  - store a value to an entity, cleared by the Garbage Collector when the element is
  no longer referenced
  - useful to create `Entity` links as unloaded entity can still be directly cleared 
  by the garbage collector if the linked `Entity` is no longer referenced

---------------------------------------

Example of API use:

```Java
public class PlayerData {
    public int score;
    public long join;
}
```

```Java
public class PlayerListener implements Listener {
    public static final EntityProperty<PlayerData> playerDataProperty = new TempEntityProperty(PlayerData.class);

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        PlayerData playerData = new PlayerData();
        playerData.join = System.currentTimeMillis();
        playerDataProperty.set(event.getPlayer(), playerData);
    }

    public static PlayerData getPlayerData(Player player) {
        return playerDataProperty.get(player);
    }
}
```
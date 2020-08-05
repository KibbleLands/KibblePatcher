# KibblePatcher

You can find downloads in [Releases](https://github.com/KibbleLands/KibblePatcher/releases)  
Please read [LICENCE](https://github.com/Fox2Code/Repacker/blob/master/LICENSE) before using the software  
You can get support on our [Discord](https://discord.gg/bJ2uF8T)

## How to use

1. Get you server .jar and KibblePatcher.jar  
(The latests verions of Paper provide a patcher jar that is not the real server jar  
please launch it one time and get the file named `/cache/patched_X.jar` instead)
2. Run the command `java -jar KibblePatcher.jar server.jar server-patched.jar`
3. Use the `server-patched.jar` as a normal server jar

## Features

### Servers optimisations

Optimise server bytecode and use faster Math calls for bukkit APIs resulting in better performances with a lot of plugins

Server specific patches also help your server run better even without plugins

### Plugin Backward Compatibility

Add back commonly used APIs by old plugins to ensure maximum compatibility with all your plugins

### Server zip compression

Reduce ZIP size by compressing better files and removing unnecessary bytes

### Reduce outdated time delay

Change the wait time from `20` seconds to `5` seconds when the server detects that the build is outdated

## Server Compatibility

This patcher should support all Bukkit/Spigot/Paper but most of the optimisations in this patcher target the latest versions of minecraft

If you think the patcher has an issue please try to launch your server with and without the patched jar 

Snapshot version are not officially supported by the patcher but they should work

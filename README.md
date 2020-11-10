# KibblePatcher

You can find downloads in [Releases](https://github.com/KibbleLands/KibblePatcher/releases)  
Please read [LICENCE](https://github.com/Fox2Code/Repacker/blob/master/LICENSE) before using the software  
You can get support on our [Discord](https://discord.gg/qgk4Saq)

## How to use

1. Get you server .jar and KibblePatcher.jar  
(The latest versions of Paper provide a patcher jar that is not the real server jar  
please launch it one time and get the file named `/cache/patched_X.jar` instead)
2. Run the command `java -jar KibblePatcher.jar server.jar server-patched.jar`
3. Use the `server-patched.jar` as a normal server jar

## Features

### Servers optimisations

Optimise server bytecode and use faster Math calls for bukkit APIs resulting in better performances with a lot of plugins

Server specific patches also help your server run better even without plugins

Redirect some java API to the optimised ones  
Redirect also on loaded plugins if the server support `plugin rewrite`

### Plugin Backward Compatibility

Add back commonly used APIs by old plugins to ensure maximum compatibility with all your plugins

You can take a look at [fixed plugins list](#fixed-plugins-list)

### Server zip compression

Reduce ZIP size by better compressing files, removing unnecessary files, and trimming json

### Reduce outdated time delay

Change the wait time from `20` seconds to `5` seconds when the server detects that the build is outdated

### Unique Server APIs to gain performances

Help developers to implement features in more efficient and simple
ways that can't be achieved without server modification

See [Developers APIs Docs](docs/README.md) if you are interested

## Patcher Compatibility

This patcher should support all Bukkit/Spigot/Paper but most of the optimisations in this patcher target the latest versions of minecraft

The `plugin rewrite` functionality require at least **MC 1.13+**

If you think the patcher has an issue please try to launch your server with and without the patched jar 

The patcher do not officially support snapshots they should work

Hybrids server (Forge + Bukkit) are not supported and won't work with the patcher

## Backported Plugins list

This is an incomplete list of plugins not working on the
latest version of spigot that KibblePatcher fix completely or partially

You can also submit your test result by opening an issue

[Essential](https://dev.bukkit.org/projects/essentials): 
Launch, but some commands does not work  


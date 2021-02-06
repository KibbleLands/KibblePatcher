# KibblePatcher

You can find downloads in [Releases](https://github.com/KibbleLands/KibblePatcher/releases)  
Please read the [LICENCE](https://github.com/Fox2Code/Repacker/blob/master/LICENSE) before using the software  
You can get support on our [Discord](https://discord.gg/qgk4Saq)

## The main goals

### Stability, compatibility, and reliability

We aim at providing a stable and safe code that work on any server jar.  
We also work at making all loaded plugins behave like the server wasn't 
patched unless requiring or using KibblePatcher specific APIs!

### Plugin Retro Compatibility and conservation

Make old abandoned plugins work on latest for plugin preservation without additional modifications.   
(Sometimes can help recent plugins if they are not yet compatible with the latest version of Minecraft)

This can be useful if you want to old abandoned plugins work again on you the latest 
server version, allowing you to experience a near identical behaviour as you would if 
you were to go back in time.

### Servers optimisations

The main gain of performance from KibblePatcher is the redirect math
calls of `sin`, `cos`, and `tan` of plugins to the Minecraft 
implementation to improve plugins performance.  
[BetterFPS](https://www.curseforge.com/minecraft/mc-mods/betterfps) 
is a great benchmark tool for different `sin` and `cos` algorithm!  
Generally only 1.13+ support this feature.  
(In theory, by back-porting a part of 1.13+ classloading system, 
it should be possible to backport the plugin rewrite feature,
I will see what I can do later for pre 1.13 servers)

### Freedom and ease of use

The patcher was made with user freedom in mind and do not contain code
that would break its own behaviour on purpose.

The patcher also contain some minor patches that make the server less
annoying, for example by changing the outdated delay timer to 5 seconds 
allowing servers owner and developers to be more productive without 
having to wait their new version to finish downloading.

## How to use

If you get a "`Server is not a valid spigot server!`" make sure it's not a hybrid server 
*(Bukkit + Forge)*

1. Get you server.jar and KibblePatcher.jar *(You can use [Yatopia](https://yatopiamc.org/download.html) as your server jar)*
2. Run the command `java -jar KibblePatcher.jar server.jar server-patched.jar`
3. Use the `server-patched.jar` as a normal server jar
4. Enjoy! :3

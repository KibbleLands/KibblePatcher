# KibblePatcher

If you are a KibblePatcher user, and find an issue, please create an issue on GitHub 
Before reporting an issue check that the issue is not occurring without KibblePatcher!
Also check that you are using the latest version of KibblePatcher.

You can find downloads in [Releases](https://github.com/KibbleLands/KibblePatcher/releases)  
Please read the [LICENCE](https://github.com/Fox2Code/Repacker/blob/master/LICENSE) before using the software  

## The main goals

Be fun and easy to do and use :3

This is mainly for who use it, I will probably remove all optimisation features in KP 1.8.x
(I no longer use this software, so I don't care if it reduces the product quality)

I originally made this project for a server project that is now dead and improved legacy plugin compatibility

Optimisation was never the main focus of the project and was just a nice bonus I liked to add to the project,
for the server I was making having exact sin/cos return value on Math wasn't needed for the plugins we used,
this helped to reduce plugin latency and improved TPS as it reduced the time spent on sin/cos calls
(Which are very common and important for graphical stuff, but don't need to be as precise as some 
resource intensive AntiCheat may need)

I will slowly remove all features that I do not use and not requested via issues except for the LegacyPlugin support,
as I want to try to keep history alive as much as I can, even if I can't do much due to my health problems.

Due to the community toxicity I received during the making of this project and lack of constructive criticism, 
if I ever do a new optimisation project on Minecraft, it will be closed source.

## How to use

If you get a "`Server is not a valid spigot server!`" make sure it's not a hybrid server 
*(Bukkit + Forge)*

1. Get you server.jar and KibblePatcher.jar *(You can use [Airplane](https://dl.airplane.gg/) as your server jar)*
2. Run the command `java -jar KibblePatcher.jar server.jar server-patched.jar`
3. Use the `server-patched.jar` as a normal server jar
4. Enjoy using very old plugins on your latest version server! :3

# Lucky Block (Fabric 1.21.1)

## Build the jar
Requires JDK 21 and Gradle 8.8+ (or open the folder in IntelliJ IDEA and run the `build` task).

    gradle build

Your jar: `build/libs/luckyblockmod-1.0.0.jar`
Drop it in `.minecraft/mods` with Fabric Loader 0.16+ and Fabric API for 1.21.1.

## Use
- Craft: gold ingots in corners, diamonds on edges, nether star in the center.
- Or: `/give @s luckyblockmod:lucky_block`
- Right-click: random ability (Omega Beam, Lightning Storm, Meteor Shower, Healing Nova, Sky Launch, Gold Rain, Gravity Warp, Ally Summon, Orbital Strike)
- Sneak + right-click: guaranteed YELLOW OMEGA BEAM

## No installs? Let GitHub build it
1. Create a new GitHub repo and upload everything in this folder (including the hidden .github folder).
2. Open the Actions tab and wait for "Build mod jar" to finish (about 5 minutes).
3. Click the run, scroll to Artifacts, and download luckyblockmod-jar. That zip contains your jar.

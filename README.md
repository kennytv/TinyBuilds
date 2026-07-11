# TinyBuilds

A Paper plugin and Fabric mod that spawns scaled-down (or if you want, scaled-up) block display copies of your Minecraft builds.


## Smarts

While VERY large builds will likely still make client FPS drop, both the number of displays spawned and actions done for
rotation are optimized about as much as they can, so this will generally work much more smoothly than similar plugins or
mods on both the server and client.

## Requirements

- Paper 1.21+, or Fabric on Minecraft 26.2
- WorldEdit

## Commands

Make a WorldEdit selection of the build, stand where the copy should appear, then:

| Command                                            | Description                                                                                                                                          |
|----------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/tinybuilds place <group> <scale> [maxMergeSize]` | Spawn a copy of your selection at the given scale (e.g. `0.1`). `maxMergeSize` merges equal blocks into fewer, larger displays (recommended as `2`). |
| `/tinybuilds remove <group>`                       | Remove a group and its display entities.                                                                                                             |
| `/tinybuilds rotation <group> <speed>`             | Rotate the group around its center (radians per second, `0` to stop).                                                                                |

Permission: `tinybuilds.command` (Paper) / gamemaster permission level (Fabric)

## Building

```
./gradlew build
```

Built jars can then be found in `paper/build/libs` and `fabric/build/libs`.

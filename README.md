# Magic Carpet

A Fabric mod for **Minecraft 26.3**: a craftable flying carpet with happy-ghast controls and elytra speed.

## Controls (while riding)

| Key | What it does |
|---|---|
| **W** | Throttle. Hold to build speed, from a crawl to 30 blocks/s over about 6 seconds |
| *(let go of W)* | Coast. You keep flying and slowly lose speed (about half every 8 seconds) |
| **S** | Brake. Stops you from top speed in about 1¼ seconds; keep holding to drift backwards |
| **Mouse** | Steer. The carpet turns toward where you look; hard turns cost speed, smooth curves don't |
| **Space** | Fly straight up |
| **Ctrl** (your Sprint key) | Fly straight down. Works even if Sprint is set to Toggle |
| **A / D** | Slide sideways (only when going slowly, handy for building) |
| **Shift** | Get off. On or near the ground it's instant; **in the air you must hold it for 1 second** |
| **C** | Call your carpet (needs Loyalty) |

Diving makes you faster (up to 38 blocks/s) and climbing slows you down. The bar at the bottom of the screen shows your speed.

## Using it

- **Place it:** right-click the ground or water with the carpet, then right-click the carpet to ride. Two players can ride, like a boat.
- **Emergency carpet:** right-click it while falling and it unrolls under you and catches you.
- **Pick it up:** sneak and right-click it, or break it like a boat. It keeps its color, durability and enchantments either way.
- **Parking:** hold Shift for a second mid-air and the carpet stays where you left it. You can stand on it to build and right-click it to climb back on.
- If you leave it any other way (dying, an ender pearl, logging out), it drifts slowly down to the ground.

## Durability

- **1250 durability**, about 20 minutes of flying (1 point per second). Hovering in place wears it out 3× slower.
- Crashing into something at high speed costs 5 points.
- It never breaks. At its last point it's **worn out**: it slowly sinks to the ground and can't climb until repaired.
- **Repair** on an anvil with **Phantom Membrane** (each restores 25%), or combine two carpets.

## Enchantments

- **Unbreaking**: enchanting table or book on an anvil.
- **Mending**: book on an anvil. Like tools, it mends while the carpet is held in your hand or offhand.
- **Loyalty**: book on an anvil. The carpet:
  - **Catches you** if you slip off a parked carpet or fall off. Loyalty I catches falls of up to 20 blocks, II up to 40, and III any height.
  - **Comes when called** (C key) from 32 / 64 / 96 blocks. It has to be in a loaded part of the world.

## Recipe

```
Phantom Membrane   Carpet        Phantom Membrane
Ghast Tear         Dried Ghast   Ghast Tear
Phantom Membrane   String        Phantom Membrane
```

The carpet you use sets the color (any of the 16). You can re-dye it later by crafting it with dye, like leather armor.

## Building the .jar (GitHub, free)

1. Create a new repository on GitHub (e.g. `magic-carpet`).
2. **Upload files** → drag in **everything inside this folder**: `.github`, `gradle`, `src`, `build.gradle`, `gradle.properties`, `settings.gradle`, `gradlew`, `gradlew.bat`, `LICENSE`, `README.md`. Commit.
3. Check that the repository shows `.github`, `src/main/resources` and `src/client`. If one is missing, upload that folder again. The build stops with a message naming any missing file.
4. **Actions** tab → wait for the green check (3–5 min) → open the run → download **magic-carpet-jar** under Artifacts.
5. **Unzip** the download. Put the **`.jar` inside it** (`magic-carpet-1.0.0.jar`) straight into your instance's `mods` folder, not in a subfolder.

Fabric API is required. Anyone joining your world (including through e4mc) needs the mod too.

On a dedicated server, set `allow-flight=true` in `server.properties` so players standing on a parked carpet don't get kicked for "flying".

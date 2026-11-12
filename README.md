# Easy Survival Pack

Server-authoritative Fabric mod porting survival mechanics from the Easy Survival Pack datapack.

![Mod version](https://img.shields.io/badge/version-0.2.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)
![Fabric](https://img.shields.io/badge/loader-Fabric-orange)
![Project status](https://img.shields.io/badge/status-in%20development-yellow)

## Project status

| | |
|---|---|
| Mod version | `0.2.0` |
| Minecraft | `1.21.1` |
| Fabric Loader | `0.16.14` |
| Fabric API used for builds | `0.116.18+1.21.1` |
| Java | `21` |
| Release status | In development; no version tags have been published |

This is an ongoing port, not a feature-complete replacement for the datapack. The version is set in `gradle.properties`. When a release is published, its version should match that value.

## Features

### Emergency Heal

Automatically restores a configured percentage of max health when triggered. Game Masters can configure the heal amount and cooldown per player:

```text
/emergencyhealhp <targets> <percent>
/emergencyhealcd <targets> <seconds>
```

- `percent` is 0-100; default is 25. Setting it to `0` disables Emergency Heal and clears its cooldown.
- `seconds` is the cooldown after a trigger; default is 30. Setting it to `0` disables the cooldown.
- Settings persist across relogs and server restarts. Reducing the configured cooldown also caps a longer active cooldown.
- The action bar clears when the cooldown ends or after respawn.

### Out-of-combat recovery

Health recovery waits for the combat delay (8 seconds by default) and environmental damage delay (3 seconds by default). A combat timer takes priority over the environmental timer. After both delays end, health regenerates at the configured percentage of max health per second, rounded up to a whole health point. Vanilla natural regeneration counts toward that total; the mod adds only the remaining amount. Food and saturation are kept full after the combat timer ends.

Game Masters can configure each player's delays and recovery rate:

```text
/recoverycombat <targets> <seconds>
/recoveryenvironment <targets> <seconds>
/recoveryrate <targets> <percent>
```

- Delays accept values from 0 seconds upward; `0` disables that delay.
- Recovery rate accepts 0.5-10% max HP per second.
- Settings persist across relogs and server restarts. Reducing a configured delay caps a longer active timer.

### Max health and respawn protection

Game Masters can set a player's maximum health and respawn protection:

```text
/sethp <targets> <amount> heart
/sethp <targets> <amount> hp
/spawnprotection <targets> <seconds>
```

- `heart` converts hearts to health points (`1 heart = 2 HP`); `hp` uses health points directly.
- Max health supports 1-1024 HP (1-512 hearts). New players default to 20 hearts (40 HP).
- Max health is stored per player, restored on rejoin and reapplied after death. Respawning restores health to the configured maximum.
- Respawn protection lasts 5 seconds by default and cancels incoming damage without applying a Resistance effect. Attacking another entity ends the attacker's protection.
- `/spawnprotection` sets the duration for the player's next respawn; `0` disables it. The setting persists and also caps longer active protection.

### Low HP Defense

Resistance is applied automatically based on current health as a fraction of max health:

| Health threshold | Effect |
|---|---|
| At or below 50% | Resistance I (20% damage reduction) |
| At or below 25% | Resistance II (40% damage reduction) |
| At or below 12.5% | Resistance III (60% damage reduction) |

Mod-managed Resistance has an infinite displayed duration and changes with the health thresholds. An external Resistance effect of equal or higher level takes priority and keeps its normal duration. A weaker external effect hidden by Low HP Defense is restored when no longer overridden. Respawn protection takes priority. This feature has no tuning command.

### Infinite Totem

A Totem of Undying can activate from either hand, the player's inventory, or a carried shulker box. On activation, the selected Totem becomes a uniquely marked, glinting Nether Star placeholder. When its cooldown ends, it becomes a new vanilla Totem. Each placeholder has its own cooldown and owner; placeholder items cannot be used in crafting recipes.

The base cooldown is 60 seconds:

| Location and owner state | Cooldown rate |
|---|---:|
| Player inventory or carried shulker, owner online | 1x |
| Player inventory or carried shulker, owner offline | 5x slower |
| Loaded container, owner online | 2x slower |
| Loaded container, owner offline | 10x slower (5x offline × 2x container) |

Game Masters can configure the per-player base cooldown and cooldown multipliers:

```text
/totemcooldown <targets> <seconds>
/totemfactors <targets> <inventory_online> <inventory_offline> <container_online> <container_offline>
```

- A cooldown of `0` makes used Totems immediately reusable.
- Each factor is an integer from 1 to 10 and means the cooldown runs that many times slower. Defaults are `1 5 2 10` in argument order.
- Changing the base cooldown caps longer active cooldowns. Factors are saved into each newly activated Totem, so changes affect future activations only.
- A held placeholder displays its remaining time in the action bar and tooltip. Vanilla clients can display both without a client mod.
- Inventory and carried-shulker cooldowns are updated on join, leave, server stop, and while online. Loaded block containers are reconciled periodically and when accessed. Items in unloaded chunks update when those chunks load again.
- Totems in Ender Chests do not activate and their cooldown is paused until they leave the Ender Chest. Dropped items continue recharging while loaded, but are not searched for a Totem during death protection.

### Infinite Ender Pearl

Drop one Ender Pearl together with:

```text
5 Amethyst Shards
5 Redstone Dust
5 Glowstone Dust
```

All ingredients must be dropped by the same player within 5 seconds and within a 2-block radius of the Ender Pearl. The server consumes exactly one Ender Pearl and five of each ingredient; extras in those stacks remain. It drops a glinting, marked Infinite Ender Pearl at the crafting spot. Throwing it launches a normal Ender Pearl without consuming the item and retains vanilla Ender Pearl cooldown behavior.

### Infinite Firework

Drop one Firework Rocket together with five Amethyst Shards, five Redstone Dust, and five Glowstone Dust. All ingredients must be dropped by the same player within 5 seconds and within a 2-block radius of the rocket. The server consumes exactly one rocket and five of each ingredient; extras in those stacks remain.

The result preserves the original rocket's flight duration and explosion payload. Use it normally or load and fire it with a crossbow; the rocket is not consumed, and its normal explosion behavior is retained.

### Thunder Sky

Drop one Trident and one Nether Star together. Both items must be dropped by the same player within 5 seconds and within a 2-block radius of the Trident. The server consumes exactly one of each and adds the Thunder Sky behavior to the Trident without changing its name or other components.

Every third throw calls five lightning strikes; other throws call three, with two or three power-4 non-block-damaging explosions. Channeling makes each strike seek a living target within 4 blocks of the impact; later strikes continue tracking the first target outside that area. Thunder Sky lightning does not ignite blocks, still ignites struck living entities, and does not damage or launch dropped items. The thrower is immune to direct damage from Thunder Sky's lightning and explosions, but can still catch fire from lightning. Non-player-thrown drops in each bolt's fire area are collected by the thrower for 5 seconds; items thrown by a player are left alone. Raw beef, pork, chicken, mutton, rabbit, cod, and salmon are cooked during recovery. Items that fit enter their inventory, and any remainder drops at their feet.

### Infinite Trident

Drop one Trident together with five Amethyst Shards, five Redstone Dust, and five Glowstone Dust. All ingredients must be dropped by the same player within 5 seconds and within a 2-block radius of the Trident. The server consumes exactly one Trident and five of each ingredient; extras remain. The result retains the Trident's name, enchantments, and components and gains the Infinite behavior. Its throws do not consume the Trident, and its projectile cannot be picked up and disappears after 5 seconds or sooner if Loyalty returns it. Thunder Sky and Infinite are separate behaviors and can both be applied to the same Trident.

### Durability repair upgrades

Drop one damageable item and one or more Dragon's Breath together. All dropped items must be from the same player, within 5 seconds and a 2-block radius of the damageable item. Each Dragon's Breath adds one repair level, up to level 10 for armor, level 3 for Elytra, and level 5 for other items. Only the required amount of Dragon's Breath is consumed; excess remains on the ground. The upgraded item retains its vanilla enchantments and components.

- Each level represents one durability point per second at 100% rate. Armor repairs at 10% of its level per second during combat; weapons repair at 20% per second during combat. Pickaxes, axes, shovels, and hoes repair at 20% of their level while recently used, then return to 100% when not in use. Other items repair at 100% of their level.
- Fractional repair accumulates on the item until it reaches one durability point; it never rounds a fractional amount into extra durability.
- Elytra repair at 100% of their level whenever carried by the player, including equipped, inventory, or a carried shulker. Elytra in an Ender Chest do not repair.
- Other upgraded items repair in equipment, inventory, offhand, Ender Chest, and shulkers carried in those locations. Combat changes repair rates but does not stop repair.
- Vanilla enchantments such as Unbreaking and Mending are retained and work normally but do not change the custom repair rate. The level and repair rate appear in the tooltip.

### Frost Walker

Frost Walker boots freeze nearby water source blocks (not flowing water) when the wearer moves, including while jumping up to 2 blocks above the water surface. Frosted ice does not age or melt from neighbor updates while it remains in the active Frost Walker area, whether the wearer is standing or moving; it resumes melting normally after leaving that area. The freeze radius follows the enchantment level. Source lava directly below nearby positions in that radius turns into magma blocks for 5 seconds per Frost Walker level, then returns to source lava if the block is still magma.

### XP loss reduction

Ordinary gameplay XP deductions are reduced by 80% by default, including XP spent on enchantments and anvils. XP is compared as total XP points derived from the player's level and progress within that level. The removed amount is restored with ceiling rounding.

Death-related XP loss is excluded, as are direct `/experience set` admin changes. Game Masters can configure the reduction per player:

```text
/xploss <targets> <percent>
```

The value is 0-100; `0` disables the reduction and `100` refunds the full ordinary gameplay XP deduction. The default is 80%.

## Status display

The server combines active system statuses into one action-bar line, for example:

```text
Combat: 6s | DEF 40% | EH: 22s
```

The line can also show the environmental recovery delay, active recovery, or respawn protection (`SP: 4s (-100% damage)`). It clears when no system has an active status.

## Command reference

All commands below require Minecraft Game Master permission (cheats/OP). Settings are per-player and persist across relogs and server restarts.

| System | Commands | Defaults |
|---|---|---|
| Emergency Heal | `/emergencyhealhp <targets> <percent>`, `/emergencyhealcd <targets> <seconds>` | 25% heal, 30s cooldown |
| Out-of-combat recovery | `/recoverycombat <targets> <seconds>`, `/recoveryenvironment <targets> <seconds>`, `/recoveryrate <targets> <percent>` | 8s combat, 3s environment, 5% max HP/s |
| Max health and respawn protection | `/sethp <targets> <amount> heart\|hp`, `/spawnprotection <targets> <seconds>` | 20 hearts (40 HP), 5s protection |
| Low HP Defense | Automatic; no command | Resistance I/II/III at 50%/25%/12.5% max HP |
| Infinite Totem | `/totemcooldown <targets> <seconds>`, `/totemfactors <targets> <inventory_online> <inventory_offline> <container_online> <container_offline>` | 60s; factors 1x/5x/2x/10x |
| XP loss reduction | `/xploss <targets> <percent>` | 80%; death loss and `/experience set` excluded |

Systems not yet ported from the datapack do not have Fabric commands yet.

## Installation

The mod runs its gameplay logic on the server:

- **Dedicated server:** install Fabric Loader and this mod JAR on the server.
- **Single-player opened to LAN:** the host must run Fabric with this mod.
- **Joining players:** friends can join with vanilla clients; they do not need to install the mod.
- **Mod installed on both server and client:** supported when both use compatible versions. Gameplay changes are handled by the server; the client copy does not run a second set of gameplay ticks. Keep the client and server mod versions aligned to avoid confusing item prediction or display differences.

## Build from source

Requirements:

- JDK 21 for the default Minecraft 1.21.1 build
- Internet access for the first Gradle build

The Gradle wrapper downloads the configured Gradle version automatically. From this directory, run:

```powershell
.\gradlew.bat build
```

The built JAR is in `build\libs`. The `build` task also stages a versioned copy in `dist`:

```text
dist\EasySurvivalPack-0.2.0+mc1.21.1.jar
```

Run `.\gradlew.bat dist` to stage the JAR without running the full verification lifecycle. On Windows, double-click `build-mod.bat` to build and keep the result window open. Pass `--no-pause` when running it from a script or terminal that should return immediately.

Each build removes older `EasySurvivalPack-*.jar` files from `dist` before staging the current JAR. Run `release.cmd` to build and publish that JAR as a GitHub Release using the version in its filename. The script requires `gh` to be installed and authenticated, and the Git worktree to be clean. Use `release.cmd --dry-run` to preview the release tag and artifact without building or publishing.

The 26.3 source set is retained and can be selected with:

```powershell
.\gradlew.bat build '-Pminecraft_version=26.3' '-Ploader_version=0.20.1' '-Pfabric_api_version=0.166.0+26.3'
```

That build requires JDK 25.

## Project layout

- `src/main/version/<minecraft-version>/java`: server-authoritative gameplay and common code for that Minecraft version
- `src/main/version/<minecraft-version>/resources`: Fabric metadata, translations, and mod resources for that Minecraft version
- `src/client`: optional client-only code, if added later
- `src/test`: tests, as portable gameplay logic is implemented

The original datapack is retained in the workspace under `datapack-to-port` as a behavior reference; it is not included in the mod JAR.

Author: Akira Hashimoto

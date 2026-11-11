# Easy Survival Pack

Server-authoritative Fabric mod porting survival mechanics from the Easy Survival Pack datapack.

![Mod version](https://img.shields.io/badge/version-0.1.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-26.3-brightgreen)
![Fabric](https://img.shields.io/badge/loader-Fabric-orange)
![Project status](https://img.shields.io/badge/status-in%20development-yellow)

## Project status

| | |
|---|---|
| Mod version | `0.1.0` |
| Minecraft | `26.3` |
| Fabric Loader | `0.20.1` |
| Fabric API used for builds | `0.166.0+26.3` |
| Java | `25` |
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
| Loaded container, owner offline | 10x slower |

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
- Ender Chests and dropped items continue recharging while loaded, but are not searched for a Totem during death protection.

### Infinite Ender Pearl

Drop the following items together:

```text
4 diamonds
4 gold ingots
1 Ender Pearl
```

All nine items must be dropped by the same player within 5 seconds and within a 2-block radius of the Ender Pearl. The server consumes exactly four diamonds, four gold ingots, and one Ender Pearl; extras in those stacks remain. It drops a glinting, marked Infinite Ender Pearl at the crafting spot. Throwing it launches a normal Ender Pearl without consuming the item and retains vanilla Ender Pearl cooldown behavior.

### Infinite Firework

Drop one Firework Rocket and one End Crystal together. The same player must drop both within 5 seconds and within a 2-block radius of the rocket. The server consumes exactly one rocket and one crystal; extras remain.

The result preserves the original rocket's flight duration and explosion payload. Use it normally or load and fire it with a crossbow; the rocket is not consumed, and its normal explosion behavior is retained.

### Durability repair upgrades

Drop one damageable item and one or more Dragon's Breath together. All dropped items must be from the same player, within 5 seconds and a 2-block radius of the damageable item. Each Dragon's Breath adds one repair level, up to level 5. Only the required amount of Dragon's Breath is consumed; excess remains on the ground. The upgraded item retains its vanilla enchantments and components.

- Each level restores one durability point per second, up to 5 per second at level 5.
- Repair waits until the player's 8-second combat recovery delay ends.
- Pickaxes, axes, shovels, and hoes also wait 5 seconds after their last durability use.
- Elytra repair at their level rate while flying and at `ceil(level * 1.5)` per second while not flying (8 per second at level 5).
- Repair applies to upgraded items in equipment, inventory, offhand, Ender Chest, and shulkers carried in those locations.
- Vanilla enchantments such as Unbreaking and Mending are retained and work normally but do not change the custom repair rate. The level and repair rate appear in the tooltip.

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

## Build from source

Requirements:

- JDK 25
- Internet access for the first Gradle build

The Gradle wrapper downloads the configured Gradle version automatically. From this directory, run:

```powershell
.\gradlew.bat build
```

The built JAR is in `build\libs`. The `build` task also stages a versioned copy in `dist`:

```text
dist\EasySurvivalPack-0.1.0+mc26.3.jar
```

Run `.\gradlew.bat dist` to stage the JAR without running the full verification lifecycle. On Windows, double-click `build-mod.bat` to build and keep the result window open. Pass `--no-pause` when running it from a script or terminal that should return immediately.

## Project layout

- `src/main/java`: server-authoritative gameplay and common code
- `src/main/resources`: Fabric metadata, translations, and mod resources
- `src/client`: optional client-only code, if added later
- `src/test`: tests, as portable gameplay logic is implemented

The original datapack is retained in the workspace under `datapack-to-port` as a behavior reference; it is not included in the mod JAR.

Author: Akira Hashimoto

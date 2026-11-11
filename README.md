# Easy Survival Pack Fabric Mod

Fabric mod project targeting Minecraft 26.3.

Gameplay logic runs authoritatively on the server. For a dedicated server, install the mod on the server only. For a single-player world opened to LAN, the host must run Fabric with this mod; friends can join with vanilla clients and do not need to install it. Author: Akira Hashimoto.

## Requirements

- JDK 25
- Internet access for the first Gradle build

The Gradle wrapper downloads the configured Gradle version automatically. A separate Gradle installation is not required.

## Build

From this directory, run:

```powershell
.\gradlew.bat build
```

Gradle's jar is created in `build\libs`, and `build` also stages a versioned copy in this project’s `dist` directory:

```text
mod\dist\EasySurvivalPack-0.1.0+mc26.3.jar
```

You can run `.\gradlew.bat dist` to stage the jar without running the full verification lifecycle.

On Windows, double-click `build-mod.bat` to build and keep the result window open. Pass `--no-pause` when running it from a script or terminal that should return immediately.

## Emergency Heal commands

The following commands require the Minecraft Game Master permission (cheats/OP):

```text
/emergencyhealhp <targets> <percent>
/emergencyhealcd <targets> <seconds>
```

- `targets` accepts a player name or selector such as `@a`.
- `percent` is the max-health percentage restored when Emergency Heal triggers (0–100; default 25). Set it to `0` to disable Emergency Heal; its cooldown is cleared and no longer ticks.
- `seconds` is the cooldown applied after a trigger (minimum 0; default 30). Set it to `0` to disable cooldown.
- Settings are stored per player and persist across relogs and server restarts. Changing the cooldown caps the active cooldown at the new duration: it shortens a longer remaining cooldown, but never extends one that is already shorter.
- The action bar clears immediately when cooldown ends or after respawn; no extra cooldown time is added.

## Out-of-combat recovery

After damage dealt to or received from an entity, health recovery waits for the combat delay (8 seconds by default). Environmental damage uses its own delay (3 seconds by default). A combat timer takes priority and clears/overrides the environmental timer; otherwise the environmental timer applies. Food and saturation are kept full whenever the combat timer has ended, including during the environmental recovery delay. Health recovery waits for both timers to end, then heals at the configured percentage of max health per second, rounded up to a whole health point. Vanilla natural regeneration (1 health per second in Minecraft 26.3 when enabled) counts toward that total; the mod only adds the remaining amount.

The following commands require the Minecraft Game Master permission (cheats/OP) and configure selected players individually:

```text
/recoverycombat <targets> <seconds>
/recoveryenvironment <targets> <seconds>
/recoveryrate <targets> <percent>
```

- Delays accept values from 0 seconds upward; `0` disables that delay.
- Recovery rate accepts 0.5–10% max HP per second, including decimal values.
- Settings persist across relogs and server restarts.
- Changing either delay caps its active timer at the new delay: it shortens a longer timer, but does not extend one that is already shorter.

## Spawn health and protection

The `/sethp` command requires the Minecraft Game Master permission (cheats/OP):

```text
/sethp <targets> <amount> heart
/sethp <targets> <amount> hp
/spawnprotection <targets> <seconds>
```

- `heart` converts hearts to health points (`1 heart = 2 HP`); `hp` uses health points directly.
- The Minecraft 26.3 max-health attribute supports 1–1024 HP (1–512 hearts).
- New players default to 20 hearts (40 HP). Max health is stored per player, restored when they rejoin, and reapplied after death. Respawning restores the player's health to their configured maximum.
- Respawn protection lasts 5 seconds by default and cancels incoming damage without applying a Resistance effect. Dealing damage to another entity immediately ends the attacker's protection.
- `/spawnprotection` sets each selected player's respawn protection duration in seconds; `0` disables it. The configured duration applies on their next respawn and persists across relogs/restarts.
- Changing the configured duration also caps protection that is already active: it shortens a longer remaining timer but does not extend a shorter one.

## Low HP Defense

Resistance is automatically applied according to current health as a fraction of max health:

- At or below 50%: Resistance I (20% damage reduction).
- At or below 25%: Resistance II (40% damage reduction).
- At or below 12.5%: Resistance III (60% damage reduction).

Mod-managed Resistance has an infinite displayed duration and is removed or changed by the mod as health thresholds change. An externally applied Resistance of equal or higher level takes priority and keeps its normal duration. A weaker external Resistance hidden by Low HP Defense has its remaining duration tracked and is restored when no longer overridden. Clearing effects also clears any hidden external Resistance. Respawn protection takes priority. Low HP Defense is automatic and has no tuning command.

## Infinite Totem

A Totem of Undying can activate from either hand, the player's inventory, or a carried shulker box. On activation, the selected Totem is replaced by a uniquely marked, glinting Nether Star placeholder; when its cooldown finishes, it becomes a new vanilla Totem. Each placeholder has its own cooldown and owner, so multiple Totems recharge independently. Placeholder items cannot be used in crafting recipes.

The base cooldown is 60 seconds. It runs at these rates:

| Location and owner state | Cooldown rate |
|---|---:|
| Player inventory or carried shulker, owner online | 1x |
| Player inventory or carried shulker, owner offline | 5x slower |
| Loaded container, owner online | 2x slower |
| Loaded container, owner offline | 10x slower |

Game Masters can set the per-player base cooldown with `/totemcooldown <targets> <seconds>`. `0` makes used Totems immediately reusable. Changing this setting caps existing Totem cooldowns owned by the selected player at the new duration: longer cooldowns are shortened, while shorter ones stay unchanged. While a cooling placeholder is held in either hand, its remaining time appears in the combined action bar; its tooltip also shows the cooldown when hovered in an inventory. Vanilla clients can display both without a client mod. A placeholder in an Ender Chest or dropped into a loaded world continues recharging, but neither location is searched for a Totem during death protection.

Game Masters can set the per-player cooldown multipliers with `/totemfactors <targets> <inventory_online> <inventory_offline> <container_online> <container_offline>`. Each factor is an integer from 1 to 10 and means the cooldown runs that many times slower. Defaults are `1 5 2 10` in that argument order. The selected player's factors are saved into each newly activated Totem placeholder, so changing them affects future activations only. Cooldowns in player inventories and carried shulkers are updated on join, leave, server stop, and while online. Loaded block containers are reconciled periodically and when accessed. Items in unloaded chunks are updated when those chunks load again, so a change between online/offline rates during unload is not accounted for until then and has not been gameplay-verified.

## Infinite Ender Pearl

Drop the following items together:

```text
4 diamonds
4 gold ingots
1 Ender Pearl
```

All nine items must be dropped by the same player within 5 seconds and within a 2-block radius of the Ender Pearl. The server consumes exactly four diamonds, four gold ingots, and one Ender Pearl; any extra items in those stacks remain. It drops a glinting, marked Infinite Ender Pearl at the crafting spot. Throwing that pearl launches a normal Ender Pearl without consuming the item; vanilla Ender Pearl cooldown behavior is retained.

This is processed by the game server, whether it is dedicated or integrated. In a LAN world the host must run Fabric with this mod, but connected friends can use vanilla clients without installing it.

## Infinite Firework

Drop one Firework Rocket and one End Crystal together. Both items must be dropped by the same player within 5 seconds and within a 2-block radius of the rocket. The server consumes exactly one rocket and one crystal; extra items in either stack remain.

The result copies the original rocket's Fireworks component, preserving its flight duration and explosion payload, then marks it as infinite. Use it normally or load and fire it with a crossbow; the rocket is not consumed. Crossbow loading, firing, and the rocket's normal explosion behavior are preserved. It remains a vanilla Firework Rocket item, so friends joining a LAN world do not need the mod.

## Durability repair upgrades

Drop one damageable item and one or more Dragon's Breath together. All dropped items must be from the same player, within 5 seconds and a 2-block radius of the damageable item. Each Dragon's Breath adds one repair level, up to level 5. The server consumes only the number of Dragon's Breath needed for the available levels; excess breath remains on the ground. It returns the original item with its vanilla enchantments and components preserved. An item starts at level 0, and items already at level 5 cannot be upgraded further.

Each repair level restores one durability point per second, up to 5 per second at level 5. All upgraded damageable items wait until the player's 8-second combat recovery delay ends before repairing. Pickaxes, axes, shovels, and hoes also wait 5 seconds after their last durability use. Elytra repair at their level rate while the player is flying; while not flying they repair at `ceil(level × 1.5)` per second (8 per second at level 5). Repair applies to upgraded items in equipment, inventory, offhand, Ender Chest, and shulkers carried in those locations. Vanilla enchantments such as Unbreaking and Mending are retained and work normally but do not change this repair rate. The level and repair rates appear in the item's tooltip.

## XP loss reduction

Ordinary gameplay XP deductions are reduced by 80% by default, including XP spent on enchantments and anvils. XP is compared as total XP points derived from the player's level and progress within that level, matching the datapack's level-to-points calculation. The removed amount is restored with ceiling rounding. Death-related XP loss is excluded, as are direct `/experience set` admin changes.

Game Masters can configure the reduction per selected player:

```text
/xploss <targets> <percent>
```

The value is 0–100; `0` disables the reduction and `100` refunds the full ordinary gameplay XP deduction. The default is 80%.

## Combined status display

The server combines active system statuses into one action-bar line, for example:

```text
Combat: 6s | DEF 40% | EH: 22s
```

The line can also show the environmental recovery delay, active recovery, or respawn protection (`SP: 4s (-100% damage)`). It clears when no system has an active status.

## Gameplay tuning command index

All currently implemented tunable settings have per-player Game Master commands; Low HP Defense is automatic:

| Mechanism | Commands | Default |
|---|---|---|
| Emergency Heal | `/emergencyhealhp <targets> <percent>`, `/emergencyhealcd <targets> <seconds>` | 25% heal, 30s cooldown; set heal to 0 to disable |
| Out-of-combat recovery | `/recoverycombat <targets> <seconds>`, `/recoveryenvironment <targets> <seconds>`, `/recoveryrate <targets> <percent>` | 8s combat, 3s environment, 5% max HP/s |
| Max health and respawn protection | `/sethp <targets> <amount> heart|hp`, `/spawnprotection <targets> <seconds>` | 20 hearts (40 HP), 5s protection |
| Low HP Defense | Automatic; no tuning command | Resistance I/II/III at 50%/25%/12.5% max HP |
| Infinite Totem | `/totemcooldown <targets> <seconds>`, `/totemfactors <targets> <inventory_online> <inventory_offline> <container_online> <container_offline>` | 60s per Totem; factors default to inventory online/offline 1x/5x and container online/offline 2x/10x |
| XP loss reduction | `/xploss <targets> <percent>` | Set per-player reduction from 0% to 100% (default 80%); death and admin `/experience set` excluded |

These settings are stored per player and persist across relogs and server restarts. Systems not yet ported do not have Fabric commands yet.

## Source layout

- `src/main/java`: server-authoritative gameplay and common code
- `src/main/resources`: Fabric metadata, translations, and mod resources
- `src/client`: optional client-only tooltip/HUD code if added later
- `src/test`: unit tests as portable gameplay logic is implemented

The original datapack is retained in the workspace under `datapack-to-port` as a behavior reference; it is not included in the mod jar.
#   e a s y s u r v i v e p a c k  
 
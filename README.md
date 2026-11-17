# Cobblemon Battle Ultra

A competitive battle companion for [Cobblemon](https://cobblemon.com) (Minecraft 1.21.1, Fabric, client side).
It reads the battle like a Showdown player would: it narrows down the opponent's build as the fight goes, shows real
damage and KO odds both ways, tells you who moves first at a glance, and points to your best move and best switch-in.

> **About this repository.** Battle Ultra is built on top of
> [Cobblemon Battle Extras](https://www.curseforge.com/minecraft/mc-mods/cobblemon-battle-extras) by **Raguto & Yemagh**
> (All Rights Reserved). This repository only contains the code and assets written for Battle Ultra. The original Battle
> Extras code, and the changes made to its own classes, are **not** published here, so this source does not build on its
> own: it is shared so people can read, learn from and discuss how the features work.

## What the code here does

| Area | Classes |
|---|---|
| Opponent build inference (Smogon / Pokémon Champions sets, deduced items: Scarf from speed, Band/Specs/Life Orb from damage, Assault Vest from bulk, Leftovers / Boots / Rocky Helmet ruled out…) | `BuildInference`, `SmogonSets`, `FormatPresets`, `ItemAbilityTracker`, `OpponentMemory` |
| Damage, KO counts with end-of-turn chip, situational move power, move advice | `DamageRefiner`, `ThreatCalc`, `MovePower`, `HiddenPowerHelper`, `Residual`, `MoveAdvisor`, `MoveInsights`, `SwitchAdvisor` |
| Speed tiers, stat stages, modifiers | `SpeedTiers`, `ModifierChips`, `BattleCounters`, `VolatileTracker` |
| Mega Evolution / form changes (Legends Z-A Megas included) | `MegaTracker`, `MegaDex`, `FormAbilityHelper` |
| Team strips: HP, status and PP of every Pokémon, benched ones included | `TeamStatusTracker`, `TeamPanelRenderer`, `TeamPreviewCapture`, `SpectatorStripRenderer` |
| UI: Ultra tiles, attached info panels, field indicators, 5 UI styles, smooth font | `UltraTileRenderer`, `AttachedPanelRenderer`, `PinnedInfoPanelRenderer`, `FieldHud`, `UiStyle`, `UltraFont`, `SwitchThreatRenderer` |
| Battle recap and HP lines in the saved battle logs | `BattleRecap`, `BattleHpLog` |
| Mixins | `name/modid/mixin/plus/*` |
| Data | `assets/cobblemon-battle-extras/data/*` (Smogon and Pokémon Champions sets, contact moves) |

The Java sources were recovered from the compiled mod, so some local variable names are generic.

## Pokémon Champions sets

Champions builds use Stat Points (0–32 per stat, 66 in total) instead of EVs. One Stat Point is worth one stat point at
level 50, i.e. 8 EVs (the first point only needs 4), so they are converted as `EV = 8 × SP − 4` (0 stays 0, 32 → 252).

## Credits

- **Raguto & Yemagh** — Cobblemon Battle Extras, the foundation of this mod.
- **Smogon** — competitive sets (Scarlet/Violet sets via the [pkmn](https://github.com/pkmn/smogon) project, Pokémon Champions sets from the Smogon Strategy Dex).
- **Pokémon Showdown** — base stats, types and abilities of the new Mega forms.
- **Poppins** font by Indian Type Foundry — SIL Open Font License 1.1 (see `font/OFL.txt`).
- Cobblemon by the Cobblemon team. Pokémon © Nintendo / Game Freak / The Pokémon Company. Not affiliated with any of them.

## License

The code in this repository written for Battle Ultra is released under the MIT License (see `LICENSE`). It does not
grant any right over Cobblemon Battle Extras, Cobblemon, the Smogon data or the Poppins font, which keep their own terms.

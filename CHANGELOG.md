# Changelog

All notable changes to Bounty SMP are documented in this file.

---

## [0.2.0] — 2025-XX-XX

### Added
- **Charge Bow** weapon — custom bow with a 4-hit charge mechanic
  - Each arrow hit on a player increments a charge counter (0 → 4)
  - After 4 player hits the bow becomes fully charged
  - Next arrow fired deals Power 20 damage
  - Charge counter resets after the powered shot is fired
  - Actionbar shows charge progress (`■ ■ □ □  Charge: 2/4`)
  - Sound effect plays on each charge increment (rising pitch)
  - Totem particle burst + anvil sound when fully charged
  - Hitting mobs does not count toward the charge
- **Charge Bow crafting recipe** — default: 4× Bone + 1× Ender Eye + 1× Stick
- `/playergames chargebow` command to view/change the Charge Bow recipe
- `/playergames reload` command to hot-reload recipes from `config.yml`
- `config.yml` for persistent recipe storage (survives server restarts)
- `playergames.chargebow` permission (default: OP)
- `playergames.reload` permission (default: OP)

### Changed
- **Vampire Sword** mechanic completely reworked:
  - No longer works on mobs (removed mob health-stealing)
  - Now only activates when hitting other players
  - Tracks a single hit counter per attacker (not per-target)
  - Every 5th player hit triggers a drain effect:
    - Target takes +4 HP bonus damage (2 hearts)
    - Attacker heals for 4 HP (2 hearts)
  - Counter resets after each drain and starts counting again
  - Added actionbar blood counter (`■ ■ ■ □ □  Blood: 3/5`)
  - Added damage particles on victim and healing particles on attacker
  - Added sound effects for both attacker and victim

## [0.1.0] — Initial Release

### Added
- Bounty hunting system with target assignment and bounty levels (0–5)
- Bounty effects: Fire Resistance, Speed, Strength, Extra Hearts, Glowing
- **Charged Mace** — right-click to launch Wind Charge (5s cooldown)
- **Vampire Sword** — every 5 mob hits steals health
- **Stun Axe** — right-click to stun a player for 1 second (10s cooldown)
- **Thunder Trident** — left-click to strike lightning on target (5s cooldown)
- In-game recipe management commands for all weapons
- Server-wide announcements when custom weapons are crafted

# CP2 + CP3 CONTRACT — balance-overhaul-order-5: perks and keystones LIVE at runtime

Spec: `/home/user/Toon3DA/.claude/agents/ideas/balance-overhaul-order-5.txt` (B2-B4, EDGE CASES, DECISIONS
D3-D6, OVERRIDE CLAUSE). Live state + CP1 handover: `/home/user/Toon3DA/.claude/checkpoints/balance-overhaul-order-5.md`
(read NOTES CARRIED FORWARD — the "RUNTIME MUST HONOUR" list is binding, plus the Wildfire runtime rule
below). Project rules: `/home/user/Toon3DA/.claude/CLAUDE.md`.

CP1 built the DATA (`progression/` BuildTag, BuildStats, PerkModifier, PerkEffect, Perk, Keystone,
PlayerBuild; `util/` SECTION 24, BuildValuationModel, R-PERK/R-KEYSTONE/R-CARRY). This contract makes
them drive the game and the simulator. Two checkpoints, done IN ORDER, reported separately (the hub
commits between them — STOP after CP2 and report, then continue with CP3 when told).

## CP2 — PerkDeck + PlayerBuild drive level-ups; UpgradeCard retired; every PERK seam live

### 2.1 `progression/PerkDeck` (replaces UpgradeCardDeck)
- Seeded (`new PerkDeck(long seed)`, same seed salt the World/SimWorld used for UpgradeCardDeck).
- `int drawPerks(Perk[] out, PlayerBuild build, EnumSet<BuildTag> carriedWeaponTags)` — B3: 3 perks
  (`BalanceConfig.PERKS_OFFERED`), distinct, never a maxed perk; at least 1 SYNERGY (tag in
  carriedWeaponTags; if every synergy-tag perk is maxed, fall back to any unmaxed perk — EDGE CASES), at
  least 1 PIVOT (a tag the player has NO ranks in); the rest weighted by the existing draw bias
  (`LEVEL_UP_DRAW_BIAS_PER_PICK` per prior pick in the perk's tag — keep the constant, rename if you
  like, document). No allocation per draw (pre-allocated scratch, like UpgradeCardDeck).
- `boolean isSynergy(Perk, EnumSet<BuildTag>)` helper for the overlay (CP5 draws the marker).
- Carried weapon tags: from the loadout's weapons' `damageClass()` through `BuildTag.forDamageClass`,
  PLUS ability tags once CP4 adds them (leave a clear single method `World/SimWorld.carriedBuildTags()`
  that CP4 extends).

### 2.2 Level-up flow (World + SimWorld)
- `World`: replace `UpgradeCardDeck`/`UpgradeCard[] offeredCards` with `PerkDeck` + `Perk[]`; tapping a
  perk = `playerBuild.addPerkRank(perk)` → `playerProgress.advanceLevel()` → `applyVitalityGrowth()` →
  `applyBuildEffects()` (ONE method that re-derives every build-dependent value: attribute grants into
  PlayerStats — apply only the DELTA since last time —, max HP/armour multipliers, damage multipliers
  pushed to EnemyManager, and anything else cached) → PLAYING. `LevelUpOverlayRenderer` (currently in
  `progression/` — leave it there) takes `Perk[]` + count and draws name / tag / effect line / rank
  (minimal text change here; CP5 does the chips/synergy/pips/keystone frame visuals).
- `SimWorld`: same flow. `PlayerPolicy.chooseCard(...)` → `choosePerk(Perk[] offered, int count, SimView
  view)` (NAIVE: first; TACTICAL / HOARDER: a sensible default — prefer SYNERGY, then the tag with most
  ranks; CP6 adds build-bias variants). Keep the run deterministic.
- Shop "player ability" offer (`shop/DefaultShopOfferSource`, `ShopAbilityService`, `ShopAbilityGrantor`)
  currently sells a random UpgradeCard boon. Re-point it to sell ONE RANK of a random unmaxed Perk
  (priced on a SECTION 24 constant replacing `LEVEL_UP_BUDGET_PP` for this purpose — document; order 6
  re-prices). SCOPE OUT says no perk sales beyond this existing offer — do not add more.
- DELETE `UpgradeCard`, `UpgradeCardDeck`, `UpgradeCardPool`, the SECTION-3/level-up card constants
  (`LEVEL_UP_BUDGET_PP`, `LEVEL_UP_BUDGET_TOLERANCE`, `CARD_*` magnitudes) and their `GameBalance` shims
  — OVERRIDE CLAUSE: R-CARD / R-CARD-BREAKPOINT are replaced by R-PERK (already live). Remove
  `RuleKind.CARD_BUDGET`, `CARD_BREAKPOINT`, `cardBudgetResults()`, `cardBreakpointResults()`,
  `cardBreakpointGainAtDepth`, `cardBestBreakpoint`, the report's upgrade-card table; UPDATE the two
  audit test methods (`upgradeCardsPriceIntoTheBudgetBand`, `everyUpgradeCardCrossesABreakpoint`) —
  never delete a test outright: re-point each to the rule that replaced it (the perk FELT / breakpoint
  rows) and rename it to say so. If any other code read `LEVEL_UP_BUDGET_PP` (boss derivation, shop,
  ExpectedPlayer), switch it to the new constant below.
- `ExpectedPlayer` / `GameMath.expectedPlayer`: the card-power term becomes "expected perk power at the
  expected rank count" — introduce SECTION 24 `EXPECTED_PERK_POWER_PP_PER_PICK` (+ keystone PP if you
  model it) and use it where `LEVEL_UP_BUDGET_PP` was. Start it at the value that keeps R-LADDER (and
  every boss/route rule that reads the expected player) green — 12 reproduces today exactly — and say
  in the javadoc how it relates to the catalog's measured average gain (the PERKS table). R-LADDER must
  stay green.

### 2.3 Perk seams — ONE typed query on `PlayerBuild.stats()` at the owner of each seam
Find each seam; never a string lookup, never allocation in a hot path, never a switch on Perk/Keystone.
Push cached multipliers on `applyBuildEffects()` where the owner already caches (EnemyManager's
ranged/melee multipliers), query live where it does not.
- class damage (`damageMultiplierFor(DamageClass)`): `EnemyManager.applyDamageTo` has
  `activationDamageClass` — one multiplication there (melee too: BLADE/BLUNT). Butcher melee bonus and
  Berserker's low-HP melee bonus in the same place.
- Steady Aim (falloff), Lane Discipline (+range, +dmg at >= 4 tiles), Point Man (+dmg at <= 2 tiles,
  knockback +1 r2, stagger at 3 tiles r3 — `entity/SpreadImpact`), First Blood (first bullet on a
  full-HP enemy, BALLISTIC/RAIL): at the weapon's distance/damage seam (`Weapon.damageAtDistance` /
  `marchShot`) or in applyDamageTo where distance is known — your call, one place each.
- Breacher (SPREAD kill refunds 1 shell, chance) — on kill with a SPREAD activation.
- Accelerant (+max burn stacks, +FIRE dmg), Wildfire (see below), Fireproof (fire-tile damage immune):
  Incinerator/StatusEffectController burn cap; hazard fire tile on the player.
- Blast Shield (own explosion dmg multiplier), Sapper (barrel dmg + barrels on mini-map: expose a
  boolean the LevelRenderer can read — drawing the dots is CP5's), `hazard/ExplosiveBarrelManager`,
  grenade self-damage path.
- Arc Jump (+chain jumps, + chain multiplier) — `entity/ArcCannon` (its visited buffers are sized by
  ARC_CANNON_CHAIN_JUMPS: size them for the max possible bonus, no allocation in fire). Overcharge
  (k-th pierced body x(1 + o*k)) — `entity/PlasmaRifle`.
- Shield Wall (wide guard arc r1, guarded heavy x0.0 r2, all-round guard r3), Riposte Drill (VULNERABLE
  +turns, +dmg) — `Player.applyDirectionalDamage` / `EnemyManager.applyGuardRiposte`.
- Plate Carrier / Hardy (max armour / HP multipliers — apply on `applyBuildEffects()` as a delta to
  `Player.adjustMaxArmor/adjustMaxHealth`, consistently with vitality growth; document the order:
  perk bonuses add, keystone costs multiply after).
- Bloodletting (melee kill heals % max HP), Field Medic (heal multiplier on EVERY heal path — medkit,
  STIM, heal station), Second Wind (once per floor, a lethal hit — including a status tick — leaves
  1 HP; reset on floor arrival).
- Pack Rat (ammo pickup amount multiplier — `PlayerController` pickup + any other ammo grant that is a
  pickup), Haggler (shop price multiplier at the price seam), Salvager (carrier enemies: +1 item drop,
  chance — find the carrier drop path in EnemyManager).
- **Wildfire (D5 runtime rule):** when an enemy that is BURNING dies (any cause), every living enemy on
  an orthogonally adjacent tile gains that enemy's full current burn stack count (capped at
  `FLAME_BURN_MAX_STACKS` + Accelerant bonus), burn timer reset to full; the dead enemy's tile ignites
  (the existing fire-hazard tile mechanism). Every burn the player applies lasts `FLAME_BURN_TURNS` +
  `PERK_WILDFIRE_BURN_TURNS_BY_RANK[rank-1]` turns.
- Attribute grants (Butcher +1 STR/rank): through PlayerStats.addPermanent (delta).
Desktop-visible floaters are optional; nothing in CP2 needs new art.

### CP2 done means
UpgradeCard* gone (grep clean in core/ and lwjgl3/); `./gradlew :core:test` (mirror) = only
StoryBarkTest red, R-LADDER + every rule green; balanceSim exit 0 (record S-* values vs baseline in the
handover; sim behaviour WILL shift — report, do not hide); A1 checkable: a seeded PerkDeck draw with
carried {SPREAD Shotgun, FIRE Incinerator} returns a CLOSE_QUARTERS-or-PYRO perk AND a pivot (verify by
a throwaway main or the sim — do NOT add a test; remove any throwaway file).

## CP3 — keystones at 5 / 10 / 15 / 20

- `PerkDeck.drawKeystones(Keystone[] out, PlayerBuild build)`: 3, never one already taken, >= 1 matching
  `build.highestRankTag()` and >= 1 not (B4; if the matching keystone is taken, fall back sensibly and
  document).
- Level-up flow: when the level being reached is in `KEYSTONE_CHARACTER_LEVELS`, the overlay offers 3
  keystones INSTEAD of perks (AS2: no double reward). The overlay needs a mode flag
  (`setOfferedKeystones(Keystone[], int)`); text = name, gain line, cost line (CP5 styles it). Tap →
  `playerBuild.addKeystone(k)` → same ceremony → `applyBuildEffects()`.
- Keystone seams (B4 + "RUNTIME MUST HONOUR"):
  SCATTERGUN DOCTRINE (SPREAD reload costs no turn — the reload path in `PlayerController`/tick: the
  reload completes without advancing the world; point-blank kills refund a shell; cost: non-SPREAD dmg
  x0.85 — through damageMultiplierFor), DEADEYE (first hit on each enemy is a guaranteed critical — find
  the crit path; cost: clip sizes x0.75 via `clipSizeMultiplier()` wherever clip size is resolved),
  PYROMANIAC (immune to fire; +bonus to every hit and tick on a burning enemy; cost: max armour mult),
  DEMOLITIONIST (immune to own explosions; barrels you detonate deal x2; cost: max HP mult), STORM
  CONDUIT (ENERGY hits arc to one extra living adjacent enemy at the fraction; cost: BALLISTIC dmg),
  IRON BULWARK (a guarded hit reflects the fraction of the blocked damage to the attacker; cost: all
  weapon dmg), BERSERKER (melee kills restore % max HP; below 30% HP melee bonus; cost: heals mult),
  QUARTERMASTER (+1 weapon loadout slot — unlock the locked gun slot in `entity/Loadout`; shop prices
  mult; cost: max HP mult; EDGE: nothing persists after death — runs are not saved), IRONHIDE (max HP
  mult; cost: heals mult AND STIMs no longer cleanse — `Player.cleansePoisonAndWeak` path).
- Sim: keystone choice in PlayerPolicy (`chooseKeystone`) — default: the one matching the highest-rank
  tag.
- A2 checkable: SCATTERGUN DOCTRINE → shotgun reload takes no turn and the Assault Rifle hits for 15%
  less (verify in the sim world or a throwaway main; remove it).

### CP3 done means
Same gates as CP2 (only StoryBarkTest red, balanceSim exit 0, S-* values reported).

## Boundaries (both)
- No render work beyond the minimal overlay text needed to make the flow playable (CP5 owns chips /
  synergy ribbon / rank pips / keystone frame / BUILD page / mini-map barrel dots).
- Do NOT touch `WeaponAbility`/`WeaponRoller` tiers or counts (CP4). Do NOT add sim bias variants or
  S-BUILD (CP6).
- No new test files or test methods. Existing tests UPDATED only where behaviour legitimately changed
  (say which, why).
- Touch-only; no keyboard. No switch on Perk/Keystone/PerkModifier. Naming per CLAUDE.md. No allocation
  in render() or per-shot hot paths.
- Do not commit; do not `git add`.

## Build
Mirror only (`/tmp/claude-0/-home-user-Toon3DA/48c7ae85-305d-55bc-9e8b-bb4641640da1/scratchpad/mirror`,
`./gradlew :core:test`, `./gradlew :core:balanceSim`; results in the real `core/build/`). Also compile
lwjgl3 (`./gradlew :lwjgl3:compileJava`).

## Handover (per checkpoint)
Files changed; the seam list (class/method where each perk/keystone is applied); constants added /
deleted; tests updated and why; gate numbers (test count, reds, balanceSim exit + S-* table lines that
moved); anything left deliberately for CP4/CP5/CP6.

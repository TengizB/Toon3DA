# CHECKPOINT — balance-overhaul order 4: dodge or die (heavies, guard, armour, status, hazards)

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-4.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-c67612fb-x1x9s1`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-4.txt` — the spec. Its rules (H1-H18) and
acceptance criteria (A1-A9) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `enemy` (HEAVY flag, danger tiles, riposte, stagger-cancel, status through the
shield, Revenant/Cinderforge move-sets) + `entity` (guard, armour absorb/shred, STIM cleanse) +
`hazard` + `level` (barrel placement near groups) + `util` (BalanceConfig SECTION 23, GameMath,
BalanceSchema R-HEAVY, BalanceReport) + `render` (danger markers, mini-map fill, heavy intent, edge
pulse, status turns, ARMOUR HELD) + `narrative` (GUARD/READ_INTENT line review) + `sim` (heavy-aware
TacticalPolicy, S-HEAVY, HEAVY REPORT).
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` (R-HEAVY; update R-TELEGRAPH, R-DOT,
R-BOSS-VERB-CAP, R-ENEMY, hazard TP) and `sim/BalanceSimTest` (S-HEAVY). No new test files. Existing
enemy/story tests UPDATED where behaviour legitimately changes. Both gates every balance checkpoint.
Render / narrative work ships with no new tests.
**STARTED:** 2026-10-10
**BASELINE:** branched from `301e72b`. Mirror build: core:test 453 tests, 1 pre-existing failure —
`StoryBarkTest` ("a joke survived into the deepest strata: bark.depth.core.2", narrative content, not
ours). balanceSim exit 0 (TACTICAL readable deaths 1.00, S-SUPPLY 0.78, 159/200 stalled). Any OTHER red test is ours.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-4/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-4/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Touch-only, turn-based, cardinal-line ranged rule untouched. No dodge button, no real-time reaction:
  avoidance is one ordinary move/strafe/GUARD turn (SCOPE OUT).
- No new verbs / SpecialAbility values / enemies / tile symbols; only existing verbs promoted or
  assigned (Revenant WIND_UP, Cinderforge AREA_STRIKE). Boss AI choreography untouched.
- HEAVY is DATA on the verb/ability (per-constant field), never a switch.
- Every number in `util/BalanceConfig` SECTION 23; every formula in `GameMath` with the
  Formula/Derivation/Edge-cases block. Danger tiles in tile space, cardinal, stop at first blocker.
- Render: no allocations in `render()`; danger tiles from a pre-allocated buffer; marker texture
  generated once and disposed.
- Sound catalog: reuse an existing telegraph row (H17); readable with sound off.
- Story strings by id only; line review per `docs/narrative-authority.txt`, no numbers; update
  `docs/story-ui-system.txt` in the same commit.
- OVERRIDE CLAUSE: each overridden rule named + replaced + tests/docs updated in the SAME checkpoint;
  never delete/@Ignore/weaken a test — update it to the new rule.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — HEAVY flags, SECTION 23 heavy numbers, heavy damage from fractions, heavy TP pricing.
      DONE WHEN: R-HEAVY (damage band, commit, guard cap) and updated R-TELEGRAPH / R-BOSS-VERB-CAP
      green; both gates green.
- [x] **CP2** — danger tiles for every heavy; 3D markers, mini-map fill, heavy intent icon, edge pulse.
      DONE WHEN: H4 avoidability asserted; A2 holds (build green; desktop check owed if no display). Split:
  - [x] **CP2a** — danger tiles computed at commit, cleared on resolve/stagger/death; queries; H4 lines in R-HEAVY.
        DONE WHEN: build green; R-HEAVY avoidable lines hold.
  - [x] **CP2b** — 3D markers, mini-map fill, 1.5x red "!!" heavy intent, off-screen edge pulse.
        DONE WHEN: build green; A2 (desktop check owed if no display).
- [x] **CP3** — GUARD rework with riposte; armour absorb/shred; status shield.
      DONE WHEN: A3, A4 (shield half) hold; both gates green.
- [x] **CP4** — status fractions, STIM cleanse, lance SLOW, HUD status turns; R-DOT updated.
      DONE WHEN: A4, A5 hold; both gates green.
- [x] **CP5** — Revenant / Cinderforge heavy assignments; stagger cancels heavy.
      DONE WHEN: A7 holds; both gates green.
- [x] **CP6** — hazard fractions; barrel placement near groups.
      DONE WHEN: A6 holds; both gates green.
- [ ] **CP7** — sim avoidance/guard + S-HEAVY + HEAVY REPORT; GUARD/READ_INTENT line review; all docs;
      reviewer PASS.
      DONE WHEN: A1, A8, A9 hold; both gates green; reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV:** `./gradlew` cannot run in the repo (Android Gradle plugin). Gate via the desktop-only MIRROR
  build in the session scratchpad (`mirror/`): symlinks to core/lwjgl3/assets/gradle*/docs/story,
  `settings.gradle` = `include 'lwjgl3','core'` + `pluginManagement` repo, root `build.gradle` with the
  `buildscript{}` block removed and `configure(subprojects)`. Maven Central returns 429 in this
  container — put `maven { url = "https://maven-central.storage-download.googleapis.com/maven2/" }`
  FIRST in both the subprojects repositories and pluginManagement. Test XML lands in the REAL
  `core/build/test-results/`. balanceSim: `./gradlew :core:balanceSim` from the mirror.
- **CP1 handover:** `enemy/HeavyAttack` {CHARGE, LANCE, AREA_STRIKE, SELF_DESTRUCT} with `damageFraction()`,
  `minimumCommitTurns()` (1/1/1/brace 2), `displayName()`. `EnemyType.heavyWindUp()` (SHELL_BRUTE->CHARGE,
  RIMESHELL_LANCER->LANCE, else null; per-constant override — Revenant gets CHARGE in CP5).
  `SpecialAbility.heavyAttack()` (ctor field). `Enemy.heavyReferenceMaxHealth` (set in `initScaledEnemy`, cached
  per depth) + `Enemy.heavyDamage(HeavyAttack)` (x empowered x weak, min 1). `EnemyType.pricedHeavyDamage(kind)`.
  GameMath `heavyDamageAtDepth`, `heavyExpectedDamageForPricing`; `specialEquivalentAreaStrike` DELETED.
  `RuleKind.HEAVY` + `BalanceSchema.heavyResults()`; test `heavyAttacksHoldTheDamageBandAndAreGuardCapped`.
  SECTION 23 holds GUARD_FRONT_ORDINARY/HEAVY as DATA ONLY (TEMPORARY — CP3 wires them; GUARD_FRONT_MULTIPLIER
  still live). Stop-short charge chance DELETED (a reached charge always lands). Lane geometry for CP2:
  `enemy.chargeDirectionColumn/Row`, `enemy.lanceDirectionColumn/Row` locked at commit; AREA_STRIKE = Chebyshev
  square r=1; SELF_DESTRUCT = wall-stopped cross r=2 (`applySelfDestructBlast` walks it).
- **CP1 decision:** charge / lance are NOT priced as heavies in TP (they were never priced as specials; their
  discounted heavy value ~26 at d1 ~= the basic attack they are priced at). Only AREA_STRIKE re-priced
  (Plague Hulk TP ~39.4->34.9, Iron Stalker ~374->309). Snapshot `e365b9fa...`.
- **CP1 gates:** core:test 455, only StoryBarkTest; balanceSim exit 0 — readable deaths 1.00, S-SUPPLY 0.74,
  TACTICAL 139/200 stalled. Boss existing derived verbs pass the new 50% telegraphed cap unchanged.
- **CP2a handover:** `HeavyAttack.dangerShape()` -> `DangerShape {LANE, SQUARE, CROSS}` with per-constant
  `build(...)`; `HeavyAttack.dangerReachTiles()` (CHARGE MAX+1=6, LANCE range 6, AREA r1, SELF_DESTRUCT r2).
  `PlannedAction.dangerTileColumns/Rows` (cap `EnemyConstants.HEAVY_DANGER_TILE_CAPACITY` 32), `dangerTileCount`,
  `heavyAttack`, `markHeavy/clearHeavy` (every set* resets). Draw guard: `enemy.isAlive() && plan.heavyAttack != null
  && plan.verb != STUNNED`. CROSS lists the caster tile at index 0 — renderer should skip it. `EnemyManager.
  findHeavyThreatAt(col,row)` / `isHeavyDangerTile(col,row)`. GameMath `TileBlockedQuery`, `dangerTilesForLane/
  Square/Cross`, `everyDangerTileHasSafeNeighbour(... [, firstCheckedIndex])`. Tiles cleared after executePlan
  (phase C recommits same tick), on stagger and on the R6 skip. AREA_STRIKE commit now calls announceWindUp (H17).
  Test count is 454 (CP1's "455" counted XML testcases, off by one).
- **CP2b handover:** EnemyRenderer Pass 1.65 (heavy floor markers, 32x32 ring texture built once, alpha blend,
  skips CROSS index 0), heavy intent 1.5x red + "!!" glyph to the RIGHT of the icon (`drawHeavyBangGlyph`; damage
  band is below). LevelRenderer red fill (enables GL blend before its filled pass). `World.updateHeavyEdgePulse()`
  -> `HitVignetteRenderer.setHeavyEdgeSideMask(mask)` via `GameMath.offscreenSideMask` (behind = both edges).
  Constants `EnemyConstants.HEAVY_MARKER_* / HEAVY_MINIMAP_ALPHA / HEAVY_INTENT_*`, `EffectConstants.HEAVY_EDGE_PULSE_*`.
  DESKTOP CHECK OWED (A2): marker readability 1-5 tiles, icon clearance, mini-map alpha, edge band width.
- **CP3 handover:** `Player.applyDirectionalDamage(amount, x, y, boolean heavy)` -> true when front-guarded
  (3-arg form = ordinary); private `applyDamage(int, boolean heavy)`; `Player.tryArmourShieldStatus(StatusType)`
  (WEAK/SLOWED/BLINDED/POISONED only; cost `GameMath.armorStatusShieldCost`, 75 max armour -> 8). EnemyManager
  `applyGuardRiposte(Enemy)` (skipNextAction + VULNERABLE 2, "RIPOSTE" gold; not bosses, not the dead hulk) and
  `armourBlocksStatus(player, type)` at the ONLY two enemy->player status sites (executeDebuffPlayer, acid POISON);
  hazard tiles untouched. phaseCCommit commits STUNNED when skipNextAction is set (also makes the missed-charge
  recovery read STUNNED). `GameMath.armorShredCost` (ceil, capped). `EventTextSystem.COLOR_ARMOUR` (0.55,0.85,1.0).
  GUARD_FRONT_MULTIPLIER DELETED; ARMOUR_ABSORB 0.67; SECTION 23 `ARMOUR_HEAVY_SHRED_MULTIPLIER`,
  `ARMOUR_STATUS_COST_FRACTION`. TacticalPolicy reads only `SIM_TACTICAL_GUARD_DAMAGE_FRACTION` (CP7 replaces it).
- **CP3 gates:** core:test 454, only StoryBarkTest; balanceSim exit 0 — readable 1.00, S-SUPPLY 0.72, TACTICAL
  145/200 stalled. Desktop check owed: RIPOSTE / ARMOUR HELD floaters.
- **CP4 handover:** `GameMath.statusTickDamage(fraction, hostMaxHealth, stacks)`; `StatusHost.getStatusMaxHealth()`;
  `StatusEffectController.applyPoisonStacks(host, stacks, source)` (per-stack magnitude from host max HP; POISON
  decays 1 stack/turn, remainingTurns == stacks) and `applyFractionBurn(host, turns, damageMultiplier, source)`.
  `Player.cleansePoisonAndWeak()`; `EnemyManager.applyLanceSlow` (through the armour shield). SECTION 23
  `POISON_TICK_FRACTION`, `BURN_TICK_FRACTION`, `POISON_STACKS_PER_ACID_HIT`, `POISON_STACKS_PER_TOXIC_TURN`,
  `LANCE_SLOW_DURATION_TURNS`; POISON_MAX_STACKS 6, WEAK 35%, enemy WEAK debuff 3 turns; weapon burn has its own
  `WEAPON_BURN_BASE_PER_TURN` 4 (R-DOT restated: one status-burn + one weapon-burn definition). DELETED
  BURN_DAMAGE_PER_TURN, POISON_DAMAGE_PER_STACK, POISON_DURATION, HAZARD_TOXIC_POISON_TURNS. HUD shows "xN" on
  the POISON icon. Snapshot `a56f924c...`.
- **CP4 decision:** "STIM ('+')" = `MedicalTier.STIM` (`ItemType.MEDKIT_SMALL`, symbol '+'); `ItemType.STIMPACK` is
  never consumed. Cleanse wired into the HEAL button and the inventory consume. A5: d1 2/130 = 1.54%, d15 7/434 =
  1.61% per stack-turn (rounding).
- **TEMPORARY (CP6 resolves):** enemies on toxic tiles get 1 decaying stack/turn at 1.5% enemy max HP; fire tiles
  hit enemies at BURN_TICK_FRACTION x HAZARD_FIRE_ENEMY_DAMAGE_MULTIPLIER (still 1.5; H13 says 2.5).
- **CP4 gates:** core:test 454, only StoryBarkTest; balanceSim exit 0 — readable 1.00, S-SUPPLY 0.70, emergency
  lifeline 0.04 (band <=0.05 — THIN), TACTICAL 150/200 stalled. Desktop owed: CLEANSED floater, xN text, lance SLOW.
- **CP5 handover:** EnemyType `chargeTriggerMinTiles()/chargeTriggerMaxTiles()/chargeCooldownTurns()` (defaults =
  Shell Brute, 0); REVENANT -> CHARGE, lunge 2..3 tiles, cooldown 3 own turns (`Enemy.lastChargeCommitTurn`);
  charger branch routes on `heavyWindUp() == CHARGE`. CHARGE danger reach in `refreshHeavyDangerTiles` = type max+1
  (Revenant 4, Brute 6); `HeavyAttack.CHARGE.dangerReachTiles()` stays 6 for the H4 proof. CINDERFORGE_COLOSSUS
  moveSet MOVESET_AREA, SPECIAL_CADENCE_SLOW. H5: charge/lance/area already cancelled by the skip path; a cancelled
  primed Hulk turn resets the countdown to full brace (stays primed). Snapshot `d29f71e4...`.
- **CP5 numbers / owner flag:** Cinderforge TP ~122.7 -> 112.16 (heavy priced x0.4 displaces a basic hit — the
  spec's avoidance discount LOWERS heavy-bearer TP; in band). Revenant 119 / Brute 133 unchanged (lunge unpriced).
  A7 evidence (throwaway counter, removed): sim heavy commits REVENANT:CHARGE 92, CINDERFORGE:AREA_STRIKE 296,
  BRUTE:CHARGE 109, LANCER:LANCE 607, HULK:AREA 263, HULK:SELF_DESTRUCT 28, IRON_STALKER:AREA 8.
  CP7 line review: Cinderforge bark says nothing about the slam (no contradiction).
- **CP5 gates:** core:test 454, only StoryBarkTest; balanceSim exit 0 — readable 1.00, S-SUPPLY 0.75, lifeline 0.03.
- **CP6 handover:** SECTION 23 `HAZARD_BARREL_{CENTRE,RING}_FRACTION` 1.2/0.6 of the R-LADDER SOLDIER (VOID_SHROUD,
  `HAZARD_BARREL_REFERENCE_SOLDIER_BASE_HEALTH`, unrounded `enemyHealthAtDepth`), player 0.30/0.15; fire-on-enemy x2.5;
  toxic + Hulk cloud 2 stacks/turn on enemies (resolves the CP4 TEMPORARY). `ExplosiveBarrelManager(Level,
  EnemyHitTarget, Player, int depth)`. DELETED EXPLOSION_DAMAGE (both), HAZARD_THREAT_TURNS_STOOD (careless window
  now HAZARD_FIRE_BURN_TURNS). `level/GroupBarrelPlacer` runs in `FloorPopulator.populate` (COMBAT/ELITE only;
  hostable group>=3 rooms, not the start room, >=5 walk tiles from start, flood-fill keeps reachability; seeded
  floorSeed ^ 0xBA22E10D; 'E' barrels). `RuleKind.HAZARD`, `hazardResults()`, `hazardPlacementResults()`; test
  `barrelsStayMeaningfulAtEveryDepthAndSitBesideGroups`. Snapshot `ed36edde...` (stable on rerun).
- **CP6 DECISION (hub) / OWNER FLAG — A6:** chaff half HOLDS (one ring kills every chaff at d10; Ghoul 97 vs ring 97 is
  an exact tie, guarded by R-HAZARD). Soldier half: one ring leaves a soldier at a flat 40%; two overlapping rings
  (the GAMEPLAY LOOP's two-barrel scene) kill it. H13's 0.6 is the rule, A6 the example — kept 0.6, did NOT retune;
  ~0.8 would satisfy one-barrel A6. Report as PARTIAL; HEAVY/hazard feel pass is order 7.
- **CP6 gates:** core:test 455, only StoryBarkTest; balanceSim exit 0 — readable 1.00, S-SUPPLY 0.73, lifeline 0.02.

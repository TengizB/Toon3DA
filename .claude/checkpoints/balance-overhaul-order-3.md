# CHECKPOINT — balance-overhaul order 3: weapon roles and matchups

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-3.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-264aaf69-oycr9f`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-3.txt` — the spec. Its rules (M1-M4, W1-W8,
R-ROLE-1..5, A-1, C1-C6) and acceptance criteria (A1-A9) are referred to by number below and are NOT
repeated here.
**LANE:** full lane — `entity` (DamageClass, MatchupCatalog, MatchupAdvisor, weapons) + `enemy`
(EnemyTrait, damage entry, stagger/knockback) + `util` (BalanceConfig SECTION 22, GameMath,
BalanceSchema, BalanceReport) + `level` (SupplyPlanner) + `input` (switch semantics) + `render`
(C1-C5) + `narrative` (TeachingTopic.MATCHUP) + `sim` (TacticalPolicy, S-SWITCH, MATCHUP REPORT).
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` (R-ROLE-1..5 replacing R-WEAPON bands and
the Railgun waiver; trait-aware R-ENEMY) and `sim/BalanceSimTest` (S-SWITCH). No new test files.
Existing weapon/enemy/story tests UPDATED where numbers/ids legitimately change. Both gates every
balance checkpoint. Render / input / narrative work ships with no new tests.
**STARTED:** 2026-10-03
**BASELINE:** branched from `09a78e0`. Mirror build green; core:test 453 tests, 1 pre-existing failure —
`StoryBarkTest` ("a joke survived into the deepest strata: bark.depth.core.2", narrative content, not ours).
balanceSim exit 0. Any OTHER red test is ours.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-3/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-3/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Touch-only (no keyboard), turn-based, cardinal-line ranged rule untouched; no enemy AI targeting
  changes (SCOPE OUT). Stagger reuses the EXISTING `IntentVerb.STUNNED` — no new verb.
- Class / trait are DATA declared on the weapon / family; no switch on class or trait in damage code
  (A1). Matchup rows via `register()`.
- Every number in `util/BalanceConfig` SECTION 22; every formula in `GameMath` with the
  Formula/Derivation/Edge-cases block. Render: no allocations in `render()`; glyph vertex lists
  pre-built.
- No new sounds (AS7), no new weapons/enemies, no bestiary/inspect screen, no numbers on the HUD
  for matchups (AS5), no numbers in ORA's line (C6).
- Story strings by id only; new lines in `assets/story/story-strings.properties` + defaults;
  `docs/story-ui-system.txt` updated in the same commit.
- OVERRIDE CLAUSE: each overridden rule named + replaced + tests/docs updated in the SAME checkpoint;
  never delete/@Ignore/weaken a test — update it to the new rule.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — DamageClass, EnemyTrait, family traits, MatchupCatalog rows, weapon class
      declarations and SECTION 22 exist; matchup not yet applied.
      DONE WHEN: they exist, headless and unwired; build green.
- [x] **CP2** — matchup applied in player->enemy damage; TP pricing trait-aware; R-ENEMY updated;
      MATCHUP_STRENGTH constant.
      DONE WHEN: both gates green.
- [x] **CP3** — W1-W7 role retunes live (knockback, stagger, falloff, incinerator stacks); R-ROLE-1..5
      replace R-WEAPON bands; Railgun waiver resolved.
      DONE WHEN: A2, A5, A6 hold; both gates green. Split:
  - [x] **CP3a** — role BEHAVIOURS live with starting numbers (falloff tables, knockback, stagger, burn stacks).
        DONE WHEN: build green; only R-WEAPON (DB + Incinerator) and S-SUPPLY red — both resolved by CP3b.
  - [x] **CP3b** — numbers fitted under R-ROLE-1..5 (replacing R-WEAPON + the Railgun waiver); S-SUPPLY back in band.
        DONE WHEN: A2, A5, A6 hold; both gates green.
- [x] **CP4** — per-type ammo generosity in SupplyPlanner and re-fitted reserve caps.
      DONE WHEN: R-ROLE-5 + R-SUPPLY green; both gates green.
- [x] **CP5** — C1, C2, C3, C5 visuals.
      DONE WHEN: A3 holds (build green; desktop check owed if no display).
- [x] **CP6** — C4 switch hint + direct switch + MATCHUP teaching topic.
      DONE WHEN: A4, A7 hold; build + fast gate green.
- [ ] **CP7** — sim TacticalPolicy switching + S-SWITCH + MATCHUP REPORT; all docs; reviewer PASS.
      DONE WHEN: A1, A8, A9 hold; both gates green; reviewer PASS; STATUS IMPLEMENTED. Split:
  - [x] **CP7a** — sim plays the C4 hint; TacticalPolicy takes hints + kit diversity; MATCHUP REPORT; S-SWITCH
        band; planned-heal re-fit; TP weight DECISION 0. DONE WHEN: committed; only S-SWITCH red (owner question).
  - [x] **CP7b** — S-SWITCH resolved per the owner's answer; docs (A9) complete; A1 grep; both gates green.
  - [ ] **CP7c** — reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV (from orders 1-2):** `./gradlew` cannot run in the repo (Android Gradle plugin 403). Gate via a
  desktop-only MIRROR build in the session scratchpad: symlinks to core/lwjgl3/assets/gradle*/docs,
  `settings.gradle` = `include 'lwjgl3','core'`, root `build.gradle` with the `buildscript{}` block
  removed and `configure(subprojects)`. Test XML lands in the REAL `core/build/test-results/`.
- **CP1 handover:** `entity/DamageClass` (8, colour floats from `WeaponConstants.DAMAGE_CLASS_COLOR_*`),
  `enemy/EnemyTrait` (`hasGlyph()`), `EnemyFamily(displayName, trait)` + `trait()`, `EnemyType.trait()` (defaults to
  family; bosses are EnemyType constants -> family trait for free: OVERSEER SHIELDED, CORRUPTOR FLESH, HELL_BARON
  INFERNAL). `entity/MatchupCatalog`: instance `register(class, trait, mult)` + `build()`; static `bootstrap()` /
  `shared()` (lazy, latched) -> `MatchupTable.rawMultiplier / multiplier / classify -> MatchupOutcome /
  strongAgainst / weakAgainst` (precomputed, no alloc). BalanceConfig SECTION 22: `MATCHUP_STRENGTH`,
  `MATCHUP_EFFECTIVE_THRESHOLD`, `MATCHUP_RESISTED_THRESHOLD`, `MATCHUP_ROW_<TRAIT>` float[] in DamageClass order.
  GameMath `matchupMultiplier(raw, strength)`, `classifyMatchup(...)` -> int code. `Weapon.damageClass()` abstract,
  13 subclasses declare. Classification uses the STRENGTH-ADJUSTED multiplier (strength 0 -> all NEUTRAL).
- **CP2 plan (from the CP1 survey):** carry the class like block pierce — `EnemyHitTarget.setActivationDamageClass`
  set/cleared in `Weapon.fire()` beside `armBlockPierce`; matchup in `EnemyManager.applyDamageTo`'s
  weak/vulnerable/backstab line; burn magnitude pre-multiplied in `applyBurningStatus`; barrels wrap their
  `applyDamageTo` in EXPLOSIVE; hazard fire tiles FIRE; Arc chain floored at neutral (W5).
- **CP2 handover:** carrier `EnemyHitTarget.setActivationDamageClass / getActivationDamageClass /
  setActivationMatchupFloorNeutral / isActivationMatchupFloorNeutral`; `Weapon.armDamageClass(target, arm)` in
  `Weapon.fire()` AND `MeleeWeapon.fire()` (melee has its own fire). Matchup in `EnemyManager.applyDamageTo` (after
  ladder, before Block); `Enemy.lastHitMatchup / effectiveMatchupWordShown / resistedMatchupWordShown` (state only);
  `ImpactEventListener.onEnemyMatchupHit(x, y, heightMultiplier, outcome, firstOfKind)` (not yet consumed — CP5).
  Burn magnitude pre-multiplied in `applyBurningStatus` (min 1); fire tiles FIRE (HazardManager); barrels
  EXPLOSIVE save/restore; Arc chain floor-neutral. `EnemyType.neutralEffectiveHitPoints()` (R8, R-LADDER, card
  breakpoints) vs `effectiveHitPoints()` (priced: TP, XP, budget). `MatchupCatalog.bootstrap()` in World + SimWorld.
- **TEMPORARY (CP7 resolves):** `MATCHUP_TP_REFERENCE_WEIGHT = 0.0f`. Any w > 0 moved S-SUPPLY exit health to
  0.82-0.87 (> 0.80) on a TACTICAL bot that never switches; NOT loosened. CP7 re-fits w (R-ENEMY holds to ~0.83,
  Spiresower limits) once TACTICAL switches by matchup, then re-baselines LevelGeneratorSnapshotTest + SECTION 6.
- **CP2 gates:** core:test 453, only pre-existing StoryBarkTest; balanceSim exit 0, S-SUPPLY 0.78, TACTICAL 160/200
  stalled (baseline 162).
- **CP3a handover:** `GameMath.shotgunFalloffAtTile(table, d)`, `incineratorBurnPerStack(ladderHit, fraction)`;
  `Weapon.damageWithFalloff(mult)`; Shotgun/DB override `damageAtDistance` with `SHOTGUN_/DOUBLE_BARREL_FALLOFF_BY_TILE`.
  `entity/SpreadImpact.applyCloseRangeImpact(target, enemy, d, stepCol, stepRow)` (knockback <=1, stagger <=2).
  EnemyHitTarget `applyBurningStack / tryStaggerEnemy / tryKnockbackEnemy`; `EnemyManager.worldTurnIndex`;
  `Enemy.lastStaggeredTurn`; `StatusEffectController.applyStacking` (one shared timer, tick = magnitude x stacks).
  Decisions: bosses ignore stagger + knockback (no phase hook in boss AI); stagger sets plannedAction.verb=STUNNED
  immediately (truthful intent); no chain = SHOTGUN_STAGGER_MIN_TURNS_BETWEEN 2; any door tile refuses knockback.
  Starting numbers: SHOTGUN 44, DBL 40 (per barrel), FLAME_IMPACT 12, FLAME_FALLOFF 8, FLAME_BURN_FRACTION 0.5,
  FLAME_BURN_MAX_STACKS 3, FLAME_BURN_TURNS 4. Deleted SHOTGUN/DBL_SHOTGUN_DAMAGE_DROP_COEFF, FLAME_BURN_DAMAGE_PER_TURN.
- **TEMPORARY RED at CP3a (CP3b resolves):** R-WEAPON out of band (DB 26.67 > 26, Incinerator 33.05 > 32) — R-WEAPON
  is REPLACED by R-ROLE in CP3b. balanceSim S-SUPPLY 0.82 > 0.80: bisected to the W1 shotgun table alone (the start
  kit, real and sim, is Chaingun + Shotgun; point-blank 36 -> 44). DECISION (hub): keep the S-SUPPLY band; after the
  R-ROLE fit, bring it back in band by re-fitting the SupplyPlanner's planned HEAL (SECTION 21 number, override
  recorded) — the stronger start kit means a floor needs less heal to leave you equally hurt.
- **CP3b handover:** `util/WeaponRoleModel` (+ RoleScenarioWeapon/Pattern/Outcome) reads class/ammo/clip/reload/
  range/accuracy/per-tile hit from a FRESH weapon instance; scenarios S1-S8 geometry + margins in SECTION 22;
  GameMath `roleScenarioMeanHit / SustainedDamagePerTurn / Clear / BruiserHitsTaken`. `RuleKind.WEAPON_ROLE`,
  `BalanceSchema.weaponRoleResults()` (R-ROLE-1..5 + A6 enforced); R-WEAPON, WeaponRole, RangedWeaponSpec,
  WEAPON_POWER_* bands, REFERENCE_AMMO_EFFICIENCY, GameMath.weaponPowerScore and the Railgun waiver DELETED.
  Test slot renamed `weaponRolesHoldOnTheReferenceScenarios`. Snapshot `2170ba3f...`.
  Fit: Shotgun 44->66, DB 32->59/barrel, Arc 28->40, Railgun full 90->75, Grenade 42/22->36/32, burn fraction
  0.5->0.6, shell box 4->3, shell cap 17->11. AR/Chaingun/Plasma/matchup cells unchanged. S4 representative =
  Cinderforge Colossus (Lancer window too narrow). Melee exempt from R-ROLE-1 (recorded).
- **CP3b decisions / owner flags:** A5 as written ("knocks a Gore Biter back") cannot be shown — a 66-damage
  1-tile hit kills a d1 Gore Biter, and knockback/stagger skip dead targets; mechanics verified on Shell Brute
  (knockback + STUNNED + skipped turn) and Revenant at 2 tiles (stagger, no knockback), Shell Brute at 3 tiles (none).
  Record as IMPLEMENTATION NOTE. Chaingun sustains ~2x the AR per turn (R-ROLE-1 margins vs it; R-ROLE-4 vs AR per W1).
  Thin: R-ROLE-4 2.03/2.0, R-ROLE-2 S1 1.20-1.23/1.30, S-SUPPLY exactly 0.80 (no heal re-fit needed), Railgun vs
  Grenade S1/S4. Stale Railgun waiver comments remain at BalanceConfig RAILGUN_MAX_SLUGS / RAILGUN_PICKUP_SLUGS and
  WeaponConstants — fix in CP4.
- **CP3b gates:** core:test 453, only StoryBarkTest; balanceSim exit 0, S-SUPPLY 0.80, TACTICAL 171/200 stalled.
- **CP4 handover:** `AmmoType.getSupplyGenerosity() / getBankingFloorsTarget()` (SECTION 22 constants, no switch);
  `GameMath.normalisedAmmoShare(base, generosity, weightedSum)` in `SupplyPlanner.addAmmoPickups` (floor total
  unchanged). Mapping: GRENADES -> ROCKETS; FUEL does not exist (Incinerator burns CELLS, 1.0) — spec's "FUEL ~1.5
  floors" lands on CELLS (1.51). Caps: BULLETS 38->25 (1.00 floors), SHELLS 11 (1.45), CELLS 27 (1.51), ROCKETS
  18->21 (1.51), SLUGS 6->7 (1.05). `BalanceSchema.ammoBankingResults()` (+/-0.15 of target). Snapshot `ed9cdc81...`.
  OWNER FLAG: bullet reserve 25 < AR clip 30 (scarce generalist, intended by A-1).
- **CP4 gates:** core:test 453, only StoryBarkTest; balanceSim exit 0, S-SUPPLY 0.80 (AT the cap — any later
  start-kit buff owes the planned-heal re-fit in the same change). R-ROLE unchanged.
- **CP5 handover:** `render/MatchupGlyphs` (`drawTrait` / `drawClass`, ShapeRenderer sink + Batch white-pixel
  sink, x,y = bottom-left); `MatchupTable.bestClassAgainst(trait)`; Enemy cosmetic `matchupTint*/matchupWord*`
  timers set in `recordMatchupHit`, decayed in `Enemy.advanceHitFlash`. C2 glyph left of bar (block number shifts
  left). C3 on the HUD SLOT STRIP (no right panel exists) for every carried gun. C5 in
  WeaponInspectOverlayRenderer (card 680 tall, origin y 20). `ImpactEventListener.onEnemyMatchupHit` still
  unconsumed (harmless default). DESKTOP CHECK OWED (no display): glyph legibility at 9-16 units, compare-card row
  spacing (~29 units). Build green; core:test 453, only StoryBarkTest.
- **CP6 handover:** `entity/MatchupAdvisor.findTarget(enemies, level, doorManager, col, row, stepCol, stepRow, range)`
  (first living ALERTED enemy in the facing lane, stops at `level.isBlockedAt`) + `hintWeapon(equipped, loadout,
  trait, distance)` (RESISTED equipped -> best EFFECTIVE loadout gun with ammo + range; ties: multiplier, ammo,
  slot; melee never offered). `PlayerInventory.selectLoadoutWeapon(w)`; `PlayerController.setSwitchHintWeapon /
  getSwitchHintWeapon` (trySwitchWeapon jumps when set). `TouchInputState.setSwitchHintActive / isSwitchHintActive`;
  pulse in TouchControllerRenderer (GameMath.pulseMultiplier, TouchConstants.SWITCH_HINT_*; outline follows the
  rect, not the corner radius). `World.updateMatchupHint()` each idle PLAYING frame; first teach = hint up AND
  target.lastHitMatchup RESISTED; episode fire-turn counter on TickCause.FIRE -> `TeachingSystem.onMatchupHintIgnored()`
  at STORY_MATCHUP_HINT_EPISODE_FIRE_TURNS (3). Strings `story.bark.control.matchup` / `.retaught.matchup`.
  SimWorld does NOT set the hint yet — CP7 wires MatchupAdvisor into SimWorld/TacticalPolicy.
  Gates: core:test 453, only StoryBarkTest; balanceSim exit 0 (unchanged; sim hint null). Desktop check owed for A4.
- **RESUMED after a usage-limit cut (2026-10-03 22:24 UTC):** the balance spoke died mid-CP7 with 15 files
  uncommitted; verified on resume (build green, core:test only StoryBarkTest, balanceSim exit 1 on S-SWITCH only).
- **CP7a handover:** `SimWorld.updateMatchupHint()` mirrors World; `SimView.switchHintWeapon()`; TacticalPolicy
  step 2b takes the hint; `PlayerPolicy.slotToReplaceForGroundWeapon` (TACTICAL: take a found gun of a NEW class,
  drop the redundant/emptiest, never the last armed gun). FloorLedger `matchupSwitches/effectiveHits/resistedHits/
  damageByClass`; PolicySummary `meanMatchupSwitchesPerCombatFloor` + take rate + per-hinted-floor; SimReport
  MATCHUP REPORT; `RuleKind.SIM_SWITCH`, `BehavioralBands.switchResult`, `SIM_SWITCH_MIN_PER_COMBAT_FLOOR` 1.0;
  BalanceSimTest `tacticalSwitchesWeaponsByMatchup`. Heal re-fit: `SUPPLY_AVOIDANCE_FACTOR` 0.55 -> 0.70 (S-SUPPLY
  0.80 -> 0.78). TP weight: DECISION 0 (w 0.25 -> S-SUPPLY 0.83, w 0.5 -> 0.81). Snapshot `2cf72803...`.
- **S-SWITCH MEASURED (CP7a):** TACTICAL 0.13 per COMBAT floor (band >= 1.0). Hint take rate 1.00; switches per
  COMBAT floor that SHOWED a hint 1.00; only 21/167 COMBAT floors showed a hint. Cause: Chaingun+Shotgun start kit,
  2 usable slots, ~85% of runs stall on floor 1 (navigation waiver), kit diversity adds ENERGY 5% / RAIL 2% damage
  share only. OWNER QUESTION raised: how A8's S-SWITCH should read. Docs (A9) were mid-edit at the cut — CP7b finishes.
- **CP7b handover:** owner delegated A8 ("make the best decision"); hub chose D3 (recorded in the idea file):
  S-SWITCH = three lines in `BehavioralBands.switchResults` — hinted COMBAT floors >= 10 (`SIM_SWITCH_MIN_HINTED_
  COMBAT_FLOORS`), take rate >= 0.8 (`SIM_SWITCH_MIN_TAKE_RATE`), switches per hinted COMBAT floor >= 1.0
  (`SIM_SWITCH_MIN_PER_HINTED_COMBAT_FLOOR`); old `SIM_SWITCH_MIN_PER_COMBAT_FLOOR` deleted; BalanceSimTest
  asserts 3 lines, none waived. A9 docs done: weapon-creation-guide (DamageClass + WeaponRoleModel row + R-ROLE +
  SpreadImpact), enemy-system (trait table per family, application point, TP split, stagger/knockback, burn
  stacks), authority S-SWITCH entry + change log + SECTION 8 regenerated; EnemyFamily Javadoc refreshed;
  CLAUDE.md sim row (nine S-* bands). A1 grep clean (only TouchAction.FIRE matches). Gates: core:test 453, only
  StoryBarkTest; balanceSim exit 0 — S-SWITCH 21 hinted floors / take 1.00 / 1.00 per hinted floor; S-SUPPLY 0.78.
- **CP7c next:** reviewer gate. Owner flags for the final report: A5 Gore Biter dies at 1 tile (mechanics proven
  on Shell Brute/Revenant); Chaingun ~2x AR per turn; bullet reserve 25 < AR clip 30; C3 glyph on slot strip (no
  right panel); FUEL -> CELLS; TP weight 0 (decision); desktop visual checks owed (A3, A4, A5, A6 in-game).
- **CP7c reviewer round 1: PASS WITH FIXES** (report: scratchpad reviewer-verdict.md). Blocking fixed: (1) R-ROLE-5
  now checks each class's DECLARED niche (`BalanceConfig.ROLE_DECLARED_NICHE_BY_CLASS`; RAIL -> S4, 1 slug vs 2.1/2.9
  per floor) instead of the first closest-to-best scenario; (2) IMPLEMENTATION NOTE added to the idea file.
  Non-blocking taken: ItemWindow Railgun range derived from the charge table (was "40-90"); stale FUEL /
  applyBurningStatus / Railgun-waiver comments; orphaned Javadoc re-attached (World.updateMatchupHint,
  Weapon.armDamageClass); MatchupGlyphs naming. Left, recorded in the note: C5 inventory rows, W3 BURNABLE-soldier
  target, S-SWITCH take-rate lines pass by construction (only the hinted-floor count can fail). Gates after fixes:
  core:test 453, only StoryBarkTest; balanceSim exit 0.

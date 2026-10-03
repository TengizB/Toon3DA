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
- [ ] **CP3** — W1-W7 role retunes live (knockback, stagger, falloff, incinerator stacks); R-ROLE-1..5
      replace R-WEAPON bands; Railgun waiver resolved.
      DONE WHEN: A2, A5, A6 hold; both gates green.
- [ ] **CP4** — per-type ammo generosity in SupplyPlanner and re-fitted reserve caps.
      DONE WHEN: R-ROLE-5 + R-SUPPLY green; both gates green.
- [ ] **CP5** — C1, C2, C3, C5 visuals.
      DONE WHEN: A3 holds (build green; desktop check owed if no display).
- [ ] **CP6** — C4 switch hint + direct switch + MATCHUP teaching topic.
      DONE WHEN: A4, A7 hold; build + fast gate green.
- [ ] **CP7** — sim TacticalPolicy switching + S-SWITCH + MATCHUP REPORT; all docs; reviewer PASS.
      DONE WHEN: A1, A8, A9 hold; both gates green; reviewer PASS; STATUS IMPLEMENTED.

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

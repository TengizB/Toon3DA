# CHECKPOINT — balance-overhaul order 1: the power ladder

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-1.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-470f487d-gs117i`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-1.txt` — the spec. Its rules (R1-R15) and
acceptance criteria (A1-A9) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `util` (BalanceConfig/GameMath/BalanceSchema/BalanceReport) + `entity` +
`enemy` + `progression` + `item` + shop + `sim` + `render` + `narrative`.
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` (R-LADDER, R-LADDER-AFFORD, re-fitted
rules) and `sim/BalanceSimTest` (S-LAG). No new test files. Existing enemy/story tests UPDATED only
where numbers/ids legitimately change. Both gates every checkpoint.
**STARTED:** 2026-09-30
**BASELINE:** branched from `35ddeaf`. Mirror build (see NOTES): compiles; 449 tests, 1 pre-existing
failure — `StoryBarkTest` "a joke survived into the deepest strata: bark.depth.core.2" (narrative
content, not ours). Any OTHER red test after CP0 is ours. balanceSim baseline recorded in NOTES.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-1/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-1/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Touch-only (no keyboard), turn-based, cardinal-line ranged rule untouched.
- Every number in `util/BalanceConfig` (SECTION 20), every formula in `GameMath` with the
  Formula/Derivation/Edge-cases block; no inline formulas; no render allocations.
- OVERRIDE CLAUSE: each overridden rule is named + replaced + tests/docs updated in the SAME
  checkpoint; never delete/@Ignore/weaken a test — update it to the new rule.
- Story: no hardcoded strings; UNDERGEARED lines contain no numbers (docs/narrative-authority.txt);
  new string ids in both the Java fallback table and `assets/story/story-strings.properties`.
- SCOPE OUT (idea file) is binding: no enemy counts/groups, roles, heavy attacks, perks, run end,
  dev tools, weapon XP.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — SECTION 20 constants, GameMath ladder methods, `util/ExpectedPlayer`, BalanceReport
      LADDER table; R-LADDER + R-LADDER-AFFORD asserted on the formulas.
      DONE WHEN: those exist, nothing wired into gameplay yet, both gates green.
- [x] **CP2** — weapon damage R2-R6 (Fist exemption) and enemy HP/damage growth R7 live with rebased
      EnemyType numbers R8; R-DEPTH and R-GEARGATE retired; R-WEAPON / R-ENEMY re-banded.
      DONE WHEN: live in gameplay code, both gates green.
- [x] **CP3** — player vitality growth R9 and fractional heals R10 live; R-HEAL / R-HEALDRAIN-DEPTH /
      R-SCARCITY-DEPTH / R-XP-PACE / R-CARD-BREAKPOINT re-fitted at 1..25.
      DONE WHEN: live, both gates green.
- [ ] **CP4** — BossBalance and the sim read `expectedPlayerAtDepth`; R-BOSS-* green; TacticalPolicy
      buys the rung; S-LAG + LADDER REPORT printed by balanceSim.
      DONE WHEN: both gates green, report printed.
- [ ] **CP5** — shop guaranteed level-up rung, cap and price (R13).
      DONE WHEN: A5 holds, build green.
- [ ] **CP6** — HUD LV tag, compare card, inventory rows, arrival "THREAT LV d", UNDERGEARED topic.
      DONE WHEN: A2, A6, A7 hold, build green.
- [ ] **CP7** — all docs updated; living tables regenerated; reviewer PASS.
      DONE WHEN: A1, A3, A4, A8, A9 hold; both gates green; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV (cost time):** `./gradlew` cannot run in the repo (403 on dl.google.com for the Android
  Gradle plugin). Gate via a desktop-only MIRROR build in the session scratchpad: symlinks to
  core/lwjgl3/assets/gradle*/docs, `settings.gradle` = `include 'lwjgl3','core'`, root `build.gradle`
  with the `buildscript{}` block removed and `configure(subprojects)`. Maven Central sometimes answers
  429 — retry with backoff. Test XML lands in the REAL `core/build/test-results/`.
- **BASELINE sim (35ddeaf):** all three policies median depth 1.0; 180-187/200 runs STALL on floor 1
  (the navigation waiver). A2's sim half cannot be read from ordinary played runs → CP4 must measure
  the LADDER REPORT on a dedicated sub-matrix (e.g. runs STARTED at depths 3-5 with an on-curve kit
  vs the start kit), not from the stalled depth-1 matrix.
- **ROOT CAUSE found:** `Weapon.damageAtDistance()` multiplies the RAW base `damage`, never
  `effectiveDamage` — weapon level has never reached an actual hit (only the inspect card). CP2 must
  route damageAtDistance (and every melee/Railgun/Grenade/Incinerator damage path) through
  `GameMath.weaponLadderDamage`.
- **CP1 handover:** `GameMath.ladderScale(growth, level)`, `levelGapMultiplier(gap)` (+ primitive),
  `rarityDamageMultiplier(tierOrdinal)`, `weaponLadderDamage(base, level, tierOrdinal, threatLevel,
  gapExempt)`, `enemyHealthAtDepth(base, d)` / `enemyDamageAtDepth(base, d)`, `playerVitalityScale(cL)`,
  `fractionOfMaximum(max, fraction)`, `expectedRarityMultiplierAtDepth(d)`, `ladderReferenceHitDamage()`
  (AR at 3 tiles = 15.2), `expectedPlayer(d, wL, rarity, cL)`, `expectedCharacterLevelAtDepth(d)`,
  `expectedPlayerAtDepth(d)`, `ladderTurnsToKill(eHP, hit)`. `util/ExpectedPlayer` fields: depth,
  weaponLevel, rarityMultiplier, levelGapMultiplier, characterLevel, damagePerTurn (d1 = 25),
  referenceHitDamage (d1 = 15.2), maxHealth, maxArmor, effectiveHitPoints (d1 = 205).
  BalanceSchema: `ladderResults()`, `ladderAffordResults()`, `ladderReferenceArchetype(role)`,
  `ladderTurnsToKill/ToDie(type, d, player)`, `ladderStartWeaponPlayer(d)`,
  `ladderWeaponOffsetPlayer(d, offset)`, `ladderLevelUpPrice(d)`, `combatFloorCreditIncome(d)`.
- **DECISION (not in spec):** offence cards are modelled as a multiplicative PP lift (the boss
  derivation's existing convention) and defence cards as flat eHP (R9 wording). With that, the fit
  needs enemy HP growth 1.139 (not the 1.09 starting point) — HP at d25 ≈ 23x, above the spec's
  informal "7-10x" estimate; AS10 accepts bigger numbers. Recorded in the authority doc.
- **DECISION:** the rung is priced at the NEW `LADDER_LEVEL_UP_POWER_POINTS` = 1.5 PP (54 cr at d1,
  184 at d25); the shop still reads `SHOP_WEAPON_LEVEL_UP_POWER_POINTS` (10 PP) until CP5 switches it.
- **TEMPORARY:** old constants (ENEMY_*_SCALE_PER_DEPTH, WEAPON_LEVEL_DAMAGE_PER_LEVEL, flat heals,
  MAX_WEAPON_LEVEL 10 in WeaponConstants) still live; CP2/CP3 replace them. R-DEPTH / R-GEARGATE still
  enforced until CP2.
- **CP2+CP3 FOLDED (one commit):** switching the enemy growth to the ladder broke the heal/scarcity
  rules, which cannot be re-fitted without R9/R10, so CP3 landed in the CP2 commit (both boxes ticked).
- **CP2/3 handover (weapon side, java-architect spoke):** `Weapon.setFloorThreatLevel(int)` /
  `getFloorThreatLevel()` (0 = unset = on-level), `getLadderDamageMultiplier()`, `getLevelGapMultiplier()`
  (1.0 for the exempt Fist), `protected isLevelGapExempt()` (Fist overrides true);
  `PlayerInventory.syncFloorThreatLevel(int)`; `World.syncWeaponThreatLevels()` runs after floor build
  and at the top of every `update()`; `SimWorld.syncWeaponThreatLevels()` (private) in buildFloor and
  around every action. `GameMath.respannedLegacyWeaponLevel(int)`; `weaponScaledDamage` DELETED.
  `WeaponRoller.rollLevel` uses WEAPON_LEVEL_ROLL_OFFSETS/WEIGHTS. `BalanceConfig.MAX_WEAPON_LEVEL` 27
  (WeaponConstants re-exports). Flat ability magnitudes (KINETIC_SLAM wall bonus, REND/static) and hazard
  damage are deliberately NOT on the ladder (priced by R-ABILITY / not weapon hits).
- **CP2/3 handover (heal side, java-architect spoke):** `PlayerStats.vitalityGrowthDelta(base, newLevel)`;
  `World.applyVitalityGrowth()` after `playerProgress.advanceLevel()` in `applyUpgradeCard()`; SimWorld
  `resolvePendingLevelUps()` mirrors it. `MedicalTier.healAmountFor(maxHealth)` / `getHealFraction()`;
  `Level.armourRestoreFractionOfPickup(char)`. ItemWindow shows "+45% HP" (no player access there).
  `DefaultShopOfferSource` prices medkits at PLAYER_MAX_HEALTH (ShopContext has no max HP).
- **DECISIONS (CP2/3, not in spec):** expected DEFENCE card share 0.25 (was 0.5 in CP1) — keeps the TTD
  drift ±7% so R-HEALDRAIN can hold (a larger flat share bends the survival curve); ENEMY_DAMAGE_GROWTH
  1.097. R8 damage chosen at the LOW end of each band (chaff damage unchanged). Enemy block gains rebased
  to ~1 reference hit and the BLOCK_MAX cap now rides enemy HP growth. Route model: ammo PP ÷ expected hit
  growth, credit PP ÷ shop depth factor. R-HEAL re-stated as fraction bands (spec's override clause).
- **DEAD END:** cutting ammo by drop FREQUENCY makes guaranteed boxes 43% richer vs demand and breaks
  R-TRAJECTORY/R-CALM-COST — cut box SIZE instead.
- **TESTS UPDATED (not new):** BalanceAuditTest (R-DEPTH/R-GEARGATE methods replaced by comments pointing at
  R-LADDER; golden-ratio -> enemyHitResults; three renames to "ToTheRunEnd"; roll sweep to MAX level),
  Spiresower/RimeshellLancer tests (golden ratio -> R8 hit bands), AuricSentinelShardTest (hit < rebased
  HP), RouteEconomicsTest (clinic stock constant), LevelGeneratorSnapshotTest (re-baselined digest,
  confirmed stable across two JVM runs). Count 451 -> 449 (two retired-rule methods).
- **NEXT (CP4):** BossBalance still derives from its own `expectedPlayerDamagePerTurn` (flat card PP) and
  `REFERENCE_PLAYER_EHP` — switch to `GameMath.expectedPlayerAtDepth`. R-BOSS-* currently green.

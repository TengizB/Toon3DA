# CHECKPOINT — balance-overhaul order 6: the 25-floor run (finite descent, bosses, economy)

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-6.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-7acac777-5826yo`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-6.txt` — the spec. Its rules (F1-F15) and
acceptance criteria (A1-A7) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `route` (finite map, region E, BossTable) + `render/routeicons` (region E crest)
+ `render` (NAV header, DESCENT COMPLETE) + `world` (VICTORY path, ending at final clear) + `narrative`
(repeatable-at-final-clear flag) + `util` (SECTION 25, StatsStore keys, BossBalance, BalanceSchema
rules) + `shop` (economy) + route events/mysteries + `sim` (depth 25, run-outcome bands, S-BOSS,
S-SHOP) + docs.
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` and `sim/BalanceSimTest`; existing
route/story tests UPDATED where finite-run behaviour legitimately changes. No new test files.
Render/story/world work ships with no new tests. Both gates every balance checkpoint.
**STARTED:** 2026-10-12
**BASELINE:** branched from `e8b211f` (order 5 merged, PR #301). Baseline gates recorded in NOTES at CP1.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-6/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-6/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Touch-only, turn-based; register() rows not switches (BossTable, region spec, crest painter, ambience).
- Every number in `util/BalanceConfig` SECTION 25 (or the matching Constants file); formulas in GameMath.
- Story: no hardcoded strings — "DESCENT COMPLETE" is a framing-strings chrome id; vocabulary ladder (F2).
- Render: no allocations in `render()`; procedural ShapeRenderer crest, no textures.
- SCOPE OUT: no new bosses, no NG+/endless, no new story lines beyond chrome, no REST changes, no boss AI
  changes; roguelike_order_19 superseded (not built).
- OVERRIDE CLAUSE: each overridden rule named + replaced + tests/docs updated in the SAME checkpoint;
  never delete/@Ignore/weaken a test — update it to the new rule.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — finite route map (25 layers, 5 regions, region E spec/ambience/crest, no append path); NAV header F6.
      DONE WHEN: A1, A4 hold; route tests updated; both gates green.
- [x] **CP2** — depth-25 boss defeat ends the run as VICTORY (report label, win stats); F5/D5 ending behaviour.
      DONE WHEN: A2, A3 hold; build green.
- [x] **CP3** — BossTable rows drive bosses; derivation from ExpectedPlayer.
      DONE WHEN: R-BOSS-* green on all five rows; both gates green.
- [x] **CP4** — region dial + elite weights (F7); R-REGION updated; F8 run-outcome bands replace S-FAIR's depth band.
      DONE WHEN: both gates green.
- [x] **CP5** — shop economy (F12, F13); R-CREDITS v2 green; S-SHOP reported.
      DONE WHEN: both gates green.
- [x] **CP6** — event/mystery rewards in ladder currencies (F15); R-MYSTERY-EV / event value green.
      DONE WHEN: both gates green.
- [ ] **CP7** — S-BOSS consistency reported; all docs; reviewer PASS.
      DONE WHEN: A5, A6, A7 hold; both gates green; reviewer PASS; STATUS IMPLEMENTED.
  - [x] **CP7a** — S-BOSS (RuleKind.SIM_BOSS, 15 lines, navigation-waived), RUN REPORT, authority SECTIONS 6 + 8 regenerated.
  - [ ] **CP7b** — remaining docs (authority S-BOSS text, knowledge SECTIONS 9/12/16, CLAUDE.md), then reviewer.

## NOTES CARRIED FORWARD

- **ENV (from orders 4/5):** `./gradlew` cannot run in the repo (Android Gradle plugin). Gate via a
  desktop-only MIRROR build in the session scratchpad (`mirror/`): symlinks to core/lwjgl3/assets/
  gradle*/docs/story, `settings.gradle` = `include 'lwjgl3','core'`, root `build.gradle` without the
  `buildscript{}` block. Maven Central may 429 — put the googleapis maven mirror first.
- **BASELINE (2026-10-12, `e8b211f`):** core:test 458, only StoryBarkTest red (pre-existing); balanceSim exit 0 —
  TACTICAL median depth 2, 175/200 stalled (navigation waiver), S-FAIR/S-SKILL/S-ECONOMY/S-BUILD-dominant WAIV.
  Baseline summary kept in scratchpad `baseline-summary.txt`. The sim never reaches a boss (navigation stalls), so
  every depth-derived band (F8, S-BOSS, S-SHOP sample) is REPORTED/WAIVED under the navigation waiver (A6 says so).
- **CP1 handover:** `RouteMapConstants.FIXED_REGION_COUNT` = RUN_FINAL_DEPTH / REGION_BAND_SIZE (5); REGION_E_* rows
  (name "THE CORE" — F2: no StoryTerm row for "Core"); `RegionPlan` constructor refuses finalDepth > RUN_FINAL_DEPTH;
  deleted: withAppendedBreachBand, RegionSpec.endless, extendWithNextRegion, shouldExtend, RouteMap.appendLayers,
  EXTENSION_TRIGGER_DISTANCE, World.ensureRouteExtended, SimWorld extension call. Ambience fallback = the_core.
  `render/routeicons/CoreLatticeCrestPainter`; palette tint `tintTheCore`. NAV header `RouteMapConstants.NAV_FLOOR_HEADER_*`
  -> "FLOOR n / 25", n = current.depth + 1 (staging room = FLOOR 1). Region dial arrays still 4 entries (clamped) — CP4.
- **CP1 FIX (generator):** region E exposed a reachability-repair dead end (seed 68: d22 COMBAT -> d23 COMBAT -> d24 ELITE,
  each its layer's last combat option, so no type rewrite is legal). Added `RouteMapGenerator.connectToReachingNeighbour`
  — a deterministic extra edge to the next layer's node that still reaches the kind. Route tests updated (finite-run
  methods replace the two endless ones; RouteEconomicsTest checks 20 seeds; Order10 five regions).
- **CP1 gates:** core:test 458, only StoryBarkTest; lwjgl3 compiles; balanceSim exit 0, every S-* line identical to baseline.
- **CP2 handover:** VICTORY = `World.recordFinalClearVictory()` (polled beside requestBossDefeatedBark: depth >=
  RUN_FINAL_DEPTH + bossFloorController.isBossDefeated()) -> `RunStats.recordVictory()/isVictory()`, report snapshot,
  `saveRunRecordsOnce()` AT THE DEFEAT. `StatsStore` keys `runsWon` / `bestWinTurns` (PersistentStats fields; a won run
  is not a death); NEW OPERATOR's prefs.clear() wipes them, `PersistentStats.reset()` zeroes them. Report title id
  `StoryUiConstants.STORY_REPORT_VICTORY_TITLE_ID` = `story.report.victory` = "DESCENT COMPLETE" (FramingStrings +
  properties). `onDescentRequested` at depth >= 25 never descends (re-arms coreEndingRequested). F5: data flag
  `ExchangeDefinition.Builder.repeatableAtFinalClear(true)` on the ending door; `ExchangeSystem.requestById` now refuses
  an answered one-shot row unless that flag is set (before this, requestById never consulted "seen" at all).
  Sim: `RunLedger.Ending.VICTORY` after clearing the depth-25 floor; `SIM_DEPTH_CEILING` = RUN_FINAL_DEPTH.
- **CP2 gates:** core:test 458, only StoryBarkTest; lwjgl3 compiles; balanceSim exit 0, S-* identical (header ceiling 25).
- **CP3 handover:** `route/BossTable` (`standard()`, `rows()`, `rowForDepth(depth)` = exact or deepest row above,
  `firstRowFor(archetype)`, `register(Row)`; `Row{depth, archetype, targetFightTurns, survivalRatio}`) bootstrapped from
  SECTION 25 `BOSS_TABLE_DEPTHS / _TARGET_FIGHT_TURNS / _SURVIVAL_RATIO` (archetypes are data in BossTable.bootstrap).
  `BossBalance.Archetype` keeps positionalMultiplier + displayName only; `statsForDepth(depth)`, `statsForRow(row)`,
  `canonicalStats(archetype)` (EnemyType reference numbers). R-BOSS-GATE/FAIR/VERB-CAP/REWARD/AMMO iterate rows; R-BOSS-
  FAIR v2 = target +/- `BOSS_FIGHT_TURNS_TOLERANCE` (0.25) + target in act band + ratio in band. R-SUPPLY S6 carrier share
  skips `spec.bossArenaAmmo()` (domain correction, documented). Authority doc: OVERRIDE RECORD + CHANGELOG lines added.
- **CP3 gates:** core:test 458, only StoryBarkTest; lwjgl3 compiles; balanceSim exit 0, no S-* moved.
- **CP4 handover:** REGION_TP_BUDGET_MULTIPLIER {0.85,1.00,1.25,1.20,1.30}, MAX 1.5; R-REGION line "region dial shape
  C>=B>=A, E>=D" + lethal {2,3,4}; ELITE weights A .60 B 1.00 C 1.60 D 1.40 E 1.60. F8 constants SECTION 25
  (`SIM_TACTICAL_WIN_RATE_*`, `SIM_TACTICAL_REACH_*`, `SIM_TACTICAL_LOSS_DEPTH_*`, `SIM_NAIVE_*`, `SIM_FIRST_BOSS_*`);
  SIM_TARGET_DEPTH_* deleted; SIM_SKILL_DEPTH_GAP_MIN 5. `BehavioralBands.fairResults(tactical, naive)` emits 8 F8 lines
  + readable deaths (all F8 lines navigation-waived); `PolicySummary.winRate/reachedDepthFraction/medianLossDepth/
  runsReachingDepth/runsClearingFirstBoss`. BalanceSimTest S-FAIR method asserts the 8 subjects + A2(b) (no floor >25,
  VICTORY ends at 25). ROUTE_AUDIT_MAX_DEPTH = RUN_FINAL_DEPTH (A5). Ripple fixes: BUILD_PYRO_FIRE_TURNS_PER_FLOOR 5.5,
  ROUTE_CALM_EV_DISCOUNT_MAX 0.40, ROUTE_TRAJECTORY_XP_PACE_MAX 3.20 (named overrides in BalanceConfig + authority doc);
  LevelGeneratorSnapshotTest digest re-baselined (reproduced on 2 JVM runs).
- **CP4 gates:** core:test 458, only StoryBarkTest; lwjgl3 compiles; balanceSim exit 0. Moved: stalls TACTICAL 175->167,
  11 TACTICAL seeds reach the first boss (0 clear), S-BUILD sample 49->37 (>=30, WATCH), dominant share 0.47->0.59 (WAIV),
  S-HEAVY sample 1108->733. Summary kept as scratchpad `cp4-summary.txt`.
- **CP5 handover:** R-CREDITS v2 = `BalanceSchema.creditResults()` (one line per region, mean income / mean
  `neededBundlePricePerFloor(d)`; `regionTierUpgradeDestination(region)`), formula `GameMath.neededBundlePricePerFloor`.
  SECTION 25: `CREDITS_V2_RATIO_MIN/MAX`, `CREDITS_NEEDED_CONSUMABLES_PER_FLOOR`, `SHOP_MACHINES_PER_FLOOR` 1,
  `SHOP_NODE_MACHINE_COUNT` 2, `SHOP_NODE_PRICE_MULTIPLIER` 0.80, `SIM_SHOP_*`. Knobs: SHOP_CREDITS_PER_POWER_POINT 15.5,
  SHOP_DEPTH_PRICE_SCALE 0.0. `ShopContext(…, PlayerBuild, float nodePriceMultiplier)`; World `isShopNodeFloor()` (pendingNode
  type SHOP, not staging room) drives machine count + discount; SimWorld.visitFabricator mirrors it (two biased rolls,
  same per-machine seeds). RunLedger `fabricatorVisits / shopPurchases / creditsHeldAtEnd`; PolicySummary
  `offersPerFabricatorVisit / medianUnspentFloorsOfIncomeAtLoss`; RuleKind.SIM_SHOP + `BehavioralBands.shopResults`;
  BalanceSimTest `tacticalSpendsAtTheShop`. Ripple: NODE_SUPPLY_CACHE_CREDITS 1.50, AFFIX_OVERCLOCKED_VAULT_AMMO_BOXES 2.
- **CP5 DELIBERATE TEMPORARY STATE (CP6 resolves):** MYSTERY_EV "weighted table vs combat" WAIVED (0.82x combat at d25 after
  the credit re-pricing). CP6 must delete that waiver when it re-derives R-MYSTERY-EV (F15).
- **CP5 gates:** core:test 458, only StoryBarkTest; lwjgl3 compiles; balanceSim exit 0 (S-SHOP offers 0.64 WAIV, unspent
  0.13 OK). Summary scratchpad `cp5-summary.txt`.
- **CP6 handover:** `route/EventEffect` is ladder currencies only (`weaponLevels`, `perkRank`, `healFraction`,
  `armourFraction`, `creditFloors`, `healthCostFraction`, `creditCostFloors`, `revealNextRegion`, `nextFloorBudgetBonus`,
  gamble; `EventEffect.NONE`); ammoBoxes / grantXp / armourToFull DELETED (+ World.grantAmmoBoxes). `MysteryOutcome.reward()`
  per row; World `applyLadderEffect(effect, seed)` (shared by events + mystery) + `payMysteryOutcomeOnce()` on the exit
  portal (flag `mysteryRewardPaid`, reset in commitRouteNode); SimWorld `payMysteryOutcome()` mirrors it. Pricing:
  `RouteEconomicsModel.ladderRewardPrice / bestChoicePrice / eventCatalogPrice / creditsPerFloorOfIncome /
  combatFloorSupplyPowerPoints`, `NodeEconomics.ladderReward(...)` / `.eventCatalogReward()`; `GameMath.gambleExpectedValue`.
  Rules: R-MYSTERY-EV v2 (`weighted table EV / combat EV` in [1.0, 1.30], `outcome spread / combat EV` >= 0.25; worst-loss
  kept), R-EVENT-VALUE (RuleKind.EVENT_VALUE, `eventValueResults()`, one line per event). EVENT excluded from R-CALM-COST;
  event + CP5 MYSTERY waivers retired; REST waiver expiry -> order 7. `RouteRegistries.events()` latch-registers.
  SECTION 25: EVENT_CREDIT_FLOORS_SMALL .75 / LARGE 3.0, EVENT_STASH_CREDIT_FLOORS 2.0, EVENT_GAMBLE_*, EVENT_TECH_ARMOUR,
  EVENT_SHRINE_HEAL, MYSTERY_REWARD_* (vault .5 + level, trap 1.5, ambush .5, lore .25, malfunction cost .25),
  ROUTE_WEAPON_LEVEL_POWER_POINTS (= perk pick 12), ROUTE_MYSTERY_EV_PREMIUM_MAX .30 (replaced ROUTE_MYSTERY_EV_TOLERANCE),
  ROUTE_MYSTERY_MIN_SPREAD_FRACTION .25, EVENT_BEST_CHOICE_MIN_SUPPLY_RATIO 1.0. Tests updated: Order10 beacon reads credits;
  BalanceAuditTest mystery method asserts both v2 lines + NEW `everyEventOffersAChoiceWorthACombatFloorsSupply`;
  `safestAndDeadliestRoutesSpanARealDifficultyRange` proves the range on net HP drain + XP at horizon 25 (reason in javadoc).
- **CP6 NOT BUILT (judgement, say so in the IMPLEMENTATION NOTE):** the "weapon drop at d..d+1" currency has no EventEffect
  field — no event/outcome in the catalog needs it (VAULT's '=' weapon racks already drop on-curve guns).
- **CP6 gates:** core:test 459, only StoryBarkTest; lwjgl3 compiles; balanceSim exit 0. Summary `cp6-summary.txt`.
- **CP7a handover:** FloorLedger `bossKillTurn / bossKillHealthFraction / bossFightTurns()`; SimWorld records them in the turn
  loop; `PolicySummary.bossFloorsAt(depth)`; `BehavioralBands.bossResults` (subjects "boss depth d TACTICAL clear rate | fight
  turns CoV | median health at the kill", first row min SIM_FIRST_BOSS_TACTICAL_CLEAR_MIN); SECTION 25 `SIM_BOSS_*`; waivers
  looped over BOSS_TABLE_DEPTHS; `SimReport.appendRunReport` (RUN REPORT + BOSS TABLE + shop stats); BalanceSimTest
  `everyBossIsAConsistentFight`. Gates (run before a worker restart, code unchanged since): core:test 459 only StoryBarkTest;
  balanceSim exit 0, all 15 S-BOSS lines WAIV (12 seeds reach depth 5, 0 clear). Living tables regenerated from that build.

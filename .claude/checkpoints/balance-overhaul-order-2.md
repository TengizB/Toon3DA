# CHECKPOINT — balance-overhaul order 2: full floors, fair supply

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-2.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-95bed53c-z8sgd2`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-2.txt` — the spec. Its rules (S1-S10, E1-E7,
C1-C2) and acceptance criteria (A1-A8) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `level` (planners + every generator) + `route` (NodeSupplySpec) + `enemy`
(carriers) + `util` (BalanceConfig SECTION 21 / GameMath / BalanceSchema / BalanceReport) + `sim` +
`render` (route node card).
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` (R-SUPPLY, R-DENSITY replacing
R-SCARCITY / R-SCARCITY-DEPTH / R-HEALDRAIN-DEPTH; route economics re-derived) and
`sim/BalanceSimTest` (S-SUPPLY, re-based S-ECONOMY). No new test files. Existing generator/route
tests UPDATED where behaviour legitimately changes. Both gates every checkpoint.
**STARTED:** 2026-10-01
**BASELINE:** branched from `2207b75`. Mirror build green; 449 tests, 1 pre-existing failure — `StoryBarkTest`
("a joke survived into the deepest strata: bark.depth.core.2", narrative content, not ours). balanceSim green
(36 s; TACTICAL 174/200 runs stall on floor 1 — the navigation waiver). Any OTHER red test is ours.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-2/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-2/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Touch-only, turn-based, cardinal-line ranged rule untouched; no enemy AI changes (SCOPE OUT).
- Every number in `util/BalanceConfig` SECTION 21; every formula in `GameMath` with the
  Formula/Derivation/Edge-cases block; registries via `register()` rows, never a switch.
- No new tile symbols, no new room blueprints, no story lines (C1 strings are route-overlay chrome).
- Supply never reads the player's CURRENT HP/ammo (AS1); the emergency ammo lifeline stays.
- SCOPE OUT: per-weapon-role ammo (order 3), hazards near groups (order 4), dev dials (order 9),
  EVENT/MYSTERY/REST numbers + shop prices (order 6).
- OVERRIDE CLAUSE: each overridden rule named + replaced + tests/docs updated in the SAME
  checkpoint; never delete/@Ignore/weaken a test — update it to the new rule.
- Hand-made `.txt` levels keep their authored pickups.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — SupplyPlanner, SupplyRequest/Plan, SupplySlotProvider, NodeSupplySpec registry (all
      node rows) and SECTION 21 exist, headless and unwired.
      DONE WHEN: they exist, planner-level R-SUPPLY checks green, both gates green.
- [x] **CP2** — ROOMS_MST (LevelGenerator) builds through slots + SupplyPlanner.
      DONE WHEN: its per-room chances and the 0.70 stim path are gone; snapshot test updated; both
      gates green.
- [ ] **CP3** — CAVERN, LINEAR_CORRIDOR, BOSS_ARENA, MED_BAY, EVENT_ROOM, GATE_AIRLOCK build through
      SupplyPlanner; carriers replace drop rolls. Split into three commits:
  - [x] **CP3a** — the six generators build through FloorPopulator.
        DONE WHEN: no generator places a pickup / weapon by its own rule; Cavern's MEDICAL_BAY-only medkit
        path is gone; fast gate green.
  - [ ] **CP3b** — carriers drop on death (independent drop rolls gone); World / SimWorld read planned
        credits + planned weapon offsets/tiers and feed `carriedAmmoTypes`.
        DONE WHEN: build + both gates green.
  - [ ] **CP3c** — R-SUPPLY generator sweep + BalanceReport SUPPLY/DENSITY table; R-SCARCITY /
        R-SCARCITY-DEPTH / R-HEALDRAIN-DEPTH replaced (override clause); dead per-room / per-kill / chip
        constants deleted.
        DONE WHEN: full R-SUPPLY sweep green; table prints; A1, A2 hold; both gates green.
- [ ] **CP4** — group templates, body targets, group placement, shape + first-contact rules in every
      combat generator.
      DONE WHEN: R-DENSITY (bodies/shape/contact) green; A5 partly (contact, count).
- [ ] **CP5** — footprint targets in the three combat generators.
      DONE WHEN: R-DENSITY footprint + density green; A3, A5 hold.
- [ ] **CP6** — node specs drive COMBAT/ELITE/CACHE/SHOP contents; weapon cadence replaces the pity
      rule; route economics re-derived; node card lines drawn.
      DONE WHEN: route economics tests green; A6 holds.
- [ ] **CP7** — sim FLOOR REPORT, S-SUPPLY + re-based S-ECONOMY; all docs updated; reviewer PASS.
      DONE WHEN: A4, A7, A8 hold; both gates green; reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV (from order 1):** `./gradlew` cannot run in the repo (Android Gradle plugin 403). Gate via a
  desktop-only MIRROR build in the session scratchpad: symlinks to core/lwjgl3/assets/gradle*/docs,
  `settings.gradle` = `include 'lwjgl3','core'`, root `build.gradle` with the `buildscript{}` block
  removed and `configure(subprojects)`. Test XML lands in the REAL `core/build/test-results/`.

- **ARCHITECTURE CONTRACT (decided at CP1 — follow it):**
  - `LevelGenConfig` will carry `supplySpec` (route.NodeSupplySpec; null -> `RouteRegistries.nodeSupplySpecs()
    .getOrCombat(null)` i.e. COMBAT), `carriedAmmoTypes` (EnumSet<AmmoType>, null -> expected player's {BULLETS}),
    `weaponCadenceDue`, `targetWalkableTiles` (CP5). `enemyBudgetScale` stays and now carries ONLY extra
    multipliers (affix, event bonus, mystery outcome); node-type threat comes from the spec. Profiles that set
    `EnemyBudgetOverride.calm()/light()/ELITE_BUDGET_SCALE` for their NODE TYPE drop that override in CP6.
  - Pipeline per generator (S1): build layout + decoration + lock-and-key + stairs FIRST, then a shared
    `level/FloorPopulator` (CP2) does: `SupplySlotSurvey.survey(grid, provider)` -> encounter plan + placement
    (CP2/3: the old planner + generator placement; CP4: templates via shared placement) -> `SupplyPlanner.plan`
    -> `SupplyPlanner.place` -> stamp grid symbols, carriers onto spawn points, WeaponSpawnPoints with planned
    offset/tier, credit spawn points, and a `FloorContentReport` attached to the Level for the audit / sim.
  - Every generator implements `SupplySlotProvider` (`supplyRegionAt(col,row)` -> room/chamber id or -1
    corridor; `isLargeSupplyRegion(id)`). Start = 'p', exit = '>' found in the grid by the survey.
  - Carriers: `EnemySpawnPoint` gains a carried drop char; EnemyManager drops it on death (any cause, summons
    never carry); independent per-kill drop rolls deleted (CP3); emergency lifeline + boss-summon lifeline kept.
  - Credits: Level carries planned credit chips; World.seedCreditChips reads them (CP3).
  - Weapons: `WeaponSpawnPoint` gains level offset + tier bonus; World/SimWorld roll via a new
    `WeaponRoller.rollPlannedToSnapshot(base, depth, offset, tierBonus)`; S9 cadence in RunStats
    (`lastNonBossFloorOfferedOnLevelWeapon`) -> `config.weaponCadenceDue`; pity rule deleted (CP6).
- **DECISIONS (not settled by the spec):**
  - E1 x E6 x E7 are numerically inconsistent if spec.threat (1.6) and the region dial (up to 1.25) multiply
    BODIES (ELITE d1: 1.6 x 14 bodies on <=450 tiles = 5.0+/100; region E d25: 1.25 x 25 on <=750 = 4.2 > 4.0).
    So threat + region dial scale the TP CAP; bodies scale by a separate `NodeSupplySpec.bodyScale`
    (ELITE 1.2, CACHE/SHOP 0.35). A6 "ELITE threat ~1.6x" is measured on TP spent.
  - The anchor group (ESCORT / WARBAND) is exempt from GROUP_TP_FRACTION_CAP (a lone Iron Stalker is 374 TP vs an
    ELITE d1 group cap of 252) — the existing "anchor exempt from the per-room cap" exception carried over.
  - S7 spread is COUNT based: a room may hold max(1, floor(0.35 x count)) pickups of a category (with 2 pickups
    on a floor "35%" can only mean one each). Corridor (connector) tiles are not rooms; used only as a fallback.
  - S5 tracking allowance = max(10% of plan, half the largest pickup of the category) — "only rounding to whole
    pickups may move it". Placed must equal the rounded plan (zero unplaced).
  - Heal floor = HEAL value (armour never substitutes). S4 "first half" = half of the farthest keycard-free
    walk distance on the floor.
  - EVENT is not exempt from S4 (spec lists BOSS/REST/REGION_GATE only) -> an event room gains one 'H'.
  - BOSS ammo plan = BossBalance.arenaAmmoBudgetDamage(boss eHP) — the arena did NOT place it before; it will now.
  - Credits: chips = round(creditScale x SUPPLY_CREDIT_CHIPS_PER_FLOOR(5)), each worth the old weighted mean chip
    value (no depth scaling, so R-CREDITS' chip term is unchanged at scale 1.0).
  - ELITE card "RARE+" vs S10 "tier >= region min + 1": built as S10 (region A: UNCOMMON). Flag to owner.
- **CP1 handover:** `SupplyPlanner.plan(SupplyRequest)` / `place(plan, slots, carrierSpawnIndices,
  anchorRegionId, halfDistance, seed)`; `SupplyPlan.plannedValue/roundedValue/count/largestPickupValue`;
  `SupplyPlacement.placedValue/keycardFreeHealFloorValue/earlyHealFloorValue/maximumRoomShare/maximumRoomCount`;
  `SupplySlotSurvey.survey(grid, provider)` (+ walk distances, exit path, `walkableTileCount()`);
  `SupplyPlanner.boxDamageAtDepth/depthOneDamagePerUnit/averageCreditChipValue/armourPickupValue`.
  GameMath: `supplyDamagePerUnitAtDepth, floorAmmoDemandUnits, floorExpectedIncomingDamage, plannedHealValue,
  bodyTargetAtDepth, densityPerHundredTiles, footprintTargetWalkableTiles`. `RouteRegistries.nodeSupplySpecs()`
  (latched, audit-safe). BalanceSchema: `RuleKind.SUPPLY`, `RuleKind.DENSITY`, `supplyPlannerResults()`,
  public `SupplyAuditAccumulator(spec, depth).add(plan, placement)/emit(results, prefix)` — reuse it for the CP3
  generator sweep. Test: `BalanceAuditTest.theSupplyPlannerTracksDemandOnEverySpec`.
- **TEMPORARY:** R-SCARCITY / R-SCARCITY-DEPTH / R-HEALDRAIN-DEPTH still enforced on the old model constants;
  they are REPLACED (override clause) in CP3 when the generator sweep lands.
- **CP2 handover:** `level/FloorPopulator.populate(generatorName, grid, provider, spawnPoints,
  new FloorPopulator.EncounterFacts(anchorSpawnIndex, threatSpent, threatCap, bodyTarget, targetWalkableTiles),
  config, depth, seed)` -> `Result{spawnPoints (carriers set), weaponSpawnPoints (planned), creditSpawnPoints,
  report}`; build the Level from it and call `result.attachTo(level)`. `FloorPopulator.specOf(config)`.
  `Level.getCreditSpawnPoints()` / `getFloorContentReport()` (null on hand levels + staging room).
  `EnemySpawnPoint(spawnChar, col, row, carriedDrop, groupId)` + `withCarriedDrop` / `withGroupId`;
  `WeaponSpawnPoint(col, row, type, planned, levelOffset, tierBonus)`; `EnemyType.fromSpawnChar(char)`;
  `LevelGenConfig.supplySpec / carriedAmmoTypes / weaponCadenceDue`. LevelGenerator implements
  SupplySlotProvider via `buildSupplyRegionMap(rooms)` (room index per interior tile). Populate runs AFTER the
  lock-and-key gate and the stairs. Research-lab reward, vault reward pickups, per-room loot and weapon racks
  deleted from LevelGenerator. Snapshot re-baselined (`70424713...`, stable over two JVM runs).
- **CP2 measurement:** ROOMS_MST ground supply d1 = 1.55 H, 0.9 '+', 2.85 armour, 2.05 ammo boxes (was 8.2 H,
  3.4 armour, 10.4 ammo of dice). Carrier drops are NOT yet live (EnemyManager ignores `carriedDrop` until CP3).
- **TEMPORARY (CP3 resolves):** EnemyManager still rolls independent drops + ignores carriers; World still seeds
  the 3-7 chip roll and rolls weapon spawns the old way (planned offset/tier unused); LevelGenConfig
  medkit/armour per-room chance fields still exist (LinearCorridor / Cache profile read them).
- **CP3a handover:** CavernGenerator / LinearCorridorGenerator implement SupplySlotProvider (cave: chambers =
  regions 0..n-1, cave body cut into 12x9 pockets `LEVEL_GEN_CAVE_SUPPLY_POCKET_*`; linear: side rooms, spine =
  connector). `CavernGenerator(seed, LevelGenConfig)` replaces `(seed, float enemyBudgetScale)` (budget scale 0
  now honoured as "empty"). BossArena / MedBay / EventRoom / GateAirlock take `(seed, LevelGenConfig)` and use
  `PocketSupplyRegions` (6x6, `SPECIAL_ROOM_SUPPLY_POCKET_SIZE`) + `FloorPopulator.specOf(config, <their node
  type>)`. Linear's central-altar 'A'/'H' roll removed (pedestal left empty). Probe d1 ground supply: rooms 1.55 H,
  linear 1.45 H, cavern 1.40 H (cavern was 0.45). GeneratorReachabilityTest / BossArenaGeneratorTest unchanged
  and green; the snapshot test only digests LevelGenerator (unchanged by CP3a).

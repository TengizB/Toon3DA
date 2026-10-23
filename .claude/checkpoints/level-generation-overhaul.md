# CHECKPOINT — level-generation-overhaul (designed props, real doors, specials first)

**PURPOSE:** the live resume record for `.claude/agents/ideas/level-generation-overhaul.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-9351baa4-lpij6d`
**IDEA:** `.claude/agents/ideas/level-generation-overhaul.txt` — the spec. Its rules (R1-R33, G1-G8) and acceptance
criteria (A1-A15) are referred to by number below and are NOT repeated here.
**LANE:** full lane / `level` (+ `tileset` palette request, `world` R27, `hazard`/`level` GroupBarrelPlacer R26,
`route` profiles R31, `util` constants/GameMath/BalanceConfig/BalanceSchema), agent + docs.
**TESTS:** balance-touching (deletes one GRANDFATHERED BalanceConfig field, changes the floors the audits sweep) —
NO new tests and no new test methods; the existing R-SUPPLY / R-DENSITY / R-HAZARD / C-ROOM / C-GENERATOR rules and
S-bands are the gate. Both gates (`test` + `balanceSim`) on CP6-CP8 and CP14. Existing tests updated only where
behaviour legitimately changed (idea TESTS list). LevelQualityAudit is an instrument (no assertion, no Gradle task).
**STARTED:** 2026-10-23
**BASELINE:** branched from `0a7d596`; build/test baseline recorded under NOTES (CP0).

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `level-generation-overhaul/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `level-generation-overhaul/CPn:`, **with this file's tick
in the same commit as the work**. Never more than one checkpoint uncommitted; split a big one
into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Build/test only from the run-game mirror (`/tmp/toon3d-runtime/mirror`), never root `./gradlew`.
- No new tile symbols, sprites or room types (SCOPE OUT). Only existing symbols in `docs/tile-symbols.txt`.
- Never a switch/if-chain over room types (R32): behaviour comes from `RoomBlueprint` rows; anchors are per-constant enum bodies.
- Every random draw from a seeded stream; dressing uses its own salted stream (R16, R33).
- Constants in `LevelGenConstants`; formulas in `GameMath` with the comment block.
- Footprint targets, encounter budgets, supply, first contact, keycard selection: unchanged balance contracts.
- No balance number moves except HAZARD_GROUP_BARREL_RADIUS_TILES 2->3 if R-HAZARD goes red (CHANGELOG line).
- Naming convention: no abbreviations in identifiers.
- Hand-made `.txt` levels untouched.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — DONE WHEN: `.claude/agents/level-generation-engineer.md` exists with the TECHNICAL NOTES sections;
      `game-level-designer.md` deleted; CLAUDE.md table, orchestrator.md, commands/implement.md, run-game SKILL.md,
      docs/level-design-context.txt, ideas/_TEMPLATE.txt name the new agent; A1 grep clean; no Java changed.
- [x] **CP2** — DONE WHEN: `level/LevelQualityAudit` runs headless on the UNCHANGED generators, writes
      `core/build/reports/level-audit/summary.txt`; BEFORE figures (I1-I16) recorded below; run command in the agent
      file; build + test green.
- [x] **CP3** — DONE WHEN: DoorSocket, DoorwayRules (validate + isInDoorClearance), CorridorRouter exist (headless);
      World.tryMakeMachineCandidate (R27) and GroupBarrelPlacer (R26) use clearance / lane rule; doorway/router
      constants in LevelGenConstants; generators not wired; test green (R-HAZARD incl.).
- [x] **CP4** — DONE WHEN: PropAnchor, PropKit/PropKitRow, RoomSizeWindow, LayoutRoom, RoomDresser exist; every
      RoomBlueprints row has window + kit (+ relaxed); STANDARD has the five regular kits; RoomBlueprint gains
      sizeWindow()/kit()/signatureSymbol() additively; unwired; build + test green.
- [ ] **CP5** — DONE WHEN: SpecialRoomPlanner + GameMath.specialRoomCountForFootprint exist; tileset palette-request
      helper adds kit demands + 'T'/'E' pins; unwired; build + test green.
- [ ] **CP6** — DONE WHEN: ROOMS_MST runs pipeline A (G1); old selection/corridor/placeDoors/prop helpers/LARGE/
      retired rolls gone from LevelGenerator; buildRoomForBridge re-pointed; symbolsStampedInIsolation re-pointed;
      snapshot test re-baselined (dated); RoomBlueprintBuildDemandTest updated; audit A3-A8 pass for ROOMS_MST;
      test + balanceSim green.
- [ ] **CP7** — DONE WHEN: LINEAR runs pipeline A with G2; STORAGE_BAY/REACTOR/bridge/native props/roulette gone;
      eligible/selectionWeight + weight/chance/depth/cap constants deleted; RoomBlueprintRegistryTest updated;
      LEVEL_GEN_PROP_CHANCE + GRANDFATHERED entry deleted (CHANGELOG); audit passes LINEAR; both gates green.
- [ ] **CP8** — DONE WHEN: CAVERN runs G3; audit passes CAVERN; both gates green.
- [ ] **CP9** — DONE WHEN: BOSS_ARENA conforms to G4; audit passes; BossArenaGeneratorTest + fast gate green.
- [ ] **CP10** — DONE WHEN: MED_BAY conforms to G5; audit passes; fast gate green.
- [ ] **CP11** — DONE WHEN: EVENT_ROOM conforms to G6; audit passes; fast gate green.
- [ ] **CP12** — DONE WHEN: GATE_AIRLOCK conforms to G7; audit passes; fast gate green.
- [ ] **CP13** — DONE WHEN: StartGameLevelGenerator conforms to G8; audit start-room row passes; fast gate green.
- [ ] **CP14** — DONE WHEN: RetiredWallDressing + retired rolls deleted; LevelGenConfig scatter flags /
      enableLargeRooms / enableServerRooms deleted, preferredRegularKit set by Cache/Elite profiles (R31); dead
      constants gone (A9); full audit A3-A8 across ALL generators, AFTER figures recorded beside BEFORE; both gates
      green; A14 captures taken.
- [ ] **CP15** — DONE WHEN: docs listed in the idea's CP15 match the code; agent invariant list matches final rules.
- [ ] **CP16** — DONE WHEN: reviewer PASS (or PASS WITH FIXES applied); STATUS: IMPLEMENTED + date.

## NOTES CARRIED FORWARD

- CP0: idea STATUS set to IN PROGRESS in this commit.
- CP0 baseline: mirror `:core:build` = 481 tests, 1 pre-existing failure `narrative.StoryBarkTest` (known,
  bark.depth.core.2). Any OTHER failing test after this point is ours.
- CP1: the new agent file is written, but agent definitions load at session start - a session that began before CP1
  cannot dispatch `level-generation-engineer` by name; such a session dispatches `java-architect` (or works on the
  main thread) with "follow .claude/agents/level-generation-engineer.md" in the contract.
- CP2: `LevelLayoutReport` (level/) is attached to every Level by `Level.attachLayoutReport` (package-private) and read
  by the audit through `Level.getLayoutReport()`. Generators report rooms as `RoomRecord`s with SHELL bounds; from CP6
  on they must fill `kitId`, `dense`, `sockets`, `laneBand` (else the audit computes its own lane), `plannedSpecials`,
  `repairCarves`, `doorwayValidation(flankFailures, demotions)`. `FloorContentReport.pluggedPassagesCleared()` counts
  `FloorPopulator.clearPluggedPassages` removals (I7/I14). Audit I13 is a stub (`signatureSymbolOf` returns null) until
  CP4 adds `RoomBlueprint.signatureSymbol()` — wire it there. Bespoke generators + start room report no layout yet
  (their CPs 9-13 add it); for them I2/I4/I5/I8/I10-I12 read 0 and mean nothing.
- CP2 BEFORE (200 seeds x depths 1,5,10,15,20,25; full text in commit; key lines):
  ROOMS_MST  I1 1101 | I2 48564 | I3 17870 | I4 27591 | I5 242 | I6 0 | I7 13197 | I8 0 | I9 9317 misplaced T, 3 floors
             no T | I10 4.51 specials/floor, 690 floors w/ duplicates | I11 server_room 1754 vs medical_bay 190, depth-1
             observatory/gore/atmo 0 (max dev 321%) | I12 regular max 0.556, 2030 rooms over cap, 636 floors >1 tight |
             I14 4556 repair carves | I15 12.9 doors/floor | I16 84 floors whose 'T' is not a terminal (C6 confirmed).
  LINEAR     I1 727 | I2 12240 | I3 8786 | I4 12136 | I5 87 | I7 9880 | I9 1696 / 176 floors no T | I10 3.45/floor,
             12 floors <2, 406 dup | I12 regular max 0.35 | I14 3900 | I15 2.61
  CAVERN     I2 68115 (chambers unwalled) | I3 9020 | I7 1292 | I8 5515 | I9 5515 / 410 floors no T | I10 0.27/floor,
             1178 floors <2 | I11 only medical_bay 197 + armory 124 | I14 2760 | I15 0.00 doors
  BOSS 1 door/floor clean; MED_BAY 2 non-flush T/floor; EVENT 1, GATE 1 non-flush T/floor; START clean.
- CP3: `LayoutRoom` was pulled forward from CP4 (validate needs rooms): SHELL bounds, `role`
  (`LevelLayoutReport.Role`), `blueprintId`, `sockets()`, `primarySocket()`, `sideOf(c,r)` (null on corners).
  `DoorSocket(c, r, Side)`: Side NORTH/SOUTH/EAST/WEST carries the OUTWARD step; outer/inner tile helpers; Leaf DOOR/OPEN.
  `DoorwayRules`: `canPlaceSocket(room, c, r, gridW, gridH)` (R10 incl. per-room cap, entrance 2) ->
  `openSocket(grid, room, c, r)` carves 'l' and registers -> `hangDoor(grid, socket)`; `passesFlankRule`;
  `validate(grid, rooms)` -> Validation{flankFailures, demotions, shellGaps} (demotes failing doors to 'l');
  `isInDoorClearance(grid,c,r)` doors only / `(grid, rooms, c, r)` + sockets / `(Level, c, r)` + reported sockets;
  `markClearance(mask, socket)`; `roomFootprintMask` / `nearRoomMask(rooms,w,h,dist)` for the router.
  `CorridorRouter.route(grid, roomMask, hugMask, fromC, fromR, toC, toR, routeSeed)` -> path incl. endpoints (empty =
  none; endpoints may be inside roomMask, nothing else); jitter is hashed from routeSeed (no Random draw).
  `CorridorRouter.widen(grid, path, nearRoomMask(dist 1), LEVEL_GEN_WIDE_HALL_TAPER_TILES)` -> spine tiles.
  The hug mask = tiles orthogonally next to a footprint: build as nearRoomMask(dist 1) minus roomFootprintMask
  (Chebyshev; acceptable — diagonal hug costs too).
  `SupplySlotProvider.isKeepClearTile(c,r)` (default false) is the R26 seam: generators with a lane model override it
  (lane bands + socket clearance); `SupplySlotSurvey.isKeepClear` reads it; GroupBarrelPlacer rejects grid-door
  clearance + keep-clear tiles and orders flush candidates first (after its unchanged shuffle).
- CP3 DIVERGENCE: LevelGeneratorSnapshotTest re-baselined HERE too (barrels moved off door-adjacent tiles), dated
  note in the test; CP6 re-baselines again for the layout. R-HAZARD stayed green -> barrel radius NOT touched.
- CP4: prop engine is in, UNWIRED. Entry points for CP6:
  `new RoomDresser(grid, rooms, floorSeed)` AFTER every socket + door leaf is final (it precomputes clearance);
  `dresser.dress(room, kit, relaxedKit)` per room (entrance: skip — R22); `dresser.hasUsableTerminal()`,
  `dresser.redress(room, RoomBlueprints.officeKit())`, `dresser.dressExtraRow(room, officeKit().rows().get(0))` for R20;
  `dresser.kitDemand()` (symbol -> legacy sprite, R29); `dresser.isKeepClear(c,r)` = clearance + lane bands (wire into
  the generator's `SupplySlotProvider.isKeepClearTile`). After dress, `room.toRecord()` carries kit/dense/sockets/band.
  Architecture hooks the dresser reads: `room.exportFeature(name, tiles)` (FEATURE_LAB_ALCOVE "alcove", observatory
  "core"/"spokes"/"annulus"/"shell_band" — constants in RoomBlueprints) and `room.claimCentre(tiles)` (HUB becomes the
  ring; the observatory core must be claimed so its ';' row can land on the FEATURE core tile).
  Blueprint data: `sizeWindow()` (null on entrance/standard -> `RoomBlueprints.regularWindow()`), `kit()`,
  `relaxedKit()` (server_room, salvage_bay), `regularKits()` (STANDARD: office/storeroom/maintenance/aftermath/bare),
  `signatureSymbol()`. Entrance window = LEVEL_GEN_ENTRANCE_MIN/MAX_DIM (5/7).
  Decisions the spec did not settle: ROOWS racks fill every legal tile, and the plug check (which now also refuses
  turning a solid NEIGHBOUR into a plug) breaks them into pairs "TT.TT"; a 1x3 power-plant block therefore places 2.
  Cryo "both long walls when H >= 6" = row 2 '&' @ENTRY_WALL SPACED whenShortSideAtLeast(6). Observatory '|' pylons go on
  the "spokes" feature = tiles FLANKING the spoke lines (architecture must export it that way).
  DEFERRED: LEVEL_GEN_SERVER_LOCKER_RATIO stays 0.30 (old racks still read it); set it to 0.35 (spec 65/35) at CP6.
  Scratch harness (not committed): /tmp/claude-0/sbx/.../DressSandbox.java dresses every kit in a min-size room.

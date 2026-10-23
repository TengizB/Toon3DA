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
- [x] **CP5** — DONE WHEN: SpecialRoomPlanner + GameMath.specialRoomCountForFootprint exist; tileset palette-request
      helper adds kit demands + 'T'/'E' pins; unwired; build + test green.
- [x] **CP6** — (CP6a a08f413 + this tick; balanceSim SR3 0.99 accepted by the owner, idea D5) DONE WHEN: ROOMS_MST runs pipeline A (G1); old selection/corridor/placeDoors/prop helpers/LARGE/
      retired rolls gone from LevelGenerator; buildRoomForBridge re-pointed; symbolsStampedInIsolation re-pointed;
      snapshot test re-baselined (dated); RoomBlueprintBuildDemandTest updated; audit A3-A8 pass for ROOMS_MST;
      test + balanceSim green.
- [x] **CP7** — (balanceSim: SR3 0.98 under D5, S-HEAVY NAIVE 0.31 under D6) DONE WHEN: LINEAR runs pipeline A with G2; STORAGE_BAY/REACTOR/bridge/native props/roulette gone;
      eligible/selectionWeight + weight/chance/depth/cap constants deleted; RoomBlueprintRegistryTest updated;
      LEVEL_GEN_PROP_CHANCE + GRANDFATHERED entry deleted (CHANGELOG); audit passes LINEAR; both gates green.
- [x] **CP8** — (balanceSim: S-HEAVY 0.30+ under D6, SR3 0.97 under D5) DONE WHEN: CAVERN runs G3; audit passes CAVERN; both gates green.
- [x] **CP9** — DONE WHEN: BOSS_ARENA conforms to G4; audit passes; BossArenaGeneratorTest + fast gate green.
- [x] **CP10** — DONE WHEN: MED_BAY conforms to G5; audit passes; fast gate green.
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
- CP5: `SpecialRoomPlanner.plan(floorSeed, targetWalkableTiles)` -> List<RoomBlueprint> (uniform, no replacement,
  floor seed ^ SPECIAL_ROOM_SEED_SALT; call ONCE per floor with the RAW floor seed, outside the FootprintPlanner
  lambda; pass the natural walkable size when the target is 0). `SpecialRoomPlanner.size(bp, attemptRandom, scale,
  atMinimum)`, `ids(planned)`, `isSpecial(id)`, `specialPool(registry)`. `GameMath.specialRoomCountForFootprint(target)`.
  `tileset.FloorSymbolPins.pin(requestBuilder, dresser.kitDemand())` adds the kit demand + 'T'/'E' pins; it replaces
  the generator's ad-hoc barrel pin. Constants: SPECIAL_ROOM_SEED_SALT, LEVEL_GEN_WALKABLE_TILES_PER_SPECIAL_ROOM,
  LEVEL_GEN_SPECIAL_ROOMS_MIN/MAX, LEVEL_GEN_SPECIAL_PLACEMENT_CANDIDATES.
- CP6a (ROOMS_MST rebuilt; NOT ticked): audit 200 seeds = A3-A8 all 0 for ROOMS_MST (I11 max dev 11%, depth-1 every
  type present, I12 regular max 0.120 / special 0.214 / dense 0.265, I15 23.3 doors/floor). Fast gate green (only the
  baseline StoryBarkTest). LevelGeneratorSnapshotTest re-baselined (dated). RoomBlueprintBuildDemandTest,
  RoomBlueprintRegistryTest and TilesetExtensibilityAcceptanceTest updated HERE (CP6 already removed the depth gates /
  weight tiers / RoomType they pinned — plan said CP7). balanceSim: ONE red band, S-RELIC SR3 (LEGENDARY/COMMON mean
  depth) 0.99 (3.17 vs 3.20); base 0a7d596 was exactly 1.00 (3.12 vs 3.12) against a 1.00 lower bound — a coin-flip
  band on two near-equal 120-run means. Per the idea's BALANCE NOTES the checkpoint STOPS and the owner decides.
  Shared engine now: `LayoutSupport` (connect / hangDoors / dressHalls / repairConnectivity / supplyRegionMap / report /
  RouteMasks), `RoomBuildContext(grid, room, random, config, rooms)` architecture facade, `LevelGenerator.
  buildRoomForBridge(grid, l, b, r, t, blueprint, random, config, floorSeed)` (LINEAR's delegated rooms; deleted at
  CP7). Router has a turn cost (LEVEL_GEN_ROUTE_TURN_COST 3) — staircases broke widened halls. Dresser RUN/SPACED now
  re-places the winning run on a clean room (an undo had left plugs). Streams use GameMath.mixSeed (consecutive
  seeds were correlated). LevelGenConfig.preferredRegularKit exists (profiles set it at CP14).
  Eligibility still exists for LINEAR's roulette until CP7, as "fits the window minimum" (no depth, no cap).
- CP6 closed: owner decision D5 (idea file) — SR3 0.99 accepted as noise; report SR3 at every later gate. Every
  other balanceSim band green at CP6 (S-NAVIGATION, S-TWIST, S-RELIC SR1/SR2/SR4 incl.).
- CP7 (LINEAR_CORRIDOR on pipeline A, G2): `LinearCorridorGenerator` rewritten — a SpineRun (head/tail + slots), the
  entrance at the head slot, the planned specials at evenly spaced spine fractions on alternating sides
  (`slotsNearest` fallback, then one retry at the window minimum), regular side rooms at SIDE_ROOM_CHANCE with a
  spine-facing socket, the plaza carved outside nearRoomMask, stairs in the room farthest down the spine, legacy base
  palette + FloorSymbolPins, layout report attached. Linear-only architecture variants: `RoomBuildContext.
  withLinearInteriorVariants()` (four-pillars / avenue / altar for rooms >= 5x5). BARE kit pillars now count(2,4).
  `LayoutSupport.dressRooms` carries the R20 terminal guarantee; `tryHallColumn` is shared.
  DELETED: RoomBlueprint.eligible/selectionWeight, LambdaRoomBlueprint eligibility/weight, fitsWindow, RoomContext,
  RoomBlueprintRegistry.selectable, LevelGenerator.buildRoomForBridge, ~300 dead LevelGenConstants lines (every
  selection weight, room *_CHANCE gate, *_MIN_DEPTH, *_MAX cap, per-room prop chance, SPINE_STORAGE_*/REACTOR_*,
  LARGE_MODIFIER_*, MIN_SPECIAL_ROOMS, SERVER_ROOM_CHANCE...), BalanceConfig.LEVEL_GEN_PROP_CHANCE + its
  GRANDFATHERED name (CHANGELOG line in game-balance-authority; procedural-level-generation.txt table line removed).
  KEPT ON PURPOSE (not this idea's): the four dead enemy-mix LEVEL_GEN_*_THRESHOLD constants and the dead BalanceConfig
  aliases LEVEL_GEN_MAX_ENEMIES_PER_ROOM / LEVEL_GEN_DEPTH_ENEMY_BONUS_MAX / _UPGRADE_CHANCE (encounter layer, dead
  before CP0). LEVEL_GEN_CAVE_* dead chances were deleted here; CavernGenerator still reads LEVEL_GEN_CRYO_CHANCE etc.
  (CP8 removes them).
  FIX found by the audit: GroupBarrelPlacer.takesATerminalsLastAccess — a group barrel may not take a 'T''s last
  walkable side (one LINEAR floor, seed 113 depth 25, had T boxed in by wall/wall/W/E). Snapshot re-baselined (dated,
  3434936d..., stable over two runs). Audit `--explain` now also reports I9 no-usable-terminal floors.
  Audit 200 seeds x 6 depths: LINEAR and ROOMS_MST every counter I1-I9/I13/I14/I16 = 0; LINEAR 2.00 specials/floor,
  I11 max deviation 9%, fill regular max 0.120 / special 0.214 / dense 0.265, tight rooms 4.4%, 5.60 doors/floor;
  ROOMS_MST unchanged from CP6 (2.83/floor, 11%, 23.30 doors/floor).
  Fast gate: 481 tests, only the baseline StoryBarkTest red. balanceSim: SR3 0.98 (3.33 vs 3.40; D5) and S-HEAVY
  "NAIVE heavies answered" 0.31 > 0.30 (CP6 0.27): every extra answer is "killed first" (97 -> 112), NAIVE avoid/guard
  stay ~0, TACTICAL 0.91 — the long spine sightlines let NAIVE shoot a heavy before it lands. Owner delegated the call;
  decision D6 (idea file): accept as a layout effect, no band moves, report S-HEAVY + SR3 at every later gate.
  NEXT (CP8): CAVERN on G3 — walled chambers with sockets, specials first, formations/debris rules, ChamberType typing
  dropped, 'T' out of the cave body; then delete the cave constants it stops reading. Both gates.
- CP8 (CAVERN on G3): `CavernGenerator` rewritten on the shared engine. CA cave unchanged (noise/smooth/region audit);
  then `placeChamber(grid, rooms, w, h, role, id, requireOverlap[, maximumSockets])` — up to CAVE_CHAMBER_TRIES samples
  inside the footprint window (fields windowLeft/Bottom/Right/Top, set in initNoise, 2 tiles in from the border), clear
  of other chambers (ROOM_MARGIN) and of tunnels (tunnelMask +1), needing >= 1 SocketPlan (a legal R10 socket whose
  straight tunnel hits cave floor within CAVE_SOCKET_TUNNEL_MAX without passing within 1 of another chamber); the first
  SPECIAL_PLACEMENT_CANDIDATES valid positions compete, most interior cave floor wins. Specials first (no overlap
  requirement; window-minimum retry), then the entrance (overlap, then without), moved to rooms[0]; then regulars.
  `rejoinSeveredCave` (R9): a stranded open region is erased if tiny and chamber-free, else a CorridorRouter bypass
  around every footprint (multi-source BFS picks the ends). Then hangDoors, architecture, 'p' at the entrance centre,
  biomes (floor moods + walkable decals, never on a socket outer tile), dressRooms (+R20), FORMATIONS + DEBRIS
  (`placeSolid` = plug check incl. neighbours + reachability count; `isCaveSolidTile` = plain floor outside
  footprint/tunnel/spawn radius/clearance with open width >= 3 on both axes), validate, repair, stairs (farthest plain
  floor, never a socket/tunnel/clearance tile). ChamberType, decorate*, retired wall rolls, stalagmite and per-tile
  prop rolls are GONE; 'T' is out of DERELICT_NEST's scatter.
  DIVERGENCE from G3 "0..2 regular chambers": ONE regular chamber is always required (R20 terminal guarantee — a
  special's kit may place a terminal but cannot promise one), >= LEVEL_GEN_CAVE_TERMINAL_CHAMBER_MIN_DIM (5) square with
  ONE socket (3-socket 4x4 chambers left the OFFICE kit no legal wall). Rolled regulars 0..2 still apply (max(1, roll)).
  New constants: CAVE_REGULAR_CHAMBERS_MAX, CAVE_SOCKETS_MIN/MAX, CAVE_SOCKET_TUNNEL_MAX, CAVE_BYPASS_SEED_SALT,
  CAVE_TERMINAL_CHAMBER_MIN_DIM, CAVE_FORMATION_* (MIN_OPEN 20, PER_BIOME_MAX 3, GROTTO_MULTIPLIER 2, CLUSTER_MAX 3,
  SOCKET_DISTANCE 3), CAVE_DEBRIS_CLUSTER_MAX 3, CAVE_SOLID_MIN_PASSAGE_WIDTH 3, CAVE_SOLID_CLUSTER_TRIES 60. Deleted:
  CAVE_CHAMBER_MIN/MAX, CAVE_STALAGMITE_CHANCE, CAVE_BIOME_WALL/PROP_CHANCE, CAVE_BIOME_PROP_MIN_OPEN,
  CAVE_BIOME_CLUSTER_SIZE and the MEDICAL_/ARMORY_/CRYO_/CONTAINMENT_ chamber-typing block. `LayoutSupport.openRegion`
  added. Audit I9 explain now lists the floor's rooms.
  Audit 200 seeds x 6 depths, CAVERN (BEFORE in CP2 notes: I2 69508, I3 9180, I8 5694, I9 5694/406, I14 2764...):
  every counter I1-I9/I13/I14/I16 = 0; 4.00 specials/floor (planner N from the cave's footprint), I11 max deviation 7%
  (depth-1 slice 22%, every type present), fill regular max 0.120 / special 0.200 / dense 0.245, tight 8.4%, 10.39
  doors/floor. Fast gate: only the baseline StoryBarkTest; snapshot (ROOMS_MST) unchanged. balanceSim: S-HEAVY NAIVE
  0.30 (402 committed; prints 0.30, fails above it — D6) and SR3 0.97 (3.36 vs 3.47 — D5; it has drifted 1.00 / 0.99 /
  0.98 / 0.97 over CP5-CP8: REPORT THE TREND to the owner at the end). S-NAVIGATION NAIVE stalled 0.05 (in band).
  NEXT (CP9): BOSS_ARENA per G4 — the arena door passes R14, cover/barrels outside its clearance (verify, do not
  re-tune), antechamber STAGING (1 'L' flush on each side wall, outside the start->door lane); attach a layout report.
- CP9 (BOSS_ARENA, G4): verified, not re-tuned — the door sits in the arena's bottom wall row (R14 flank holds) and
  cover/barrels keep BOSS_ARENA_KEEP_CLEAR_RADIUS (3) from the door mouth / centre lane, wider than the clearance zone
  (audit I1/I3 = 0 over 1200 floors). Added: `dressStaging` (one 'L' flush on each antechamber side wall at the middle
  row, drawn every attempt, no random draw) and a layout report — the arena (rectangle widened by
  BOSS_ARENA_ALCOVE_DEPTH so the alcoves are interior, id "boss_arena") and the antechamber (id "staging"), both
  REGULAR, both holding the one door as their socket. Lanes are REPORTED, not computed by the audit: the arena's lane
  is the door mouth's keep-clear approach (the cover scatter is the fight's design and stays — G4), the antechamber's
  is the start tile's centre-column walk to the door +-1. Audit 200 x 6: every counter 0, fill max 0.057.
  BossArenaGeneratorTest + fast gate green (only the baseline StoryBarkTest). No balance input moved (no gate needed).
  NEXT (CP10): MED_BAY per G5 — approach ends at a DOOR socket in the ward's left wall; each pod alcove opens through
  one OPEN socket; fixed flush CLINIC kit ('&' beds SPACED on alcove far walls, 'I' in two ward corners, 'T' flush on
  ward top + bottom walls, '%' flush in one corner); 'W' auto-doc = CENTRE feature, lane routed around it; zero enemies.
- CP10 (MED_BAY, G5): the ward and its pod bays are LayoutRooms. The approach now ends at wardLeft - 2, so the ward's
  left shell column survives and the clinic entrance is a DOOR socket at (wardLeft - 1, centerRow). Each bay ("clinic_pods",
  role SPECIAL so its 2 beds fit the 0.22 cap on a 4x4 bay) opens through ONE open socket centred on it in the wall row
  it shares with the ward (both rooms hold the socket). The auto-doc 'W' is `ward.claimCentre`, so the dresser's lanes
  end on the ring around it. `MedBayGenerator.clinicKit()` ("clinic": 'T' SIDE_WALL x2 — the ward's top/bottom walls,
  'I' CORNER x2, '%' CORNER x1, 'm' ANY_WALL x1) and `clinicPodsKit()` ('&' BACK_WALL SPACED x2) go through the shared
  RoomDresser. The only random draws (alcove side, second bay) are unchanged. Zero enemies. Layout report attached.
  Audit 200 x 6: every counter 0 (ward fill 0.038, bays <= 0.125). Fast gate green (only the baseline StoryBarkTest).
  NEXT (CP11): EVENT_ROOM per G6 — approach ends at a DOOR socket into the room; 'W' station = CENTRE feature; 'T' and
  '%' flush to walls; corpse/oil decals optional.

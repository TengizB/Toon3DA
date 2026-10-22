# CHECKPOINT — generated-wall-art order 2: the switch (region sets live, old walls gone)

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-wall-art-order-2.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-317849b4-yc0zjt`
**IDEA:** `.claude/agents/ideas/generated-wall-art-order-2.txt` — the spec (series rules W1-W14, the symbol table and
P1-P9 in `generated-wall-art.txt`; THE METHOD + manifest contract in `docs/wall-art-creation-guide.txt` 6/7/12/13).
Its rules (R1-R7) and acceptance criteria (A1-A9) are referred to by number below and are NOT repeated here.
**LANE:** full lane / `tileset` + `level` + `route` + `world` + `render` (+ `render/tilesetgfx`) + `util` + docs.
**TESTS:** none new — not balance-bearing (series W10, Testing Policy). Existing tileset/level tests that encode the old
catalog or allocator are UPDATED in the same commit (listed below), never deleted or weakened. Both balance gates run.
**STARTED:** 2026-10-22
**BASELINE:** branched from `c163c98` (master after order 1). Build via the run-game mirror only.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-wall-art-order-2/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-wall-art-order-2/CPn:`, **with this file's tick
in the same commit as the work**. Never more than one checkpoint uncommitted; split a big one into `CPna`/`CPnb`.
Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Build: `.claude/skills/run-game/setup.sh` then `cd /tmp/toon3d-runtime/mirror && ./gradlew -q :core:build
  :lwjgl3:compileJava` — never the repo-root gradlew.
- R1/W7: the 2D game is untouched. The generators keep EVERY random draw; only the char written changes. Proved by a
  throwaway script comparing walkable mask + every FIXED symbol vs the baseline build (output pasted below).
- Never regenerate the order-1 art; its manifest (guide section 12) is the contract.
- No allocation in render(); constants in the matching *Constants file; formulas in GameMath (comment blocks).
- register() rows, never a switch; enums carry per-constant data.
- No new tests; never delete/weaken an existing one. Do not start order 3.
- CP5 + CP6: send the owner run-game captures under cool_steel (hall run, medical room, door, light flicker, 8+ tile
  view) and continue (not a hard stop).
- The old wall layer is deleted completely in CP7 (W12). Regions B-E alias set A.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — GameMath wallSetSeed / wallVariantIndex / wallMipLevel; WallGroup, WallSlot, RegionWallSet,
      RegionWallSetRegistry, RegionWallSets.bootstrap() (A real from the manifest ids, B-E alias A); the slot table in
      TilesetConstants. DONE WHEN: they exist and the mirror build is green.
- [x] **CP2** — LevelFinisher exists, every generator's final Level goes through it with family stamping + the region
      palette; no generator writes a wall picture char. DONE WHEN: build green and R1 script shows no difference.
- [x] **CP3** — route wallSetId + World resolution + LevelGenConfig fields; hall placement P4-P9. DONE WHEN: build
      green, R1 holds.
- [x] **CP4** — RegionWallAtlas loads per floor; walls, doors, columns draw from the atlas (base pass). DONE WHEN:
      run-game shows set A walls/doors/columns.
- [x] **CP5** — variants + LOD + glow + animation; captures A4-A6 sent.
- [x] **CP6** — floor/ceiling from the set's surfaces; capture sent.
- [x] **CP7** — Part 3 deletion; tests updated; build green; A3 grep clean; A7.
- [x] **CP8a** — docs (A9): wall-art guide 6.4/7/13/15/17, art-direction 5/6/7/17, environment-tileset §15 +
      superseded notes, tile-symbols, wall-renderer-guide, procedural-level-generation, route-map-system, CLAUDE.md;
      run-game `textures` step; leak check done.
- [ ] **CP8b** — probe (A8) recorded; R1 re-run on the final code; both balance gates; reviewer; STATUS IMPLEMENTED.

## UPDATED TESTS (W10)

- CP2 `level/LevelGeneratorSnapshotTest` — EXPECTED_DIGEST re-baselined (wall symbols change by design; R1 proven
  separately), stable across two JVM runs.
- CP2 `level/RoomBlueprintBuildDemandTest` — unchanged and green: the blueprint demands lost their wall rows
  (build() no longer stamps a wall picture), so "stamped flexible symbols == declared demand" still holds.
- CP3 `level/LevelGeneratorSnapshotTest` — re-baselined again for the hall placement (f00a581d...).
- CP3 `route/Order10RegistriesTest.everyRegionCarriesCompleteThemingData` — asserts each region's `wallSetId()` is
  registered (was: non-empty `wallPalette()`).
- CP7 `tileset/TilesetRegistriesTest` — wall/column catalog checks merged into
  `everyWallSymbolAndTheColumnResolveInEveryRegionWallSet` (every Level.isWall char is a slot and resolves inside
  every set; 'P' a column; 2 columns + 4 doors per set); duplicate/unknown/bootstrap probes use prop ids; the
  height/occluder probe checks a crate instead of the deleted wall. (481 tests: two old methods merged into one.)
- CP7 `tileset/LevelPaletteTest` — legacy maps walls through set A (spot ids), props unchanged;
  `regionPaletteRebindsOnlyTheWallsAndTheColumn`, `regionPaletteBindsEveryWallSymbolToAFaceOfItsOwnSet` (THE guard
  "every wall symbol resolves", now per region set) replace the two base-wall tests; catalog coverage skips set ids.
- CP7 `tileset/SymbolAllocatorTest` — every rule (reservation, freed symbol, containment, anti-repetition, coherence,
  capacity, clash) re-targeted from WALL slots to SOLID_PROP slots; `allocatorNeverBindsAWallOrTheColumn` and
  `registryWithoutAnyWallOrColumnSpriteAllocates` replace the base-wall tests; themed registry holds themed props.
- CP7 `tileset/SymbolBudgetTest` — 'x' FIXED with no sprite; wall chars == slot table, never reassignable; 'P' a
  column outside the budget; unmodifiable/populated checks on SOLID_PROP.
- CP7 `tileset/TilesetExtensibilityAcceptanceTest` — RECIPE A for walls: one alias row reaches a palette; hand levels
  read set A. `tileset/LevelPaletteDistinctSpriteIdsTest`, `render/tilesetgfx/TextureGeneratorRegistryTest` — the
  legacy catalog-coverage checks subtract set A's faces; synthetic ids renamed to set-A ids.

## NOTES CARRIED FORWARD

**CP0:** baseline c163c98. Baseline test run (mirror): 482 tests, 1 pre-existing failure `narrative.StoryBarkTest`
(bark.depth.core.2) — not ours. R1 harness (throwaway, scratchpad only): `scratchpad/r1src/r1/R1Dump.java` + `r1.sh
CLASSES_DIR OUT SEEDS` hashes (walls -> '#', every other char kept) + enemy/weapon spawns + heal/event stations +
credit count per (generator, seed*7919+13, depth 1..25) for the 7 GeneratorIds plus 200 START rooms (35 200 cases,
~2.5 min). Baseline classes copied to `scratchpad/baseline-classes`; baseline output `scratchpad/r1-baseline.txt`
(deterministic: re-run identical). If the scratchpad is lost: `git worktree add --detach /tmp/base c163c98`, build it,
use its core/build/classes/java/main.

**CP1:** `GameMath.wallSetSeed(levelSeed)`, `wallVariantIndex(column,row,side,seed,count)` (side 0 W,1 E,2 S,3 N),
`wallMipLevel(lineHeight, faceSize, bias, maxMip)`, plus `wallGlowDistanceAlpha` and `wallGlowFrameIndex` for CP5.
tileset: `WallGroup` (idToken, baseVariantCount 3/2, slots), `WallSlot` (idToken, focal, fallback), `WallSlotTable`
(parses `TilesetConstants.WALL_SLOT_TABLE`; `groupOf/slotOf/symbolFor/isWallSymbol/symbols()`), `RegionWallSet`
(`of(id, letter, breachBase, breachStep)` builds the stable ids of guide 12; `alias(id, borrowsId)`; `resolve(group,
slot)` = R3 fallback; `doorIdFor(char)`, `columnIds()`, file names), `RegionWallSetRegistry` (+`resolve` follows
aliases), `RegionWallSets.bootstrap()/registry()/resolveOrDefault(id)` (A real, B-E alias A), `RegionPaletteBuilder
.build(set, basePalette)` (walls -> slot sprite variant 0, 'P' -> first column, everything else copied),
`LevelPalette.wallSetId()`. Placement constants `WALL_SET_*` in LevelGenConstants. DECISION (spec said "reading the
shipped manifests' ids"): the headless registry DERIVES the ids from the stable naming convention (guide 12) — tileset
cannot read assets headlessly (tests have no Gdx.files); the render-side RegionWallAtlas (CP4) cross-checks every id
of the set against the manifest and falls back to set A on a mismatch (R7). DECISION: the column ('P') picture is
chosen per FLOOR by the renderer (wallVariantIndex over the set's two columns), not per tile, so a row of columns
reads as one architecture.

**CP2:** R1 PROVEN: `r1-cp2.txt` vs `r1-baseline.txt` — 35 200 / 35 200 cases identical, 0 errors (walkable mask +
every fixed symbol + spawns + stations + credits; 7 generators x 200 seeds x depths 1-25 + 200 start rooms).
`level/LevelFinisher` (`request(grid, seed).room(interiorL,B,R,T, group)...floorGroup(G).cavern().config(cfg)
.basePalette(p).finish()` -> LevelPalette; dresses the grid in place): P8 normalise + exposure, P2/P3 ownership
(first room whose interior a wall faces), family BASE, accents (facing-entrance first, then random, P9 via
`FocalSpacing`, Chebyshev), RegionPaletteBuilder. Every generator + the start room + every fallback builds through it
(MED_BAY MEDICAL, gate SECURITY, event + boss SIGNATURE, cavern `.cavern()`, chambers/rooms with their groups;
LinearCorridor's local STORAGE_BAY/REACTOR name STORAGE/MACHINERY via `RoomType.localWallGroup`).
`RoomBlueprint.wallGroup()` per registration row (family table of the master). `LevelGenConfig.wallSetId` +
`floorIndexInRegion` exist (World does not set them yet — CP3). Blueprint demands lost their wall rows.
THE R1 MECHANISM: generators keep every random draw of the retired wall passes and write no picture; a few old draws
were conditioned on "is this wall still plain 'x'" (rust, gore, the old bulkhead), so `level/RetiredWallDressing`
(per-generator ledger, shared with the LevelGenerator bridge via `buildRoomForBridge(..., ledger)`) records which
walls a retired pass would have repainted (`paint(grid,c,r,sym)` writes 'x'; `markDressed`; `isPlainWall`). Retired
passes are renamed `retired*Rolls`; the per-type room theming switch became `RETIRED_ROOM_WALL_PAINT_CHANCE` (summed
rates); `stampGenericVarietyAccents` sizes its two nextInt draws from `RETIRED_ACCENT_RESERVATIONS` (what the old
demands reserved) so the demands could lose their wall rows. The bulkhead pass had no RNG -> deleted. Cavern biome
wall lists -> `retiredWallDrawSpan` ints; MedBay's accent painting deleted (no RNG). Geometry writes of a picture
char (research-lab force-field barrier 'F', observatory shell '`') now write 'x'.
TEMPORARY STATE (until CP4): EnvironmentTextureSet skips sprite ids without a generator and WallRenderer draws
unrealized wall/column ids with the white pixel (walls render flat-shaded). Both marked TEMPORARY in code; CP4
replaces the path, CP7 deletes it.

**CP3:** R1 PROVEN again (`r1-cp3.txt`: 35 200 / 35 200 identical). `RegionAmbience.wallSetId()` replaces
`wallPalette()`; `RouteMapConstants.REGION_*_WALL_SET` (= TilesetConstants ids) replace `REGION_*_WALL_PALETTE`;
`RouteRegistries.requireRegisteredWallSet` fails startup on an unregistered set. `World.applyFloorWallSet(config)`
(in `buildFloorForNode`, beside applyFloorSupplyInputs) sets `config.wallSetId` from the route plan's region for the
current depth (like the mood) and `floorIndexInRegion = depth - region.firstDepth()`; fallback outer_facility + log
"WallSet". The route-less dev path (`pickLevelGenerator`) passes no config -> set A. LevelFinisher now runs P4 door
frames (in-line flanking walls, any owner) + rhythm (runs = maximal straight lines of rhythm walls sharing an open
side; rhythm walls = HALL, or the special floor's group walls no room claims), P5 lights (latest allowed base tile
before a 7-stretch closes, preferring a tile flanked by base; P9 wins when nothing is allowed), P6 markers (near 'p'
then '>' then anywhere), P7 breach (base + index x step, +0.04 in caverns) and P6 SERVICE/VENT (6-10%), clusters
1-3 along a line. MEASURED (40 seeds/generator, exposed walls): ROOMS_MST hall base 46% + room bases ~26% (= ~72%
base, inside the guide's 65-75%), STRUCTURE 16-18% (P4's literal "every 4th" + frames — above the guide's rough
"~10% structure/light"; record in guide 7 at CP8), LIGHT ~4%, SERVICE+VENT ~4%; breach 0% on a region's first floor,
~6% of exposed (12% of hall base) on its last; caverns 80% base. Scratchpad tools: `dumpsrc/r1/Dump.java` (print a
floor), `Stats.java` (fractions).

**CP4:** `render/tilesetgfx/RegionWallAtlas.loadForFloor(palette.wallSetId())` (R7: set -> set A -> null = white-pixel
flat walls; validates version/size/mips and every id the set names) parses the manifest into dense face arrays
(`faceIndex(id)`, `sourceX/Y(mip, face)`, `isGlowing`, `animateMode`, `glowFrameCount/X/Y/Seconds`, `floorPath()`,
`ceilingPath()`). World builds it in buildLevelDependentResources and disposes it in rebuildForLevel + dispose;
`WallRenderer.setRegionWallAtlas(atlas, floorSeed)` replaces setEnvironmentTextureSet (props keep the set) and fills
char -> face tables (`wallFaceStart/Count/Indices`, `doorFaceByChar`, `columnFace` picked per floor). Old door
textures are no longer loaded (generators still in the file until CP7). Base pass draws srcX = face origin + texel
column at mip 0, variant 0 (CP5 enables variants/mips/glow). New RenderConstants WALL_* (face size, max mip, mip bias,
glow fade, some-animate 1 in 6, pick sides, phase buckets). Verified in run-game (seed 7, first node, cool_steel):
set A hall base + structure ribs on a generated floor. run-game opening for a generated floor: standard opening,
`13:tap:644:100;14:seed:7;14.5:set:startRoomChoiceResolved:true;15:call:.:onDescentRequested;17:engage;
23:tap:640:450;24.5:tap:640:450;26:tap:644:80;27:tap:644:100;28:tap:915:667` (briefing answer + ack, relic LEAVE IT).

**CP5:** WallRenderer: BASE variant per face (`side` 0 W / 1 E / 2 S / 3 N from the DDA step), mip per stripe
(`wallMipLevel(lineHeight)`, faceSize >> mip, both atlases share packing), glow per column (`assignGlow`: static glow at
the face's mip rect; at mip 0 an animated face picks its frame via `GameMath.wallGlowFrameIndex` with a per-face phase
bucket; "some" = `wallVariantIndex(col,row,side, seed ^ face, 6) == 0`; alpha = distance fade x
`level.getLightMultiplier()`), Phase 4 glow pass after the muzzle pass (NORMAL blend, white x alpha, one bind; a door
column shows only the panel's glow). `RegionWallAtlas.glowCycleSeconds(face)`. Capture driver gained
`lookat:SYMBOL:INDEX:BACK[:SIDE]` (SKILL.md row). Captures (seed 7, first node): hall at 9 tiles (x#105) and 8 tiles
(x#35), medical room (M#12 from 6), door (d#0 from 3), research room (t#0), flicker = LIGHT 49,27 west face
(`lookat:S:2:4:W`), turn burst at x#105; A5 under amber_hot: lamp + cyan seams keep colour, panels take the grade.
Verified: forcing ONE_IN=1 animates every lamp; the 1-in-6 hash measured 2383/14400. Sent to the owner (not a stop).

**CP6:** `render/SurfaceMaterials` rewritten: two lazy loaders per registered wall set id (aliases included) —
`floorMaterialId(setId)` = "<id>.floor", `ceilingMaterialId(setId)` = "<id>.ceiling" — reading the resolved set's PNG,
flipped to the generator layout (row 0 = south), R7 fallback to set A. The ten procedural material generators and
`ArtDirection.MATERIAL_ID_*` are DELETED here (Part 3 item done early, because the switch replaces them). Moods bind
their region's set surfaces in LightingMoods (cool_steel A, clinical_white B, amber_hot C, corrupted_violet D,
core_white_violet E — B-E alias A today). FloorCeilingRenderer untouched. Captured + sent (hall 9 tiles, medical, door).

**CP7:** DELETED: every WallRenderer generator/loader/door texture/opacity record (3311 -> ~970 lines) and
`registerTextureGenerators`; `assets/textures/lab/lab_wall_*.jpg`, `lab_door_*.jpg`, `assets/textures/wall.png`
(barrel_radioactive.png stays; assets/assets.txt is build-generated, untracked); tileset wall/column registrations +
SPRITE_ID constants (VARIETY_ONLY_SPRITE_IDS kept, now empty), SymbolBudget base wall + WALL/COLUMN slots,
SymbolAllocator base-wall branch, LevelPalettes.generatedWithBaseWall (legacy() = props/decals + set A via
RegionPaletteBuilder), TilesetConstants FIXED_WALL_* / BASE_WALL_ALTERNATE / FLEXIBLE_WALL / FLEXIBLE_COLUMN;
119 RenderConstants orphaned by the generators (LAB_* paths, *_WALL_TEXTURE_SIZE / seeds / feature counts, column
texture constants, wall-only STELLAR colour rows) + their comment blocks. SymbolCategories derives WALL from the slot
table and COLUMN from 'P'. DECISION: GameBalance PALETTE_* knobs are KEPT — the prop/decal allocator still reads all
four (no wall-only knob existed), and GameBalance is balance-gated; no balance number changed. A3: grep for deleted
ids/files/constants/wallPalette over core + assets + skills is clean (docs come in CP8). A7 in run-game: depth 9
(region B alias, cavern), depth 5 boss arena (SIGNATURE lockers), depth 21 (region E), start room — all set A, no
missing texture, no fallback log. Hand-made levels: legacy palette covered by LevelPaletteTest (LevelLoader path is
dev-only).

**CP8a:** LEAK (A8): 20 floor transitions (rooms, cavern, corridor, boss arena, med bay) - `Texture.getNumManagedTextures()`
stays 11-29 depending on floor/overlay state, no growth (a per-floor atlas leak would add +2 per floor). Docs updated
(see ledger). Probe A/B (base worktree /tmp/base-wt at c163c98, runtime /tmp/base-rt) running; results go in CP8b.

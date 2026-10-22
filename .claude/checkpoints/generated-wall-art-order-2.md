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
- [ ] **CP3** — route wallSetId + World resolution + LevelGenConfig fields; hall placement P4-P9. DONE WHEN: build
      green, R1 holds.
- [ ] **CP4** — RegionWallAtlas loads per floor; walls, doors, columns draw from the atlas (base pass). DONE WHEN:
      run-game shows set A walls/doors/columns.
- [ ] **CP5** — variants + LOD + glow + animation; captures A4-A6 sent.
- [ ] **CP6** — floor/ceiling from the set's surfaces; capture sent.
- [ ] **CP7** — Part 3 deletion; tests updated; build green; A3 grep clean; A7.
- [ ] **CP8** — probe + leak check (A8); docs (A9); reviewer gate; STATUS IMPLEMENTED.

## UPDATED TESTS (W10)

- CP2 `level/LevelGeneratorSnapshotTest` — EXPECTED_DIGEST re-baselined (wall symbols change by design; R1 proven
  separately), stable across two JVM runs.
- CP2 `level/RoomBlueprintBuildDemandTest` — unchanged and green: the blueprint demands lost their wall rows
  (build() no longer stamps a wall picture), so "stamped flexible symbols == declared demand" still holds.

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

# CHECKPOINT — Visual Identity 1: atmosphere (palette, moods, grade)

**PURPOSE:** the live resume record for `.claude/agents/ideas/visual-identity-order-1.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-1c332c6a-z0q18l`
**IDEA:** `.claude/agents/ideas/visual-identity-order-1.txt` — the spec. Its rules (R1-R13) and acceptance
criteria (A1-A14) are referred to by number below and are NOT repeated here.
**LANE:** full lane / `util` + `render` + `world` (+ docs).
**TESTS:** none — not balance-bearing (CLAUDE.md Testing Policy). Verified by build + run; `./gradlew test`
must stay at baseline.
**STARTED:** 2026-10-19
**BASELINE:** branched from `b0aa2e5`. Mirror build compiles; `test` = 480 tests, **1 pre-existing failure**:
`narrative.StoryBarkTest` ("a joke survived into the deepest strata: bark.depth.core.2", LEVITY vs LORE).
Not ours, not in scope — "green" for this work means "no failure beyond that one".

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `visual-identity-order-1/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `visual-identity-order-1/CPn:`, **with this file's
tick in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- No allocations in any render()/per-frame path; every Disposable disposed (vignette texture/batch).
- Every value in a constants file (ArtDirection / RenderConstants); every formula in GameMath with the comment block.
- register() rows, never a switch on mood/material id.
- Emissive exemption (R4) — glows, bars, icons, hazards, portal never graded.
- Grade is multiplicative and luminance-neutral (R3); never raise the shade floor.
- Floor worker threads read only immutable per-floor mood data (R13).
- Full identifier names (CLAUDE.md naming table). No new tests. No new persistence keys.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — palette constants + GameMath formulas.
      DONE WHEN: util/ArtDirection has every TECHNICAL NOTES constant; GameMath.atmosphereFogFactor and
      applyRgbShadeToPackedRGBA exist with comment blocks; nothing reads them yet; build green.
- [x] **CP2** — mood + material registries, AtmosphereGrade, World resolution.
      DONE WHEN: LightingMood/Registry/LightingMoods (5 rows + R11), SurfaceMaterialRegistry/SurfaceMaterials
      (10 generators), AtmosphereGrade exist; World bootstraps, resolves mood (R11/R12), logs
      "Atmosphere mood=<id>"; build + test at baseline.
- [x] **CP3** — WallRenderer grades walls, doors, columns (R1). DONE WHEN: build green; A3 walls part.
- [x] **CP4** — FloorCeilingRenderer materials, horizon, per-row grade (R7, R8, R13). DONE WHEN: A3 holds; build green.
- [x] **CP5** — Prop/ShopMachine/Enemy bodies graded (R1, R2, R4, R5). DONE WHEN: A5-A7 hold; build green.
- [x] **CP6** — weapon tint (R6) + ViewVignetteRenderer (R10), disposed by World. DONE WHEN: A9, A10; build green.
- [x] **CP7** — five moods checked via dev override (A4, A8, A12); docs (A14). DONE WHEN: build + test at
      baseline; ready for reviewer.
- [ ] **CP8** — reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV (cost time):** `./gradlew` cannot run in the repo (Maven Central 429 on the Android Gradle plugin).
  Gate via the desktop-only MIRROR in the session scratchpad (`<scratchpad>/mirror/`): symlinks to
  core/lwjgl3/assets/gradle/gradlew/gradle.properties/docs/story; `settings.gradle` =
  pluginManagement{googleapis mirror + gradlePluginPortal} + `include 'lwjgl3','core'`; root `build.gradle`
  = the repo's with the `buildscript{}` block deleted, `configure(subprojects)`, and
  `maven { url = "https://maven-central.storage-download.googleapis.com/maven2/" }` FIRST in subprojects
  repositories. Test XML lands in the REAL `core/build/test-results/`.
- **CP1 handover:** `util/ArtDirection` — CombatPalette idiom (`_RED/_GREEN/_BLUE` triples), `MOOD_<ID>_NEAR_R/G/B`,
  `_FAR_R/G/B`, `_FOG_START_TILES/_FOG_END_TILES`, `_HORIZON_FLOOR_PACKED/_HORIZON_CEILING_PACKED`, `_VIGNETTE_ALPHA`,
  `MOOD_ID_*`, `MATERIAL_ID_*`, `GRADE_STRENGTH_ENVIRONMENT/ENEMY/WEAPON`, `WEAPON_LIGHT_MIN/MAX`, `VIGNETTE_*`.
  `GameMath.atmosphereFogFactor(d, start, end)`; `GameMath.applyRgbShadeToPackedRGBA(pixel, r, g, b, boost, dampen,
  pulse)` mirrors the scalar version per channel (red-alert boost caps red shade at 1 before the multiply, as the
  scalar does). `RenderConstants.SURFACE_MATERIAL_SIZE = 64`.
- **CP2 handover:** `LightingMood` getters `nearRed()..farBlue()`, `fogStartTiles()`, `fogEndTiles()`,
  `horizonFloorPacked()`, `horizonCeilingPacked()`, `vignetteAlpha()`, `floorMaterialId()`, `ceilingMaterialId()`.
  `LightingMoods.registry()` / `SurfaceMaterials.registry().getPixels(id)` (cached, SHARED across floors — read-only;
  packed RGBA8888 row-major `row*64+column`, row 0 = south edge, column 0 = west — matches FloorCeilingRenderer's
  existing `fillRows` sampling exactly, so it is a drop-in for `floorTexelsPacked`/`ceilingTexelsPacked`).
  `AtmosphereGrade`: fields `red/green/blue` (render thread only), `evaluate(distanceTiles, strength)`,
  `evaluateWeaponTint(playerTileBrightness)`, static thread-safe `evaluateInto(mood, d, strength, float[] out, off)`.
  World: `resolveLightingMood()` runs just before `new FloorCeilingRenderer(targetLevel)` in the two-arg
  `buildLevelDependentResources`; getters `getAtmosphereGrade()`, `getCurrentLightingMood()`; field `currentLightingMood`.
  R11 enumerates `RegionPlan.defaultPlan().regions()` (RegionAmbienceRegistry has no enumerator).
  Material shapes adjusted to meet R9 (colours kept): panel_grid light slot 20x4; reactor bars 4px; conduit 4px +
  6 blobs r4; circuit 40 routes; flesh vein threshold 1.2 + nucleus lift. Measured R9 averages all in band
  (floors 0.105-0.224, ceilings 0.063-0.176). Hash = `GameMath.combineHash`.
  Still TODO: FLOOR/CEILING_AMBIENT_COLOUR_PACKED still read by FloorCeilingRenderer (CP4 decides).
- **CP3 handover:** `WallRenderer.setAtmosphereGrade(grade)` (World calls it after `new WallRenderer`). Wall columns run
  on WORKER threads: grading uses per-column `WallColumnResult.gradeScratch` + static `AtmosphereGrade.evaluateInto`,
  never `grade.evaluate()` (shared fields). Same rule for every worker-threaded renderer. perpWallDistance is in tiles.
  No display in this container: on-screen A3 check is OWED to a desktop run (CP7 / reviewer note).
- **CP4 handover:** `FloorCeilingRenderer(Level, LightingMood)` (null → fallback); materials from
  `SurfaceMaterials.registry().getPixels`; old placeholder generators and `FLOOR/CEILING_AMBIENT_COLOUR_PACKED`
  deleted (no other readers). Per-row tint table `rowTintTable` (float[rows*3]) rebuilt in ctor + `setRenderResolution`;
  row distance = the same `floorRowDistance` fillRows uses (tiles, player-independent). Only the exact horizon row is
  filled with a flat colour today and it is a FLOOR row → `horizonFloorPacked`; the ceiling horizon colour has no
  slot in this renderer and is consumed by the CP6 vignette (AS8). Prop/Enemy/ShopMachine/WeaponHud renderers are
  render-thread only → `grade.evaluate()` + fields are safe there.
- **CP5 handover:** `setAtmosphereGrade` on PropRenderer / ShopMachineRenderer / EnemyRenderer, plus
  `EnemyRenderer.setLevel(Level)` + `setLightingTime(float)` (fed `facilityTimeSeconds` per frame in World.render).
  Emissive props exempt via existing predicates `Level.isHazardDecal || Level.isStairsDown` (portal '>'), no new flag.
  Enemy: shade × tile brightness (logical tile) → existing min clamp / dormant / BLACKOUT floor → grade 0.5 → status
  and boss tints lerped ON TOP (ungraded). Overlays (bars, tags, icons, auras, shop glow layers) use scalar shade.
  A5-A7 checked by code read only (no display).
- **CP6 handover:** `WeaponHudRenderer.setWeaponTint(r,g,b)` (frame draws only; effect sprites untinted), fed per frame
  in World.render from `atmosphereGrade.evaluateWeaponTint(level.getTileBrightness(playerTile..., facilityTimeSeconds))`.
  `render/ViewVignetteRenderer` (own SpriteBatch(1), 128x72 alpha texture, elliptical radius normalised so corners = 1,
  `GameMath.vignetteAlpha(r, clear)`), World-lifetime field, mood set in `resolveLightingMood`, drawn right before
  `weaponHudRenderer.render` (after impact world effects — particles ARE vignetted), disposed in `World.dispose()`.
  New `ArtDirection.VIGNETTE_SHAKE_MARGIN_WORLD_UNITS = 24f` (max shake constant is 10). A9/A10 code-read only.
- **CP7 handover:** On-screen check DONE via a scratchpad-only capture harness (not committed): a wrapper
  ApplicationListener around `Main` that proxies `Gdx.input` (re-installed every frame — Lwjgl3Application resets
  it) and reports `isPeripheralAvailable(MultitouchScreen)=true` (desktop otherwise never enables touch controls —
  `World.initTouchControls` returns early), scripted taps in world coords, `ScreenUtils.getFrameBufferPixels`
  shots; run under Xvfb :77 + openbox. Title needs TWO taps (first skips the reveal). All five moods captured
  with `-Dtoon3d.forceMood` (via JAVA_TOOL_OPTIONS): five clearly distinct regions (A4). Tuned reactor_grate_hot
  (8-texel period aliased to moire → 16-texel cells, 6-texel bars, lifted gap 0x1A1008, softer chevron), est. R9
  avg ~0.11. NOT seen on screen: an enemy 8+ tiles away (A6), unlit-vs-lit enemy (A7), red alert (A8), FULL HD
  (A12) — code-read only. Pre-existing, out of scope: the mini-map draws a large white unexplored grid (order 4).
  Docs: docs/art-direction.txt (new), wall-renderer-guide, route-map-system, INDEX.md, CLAUDE.md (constants table,
  Where to Find Info, Docs table 20, render order 4b).

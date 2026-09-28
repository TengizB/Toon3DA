# CHECKPOINT — render resolution setting (HD 1280×720 / FULL HD 1920×1080)

**PURPOSE:** the live resume record for the `/feature` "render resolution setting". There is no idea
file; the DESIGN section below is the spec. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `claude/game-rendering-resolution-rlvsxu`
**IDEA:** none (`/feature`) — the spec is the DESIGN section of this file.
**LANE:** full lane — new persisted setting key; `util` + `narrative` (settings model) + `render` +
`world` wiring.
**TESTS:** none new (not balance — CLAUDE.md Testing Policy). Existing `StoryFramingTest` /
`StoryCodexTest` / `StoryLocalizationTest` loop over the settings count and menu geometry and must
stay green; only update them if behaviour legitimately changed.
**STARTED:** 2026-09-28
**BASELINE:** branched from `99eb979`; baseline `./gradlew build` result recorded in NOTES.

## DESIGN (the spec)

**What "resolution" means here.** World units stay 1280×720 (`FitViewport`) — every HUD/menu/touch
layout is authored in them and nothing about that changes. Output already reaches the device at
native pixels, so HUD shapes and text are already sharp. What is actually capped at 1280×720 today is
the **3D view's internal resolution**: `WallRenderer` casts one ray per column
(`WALL_PROJECTION_SCREEN_WIDTH = 1280`, each column `WALL_COLUMN_WIDTH = 1` world unit wide) and
`FloorCeilingRenderer` fills a 320×180 backdrop. The setting raises that:

| `RenderResolution` | ray columns | column width (world units) | floor backdrop |
|---|---|---|---|
| `HD` (default — today's behaviour, byte-identical) | 1280 | 1.0 | 320×180 |
| `FULL_HD` | 1920 | 1280/1920 ≈ 0.667 | 480×270 |

(The floor keeps its ÷4 ratio of the projection resolution.)

**Rules.**
- R1 Player-facing, tap-to-cycle knob **RESOLUTION: HD / FULL HD**, 6th slot of the existing
  settings model (`StorySettings`, same idiom as the EFFECTS knob) → it appears on BOTH the settings
  screen (title + pause) and the codex footer strip automatically.
- R2 Default `HD`. Persisted by ordinal (HD 0, FULL_HD 1) via `StoryProgressStore.saveSettingInt`
  under `RenderConstants.RENDER_RESOLUTION_SETTING_KEY = "render.resolution"`. Additive key, **no
  `SCHEMA_VERSION` bump** (the SFX-knob precedent). Out-of-range stored ordinal → HD.
- R3 Takes effect immediately on the next frame (mid-run or from the title) — no restart.
  Reallocation (floor Pixmap/Texture) happens only on a CHANGE, never per frame; no allocation in
  `render()`.
- R4 Available on every platform (desktop is the dev test bed); it exists for Android.
- R5 At `HD` the rendered image must be identical to before this work.

**Contracts between spokes.**
- C1 `util/RenderResolution` — headless enum (no LibGDX): `HD`, `FULL_HD`;
  `getProjectionColumnCount()`, `getFloorBackdropWidth()`, `getFloorBackdropHeight()`,
  `getLabelStringId()`, `next()`, `fromOrdinal(int)` (clamps to HD). Numbers live in
  `RenderConstants`: `RENDER_RESOLUTION_HD_COLUMNS = 1280`, `RENDER_RESOLUTION_FULL_HD_COLUMNS = 1920`,
  `RENDER_RESOLUTION_MAX_COLUMNS = 1920` (array/batch sizing). Label ids
  `story.codex.setting.resolution.0` = HD, `.1` = FULL HD; name id `story.codex.setting.resolution`
  = RESOLUTION (constant `StoryUiConstants.STORY_CODEX_SETTING_RESOLUTION_ID`).
- C2 `StorySettings.getRenderResolution()` / `cycleRenderResolution()`; slot index 5 in
  `getSettingValueStringId` / `getSettingNameStringId` / `cycleSetting`.
- C3 `WallRenderer` is the SINGLE runtime authority for the projection:
  `setRenderResolution(RenderResolution)`, `getProjectionColumnCount()`, `getColumnWidth()`.
  Every column-space consumer that holds a `WallRenderer` (Prop, Enemy, ShopMachine,
  EnemyAttackEffectSystem) reads those getters each frame — no second copy that can drift. Arrays and
  `SpriteBatch` sizes use `RENDER_RESOLUTION_MAX_COLUMNS`. Any width/extent computed in WORLD units
  and then used as a COLUMN count must be converted (÷ column width) — at HD they were equal, which is
  exactly the latent bug to hunt.
- C4 `ImpactEffectRenderer` / `ImpactEffectSystem` use the projected column as a WORLD X, so they
  project against `Constants.WORLD_WIDTH` and are decoupled from the setting.
- C5 `FloorCeilingRenderer.setRenderResolution(RenderResolution)` rebuilds backdrop Pixmap/Texture/
  backbuffer only on change (dispose old); its row→world-Y mapping uses
  `WORLD_HEIGHT / backdropHeight`, not the ÷4 constant.
- C6 `World.applyStoryAccessibilitySettings()` pushes `storySettings.getRenderResolution()` into
  `WallRenderer` + `FloorCeilingRenderer` (called at construction and on every knob tap already).
- C7 Menu geometry: the settings screen becomes 7 rows (6 knobs + BACK). `STORY_FRAME_MENU_MAX_ROWS`
  6 → 7, `STORY_FRAME_MENU_ROW_GAP` 12 → 8, `STORY_FRAME_MENU_TOP_Y` 540 → 560 (row height stays 68
  — thumb-sized). Codex strip: 6 buttons across the body width.

**Risks.** (1) hidden 1:1 column==world-unit assumptions in sprite widths / health-bar placement
(C3); (2) FULL_HD cost on phones — 1.5× rays, 2.25× floor pixels + texture upload; opt-in by design;
(3) settings screen crowding (C7); (4) codex strip labels at 1/6 width.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `render-resolution-setting/CPn` commit says where work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — dirty tree = unverified work: finish to DONE WHEN and verify, or `git restore`.
5. If this file and git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `render-resolution-setting/CPn:`, with this
file's tick in the same commit. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY

- Touch only; no keyboard; knob is a tap-to-cycle plate like the others.
- No allocations inside `render()`; every Disposable disposed (old floor texture/pixmap on change).
- No hardcoded numbers/strings: constants in `RenderConstants`/`StoryUiConstants`, strings as ids
  in `assets/story/story-strings.properties`.
- `narrative` stays headless (imports only `util.RenderResolution`, which has no LibGDX import).
- Naming conventions (no abbreviations). Y-up; `GameMath.cameraPlaneParameter` for camera param.
- No new tests (not balance).

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — settings model + UI slot (C1, C2, C7, strings).
      DONE WHEN: `RenderResolution` exists; `StorySettings` persists/cycles it in slot 5;
      `STORY_CODEX_SETTING_COUNT == 6`; RESOLUTION row shows HD/FULL HD on the settings screen and
      codex strip; `./gradlew build` (incl. tests) green. Renderers still ignore it (temporary).
- [ ] **CP2** — renderers honour the setting (C3, C4, C5, C6).
      DONE WHEN: no `WALL_PROJECTION_SCREEN_WIDTH`/`WALL_COLUMN_WIDTH`/`FLOOR_BACKDROP_WIDTH|HEIGHT`
      compile-time use remains in the scene renderers' runtime paths; toggling the knob switches
      WallRenderer to 1920 columns and the floor to 480×270 on the next frame; HD path unchanged;
      `./gradlew build` green.
- [ ] **CP3** — docs + review.
      DONE WHEN: `docs/wall-renderer-guide.txt` and `docs/story-ui-system.txt` (settings) describe
      the knob; CLAUDE.md WallRenderer blurb no longer claims a fixed 1280 columns; `reviewer` PASS.

## NOTES CARRIED FORWARD
- **ENV (cost time):** `./gradlew` cannot run in this container — the network policy returns 403 for
  `dl.google.com`, so the Android Gradle plugin on the root buildscript never resolves (same limitation
  recorded in narrative-rework-order-4/6/7). Workaround used for every gate here: a desktop-only
  MIRROR build in the session scratchpad (`settings.gradle` = `include 'lwjgl3','core'`, root
  `build.gradle` with the `buildscript{}` block removed and `configure(subprojects)` instead of
  `subprojects - project(':android')`, everything else symlinked). `core` + `lwjgl3` compile and
  `core:test` runs for real; the `android` module is NOT compiled by it (this feature touches no
  android/ code).
- **BASELINE (99eb979, mirror build):** compiles; 449 tests, **1 pre-existing failure** —
  `StoryBarkTest` "a joke survived into the deepest strata: bark.depth.core.2" (narrative content,
  unrelated to this work; not ours to fix here). Any OTHER red test after CP0 is ours.

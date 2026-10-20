# CHECKPOINT — Visual identity order 3: typography (one procedural pixel font)

**PURPOSE:** the live resume record for `.claude/agents/ideas/visual-identity-order-3.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and continues from the
first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-3cd79d47-hohoc2`
**IDEA:** `.claude/agents/ideas/visual-identity-order-3.txt` — the spec. Rules R1-R9, criteria A1-A10 are referred
to by number and are NOT repeated here.
**LANE:** full lane / `render` (+ `progression/LevelUpOverlayRenderer`, `hud/HudRenderer`, `util/ArtDirection`, `Main`).
**TESTS:** none — not balance-bearing (Testing Policy). `test` must stay at baseline.
**STARTED:** 2026-10-20
**BASELINE:** branched from `15ac0af` (origin/master). Build/test via the run-game mirror
(`/tmp/toon3d-runtime/mirror`). Known pre-existing failure: `narrative.StoryBarkTest` (bark.depth.core.2).

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `visual-identity-order-3/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is unverified work: finish it to DONE WHEN and verify, or restore and redo.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `visual-identity-order-3/CPn:`, with this file's tick in
the same commit. Never `git add -A`. Never commit `assets/game-audio/` or `assets/story-audio/`.

## CONSTRAINTS THIS WORK IS BOUND BY

- No FreeType / TTF / .fnt asset; the font is code (SCOPE OUT).
- No copy changes; no layout changes; R9 fixes are scale constants only (never the wrap count).
- No allocations in render(); every Disposable disposed; atlas disposed exactly once (R6).
- Constants in `ArtDirection`, never hardcoded. Full-word identifiers.
- No new tests. Build/test from the mirror, never `./gradlew` at the repo root.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — ArtDirection typography constants, FontFace, PixelFontGlyphs (full R2 coverage).
      DONE WHEN: they exist, nothing reads them yet, build green.
- [x] **CP2** — GameFonts (atlas, both faces, fallback, R5 metric match + log line, R6 sharing), Main wiring,
      StoryPanelRenderer as pilot. DONE WHEN: a bark shows in the pixel font on screen; build green.
- [x] **CP3** — HUD-layer swaps (HudRenderer, BossHudRenderer, EventTextRenderer, ImpactEffectRenderer,
      EnemyRenderer, FadeOverlayRenderer) with R7 faces. DONE WHEN: A5 holds; build green.
- [x] **CP4** — all remaining swap sites. DONE WHEN: A1 + A2 hold; build green.
- [x] **CP5** — R9 overflow sweep (3 text sizes), docs (step 6). DONE WHEN: A3, A4, A7, A8 seen on screen; build +
      test at baseline (480 tests, 1 pre-existing failure).
- [ ] **CP6** — reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

- **Baseline (CP1):** mirror `:core:build` = only `narrative.StoryBarkTest` fails (pre-existing), ~56 s.
- **CP1:** default font in LibGDX 1.14.1 is `lsans-15` (not Arial): capHeight 11, lineHeight 18, xHeight 8,
  ascent 3, space 4 — measured from its BitmapFontData (scratch program, not committed). So
  `PIXEL_FONT_TARGET_CAP_HEIGHT = 11f`. R2 scan of shipped strings found — · … × ▶ ◀ → ● • ◇ ±; all in the table.
  ▶/◀ are 4x7 (a true 7-tall triangle is 4 wide), not 5x7 as VISUAL DESIGN says.
- **CP2 plan:** glyph metrics in atlas TEXELS; `PixelFontData.setScale` multiplies by base 11/28, so
  `setScale(1)` = cap 11. Consequence: `getScaleX()` is base-inclusive (no project code reads it — grep).
- **CP2:** log line: `pixel font atlas 512x512, REGULAR capHeight at setScale(1) = 11.0, target = 11.0, owns atlas =
  false` (A6, and R6's ownsTexture confirmed false on the BitmapFont(data, region, integer) path). Seen on screen:
  the reprint card's SYSTEM + ORA panels (both StoryPanelRenderer) in the pixel font, no overflow. A live bark did
  NOT appear in the first 20 s of run 1 (no bark fired), so the bark itself is re-checked in the CP5 sweep.
  `setGlyphRegion(missingGlyph)` is called explicitly after construction (BitmapFont.load may skip it).
- **CP3:** HudRenderer gained `ammoDigitsFont` (HEAVY) + face-taking overloads of drawValueRight /
  drawTextWithShadow (the old signatures delegate with the REGULAR font). A5 holds by construction: digits 0-9 are
  all 5 font px wide in both faces (HEAVY 6), advance identical. Seen: HUD left panel + CREDITS in the pixel font,
  HEAVY "0/1" ammo. Swap helper used for every site: scratchpad `swap.py` (deletes the font setFilter calls and the
  `markupEnabled = false` lines — the default is already false).
- **CP4:** A1/A2 hold (grep: `new BitmapFont(` only in GameFonts; no font setFilter left; no markupEnabled left).
  FramingScreenRenderer gained `displayFont` (HEAVY: title + death stroke) with face-taking overloads of
  drawCentered / drawWithShadow. DIVERGENCE (R7): BootCardRenderer's own font draws only the counter and CONTINUE;
  the "status header" (SYSTEM block) is drawn by the shared StoryPanelRenderer, which also draws every bark, so it
  stays REGULAR — a per-panel face is a layout/structure change left to order 4/5. Glyph fixes after on-screen read:
  A round-topped (square A read as R), M/W redrawn with a centre stem (7-wide diagonals blurred into N/H).
  Seen: title, title settings, title archive, HUD, pause, pause settings, inventory (— EMPTY — now renders the em
  dash; before it showed boxes), build page, inventory codex, weapon inspect.
- **CP5 sweep (A3/A4/A7/A8):** story text sizes are NORMAL / LARGE / LARGEST (the spec's SMALL/MEDIUM/LARGE).
  Seen in the pixel font at all three: reprint card, bark, codex list + entry detail, title settings. Seen at
  NORMAL: title, title archive, HUD, pause, pause settings, inventory, build page, weapon inspect, level-up, relic
  offer, route map, exchange (+ reply), death stroke, instance report, damage number (-9997). NOT seen on screen
  (unreachable in a scripted run; same one-line swap): shop, event console, boss bar, enemy name tags.
  A4: route map title "FACILITY NAV — SELECT VECTOR", "◀ CANCEL", "ENGAGE ▶", inventory "— EMPTY —", weapon
  inspect "— NO SPECIAL ABILITIES —" and "LV 1 • COMMON" render the glyphs (baseline: boxes).
  A7: no text crosses a panel/plate edge because of the font, at any size. PRE-EXISTING layout faults (identical on
  the baseline worktree at 15ac0af, so NOT fixed here — layout is SCOPE OUT, for orders 4-6): the death stroke
  overlaps FadeOverlayRenderer's "SUBLEVEL N" banner; level-up rank pips overlap "RANK n / m"; at LARGEST the bark
  drops its third line and the SYSTEM block's lines nearly touch; the title's last status line touches the title.
  A8: die -> card -> CONTINUE -> new run: text correct, no GL/disposed error, GameFonts logs once per session.
- **R8 raised to TYPE_CAPTION (verified to fit on screen):** HUD_LABEL_SCALE 0.95, STORY_CODEX_TAB_COUNT_TEXT_SIZE
  0.95, STORY_CODEX_ROW_SOURCE_TEXT_SIZE 0.9, RouteMapConstants DEPTH_TEXT_SCALE 0.95 / NODE_LABEL_SCALE 0.9 /
  LEGEND_TEXT_SCALE 0.95, LevelUpOverlayRenderer.SCALE_TAP 0.95 -> all 1.0.
  **R8 DEFERRED to orders 4-6** (below 1.0; unreachable screen or would not fit without layout): BOSS_STATE_LABEL_
  FONT_SCALE .62, ENEMY_BLOCK_NUMBER_FONT_SCALE .62, INTENT_DAMAGE_NUMBER_SCALE .62, ENEMY_HP_TEXT_FONT_SCALE .65,
  HUD_LEVEL_TAG_SCALE .66, HudRenderer literal setScale(0.6f) (slot index) and (0.85f) (medkit warn),
  HUD_SLOT_NAME_SCALE .78, BOSS_HP_NAME_FONT_SCALE .80, ENEMY_MATCHUP_WORD_FONT_SCALE .8,
  FRAMING_TOGGLE_LABEL_SCALE .8, SHOP_CARD_DESC_SCALE .80, SHOP_CONFIRM_PROMPT_SCALE .84, SHOP_FOOTER_FLAVOR_SCALE
  .85, SHOP_CONFIRM_LABEL_SCALE .86, SHOP_CARD_NAME_SCALE / SHOP_CARD_TAG_SCALE .92,
  EventChoiceOverlayRenderer.SCALE_TAP .95, ItemWindow 0.80-0.85 x FONT_SCALE literals. (Composite scales built
  from multipliers were not audited.)
- **Capture driver:** added `call:` and `set:` reflection steps (SKILL.md documents them) to reach the route map,
  death, level-up, relic offer. Baseline comparisons ran from a detached worktree at 15ac0af
  (`TOON3D_RUNTIME_DIR=/tmp/toon3d-base`).

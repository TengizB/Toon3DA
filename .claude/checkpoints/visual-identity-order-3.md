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
- [ ] **CP3** — HUD-layer swaps (HudRenderer, BossHudRenderer, EventTextRenderer, ImpactEffectRenderer,
      EnemyRenderer, FadeOverlayRenderer) with R7 faces. DONE WHEN: A5 holds; build green.
- [ ] **CP4** — all remaining swap sites. DONE WHEN: A1 + A2 hold; build green.
- [ ] **CP5** — R9 overflow sweep (3 text sizes), docs (step 6). DONE WHEN: A3, A4, A7, A8 seen on screen; build +
      test at baseline; reviewer PASS.

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

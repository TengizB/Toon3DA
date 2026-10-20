# CHECKPOINT — Visual identity order 4: UI kit + the in-play HUD layer

**PURPOSE:** the live resume record for `.claude/agents/ideas/visual-identity-order-4.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and continues from the
first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-eb246ad6-ypyqrt`
**IDEA:** `.claude/agents/ideas/visual-identity-order-4.txt` — the spec. Rules R1-R15, criteria A1-A11 are referred
to by number and are NOT repeated here.
**LANE:** full lane / `render` + `hud` + `input/touch` + `util` (ArtDirection, TouchConstants, HudConstants).
**TESTS:** none — not balance-bearing (Testing Policy). `test` must stay at baseline.
**STARTED:** 2026-10-20
**BASELINE:** branched from `682fd6d` (origin/master; order 3 is merged there). Build/test via the run-game mirror
(`/tmp/toon3d-runtime/mirror`). Known pre-existing failure: `narrative.StoryBarkTest` (bark.depth.core.2); 480 tests.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `visual-identity-order-4/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is unverified work: finish it to DONE WHEN and verify, or restore and redo.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `visual-identity-order-4/CPn:`, with this file's tick in
the same commit. Never `git add -A`. Never commit `assets/game-audio/` or `assets/story-audio/`.

## CONSTRAINTS THIS WORK IS BOUND BY

- TouchConstants positions/sizes byte-identical (V4/D4); hit testing must not read the chamfer constant.
- No HUD re-layout (panel positions, centre gap, face box, bar sizes stay). Re-skin only.
- No allocations in render(); UiKit has no `new`. Constants in ArtDirection; full-word identifiers.
- Fonts only from GameFonts; never setFilter on a font texture.
- No new tests. Build/test from the mirror, never `./gradlew` at the repo root.
- Before/after shots from a detached worktree of `682fd6d` (`TOON3D_RUNTIME_DIR=/tmp/toon3d-base`).

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — UiKit + PlateState + ArtDirection R7 constants (deliberately unused).
      DONE WHEN: they exist, nothing reads them yet, build green.
- [x] **CP2** — HudRenderer restyled (R9 + R15). DONE WHEN: A2 (HUD), A3 (HUD), A9 seen on run; build green.
- [x] **CP3** — TouchControllerRenderer restyled (R10), constant renamed. DONE WHEN: A4 incl. tap spot-check; build green.
- [x] **CP4** — LevelRenderer (R11) + BossHudRenderer (R12). DONE WHEN: A5 seen on run; build green.
- [x] **CP5** — EventText (R13) + ImpactEffect damage numbers (R14), docs. DONE WHEN: A6, A7, A8 hold; build + test
      at baseline.
- [x] **CP6** — reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

- **CP1:** baseline on the mirror: 480 tests, 1 failure (StoryBarkTest, pre-existing). `UiKit` expects
  `ShapeType.Filled` for EVERY primitive (borders are filled quads, so no Line/Filled churn) and caller-enabled
  blending; pieces never overlap (no double-blend seams). Added beyond R1-R7: `UiKit.fillOctagon`, a full
  `panel(..., chamfer, ..., borderAlpha, borderThickness)` overload, `plate(..., chamfer, ...)` for small plates,
  alpha overloads of `meter`, an aligned/wrapped `text` overload (instead of a GlyphLayout one — a layout bakes its
  colours, so it cannot be shadow-tinted), `GameMath.insetChamfer`, `ArtDirection.UI_DIVIDER_*`,
  `UI_PLATE_DISABLED_ALPHA_SCALE`, `BOSS_HEALTH_SEGMENTS` (in ArtDirection). Shadow offset scales by
  `capHeight / PIXEL_FONT_TARGET_CAP_HEIGHT` (getScaleX includes GameFonts' base scale).
- **CP2:** the HUD is the LEFT panel only (x 0..420, y 0..206): there is no right panel and no face box in
  `hud/HudRenderer` (CLAUDE.md's HUD Layout section is stale), so R9's right-panel / face-box items have nothing
  to restyle — record in the IMPLEMENTATION NOTE. `HUD_PANEL_ALPHA` was 0.88 in code (not 0.82): it now references
  `UI_PANEL_ALPHA_HUD` 0.82 per R7 (slightly more see-through). No low-ammo threshold exists, so ammo digits are
  primary until empty (DAMAGE). Weapon slots (38 tall) cannot hold UI_PLATE_CHAMFER + 14-unit brackets beside two
  TYPE_CAPTION lines: they use `UiKit.plate(..., UI_CHAMFER, UI_BRACKET_LENGTH_COMPACT 7, ...)` and the slot text
  insets moved inward (HUD_SLOT_NUMBER_*, HUD_SLOT_NAME_*, HUD_CLASS_GLYPH_*, HUD_LEVEL_TAG_* insets). All HUD
  scales are TYPE_* now (HUD_LEVEL_TAG_SCALE .66 -> CAPTION, credits LABEL, HP value BODY, ...). Rivets and bevel
  lines are gone; the alert LED is SUIT_AMBER at rest. Capture driver gained `equip:INDEX` (SKILL.md documents
  it). Seen on run: HUD full HP + low HP (pulsing DAMAGE value), plasma equipped (4/4 [0]), NAV OFFLINE plate
  (via `set:minimapOffline:true` + `set:hudRenderer.navOffline:true`, not a real BLACKOUT floor). NOT seen: dead
  state, status icons/debuff list, ground-weapon label, medkit warning.
- **CP3:** `TouchButton.contains` (a plain rect, unchanged) is the only hit test; nothing reads the renamed
  `TOUCH_BUTTON_CHAMFER`. Positions/sizes in TouchConstants untouched. The old rounded fill was a cross of two
  overlapping translucent rects (a lighter inner square artefact) — the UiKit octagon has no overlap. The CAPSULE
  shape (enum kept, no button uses it) is no longer drawn specially. The icon halo is now UI_INK_BLACK
  (TOUCH_ICON_HALO_*), not dim phosphor. First-press glow lerps the rim toward SUIT_AMBER. A4 spot-check (build
  at baseline: 480 tests, the 1 known failure): capture-driver `hittest:X:Y` (new, in SKILL.md) maps all 16 points
  to the right action (USE_MACHINE at 640,360 and INSPECT at 180,200 tested as if visible); real held taps lit the
  matching plate in amber and did the action for FORWARD/BACK/ROTATE x2/STRAFE x2/FIRE ("Whiff!")/RELOAD/SKIP
  ("Turn skipped")/SWITCH (to FIST)/GUARD/HEAL ("NO MEDKITS")/PAUSE (menu)/INVENTORY (overlay)/INSPECT (standing on
  a start-room weapon, card reopened). USE was NOT tapped for real (no fabricator on floor 1) — hit-test only.
- **CP4:** mini-map is ONE Filled pass now: UiKit frame fill (UI_INK_BLACK 0.70), tiles, markers, grid as 1-unit
  filled rects, then the UiKit border + SUIT_AMBER_DIM brackets on top. Every tile/grid rect is clipped to the
  chamfered frame (`GameMath.clipPolygonToHalfPlane`, preallocated scratch arrays) so the corner cuts survive.
  Markers (circles/triangles) are not clipped (a corner-tile marker can graze a cut by < 1 unit). Health/armour
  pickup dots moved to HEALTH_GREEN / ARMOUR_BLUE (semantic); ammo pickup and the barrel prop square keep their
  RenderConstants colours; the MINIMAP_EXIT/PROP/BARREL_DOT/SHOP/ENEMY_THREAT/HEALTH/ARMOR RGB constants were
  deleted (replaced by palette reads) and MINIMAP_FRAME_FILL_ALPHA / WALL_ALPHA / GRID_ALPHA / GRID_LINE_THICKNESS
  added. Player marker (entity/Player.render) SUIT_AMBER. No fog of war exists in LevelRenderer (nothing to keep).
  Boss bar: panel + brackets around a 20-cell DAMAGE meter (`BOSS_HP_PANEL_PADDING` 6), SUIT_AMBER phase tick
  across the panel, name primary HEAVY TYPE_LABEL (inset `BOSS_HP_NAME_INSET_X` 10 to clear the bracket), state
  label TYPE_CAPTION under the panel, intro name TYPE_HEADING (keeps the boss accent), intro epithet secondary
  TYPE_CAPTION, banner TYPE_LABEL primary; every boss text is shadowed. Capture driver gained `bosshud:PCT[:STATE]`,
  `alertall`, `engage` (SKILL.md). Seen on run: boss bar at 60% CHARGING (stand-in boss, floor 1); mini-map on a
  real combat floor with an alerted enemy (red), amber player + weapon dots, health/armour pickups, a tinted
  door. NOT seen: exit (cyan) and shop (gold) markers, keycard doors, barrel Sapper dot, the boss intro letterbox
  text and phase banner. PRE-EXISTING (identical at 682fd6d): the boss bar (x 80..1200, y 572..596) overlaps the
  mini-map; the mini-map's bottom edge overlaps the INVENTORY button — layout is SCOPE OUT.
  Build: 480 tests, the 1 known failure.
- **CP5:** the event text colour TABLE lives in EventTextRenderer (BASE_*), not EventTextSystem (which only
  holds the byte codes) — codes and call sites untouched. COLOR_CREDIT_CYAN (not in R13's list) -> CREDITS_GOLD
  (AS4). Scales moved to the NEAREST type step: legacy event text 1.8 -> TYPE_HEADING (new
  EffectConstants.EVENT_TEXT_FONT_SCALE), PROC 2.0 / SLAM 2.6 -> HEADING, LEGENDARY 3.2 -> DISPLAY, damage numbers
  1.4 -> TYPE_LABEL growing to `TYPE_LABEL * DAMAGE_NUMBER_MAX_EMPHASIS` (2.0, keeps the 3.0 ceiling), heal '+'
  1.6 -> LABEL in HEALTH_GREEN; every `setScale(1f)` restore -> TYPE_CAPTION. A8 grep: no literal reaches
  setScale in the six renderers. Damage numbers: hit UI_TEXT_PRIMARY, kill SUIT_AMBER (the only two categories),
  HEAVY + UiKit.text. DIVERGENCE: NAV OFFLINE text is FACILITY_CYAN, not R9's FACILITY_CYAN_DIM (3.3:1 on
  UI_PANEL; R8 and A9 both say cyan; V8). Docs: art-direction SECTION 13 UI KIT (+ contrast table),
  procedural-vitals-hud SUIT-INK RESKIN, CLAUDE.md hud row + UiKit key-class + HUD_PANEL_ALPHA note. Capture
  driver: `damagenumber:AMOUNT[:true]`, `eventtext:CODE:TEXT`. Seen on run (clinical_white mood, bright wall):
  white/gold/red event text shadowed and readable; kill number "-24!" amber HEAVY shadowed; NAV OFFLINE cyan.
  NOT seen: ability banners (PROC/SLAM/LEGENDARY), heal '+' particles, a plain hit number on screen (it faded
  before the shot). Build + test: 480 tests, the 1 known failure.
- **CP6:** reviewer r1 = PASS, no blocking findings (`visual-identity-order-4.reviewer-r1.md`; its first run hit a
  session limit and was resumed). Applied with this commit: NB-1 (legacy event text -> TYPE_LABEL, under the
  banners again; stale "1.8f" comment fixed), NB-2 (art-direction line: the damage-number growth is continuous on
  purpose), NB-3 (SKILL.md: `-alpha off` when composing sheets — the framebuffer alpha made translucent UI look
  light in the r1 sheets), NB-4 noted (the boss state label lands on the mini-map; layout follow-up), nits 1-4
  (HudRenderer javadoc, the unused HUD_BAR_SEGMENT_GAP removed, CLAUDE.md LevelRenderer/tree/HUD-layout, cx/cy ->
  centerX/centerY in TouchControllerRenderer). Nit 5 (CAPSULE) recorded in the IMPLEMENTATION NOTE.
  STATUS: IMPLEMENTED 2026-10-20.

# CHECKPOINT — Visual identity order 6: facility, story and first impression

**PURPOSE:** the live resume record for `.claude/agents/ideas/visual-identity-order-6.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and continues from the
first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-3bb8f9af-qj8kwk`
**IDEA:** `.claude/agents/ideas/visual-identity-order-6.txt` — the spec. Rules R1-R12, criteria A1-A13 are referred
to by number and are NOT repeated here.
**LANE:** full lane / `render` (story panels, boot card, report, fade, framing, route map, shop, event console) +
`util` (StoryUiConstants, RouteMap palette sourcing) + `android/res` + docs.
**TESTS:** none — not balance-bearing (Testing Policy). `test` must stay at baseline.
**STARTED:** 2026-10-20
**BASELINE:** branched from `fe710e9` (origin/master; order 5 merged there via #315). Build/test via the run-game
mirror (`/tmp/toon3d-runtime/mirror`). Known pre-existing failure: `narrative.StoryBarkTest` (bark.depth.core.2);
480 tests. StoryUiFoundationTest must stay green (A1).

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `visual-identity-order-6/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is unverified work: finish it to DONE WHEN and verify, or restore and redo.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `visual-identity-order-6/CPn:`, with this file's tick in
the same commit. Never `git add -A`. Never commit `assets/game-audio/` or `assets/story-audio/`.

## CONSTRAINTS THIS WORK IS BOUND BY

- Re-skin, not re-layout (SCOPE OUT): no layout, geometry, timing, reveal pacing, lockout or touch target moves; no
  story copy or string id changes.
- Route-map node accents, pips, hazard amber, mystery violet, crests, node icons: pixel-identical (A5).
- Speaker accents stay semantic: ORA amber, Organization cyan, planet red, SYSTEM bone (R1).
- UiKit: every primitive expects ShapeType.Filled; caller enables blending; aligned/wrapped text overload only.
- GameFonts: never restore a font's raw getData().scaleX — restore the logical scale. A GlyphLayout is drawn with the
  font it was built with.
- Type scale: TYPE_* steps only, no raw literal into setScale, nothing below TYPE_CAPTION; no `_DIM` ink for text;
  DAMAGE red text is TYPE_BODY or larger. One scrim per screen.
- No allocations in render(); constants in constant files; full-word identifiers; fonts from GameFonts.
- No new tests. Build/test from the mirror, never `./gradlew` at the repo root.
- Before shots from a detached worktree of `fe710e9` (`TOON3D_RUNTIME_DIR=/tmp/toon3d-base`).

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — R1 accents, StoryUiConstants chrome colours, R2 RouteMapColorPalette sourcing.
      DONE WHEN: every reader compiles; StoryUiFoundationTest green; build + test at baseline.
- [x] **CP2** — StoryPanelRenderer + StoryExchangeRenderer restyled (R3, R4); bark panel inherits.
      DONE WHEN: A1 and A2 hold on run; build green.
- [x] **CP3** — BootCardRenderer, InstanceReportRenderer, FadeOverlayRenderer restyled (R5, R7, R11).
      DONE WHEN: A3 and A7 hold on run; build green.
- [x] **CP4** — FramingScreenRenderer: title, pause, settings, confirmation, terminal RETURN, death stroke (R6).
      DONE WHEN: A4 holds on run; build green.
- [x] **CP5** — RouteMapOverlayRenderer frame/header/text/confirm bar (R8).
      DONE WHEN: A5 holds incl. the same-seed glyph comparison; build green.
- [ ] **CP6** — ShopOverlayRenderer + EventChoiceOverlayRenderer (R9).
      DONE WHEN: A6 holds on run; build green.
- [ ] **CP7** — Android XML (R10, A8), A9/A10 sweep, A11 series check, docs.
      DONE WHEN: build + test at baseline; handover lists A11 follow-ups; ready for reviewer.
- [ ] **CP8** — reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

- **CP0:** order 5's branch is merged into master (#315), so this branch starts at origin/master fe710e9.
  Order-3 R8 deferred sites in this order's classes: shop SHOP_* 0.80-0.92 scales,
  EventChoiceOverlayRenderer.SCALE_TAP .95, FRAMING_TOGGLE_LABEL_SCALE .8 -> TYPE_* here.
- **CP1:** StoryUiConstants: SPEAKER_ACCENT_* = SUIT_AMBER / PLANET_RED / FACILITY_CYAN / UI_TEXT_PRIMARY (R1);
  STORY_BODY = UI_TEXT_PRIMARY; STORY_PANEL_BG = UI_PANEL, STORY_PANEL_ALPHA = UI_PANEL_ALPHA_HUD (same 0.82);
  STORY_BOOT_BACKDROP (and STORY_FRAME_BACKDROP through it) = UI_INK_BLACK; STORY_BOOT_COUNTER = FACILITY_CYAN_DIM
  (R5 - HEAVY makes it large text, 3.6:1 on black); STORY_FRAME_SCRIM_ALPHA = UI_SCRIM_ALPHA (.86 -> .75, the
  one-scrim value); STORY_TITLE_GAME / TAGLINE = UI_TEXT_PRIMARY / SECONDARY; STORY_DEATH_STROKE = UI_TEXT_PRIMARY;
  STORY_REPORT = UI_TEXT_PRIMARY, STORY_REPORT_RECORD = CREDITS_GOLD. Left for the render CPs: the plate fill
  alphas (STORY_EXCHANGE_BUTTON_*, STORY_BOOT_CONTINUE_FILL_ALPHA, STORY_FRAME_MENU_*, STORY_FRAME_RETURN_FILL_ALPHA)
  and STORY_PANEL_BORDER_ALPHA / CORNER_RADIUS / TEXT_SHADOW_* (delete them when the last reader goes). The world dim
  (STORY_EXCHANGE_DIM_*) is unchanged (R4). RouteMapColorPalette (R2): holoCyan/holoDim/creditGold/medGreen =
  FACILITY_CYAN/_DIM/CREDITS_GOLD/HEALTH_GREEN (identical values, so pips/accents that alias them are unchanged),
  scrimNavy/screenBlack = UI_INK_BLACK, steel = UI_PANEL_RAISED, textBright/textDim = UI_TEXT_PRIMARY/SECONDARY.
  Capture driver (dev tooling, SKILL.md at CP7): `seed:N` (fixes the route map), `ending:ENDING_FREE|ENDING_KILL|...`
  (BootCardVariant constant names), `event[:ID]`, `shop` (first vending machine; give credits with
  `call:playerStats:addCredits:N`), `barkspeaker:AI|ORGANIZATION|PLANET` (no SYSTEM barks exist). BEFORE shots:
  /tmp/claude-0/shots/before (base worktree /tmp/claude-0/base at fe710e9, mirror /tmp/toon3d-base). Coordinates:
  NEW OPERATOR needs a printed save (fresh save hides it); confirm PROCEED (470,242) CANCEL (810,242); pause rows
  y 526/450/374/298; REPORT plate (170,148); report BACK (640,138); exchange answers x=640 y~450/362/274, the reply's
  acknowledge at (640,450); relic offer LEAVE IT (644,100). Opening for in-run shots: `2:tap:640:600;3:tap:640:390;
  6:tap:640:600;7:tap:640:144` (on a printed save). Build + test: 480, the 1 known failure.
- **CP2:** StoryPanelRenderer: UiKit.panel (STORY_PANEL_BG = UI_PANEL at STORY_PANEL_ALPHA .82, UI_LINE border) +
  UiKit.brackets; new general overload `drawPanel(..., drawCloseGlyph, bracketRed, bracketGreen, bracketBlue,
  heavySpeakerName)` - the boot card's SYSTEM block passes FACILITY_CYAN brackets + true (CP3). It owns a HEAVY font
  now. Text through UiKit.text (kit shadow); name STORY_NAME_TEXT_SIZE = TYPE_CAPTION (same 1.0). Close X: an IDLE
  plate STORY_BARK_CLOSE_PLATE_SIZE 38 (visual) + UI_TEXT_SECONDARY glyph; touch rect unchanged. Removed
  STORY_PANEL_CORNER_RADIUS (no readers). StoryExchangeRenderer: answers + acknowledge = UiKit.plate IDLE / PRESSED in
  SUIT_AMBER (fill alphas = UI_PANEL_ALPHA_SCREEN), labels UiKit.text, STORY_EXCHANGE_OPTION_TEXT_SIZE 1.4 -> TYPE_LABEL
  (34 chars still fit the 604 plate); STORY_EXCHANGE_BUTTON_BORDER removed. Seen: ORA / Overseer / Planet barks at
  NORMAL, LARGE, LARGEST; exchange IDLE, PRESSED (held), reply + acknowledge IDLE and PRESSED; prompt at LARGE and
  LARGEST (wraps to 3 lines, fits). Taps: answer 1 (reply came), acknowledge (closed), bark X (dismissed). Swipe NOT
  exercised (input code untouched). PRE-EXISTING, same in base (follow-up, not this order): at LARGEST a bark wraps to
  3 lines but a bark holds 2 (the third line is dropped) and the fixed 30-unit line pitch makes the two lines touch;
  the floor-entry event text ("ENTERING: OUTER FACILITY") shows through the bottom of the exchange prompt panel.
  /tmp/claude-0/mkprefs.sh N writes a printed save with story text size N (0/1/2). Build + test: 480, 1 known failure.
- **CP3:** BootCardRenderer: SYSTEM block = StoryPanelRenderer general overload with FACILITY_CYAN brackets + HEAVY
  name (R5); ORA's panel default (amber); counter HEAVY at STORY_BOOT_COUNTER_TEXT_SIZE = TYPE_BODY (was 1.1) in
  FACILITY_CYAN_DIM; CONTINUE = SELECTED SUIT plate (the card's one way on - an IDLE plate has no amber), label
  TYPE_LABEL; removed STORY_BOOT_CONTINUE_CORNER/BORDER. InstanceReportRenderer (R7): kit panel at
  UI_PANEL_ALPHA_SCREEN + FACILITY_CYAN brackets; title = UiKit.screenHeaderTitle (HEAVY HEADING) + NEW UiKit overload
  `screenHeaderRule(shapes, x, y, width, inkRed, inkGreen, inkBlue, alpha)` with a FACILITY_CYAN tick (an overload,
  not a new primitive - CP5/CP6 use it for the shop and event console); values HEAVY; lines STORY_REPORT_TEXT_SIZE =
  TYPE_BODY (was 1.0; the longest autopsy line ~57 chars fits the 780 text width); a FACILITY_CYAN_DIM rule on the
  blank row between stats and autopsy; REPORT + BACK = UiKit.card IDLE in FACILITY_CYAN_DIM ("FACILITY plate").
  STORY_REPORT_TITLE_TEXT_SIZE removed. FadeOverlayRenderer (R11): HEAVY TYPE_HEADING (was 2.2), label "SUBLEVEL"
  FACILITY_CYAN_DIM + number UI_TEXT_PRIMARY at the old anchor; constants EffectConstants.FADE_DEPTH_READOUT_*;
  the black is UI_INK_BLACK. NEW render(camera, alpha, depth, showDepthReadout): World's death beat and MERGE hold
  pass false - the old banner sat on top of THIS BODY DIED in every death (before shots), against A4 and World's own
  "nothing else is drawn" comment (World.java: 2 call sites, paint only). Seen: first-print card, reprint card
  with REPORT, report page, fade readout, stroke alone. Taps: CONTINUE, REPORT (opened), BACK (closed).
- **CP4:** FramingScreenRenderer (R6), ink per screen: TITLE = FACILITY - rows are "FACILITY plates" (UiKit.card inked
  FACILITY_CYAN_DIM IDLE, FACILITY_CYAN when PRESSED/SELECTED, DISABLED card for an unavailable row), row 0
  (BEGIN / CONTINUE DESCENT) SELECTED; title HEAVY TYPE_DISPLAY (3.2 -> 3.5) + a FACILITY_CYAN-ticked
  screenHeaderRule as wide as the title (measured once in setStrings) at titleRuleY(); status lines / tagline
  TYPE_BODY. PAUSE / SETTINGS = SUIT - R1 header (screenHeaderTitle in the HEAVY font, left-aligned on the row
  column at STORY_FRAME_HEADER_TOP_Y, amber-ticked rule the column's width), SUIT plates (IDLE / PRESSED / DISABLED),
  SUIT_AMBER_DIM brackets round header + rows (UI_SCREEN_FRAME_MARGIN; no outer panel - the order-5 rule), values in
  SUIT_AMBER. NEW renderMenu(..., overLaunchScreen): the settings screen opened from the title draws the launch
  blackout (0.94) instead of the 0.75 scrim, or the title showed through (World.java: 1 call site). CONFIRMATION:
  kit panel + brackets + SELECTED PROCEED in the opener's ink (title -> FACILITY_CYAN, pause -> SUIT_AMBER), CANCEL
  IDLE in that ink's plate style; prose STORY_FRAME_CONFIRM_TEXT_SIZE = TYPE_BODY (new constant). RETURN = DISABLED
  FACILITY plate, label UI_TEXT_SECONDARY. DEATH STROKE: HEAVY TYPE_DISPLAY (2.6 -> 3.5), unshadowed. No text on
  these screens is shadowed any more. Constants: STORY_FRAME_MENU_TEXT_SIZE TYPE_LABEL (1.3), VALUE TYPE_BODY (1.1),
  FILL_ALPHA = UI_PANEL_ALPHA_SCREEN; removed STORY_FRAME_MENU_CORNER/BORDER/PRESSED_ALPHA/DISABLED_ALPHA,
  STORY_FRAME_RETURN_FILL_ALPHA, STORY_FRAME_HEADER_TEXT_SIZE, STORY_TEXT_SHADOW_OFFSET/ALPHA,
  STORY_PANEL_BORDER_ALPHA (no readers). Seen: title reveal, title (fresh + printed), NEW OPERATOR confirmation,
  title settings (TEXT cycled NORMAL->LARGE->LARGEST->NORMAL), pause, pause settings, ABANDON confirmation, terminal
  ENDING_FREE card + RETURN, death stroke. Taps: BEGIN DESCENT, NEW OPERATOR, CANCEL (both confirmations), SETTINGS
  (title and pause), TEXT x3, BACK (both), PAUSE, ABANDON RUN, RESUME, RETURN (went to the title). NOT tapped:
  PROCEED (destructive; its rect is the unchanged static isInsideConfirmYes), ARCHIVE, QUIT, CONTINUE DESCENT.
  PRE-EXISTING (follow-up): the title's settings header sits over the launch status lines' rows (now hidden by the
  blackout).
- **CP5:** RouteMapOverlayRenderer (R8), only drawBezel / drawChromeText / the CRT literals changed: console FRAME =
  UiKit.panel border only (fill alpha 0) chamfered UI_CHAMFER, FRAME_BORDER_WIDTH thick, still in trimColor() at
  0.8 (region tint + red-alert pulse keep the legibility fix's contrast) + FACILITY_CYAN UiKit.brackets; the band
  separators are filled rects - the bezel's Line pass is gone. Confirm bar: CANCEL = IDLE facility plate (UiKit.card
  inked holoDim = FACILITY_CYAN_DIM), ENGAGE = DISABLED plate until a node is focused, then SELECTED with its ink
  pulsing holoDim -> holoCyan (ENGAGE_PULSE_SPEED kept; ENGAGE_DISABLED/ENABLED_ALPHA removed); MAP/DETAIL toggle =
  IDLE / SELECTED (map framing). Header HEAVY (new heavyFont field): title + region name at TYPE_BODY (title 1.2,
  region 1.0); TITLE measured with the heavy font. Scales: CONFIRM_LABEL TYPE_BODY (1.1), FRAMING_TOGGLE_LABEL
  TYPE_CAPTION (0.8, the order-3 R8 site), DEPTH/NODE_LABEL/LEGEND -> ArtDirection.TYPE_CAPTION (same 1.0), new
  UAC_STENCIL_TEXT_SCALE TYPE_CAPTION (was a raw 0.7 literal, alpha .55 -> 1). Labels: CANCEL primary, ENGAGE cyan
  armed / secondary unarmed. CRT black literals -> palette.screenBlack (all CRT alphas are 0 anyway). A5: seed 12345
  map before/after (route.png / route_focus.png): diff inside the map band = ONLY the node label text (R2 re-points
  textBright to UI_TEXT_PRIMARY - card text, allowed by R8) and the MAP toggle; icons, pips, conduits, glows, card
  bodies/borders, crest identical (/tmp/claude-0/diff_route*.png). Taps: node (focused, ENGAGE armed), ENGAGE
  (commit animation), MAP toggle (DETAIL framing, SELECTED) and back, CANCEL (a documented no-op from the start
  room - unchanged). Seen: idle, focused, MAP framing. NOT seen: a region-tinted frame other than UNKNOWN SECTOR's
  cyan, red alert, the scanner readout, a twist line.

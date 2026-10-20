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
- [ ] **CP2** — StoryPanelRenderer + StoryExchangeRenderer restyled (R3, R4); bark panel inherits.
      DONE WHEN: A1 and A2 hold on run; build green.
- [ ] **CP3** — BootCardRenderer, InstanceReportRenderer, FadeOverlayRenderer restyled (R5, R7, R11).
      DONE WHEN: A3 and A7 hold on run; build green.
- [ ] **CP4** — FramingScreenRenderer: title, pause, settings, confirmation, terminal RETURN, death stroke (R6).
      DONE WHEN: A4 holds on run; build green.
- [ ] **CP5** — RouteMapOverlayRenderer frame/header/text/confirm bar (R8).
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

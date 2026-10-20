# CHECKPOINT — Visual identity order 5: suit screens (inventory, cards, codex)

**PURPOSE:** the live resume record for `.claude/agents/ideas/visual-identity-order-5.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and continues from the
first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-18db4a27-3ytekw`
**IDEA:** `.claude/agents/ideas/visual-identity-order-5.txt` — the spec. Rules R1-R11, criteria A1-A9 are referred
to by number and are NOT repeated here.
**LANE:** full lane / `render` + `progression` (LevelUpOverlayRenderer) + `util` (ArtDirection, HudConstants,
ItemConstants, StoryUiConstants) + docs.
**TESTS:** none — not balance-bearing (Testing Policy). `test` must stay at baseline.
**STARTED:** 2026-10-20
**BASELINE:** branched from `077152d` (origin/master; order 4 + the two HUD follow-ups b876bcc/73b6c7a are merged
there via #314). Build/test via the run-game mirror (`/tmp/toon3d-runtime/mirror`). Known pre-existing failure:
`narrative.StoryBarkTest` (bark.depth.core.2); 480 tests.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `visual-identity-order-5/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is unverified work: finish it to DONE WHEN and verify, or restore and redo.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `visual-identity-order-5/CPn:`, with this file's tick in
the same commit. Never `git add -A`. Never commit `assets/game-audio/` or `assets/story-audio/`.

## CONSTRAINTS THIS WORK IS BOUND BY

- Re-skin, not re-layout (SCOPE OUT): no panel rect, card position, row height, scroll metric or touch target moves.
- Rarity / tier / tag / status colours are semantic: card border + bracket INK only (R2, D2).
- UiKit: every primitive expects ShapeType.Filled; caller enables blending; aligned/wrapped text overload only.
- Type scale: TYPE_* steps only, no raw literal into setScale; no `_DIM` ink for text (contrast table).
- No allocations in render(); constants in ArtDirection / constant files; full-word identifiers; fonts from GameFonts.
- No new tests. Build/test from the mirror, never `./gradlew` at the repo root.
- Before/after shots from a detached worktree of `077152d` (`TOON3D_RUNTIME_DIR=/tmp/toon3d-base`).

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — UiKit.screenHeader + UiKit.card + UI_PANEL_ALPHA_SCREEN; chrome colour blocks re-pointed.
      DONE WHEN: they exist, every reader compiles, build green.
- [x] **CP2** — InventoryOverlayRenderer, ItemGridPanel, WeaponSlotsPanel, BuildPagePanel restyled.
      DONE WHEN: A1/A2/A3 hold for the inventory on run; build green.
- [x] **CP3** — ItemWindow, AbilityWindow, WeaponInspectOverlayRenderer restyled.
      DONE WHEN: A1/A3 hold for them on run; build green.
- [x] **CP4** — LevelUpOverlayRenderer, RelicOfferOverlayRenderer restyled (R2, R8).
      DONE WHEN: A1/A2/A3 hold on run; build green.
- [x] **CP5** — CodexOverlayRenderer restyled (R9, R7). DONE WHEN: A5 holds on run; build green.
- [x] **CP6** — A4/A6/A7 sweep, docs. DONE WHEN: build + test at baseline; ready for reviewer.
- [ ] **CP7** — reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

- **CP0:** order 4's branch ccr-eb246ad6-ypyqrt is merged into master (PR #314), so this branch starts at
  origin/master 077152d. Order-3 R8 deferred sites inside this order's classes: ItemWindow 0.80-0.85 x FONT_SCALE
  literals (+ any sub-caption scales found in BuildPagePanel / LevelUp / RelicOffer / Codex) move to TYPE_* here.
- **CP1:** `UiKit.card` (border ALWAYS the ink, brackets in the ink when SELECTED, fill = state fill at
  UI_PANEL_ALPHA_SCREEN), `UiKit.screenHeaderTitle(batch, heavyFont, title, x, topY)` + `screenHeaderRule(shapes,
  x, y, width, alpha)` + `screenHeaderRuleY(titleTopY)` (R1 split in two: a ShapeRenderer and a SpriteBatch cannot
  be active at once; title is UNSHADOWED - screen text never sits on the 3D view), `UiKit.newMarker` (R7).
  ArtDirection: UI_PANEL_ALPHA_SCREEN 0.94, UI_SCREEN_HEADER_RULE_GAP, UI_NEW_MARKER_SIZE/EDGE,
  UI_SELECTION_BAR_WIDTH, UI_CHIP_BORDER_THICKNESS/CHAMFER. Re-pointed: ItemConstants INV_SCRIM_ALPHA (.60->.75),
  INV_PANEL_ALPHA (.95->.94), INVENTORY_CREDITS_COLOR (CREDITS_GOLD); HudConstants RELIC_OFFER_SCRIM_ALPHA
  (.78->.75), RELIC_OFFER_LEAVE (UI_PANEL / UI_TEXT_PRIMARY), RELIC_COST_TEXT_COLOR (CombatPalette.DAMAGE);
  StoryUiConstants STORY_CODEX_SCRIM (UI_INK_BLACK @ .75, was .86); ProgressionConstants BUILD_PAGE_* chrome,
  BUILD_CHIP_TEXT / PIP (ink black / UI_LINE), BUILD_COST (DAMAGE), BUILD_SYNERGY (SUIT_AMBER) - KEYSTONE_GOLD and
  the BUILD_TAG_* colours stay (semantic). Capture driver: `additem:TYPE:QTY`, `inspectweapon` (SKILL.md).
  BEFORE shots are in /tmp/claude-0/shots/before (base worktree /tmp/claude-0/base, mirror /tmp/toon3d-base).
  Screen coordinates: inventory BUILD (1011,685) CODEX (1143,685) EXIT (1245,685); level-up card centre y 330;
  codex first row (700,547). The RUN_START relic offer opens right after `engage` (no exchange on a fresh save).
  Build: 480 tests, the 1 known failure.
- **CP2:** inventory family restyled in ONE Filled pass each (no Line passes left): scrim UI_INK_BLACK .75; header bar +
  both body panels UiKit panels (UI_PANEL @ .94, UI_LINE); the inventory has no single outer panel, so its
  SUIT_AMBER_DIM brackets frame the bounding rect of header + panels (0,110)-(1280,720), brackets only (no extra
  fill); BUILD (SELECTED while the page is open) / CODEX / EXIT are IDLE plates, labels TYPE_BODY (EXIT overflowed
  its 50-unit plate at LABEL); the left/right panels are skipped while the BUILD page is open (two stacked .94
  panels showed a seam). Grid: tab plates inset INV_TAB_PLATE_INSET (3) inside their unchanged touch rects,
  selected SELECTED amber; slots IDLE / SELECTED / DISABLED (= a filled slot the filter hides); the selection
  pulse is gone; equipped star SUIT_AMBER; category dots keep their semantic colours. Weapon slots: filled = card in
  WeaponTier ink, equipped = SELECTED card; empty IDLE plate; locked DISABLED plate + a filled padlock; section
  rules are UiKit dividers starting past the TYPE_LABEL label width (measured per frame). Dense slot/stat text
  stays TYPE_CAPTION (fixed 182-unit info zone; the order-3 R8 sub-caption scales are gone everywhere here).
  BUILD page: UiKit panel; chips = tag fill + 1-unit UI_LINE border over the old pill footprint, text centred by
  cap height (it sat on the new border); empty pips drawn filled (ring + dark centre); keystone blocks are cards in
  the keystone's TAG ink (gold frame gone; R2); relic rarity only as the ink of a frame around its rarity label;
  section headers TYPE_LABEL secondary; names TYPE_BODY, effects TYPE_CAPTION. ItemWindow + AbilityWindow (CP3
  scope, landed here because the coordinator passes them the new HEAVY font): UiKit panel + amber-dim brackets,
  title = R1 header (HEAVY HEADING), X / ability rows / footer actions are plates (disabled actions DISABLED),
  type badges are outline chips (PASSIVE secondary, ACTIVE amber), category glyph-box frame from the shared
  `ItemGridPanel.writeCategoryColor`; descriptions TYPE_CAPTION (TYPE_BODY ran into the stat block). FIX in UiKit:
  screenHeaderTitle restored the RAW font scale, which GameFonts multiplies by its base again -> shrank to 0 and
  threw "scaleX cannot be 0"; it now restores the logical scale.
  Tap spot-check (run.sh, real taps): tabs (AMMO, MISC), grid slot -> item window, weapon slot -> weapon window,
  BUILD <-> ITEMS, CODEX (opens archive, close returns to the inventory), EXIT (closes; a pending level-up then
  opened), item window X / USE (medkit consumed, +25 HP, closed with a turn) / DROP on a keycard ("Cannot drop
  quest items" flash), ability row -> ability window, its X. NOT tapped: BUILD page UP/DOWN (needs > 11 rows;
  handleTouch unchanged), EQUIP on a grid weapon.
  Build: 480 tests, the 1 known failure.
- **CP3:** WeaponInspectOverlayRenderer in one Filled pass: scrim .75 (was .72); the card is a UiKit CARD in the found
  weapon's TIER ink + SUIT_AMBER_DIM brackets (outermost panel); the old 5-unit tier stripe and the tier-coloured
  title / "LV n - TIER" tag are gone (D2: tier colour in the frame only; the tag reads secondary, the tier ICON keeps
  its colour as an icon); title = R1 header (HEAVY HEADING) whose rule doubles as the header edge (the separate
  header line 9 units below it was dropped as clutter); zone/column rules are UI_LINE dividers / 1-unit rects; zone
  background fills dropped (the card fill is uniform). EQUIP/CHOOSE, CLOSE, CONVERT, SWAP are IDLE plates;
  full-loadout slot rows are weapon CARDS in their own tier ink, the active slot SELECTED. Deltas: HEALTH_GREEN
  better / CombatPalette.DAMAGE worse; matchup glyphs untouched (MatchupGlyphs and WeaponLevelTag draw no chrome -
  not edited). FS_* -> TYPE_* (title HEADING, stats LABEL, button HEADING, slot name BODY, deltas/slot stats
  CAPTION, footer LABEL). HudConstants.WEAPON_CARD_PANEL_ALPHA deleted (card() uses UI_PANEL_ALPHA_SCREEN); the
  touched literals are named WEAPON_CARD_* / WEAPON_SLOT_ROW_*. Taps: CLOSE (closed), CHOOSE THIS WEAPON (equipped
  the chaingun, single-button mode). Full-loadout rows were SEEN rendered (CP3 shot) but a SWAP row was not tapped
  (handleTap untouched); CONVERT is not offered in the start room - not seen.
- **CP4:** LevelUpOverlayRenderer: scrim UI_INK_BLACK .75 (was .84); R1 header LEFT-aligned at the card row's left
  edge (title top TITLE_Y unchanged, rule across the row, subtitle TYPE_LABEL secondary left-aligned at its old Y);
  each card = UiKit CARD in its BuildTag ink (keystones too - the gold double frame is gone, the gold HALO stays,
  chamfered); gradients, drop shadows, inner highlight and the header strip fill are gone (a plain rule marks the
  name band); chips = tag fill + UI_LINE border; pips drawn filled; the SYNERGY corner ribbon is gone (it poked
  through the chamfer) - the amber SYNERGY word stays; name HEAVY TYPE_LABEL primary, rank line TYPE_LABEL primary
  (the old 1.86 already ran into the pip row - LABEL clears it), effects TYPE_CAPTION (a keystone gain must stay
  above its cost line), tap hint caption secondary; no text shadows. RelicOfferOverlayRenderer: scrim .75; R1 header
  left-aligned on the card row; cards = UiKit CARD in RARITY ink over the rarity glow (colour + LEGENDARY pulse
  unchanged, now chamfered); name built AND drawn with the HEAVY font; rarity label = a secondary WORD (colour only
  in the frame, D2); trigger badge = outline chip (raised fill, UI_LINE border, primary text); affinity chip = tag
  fill + UI_LINE border; cost line keeps DAMAGE red; the CURSED dark-red inner vignette is GONE (a fill gradient;
  CURSED still reads from its red frame, its CURSED label and the red cost line - divergence for the note); LEAVE
  IT = IDLE plate with a SECONDARY label (R8). The 0.4 s lockout / glow / pulse constants untouched. Scales:
  RELIC_OFFER_SCALE_NAME LABEL, BADGE CAPTION, EFFECT BODY, LEAVE LABEL; dead constants removed (SCALE_TITLE,
  VIGNETTE_*, SHADOW_OFFSET, BORDER_THICKNESS, LEAVE_R/G/B, LEVEL_UP_TAG_*, SYNERGY_RIBBON_SIZE,
  KEYSTONE_INNER_FRAME_INSET, CHIP_TEXT_RISE). Seen on run: perk offer (levels 2 and 6), KEYSTONE offer (level 5:
  IRON BULWARK / DEMOLITIONIST / STORM CONDUIT), RUN_START relic offer (3 COMMON), BUILD page with a relic row,
  perks under three tags and a keystone card. Taps: perk cards (picked), keystone card (picked), relic card
  (bonded FIELD DRESSING), LEAVE IT (closed the offer). NOT seen: a CURSED / RARE / LEGENDARY card (only RUN_START
  COMMONs are reachable from floor 1), the quarantine title, pause opened over the offer.
- **CP5:** CodexOverlayRenderer: scrim UI_INK_BLACK .75 (was .86); panel = UiKit panel (UI_PANEL .94, UI_LINE) with
  SUIT_AMBER_DIM brackets drawn AFTER the mask bands; R1 header = HEAVY title at its old anchor, the amber-ticked
  rule along the header band's bottom (STORY_CODEX_HEADER_Y) so it also sits under BACK; tabs = plates (open tab
  SELECTED amber), BACK / settings strip = IDLE plates, close X on a 38-unit IDLE plate (touch rect unchanged:
  glyph + 18 padding); rows FLAT with a divider under each (SUIT_AMBER tick under the PINNED page, UI_LINE
  otherwise), the R9 3-unit amber bar on the LAST-OPENED row (the list has no other selection state: the renderer
  remembers CodexSystem.getOpenEntry(); new read-only `CodexSystem.getVisibleEntry(int)` for pinned/identity),
  NEW = UiKit.newMarker amber square + the amber NEW word (the per-frame `newLabel + " - " + source` concatenation
  is gone - two draw runs). Text unshadowed; ORA's take keeps her speaker accent (a voice, not chrome); detail
  title TYPE_LABEL primary. Scales: TITLE 1.5 -> LABEL (same value), TAB 1.15 -> BODY, ROW 1.2 -> BODY, DETAIL 1.3
  -> BODY (x the story text multiplier, unchanged), counts / sources / footer CAPTION. UiKit.plate gained an alpha
  overload (the codex fades in). Removed STORY_CODEX_PLATE_* and NEW_DOT_RADIUS (no readers). Seen on run: list with
  NEW marker; an open entry at NORMAL / LARGE / LARGEST (wraps, no overflow); list with the selection bar; PEOPLE
  tab still listing the pinned WHERE WE'D GOT TO first (A5); close back to the inventory. Taps: CODEX button, row
  (opens entry), BACK, TEXT setting x3 (cycles NORMAL -> LARGE -> LARGEST -> NORMAL), PEOPLE tab, close X. Not
  seen: a completed tab, an empty tab with no pinned page (the pinned page appears in every tab), a long scrolled
  entry (drag scroll not exercised; scroll code untouched).
- **CP6:** A4 grep over the twelve files (11 classes + WeaponLevelTag/MatchupGlyphs, which draw no chrome and were
  not edited): no colour literal left except two SpriteBatch tint resets in ItemGridPanel (icon routine, commented);
  no literal reaches setScale; the only Line pass left is WeaponLevelTag's LV arrow glyph (an icon). A6: every
  scrim is UI_SCRIM_ALPHA 0.75 (INV_SCRIM_ALPHA, RELIC_OFFER_SCRIM_ALPHA, STORY_CODEX_SCRIM_ALPHA, inspect and
  level-up read UI_SCRIM_ALPHA); the inventory's popup dims (INV_BASE_DIM_FACTOR .40 / INV_ITEM_WIN_DIM_FACTOR
  .50) are REMOVED - R3 "never stacked". A7: only the codex follows the story text size; its entry was seen at
  NORMAL / LARGE / LARGEST (CP5) with no overflow. CONTRAST: DAMAGE is 4.1:1 on UI_PANEL ("large text only"), so
  the BUILD page cost rows, the keystone card's cost line and the inspect card's "-n" deltas moved to TYPE_BODY.
  A3 diff check: no panel rect, card position, row height, scroll metric or touch target constant changed (only
  colours, alphas, scales and dead visual constants). Docs: art-direction SECTION 14 SCREENS (new), relic-system
  "WHAT THE PLAYER SEES", story-ui-system CODEX PAINT, xp-level-progression (level-up + BUILD page lines), CLAUDE.md
  docs table + UiKit entry. Final AFTER shots: /tmp/claude-0/shots/after (same scripts as /tmp/claude-0/shots/before).
  Build + test: 480 tests, the 1 known failure (StoryBarkTest bark.depth.core.2).

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
- [ ] **CP1** — UiKit.screenHeader + UiKit.card + UI_PANEL_ALPHA_SCREEN; chrome colour blocks re-pointed.
      DONE WHEN: they exist, every reader compiles, build green.
- [ ] **CP2** — InventoryOverlayRenderer, ItemGridPanel, WeaponSlotsPanel, BuildPagePanel restyled.
      DONE WHEN: A1/A2/A3 hold for the inventory on run; build green.
- [ ] **CP3** — ItemWindow, AbilityWindow, WeaponInspectOverlayRenderer restyled.
      DONE WHEN: A1/A3 hold for them on run; build green.
- [ ] **CP4** — LevelUpOverlayRenderer, RelicOfferOverlayRenderer restyled (R2, R8).
      DONE WHEN: A1/A2/A3 hold on run; build green.
- [ ] **CP5** — CodexOverlayRenderer restyled (R9, R7). DONE WHEN: A5 holds on run; build green.
- [ ] **CP6** — A4/A6/A7 sweep, docs. DONE WHEN: build + test at baseline; ready for reviewer.
- [ ] **CP7** — reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

- **CP0:** order 4's branch ccr-eb246ad6-ypyqrt is merged into master (PR #314), so this branch starts at
  origin/master 077152d. Order-3 R8 deferred sites inside this order's classes: ItemWindow 0.80-0.85 x FONT_SCALE
  literals (+ any sub-caption scales found in BuildPagePanel / LevelUp / RelicOffer / Codex) move to TYPE_* here.

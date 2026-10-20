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
- [ ] **CP1** — UiKit + PlateState + ArtDirection R7 constants (deliberately unused).
      DONE WHEN: they exist, nothing reads them yet, build green.
- [ ] **CP2** — HudRenderer restyled (R9 + R15). DONE WHEN: A2 (HUD), A3 (HUD), A9 seen on run; build green.
- [ ] **CP3** — TouchControllerRenderer restyled (R10), constant renamed. DONE WHEN: A4 incl. tap spot-check; build green.
- [ ] **CP4** — LevelRenderer (R11) + BossHudRenderer (R12). DONE WHEN: A5 seen on run; build green.
- [ ] **CP5** — EventText (R13) + ImpactEffect damage numbers (R14), docs. DONE WHEN: A6, A7, A8 hold; build + test
      at baseline.
- [ ] **CP6** — reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

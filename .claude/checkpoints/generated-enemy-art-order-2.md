# CHECKPOINT — generated-enemy-art order 2: the animated enemy runtime

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-enemy-art-order-2.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-3e3cd62b-w39s9t` (equal to `origin/master` a99b493 at start — order 1's PR #318 merged)
**IDEA:** `.claude/agents/ideas/generated-enemy-art-order-2.txt` — the spec. Its rules (R1-R6) and acceptance
criteria (A1-A9) are referred to by number below and are NOT repeated here.
**LANE:** full lane / `enemy` (seam only) + `render/enemyvisual` (new) + `render/EnemyRenderer` + `world` + run-game
capture tooling + docs.
**TESTS:** none — not balance-bearing (series G12, Testing Policy). Verified by the mirror build + run-game captures.
**STARTED:** 2026-10-21
**BASELINE:** branched from `a99b493`. Known pre-existing test failure: `narrative.StoryBarkTest`
(`humourLivesNearTheSurfaceAndDiesNearTheCore`) — not ours, not to be fixed. Anything else red is reported.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-enemy-art-order-2/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-enemy-art-order-2/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- G9 / R2: visuals are cosmetic; the animator never writes to Enemy / EnemyManager / World; no rule, timing,
  turn or action-lock change. `enemy/` never imports `render/` (A8).
- The seam follows `enemy/EnemyVoiceListener` exactly: no-op/null default, World owns the impl, payload reused.
- R6: no allocation in render() or per-frame update; GL only on the render thread; every Texture disposed.
- register() rows, never a switch (EnemyVisualRegistry, EnemyAnimationRules as data).
- Build/verify with the run-game MIRROR build (`/tmp/toon3d-runtime/mirror`), never the repo-root gradlew.
- No new tests (G12). Constants in the matching Constants file (heavy-hurt 25% -> EffectConstants, AS2).
- OWNER GATE at CP5: burst GIFs/screens of ACID_DRONE and SHELL_BRUTE for every core clip, default mood +
  one other, sent; then STOP until the owner approves (owner instruction for this run).
- Docs at CP6: guide 8 + 10, art-direction 16, enemy-system.txt, CLAUDE.md package row.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — the seam exists with a no-op default, fired at every site; build green; sim/ unchanged.
      DONE WHEN: `enemy/EnemyVisualListener` + `EnemyVisualMoment` + payload exist, EnemyManager fires every
      moment of the idea's list at its commit site, nothing in `sim/` changed, mirror build compiles.
- [ ] **CP2** — registry + manifest parser + per-floor atlas cache load the two creatures; build green.
- [ ] **CP3** — animator + rules + dying pool; EnemyBodyDrawer draws IDLE in-engine for both types.
- [ ] **CP4** — every core clip plays from its moment in-engine; glow pass; legacy types unchanged.
- [ ] **CP5** — capture tooling; burst GIFs sent; owner approval recorded (stop until it is).
- [ ] **CP6** — performance measured; docs; reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

(none yet)

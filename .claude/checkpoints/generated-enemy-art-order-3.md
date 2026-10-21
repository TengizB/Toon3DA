# CHECKPOINT — generated-enemy-art order 3: reactions and effects

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-enemy-art-order-3.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-7b97a7bb-y7zn4u` (equal to `origin/master` 649c7a6 at start — order 2 merged)
**IDEA:** `.claude/agents/ideas/generated-enemy-art-order-3.txt` — the spec. Its rules (R1-R5) and acceptance
criteria (A1-A7) are referred to by number below and are NOT repeated here.
**LANE:** full lane / enemy-art skill (`effects.py`, new) + `render/enemyvisual` + `render/EnemyRenderer` + `enemy`
(seam fire sites only) + `world` wiring + run-game capture tooling + docs.
**TESTS:** none — not balance-bearing (series G12, Testing Policy). Verified by the mirror build + run-game captures.
**STARTED:** 2026-10-21
**BASELINE:** branched from `649c7a6`. Known pre-existing test failure: `narrative.StoryBarkTest`
(`humourLivesNearTheSurfaceAndDiesNearTheCore`) — not ours, not to be fixed. Anything else red is reported.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-enemy-art-order-3/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-enemy-art-order-3/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- G9 / R1: reactions are cosmetic; the visual layer never writes Enemy / EnemyManager / World; no rule, timing, turn
  or action-lock change. `enemy/` never imports `render/`.
- BUILD ON order 2's layer, do not fork it: new reactions are ROWS in `EnemyAnimationRules`; the one reused
  `EnemyVisualEvent` payload; STATUS_APPLIED + resisted/blocked/absorbed are silent today (Kind.NONE / clipFor null) —
  order 3 maps them. A DoT DAMAGED moment, if needed, is added at StatusEffectController / processDoTKill without
  breaking R2 of order 2.
- GLOW: emissive effect atlases are drawn as an ungraded NORMAL-blend overlay like order 2's glow (owner D2), never
  additive unless a reason is written down and shown to the owner. (The idea text says "additively" — superseded.)
- CORPSE HANDOVER (A4): the sim stamps the 'm' decal at death; the creature's own remains replace what is DRAWN on
  that tile; the corpse tile and its semantics are untouched (R5).
- effects.py is offline Python in the enemy-art skill, deterministic; `warden.*` and `harvest_tick.*` must regenerate
  byte-identically unless the owner approves a change.
- R4: every particle / remains pool has a fixed cap, the oldest recycled; no allocation in render() or per-frame update.
- Build/verify with the run-game MIRROR build (`/tmp/toon3d-runtime/mirror`), never the repo-root gradlew.
- Capture scripts are TIME-SORTED (steps run in list order). New capture steps documented in run-game SKILL.md.
  STAGGERED / KNOCKED_BACK must be exercised FOR REAL (equip a shotgun / hammer and fire), not forced.
- OWNER GATE at CP5: in-engine burst GIFs of both creatures — hurt light, hurt heavy, resisted, blocked, kill; WOUNDED
  idle + leak; burning + stunned; remains on the tile; the Warden's projectile from its emitter; stagger + knockback —
  default mood + one other, labelled per segment. Then STOP until the owner approves.
- CP6: frame time vs order 2 (same room, interleaved runs, worktree baseline at 649c7a6) + GC log (no allocation after
  floor start). Docs: guide 8 + 9 (drop [PLANNED]), art-direction 16, enemy-system.txt, SKILL.md files, CLAUDE.md row.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — effects.py writes machine + insect effect atlases and manifests.
      DONE WHEN: `python3 effects.py <dir>` writes `machine.fx.png` / `insect.fx.png` + `.fx.json` deterministically
      (two runs byte-identical), and `bestiary.py` still regenerates warden.* / harvest_tick.* byte-identical.
- [ ] **CP2** — EnemyEffectSystem draws hit effects at the hit anchor in-engine; build green.
- [ ] **CP3** — outcomes (hurt/resisted/blocked/kill), recoil, wounded idle + leak work for both creatures.
- [ ] **CP4** — status visuals; remains; projectile + muzzle; telegraph effects.
- [ ] **CP5** — burst GIFs sent; owner approval recorded (stop until it is).
- [ ] **CP6** — performance measured; docs; reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

**CP0:** baseline 649c7a6.

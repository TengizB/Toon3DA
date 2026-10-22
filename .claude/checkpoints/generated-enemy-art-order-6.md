# CHECKPOINT — generated-enemy-art order 6: the Undead (humanoid rig)

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-enemy-art-order-6.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-fd4ecb7d-5f8ixb` (equal to `origin/master` 018390c at start — orders 1-5 merged)
**IDEA:** `.claude/agents/ideas/generated-enemy-art-order-6.txt` — the spec. Its rules (R1-R4) and acceptance
criteria (A1-A6) are referred to by number below and are NOT repeated here.
**LANE:** full lane / enemy-art skill (`kit.py`, five `*_rig.py`, `bestiary.py`, `effects.py`) + generated assets
+ `render/enemyvisual` (registry rows + the smallest data-driven runtime additions) + docs.
**TESTS:** none — not balance-bearing (series G12, Testing Policy). Verified by the mirror build + run-game captures.
**STARTED:** 2026-10-22
**BASELINE:** branched from `018390c`. Known pre-existing test failure: `narrative.StoryBarkTest`
(`humourLivesNearTheSurfaceAndDiesNearTheCore`) — not ours, not to be fixed. Anything else red is reported.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-enemy-art-order-6/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-enemy-art-order-6/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Build ON the kit and the order 2-5 runtime, never fork them. Kit growth: humanoid rig, cloth materials, dead-skin
  ramp, SOUL emissive ramp, cloth-fold helper, cloth lag. Materials only APPENDED to `kit.MATERIALS`.
- Every earlier creature (`warden`, `harvest_tick`, `iron_stalker`, `plague_hulk`, `eye_tyrant`, `gore_biter`,
  `sump_wretch`, `mire_wraith`) and every `*.fx.*` set regenerates BYTE-IDENTICAL unless the owner approves.
- OWNER GATES (owner's instruction 2026-10-22): concept STILL of each enemy sent and approved BEFORE any animation;
  the stills checkpoint sends the family sheet; the clips checkpoint sends labelled clip GIFs + in-engine burst GIFs
  (default mood + one other) and STOPS for approval. A1: the Ghoul's read comes first.
- Runtime: any change = the smallest data-driven change, named in the IMPLEMENTATION NOTE.
- Wiring = one `EnemyVisualRegistry` row per creature, only after the owner approves it.
- G1 originality: the old enemy textures are never opened. G5 no saturated red/green; SOUL never reads purple.
  G9 cosmetic only. G10 no name/stat/ability change.
- Build/verify with the run-game MIRROR build, never the repo-root gradlew. No new tests.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — kit: humanoid rig, cloth + dead-skin materials (appended), SOUL ramp, fold helper, cloth lag.
      DONE WHEN: every earlier creature and *.fx.* set regenerates byte-identical.
- [ ] **CP2** — concept stills: the Ghoul first (A1), then the other four; family sheet + each image sent; owner's
      concept approval recorded in DECISIONS (stop until it is).
- [ ] **CP3** — all five rigs' clips + ability clips + effects; labelled clip GIFs + in-engine burst GIFs (default +
      one mood) sent. DONE WHEN: sent; build green.
- [ ] **CP4** — owner approval recorded (stop until it is).
- [ ] **CP5** — five rows wired; A3-A5 verified in-engine; build green.
- [ ] **CP6** — docs (guide, art-direction 16, SKILL.md); reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

**CP0:** baseline 018390c.

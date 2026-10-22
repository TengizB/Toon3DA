# CHECKPOINT — generated-enemy-art order 6: the Undead (humanoid rig)

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-enemy-art-order-6.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `claude/generated-enemy-art-order-6-f9plfe` (equal to `origin/master` 018390c at start — orders 1-5 merged)
**IDEA:** `.claude/agents/ideas/generated-enemy-art-order-6.txt` — the spec. Its rules (R1-R4) and acceptance
criteria (A1-A6) are referred to by number below and are NOT repeated here.
**LANE:** full lane / enemy-art skill (`kit.py`, a humanoid rig module, five `*_rig.py`, `bestiary.py`, `effects.py`) +
generated assets + `render/enemyvisual` (registry rows + the smallest data-driven runtime additions) + docs.
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

- OWNER OVERRIDE (2026-10-22, the /implement request; supersedes the idea's VISUAL DESIGN where they clash):
  NO HUMAN FACE IS VISIBLE on any undead — at most glowing eyes. Every one is covered: armour, hazard suit,
  hood, respirator, helmet. ONE of them is a TALL, THIN humanoid with disproportionately long legs, arms and long
  fingers, in a hazard suit and a gas mask with ONE single glass visor (not two eye lenses) and glowing RED eyes.
  Others may have two-lens masks.
- OWNER GATES: (1) the CONCEPT still of each enemy is sent and approved BEFORE any animation is made;
  (2) the family sheet at the stills checkpoint; (3) labelled clip GIFs + in-engine burst GIFs (default mood + one
  other) at the clips checkpoint, then STOP for approval; (4) registry rows only after approval.
- Build ON the kit and the order 2-5 runtime, never fork them. Materials only APPENDED to `kit.MATERIALS`.
- Every earlier creature (`warden`, `harvest_tick`, `iron_stalker`, `plague_hulk`, `eye_tyrant`, `gore_biter`,
  `mire_wraith`, `sump_wretch`) and every `*.fx.*` regenerates BYTE-IDENTICAL.
- Runtime: any change is the smallest data-driven one, named in the IMPLEMENTATION NOTE (expected: a SUMMON
  ability clip id + its `abilityClip` row).
- G1 originality: the old enemy textures are never opened. G10 no name/stat/ability change.
- Build/verify with the run-game MIRROR build, never the repo-root gradlew. No new tests.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — kit: humanoid rig helper, cloth/suit/skin materials appended, soul ink as the UNDEAD family ink, fold
      helper, cloth lag. DONE WHEN: every earlier creature + fx set regenerates byte-identical.
- [ ] **CP2** — five rigs at rest pose (concept stills, owner override applied); family sheet + one image per enemy
      SENT; STOP for the owner's concept read (A1 recorded). DONE WHEN: sent and the answer recorded in DECISIONS.
- [ ] **CP3** — all clips + ability clips (DEBUFF_PLAYER, SUMMON) + the `undead` effect set; clip GIFs + in-engine burst
      GIFs (default + one mood) SENT. DONE WHEN: sent; build green.
- [ ] **CP4** — owner approval of all five recorded (stop until it is).
- [ ] **CP5** — five registry rows + SUMMON clip row wired; A3-A5 verified in-engine; build green.
- [ ] **CP6** — docs (guide, art-direction 16, SKILL.md, enemy-system, CLAUDE.md row); reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

**CP0:** baseline 018390c. Facts found: VORTEX_EYE is NOT hovered by the renderer (only EYE_TYRANT) -> bake its hover
into the canvas. REVENANT has heavyWindUp CHARGE (a real WIND_UP in the sim). VORTEX_EYE is ranged (DEBUFF_PLAYER) ->
needs PROJECTILE/TRAIL/MUZZLE in the undead set. BLIGHT_CORRUPTOR uses SUMMON - no EnemyClipId/abilityClip row yet.
`soul` (emissive) and `cloth`, `rubber` already exist in MATERIALS (wall-art order 1) - reuse them.

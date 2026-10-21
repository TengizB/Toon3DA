# CHECKPOINT — generated-enemy-art order 4: Iron Stalker (insect complete)

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-enemy-art-order-4.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-031fb944-u576an` (equal to `origin/master` 27725f1 at start — orders 1-3 merged)
**IDEA:** `.claude/agents/ideas/generated-enemy-art-order-4.txt` — the spec. Its rules (R1-R2) and acceptance
criteria (A1-A4) are referred to by number below and are NOT repeated here.
**LANE:** full lane / enemy-art skill (`kit.py`, `iron_stalker_rig.py`, `bestiary.py`, `effects.py`) + generated assets
+ `render/enemyvisual` (registry row + the ability-clip mapping) + docs.
**TESTS:** none — not balance-bearing (series G12, Testing Policy). Verified by the mirror build + run-game captures.
**STARTED:** 2026-10-21
**BASELINE:** branched from `27725f1`. Known pre-existing test failure: `narrative.StoryBarkTest`
(`humourLivesNearTheSurfaceAndDiesNearTheCore`) — not ours, not to be fixed. Anything else red is reported.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-enemy-art-order-4/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-enemy-art-order-4/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Build ON the kit and the order 2-3 runtime, never fork them. Kit growth: a SPIKE/blade primitive + a riveted
  plate helper. Materials only APPENDED to `kit.MATERIALS`.
- `warden.*`, `harvest_tick.*` and the order-3 `*.fx.*` must regenerate BYTE-IDENTICAL unless the owner approves.
- Effects: reuse the insect set + add an armour-plate spark for the Stalker's RESISTED hits; one effects.py
  creatures row for `iron_stalker` (leak / burn / charge / stun / poison / body).
- Runtime: BUFF_SELF + AREA_STRIKE ability clips mapped with the SMALLEST data-driven change (rule rows, not a
  switch); named in the IMPLEMENTATION NOTE. ABILITY_USED plays ATTACK today. The strike AREA MARKER stays with
  `EnemyAttackEffectSystem`.
- Wiring = ONE `EnemyVisualRegistry` row: IRON_STALKER -> "iron_stalker", only after the owner gate (CP3).
- G1 originality: the old enemy textures are never opened. G9: cosmetic only. G10: no name/stat/ability change.
- Build/verify with the run-game MIRROR build, never the repo-root gradlew. Capture scripts time-sorted; never
  pipe run.sh into head. No new tests.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — spike + riveted-plate primitives in kit.py; `iron_stalker_rig.py` rest pose; stills sheet beside
      Warden + Harvest Tick sent. DONE WHEN: `bestiary.py` regenerates warden.* / harvest_tick.* byte-identical and
      `--stills iron_stalker` renders with no edge warning.
- [x] **CP2** — full clip set + BUFF_SELF + AREA_STRIKE baked within 2048x2048; effects.py insect plate spark +
      iron_stalker row; *.fx.* byte-identical except insect (owner-visible change named); runtime ability-clip rows;
      clip GIFs + in-engine burst GIFs (default + one mood) sent. DONE WHEN: the GIFs are sent and the build is green.
- [ ] **CP3** — owner approval recorded in the idea's DECISIONS (stop until it is).
- [ ] **CP4** — registry row wired; A2-A3 verified in-engine; build green.
- [ ] **CP5** — docs (guide, art-direction 16, SKILL.md); reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

**CP0:** baseline 27725f1.

**CP1:** kit.py gained `spike(x1, y1, x2, y2, r, mat, bend, tip, segments, edge, edge_side)` (tapered curved blade, optional
raised edge material) and `riveted_plate(points, mat, rivet_mat, spacing, inset, rivet_r, bevel, **slab kw)` (slab +
`add` rivets; op="add" fits it ON a body, a union slab in its own layer is a liftable piece), plus a "P" stencil glyph.
No MATERIALS change. Regenerated warden.*, harvest_tick.*, machine/insect/status .fx.* into the scratchpad: ALL
byte-identical (cmp). `iron_stalker_rig.py`: rest pose only (STALKER_CLIPS = [] - the default bestiary bake must not be
run for it until CP2 adds the clips; `--stills` works). Pose params already include everything the clips need (blade
arms l_/r_ sh/el/reach/smear, lift, ring, tear, drip, collar, tuck/splay, tag). Silhouette: canvas x 17..142,
y 24..232 (no edge). Stills sheet (beside Warden + Tick, 3x + in-game relative scale) sent to the owner for an early read.

**CP2:** 13 clips (standard set + BUFF_SELF + AREA_STRIKE), 100 unique frames, atlas 2048x2048, no edge warnings. Rig
details settled here: `STAND = 1.065` stretches the whole figure about the ground line (A3: the first in-engine read
had it only ~6% taller than the Shell Brute; top row now 10); `reach` scales an arm mostly sideways (y at 35%) so a
lunge never lifts the elbow off the top; smear is SIGNED (+ trails outward on an inward swing, - inward on an outward
one). Effects: insect set APPENDS PLATE_SPARK / PLATE_STREAK / PLATE_FLECK + burst RESIST_PLATE, and an `iron_stalker`
creatures row whose `"bursts": {"RESIST": "RESIST_PLATE"}` is a per-creature override; every pre-existing insect frame
is pixel-identical (only atlas positions repacked), old bursts/rows unchanged; machine + status byte-identical.
RUNTIME (name these in the IMPLEMENTATION NOTE): (1) EnemyClipId += BUFF_SELF, AREA_STRIKE (the manifest parser picks
them up by name); (2) EnemyAnimationRules: ABILITY_USED row is Kind.ABILITY + two `abilityClip(SpecialAbility, clip)`
rows, falling back to the row's ATTACK when a creature lacks the clip; (3) `holdFor(Enemy)`: a committed heavy
(`plannedAction.heavyAttack != null`) holds WIND_UP - FOUND in-engine: AREA_STRIKE is committed as a planned heavy
while the state stays ATTACKING/CHASING, so the state-keyed hold dropped the telegraph pose; `holdsWindUp` also drives
the CHARGE burst; (4) EnemyEffectSet parses every burst by name + per-creature `bursts` overrides, read first by
`CreatureVisual.burst()`. NOT COMMITTED: the `EnemyVisualRegistry` row IRON_STALKER -> "iron_stalker" sits in the
working tree only (needed for the captures) - CP4 commits it after approval; if resuming with a clean tree, re-add it.
Captures: scratchpad a.txt / r.txt / h.txt via cap.sh; GIFs sent (clips_all + strip + insect fx sheet; in-engine
default + amber_hot). Sim ability order is randomised: label segments from `logmoments`. Build: only StoryBarkTest red.
WAITING for owner approval (CP3).

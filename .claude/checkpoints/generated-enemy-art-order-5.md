# CHECKPOINT — generated-enemy-art order 5: the Aberrations

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-enemy-art-order-5.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-671e32a6-tg3zlt` (equal to `origin/master` 7a3970d at start — orders 1-4 merged)
**IDEA:** `.claude/agents/ideas/generated-enemy-art-order-5.txt` — the spec. Its rules (R1-R4) and acceptance
criteria (A1-A6) are referred to by number below and are NOT repeated here.
**LANE:** full lane / enemy-art skill (`kit.py`, four `*_rig.py`, `bestiary.py`, `effects.py`) + generated assets
+ `render/enemyvisual` (registry rows + the smallest data-driven runtime additions) + docs.
**TESTS:** none — not balance-bearing (series G12, Testing Policy). Verified by the mirror build + run-game captures.
**STARTED:** 2026-10-21
**BASELINE:** branched from `7a3970d`. Known pre-existing test failure: `narrative.StoryBarkTest`
(`humourLivesNearTheSurfaceAndDiesNearTheCore`) — not ours, not to be fixed. Anything else red is reported.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-enemy-art-order-5/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-enemy-art-order-5/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Build ON the kit and the order 2-4 runtime, never fork them. Kit growth: flesh materials, a wet-sheen setting,
  an eyeball helper, a teeth-row helper, a jiggle helper. Materials only APPENDED to `kit.MATERIALS`.
- `warden.*`, `harvest_tick.*`, `iron_stalker.*` and every `*.fx.*` regenerate BYTE-IDENTICAL unless the owner approves.
- Effects: a NEW `aberration` set in effects.py + one creatures row per creature; ichor ink shared with insects (AS1).
- Runtime: reuse order 4's `abilityClip` rows. Any runtime change (SELF_DESTRUCT priming held clip, self-destruct
  DEATH variant, the Eye Tyrant beam from the eye anchor) = the smallest data-driven change, named in the
  IMPLEMENTATION NOTE.
- Wiring = one `EnemyVisualRegistry` row per creature, only after the owner gate (CP4).
- G1 originality: the old enemy textures are never opened. G5 no saturated red. G9 cosmetic only. G10 no
  name/stat/ability change.
- Build/verify with the run-game MIRROR build, never the repo-root gradlew. Capture scripts time-sorted; never
  pipe run.sh into head. No new tests.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — kit additions (flesh materials appended, wet sheen, eyeball, teeth row, jiggle).
      DONE WHEN: warden.* / harvest_tick.* / iron_stalker.* / *.fx.* regenerate byte-identical.
- [x] **CP2** — four rigs (rest poses); family stills sheet (beside Harvest Tick + Iron Stalker) sent.
      DONE WHEN: `--stills` renders all four with no edge warning and the sheet is sent.
- [ ] **CP3** — all clips + ability clips + the aberration effect set; runtime additions; clip GIFs + in-engine burst
      GIFs (default + one mood) sent. DONE WHEN: the GIFs are sent and the build is green.
- [ ] **CP4** — owner approval recorded in the idea's DECISIONS (stop until it is).
- [ ] **CP5** — four registry rows wired; A2-A5 verified in-engine; build green.
- [ ] **CP6** — docs (guide, art-direction 16, SKILL.md); reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

**CP0:** baseline 7a3970d.

**CP1:** kit.py APPENDS five materials (flesh, flesh_dk, sclera, membrane [translucent, glow=ichor], pupil), each with a
`wet=(strength, shininess)` WET SHEEN (a tight second specular lobe -> WET_GLINT near-white; cleanup shade +1000 so a
glint is its own shade). Helpers: `eyeball(cx, cy, r, look, iris, pupil, slit, lid_top, lid_bottom, lids, ...)`,
`teeth_row(x1, y1, x2, y2, count, length, r, side, sag, rake, jitter, seed)`, `jiggle(source_clip, param, duration,
out_param, freq, damping, gain, fps, loop)` -> keys for "<param>_jig" (a damped spring lagging its parent track, baked;
a loop integrates 4 periods so it wraps seamlessly). Stencil glyphs T S G M A B. Verified: bestiary + effects
regenerated into the scratchpad, all 15 shipped files byte-identical (cmp).

**CP2:** four rigs, rest poses only: `plague_hulk_rig.py` (seed 31), `eye_tyrant_rig.py` (37), `gore_biter_rig.py` (41),
`mire_wraith_rig.py` (43); four CREATURES rows with EMPTY clip lists (`--stills` works; the default bake must not be run
for them until CP3 adds clips). kit.eyeball's lid was remodelled as a CAP (crease at 0, covers the eye at 1) - eyeball is
new in this order, so no earlier sprite moves. Anchors: Hulk mouth = between the fists; Eye Tyrant muzzle = eye = the
pupil (A5: the beam leaves it); Gore Biter mouth; Mire Wraith muzzle = spout mouth. No edge warnings at rest. Family
stills sheet (3x + in-game relative scale, beside Harvest Tick + Iron Stalker) sent for an early read.
Planned runtime (CP3, name in IMPLEMENTATION NOTE): (a) Hold gets a fallback clip: SELF_DESTRUCTING holds PRIMING else
WIND_UP; a hold that changes while another hold is showing starts from frame 0; (b) abilityClip rows SELF_DESTRUCT and
DEBUFF_PLAYER; (c) death rows by DeathCause: SELF_DESTRUCT -> DEATH_BURST clip + DETONATE burst (fallback DEATH);
dying/remains carry their clip; (d) EYE_TYRANT beam starts at the frame's EMITTER anchor; dying EYE_TYRANT eases its
hover down (else it pops a quarter wall at death); (e) scheduleShot reads the emit frame of whichever one-shot plays
(the DEBUFF_PLAYER spit). Aberration PROJECTILE/TRAIL/MUZZLE live as per-creature overrides on mire_wraith only, so the
Eye Tyrant keeps the renderer's beam with no runtime change.

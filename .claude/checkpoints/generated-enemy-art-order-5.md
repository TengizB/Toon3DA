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
- [x] **CP3** — all clips + ability clips + the aberration effect set; runtime additions; clip GIFs + in-engine burst
      GIFs (default + one mood) sent. DONE WHEN: the GIFs are sent and the build is green.
- [x] **CP4** — owner answer recorded (D1-D4): Hulk / Eye Tyrant / Gore Biter approved; Mire Wraith rejected -> redesign;
      its art kept as `sump_wretch`; arrival splash approved. DONE WHEN: DECISIONS written, sump_wretch renamed
      pixel-identical, committed.
- [x] **CP5** — three rows wired (PLAGUE_HULK, EYE_TYRANT, GORE_BITER) + the acid arrival splash (runtime IMPACT burst);
      A2-A5 for the three re-verified in-engine; build green.
- [x] **CP5b** — the NEW Mire Wraith (dangerous, alive, not an egg): rig + full clip set + effects row; stills, clip GIFs
      and in-engine bursts (default + one mood) sent. DONE WHEN: sent; build green.
- [x] **CP5c** — owner approval of the new Mire Wraith recorded (stop until it is); then its row wired, A2 verified.
- [ ] **CP6** — docs (guide, art-direction 16, SKILL.md, enemy-system); reviewer PASS; STATUS: IMPLEMENTED.

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

**CP3:** all clips + effects + runtime. Frames / atlas: plague_hulk 98 (2048x2048, NARROW x0.86 about the centre line so
the swings and the death spread stay on the canvas), eye_tyrant 96 (2048), gore_biter 83 (1024x2048), mire_wraith 96
(2048); no edge warnings. Ability clips: Hulk AREA_STRIKE + PRIMING (hold) + SELF_DESTRUCT (hold) + DEATH_BURST (variant of
DEATH); Eye Tyrant + Mire Wraith DEBUFF_PLAYER (the Wraith's carries an "emit" event). Arm swings: a 2-bone arm rotated
about the shoulder sweeps OUTSIDE the canvas on any overhead raise - raises go up the FRONT (negative elbow = the curl),
"fists high" = fists beside the head (sh 0, el -2.75). Effects: `aberration` set (13 sprites, 15 bursts incl. DETONATE and
the creature-only SPIT / SPIT_TRAIL / SPIT_MUZZLE / SPLASH / SPLASH_HEAVY / SCUFF); rows: mire_wraith overrides
PROJECTILE/TRAIL/MUZZLE/HIT/HIT_HEAVY, gore_biter CHARGE -> SCUFF. Eye Tyrant has NO projectile -> the renderer's beam.
Fresh bake of ALL 29 generated files == shipped (cmp); the 15 earlier files byte-identical.
RUNTIME (name these in the IMPLEMENTATION NOTE): (1) EnemyClipId += DEBUFF_PLAYER, SELF_DESTRUCT, PRIMING, DEATH_BURST;
(2) abilityClip rows DEBUFF_PLAYER, SELF_DESTRUCT; (3) Hold.fallback + clipIn(manifest): SELF_DESTRUCTING holds PRIMING
else WIND_UP (holdsWindUp counts the fallback, so priming seeps CHARGE motes); a hold that REPLACES a showing hold starts
at frame 0 (WIND_UP_STARTED arrives before the state flips); (4) a HOLD-mode one-shot keeps its last frame while the
enemy still holds (the countdown's SELF_DESTRUCT stays one level up) - FOUND in-engine, it fell back to PRIMING;
(5) DeathRow by DeathCause: SELF_DESTRUCT -> DEATH_BURST + BurstId.DETONATE (fallbacks DEATH); DyingEnemyPool / Remains
carry their clip; (6) scheduleShot reads the emit frame of WHICHEVER one-shot plays (the Wraith's volley);
(7) EnemyRenderer: EYE_TYRANT beam from the frame's EMITTER anchor (GameMath.billboardCanvasX/Y); the dying EYE_TYRANT
eases its hover down (GameMath.hoverRemainingWhileFalling, EffectConstants.ENEMY_VISUAL_HOVER_FALL_PORTION 0.62) - it
popped a quarter wall at death otherwise; (8) MOVE loop starts at the per-enemy idle phase (A3 - FOUND in-engine: the
three biters' slides start together, so they stepped in unison; touches every creature's MOVE, cosmetic).
NOT COMMITTED: the four EnemyVisualRegistry rows (PLAGUE_HULK -> plague_hulk, EYE_TYRANT -> eye_tyrant, GORE_BITER ->
gore_biter, MIRE_WRAITH -> mire_wraith) were used for the captures, then REMOVED from the tree (clean tree at the gate) - CP5 adds them;
if resuming with a clean tree, re-add them. Findings for the gate: aberrations are trait FLESH (matchup row all 1.0), so
RESISTED never happens to them in the sim (the WELT resist burst ships, unreachable today); the Eye Tyrant, Gore Biter
and Mire Wraith never commit a WIND_UP (no heavy) - their WIND_UP shows only offline; the acid arrival splash would need a
runtime hook (projectile arrival burst) - the splash is at the spout instead. Captures: scratchpad tools/gen.py ->
hulkA/hulkB/tyrant/biters/wraith.txt via tools/cap.sh, GIFs by tools/assemble.py (labels from logmoments).
Build: only StoryBarkTest red. WAITING for owner approval (CP4).

**CP4 (gate):** owner 2026-10-22 -> idea DECISIONS D1-D4. Rename done: mire_wraith_rig.py -> sump_wretch_rig.py (rig name
"sump_wretch", same seed 43 and geometry: atlas pixels identical; manifest "creature"/file names changed), CREATURES row and
the effects creatures row renamed. The new-enemy idea file (acid splash on death, built on sump_wretch) was handed to
creative-game-designer - separate full-lane work, owner approves it separately; NOT part of this order. The new Mire
Wraith needs its own effects creatures row (mire_wraith) at CP5b - none exists at this commit.

**CP5:** rows PLAGUE_HULK -> plague_hulk, EYE_TYRANT -> eye_tyrant, GORE_BITER -> gore_biter COMMITTED. Arrival splash
(runtime #9 for the IMPLEMENTATION NOTE): BurstId.IMPACT; EnemyEffectSystem.launch(..., impact, ...) - a projectile with an
IMPACT burst ends EffectConstants.ENEMY_EFFECT_PROJECTILE_IMPACT_LEAD_TILES (1.1) short of its target and bursts there
(GameMath.projectileImpactSeconds); EnemyVisualLayer.launchShot passes visual.burst(IMPACT). Data: aberration burst
ACID_SPLASH (DETONATE flash + SPLAT + ICHOR rain), taken through an "IMPACT" override (sump_wretch now; the new
mire_wraith row must carry it too). 0.6 tiles was tried first: the splash sat at the lens, clamped tiny - 1.1 reads.
The Warden has no IMPACT: unchanged. Verified in-engine with a TEMPORARY capture-only row MIRE_WRAITH -> sump_wretch
(removed, never committed). PRE-EXISTING bug found (not fixed, out of scope): EnemyManager.chainAlertQueue is sized to the
floor's STARTING enemy count, so several dormant enemies added later that wake in one turn overflow it
(ArrayIndexOutOfBoundsException in phaseA) - capture spawns hit it; capture scripts alert spawns by index instead.
Build: only StoryBarkTest red. Designer delivered .claude/agents/ideas/sump-wretch-acid-death.txt (separate work).

**CP5b:** the NEW Mire Wraith: `mire_wraith_rig.py` (seed 47) - a shrouded sludge predator: a lamprey maw (the spout =
muzzle anchor at MAW (80, 94)), a veined acid gullet under it, hooked arms (left down in the mire, right raised), a ragged
skin shroud (flat slabs), ribs + acid veins, the S4 hose on the left. 95 frames, 2048x2048, no edge warnings. Clips: the
standard set + DEBUFF_PLAYER (volley, "emit" at 0.22). Lessons: the column first read as a stack of tyres - one tapering
chain merged INTO the torso layer (12.1); tube drapes read as extra arms - flat bevelled slabs read as cloth; lowering a
raised forearm must fold it INWARD (negative el) or the hand leaves the canvas. Effects: creatures row `mire_wraith`
(overrides incl. IMPACT -> ACID_SPLASH). Fresh bake == shipped; other sets unchanged. Captures used a TEMPORARY row
MIRE_WRAITH -> mire_wraith (removed, not committed). Sent: stills (early), clip GIF + strip, in-engine GIFs (default +
amber_hot, a 2-tile close-up and the full reaction run). WAITING for owner approval (CP5c).

**CP5c:** owner approved the new Mire Wraith (D5, "Now I like everything, you have my approvals to continue" - the same
message approves .claude/agents/ideas/sump-wretch-acid-death.txt, separate work). Row MIRE_WRAITH -> mire_wraith committed.
Re-run on the committed state: all four aberrations animated (ALERT clip), Wraith ATTACK + DEBUFF_PLAYER, no exception.

**CP6a (docs):** guide (2, 4.3, 4.4b wet sheen + glow ceiling, 7.3, 7.6 API, 7.7, 7.9, 8.2 order-5 rows + MOVE phase, 8.7
shot/impact, 9.1 aberration set, 9.2 burst ids, lessons 12.17-12.20, recipe), art-direction 16 (rows, roster, G13 number),
enemy-system (order 5 + the chainAlertQueue finding), enemy-art + run-game SKILL.md, CLAUDE.md enemyvisual row; idea
IMPLEMENTATION NOTE, STATUS IN PROGRESS. Next: reviewer, then STATUS IMPLEMENTED.

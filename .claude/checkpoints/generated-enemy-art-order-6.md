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
- [x] **CP1** — kit: humanoid rig helper, cloth/suit/skin materials appended, soul ink as the UNDEAD family ink, fold
      helper, cloth lag. DONE WHEN: every earlier creature + fx set regenerates byte-identical.
- [x] **CP2a** — five rigs at rest pose (concept stills, owner override applied); family sheet + one image per enemy
      SENT. DONE WHEN: `--stills` renders all five with no edge warning and the images are sent.
- [x] **CP2b** — the owner's concept read recorded in DECISIONS (A1); requested changes applied. STOP until it is.
- [x] **CP3** — all clips + ability clips (DEBUFF_PLAYER, SUMMON) + the `undead` effect set; clip GIFs + in-engine burst
      GIFs (default + one mood) SENT. DONE WHEN: sent; build green.
- [x] **CP3b** — the Revenant redesigned after the owner's clip-gate feedback (D5: no club; riot shield + shield bash);
      re-baked, in-engine re-checked, clip + in-engine GIFs sent. DONE WHEN: sent; others byte-identical.
- [ ] **CP4** — owner approval of all five recorded (stop until it is).
- [ ] **CP5** — five registry rows + SUMMON clip row wired; A3-A5 verified in-engine; build green.
- [ ] **CP6** — docs (guide, art-direction 16, SKILL.md, enemy-system, CLAUDE.md row); reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

**CP0:** baseline 018390c. Facts found: VORTEX_EYE is NOT hovered by the renderer (only EYE_TYRANT) -> bake its hover
into the canvas. REVENANT has heavyWindUp CHARGE (a real WIND_UP in the sim). VORTEX_EYE is ranged (DEBUFF_PLAYER) ->
needs PROJECTILE/TRAIL/MUZZLE in the undead set. BLIGHT_CORRUPTOR uses SUMMON - no EnemyClipId/abilityClip row yet.
`soul` (emissive) and `cloth`, `rubber` already exist in MATERIALS (wall-art order 1) - reuse them.

**CP1:** kit.py APPENDS eight materials after key_blue: overalls (slate suit), hazmat (olive-grey rubber), labcoat (off-white
cloth), visor (smoked glass, glossy), deadskin (ash grey: torn cuffs / split seams only, never a face), spore (translucent,
glow=soul), redeye (emissive: the Ghoul's eyes - owner exception to G5). `soul` (wall-art order 1) is the UNDEAD ink.
Helpers: `fold_lines(x1,y1,x2,y2,count,length,...)` (carved creases), `Humanoid` (one skeleton: joints(p) -> named points
from MIRRORED joint angles, + = outward, children relative; `Humanoid.params(**defaults)`; a NEGATIVE spine/neck/head hangs
it upside down = a body on all fours seen head-on - the Crawler), `hand(wrist, angle, side, length, mat, fingers,
finger_length, curl, ...)`, `lag(clip, param, delay, duration)` (cloth that follows its limb late). Verified: bestiary +
effects regenerated into the scratchpad, all 32 shipped files byte-identical (cmp).

**CP2a:** five rigs, rest poses only, five CREATURES rows with EMPTY clip lists (do NOT run the default bake for them before
CP3): `ghoul_rig.py` (seed 53) = THE owner's tall thin gas-masked figure (slate hazard suit, hood, ONE panoramic visor,
red eyes, arms to the knee, 29 px gloved fingers, badge "14" on a lanyard); `crawler_rig.py` (61) = Humanoid with a
NEGATIVE spine (head-on all fours), lab coat, clean-room hood, two-lens respirator, the coat split down the spine leaking
soul; `revenant_rig.py` (59) = riot armour, one cracked visor, soul eyes, domed pauldrons, baton, soul breach in the chest
plate, stencil S-07; `vortex_eye_rig.py` (67) = isolation-helmet bust (oval window, two eye ports pouring soul), torn
coat hem, 3 uneven torn soul strands (hover baked: bust ~y40-150, vortex to ~y200); `blight_corruptor_rig.py` (71) =
olive-grey hazmat, two-lens mask, spore pods riding skeleton joints (own layer, in front of the left arm), decon tank
+ wand, stencil Q. Decision taken: the Ghoul is the owner's tall gas-mask humanoid (the humanoid-rig showcase, slow
grabber with long fingers; its 0.90 walls makes it fill its canvas top to bottom). The hazmat ramp was desaturated after
the CP1 cmp - a new material no earlier sprite uses. A1: the owner's own instruction ("send the image of each enemy
before animations") replaces the idea's Ghoul-alone early read; all five concepts were sent together.

**CP2a (revision):** owner asked for a LONG gas mask on the Ghoul (D3; a reference image given, used loosely - no chest
device, not a copy). `ghoul_rig.py`: SNOUT_END = 30 (head space): a ribbed rubber snout (6 raised rings) to a gunmetal filter
canister, the hose now leaves the canister; taped cuffs (darkmetal rings) at glove and boot tops; badge moved to cy+24.
Revised Ghoul + family sheet sent. Still WAITING for the concept approval (CP2b).

**CP2b:** owner approved all five concepts (D4). Next: CP3 clips. The rest poses ARE the approved stills - a clip may move
them but the defaults must not change.

**CP3:** all clips + effects + the one runtime change. Frames / atlas: ghoul 93 (1024x2048), crawler 64 (1024x1024),
revenant 77 (1024x2048), vortex_eye 97 (1024x1024), blight_corruptor 96 (1024x2048); NO edge warnings. Ability clips:
Vortex Eye DEBUFF_PLAYER (fps 16, "emit" at 0.28) + ATTACK ("emit" 0.12); Corruptor SUMMON (0.6 s, pods split via `split`).
Rig additions (rest poses byte-identical to the approved stills, checked with --stills + cmp): kit `Figure.transformed(xf)`
(no earlier output changes); Ghoul + Revenant `fall` (DEATH collapse about the ground line); Crawler NARROW 0.90 + lift 2 px
applied to the WHOLE figure incl. rest (its rest spanned x 8-154 with fingers under the ground line, so every clip clipped -
a deviation from the approved still of 10% width, lesson 12.20, to be named in the IMPLEMENTATION NOTE); Vortex Eye coat
strips shrink as it unravels; Corruptor's left arm drawn in front of the pods when raised / forward. Revenant raise: arm
straight up (r_sh 2.85) with the baton laid back over the helmet; the swing passes foreshortened (arm_fore) and the baton
angle is keyed continuously (-5.53 = 0.75 - 2pi) so it never sweeps off the right edge.
Effects: `undead` set (14 sprites, 19 bursts) + five creatures rows; overrides: ghoul LEAK -> LEAK_EMBER (red), revenant
RESIST -> RESIST_ARMOUR + CHARGE -> CHARGE_BATON, vortex_eye PROJECTILE/TRAIL/MUZZLE -> SOUL_BOLT/BOLT_TRAIL/BOLT_MUZZLE,
blight_corruptor HIT/HIT_HEAVY/DEATH/LEAK/CHARGE -> spore bursts. The runtime loads it by family name (no code change).
RUNTIME (name in the IMPLEMENTATION NOTE): EnemyClipId.SUMMON + abilityClip(SpecialAbility.SUMMON, SUMMON). Nothing else.
Fresh bake of everything: the 32 earlier files unchanged (git clean on them).
Build (mirror): only StoryBarkTest.humourLivesNearTheSurfaceAndDiesNearTheCore red (known).
In-engine (TEMPORARY rows GHOUL/CRAWLER/REVENANT/VORTEX_EYE/BLIGHT_CORRUPTOR, removed again - CP5 adds them): every creature
showed dormant, ALERT, MOVE, real ATTACK (logmoments), HURT_LIGHT, HURT_HEAVY, WOUNDED + leak, BURNING, DEATH -> remains;
Revenant real WIND_UP (its lunge); Vortex Eye real ABILITY (DEBUFF_PLAYER) + bolts; Corruptor real SUMMON (summoned dead
arrive with ALERTED). A4 line-up at 3 tiles in default / corrupted_violet / core_white_violet; A5 soul reads cold white.
Tools (scratchpad, not committed): tools/gen.py, runall.sh, runmood.sh, lineup.py, assemble.py, clipgif.py.
Sent: labelled clip GIFs, in-engine GIFs default + corrupted_violet, the 3-tile line-up. WAITING for owner approval (CP4).

**CP3b (owner D5):** the baton is gone. First attempt kept it and fixed the arm (rigid grip, `r_upper_fore`, `baton_fore`,
a solid `_fist`) - rejected: "the right hand is cursed", no club. LESSON (12.21 for the guide): an articulated hand gripping
a long held object at this resolution breaks - fingers lump on a turned wrist, and an independent prop angle spins through
the fist between keys; and shortening a WHOLE arm toward the camera crushes it into a stub. Give a humanoid a weapon that
is a rigid plate moved with the body (a shield), or none.
Now: `revenant_rig.py` - `_shield(p)` (chamfered smoked-polycarbonate plate in a gunmetal rim, brace + boss, scars, RIOT
stencil) moved as ONE part by shield_x / shield_y / shield_scale (toward the viewer) / shield_tilt, SHIELD_REST (48,146),
SHIELD_HALF (20,37); the left hand solved to sit behind the shield's centre in every key; right hand = `_fist` (one closed
gauntlet, never articulated). Keys _BRACED / _DRAWN / _SLAMMED; the S-07 chest stencil is dropped while the shield covers
the chest (12.12). Kit: Humanoid gains `l_upper_fore` / `r_upper_fore` (0 default - no earlier output changes; unused by
the shield design, kept as a documented skeleton option). effects.py: CHARGE_BATON removed, the Revenant's charge = the
family motes at the shield. Revenant 62 frames, atlas 1024x2048, no edge warnings. Re-bake: only revenant.* and
undead.fx.json changed. In-engine (temporary row, removed): real WIND_UP (braced), real ATTACKs (the bash), hurt, wounded,
burning, death - default + corrupted_violet. WAITING for owner approval (CP4).

**CP3c (pending owner approval):** owner: the Ghoul "is too thin and looks weak" - thicker, no muscles; show a single image
first. `ghoul_rig.py`: BULK = 1.35 on limb / torso / joint / boot / cuff radii, fingers 1.45 + palm 4.0, shoulder_half 17.5,
hip_half 9.5, belt and hip band widened. Rest still sent (before/after). COMMITTED UNBAKED: the shipped ghoul.* atlas is
still the thin one. On approval: rebake ghoul, re-check in-engine, send GIFs; on rejection: adjust BULK.
Owner: "better, but a little more thicker legs, just a bit" -> LEG_BULK = 1.55 on the legs only (hips, knees, shins,
boots), hip_half 10.5. Still re-sent; still awaiting approval, still UNBAKED.
Owner: "even thicker legs and arms and body, the body 10-15% longer, the hands 10-15% longer" -> BULK 1.6, LEG_BULK 1.8,
spine 58 -> 65, upper_arm/fore_arm 44/42 -> 50/47 ("hands" read as the ARMS, as in the first brief; fingers unchanged),
pelvis y 112 -> 118 with thigh/shin 58/60 -> 55/57 so the head keeps its place, shoulder_half 19.5, hip_half 12; the hip band
and the pelvis end of the lower torso narrowed (they swelled into a waist bulge). Still re-sent; awaiting approval; UNBAKED.

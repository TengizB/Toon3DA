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
- [x] **CP1** — effects.py writes machine + insect effect atlases and manifests.
      DONE WHEN: `python3 effects.py <dir>` writes `machine.fx.png` / `insect.fx.png` + `.fx.json` deterministically
      (two runs byte-identical), and `bestiary.py` still regenerates warden.* / harvest_tick.* byte-identical.
- [x] **CP2** — EnemyEffectSystem draws hit effects at the hit anchor in-engine; build green.
- [x] **CP3** — outcomes (hurt/resisted/blocked/kill), recoil, wounded idle + leak work for both creatures.
- [x] **CP4** — status visuals; remains; projectile + muzzle; telegraph effects.
- [x] **CP5** — burst GIFs sent; owner approval recorded (stop until it is).
- [ ] **CP6** — performance measured; docs; reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

**CP0:** baseline 649c7a6.

**CP1 (kit):** `.claude/skills/enemy-art/effects.py` writes THREE sets into `assets/textures/enemies/generated/`:
`machine.fx.*`, `insect.fx.*` (one per EnemyFamily, file = family enum name lower-case) and the shared `status.fx.*`
(fire / smoke / toxin bubble / dazed stars — semantic colours, V5). FX MANIFEST (`FX_VERSION 1`): `sprites` {NAME:
emissive, mode life|loop, fps, frames[orientation][frame] {rect, pivot}}, `bursts` {NAME: {emitters[...], interval?}},
`creatures` {creature id: {leak|burn|charge|stun|poison|body: {anchor, offset, radius}}}. Emitter units are CREATURE
CANVAS PIXELS (speed px/s, gravity px/s^2 + = down, angle deg 0 = right 90 = up, toward px/s to the viewer); the
runtime converts with the creature's px scale (heightMultiplier / canvasHeight tiles per px). Burst names the runtime
reads: HIT, HIT_HEAVY, RESIST, KILL, DEATH, LEAK, CHARGE, MUZZLE, PROJECTILE, TRAIL, BLEED (family) and BURNING,
BURNING_ONSET, POISONED, STUNNED (status). EMISSIVE sprites keep only their emissive pixels (no outline) -> drawn
ungraded NORMAL blend (D2 rule). Two materials APPENDED to kit.MATERIALS (`flare` white-hot, `toxin` poison green):
appended last, so every index is unchanged — warden.* / harvest_tick.* regenerated byte-identical (checked), and two
effects.py runs are byte-identical. Anchors available per frame: ground, hit_centre, eye, emitter (muzzle|mouth).

**CP2 (runtime, verified in-engine):** NEW `render/enemyvisual/EnemyEffectSet` (fx manifest + atlas; `BurstId`,
`PlacementId`), `EnemyEffectSystem` (320-slot particle pool, oldest recycled; world point = tile x/y + height in walls;
`emit(burst, x, y, h, tilesPerPixel, radiusPx, angleBias)`, `launch(...)` for projectiles + trail; orbit for the dazed
stars), `RemainsRegistry` (32 slots, one per tile, revealed when DEATH ends), `EnemyStatusVisuals` (status rows). The
sets are loaded by `EnemyAtlasCache.attachEffects` (family = `EnemyFamily.name().toLowerCase`). EnemyRenderer draws
particles in `drawEffectParticles()` right after pass 1 (emissive: ungraded, NORMAL blend, glowAlphaAtDepth; lit:
body shade + grade), drawn at `ENEMY_EFFECT_DRAW_SCALE` 2x the creature's pixel (1x read too small in-engine).
NOTE: CP2's commit already carries most of CP3/CP4's code (the layer was written in one piece): HitReaction rows,
recoil, flash suppression, shimmer, WOUNDED, dazed, status particles, remains + corpse-decal suppression
(`PropRenderer.setRemainsRegistry`, dynamic 'm' only), generated projectile (`EnemyAttackEffectSystem.setGeneratedProjectiles`),
legacy screen-space hit spray suppressed for migrated bodies (`ImpactEffectSystem.setBodyEffectsOwner` +
`EnemyVisualLayer.answeredHitAt`), splash origin seam `EnemyHitTarget.setVisualBlastOrigin` (grenade / barrel / relic).
CP3 / CP4 are their VERIFICATION + fixes. Capture steps added: `status:INDEX:TYPE:TURNS` (enemy), `block:INDEX:AMOUNT`
(forced), `hit:INDEX:AMOUNT:CLASS` (BALLISTIC = RESISTED on both), `fx`. Scratch helper: scratchpad `cap.sh` sorts
steps by time. TRAP: after a kill, enemy indices shift — address the survivor with `-1`/new index.
Seen in-engine (t3): light/heavy sprays on both, RESIST star (Warden) / chip (Tick), BLOCKED shimmer on both, Tick
WOUNDED, burning flames under the Warden, stun stars on the Tick, Warden kill burst + remains. Build: only StoryBarkTest red.

**CP3 (verified in-engine, t4-t7):** heavy hit = HURT_HEAVY + spray + visible recoil on both; WOUNDED clip chosen
below 50% (`clip:` printed WOUNDED for both); Tick burst blister + drips; Warden LEAK recipe bumped (2-4 sparks + an
arc, 0.30-0.80 s — 1-3 short sparks were invisible). REAL weapon reactions (capture `logmoments` proves them):
`equipmelee:WEAPON_HAMMER` + FIRE on a Tick 1 tile ahead -> KNOCKED_BACK on the 3rd swing (50% roll); `equip:0`
(shotgun) + FIRE on a Tick 2 tiles ahead -> DAMAGED EFFECTIVE + STAGGERED. The shotgun one-shots a Warden (45 HP), so
stagger is shown on the Tick. FOUND: a stagger cancels the action of THAT turn (skipNextAction consumed at once) and
the enemy then commits a fresh WIND_UP, so a stagger reads as the heavy flinch + heavy kick; the DAZED hold is the
STUNNED status (or a still-pending skipNextAction) and now wins a tie with a held WIND_UP (no crackle while dazed).
New capture steps: `equipmelee:ITEM_TYPE`, `logmoments`.

**CP4 (verified in-engine, t8-t10):** Warden ATTACK_COMMITTED -> muzzle flash at the prod tip (EMITTER anchor) on the
emit frame + the cyan glob with dripping trail flying to the player; the procedural glob is gone for ACID_DRONE. FIX:
near the lens the glob blew up into giant blocks — effect sprites now stop growing inside
ENEMY_EFFECT_MIN_SIZE_DEPTH_TILES (1.2) and a projectile fades 1 - t^2 (the legacy glob's curve). Remains: Warden
and Tick carcasses stand on their tiles after DEATH (decal suppressed). Statuses: BURNING flames + smoke (Warden under
the hull, Tick along the back), STUNNED stars orbiting the Tick's head; POISONED not shown (Warden is immune; Tick
row exists). Telegraph: Tick WIND_UP throws grit + dust at its feet (recipe brightened: bone grit + DUST — chitin_dk
grit was invisible on the floor); BURNING flame count 2-3. The Warden never winds up in the sim (no heavy), so its
prod crackle is data-ready but not seen. Build: only StoryBarkTest red.

**CP5a (gate prepared):** owner GIFs in the scratchpad `gifs/`: warden_default / warden_amber_hot (hurt light, hurt
heavy, resisted, blocked, wounded + leak, attack projectile, stunned, kill, remains, burning) and harvest_tick_default /
_amber_hot (the same outcomes + real shotgun STAGGER, real hammer KNOCKBACK, burning). Scripts: scratchpad
warden.txt / tickA.txt / tickB.txt run through cap.sh (time-sorted). SKILL.md documents the new capture steps.
WAITING for owner approval; CP5 is ticked only when the answer is in the idea's DECISIONS.

**CP5 (gate):** owner approved 2026-10-21 ("Approved") -> idea DECISIONS D2. Proceed with CP6.

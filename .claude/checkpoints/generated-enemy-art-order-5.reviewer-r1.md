# REVIEWER r1 - generated-enemy-art-order-5 (the Aberrations)

VERDICT: PASS WITH FIXES

Spec:    .claude/agents/ideas/generated-enemy-art-order-5.txt -> A1 met · A2 met (RESISTED unreachable, recorded) · A3 met · A4 met · A5 met · A6 met
Build:   reviewer's own run, mirror `./gradlew -q :core:build` at d02528f -> compiles; "481 tests completed, 1 failed",
         and the one failure is the known pre-existing `narrative.StoryBarkTest.humourLivesNearTheSurfaceAndDiesNearTheCore()`.
         balanceSim: not owed, because nothing balance-bearing changed (no BalanceConfig/BalanceSchema/GameBalance/sim; the new GameMath methods are cosmetic).
Bake:    reviewer's own fresh `bestiary.py` + `effects.py` into the scratchpad -> all 32 generated files byte-identical to
         assets/textures/enemies/generated/. That covers warden.*, harvest_tick.*, iron_stalker.*, machine/insect/status .fx.*
         (none appear in the git diff) and the 17 new files.
Scope:   7a3970d..d02528f, excluding 0a99ab0. Java: EnemyRenderer, enemyvisual/{DyingEnemyPool, EnemyAnimationRules,
         EnemyAnimator, EnemyClipId, EnemyEffectSet, EnemyEffectSystem, EnemyVisualLayer, EnemyVisualRegistry,
         RemainsRegistry}, util/{EffectConstants, GameMath}. Skill: kit.py, bestiary.py, effects.py, five *_rig.py. Docs:
         sprite-art guide, art-direction 16, enemy-system, both SKILL.md files, CLAUDE.md. No changes to enemy/, sim/ or tests.
Ckpts:   .claude/checkpoints/generated-enemy-art-order-5.md -> CP0..CP5c are ticked and each has its own
         `generated-enemy-art-order-5/CPn:` commit with the tick inside it. CP6 is correctly unticked, and CP6a (docs) is
         committed. STATUS is still IN PROGRESS, which is correct. The handover notes give reasoning (arm-swing lesson,
         0.6 vs 1.1 lead, temporary capture rows removed). Discipline is good.

## Acceptance criteria
- A1: D1-D5 record both gates. The rejected design was never wired: it was kept as `sump_wretch`, has no registry row, and
  its separate idea file covers it. The redesign was re-gated (D5) before its row landed in 933fd24.
- A2: The captures cover every standard clip for all four: dormant, alert, the t1..t9 moves and attacks, wind-up offline,
  hurt light/heavy, blocked, defend, wounded, burning, stunned, death and remains. Ability clips were seen in the logs
  (ABILITY_USED -> DEBUFF_PLAYER / AREA_STRIKE / SELF_DESTRUCT). RESISTED cannot happen in the sim: the FLESH trait is
  neutral. This is recorded under FINDINGS and is a fact about the sim, not a gap in the work.
- A3: per-enemy MOVE phase (`movePhaseSeconds`, from the same id-spread as the idle phase) is in place, and three biters
  were captured.
- A4: in hB_prime_vs_defend the brace (arms over the face) sits beside the primed Hulk (swollen, collar strained, ichor
  glow), and they read clearly differently. The log shows `cause=SELF_DESTRUCT`, then DEATH_BURST and the DETONATE chunks,
  then the burst remains. The primed Hulk killed by a weapon logs `cause=WEAPON`, which is the normal DEATH (the edge case).
- A5: in eT_t3 the beam leaves the pupil, and the body hovers with its tendrils clear of the floor. The death sequence eases
  down to the floor with no pop.
- A6: an HSV scan of all new atlases finds the only high-saturation pixels on the existing ichor ink ramp entry. The same
  holds for harvest_tick and iron_stalker. There are no saturated-red body pixels. Earlier creatures are byte-identical,
  and the build is green apart from the known failure.

## Owner constraints / rules
- kit.MATERIALS: five entries added after `toxin`, the last one. Nothing was reordered.
- The new `aberration` set has a creatures row each for plague_hulk, gore_biter, mire_wraith and sump_wretch. The Eye
  Tyrant correctly keeps the renderer's beam.
- Order 4's `abilityClip` rows are reused (2 new rows), and the death-by-cause rule is a data row (`death(...)`).
- All 9 runtime changes are named in the IMPLEMENTATION NOTE. Each is small, all are render-side, and there is no sim
  write-back (G9).
- Wiring is four `register()` rows, landed after the gates (three in CP5, the Wraith in CP5c).
- Testing Policy: no test files were touched.
- No `new` in render paths. The dying and remains clip fields are plain fields on pooled objects.

## BLOCKING
None.

## REQUIRED FIX (small, before STATUS: IMPLEMENTED)
1. core/src/main/java/ge/tbegvadze/toon3d/render/enemyvisual/EnemyEffectSystem.java (launch(), impact branch,
   ~line 429): the Euclidean distance is computed inline (`(float) Math.sqrt(...)`), but `GameMath.distance(fromX, fromY,
   toX, toY)` already exists (GameMath.java:308). CLAUDE.md says no formula inline in game code. Replace it with
   `GameMath.distance(fromTileX, fromTileY, toTileX, toTileY)`. This is behaviour-identical, and a build is enough to
   verify it.

## NON-BLOCKING (should fix / note)
1. EnemyAnimator.advance(), the HOLD-mode one-shot rule. During a self-destruct countdown, a HURT one-shot (priority 6)
   replaces the held SELF_DESTRUCT one-shot (priority 5). When the HURT finishes, `oneShotClip` becomes null and the body
   falls back to the PRIMING hold's last frame. So a Hulk shot mid-countdown visibly deflates one swell level until the
   next countdown turn re-plays SELF_DESTRUCT. This is cosmetic only and corrects itself on the next turn. Either accept it
   and record it, or remember the last held ability clip so it can be restored when a higher one-shot ends.
2. EnemyRenderer, the dying hover: `dying.type == EnemyType.EYE_TYRANT` is a type check, not data. It matches the existing
   live-hover check, so it is consistent with the file. If a second hovering type ever arrives, move "hovers" into data.
3. EnemyAnimator, hold replacement: a hold that REPLACES another now always starts at frame 0. This affects every creature,
   for example DEFEND -> WIND_UP when the state changes with no moment. It is cosmetic and arguably better, but it is wider
   than the IMPLEMENTATION NOTE's wording ("a hold that replaces a showing hold starts at frame 0") makes it sound. The note
   is accurate; this is just flagged.
4. The `sump_wretch` atlas and glow (~1.9 MB) ship in assets/ unwired until its own idea lands. The owner approved this
   (D3), so it is not a finding against this order. It is noted for APK size.

## NITS
1. EnemyEffectSystem.update: `float tileX = ..., tileY = ..., height = ...` declares several variables on one line. This
   is legal, but the rest of the file uses one declaration per line.

Assign to: libgdx-specialist (required fix 1; non-blocking 1 if taken)

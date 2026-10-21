# reviewer r1 — generated-enemy-art-order-3 (reactions and effects)

VERDICT: FAIL (two blocking defects, both in the projectile path and fixed together; re-review covers only that path plus the TOC)

Spec:    .claude/agents/ideas/generated-enemy-art-order-3.txt → A1 met (D2 owner approval) · A2 met (captures t4-t7) ·
         A3 met (BURNING + STUNNED captured; POISONED not shown, Warden is immune, recorded) · A4 met (decal skip in
         PropRenderer, remains revealed when DEATH ends) · A5 met for the PLAIN ranged attack only, see #1 and #2 ·
         A6 met (11.42/11.25 vs 11.27/11.52 ms; gclog shows no pause after floor start) · A7 mostly met: the build was
         reported green, and guide 8 + 9, art-direction 16, enemy-system and the CLAUDE.md row are written, but the
         guide CONTENTS still says [PLANNED] (NB1)
Build:   orchestrator's run: green except the known pre-existing narrative.StoryBarkTest. I did not re-run it, because
         nothing in the diff is outside what that run compiled. balanceSim not owed: no balance-bearing file was touched.
Scope:   every changed .java file (enemy/EnemyManager, entity/EnemyHitTarget, entity/GrenadeLauncher,
         hazard/ExplosiveBarrelManager, render/EnemyRenderer, render/PropRenderer, render/ImpactEffectSystem,
         render/EnemyAttackEffectSystem, render/enemyvisual/*, util/EffectConstants, EnemyConstants, RenderConstants,
         world/World); fx manifests (structure checked); the effects.py seeding; kit.py append; skill docs; docs/*.
Ckpts:   .claude/checkpoints/generated-enemy-art-order-3.md → CP0-CP5 ticked, each with its own commit
         (CP5a, CP6a as sub-commits). CP6 is correctly left open. STATUS is IN PROGRESS (correct).
         The CP2 commit carried most of CP3/CP4's code. The handover note says so honestly, and CP3/CP4 are its
         verification + fixes. Not a finding, but a cut after CP2 would have left unverified code behind a tick.

R1/G9:   held. The layer only reads Enemy (state, health, skipNextAction, the status EnumMap). The only sim-side edits
         are the setVisualBlastOrigin seam, which nothing but the visual payload reads. enemy/ gains no render/ import.
Tests:   none added, changed or removed. Correct (G12, Testing Policy).
Alloc:   no `new` in render()/update(). Pools are fixed (320 particles, 32 remains, 8 dying, 64 animators), and the
         oldest slot is recycled. The sort scratch is sized 8+32 and guarded.
Dispose: effect-set atlases are disposed in EnemyAtlasCache.dispose(). A failed load disposes its atlas. World nulls the
         impact owner before it disposes the layer.

BLOCKING (must fix)
1. render/EnemyAttackEffectSystem.java:90 + render/enemyvisual/EnemyAnimationRules.java:195 — the Warden's DEBUFF
   shot draws NO projectile at all. ACID_DRONE's moveset is MOVESET_DEBUFF (enemy/EnemyType.java:153).
   enemy/EnemyManager.java:2304 executeDebuffPlayer calls onRangedAttack WITHOUT an ATTACK_COMMITTED moment: it fires
   ABILITY_USED, which is a plain ONE_SHOT row, so scheduleShot never runs. But line 90 still suppresses the procedural
   glob for the whole type. When it happens: any turn the Warden resolves DEBUFF_PLAYER. The player is SLOWED or
   BLINDED by a shot that is never drawn. In order 2 that shot drew the legacy glob, so this is a regression of a
   readability cue. Fix: see #2 (one change fixes both).
2. render/enemyvisual/EnemyVisualLayer.java:157-159 (scheduleShot) — the generated glob flies to where the player
   stands NOW (viewerWorldX/Y). The legacy glob flew to the COMMITTED lane target, plan.targetColumn/Row, which
   EnemyManager.java:2012 passes to onRangedAttack. The tick fires from PlayerController.finishAction, after the move;
   World.java:2712 then 2774 means the viewer is already at, or nearly at, the new tile. When it happens: the Warden
   telegraphs a shot, and the player strafes off its row or column to dodge (the core cardinal-line read). The sim
   resolves a miss, but the glob arcs diagonally into the player's new tile and appears to hit. The 3D view should
   never draw an off-lane shot (CLAUDE.md Design Constraints / isSameCardinalLine). This is from reading the code; I
   found no capture of a dodge.
   Fix for #1 and #2 together: drive the launch from the attack listener. In EnemyAttackEffectSystem.onRangedAttack,
   when the layer owns the type, call `generatedProjectiles.scheduleShot(enemy, targetColumn, targetRow)` before
   returning. That means a public variant taking the target TILE: aim at the tile centre, keep the emit-frame delay
   when the ATTACK clip is the one playing, otherwise leave at once. Remove the scheduleShot call from Kind.ATTACK.
   Then every path that used to draw a glob draws the generated one, aimed down the lane. Verify with one capture:
   the Warden is aligned, the player strafes off the lane on its firing turn, and the glob travels along the
   original lane.

NON-BLOCKING (should fix)
1. docs/sprite-art-creation-guide.txt:28-29 — the CONTENTS still marks sections 8 and 9 [PLANNED: orders 2-3] /
   [PLANNED: order 3]. The section headings themselves were updated (lines 365, 454). The guide's own rule (line 11)
   is that the shipping order removes the marker. Drop both. (Line 164's "[PLANNED: order 2] drawn additively" is an
   order-2 leftover, and it now contradicts D2: glow is a normal-blend overlay. Fix it in the same edit.)
2. render/enemyvisual/EnemyAnimator.java:283-295, EnemyEffectSystem.java:139-149, EnemyRenderer.java:1781-1785 —
   these formulas are written inline: the recoil ease-out (1 - t)^2, the dazed sway sine, the tail fade, the
   projectile fade 1 - t^2, and the orbit. CLAUDE.md wants each in GameMath with its derivation block. They are
   trivial and cosmetic, but order 2 used GameMath.smoothstep01 for the same kind of curve. Move the three
   envelopes (ease-out quadratic, tail fade, projectile fade) into GameMath.
3. render/EnemyRenderer.java:871 — `(1f - progress * 0.5f)` is a hardcoded shimmer decay. Make it an EffectConstants
   ENEMY_VISUAL_SHIMMER_* entry.
4. render/enemyvisual/EnemyAttackEffectSystem.java:90 + EnemyVisualLayer.drawsProjectileFor — the suppression is
   decided per TYPE, but the generated shot needs an ANIMATOR. If the 64-slot animator pool is exhausted, a Warden
   drawn as a legacy sprite has its glob suppressed and gets no replacement. The fix for #1/#2 removes this if the
   layer's scheduleShot returns false when there is no animator, and the glob falls back.

NITS (optional)
1. render/enemyvisual/EnemyAnimator.java:82 — `java.util.Arrays.fill` is written fully qualified. Import it.
2. RemainsRegistry is capped at 32 per floor, with the oldest recycled (R4). Past 32 migrated kills on one floor, the
   oldest tile shows the generic 'm' decal again. That is acceptable under R4. Mention it in guide 8.6 so nobody
   reads it as a bug.
3. A remains billboard keeps whatever recoil or sway offset was easing out at the death frame
   (EnemyVisualLayer.java:176). It is sub-tile and cosmetic.

Assign to: libgdx-specialist (B1+B2 as one change in EnemyAttackEffectSystem + EnemyVisualLayer, NB2, NB3, NB4),
           main thread (NB1 docs)

# reviewer r2 — generated-enemy-art-order-3 (reactions and effects)

VERDICT: PASS

Spec:    .claude/agents/ideas/generated-enemy-art-order-3.txt → A1 met · A2 met · A3 met · A4 met ·
         A5 met (every ranged shot, the debuff shot included, is drawn as the generated glob down the committed
         lane; in-engine t12) · A6 met · A7 met (the guide contents and the 3.11 glow line are corrected)
Build:   orchestrator's run on ee34d5f: green except the pre-existing narrative.StoryBarkTest. Not re-run: the fix
         diff is small and fully covered by that run. balanceSim not owed: the new GameMath methods are cosmetic
         curves, not balance-bearing formulas.
Scope:   diff 294f5b0..ee34d5f, i.e. EnemyAttackEffectSystem, EnemyVisualLayer, EnemyAnimationRules, EnemyAnimator,
         EnemyEffectSystem, EnemyRenderer, EffectConstants, GameMath, docs/sprite-art-creation-guide.txt and the
         checkpoint file. Everything else was accepted in r1.
Ckpts:   .claude/checkpoints/generated-enemy-art-order-3.md → CP0-CP5 ticked, each with a commit. CP6b (ee34d5f) has
         a handover note giving the reasoning. CP6 stays open until the orchestrator sets STATUS: correct.

r1 BLOCKING 1 (debuff shot invisible) — RESOLVED. The shot is now scheduled from
  EnemyAttackEffectSystem.onRangedAttack, the one listener that every ranged path calls (EnemyManager.java:2012 and
  :2308), through EnemyVisualLayer.scheduleShot. Both paths set ATTACK as the one-shot before the listener runs
  (ATTACK_COMMITTED / ABILITY_USED rows → ONE_SHOT ATTACK), so the emit-frame delay applies to both. The delay is
  measured from the one-shot's elapsed time.
r1 BLOCKING 2 (dodged shot homes onto the player) — RESOLVED. pendingTarget is now the committed target tile's
  centre, not the viewer's position. A strafe off the lane was captured in-engine (t12): the glob flies down the old
  lane and misses.
r1 NB4 (animator pool exhausted) — RESOLVED. scheduleShot returns false without an animator or a PROJECTILE burst,
  and the procedural glob is then drawn. drawsProjectileFor is deleted.
r1 NB1-NB3, nits — RESOLVED. The contents markers now read "shipped". The 3.11 line now says normal-blend glow,
  matching D2 (the [PLANNED: order 6] at line 183 belongs to a later order and is correct). 8.6 now notes the
  remains cap. Five curves moved into GameMath with derivation blocks. ENEMY_VISUAL_SHIMMER_DECAY replaces the 0.5f.
  Arrays is imported.

BLOCKING
  none.

NON-BLOCKING
  none.

NITS (optional)
1. render/enemyvisual/EnemyVisualLayer.java (scheduleShot / release): a shot is still pending if the Warden is
   killed between its commit and the emit frame. That gap is under one ATTACK clip, shorter than the clip itself. In
   that case the animator is released and the glob is never drawn, because scheduleShot already returned true, so
   the procedural fallback does not fire either. This is only reachable when the player acts inside that fraction of
   a second, and the hit itself still lands and shows. Optional fix: launch the pending shot at once from
   beginDying.

The orchestrator may now set STATUS: IMPLEMENTED with today's date and tick CP6.

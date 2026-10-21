VERDICT: PASS

Spec:    .claude/agents/ideas/generated-enemy-art-order-4.txt -> A1 met (D1/D2, owner quote recorded) · A2 met
         (in-engine, main thread's captures; DEFEND shown forced and declared as such) · A3 met (in-engine frame,
         STAND x1.065 recorded in the IMPLEMENTATION NOTE + guide 12.14) · A4 met (see Build)
Build:   ./gradlew :core:build via the run-game mirror (main thread's run): 481 tests, only the known pre-existing
         StoryBarkTest.humourLivesNearTheSurfaceAndDiesNearTheCore red. Not re-run (diff is render-only + assets).
         ./gradlew balanceSim -> not owed: not balance-bearing (no enemy/, util/, sim/ file in the diff).
         Reviewer re-ran `python3 effects.py <scratch>`: machine.fx.*, insect.fx.*, status.fx.* regenerate
         byte-identical to the committed assets (cmp). machine/status are absent from the diff vs 27725f1.
Scope:   render/enemyvisual/{EnemyClipId, EnemyAnimationRules, EnemyAnimator, EnemyVisualLayer, EnemyEffectSet,
         EnemyAtlasCache, EnemyVisualRegistry}.java; enemy-art kit.py, iron_stalker_rig.py (imports only kit),
         effects.py, bestiary.py; insect.fx.json; iron_stalker.clips.json (13 clips: standard set + BUFF_SELF +
         AREA_STRIKE); docs (sprite guide, art-direction 16, enemy-system, both SKILL.md, CLAUDE.md row); idea + checkpoint.
Ckpts:   .claude/checkpoints/generated-enemy-art-order-4.md -> CP0 first (08e66dc, checkpoint file only), CP1-CP4
         ticked, each with its own commit; CP5 correctly unticked pending this verdict; STATUS still IN PROGRESS.
         Tree clean.

CORRECTNESS - holdFor(Enemy) committed-heavy hold (the requested check)
  plannedAction.heavyAttack is cleared on every path that could leave it stale:
  - every PlannedAction.setX(...) nulls it, so each phase-C recommit (computeNextPlan) wipes it unless commitHeavy
    re-marks it;
  - phase B clears it after executePlan (EnemyManager ~1811) and on the stun/skip branch (~1796-1797);
  - tryStaggerEnemy (~1536) and applyGuardRiposte (~1555) clear it at once; phase C's skipNextAction branch uses set(STUNNED).
  No de-alert path exists (grep: no alerted=false / return to DORMANT), so an un-alerted enemy cannot keep a stale
  heavy. Between a status STUN landing and phase B the heavy is still set, but PRIORITY_DAZED >= WIND_UP wins in
  currentFrame and holdsWindUp is gated by !isDazed, so the body reads dazed, not telegraphing. Same predicate as
  EnemyRenderer.isHeavyTelegraphing (the floor danger markers), so body pose and markers agree.
  Regression surface: ACID_DRONE (Warden) has MOVESET_DEBUFF and heavyWindUp()==null -> never commits a heavy,
  behaviour unchanged. SHELL_BRUTE (Tick) commits CHARGE only together with state WINDING_UP -> the state row wins
  first, identical result; CHARGE burst condition (WINDING_UP || SELF_DESTRUCTING) is reproduced exactly by
  holdsWindUp for every pre-existing case. A DEFENDING enemy keeps DEFEND (state row is checked first).
  ABILITY kind: falls back to the row's ATTACK when the creature lacks the clip; manifest parser only requires IDLE,
  so extending EnemyClipId cannot break warden/harvest_tick loading. Burst override: only RESISTED uses
  BurstId.RESIST, so the plate spark replaces exactly the resisted chip. No allocation on a per-frame path; no switch
  (abilityClip rows); naming clean; G9 respected (no enemy/ file touched, layer only reads).

BLOCKING (must fix)
  none.

NON-BLOCKING (should fix)
  1. Checkpoint discipline: the EnemyVisualRegistry row lived uncommitted in the working tree from CP2 through CP3
     (needed for the captures). It was named as a deliberate temporary state with the checkpoint that resolves it
     (CP4), and CP4 did resolve it - acceptable, noted only because it bent "never leave work uncommitted".

NITS (optional)
  1. EnemyEffectSet.java:~205 - every burst is now parsed, including ones no runtime id names (previously skipped
     before parsing). Needed for creature-only bursts, but a malformed future burst now fails the whole set load
     where it used to be ignored. Fine for the shipped assets (reviewer regenerated them).
  2. The brief asks for two story details (pen number + snapped collar) against series G7 "one story detail";
     the implementation follows the brief, which owns that call - no action.

Assign to: none.

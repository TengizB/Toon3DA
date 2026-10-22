# reviewer r1 — sump-wretch-acid-death

VERDICT: PASS

Spec:    .claude/agents/ideas/sump-wretch-acid-death.txt
         A1 met (in-engine, CP4 note) · A2 met with the D2 deviation (POISONED x3 on the same turn, applied without
         armour cost, guard or shield; the ticks go through the existing DoT pipeline, so a TOUGHNESS-2 start player
         takes 4+2+1 = 7, not 12, and an armoured player's armour absorbs ticks, the same as on a toxic tile; recorded
         as D2) · A3 met · A4 met · A5 met (the chain was seen: DIED cause=STATUS) · A6 met (the pickup branch is
         code-read: drops are stamped as cells before the hook runs and are not isHazardSpreadable) · A7 met
         (scratchpad swA_sheet.png shows ACID_SPLASH bursts and POISONED (3) on the HUD) · A8 code-read only (the
         splash touches only the player and the enemy list; the hook runs on every death cause) · A9 met (both
         lambdas are gone; the balanceSim summary is byte-identical at CP1) · A10 met · A11 met (CP2 commit 2e3faf7)
         · A12 met with D1 (TP 14.4 = 9.6 + 4.8; DEATH_SPLASH_ADJACENT_SHARE 0.4 is inside the idea's own 0.3-0.7
         range) · A13 met (BalanceAuditTest asserts the term is 0 for every non-splashing type) · A14 met (balanceSim
         green per CP5; wretches fielded at depths 1-5, measured from the planner) · A15 met (registry: MIRE_WRAITH
         -> mire_wraith only).
Build:   reviewer's own run, mirror `./gradlew -q :core:build` at 1acea45: 482 tests, 1 failed. The failure is
         narrative.StoryBarkTest.humourLivesNearTheSurfaceAndDiesNearTheCore ("bark.depth.core.2 LEVITY"). It is
         pre-existing and has nothing to do with this diff. BalanceAuditTest.deathSplashIsPricedOnlyWhereItExists...
         is green. balanceSim: not re-run; the orchestrator reported it green (CP5) and nothing balance-bearing
         changed after e1fbcbe.
Scope:   be5905a..1acea45 without 3af1ed2. That commit is generated-enemy-art-order-5's PASS commit and also
         carries the EnemyEffectSystem tidy-up, so it is not reviewed here. Files reviewed: EnemyType,
         EnemyManager, HazardManager, World, SimWorld, LevelLoader, EncounterBudgetPlanner, BalanceConfig,
         BalanceSchema, EnemyConstants, GameBalance, GameMath, BarkStrings, the properties file, EnemyRenderer,
         EnemyVisualRegistry, EnemyVisualLayer, BalanceAuditTest, LevelGeneratorSnapshotTest, and docs
         (tile-symbols, enemy-system, game-balance-authority/knowledge, sprite-art guide, art-direction, CLAUDE.md).
Ckpts:   .claude/checkpoints/sump-wretch-acid-death.md: CP0-CP5 ticked, each with its own pushed commit. CP6 is
         correctly still open, waiting on this verdict. The handover notes give reasons and record the deviations.
         STATUS is still NOT IMPLEMENTED, which is correct before a PASS.

Rules checked: STRICT RULE ('o' is in tile-symbols.txt with budget 91/4, plus LevelLoader, EnemyManager and
CLAUDE.md, all in 2e3faf7; Level/renderers deliberately unchanged, as the commit message states; SymbolBudget treats
undeclared characters as FIXED, so no tileset edit is owed). R3/R4 (orthogonal only, status only, no list mutation).
R5 (the splash never calls tryArmourShieldStatus). R6 (applyPoisonStacks honours immunity and resistance). R7 (the
ignite() eligibility covers pickups, the exit and fire). R10 (the listener is null in SimWorld). The player tile is
read after finishAction has snapped the position. Admission: K1/K11/K18 rows are present, nothing is
GRANDFATHERED and there are no waivers. Tests: exactly one new balance method, plus a legitimate re-baseline of an
existing snapshot test. Naming is fine, there are no hardcoded numbers, and render paths do not allocate.

BLOCKING
(none)

NON-BLOCKING
1. core/src/main/java/ge/tbegvadze/toon3d/util/GameMath.java (deathSplashThreatPoints comment block): the worked
   example "(12 x 0.5 = 6.0)" still uses the designed 0.5. Change it to 0.4 -> 4.8, or label it as an example.
2. core/src/main/java/ge/tbegvadze/toon3d/util/BalanceConfig.java (SUMP_WRETCH_SPLASH_PLAYER_STACKS javadoc):
   "~9% max HP" is the gross figure. Per D2 a start player really loses about 5% (7 HP). Mention that the
   TOUGHNESS / armour pipeline applies.
3. core/src/main/java/ge/tbegvadze/toon3d/hazard/HazardManager.java splashAcid: the burst skips only Level.isWall
   tiles, so a closed door or solid-prop neighbour still gets an ACID_SPLASH burst. This is cosmetic only, but the
   idea's edge case says "no burst on a wall or closed door". Optional fix: also skip closed doors.
4. A8 is code-read only (no in-engine barrel staging). Construction makes it safe, but record it as code-read in
   the CP6 note.

NITS
1. The idea's IMPLEMENTATION NOTE and D2 could say in one line that A2's "armour value does not change" holds only
   at armour 0 (ticks are absorbed when armour > 0).

Assign to: orchestrator (docs/comment touch-ups 1, 2, 4 and the nit, optional), libgdx-specialist (3, optional)

VERDICT: PASS

Round 2 (focused) — balance-overhaul-order-5, fixes 5d96f5b..6bd33d5 (final 6bd33d5; 5c26db8/6831c55 WIP snapshots)

Spec:    unchanged from r1 (A1-A7 met; A6 as amended by D7; A5 desktop check still owed, recorded in the note)
Build:   hub's solo mirror run, not re-run by me (fixes are small, local, and the quoted run postdates 6bd33d5):
         core:test 458, only StoryBarkTest red (pre-existing, narrative); lwjgl3 compiles;
         balanceSim exit 0, 0 FAIL, no S-* moved, S-BUILD dominant 0.47 WAIV (D7)
Scope:   git diff 5d96f5b..HEAD -- core/ (EnemyManager, Weapon, ExplosiveBarrelManager, BuildStats, Keystone,
         LevelUpOverlayRenderer, BuildPagePanel, LevelRenderer, ShopAbilityService, SimWorld, World,
         ProgressionConstants) + impl-note draft
Ckpts:   CP6 correctly still unticked; STATUS IN PROGRESS (correct until this PASS)

Round-1 findings:
- BLOCKING 1 (hulk double-kill) RESOLVED. EnemyManager.java:2224 sets selfDestructed before
  applySelfDestructBlast (:2225), so strikePlayer's reflect guard (:3295 `!attacker.selfDestructed`)
  skips the detonating hulk; :2231-2233 dedups the enqueue as belt-and-braces. The only other
  selfDestructed readers are resolveTrackedHeavy (:740, runs after execute at :1403 — unaffected
  by the reorder) and the death-hazard hook in killEnemy (:3273, still reads true -> MASSIVE cloud).
  Kill credit / voice / death hazard / Wildfire now run exactly once via the single phaseBExecute
  killEnemy, same as pre-order behaviour.
- NB1 RESOLVED. ShopAbilityService.canApply refuses a maxed perk (Predicate wired to
  playerBuild::isMaxed in World:744 and SimWorld:228; playerBuild is final in both, so the bound
  reference cannot go stale). ShopTransaction.buy checks canApply before spendCredits -> no charge.
- NB2 RESOLVED. Weapon.onBuildChanged (Weapon.java:1161-1166) refunds the overflow via
  refundReserveRounds (null-guarded for melee / no ammo inventory).
- NB3 RESOLVED. Barrel blasts (only setter of activationHazardBlast is ExplosiveBarrelManager:172)
  now take BuildStats.burningTargetMultiplier (EnemyManager:897-901); reflectIntoAttacker applies it
  (:3309). Matches docs/xp-level-progression.txt:180 "from every source".
- NB5 RESOLVED. Literals moved to ProgressionConstants with identical values; all static final,
  render() paths unchanged structurally — no new allocation.
- Nit 1 RESOLVED (Keystone.java:43 Math.round -> "x2"). Nit 2 RESOLVED (isBarrelCell, reused in
  ExplosiveBarrelManager:185 and LevelRenderer:170, :281).
- NB4 impl-note draft: ACCURATE (spot-checked BalanceConfig: First Blood .90/1.8/2.7, Plate Carrier
  .42/.90/1.48, IRON BULWARK 1.4 / 0.90, BERSERKER 0.14, STORM 0.60, IRONHIDE 1.475,
  BURST_FIRE_ROUND_COUNT 2). Complete for the r1 divergence list.

BLOCKING
none

NON-BLOCKING
1. Impl note: add the one r1 owner-awareness item it omits — the Double-Barrel's built-in BURST_FIRE
   can duplicate a rolled MAJOR. One line.

NITS
1. Weapon.refundReserveRounds -> ammoInventory.tryAdd: rounds beyond the reserve stack cap are still
   dropped. Edge of an edge; acceptable.
2. LevelUpOverlayRenderer still has pre-order literals (VALUE_Y, DIVIDER_Y, DESC_Y, 0.35f/0.62f
   scaleRgb factors). Not introduced by this order; not a finding against it.

Gate: PASS. Orchestrator may add the IMPLEMENTATION NOTE, tick CP6 and flip STATUS to IMPLEMENTED
(desktop A5 check remains owed, as the note says).

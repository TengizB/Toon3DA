VERDICT: PASS WITH FIXES

Spec:    .claude/agents/ideas/balance-overhaul-order-5.txt
         A1 met · A2 met · A3 met · A4 met · A5 met by code reading (desktop check still owed) ·
         A6 met as amended by D7 (the dominant-tag share is waived) · A7 met
Build:   I trust the hub's mirror run, and I checked its artefacts on disk. The mirror's core is a symlink to /home/user/Toon3DA/core.
         - The BalanceAuditTest XML says 52 tests, 0 failures, timestamped 19:03:34Z.
         - The only red suite is StoryBarkTest (1 failure). It was already red at baseline and is narrative, not balance.
         - balanceSim summary.txt shows S-BUILD as: spread OK 1.00, sample 49 OK, dominant share 0.47 WAIV. No band is FAIL.
         - lwjgl3 compileJava was reported by the hub. I did not re-run it.
         - The last commit (5d96f5b, 19:05) touched BalanceSchema, BalanceSimTest and SimReport. That was 90 seconds after the test timestamp, so a re-run after the fix below is owed anyway.
Scope:   git diff 20c8c41..5d96f5b. These files were read in full or by hunk:
         - enemy/EnemyManager (applyDamageTo, kill terms, strikePlayer/reflectIntoAttacker, dischargeStormArc, spreadWildfire, killEnemy), enemy/Enemy
         - status/StatusEffectController
         - entity/Player, Weapon, ArcCannon, PlasmaRifle, Railgun, Shotgun, DoubleBarrelShotgun, SpreadImpact, MeleeWeapon, AssaultRifle, Chaingun, Loadout, WeaponTier, WeaponRoller, EnemyHitTarget
         - input/PlayerController
         - world/World
         - hazard/ExplosiveBarrelManager, HazardManager
         - shop/DefaultShopOfferSource, ShopAbilityService, ShopContext
         - progression/PerkDeck, PerkModifier, Keystone, Perk (head), PlayerBuild (API)
         - render/BuildPagePanel, LevelRenderer, progression/LevelUpOverlayRenderer (render path)
         - sim/BuildBias, PlayerPolicy, TacticalPolicy
         - util/BalanceSchema (waivers), GameMath (comment blocks)
         - both test files
         - docs: grep for coverage of A7
Ckpts:   .claude/checkpoints/balance-overhaul-order-5.md has CP0-CP5 ticked, each in a commit that carries its own tick (253aeec, 9352189, 5f1fa70, 9d90432, 34c83a3, 8722903, 93b5e27).
         - CP6 is correctly left unticked until this gate passes.
         - The WIP and partial commits are labelled honestly. The CP4 usage-limit cut was handled to protocol: WIP saved, tree restored, resumed.
         - The work arrived across 18 pushed commits. Handover notes carry reasoning.
         - STATUS is IN PROGRESS (correct).

BLOCKING (must fix)
1. core/src/main/java/ge/tbegvadze/toon3d/enemy/EnemyManager.java:2218-2227 together with :3284-3287 and :3316-3321.
   IRON BULWARK reflects into a detonating Plague Hulk and kills it twice.
   - What goes wrong: executeSelfDestruct calls applySelfDestructBlast, which calls strikePlayer, before it sets `enemy.selfDestructed = true` (line 2219). So strikePlayer's guard `attacker.isAlive() && !attacker.selfDestructed` is still true when the hulk detonates.
   - Trigger: a guarding player with IRON BULWARK (reflect 1.4x the blocked damage) faces a wounded hulk as it detonates.
     1. reflectIntoAttacker kills the hulk. That awards XP, a kill event and credits, and queues the hulk in pendingExecuteDeaths (the queue is deduplicated).
     2. executeSelfDestruct then sets health = 0, fires onEnemyKilled again, and appends the SAME enemy to pendingExecuteDeaths with no dedup check.
     3. phaseBExecute therefore calls killEnemy() twice on one enemy. The second call fires the death voice and the death-hazard listener (a second MASSIVE toxic cloud) again, may drop a corpse or the once-per-floor emergency ammo, and re-runs Wildfire.
   - This also contradicts the code's own javadoc: "a self-destructing source is gone already and takes nothing back".
   - Fix: make the self-destruct blast skip the reflect. Either call player.applyDirectionalDamage directly in applySelfDestructBlast, or set selfDestructed before the blast. Also add the same dedup loop to executeSelfDestruct's enqueue.

NON-BLOCKING (should fix)
1. shop/ShopAbilityService.java:25 — canApply does not check `build.isMaxed(perk)`.
   - A machine stocks FIREPROOF (1 rank). The player then gets FIREPROOF from a level-up on the same floor. Buying the stocked rank now charges credits, and World.grantShopAbility's addPerkRank no-ops, so nothing is granted.
   - Fix: refuse in canApply when the payload perk is maxed.
2. entity/Weapon.java:508-511 — onBuildChanged clamps shotsInClip down to DEADEYE's smaller clip and the excess rounds vanish. Refund the overflow via refundReserveRounds.
3. EnemyManager.java:887 / hazard/ExplosiveBarrelManager.java:166 — PYROMANIAC's "+50% from every source" is not applied to barrel blasts or IRON BULWARK reflects. Both skip buildMultiplier. Either apply the burning term there, or reword the gain line.
4. The idea file has no IMPLEMENTATION NOTE for the deliberate divergences. They are recorded only in the checkpoint, docs and javadoc:
   - Sapper r1 is a mini-map highlight, not a reveal;
   - Shield Wall gets an invented r3 (all-round guard);
   - Point Man r1 gets a point-blank damage term;
   - Accelerant and Arc Jump each get a second effect;
   - First Blood is first-hit-only;
   - BURST_FIRE has a fixed round count;
   - magnitudes moved far from the defaults (IRON BULWARK 0.5 to 1.4).
   Add the note when STATUS flips.
5. render/BuildPagePanel.java:40-87 and LevelUpOverlayRenderer — panel geometry, scales and colours are class-local literals rather than ProgressionConstants/ItemConstants entries. The existing renderer pattern is mixed, but CLAUDE.md asks for the constants file.
6. Owner-awareness items. These are already flagged in NOTES; I confirm all four are real in the code:
   - IRON BULWARK reflects ordinary guarded hits (strikePlayer runs on every guarded front-arc hit) at 1.4x;
   - shop tier-up prices roughly double;
   - the Double-Barrel's built-in BURST_FIRE can duplicate a rolled MAJOR;
   - boss attacks (BossFloorController) never route through strikePlayer, so IRON BULWARK never reflects into a boss.

NITS (optional)
1. Keystone.java:42 — the DEMOLITIONIST gain line renders "x2.0" (float concatenation).
2. LevelRenderer.java:169 — `cell == 'g' || cell == 'E'` duplicates ExplosiveBarrelManager's barrel test. Reuse it.
3. .claude/checkpoints/balance-overhaul-order-5.cp{2,4,5,6}-contract.md are committed scratch contracts. Fine to keep, but they are not part of the checkpoint protocol.

Checked and clean:
- Testing policy:
  - only BalanceAuditTest and BalanceSimTest changed;
  - the R-CARD / R-CARD-BREAKPOINT methods were re-pointed to R-PERK, not deleted;
  - the ceiling-only roll sweep was strengthened to count plus band (not weakened);
  - the BalanceSimTest D7 allowance is for a test added in this order, and is per owner ruling;
  - no new test files.
- Override clause:
  - R-CARD, R-CARD-BREAKPOINT, the total-PP invariant and R-ABILITY are each named and replaced in docs/game-balance-authority.txt and the knowledge doc;
  - the D3, D6 and D7 waivers in BalanceSchema match the owner rulings exactly.
- Never-a-switch: no new switch was added (PerkModifier, OfferSlot and BuildBias are per-constant data).
- Naming: no forbidden short names.
- No keyboard input.
- Headless packages (sim, progression build classes, util valuation) import no LibGDX.
- Every new GameMath method has a Formula/Derivation/Edge-cases block.
- No allocation in the render paths (plate text is built in setOfferedPerks; BuildPagePanel flattens its rows in open()).
- Weapons are wired with build stats at run start (the whole arsenal is constructed there).
- SCATTERGUN's free reload is a spec-mandated exception to one-action-one-turn (B4/A2).

Assign to: java-architect (blocking 1; non-blocking 1-3), libgdx-specialist (non-blocking 5), orchestrator (non-blocking 4, desktop check for A5/CP5, re-run core:test + balanceSim after the fix)

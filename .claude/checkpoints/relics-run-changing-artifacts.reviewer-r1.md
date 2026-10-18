VERDICT: PASS WITH FIXES

Spec:    .claude/agents/ideas/relics-run-changing-artifacts.txt, judged as amended by its IMPLEMENTATION NOTE
         A1 met · A2 met as amended (10/11/9/6; no switch on relic/rarity/trigger/source/PerkModifier added) ·
         A3 met · A4 met by code reading, by-hand run OWED (B1) · A5 met as amended (VAMPIRIC CORE now 2%; same-turn
         applyHealing) · A6 met · A7 met in behaviour, text NOT as written (B2) · A8 met · A9 met · A10 met by code
         reading + xvfb screenshot, by-hand run OWED (B1) · A11 met (I diffed the SECTION 6 BUILDS table at
         e397c10 against HEAD: identical) · A12 met · A13 met · A14 met · A15 met · A16 met · A17 build met,
         lwjgl3:run OWED (B1)
Build:   Hub mirror run. I checked the artefacts on disk and did not re-run.
         - core/build/test-results/test: 463 tests, 1 failure, StoryBarkTest#humourLivesNearTheSurfaceAndDiesNearTheCore,
           which already failed at base e397c10 (narrative, not ours). Written 21:30Z.
         - core/build/test-results/balanceSim: 17 tests, 0 failures, including noRelicIsATrapAndNoneIsMandatory.
           Written 21:31Z.
         - core/build/reports/balance-sim/summary.txt: no FAIL band. SR1-SR6 all OK. R1 row IN. RELIC REPORT and the
           "relics (expected)" column are both present.
         - The only commit after that run is CP9a, and its only Java change is one GameMath comment line, so the
           gate output still covers HEAD.
Scope:   git diff e397c10..2341d1c (70 files).
         - Read in full: relic/* (13 classes), render/RelicOfferOverlayRenderer.
         - Read by hunk: entity/Player, EnemyHitTarget, enemy/EnemyManager (with applyDamageTo, killEnemy,
           processDoTKill, reflectIntoAttacker), hazard/ExplosiveBarrelManager + RelicBlastTarget,
           door/DoorManager + DoorOpenedListener, progression/PerkModifier, BuildStats, PlayerBuild,
           world/World (every relic hunk plus the pause, settings and app-pause paths), RunStats,
           sim/SimWorld, PlayerPolicy, TacticalPolicy, RelicProbe, route/* (5 files), render/BuildPagePanel,
           InstanceReportRenderer, InventoryOverlayRenderer, util/BalanceConfig SECTION 26,
           HudConstants/EffectConstants additions, GameMath (the Formula block of every new method), all 3 test files.
         - Docs: relic-system.txt section 5, plus a grep of the authority / route / sound docs for A14.
Ckpts:   .claude/checkpoints/relics-run-changing-artifacts.md has CP0-CP9a ticked.
         - Ten pushed commits (fdab2bd..2341d1c). Each carries its own tick, and the tree is clean.
         - CP9b is correctly unticked and STATUS is IN PROGRESS.
         - The handovers carry reasoning: the R11 entry point, the double-count trap in the cache weight, the
           zero-value effect halves, the 1.8% -> 3.2% stack slack. The one deliberate temporary state (the CP6
           placeholder tap) was resolved in CP7.

What I verified as correct (no finding):
- The shield and multiplier sit at the top of Player's private applyDamage. Every hit path reaches it: enemy
  directional hits, boss hits, barrels and own explosions. DoT enters applyDoTDamage and never reaches it. A hit
  guarded down to 0 does not spend the shield (amount > 0). Event costs bypass it through applyEventCostDamage.
- Corpse bursts cannot chain, guarded twice: activationRelicBlast leads to applyRelicKillTerms(.., false), and
  resolvingCorpseBurst is reset in a finally. applyHazardBlastToEnemy restores class, floor-neutral and hazard flags.
- Relic blasts hit enemies only. The player and barrels are never touched.
- Door trigger fires once per tile per floor, from a grid allocated in beginFloor. PlayerController is the only
  requestOpen caller.
- Enemy-HP scaling: initial enemies are scaled once (an initialEnemyHealthScaled guard on a per-floor
  EnemyManager), and later spawns are scaled in spawnEnemyInternal. BOSS is skipped. No double scaling, because
  EnemyManager.setBuildStats has exactly one call site per floor.
- KILL_AMMO_REFUND draws buildRandom only when its chance is > 0. The roller uses its own seeded Random.
  TacticalPolicy.chooseRelic draws nothing. So a relic-free run gains no RNG draws.
- Phase machine:
  - An offer opens only in PLAYING, with the player alive and idle, no pending level-up, and no bark mid-fade.
  - It opens before the story exchange.
  - The queue is cleared on death.
  - PAUSE round trip: pauseReturnPhase survives SETTINGS and CODEX (both return to PAUSE_MENU), and the lockout
    re-arms on return and on app resume.
  - Abandon goes to standDownToTitle.
  - Barks are suppressed (runPhase != PLAYING). Thumb clusters and event text are not drawn.
- No render allocation: the overlay builds its layouts once per offer, BuildPagePanel builds rows in open(), and
  the report line is built once per death.
- Every new Disposable is disposed (the overlay is in World.dispose).
- The new package and doc are listed in CLAUDE.md.
- The Testing Policy is respected: exactly 4 BalanceAuditTest methods and 1 BalanceSimTest method are added. The
  Order10RegistriesTest change (5 -> 6 events, quarantine_locker added to the id list) is a legitimate count
  update, not a weakening. No new test file or dependency.

BLOCKING (must fix before STATUS: IMPLEMENTED; no code defect among them)
1. A4 / A5 / A7 / A10 / A17 — the by-hand run is still owed. No real input has ever driven a tap into the offer.
   - Code reading shows the path is sound. updateRelicOffer uses the same Gdx.input.justTouched() +
     gameViewport.unproject idiom the LEVEL_UP overlay already ships with, and the hit-test geometry is shared with
     drawing. That is why this is not a FAIL.
   - But the spec's done-state for these criteria is a played check, and the xvfb harness only rendered.
   - Do one of these:
     (a) the owner plays `./gradlew lwjgl3:run` or a device build; or
     (b) drive `lwjgl3:run` under xvfb with xdotool mouse clicks (on desktop a mouse click is the same
         Gdx.input touch path).
   - Steps to cover: a depth-1 start offer; a tap inside 0.4 s does nothing; a card tap bonds it, plays
     RELIC_BONDED and lists it on the BUILD page; VAMPIRIC CORE heals on a kill; ABLATIVE SHELL absorbs the first
     hit and not a burn tick; death prints "RELICS: ..." and the next run holds none.
   - Record the result in the CP9b handover. If it surfaces a defect, only that defect needs re-review.
2. .claude/agents/ideas/relics-run-changing-artifacts.txt IMPLEMENTATION NOTE — two deviations from the written
   spec are recorded only in the checkpoint file and relic-system.txt. The NOTE is where the spec is amended.
   (a) A7 / VISUAL DESIGN: the feedback text is generic.
       - Shipped: "SHIELD - HIT ABSORBED" and "+N HP - RELIC" (HudConstants:RELIC_SHIELD_ABSORB_TEXT,
         RELIC_FEEDBACK_SUFFIX).
       - Spec: "ABLATIVE SHELL — HIT ABSORBED" and "+9 HP — FIELD DRESSING".
       - Either add a NOTE line, or name the relic without a switch: World holds the RelicHand and can find the held
         relic whose effects() contain FIRST_HIT_SHIELD.
   (b) R8 says a kill is "a death that awards XP through KillXpListener". The relic kill terms actually run only on
       the BLOODLETTING path (EnemyManager:1001-1002).
       - processDoTKill (~3271) and reflectIntoAttacker (~3415) award XP and credits, and DOG TAGS / BOUNTY CHIP /
         TITHE ENGINE multiply them, because the multipliers live in depthScaled*Reward.
       - But those kills give no KILL_HEAL, KILL_ARMOR, ammo refund or corpse burst.
       - Record it in the NOTE, or fix it as in NON-BLOCKING 1.

NON-BLOCKING (should fix)
1. enemy/EnemyManager.java:3271 (processDoTKill) — a burn or poison kill pays TITHE ENGINE but not VAMPIRIC CORE.
   - Example: a PYRO run holding FEVER CORE gets no heal from the burn kills its build is made of.
   - processDoTKill already runs after the status loop, so the list is safe to modify: calling
     applyRelicKillTerms(enemy, true) there is safe.
   - reflectIntoAttacker runs mid enemy phase, so it should use allowCorpseBurst=false (removal is deferred there).
2. The IMPLEMENTATION NOTE takes two decisions outside D7's licence. Both are small and technically argued.
   Surface both to the owner explicitly, because D7 and AS14 make bands, weights and floors owner design, not hub
   tuning.
   (a) MYSTERY_WEIGHT_RELIC_CACHE = 3, which is below D7's stated floor of 4. R-MYSTERY-EV margin 0.0004.
   (b) RELIC_CARRY_BAND_LEGENDARY top widened 1.20 -> 1.22, a band AS14 calls "the design". The cause is
       second_heart: a lone boolean that cannot be tuned.
3. relic/RelicCatalog.java:90-97 (SPOTTER'S LENS) is a recomposition that no longer means what it says.
   - Its value is now DAMAGE_TAKEN 0.95, which is generic.
   - It still carries MARKSMAN affinity, so it is offered only to MARKSMAN players for no mechanical reason.
   - Its card prints a vestigial "+1% BALLISTIC / RAIL damage to unhurt enemies"
     (RELIC_SPOTTERS_LENS_FULL_HEALTH_TARGET_DAMAGE = 0.01). Nobody can feel a 1% effect, so the card is noise.
   - Send it to creative-game-designer: drop the 1% effect and the affinity, or re-theme the relic.
4. world/World.java:5315 (updateRelicOffer) — the PAUSE hit target works in RELIC_OFFER (R14) but cannot be seen.
   The thumb clusters, which draw the pause button, are suppressed in this phase. Draw the pause glyph on the
   overlay, or accept and document it.
5. relic/RelicOfferRoller.java:118 — R5 excludes only boolean kinds that are already satisfied. A saturating
   number is still offered when it can add nothing.
   - Example: SALVAGE HOOK (CARRIER_EXTRA_DROP 1.00, which clamps to 1) still appears to a player who already holds
     SALVAGER.
   - Fix: a PerkModifier.isSatisfiedBy override for the clamped kinds (CARRIER_EXTRA_DROP, KILL_AMMO_REFUND).
6. S-RELIC proves wiring, not feel. SR3 "rarity is felt" passes on 3.23 vs 3.20 mean depth, and SR6 on 1.01.
   That is within noise at ~3 floors deep, as the idea's RISKS predicted. The audit (R-RELIC) is the real gate.
   Note this in the CP9b handover so a later green SR3 is not read as evidence.

NITS (optional)
1. core/src/test/.../route/Order10RegistriesTest.java:36 — the method is still named allFiveV1EventsAreRegistered
   but now asserts 6.
2. render/RelicOfferOverlayRenderer.java:58-63, 128-131, 271, 310 — bare literals (7f shadow offset, 3f/6f inner
   edge, colour factors 0.20/0.07/0.92/0.57). This mirrors LevelUpOverlayRenderer, but CLAUDE.md wants them in
   HudConstants.
3. Bonding a card plays RELIC_BONDED, and then the RELIC_OFFER -> PLAYING transition also plays UI_MENU_CLOSE
   (isMenuPhase). Two interface sounds stack.
4. Player.applyOwnExplosionDamage reaches the shield, so a point-blank grenade of your own spends ABLATIVE SHELL.
   That is defensible as "an explosion" (R11), but say so in relic-system.txt section 5.

How the owed touch run should weigh: it does NOT turn this into a FAIL.
- Every tap-routing line was read, and it is the shipped LEVEL_UP idiom.
- But the criteria are "plays", so the gate is conditional: BLOCKING 1 and 2 must be closed and recorded in the
  checkpoint before STATUS moves to IMPLEMENTED.
- No re-review is needed unless the run finds a defect.

Assign to: orchestrator (B1 run + record, B2 NOTE, NB2 owner sign-off, NB6 handover) · java-architect (NB1, NB5) ·
libgdx-specialist (NB4, nits 2-3) · creative-game-designer (NB3)

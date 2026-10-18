# CHECKPOINT — Relics: rare run-changing artifacts, priced in balance

**PURPOSE:** the live resume record for `.claude/agents/ideas/relics-run-changing-artifacts.txt`. This
session may be cut off at any moment. Whoever picks the work up next reads THIS FILE FIRST and continues
from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-13bf0cd8-wctbbe`
**IDEA:** `.claude/agents/ideas/relics-run-changing-artifacts.txt` — the spec. Rules (R1–R15), acceptance
criteria (A1–A17) and decisions (D1–D10) are referred to by number and NOT repeated here.
**LANE:** full lane — new `relic` package + `progression` + `entity` + `enemy` + `hazard` + `door` +
`world` + `route` + `util` (balance) + `sim` + `render` + `audio`.
**TESTS:** balance-bearing. ONLY new test methods allowed: the four in `util/BalanceAuditTest` and one in
`sim/BalanceSimTest` (idea step 5). No new test files. Gates: `./gradlew test` + `./gradlew balanceSim`.
**STARTED:** 2026-10-18
**BASELINE:** branched from `e397c10`. Mirror build: core:test 459 tests, 1 pre-existing failure —
`StoryBarkTest` ("a joke survived into the deepest strata: bark.depth.core.2"). Not ours; never fix by
weakening it.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `relics-run-changing-artifacts/CPn` commit says where work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — dirty tree = unverified work: finish to DONE WHEN and verify, or `git restore` and redo.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `relics-run-changing-artifacts/CPn:`, with this
file's tick in the same commit. Never more than one checkpoint uncommitted; split (`CPna`/`CPnb`) rather
than hold. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY

- Touch only; no keyboard anywhere. Every action = one turn; offers cost no turn (hard-pause overlay).
- `register()` never switch (A2: no switch on relic/rarity/trigger/source/PerkModifier).
- Every magnitude a BalanceConfig SECTION 26 constant `RELIC_<ID>_<KIND>`; card text built from it (A3).
- No tile symbol, no ground item, no persistence key, no story string id (D2, D5, D6).
- `relic` package: no `com.badlogic.gdx` import; never imports `route` (D9).
- Relic-free BuildStats accessors and the BUILDS (R-CARRY) table byte-identical (A11).
- D7 tuning licence: only SECTION 26 magnitudes + MYSTERY_WEIGHT_RELIC_CACHE (floor 4) may move.
- No allocations in render(); every Disposable disposed; GameMath comment block on every formula.
- Naming rules (no abbreviations).

## STEP LEDGER

- [x] **CP0** — this file + the idea file committed and pushed.
- [x] **CP1** — relic package (A1 classes, catalog empty), 17 PerkModifier kinds, BuildStats fields/
      accessors/reset/copyFrom/ofEffects, PlayerBuild.addRelicEffects, BalanceConfig SECTION 26 skeleton.
      DONE WHEN: build + test green (baseline failure only), BUILDS table unchanged; seams unwired (CP2).
- [x] **CP2** — every new kind read at its seam (Player, EnemyManager, RelicBlastTarget in
      ExplosiveBarrelManager, DoorOpenedListener in DoorManager, RelicRuntime) wired in World AND SimWorld.
      DONE WHEN: build + test green, sim output unchanged; R11 status-tick method noted below.
- [x] **CP3** — RelicCatalog registers all 36 with SECTION 26 magnitudes; RelicOfferRoller R4–R6.
      DONE WHEN: build + test green; local (uncommitted) roll harness showed R5 eligibility.
- [x] **CP4** — BuildValuationModel.carryOf extracted (BUILDS table byte-identical), valuation term +
      GameMath formula per new kind, RelicValuationModel, worstLoss null niche.
      DONE WHEN: build + test green; BUILDS diff empty.
- [x] **CP5** — BalanceSchema RELIC / RELIC_CURSE / RELIC_STACK / RELIC_SOURCE + COVERAGE, four named
      BalanceAuditTest methods, all relics in band, BalanceReport RELICS + RELIC STACK tables.
      DONE WHEN: `./gradlew test` green (baseline failure only); negative-drill message recorded below.
- [x] **CP6** — RunPhase.RELIC_OFFER + pending FIFO, RUN_START/BOSS/EventEffect queuing, RELIC_CACHE,
      RouteEconomics row, quarantine_locker, RouteEconomicsModel pricing, RunStats; placeholder tap takes
      card 0. DONE WHEN: test green incl. route rules; flow playable end to end.
- [x] **CP7** — RelicOfferOverlayRenderer, BuildPagePanel RELICS, run-report line, event-text feedback,
      RELIC_BONDED sound. DONE WHEN: build green; desktop check of A4/A5/A7/A10 (or noted as owed).
- [x] **CP8** — sim: policy relic methods, SimWorld acquisition, RunLedger ids, 5 probe rows, S-RELIC
      SR1–SR6, BalanceSimTest method, RELIC REPORT, RoundSummary R1, LadderReport column.
      DONE WHEN: test + balanceSim green; before/after ROUND SUMMARY recorded below.
- [x] **CP9a** — docs (idea step 6), authority SECTIONS 6/8 regenerated, CHANGELOG, CLAUDE.md.
- [ ] **CP9b** — reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV:** `./gradlew` cannot run in the repo (Android Gradle plugin: dl.google.com 403; Maven Central
  429). Gate via the desktop-only MIRROR in the session scratchpad
  (`<scratchpad>/mirror/`): symlinks to core/lwjgl3/assets/gradle/gradlew/docs/story/gradle.properties;
  `settings.gradle` = pluginManagement (googleapis maven mirror first) + `include 'lwjgl3','core'`; root
  `build.gradle` = the repo's minus the `buildscript{}` block, `configure(subprojects)`, and
  `maven { url = "https://maven-central.storage-download.googleapis.com/maven2/" }` first in subprojects
  repositories. Run `./gradlew build` / `./gradlew :core:balanceSim` from the mirror. Test XML lands in the
  REAL `core/build/test-results/`.
- **CP1 handover:** 17 new PerkModifier kinds (ALL_WEAPON_DAMAGE_COST already existed — no 18th). Every
  catalog kind name exists verbatim. BuildStats new fields: killHealFraction, killArmorFraction,
  killCreditBonus, killAmmoRefundChance, experienceBonus, floorArrival{Heal,Armor,Ammo}Fraction,
  doorBlastFraction, doorOpenHealFraction, corpseBlastFraction, lowHealthDamageBonus, firstHitShield,
  damageTakenFactor, enemyHealthFactor; accessors per spec (killCreditMultiplier/experienceMultiplier =
  1+sum; damageTaken/enemyHealth clamped). ALL_WEAPON_DAMAGE lands in classDamageBonus; CLIP_SIZE_MULTIPLIER
  multiplies clipSizeCostMultiplier (read by clipSizeMultiplier()). `BuildStats.ofEffects(PerkEffect...)`.
  `PlayerBuild.addRelicEffects(PerkEffect[])` stores the definition's array reference.
  `RelicSource.rarityWeightsFor(bossIndex)` aligned with `allowedRarities()` (row clamped).
  TEMPORARY: `RelicOfferRoller.roll` is a stub (count=0) — SPEC GAP: it needs a `RelicRegistry`
  parameter; CP3 adds it (signature change is fine, no callers yet). RelicRuntime methods are no-op
  `// CP2` stubs; RelicCatalog.bootstrap empty (CP3).
  R11: status ticks enter via `Player.applyDoTDamage(int)` (~l.147, separate path ending in loseHealth);
  hits via public `applyDamage(int)` -> private `applyDamage(int, boolean heavy)` (~l.375), which
  applyDirectionalDamage (~l.267) and applyOwnExplosionDamage (~l.453) also reach. Put shield + damage
  multiplier at the top of the private method, before the dodge roll; leave applyDoTDamage alone.
  resetFloorBuildState() ~l.474 re-arms. There is no entity/StatusEffectController — grep for the caller.
  BUILDS table verified identical vs authority doc l.1564–1577 by running util.BalanceReport from the
  mirror's classes.
- **CP2 handover:** `RelicBlastTarget.detonateRelicBlast(col, row, centreFraction, ringFraction, visualScale)`
  (5 args). Corpse burst = centre 0, ring = fraction. Relic blasts arm `EnemyHitTarget.setActivationRelicBlast`
  (+ the hazard-blast flag): their kills get XP/credits AND the relic kill terms minus the corpse burst
  (`applyRelicKillTerms(enemy, allowCorpseBurst=false)`) — R8/EDGE CASES; barrel kills still get none.
  Hub fix: route-event costs use new `Player.applyEventCostDamage(int)` (bypasses the relic shield and
  damage multiplier; R11 = enemy attacks/heavies/explosions only). XP/credit multipliers live in
  `EnemyManager.depthScaledXpReward/depthScaledCreditReward` (every kill site). Enemy HP scaling: in
  `setBuildStats` (initial floor enemies) + `spawnEnemyInternal`, BOSS skipped. Door open is turn-less
  (INTERACTING -> finishAction(false)); blast resolves inside requestOpen before any enemy turn.
  Door blast centre = door + signum(door - player), wall -> door tile. Feedback strings in HudConstants
  `RELIC_FEEDBACK_*` / `RELIC_SHIELD_ABSORB_TEXT` (generic "RELIC"/"SHIELD", no per-relic name).
  Getters: `World.getRelicHand()`, `SimWorld.relicHand()` (package-private), `SimWorld.playerBuild()`.
  Bond = `relicHand.add(def, playerBuild)` then existing `applyBuildEffects()` (World ~5173, SimWorld ~830).
  Floor build: World `buildLevelDependentResources` (~1333), SimWorld `buildFloor` (~377).
  World `applyLadderEffect` ~3028/3095. Sim summary byte-identical to CP1 (diff of summary.txt);
  balanceSim ~50 s. HEAD summary kept at <scratchpad>/head-summary.txt.
- **CP3 handover:** registry = static lazy `RelicCatalog.registry()` (RouteRegistries idiom); iterate
  `.all()` / `.allOfRarity(r)`; `def.effects()` -> `BuildStats.ofEffects(...)`. 53 `RELIC_<ID>_<KIND>`
  constants in SECTION 26 (before RARITY BANDS). Conventions: gain kinds add; DAMAGE_TAKEN/ENEMY_HEALTH
  multipliers and every *_COST kind are the multiplier itself (text = percent(1-m) or percent(m-1));
  integer kinds int 1; booleans `PerkEffect.of(kind, 1f)`. `PerkModifier.isSatisfiedBy(BuildStats)` per
  boolean kind. Roll call: `seed = RelicOfferRoller.seedFor(runSeed, depth, source);
  RelicOfferRoller.roll(source, depth, bossIndex, relicHand, playerBuild.stats(), carriedOrRankedTags,
  seed, offer, RelicCatalog.registry())`; bossIndex 0..3 (clamped); tags = BuildTag.forDamageClass of
  carried weapons + perks with rank>0. Fallback stays within the source's allowed rarities; BOSS
  guarantee re-rolls last card RARE then LEGENDARY, else shrinks. Harness (uncommitted):
  <scratchpad>/harness/RollHarness.java — 300 seeds, all R4–R6 checks clean. "1 HP" in SECOND HEART
  text is the fixed Second Wind rule (no constant exists), not a tunable.
- **CP4 handover:** `BuildValuationModel.carryOf(BuildStats, BuildTag kitTag|null, int depth) -> Carry`
  (.score). Full BalanceReport output byte-identical to CP3 (re-run: `java -cp
  core/build/classes/java/main:<gdx-1*.jar> ge.tbegvadze.toon3d.util.BalanceReport`; baseline at
  <scratchpad>/report_before.txt). `worstLoss(stats, null, depth)` = every probe. `RelicValuationModel`:
  carryLift, gain, nearestReferenceDepth, meanCarryLift, expectedRelicCount, nodeOfferRatePerFloor,
  expectedHandLift, bestHandLift, offerPowerPoints, QUARANTINE_LOCKER_EVENT_ID. New SECTION 26 constant
  RELIC_VALUATION_LOCKER_EVENT_WEIGHT = 1. CP6: the model sums the six RouteMapConstants.MYSTERY_WEIGHT_*
  + BalanceConfig.MYSTERY_WEIGHT_RELIC_CACHE explicitly — the RELIC_CACHE MysteryOutcome must read the
  BalanceConfig constant (RouteMapConstants may mirror it) and the model must not then double-count it;
  locker counted once (registered weight if present).
  ZERO-VALUE halves in carry (relic still priced by its other effect): MELEE_DAMAGE (bone_saw), BURN_TURNS,
  KNOCKBACK_BONUS, ARC_CHAIN_JUMP, GUARD_REFLECT, RANGE_BONUS.
  CP5 inputs: harness table at <scratchpad>/harness/val/table.txt — only 8/36 in band; RELIC-STACK fails
  at d15-25 as spec'd. worstLoss(null) reports SPURIOUS losses for pure gains (Incinerator-S2 cell), so
  R-RELIC-CURSE must measure the loss on the relic's COST effects alone (hub decision, see CP5).
  Out-of-scope latent bug noted (NOT fixed): BuildStats.NONE built before its count constants ->
  zero-length arrays; use `new BuildStats()` in new code.
- **CP5 handover:** RuleKinds RELIC / RELIC_CURSE / RELIC_STACK / RELIC_SOURCE + COVERAGE rows;
  BalanceSchema.relicResults()/relicCurseResults()/relicStackResults()/relicSourceResults(). Four
  BalanceAuditTest methods added (only). test: 463, only StoryBarkTest fails. All 36 in band (see the
  idea's IMPLEMENTATION NOTE for the 9 recompositions + LEGENDARY band 1.22). RELIC STACK d25 expected hand
  1.719 vs ceiling 1.751 — only 1.8% slack: any raise of a COMMON/RARE magnitude or the cache weight can
  break it. Perk-rank anchor 1.0725 (1.08±0.03). Negative drill: kevlar 0.70 ->
  "[RELIC] R-RELIC kevlar_weave d5: value=1.43 band=1.06..1.13 OUT-OF-BAND — RARE CARRY gain 1.4286
  OUTSIDE band [1.06, 1.13] at depth 5 (carry 1.4286)". BalanceReport: only 52 added lines (RELICS + RELIC
  STACK) after BUILDS; earlier sections identical. salvage_hook sits at CARRIER_EXTRA_DROP 1.00 (margin
  0.0003). Tuning tools: <scratchpad>/harness/tune/ (ComposeHarness, TuneHarness, StackHarness, cp.txt),
  <scratchpad>/harness/setc.py + rep.sh. CP6 must re-check R-MYSTERY-EV, R-HONEST-SAFE, R-ROUTE-PRICED,
  R-EVENT-VALUE, R-RELIC-STACK after registering RELIC_CACHE (read BalanceConfig.MYSTERY_WEIGHT_RELIC_CACHE;
  RelicValuationModel already adds it to the mystery total) and quarantine_locker (weight 1).
- **CP6 handover:** RunPhase is a private enum in World; RELIC_OFFER added after LEVEL_UP_OVERLAY (update
  early-return, thumb-cluster exclusion, isMenuPhase blips, pause round-trip via pauseReturnPhase, resume
  re-arms lockout). Queue holds REQUESTS (source, depth, bossIndex) and rolls on open (seedFor). Opens in
  PLAYING + idle + no pending level-up + no bark mid-fade; before openPendingStoryExchange. Dropped on
  death. API: `getActiveRelicOffer()`, `chooseRelicOfferCard(int)`, `leaveRelicOffer()`,
  `getRelicOfferOpenSeconds()`; private `updateRelicOffer(deltaTime)` holds the lockout, the pause-button
  hit-test (TouchConstants.PAUSE_BUTTON_*) and the PLACEHOLDER `chooseRelicOfferCard(0); // CP7 replaces`.
  Nothing is drawn in RELIC_OFFER yet. RUN_START queued by `queueRunStartRelicOffer()` on first PLAYING
  frame with !isStartingRoom; BOSS by `queueBossRelicOffer()` after requestBossDefeatedBark; EventEffect in
  applyLadderEffect. RunStats.getRelicsBonded() = "" when none. HudConstants RELIC_BONDED_TEXT_PREFIX,
  RELIC_CAPACITY_TEXT. MYSTERY_WEIGHT_RELIC_CACHE = 3 (see idea IMPLEMENTATION NOTE; R-MYSTERY-EV 1.2996).
  RelicValuationModel.mysteryWeightTotal() sums MysteryOutcome.values() (added MysteryOutcome.weight()).
  Order10RegistriesTest count 5 -> 6 events (quarantine_locker), legitimately. balanceSim exit 0; summary
  moved 3 lines from the new outcome (no band flipped). Not run on desktop yet.
- **CP7 handover:** `render/RelicOfferOverlayRenderer.render(camera, offer, openSeconds)`,
  `cardIndexAt(RelicOffer, x, y)`, `isLeaveAt(x, y)`; text layouts rebuilt once per new offer.
  World.updateRelicOffer: pause hit-test -> lockout -> card / LEAVE IT (UI_CONFIRM). BuildPagePanel shows
  RELICS (n) at the top (flattened in open()); InstanceReportRenderer "RELICS: ..." line (page at its
  12-line cap). Shield absorb adds hitVignetteRenderer.triggerHeal(). Sound RELIC_BONDED (INTERFACE bus,
  played via gameAudio.playMenu); docs/sound-system.txt updated (93 sounds). Default font lacks U+2014/
  U+2026: UI strings use " - " and "..". Hub fix: event text is not drawn during RELIC_OFFER (a prior
  bond's line overlapped the cards). Desktop check: xvfb harness screenshots of BOSS / QUARANTINE offers,
  BUILD page and report at <scratchpad>/harness/shot/ (visuals OK). OWED: real touch-tap routing and the
  by-hand A4/A5/A7/A10 run on a device — never exercised by real input. Running from repo assets/ writes
  assets/game-audio + story-audio WAV caches (untracked): delete before committing.
- **CP8 handover:** gates verified by hub: build 463 tests (StoryBarkTest only); balanceSim exit 0, 0 FAIL
  lines, ~48 s; BalanceSimTest 17 incl. `noRelicIsATrapAndNoneIsMandatory` (balanceSim-tagged suite).
  `sim/RelicProbe` enum (NONE/NO_RELIC/COMMON/RARE/LEGENDARY/CURSED); rows TACTICAL-NO-RELIC,
  TACTICAL-RELIC-{COMMON,RARE,LEGENDARY,CURSED}, 120 seeds each. RuleKind.SIM_RELIC (javadoc = full rule).
  SimWorld resolves offers at trigger time (not queued): resolveRunStartRelic / resolveBossRelicOffer /
  payMysteryOutcome->offerRelic; no EVENT choices in sim (locker not mirrored). LADDER probes carry no
  relics (D3). AS17 via SimWorld.nonAffinePool. No retune, no waiver. S-RELIC: NO-RELIC mean depth 3.15;
  SR1 C/R/L 1.02/1.02/1.03; SR2 CURSED 1.04; SR3 1.01; SR4 30 lines 0.86..1.12; SR5 min 13 runs;
  SR6 1.01 (thin: TACTICAL took the start relic 200/200). Pre-existing band OK/WAIV/FAIL set identical.
  ROUND SUMMARY before/after (CHANGELOG evidence): T4 exit health 0.91->0.90 OUT; T4 no-heal deaths
  0.32 OUT->0.26 IN; T6 SPREAD share 0.62->0.64 OUT; T7 dominant tag 0.47->0.46 OUT; T7 keystone share
  -->0.43 IN; T8 first-boss clears 0.00->0.02; T9 offers/visit 1.00->1.11 OUT; T9 unspent credits
  0.19->0.21; G BULLETS reserve 0.40->0.56; G lifeline floors 0.10->0.07 OUT; J2 top perk STEADY_AIM 0.85
  -> LANE_DISCIPLINE 0.83 OUT; NEW R1 relic lift 1.03 IN. Summaries: <scratchpad>/cp8-before-summary.txt,
  cp8-after-summary.txt. MODEL vs PLAYED: kill share 0.60/0.48, doors/floor 3/2.03 (report-only).
- **CP9a handover:** new docs/relic-system.txt (655 lines; shipped catalog generated from code); authority
  SECTION 3/5/6/8 + CHANGELOG; knowledge SECTION 6B + problems 30-34; route-map, xp-progression docs;
  CLAUDE.md (package row, tree, Key Classes, docs rows "All 19", sim row "fifteen" bands, render order).
  SECTION 6 = BalanceReport stdout run with -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 (else em-dashes
  print '?'); SECTION 8 = summary.txt indented 2 spaces + NOTES block. Live R-RELIC-STACK d25 = 1.695 vs
  1.751 (3.2% slack — the 1.8% in CP5 predates CP6's cache weight 10->3). Hub fixed the stale worked example
  in GameMath.relicNodeOfferRate (0.0079). Known deviations for the reviewer: generic feedback strings
  ("+N HP - RELIC", "SHIELD - HIT ABSORBED") instead of per-relic names; 5-arg detonateRelicBlast; roller
  takes RelicRegistry; offers queue as requests and roll on open. Owed: device touch check.

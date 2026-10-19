# CHECKPOINT — Floor Twists (BLACKOUT / SALVAGE / HUNTED)

**PURPOSE:** the live resume record for `.claude/agents/ideas/floor-twists.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `claude/level-generation-improvements-qwx3lm`
**IDEA:** `.claude/agents/ideas/floor-twists.txt` — the spec. Its rules (R…) and acceptance criteria (A…)
are referred to by number below and are NOT repeated here.
**LANE:** full lane — `route` + `level` + `enemy` + `door` + `world` + `sim` + `render` + `narrative` + `util` (balance).
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` (R-TWIST) and `sim/BalanceSimTest` (S-TWIST)
only; run `./gradlew test` + `./gradlew balanceSim`. No tests for renderer / hunter AI / door / narrative / route console.
**STARTED:** 2026-10-18
**BASELINE:** branched from `9b9ae94`. Mirror `./gradlew build`: compiles; `:core:test` 463 run, 1 FAILS
(pre-existing, unrelated: `narrative/StoryBarkTest.humourLivesNearTheSurfaceAndDiesNearTheCore` — `bark.depth.core.2`
is LEVITY). Not ours, not touched; every later "green" means "no failure other than this one".
**DISPATCH:** this run had no spoke-dispatch tool; every layer is done by the hub itself. Any review is a
SELF-CHECK, not a `reviewer` verdict — STATUS stays IN PROGRESS until an independent `reviewer` PASS.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `floor-twists/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `floor-twists/CPn:`, **with this file's tick
in the same commit as the work**. Never more than one checkpoint uncommitted; split a big one
into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Touch only; no keyboard; no new touch button (A11).
- Turn-based: the hunter clock counts completed player turns, never seconds (R9a).
- Ranged attacks only on a cardinal line (`isSameCardinalLine`) — the hunter included (R13).
- `register()` rows, never a switch on twist id; adding a 4th twist touches no generator/profile/World/overlay code (A12).
- No allocation in `render()`; strings/colours/geometry in Constants files; formulas in `GameMath`.
- No new tile symbol; no new StoryStore/StatsStore key; no SCHEMA_VERSION bump (A11). Seen-flags ARE
  generic (`StoryStore.KEY_BEAT_PREFIX + beatId`) — stop condition checked at CP0, not triggered.
- No ELITE affix roll may change for a fixed seed (A1). No existing band waived; numbers move, not bands.
- SCOPE OUT is binding: no LOCKDOWN/FLOODED, no codex entry, no new sounds, no twist-aware sim policy, no HUD countdown.
- Story text only via string ids; ORA lines in `assets/story/story-strings.properties`.
- No model identifiers in commits or docs.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — data + constants + economics rows.
      DONE WHEN: `route/AffixScope` exists; `NodeAffixDefinition` carries scope/minDepth/ruleLine/twist
      modifiers with no-effect Builder defaults; `NodeAffixRegistry.poolFor(AffixScope)` exists and
      `elitePool()` returns the same five; three FLOOR_TWIST rows registered; `FloorEffects.twistId`;
      BalanceConfig SECTION 27; RouteMapConstants/RenderConstants/HudConstants/EnemyConstants entries;
      three `Kind.TWIST` NodeEconomics rows; nothing rolls a twist; `./gradlew build` + `./gradlew test` green.
- [x] **CP2** — twist roll + plan folding (SALVAGE fully live).
      DONE WHEN: RouteMapGenerator rolls twists on COMBAT only (R1-R4) and every generator owner sets the
      pool; DefaultCombatProfile folds the twist (R5) with sting/statLog/twistId; ELITE affixes identical
      for a fixed seed; `./gradlew build` + `./gradlew test` green.
- [x] **CP3** — R-TWIST (T1-T3) + trajectory folding + BalanceReport twist rows, tuned in band; `./gradlew test` green.
- [x] **CP4** — BLACKOUT runtime (light multiplier w/ exit exemption, NAV OFFLINE plate, enemy min shade,
      wake perception override, all reset on rebuild) in World + SimWorld; `./gradlew build` green.
- [x] **CP5** — HUNTED runtime (PlayerTrail, HuntedPursuit + listener, trail branch R11a-g,
      DoorManager.forceOpenForEnemy, bounty, texts + voice, amber frame) in World + SimWorld;
      `./gradlew build` + `./gradlew test` green.
- [x] **CP6** — route console third confirm-bar line (R14, eight rule lines); `./gradlew build` green.
- [x] **CP7** — three TeachingTopic rows + strings; World requests them on twist arrival; `./gradlew build` + `./gradlew test` green.
- [ ] **CP8** — S-TWIST (ST1/ST2), per-floor twist id in the sim ledger, TWIST REPORT, BalanceSimTest
      extended; `./gradlew balanceSim` green, no existing band changes status; sample sizes recorded.
- [ ] **CP9** — docs (route-map, balance authority incl. SECTION 6 + CHANGELOG, balance knowledge,
      enemy-system, story-ui, tick-system, CLAUDE.md); `./gradlew build` + `./gradlew test` green; ready for `reviewer`.

## NOTES CARRIED FORWARD

- **ENV (cost time):** `./gradlew` cannot run in the repo (Android Gradle plugin, dl.google.com 403). Gate via
  the desktop-only MIRROR at `<scratchpad>/mirror/` (symlinks to core/lwjgl3/assets/gradle/gradlew/docs/story/
  gradle.properties; settings.gradle = pluginManagement googleapis mirror + `include 'lwjgl3','core'`; root
  build.gradle = repo's minus `buildscript{}`, `configure(subprojects)`, googleapis maven first). Run
  `./gradlew build` / `./gradlew :core:balanceSim` from the mirror; test XML lands in the real `core/build/`.
- CP0: stop-condition check — `narrative/StoryProgress.hasSeen/markSeen(String beatId)` →
  `util/StoryStore.isBeatSeen/markBeatSeen` keyed `story.beat.<id>`; generic, so three new
  TeachingTopic rows need no schema change.
- **CP1 handover:** `route/AffixScope{ELITE_AFFIX(ELITE, AFFIX_ROLL_CHANCE_ELITE), FLOOR_TWIST(COMBAT,
  TWIST_ROLL_CHANCE_COMBAT)}` with `hostNodeType()`/`rollChance()`. `NodeAffixDefinition` new accessors:
  `scope() minDepth() ruleLine() minimapDisabled() lightMultiplier() wakeRadiusTiles() wakeSightTiles()
  overridesWakePerception() hunterEnemyTypeName() scatteredAmmoBoxes() extraStims() extraArmourShards()
  twistGuarantees(seed)` (the '+'/'a' SCATTERED pickups; ammo boxes are NOT in it — use an
  `AmmoCacheRequest(n, Placement.SCATTERED)`). Builder: `.scope() .minDepth() .ruleLine() .minimapDisabled()
  .lightMultiplier() .wakePerception(radius, sight) .hunterEnemyTypeName() .scatteredAmmoBoxes() .extraStims()
  .extraArmourShards()`. Registry: `poolFor(scope)`, `definitionsFor(scope)`; `elitePool()` = poolFor(ELITE_AFFIX).
  `FloorEffects.withTwist(id)/twistId()`. `NodeEconomics.Kind.TWIST` + `NodeEconomics.twist(id)` — a SEPARATE kind
  so R-RISK-PREMIUM's AFFIX loop never folds a twist onto ELITE. HUNTED row = budgetScale 1+TWIST_HUNTED_THREAT_SHARE
  (placeholder 0.40, CP3 tunes it against the real IRON_STALKER TP). Constants: BalanceConfig SECTION 27 (end of
  file); RouteMapConstants `TWIST_*_ID/_NAME/_RULE_LINE`, `AFFIX_*_RULE_LINE`, `TWIST_ROLL_CHANCE_COMBAT`,
  `TWIST_HUNTED_ENEMY_TYPE_NAME="IRON_STALKER"`, `LEGEND_TWIST_OFFSET_Y=-30`; RenderConstants
  `TWIST_BLACKOUT_LIGHT_MULTIPLIER/ENEMY_MIN_SHADE`; HudConstants `TWIST_AMBER_*`, `TWIST_NAV_OFFLINE_*`,
  `TWIST_HUNTED_WARNING_TEXT/ARRIVAL_TEXT`; EnemyConstants `TWIST_HUNTED_TRAIL_CAPACITY`, `_ENGAGE_DISTANCE_TILES`(2),
  `_BLOCKED_WAIT_TURNS`(2), `TWIST_HUNTER_HEALTH_BAR_FRAME_*`, SECTION-27 shims. EliteProfile ignores a non-ELITE_AFFIX
  row (defensive). Idea STATUS -> IN PROGRESS. Nothing rolls a twist yet (CP2 wires the generator).
- **CP2 handover:** `RouteMapGenerator.setTwistPool(List<NodeAffixDefinition>)` (pass
  `RouteRegistries.affixes().definitionsFor(AffixScope.FLOOR_TWIST)`); set in World, SimWorld, BalanceSchema
  `generateAuditMap`, and the two existing tests that build generators (BalanceAuditTest
  `safestAndDeadliestRoutesSpanARealDifficultyRange`, RouteEconomicsTest `generator()`) — behaviour-change updates,
  no new test. `rollTwist` = chance then weighted pick over rows with minDepth <= depth (no boss depth).
  `DefaultCombatProfile(GeneratorRegistry, NodeAffixRegistry)` folds only a COMBAT node's FLOOR_TWIST row: budget ->
  `EnemyBudgetOverride.scaled`, ammo -> `AmmoCacheRequest(n, SCATTERED)`, `twistGuarantees(seed)`, FloorEffects
  `.withTwist(id).withSting(NAME).withStatLog(id)` (route string -> "combat:salvage"). World: twist stings use
  new `EventTextSystem.COLOR_AMBER` (8; EventTextRenderer BASE_AMBER from HudConstants.TWIST_AMBER_*); build log
  line adds `twist= budgetScale= scatteredAmmoBoxes=` (A8). **Decision:** SimWorld never fulfilled ANY plan's
  ammo-cache request (pre-existing gap, ELITE vault boxes included); it now stamps them ONLY on twisted floors
  (`fulfillTwistAmmo`, a mirror of World.fulfillAmmoCache) so existing bands do not move. `SimWorld.currentTwistId`
  holds the floor's twist (set in generateLevel) for CP4/5/8.
  Verification: scratch harness `<scratchpad>/harness/TwistMapCheck.java` (not committed) over 300 seeds — ELITE
  affix signature identical with/without the twist pool (0 maps differ), 1316 twisted nodes (blackout 455 / hunted
  394 / salvage 467), min depths 2/4/2, all on COMBAT, regeneration identical, 0 violations.
- **CP3 handover:** `BalanceSchema.twistResults()` (RuleKind.TWIST, in the full sweep after riskPremium) — T1
  for budgetScale>1 rows, T2 otherwise, T3 for any row whose definition names a hunter (resolved by
  `EnemyType.valueOf`). New GameMath `threatShareOfBudget(extra, floor)` and `relativeDeparture(value, ref)`.
  BalanceAuditTest gains `floorTwistsArePricedAgainstPlainCombat` (extension, not a new file). BalanceReport prints
  the twist lines under ROUTE ECONOMICS. **R-TRAJECTORY needed NO code:** `RouteEconomicsModel.walk` already folds
  `affixRowFor(ledger, node)` (the spec assumed it ignored affixes), so the CP2 `setTwistPool` in
  `generateAuditMap` is the whole fold. **Tuning (numbers moved, no band):** `TWIST_HUNTED_THREAT_SHARE` 0.40->0.78
  (real IRON_STALKER share peaks 0.765 at depth 4 — it is a MINI_ELITE); `TWIST_HUNTED_BOUNTY_AMMO_BOXES` 3->10 (T1
  needed >= 8 at that share); `TWIST_WEIGHT_HUNTED` 1.0->0.5 (at >= 0.75 the DEADLIEST trajectory's depth coupling
  steps to 1.72 > 1.65; 0.5 keeps all policies <= 1.58). Final: T2 blackout 0.00; T1 salvage 1.037 (d25); T1 hunted
  1.022 (d25); T3 hunted 0.765 / 0.78 (d4). **Known model gap (put in the authority doc, CP9):** the ledger prices
  HUNTED by scaling the roster, which also scales PLANNED supply/heal the hunter does not bring (~0.90 premium with
  zero bounty) — partly offset by pricing the hunter as always arriving (AS16). A9's "3 boxes" is now 10 —
  record as an IMPLEMENTATION NOTE in the idea file at close. Sweep harnesses: `<scratchpad>/harness/TwistSweep.java`,
  `CouplingSweep.java` (a zero-effect dummy twist emulates a lower hunted frequency without recompiling).

- **CP4 handover:** BLACKOUT is live. `Level.setLightMultiplier/getLightMultiplier` (default 1, clamped [0,1];
  `getTileBrightness` multiplies every tile but '>' — the exit stays LIT, R7b). `EnemyManager.setWakePerception(radius,
  sight)` / `resetWakePerception()` — read ONLY by Phase A's wake check (fields `wakeRadiusTiles/wakeSightTiles`).
  `EnemyRenderer.setLightMultiplier` + `GameMath.darkenedShadeWithFloor(shade, light, min)` (body only; health bars and
  intents are separate passes, untouched). `HudRenderer.setNavOffline(bool)` draws the plate in the mini-map rect.
  `World.activeTwist` (NodeAffixDefinition) + `minimapOffline`; `World.applyTwistRuntimeModifiers(def)` is the ONE
  place a twist's runtime modifiers apply (called from applyPendingFloorEffects by twist id) — CP5 adds the hunter
  clock there; `rebuildForLevel` nulls activeTwist/minimapOffline and the per-floor Level/EnemyManager/EnemyRenderer
  are rebuilt, so A7 holds by construction. SimWorld applies wake perception in generateLevel from `currentTwistId`.
  **How it was verified (A6):** temporary, UNCOMMITTED hacks — Main `new World(seed, StartMode.RESPAWN)` (skips the
  title) and, at the end of World's initial build, `String f=System.getProperty("toon3d.forceTwist"); if (f!=null)
  applyTwistRuntimeModifiers(RouteRegistries.affixes().definition(f));` — then from the mirror
  `Xvfb :77 &`, `DISPLAY=:77 JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dtoon3d.forceTwist=blackout" ./gradlew lwjgl3:run`,
  `DISPLAY=:77 import -window root shot.png` after ~75 s: NAV OFFLINE plate in the mini-map rect, no dot, dark view.
  Both hacks REVERTED before commit (grep TEMP-VERIFY must be empty). Runtime WAV caches assets/game-audio/ and
  assets/story-audio/ are produced by running — never stage them.
- **CP5 handover:** HUNTED is live. New headless `enemy/PlayerTrail` (int ring buffer addressed by SEQUENCE:
  `record/clear/newestSequence/oldestSequence/holds/columnAt/rowAt`), `enemy/HuntedPursuit` (State INACTIVE/WAITING/
  WARNED/ARRIVED/DONE; `turnsOnFloor()`), `enemy/HuntedPursuitListener` (`onHunterWarning()`, `onHunterArrived(c,r)`,
  `onHunterKilled(c,r)`). EnemyManager: public `beginHunt(EnemyType, arrivalCol, arrivalRow, listener)` and
  `getHuntedPursuit()`; `takeTurn` records the trail FIRST (the tick fires on settle, so the tile is settled) and
  advances the clock LAST; the trail branch sits at the top of `computeNextPlan` (after the Hulk finisher) when
  `followsPlayerTrail && !trailHunterEngages` (Manhattan <= 2 or hasLineOfSight); `executeMove` routes a fixed-tile
  move of the hunter to `executeTrailStep` (door forcing: first time costs the turn, a re-closed door it already paid
  for is re-forced free via `Enemy.forcedDoorColumn/Row`). Enemy fields: `followsPlayerTrail, trailSequence,
  trailTargetSequence, trailBlockedTurns, forcedDoorColumn/Row`. `DoorManager.forceOpenForEnemy(c,r)` (-> OPEN now,
  DoorStateListener sound only from CLOSED, never DoorOpenedListener / openedDoorCount, refuses keycard/arena locks) +
  `isLockedAgainstForcing`. **Decision (spec said World fulfils the bounty):** the bounty is placed INSIDE
  `EnemyManager.killEnemy` (`placeHunterBounty`) with the cache's owned-aware rule (owned ammo types from the loadout,
  every type if none, spread evenly) on the nearest free tiles within the new `EnemyConstants.
  TWIST_HUNTED_BOUNTY_SEARCH_RADIUS_TILES` (8 — radius 4 fits only 9 boxes in a 1-wide corridor), so World and
  SimWorld get the identical bounty with no mirror code; World sees the boxes through the existing DropPlacedListener.
  Record this as an IMPLEMENTATION NOTE at close. World: `huntedPursuitListener` field (amber warning / red arrival
  event text, HudConstants strings); `applyTwistRuntimeModifiers` resolves `hunterEnemyTypeName` with
  `EnemyType.valueOf` and calls beginHunt at the player's tile. ALERT voice is fired by EnemyManager at spawn
  (`enemyVoiceListener`, INSECT family = IRON_STALKER's). SimWorld: `beginTwistHunt(twist)` after the floor ledger
  exists; `FloorLedger.hunterArrived/hunterKilled` (CP8's TWIST REPORT reads them). EnemyRenderer: amber border for
  `followsPlayerTrail`. **Verified:** scratch harness `<scratchpad>/harness/ge/tbegvadze/toon3d/level/HuntHarness.java`
  (not committed; javac against core/build/classes/java/main + gdx-1.14.1.jar) drives the REAL EnemyManager/DoorManager
  on a two-room map: warning at turn 35, arrival at 40 on the arrival tile, loop cut (seq 0 -> 7), the closed door
  costs exactly one turn and plays one open sound, player-opened-door count unchanged (A10), engages on sight, kill ->
  10 ammo boxes, state DONE. **NOT verified on device:** the amber health-bar frame and the two event texts (xdotool
  is absent, so 40 turns of touch input cannot be scripted under Xvfb) — build-verified only; reviewer should look.
- **CP6 handover:** `RouteMapOverlayRenderer.buildHeaderText` (runs on focus change, never per frame) builds
  `twistLineText` = `affix.displayName() + LEGEND_TWIST_SEPARATOR + definition.ruleLine()` for ANY revealed focused
  node with an affix row that has a rule line (five ELITE affixes + three twists — read from the row, so A12 holds),
  truncated to `LEGEND_TWIST_MAX_WIDTH` (640). Drawn in `palette.hazardAmber` at `LEGEND_TWIST_OFFSET_Y` (now -20);
  with the line present the two promise lines move to `LEGEND_PROMISE_THREAT/REWARD_OFFSET_Y_WITH_TWIST` (24 / 2) so
  all three sit inside the bar (y 12..92). **Decision:** separator is " - ", not the spec's em dash — the console's
  default BitmapFont (lsans-15) has no U+2014 glyph (it does have the "·" the rule lines use). **Verified (A3):**
  temporary uncommitted hacks (Main RESPAWN start; World end-of-initial-build `startRoomChoiceResolved=true;
  runPhase=PLAYING; onDescentRequested()` behind -Dtoon3d.forceRoute; overlay `present()` giving candidate 0 the
  -Dtoon3d.forceTwist affix token, revealed + focused) -> Xvfb screenshot: "RESISTANCE / STANDARD SUPPLY / HUNTED - A
  HUNTER WALKS YOUR TRAIL" stacked legibly in the bar. Hacks reverted from pre-hack copies before commit.
- **CP7 handover:** `TeachingTopic.TWIST_BLACKOUT(TACTICAL,0) / TWIST_SALVAGE(TACTICAL,0) / TWIST_HUNTED(CRITICAL,0)`
  + `TeachingTopic.forFloorTwist(twistId)` ("TWIST_" + id upper-cased -> forSubjectKey; null = no line). World's
  `applyTwistRuntimeModifiers` requests `BarkTrigger.CONTROL_HINT` with that subject on every twist arrival — the
  one-shot seen-flag (`story.beat.<id>`, generic: confirmed at CP0) makes it once per save (A5); no key, no schema bump.
  Strings `story.bark.control.twist_*` in BarkStrings + the shipped properties. **Decision:** the idea's draft lines
  overflowed the existing two-line bark cap (`StoryBarkTest.everyBarkFitsTheTwoLineCap`, 2 x 40 chars) and the BLACKOUT
  one used an em dash the default story font lacks — rewritten (same meaning) and recorded in docs/story-ui-system.txt
  (TeachingTopic count is now twenty-two; that doc is DONE for this feature). Record as an IMPLEMENTATION NOTE at
  close. Verified: `./gradlew build` (StoryLocalizationTest + two-line cap green). Not watched on device.

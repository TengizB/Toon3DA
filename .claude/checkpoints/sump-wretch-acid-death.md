# CHECKPOINT — Sump Wretch (low-level chaff that bursts into acid)

**PURPOSE:** the live resume record for `.claude/agents/ideas/sump-wretch-acid-death.txt`. This session may be cut off by a
usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and continues from the first step whose
box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-671e32a6-tg3zlt` (on top of generated-enemy-art order 5, d02528f)
**IDEA:** `.claude/agents/ideas/sump-wretch-acid-death.txt` — the spec. Its rules (R1-R10) and acceptance criteria (A1-A15)
are referred to by number below and are NOT repeated here. Owner approval: 2026-10-22 ("Now I like everything, you have my
approvals to continue" / "go ahead with the Sump Wretch").
**LANE:** full lane / `enemy` + `hazard` + `world` + `sim` + `util` (balance) + `level` + `narrative` strings + `render/enemyvisual`.
**TESTS:** BALANCE-BEARING — extend `util/BalanceAuditTest` with ONE method (idea step 5); both gates (`test` + `balanceSim`).
No other new test.
**STARTED:** 2026-10-22
**BASELINE:** d02528f; mirror build: only `narrative.StoryBarkTest.humourLivesNearTheSurfaceAndDiesNearTheCore` red
(pre-existing). balanceSim baseline: to be measured at CP1 (before any balance number moves).

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `sump-wretch-acid-death/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is unverified work: finish it to the step's DONE WHEN or restore it.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `sump-wretch-acid-death/CPn:`, tick in the same commit. Never
`git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY

- Build/verify with the run-game MIRROR (`/tmp/toon3d-runtime/mirror`, `./gradlew -q :core:build`, `:core:balanceSim`).
- STRICT RULE for the new spawn symbol 'o': tile-symbols.txt + LevelLoader + EnemyManager in ONE commit.
- No hardcoded numbers (BalanceConfig / EnemyConstants / GameBalance); formulas in GameMath with the comment block.
- Every new BalanceConfig field in CONSTANT_SUBJECTS (never GRANDFATHERED). No waivers.
- If a sim band moves: tune ONLY the wretch inside the idea's BALANCE NOTES ranges; else stop and report.
- Touch-only, cardinal rules, story strings by id (properties + BarkStrings).

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed.
- [x] **CP1** — shared `HazardManager.applyEnemyDeathHazard` + `igniteToxic(c, r, lifetime)` + EnemyType death-splash data
      (all 0); World + SimWorld call it. DONE WHEN: build + test green, behaviour unchanged (A9).
- [x] **CP2** — SUMP_WRETCH + constants + schema rows + 'o' (STRICT RULE) + ORA line + legacy fallback + SPAWN_POOL; splash
      live. DONE WHEN: build + test green.
- [x] **CP3** — GameMath.deathSplashThreatPoints + baseThreatPoints term + DEATH_SPLASH_ADJACENT_SHARE + the one
      BalanceAuditTest method. DONE WHEN: test green, A12 + A13.
- [x] **CP4** — registry row + AcidSplash seam -> ACID_SPLASH bursts; A1, A2-A8, A15 verified in-engine.
- [ ] **CP5** — balanceSim green (A14).
- [ ] **CP6** — docs; reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

**CP0:** baseline d02528f.

**CP1:** `HazardManager.applyEnemyDeathHazard(EnemyType, col, row, selfDestructMassive, Player)` is THE death-hazard path;
World and SimWorld both pass `(t,c,r,m) -> hazardManager.applyEnemyDeathHazard(t,c,r,m,player)`. Splash = private
`splashAcid` (status only; player tile via floor(position / CELL_SIZE); live-list iteration is safe since nothing is
removed). Cosmetic seam already in place: `HazardManager.AcidSplashListener.onAcidSplash(type, col, row)` fired for the
death tile + each non-wall neighbour; `setAcidSplashListener` — CP4 wires it from World to EnemyVisualLayer (sim leaves it
null). `igniteToxic(c, r, lifetime)` overload; the 2-arg one delegates. EnemyType `deathSplashPlayerStacks()` /
`deathSplashEnemyStacks()` / `deathPuddleTurns()` default 0. Gates: build red only on the known StoryBarkTest;
balanceSim summary byte-identical to the pre-change baseline (scratchpad sim_baseline_summary.txt) → A9 holds.

**CP2:** SUMP_WRETCH sits after VERDANT_SPIRESOWER, before the bosses (ordinals of the bosses shifted by one — nothing
persists ordinals). Numbers: BalanceConfig stats block after MIRE_WRAITH, credits after VORTEX_EYE, splash/puddle after
HAZARD_TOXIC_LIFETIME_TURNS; CONSTANT_SUBJECTS rows after RELIC_SALVAGE_HOOK (stats -> ENEMY_HITS, move -> ENEMY_THREAT_POINTS,
credits -> CREDITS, splash/puddle -> HAZARD). TELEGRAPH "Sump Wretch spit"; HAZARD_DECLARATIONS "sump_wretch_acid_splash".
'o' wired in LevelLoader + EnemyManager + tile-symbols.txt (91/4) + CLAUDE.md (16) in this commit; Level.java / renderers
need NO change (a spawn marker is replaced with floor at load). SPAWN_POOL: after VORTEX_EYE. Legacy fallback = Mire Wraith
blight region. ORA line in properties + BarkStrings. LevelGeneratorSnapshotTest RE-BASELINED (existing test, legitimate
behaviour change: the new pool member reshuffles every generated roster from floor 1) — new digest stable across 2 JVMs.
DEVIATION: the in-engine scratch-level check of A2-A6/A8 is folded into CP4's run-game session (one session, with the
animated sprite + bursts live) rather than run twice. Splash TP not yet priced (CP3).
LevelLoader '}' gap: EncounterBudgetPlanner's comment says the Spiresower is "placed by its own systems" — still report it.

**CP3:** `GameMath.deathSplashThreatPoints(perStack, stacks, share)` + `EnemyType.deathSplashThreatPoints()` (perStack from
the depth-1 on-curve player's max HP) added to `baseThreatPoints()`. BalanceAuditTest +1 method
(`deathSplashIsPricedOnlyWhereItExistsAndTheWretchStaysInBand`). TUNING (inside the idea's 0.3-0.7 range):
DEATH_SPLASH_ADJACENT_SHARE = 0.4, not the designed 0.5 — swept 0.3/0.4/0.45/0.5/0.6/0.7: from 0.45 up the higher wretch
TP thins the depth-1 COMBAT floor, MYSTERY_EV hits 1.30 (ceiling) and linear_corridor d1 gets a room with 4 of one
archetype (V7); 0.7 also tips R-RELIC salvage_hook d15. 0.3 and 0.4 are green. So A12's "TP 15.6 +/- 1" reads 14.4
(splash term 4.8) — record as a DECISION in the idea file at CP6. BalanceReport: Sump Wretch CHAFF TP 14.4 OK, 2|21 OK;
C-ENEMY / C-HAZARD admitted; telegraph row OK; every other archetype's splash term asserted exactly 0 (A13).
"rest EV discount 0.58 OUT-OF-BAND" in the report is pre-existing (identical at CP2) and not an audit failure.
Snapshot re-baselined again (62efb6fb...). V7 note to report: linear_corridor can exceed the 3-per-room cap when the
roster shifts — the placer cap is not airtight on that layout (pre-existing, sensitive to seeds).

**CP4:** `register(SUMP_WRETCH, "sump_wretch")`; World wires `hazardManager.setAcidSplashListener` ->
`EnemyVisualLayer.onAcidSplash(type, col, row)` = the dead creature's IMPACT burst (sump_wretch overrides IMPACT ->
ACID_SPLASH) at the tile floor; a tile within ENEMY_EFFECT_PROJECTILE_IMPACT_LEAD_TILES of the viewer bursts that far in
front of the camera (the projectile-impact rule) so the player's own splash is seen. No order-5 "burst at a tile" seam
existed to reuse (the arrival splash rides the projectile). The listener carries the EnemyType (the idea sketched
(col,row) only) because the burst is the creature's. Melee wretch never launches a projectile: scheduleShot is only
reached from onRangedAttack. Height 0.75 kept (reads lower than the Mire Wraith on screen).
IN-ENGINE (run-game, depth 1, temporary `probe` command added to the MIRROR's CaptureLauncher only — not committed):
A1 moves 21->20->19->18 on every 2nd turn, attacks only once adjacent (-8 = 10 - toughness 2). A2 adjacent kill: player
POISONED x3 the same turn, armour unchanged (0) — BUT ticks are 4/2/1 = 7 HP, not 6/4/2 = 12: the player's existing DoT
pipeline (Player.applyDoTDamage) applies the TOUGHNESS flat reduction (and armour absorption) to EVERY poison tick,
exactly as for a toxic tile; the splash itself is not a hit (R5 holds). Pricing therefore over-estimates slightly
(conservative). Record as a DECISION at CP6, do not change the shared poison path. A3 a wretch dying 2 tiles away in the
same column: no poison. A4 diagonal kill: no poison. A5 Gore Biter + second wretch adjacent got x4; the wounded wretch
(7 HP) died of the poison on turn 2 (DIED cause=STATUS) and left its own puddle. A6 each puddle 'q' lasted 3 turns then
became the 'm' stain; toxic creep onto a neighbour seen (allowed, 0.10/turn). A7 ACID_SPLASH bursts visible on the
splashed tiles + in front of the camera (screens: scratchpad swA_sheet.png). A10 ORA: "It bursts when it dies. Not next
to you - next to its friends." (needs the ABERRATION family line spent first — the existing per-trigger schedule). A15:
only MIRE_WRAITH -> mire_wraith. A8 not staged (no barrel spawner): by construction the splash only touches the
player + the enemy list, and the hook runs on every death cause including a barrel kill.
Pre-existing chainAlertQueue overflow hit again when two dormant spawns woke on one turn (workaround: alert by index).

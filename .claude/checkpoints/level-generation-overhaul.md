# CHECKPOINT — level-generation-overhaul (designed props, real doors, specials first)

**PURPOSE:** the live resume record for `.claude/agents/ideas/level-generation-overhaul.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-9351baa4-lpij6d`
**IDEA:** `.claude/agents/ideas/level-generation-overhaul.txt` — the spec. Its rules (R1-R33, G1-G8) and acceptance
criteria (A1-A15) are referred to by number below and are NOT repeated here.
**LANE:** full lane / `level` (+ `tileset` palette request, `world` R27, `hazard`/`level` GroupBarrelPlacer R26,
`route` profiles R31, `util` constants/GameMath/BalanceConfig/BalanceSchema), agent + docs.
**TESTS:** balance-touching (deletes one GRANDFATHERED BalanceConfig field, changes the floors the audits sweep) —
NO new tests and no new test methods; the existing R-SUPPLY / R-DENSITY / R-HAZARD / C-ROOM / C-GENERATOR rules and
S-bands are the gate. Both gates (`test` + `balanceSim`) on CP6-CP8 and CP14. Existing tests updated only where
behaviour legitimately changed (idea TESTS list). LevelQualityAudit is an instrument (no assertion, no Gradle task).
**STARTED:** 2026-10-23
**BASELINE:** branched from `0a7d596`; build/test baseline recorded under NOTES (CP0).

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `level-generation-overhaul/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `level-generation-overhaul/CPn:`, **with this file's tick
in the same commit as the work**. Never more than one checkpoint uncommitted; split a big one
into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Build/test only from the run-game mirror (`/tmp/toon3d-runtime/mirror`), never root `./gradlew`.
- No new tile symbols, sprites or room types (SCOPE OUT). Only existing symbols in `docs/tile-symbols.txt`.
- Never a switch/if-chain over room types (R32): behaviour comes from `RoomBlueprint` rows; anchors are per-constant enum bodies.
- Every random draw from a seeded stream; dressing uses its own salted stream (R16, R33).
- Constants in `LevelGenConstants`; formulas in `GameMath` with the comment block.
- Footprint targets, encounter budgets, supply, first contact, keycard selection: unchanged balance contracts.
- No balance number moves except HAZARD_GROUP_BARREL_RADIUS_TILES 2->3 if R-HAZARD goes red (CHANGELOG line).
- Naming convention: no abbreviations in identifiers.
- Hand-made `.txt` levels untouched.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — DONE WHEN: `.claude/agents/level-generation-engineer.md` exists with the TECHNICAL NOTES sections;
      `game-level-designer.md` deleted; CLAUDE.md table, orchestrator.md, commands/implement.md, run-game SKILL.md,
      docs/level-design-context.txt, ideas/_TEMPLATE.txt name the new agent; A1 grep clean; no Java changed.
- [ ] **CP2** — DONE WHEN: `level/LevelQualityAudit` runs headless on the UNCHANGED generators, writes
      `core/build/reports/level-audit/summary.txt`; BEFORE figures (I1-I16) recorded below; run command in the agent
      file; build + test green.
- [ ] **CP3** — DONE WHEN: DoorSocket, DoorwayRules (validate + isInDoorClearance), CorridorRouter exist (headless);
      World.tryMakeMachineCandidate (R27) and GroupBarrelPlacer (R26) use clearance / lane rule; doorway/router
      constants in LevelGenConstants; generators not wired; test green (R-HAZARD incl.).
- [ ] **CP4** — DONE WHEN: PropAnchor, PropKit/PropKitRow, RoomSizeWindow, LayoutRoom, RoomDresser exist; every
      RoomBlueprints row has window + kit (+ relaxed); STANDARD has the five regular kits; RoomBlueprint gains
      sizeWindow()/kit()/signatureSymbol() additively; unwired; build + test green.
- [ ] **CP5** — DONE WHEN: SpecialRoomPlanner + GameMath.specialRoomCountForFootprint exist; tileset palette-request
      helper adds kit demands + 'T'/'E' pins; unwired; build + test green.
- [ ] **CP6** — DONE WHEN: ROOMS_MST runs pipeline A (G1); old selection/corridor/placeDoors/prop helpers/LARGE/
      retired rolls gone from LevelGenerator; buildRoomForBridge re-pointed; symbolsStampedInIsolation re-pointed;
      snapshot test re-baselined (dated); RoomBlueprintBuildDemandTest updated; audit A3-A8 pass for ROOMS_MST;
      test + balanceSim green.
- [ ] **CP7** — DONE WHEN: LINEAR runs pipeline A with G2; STORAGE_BAY/REACTOR/bridge/native props/roulette gone;
      eligible/selectionWeight + weight/chance/depth/cap constants deleted; RoomBlueprintRegistryTest updated;
      LEVEL_GEN_PROP_CHANCE + GRANDFATHERED entry deleted (CHANGELOG); audit passes LINEAR; both gates green.
- [ ] **CP8** — DONE WHEN: CAVERN runs G3; audit passes CAVERN; both gates green.
- [ ] **CP9** — DONE WHEN: BOSS_ARENA conforms to G4; audit passes; BossArenaGeneratorTest + fast gate green.
- [ ] **CP10** — DONE WHEN: MED_BAY conforms to G5; audit passes; fast gate green.
- [ ] **CP11** — DONE WHEN: EVENT_ROOM conforms to G6; audit passes; fast gate green.
- [ ] **CP12** — DONE WHEN: GATE_AIRLOCK conforms to G7; audit passes; fast gate green.
- [ ] **CP13** — DONE WHEN: StartGameLevelGenerator conforms to G8; audit start-room row passes; fast gate green.
- [ ] **CP14** — DONE WHEN: RetiredWallDressing + retired rolls deleted; LevelGenConfig scatter flags /
      enableLargeRooms / enableServerRooms deleted, preferredRegularKit set by Cache/Elite profiles (R31); dead
      constants gone (A9); full audit A3-A8 across ALL generators, AFTER figures recorded beside BEFORE; both gates
      green; A14 captures taken.
- [ ] **CP15** — DONE WHEN: docs listed in the idea's CP15 match the code; agent invariant list matches final rules.
- [ ] **CP16** — DONE WHEN: reviewer PASS (or PASS WITH FIXES applied); STATUS: IMPLEMENTED + date.

## NOTES CARRIED FORWARD

- CP0: idea STATUS set to IN PROGRESS in this commit.

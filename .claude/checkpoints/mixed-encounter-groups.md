# CHECKPOINT — mixed encounter groups (no rooms of one enemy type)

**PURPOSE:** the live resume record for `.claude/agents/ideas/mixed-encounter-groups.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `claude/enemy-spawn-variety-7ftubh`
**IDEA:** `.claude/agents/ideas/mixed-encounter-groups.txt` — the spec. Rules V1-V9 and criteria A1-A13
are referred to by number below and are NOT repeated here.
**LANE:** full lane / `level` (planner, templates, placer) + `util` (BalanceConfig, BalanceSchema) + docs.
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` with ONE method; both gates
(`./gradlew test` + `./gradlew balanceSim`); `LevelGeneratorSnapshotTest` data may be regenerated.
**STARTED:** 2026-10-21
**BASELINE:** branched from `af7bad6`. Mirror build (`/tmp/toon3d-runtime/mirror`, see run-game skill):
`:core:test` has ONE pre-existing failure (`narrative.StoryBarkTest`); `:core:balanceSim` exit 0
(baseline summary kept at /tmp/claude-0/baseline-summary.txt — not committed, container-local).

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `mixed-encounter-groups/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `mixed-encounter-groups/CPn:`, **with this file's tick
in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- register() rows / per-constant enum bodies — never a switch on template id, SlotFill or EncounterBehaviour.
- Every balance number in BalanceConfig, declared in BalanceSchema.CONSTANT_SUBJECTS (never GRANDFATHERED).
- Testing Policy: only BalanceAuditTest gets the one new method; never weaken an existing test.
- V1, V6, V7 are never loosened to make a gate pass (the owner's request).
- Planner stays pure and deterministic (all draws from the supplied Random).

## STEP LEDGER

- [x] **CP0** — this file + the idea file, committed and pushed before anything else changes.
- [x] **CP1** — the seven GROUP_* numbers in BalanceConfig + CONSTANT_SUBJECTS -> DENSITY.
      DONE WHEN: `:core:test` shows only the baseline failure (C-CONSTANT green).
- [x] **CP2** — EncounterBehaviour, SlotFill, Slot.fill, 8 template rows, instantiate V1-V3.
      DONE WHEN: build green; no `sameType`, no PACK.
- [x] **CP3** — planner V4 / V5 / V6. DONE WHEN: scratch measurement meets A1-A5, A7-A8 (figures below).
- [x] **CP4** — placer V7 + brood first-contact skip; densityVarietyResults + "room variety"; the one test.
      DONE WHEN: `:core:test` shows only the baseline failure.
- [x] **CP5** — balanceSim green (tuning recorded), snapshot data regenerated.
- [ ] **CP6** — docs updated; A13 run check noted; reviewer gate; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- Scratch measurement harness (not committed): /tmp/claude-0/meas/Measure.java — compile against
  `/tmp/toon3d-runtime/mirror/core/build/classes/java/main` + the gdx jar.
- CP2+CP3 landed as ONE commit (the planner rewrite touched the same methods; splitting it was artificial).
- DEVIATIONS FROM THE SPEC (record in the idea's IMPLEMENTATION NOTE at CP6):
  * TEN template rows, not eight: PAIR (one chaff pair, CALM only) and LOOKOUT (one soldier alone, CALM only,
    at most GROUP_LOOKOUT_MAX_PER_FLOOR = 1, a NEW 8th BalanceConfig number). Why: on CALM floors the per-group
    TP cap (0.35 x cap ~= 45 TP at d1) cannot buy a 4-chaff RABBLE (cheapest mixed 44.6), so CACHE/SHOP floors
    fell from 4.2 to 0.7 bodies; and pairs alone cannot reach an odd body target, which made FootprintPlanner
    rank those layouts "unfielded" and broke "linear_corridor SHOP d1 footprint smallest (E6)". With PAIR +
    LOOKOUT: CACHE d1 4.6 bodies (old 4.2), footprint green.
  * Template.maximumPerFloor() (data, default 0 = unlimited) carries LOOKOUT's cap.
  * V3 tie-break: when the variety caps (not the pool) block the missing behaviour, the draw FAILS (planner tries
    another draw/shape) instead of dropping V3; V3 is dropped only when the pool truly lacks the behaviour.
  * BROOD is eligible only when GROUP_BROOD_SIZE fits under the floor's V6 body cap (V6 binds before V4 on
    small floors).
  * The brood roll is consumed AFTER the "fields no enemies" early return, so enemy-free floors keep their stream.
  * salvage_hook RE-COMPOSED (docs/relic-system.txt 11 predicted it): the new rosters moved its d25 SUPPLY gain
    1.0603 -> 1.0586 (< COMMON floor 1.06; magnitude clamped at 1.00). Added KILL_CREDIT_BONUS 0.03
    (RELIC_SALVAGE_HOOK_KILL_CREDIT_BONUS, CONSTANT_SUBJECTS -> RELIC): gain now 1.071 / 1.076 / 1.076; every
    R-RELIC / CURSE / STACK / SOURCE row green. docs/relic-system.txt must be updated at CP6.
- CP3 MEASUREMENT (planner, 300 seeds, seeds seed*97+depth):
  COMBAT d1/5/10/25: over2 0, floors with a 4-same group 0%, mono 1/2/1/2%, distinct 2.38/2.35/2.37/2.33,
    mix 100%, body-share fails 0, brood floors 5/7/6/11%, spent 94-95% of cap (unchanged), bodies 13.9/14.0 etc.
  ELITE d3..25: over2 0, f4 0, mono <= 3%, distinct 2.45-2.78, mix 100%.
  CACHE: over2 0, f4 0; mono share high (PAIR/LOOKOUT shapes — A3 is COMBAT-only); spent 69-80% of cap (old
    37-62%: the old PACK underspent) — the gates judge it.
- OPEN REDS carried into CP4/CP5: [HAZARD] linear_corridor COMBAT d5 barrel rooms (H14) shortfall 1 (worst floor);
  LevelGeneratorSnapshotTest hash (legit regeneration at CP5).
- CP4 (all 75 BalanceAuditTest green; only LevelGeneratorSnapshotTest [legit, CP5] + baseline StoryBarkTest red):
  * V7 in EncounterPlacer: Region.archetypeCounts; a LARGE room's second group only if merged max <= 3; fallback
    leastCrowdedFor (min merged max, ties roomiest); if even that passes the cap, the group stands in the
    CORRIDORS (collectConnectorPocket, unlimited walk) — needed on small d1 linear_corridor floors where rooms ran
    out (4-same rooms on ~3% of them otherwise). placeGroup's overflow spills member-by-member into the room with
    the fewest of that archetype. First contact skips a BROOD while another group of 2+ exists.
  * FloorContentReport.roomMaximumSameArchetype (computed in FloorPopulator.buildReport from spawns x survey rooms;
    corridor tiles are not a room).
  * BalanceSchema.densityVarietyResults() (in the full sweep) + "room variety (V7)" in densitySweepResults;
    BalanceAuditTest.encounterGroupsMixArchetypesAndBehaviours asserts both.
  * salvage_hook bonus LOWERED 0.03 -> 0.01: at 0.03 the RELIC_CACHE outcome pushed R-MYSTERY-EV to 1.30004 (> 1.30
    ceiling; it was 1.29994 before — a pre-existing 0.00006 margin). At 0.01: mystery 1.29998, salvage_hook
    1.064 / 1.066 / 1.064. Both soft spots are documented in docs/relic-system.txt 11 — update at CP6.
  * GroupBarrelPlacer: hostableCandidates now applies plugsAPassage too (placeInRoom already did), so a room whose
    every tile plugs a passage is not counted eligible-but-unservable. Fixed "[HAZARD] linear_corridor COMBAT d5
    barrel rooms (H14) shortfall 1" that the new layouts exposed (latent pre-existing mismatch).
- CP5 (both gates green; :core:test only the baseline StoryBarkTest red; snapshot stable over two JVM runs):
  * First balanceSim on CP4 code: exit 1 — SR3 rarity felt 0.99 (< 1.00) and SR4 salvage_hook lift 0.67 (13
    probe runs). TACTICAL-NO-RELIC mean depth 3.14 -> 2.98, NAIVE median depth 2.0 -> 1.0: mixed rooms are a bit
    harder (expected — the synergy is not priced).
  * Tuning order (idea BALANCE NOTES): step 1 weights RABBLE 3 -> 3.5, SKIRMISH 2 -> 1.5, BATTERY 2 -> 1.5:
    still SR3 0.98 / SR4 breaching_charge + second_heart 0.75 (edge). Step 3 tried OUT OF ORDER first
    (ENCOUNTER_BUDGET_FILL_TARGET_FRACTION 0.95 -> 0.90): sim green BUT the fast gate broke R-MYSTERY-EV (1.32)
    and TWIST T3 hunted hunter TP share (0.81 > 0.78) — both are priced off the fill fraction. REVERTED to 0.95.
    Step 2 GROUP_MIXED_BEHAVIOUR_MIN_SIZE 3 -> 4 (+ step-1 weights kept): sim GREEN, NAIVE median depth back
    to 2.0, TACTICAL-NO-RELIC 3.0 median. V3 now applies from FOUR members (a 3-body group still fields 2+
    archetypes by V1); the top-up honours V3 too (a group grown to 4+ must mix — needed once the threshold
    moved, else 1% of 4+ groups grew single-behaviour).
  * LevelGeneratorSnapshotTest EXPECTED_DIGEST re-baselined -> 5f3baeb8... with a RE-BASELINE comment.

# CHECKPOINT — balance-overhaul order 2: full floors, fair supply

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-2.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-95bed53c-z8sgd2`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-2.txt` — the spec. Its rules (S1-S10, E1-E7,
C1-C2) and acceptance criteria (A1-A8) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `level` (planners + every generator) + `route` (NodeSupplySpec) + `enemy`
(carriers) + `util` (BalanceConfig SECTION 21 / GameMath / BalanceSchema / BalanceReport) + `sim` +
`render` (route node card).
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` (R-SUPPLY, R-DENSITY replacing
R-SCARCITY / R-SCARCITY-DEPTH / R-HEALDRAIN-DEPTH; route economics re-derived) and
`sim/BalanceSimTest` (S-SUPPLY, re-based S-ECONOMY). No new test files. Existing generator/route
tests UPDATED where behaviour legitimately changes. Both gates every checkpoint.
**STARTED:** 2026-10-01
**BASELINE:** branched from `2207b75`. Gate result recorded in NOTES once the baseline run finishes.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-2/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-2/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Touch-only, turn-based, cardinal-line ranged rule untouched; no enemy AI changes (SCOPE OUT).
- Every number in `util/BalanceConfig` SECTION 21; every formula in `GameMath` with the
  Formula/Derivation/Edge-cases block; registries via `register()` rows, never a switch.
- No new tile symbols, no new room blueprints, no story lines (C1 strings are route-overlay chrome).
- Supply never reads the player's CURRENT HP/ammo (AS1); the emergency ammo lifeline stays.
- SCOPE OUT: per-weapon-role ammo (order 3), hazards near groups (order 4), dev dials (order 9),
  EVENT/MYSTERY/REST numbers + shop prices (order 6).
- OVERRIDE CLAUSE: each overridden rule named + replaced + tests/docs updated in the SAME
  checkpoint; never delete/@Ignore/weaken a test — update it to the new rule.
- Hand-made `.txt` levels keep their authored pickups.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — SupplyPlanner, SupplyRequest/Plan, SupplySlotProvider, NodeSupplySpec registry (all
      node rows) and SECTION 21 exist, headless and unwired.
      DONE WHEN: they exist, planner-level R-SUPPLY checks green, both gates green.
- [ ] **CP2** — ROOMS_MST (LevelGenerator) builds through slots + SupplyPlanner.
      DONE WHEN: its per-room chances and the 0.70 stim path are gone; snapshot test updated; both
      gates green.
- [ ] **CP3** — CAVERN, LINEAR_CORRIDOR, BOSS_ARENA, MED_BAY, EVENT_ROOM, GATE_AIRLOCK build through
      SupplyPlanner; carriers replace drop rolls.
      DONE WHEN: Cavern's MEDICAL_BAY-only medkit path is gone; full R-SUPPLY sweep green;
      BalanceReport SUPPLY/DENSITY table prints; A1, A2 hold.
- [ ] **CP4** — group templates, body targets, group placement, shape + first-contact rules in every
      combat generator.
      DONE WHEN: R-DENSITY (bodies/shape/contact) green; A5 partly (contact, count).
- [ ] **CP5** — footprint targets in the three combat generators.
      DONE WHEN: R-DENSITY footprint + density green; A3, A5 hold.
- [ ] **CP6** — node specs drive COMBAT/ELITE/CACHE/SHOP contents; weapon cadence replaces the pity
      rule; route economics re-derived; node card lines drawn.
      DONE WHEN: route economics tests green; A6 holds.
- [ ] **CP7** — sim FLOOR REPORT, S-SUPPLY + re-based S-ECONOMY; all docs updated; reviewer PASS.
      DONE WHEN: A4, A7, A8 hold; both gates green; reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV (from order 1):** `./gradlew` cannot run in the repo (Android Gradle plugin 403). Gate via a
  desktop-only MIRROR build in the session scratchpad: symlinks to core/lwjgl3/assets/gradle*/docs,
  `settings.gradle` = `include 'lwjgl3','core'`, root `build.gradle` with the `buildscript{}` block
  removed and `configure(subprojects)`. Test XML lands in the REAL `core/build/test-results/`.

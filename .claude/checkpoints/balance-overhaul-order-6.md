# CHECKPOINT — balance-overhaul order 6: the 25-floor run (finite descent, bosses, economy)

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-6.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-7acac777-5826yo`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-6.txt` — the spec. Its rules (F1-F15) and
acceptance criteria (A1-A7) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `route` (finite map, region E, BossTable) + `render/routeicons` (region E crest)
+ `render` (NAV header, DESCENT COMPLETE) + `world` (VICTORY path, ending at final clear) + `narrative`
(repeatable-at-final-clear flag) + `util` (SECTION 25, StatsStore keys, BossBalance, BalanceSchema
rules) + `shop` (economy) + route events/mysteries + `sim` (depth 25, run-outcome bands, S-BOSS,
S-SHOP) + docs.
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` and `sim/BalanceSimTest`; existing
route/story tests UPDATED where finite-run behaviour legitimately changes. No new test files.
Render/story/world work ships with no new tests. Both gates every balance checkpoint.
**STARTED:** 2026-10-12
**BASELINE:** branched from `e8b211f` (order 5 merged, PR #301). Baseline gates recorded in NOTES at CP1.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-6/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-6/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Touch-only, turn-based; register() rows not switches (BossTable, region spec, crest painter, ambience).
- Every number in `util/BalanceConfig` SECTION 25 (or the matching Constants file); formulas in GameMath.
- Story: no hardcoded strings — "DESCENT COMPLETE" is a framing-strings chrome id; vocabulary ladder (F2).
- Render: no allocations in `render()`; procedural ShapeRenderer crest, no textures.
- SCOPE OUT: no new bosses, no NG+/endless, no new story lines beyond chrome, no REST changes, no boss AI
  changes; roguelike_order_19 superseded (not built).
- OVERRIDE CLAUSE: each overridden rule named + replaced + tests/docs updated in the SAME checkpoint;
  never delete/@Ignore/weaken a test — update it to the new rule.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — finite route map (25 layers, 5 regions, region E spec/ambience/crest, no append path); NAV header F6.
      DONE WHEN: A1, A4 hold; route tests updated; both gates green.
- [ ] **CP2** — depth-25 boss defeat ends the run as VICTORY (report label, win stats); F5/D5 ending behaviour.
      DONE WHEN: A2, A3 hold; build green.
- [ ] **CP3** — BossTable rows drive bosses; derivation from ExpectedPlayer.
      DONE WHEN: R-BOSS-* green on all five rows; both gates green.
- [ ] **CP4** — region dial + elite weights (F7); R-REGION updated; F8 run-outcome bands replace S-FAIR's depth band.
      DONE WHEN: both gates green.
- [ ] **CP5** — shop economy (F12, F13); R-CREDITS v2 green; S-SHOP reported.
      DONE WHEN: both gates green.
- [ ] **CP6** — event/mystery rewards in ladder currencies (F15); R-MYSTERY-EV / event value green.
      DONE WHEN: both gates green.
- [ ] **CP7** — S-BOSS consistency reported; all docs; reviewer PASS.
      DONE WHEN: A5, A6, A7 hold; both gates green; reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV (from orders 4/5):** `./gradlew` cannot run in the repo (Android Gradle plugin). Gate via a
  desktop-only MIRROR build in the session scratchpad (`mirror/`): symlinks to core/lwjgl3/assets/
  gradle*/docs/story, `settings.gradle` = `include 'lwjgl3','core'`, root `build.gradle` without the
  `buildscript{}` block. Maven Central may 429 — put the googleapis maven mirror first.

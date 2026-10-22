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
- [ ] **CP2** — SUMP_WRETCH + constants + schema rows + 'o' (STRICT RULE) + ORA line + legacy fallback + SPAWN_POOL; splash
      live. DONE WHEN: build + test green.
- [ ] **CP3** — GameMath.deathSplashThreatPoints + baseThreatPoints term + DEATH_SPLASH_ADJACENT_SHARE + the one
      BalanceAuditTest method. DONE WHEN: test green, A12 + A13.
- [ ] **CP4** — registry row + AcidSplash seam -> ACID_SPLASH bursts; A1, A2-A8, A15 verified in-engine.
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

# CHECKPOINT — balance-overhaul order 4: dodge or die (heavies, guard, armour, status, hazards)

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-4.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-c67612fb-x1x9s1`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-4.txt` — the spec. Its rules (H1-H18) and
acceptance criteria (A1-A9) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `enemy` (HEAVY flag, danger tiles, riposte, stagger-cancel, status through the
shield, Revenant/Cinderforge move-sets) + `entity` (guard, armour absorb/shred, STIM cleanse) +
`hazard` + `level` (barrel placement near groups) + `util` (BalanceConfig SECTION 23, GameMath,
BalanceSchema R-HEAVY, BalanceReport) + `render` (danger markers, mini-map fill, heavy intent, edge
pulse, status turns, ARMOUR HELD) + `narrative` (GUARD/READ_INTENT line review) + `sim` (heavy-aware
TacticalPolicy, S-HEAVY, HEAVY REPORT).
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` (R-HEAVY; update R-TELEGRAPH, R-DOT,
R-BOSS-VERB-CAP, R-ENEMY, hazard TP) and `sim/BalanceSimTest` (S-HEAVY). No new test files. Existing
enemy/story tests UPDATED where behaviour legitimately changes. Both gates every balance checkpoint.
Render / narrative work ships with no new tests.
**STARTED:** 2026-10-10
**BASELINE:** branched from `301e72b`. Mirror build: core:test 453 tests, 1 pre-existing failure —
`StoryBarkTest` ("a joke survived into the deepest strata: bark.depth.core.2", narrative content, not
ours). balanceSim exit 0 (TACTICAL readable deaths 1.00, S-SUPPLY 0.78, 159/200 stalled). Any OTHER red test is ours.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-4/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-4/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Touch-only, turn-based, cardinal-line ranged rule untouched. No dodge button, no real-time reaction:
  avoidance is one ordinary move/strafe/GUARD turn (SCOPE OUT).
- No new verbs / SpecialAbility values / enemies / tile symbols; only existing verbs promoted or
  assigned (Revenant WIND_UP, Cinderforge AREA_STRIKE). Boss AI choreography untouched.
- HEAVY is DATA on the verb/ability (per-constant field), never a switch.
- Every number in `util/BalanceConfig` SECTION 23; every formula in `GameMath` with the
  Formula/Derivation/Edge-cases block. Danger tiles in tile space, cardinal, stop at first blocker.
- Render: no allocations in `render()`; danger tiles from a pre-allocated buffer; marker texture
  generated once and disposed.
- Sound catalog: reuse an existing telegraph row (H17); readable with sound off.
- Story strings by id only; line review per `docs/narrative-authority.txt`, no numbers; update
  `docs/story-ui-system.txt` in the same commit.
- OVERRIDE CLAUSE: each overridden rule named + replaced + tests/docs updated in the SAME checkpoint;
  never delete/@Ignore/weaken a test — update it to the new rule.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — HEAVY flags, SECTION 23 heavy numbers, heavy damage from fractions, heavy TP pricing.
      DONE WHEN: R-HEAVY (damage band, commit, guard cap) and updated R-TELEGRAPH / R-BOSS-VERB-CAP
      green; both gates green.
- [ ] **CP2** — danger tiles for every heavy; 3D markers, mini-map fill, heavy intent icon, edge pulse.
      DONE WHEN: H4 avoidability asserted; A2 holds (build green; desktop check owed if no display).
- [ ] **CP3** — GUARD rework with riposte; armour absorb/shred; status shield.
      DONE WHEN: A3, A4 (shield half) hold; both gates green.
- [ ] **CP4** — status fractions, STIM cleanse, lance SLOW, HUD status turns; R-DOT updated.
      DONE WHEN: A4, A5 hold; both gates green.
- [ ] **CP5** — Revenant / Cinderforge heavy assignments; stagger cancels heavy.
      DONE WHEN: A7 holds; both gates green.
- [ ] **CP6** — hazard fractions; barrel placement near groups.
      DONE WHEN: A6 holds; both gates green.
- [ ] **CP7** — sim avoidance/guard + S-HEAVY + HEAVY REPORT; GUARD/READ_INTENT line review; all docs;
      reviewer PASS.
      DONE WHEN: A1, A8, A9 hold; both gates green; reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV:** `./gradlew` cannot run in the repo (Android Gradle plugin). Gate via the desktop-only MIRROR
  build in the session scratchpad (`mirror/`): symlinks to core/lwjgl3/assets/gradle*/docs/story,
  `settings.gradle` = `include 'lwjgl3','core'` + `pluginManagement` repo, root `build.gradle` with the
  `buildscript{}` block removed and `configure(subprojects)`. Maven Central returns 429 in this
  container — put `maven { url = "https://maven-central.storage-download.googleapis.com/maven2/" }`
  FIRST in both the subprojects repositories and pluginManagement. Test XML lands in the REAL
  `core/build/test-results/`. balanceSim: `./gradlew :core:balanceSim` from the mirror.
- **CP1 handover:** `enemy/HeavyAttack` {CHARGE, LANCE, AREA_STRIKE, SELF_DESTRUCT} with `damageFraction()`,
  `minimumCommitTurns()` (1/1/1/brace 2), `displayName()`. `EnemyType.heavyWindUp()` (SHELL_BRUTE->CHARGE,
  RIMESHELL_LANCER->LANCE, else null; per-constant override — Revenant gets CHARGE in CP5).
  `SpecialAbility.heavyAttack()` (ctor field). `Enemy.heavyReferenceMaxHealth` (set in `initScaledEnemy`, cached
  per depth) + `Enemy.heavyDamage(HeavyAttack)` (x empowered x weak, min 1). `EnemyType.pricedHeavyDamage(kind)`.
  GameMath `heavyDamageAtDepth`, `heavyExpectedDamageForPricing`; `specialEquivalentAreaStrike` DELETED.
  `RuleKind.HEAVY` + `BalanceSchema.heavyResults()`; test `heavyAttacksHoldTheDamageBandAndAreGuardCapped`.
  SECTION 23 holds GUARD_FRONT_ORDINARY/HEAVY as DATA ONLY (TEMPORARY — CP3 wires them; GUARD_FRONT_MULTIPLIER
  still live). Stop-short charge chance DELETED (a reached charge always lands). Lane geometry for CP2:
  `enemy.chargeDirectionColumn/Row`, `enemy.lanceDirectionColumn/Row` locked at commit; AREA_STRIKE = Chebyshev
  square r=1; SELF_DESTRUCT = wall-stopped cross r=2 (`applySelfDestructBlast` walks it).
- **CP1 decision:** charge / lance are NOT priced as heavies in TP (they were never priced as specials; their
  discounted heavy value ~26 at d1 ~= the basic attack they are priced at). Only AREA_STRIKE re-priced
  (Plague Hulk TP ~39.4->34.9, Iron Stalker ~374->309). Snapshot `e365b9fa...`.
- **CP1 gates:** core:test 455, only StoryBarkTest; balanceSim exit 0 — readable deaths 1.00, S-SUPPLY 0.74,
  TACTICAL 139/200 stalled. Boss existing derived verbs pass the new 50% telegraphed cap unchanged.

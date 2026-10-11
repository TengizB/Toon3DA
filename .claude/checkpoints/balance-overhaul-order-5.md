# CHECKPOINT — balance-overhaul order 5: builds that differ (perks, keystones, real rarity)

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-5.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-ad06e52e-di9iut`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-5.txt` — the spec. Its rules (B1-B8) and
acceptance criteria (A1-A7) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `progression` (BuildTag, Perk, PerkModifier, Keystone, PerkDeck, PlayerBuild;
UpgradeCard retired) + `entity` (ability prune / tier counts / tags; damage, reload, clip, heal, guard
seams) + `enemy` / `hazard` / `item` / `shop` (perk seams) + `util` (BalanceConfig SECTION 24, GameMath
valuations, BalanceSchema R-PERK / R-KEYSTONE / R-CARRY / R-ABILITY, BalanceReport PERKS + BUILDS) +
`render` (level-up overlay chips / synergy / pips, keystone frame, inventory BUILD page) + `sim`
(build-bias variants, S-BUILD, BUILD REPORT) + docs.
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` (R-PERK, R-KEYSTONE, R-CARRY, updated
R-ABILITY; R-CARD / R-CARD-BREAKPOINT assertions replaced) and `sim/BalanceSimTest` (S-BUILD). No new
test files. Render work ships with no new tests. Both gates every balance checkpoint.
**STARTED:** 2026-10-11
**BASELINE:** branched from `20c8c41` (order 4 merged, PR #300). Baseline gates recorded in NOTES at CP1.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-5/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-5/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Touch-only (no keys), turn-based, cardinal-line ranged rule untouched; no free aim.
- Perk / keystone / modifier behaviour is DATA + per-constant bodies (EnemyType idiom) — never a switch.
- Every magnitude in `util/BalanceConfig` SECTION 24; every formula in `GameMath` with the
  Formula/Derivation/Edge-cases block. Hot paths query `PlayerBuild` through typed accessors only.
- Perk names / effects are UI text via the level-up overlay's existing text mechanism (not story ids).
- Render: no allocations in `render()`; procedural ShapeRenderer + BitmapFont; thumb-sized plates.
- SCOPE OUT: no meta-progression, no new weapons/enemies/mechanics, no respec/reroll, attributes stay,
  no perk shop sales beyond the existing ability offer.
- OVERRIDE CLAUSE: each overridden rule named + replaced + tests/docs updated in the SAME checkpoint;
  never delete/@Ignore/weaken a test — update it to the new rule.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — BuildTag, Perk, PerkModifier, Keystone, SECTION 24, GameMath valuations.
      DONE WHEN: R-PERK / R-KEYSTONE / R-CARRY asserted on the data and green; UpgradeCard still live;
      both gates green. Split:
  - [x] **CP1a** — data, SECTION 24, valuation model, R-PERK/R-KEYSTONE/R-CARRY + 3 audit methods, report tables.
        DELIBERATE TEMPORARY RED: PK1 WILDFIRE breakpoint + R-CARRY (owner rulings B1/B2 below).
  - [ ] **CP1b** — owner rulings applied; R-PERK/R-KEYSTONE/R-CARRY green; both gates green.
- [ ] **CP2** — PerkDeck + PlayerBuild drive level-ups (B3); UpgradeCard / UpgradeCardPool retired;
      R-CARD / R-CARD-BREAKPOINT replaced; ExpectedPlayer updated.
      DONE WHEN: R-LADDER still green; A1 holds; both gates green.
- [ ] **CP3** — keystones at 5/10/15/20 live with gains and costs.
      DONE WHEN: A2 holds; both gates green.
- [ ] **CP4** — ability prune/merge, tier counts, budgets, tags; R-ABILITY updated.
      DONE WHEN: A4 holds; both gates green.
- [ ] **CP5** — level-up overlay chips / synergy / pips, keystone frame, inventory BUILD page.
      DONE WHEN: build green; A5 holds (desktop check owed if no display).
- [ ] **CP6** — sim build-bias variants + S-BUILD + BUILD REPORT; all docs; reviewer PASS.
      DONE WHEN: A3, A6, A7 hold; both gates green; reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV (from order 4):** `./gradlew` cannot run in the repo (Android Gradle plugin). Gate via a
  desktop-only MIRROR build in the session scratchpad (`mirror/`): symlinks to core/lwjgl3/assets/
  gradle*/docs/story, `settings.gradle` = `include 'lwjgl3','core'`, root `build.gradle` without the
  `buildscript{}` block. Maven Central may 429 — put the googleapis maven mirror first.
- **BASELINE (2026-10-11):** core:test 455, only StoryBarkTest red; balanceSim exit 0 (TACTICAL median depth 2,
  177/200 stalled — navigation waiver; readable 1.00).
- **CP1a handover (builder, Opus):** `progression/` BuildTag, BuildStats (perk bonuses add, keystone costs multiply after;
  `damageMultiplierFor(DamageClass)`, `maxHealthMultiplier()`, `maxArmorMultiplier()`, `healMultiplier()`,
  `shopPriceMultiplier()`, `ammoPickupMultiplier()`, `clipSizeMultiplier()`, `ownExplosionDamageMultiplier()`,
  `barrelDamageMultiplier()`, `meleeKillHealTotalFraction()`, `isFireTileImmune()`, `guardHeavyMultiplierOr(float)`,
  `attributeGrant(Attribute)` + one raw getter per field), PerkModifier, PerkEffect (TOTAL magnitude per rank), Perk (26),
  Keystone (9; applyGains/applyCosts), PlayerBuild (addPerkRank, addKeystone, rankOf, isMaxed, hasKeystone, ranksInTag,
  highestRankTag, attributeGrantTotal, stats, reset). `util/` BuildValuationModel + BuildNicheMetric; RuleKinds PERK /
  KEYSTONE / BUILD_CARRY. Harness: scratchpad `harness/run.sh` (MAIN=CarryDebug for the carry breakdown).
- **CP1a decisions:** Point Man every rank +X% SPREAD at <=2 tiles (r2 +1 knockback, r3 stagger at 3); Shield Wall r2 guarded
  heavy x0.0, r3 all-round guard; First Blood / Steady Aim / Lane Discipline BALLISTIC+RAIL only, Lane >= 4 tiles; DEADEYE crit
  any weapon; Accelerant +FIRE dmg, Arc Jump +chain multiplier as second effects; Butcher +1 STR/rank; carry = kill-speed lift x
  survival lift (geo-mean S1-S8, two-gun loadout); expected ranks = min(capacity, round(0.6 x picks)), picks = (cL-1) - keystone
  levels (11 at d15). Magnitudes moved far from defaults (e.g. First Blood .80/1.6/2.4, Plate Carrier .42/.90/1.48) — every
  move has a "MOVED" javadoc. Unverified model inputs (sim could measure): heavy every 4 turns, heavies 35% of floor damage,
  PYRO stands 5 turns/floor in fire, BLADE melee-kills 45% of bodies.
- **RUNTIME MUST HONOUR (CP2/CP3):** class bonuses add / keystone costs multiply; Steady Aim closes falloff loss by (1-r) on
  BALLISTIC/RAIL; First Blood once per enemy, first bullet only, multiplies with DEADEYE crit; Point Man <=2 tiles, Lane >=4;
  Overcharge k-th pierced body x(1+o*k); STORM CONDUIT arcs to one extra living enemy; PYROMANIAC bonus on every hit and tick
  vs a burning enemy; Wildfire gives other living group members one burn stack; guarded heavy ripostes + reflects 1.4x
  blocked; Second Wind one lethal hit per floor; QUARTERMASTER unlocks gun slot 2; Breacher / SCATTERGUN refunds per kill.
- **OPEN — OWNER (blocks CP1b):** B1 R-CARRY unreachable: VITALITY floor ~2.13 (PK1 + R-KEYSTONE pin it), SCAVENGER tops at
  1.09 (economy tag, not measured in fights), MARKSMAN ~1.72; spread 1.96 (1.24 without SCAVENGER). B2 Wildfire PK1
  breakpoint unreachable: S5 clears in <1 turn at every depth; S2 enemies die to the same spray.

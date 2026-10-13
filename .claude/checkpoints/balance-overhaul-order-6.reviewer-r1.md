# REVIEWER r1 — balance-overhaul-order-6 (the 25-floor run)

```
VERDICT: PASS WITH FIXES  (B1 must land before STATUS: IMPLEMENTED; N1 must land with it)

Spec:    .claude/agents/ideas/balance-overhaul-order-6.txt
         A1 met · A2 met · A3 NOT MET as written (see B1) · A4 met (F2 checked: no StoryTerm row for "Core";
         desktop visual of crest/header/DESCENT COMPLETE not performed, no display) · A5 met · A6 met
         (all depth-derived lines reported + navigation-waived; S-GATE and S-SHOP unspent enforced) · A7 met
         (two stale "endless" lines left in knowledge doc, see NIT 1)
Build:   core:test re-run by reviewer in the scratchpad mirror at HEAD cbd3c9d -> "459 tests completed, 1 failed";
         the one failure is narrative.StoryBarkTest ("a joke survived into the deepest strata: bark.depth.core.2"),
         pre-existing on baseline e8b211f and unrelated to this diff.
         lwjgl3:compileJava green, balanceSim exit 0 -> orchestrator's runs (not re-run: no balance change since CP7a).
Scope:   git diff e8b211f..HEAD (59 files). Java read in full diff: route/*, util/BossBalance, BalanceSchema,
         BalanceConfig SECTION 25, GameMath (2 formulas), StatsStore, shop/ShopContext, world/World/RunStats/
         PersistentStats, narrative/Exchange*, render (report, NAV header, crest), sim/*, all test diffs.
Ckpts:   .claude/checkpoints/balance-overhaul-order-6.md -> CP0..CP6, CP7a, CP7b ticked, each with its own pushed
         `balance-overhaul-order-6/CPn:` commit carrying the tick; CP7/CP7c correctly open. Branch up to date with
         origin. Handover notes carry reasoning; the CP5 temporary MYSTERY_EV waiver is named and CP6 retired it.
```

## BLOCKING (must fix)

**B1. core/src/main/java/ge/tbegvadze/toon3d/world/World.java:3719-3724 (`requestCoreEndingExchange`) — the ending
door opens on ANY boss kill once the save has ever reached the Core, not only at the final clear.**
The guard is `deepestRegion == CORE` (persistent, "ever") + `bossFloorController.isBossDefeated()` (any boss floor).
`ExchangeSystem.requestById` (narrative/ExchangeSystem.java:178-185) now skips the one-shot "seen" check for the
door because of `repeatableAtFinalClear(true)` — but nothing checks that this IS a final clear.
Failure: a player who once reached floor 21 starts a new run, kills the depth-5 Overseer -> the Core ending
exchange opens -> answering FREE/KILL/MERGE ends the run at depth 5 with no VICTORY recorded (and OBEY
"promotes" a floor-5 run). This contradicts F4/F5 ("at a final clear"), A3, and the story doc's new FINAL CLEAR
text ("the ending door then opens as the victory's first beat"). The pre-order-6 code had the same hole (requestById
never read "seen"), but this order re-specified exactly this beat and named the flag "AtFinalClear".
Fix (one line, in the guard): `if (currentDepth < BalanceConfig.RUN_FINAL_DEPTH) return;` — or, equivalently and
tighter, `if (!runStats.isVictory()) return;` (recordFinalClearVictory runs just before it in the update path).
No new test (Testing Policy); verify by reading.

## NON-BLOCKING (should fix)

**N1. .claude/agents/ideas/balance-overhaul-order-6.txt — no IMPLEMENTATION NOTE yet.** The deliberate divergences
are recorded only in the checkpoint file; the idea file is the spec and must carry them before IMPLEMENTED:
(a) F15's "weapon drop at d..d+1" currency not built (no catalog row needs it); (b) boss floors keep one
Fabricator (F13 only fixes non-boss floors); (c) S-SHOP measures offers per Fabricator VISIT (a SHOP node's two
machines = one visit), F14 says "per floor visited"; (d) SHOP_DEPTH_PRICE_SCALE re-fit to 0.0; (e) EVENT moved
out of R-CALM-COST into R-EVENT-VALUE; (f) R-SUPPLY S6 carrier share excludes boss arenas; (g) the
RouteMapGenerator structural repair fallback (extra edge); (h) the ending guard as fixed by B1. All of (a)-(g) I
judge sound and adequately reasoned in the authority doc; they just need to be on the spec.

**N2. world/World.java:3068-3130 vs sim/SimWorld.java:876-909 — the ladder payout is copy-pasted.** The weapon-level
loop with its `Math.max(1, depth) + 1` ceiling, the perk-pool selection and the credit/toll arithmetic exist twice;
the sim "mirrors" World by duplication, so the first retune of one side silently desynchronises the balance sim
from device play (the exact drift the sim exists to catch). Move the headless part (perk pick + amounts) into one
helper both call. Also: `RouteEconomicsModel.creditsPerFloorOfIncome` (route/RouteEconomicsModel.java:149) and
`BalanceSchema.combatFloorCreditIncome` are two implementations of "one floor of income" — the model prices with
one, World/SimWorld pay with the other. They agree today; make one call the other.

**N3. util/BossBalance.java:3 imports route/BossTable, which imports util/BossBalance.** A util<->route cycle around
the boss derivation. It works (lazy holder), and BalanceSchema already reaches into route, so not blocking — but
the BOSS_TABLE_* arrays already live in BalanceConfig; the archetype column could too (or BossTable could live in
util), which would keep `util` free of a route dependency in the stat engine.

## NITS (optional)

1. docs/game-balance-knowledge.txt:138 still calls route-map-system.txt "Branching endless descent"; :748 still says
   "Endless runs past depth ~20 are explicitly unbalanced" (problem 8 is RESOLVED at :1092). Reword both.
2. World.java applyLadderEffect / payMysteryOutcomeOnce use inline seed salts (`0xE7E1D1CEL`,
   `0x4D59535445525921L`) — same pattern as the pre-existing machine salt, but they belong in a Constants file.
3. RouteMapConstants.REGION_E_GATE_FLAVOUR ("The shaft ends here. Nothing lies below.") is player-visible copy in
   Java, consistent with the four existing gate lines; fine under the existing pattern, flagged only because SCOPE
   OUT limits new lines to chrome.

## JUDGEMENTS ON THE CALLS THE CALLER ASKED ABOUT

- **Test updates — legitimate, not weakening.** RouteMapGeneratorTest's two endless-extension tests tested code that
  this order deletes; their 1:1 replacements test A1 (25 layers, 5 regions, BOSS last) and the RegionPlan refusal —
  the "RouteMapGeneratorTest updated" A1 asks for. RouteEconomicsTest now checks 20 seeds through region E instead of
  one appended band: strictly more coverage. `safestAndDeadliestRoutesSpanARealDifficultyRange`: the ammo-scarcity
  assertion's premise died twice (order 2 pinned ammo to demand; F15 deleted the last route-dependent ammo grant), the
  replacement asserts the same claim ("SAFEST banks more resources than DEADLIEST") on the resource that still
  varies by route, the reason is in the javadoc and the authority doc, and the XP-pace half is unchanged at a longer
  horizon. Accepted. LevelGeneratorSnapshotTest re-baseline is a deliberate F7 consequence, recorded.
- **New balance test methods** (BalanceSimTest S-SHOP/S-BOSS, BalanceAuditTest R-EVENT-VALUE) are in balance scope.
  No non-balance test was added beyond the 1:1 replacements above. No test deleted/@Ignored/weakened.
- **OVERRIDE CLAUSE** — every override (F7 dial + shape, F8 bands, SKILL gap, boss table/R-BOSS-FAIR v2, R-CREDITS v2,
  R-MYSTERY-EV v2, EVENT out of R-CALM-COST, R-CALM-COST max 0.40, XP pace max 3.20, PYRO 5.5, cache credits 1.50,
  overclocked vault 2, S6 boss-arena domain) is named, replaced and recorded in the authority doc's OVERRIDE
  records + CHANGELOG in its own checkpoint, with BalanceConfig comments. Satisfied.
- **SHOP_DEPTH_PRICE_SCALE 0.0** — reasoned (flattest ratio across A..E), formula kept. Fine.
- **RouteMapGenerator repair fallback** — deterministic, adds a choice rather than removing one, bounded by the
  existing attempt cap, documented in the route doc. Fine.
- **Game rules** — no keyboard input, no new tile symbol, nothing real-time, no new allocation in a render() path
  (crest painter reuses a member Color). Constants in BalanceConfig SECTION 25 / RouteMapConstants; the two new
  formulas are in GameMath with derivation blocks.

Assign to: java-architect (B1, N2, N3), orchestrator (N1, NIT 1).
Re-review: B1 only — a one-line diff; I will re-check it and N1 on request.

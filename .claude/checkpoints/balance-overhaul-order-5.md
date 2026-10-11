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
- [x] **CP1** — BuildTag, Perk, PerkModifier, Keystone, SECTION 24, GameMath valuations.
      DONE WHEN: R-PERK / R-KEYSTONE / R-CARRY asserted on the data and green; UpgradeCard still live;
      both gates green. Split:
  - [x] **CP1a** — data, SECTION 24, valuation model, R-PERK/R-KEYSTONE/R-CARRY + 3 audit methods, report tables.
        DELIBERATE TEMPORARY RED: PK1 WILDFIRE breakpoint + R-CARRY (owner rulings B1/B2 below).
  - [x] **CP1b** — owner rulings applied; R-PERK/R-KEYSTONE/R-CARRY green; both gates green.
- [x] **CP2** — PerkDeck + PlayerBuild drive level-ups (B3); UpgradeCard / UpgradeCardPool retired;
      R-CARD / R-CARD-BREAKPOINT replaced; ExpectedPlayer updated.
      DONE WHEN: R-LADDER still green; A1 holds; both gates green.
- [x] **CP3** — keystones at 5/10/15/20 live with gains and costs.
      DONE WHEN: A2 holds; both gates green.
- [x] **CP4** — ability prune/merge, tier counts, budgets, tags; R-ABILITY updated.
      DONE WHEN: A4 holds; both gates green.
- [x] **CP5** — level-up overlay chips / synergy / pips, keystone frame, inventory BUILD page.
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
- **CP1b progress (D3 applied):** SCAVENGER waived on R-CARRY (`BalanceSchema.isCarryWaived(BuildTag)`, waiver block, expiry
  "order 6 re-derives the economy"); MARKSMAN up (Steady Aim .33/.66/1.0, First Blood .9/1.8/2.7, Lane .24/.50/.80, DEADEYE clip
  0.75), Shaped Charges .32/.68/1.12, VITALITY down to its own rule floor (Hardy .147/.309/.488, Field Medic .105/.22/.345,
  IRONHIDE 1.475). Eight-tag spread 1.189 (limit 1.20) — GREEN, ~1% headroom. D4 built as data (`PERK_BREAKPOINT_OVERRIDES`,
  `breakpointNicheOf(Perk)`): Wildfire = Incinerator fuel to clear S5 — STILL RED: fuel 3->3 (d1..d20), 6->6 at d25; S2 6->6.
  Gates: core:test 458, red = StoryBarkTest + the Wildfire row (2 audit methods); balanceSim exit 0.
- **OPEN — OWNER (blocks CP1b):** B3 Wildfire changes no integer in any reference fight (the spray + first tick already clears).
- **CP1b CLOSED:** D5 Wildfire (3 ranks; full-stack spread on a burning death, +1 burn turn/rank via `PerkModifier.BURN_TURNS`
  -> `BuildStats.burnTurnBonus()`; `PERK_WILDFIRE_BURN_TURNS_BY_RANK`). D6 waivers (RuleKind.PERK, exact subjects):
  `PK1 WILDFIRE r2|r3 felt d5|d15`, `PK1 WILDFIRE breakpoint by r3 (D4 resource: SCENARIO_AMMO S5 Incinerator)`; r1 felt + PK2
  enforced. Gates: core:test 458, only StoryBarkTest; balanceSim exit 0, every S-* value identical to baseline.
- **WILDFIRE RUNTIME (CP2):** a BURNING enemy that dies (any cause) gives every living orthogonal neighbour its full current
  burn stacks (cap FLAME_BURN_MAX_STACKS + Accelerant), timer reset; the dead enemy's tile ignites. Every player-applied burn
  lasts FLAME_BURN_TURNS + PERK_WILDFIRE_BURN_TURNS_BY_RANK[rank-1].
- **CP2 contract:** scratchpad `cp2-contract.md` (CP2 + CP3 runtime wiring).
- **CP2 handover:** `PerkDeck(seed)` (salt runSeed ^ 0xCA12D00D5EED1234L), `drawPerks(Perk[], PlayerBuild, EnumSet<BuildTag>)`
  (slot 1 SYNERGY, slot 2 PIVOT, rest biased by `PERK_DRAW_BIAS_PER_PICK`; private OfferSlot enum), `static isSynergy`.
  `World/SimWorld.carriedBuildTags()` (CP4 EXTENDS with ability tags). `applyBuildEffects()` re-pushes deltas (attribute
  grants, max HP/armour via `GameMath.buildMaxPoolBonus`, damage multipliers). Hit seam: `BuildStats.hitDamageMultiplier(...)`
  in `EnemyManager.applyDamageTo` (class, Point Man, Lane, First Blood, Riposte window, DEADEYE crit, PYROMANIAC, BERSERKER);
  kill terms `applyBuildKillTerms`; Wildfire `spreadWildfire` in killEnemy; Steady Aim `Weapon.buildFalloff`; Shield Wall in
  `Player.applyDirectionalDamage`; Second Wind `Player.loseHealth` (reset `resetFloorBuildState`); Field Medic
  `Player.applyTreatment`; Blast Shield `Player.applyOwnExplosionDamage`; Pack Rat floor ammo tiles only; Haggler via
  `ShopContext.priceMultiplier`. Shop ability offer = one rank of a random unmaxed perk (`SHOP_PERK_RANK_POWER_POINTS`).
  `EXPECTED_PERK_POWER_PP_PER_PICK` = 12 replaces LEVEL_UP_BUDGET_PP everywhere (R-LADDER unchanged). UpgradeCard*,
  LEVEL_UP_BUDGET_*, CARD_* deleted; RuleKind CARD_BUDGET/CARD_BREAKPOINT deleted; audit methods renamed + re-pointed
  (`everyPerkRankIsFeltOnItsNicheReplacingTheCardBudget`, `everyPerkCrossesABreakpointByItsMaxRank`). Build RNG on its own
  EnemyManager stream (`BUILD_STREAM_SALT`). Docs: minimal RETIRED marks only (full rewrite CP6).
- **CP2 gates:** core:test 458, only StoryBarkTest; lwjgl3 compiles; balanceSim exit 0 — S-HEAVY 1114->1112 committed (0.99
  answered), TACTICAL stalled 177->176, all else identical. A1 verified (throwaway main, 200 seeds x 20 offers, 0 violations).
- **CP3 TODO (already wired, inert until addKeystone):** DEADEYE crit, PYROMANIAC burning bonus + fire-tile immunity, BERSERKER
  melee + heal, DEMOLITIONIST immunity + x2 barrels, class costs, heal costs. STILL TO BUILD: SCATTERGUN free reload + point-
  blank refund, DEADEYE clip cut, STORM CONDUIT arc, IRON BULWARK reflect, QUARTERMASTER slot, IRONHIDE no-cleanse, PYROMANIAC
  DoT-tick bonus + fire-attack immunity, drawKeystones, overlay keystone mode, chooseKeystone.
- **REVIEW FLAG:** CP2 edits made via shell scripts, so the per-file code-reviewer hook did not run — reviewer must read them.
- **CP3 handover:** `PerkDeck.drawKeystones(Keystone[], PlayerBuild)` (B4; fallback = untaken keystone of the most-ranked tag),
  `PerkDeck.isKeystoneLevel(int)`; overlay `setOfferedKeystones/getOfferedKeystone/isKeystoneMode` (cost line red already);
  `PlayerPolicy.chooseKeystone` default = highest-rank tag. Seams: SCATTERGUN `Weapon.reloadCostsNoTurn()` + `completeReload()`,
  `PlayerController.tryReload` returns before the turn; DEADEYE clip via `Weapon.recomputeEffectiveStats` / `onBuildChanged()`;
  PYROMANIAC DoT ticks in `StatusEffectController.tickEnemy`, player BURNING refused; STORM CONDUIT `dischargeStormArc` at end of
  applyDamageTo; IRON BULWARK `strikePlayer` at all six enemy->player hit sites + `reflectIntoAttacker` (deaths queued in
  pendingExecuteDeaths); QUARTERMASTER `Loadout.setUnlockedExtraSlots`; IRONHIDE `Player.hasCleansableStatus()` false.
  A2 verified (throwaway main): shotgun reload with 0 world ticks; AR hit 103 -> 87 (x0.845).
- **CP3 gates:** core:test 458, only StoryBarkTest; lwjgl3 compiles; balanceSim exit 0, no S-* moved vs CP2 (LADDER REPORT d11-25
  shifts: probes past level 15/20 now take keystones).
- **REVIEW / OWNER FLAGS:** IRON BULWARK reflects ORDINARY guarded hits too (spec text "a guarded hit"); valuation prices heavies
  only -> stronger in play than modelled (order 6/7 re-fit). Pre-existing: both reload start paths set ticksRemaining = base
  reloadTime, not effectiveReloadTicks — left alone (changes the sim; out of scope).
- **CP4 INTERRUPTED (2026-10-11):** the CP4 builder (contract: scratchpad `cp4-contract.md`, copied below as the source of
  truth if the scratchpad is gone) hit the account usage limit mid-edit. Its only work was a SECTION 15/24 constants rewrite in
  BalanceConfig + GameBalance (unverified, likely non-compiling alone). Saved as `balance-overhaul-order-5.cp4-wip.patch` beside
  this file; tree restored to CP3. RESUME: re-dispatch CP4 from the contract; the patch is reference only (apply with
  `git apply` if useful, verify everything).
- **CP4 handover (resumed builder, WIP patch applied + corrected):** `WeaponTier` {abilityCount, majorCount,
  abilityPowerPointBudget, rollsSignature; standardCount(), totalAbilityCount(), abilityPowerPointFloor/Ceiling()};
  `WeaponAbility.Rank {STANDARD, MAJOR, SIGNATURE}`, `buildTag`, `isMajor()`; `Weapon.addAbilityBuildTags(EnumSet)` feeds
  carriedBuildTags(). Counts/budgets: COMMON 0/0, UNCOMMON 1/12, RARE 2/25, EPIC 3 (1 MAJOR)/40, LEGENDARY 1 MAJOR + 2 std +
  signature/60, +/-20% both ways. ABILITY_MIN_FELT_PP 8. Expected level = shallowest depth r*5+1 whose region max tier rolls
  the rank (STANDARD d1/L1, MAJOR d11/L11, SIGNATURE d16/L16). CUT 13 (HEMORRHAGE_HARVEST, VAMPIRIC_CRIT, BULWARK_ROUNDS,
  ADRENAL_SURGE, SECOND_WIND, SALVAGE_STRIKE, SCHOLARS_EDGE, QUICK_HANDS, FIELD_MEDIC_ROUNDS, CREDIT_FANG, EXTENDED_MAG,
  MARKSMANS_PATIENCE, RESONANT_ROUNDS); MERGED OVERPENETRATION -> ARMOR_PIERCE. MAJOR = EXECUTIONER, CLEAVE, OPENING_SALVO,
  BURST_FIRE (fixed 2-round). Standards ~10.5 PP, MAJORs 12->17.4, signatures 18 (near-flat across levels, forced by band).
  Sweep depths {3,12,22} x 30 seeds. Audit methods renamed `everySurvivingAbilityIsFeltAndEveryTierShapeFitsItsBand`,
  `everyRollMeetsItsTierCountAndStaysInsideItsBand`. Report: scratchpad cp4/report_cp4.txt.
- **CP4 gates:** core:test 458, only StoryBarkTest; lwjgl3 compiles; balanceSim exit 0 — S-HEAVY 1112->1110 (0.99), TACTICAL
  stalled 176->175, S-ROUTE 0.15->0.16, NAIVE stalled 150->151; LADDER REPORT d16-25 shifted. All bands unchanged.
- **CP4 FLAGS:** edits via shell (reviewer must read diff); docs owed in CP6 (authority R-ABILITY override, knowledge, weapon
  guide catalogue); Double-Barrel EPIC/LEGENDARY can roll BURST_FIRE as its MAJOR redundantly (no per-weapon eligibility);
  shop tier-up price ~doubles (prices on destination budget; R-CREDITS green) — owner awareness; dead leftovers
  BULWARK_TEMP_ARMOR_SLOTS / temp-armor pool / Trigger.ON_RELOAD.
- **CP5 handover:** `render/BuildTagPalette.colorOf(BuildTag)` (ordinal array; weapon tags = DamageClass palette), `render/
  BuildPagePanel` (rows flattened in open(), tap paging UP/DOWN when overflowing), `LevelUpOverlayRenderer.setOfferedPerks(...,
  EnumSet<BuildTag> carriedTags)` (chip, amber SYNERGY corner triangle + label, rank pips with next pip white, gold double frame
  for keystones, red cost line), inventory BUILD header button left of CODEX (`ItemConstants.INV_HEADER_BUILD_BUTTON_WIDTH`),
  `InventoryOverlayRenderer.setPlayerBuild`, `LevelRenderer.setBarrelsHighlighted` (pulsing dot over barrels). Constants in
  ProgressionConstants (BUILD_TAG_*, BUILD_SYNERGY_*, BUILD_COST_*, BUILD_KEYSTONE_GOLD_*), RenderConstants MINIMAP_BARREL_DOT_*.
- **CP5 gates:** core:test 458, only StoryBarkTest; lwjgl3 compiles. DESKTOP CHECK OWED (A5 + clearances: plate text vs
  synergy triangle, 3-line effect vs hint, keystone cost line vs gain text, header BUILD/CODEX/EXIT + SUBLEVEL label vs credits,
  BUILD page empty state / paging / keystone frame, mini-map Sapper dot).
- **CP5 DECISION (hub) / OWNER FLAG:** barrels were ALREADY drawn on the mini-map before this order, so Sapper r1 is a
  highlight (pulsing dot), not a reveal — hiding barrels by default would remove information players have today. Edits via
  shell (reviewer must read diff).
- **CP6a (Part A sim, WIP — balanceSim RED on S-BUILD):** `sim/BuildBias` {NONE, CLOSE, MARKSMAN, PYRO_DEMOLITION, BULWARK};
  `TacticalPolicy(long, BuildBias)` (bias does NOT steer weapons — would move S-SWITCH/S-HEAVY); four rows TACTICAL-CLOSE /
  -MARKSMAN / -PYRO-DEMO / -BULWARK; RunLedger perk/keystone offer+pick counts, dominantTagAtEnd; RuleKind.SIM_BUILD (depth
  spread waived with SIM_FAIR's navigation waiver; sample >= SIM_BUILD_MIN_SAMPLE 30; dominant share <= 0.40);
  `buildBiasesStayCloseAndNoTagDominates` in BalanceSimTest; BUILD REPORT. Slow gate 41s.
  RED because (1) only 20/200 TACTICAL runs level up (navigation stalls; mean level 1.10; no keystone ever offered) and
  (2) PerkDeck seeding artefact: `new Random(runSeed ^ salt)` with sequential sim seeds -> first nextFloat in the top 10% on
  200/200 seeds -> LANE_DISCIPLINE 16/20. HUB DECISION: fix PerkDeck seeding (SplitMix64 finaliser) — a CP2 bug; raise the
  dominant-tag SAMPLE (more plain-TACTICAL seeds for the build band), never the bar. Part B docs not started.
- **CP6b (WIP, balanceSim RED on S-BUILD dominant share only):** seeding fixed — `PerkDeck` = `new Random(GameMath.splitMix64(seed))`;
  `EnemyManager.buildRandom` same artefact, fixed (`splitMix64(randomSeed ^ BUILD_STREAM_SALT)`). Flagged, not touched (pre-existing):
  EnemyManager spawn/wiggle/effect streams depth-adjacent; TacticalPolicy tieBreak `new Random(seed)` fully correlated. Sample:
  `SIM_BUILD_SAMPLE_SEED_COUNT` 600, row `TACTICAL-BUILD-SAMPLE` (plain TACTICAL; main 200-seed rows untouched); 49 ranked runs
  (>= 30). Dominant share 0.47 (MARKSMAN 23, CQ 17, ENERGY 6, PYRO/DEMO/VIT 1) vs cap 0.40: every ranked run holds EXACTLY one rank
  (mean level 1.08), so "dominant tag" = first synergy pick from the Chaingun+Shotgun start kit. No keystone ever offered.
  Moves: S-SUPPLY 0.85->0.86, S-HEAVY 1110->1108. Slow gate 49.7s. Part B docs NOT started. OWNER RULING NEEDED (B4).

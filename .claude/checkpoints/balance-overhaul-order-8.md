# CHECKPOINT — balance-overhaul order 8: the content balance contract

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-8.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-b6b07eb5-sqyr5p`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-8.txt` — the spec. Its rules (G1-G5, K1-K18,
E1-E6, B1-B5) and acceptance criteria (A1-A8) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `util` (BalanceSchema RuleKind.ADMISSION, BalanceReport ADMISSION table) + content
declaration hardening (`enemy`, `entity`, `progression`, `level`) + docs + agent/process files.
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` only (one method per C-rule). No new test
files. Both gates (`./gradlew test`, `./gradlew balanceSim`) at every checkpoint that moves code.
**STARTED:** 2026-10-19
**BASELINE:** branched from `de13c3e` (main, floor-twists merged, PR #308). Baseline gates recorded in NOTES at CP1.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-8/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-8/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- SCOPE OUT: no new content, no retuned number. An existing item failing a C-rule is fixed by a
  declaration or pricing row; a genuine out-of-band value becomes a WAIVER (reason + expiry).
- G5: a missing declaration / pricing row is never waivable.
- No new test files; one BalanceAuditTest method per C-rule; COVERAGE folded into ADMISSION (strictly stronger).
- Negative drill (E6) is run locally and REVERTED — dummy content is never committed.
- Every number in BalanceConfig, every formula in GameMath, register()/enum rows — never a switch.
- The audit stays headless (no LibGDX in anything a C-rule bootstraps).
- Process-file edits (CLAUDE.md, agents) are short pointers to the authority doc (single source).
- No model identifiers in commits or docs.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes; idea STATUS: IN PROGRESS.
- [x] **CP1** — RuleKind.ADMISSION with C-ENEMY, C-FAMILY, C-VERB, C-BOSS; COVERAGE enemy/verb checks folded in.
      DONE WHEN: both gates green on current content.
- [x] **CP2** — C-WEAPON, C-ABILITY, C-PERK, C-AMMO live; COVERAGE weapon/ammo checks folded in.
      DONE WHEN: both gates green.
- [x] **CP3** — C-ITEM, C-STATUS, C-HAZARD, C-ROOM, C-GENERATOR live.
      DONE WHEN: both gates green.
- [x] **CP4** — C-NODE, C-EVENT, C-SHOP, C-REGION live; BalanceReport ADMISSION table prints.
      DONE WHEN: A1, A7 hold; both gates green.
- [x] **CP5** — declaration hardening (E3); C-CONSTANT with GRANDFATHERED / UNBANDED.
      DONE WHEN: A3, A4 hold; both gates green.
- [x] **CP6** — negative drill for all 18 C-rules, messages recorded in the handover below.
      DONE WHEN: A2 holds (nothing from the drill committed).
- [ ] **CP7** — authority doc ADD CONTENT (G, K, X), knowledge doc, weapon guide, enemy doc,
      route/tileset/story cross-links.
      DONE WHEN: A5 holds.
- [ ] **CP8** — CLAUDE.md rule + row, the seven agent files, _TEMPLATE.txt.
      DONE WHEN: A6, A8 hold; both gates green.
- [ ] **CP9** — reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **SERIES ORDER (decided at CP0):** orders 4 (reviewer close pending) and 7 (waiting on owner
  playtest notes) are still IN PROGRESS. The owner invoked `/implement balance-overhaul-order-8`
  explicitly; order 8 adds rules and changes no number, and order 7 only tunes numbers, so it is built
  now. Per AS1, if order 7 later moves a rule, it changes here in the same commit; any BalanceConfig
  field order 7 adds after CP5 must be a rule subject or UNBANDED (C-CONSTANT is doing its job).
- **ENV:** `./gradlew` cannot run in the repo (Android plugin; dl.google.com 403). Gate via the desktop-only
  MIRROR in the scratchpad (`mirror/`: symlinks core/lwjgl3/assets/gradle*/docs/story, `settings.gradle` =
  `include 'lwjgl3','core'`, root build.gradle minus `buildscript{}`, `configure(subprojects)`, googleapis maven
  first). `./gradlew core:test lwjgl3:compileJava -q --continue` (~1 min), `./gradlew core:balanceSim -q` (~10 min).
- **BASELINE (2026-10-19, `de13c3e`):** core:test 464, only `narrative.StoryBarkTest` red (pre-existing joke row
  `bark.depth.core.2`, red since order 6 — not ours); balanceSim green (exit 0).
- **The design decisions the spec left open** are in `balance-overhaul-order-8.contract.md` (committed `3e9fc6b`):
  ADMISSION checks declared / priced / reached-by-its-band-rule, never the band verdict, so it is never waivable.
- **CP1 handover:** `BalanceSchema.AdmissionRule` (18 constants; each carries its evaluator as a
  `Supplier<List<RuleResult>>` method ref — a later CP implements a rule by passing `BalanceSchema::admissionXResults`
  in the constant, nothing else). `admission(rule, id, missing, okDetail)` helper; subject `"C-X: ID"`;
  `RuleResult.toString()` prints `ADMISSION C-X: ID — detail` (E2). `waive(ADMISSION, ...)` throws. Test helper
  `assertAdmitted(results)` in BalanceAuditTest (fails on empty too). New read-only
  `SoundRegistry.hasEnemyFamilyVoice(family, moment)` (forEnemyFamily falls back, so it could not report a miss).
  COVERAGE now holds only weapon / consumable / ammo / relic rows. Counts: C-ENEMY 19, C-FAMILY 12 (6+6),
  C-VERB 5, C-BOSS 5 rows + 3 boss types. Nothing in current content failed. core:test 467 (StoryBarkTest only).
  Calls: WIND_UP is not a family voice (shared by design); summon caps = SUMMON_COUNT_MAX + SUMMON_ROOM_LIVE_CAP.
- **CP2 handover:** `admissionWeaponResults()` (14), `admissionAbilityResults()` (19), `admissionPerkResults()`
  (26 perks + 9 keystones + 9 tags; ids prefixed `PERK ` / `KEYSTONE ` / `TAG `), `admissionAmmoResults()` (5).
  New read-only: `SupplyPlanner.weaponDropPool()`, `Perk.effectCount()`, `Keystone.gainEffectCount()/costEffectCount()`.
  Melee weapons have no headless factory: BalanceSchema `meleeWeaponInstances()` builds Fist/CombatKnife/Hammer/
  MeleeChainsaw directly (as WeaponRoleModel does for ranged) — a new melee item must be added there or C-WEAPON says so.
  Decisions vs the contract: SIGNATURE abilities are legendary-slot rolls, not bound to a weapon
  (`ItemType.getSignatureAbilities()` holds two STANDARD characteristic abilities), so C-ABILITY checks "eligible for
  >= 1 weapon family" instead of "exactly one weapon lists it"; class reach = an `R-ROLE-3 <CLASS>` subject; drop-pool
  membership required for RANGED only. COVERAGE now holds consumable + relic rows only (CP3 removes the kind).
- **CP3 handover:** `admissionItemResults()` (15), `admissionStatusResults()` (10), `admissionHazardResults()` (4:
  'E', 'g' — `ExplosiveBarrelManager.isBarrelCell` treats both as barrels —, fire 'i', toxic 'q'; SpireManager spires
  deal no damage), `admissionRoomResults()` (15, all SUPPLY_NEUTRAL on a 120-seed isolated stamp sweep),
  `admissionGeneratorResults()` (7). RuleKind.COVERAGE is GONE: consumables -> C-ITEM, the relic line ->
  RELIC_SOURCE `"R-RELIC-SOURCE resolves <id>"`. New: `level/RoomSupplyStance`, `RoomBlueprint.supplyStance()` (no
  default — E3 hardening for rooms is DONE here, CP5 need not repeat it), `RoomBlueprints.symbolsStampedInIsolation`,
  `BalanceSchema.hazardDeclarations()`; `LevelGenerator.roomTypeForBlueprint` now package-private.
  Decisions vs the contract: StatusShape gained DOT_FLAT (BLEED = Rend's flat per-turn, GameBalance); enemy-side
  hazard constants checked > 0 only (barrel centre is 1.2 x reference soldier HP); toxic has no DamageClass (no poison
  column) — declared with a reason; C-GENERATOR does NOT require `instanceof SupplySlotProvider` (BOSS_ARENA, MED_BAY,
  EVENT_ROOM, GATE_AIRLOCK supply through FloorPopulator + PocketSupplyRegions) — it requires R-SUPPLY sweep results
  per served type; BalanceReport HAZARDS keeps its rows and gains a "Declared hazards" list from the registry.
  STIMPACK / CREDITS / keycards are NOT_FLOOR_SUPPLIED (no placer; keycards are hand-level decals r/y/b).
- **CP4 handover:** `admissionNodeResults()` (9), `admissionEventResults()` (21; ids `EVENT <id>` / `MYSTERY <NAME>` /
  `AFFIX <id>`), `admissionShopResults()` (5; private `SHOP_OFFER_SPECS`; the literal-price tripwire rolls a real
  DefaultShopOfferSource at d1/5/15/25 x 12 seeds for ALL five categories and compares each price to shopPrice of an
  independently declared value), `admissionRegionResults()` (5). `BalanceReport.printAdmissionTable()` prints after the
  WAIVERS table (A7). Live counts: ENEMY 20, FAMILY 12, VERB 5, BOSS 8, WEAPON 14, ABILITY 19, PERK 44, ITEM 15,
  AMMO 5, STATUS 10, HAZARD 4, ROOM 15, GENERATOR 7, NODE 9, EVENT 21, SHOP 5, REGION 5 — all admitted.
  Decisions: a route-card promise is "threat word + reward promise both or neither" (only COMBAT/ELITE/CACHE/SHOP ship
  one — RouteMapOverlayRenderer draws it only when both exist); an unset RegionSpec multiplier = the documented 1.0
  (Region E has no ELITE row); BossTable.rowForDepth falls back, so C-REGION matches rows() by exact depth; StoryRegion
  via fromRouteRegionIndex (clamps) so the check is index < StoryRegion.values().length. An affix with budgetScale > 1
  must declare a reward. A1 completes at CP5 (C-CONSTANT is the 18th rule).
- **CP5 handover (A1, A3, A4 hold):** all 18 AdmissionRule constants implemented. E3: `EnemyType.heavyWindUp()` and
  `moveSet()` are now ABSTRACT (every constant states them; values unchanged); `Weapon` already compile-enforced
  (damageClass / getItemType / isMelee abstract, ammo type a required constructor argument); WeaponAbility / Perk /
  Keystone constructors `Objects.requireNonNull` their tag (and ability family / trigger / rank); RoomBlueprint
  `supplyStance()` since CP3. C-CONSTANT: 1066 public numeric BalanceConfig fields scanned by reflection;
  `GRANDFATHERED` (1066 names, sorted, SHRINK-ONLY, at the end of BalanceSchema), `CONSTANT_SUBJECTS` and `UNBANDED`
  empty with javadoc; results = one per offending / stale name + a `C-CONSTANT: GRANDFATHERED` summary row.
  Test `everyBalanceNumberIsBandedOrDeclaredUnbanded`. NOTE for order 7: any number it ADDS must go in
  CONSTANT_SUBJECTS or UNBANDED (never GRANDFATHERED).
- **CP6 handover (A2 holds) — THE NEGATIVE DRILL (E6), run 2026-10-19, never committed.** One dummy per C-rule, all
  applied at once by a scratchpad script (`drill/apply_drill.py`), `./gradlew core:test --tests '*BalanceAuditTest*'`
  -> 18 of 18 ADMISSION tests failed (one per rule) + 7 band-rule tests reacting to the dummies; then
  `git checkout -- core/src/main/java` (verified: no dummy left). Dummies: a SOLDIER on a taken spawn symbol with no
  verb, a BOSS no BossTable row fights, a family with no voice/lines, an unpriced damaging non-heavy verb, an
  unclassified weapon item, an unpriced ability, a cost-less second VITALITY keystone, an unstanced consumable, an
  ammo type with no scarcity row/pickup, an undeclared status, a hazard row on a missing constant, a stance-less
  room, an unregistered generator id, a bare node type, an unpriced mystery outcome, a spec-less shop category,
  region A on an unregistered theme, and an unlisted BalanceConfig float. **The drill found one real defect, fixed in
  this commit:** a new AmmoType crashed BalanceSchema's CLASS INIT (`AmmoType.getItemType()` throws for an unmapped
  type, called by ITEM_SUPPLY_STANCES) — every audit test died with NoClassDefFoundError instead of naming the miss.
  `BalanceSchema.ammoPickupItemOrNull()` now catches it; C-AMMO names the miss. Observed messages (first per rule;
  the duplicate-symbol case reports the archetype that lost the symbol, PLAGUE_HULK, beside the dummy's own row):
    ADMISSION C-NODE: DRILL_NODE — no NodeTypeDefinition — register it in RouteRegistries.registerNodeTypes; no NodeSupplySpec — add a row in route/NodeSupplySpecs.registerAll; no NodeEconomics row — register one in route/RouteEconomics
    ADMISSION C-BOSS: DRILL_WARDEN — no BossTable row has an archetype named DRILL_WARDEN — add a BossTable row (route/BossTable) and a BossBalance.Archetype for it
    ADMISSION C-GENERATOR: DRILL_GENERATOR — not registered — RouteRegistries.registerGenerators; serves no node type — add it to BalanceSchema GENERATOR_SERVES
    ADMISSION C-ROOM: drill_room — no supplyStance() — declare SUPPLY_NEUTRAL or SLOT_PROVIDER in RoomBlueprints
    ADMISSION C-HAZARD: drill_spike_trap — player-side constant HAZARD_DRILL_SPIKE_FRACTION not found in BalanceConfig / EffectConstants / GameBalance; enemy-side constant HAZARD_DRILL_SPIKE_FRACTION not found in BalanceConfig / EffectConstants / GameBalance
    ADMISSION C-AMMO: DRILL_FLECHETTES — NOT priced — register a scarcity row in BalanceSchema buildScarcityRowRegistry; no AMMO ItemType pickup — add an AMMO_DRILL_FLECHETTES ItemType and map it in AmmoType.getItemType()
    ADMISSION C-ABILITY: DRILL_ABILITY — abilityPowerPointsAt level 1 = NaN — price it in GameMath.abilityPowerPoints / WeaponRoller.buildAbilityInstance
    ADMISSION C-ENEMY: DRILL_SCRAP_HOUND — blank tacticalVerb() — ORA's archetype line is built from it; ORA archetype line story.bark.enemy.drill_scrap_hound is missing — add it to BarkStrings.registerDefaults and assets/story/story-strings.properties
    ADMISSION C-ENEMY: DRILL_WARDEN — ORA archetype line story.bark.enemy.drill_warden is missing — add it to BarkStrings.registerDefaults and assets/story/story-strings.properties
    ADMISSION C-ENEMY: PLAGUE_HULK — spawnChar() '1' is not unique to this archetype — pick an unused symbol from docs/tile-symbols.txt
    ADMISSION C-FAMILY: DRILL_FAMILY — no ALERT voice — add registerFamilyVoice(DRILL_FAMILY, ...) in audio/GameSoundCatalog; no ATTACK voice — add registerFamilyVoice(DRILL_FAMILY, ...) in audio/GameSoundCatalog; no DEATH voice — add registerFamilyVoice(DRILL_FAMILY, ...) in audio/GameSoundCatalog; early bestiary line story.bark.family.drill_family is missing from BarkStrings / story-strings.properties; deep bestiary line story.bark.family.drill_family.deep is missing from BarkStrings / story-strings.properties
    ADMISSION C-VERB: DRILL_VERB — UNPRICED — register a SpecialVerbPricing equivalence in BalanceSchema SPECIAL_VERB_PRICING; dealsDamage() but heavyAttack() is null — a damaging verb must be a declared HeavyAttack; ORA ability line story.bark.ability.drill_verb is missing from BarkStrings / story-strings.properties
    ADMISSION C-WEAPON: DRILL_FLECHETTE_CARBINE — UNCLASSIFIED — register it in BalanceSchema WEAPON_ITEM_CLASSIFICATIONS (ranged role, melee, or fallback)
    ADMISSION C-PERK: KEYSTONE DRILL_KEYSTONE — no cost effect — a keystone needs >= 1 cost
    ADMISSION C-PERK: TAG VITALITY — 2 keystones — a tag has exactly one (Keystone)
    ADMISSION C-ITEM: DRILL_TRAUMA_KIT — no supply stance — declare it in BalanceSchema ITEM_SUPPLY_STANCES (a SupplyCategory, or NOT_FLOOR_SUPPLIED + reason); consumable is neither a priced heal nor a declared utility — price it as a HealPickupSpec or declare it UTILITY in BalanceSchema CONSUMABLE_CLASSIFICATIONS
    ADMISSION C-STATUS: DRILL_BLEEDOUT — no declaration — add a row to BalanceSchema STATUS_DECLARATIONS (shape, constants, armour interaction, cleanse)
    ADMISSION C-EVENT: MYSTERY DRILL_OUTCOME — no NodeEconomics row — register a mysteryOutcome row in route/RouteEconomics
    ADMISSION C-SHOP: DRILL_OFFER — no SHOP_OFFER_SPECS row — declare its value and weight constant in BalanceSchema
    ADMISSION C-REGION: REGION 0 OUTER FACILITY — ambience 'drill_theme' is not registered — RouteRegistries.registerRegionAmbience
    ADMISSION C-CONSTANT: DRILL_NEW_NUMBER — a new BalanceConfig number must be the subject of a schema rule (CONSTANT_SUBJECTS) or listed in UNBANDED with a one-line reason

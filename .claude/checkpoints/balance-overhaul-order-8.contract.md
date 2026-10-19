# CONTRACT — balance-overhaul order 8 (the C-rules), written by the hub at CP1

The spec is `.claude/agents/ideas/balance-overhaul-order-8.txt` (K1-K18, E1-E6). This file fixes the
DESIGN DECISIONS the spec leaves open, so every checkpoint builds the same shape. It changes nothing
in the spec.

## D-A. The division of labour between ADMISSION and the band rules

An ADMISSION rule (C-*) checks that a content item is DECLARED, PRICED and REACHED BY ITS BAND RULE —
never the band verdict itself. The band verdict stays with the existing rule (R-ENEMY, R-ROLE, R-HEAL,
R-HEAVY, R-ABILITY, R-PERK, R-BOSS-*, R-ROUTE-*, ...), which keeps its waiver policy. That is what makes
G5 mechanical: **every ADMISSION result is a missing declaration / pricing row / dangling reference, so
no ADMISSION result is waivable.** `waive(RuleKind.ADMISSION, ...)` throws `IllegalArgumentException`,
and `RuleResult` never marks an ADMISSION result waived.

"Reached by its band rule" = the item appears as (part of) a subject in that rule's results, or the
band rule's registry holds a row for it (e.g. a HealPickupSpec, a RoleWeapon, a BossTable row).

## D-B. Shape

- `RuleKind.ADMISSION` — one new constant. `RuleKind.COVERAGE` is DELETED once its checks are folded
  (enemy + verb at CP1, weapon + ammo at CP2, consumable at CP3); the relic coverage line moves to
  `RELIC_SOURCE` (subject `"R-RELIC-SOURCE resolves " + relic.id()`) at CP3.
- `public enum AdmissionRule` nested in BalanceSchema, one constant per C-rule, in E1 order:
  `ENEMY("C-ENEMY","K1 ENEMY ARCHETYPE")`, `FAMILY("C-FAMILY","K2 ENEMY FAMILY / TRAIT")`,
  `VERB("C-VERB","K3 ENEMY SPECIAL VERB")`, `BOSS("C-BOSS","K4 BOSS")`, `WEAPON("C-WEAPON","K5 WEAPON")`,
  `ABILITY("C-ABILITY","K6 WEAPON ABILITY")`, `PERK("C-PERK","K7 PERK / KEYSTONE")`,
  `ITEM("C-ITEM","K8 ITEM / PICKUP / CONSUMABLE")`, `AMMO("C-AMMO","K9 AMMO TYPE")`,
  `STATUS("C-STATUS","K10 STATUS EFFECT")`, `HAZARD("C-HAZARD","K11 HAZARD / GAMEPLAY PROP")`,
  `ROOM("C-ROOM","K12 ROOM BLUEPRINT")`, `GENERATOR("C-GENERATOR","K13 LEVEL GENERATOR")`,
  `NODE("C-NODE","K14 ROUTE NODE TYPE")`, `EVENT("C-EVENT","K15 ROUTE EVENT / MYSTERY OUTCOME / NODE AFFIX")`,
  `SHOP("C-SHOP","K16 SHOP OFFER TYPE")`, `REGION("C-REGION","K17 REGION")`,
  `CONSTANT("C-CONSTANT","K18 ANY NEW BALANCE NUMBER")`. Fields `ruleId`, `kindLabel`.
- ONE result per (rule, content item): subject = `"<ruleId>: <contentId>"` (e.g. `"C-ENEMY: PLAGUE_HULK"`),
  content id = the enum constant name / registry id. value 1 = admitted, 0 = not; band 1..1.
  detail when admitted = a short summary of what was declared; detail when not = EVERY missing item,
  `"; "`-joined, each naming the missing thing and where to add it (E2).
- `RuleResult.toString()` for `kind == ADMISSION` prints exactly
  `ADMISSION C-WEAPON: FLECHETTE_CARBINE — <detail>` (E2). Other kinds unchanged.
- Helper: `private static RuleResult admission(AdmissionRule rule, String contentId, List<String> missing, String admittedDetail)`.
- One public method per rule: `admissionEnemyResults()`, `admissionFamilyResults()`, … ,
  `admissionConstantResults()`; plus `admissionResults()` (all of them, AdmissionRule order) and
  `admissionResults(AdmissionRule)`. `evaluate()` calls `admissionResults()` (replacing `coverageResults()`).
- A rule iterates the game's OWN collection (`values()`, or the registry after its headless bootstrap),
  never a hand list — except where no collection exists (hazards, D-F), which is then the registry.

## D-C. Enforcement (BalanceAuditTest)

One `@Test` per C-rule (18), each `assertNoViolations(BalanceSchema.admission<X>Results())` plus
`assertFalse(results.isEmpty())` (a rule that finds no content is itself broken). The COVERAGE test
(`allContentIsCoveredByTheSchema`) and `everySpecialVerbHasARegisteredEquivalence` are UPDATED to the
new rules (behaviour legitimately changed), not deleted silently — fold their assertions into the
C-VERB / C-WEAPON / C-ITEM / C-AMMO tests. `assertNoViolations`' message for an ADMISSION failure says:
"ADMISSION failures are missing declarations or pricing rows and can never be waived (G5) — add the
declaration; see docs/game-balance-authority.txt ADD CONTENT".

## D-D. What each C-rule checks (DECLARE + PRICE + REGISTER + COMMUNICATE, structurally)

K1 C-ENEMY, every EnemyType (bosses too, BOSS-only lines marked):
  role, family, trait non-null; maxHealth > 0, attackDamage > 0, attackRangeTiles >= 1, attackCadenceTurns
  >= 1, moveEveryNTurns >= 1; positionalMultiplier equals one of POSITIONAL_MULT_MELEE / _FAST_MELEE /
  _RANGED; tacticalVerb non-blank; every moveSet() ability has a SpecialVerbPricing; heavyWindUp() (if
  non-null) is a HeavyAttack R-HEAVY evaluates; minSpawnDepth in 1..RUN_FINAL_DEPTH.
  Non-boss: TP band AND hit band registered for its role (folds COVERAGE "enemy"); baseThreatPoints()
  finite and > 0; baseXpReward() == round(XP_PER_THREAT_POINT x baseThreatPoints()) (XP derived);
  fromSpawnChar(spawnChar()) == this type (unique spawn symbol); at least one EncounterGroupTemplate
  slot accepts its role; its ORA archetype line id resolves in StoryStrings.defaults() (the id
  BestiaryCatalog registers for it).
K2 C-FAMILY, every EnemyFamily AND every EnemyTrait:
  family: trait non-null; family voice bound in the audio SoundRegistry for every EnemyVoiceMoment (the
  headless audio catalog) — if the binding is keyed differently, the equivalent "this family has a voice"
  check; its early and deep bestiary line ids resolve. trait: matchup cell registered and > 0 for EVERY
  DamageClass in MatchupCatalog.shared(); a trait other than FLESH has a glyph (hasGlyph()); at least one
  WeaponRoleModel reference scenario's archetype presents the trait (a new trait adds a scenario).
K3 C-VERB, every SpecialAbility:
  SpecialVerbPricing registered (folds COVERAGE "special verb"); dealsDamage() => heavyAttack() != null
  (a damaging verb is a declared HEAVY, committed >= 1 turn ahead, banded by R-HEAVY); the heavy, if any,
  appears in heavyResults() subjects; AMORTISED_SUMMON => the summon caps are > 0 (the per-summoner and
  per-room constants); its ORA ability line id resolves.
K4 C-BOSS, every BossTable.standard() row AND every BOSS-role EnemyType:
  row: depth in 1..RUN_FINAL_DEPTH and in BOSS_TABLE_DEPTHS; targetFightTurns > 0, survivalRatio > 0;
  archetype has BossVerbSpec rows (bossVerbs()); BossStats derived at that depth (finite, > 0 HP);
  R-BOSS-GATE / R-BOSS-FAIR results exist for that depth. BOSS EnemyType: reachable from some row's
  archetype (no orphan boss) — if the current Archetype→EnemyType mapping makes that untrue for a
  legacy boss, report it to the hub rather than inventing a mapping.
K5 C-WEAPON (CP2) every WEAPON ItemType: classified (folds COVERAGE); RANGED -> RoleWeapon row with
  damageClass + ammoType, class in rangedClasses(), a declared niche (ROLE_DECLARED_NICHE_BY_CLASS non-null,
  or BALLISTIC = the generalist) naming an existing scenario id, R-ROLE results reach its class, base
  damage/clip/range > 0, in the planner's weapon drop pool; MELEE -> a melee DamageClass (BLADE/BLUNT).
K6 C-ABILITY (CP2) every WeaponAbility: family, trigger, rank, buildTag non-null; abilityPowerPointsAt finite
  and >= 0 at weapon levels 1..max; a SIGNATURE ability is the signature of exactly one weapon ItemType.
K7 C-PERK (CP2) every Perk: tag non-null, maxRank >= 1, >= 1 effect, display name + effect line non-blank,
  PK1 results reach it. Every Keystone: tag non-null, >= 1 gain AND >= 1 cost effect, gain + cost lines
  non-blank, R-KEYSTONE results reach it. Every BuildTag: >= 1 perk, exactly one keystone, an R-CARRY row.
  (Add `effectCount()` to Perk and `gainEffectCount()` / `costEffectCount()` to Keystone — read-only.)
K8 C-ITEM (CP3) every non-WEAPON ItemType: a declared supply stance in a new BalanceSchema EnumMap
  ITEM_SUPPLY_STANCES (SupplyCategory, or NOT_FLOOR_SUPPLIED + one-line reason); CONSUMABLE ->
  classified heal (HealPickupSpec) or utility (folds COVERAGE); HEAL stance -> a HealPickupSpec; stackable
  -> maxStackSize > 0; description non-blank.
K9 C-AMMO (CP2) every AmmoType: scarcity row (folds COVERAGE); generosity, reserve cap, box size, banking
  target > 0; an AMMO ItemType pickup exists for it; SHOP_AMMO_DAMAGE_PER_POWER_POINT > 0.
K10 C-STATUS (CP3) every StatusType: a row in a new STATUS_DECLARATIONS EnumMap (shape DOT_FRACTION /
  MULTIPLIER / CONTROL, magnitude + duration constant NAMES resolved by reflection in BalanceConfig /
  EffectConstants / GameBalance and > 0, armour-shield interaction, cleanse item or none); a DOT_FRACTION
  magnitude is a fraction in (0, 1).
K11 C-HAZARD (CP3) every row of a new HAZARD_DECLARATIONS registry (barrel 'E', fire 'i', toxic 'q', and any
  other damaging prop/tile found): damage fraction constant names resolve and lie in (0, 1]; sides;
  applied status declared in C-STATUS; DamageClass; placement owner; listed by BalanceReport HAZARDS.
K12 C-ROOM (CP3) every RoomBlueprint: supplyStance() non-null; SUPPLY_NEUTRAL -> build() in isolation over
  a seed sweep emits no pickup and no enemy-spawn symbol.
K13 C-GENERATOR (CP3) every GeneratorId: registered; implements SupplySlotProvider; serves >= 1 node type;
  supply / density sweep results exist for each node type it serves.
K14 C-NODE (CP4) every RouteNodeType: NodeTypeDefinition, NodeSupplySpec, level profile, NodeEconomics row,
  and a region multiplier in every RegionSpec (or the documented default).
K15 C-EVENT (CP4) every FacilityEvent choice, MysteryOutcome and node affix: NodeEconomics row / priced
  effect; every effect in ladder currencies; a threat-raising affix declares a reward.
K16 C-SHOP (CP4) every OfferCategory: a row in a new SHOP_OFFER_SPECS registry (PP value at depth, weight
  constant); GameMath.shopPrice(value) > 0 at every depth 1..RUN_FINAL_DEPTH; a rolled offer's price
  equals the shopPrice of its declared value wherever the roll is deterministic (catches a literal price).
K17 C-REGION (CP4) every RegionSpec in RegionPlan.defaultPlan(): REGION_TP_BUDGET_MULTIPLIER entry, an
  ELITE multiplier, a BossTable row on its last floor, ambience + crest ids registered, a StoryRegion.
K18 C-CONSTANT (CP5) every public static final numeric (or numeric array) field of BalanceConfig is in
  CONSTANT_SUBJECTS (field -> the RuleKind that bands it), UNBANDED (field -> reason), or GRANDFATHERED
  (shrink-only); every GRANDFATHERED / UNBANDED / CONSTANT_SUBJECTS name exists (a stale name fails, which
  is what keeps the list shrinking honestly).

## D-E. Fixing what the rules expose

If current content fails a C-rule, the fix is a DECLARATION or a PRICING ROW (SCOPE OUT: no number
moves, no new content). Never a waiver (G5). If a fix would need a number change or a design decision,
STOP and report it to the hub.

## D-F. Where no collection exists

Hazards (K11) have no enum; HAZARD_DECLARATIONS is the registry, and BalanceReport's HAZARDS rows are
rewired to iterate it so the table and the rule can never disagree.

package ge.tbegvadze.toon3d.util;

import ge.tbegvadze.toon3d.enemy.EnemyRole;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.enemy.HeavyAttack;
import ge.tbegvadze.toon3d.entity.DamageClass;
import ge.tbegvadze.toon3d.entity.MatchupCatalog;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.PlannedPickup;
import ge.tbegvadze.toon3d.level.SupplyCategory;
import ge.tbegvadze.toon3d.level.SupplyPlan;
import ge.tbegvadze.toon3d.level.SupplyPlanner;
import ge.tbegvadze.toon3d.level.SupplyRequest;
import ge.tbegvadze.toon3d.progression.Attribute;
import ge.tbegvadze.toon3d.progression.BuildStats;
import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.progression.Keystone;
import ge.tbegvadze.toon3d.progression.Perk;
import ge.tbegvadze.toon3d.route.NodeSupplySpec;
import ge.tbegvadze.toon3d.route.NodeSupplySpecs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * THE BUILD VALUATION (balance-overhaul order 5) — how much a perk rank, a keystone or a whole tag build
 * is worth, measured on the order-3 reference scenarios ({@link WeaponRoleModel}) and three attrition
 * models, for R-PERK (PK1 felt + breakpoint, PK2 focused), R-KEYSTONE and R-CARRY and for BalanceReport's
 * PERKS / BUILDS tables. Pure, headless, deterministic; per-depth contexts are cached.
 *
 * <p><b>The niche.</b> Every {@link BuildTag} declares a default niche (metric + scenario + weapon) and a
 * perk or keystone whose effect lives elsewhere declares an OVERRIDE here (the registries below — the
 * valuation's concern, kept off the gameplay enums). The offence tags read turns to clear their class's
 * scenario with their reference weapon; BULWARK and VITALITY read floors survived (sustain over a COMBAT
 * floor's incoming damage — Riposte Drill and IRON BULWARK the S6 duel, IRONHIDE the pool's hits to die,
 * Field Medic the floor's heal supply); BLADE the melee reference fight; SCAVENGER a floor's supply in
 * power points (each SCAVENGER perk the component it changes).
 *
 * <p><b>R-CARRY.</b> A tag's expected depth-15 build (round-robin ranks at
 * {@link GameMath#expectedTagRankCount}, plus its keystone) against the same player without it:
 * TTK lift = the geometric mean over S1..S8 of each scenario's lift for the build's two-gun LOADOUT (its own
 * class + the generalist; a tag with no ranged class picks the best pair from every weapon;
 * QUARTERMASTER adds a gun), fights floored at one turn; TTD lift = the floor-survivals lift (SCAVENGER's
 * extra supply spent on heals at the Fabricator's price); carry = TTK lift x TTD lift
 * ({@link GameMath#buildCarryScore}).
 *
 * <p><b>How a build touches a scenario.</b> {@link #buildWeapon} rewrites a registry row's
 * {@link RoleScenarioWeapon} with the build's terms — class damage (through the damage scale), Steady Aim
 * on the per-tile hit ({@link GameMath#falloffReducedHit}), point-blank / long-range tile multipliers,
 * reach, clip ({@link GameMath#buildClipActions}), a free SPREAD reload, knockback and stagger reach, burn
 * stacks, Arc jumps and multiplier, and the walk's build terms (first hit, pierce, arc, burning target,
 * Wildfire). A heavy-capable archetype's fight is shortened by the build's EXTRA riposte and reflect.
 *
 * <p>MODEL ASSUMPTIONS (stated once, here; the docs/game-balance-authority.txt R-PERK entry lands with the
 * order's docs checkpoint): heavies come
 * every {@code BUILD_HEAVY_PERIOD_TURNS} turns from each heavy-capable enemy alive; the player faces
 * {@code HEAVY_EXPECTED_AVOIDANCE} of them (the order-4 pricing share) and guards every one faced, spending
 * the turn; the ±90 arc covers the two side lanes; a floor's incoming damage is the SupplyPlanner's own
 * model ({@code BUILD_FLOOR_HEAVY_DAMAGE_SHARE} of it heavy); a PYRO build stands
 * {@code BUILD_PYRO_FIRE_TURNS_PER_FLOOR} turns in fire; a BLADE build melee-kills
 * {@code BUILD_BLADE_MELEE_KILL_SHARE} of a floor's bodies; Second Wind absorbs one CHARGE-sized heavy.
 */
public final class BuildValuationModel {

    private BuildValuationModel() {}

    // =====================================================================================
    // NICHES
    // =====================================================================================

    /** What a perk / keystone / tag is valued on: a metric, its scenario and weapon (when it reads one). */
    public static final class Niche {
        public final BuildNicheMetric metric;
        /** Reference scenario id (S1..S8), or null for the floor / melee / supply metrics. */
        public final String   scenarioId;
        /** Reference weapon, or null when the metric reads none. */
        public final ItemType weapon;
        /** The tag whose context the floor model reads (PYRO fire exposure, BLADE melee kills). */
        public final BuildTag tag;
        private final int weaponIndex;
        private final int scenarioIndex;

        Niche(BuildNicheMetric metric, String scenarioId, ItemType weapon, BuildTag tag) {
            this.metric        = metric;
            this.scenarioId    = scenarioId;
            this.weapon        = weapon;
            this.tag           = tag;
            this.weaponIndex   = weapon == null ? -1 : weaponIndexOf(weapon);
            this.scenarioIndex = scenarioId == null ? -1 : scenarioIndexOf(scenarioId);
        }

        int weaponIndex()   { return weaponIndex; }
        int scenarioIndex() { return scenarioIndex; }

        /** Short label for the report, e.g. "SCENARIO_TURNS S6 Shotgun". */
        public String label() {
            StringBuilder label = new StringBuilder(metric.name());
            if (scenarioId != null) label.append(' ').append(scenarioId);
            if (weapon != null) label.append(' ').append(WeaponRoleModel.weapons().get(weaponIndex).displayName);
            return label.toString();
        }
    }

    private static final Map<BuildTag, Niche> TAG_NICHES      = buildTagNiches();
    private static final Map<Perk, Niche>     PERK_OVERRIDES  = buildPerkOverrides();
    private static final Map<Keystone, Niche> KEYSTONE_OVERRIDES = buildKeystoneOverrides();
    private static final Map<Perk, Niche>     PERK_BREAKPOINT_OVERRIDES = buildPerkBreakpointOverrides();

    /**
     * Per-row PK1 BREAKPOINT metrics (owner ruling D4): a perk whose felt niche can never cross a whole
     * turn (its scenario clears inside the first turn) declares the INTEGER RESOURCE its breakpoint is read
     * on instead. Its PK1 felt gain stays on its own niche.
     */
    private static Map<Perk, Niche> buildPerkBreakpointOverrides() {
        Map<Perk, Niche> overrides = new EnumMap<>(Perk.class);
        // D4: Wildfire's breakpoint = fuel (Incinerator ammo = sprays x FUEL_PER_SHOT) spent clearing its
        // group scenario S5.
        overrides.put(Perk.WILDFIRE, new Niche(BuildNicheMetric.SCENARIO_AMMO, "S5", ItemType.WEAPON_INCINERATOR, BuildTag.PYRO));
        return Collections.unmodifiableMap(overrides);
    }

    /** The niche a perk's PK1 breakpoint is read on: its D4 breakpoint metric if declared, else its niche. */
    public static Niche breakpointNicheOf(Perk perk) {
        Niche override = PERK_BREAKPOINT_OVERRIDES.get(perk);
        return override != null ? override : nicheOf(perk);
    }

    /** True when the perk's breakpoint is read on a declared D4 resource metric. */
    public static boolean hasBreakpointOverride(Perk perk) {
        return PERK_BREAKPOINT_OVERRIDES.containsKey(perk);
    }

    private static Map<BuildTag, Niche> buildTagNiches() {
        Map<BuildTag, Niche> niches = new EnumMap<>(BuildTag.class);
        // Offence tags: the class's declared role niche (ROLE_DECLARED_NICHE_BY_CLASS) with its workhorse,
        // except PYRO: S5 clears inside the first spray, before any burn exists, so it is measured on S2.
        niches.put(BuildTag.CLOSE_QUARTERS, turns("S6", ItemType.WEAPON_SHOTGUN, BuildTag.CLOSE_QUARTERS));
        niches.put(BuildTag.MARKSMAN,       turns("S1", ItemType.WEAPON_ASSAULT_RIFLE, BuildTag.MARKSMAN));
        niches.put(BuildTag.PYRO,           turns("S2", ItemType.WEAPON_INCINERATOR, BuildTag.PYRO));
        niches.put(BuildTag.DEMOLITION,     turns("S2", ItemType.WEAPON_ROCKET, BuildTag.DEMOLITION));
        niches.put(BuildTag.ENERGY,         turns("S3", ItemType.WEAPON_ARC_CANNON, BuildTag.ENERGY));
        // BULWARK guards heavies and wears armour across a floor: the floor model (guarded heavy share).
        niches.put(BuildTag.BULWARK,        new Niche(BuildNicheMetric.FLOOR_SURVIVALS, null, null, BuildTag.BULWARK));
        niches.put(BuildTag.BLADE,          new Niche(BuildNicheMetric.MELEE_TURNS, null, null, BuildTag.BLADE));
        niches.put(BuildTag.SCAVENGER,      new Niche(BuildNicheMetric.SUPPLY_TOTAL, null, null, BuildTag.SCAVENGER));
        niches.put(BuildTag.VITALITY,       new Niche(BuildNicheMetric.FLOOR_SURVIVALS, null, null, BuildTag.VITALITY));
        return Collections.unmodifiableMap(niches);
    }

    private static Map<Perk, Niche> buildPerkOverrides() {
        Map<Perk, Niche> overrides = new EnumMap<>(Perk.class);
        // Breacher refunds shells: valued on the ammo a group clear costs.
        overrides.put(Perk.BREACHER, new Niche(BuildNicheMetric.SCENARIO_AMMO, "S2", ItemType.WEAPON_SHOTGUN, BuildTag.CLOSE_QUARTERS));
        // Fireproof is defence: the PYRO floor, where the build stands in its own fire.
        overrides.put(Perk.FIREPROOF, new Niche(BuildNicheMetric.FLOOR_SURVIVALS, null, null, BuildTag.PYRO));
        // Blast Shield: the contact-fuse self-hits of the Grenade Launcher in the point-blank bruiser charge.
        overrides.put(Perk.BLAST_SHIELD, turns("S8", ItemType.WEAPON_ROCKET, BuildTag.DEMOLITION));
        // Sapper: a soldier fight that opens on the barrel it stands beside (H14 puts barrels by groups).
        overrides.put(Perk.SAPPER, new Niche(BuildNicheMetric.BARREL_OPENER_TURNS, "S1", ItemType.WEAPON_ROCKET, BuildTag.DEMOLITION));
        // Wildfire spreads fire when a BURNING enemy dies: S5 (burnable chaff), where the burn ticks are
        // what kill. In S2 every member dies to the same spray, so there is never anyone left to ignite.
        overrides.put(Perk.WILDFIRE, turns("S5", ItemType.WEAPON_INCINERATOR, BuildTag.PYRO));
        // The two ENERGY group perks need a group with the weapon they modify (Arc: S5, Plasma: S2).
        overrides.put(Perk.ARC_JUMP, turns("S5", ItemType.WEAPON_ARC_CANNON, BuildTag.ENERGY));
        overrides.put(Perk.OVERCHARGE, turns("S2", ItemType.WEAPON_PLASMA, BuildTag.ENERGY));
        // Field Medic scales what every medkit restores: the floor's heal supply, a TTD component.
        overrides.put(Perk.FIELD_MEDIC, new Niche(BuildNicheMetric.HEAL_SUPPLY, null, null, BuildTag.VITALITY));
        // Riposte Drill shortens a heavy-capable fight: the duel against the S6 bruiser.
        overrides.put(Perk.RIPOSTE_DRILL, duelNiche());
        // Bloodletting heals: the BLADE floor (melee kills x heal).
        overrides.put(Perk.BLOODLETTING, new Niche(BuildNicheMetric.FLOOR_SURVIVALS, null, null, BuildTag.BLADE));
        // SCAVENGER perks each read the supply component they change.
        overrides.put(Perk.PACK_RAT, new Niche(BuildNicheMetric.SUPPLY_AMMO, null, null, BuildTag.SCAVENGER));
        overrides.put(Perk.HAGGLER, new Niche(BuildNicheMetric.SUPPLY_CREDITS, null, null, BuildTag.SCAVENGER));
        overrides.put(Perk.SALVAGER, new Niche(BuildNicheMetric.SUPPLY_CARRIERS, null, null, BuildTag.SCAVENGER));
        return Collections.unmodifiableMap(overrides);
    }

    private static Map<Keystone, Niche> buildKeystoneOverrides() {
        Map<Keystone, Niche> overrides = new EnumMap<>(Keystone.class);
        // DEADEYE's critical opens every fight: measured on the reference SOLDIER fight (S7), not the tank.
        overrides.put(Keystone.DEADEYE, turns("S7", ItemType.WEAPON_ASSAULT_RIFLE, BuildTag.MARKSMAN));
        // IRON BULWARK's reflection shortens a heavy-capable fight: the duel.
        overrides.put(Keystone.IRON_BULWARK, duelNiche());
        overrides.put(Keystone.DEMOLITIONIST, new Niche(BuildNicheMetric.BARREL_OPENER_TURNS, "S1", ItemType.WEAPON_ROCKET, BuildTag.DEMOLITION));
        overrides.put(Keystone.STORM_CONDUIT, turns("S2", ItemType.WEAPON_PLASMA, BuildTag.ENERGY));
        overrides.put(Keystone.BERSERKER, new Niche(BuildNicheMetric.FLOOR_SURVIVALS, null, null, BuildTag.BLADE));
        overrides.put(Keystone.QUARTERMASTER, new Niche(BuildNicheMetric.SUPPLY_CREDITS, null, null, BuildTag.SCAVENGER));
        // IRONHIDE's gain is max HP and its cost is heals: the gain is read on the pool alone (hits to die),
        // so the cost shows on the floor and the medkit instead of netting inside its own niche.
        overrides.put(Keystone.IRONHIDE, new Niche(BuildNicheMetric.HITS_TO_DIE, null, null, BuildTag.VITALITY));
        return Collections.unmodifiableMap(overrides);
    }

    private static Niche duelNiche() {
        return new Niche(BuildNicheMetric.DUEL_SURVIVALS, "S6", ItemType.WEAPON_ASSAULT_RIFLE, BuildTag.BULWARK);
    }

    private static Niche turns(String scenarioId, ItemType weapon, BuildTag tag) {
        return new Niche(BuildNicheMetric.SCENARIO_TURNS, scenarioId, weapon, tag);
    }

    /** The niche a tag's builds are valued on by default. */
    public static Niche tagNiche(BuildTag tag) {
        return TAG_NICHES.get(tag);
    }

    /** A perk's niche: its override, else its tag's default. */
    public static Niche nicheOf(Perk perk) {
        Niche override = PERK_OVERRIDES.get(perk);
        return override != null ? override : TAG_NICHES.get(perk.tag);
    }

    /** True when the perk is valued on an override niche (the report marks it). */
    public static boolean hasOverride(Perk perk) {
        return PERK_OVERRIDES.containsKey(perk);
    }

    /** A keystone's niche: its override, else its tag's default. */
    public static Niche nicheOf(Keystone keystone) {
        Niche override = KEYSTONE_OVERRIDES.get(keystone);
        return override != null ? override : TAG_NICHES.get(keystone.tag);
    }

    // =====================================================================================
    // CONTEXT — everything one depth needs, computed once
    // =====================================================================================

    /** One depth's inputs: the expected player, the ladder scale, and one average COMBAT floor's plan. */
    public static final class Context {
        public final int            depth;
        public final ExpectedPlayer player;
        final float damageScale;
        /** Planned incoming damage of a COMBAT floor, fraction of the on-curve max HP. */
        final float incomingFraction;
        /** Planned heal / armour value of a COMBAT floor, fractions of the on-curve max HP. */
        final float healFraction;
        final float armourFraction;
        /** Supply in depth-1 power points (no build). */
        final float ammoPowerPoints;
        final float healPowerPoints;
        final float creditsPerFloor;
        final float carriedPowerPoints;
        /** Bodies on an average COMBAT floor. */
        final float bodiesPerFloor;
        /** Planned units of each ammo type per floor (the R-ROLE-5 yardstick, from the role table). */
        final Map<AmmoType, Float> ammoUnitsPerFloor;
        /** The kill share of creditsPerFloor (KILL_CREDIT_BONUS pays on it; relics CP4). */
        final float killCreditsPerFloor;
        /** Mean depth-1 power points of one planned ammo unit on the floor (KILL_AMMO_REFUND; relics CP4). */
        final float ammoPowerPointsPerUnit;
        /** BULLETS' reserve cap in power points, and the mean over the other types (FLOOR_ARRIVAL_AMMO). */
        final float generalistReserveCapPowerPoints;
        final float otherReserveCapPowerPoints;

        Context(int depth, ExpectedPlayer player, float damageScale, float incomingFraction, float healFraction,
                float armourFraction, float ammoPowerPoints, float healPowerPoints, float creditsPerFloor,
                float carriedPowerPoints, float bodiesPerFloor, Map<AmmoType, Float> ammoUnitsPerFloor,
                float killCreditsPerFloor, float ammoPowerPointsPerUnit, float generalistReserveCapPowerPoints,
                float otherReserveCapPowerPoints) {
            this.depth              = depth;
            this.player             = player;
            this.damageScale        = damageScale;
            this.incomingFraction   = incomingFraction;
            this.healFraction       = healFraction;
            this.armourFraction     = armourFraction;
            this.ammoPowerPoints    = ammoPowerPoints;
            this.healPowerPoints    = healPowerPoints;
            this.creditsPerFloor    = creditsPerFloor;
            this.carriedPowerPoints = carriedPowerPoints;
            this.bodiesPerFloor     = bodiesPerFloor;
            this.ammoUnitsPerFloor  = ammoUnitsPerFloor;
            this.killCreditsPerFloor = killCreditsPerFloor;
            this.ammoPowerPointsPerUnit = ammoPowerPointsPerUnit;
            this.generalistReserveCapPowerPoints = generalistReserveCapPowerPoints;
            this.otherReserveCapPowerPoints = otherReserveCapPowerPoints;
        }
    }

    private static final Map<Integer, Context> CONTEXT_CACHE = new HashMap<>();

    /** The cached context at a depth. */
    public static synchronized Context context(int depth) {
        Context cached = CONTEXT_CACHE.get(depth);
        if (cached != null) return cached;
        Context built = buildContext(depth);
        CONTEXT_CACHE.put(depth, built);
        return built;
    }

    private static Context buildContext(int depth) {
        ExpectedPlayer player = GameMath.expectedPlayerAtDepth(depth);
        float damageScale = player.referenceHitDamage / GameMath.ladderReferenceHitDamage();
        float hitGrowth = GameMath.expectedHitGrowthAtDepth(depth);
        NodeSupplySpec combat = NodeSupplySpecs.combat();
        float incoming = 0f, heals = 0f, armour = 0f, ammoPoints = 0f, credits = 0f, carried = 0f, bodies = 0f;
        // Relic terms (CP4): accumulated beside the original sums, never into them (A11 byte identity).
        float killCreditTotal = 0f, ammoUnits = 0f;
        int seeds = Math.max(1, BalanceConfig.BUILD_SUPPLY_SEED_COUNT);
        for (int seedIndex = 0; seedIndex < seeds; seedIndex++) {
            long seed = GameMath.floorSeed(BalanceConfig.BUILD_SUPPLY_SEED_SALT + seedIndex, depth);
            List<EnemyType> roster = BalanceSchema.syntheticRoster(combat, depth, seed);
            SupplyPlan plan = SupplyPlanner.plan(new SupplyRequest(depth, combat, roster, player,
                    SupplyPlanner.expectedPlayerAmmoTypes(), seed, false, 0));
            incoming += plan.incomingFraction;
            heals    += plan.plannedValue(SupplyCategory.HEAL);
            armour   += plan.plannedValue(SupplyCategory.ARMOUR);
            bodies   += roster.size();
            float killCredits = 0f;
            for (EnemyType type : roster) killCredits += type.baseCreditReward();
            credits += killCredits * (1f + BalanceConfig.CREDIT_DEPTH_SCALE * (depth - 1));
            killCreditTotal += killCredits * (1f + BalanceConfig.CREDIT_DEPTH_SCALE * (depth - 1));
            for (PlannedPickup pickup : plan.pickups()) {
                float points = pickupPowerPoints(pickup, hitGrowth);
                if (pickup.category == SupplyCategory.AMMO) {
                    ammoPoints += points;
                    ammoUnits += ammoUnitsOf(pickup, depth);
                }
                if (pickup.category == SupplyCategory.CREDITS) credits += pickup.creditAmount;
                if (pickup.carried) carried += points;
            }
        }
        float healPoints = GameMath.healPowerPoints((heals + armour) / seeds, BalanceConfig.PLAYER_MAX_HEALTH,
                BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT);
        float otherCapPoints = 0f;
        int otherTypes = 0;
        for (AmmoType ammoType : AmmoType.values()) {
            if (ammoType == AmmoType.BULLETS) continue;
            otherCapPoints += reserveCapPowerPoints(ammoType, depth, hitGrowth);
            otherTypes++;
        }
        return new Context(depth, player, damageScale, incoming / seeds, heals / seeds, armour / seeds,
                ammoPoints / seeds, healPoints, credits / seeds, carried / seeds, bodies / seeds,
                WeaponRoleModel.table(depth).supplyPerFloor, killCreditTotal / seeds,
                ammoUnits > 0f ? ammoPoints / ammoUnits : 0f,
                reserveCapPowerPoints(AmmoType.BULLETS, depth, hitGrowth),
                otherTypes > 0 ? otherCapPoints / otherTypes : 0f);
    }

    /** Units in one planned ammo pickup (its damage value over the type's per-unit damage at the depth). */
    private static float ammoUnitsOf(PlannedPickup pickup, int depth) {
        AmmoType ammoType = AmmoType.fromPickupChar(pickup.symbol);
        if (ammoType == null) return 0f;
        float perUnit = GameMath.supplyDamagePerUnitAtDepth(SupplyPlanner.depthOneDamagePerUnit(ammoType), depth);
        return perUnit > 0f ? pickup.value / perUnit : 0f;
    }

    /** One ammo type's full reserve cap in depth-1 power points (the FLOOR_ARRIVAL_AMMO refill's yardstick). */
    private static float reserveCapPowerPoints(AmmoType ammoType, int depth, float hitGrowth) {
        float perUnit = GameMath.supplyDamagePerUnitAtDepth(SupplyPlanner.depthOneDamagePerUnit(ammoType), depth);
        return GameMath.ammoPowerPoints(ammoType.getReserveCap() * perUnit, hitGrowth,
                BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT);
    }

    /** One planned pickup in depth-1 power points (ammo / heal / armour; credits and weapons read 0 here). */
    private static float pickupPowerPoints(PlannedPickup pickup, float hitGrowth) {
        if (pickup.category == SupplyCategory.AMMO) {
            return GameMath.ammoPowerPoints(pickup.value, hitGrowth, BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT);
        }
        if (pickup.category == SupplyCategory.HEAL || pickup.category == SupplyCategory.ARMOUR) {
            return GameMath.healPowerPoints(pickup.value, BalanceConfig.PLAYER_MAX_HEALTH,
                    BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT);
        }
        return 0f;
    }

    // =====================================================================================
    // THE BUILD ON A WEAPON
    // =====================================================================================

    /** A registry row's scenario model with the build's terms folded in (the row itself is untouched). */
    static RoleScenarioWeapon buildWeapon(WeaponRoleModel.RoleWeapon row, BuildStats stats) {
        RoleScenarioWeapon model = row.model;
        DamageClass damageClass = row.damageClass;
        boolean marksman = BuildTag.MARKSMAN.ownsDamageClass(damageClass);
        boolean spread = BuildTag.CLOSE_QUARTERS.ownsDamageClass(damageClass);
        boolean energy = BuildTag.ENERGY.ownsDamageClass(damageClass);

        int reach = model.baseHitByTile.length + (marksman ? stats.rangeBonusTiles() : 0);
        float[] hits = new float[reach];
        for (int distance = 1; distance <= reach; distance++) {
            float hit = model.baseHitAt(Math.min(distance, model.baseHitByTile.length));
            if (marksman) {
                hit = GameMath.falloffReducedHit(hit, row.undroppedHit, stats.ballisticRailFalloffReduction());
                if (distance >= BalanceConfig.BUILD_LONG_RANGE_MIN_TILES) hit *= 1f + stats.longRangeDamageBonus();
            }
            if (spread && distance <= BalanceConfig.BUILD_POINT_BLANK_MAX_TILES) {
                hit *= 1f + stats.spreadPointBlankDamageBonus();
            }
            hits[distance - 1] = hit;
        }
        float firstHitBonus = GameMath.combinedFirstHitBonus(marksman ? stats.fullHealthTargetDamageBonus() : 0f,
                stats.firstHitGuaranteedCritical(), BalanceConfig.CRIT_DAMAGE_MULTIPLIER);
        boolean chain = model.pattern == RoleScenarioPattern.CHAIN;
        boolean pierce = model.pattern == RoleScenarioPattern.PIERCE;
        return new RoleScenarioWeapon(model.displayName,
                GameMath.buildClipActions(model.clipActions, model.ammoPerAction, stats.clipSizeMultiplier()),
                spread && stats.spreadReloadFree() ? 0 : model.reloadTurns,
                model.ammoPerAction, model.hitsPerAction, model.accuracy, hits, model.pattern,
                model.splashNeighbourHit,
                model.chainJumps + (chain ? stats.arcChainJumpBonus() : 0),
                Math.min(1f, model.chainMultiplier + (chain ? stats.arcChainMultiplierBonus() : 0f)),
                model.chargeTurns, model.burnFraction, model.burnImpactHit,
                model.burnTurns + (model.burns() ? stats.burnTurnBonus() : 0),
                model.burnMaxStacks + (model.burns() ? stats.burnMaxStackBonus() : 0),
                model.knockbackMaxTiles + (spread ? stats.knockbackBonusTiles() : 0),
                model.staggerMaxTiles + (spread ? stats.staggerBonusTiles() : 0),
                model.selfDamageWhenAdjacent,
                firstHitBonus,
                energy && pierce ? stats.piercePerEnemyBonus() : 0f,
                energy ? stats.energyArcFraction() : 0f,
                stats.burningTargetDamageBonus(),
                stats.wildfire());
    }

    /**
     * Enemy HP of a scenario's archetype at the context's depth (matchup-neutral eHP, grown), x the build's
     * ENEMY_HEALTH_MULTIPLIER (exactly 1 without a relic; scenarios are never bosses, which the relic skips).
     */
    private static float scenarioHitPoints(Context context, WeaponRoleModel.RoleScenario scenario, BuildStats stats) {
        return GameMath.enemyHealthAtDepth(scenario.archetype.neutralEffectiveHitPoints(), context.depth)
                * stats.enemyHealthMultiplier();
    }

    /** The relic damage term on every scenario hit: LOW_HEALTH_DAMAGE (exactly 1 without a relic). */
    private static float relicDamageMultiplier(BuildStats stats) {
        return GameMath.relicLowHealthDamageMultiplier(stats.lowHealthDamageBonus(),
                BalanceConfig.BUILD_BLADE_LOW_HEALTH_TURN_SHARE);
    }

    /** A relic blast tile's damage on a scenario's archetype: the reference-soldier yardstick x the EXPLOSIVE matchup. */
    private static float relicBlastOn(Context context, WeaponRoleModel.RoleScenario scenario, float fraction) {
        return GameMath.relicBlastDamage(fraction, BalanceSchema.barrelReferenceSoldierMaxHealth(context.depth))
                * MatchupCatalog.shared().multiplier(DamageClass.EXPLOSIVE, scenario.archetype.trait());
    }

    /**
     * A cleared group fight's turns under the relic blast kinds: CORPSE_BLAST
     * ({@link GameMath#relicCorpseBurstScenarioTurns}) and DOOR_BLAST ({@link GameMath#relicDoorOpenerTurns} —
     * a share of fights opens on a turn-less door blast, re-walked on its survivors at their mean health). Both
     * are priced on the walk's output (the GameMath blocks state the approximation). Exactly {@code turns}
     * without a relic.
     */
    private static float relicFightTurns(Context context, RoleScenarioWeapon model, WeaponRoleModel.RoleScenario scenario,
                                         float hitPoints, float scale, float matchup, BuildStats stats, float turns) {
        float doorFraction = stats.doorBlastFraction();
        float corpseFraction = stats.corpseBlastFraction();
        if (!(doorFraction > 0f) && !(corpseFraction > 0f)) return turns;
        float burst = corpseFraction > 0f ? relicBlastOn(context, scenario, corpseFraction) : 0f;
        float result = GameMath.relicCorpseBurstScenarioTurns(turns, scenario.groupSize, hitPoints, burst,
                BalanceConfig.RELIC_VALUATION_CORPSE_ADJACENCY);
        if (doorFraction > 0f) {
            float centre = relicBlastOn(context, scenario, doorFraction);
            float ring = relicBlastOn(context, scenario, doorFraction * BalanceConfig.RELIC_BLAST_RING_SHARE);
            int survivors = GameMath.relicBlastSurvivorCount(hitPoints, centre, ring, scenario.groupSize);
            float openerTurns = 0f;
            if (survivors > 0) {
                float meanHealth = GameMath.relicBlastSurvivorMeanHealth(hitPoints, centre, ring, scenario.groupSize);
                RoleScenarioOutcome opener = GameMath.roleScenarioClear(model, survivors,
                        BalanceConfig.ROLE_GROUP_TARGETS_IN_LANE, scenario.distanceMin, scenario.distanceMax,
                        meanHealth, scale, matchup, BalanceConfig.ROLE_SCENARIO_TURN_CAP);
                openerTurns = opener.cleared ? opener.turnsToClear : BalanceConfig.ROLE_SCENARIO_TURN_CAP;
                // Blast kills never burst (R8), so only the opener's survivors feed the corpse burst.
                openerTurns = GameMath.relicCorpseBurstScenarioTurns(openerTurns, survivors, meanHealth, burst,
                        BalanceConfig.RELIC_VALUATION_CORPSE_ADJACENCY);
            }
            result = GameMath.relicDoorOpenerTurns(result, openerTurns, BalanceConfig.RELIC_VALUATION_DOOR_FIGHT_SHARE);
        }
        return result;
    }

    /**
     * Score of one weapon in one scenario under a build (lower = better): turns to clear, or S8's weighted
     * bruiser hits taken. A heavy-capable archetype's HP is first reduced by the build's EXTRA riposte and
     * reflect damage over the fight (the order-3 table models neither, so only the build's increment).
     */
    static float scenarioScore(Context context, int weaponIndex, int scenarioIndex, BuildStats stats) {
        return scenarioOutcome(context, weaponIndex, scenarioIndex, stats).score;
    }

    /** Score + ammo + clear flag of one weapon in one scenario under a build. */
    static ScenarioOutcome scenarioOutcome(Context context, int weaponIndex, int scenarioIndex, BuildStats stats) {
        WeaponRoleModel.RoleWeapon row = WeaponRoleModel.weapons().get(weaponIndex);
        WeaponRoleModel.RoleScenario scenario = WeaponRoleModel.scenarios().get(scenarioIndex);
        RoleScenarioWeapon model = buildWeapon(row, stats);
        float hitPoints = scenarioHitPoints(context, scenario, stats);
        float matchup = MatchupCatalog.shared().multiplier(row.damageClass, scenario.archetype.trait());
        float scale = context.damageScale * stats.damageMultiplierFor(row.damageClass) * relicDamageMultiplier(stats);
        if (scenario.bruiserCharge) {
            float hits = bruiserHits(model, scenario, hitPoints, scale, matchup, stats);
            boolean cleared = hits < BalanceConfig.ROLE_SCENARIO_TURN_CAP;
            if (cleared && stats.doorBlastFraction() > 0f) {
                // DOOR_BLAST: the charge that opens through a door starts with the centre blast on the bruiser.
                float left = hitPoints - relicBlastOn(context, scenario, stats.doorBlastFraction());
                float openerHits = left > 0f ? bruiserHits(model, scenario, left, scale, matchup, stats) : 0f;
                hits = GameMath.relicDoorOpenerTurns(hits, openerHits, BalanceConfig.RELIC_VALUATION_DOOR_FIGHT_SHARE);
            }
            return new ScenarioOutcome(hits, 0, cleared);
        }
        RoleScenarioOutcome outcome = clear(model, scenario, hitPoints, scale, matchup);
        float guardTerms = heavyFightDamageMultiplier(context, scenario.archetype, stats, outcome, hitPoints,
                scenario.groupSize, false);
        if (guardTerms != 1f) outcome = clear(model, scenario, hitPoints, scale * guardTerms, matchup);
        float turns = outcome.cleared ? outcome.turnsToClear : BalanceConfig.ROLE_SCENARIO_TURN_CAP;
        if (outcome.cleared) {
            turns = relicFightTurns(context, model, scenario, hitPoints, scale * guardTerms, matchup, stats, turns);
        }
        return new ScenarioOutcome(turns, outcome.ammoSpent, outcome.cleared);
    }

    private static float bruiserHits(RoleScenarioWeapon model, WeaponRoleModel.RoleScenario scenario, float hitPoints,
                                     float scale, float matchup, BuildStats stats) {
        return GameMath.roleScenarioBruiserWeightedHitsTaken(model, scenario.distanceMax, hitPoints, scale,
                matchup, BalanceConfig.SHOTGUN_STAGGER_MIN_TURNS_BETWEEN, BalanceConfig.ROLE_SCENARIO_TURN_CAP,
                stats.ownExplosionDamageMultiplier());
    }

    private static RoleScenarioOutcome clear(RoleScenarioWeapon model, WeaponRoleModel.RoleScenario scenario,
                                             float hitPoints, float scale, float matchup) {
        return GameMath.roleScenarioClear(model, scenario.groupSize, BalanceConfig.ROLE_GROUP_TARGETS_IN_LANE,
                scenario.distanceMin, scenario.distanceMax, hitPoints, scale, matchup,
                BalanceConfig.ROLE_SCENARIO_TURN_CAP);
    }

    /** One weapon-in-scenario result under a build. */
    static final class ScenarioOutcome {
        final float   score;
        final int     ammoSpent;
        final boolean cleared;

        ScenarioOutcome(float score, int ammoSpent, boolean cleared) {
            this.score     = score;
            this.ammoSpent = ammoSpent;
            this.cleared   = cleared;
        }
    }

    /**
     * The damage multiplier a heavy-capable enemy's fight gets from the guard terms: the riposte window
     * ({@link GameMath#riposteDamageMultiplier}) and IRON BULWARK's reflection
     * ({@link GameMath#guardReflectDamagePerTurn}, folded in as (per-turn damage + reflected) / per-turn
     * damage). With includeBase the base riposte (VULNERABLE_DURATION at VULNERABLE_DAMAGE_PERCENT) counts
     * too (the duel); without it only the build's increment over the base does (the order-3 scenarios
     * model no riposte, so a no-build fight stays exactly the R-ROLE table's). 1 for an archetype with no
     * heavy, or a fight that never got going.
     */
    private static float heavyFightDamageMultiplier(Context context, EnemyType archetype, BuildStats stats,
                                                    RoleScenarioOutcome firstPass, float hitPoints, int members,
                                                    boolean includeBase) {
        HeavyAttack heavy = archetype.heavyWindUp();
        if (heavy == null || !firstPass.cleared || firstPass.turnsToClear <= 0f) return 1f;
        float period = BalanceConfig.BUILD_HEAVY_PERIOD_TURNS;
        float baseBonus = BalanceConfig.VULNERABLE_DAMAGE_PERCENT / 100f;
        float build = GameMath.riposteDamageMultiplier(guardedShare(stats), vulnerableTurns(stats),
                baseBonus + stats.riposteDamageBonus(), period);
        float base = includeBase ? 1f : GameMath.riposteDamageMultiplier(BalanceConfig.HEAVY_EXPECTED_AVOIDANCE,
                BalanceConfig.VULNERABLE_DURATION, baseBonus, period);
        float perTurn = hitPoints * Math.max(1, members) / firstPass.turnsToClear;
        float reflect = GameMath.guardReflectDamagePerTurn(guardedShare(stats), stats.guardReflectFraction(),
                heavyHit(context, heavy), guardHeavyMultiplier(stats), period) * Math.max(1, members);
        return build / base * (perTurn + reflect) / perTurn;
    }

    // =====================================================================================
    // METRIC BODIES (called by BuildNicheMetric)
    // =====================================================================================

    /** Net ammo the niche weapon spends clearing the niche scenario, after Breacher / SCATTERGUN refunds. */
    static float scenarioNetAmmo(Context context, int weaponIndex, int scenarioIndex, BuildStats stats) {
        WeaponRoleModel.RoleWeapon row = WeaponRoleModel.weapons().get(weaponIndex);
        WeaponRoleModel.RoleScenario scenario = WeaponRoleModel.scenarios().get(scenarioIndex);
        ScenarioOutcome outcome = scenarioOutcome(context, weaponIndex, scenarioIndex, stats);
        float refundChance = 0f;
        if (row.damageClass == DamageClass.SPREAD) {
            refundChance = stats.spreadKillShellRefundChance()
                    + (stats.pointBlankKillShellRefund() ? pointBlankShare(scenario) : 0f);
        }
        return GameMath.netAmmoAfterRefunds(outcome.ammoSpent, scenario.groupSize, refundChance);
    }

    /** Share of a scenario's band at point-blank range (SCATTERGUN's refund applies there only). */
    private static float pointBlankShare(WeaponRoleModel.RoleScenario scenario) {
        return GameMath.bandShareWithin(scenario.distanceMin, scenario.distanceMax, BalanceConfig.BUILD_POINT_BLANK_MAX_TILES);
    }

    /** Turns to clear the niche scenario when it opens on a barrel (ring blast on every member). */
    static float barrelOpenerTurns(Context context, int weaponIndex, int scenarioIndex, BuildStats stats) {
        WeaponRoleModel.RoleWeapon row = WeaponRoleModel.weapons().get(weaponIndex);
        WeaponRoleModel.RoleScenario scenario = WeaponRoleModel.scenarios().get(scenarioIndex);
        float hitPoints = scenarioHitPoints(context, scenario, stats);
        float blast = BalanceSchema.barrelEnemyDamage(BalanceConfig.HAZARD_BARREL_RING_FRACTION, context.depth)
                * stats.barrelDamageMultiplier()
                * MatchupCatalog.shared().multiplier(DamageClass.EXPLOSIVE, scenario.archetype.trait());
        float followUp = 0f;
        if (blast < hitPoints) {
            RoleScenarioWeapon model = buildWeapon(row, stats);
            float matchup = MatchupCatalog.shared().multiplier(row.damageClass, scenario.archetype.trait());
            RoleScenarioOutcome outcome = clear(model, scenario, hitPoints - blast,
                    context.damageScale * stats.damageMultiplierFor(row.damageClass) * relicDamageMultiplier(stats),
                    matchup);
            followUp = outcome.cleared ? outcome.turnsToClear : BalanceConfig.ROLE_SCENARIO_TURN_CAP;
        }
        return GameMath.barrelOpenerTurns(hitPoints, blast, followUp);
    }

    /** One duel's outcome: turns it lasts, duels survived, and whole turns to die at its damage rate. */
    public static final class DuelOutcome {
        public final float turns;
        public final float damage;
        public final float survivals;
        public final int   turnsToDie;

        DuelOutcome(float turns, float damage, float survivals, int turnsToDie) {
            this.turns      = turns;
            this.damage     = damage;
            this.survivals  = survivals;
            this.turnsToDie = turnsToDie;
        }
    }

    /**
     * THE BULWARK DUEL: the niche weapon against one of the niche scenario's heavy-capable archetype. The
     * player guards every heavy it faces (a turn each); each guarded heavy ripostes (stun + VULNERABLE) and,
     * under IRON BULWARK, reflects; the enemy's other turns are ordinary hits. Base riposte included in both
     * the before and the after, so the gain is the build's alone.
     */
    static DuelOutcome duel(Context context, Niche niche, BuildStats stats) {
        WeaponRoleModel.RoleWeapon row = WeaponRoleModel.weapons().get(niche.weaponIndex());
        WeaponRoleModel.RoleScenario scenario = WeaponRoleModel.scenarios().get(niche.scenarioIndex());
        EnemyType archetype = scenario.archetype;
        HeavyAttack heavy = archetype.heavyWindUp();
        RoleScenarioWeapon model = buildWeapon(row, stats);
        float hitPoints = scenarioHitPoints(context, scenario, stats);
        float matchup = MatchupCatalog.shared().multiplier(row.damageClass, archetype.trait());
        float scale = context.damageScale * stats.damageMultiplierFor(row.damageClass) * relicDamageMultiplier(stats);
        RoleScenarioOutcome firstPass = clear(model, scenario, hitPoints, scale, matchup);
        float heavyHit = heavy == null ? 0f : heavyHit(context, heavy);
        float guardMultiplier = guardHeavyMultiplier(stats);
        float guardTerms = heavyFightDamageMultiplier(context, archetype, stats, firstPass, hitPoints, 1, true);
        float fireTurns = clear(model, scenario, hitPoints, scale * guardTerms, matchup).turnsToClear;
        float heavies = heavy == null ? 0f : fireTurns / BalanceConfig.BUILD_HEAVY_PERIOD_TURNS;
        float guarded = heavy == null ? 0f : guardedShare(stats) * heavies;
        float totalTurns = fireTurns + guarded;
        float ordinaryTurns = GameMath.duelOrdinaryTurns(totalTurns, heavies, guarded);
        float ordinaryHit = GameMath.enemyDamageAtDepth(archetype.attackDamage(), context.depth)
                / Math.max(1, archetype.attackCadenceTurns());
        float damage = GameMath.attritionDamageTaken(ordinaryTurns, ordinaryHit, heavies, heavyHit,
                GameMath.heavyTakenFactor(guardedShare(stats), guardMultiplier));
        // DAMAGE_TAKEN_MULTIPLIER on every hit; FIRST_HIT_SHIELD absorbs the opening ordinary hit.
        damage = GameMath.relicDuelDamage(damage, stats.damageTakenMultiplier(), stats.firstHitShield() ? ordinaryHit : 0f);
        float pool = buildPool(context, stats);
        float survivals = GameMath.fightsSurvived(pool, damage);
        float damagePerTurn = totalTurns > 0f ? damage / totalTurns : 0f;
        int turnsToDie = damagePerTurn > 0f ? GameMath.turnsToKill(pool, damagePerTurn) : Integer.MAX_VALUE;
        return new DuelOutcome(totalTurns, damage, survivals, turnsToDie);
    }

    /** Floors survived: sustain / incoming, both in fractions of the on-curve max HP. */
    static float floorSurvivals(Context context, Niche niche, BuildStats stats) {
        return GameMath.fightsSurvived(floorSustain(context, niche.tag, stats, 0f), floorIncoming(context, niche.tag, stats));
    }

    /** Whole hits of the reference soldier the floor sustain absorbs (the FLOOR breakpoint). */
    /**
     * The FLOOR breakpoint: whole reference-soldier hits the build survives on one floor, with an incoming
     * reduction read as the equivalent sustain (floors survived x the no-build floor's incoming) — so a
     * perk that lowers incoming (Fireproof, Shield Wall) and one that raises sustain cross the same steps.
     */
    static int floorHitsToDie(Context context, Niche niche, BuildStats stats) {
        float equivalentSustain = floorSurvivals(context, niche, stats) * floorIncoming(context, niche.tag, new BuildStats());
        return GameMath.turnsToKill(equivalentSustain * context.player.maxHealth, referenceSoldierHit(context));
    }

    /**
     * A floor's sustain under a build, in fractions of the on-curve max HP; extraHealFraction is heal the
     * build's extra supply buys (SCAVENGER's carry conversion), resolved like any heal.
     */
    static float floorSustain(Context context, BuildTag tag, BuildStats stats, float extraHealFraction) {
        ExpectedPlayer player = context.player;
        float armourRatio = player.maxHealth > 0f ? player.maxArmor / player.maxHealth : 0f;
        float meleeKills = tag == BuildTag.BLADE ? context.bodiesPerFloor * BalanceConfig.BUILD_BLADE_MELEE_KILL_SHARE : 0f;
        float meleeHeal = meleeKills * stats.meleeKillHealTotalFraction() * stats.maxHealthMultiplier();
        float secondWind = stats.secondWind()
                ? HeavyAttack.CHARGE.damageFraction() * BalanceConfig.HEAVY_DAMAGE_SCALE : 0f;
        float shieldHit = stats.firstHitShield()
                ? GameMath.relicShieldHitFraction(referenceSoldierHit(context), stats.damageTakenMultiplier(), player.maxHealth)
                : 0f;
        // Relic sustain (CP4): exactly 0 without a relic, so the sum below is the pre-relic value bit for bit.
        float relicSustain = GameMath.relicFloorSustain(context.bodiesPerFloor, BalanceConfig.RELIC_VALUATION_KILL_SHARE,
                stats.killHealFraction(), stats.killArmorFraction(), BalanceConfig.RELIC_VALUATION_DOORS_PER_FLOOR,
                stats.doorOpenHealFraction(), stats.floorArrivalHealFraction(), stats.floorArrivalArmorFraction(),
                shieldHit, stats.maxHealthMultiplier(), armourRatio, stats.maxArmorMultiplier());
        return GameMath.floorSustain(stats.maxHealthMultiplier(), armourRatio, stats.maxArmorMultiplier(),
                context.healFraction + Math.max(0f, extraHealFraction), stats.healMultiplier(),
                context.armourFraction, meleeHeal, secondWind) + relicSustain;
    }

    /**
     * A floor's incoming damage under a build (guarded heavies, PYRO fire exposure). The enemy part is scaled
     * by the relic DAMAGE_TAKEN and ENEMY_HEALTH multipliers ({@link GameMath#relicIncomingMultiplier}); the
     * fire part is not (status ticks bypass the relic damage entry, R11).
     */
    static float floorIncoming(Context context, BuildTag tag, BuildStats stats) {
        float baseFactor = GameMath.heavyTakenFactor(BalanceConfig.HEAVY_EXPECTED_AVOIDANCE, BalanceConfig.GUARD_FRONT_HEAVY);
        float buildFactor = GameMath.heavyTakenFactor(guardedShare(stats), guardHeavyMultiplier(stats));
        float fire = 0f;
        if (tag == BuildTag.PYRO && !stats.isFireTileImmune()) {
            fire = BalanceConfig.BUILD_PYRO_FIRE_TURNS_PER_FLOOR * BalanceConfig.BURN_TICK_FRACTION
                    * stats.maxHealthMultiplier();
        }
        float enemyIncoming = GameMath.floorIncoming(context.incomingFraction, BalanceConfig.BUILD_FLOOR_HEAVY_DAMAGE_SHARE,
                buildFactor, baseFactor, 0f);
        return enemyIncoming * GameMath.relicIncomingMultiplier(stats.damageTakenMultiplier(), stats.enemyHealthMultiplier())
                + Math.max(0f, fire);
    }

    /** A COMBAT floor's planned heal (medkits) under the build, in fractions of the on-curve max HP. */
    static float floorHealSupply(Context context, BuildStats stats) {
        return context.healFraction * stats.healMultiplier() * stats.maxHealthMultiplier();
    }

    /** Whole reference-soldier hits a quantity of HP (fraction of the on-curve max HP) absorbs. */
    static int hitsAbsorbed(Context context, float fractionOfMaxHealth) {
        return (int) Math.floor(fractionOfMaxHealth * context.player.maxHealth / referenceSoldierHit(context)
                + BuildNicheMetric.INTEGER_EPSILON);
    }

    /** The build's pool in reference-soldier hits (R8 hits-to-die at the depth, continuous). */
    static float poolHitsToDie(Context context, BuildStats stats) {
        return GameMath.ladderTurnsToKill(buildPool(context, stats), referenceSoldierHit(context) * stats.damageTakenMultiplier());
    }

    private static float referenceSoldierHit(Context context) {
        EnemyType soldier = BalanceSchema.ladderReferenceArchetype(EnemyRole.SOLDIER);
        return GameMath.enemyDamageAtDepth(soldier.attackDamage(), context.depth);
    }

    /** Turns to kill the reference soldier with the reference melee weapon (Combat Knife). */
    static float meleeTurns(Context context, BuildStats stats) {
        EnemyType soldier = BalanceSchema.ladderReferenceArchetype(EnemyRole.SOLDIER);
        float hitPoints = GameMath.enemyHealthAtDepth(soldier.neutralEffectiveHitPoints(), context.depth)
                * stats.enemyHealthMultiplier();
        float strength = GameMath.meleeDamageMultiplier(stats.attributeGrant(Attribute.STRENGTH), 0,
                BalanceConfig.STR_MELEE_PER_POINT);
        float lowHealth = 1f + stats.berserkLowHealthMeleeBonus() * BalanceConfig.BUILD_BLADE_LOW_HEALTH_TURN_SHARE;
        float hit = BalanceConfig.MELEE_KNIFE_DAMAGE * context.damageScale * strength
                * stats.damageMultiplierFor(DamageClass.BLADE) * lowHealth * relicDamageMultiplier(stats);
        return GameMath.ladderTurnsToKill(hitPoints, hit);
    }

    /** A floor's supply under a build, by component, in depth-1 power points. */
    public static final class SupplyValue {
        public final float ammo;
        public final float heals;
        public final float credits;
        /** What the carriers drop, Salvager's extra items included (part of ammo + heals already). */
        public final float carriers;
        /** Salvager's extra items alone. */
        public final float carrierExtras;
        /** XP_MULTIPLIER's extra levels in power points (relics CP4; 0 without a relic). */
        public final float experience;

        SupplyValue(float ammo, float heals, float credits, float carriers, float carrierExtras, float experience) {
            this.ammo          = ammo;
            this.heals         = heals;
            this.credits       = credits;
            this.carriers      = carriers;
            this.carrierExtras = carrierExtras;
            this.experience    = experience;
        }

        /** Every component once (the carriers' base drops are already in ammo and heals). */
        public float total() {
            return ammo + heals + credits + carrierExtras + experience;
        }

        /** Every component floorSustain does not already read (all but heals) — the carry's conversion base. */
        public float totalExceptHeals() {
            return ammo + credits + carrierExtras + experience;
        }
    }

    /**
     * A floor's supply under a build. Relic kinds (CP4), each exactly 0 / the identity without a relic:
     * KILL_AMMO_REFUND and FLOOR_ARRIVAL_AMMO on ammo ({@link GameMath#relicAmmoPowerPoints}; the arrival
     * refill covers the kit's gun slots — BULLETS for the generalist, each other slot at the mean of the other
     * types' reserve caps), KILL_CREDIT_BONUS on the kill credits ({@link GameMath#relicKillCredits}) and
     * XP_MULTIPLIER as the experience component ({@link GameMath#relicExperiencePowerPoints}).
     */
    static SupplyValue supply(Context context, BuildStats stats) {
        float arrivalCapPoints = GameMath.relicKitReserveCapPowerPoints(context.generalistReserveCapPowerPoints,
                context.otherReserveCapPowerPoints, BalanceConfig.BUILD_KIT_GUN_SLOTS + stats.extraLoadoutSlots());
        float ammo = context.ammoPowerPoints * stats.ammoPickupMultiplier()
                + GameMath.relicAmmoPowerPoints(context.bodiesPerFloor, BalanceConfig.RELIC_VALUATION_KILL_SHARE,
                        stats.killAmmoRefundChance(), context.ammoPowerPointsPerUnit, stats.floorArrivalAmmoFraction(),
                        arrivalCapPoints);
        float heals = context.healPowerPoints * stats.healMultiplier() * stats.maxHealthMultiplier();
        float creditsPerFloor = GameMath.relicKillCredits(context.creditsPerFloor, context.killCreditsPerFloor,
                stats.killCreditMultiplier() - 1f);
        float credits = GameMath.creditPowerPoints(creditsPerFloor, BalanceConfig.SHOP_CREDITS_PER_POWER_POINT,
                context.depth, BalanceConfig.SHOP_DEPTH_PRICE_SCALE, stats.shopPriceMultiplier());
        float extras = context.carriedPowerPoints * stats.carrierExtraDropChance();
        float experience = GameMath.relicExperiencePowerPoints(stats.experienceMultiplier() - 1f,
                BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, BalanceConfig.EXPECTED_PERK_POWER_PP_PER_PICK);
        return new SupplyValue(ammo, heals, credits, context.carriedPowerPoints + extras, extras, experience);
    }

    // =====================================================================================
    // GUARD HELPERS
    // =====================================================================================

    private static float guardedShare(BuildStats stats) {
        int extraLanes = stats.allRoundGuard() ? BalanceConfig.BUILD_GUARD_LANES - 1
                : stats.wideGuardArc() ? BalanceConfig.BUILD_WIDE_GUARD_EXTRA_LANES : 0;
        return GameMath.guardedHeavyShare(BalanceConfig.HEAVY_EXPECTED_AVOIDANCE, extraLanes, BalanceConfig.BUILD_GUARD_LANES);
    }

    private static float guardHeavyMultiplier(BuildStats stats) {
        return stats.guardHeavyMultiplierOr(BalanceConfig.GUARD_FRONT_HEAVY);
    }

    private static float vulnerableTurns(BuildStats stats) {
        return BalanceConfig.VULNERABLE_DURATION + stats.riposteVulnerableBonusTurns();
    }

    private static float heavyHit(Context context, HeavyAttack heavy) {
        return GameMath.heavyDamageAtDepth(heavy.damageFraction(), context.player.maxHealth, BalanceConfig.HEAVY_DAMAGE_SCALE);
    }

    /** The build's fight pool: max HP + max armour, each under the build's multipliers. */
    private static float buildPool(Context context, BuildStats stats) {
        return context.player.maxHealth * stats.maxHealthMultiplier() + context.player.maxArmor * stats.maxArmorMultiplier();
    }

    // =====================================================================================
    // PER-SCENARIO "BEST ANSWER" (PK2) and the scenario survivals
    // =====================================================================================

    /**
     * The player's best answer in each reference scenario under a build: the lowest score of any weapon
     * that clears it on one floor's planned ammo of its type (x the build's ammo pickup multiplier) — the
     * R-ROLE "eligible" rule. +infinity when none is eligible.
     */
    public static float[] bestAnswers(Context context, BuildStats stats) {
        return bestAnswers(context, stats, null);
    }

    /** Best answers restricted to a kit (null = every registered weapon). */
    public static float[] bestAnswers(Context context, BuildStats stats, BuildTag kitTag) {
        List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        float[] best = new float[scenarios.size()];
        for (int scenarioIndex = 0; scenarioIndex < scenarios.size(); scenarioIndex++) {
            best[scenarioIndex] = bestAnswer(context, scenarioIndex, stats, kitTag);
        }
        return best;
    }

    /**
     * True when a weapon is in a tag build's kit: the generalist (BUILD_KIT_GENERALIST, carried by every
     * build) plus every weapon whose class the tag owns. A null tag is the full arsenal.
     */
    public static boolean inKit(WeaponRoleModel.RoleWeapon weapon, BuildTag kitTag) {
        return kitTag == null || weapon.itemType == BUILD_KIT_GENERALIST || kitTag.ownsDamageClass(weapon.damageClass);
    }

    /** True when the tag owns the class of at least one registered ranged weapon (BLADE owns only melee). */
    private static boolean ownsRangedWeapon(BuildTag tag) {
        for (WeaponRoleModel.RoleWeapon weapon : WeaponRoleModel.weapons()) {
            if (tag.ownsDamageClass(weapon.damageClass)) return true;
        }
        return false;
    }

    /** The weapon every tag build's kit carries beside its own class (the order-3 generalist). */
    public static final ItemType BUILD_KIT_GENERALIST = ItemType.WEAPON_ASSAULT_RIFLE;

    static float bestAnswer(Context context, int scenarioIndex, BuildStats stats) {
        return bestAnswer(context, scenarioIndex, stats, null);
    }

    static float bestAnswer(Context context, int scenarioIndex, BuildStats stats, BuildTag kitTag) {
        List<WeaponRoleModel.RoleWeapon> weapons = WeaponRoleModel.weapons();
        float best = Float.POSITIVE_INFINITY;
        for (int weaponIndex = 0; weaponIndex < weapons.size(); weaponIndex++) {
            if (!inKit(weapons.get(weaponIndex), kitTag)) continue;
            best = Math.min(best, eligibleScore(context, weaponIndex, scenarioIndex, stats));
        }
        return best;
    }

    /**
     * Scenario survivals (PK2's defence side): how many of this scenario's fights the build's pool survives
     * back to back, the fight lasting the best answer's turns (S8: the bruiser hits taken). +infinity when
     * the best answer takes no damage (e.g. S8 with a shotgun) — a defensive perk cannot improve it.
     */
    public static float[] scenarioSurvivals(Context context, BuildStats stats) {
        List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        float[] survivals = new float[scenarios.size()];
        float pool = buildPool(context, stats);
        for (int scenarioIndex = 0; scenarioIndex < scenarios.size(); scenarioIndex++) {
            WeaponRoleModel.RoleScenario scenario = scenarios.get(scenarioIndex);
            float best = bestAnswer(context, scenarioIndex, stats);
            EnemyType archetype = scenario.archetype;
            float ordinaryHit = GameMath.enemyDamageAtDepth(archetype.attackDamage(), context.depth)
                    / Math.max(1, archetype.attackCadenceTurns());
            float damage;
            if (Float.isInfinite(best)) {
                damage = Float.POSITIVE_INFINITY;
            } else if (scenario.bruiserCharge) {
                damage = best * ordinaryHit;
            } else {
                float aliveMembers = GameMath.averageAliveMembers(scenario.groupSize);
                HeavyAttack heavy = archetype.heavyWindUp();
                float heavies = heavy == null ? 0f : aliveMembers * best / BalanceConfig.BUILD_HEAVY_PERIOD_TURNS;
                float guarded = heavy == null ? 0f : guardedShare(stats) * heavies;
                damage = GameMath.attritionDamageTaken(Math.max(0f, aliveMembers * best - heavies - guarded),
                        ordinaryHit, heavies, heavy == null ? 0f : heavyHit(context, heavy),
                        GameMath.heavyTakenFactor(guardedShare(stats), guardHeavyMultiplier(stats)));
            }
            damage *= stats.damageTakenMultiplier();
            survivals[scenarioIndex] = Float.isInfinite(damage) ? 0f : GameMath.fightsSurvived(pool, damage);
        }
        return survivals;
    }

    // =====================================================================================
    // MEASURING A NICHE
    // =====================================================================================

    /** The niche metric's value for a build at a depth. */
    public static float measure(Niche niche, BuildStats stats, int depth) {
        return niche.metric.measure(context(depth), niche, stats);
    }

    /** The niche metric's integer pair {turns to kill, turns/hits to die} for a build at a depth. */
    public static int[] integers(Niche niche, BuildStats stats, int depth) {
        return niche.metric.integers(context(depth), niche, stats);
    }

    /** The aggregate of one perk at a rank alone (rank 0 = no build). */
    public static BuildStats statsWithPerk(Perk perk, int rank) {
        BuildStats stats = new BuildStats();
        perk.applyRanks(stats, rank);
        return stats;
    }

    /** The aggregate of one keystone alone. */
    public static BuildStats statsWithKeystone(Keystone keystone) {
        BuildStats stats = new BuildStats();
        keystone.apply(stats);
        return stats;
    }

    // =====================================================================================
    // R-KEYSTONE COST — the worst other scenario or metric a build makes worse
    // =====================================================================================

    /** One probe's loss: what got worse and by how much (-gain; > 0 = worse). */
    public static final class Loss {
        public final String probe;
        public final float  loss;

        Loss(String probe, float loss) {
            this.probe = probe;
            this.loss  = loss;
        }
    }

    /**
     * The largest loss a build causes against the no-build player at a depth, over every probe OTHER than
     * the excluded niche: every weapon x scenario cell (a weapon the player carries got slower), the duel,
     * the floor (in the build tag's context and in the neutral one), the melee fight, the floor's supply,
     * and the share of max HP one full medkit restores. A null niche excludes nothing (every probe counts;
     * the floor is read in the neutral VITALITY context only) — the R-RELIC-CURSE cost probe.
     */
    public static Loss worstLoss(BuildStats stats, Niche excluded, int depth) {
        if (excluded == null) return worstLossOverEveryProbe(stats, depth);
        Context context = context(depth);
        BuildStats none = new BuildStats();
        Loss worst = new Loss("none", 0f);
        List<WeaponRoleModel.RoleWeapon> weapons = WeaponRoleModel.weapons();
        List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        boolean excludesCell = excluded.metric == BuildNicheMetric.SCENARIO_TURNS;
        for (int weaponIndex = 0; weaponIndex < weapons.size(); weaponIndex++) {
            for (int scenarioIndex = 0; scenarioIndex < scenarios.size(); scenarioIndex++) {
                if (excludesCell && weaponIndex == excluded.weaponIndex() && scenarioIndex == excluded.scenarioIndex()) continue;
                ScenarioOutcome before = scenarioOutcome(context, weaponIndex, scenarioIndex, none);
                if (!before.cleared) continue;
                ScenarioOutcome after = scenarioOutcome(context, weaponIndex, scenarioIndex, stats);
                worst = worse(worst, weapons.get(weaponIndex).displayName + " in " + scenarios.get(scenarioIndex).id,
                        before.score, after.score, false);
            }
        }
        Niche duelNiche = duelNiche();
        if (excluded.metric != BuildNicheMetric.DUEL_SURVIVALS) {
            worst = worse(worst, "duel", duel(context, duelNiche, none).survivals, duel(context, duelNiche, stats).survivals, true);
        }
        for (BuildTag floorTag : new BuildTag[]{BuildTag.VITALITY, excluded.tag}) {
            Niche floorNiche = new Niche(BuildNicheMetric.FLOOR_SURVIVALS, null, null, floorTag);
            if (excluded.metric == BuildNicheMetric.FLOOR_SURVIVALS && excluded.tag == floorTag) continue;
            worst = worse(worst, "floor (" + floorTag + ")", floorSurvivals(context, floorNiche, none),
                    floorSurvivals(context, floorNiche, stats), true);
        }
        if (excluded.metric != BuildNicheMetric.MELEE_TURNS) {
            worst = worse(worst, "melee", meleeTurns(context, none), meleeTurns(context, stats), false);
        }
        worst = worse(worst, "supply", supply(context, none).total(), supply(context, stats).total(), true);
        worst = worse(worst, "medkit restores", medkitRestoreFraction(none), medkitRestoreFraction(stats), true);
        return worst;
    }

    private static Loss worstLossOverEveryProbe(BuildStats stats, int depth) {
        Context context = context(depth);
        BuildStats none = new BuildStats();
        Loss worst = new Loss("none", 0f);
        List<WeaponRoleModel.RoleWeapon> weapons = WeaponRoleModel.weapons();
        List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        for (int weaponIndex = 0; weaponIndex < weapons.size(); weaponIndex++) {
            for (int scenarioIndex = 0; scenarioIndex < scenarios.size(); scenarioIndex++) {
                ScenarioOutcome before = scenarioOutcome(context, weaponIndex, scenarioIndex, none);
                if (!before.cleared) continue;
                ScenarioOutcome after = scenarioOutcome(context, weaponIndex, scenarioIndex, stats);
                worst = worse(worst, weapons.get(weaponIndex).displayName + " in " + scenarios.get(scenarioIndex).id,
                        before.score, after.score, false);
            }
        }
        Niche duelNiche = duelNiche();
        worst = worse(worst, "duel", duel(context, duelNiche, none).survivals, duel(context, duelNiche, stats).survivals, true);
        Niche floorNiche = new Niche(BuildNicheMetric.FLOOR_SURVIVALS, null, null, BuildTag.VITALITY);
        worst = worse(worst, "floor (" + BuildTag.VITALITY + ")", floorSurvivals(context, floorNiche, none),
                floorSurvivals(context, floorNiche, stats), true);
        worst = worse(worst, "melee", meleeTurns(context, none), meleeTurns(context, stats), false);
        worst = worse(worst, "supply", supply(context, none).total(), supply(context, stats).total(), true);
        worst = worse(worst, "medkit restores", medkitRestoreFraction(none), medkitRestoreFraction(stats), true);
        return worst;
    }

    /** The share of max HP one full medkit restores under a build (the bar the player watches refill). */
    public static float medkitRestoreFraction(BuildStats stats) {
        return BalanceConfig.MEDKIT_FULL_HEAL_FRACTION * stats.healMultiplier();
    }

    private static Loss worse(Loss current, String probe, float before, float after, boolean higherIsBetter) {
        float loss = -GameMath.buildNicheGain(before, after, higherIsBetter);
        if (Float.isNaN(loss) || Float.isInfinite(loss)) return current;
        return loss > current.loss ? new Loss(probe, loss) : current;
    }

    // =====================================================================================
    // R-CARRY — the expected tag build at BUILD_CARRY_DEPTH
    // =====================================================================================

    /** One tag build's carry: ranks, the per-axis lifts and the score. */
    public static final class Carry {
        public final BuildTag tag;
        public final Keystone keystone;
        public final int      ranks;
        public final String   ranksSummary;
        public final float    turnsToKillLift;
        public final float    turnsToDieLift;
        public final float    score;

        Carry(BuildTag tag, Keystone keystone, int ranks, String ranksSummary, float turnsToKillLift,
              float turnsToDieLift, float score) {
            this.tag             = tag;
            this.keystone        = keystone;
            this.ranks           = ranks;
            this.ranksSummary    = ranksSummary;
            this.turnsToKillLift = turnsToKillLift;
            this.turnsToDieLift  = turnsToDieLift;
            this.score           = score;
        }
    }

    private static final Map<BuildTag, Carry> CARRY_CACHE = new EnumMap<>(BuildTag.class);

    /** The expected player's build for a tag at BUILD_CARRY_DEPTH, scored against the same player without it. */
    public static synchronized Carry carry(BuildTag tag) {
        Carry cached = CARRY_CACHE.get(tag);
        if (cached != null) return cached;
        Carry built = buildCarry(tag);
        CARRY_CACHE.put(tag, built);
        return built;
    }

    private static Carry buildCarry(BuildTag tag) {
        int depth = BalanceConfig.BUILD_CARRY_DEPTH;
        Context context = context(depth);
        int picks = GameMath.expectedPerkPicks(context.player.characterLevel, BalanceConfig.KEYSTONE_CHARACTER_LEVELS);
        List<Perk> tagPerks = new ArrayList<>();
        int capacity = 0;
        for (Perk perk : Perk.values()) {
            if (perk.tag == tag) {
                tagPerks.add(perk);
                capacity += perk.maxRank;
            }
        }
        int ranks = GameMath.expectedTagRankCount(picks, BalanceConfig.BUILD_EXPECTED_FOCUS_SHARE, capacity);
        int[] rankByPerk = distributeRanks(tagPerks, ranks);
        BuildStats stats = new BuildStats();
        StringBuilder summary = new StringBuilder();
        for (int index = 0; index < tagPerks.size(); index++) {
            tagPerks.get(index).applyRanks(stats, rankByPerk[index]);
            if (summary.length() > 0) summary.append(' ');
            summary.append(tagPerks.get(index).name()).append('=').append(rankByPerk[index]);
        }
        Keystone keystone = keystoneOf(tag);
        if (keystone != null) keystone.apply(stats);
        return carryOf(context, stats, tag, keystone, ranks, summary.toString());
    }

    /**
     * THE CARRY OF ANY BUILD AGGREGATE (relics CP4 — the R-CARRY measurement extracted from {@link #carry}):
     * {@code stats} against the same player without it at {@code depth}. TTK lift = the geometric mean over
     * S1..S8 of the loadout's scenario lifts, the kit being {@code kitTag}'s (its own class + the generalist;
     * null or a tag with no ranged class = the best roster from every weapon) with
     * BUILD_KIT_GUN_SLOTS + stats.extraLoadoutSlots() guns (QUARTERMASTER / FOURTH HOLSTER add one); TTD lift =
     * the floor-survivals lift in kitTag's floor context, with the build's extra supply spent on heals at the
     * Fabricator ({@link #supplyHealEquivalent}). carry = TTK x TTD. Not cached (callers cache:
     * {@link #carry} per tag, RelicValuationModel per relic and depth).
     */
    public static Carry carryOf(BuildStats stats, BuildTag kitTag, int depth) {
        return carryOf(context(depth), stats, kitTag, null, 0, "");
    }

    private static Carry carryOf(Context context, BuildStats stats, BuildTag kitTag, Keystone keystone, int ranks,
                                 String ranksSummary) {
        BuildStats none = new BuildStats();
        float[] before = loadoutAnswers(context, none, kitTag, BalanceConfig.BUILD_KIT_GUN_SLOTS);
        float[] after = loadoutAnswers(context, stats, kitTag, BalanceConfig.BUILD_KIT_GUN_SLOTS + stats.extraLoadoutSlots());
        float[] lifts = new float[before.length];
        List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        for (int index = 0; index < before.length; index++) {
            lifts[index] = GameMath.scenarioLift(before[index], after[index], scenarios.get(index).bruiserCharge,
                    BalanceConfig.BUILD_CARRY_MIN_TURNS);
        }
        float killLift = GameMath.geometricMeanLift(lifts);
        float extraHeal = supplyHealEquivalent(context, none, stats, kitTag);
        float survivalsBefore = GameMath.fightsSurvived(floorSustain(context, kitTag, none, 0f), floorIncoming(context, kitTag, none));
        float survivalsAfter = GameMath.fightsSurvived(floorSustain(context, kitTag, stats, extraHeal), floorIncoming(context, kitTag, stats));
        float dieLift = GameMath.survivalLift(survivalsBefore, survivalsAfter);
        return new Carry(kitTag, keystone, ranks, ranksSummary, killLift, dieLift,
                GameMath.buildCarryScore(killLift, dieLift));
    }

    /**
     * The tag build's LOADOUT answers: the player carries {@code slots} guns (the Loadout's usable gun slots,
     * plus QUARTERMASTER's), chosen as the best roster from the tag's pool — a weapon tag's own class plus
     * the generalist; a tag that owns no class picks from every registered weapon. "Best" is the lowest
     * geometric mean over the roster of each scenario's best eligible score (floored at one turn; S8 hits + 1),
     * and the loadout is re-picked under the build (a build can change which pair is best).
     */
    static float[] loadoutAnswers(Context context, BuildStats stats, BuildTag tag, int slots) {
        List<WeaponRoleModel.RoleWeapon> weapons = WeaponRoleModel.weapons();
        List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        float[][] scores = new float[weapons.size()][];
        List<Integer> pool = new ArrayList<>();
        for (int weaponIndex = 0; weaponIndex < weapons.size(); weaponIndex++) {
            WeaponRoleModel.RoleWeapon weapon = weapons.get(weaponIndex);
            boolean inPool = tag == null || !ownsRangedWeapon(tag) || inKit(weapon, tag);
            if (!inPool) continue;
            pool.add(weaponIndex);
            scores[weaponIndex] = new float[scenarios.size()];
            for (int scenarioIndex = 0; scenarioIndex < scenarios.size(); scenarioIndex++) {
                scores[weaponIndex][scenarioIndex] = eligibleScore(context, weaponIndex, scenarioIndex, stats);
            }
        }
        int size = Math.max(1, Math.min(slots, pool.size()));
        int[] chosen = new int[size];
        float[] bestAnswers = null;
        float bestRoster = Float.POSITIVE_INFINITY;
        int[] combination = new int[size];
        for (int index = 0; index < size; index++) combination[index] = index;
        float[] answers = new float[scenarios.size()];
        float[] floored = new float[scenarios.size()];
        while (true) {
            for (int scenarioIndex = 0; scenarioIndex < scenarios.size(); scenarioIndex++) {
                float best = Float.POSITIVE_INFINITY;
                for (int slot = 0; slot < size; slot++) {
                    best = Math.min(best, scores[pool.get(combination[slot])][scenarioIndex]);
                }
                answers[scenarioIndex] = best;
                floored[scenarioIndex] = GameMath.scenarioLift(BalanceConfig.ROLE_SCENARIO_TURN_CAP, best,
                        scenarios.get(scenarioIndex).bruiserCharge, BalanceConfig.BUILD_CARRY_MIN_TURNS);
            }
            // floored[] holds cap / answer — the LARGER its geometric mean, the better the loadout.
            float roster = 1f / GameMath.geometricMeanLift(floored);
            if (roster < bestRoster) {
                bestRoster = roster;
                bestAnswers = answers.clone();
                System.arraycopy(combination, 0, chosen, 0, size);
            }
            int position = size - 1;
            while (position >= 0 && combination[position] == pool.size() - size + position) position--;
            if (position < 0) break;
            combination[position]++;
            for (int next = position + 1; next < size; next++) combination[next] = combination[next - 1] + 1;
        }
        return bestAnswers;
    }

    /** One weapon's score in one scenario under a build if it is eligible (cleared, ammo-feasible), else +inf. */
    private static float eligibleScore(Context context, int weaponIndex, int scenarioIndex, BuildStats stats) {
        ScenarioOutcome outcome = scenarioOutcome(context, weaponIndex, scenarioIndex, stats);
        if (!outcome.cleared) return Float.POSITIVE_INFINITY;
        float available = context.ammoUnitsPerFloor.getOrDefault(WeaponRoleModel.weapons().get(weaponIndex).ammoType, 0f)
                * stats.ammoPickupMultiplier();
        return outcome.ammoSpent > available ? Float.POSITIVE_INFINITY : outcome.score;
    }

    /**
     * The carry's supply conversion: the extra supply power points a floor yields under the build, spent at
     * the Fabricator on heals — a full medkit costs MEDKIT_FULL_HEAL_FRACTION x PLAYER_MAX_HEALTH /
     * SHOP_HEAL_HP_PER_POWER_POINT points and restores MEDKIT_FULL_HEAL_FRACTION of max HP, so one point
     * buys SHOP_HEAL_HP_PER_POWER_POINT / PLAYER_MAX_HEALTH of max HP. Returned in fractions of max HP.
     *
     * <p>Every kit converts only the supply floorSustain does NOT already read ({@link SupplyValue#totalExceptHeals}:
     * the heals component is the floor's planned heal, which floorSustain counts through healMultiplier x
     * hpMult — converting it too would count a medkit twice). A tag build that changes no non-heal component
     * converts exactly 0, which is the pre-relic R-CARRY for every tag but SCAVENGER. SCAVENGER's row keeps
     * its pre-relic conversion of the WHOLE delta (heals included — QUARTERMASTER's max-HP cut reads there
     * twice), so the BUILDS (R-CARRY) table stays byte-identical (A11); no relic carries the SCAVENGER tag.
     */
    private static float supplyHealEquivalent(Context context, BuildStats none, BuildStats stats, BuildTag kitTag) {
        SupplyValue before = supply(context, none);
        SupplyValue after = supply(context, stats);
        float extraPoints = kitTag == BuildTag.SCAVENGER
                ? after.total() - before.total()
                : after.totalExceptHeals() - before.totalExceptHeals();
        return GameMath.powerPointsToHealFraction(extraPoints, BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT,
                BalanceConfig.PLAYER_MAX_HEALTH);
    }

    /** Round-robin ranks over the tag's perks (rank 1 for each in catalog order, then rank 2, ...). */
    private static int[] distributeRanks(List<Perk> perks, int ranks) {
        int[] rankByPerk = new int[perks.size()];
        int remaining = ranks;
        for (int level = 1; level <= BalanceConfig.PERK_MAX_RANK && remaining > 0; level++) {
            for (int index = 0; index < perks.size() && remaining > 0; index++) {
                if (perks.get(index).maxRank >= level) {
                    rankByPerk[index] = level;
                    remaining--;
                }
            }
        }
        return rankByPerk;
    }

    /** The keystone that matches a tag (one per tag in this catalog), or null. */
    public static Keystone keystoneOf(BuildTag tag) {
        for (Keystone keystone : Keystone.values()) {
            if (keystone.tag == tag) return keystone;
        }
        return null;
    }

    // =====================================================================================
    // LOOKUPS
    // =====================================================================================

    private static int weaponIndexOf(ItemType itemType) {
        List<WeaponRoleModel.RoleWeapon> weapons = WeaponRoleModel.weapons();
        for (int index = 0; index < weapons.size(); index++) {
            if (weapons.get(index).itemType == itemType) return index;
        }
        throw new IllegalStateException("No R-ROLE weapon " + itemType);
    }

    private static int scenarioIndexOf(String id) {
        List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        for (int index = 0; index < scenarios.size(); index++) {
            if (scenarios.get(index).id.equals(id)) return index;
        }
        throw new IllegalStateException("No R-ROLE scenario " + id);
    }

    /** Every tag that has no niche registered (the audit's coverage check; empty when complete). */
    public static EnumSet<BuildTag> tagsWithoutNiche() {
        EnumSet<BuildTag> missing = EnumSet.noneOf(BuildTag.class);
        for (BuildTag tag : BuildTag.values()) {
            if (!TAG_NICHES.containsKey(tag)) missing.add(tag);
        }
        return missing;
    }
}

package ge.tbegvadze.toon3d.util;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.entity.ArcCannon;
import ge.tbegvadze.toon3d.entity.AssaultRifle;
import ge.tbegvadze.toon3d.entity.Chaingun;
import ge.tbegvadze.toon3d.entity.DamageClass;
import ge.tbegvadze.toon3d.entity.DoubleBarrelShotgun;
import ge.tbegvadze.toon3d.entity.GrenadeLauncher;
import ge.tbegvadze.toon3d.entity.Incinerator;
import ge.tbegvadze.toon3d.entity.MatchupCatalog;
import ge.tbegvadze.toon3d.entity.PlasmaRifle;
import ge.tbegvadze.toon3d.entity.Railgun;
import ge.tbegvadze.toon3d.entity.Shotgun;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.PlannedPickup;
import ge.tbegvadze.toon3d.level.SupplyCategory;
import ge.tbegvadze.toon3d.level.SupplyPlan;
import ge.tbegvadze.toon3d.level.SupplyPlanner;
import ge.tbegvadze.toon3d.level.SupplyRequest;
import ge.tbegvadze.toon3d.route.NodeSupplySpec;
import ge.tbegvadze.toon3d.route.NodeSupplySpecs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * THE R-ROLE MODEL (balance-overhaul order 3) — the data the R-ROLE rules and BalanceReport's
 * MATCHUP / SCENARIO tables read: the ranged-weapon registry (each row's {@link DamageClass}, ammo, clip,
 * reload, range and accuracy come from the WEAPON ITSELF — a fresh instance — so the class is declared
 * in exactly one place), the eight reference scenarios, and the evaluated table (turns to clear per
 * weapon per scenario at a depth, plus each ammo type's planned supply on an average COMBAT floor).
 *
 * <p>Replaces the R-WEAPON power-score registry. Pure, headless, deterministic; evaluated tables are
 * cached per depth because the supply average plans {@code ROLE_SUPPLY_SEED_COUNT} floors.
 *
 * <p>MODEL ASSUMPTIONS (stated once, here and in docs/game-balance-authority.txt R-ROLE):
 * <ul>
 *   <li>Expected-value hits: base accuracy multiplies every hit; no crit, no ability, no level-up card.</li>
 *   <li>Every weapon is the on-curve player's: its hit scales by the expected player's ladder factor
 *       (referenceHitDamage / the depth-1 reference hit); enemies use the NEUTRAL eHP at the depth.</li>
 *   <li>A group is clustered inside the band: a cone or a splash reaches all of it, a pierce reaches
 *       ROLE_GROUP_TARGETS_IN_LANE of it, the Arc's chain reaches up to its jump count.</li>
 *   <li>The Railgun always fires at FULL charge: one charge turn, then the shot.</li>
 *   <li>Melee classes (BLADE / BLUNT) are not modelled: no ammo, adjacent only, the fallback — they are
 *       exempt from R-ROLE-1 (recorded decision).</li>
 * </ul>
 */
public final class WeaponRoleModel {

    private WeaponRoleModel() {}

    // =====================================================================================
    // WEAPONS
    // =====================================================================================

    /** One registered ranged weapon: identity from the weapon instance plus its scenario model. */
    public static final class RoleWeapon {
        public final String             displayName;
        public final ItemType           itemType;
        public final DamageClass        damageClass;
        public final AmmoType           ammoType;
        public final RoleScenarioWeapon model;

        RoleWeapon(String displayName, ItemType itemType, DamageClass damageClass, AmmoType ammoType,
                   RoleScenarioWeapon model) {
            this.displayName = displayName;
            this.itemType    = itemType;
            this.damageClass = damageClass;
            this.ammoType    = ammoType;
            this.model       = model;
        }
    }

    private static final List<RoleWeapon> WEAPONS = buildWeapons();

    /** The registered ranged weapons, in table order. */
    public static List<RoleWeapon> weapons() {
        return WEAPONS;
    }

    private static List<RoleWeapon> buildWeapons() {
        List<RoleWeapon> registry = new ArrayList<>();
        registry.add(single("Assault Rifle", new AssaultRifle(), 1, 1));
        // Chaingun: one action = a CHAINGUN_BURST_SIZE-round burst (three per-bullet hits, three bullets).
        registry.add(single("Chaingun", new Chaingun(), WeaponConstants.CHAINGUN_BURST_SIZE,
                WeaponConstants.CHAINGUN_BURST_SIZE));
        registry.add(spread("Shotgun", new Shotgun(), 1));
        // Double-Barrel: both barrels in ONE action (built-in BURST_FIRE) — two hits, two shells.
        registry.add(spread("Double-Barrel Shotgun", new DoubleBarrelShotgun(), BalanceConfig.DBL_SHOTGUN_CLIP_SIZE));
        registry.add(withPattern("Plasma Rifle", new PlasmaRifle(), RoleScenarioPattern.PIERCE));
        registry.add(arc(new ArcCannon()));
        registry.add(railgun(new Railgun()));
        registry.add(incinerator(new Incinerator()));
        registry.add(grenadeLauncher(new GrenadeLauncher()));
        return Collections.unmodifiableList(registry);
    }

    /** Per-tile hit of a falloff weapon, read back through the weapon's own damageAtDistance (ladder 1). */
    private static float[] hitsByTile(Weapon weapon) {
        float[] hits = new float[weapon.getBaseRange()];
        float ladder = Math.max(1e-6f, weapon.getLadderDamageMultiplier());
        for (int distance = 1; distance <= hits.length; distance++) {
            hits[distance - 1] = weapon.damageAtDistance(distance) / ladder;
        }
        return hits;
    }

    private static RoleWeapon row(String name, Weapon weapon, RoleScenarioWeapon model) {
        return new RoleWeapon(name, weapon.getItemType(), weapon.damageClass(), weapon.getAmmoType(), model);
    }

    private static RoleWeapon single(String name, Weapon weapon, int hitsPerAction, int ammoPerAction) {
        return withExtras(name, weapon, hitsPerAction, ammoPerAction, RoleScenarioPattern.SINGLE, 0, 0);
    }

    private static RoleWeapon spread(String name, Weapon weapon, int hitsPerAction) {
        return withExtras(name, weapon, hitsPerAction, hitsPerAction, RoleScenarioPattern.SINGLE,
                BalanceConfig.SHOTGUN_KNOCKBACK_MAX_TILES, BalanceConfig.SHOTGUN_STAGGER_MAX_TILES);
    }

    private static RoleWeapon withPattern(String name, Weapon weapon, RoleScenarioPattern pattern) {
        return withExtras(name, weapon, 1, 1, pattern, 0, 0);
    }

    private static RoleWeapon withExtras(String name, Weapon weapon, int hitsPerAction, int ammoPerAction,
                                         RoleScenarioPattern pattern, int knockbackMaxTiles, int staggerMaxTiles) {
        return row(name, weapon, new RoleScenarioWeapon(name,
                weapon.getBaseClipSize() / ammoPerAction, weapon.getBaseReloadTicks(), ammoPerAction,
                hitsPerAction, weapon.getBaseAccuracy(), hitsByTile(weapon), pattern,
                0f, 0, 0f, 0, 0f, 0f, 0, 0, knockbackMaxTiles, staggerMaxTiles, false));
    }

    private static RoleWeapon arc(Weapon weapon) {
        return row("Arc Cannon", weapon, new RoleScenarioWeapon("Arc Cannon",
                weapon.getBaseClipSize(), weapon.getBaseReloadTicks(), 1, 1, weapon.getBaseAccuracy(),
                hitsByTile(weapon), RoleScenarioPattern.CHAIN, 0f,
                WeaponConstants.ARC_CANNON_CHAIN_JUMPS, WeaponConstants.ARC_CANNON_CHAIN_DAMAGE_MULTIPLIER,
                0, 0f, 0f, 0, 0, 0, 0, false));
    }

    private static RoleWeapon railgun(Weapon weapon) {
        float[] hits = new float[weapon.getBaseRange()];
        float fullCharge = WeaponConstants.RAILGUN_DAMAGE_BY_CHARGE[WeaponConstants.RAILGUN_MAX_CHARGE];
        for (int distance = 1; distance <= hits.length; distance++) {
            hits[distance - 1] = fullCharge * GameMath.damageDropMultiplier(WeaponConstants.RAILGUN_DROP_COEFF,
                    distance, WeaponConstants.RAILGUN_DAMAGE_MIN_MULTIPLIER);
        }
        // Full charge only: RAILGUN_MAX_CHARGE - 1 charge turns, then the auto-fired full-charge shot.
        return row("Railgun", weapon, new RoleScenarioWeapon("Railgun",
                weapon.getBaseClipSize(), weapon.getBaseReloadTicks(), 1, 1, weapon.getBaseAccuracy(), hits,
                RoleScenarioPattern.PIERCE, 0f, 0, 0f, WeaponConstants.RAILGUN_MAX_CHARGE - 1,
                0f, 0f, 0, 0, 0, 0, false));
    }

    private static RoleWeapon incinerator(Weapon weapon) {
        // Impact on every cone tile short of the edge, FLAME_FALLOFF at the far edge (Incinerator.marchShot).
        float[] hits = new float[weapon.getBaseRange()];
        for (int distance = 1; distance <= hits.length; distance++) {
            hits[distance - 1] = distance >= hits.length
                    ? WeaponConstants.FLAME_FALLOFF : WeaponConstants.FLAME_IMPACT_DAMAGE;
        }
        int ammoPerAction = WeaponConstants.FUEL_PER_SHOT;
        return row("Incinerator", weapon, new RoleScenarioWeapon("Incinerator",
                weapon.getBaseClipSize() / ammoPerAction, weapon.getBaseReloadTicks(), ammoPerAction, 1,
                weapon.getBaseAccuracy(), hits, RoleScenarioPattern.CONE, 0f, 0, 0f, 0,
                WeaponConstants.FLAME_BURN_FRACTION, WeaponConstants.FLAME_IMPACT_DAMAGE,
                WeaponConstants.FLAME_BURN_TURNS, WeaponConstants.FLAME_BURN_MAX_STACKS, 0, 0, false));
    }

    private static RoleWeapon grenadeLauncher(Weapon weapon) {
        // Contact fuse at any range: the centre of the plus takes the splash, every neighbour the falloff.
        float[] hits = new float[weapon.getBaseRange()];
        java.util.Arrays.fill(hits, WeaponConstants.GRENADE_SPLASH_DAMAGE);
        return row("Grenade Launcher", weapon, new RoleScenarioWeapon("Grenade Launcher",
                weapon.getBaseClipSize(), weapon.getBaseReloadTicks(), 1, 1, weapon.getBaseAccuracy(), hits,
                RoleScenarioPattern.SPLASH, WeaponConstants.GRENADE_FALLOFF_DAMAGE, 0, 0f, 0,
                0f, 0f, 0, 0, 0, 0, true));
    }

    // =====================================================================================
    // SCENARIOS
    // =====================================================================================

    /** One reference scenario: a representative archetype (its trait is the scenario's) x group x band. */
    public static final class RoleScenario {
        public final String    id;
        public final EnemyType archetype;
        public final int       groupSize;
        public final int       distanceMin;
        public final int       distanceMax;
        /** True for S8: scored in bruiser hits taken (GameMath.roleScenarioBruiserHitsTaken), not turns. */
        public final boolean   bruiserCharge;

        RoleScenario(String id, EnemyType archetype, int groupSize, int distanceMin, int distanceMax,
                     boolean bruiserCharge) {
            this.id            = id;
            this.archetype     = archetype;
            this.groupSize     = groupSize;
            this.distanceMin   = distanceMin;
            this.distanceMax   = distanceMax;
            this.bruiserCharge = bruiserCharge;
        }

        /** Short label for tables, e.g. "S2 FLESH x3 1-3". */
        public String label() {
            if (bruiserCharge) return id + " " + archetype.trait() + " charge " + distanceMax;
            return id + " " + archetype.trait() + " x" + groupSize + " " + distanceMin + "-" + distanceMax;
        }
    }

    private static final List<RoleScenario> SCENARIOS = buildScenarios();

    /** The eight reference scenarios, S1..S8. */
    public static List<RoleScenario> scenarios() {
        return SCENARIOS;
    }

    private static List<RoleScenario> buildScenarios() {
        // Representatives (the only archetype of that trait in that role, or the role's canonical one):
        //   S1 Plague Hulk (FLESH soldier)      S2 Gore Biter (FLESH chaff — the "on-curve chaff" of A6)
        //   S3 Acid Drone (the SHIELDED soldier) S4 Cinderforge Colossus (the PLATED tank — the elite-buster niche)
        //   S5 Ghoul (BURNABLE chaff)            S6 Shell Brute (CHITIN bruiser)
        //   S7 Void Shroud (INFERNAL soldier)    S8 Shell Brute (THE charging bruiser)
        List<RoleScenario> registry = new ArrayList<>();
        registry.add(new RoleScenario("S1", EnemyType.PLAGUE_HULK, BalanceConfig.ROLE_S1_GROUP,
                BalanceConfig.ROLE_S1_MIN_TILES, BalanceConfig.ROLE_S1_MAX_TILES, false));
        registry.add(new RoleScenario("S2", EnemyType.GORE_BITER, BalanceConfig.ROLE_S2_GROUP,
                BalanceConfig.ROLE_S2_MIN_TILES, BalanceConfig.ROLE_S2_MAX_TILES, false));
        registry.add(new RoleScenario("S3", EnemyType.ACID_DRONE, BalanceConfig.ROLE_S3_GROUP,
                BalanceConfig.ROLE_S3_MIN_TILES, BalanceConfig.ROLE_S3_MAX_TILES, false));
        registry.add(new RoleScenario("S4", EnemyType.CINDERFORGE_COLOSSUS, BalanceConfig.ROLE_S4_GROUP,
                BalanceConfig.ROLE_S4_MIN_TILES, BalanceConfig.ROLE_S4_MAX_TILES, false));
        registry.add(new RoleScenario("S5", EnemyType.GHOUL, BalanceConfig.ROLE_S5_GROUP,
                BalanceConfig.ROLE_S5_MIN_TILES, BalanceConfig.ROLE_S5_MAX_TILES, false));
        registry.add(new RoleScenario("S6", EnemyType.SHELL_BRUTE, BalanceConfig.ROLE_S6_GROUP,
                BalanceConfig.ROLE_S6_MIN_TILES, BalanceConfig.ROLE_S6_MAX_TILES, false));
        registry.add(new RoleScenario("S7", EnemyType.VOID_SHROUD, BalanceConfig.ROLE_S7_GROUP,
                BalanceConfig.ROLE_S7_MIN_TILES, BalanceConfig.ROLE_S7_MAX_TILES, false));
        registry.add(new RoleScenario("S8", EnemyType.SHELL_BRUTE, 1,
                BalanceConfig.ROLE_S8_START_TILES, BalanceConfig.ROLE_S8_START_TILES, true));
        return Collections.unmodifiableList(registry);
    }

    // =====================================================================================
    // EVALUATION
    // =====================================================================================

    /** One evaluated cell: a weapon in a scenario at a depth. Lower score = better. */
    public static final class Cell {
        /** Turns to clear (S1-S7) or bruiser hits taken (S8). */
        public final float   score;
        /** Fire actions taken (S8: 0) — an Incinerator action is one spray (A6). */
        public final int     actions;
        public final int     ammoSpent;
        public final boolean cleared;
        /** Ammo spent <= one average COMBAT floor's planned supply of the weapon's ammo type (R-ROLE-5). */
        public final boolean ammoFeasible;

        Cell(float score, int actions, int ammoSpent, boolean cleared, boolean ammoFeasible) {
            this.score        = score;
            this.actions      = actions;
            this.ammoSpent    = ammoSpent;
            this.cleared      = cleared;
            this.ammoFeasible = ammoFeasible;
        }

        /** Counts toward "best": cleared and ammo-feasible. */
        public boolean eligible() {
            return cleared && ammoFeasible;
        }
    }

    /** The evaluated table at one depth. */
    public static final class Table {
        public final int depth;
        /** cells[weapon index][scenario index] */
        public final Cell[][] cells;
        /** Planned units of each ammo type on an average COMBAT floor at this depth (carried with BULLETS). */
        public final Map<AmmoType, Float> supplyPerFloor;

        Table(int depth, Cell[][] cells, Map<AmmoType, Float> supplyPerFloor) {
            this.depth          = depth;
            this.cells          = cells;
            this.supplyPerFloor = supplyPerFloor;
        }

        /** The best eligible score a class reaches in a scenario, or +infinity when none is eligible. */
        public float classBest(DamageClass damageClass, int scenarioIndex) {
            float best = Float.POSITIVE_INFINITY;
            for (int weaponIndex = 0; weaponIndex < WEAPONS.size(); weaponIndex++) {
                if (WEAPONS.get(weaponIndex).damageClass != damageClass) continue;
                Cell cell = cells[weaponIndex][scenarioIndex];
                if (cell.eligible()) best = Math.min(best, cell.score);
            }
            return best;
        }

        /** The best eligible score of any class in a scenario. */
        public float overallBest(int scenarioIndex) {
            float best = Float.POSITIVE_INFINITY;
            for (DamageClass damageClass : rangedClasses()) best = Math.min(best, classBest(damageClass, scenarioIndex));
            return best;
        }

        /** True when the class's best equals the scenario's best (ties: every tied class is best). */
        public boolean isClassBest(DamageClass damageClass, int scenarioIndex) {
            float classBest = classBest(damageClass, scenarioIndex);
            return !Float.isInfinite(classBest) && classBest <= overallBest(scenarioIndex);
        }

        /** The best non-BALLISTIC score in a scenario. */
        public float bestExcluding(DamageClass excluded, int scenarioIndex) {
            float best = Float.POSITIVE_INFINITY;
            for (DamageClass damageClass : rangedClasses()) {
                if (damageClass != excluded) best = Math.min(best, classBest(damageClass, scenarioIndex));
            }
            return best;
        }
    }

    /** The damage classes the ranged registry covers, in DamageClass order. */
    public static List<DamageClass> rangedClasses() {
        EnumSet<DamageClass> classes = EnumSet.noneOf(DamageClass.class);
        for (RoleWeapon weapon : WEAPONS) classes.add(weapon.damageClass);
        return new ArrayList<>(classes);
    }

    private static final Map<Integer, Table> TABLE_CACHE = new HashMap<>();

    /** The evaluated scenario table at a depth (cached; deterministic). */
    public static synchronized Table table(int depth) {
        Table cached = TABLE_CACHE.get(depth);
        if (cached != null) return cached;
        Table built = evaluate(depth);
        TABLE_CACHE.put(depth, built);
        return built;
    }

    private static Table evaluate(int depth) {
        ExpectedPlayer player = GameMath.expectedPlayerAtDepth(depth);
        float damageScale = player.referenceHitDamage / GameMath.ladderReferenceHitDamage();
        Map<AmmoType, Float> supply = supplyPerFloor(depth);
        Cell[][] cells = new Cell[WEAPONS.size()][SCENARIOS.size()];
        for (int weaponIndex = 0; weaponIndex < WEAPONS.size(); weaponIndex++) {
            RoleWeapon weapon = WEAPONS.get(weaponIndex);
            for (int scenarioIndex = 0; scenarioIndex < SCENARIOS.size(); scenarioIndex++) {
                RoleScenario scenario = SCENARIOS.get(scenarioIndex);
                float hitPoints = GameMath.enemyHealthAtDepth(scenario.archetype.neutralEffectiveHitPoints(), depth);
                float matchup = MatchupCatalog.shared().multiplier(weapon.damageClass, scenario.archetype.trait());
                if (scenario.bruiserCharge) {
                    int hitsTaken = GameMath.roleScenarioBruiserHitsTaken(weapon.model, scenario.distanceMax,
                            hitPoints, damageScale, matchup, BalanceConfig.SHOTGUN_STAGGER_MIN_TURNS_BETWEEN,
                            BalanceConfig.ROLE_SCENARIO_TURN_CAP);
                    boolean stopped = hitsTaken < BalanceConfig.ROLE_SCENARIO_TURN_CAP;
                    cells[weaponIndex][scenarioIndex] = new Cell(hitsTaken, 0, 0, stopped, true);
                } else {
                    RoleScenarioOutcome outcome = GameMath.roleScenarioClear(weapon.model, scenario.groupSize,
                            BalanceConfig.ROLE_GROUP_TARGETS_IN_LANE, scenario.distanceMin, scenario.distanceMax,
                            hitPoints, damageScale, matchup, BalanceConfig.ROLE_SCENARIO_TURN_CAP);
                    float available = supply.getOrDefault(weapon.ammoType, 0f);
                    cells[weaponIndex][scenarioIndex] = new Cell(outcome.turnsToClear, outcome.actions, outcome.ammoSpent,
                            outcome.cleared, outcome.ammoSpent <= available);
                }
            }
        }
        return new Table(depth, cells, supply);
    }

    /**
     * R-ROLE-5's yardstick: planned units of each ammo type on an AVERAGE COMBAT floor at the depth — the
     * CURRENT SupplyPlanner run over ROLE_SUPPLY_SEED_COUNT encounter-planned rosters, with the player
     * carrying BULLETS (the generalist) plus that type (the niche weapon in hand).
     */
    private static Map<AmmoType, Float> supplyPerFloor(int depth) {
        Map<AmmoType, Float> supply = new EnumMap<>(AmmoType.class);
        NodeSupplySpec combat = NodeSupplySpecs.combat();
        ExpectedPlayer player = GameMath.expectedPlayerAtDepth(depth);
        for (AmmoType ammoType : AmmoType.values()) {
            Set<AmmoType> carried = EnumSet.of(AmmoType.BULLETS, ammoType);
            float units = 0f;
            for (int seedIndex = 0; seedIndex < BalanceConfig.ROLE_SUPPLY_SEED_COUNT; seedIndex++) {
                long seed = GameMath.floorSeed(0x7_0E5L + seedIndex, depth);
                List<EnemyType> roster = BalanceSchema.syntheticRoster(combat, depth, seed);
                SupplyPlan plan = SupplyPlanner.plan(new SupplyRequest(depth, combat, roster, player, carried,
                        seed, false, 0));
                for (PlannedPickup pickup : plan.pickups()) {
                    if (pickup.category == SupplyCategory.AMMO && pickup.symbol == ammoType.getPickupTileChar()) {
                        units += ammoType.getAmountPerBox();
                    }
                }
            }
            supply.put(ammoType, units / BalanceConfig.ROLE_SUPPLY_SEED_COUNT);
        }
        return supply;
    }
}

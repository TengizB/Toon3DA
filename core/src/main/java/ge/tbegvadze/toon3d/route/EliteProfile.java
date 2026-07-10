package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.LevelGenConfig;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ELITE HOTZONE node's profile (route-map order-9): a containment breach — fewer enemies but MEAN
 * ones, hazards live, red alert pulsing. High risk, but the facility stored something worth guarding
 * here, so an ELITE promises a guaranteed ~1.5x reward, often behind a keycard-gated vault. This is
 * the DANGER counterweight to the CALM cache/rest nodes (order-8): routing here is a deliberate gamble
 * for a bigger payoff.
 *
 * <p>Recipe:
 * <ul>
 *   <li>Generator: the node's pre-rolled {@link RouteNode#chosenGeneratorId} (order-2) — the SHAPE is
 *       a normal combat floor; the danger comes from budget + affix + hazards, so no bespoke generator
 *       is needed. Boss depths still defer to the arena ({@link GameMath#isBossFloor(int)} is the
 *       single authority, matching every other profile).</li>
 *   <li>Config: hazard-forward — radioactive barrels weighted up, large arena room on, some flicker /
 *       unlit dread, oil/corpses up. Lock-and-key on so clearing the floor can literally unlock the
 *       payout. {@link FloorEffects#redAlert()} lights the emergency pulse the moment you arrive.</li>
 *   <li>Budget: {@link RouteMapConstants#ELITE_BUDGET_SCALE} of the raw depth budget — a mini-setpiece,
 *       still depth-scaled (order-3 invariant). The affix may raise it further.</li>
 *   <li>Reward (post-generation, deterministic from the floor seed): an owned-ammo vault cache plus a
 *       depth-scaled armour pickup, medkits, stims and a weapon-rack display, placed in the deep gated
 *       room so clearing the guardian earns the vault.</li>
 *   <li>Affix: if the node rolled a {@link NodeAffix} at map-gen, its {@link NodeAffixDefinition} (from
 *       the {@link NodeAffixRegistry}) folds its typed modifiers in — config bias, budget multiplier,
 *       and extra hazard / cover / armour guarantees.</li>
 * </ul>
 *
 * <p>Pure / headless — no LibGDX imports.
 */
public final class EliteProfile implements NodeLevelProfile {

    /** The stable id ELITE nodes reference (matches the node definition's {@code levelProfileId}). */
    public static final String PROFILE_ID = "elite_hotzone";

    private final GeneratorRegistry generators;
    private final NodeAffixRegistry affixes;

    public EliteProfile(GeneratorRegistry generators, NodeAffixRegistry affixes) {
        this.generators = generators;
        this.affixes    = affixes;
    }

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public LevelPlan resolve(RouteNode node, int depth, long seed) {
        if (GameMath.isBossFloor(depth)) {
            // An ELITE is never forced onto a boss depth, but stay consistent: the arena always wins.
            return new LevelPlan(GeneratorId.BOSS_ARENA, null, Collections.emptyList());
        }

        LevelGenConfig config = buildHotzoneConfig();
        List<GuaranteedContent> guarantees = buildRewardGuarantees(depth, seed);
        float budgetScale = RouteMapConstants.ELITE_BUDGET_SCALE;

        // Fold in the map-gen affix, if any: a bundle of typed modifiers, no bespoke per-affix code.
        NodeAffixDefinition affix = node != null && node.affix != null
                ? affixes.definition(node.affix.id()) : null;
        if (affix != null) {
            affix.applyToConfig(config);
            budgetScale *= affix.budgetScaleMultiplier();
            guarantees.addAll(affix.extraGuarantees(depth, seed));
        }

        AmmoCacheRequest vault =
                new AmmoCacheRequest(RouteMapConstants.ELITE_AMMO_BOXES, Placement.GATED_ROOM);
        FloorEffects effects = RouteMapConstants.ELITE_RED_ALERT
                ? FloorEffects.redAlert().withSting(RouteMapConstants.ELITE_STING_TEXT)
                : FloorEffects.NONE.withSting(RouteMapConstants.ELITE_STING_TEXT);

        return new LevelPlan(resolveGeneratorId(node, seed), config, guarantees,
                EnemyBudgetOverride.scaled(budgetScale), vault, effects);
    }

    /** A hazard-forward hotzone config: barrels up, an arena room, dread lighting, lock-and-key on. */
    private LevelGenConfig buildHotzoneConfig() {
        LevelGenConfig config = new LevelGenConfig();
        // Hazards live — the arena itself is dangerous.
        config.radioactiveBarrels      = true;
        config.radioactiveBarrelWeight = RouteMapConstants.ELITE_RADIOACTIVE_BARREL_WEIGHT;
        // Dread lighting: some flicker / unlit so it LOOKS like a breach (red alert does the rest).
        config.flickeringFloors = true;
        config.unlitFloors      = true;
        config.normalFloors     = true;
        // An arena-ish reward room + cover columns for a real setpiece.
        config.enableLargeRooms = true;
        config.columns          = true;
        // Gore up (a place things died guarding).
        config.corpses  = true;
        config.oilPools = true;
        // Clearing the floor can literally unlock the vault (reuses the DoorManager keycard system).
        config.enableLockAndKey = true;
        return config;
    }

    /**
     * The guaranteed ~1.5x payoff, placed in the deep gated vault ({@link Placement#GATED_ROOM}) so the
     * player must clear the guardian to claim it. Ammo is fulfilled separately via the owned-aware
     * {@link AmmoCacheRequest}; everything else is symbol-stamped here.
     */
    private List<GuaranteedContent> buildRewardGuarantees(int depth, long seed) {
        List<GuaranteedContent> guarantees = new ArrayList<>();
        // A depth-scaled armour drop: a shard shallow, a full vest deep.
        char armourSymbol = depth >= RouteMapConstants.ELITE_ARMOUR_VEST_DEPTH ? 'A' : 'a';
        guarantees.add(Guarantees.pickup(armourSymbol, RouteMapConstants.ELITE_ARMOUR, Placement.GATED_ROOM, seed));
        // Field medkits + a stim top-up for surviving the fight.
        guarantees.add(Guarantees.pickup('H', RouteMapConstants.ELITE_MEDKITS, Placement.GATED_ROOM, seed));
        guarantees.add(Guarantees.pickup('+', RouteMapConstants.ELITE_STIMS, Placement.GATED_ROOM, seed));
        // The vault set-dressing: a weapon rack promising "worth guarding", plus cover columns.
        guarantees.add(Guarantees.prop('=', RouteMapConstants.ELITE_WEAPON_RACKS, Placement.GATED_ROOM, seed));
        guarantees.add(Guarantees.prop('P', RouteMapConstants.ELITE_COVER_COLUMNS, Placement.SCATTERED, seed));
        return guarantees;
    }

    /**
     * The generator for this hotzone: the node's pre-rolled combat generator when present (order-2),
     * else a deterministic pick from the standard pool, else the room generator as a last resort — the
     * same selection contract {@link DefaultCombatProfile} uses.
     */
    private GeneratorId resolveGeneratorId(RouteNode node, long seed) {
        if (node != null && node.chosenGeneratorId != null) {
            return node.chosenGeneratorId;
        }
        List<GeneratorId> pool = generators.standardPool();
        if (pool.isEmpty()) {
            return GeneratorId.ROOMS_MST;
        }
        int index = (int) Math.floorMod(seed, pool.size());
        return pool.get(index);
    }
}

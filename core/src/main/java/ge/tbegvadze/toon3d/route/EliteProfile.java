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
 * here, so an ELITE promises a heavier supply plan and a reward weapon behind its anchor group. This is
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
 *   <li>Threat + reward: the ELITE {@link NodeSupplySpec} (balance-overhaul order 2) — 1.6x threat
 *       through the shared encounter planner (a WARBAND anchor), the vault ammo share, the guaranteed
 *       vest and a +1..+2 reward weapon one tier up, placed behind the anchor group. The profile only
 *       dresses the vault (a weapon rack, cover columns). The affix may raise the threat further.</li>
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
        // The ELITE's threat, supply and reward weapon are the node's NodeSupplySpec (balance-overhaul
        // order 2): 1.6x threat, the vault share, the guaranteed vest and the +1..+2 weapon behind the
        // anchor group are all planned by the shared FloorPopulator — no profile-side budget or pickups.
        config.supplySpec = RouteRegistries.nodeSupplySpecs().get(RouteNodeType.ELITE);
        List<GuaranteedContent> guarantees = buildSetDressing(seed);
        EnemyBudgetOverride budget = null;
        AmmoCacheRequest vault = null;

        // Fold in the map-gen affix, if any: a bundle of typed modifiers, no bespoke per-affix code.
        NodeAffixDefinition affix = node != null && node.affix != null
                ? affixes.definition(node.affix.id()) : null;
        if (affix != null) {
            affix.applyToConfig(config);
            // An affix multiplies the spec's threat (an EXTRA multiplier, never the node type's own).
            if (affix.budgetScaleMultiplier() != 1f) {
                budget = EnemyBudgetOverride.scaled(affix.budgetScaleMultiplier());
            }
            // An affix that raises the THREAT must raise the REWARD with it (R-RISK-PREMIUM, order 7):
            // SWARM / OVERCLOCKED add vault boxes so their risk premium stays in the [1.0, 1.2] band.
            if (affix.extraVaultAmmoBoxes() > 0) {
                vault = new AmmoCacheRequest(affix.extraVaultAmmoBoxes(), Placement.GATED_ROOM);
            }
            guarantees.addAll(affix.extraGuarantees(depth, seed));
        }

        FloorEffects effects = RouteMapConstants.ELITE_RED_ALERT
                ? FloorEffects.redAlert().withSting(RouteMapConstants.ELITE_STING_TEXT)
                : FloorEffects.NONE.withSting(RouteMapConstants.ELITE_STING_TEXT);

        return new LevelPlan(resolveGeneratorId(node, seed), config, guarantees, budget, vault, effects);
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
     * The vault's SET DRESSING (a weapon rack promising "worth guarding" in the gated room, plus cover
     * columns). The payoff itself — ammo vault, armour, medkits, the reward weapon — is the ELITE spec's
     * plan (balance-overhaul order 2), placed by the shared FloorPopulator behind the anchor group.
     */
    private List<GuaranteedContent> buildSetDressing(long seed) {
        List<GuaranteedContent> guarantees = new ArrayList<>();
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

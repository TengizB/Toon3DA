package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.LevelGenConfig;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The MYSTERY node's profile (route-map order-9): a META-profile over the hidden {@link MysteryOutcome}
 * table. The icon is always '?'; the payload is the surprise. On ENTRY (not at map-gen — so a scanner
 * reveals only a tone, never the result) it rolls a hidden outcome from the node's fixed
 * {@code nodeSeed}, then either DELEGATES to another registered profile (so MYSTERY automatically
 * benefits from every special level in the pool) or builds the outcome's floor directly. Every
 * resolution stamps the generic reveal sting and logs the resolved outcome to run telemetry (hidden,
 * but recorded, so the post-run route story is complete).
 *
 * <p>Expected value is tuned neutral (see {@link RouteMapConstants} MYSTERY weights): jackpots and
 * duds balance, so MYSTERY stays a genuine coin-flip. No outcome is a pure "you lose the run" trap —
 * the worst case (MALFUNCTION) wastes a hop but is always survivable. Where a referenced system
 * (traps, secret rooms, lockdown, relics) is not yet implemented, the outcome degrades to the nearest
 * available effect (e.g. TRAP GAUNTLET stamps explosive/radioactive barrels in place of trap tiles).
 *
 * <p>Pure / headless — no LibGDX imports.
 */
public final class MysteryProfile implements NodeLevelProfile {

    /** The stable id MYSTERY nodes reference (matches the node definition's {@code levelProfileId}). */
    public static final String PROFILE_ID = "mystery";

    private final NodeLevelProfileRegistry profiles;
    private final GeneratorRegistry generators;

    public MysteryProfile(NodeLevelProfileRegistry profiles, GeneratorRegistry generators) {
        this.profiles   = profiles;
        this.generators = generators;
    }

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public LevelPlan resolve(RouteNode node, int depth, long seed) {
        if (GameMath.isBossFloor(depth)) {
            return new LevelPlan(GeneratorId.BOSS_ARENA, null, Collections.emptyList());
        }

        long nodeSeed = node != null ? node.nodeSeed : seed;
        MysteryOutcome outcome = MysteryOutcome.rollFor(nodeSeed);

        // Every resolution reveals with the generic surprise sting and logs its resolved outcome.
        FloorEffects effects = FloorEffects.NONE
                .withSting(RouteMapConstants.MYSTERY_STING_TEXT)
                .withStatLog(outcome.statToken());

        if (outcome.delegateProfileId() != null) {
            // Forward to another registered profile — MYSTERY inherits its whole floor recipe, and
            // any future special level added to the pool becomes a possible MYSTERY payload for free.
            LevelPlan delegated = profiles.getOrDefault(outcome.delegateProfileId()).resolve(node, depth, seed);
            // Preserve a delegate's own red alert (e.g. the ELITE ambush) but re-stamp OUR sting + log.
            FloorEffects merged = delegated.floorEffects().redAlertActive() ? effects.withRedAlert() : effects;
            return delegated.withFloorEffects(merged);
        }

        LevelPlan built;
        switch (outcome) {
            case VAULT_JACKPOT: built = buildVault(seed);       break;
            case SECRET_WARREN: built = buildSecretWarren(seed); break;
            case TRAP_GAUNTLET: built = buildTrapGauntlet(seed);       break;
            case MALFUNCTION:   built = buildMalfunction(seed);        break;
            default:
                // Defensive: any outcome without a delegate or a build recipe falls back to a plain
                // combat floor rather than failing to produce a level.
                built = new LevelPlan(pickGeneratorId(seed), new LevelGenConfig(), Collections.emptyList());
                break;
        }
        return built.withFloorEffects(effects);
    }

    /** VAULT / JACKPOT: a bright, lightly-guarded room stuffed with loot — the dream pull. */
    private LevelPlan buildVault(long seed) {
        LevelGenConfig config = new LevelGenConfig();
        config.unlitFloors      = false;
        config.flickeringFloors = false;
        config.radioactiveBarrels = false;
        config.crates  = true;
        config.lockers = true;
        config.enableLargeRooms = true;
        List<GuaranteedContent> guarantees = new ArrayList<>();
        guarantees.add(Guarantees.brightenFloors());
        guarantees.add(Guarantees.pickup('A', RouteMapConstants.ELITE_ARMOUR, Placement.FARTHEST_ROOM, seed));
        guarantees.add(Guarantees.pickup('H', RouteMapConstants.ELITE_MEDKITS, Placement.FARTHEST_ROOM, seed));
        guarantees.add(Guarantees.prop('=', RouteMapConstants.ELITE_WEAPON_RACKS, Placement.FARTHEST_ROOM, seed));
        AmmoCacheRequest loot =
                new AmmoCacheRequest(RouteMapConstants.MYSTERY_VAULT_AMMO_BOXES, Placement.FARTHEST_ROOM);
        return new LevelPlan(pickGeneratorId(seed), config, guarantees,
                EnemyBudgetOverride.light(), loot);
    }

    /** SECRET WARREN: server rooms + lock-and-key — rewards those who search. */
    private LevelPlan buildSecretWarren(long seed) {
        LevelGenConfig config = new LevelGenConfig();
        config.enableServerRooms = true;
        config.enableLockAndKey  = true;
        config.enableLargeRooms  = true;
        List<GuaranteedContent> guarantees = new ArrayList<>();
        guarantees.add(Guarantees.pickup('H', RouteMapConstants.MYSTERY_WARREN_MEDKITS, Placement.GATED_ROOM, seed));
        AmmoCacheRequest gatedLoot =
                new AmmoCacheRequest(RouteMapConstants.MYSTERY_WARREN_AMMO_BOXES, Placement.GATED_ROOM);
        // Normal depth budget — a warren has real (if modest) resistance to search through.
        return new LevelPlan(pickGeneratorId(seed), config, guarantees, null, gatedLoot);
    }

    /** TRAP GAUNTLET: dense hazards (traps degrade to barrels), loot at the end — a patience test. */
    private LevelPlan buildTrapGauntlet(long seed) {
        LevelGenConfig config = new LevelGenConfig();
        config.radioactiveBarrels      = true;
        config.radioactiveBarrelWeight = RouteMapConstants.ELITE_RADIOACTIVE_BARREL_WEIGHT;
        config.unlitFloors = true;
        List<GuaranteedContent> guarantees = new ArrayList<>();
        // Traps are not implemented yet — degrade to a dense barrel field (explosive + radioactive).
        guarantees.add(Guarantees.prop('E', RouteMapConstants.MYSTERY_TRAP_BARRELS, Placement.SCATTERED, seed));
        guarantees.add(Guarantees.prop('g', RouteMapConstants.MYSTERY_TRAP_BARRELS, Placement.SCATTERED, seed));
        // Modest reward waiting at the exit for surviving the gauntlet.
        guarantees.add(Guarantees.pickup('H', RouteMapConstants.MYSTERY_TRAP_MEDKITS, Placement.NEAR_EXIT, seed));
        AmmoCacheRequest endLoot =
                new AmmoCacheRequest(RouteMapConstants.MYSTERY_TRAP_AMMO_BOXES, Placement.NEAR_EXIT);
        // Calm enemy budget: the HAZARDS are the threat, not a swarm.
        return new LevelPlan(pickGeneratorId(seed), config, guarantees,
                EnemyBudgetOverride.calm(), endLoot);
    }

    /** MALFUNCTION / HAZARD: a failing sector — hazards, unlit, no reward. The bad-but-survivable pull. */
    private LevelPlan buildMalfunction(long seed) {
        LevelGenConfig config = new LevelGenConfig();
        config.radioactiveBarrels = true;
        config.unlitFloors      = true;
        config.flickeringFloors = true;
        config.normalFloors     = false;
        config.medkits    = false;
        config.armourKits = false;
        List<GuaranteedContent> guarantees = new ArrayList<>();
        guarantees.add(Guarantees.prop('g', RouteMapConstants.MYSTERY_TRAP_BARRELS, Placement.SCATTERED, seed));
        // No reward — just a hazardous shortcut. Calm budget keeps it survivable (permadeath is harsh).
        return new LevelPlan(pickGeneratorId(seed), config, guarantees, EnemyBudgetOverride.calm());
    }

    /**
     * Deterministic generator pick for a build-directly outcome. MYSTERY nodes are not combat-family,
     * so they carry no pre-rolled generator; the shape is drawn from the standard pool by the floor
     * seed (matching {@link DefaultCombatProfile}'s special-node fallback), falling back to the room
     * generator if the pool is empty.
     */
    private GeneratorId pickGeneratorId(long seed) {
        List<GeneratorId> pool = generators.standardPool();
        if (pool.isEmpty()) {
            return GeneratorId.ROOMS_MST;
        }
        int index = (int) Math.floorMod(seed, pool.size());
        return pool.get(index);
    }
}

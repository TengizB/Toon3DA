package ge.tbegvadze.toon3d.route;

/**
 * Turns a committed {@link RouteNode} into a concrete {@link LevelPlan} (generator + config +
 * guarantees). One profile per {@code NodeTypeDefinition.levelProfileId()}, resolved through the
 * {@link NodeLevelProfileRegistry}. This is the seam that keeps {@code World} free of a per-node-type
 * switch: World asks the registry for the node's profile and calls {@link #resolve}.
 *
 * <p>Order-3 ships exactly one implementation — {@link DefaultCombatProfile} — registered as the
 * fallback so EVERY node type yields a playable combat floor before order-7 supplies the bespoke
 * CACHE / REST / SHOP / MYSTERY / EVENT / BOSS profiles. Adding a special profile later is one
 * {@code register(...)} line in {@link RouteRegistries#bootstrap()} — no {@code World} edits.
 *
 * <p><b>Depth invariant (roguelike_order_16):</b> a profile chooses the KIND and flavour of a floor
 * and its rewards, but must NEVER alter the {@code depth} it is handed — difficulty scaling always
 * reads the raw descent depth. Choosing a "safe" node costs loot/tempo, never the clock.
 *
 * <p>Pure / headless — no LibGDX imports.
 */
public interface NodeLevelProfile {

    /** The {@code levelProfileId} this profile answers to (matches {@code NodeTypeDefinition.levelProfileId()}). */
    String profileId();

    /**
     * Resolves the floor recipe for a node at a given descent depth.
     *
     * @param node  the committed node whose floor is being built
     * @param depth the RAW descent depth (1-based) — must be passed through unchanged to the generator
     * @param seed  the floor seed ({@code World.floorSeed(runSeed, depth)}) for any deterministic rolls
     * @return a fully-resolved {@link LevelPlan}
     */
    LevelPlan resolve(RouteNode node, int depth, long seed);
}

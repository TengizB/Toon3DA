package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.Level;

/**
 * A single piece of content a node PROMISES its floor, applied POST-generation so any generator can
 * host any node's guarantees (a supply cache can be a cavern OR a room map and still receive its
 * crates). This orthogonality between generators and node contents is the extensibility win the
 * route-map vision calls for.
 *
 * <p>Order-3 defines only the interface; the guarantee list on a {@link LevelPlan} is empty until
 * order-7 ships concrete guarantees (guaranteed medkit, heal station, shop flag, ...). Pure /
 * headless — no LibGDX; {@link Level} carries no LibGDX imports either.
 */
@FunctionalInterface
public interface GuaranteedContent {

    /**
     * Applies this guarantee to a freshly generated level, in place.
     *
     * @param level the generated floor to augment (already built by the node's generator)
     */
    void applyTo(Level level);
}

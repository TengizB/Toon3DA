package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * Which slot of the map a {@link NodeAffixDefinition} fills (floor-twists). One registry holds both:
 * the order-9 ELITE affixes (hotzone flavour on an ELITE node) and the FLOOR TWISTS (a rule change on
 * a COMBAT node). Both ride the same {@link RouteNode#affix} slot, the same affix sub-stream, the same
 * " — NAME" hint suffix and the same card corner tag — a node holds at most one of either (R1).
 *
 * <p>Per-constant data names the HOST node type and the ROLL CHANCE, so the generator holds no switch on
 * scope. Pure / headless — no LibGDX imports.
 */
public enum AffixScope {

    /** An ELITE hotzone modifier (order-9): IRRADIATED, OVERCLOCKED, SWARM, FORTIFIED, VOLATILE. */
    ELITE_AFFIX(RouteNodeType.ELITE, RouteMapConstants.AFFIX_ROLL_CHANCE_ELITE),

    /** A floor twist (floor-twists): BLACKOUT, SALVAGE, HUNTED — only ever on a COMBAT node. */
    FLOOR_TWIST(RouteNodeType.COMBAT, RouteMapConstants.TWIST_ROLL_CHANCE_COMBAT);

    private final RouteNodeType hostNodeType;
    private final float         rollChance;

    AffixScope(RouteNodeType hostNodeType, float rollChance) {
        this.hostNodeType = hostNodeType;
        this.rollChance   = rollChance;
    }

    /** The only node type a row of this scope may be rolled onto. */
    public RouteNodeType hostNodeType() {
        return hostNodeType;
    }

    /** The chance an eligible host node rolls one row of this scope. */
    public float rollChance() {
        return rollChance;
    }
}

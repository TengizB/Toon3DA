package ge.tbegvadze.toon3d.progression;

/**
 * The four thematic pools every level-up card belongs to (idea 5: build diversity).
 *
 * <p>A pool is a CATEGORY of power, not a power level — every card in every pool costs the
 * same {@link ge.tbegvadze.toon3d.util.GameBalance#LEVEL_UP_BUDGET_PP} budget, so picking
 * across pools shapes a build (the SHAPE of the player's power) without changing its total
 * amount. The card deck biases new offers toward pools the player has already invested in,
 * deepening run identity over the course of a run.</p>
 *
 * <ul>
 *   <li>{@link #OFFENSE} — conditional and flat damage (Hollow Points, Marksman, Brutal Strength, Glass Cannon).</li>
 *   <li>{@link #DEFENSE} — flat survivability via armour and toughness (Combat Armour, Toughened Hide).</li>
 *   <li>{@link #SUSTAIN} — the health pool (Vitality, Iron Constitution).</li>
 *   <li>{@link #UTILITY} — mobility and evasion via agility (Evasion Training, Reckless Charge).</li>
 * </ul>
 *
 * The accent colours used to render each pool live in the overlay renderer, not here, so the
 * progression package stays free of LibGDX rendering types.
 */
public enum UpgradeCardPool {
    OFFENSE,
    DEFENSE,
    SUSTAIN,
    UTILITY
}

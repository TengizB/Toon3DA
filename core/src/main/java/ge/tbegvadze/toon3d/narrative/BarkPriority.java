package ge.tbegvadze.toon3d.narrative;

/**
 * How hard a bark fights for the one slot on screen (Story UI order-2 queue behaviour).
 * Declared shallowest-first so {@link #ordinal()} is directly comparable: a higher ordinal wins.
 *
 * <ul>
 *   <li>{@link #FLAVOR} — idle quips and backtrack ribbing.  Dropped FIRST under any pressure:
 *       if a bark is on screen, something is queued, or the rate limit is still cooling, a flavour
 *       request is discarded rather than delayed.  Silence is allowed and good.</li>
 *   <li>{@link #REACTIVE} — kills, low health, first sight of an enemy family.  Queued when there
 *       is room; dropped when the queue is full or has gone stale.</li>
 *   <li>{@link #STORY_CRITICAL} — mandatory beats (region entry, the Organization's gate order).
 *       NEVER dropped, never expires from the queue, and cuts a lower-priority bark short so it is
 *       delivered promptly.</li>
 * </ul>
 *
 * <p>Headless: no LibGDX imports.
 */
public enum BarkPriority {
    FLAVOR,
    REACTIVE,
    STORY_CRITICAL;

    /** True when this priority outranks {@code other}. */
    public boolean isHigherThan(BarkPriority other) {
        return ordinal() > other.ordinal();
    }
}

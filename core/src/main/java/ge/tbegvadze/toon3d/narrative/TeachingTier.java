package ge.tbegvadze.toon3d.narrative;

/**
 * How much a {@link TeachingTopic} costs the player to never learn (narrative-rework order-4).
 *
 * <p>The tier decides nothing about whether the FIRST teaching line lands — every topic's first
 * telling is a {@code STORY_CRITICAL} bark, full stop, so a player is never left holding a button
 * nobody told them about.  It only decides how a RE-TEACH behaves once evidence says the first
 * telling did not take: a {@link #CRITICAL} topic's re-teach still lands mid-fight (a dropped
 * critical hint is a stuck player), while a {@link #TACTICAL} topic's re-teach waits for the lull
 * like any other non-mandatory line.
 */
public enum TeachingTier {
    /** The player cannot play the game at all without this. */
    CRITICAL,
    /** The player plays much better with this, but survives without it. */
    TACTICAL
}

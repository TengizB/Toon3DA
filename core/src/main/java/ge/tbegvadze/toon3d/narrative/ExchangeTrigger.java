package ge.tbegvadze.toon3d.narrative;

/**
 * The gameplay MOMENTS that may open an exchange (Story UI order-4).
 *
 * <p>Deliberately tiny.  A bark fires on nine different triggers because barks are cheap and
 * constant; an exchange STOPS THE GAME, so it must stay rare — a handful per region — or it becomes
 * a chore.  Every trigger here is a moment the player has just finished doing something meaningful,
 * never a moment they are mid-action.
 *
 * <p>Adding a trigger means adding a firing site in {@code World} as well, so prefer hanging a new
 * exchange off {@link #MANUAL} (order-5's hand-placed beats) over growing this enum.
 *
 * <p>Headless: no LibGDX imports.
 */
public enum ExchangeTrigger {

    /**
     * The descent just entered a new story region.  The natural home for the deliberate beats: one
     * exchange per region, one-shot, which is exactly the "handful per region" budget.
     */
    REGION_ENTERED,

    /**
     * A deep-strata milestone (every {@code STORY_BARK_DEEP_STRATA_INTERVAL} floors).  Used for the
     * quieter mid-region check-ins ORA starts once she has doubts.
     */
    DEEP_STRATA,

    /**
     * The player just read a facility terminal (order-5's LOG channel).  Most logs are a bark and
     * nothing more; a handful per region carry the beat that is worth stopping for, which is why
     * the region's heaviest PROBE and CONSEQUENTIAL rows hang here rather than on region entry —
     * the player asked for this one by walking up to it.
     */
    LOG_FOUND,

    /**
     * The Organization transmitted, one floor after the region's gate order (order-5).  Deliberately
     * NOT the gate itself: the region-entry exchange already fires there, and two exchanges without
     * gameplay between them is exactly the pacing failure order-6 Part B forbids.
     */
    ORGANIZATION_ORDER,

    /**
     * A genuinely quiet moment in play — the floor's enemies are all still asleep and the player has
     * walked a while without a fight.  The home of the low-stakes teaching exchange that shows a new
     * player, at no risk, that answering is a thing they get to do.
     */
    QUIET_MOMENT,

    /**
     * Not fired by any moment — presented by id ({@link ExchangeSystem#present(String)}).  The seam
     * for order-5's hand-placed consequential beats (the ending choice and its confirmations) and for
     * follow-up exchanges chained from an option's {@code nextExchangeId}.
     */
    MANUAL
}

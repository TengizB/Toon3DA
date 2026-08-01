package ge.tbegvadze.toon3d.narrative;

/**
 * The gameplay MOMENTS a bark can hang off (Story UI order-2).  Every bark is tied to a moment —
 * there is no free-floating lore on a timer.  The world fires a request for a trigger; the
 * {@link BarkRegistry} answers with the region-appropriate pool for it.
 *
 * <p>Each value below is wired to a real engine event (see {@code world/StoryBarkTickSubscriber} and
 * the World call sites).  A trigger with no live wiring must not be added here — an unfireable
 * moment is dead data.
 *
 * <p>Headless: no LibGDX imports.
 */
public enum BarkTrigger {

    /** The player arrived on a fresh floor and the first room is on screen. */
    FLOOR_ARRIVAL,
    /** The descent crossed into a new STORY region for the first time ever (one-shot beats). */
    REGION_ENTERED,
    /** The cold order the Organization transmits at a region gate — rare, so each line lands. */
    REGION_GATE_ORDER,
    /**
     * An enemy of a FAMILY the player has never faced woke up and came for them.
     * Subject key = {@code EnemyFamily.name()}.
     */
    ENEMY_FAMILY_FIRST_SEEN,
    /** An enemy died.  Occasional (rate-limited + rolled), never every kill. */
    KILL,
    /** The player's health fraction crossed below the low-health threshold. */
    LOW_HEALTH,
    /** A deep-strata proximity milestone — the planet's growing whispers/fragments. */
    DEEP_STRATA,
    /** The player has taken no action for a while — dry flavour quip, lowest priority. */
    IDLE,
    /** The player keeps re-walking tiles they already cleared on this floor. */
    BACKTRACK
}

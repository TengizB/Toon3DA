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
     * An enemy the player has never faced woke up and came for them.  Carries BOTH halves of the
     * bestiary voice (narrative-rework order-5): subject key = {@code EnemyFamily.name()} for the
     * first sight of a whole family, or {@code EnemyType.name()} for the first sight of one
     * archetype.  Same moment, two grains — what the thing IS, then how this particular one kills
     * you.  See {@link BestiaryCatalog}.
     */
    ENEMY_FAMILY_FIRST_SEEN,
    /**
     * The player just watched a {@code SpecialAbility} RESOLVE for the first time — not the turn it
     * was telegraphed, the turn it happened.  Subject key = {@code SpecialAbility.name()}.  These
     * are the rules that kill a player who does not know them, so every row is one-shot and
     * mandatory (narrative-rework order-5 C).
     */
    ENEMY_ABILITY_RESOLVED,
    /**
     * The player walked into a boss arena for the first time ever.  A bark, deliberately never an
     * exchange: they are about to fight, and a modal there is cruel (narrative-rework order-5 D).
     */
    BOSS_ARENA_ENTERED,
    /**
     * The first boss the player has ever put down is down.  The region's own REVEAL rides it —
     * a spine beat on a moment everybody who gets that far reaches.
     */
    BOSS_DEFEATED,
    /** An enemy died.  Occasional (rate-limited + rolled), never every kill. */
    KILL,
    /** The player's health fraction crossed below the low-health threshold. */
    LOW_HEALTH,
    /** A deep-strata proximity milestone — the planet's growing whispers/fragments. */
    DEEP_STRATA,
    /** The player has taken no action for a while — dry flavour quip, lowest priority. */
    IDLE,
    /** The player keeps re-walking tiles they already cleared on this floor. */
    BACKTRACK,
    /**
     * Control has just been handed to the player at the start of a run (order-5's COLD OPEN).  ORA
     * introduces herself here; the rows are one-shot, so she does it once and then never again.
     */
    RUN_START,
    /**
     * A control has become useful for the first time and ORA teaches it in one line — the game's
     * entire tutorial.  Subject key = {@code ControlHint.name()}.
     */
    CONTROL_HINT,
    /**
     * The player walked up to a facility terminal and read what was left on it (order-5's LOG
     * channel).  ORA's one-line "take" on the log; the full text is order-6's codex.
     */
    LOG_FOUND,
    /**
     * The player just filled a whole {@link CodexCategory} of the archive (order-6 Part A's
     * "completion perk").  Subject key = {@code CodexCategory.getCatalogKey()}.
     *
     * <p>The perk is deliberately COSMETIC: ORA notices, and the tab wears a completion mark.  It is
     * never gameplay-critical and never required, because the codex is opt-in and paying it in power
     * would turn an archive nobody has to read into a chore everybody does.
     */
    CODEX_COMPLETE
}

package ge.tbegvadze.toon3d.narrative;

import java.util.Locale;

/**
 * Every teachable thing in the game (narrative-rework order-4, "ORA the guide & the competence
 * model"). Widens the old {@code ControlHint} (six values, kept below with identical names and
 * identical string ids, so no content is lost) to the roughly twenty things a player must actually
 * understand — including the systems players fail at most: guarding, reading an enemy's intent,
 * stepping out of a ranged enemy's line, and the route map the first '&gt;' portal opens.
 *
 * <p>There is no tutorial screen in this game and there is never going to be one. Every topic is a
 * one-shot {@link BarkTrigger#CONTROL_HINT} bark keyed by {@link #getSubjectKey()} — exactly like
 * the control hints it replaces, and exactly like the first-sight-of-an-enemy-family lines are keyed
 * by {@code EnemyFamily.name()}.
 *
 * <p>The trigger is the moment the thing BECOMES USEFUL (a {@link TeachingTier#CRITICAL} topic) or
 * the moment it would have HELPED (a {@link TeachingTier#TACTICAL} one), never the moment the run
 * starts: the reload line waits for an empty magazine, the guard line waits for the first heavy
 * telegraphed hit. A hint for a thing the player cannot use yet is noise, and noise is what makes
 * people stop reading.
 *
 * <h3>The competence model</h3>
 * {@link #getReteachEvidenceThreshold()} is how many pieces of concrete evidence
 * {@link TeachingSystem} needs before it EVER repeats this topic — zero means never (the topic is
 * taught once and, right or wrong, the game never mentions it again). A topic with a threshold is
 * re-taught AT MOST ONCE, ever, in different, shorter words than the first telling: see
 * {@link TeachingCatalog} for the evidence rule and the re-teach row.
 *
 * <p>Headless: no LibGDX imports.
 */
public enum TeachingTopic {

    // ---- the original six (order-5), kept verbatim so no persisted seen-flag or telemetry id moves ----
    /** The very first thing: stepping, and the direction the whole game points in. */
    MOVE(TeachingTier.CRITICAL, 0),
    /** An enemy has woken up and is coming — the first moment the fire button matters. */
    FIRE(TeachingTier.CRITICAL, 0),
    /** The magazine just ran dry mid-floor. Re-taught once a player fires an empty weapon 4x. */
    RELOAD(TeachingTier.CRITICAL, 4),
    /** The player is hurt and carrying something that would fix it. Re-taught if they die holding one. */
    HEAL(TeachingTier.CRITICAL, 1),
    /** A second gun is in the loadout. Re-taught after 2 whole floors never switching between them. */
    SWITCH_WEAPON(TeachingTier.TACTICAL, 2),
    /** There is something stashed worth opening the bag for. */
    INVENTORY(TeachingTier.TACTICAL, 0),

    // ---- the fourteen the game never mentioned before this order ----
    /** The first corner reached: turning versus strafing. */
    TURN(TeachingTier.CRITICAL, 0),
    /** The first telegraphed wind-up. Re-taught after 3 hits taken from a shown intent. */
    READ_INTENT(TeachingTier.CRITICAL, 3),
    /** The first heavy hit taken from a telegraphed attack. Re-taught after 3 of those, unguarded. */
    GUARD(TeachingTier.TACTICAL, 3),
    /** The second ranged hit taken while sharing a lane. Re-taught after 3 such hits. */
    BREAK_LANE(TeachingTier.TACTICAL, 3),
    /** A telegraphed enemy is closing but not yet adjacent. */
    SKIP_TURN(TeachingTier.TACTICAL, 0),
    /** The first closed door. */
    DOOR(TeachingTier.CRITICAL, 0),
    /** The first barrel in the line of fire with an enemy near it. */
    BARREL(TeachingTier.TACTICAL, 0),
    /** The first usable machine. */
    MACHINE(TeachingTier.CRITICAL, 0),
    /** The first '&gt;' portal in sight. */
    PORTAL(TeachingTier.CRITICAL, 0),
    /** The first level-up card. */
    LEVEL_UP(TeachingTier.CRITICAL, 0),
    /** A better weapon than the one carried is on the floor. */
    WEAPON_SWAP(TeachingTier.TACTICAL, 0);

    private final TeachingTier tier;
    private final int          reteachEvidenceThreshold;

    TeachingTopic(TeachingTier tier, int reteachEvidenceThreshold) {
        this.tier = tier;
        this.reteachEvidenceThreshold = reteachEvidenceThreshold;
    }

    /** {@link TeachingTier#CRITICAL} topics still re-teach mid-fight; {@link TeachingTier#TACTICAL} ones wait for the lull. */
    public TeachingTier getTier() {
        return tier;
    }

    /** How many pieces of evidence {@link TeachingSystem} needs before it will ever re-teach this. */
    public int getReteachEvidenceThreshold() {
        return reteachEvidenceThreshold;
    }

    /** True when this topic has a re-teach row at all — a threshold of zero means never, by design. */
    public boolean hasReteach() {
        return reteachEvidenceThreshold > 0;
    }

    /**
     * The subject key this topic's FIRST telling is registered and requested under. Kept as an
     * explicit accessor (rather than callers reaching for {@link #name()}) so the catalog and the
     * firing sites can never drift apart.
     */
    public String getSubjectKey() {
        return name();
    }

    /** The subject key this topic's RE-TEACH row is registered and requested under. Never asked for by anything but {@link TeachingSystem} — the ordinary per-frame firing sites keep asking for {@link #getSubjectKey()}. */
    public String getRetaughtSubjectKey() {
        return name() + "_RETAUGHT";
    }

    /** The short, stable token this topic uses inside catalog and localisation ids. */
    public String getCatalogKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The topic whose {@link #getSubjectKey()} matches, or null. Used to resolve order-9's fast-dismiss evidence back to a topic. */
    public static TeachingTopic forSubjectKey(String subjectKey) {
        if (subjectKey == null) return null;
        for (TeachingTopic topic : values()) {
            if (topic.getSubjectKey().equals(subjectKey)) return topic;
        }
        return null;
    }

    /**
     * The topic whose RE-TEACH row was just delivered under {@code barkId}, or null when the id is not
     * a re-teach delivery at all (the ordinary first-teach id, or an unrelated bark). Mirrors the
     * literal {@link TeachingSystem} builds internally ({@code "bark.control.retaught." + catalogKey})
     * so order-10's {@code reTeachFiredByTopic} telemetry can resolve a delivered id without
     * {@code TeachingSystem} needing a telemetry dependency of its own.
     */
    public static TeachingTopic forRetaughtBarkId(String barkId) {
        if (barkId == null) return null;
        final String prefix = "bark.control.retaught.";
        if (!barkId.startsWith(prefix)) return null;
        String catalogKey = barkId.substring(prefix.length());
        for (TeachingTopic topic : values()) {
            if (topic.getCatalogKey().equals(catalogKey)) return topic;
        }
        return null;
    }
}

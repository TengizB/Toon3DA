package ge.tbegvadze.toon3d.narrative;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * THE COMPETENCE MODEL (narrative-rework order-4) — the headless brain that decides whether one of
 * ORA's teaching lines gets said a second time. A {@link TeachingTopic} is taught once
 * ({@link TeachingCatalog}'s first-teach row does that on its own, through the ordinary bark
 * plumbing). This class owns everything after that: piling up concrete evidence that the first
 * telling did not take, and — at most once, ever, per topic — asking for the re-teach row once the
 * rate limits clear.
 *
 * <h3>Evidence, not repetition</h3>
 * Every {@code onX(...)} method here is a report from the engine of something that just happened;
 * nothing is inferred and nothing is guessed. A topic with
 * {@link TeachingTopic#getReteachEvidenceThreshold()} of zero has no re-teach at all — silence is
 * the default, exactly as the design calls for.
 *
 * <h3>The rate limits</h3>
 * All four hold at once, every time:
 * <ul>
 *   <li>the topic's first-teach row must have actually reached the screen ({@link #onBarkDelivered});</li>
 *   <li>at least {@link StoryUiConstants#STORY_TEACHING_RETEACH_MIN_SECONDS_SINCE_TAUGHT} must have
 *       passed since that delivery — someone still reading is not someone who failed to;</li>
 *   <li>no more than {@link StoryUiConstants#STORY_TEACHING_MAX_RETEACHES_PER_FLOOR} re-teach line
 *       may reach the screen between one {@link #beginFloor()} and the next;</li>
 *   <li>a {@link TeachingTier#TACTICAL} topic never re-teaches during a combat spike
 *       ({@link #setCombatSpike}) — only a {@link TeachingTier#CRITICAL} one may land mid-fight.</li>
 * </ul>
 * The re-teach row itself is one-shot in {@link StoryProgress} exactly like every other bark, so once
 * asked for and delivered it can never be asked for again — "one-shot ever" is enforced by the bark
 * layer this class merely asks.
 *
 * <h3>EVIDENCE A — order-9's seam</h3>
 * {@link StoryRecovery#consumeRecoverableTopic()} hands back a topic whose first-teach line was
 * dismissed before it could plausibly have been read. This class drains it every {@link #update}:
 * that alone is treated as satisfied evidence (skipping straight past the threshold), because
 * somebody who did not read a line is not bored, they are about to be stuck.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class TeachingSystem {

    private static final String TAUGHT_PREFIX    = "bark.control.";
    private static final String RETAUGHT_INFIX   = "retaught.";

    private final BarkSystem    barkSystem;
    private final StoryRecovery storyRecovery;

    private final int[]     evidenceCount        = new int[TeachingTopic.values().length];
    private final boolean[] taughtTimerStarted   = new boolean[TeachingTopic.values().length];
    private final float[]   secondsSinceTaught   = new float[TeachingTopic.values().length];

    private int     reteachesDeliveredThisFloor;
    private boolean combatSpike;

    public TeachingSystem(BarkSystem barkSystem, StoryRecovery storyRecovery) {
        if (barkSystem == null)    throw new IllegalArgumentException("barkSystem must not be null");
        if (storyRecovery == null) throw new IllegalArgumentException("storyRecovery must not be null");
        this.barkSystem    = barkSystem;
        this.storyRecovery = storyRecovery;
    }

    /** THE PER-FLOOR CAP: resets the "one re-teach a floor" counter. Call alongside {@code BarkSystem.beginFloor()}. */
    public void beginFloor() {
        reteachesDeliveredThisFloor = 0;
    }

    /** A {@link TeachingTier#TACTICAL} re-teach never lands mid-fight; a {@link TeachingTier#CRITICAL} one still does. */
    public void setCombatSpike(boolean value) {
        this.combatSpike = value;
    }

    /**
     * Advances the "seconds since taught" clocks, drains order-9's fast-dismiss evidence, and asks
     * for a re-teach on any topic whose evidence and rate limits now clear. Call once per frame from
     * the PLAYING update path, alongside {@code BarkSystem.update}.
     */
    public void update(float deltaTime) {
        for (int ordinal = 0; ordinal < taughtTimerStarted.length; ordinal++) {
            if (taughtTimerStarted[ordinal]) secondsSinceTaught[ordinal] += deltaTime;
        }

        String recoveredSubject;
        while ((recoveredSubject = storyRecovery.consumeRecoverableTopic()) != null) {
            TeachingTopic topic = TeachingTopic.forSubjectKey(recoveredSubject);
            if (topic != null && topic.hasReteach()) {
                evidenceCount[topic.ordinal()] = topic.getReteachEvidenceThreshold();
            }
        }

        for (TeachingTopic topic : TeachingTopic.values()) {
            if (!topic.hasReteach()) continue;
            if (evidenceCount[topic.ordinal()] < topic.getReteachEvidenceThreshold()) continue;
            if (!isEligibleForReteach(topic)) continue;
            if (barkSystem.request(BarkTrigger.CONTROL_HINT, topic.getRetaughtSubjectKey())) {
                evidenceCount[topic.ordinal()] = 0;   // asked for — do not ask again every frame
            }
        }
    }

    /**
     * Reports a line actually DELIVERED (never merely requested), so this class can start a topic's
     * 60-second clock the moment its first telling was actually read, and count a re-teach against
     * the per-floor cap only once it truly reached the screen. Feed it every
     * {@code BarkSystem.consumeJustDeliveredBarkId()} — ids it does not recognise are ignored.
     */
    public void onBarkDelivered(String barkId) {
        if (barkId == null || !barkId.startsWith(TAUGHT_PREFIX)) return;
        String remainder = barkId.substring(TAUGHT_PREFIX.length());
        if (remainder.startsWith(RETAUGHT_INFIX)) {
            reteachesDeliveredThisFloor++;
            return;
        }
        TeachingTopic topic = topicForCatalogKey(remainder);
        if (topic == null || taughtTimerStarted[topic.ordinal()]) return;
        taughtTimerStarted[topic.ordinal()] = true;
        secondsSinceTaught[topic.ordinal()] = 0f;
    }

    // -------------------------------------------------------------------------
    // Evidence — one method per concrete, observable failure the design names.
    // -------------------------------------------------------------------------

    /** EVIDENCE: the fire button was pressed on an empty, non-reloading weapon. */
    public void onEmptyFireAttempt() {
        evidenceCount[TeachingTopic.RELOAD.ordinal()]++;
    }

    /** A reload actually started — the "in a row" count resets, since the player just did the right thing. */
    public void onWeaponReloaded() {
        evidenceCount[TeachingTopic.RELOAD.ordinal()] = 0;
    }

    /** EVIDENCE: the run just ended with a usable medkit never spent. */
    public void onPlayerDied(boolean hadUnusedMedkit) {
        if (hadUnusedMedkit) evidenceCount[TeachingTopic.HEAL.ordinal()]++;
    }

    /**
     * EVIDENCE: a hit landed from an attack that was committed and shown to the player a full turn
     * ahead (a WIND_UP charge, a locked beam, a telegraphed area strike or blast) — the class of hit
     * {@link TeachingTopic#READ_INTENT} and {@link TeachingTopic#GUARD} both exist to prevent.
     * Counts toward READ_INTENT always, and toward GUARD only when the player was not braced for it.
     */
    public void onTelegraphedHitLanded(boolean playerWasGuarding) {
        evidenceCount[TeachingTopic.READ_INTENT.ordinal()]++;
        if (!playerWasGuarding) evidenceCount[TeachingTopic.GUARD.ordinal()]++;
    }

    /** EVIDENCE: a ranged shot landed — by the game's own cardinal-line rule, it can only have landed in-lane. */
    public void onRangedHitLanded() {
        evidenceCount[TeachingTopic.BREAK_LANE.ordinal()]++;
    }

    /** EVIDENCE: a floor just ended with two ranged weapons in the loadout and neither ever swapped to. */
    public void onFloorArrived(boolean holdingTwoGunsNeverSwitched) {
        if (holdingTwoGunsNeverSwitched) evidenceCount[TeachingTopic.SWITCH_WEAPON.ordinal()]++;
    }

    /** The player actually switched weapons — the "whole floors without switching" count resets. */
    public void onWeaponSwitched() {
        evidenceCount[TeachingTopic.SWITCH_WEAPON.ordinal()] = 0;
    }

    // -------------------------------------------------------------------------

    private boolean isEligibleForReteach(TeachingTopic topic) {
        int ordinal = topic.ordinal();
        if (!taughtTimerStarted[ordinal]) return false;   // never actually taught yet
        if (secondsSinceTaught[ordinal] < StoryUiConstants.STORY_TEACHING_RETEACH_MIN_SECONDS_SINCE_TAUGHT) {
            return false;
        }
        if (reteachesDeliveredThisFloor >= StoryUiConstants.STORY_TEACHING_MAX_RETEACHES_PER_FLOOR) {
            return false;
        }
        if (combatSpike && topic.getTier() != TeachingTier.CRITICAL) return false;
        // Belt-and-braces: the bark layer's own one-shot flag already refuses a row seen before, but
        // checking here too means a topic already retaught never even queues a doomed request.
        return !barkSystem.getProgress().hasSeen("bark.control.retaught." + topic.getCatalogKey());
    }

    private static TeachingTopic topicForCatalogKey(String catalogKey) {
        for (TeachingTopic topic : TeachingTopic.values()) {
            if (topic.getCatalogKey().equals(catalogKey)) return topic;
        }
        return null;
    }
}

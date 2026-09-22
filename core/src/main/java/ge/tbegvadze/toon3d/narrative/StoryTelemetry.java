package ge.tbegvadze.toon3d.narrative;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Story-UI TUNING COUNTERS (Story UI order-6 Part E; widened by narrative-rework order-10 Part B) —
 * the numbers a designer needs to answer "which lines are people actually reading?" and prune the
 * ones they are not.
 *
 * <p><b>What this is not.</b> It collects no identifiers, no user data, no text, and it performs no
 * I/O of any kind: it is a handful of ints in memory for the lifetime of the process, readable in a
 * playtest log or an assertion.  Part E asks for tuning signal with no PII, and the smallest thing
 * that satisfies that is a counter that never leaves the device — so that is all this is. None of
 * these numbers gate the build; they exist so "if in doubt, cut" is a decision with evidence behind
 * it, surfaced in the desktop dev build only (see {@code Main.dispose()}).
 *
 * <p><b>What the numbers mean.</b> The one that matters is the split between barks CLOSED FAST and
 * barks READ: a line the player swipes away inside
 * {@link StoryUiConstants#STORY_TELEMETRY_FAST_DISMISS_SECONDS} was skipped, not read, and a line
 * whose pool keeps getting skipped is a line to delete rather than a line to make louder.  Order-6
 * Part B's rule is "if in doubt, cut", and this is how the doubt is measured.
 *
 * <p><b>Order-10's four additions</b> are each the failure mode of a specific teaching or pacing
 * rule, so a bad number points at a specific row to rewrite rather than at the layer in general:
 * {@code fastDismissByBeatId} (which specific lines get tapped away unread — the top of that map is
 * the cut list), {@code reTeachFiredByTopic} (a topic that re-teaches for most players has a bad
 * FIRST line, not a bad player), {@code beatsDeliveredPerFloor} (the actual delivery rate against
 * order-8's per-floor budget), and the four {@code comprehensionProxies} (deaths before the first
 * GUARD use, medkits still held at death, ranged hits taken in-lane, seconds spent on the NAV console
 * before a node pick).
 *
 * <p>Headless: no LibGDX imports.
 */
public final class StoryTelemetry {

    private int barksShown;
    private int barksClosedFast;
    private int barksRead;
    private int exchangesAnswered;
    private final int[] answersByKind = new int[ExchangeOptionKind.values().length];
    private int codexOpens;
    private int codexEntriesOpened;
    /** The last line closed unread (order-9 C) — a one-frame hand-off, never a history. */
    private String lastFastDismissedBarkId;

    // -------------------------------------------------------------------------
    // narrative-rework order-10 Part B — the four new counters
    // -------------------------------------------------------------------------

    /** Which specific bark ids get tapped away unread. The top of this map is the next cut list. */
    private final Map<String, Integer> fastDismissByBeatId = new HashMap<>();
    /** How often the competence model had to speak a topic twice, by {@link TeachingTopic}. */
    private final int[] reteachFiredByTopic = new int[TeachingTopic.values().length];
    private int totalBarksDeliveredAcrossFloors;
    private int floorsBegun;

    // comprehensionProxies — four gameplay facts standing in for "did they understand?"
    /** Deaths recorded before the player's first-ever GUARD use, or -1 until GUARD has been used. */
    private int deathsBeforeFirstGuardUse = -1;
    private int totalMedkitsHeldAtDeath;
    private int deathsRecorded;
    private int rangedHitsTakenInLane;
    private float totalNavConsoleSecondsBeforePick;
    private int navConsolePicks;

    /** A bark reached the screen. */
    public void recordBarkShown() {
        barksShown++;
    }

    /**
     * A bark was dismissed by the player after {@code visibleSeconds} on screen.  Anything under the
     * fast-dismiss threshold counts as skipped rather than read.
     *
     * @param barkId which line it was.  Recorded (narrative-rework order-9 C) so recovery can act on
     *               the skip rather than merely count it: until order-9 this method knew a line had
     *               been thrown away and not WHICH line, which is the difference between a tuning
     *               statistic and something a player can be handed back.
     * @return true when the line was closed too fast to have been read
     */
    public boolean recordBarkDismissed(float visibleSeconds, String barkId) {
        if (visibleSeconds >= StoryUiConstants.STORY_TELEMETRY_FAST_DISMISS_SECONDS) {
            barksRead++;
            return false;
        }
        barksClosedFast++;
        lastFastDismissedBarkId = barkId;
        if (barkId != null) {
            fastDismissByBeatId.merge(barkId, 1, Integer::sum);
        }
        return true;
    }

    /**
     * The id of the last line closed inside the fast-dismiss window, or null.  Read once, by the
     * engine, on the frame the dismissal happened — this is a hand-off, not a history, and the
     * counters above remain the only thing this class accumulates.
     */
    public String getLastFastDismissedBarkId() {
        return lastFastDismissedBarkId;
    }

    /** The player answered an exchange with an option of this kind. */
    public void recordExchangeAnswered(ExchangeOptionKind kind) {
        exchangesAnswered++;
        if (kind != null) answersByKind[kind.ordinal()]++;
    }

    /** The player opened the archive. */
    public void recordCodexOpened() {
        codexOpens++;
    }

    /** The player opened one entry's full text. */
    public void recordCodexEntryOpened() {
        codexEntriesOpened++;
    }

    // -------------------------------------------------------------------------
    // narrative-rework order-10 Part B
    // -------------------------------------------------------------------------

    /**
     * Files a floor's non-critical bark delivery count into the running average. Call once per floor
     * arrival with {@code BarkSystem.getNonCriticalDeliveredThisFloor()}, read just BEFORE
     * {@code BarkSystem.beginFloor()} resets it — that count is already the authoritative one measured
     * against {@code STORY_BARK_FLOOR_BUDGET}, so this simply accumulates it rather than re-deriving it.
     */
    public void recordFloorBarkDelivery(int nonCriticalDeliveredThisFloor) {
        totalBarksDeliveredAcrossFloors += nonCriticalDeliveredThisFloor;
        floorsBegun++;
    }

    /** A re-teach line for this topic actually reached the screen (never merely requested). */
    public void recordReteachFired(TeachingTopic topic) {
        if (topic != null) reteachFiredByTopic[topic.ordinal()]++;
    }

    /**
     * The player used GUARD for the first time this process. Recorded once — later calls are ignored,
     * since the number that matters is how many deaths it took to reach the first use.
     *
     * @param deathsSoFar the persistent death count at the moment of this first use
     */
    public void recordGuardFirstUsed(int deathsSoFar) {
        if (deathsBeforeFirstGuardUse < 0) deathsBeforeFirstGuardUse = deathsSoFar;
    }

    /** A run ended; {@code medkitCount} is how many medkits were still held at the moment of death. */
    public void recordDeathWithMedkits(int medkitCount) {
        totalMedkitsHeldAtDeath += medkitCount;
        deathsRecorded++;
    }

    /** A ranged hit landed on the player while sharing the attacker's row or column. */
    public void recordRangedHitInLane() {
        rangedHitsTakenInLane++;
    }

    /** Seconds spent on the FACILITY NAV console before the node pick that closed it. */
    public void recordNavConsoleSecondsBeforePick(float seconds) {
        totalNavConsoleSecondsBeforePick += seconds;
        navConsolePicks++;
    }

    public Map<String, Integer> getFastDismissByBeatId() { return fastDismissByBeatId; }
    public int getReteachFiredCount(TeachingTopic topic) { return topic != null ? reteachFiredByTopic[topic.ordinal()] : 0; }

    /** Average non-critical barks delivered per floor across every floor begun so far, or 0 before the first. */
    public float getAverageBarksDeliveredPerFloor() {
        return floorsBegun > 0 ? (float) totalBarksDeliveredAcrossFloors / floorsBegun : 0f;
    }

    /** -1 until GUARD has ever been used; otherwise the death count at the moment of its first use. */
    public int getDeathsBeforeFirstGuardUse() { return deathsBeforeFirstGuardUse; }

    /** Average medkits still held at the moment of death, or 0 before any death has been recorded. */
    public float getAverageMedkitsHeldAtDeath() {
        return deathsRecorded > 0 ? (float) totalMedkitsHeldAtDeath / deathsRecorded : 0f;
    }

    public int getRangedHitsTakenInLane() { return rangedHitsTakenInLane; }

    /** Average seconds spent on the NAV console before a pick, or 0 before the first pick. */
    public float getAverageNavConsoleSecondsBeforePick() {
        return navConsolePicks > 0 ? totalNavConsoleSecondsBeforePick / navConsolePicks : 0f;
    }

    public int getBarksShown()         { return barksShown; }
    public int getBarksClosedFast()    { return barksClosedFast; }
    public int getBarksRead()          { return barksRead; }
    public int getExchangesAnswered()  { return exchangesAnswered; }
    public int getCodexOpens()         { return codexOpens; }
    public int getCodexEntriesOpened() { return codexEntriesOpened; }

    public int getAnswersOfKind(ExchangeOptionKind kind) {
        return kind != null ? answersByKind[kind.ordinal()] : 0;
    }

    /**
     * Fraction of dismissed barks that were closed too fast to have been read, or 0 when none have
     * been dismissed yet.  The single number Part E exists to produce: if it climbs, there are too
     * many lines, and the fix is to delete rows rather than to slow anything down.
     */
    public float getFastDismissFraction() {
        int dismissed = barksClosedFast + barksRead;
        if (dismissed <= 0) return 0f;
        return (float) barksClosedFast / dismissed;
    }

    /** One-line summary for a playtest log.  Allocates, so never call it from a per-frame path. */
    public String describe() {
        return "barks shown=" + barksShown
                + " read=" + barksRead
                + " skipped=" + barksClosedFast
                + " | exchanges answered=" + exchangesAnswered
                + " | codex opens=" + codexOpens
                + " entries=" + codexEntriesOpened;
    }

    /**
     * A second summary line for order-10's four additions — kept separate from {@link #describe()}
     * so the order-6 line stays exactly as it was for anything already parsing it. Allocates, so never
     * call it from a per-frame path.
     */
    public String describeComprehensionProxies() {
        return "avg barks/floor=" + getAverageBarksDeliveredPerFloor()
                + " | deaths before first GUARD=" + deathsBeforeFirstGuardUse
                + " | avg medkits at death=" + getAverageMedkitsHeldAtDeath()
                + " | ranged hits in-lane=" + rangedHitsTakenInLane
                + " | avg NAV console seconds before pick=" + getAverageNavConsoleSecondsBeforePick()
                + " | fast-dismissed beat ids=" + fastDismissByBeatId;
    }

    /** Zeroes every counter — a fresh sample window. */
    public void reset() {
        barksShown         = 0;
        barksClosedFast    = 0;
        barksRead          = 0;
        exchangesAnswered  = 0;
        codexOpens         = 0;
        codexEntriesOpened = 0;
        lastFastDismissedBarkId = null;
        fastDismissByBeatId.clear();
        for (int topicIndex = 0; topicIndex < reteachFiredByTopic.length; topicIndex++) {
            reteachFiredByTopic[topicIndex] = 0;
        }
        totalBarksDeliveredAcrossFloors = 0;
        floorsBegun                     = 0;
        deathsBeforeFirstGuardUse       = -1;
        totalMedkitsHeldAtDeath         = 0;
        deathsRecorded                  = 0;
        rangedHitsTakenInLane           = 0;
        totalNavConsoleSecondsBeforePick = 0f;
        navConsolePicks                 = 0;
        for (int kindIndex = 0; kindIndex < answersByKind.length; kindIndex++) {
            answersByKind[kindIndex] = 0;
        }
    }
}

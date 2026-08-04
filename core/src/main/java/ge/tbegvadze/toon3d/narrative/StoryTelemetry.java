package ge.tbegvadze.toon3d.narrative;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Story-UI TUNING COUNTERS (Story UI order-6 Part E) — the numbers a designer needs to answer "which
 * lines are people actually reading?" and prune the ones they are not.
 *
 * <p><b>What this is not.</b> It collects no identifiers, no user data, no text, and it performs no
 * I/O of any kind: it is a handful of ints in memory for the lifetime of the process, readable in a
 * playtest log or an assertion.  Part E asks for tuning signal with no PII, and the smallest thing
 * that satisfies that is a counter that never leaves the device — so that is all this is.
 *
 * <p><b>What the numbers mean.</b> The one that matters is the split between barks CLOSED FAST and
 * barks READ: a line the player swipes away inside
 * {@link StoryUiConstants#STORY_TELEMETRY_FAST_DISMISS_SECONDS} was skipped, not read, and a line
 * whose pool keeps getting skipped is a line to delete rather than a line to make louder.  Order-6
 * Part B's rule is "if in doubt, cut", and this is how the doubt is measured.
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

    /** A bark reached the screen. */
    public void recordBarkShown() {
        barksShown++;
    }

    /**
     * A bark was dismissed by the player after {@code visibleSeconds} on screen.  Anything under the
     * fast-dismiss threshold counts as skipped rather than read.
     */
    public void recordBarkDismissed(float visibleSeconds) {
        if (visibleSeconds < StoryUiConstants.STORY_TELEMETRY_FAST_DISMISS_SECONDS) {
            barksClosedFast++;
        } else {
            barksRead++;
        }
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

    /** Zeroes every counter — a fresh sample window. */
    public void reset() {
        barksShown         = 0;
        barksClosedFast    = 0;
        barksRead          = 0;
        exchangesAnswered  = 0;
        codexOpens         = 0;
        codexEntriesOpened = 0;
        for (int kindIndex = 0; kindIndex < answersByKind.length; kindIndex++) {
            answersByKind[kindIndex] = 0;
        }
    }
}

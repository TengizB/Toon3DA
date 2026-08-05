package ge.tbegvadze.toon3d.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * OVERLAY PRECEDENCE AND INTERRUPTS (Story UI order-7 Part E) — the rules that keep the story layer
 * from colliding with everything else that can own the screen.
 *
 * <p>The story UI shares a phone screen with the inventory, the level-up card, the shop, the nav
 * console, the death screen and the boot card.  Three promises hold it together:
 *
 * <ol>
 *   <li><b>A hard-pause overlay wins.</b>  While one is open, barks are SUPPRESSED — queued, never
 *       delivered and never aged out — and the queue resumes intact when it closes.  A line the
 *       player could not have read must not be spent.</li>
 *   <li><b>Never two story panels at once.</b>  One story element is visible at a time, ever.</li>
 *   <li><b>An interruption never eats a beat.</b>  A one-shot exchange is spent when it is
 *       ANSWERED, so a prompt taken away by the OS (a call, a process kill) comes back on the next
 *       run instead of being lost for the life of the save.</li>
 * </ol>
 *
 * <p>These are properties of the headless brains, which is where they are tested; {@code World}
 * feeds them the phase.  Headless — no LibGDX context anywhere.
 */
class StoryOverlayPrecedenceTest {

    private static final float FRAME_SECONDS = 1f / 60f;

    private static BarkSystem newBarkSystem(StoryProgress progress) {
        return new BarkSystem(BarkCatalog.defaultRegistry(), StoryStrings.defaults(), progress, 99L);
    }

    private static ExchangeSystem newExchangeSystem(StoryProgress progress) {
        return new ExchangeSystem(ExchangeCatalog.defaultRegistry(), StoryStrings.defaults(),
                                  progress, 99L);
    }

    private static void advance(BarkSystem system, float seconds) {
        int frames = Math.max(1, Math.round(seconds / FRAME_SECONDS));
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            system.update(FRAME_SECONDS);
        }
    }

    // -------------------------------------------------------------------------
    // 1. A hard-pause overlay wins, and the queue survives it
    // -------------------------------------------------------------------------

    @Test
    void aSuppressedBarkLayerDeliversNothing() {
        BarkSystem barks = newBarkSystem(new StoryProgress());
        barks.setSuppressed(true);
        assertTrue(barks.request(BarkTrigger.REGION_ENTERED), "the request was refused outright");
        advance(barks, 2f);
        assertFalse(barks.hasActiveBark(), "a bark talked over a hard-pause overlay");
    }

    @Test
    void theQueueResumesIntactWhenTheOverlayCloses() {
        BarkSystem barks = newBarkSystem(new StoryProgress());
        barks.setSuppressed(true);
        barks.request(BarkTrigger.REGION_ENTERED);
        advance(barks, 5f);                  // a long time in a menu
        assertFalse(barks.hasActiveBark());
        assertTrue(barks.getQueuedCount() > 0, "the held line was dropped rather than queued");

        barks.setSuppressed(false);
        advance(barks, StoryUiConstants.STORY_FADE_IN_SECONDS + 0.05f);
        assertTrue(barks.hasActiveBark(), "the queued line never arrived after the overlay closed");
        assertNotNull(barks.getActiveBarkId());
    }

    /**
     * Time spent in a menu must not age a queued line out of existence — the staleness clock only
     * runs while the screen is actually free, or a long inventory visit silently eats the story.
     */
    @Test
    void suppressionFreezesTheQueuesAgeing() {
        BarkSystem barks = newBarkSystem(new StoryProgress());
        barks.setSuppressed(true);
        barks.request(BarkTrigger.FLOOR_ARRIVAL);
        int queuedBefore = barks.getQueuedCount();
        advance(barks, StoryUiConstants.STORY_BARK_QUEUE_STALE_SECONDS * 3f);
        assertEquals(queuedBefore, barks.getQueuedCount(),
                "a queued line went stale while the screen was not even available");
    }

    // -------------------------------------------------------------------------
    // 2. Never two story panels at once
    // -------------------------------------------------------------------------

    @Test
    void anExchangeNeverOpensOnTopOfAnother() {
        StoryProgress  progress  = new StoryProgress();
        ExchangeSystem exchanges = newExchangeSystem(progress);

        assertTrue(exchanges.request(ExchangeTrigger.QUIET_MOMENT), "no exchange to test with");
        assertTrue(exchanges.openPending());
        assertTrue(exchanges.isActive());

        // A second moment fires while the first is still on screen: it must not queue behind itself
        // or stack on top. The world is paused, so nothing is lost by refusing.
        assertFalse(exchanges.request(ExchangeTrigger.QUIET_MOMENT),
                "a second exchange was accepted while one was on screen");
        assertFalse(exchanges.openPending(), "an exchange opened over the top of another");
    }

    /**
     * An exchange asked for mid-transition is HELD, not opened: the caller decides when the world is
     * actually ready to stop, which is what keeps a modal from landing over a black loading screen.
     */
    @Test
    void anExchangeIsHeldPendingUntilTheWorldIsReady() {
        ExchangeSystem exchanges = newExchangeSystem(new StoryProgress());
        assertTrue(exchanges.request(ExchangeTrigger.QUIET_MOMENT));
        assertTrue(exchanges.hasPending(), "the exchange opened itself instead of waiting");
        assertFalse(exchanges.isActive(), "the exchange took the screen before the caller allowed it");
    }

    // -------------------------------------------------------------------------
    // 3. An interruption never eats a beat
    // -------------------------------------------------------------------------

    /**
     * The phone rings during a prompt and Android kills the process.  Marking a one-shot seen on
     * OPEN would spend the beat on a conversation nobody ever read — and a one-shot never comes
     * back.  It is spent on the ANSWER, so the moment is simply asked again next run.
     */
    @Test
    void anInterruptedPromptIsAskedAgainOnTheNextRun() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();

        ExchangeSystem interrupted = newExchangeSystem(new StoryProgress(store));
        assertTrue(interrupted.request(ExchangeTrigger.QUIET_MOMENT));
        assertTrue(interrupted.openPending());
        String interruptedId = interrupted.getActiveExchangeId();
        assertNotNull(interruptedId);
        // ...the process dies here. Nothing else is called on `interrupted`.

        ExchangeSystem nextRun = newExchangeSystem(new StoryProgress(store));
        assertTrue(nextRun.request(ExchangeTrigger.QUIET_MOMENT),
                "the interrupted beat was spent without ever being answered");
        assertTrue(nextRun.openPending());
        assertEquals(interruptedId, nextRun.getActiveExchangeId(),
                "the next run was offered a different beat than the one it lost");
    }

    /** Answered, though, it is spent for good — a one-shot is one-shot for the life of the save. */
    @Test
    void anAnsweredOneShotNeverReturns() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();

        ExchangeSystem firstRun = newExchangeSystem(new StoryProgress(store));
        assertTrue(firstRun.request(ExchangeTrigger.QUIET_MOMENT));
        assertTrue(firstRun.openPending());
        String answeredId = firstRun.getActiveExchangeId();
        assertNotNull(answeredId);
        assertTrue(firstRun.selectOption(0), "the answer was refused");

        ExchangeSystem nextRun = newExchangeSystem(new StoryProgress(store));
        if (nextRun.request(ExchangeTrigger.QUIET_MOMENT)) {
            assertTrue(nextRun.openPending());
            assertFalse(answeredId.equals(nextRun.getActiveExchangeId()),
                    "an answered one-shot exchange was asked a second time");
        }
    }

    // -------------------------------------------------------------------------
    // Nothing in the layer is on a timer — the other half of "never trap anyone"
    // -------------------------------------------------------------------------

    /**
     * A bark waits for the player, however long that is.  Nothing may cut a line short: not a
     * timeout, and not the next beat wanting the screen.
     */
    @Test
    void aBarkNeverExpiresOnItsOwn() {
        BarkSystem barks = newBarkSystem(new StoryProgress());
        barks.request(BarkTrigger.REGION_ENTERED);
        advance(barks, StoryUiConstants.STORY_FADE_IN_SECONDS + 0.05f);
        assertTrue(barks.hasActiveBark());
        String barkId = barks.getActiveBarkId();

        advance(barks, 120f);   // the player set the phone down and read it two minutes later
        assertTrue(barks.hasActiveBark(), "a bark timed out on its own");
        assertEquals(barkId, barks.getActiveBarkId(), "a bark was replaced while it was being read");
    }

    /** An exchange waits for an ANSWER.  There is no auto-select and no way past a prompt but a tap. */
    @Test
    void anExchangeNeverAnswersItself() {
        ExchangeSystem exchanges = newExchangeSystem(new StoryProgress());
        assertTrue(exchanges.request(ExchangeTrigger.QUIET_MOMENT));
        assertTrue(exchanges.openPending());

        for (int frameIndex = 0; frameIndex < 60 * 120; frameIndex++) {   // two minutes of frames
            exchanges.update(FRAME_SECONDS);
        }
        assertTrue(exchanges.isAwaitingChoice(), "the exchange advanced past the prompt by itself");
        assertFalse(exchanges.isFinished(), "the exchange closed without an answer");
    }
}

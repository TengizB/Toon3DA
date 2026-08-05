package ge.tbegvadze.toon3d.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * THE ROGUELIKE RECONCILIATION (Story UI order-7 Parts A and B) — the tests behind the one promise
 * that makes a linear story survive a permadeath roguelike: <b>death never rewinds the story.</b>
 *
 * <p>The story is a linear descent through five regions; the game is a procedural, branching,
 * permadeath descent the player will restart dozens of times.  Those only fit together because story
 * progress is gated by the DEEPEST region ever reached, persisted outside the run — so ORA's tone,
 * the planet's voice stage and the Organization's escalation only ever move forward, and a beat that
 * fired two runs ago never replays.  That is not a concession to the roguelike: the clone remembers,
 * ORA is never wiped, the planet never forgets you.  It IS the premise.
 *
 * <p>What is asserted here:
 * <ul>
 *   <li>Part A — the deepest region only climbs, out-of-order routes cannot rewind it, one-shot
 *       beats never replay after a death, and every channel reads the same persistent state;</li>
 *   <li>Part B — the whole schema round-trips through a store (save → load → identical), the schema
 *       version is stamped, and a "new game" clears EVERYTHING together.</li>
 * </ul>
 *
 * <p>A death is modelled the way the engine models it: the {@link StoryProgressStore} survives, and
 * every narrative system is rebuilt on top of it.  Headless — no LibGDX context anywhere.
 */
class StoryPersistenceTest {

    private static final float FRAME_SECONDS = 1f / 60f;

    /** Rebuilds the narrative state exactly as a fresh {@code World} does after a death. */
    private static StoryProgress reprint(StoryProgressStore store) {
        return new StoryProgress(store);
    }

    private static BarkSystem newBarkSystem(StoryProgress progress) {
        return new BarkSystem(BarkCatalog.defaultRegistry(), StoryStrings.defaults(), progress, 4242L);
    }

    private static void advance(BarkSystem system, float seconds) {
        int frames = Math.max(1, Math.round(seconds / FRAME_SECONDS));
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            system.update(FRAME_SECONDS);
        }
    }

    // -------------------------------------------------------------------------
    // PART A — the story only ever moves forward
    // -------------------------------------------------------------------------

    @Test
    void deepestRegionSurvivesDeathAndRebuild() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress firstLife = reprint(store);
        firstLife.reachRegion(StoryRegion.RELIQUARY);

        // ...death. A new run starts at the surface, with everything rebuilt from the store.
        StoryProgress secondLife = reprint(store);
        assertEquals(StoryRegion.RELIQUARY, secondLife.getDeepestRegion(),
                "a death rewound the story");

        // Re-entering Region 1 on the new run must not drag the story back up with the player.
        assertFalse(secondLife.reachRegion(StoryRegion.HABITATION_RINGS),
                "re-entering a shallow region reported new progress");
        assertEquals(StoryRegion.RELIQUARY, secondLife.getDeepestRegion(),
                "walking back up the facility rewound the story");
    }

    /**
     * The route map branches, so regions can be reached out of the "ideal" order.  Gating is strictly
     * by DEPTH, so an unusual path can only ever deepen the story — never shuffle it backwards.
     */
    @Test
    void outOfOrderRouteCannotRewindTheStory() {
        StoryProgress progress = new StoryProgress();
        assertTrue(progress.reachRegion(StoryRegion.WOUND));
        assertFalse(progress.reachRegion(StoryRegion.HARVESTING_GALLERIES));
        assertEquals(StoryRegion.WOUND, progress.getDeepestRegion());
        assertTrue(progress.reachRegion(StoryRegion.CORE), "the Core still had to be new");
    }

    /**
     * A one-shot beat is one-shot for the life of the SAVE, not of the run.  This is the difference
     * between a story and a loop: the second descent past a region gate does not re-explain it.
     */
    @Test
    void oneShotBeatsDoNotReplayOnALaterRun() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();

        BarkSystem firstRun = newBarkSystem(reprint(store));
        assertTrue(firstRun.request(BarkTrigger.REGION_ENTERED), "the region beat never fired");
        advance(firstRun, StoryUiConstants.STORY_FADE_IN_SECONDS + 0.05f);
        String firstBarkId = firstRun.getActiveBarkId();
        assertNotNull(firstBarkId);

        // Death: same store, new systems, fresh cooldowns — the only thing carried is the save.
        BarkSystem secondRun = newBarkSystem(reprint(store));
        secondRun.request(BarkTrigger.REGION_ENTERED);
        advance(secondRun, StoryUiConstants.STORY_FADE_IN_SECONDS + 0.05f);
        assertFalse(firstBarkId.equals(secondRun.getActiveBarkId()),
                "a one-shot region beat replayed on the next run");
    }

    /**
     * Every channel reads the SAME persistent progress, which is what keeps ORA one character rather
     * than three: the boot card cannot greet the player as a beginner while the barks talk like the
     * Reliquary.
     */
    @Test
    void everyChannelSharesTheSamePersistentProgress() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress progress = reprint(store);
        progress.reachRegion(StoryRegion.WOUND);

        BarkSystem     barks    = newBarkSystem(progress);
        BootCardSystem bootCard = new BootCardSystem(BootCardCatalog.defaultRegistry(),
                StoryStrings.defaults(), barks.getProgress(), 7L);
        ExchangeSystem exchanges = new ExchangeSystem(ExchangeCatalog.defaultRegistry(),
                StoryStrings.defaults(), barks.getProgress(), 7L);

        assertEquals(StoryRegion.WOUND, barks.getProgress().getDeepestRegion());
        assertEquals(StoryRegion.WOUND, bootCard.getProgress().getDeepestRegion());
        assertEquals(StoryRegion.WOUND, exchanges.getProgress().getDeepestRegion());
    }

    /** The reprint counter is the one number that only climbs, and a death is what makes it climb. */
    @Test
    void reprintCounterClimbsAcrossDeaths() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        assertEquals(1, reprint(store).recordReprint(), "the first run is already reprint 1");
        assertEquals(2, reprint(store).recordReprint());
        assertEquals(3, reprint(store).recordReprint());
        assertEquals(3, reprint(store).getReprintCount(), "the counter did not survive a rebuild");
    }

    /** Who the player has been is as persistent as where they have been (order-4's hidden model). */
    @Test
    void stanceAndOutcomesSurviveDeath() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress firstLife = reprint(store);
        firstLife.nudgeStance(Stance.PLANET, 2);
        firstLife.recordOutcome("exchange.test", "option.kept");
        firstLife.unlockCodexEntry("codex.test");
        firstLife.markCodexEntryRead("codex.test");

        StoryProgress secondLife = reprint(store);
        assertEquals(2, secondLife.getStance(Stance.PLANET));
        assertEquals(Stance.PLANET, secondLife.getDominantStance());
        assertEquals("option.kept", secondLife.getOutcome("exchange.test"));
        assertTrue(secondLife.isCodexUnlocked("codex.test"));
        assertTrue(secondLife.isCodexEntryRead("codex.test"));
    }

    // -------------------------------------------------------------------------
    // PART B — the persisted schema: round-trip, versioning, and the wipe
    // -------------------------------------------------------------------------

    /** Save → load → identical, for every field the schema holds. */
    @Test
    void wholeSchemaRoundTripsThroughTheStore() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress written = reprint(store);
        written.reachRegion(StoryRegion.HARVESTING_GALLERIES);
        written.markSeen("beat.one");
        written.markSeen("beat.two");
        written.recordReprint();
        written.recordReprint();
        written.nudgeStance(Stance.ORA, 3);
        written.nudgeStance(Stance.ORGANIZATION, -1);
        written.recordOutcome("exchange.yield", "option.refused");
        written.unlockCodexEntry("codex.yield");

        StorySettings writtenSettings = new StorySettings(store);
        writtenSettings.setTextSize(StoryTextSize.LARGEST);
        writtenSettings.setReduceTextHold(true);
        writtenSettings.setStoryAudioEnabled(false);

        StoryProgress read = reprint(store);
        assertEquals(StoryRegion.HARVESTING_GALLERIES, read.getDeepestRegion());
        assertTrue(read.hasSeen("beat.one"));
        assertTrue(read.hasSeen("beat.two"));
        assertFalse(read.hasSeen("beat.never"));
        assertEquals(2, read.getReprintCount());
        assertEquals(3, read.getStance(Stance.ORA));
        assertEquals(-1, read.getStance(Stance.ORGANIZATION));
        assertEquals("option.refused", read.getOutcome("exchange.yield"));
        assertTrue(read.isCodexUnlocked("codex.yield"));

        StorySettings readSettings = new StorySettings(store);
        assertEquals(StoryTextSize.LARGEST, readSettings.getTextSize());
        assertTrue(readSettings.isReduceTextHold());
        assertFalse(readSettings.isReduceMotion(), "a setting nobody touched came back changed");
        assertFalse(readSettings.isStoryAudioEnabled());
    }

    /** A save says which format wrote it, so a future schema change can migrate instead of guess. */
    @Test
    void everyWriteStampsTheSchemaVersion() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        assertEquals(0, store.loadSchemaVersion(), "an untouched save claimed a version");

        reprint(store).markSeen("beat.one");
        assertEquals(StoryProgressStore.SCHEMA_VERSION, store.loadSchemaVersion());
    }

    /**
     * The "new game" wipe.  Everything goes TOGETHER — a save with the region reset but the one-shot
     * flags intact would start a story whose beats have all already been spent, which is worse than
     * either extreme.
     */
    @Test
    void newGameWipeClearsEveryNarrativeFactTogether() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress progress = reprint(store);
        progress.reachRegion(StoryRegion.CORE);
        progress.markSeen("beat.one");
        progress.recordReprint();
        progress.nudgeStance(Stance.ORA, 4);
        progress.recordOutcome("exchange.core", "option.free");
        progress.unlockCodexEntry("codex.core");
        progress.markCodexEntryRead("codex.core");
        new StorySettings(store).setTextSize(StoryTextSize.LARGE);

        progress.wipeAllNarrativeState();

        // The live object, not just the store: a stale cache would go on reporting the old story.
        assertEquals(StoryRegion.HABITATION_RINGS, progress.getDeepestRegion());
        assertFalse(progress.hasSeen("beat.one"));
        assertEquals(0, progress.getReprintCount());
        assertEquals(0, progress.getStance(Stance.ORA));
        assertNull(progress.getDominantStance());
        assertNull(progress.getOutcome("exchange.core"));
        assertFalse(progress.isCodexUnlocked("codex.core"));
        assertFalse(progress.isCodexEntryRead("codex.core"));

        // And a fresh load of the same store agrees — including the settings, which live beside it.
        StoryProgress afterWipe = reprint(store);
        assertEquals(StoryRegion.HABITATION_RINGS, afterWipe.getDeepestRegion());
        assertEquals(0, afterWipe.getReprintCount());
        assertEquals(StoryTextSize.NORMAL, new StorySettings(store).getTextSize());
        assertEquals(StoryProgressStore.SCHEMA_VERSION, store.loadSchemaVersion(),
                "the wiped save lost its schema stamp");
    }

    /** After the store is cleared behind its back, a live {@link StoryProgress} can re-read it. */
    @Test
    void reloadFromStoreRefreshesACachedProgress() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress progress = reprint(store);
        progress.reachRegion(StoryRegion.WOUND);
        progress.markSeen("beat.one");
        progress.recordReprint();

        store.clear();                 // as a whole-preferences-file wipe does
        progress.reloadFromStore();

        assertEquals(StoryRegion.HABITATION_RINGS, progress.getDeepestRegion());
        assertFalse(progress.hasSeen("beat.one"), "a cached seen-flag outlived the save");
        assertEquals(0, progress.getReprintCount());
    }

    /** A wiped save reads as a brand-new player: the story starts over from the first line. */
    @Test
    void wipedSaveReplaysTheStoryFromTheStart() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress progress = reprint(store);

        BarkSystem firstPlaythrough = newBarkSystem(progress);
        firstPlaythrough.request(BarkTrigger.REGION_ENTERED);
        advance(firstPlaythrough, StoryUiConstants.STORY_FADE_IN_SECONDS + 0.05f);
        String firstBarkId = firstPlaythrough.getActiveBarkId();
        assertNotNull(firstBarkId);

        progress.wipeAllNarrativeState();

        BarkSystem afterWipe = newBarkSystem(reprint(store));
        afterWipe.request(BarkTrigger.REGION_ENTERED);
        advance(afterWipe, StoryUiConstants.STORY_FADE_IN_SECONDS + 0.05f);
        assertEquals(firstBarkId, afterWipe.getActiveBarkId(),
                "a new game did not put the story back at its first line");
    }
}

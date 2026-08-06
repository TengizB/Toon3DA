package ge.tbegvadze.toon3d.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Headless guards for the Story UI order-3 REPRINT / BOOT CARD: the card is complete and readable,
 * the instance counter climbs and survives death, ORA's wake line is gated by the deepest story
 * region ever reached, the card never traps the player, and the endgame variants subvert it —
 * including the FREE/KILL state where the run correctly does NOT reprint.
 *
 * <p>Everything here runs without a LibGDX context, which is the point of keeping the boot-card
 * brain in {@code …toon3d.narrative}.
 */
class StoryBootCardTest {

    /** A frame step small enough that reveal-timing assertions are exact to a few hundredths. */
    private static final float FRAME_SECONDS = 1f / 60f;

    private static BootCardSystem newSystem(StoryProgress progress) {
        return new BootCardSystem(BootCardCatalog.defaultRegistry(), StoryStrings.defaults(),
                                  progress, 4242L);
    }

    private static BootCardSystem newSystemInRegion(StoryRegion region) {
        StoryProgress progress = new StoryProgress();
        progress.reachRegion(region);
        return newSystem(progress);
    }

    /**
     * A system for a save that has already printed at least one body, so {@code present(REPRINT)}
     * resolves to the ORDINARY reprint card rather than to the FIRST-PRINT one (order-2 B).  The
     * count is pushed past the reserved first-death lines too, so the wake line comes from the
     * region pool — which is what most of the tests below are about.
     */
    private static BootCardSystem newOrdinarySystemInRegion(StoryRegion region) {
        StoryProgress progress = new StoryProgress();
        progress.reachRegion(region);
        for (int printIndex = 0; printIndex < 5; printIndex++) progress.recordReprint();
        return newSystem(progress);
    }

    /** Runs the system forward, one frame at a time, for the given number of seconds. */
    private static void advance(BootCardSystem system, float seconds) {
        int frames = Math.max(1, Math.round(seconds / FRAME_SECONDS));
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            system.update(FRAME_SECONDS);
        }
    }

    /**
     * Every status-line id a card can print, across ALL of its region bands (order-2 C).  A card may
     * say more to a player who has been deep enough to understand it, and a band nobody checks is a
     * band that ships an unresolvable id.
     */
    private static List<String> everyStatusLineIdOf(BootCardDefinition card) {
        List<String> ids = new ArrayList<>();
        for (int blockIndex = 0; blockIndex < card.getStatusBlockCount(); blockIndex++) {
            for (String stringId : card.getStatusBlockLineStringIds(blockIndex)) ids.add(stringId);
        }
        return ids;
    }

    /** The reveal delay for the whole status block, plus a margin. */
    private static float fullRevealSeconds() {
        return StoryUiConstants.STORY_BOOT_LINE_REVEAL_SECONDS
                * StoryUiConstants.STORY_BOOT_SYSTEM_MAX_LINES + 0.2f;
    }

    // -------------------------------------------------------------------------
    // Catalog integrity — every line exists, and every line is readable
    // -------------------------------------------------------------------------

    @Test
    void everyVariantThatShowsACardHasOneRegistered() {
        BootCardRegistry registry = BootCardCatalog.defaultRegistry();
        for (BootCardVariant variant : BootCardVariant.values()) {
            if (variant.showsCard()) {
                assertNotNull(registry.getCard(variant), "no card registered for " + variant);
            } else {
                // MERGE registers nothing on purpose: the interface never reboots.
                assertNull(registry.getCard(variant), variant + " must not register a card");
            }
        }
    }

    @Test
    void everyCatalogLineResolvesToRealText() {
        StoryStrings strings = StoryStrings.defaults();
        for (BootCardDefinition card : BootCardCatalog.defaultRegistry().getCards()) {
            for (String stringId : everyStatusLineIdOf(card)) {
                assertTrue(strings.has(stringId),
                        card.getVariant() + " has no text for id " + stringId);
                assertFalse(strings.get(stringId).trim().isEmpty(),
                        card.getVariant() + " resolves to blank text for " + stringId);
            }
        }
        for (BootWakeLine line : BootCardCatalog.defaultRegistry().getWakeLines()) {
            assertTrue(strings.has(line.getTextStringId()),
                    "wake line " + line.getId() + " has no text for id " + line.getTextStringId());
            assertFalse(strings.get(line.getTextStringId()).trim().isEmpty(),
                    "wake line " + line.getId() + " resolves to blank text");
        }
        // The button label is a localised id too — never a literal in the renderer.
        assertTrue(strings.has(StoryUiConstants.STORY_BOOT_CONTINUE_LABEL_ID));
    }

    /** The shipped asset is the real table; the Java defaults are only its fallback. */
    @Test
    void shippedStringAssetCoversEveryBootCardLine() throws IOException {
        File assetFile = new File("../assets/" + StoryUiConstants.STORY_STRINGS_ASSET_PATH);
        assumeTrue(assetFile.exists(), "string asset not reachable from the test working directory");
        StoryStrings shipped = StoryStrings.fromProperties(
                new String(Files.readAllBytes(assetFile.toPath()), StandardCharsets.UTF_8));
        StoryStrings defaults = StoryStrings.defaults();

        for (BootCardDefinition card : BootCardCatalog.defaultRegistry().getCards()) {
            for (String stringId : everyStatusLineIdOf(card)) {
                assertTrue(shipped.has(stringId),
                        "story-strings.properties is missing " + stringId);
                assertEquals(defaults.get(stringId), shipped.get(stringId),
                        "asset and BootCardStrings disagree on " + stringId);
            }
        }
        for (BootWakeLine line : BootCardCatalog.defaultRegistry().getWakeLines()) {
            assertTrue(shipped.has(line.getTextStringId()),
                    "story-strings.properties is missing " + line.getTextStringId());
            assertEquals(defaults.get(line.getTextStringId()), shipped.get(line.getTextStringId()),
                    "asset and BootCardStrings disagree on " + line.getTextStringId());
        }
        assertEquals(defaults.get(StoryUiConstants.STORY_BOOT_CONTINUE_LABEL_ID),
                shipped.get(StoryUiConstants.STORY_BOOT_CONTINUE_LABEL_ID));
    }

    /**
     * A status readout that wraps looks like a layout bug rather than like a cold terminal, so every
     * SYSTEM line must fit on exactly one panel row — and the card must not print more rows than the
     * panel was sized for.
     */
    @Test
    void everySystemLineFitsOnOnePanelRow() {
        StoryStrings strings = StoryStrings.defaults();
        for (BootCardDefinition card : BootCardCatalog.defaultRegistry().getCards()) {
            assertTrue(card.getSystemLineCount() <= StoryUiConstants.STORY_BOOT_SYSTEM_MAX_LINES,
                    card.getVariant() + " has more status lines than the panel holds");
            for (String stringId : everyStatusLineIdOf(card)) {
                String text = strings.get(stringId);
                assertTrue(text.length() <= StoryUiConstants.STORY_LINE_MAX_CHARS,
                        "system line over the one-row cap (" + text.length() + "): " + text);
            }
        }
    }

    /**
     * ORA's line is prose and wraps — but never past the card's two-line cap, even once the
     * {instance} token has been swapped for a wide five-digit reprint number.
     */
    @Test
    void everyWakeLineFitsTheTwoLineCapAtTheWidestCounter() {
        StoryStrings strings = StoryStrings.defaults();
        String widestCounter = GameMath.formatInstanceCounter(99999,
                StoryUiConstants.STORY_BOOT_COUNTER_DIGITS);
        for (BootWakeLine line : BootCardCatalog.defaultRegistry().getWakeLines()) {
            String text = strings.get(line.getTextStringId())
                                 .replace(BootCardSystem.INSTANCE_TOKEN, widestCounter);
            List<String> wrapped = StoryText.wrapToMaxChars(text,
                    StoryUiConstants.STORY_LINE_MAX_CHARS);
            assertTrue(wrapped.size() <= StoryUiConstants.STORY_BOOT_WAKE_MAX_LINES,
                    "wake line " + line.getId() + " wraps to " + wrapped.size() + " lines: " + text);
        }
    }

    /**
     * Every region needs a pool deep enough that consecutive reprints do not repeat a greeting.
     * RESERVED rows do not count towards it: each of those can only ever be drawn by one card in the
     * life of a save (order-2 B/C), so they are not depth, they are single beats.
     */
    @Test
    void everyRegionHasADeepEnoughWakeLinePool() {
        for (StoryRegion region : StoryRegion.values()) {
            int poolSize = 0;
            for (BootWakeLine line : BootCardCatalog.defaultRegistry().getWakeLines()) {
                if (!line.isReserved() && line.matchesRegion(region)) poolSize++;
            }
            assertTrue(poolSize >= 3,
                    "region " + region + " has only " + poolSize + " wake lines");
        }
    }

    // -------------------------------------------------------------------------
    // The instance counter — it only ever climbs, and death never resets it
    // -------------------------------------------------------------------------

    @Test
    void theVeryFirstRunAlreadyCountsAsAReprint() {
        // In-fiction the original self has just died, so the player's first boot shows the same
        // death notice as their hundredth — and is already instance 1.
        BootCardSystem system = newSystem(new StoryProgress());
        assertTrue(system.present(BootCardVariant.REPRINT));
        assertEquals(StoryUiConstants.STORY_BOOT_COUNTER_PREFIX
                        + GameMath.formatInstanceCounter(1, StoryUiConstants.STORY_BOOT_COUNTER_DIGITS),
                system.getInstanceCounterText());
    }

    @Test
    void theCounterClimbsAcrossDeathsAndSurvivesARestart() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();

        // Three runs, each its own World and its own BootCardSystem — exactly what a death does.
        for (int runIndex = 1; runIndex <= 3; runIndex++) {
            BootCardSystem system = newSystem(new StoryProgress(store));
            system.present(BootCardVariant.REPRINT);
            assertTrue(system.getInstanceCounterText().endsWith(
                            GameMath.formatInstanceCounter(runIndex,
                                    StoryUiConstants.STORY_BOOT_COUNTER_DIGITS)),
                    "counter did not climb on run " + runIndex);
        }
        // A fresh StoryProgress over the same store is the app being restarted.
        assertEquals(3, new StoryProgress(store).getReprintCount());
    }

    @Test
    void theCounterIsZeroPaddedToAFixedWidth() {
        assertEquals("0048", GameMath.formatInstanceCounter(48, 4));
        assertEquals("0001", GameMath.formatInstanceCounter(1, 4));
        // More digits than the pad is printed in full — never truncated back to a smaller number.
        assertEquals("12345", GameMath.formatInstanceCounter(12345, 4));
        assertEquals("0", GameMath.formatInstanceCounter(-7, 0));
    }

    @Test
    void theInstanceTokenIsSubstitutedIntoTheWakeLine() {
        // Five prints in, so the card is the ordinary reprint and the line comes from the region
        // pool rather than from one of the reserved first-print / first-death rows.
        StoryProgress progress = new StoryProgress();
        for (int printIndex = 0; printIndex < 5; printIndex++) progress.recordReprint();
        StoryStrings strings = new StoryStrings()
                .put("story.speaker.ai.name", "ORA")
                .put("story.boot.system.reprint.1", "PREVIOUS BODY ........ DECEASED")
                .put("story.boot.system.reprint.2", "BACKUP COPY .......... ON FILE")
                .put("story.boot.system.reprint.3", "PRINTING NEW BODY .... STAND BY")
                .put("story.boot.wake.rings.1", "Reprint {instance}.")
                .put("story.boot.wake.rings.2", "Reprint {instance}.")
                .put("story.boot.wake.rings.3", "Reprint {instance}.")
                .put("story.boot.wake.rings.4", "Reprint {instance}.");
        BootCardSystem system =
                new BootCardSystem(BootCardCatalog.defaultRegistry(), strings, progress, 7L);
        system.present(BootCardVariant.REPRINT);

        assertEquals(1, system.getWakeLineCount());
        assertEquals("Reprint 0006.", system.getWakeLines()[0]);
        assertFalse(system.getWakeLines()[0].contains(BootCardSystem.INSTANCE_TOKEN),
                "the {instance} placeholder leaked to the screen");
    }

    // -------------------------------------------------------------------------
    // THE FIRST PRINT, and the words that have to wait (narrative-rework order-2 B/C)
    // -------------------------------------------------------------------------

    /**
     * A save that has never printed anybody gets the FIRST-PRINT card — the one that says, in four
     * plain lines and no proper nouns, that a person died, that person is you, there is a copy, and
     * the copy is being made.  Every call site still just asks for "the reprint card".
     */
    @Test
    void aSaveWithNobodyPrintedYetGetsTheFirstPrintCard() {
        BootCardSystem system = newSystem(new StoryProgress());
        assertTrue(system.present(BootCardVariant.REPRINT));
        assertEquals(BootCardVariant.FIRST_PRINT, system.getActiveVariant(),
                "a first-timer was shown the card written for somebody who already knows the loop");
        assertEquals(4, system.getSystemLineCount(),
                "the first-print notice must say all four things");

        for (int lineIndex = 0; lineIndex < system.getSystemLineCount(); lineIndex++) {
            String line = system.getSystemLines()[lineIndex];
            assertFalse(line.contains("INSTANCE"), "the first card a stranger reads uses 'instance'");
            assertFalse(line.contains("REPRINT"),  "the first card a stranger reads uses 'reprint'");
            assertFalse(line.contains("SOUL"),     "the first card a stranger reads uses 'soul'");
        }
        // And the very first line states the death plainly enough to parse cold.
        assertTrue(system.getSystemLines()[0].contains("DECEASED"),
                "the first line must say somebody died: " + system.getSystemLines()[0]);
        assertNotNull(StoryStrings.defaults().get("story.boot.system.firstprint.4"));
    }

    /** From the second print on, it is the ordinary card — the FIRST-PRINT one never returns. */
    @Test
    void theSecondPrintOnwardsGetsTheOrdinaryCard() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        newSystem(new StoryProgress(store)).present(BootCardVariant.REPRINT);   // print 1

        BootCardSystem secondPrint = newSystem(new StoryProgress(store));
        assertTrue(secondPrint.present(BootCardVariant.REPRINT));
        assertEquals(BootCardVariant.REPRINT, secondPrint.getActiveVariant(),
                "the first-print card came back for a body that was not the first");
    }

    /**
     * "SOUL RESERVE" is the Region-3 reveal, and it does not appear on ANY card until the player has
     * reached the Harvesting Galleries — which is where ORA starts asking what the printers run on.
     * Printed on screen one it was wallpaper for hours; printed here it is the beat.
     */
    @Test
    void theReserveLineWaitsForTheGalleries() {
        assertFalse(cardTextAt(StoryRegion.HABITATION_RINGS).contains("SOUL RESERVE"),
                "the Region-3 reveal is printed on the surface, where it means nothing");
        assertTrue(cardTextAt(StoryRegion.HARVESTING_GALLERIES).contains("SOUL RESERVE"),
                "the reserve line never arrives, even once the player could understand it");
        assertTrue(cardTextAt(StoryRegion.CORE).contains("SOUL RESERVE"),
                "the reserve line stops being printed deeper down");
    }

    /** Everything the reprint card prints at a given depth, as one string. */
    private static String cardTextAt(StoryRegion region) {
        BootCardSystem system = newOrdinarySystemInRegion(region);
        system.present(BootCardVariant.REPRINT);
        StringBuilder text = new StringBuilder();
        for (int lineIndex = 0; lineIndex < system.getSystemLineCount(); lineIndex++) {
            text.append(system.getSystemLines()[lineIndex]).append('\n');
        }
        return text.toString();
    }

    /**
     * The cards a player only ever reads once get the line written for them, in order: ORA's first
     * words on the first print, then the two that explain what a death actually costs, then the one
     * that points at the REPORT plate.  A plate nobody taps is a page nobody reads.
     */
    @Test
    void theFirstPrintAndTheFirstDeathsGetTheirOwnLines() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        String[] expectedInOrder = {
                "boot.wake.first.1",             // print 1 — the first print
                "boot.wake.firstdeath.1",        // print 2 — the player's own first death
                "boot.wake.firstdeath.2",        // print 3 — what did and did not come back
                "boot.wake.firstdeath.report",   // print 4 — and where to read what got them
        };
        for (int printIndex = 0; printIndex < expectedInOrder.length; printIndex++) {
            BootCardSystem system = newSystem(new StoryProgress(store));
            system.present(BootCardVariant.REPRINT);
            assertEquals(expectedInOrder[printIndex], system.getActiveWakeLineId(),
                    "print " + (printIndex + 1) + " drew the wrong line");
        }
        // And then the ordinary pool takes over for good.
        BootCardSystem fifthPrint = newSystem(new StoryProgress(store));
        fifthPrint.present(BootCardVariant.REPRINT);
        assertTrue(fifthPrint.getActiveWakeLineId().startsWith("boot.wake.rings."),
                "a reserved line was drawn twice: " + fifthPrint.getActiveWakeLineId());
    }

    /** The first-death lines must land whatever depth the story happens to have reached by then. */
    @Test
    void theFirstDeathLineIsNotLostToADeepStory() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress progress = new StoryProgress(store);
        progress.reachRegion(StoryRegion.RELIQUARY);
        progress.recordReprint();   // the first print happened before this test's card

        BootCardSystem firstDeath = newSystem(new StoryProgress(store));
        firstDeath.present(BootCardVariant.REPRINT);
        assertEquals("boot.wake.firstdeath.1", firstDeath.getActiveWakeLineId(),
                "the line that explains the loop was lost to the region gate");
    }

    // -------------------------------------------------------------------------
    // Region gating — ORA's arc, expressed purely by which band a line sits in
    // -------------------------------------------------------------------------

    @Test
    void theWakeLineIsGatedByTheDeepestRegionReached() {
        assertTrue(newSystemAndPresent(StoryRegion.HABITATION_RINGS).startsWith("boot.wake.rings."));
        assertTrue(newSystemAndPresent(StoryRegion.HARVESTING_GALLERIES).startsWith("boot.wake.galleries."));
        assertTrue(newSystemAndPresent(StoryRegion.RELIQUARY).startsWith("boot.wake.reliquary."));
        assertTrue(newSystemAndPresent(StoryRegion.WOUND).startsWith("boot.wake.wound."));
        assertTrue(newSystemAndPresent(StoryRegion.CORE).startsWith("boot.wake.core."));
    }

    private static String newSystemAndPresent(StoryRegion region) {
        BootCardSystem system = newOrdinarySystemInRegion(region);
        system.present(BootCardVariant.REPRINT);
        return system.getActiveWakeLineId();
    }

    @Test
    void deathDoesNotRewindOraSToneOnTheNextReprint() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress firstRun = new StoryProgress(store);
        firstRun.reachRegion(StoryRegion.RELIQUARY);
        // A player deep enough to be in the Reliquary has been printed a good many times.
        for (int printIndex = 0; printIndex < 5; printIndex++) firstRun.recordReprint();

        // The player dies and starts a whole new run at the surface. ORA must NOT go back to
        // cheerfully counting deaths — she cannot un-know what the Cradles are fed.
        BootCardSystem nextRun = newSystem(new StoryProgress(store));
        nextRun.present(BootCardVariant.REPRINT);
        assertTrue(nextRun.getActiveWakeLineId().startsWith("boot.wake.reliquary."),
                "death rewound the story: " + nextRun.getActiveWakeLineId());
    }

    @Test
    void consecutiveReprintsDoNotRepeatTheSameGreeting() {
        BootCardSystem system = newOrdinarySystemInRegion(StoryRegion.HABITATION_RINGS);
        Set<String> saidRecently = new HashSet<>();
        for (int reprintIndex = 0; reprintIndex < 3; reprintIndex++) {
            system.present(BootCardVariant.REPRINT);
            assertTrue(saidRecently.add(system.getActiveWakeLineId()),
                    "ORA repeated " + system.getActiveWakeLineId() + " within three reprints");
        }
    }

    // -------------------------------------------------------------------------
    // The card never traps the player
    // -------------------------------------------------------------------------

    @Test
    void continueProceedsImmediatelyEvenMidReveal() {
        BootCardSystem system = newSystem(new StoryProgress());
        system.present(BootCardVariant.REPRINT);
        // Not a single frame has passed: the status lines are still printing in.
        assertFalse(system.isRevealComplete());
        assertTrue(system.requestContinue(), "CONTINUE was refused mid-reveal");
        assertTrue(system.isContinueRequested());
    }

    @Test
    void theCardNeverAutoAdvances() {
        BootCardSystem system = newSystem(new StoryProgress());
        system.present(BootCardVariant.REPRINT);
        advance(system, 120f);   // two minutes of a player reading, or not
        assertTrue(system.hasActiveCard(), "the card vanished on its own");
        assertFalse(system.isContinueRequested(), "the card advanced without a tap");
    }

    @Test
    void statusLinesPrintInOneAtATimeAndATapCompletesThem() {
        BootCardSystem system = newSystem(new StoryProgress());
        system.present(BootCardVariant.REPRINT);
        assertEquals(1, system.getRevealedSystemLineCount(), "the card started blank");

        advance(system, StoryUiConstants.STORY_BOOT_LINE_REVEAL_SECONDS + 0.01f);
        assertEquals(2, system.getRevealedSystemLineCount());

        // A tap that is not CONTINUE skips the mood effect rather than doing nothing.
        system.skipReveal();
        assertEquals(system.getSystemLineCount(), system.getRevealedSystemLineCount());
        assertTrue(system.isRevealComplete());
    }

    @Test
    void reduceTextHoldShortensTheRevealWithoutChangingTheCard() {
        BootCardSystem paced = newSystem(new StoryProgress());
        paced.present(BootCardVariant.REPRINT);

        BootCardSystem reduced = newSystem(new StoryProgress());
        reduced.setReducedTextHold(true);
        reduced.present(BootCardVariant.REPRINT);

        float halfWay = StoryUiConstants.STORY_BOOT_LINE_REVEAL_SECONDS
                * StoryUiConstants.STORY_BOOT_SYSTEM_MAX_LINES * 0.5f;
        advance(paced, halfWay);
        advance(reduced, halfWay);

        assertTrue(reduced.getRevealedSystemLineCount() >= paced.getRevealedSystemLineCount(),
                "reduce-text-hold did not shorten the reveal");
        assertTrue(reduced.isRevealComplete(), "reduce-text-hold left the card still printing");
        assertEquals(paced.getSystemLineCount(), reduced.getSystemLineCount(),
                "reduce-text-hold changed what the card says");
    }

    @Test
    void theWholeCardIsUpAndReadableOnceTheRevealFinishes() {
        BootCardSystem system = newOrdinarySystemInRegion(StoryRegion.HABITATION_RINGS);
        system.present(BootCardVariant.REPRINT);
        advance(system, fullRevealSeconds());

        assertTrue(system.isRevealComplete());
        assertEquals(3, system.getSystemLineCount());
        assertNotNull(system.getInstanceCounterText());
        assertTrue(system.getWakeLineCount() > 0, "ORA said nothing on a reprint card");
        assertNotNull(system.getWakeSpeakerName());
        assertEquals(1f, system.getVisibleFraction(), 0.001f, "the card never reached full opacity");
    }

    // -------------------------------------------------------------------------
    // The endgame subversions — the payload
    // -------------------------------------------------------------------------

    @Test
    void freeAndKillEndingsPrintTheCardAndThenRefuseToReprint() {
        for (BootCardVariant variant : new BootCardVariant[] {
                BootCardVariant.ENDING_FREE, BootCardVariant.ENDING_KILL }) {
            BootCardSystem system = newSystem(new StoryProgress());
            assertTrue(system.present(variant), variant + " printed no card");

            assertTrue(system.isTerminal(), variant + " is not terminal");
            assertFalse(system.isContinueAllowed(), variant + " offered a way onward");
            assertFalse(system.requestContinue(), variant + " accepted a continue request");
            assertFalse(system.isContinueRequested(), variant + " would have reloaded the run");
            // The counter is not printed: there is no next instance to number.
            assertNull(system.getInstanceCounterText(), variant + " printed an instance counter");
            // The machine ends this one alone.
            assertEquals(0, system.getWakeLineCount(), variant + " let ORA talk over the ending");
        }
    }

    @Test
    void theEndingCardInvertsTheCardThePlayerHasReadAHundredTimes() {
        StoryStrings strings = StoryStrings.defaults();
        BootCardRegistry registry = BootCardCatalog.defaultRegistry();

        // The reserve line is the DEEP band of the reprint card (order-2 C): the ending inverts the
        // card as the player has been reading it for regions, which is only that band.
        String defaultReserve = strings.get(
                registry.getCard(BootCardVariant.REPRINT)
                        .getSystemLineStringIds(StoryRegion.HARVESTING_GALLERIES)[1]);
        String freeReserve = strings.get(
                registry.getCard(BootCardVariant.ENDING_FREE).getSystemLineStringIds()[1]);
        String freeClosing = strings.get(
                registry.getCard(BootCardVariant.ENDING_FREE).getSystemLineStringIds()[2]);

        // Same instrument, opposite reading — that recognition is the whole payload.
        assertTrue(defaultReserve.contains("SUFFICIENT"), "the default card lost its reserve line");
        assertTrue(freeReserve.contains("DEPLETED"), "the FREE card did not invert the reserve line");
        assertTrue(freeClosing.contains("NO CHECKPOINT"),
                "the FREE card did not withdraw the checkpoint");
    }

    @Test
    void obeyIsARewardCardThatStillContinues() {
        BootCardSystem system = newSystem(new StoryProgress());
        assertTrue(system.present(BootCardVariant.ENDING_OBEY));
        assertFalse(system.isTerminal(), "OBEY must continue: the reward is more of the same");
        assertTrue(system.isContinueAllowed());
        assertTrue(system.requestContinue());
        // The counter stays, because it will keep climbing forever.
        assertNotNull(system.getInstanceCounterText());
    }

    @Test
    void mergeShowsNoCardAtAll() {
        BootCardSystem system = newSystem(new StoryProgress());
        assertFalse(system.present(BootCardVariant.ENDING_MERGE),
                "MERGE presented a card; the interface must simply stop being the player's");
        assertFalse(system.hasActiveCard());
        assertEquals(0f, system.getVisibleFraction(), 0.0001f);
    }

    @Test
    void clearingTheCardLeavesNothingBehind() {
        BootCardSystem system = newSystem(new StoryProgress());
        system.present(BootCardVariant.REPRINT);
        system.requestContinue();
        system.clear();

        assertFalse(system.hasActiveCard());
        assertFalse(system.isContinueRequested(), "a stale continue would skip the next card");
        assertEquals(0, system.getSystemLineCount());
        assertEquals(0, system.getWakeLineCount());
        assertNull(system.getInstanceCounterText());
    }

    // -------------------------------------------------------------------------
    // Layout — the card fits the screen and the tap target suits a thumb
    // -------------------------------------------------------------------------

    @Test
    void theStackFitsOnScreenAndNeverOverlapsItself() {
        assertTrue(StoryUiConstants.STORY_BOOT_SYSTEM_TOP_Y <= Constants.WORLD_HEIGHT,
                "the status panel runs off the top of the screen");
        assertTrue(StoryUiConstants.STORY_BOOT_PANEL_X >= 0f);
        assertTrue(StoryUiConstants.STORY_BOOT_PANEL_X + StoryUiConstants.STORY_BOOT_PANEL_WIDTH
                <= Constants.WORLD_WIDTH, "the card is wider than the screen");

        // Top-down stack: status panel, then the counter, then ORA, then the button.
        assertTrue(StoryUiConstants.STORY_BOOT_COUNTER_TOP_Y < StoryUiConstants.STORY_BOOT_SYSTEM_Y,
                "the counter overlaps the status panel");
        assertTrue(StoryUiConstants.STORY_BOOT_WAKE_TOP_Y < StoryUiConstants.STORY_BOOT_COUNTER_TOP_Y,
                "ORA's panel overlaps the counter");
        assertTrue(StoryUiConstants.STORY_BOOT_CONTINUE_Y
                        + StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT
                        <= StoryUiConstants.STORY_BOOT_WAKE_Y,
                "the CONTINUE button overlaps ORA's panel");
        assertTrue(StoryUiConstants.STORY_BOOT_CONTINUE_Y >= 0f,
                "the CONTINUE button runs off the bottom of the screen");
    }

    @Test
    void theContinueButtonIsAComfortableThumbTarget() {
        // At least as generous as the order-4 choice button — the player has just died.
        assertTrue(StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT
                        >= StoryUiConstants.STORY_CHOICE_BUTTON_MIN_HEIGHT,
                "the CONTINUE button is shorter than a choice button");
        assertTrue(StoryUiConstants.STORY_BOOT_CONTINUE_WIDTH >= 240f,
                "the CONTINUE button is too narrow for a thumb");
    }

    @Test
    void panelHeightsMatchTheLineCountsTheyMustHold() {
        // The height rule the panels are sized by: 30 * lines + 70 (see StoryUiConstants).
        assertEquals(30f * StoryUiConstants.STORY_BOOT_SYSTEM_MAX_LINES + 70f,
                StoryUiConstants.STORY_BOOT_SYSTEM_PANEL_HEIGHT, 0.001f,
                "the status panel is not sized for its line count");
        assertEquals(30f * StoryUiConstants.STORY_BOOT_WAKE_MAX_LINES + 70f,
                StoryUiConstants.STORY_BOOT_WAKE_PANEL_HEIGHT, 0.001f,
                "ORA's panel is not sized for its line count");
        // The same rule reproduces the bark panel the primitive was tuned against.
        assertEquals(StoryUiConstants.STORY_BARK_HEIGHT,
                30f * StoryUiConstants.STORY_BARK_MAX_LINES + 70f, 0.001f);
    }

    @Test
    void theRevealFormulaHandlesItsEdges() {
        assertEquals(0, GameMath.bootCardRevealedLineCount(-1f, 0.5f, 3));
        assertEquals(0, GameMath.bootCardRevealedLineCount(1f, 0.5f, 0));
        // A zero delay (reduce-text-hold driven to nothing) must not divide by zero.
        assertEquals(3, GameMath.bootCardRevealedLineCount(0f, 0f, 3));
        assertEquals(1, GameMath.bootCardRevealedLineCount(0f, 0.5f, 3));
        assertEquals(3, GameMath.bootCardRevealedLineCount(999f, 0.5f, 3));
    }
}

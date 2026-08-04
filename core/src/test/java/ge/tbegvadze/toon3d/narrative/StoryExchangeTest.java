package ge.tbegvadze.toon3d.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Headless guards for the Story UI order-4 EXCHANGE layer: the catalog is complete and readable, an
 * exchange blocks until the player answers and never advances on its own, there is no way past the
 * prompt except an actual answer, the hidden stance model moves and survives death, consequential
 * answers and codex unlocks are recorded permanently, and the button stack is a thumb target that
 * fits on screen.
 *
 * <p>Everything here runs without a LibGDX context, which is the point of keeping the exchange brain
 * in {@code …toon3d.narrative}.
 */
class StoryExchangeTest {

    /** A frame step small enough that fade assertions are exact to a few hundredths. */
    private static final float FRAME_SECONDS = 1f / 60f;

    /** The whole catalog must stay SMALL — an exchange stops the world, so rarity is the design. */
    private static final int EXCHANGE_BUDGET = 12;

    private static ExchangeSystem newSystem(StoryProgress progress) {
        return new ExchangeSystem(ExchangeCatalog.defaultRegistry(), StoryStrings.defaults(),
                                  progress, 4242L);
    }

    private static ExchangeSystem newSystemInRegion(StoryRegion region) {
        StoryProgress progress = new StoryProgress();
        progress.reachRegion(region);
        return newSystem(progress);
    }

    /** Asks for a moment's exchange and puts it on screen, the way {@code World} does. */
    private static boolean openFor(ExchangeSystem system, ExchangeTrigger trigger) {
        return system.request(trigger) && system.openPending();
    }

    private static void advance(ExchangeSystem system, float seconds) {
        int frames = Math.max(1, Math.round(seconds / FRAME_SECONDS));
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            system.update(FRAME_SECONDS);
        }
    }

    /** Answers the option at {@code optionIndex} and rides the reply out to the end. */
    private static void answerAndFinish(ExchangeSystem system, int optionIndex) {
        system.selectOption(optionIndex);
        if (system.isShowingReply()) system.requestContinue();
        advance(system, StoryUiConstants.STORY_EXCHANGE_FADE_OUT_SECONDS + 0.1f);
    }

    // -------------------------------------------------------------------------
    // Catalog integrity — every line exists, is readable, and stays inside its budget
    // -------------------------------------------------------------------------

    @Test
    void everyCatalogLineResolvesToRealText() {
        StoryStrings strings = StoryStrings.defaults();
        for (ExchangeDefinition exchange : ExchangeCatalog.defaultRegistry().getAll()) {
            assertResolves(strings, exchange.getPromptStringId(), exchange.getId());
            for (ExchangeOption option : exchange.getOptions()) {
                assertResolves(strings, option.getPlayerLineStringId(),
                        exchange.getId() + "/" + option.getId());
                if (option.getReplyStringId() != null) {
                    assertResolves(strings, option.getReplyStringId(),
                            exchange.getId() + "/" + option.getId() + " reply");
                }
            }
        }
        // The acknowledge button label is a localised id too — never a literal in the renderer.
        assertTrue(strings.has(StoryUiConstants.STORY_EXCHANGE_CONTINUE_LABEL_ID));
    }

    private static void assertResolves(StoryStrings strings, String stringId, String where) {
        assertTrue(strings.has(stringId), where + " has no text for id " + stringId);
        assertFalse(strings.get(stringId).trim().isEmpty(), where + " resolves to blank text");
    }

    /** The shipped asset is the real table; the Java defaults are only its fallback. */
    @Test
    void shippedStringAssetCoversEveryExchangeLine() throws IOException {
        File assetFile = new File("../assets/" + StoryUiConstants.STORY_STRINGS_ASSET_PATH);
        assumeTrue(assetFile.exists(), "string asset not reachable from the test working directory");
        StoryStrings shipped = StoryStrings.fromProperties(
                new String(Files.readAllBytes(assetFile.toPath()), StandardCharsets.UTF_8));
        StoryStrings defaults = StoryStrings.defaults();

        for (ExchangeDefinition exchange : ExchangeCatalog.defaultRegistry().getAll()) {
            assertAssetMatches(shipped, defaults, exchange.getPromptStringId());
            for (ExchangeOption option : exchange.getOptions()) {
                assertAssetMatches(shipped, defaults, option.getPlayerLineStringId());
                if (option.getReplyStringId() != null) {
                    assertAssetMatches(shipped, defaults, option.getReplyStringId());
                }
            }
        }
        assertAssetMatches(shipped, defaults, StoryUiConstants.STORY_EXCHANGE_CONTINUE_LABEL_ID);
    }

    private static void assertAssetMatches(StoryStrings shipped, StoryStrings defaults, String stringId) {
        assertTrue(shipped.has(stringId), "story-strings.properties is missing " + stringId);
        assertEquals(defaults.get(stringId), shipped.get(stringId),
                "asset and ExchangeStrings disagree on " + stringId);
    }

    /** A prompt that overflows its panel is unreadable exactly when the player must decide. */
    @Test
    void everyPromptFitsThePanelsLineCap() {
        StoryStrings strings = StoryStrings.defaults();
        for (ExchangeDefinition exchange : ExchangeCatalog.defaultRegistry().getAll()) {
            assertWrapsWithin(strings, exchange.getPromptStringId(), exchange.getId() + " prompt");
            for (ExchangeOption option : exchange.getOptions()) {
                if (option.getReplyStringId() == null) continue;
                assertWrapsWithin(strings, option.getReplyStringId(),
                        exchange.getId() + "/" + option.getId() + " reply");
            }
        }
    }

    private static void assertWrapsWithin(StoryStrings strings, String stringId, String where) {
        List<String> wrapped = StoryText.wrapToMaxChars(strings.get(stringId),
                StoryUiConstants.STORY_LINE_MAX_CHARS);
        assertTrue(wrapped.size() <= StoryUiConstants.STORY_EXCHANGE_MAX_LINES,
                where + " wraps to " + wrapped.size() + " lines: " + strings.get(stringId));
    }

    /**
     * Answers must be ONE short line and DISTINCT at a glance.  Three shades of the same "yes" is
     * the same as having no choice at all, which is the one thing this layer cannot afford.
     */
    @Test
    void everyAnswerIsOneShortDistinctLine() {
        StoryStrings strings = StoryStrings.defaults();
        for (ExchangeDefinition exchange : ExchangeCatalog.defaultRegistry().getAll()) {
            Set<String> seenAnswers = new HashSet<>();
            for (ExchangeOption option : exchange.getOptions()) {
                String text = strings.get(option.getPlayerLineStringId());
                assertTrue(text.length() <= StoryUiConstants.STORY_EXCHANGE_OPTION_MAX_CHARS,
                        "answer over the one-line cap (" + text.length() + "): " + text);
                assertTrue(seenAnswers.add(text),
                        exchange.getId() + " offers the same answer twice: " + text);
            }
        }
    }

    @Test
    void everyExchangeOffersTwoOrThreeAnswers() {
        for (ExchangeDefinition exchange : ExchangeCatalog.defaultRegistry().getAll()) {
            assertTrue(exchange.getOptionCount() >= StoryUiConstants.STORY_EXCHANGE_MIN_OPTIONS
                    && exchange.getOptionCount() <= StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS,
                    exchange.getId() + " offers " + exchange.getOptionCount() + " answers");
        }
    }

    /** The rarity budget, as a test: an exchange stops the world, so there must not be many. */
    @Test
    void theCatalogStaysRare() {
        ExchangeRegistry registry = ExchangeCatalog.defaultRegistry();
        assertTrue(registry.size() <= EXCHANGE_BUDGET,
                "the exchange catalog has grown to " + registry.size()
                        + "; cut rows rather than raising the budget");
        for (ExchangeDefinition exchange : registry.getAll()) {
            assertTrue(exchange.isOneShot(),
                    exchange.getId() + " is repeatable; exchanges must be one-shot to stay special");
        }
    }

    /** Every region entry carries exactly one deliberate beat — no region is silent, none doubles up. */
    @Test
    void everyRegionHasExactlyOneRegionEntryExchange() {
        for (StoryRegion region : StoryRegion.values()) {
            int matching = 0;
            for (ExchangeDefinition exchange
                    : ExchangeCatalog.defaultRegistry().getForTrigger(ExchangeTrigger.REGION_ENTERED)) {
                if (exchange.matchesRegion(region)) matching++;
            }
            assertEquals(1, matching, "region " + region + " has " + matching + " entry exchanges");
        }
    }

    /** A reward is granted by the engine through {@code ItemType.valueOf} — a typo must not ship. */
    @Test
    void everyRewardNamesARealItemType() {
        for (ExchangeDefinition exchange : ExchangeCatalog.defaultRegistry().getAll()) {
            for (ExchangeOption option : exchange.getOptions()) {
                String itemTypeName = option.getRewardItemTypeName();
                if (itemTypeName == null) continue;
                assertNotNull(ItemType.valueOf(itemTypeName),
                        exchange.getId() + " rewards unknown item " + itemTypeName);
                assertTrue(option.getRewardQuantity() > 0);
                assertEquals(ExchangeOptionKind.PROBE, option.getKind(),
                        "rewards pay CURIOSITY; only a probe should carry one");
            }
        }
    }

    /** Every chained follow-up must actually exist, or the chain silently ends the conversation. */
    @Test
    void everyChainedExchangeResolves() {
        ExchangeRegistry registry = ExchangeCatalog.defaultRegistry();
        for (ExchangeDefinition exchange : registry.getAll()) {
            for (ExchangeOption option : exchange.getOptions()) {
                String nextId = option.getNextExchangeId();
                if (nextId == null) continue;
                assertNotNull(registry.getById(nextId),
                        exchange.getId() + " chains to unknown exchange " + nextId);
            }
        }
    }

    // -------------------------------------------------------------------------
    // The exchange blocks, and never takes the answer out of the player's hands
    // -------------------------------------------------------------------------

    @Test
    void anExchangeIsHeldPendingUntilTheWorldIsReadyToStop() {
        ExchangeSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        assertTrue(system.request(ExchangeTrigger.REGION_ENTERED));
        assertTrue(system.hasPending(), "the exchange did not wait for the world");
        assertFalse(system.isActive(), "the exchange opened mid-transition");

        assertTrue(system.openPending());
        assertTrue(system.isActive());
        assertFalse(system.hasPending());
    }

    @Test
    void theExchangeNeverAdvancesOnItsOwn() {
        ExchangeSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        assertTrue(openFor(system, ExchangeTrigger.REGION_ENTERED));
        advance(system, 120f);   // two minutes of the player thinking about it, or making tea

        assertTrue(system.isActive(), "the exchange closed on its own");
        assertTrue(system.isAwaitingChoice(), "an answer was taken out of the player's hands");
        assertFalse(system.isFinished());
        assertEquals(1f, system.getVisibleFraction(), 0.001f);
    }

    @Test
    void thereIsNoWayPastThePromptWithoutAnswering() {
        ExchangeSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        openFor(system, ExchangeTrigger.REGION_ENTERED);

        assertFalse(system.requestContinue(), "the prompt offered a blank 'next'");
        assertFalse(system.selectOption(-1), "an out-of-range tap answered something");
        assertFalse(system.selectOption(StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS + 1));
        assertTrue(system.isAwaitingChoice(), "the prompt was dismissed without an answer");
    }

    @Test
    void answeringShowsTheReplyAndThenReturnsControl() {
        ExchangeSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        openFor(system, ExchangeTrigger.REGION_ENTERED);
        assertTrue(system.getOptionCount() >= StoryUiConstants.STORY_EXCHANGE_MIN_OPTIONS);

        assertTrue(system.selectOption(0));
        assertTrue(system.isShowingReply(), "the answer produced no reply");
        assertEquals(0, system.getOptionCount(), "the answer buttons outlived the choice");
        assertTrue(system.getBodyLineCount() > 0, "the reply was blank");

        // Still no timer: the reply waits for the player exactly like the prompt did.
        advance(system, 60f);
        assertTrue(system.isShowingReply());

        assertTrue(system.requestContinue());
        advance(system, StoryUiConstants.STORY_EXCHANGE_FADE_OUT_SECONDS + 0.1f);
        assertFalse(system.isActive(), "the exchange never let go of the screen");
        assertTrue(system.consumeFinished(), "the world was never told it may resume");
        assertFalse(system.consumeFinished(), "a stale finished flag would skip the next exchange");
    }

    @Test
    void theOrganizationSpeaksInItsOwnAllCapsVoiceAndThePlayerDoesNot() {
        ExchangeSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        openFor(system, ExchangeTrigger.REGION_ENTERED);
        assertEquals(Speaker.ORGANIZATION, system.getActiveSpeaker());

        String promptLine = system.getBodyLines()[0];
        assertEquals(promptLine.toUpperCase(java.util.Locale.ROOT), promptLine,
                "the Organization's prompt lost its ALL-CAPS styling");
        // The player's answers are their own voice — never the Organization's, even when obeying.
        String obedientAnswer = system.getOptionLines()[0];
        assertNotEquals(obedientAnswer.toUpperCase(java.util.Locale.ROOT), obedientAnswer,
                "the player's answer was shouted in the Organization's voice");
    }

    // -------------------------------------------------------------------------
    // Region gating and one-shot behaviour — the roguelike story rules
    // -------------------------------------------------------------------------

    @Test
    void theRegionEntryBeatIsGatedByTheDeepestRegionReached() {
        assertEquals("exchange.rings.briefing",     openedIdFor(StoryRegion.HABITATION_RINGS));
        assertEquals("exchange.galleries.ledger",   openedIdFor(StoryRegion.HARVESTING_GALLERIES));
        assertEquals("exchange.reliquary.log",      openedIdFor(StoryRegion.RELIQUARY));
        assertEquals("exchange.wound.voice",        openedIdFor(StoryRegion.WOUND));
        assertEquals("exchange.core.order",         openedIdFor(StoryRegion.CORE));
    }

    private static String openedIdFor(StoryRegion region) {
        ExchangeSystem system = newSystemInRegion(region);
        openFor(system, ExchangeTrigger.REGION_ENTERED);
        return system.getActiveExchangeId();
    }

    @Test
    void aOneShotExchangeNeverReAsksOnALaterRun() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress firstRun = new StoryProgress(store);
        firstRun.reachRegion(StoryRegion.HABITATION_RINGS);
        ExchangeSystem system = newSystem(firstRun);
        assertTrue(openFor(system, ExchangeTrigger.REGION_ENTERED));
        answerAndFinish(system, 0);

        // The player dies. A whole new run, a whole new World — and the same region entry.
        StoryProgress nextRun = new StoryProgress(store);
        ExchangeSystem afterDeath = newSystem(nextRun);
        assertFalse(afterDeath.request(ExchangeTrigger.REGION_ENTERED),
                "a one-shot exchange re-asked after a death");
    }

    @Test
    void theDeepCheckInsAreSilentBeforeOraHasDoubts() {
        ExchangeSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        assertFalse(system.request(ExchangeTrigger.DEEP_STRATA),
                "ORA asked a personal question before she had any reason to");
    }

    // -------------------------------------------------------------------------
    // The hidden stance model
    // -------------------------------------------------------------------------

    @Test
    void answersNudgeTheHiddenStanceAndItSurvivesDeath() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress progress = new StoryProgress(store);
        progress.reachRegion(StoryRegion.HABITATION_RINGS);
        ExchangeSystem system = newSystem(progress);
        openFor(system, ExchangeTrigger.REGION_ENTERED);

        assertEquals(0, progress.getStance(Stance.ORGANIZATION));
        answerAndFinish(system, 0);   // "Understood. Going down."
        assertEquals(1, progress.getStance(Stance.ORGANIZATION));
        assertEquals(Stance.ORGANIZATION, progress.getDominantStance());

        // A new run reloads who the player has been: a death wipes the body, not the character.
        assertEquals(1, new StoryProgress(store).getStance(Stance.ORGANIZATION));
    }

    @Test
    void aDominantStanceNeedsAClearLead() {
        StoryProgress progress = new StoryProgress();
        assertNull(progress.getDominantStance(), "an unexpressed player already had a leaning");

        progress.nudgeStance(Stance.PLANET, 2);
        progress.nudgeStance(Stance.ORA, 2);
        assertNull(progress.getDominantStance(), "a dead tie picked a side");

        progress.nudgeStance(Stance.ORA, 1);
        assertEquals(Stance.ORA, progress.getDominantStance());
    }

    @Test
    void stanceFlavoursWhichDeepCheckInOpens() {
        assertEquals("exchange.deep.warm", deepCheckInFor(Stance.ORA));
        assertEquals("exchange.deep.duty", deepCheckInFor(Stance.ORGANIZATION));
    }

    private static String deepCheckInFor(Stance leaning) {
        StoryProgress progress = new StoryProgress();
        progress.reachRegion(StoryRegion.RELIQUARY);
        progress.nudgeStance(leaning, 3);
        ExchangeSystem system = newSystem(progress);
        openFor(system, ExchangeTrigger.DEEP_STRATA);
        return system.getActiveExchangeId();
    }

    /**
     * Stance may FLAVOUR, never GATE.  A player leaning a way no row was written for must still get
     * the moment — the neutral fallback is what guarantees it.
     */
    @Test
    void aStanceCanNeverSilenceAMoment() {
        StoryProgress progress = new StoryProgress();
        progress.reachRegion(StoryRegion.RELIQUARY);
        progress.nudgeStance(Stance.PLANET, 5);   // no deep check-in is written for the planet
        ExchangeSystem system = newSystem(progress);

        assertTrue(openFor(system, ExchangeTrigger.DEEP_STRATA),
                "a leaning with no matching row silenced the moment");
        assertEquals("exchange.deep.quiet", system.getActiveExchangeId(),
                "the neutral fallback was not preferred over a row written for someone else");
    }

    // -------------------------------------------------------------------------
    // Consequential outcomes, codex unlocks and rewards
    // -------------------------------------------------------------------------

    @Test
    void aConsequentialAnswerIsRecordedPermanently() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress progress = new StoryProgress(store);
        progress.reachRegion(StoryRegion.RELIQUARY);
        ExchangeSystem system = newSystem(progress);
        assertTrue(openFor(system, ExchangeTrigger.REGION_ENTERED));
        assertEquals("exchange.reliquary.log", system.getActiveExchangeId());
        assertFalse(progress.hasOutcome("exchange.reliquary.log"));

        answerAndFinish(system, 0);   // "Delete it. Stay safe."
        assertEquals("delete", progress.getOutcome("exchange.reliquary.log"));
        // ...and it is still there in the next run, which is what makes it consequential.
        assertEquals("delete", new StoryProgress(store).getOutcome("exchange.reliquary.log"));
    }

    @Test
    void theOtherAnswerRecordsTheOtherOutcome() {
        StoryProgress progress = new StoryProgress();
        progress.reachRegion(StoryRegion.RELIQUARY);
        ExchangeSystem system = newSystem(progress);
        openFor(system, ExchangeTrigger.REGION_ENTERED);
        answerAndFinish(system, 1);   // "Leave it. Let them read it."
        assertEquals("keep", progress.getOutcome("exchange.reliquary.log"));
    }

    @Test
    void aProbeThatChainsFilesItsAnswerUnderTheSameBeat() {
        StoryProgress progress = new StoryProgress();
        progress.reachRegion(StoryRegion.RELIQUARY);
        ExchangeSystem system = newSystem(progress);
        openFor(system, ExchangeTrigger.REGION_ENTERED);

        // Ask what it says first — the probe answers, then hands the same decision back.
        assertTrue(system.selectOption(2));
        assertTrue(system.isShowingReply());
        assertTrue(progress.isCodexUnlocked("codex.soul.reserve"),
                "the probe did not pay curiosity with a codex entry");

        system.requestContinue();
        assertTrue(system.isActive(), "the chained follow-up never opened");
        assertEquals("exchange.reliquary.log.decide", system.getActiveExchangeId());
        assertTrue(system.isAwaitingChoice());

        answerAndFinish(system, 0);   // "Delete it."
        assertEquals("delete", progress.getOutcome("exchange.reliquary.log"),
                "the chained answer was filed under the wrong beat");
    }

    @Test
    void aProbeRevealsItsRewardExactlyOnce() {
        StoryProgress progress = new StoryProgress();
        progress.reachRegion(StoryRegion.HARVESTING_GALLERIES);
        ExchangeSystem system = newSystem(progress);
        openFor(system, ExchangeTrigger.REGION_ENTERED);
        assertEquals("exchange.galleries.ledger", system.getActiveExchangeId());

        assertNull(system.consumePendingRewardItemTypeName(), "a reward appeared before the answer");
        system.selectOption(0);   // "Read it to me."

        assertEquals(1, system.getPendingRewardQuantity());
        assertEquals("MEDKIT_SMALL", system.consumePendingRewardItemTypeName());
        assertNull(system.consumePendingRewardItemTypeName(), "the reward could be granted twice");
        assertTrue(progress.isCodexUnlocked("codex.yield.ledger"));
    }

    @Test
    void clearingTheExchangeLeavesNothingBehind() {
        ExchangeSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        openFor(system, ExchangeTrigger.REGION_ENTERED);
        system.selectOption(0);
        system.clear();

        assertFalse(system.isActive());
        assertFalse(system.hasPending());
        assertFalse(system.isFinished());
        assertEquals(0, system.getOptionCount());
        assertEquals(0, system.getBodyLineCount());
        assertNull(system.consumePendingRewardItemTypeName());
    }

    @Test
    void aPressedAnswerIsTrackedSoATapCanBeCancelled() {
        ExchangeSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        openFor(system, ExchangeTrigger.REGION_ENTERED);

        system.setPressedOptionIndex(1);
        assertEquals(1, system.getPressedOptionIndex());
        system.setPressedOptionIndex(-1);            // the finger slid off the plate
        assertEquals(-1, system.getPressedOptionIndex());
        assertTrue(system.isAwaitingChoice(), "a cancelled tap answered anyway");
    }

    // -------------------------------------------------------------------------
    // Layout — the stack fits the screen and every plate is a thumb target
    // -------------------------------------------------------------------------

    @Test
    void theStackFitsOnScreenAndNeverOverlapsItself() {
        assertTrue(StoryUiConstants.STORY_EXCHANGE_PROMPT_TOP_Y <= Constants.WORLD_HEIGHT,
                "the prompt panel runs off the top of the screen");
        assertTrue(StoryUiConstants.STORY_EXCHANGE_X >= 0f);
        assertTrue(StoryUiConstants.STORY_EXCHANGE_X + StoryUiConstants.STORY_EXCHANGE_WIDTH
                <= Constants.WORLD_WIDTH, "the exchange is wider than the screen");

        // Top-down stack: prompt panel, then the buttons, all clear of the HUD band so health and
        // ammo stay readable while the player decides.
        assertTrue(StoryUiConstants.STORY_EXCHANGE_BUTTONS_TOP_Y
                        <= StoryUiConstants.STORY_EXCHANGE_PROMPT_Y,
                "the first answer overlaps the prompt panel");
        assertTrue(StoryUiConstants.STORY_EXCHANGE_BUTTONS_BOTTOM_Y >= HudConstants.HUD_HEIGHT,
                "the answer stack overlaps the HUD band");
        assertEquals(StoryUiConstants.STORY_EXCHANGE_BUTTONS_BOTTOM_Y,
                StoryUiConstants.STORY_EXCHANGE_Y, 0.001f);
    }

    @Test
    void everyPlateIsAThumbTargetAndThePlatesNeverTouch() {
        assertTrue(StoryUiConstants.STORY_EXCHANGE_BUTTON_HEIGHT
                        >= StoryUiConstants.STORY_CHOICE_BUTTON_MIN_HEIGHT,
                "an answer plate is shorter than the minimum thumb target");
        assertTrue(StoryUiConstants.STORY_CHOICE_BUTTON_GAP > 0f,
                "adjacent answers with no gap invite a mis-tap");
        assertTrue(StoryUiConstants.STORY_EXCHANGE_BUTTON_X >= StoryUiConstants.STORY_EXCHANGE_X);
        assertTrue(StoryUiConstants.STORY_EXCHANGE_BUTTON_X
                        + StoryUiConstants.STORY_CHOICE_BUTTON_WIDTH
                        <= StoryUiConstants.STORY_EXCHANGE_X + StoryUiConstants.STORY_EXCHANGE_WIDTH,
                "an answer plate is wider than the exchange");
    }

    /** The stack geometry the renderer draws with and the hit test reads must be one formula. */
    @Test
    void theButtonStackWalksDownwardByOnePitchPerSlot() {
        float top    = StoryUiConstants.STORY_EXCHANGE_BUTTONS_TOP_Y;
        float height = StoryUiConstants.STORY_EXCHANGE_BUTTON_HEIGHT;
        float gap    = StoryUiConstants.STORY_CHOICE_BUTTON_GAP;

        assertEquals(top - height, GameMath.stackedButtonBottomY(top, height, gap, 0), 0.001f);
        assertEquals(top - height - (height + gap),
                GameMath.stackedButtonBottomY(top, height, gap, 1), 0.001f);
        // A negative slot clamps to the first rather than drawing above the stack.
        assertEquals(GameMath.stackedButtonBottomY(top, height, gap, 0),
                GameMath.stackedButtonBottomY(top, height, gap, -3), 0.001f);
        // The last slot lands exactly on the stack's declared floor.
        assertEquals(StoryUiConstants.STORY_EXCHANGE_BUTTONS_BOTTOM_Y,
                GameMath.stackedButtonBottomY(top, height, gap,
                        StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS - 1), 0.001f);
    }

    /** The prompt panel is sized by the same rule every other story panel uses: 30 * lines + 70. */
    @Test
    void thePromptPanelIsSizedForItsLineCount() {
        assertEquals(30f * StoryUiConstants.STORY_EXCHANGE_MAX_LINES + 70f,
                StoryUiConstants.STORY_EXCHANGE_PROMPT_HEIGHT, 0.001f);
    }
}

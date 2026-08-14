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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Headless guards for the Story UI order-6 wrap-up: the CODEX archives what was actually said and
 * nothing else, unlocks are permanent across deaths, NEW dots invite once and then stop, category
 * completion is cosmetic and one-shot, the body scrolls only over real content, and the
 * accessibility settings both persist and take effect.
 *
 * <p>Also guards order-6 Part B's pacing budget (nothing non-critical lands during a combat spike,
 * and a line held through one is not lost) and Part E's tuning counters.
 *
 * <p>Everything here runs without a LibGDX context, which is the point of keeping the archive's
 * brain in {@code …toon3d.narrative}.
 */
class StoryCodexTest {

    private static final float FRAME_SECONDS = 1f / 60f;

    /**
     * The recap page (narrative-rework order-9 B), which sits at index 0 of EVERY tab and is never
     * locked.  Every count of "what the player has recovered" below is offset past it.
     */
    private static final int PINNED_ROWS = 1;

    private static CodexSystem newCodex(StoryProgress progress) {
        return new CodexSystem(CodexCatalog.defaultRegistry(), StoryStrings.defaults(),
                               progress, new StorySettings());
    }

    // -------------------------------------------------------------------------
    // Catalog integrity — every entry is real, readable and reachable
    // -------------------------------------------------------------------------

    @Test
    void everyEntryResolvesToRealTitleSourceAndBody() {
        StoryStrings strings = StoryStrings.defaults();
        for (CodexEntry entry : CodexCatalog.defaultRegistry().getAll()) {
            for (String stringId : new String[] { entry.getTitleStringId(),
                                                  entry.getSourceStringId(),
                                                  entry.getBodyStringId() }) {
                assertTrue(strings.has(stringId),
                        "codex entry " + entry.getId() + " has no text for id " + stringId);
                assertFalse(strings.get(stringId).trim().isEmpty(),
                        "codex entry " + entry.getId() + " resolves to blank text for " + stringId);
            }
            if (entry.getAiTakeStringId() != null) {
                assertTrue(strings.has(entry.getAiTakeStringId()),
                        "codex entry " + entry.getId() + " has no take text");
            }
        }
    }

    /**
     * Every codex id a PROBE answer pays out must exist as an entry, or a player who spent an
     * exchange on curiosity gets an unlock that opens nothing.
     */
    @Test
    void everyExchangeCodexRewardHasAnEntry() {
        CodexRegistry codex = CodexCatalog.defaultRegistry();
        for (ExchangeDefinition exchange : ExchangeCatalog.defaultRegistry().getAll()) {
            for (int optionIndex = 0; optionIndex < exchange.getOptionCount(); optionIndex++) {
                String codexId = exchange.getOption(optionIndex).getUnlocksCodexId();
                if (codexId == null) continue;
                assertNotNull(codex.getById(codexId),
                        "exchange " + exchange.getId() + " unlocks unknown codex entry " + codexId);
            }
        }
    }

    /**
     * Every entry must be REACHABLE: either a line archives it, or a probe pays for it.  An entry
     * with neither is content nobody can ever read.
     *
     * <p>The COMPOSED pages (narrative-rework order-9) are the exception, and they are exempt for the
     * reason the rule exists rather than in spite of it: they are not archived by anything because
     * they are not archives of anything.  The recap is always unlocked, and the missed-beat shelf is
     * unlocked by the engine the first time a mandatory line is closed unread — a moment no catalog
     * row could ever name.
     */
    @Test
    void everyEntryIsReachableBySomething() {
        Set<String> probeUnlockedIds = new HashSet<>();
        for (ExchangeDefinition exchange : ExchangeCatalog.defaultRegistry().getAll()) {
            for (int optionIndex = 0; optionIndex < exchange.getOptionCount(); optionIndex++) {
                String codexId = exchange.getOption(optionIndex).getUnlocksCodexId();
                if (codexId != null) probeUnlockedIds.add(codexId);
            }
        }
        for (CodexEntry entry : CodexCatalog.defaultRegistry().getAll()) {
            if (entry.isComposed()) continue;
            boolean reachable = !entry.getUnlockLineIds().isEmpty()
                    || probeUnlockedIds.contains(entry.getId());
            assertTrue(reachable, "codex entry " + entry.getId() + " can never be unlocked");
        }
    }

    /**
     * THE PINNED PAGE (narrative-rework order-9 B): exactly one entry sits above the open tab's own
     * rows, it is never locked, and it is composed from live state.  All three properties together —
     * a pinned entry that had to be earned would be useless to the player it exists for, and a
     * pinned entry with an authored body would be a page that stops being true.
     */
    @Test
    void exactlyOnePageIsPinnedAndItIsNeverLocked() {
        int pinnedCount = 0;
        for (CodexEntry entry : CodexCatalog.defaultRegistry().getAll()) {
            if (!entry.isPinned()) continue;
            pinnedCount++;
            assertTrue(entry.isAlwaysUnlocked(), entry.getId() + " is pinned but can be locked");
            assertTrue(entry.isComposed(), entry.getId() + " is pinned but is not composed");
        }
        assertEquals(1, pinnedCount, "the archive pins exactly one page");
    }

    /** Every line an entry hangs off must be a real catalog row, or the archive waits on nothing. */
    @Test
    void everyUnlockLineIsARealCatalogRow() {
        BarkRegistry     barks     = BarkCatalog.defaultRegistry();
        ExchangeRegistry exchanges = ExchangeCatalog.defaultRegistry();
        for (CodexEntry entry : CodexCatalog.defaultRegistry().getAll()) {
            List<String> lineIds = entry.getUnlockLineIds();
            for (int lineIndex = 0; lineIndex < lineIds.size(); lineIndex++) {
                String lineId = lineIds.get(lineIndex);
                boolean known = barks.getById(lineId) != null || exchanges.getById(lineId) != null;
                assertTrue(known, "codex entry " + entry.getId()
                        + " waits on unknown line id " + lineId);
            }
        }
    }

    @Test
    void everyCategoryHasEntries() {
        CodexRegistry registry = CodexCatalog.defaultRegistry();
        for (CodexCategory category : CodexCategory.values()) {
            assertFalse(registry.getForCategory(category).isEmpty(),
                    "no codex entries filed under " + category);
        }
    }

    /** A tab reads top-to-bottom as a descent: entries never jump back up a region. */
    @Test
    void aTabIsOrderedShallowestRegionFirst() {
        CodexRegistry registry = CodexCatalog.defaultRegistry();
        for (CodexCategory category : CodexCategory.values()) {
            List<CodexEntry> entries = registry.getForCategory(category);
            for (int entryIndex = 1; entryIndex < entries.size(); entryIndex++) {
                assertTrue(entries.get(entryIndex - 1).getRegion().ordinal()
                                <= entries.get(entryIndex).getRegion().ordinal(),
                        category + " is not ordered by region depth");
            }
        }
    }

    /** The shipped asset is the real table; the Java defaults are only its fallback. */
    @Test
    void shippedStringAssetCoversEveryCodexEntry() throws IOException {
        File assetFile = new File("../assets/" + StoryUiConstants.STORY_STRINGS_ASSET_PATH);
        assumeTrue(assetFile.exists(), "string asset not reachable from the test working directory");
        StoryStrings shipped = StoryStrings.fromProperties(
                new String(Files.readAllBytes(assetFile.toPath()), StandardCharsets.UTF_8));
        StoryStrings defaults = StoryStrings.defaults();
        for (CodexEntry entry : CodexCatalog.defaultRegistry().getAll()) {
            for (String stringId : new String[] { entry.getTitleStringId(),
                                                  entry.getSourceStringId(),
                                                  entry.getBodyStringId() }) {
                assertTrue(shipped.has(stringId),
                        "story-strings.properties is missing " + stringId);
                assertEquals(defaults.get(stringId), shipped.get(stringId),
                        "asset and CodexStrings disagree on " + stringId);
            }
        }
        for (CodexCategory category : CodexCategory.values()) {
            assertTrue(shipped.has(category.getTitleStringId()),
                    "story-strings.properties is missing " + category.getTitleStringId());
        }
    }

    // -------------------------------------------------------------------------
    // The archive holds what was SAID — and unlocks are permanent
    // -------------------------------------------------------------------------

    @Test
    void aSpokenLineArchivesItsEntry() {
        StoryProgress progress = new StoryProgress();
        CodexSystem   codex    = newCodex(progress);
        CodexEntry    roster   = CodexCatalog.defaultRegistry().getById("codex.log.rings.roster");

        assertFalse(codex.isUnlocked(roster), "the archive starts empty");
        assertTrue(codex.noteLineSpoken("bark.log.rings.1"), "the take should archive its log");
        assertTrue(codex.isUnlocked(roster));
        assertFalse(codex.noteLineSpoken("bark.log.rings.1"), "a repeat unlocks nothing new");
    }

    @Test
    void aLineNobodyHeardArchivesNothing() {
        StoryProgress progress = new StoryProgress();
        CodexSystem   codex    = newCodex(progress);
        assertFalse(codex.noteLineSpoken("bark.does.not.exist"));
        assertFalse(codex.noteLineSpoken(null));
        codex.selectCategory(CodexCategory.LOGS);
        assertEquals(0, recoveredEntryCount(codex), "an unheard line must list no entry");
    }

    /**
     * How many RECOVERED entries a tab lists, ignoring the page pinned above them (narrative-rework
     * order-9 B).  The pinned recap is on every tab and is never locked, so it is present in every
     * count below; what these tests are about is what the player actually earned.
     */
    private static int recoveredEntryCount(CodexSystem codex) {
        return codex.getVisibleEntryCount() - PINNED_ROWS;
    }

    /** Death never takes an entry back — the same rule that keeps the deepest region from rewinding. */
    @Test
    void unlocksSurviveDeath() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        CodexSystem beforeDeath = newCodex(new StoryProgress(store));
        beforeDeath.noteLineSpoken("bark.log.rings.1");

        CodexSystem afterReprint = newCodex(new StoryProgress(store));
        afterReprint.selectCategory(CodexCategory.LOGS);
        assertEquals(1, recoveredEntryCount(afterReprint),
                "a reprint must not empty the archive");
    }

    /** A probe answered in an exchange lands in the same archive, with no bark involved. */
    @Test
    void aProbeRewardShowsUpInTheArchive() {
        StoryProgress progress = new StoryProgress();
        CodexSystem   codex    = newCodex(progress);
        progress.unlockCodexEntry("codex.planet.name");
        codex.refresh();
        codex.selectCategory(CodexCategory.PLANET);
        assertEquals(1, recoveredEntryCount(codex));
        assertEquals(StoryStrings.defaults().get("story.codex.planet.name.title"),
                     codex.getVisibleEntryTitle(PINNED_ROWS));
    }

    /** Locked entries are never listed: a tab shows what the player HAS, not a checklist. */
    @Test
    void lockedEntriesAreNotListed() {
        CodexSystem codex = newCodex(new StoryProgress());
        codex.selectCategory(CodexCategory.MEMORIES);
        assertEquals(0, recoveredEntryCount(codex));
        assertTrue(CodexCatalog.defaultRegistry().getForCategory(CodexCategory.MEMORIES).size() > 0,
                "the catalog is not empty — the LIST is");
    }

    // -------------------------------------------------------------------------
    // NEW dots, reading, and the cosmetic completion perk
    // -------------------------------------------------------------------------

    @Test
    void anUnreadEntryWearsANewDotUntilItIsOpened() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        CodexSystem codex = newCodex(new StoryProgress(store));
        codex.noteLineSpoken("bark.log.rings.1");
        codex.selectCategory(CodexCategory.LOGS);

        assertTrue(codex.isVisibleEntryNew(PINNED_ROWS), "a fresh entry invites a look");
        assertTrue(codex.openEntry(PINNED_ROWS));
        assertFalse(codex.isVisibleEntryNew(PINNED_ROWS), "opening it clears the dot");

        CodexSystem afterRestart = newCodex(new StoryProgress(store));
        afterRestart.selectCategory(CodexCategory.LOGS);
        assertFalse(afterRestart.isVisibleEntryNew(PINNED_ROWS), "the archive must never re-nag");
    }

    @Test
    void fillingACategoryReportsExactlyOneCompletionBeat() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress progress = new StoryProgress(store);
        CodexSystem   codex    = newCodex(progress);

        for (CodexEntry entry : CodexCatalog.defaultRegistry().getForCategory(CodexCategory.MEMORIES)) {
            progress.unlockCodexEntry(entry.getId());
        }
        codex.refresh();
        assertTrue(codex.isCategoryComplete(CodexCategory.MEMORIES));
        assertEquals(CodexCategory.MEMORIES, codex.consumePendingCompletion());
        assertNull(codex.consumePendingCompletion(), "a completion is reported once");

        // And never again, on any later run — the flag is persistent.
        CodexSystem laterRun = newCodex(new StoryProgress(store));
        laterRun.refresh();
        assertNull(laterRun.consumePendingCompletion(),
                "a filled category must not re-celebrate on a later run");
    }

    /** The perk is a bark and a mark — a line exists for every category and nothing else changes. */
    @Test
    void everyCategoryHasACompletionLine() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        StoryStrings strings  = StoryStrings.defaults();
        for (CodexCategory category : CodexCategory.values()) {
            BarkDefinition found = null;
            for (BarkDefinition row : registry.getForTrigger(BarkTrigger.CODEX_COMPLETE)) {
                if (row.matchesSubject(category.getCatalogKey())) found = row;
            }
            assertNotNull(found, "no completion line for " + category);
            assertTrue(found.isOneShot(), "a completion line must fire once, ever");
            assertTrue(strings.has(found.getTextStringId()));
        }
    }

    @Test
    void progressCountsTrackTheUnlockSet() {
        StoryProgress progress = new StoryProgress();
        CodexSystem   codex    = newCodex(progress);
        int total = codex.getEntryCount(CodexCategory.MEMORIES);

        assertEquals(0, codex.getUnlockedCount(CodexCategory.MEMORIES));
        assertFalse(codex.isCategoryComplete(CodexCategory.MEMORIES));
        codex.noteLineSpoken("bark.region.rings");
        assertEquals(1, codex.getUnlockedCount(CodexCategory.MEMORIES));
        assertTrue(total > 1, "MEMORIES fills across the descent, not in one go");
    }

    // -------------------------------------------------------------------------
    // Reading an entry: paragraphs, and scrolling that cannot run off the content
    // -------------------------------------------------------------------------

    @Test
    void anOpenEntryBuildsTitleSourceTakeAndBodyLines() {
        CodexSystem codex = newCodex(new StoryProgress());
        codex.noteLineSpoken("bark.log.rings.1");
        codex.selectCategory(CodexCategory.LOGS);
        assertTrue(codex.openEntry(PINNED_ROWS));
        assertTrue(codex.isShowingEntry());

        boolean sawTitle = false, sawSource = false, sawTake = false, sawBody = false;
        for (int lineIndex = 0; lineIndex < codex.getDetailLineCount(); lineIndex++) {
            switch (codex.getDetailLineStyle(lineIndex)) {
                case TITLE:  sawTitle  = true; break;
                case SOURCE: sawSource = true; break;
                case TAKE:   sawTake   = true; break;
                case BODY:   sawBody   = true; break;
                default: break;
            }
        }
        assertTrue(sawTitle && sawSource && sawBody, "an entry prints its title, source and text");
        assertTrue(sawTake, "a log reprints the line ORA already said about it");
    }

    /** No line of the archive may run wider than the wrap cap, at any accessibility text size. */
    @Test
    void everyDetailLineFitsTheWrapCap() {
        for (StoryTextSize size : StoryTextSize.values()) {
            StorySettings settings = new StorySettings();
            settings.setTextSize(size);
            StoryProgress progress = new StoryProgress();
            CodexSystem codex = new CodexSystem(CodexCatalog.defaultRegistry(),
                                                StoryStrings.defaults(), progress, settings);
            for (CodexEntry entry : CodexCatalog.defaultRegistry().getAll()) {
                progress.unlockCodexEntry(entry.getId());
            }
            codex.refresh();
            for (CodexCategory category : CodexCategory.values()) {
                codex.selectCategory(category);
                for (int rowIndex = 0; rowIndex < codex.getVisibleEntryCount(); rowIndex++) {
                    codex.openEntry(rowIndex);
                    for (int lineIndex = 0; lineIndex < codex.getDetailLineCount(); lineIndex++) {
                        assertTrue(codex.getDetailLine(lineIndex).length()
                                        <= settings.getCodexLineMaxChars(),
                                "a line ran past the wrap cap at " + size + ": "
                                        + codex.getDetailLine(lineIndex));
                    }
                    codex.closeEntry();
                }
            }
        }
    }

    @Test
    void aBodySeparatorBecomesAParagraphBreak() {
        CodexSystem codex = newCodex(new StoryProgress());
        codex.noteLineSpoken("bark.log.rings.1");
        codex.selectCategory(CodexCategory.LOGS);
        codex.openEntry(PINNED_ROWS);
        int blankCount = 0;
        for (int lineIndex = 0; lineIndex < codex.getDetailLineCount(); lineIndex++) {
            if (codex.getDetailLineStyle(lineIndex) == CodexSystem.DetailLineStyle.BLANK) blankCount++;
        }
        assertTrue(blankCount >= 2, "the entry's paragraphs must be separated");
        assertFalse(StoryStrings.defaults().get("story.codex.log.rings.roster.body")
                        .contains("\n"),
                "a body is one properties line; breaks are the separator, not newlines");
    }

    @Test
    void scrollingIsClampedToRealContent() {
        CodexSystem codex = newCodex(new StoryProgress());
        codex.noteLineSpoken("bark.log.rings.1");
        codex.selectCategory(CodexCategory.LOGS);

        // One row is far shorter than the body: there is nothing to scroll.
        codex.scrollBy(5000f);
        assertEquals(0f, codex.getScrollOffset(), 0.001f, "a short list cannot scroll at all");
        assertFalse(codex.isScrollable());

        codex.openEntry(PINNED_ROWS);
        codex.scrollBy(100000f);
        float maximum = Math.max(0f, codex.getContentHeight()
                - StoryUiConstants.STORY_CODEX_BODY_HEIGHT);
        assertEquals(maximum, codex.getScrollOffset(), 0.001f,
                "the body must stop at the end of the text");
        codex.scrollBy(-100000f);
        assertEquals(0f, codex.getScrollOffset(), 0.001f, "and at the start of it");
    }

    @Test
    void scrollClampFormulaHandlesTheDegenerateCases() {
        assertEquals(0f, GameMath.clampScrollOffset(50f, 100f, 400f), 0.001f);
        assertEquals(0f, GameMath.clampScrollOffset(-50f, 800f, 400f), 0.001f);
        assertEquals(400f, GameMath.clampScrollOffset(9999f, 800f, 400f), 0.001f);
        assertEquals(120f, GameMath.clampScrollOffset(120f, 800f, 400f), 0.001f);
        assertEquals(0f, GameMath.clampScrollOffset(Float.NaN, 800f, 400f), 0.001f);
    }

    @Test
    void openingATabResetsTheViewToTheTopOfIt() {
        StoryProgress progress = new StoryProgress();
        CodexSystem   codex    = newCodex(progress);
        for (CodexEntry entry : CodexCatalog.defaultRegistry().getAll()) {
            progress.unlockCodexEntry(entry.getId());
        }
        codex.refresh();
        codex.selectCategory(CodexCategory.LOGS);
        codex.scrollBy(200f);
        codex.selectCategory(CodexCategory.PEOPLE);
        assertEquals(0f, codex.getScrollOffset(), 0.001f);
        assertFalse(codex.isShowingEntry());
    }

    // -------------------------------------------------------------------------
    // The codex never interrupts anything, and never runs on a timer
    // -------------------------------------------------------------------------

    @Test
    void theArchiveOnlyOpensWhenAskedAndNeverClosesItself() {
        CodexSystem codex = newCodex(new StoryProgress());
        assertFalse(codex.isOpen(), "nothing opens the archive on its own");

        codex.open();
        for (int frame = 0; frame < 60 * 60; frame++) {   // a full minute of frames
            codex.update(FRAME_SECONDS);
        }
        assertTrue(codex.isOpen(), "the archive has no timer and must wait for the player");
        assertEquals(1f, codex.getVisibleFraction(), 0.001f);

        codex.close();
        assertFalse(codex.isOpen());
    }

    // -------------------------------------------------------------------------
    // PART B — the pacing budget
    // -------------------------------------------------------------------------

    /** A flavour line asked for mid-fight waits for the lull rather than landing over the fight. */
    @Test
    void aCombatSpikeHoldsANonCriticalLineUntilTheLull() {
        BarkSystem barks = new BarkSystem(BarkCatalog.defaultRegistry(), StoryStrings.defaults(),
                                          new StoryProgress(), 7L);
        advance(barks, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 1f);
        barks.setCombatSpike(true);
        assertTrue(barks.request(BarkTrigger.FLOOR_ARRIVAL), "the moment still queues");

        advance(barks, 5f);
        assertFalse(barks.hasActiveBark(), "nothing lands during a spike");
        assertEquals(1, barks.getQueuedCount(), "and the line is HELD, not dropped");

        barks.setCombatSpike(false);
        advance(barks, 1f);
        assertTrue(barks.hasActiveBark(), "it arrives in the lull right after");
    }

    /** A held line does not go stale while the fight is on — the moment has not passed, the player is busy. */
    @Test
    void aHeldLineDoesNotExpireDuringASpike() {
        BarkSystem barks = new BarkSystem(BarkCatalog.defaultRegistry(), StoryStrings.defaults(),
                                          new StoryProgress(), 11L);
        advance(barks, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 1f);
        barks.setCombatSpike(true);
        barks.request(BarkTrigger.FLOOR_ARRIVAL);

        advance(barks, StoryUiConstants.STORY_BARK_QUEUE_STALE_SECONDS * 3f);
        assertEquals(1, barks.getQueuedCount(), "a long fight must not silently eat the line");

        barks.setCombatSpike(false);
        advance(barks, 1f);
        assertTrue(barks.hasActiveBark());
    }

    /** A mandatory beat still lands mid-fight: a control hint the player needs NOW cannot wait. */
    @Test
    void aCriticalBeatStillLandsDuringASpike() {
        BarkSystem barks = new BarkSystem(BarkCatalog.defaultRegistry(), StoryStrings.defaults(),
                                          new StoryProgress(), 13L);
        advance(barks, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 1f);
        barks.setCombatSpike(true);
        assertTrue(barks.request(BarkTrigger.CONTROL_HINT, ControlHint.FIRE.getSubjectKey()));

        advance(barks, 1f);
        assertTrue(barks.hasActiveBark(), "a dropped hint is a stuck player");
        assertEquals(BarkPriority.STORY_CRITICAL,
                BarkCatalog.defaultRegistry().getById(barks.getActiveBarkId()).getPriority());
    }

    /** Only a line that actually reached the screen is reported as delivered. */
    @Test
    void onlyDeliveredLinesAreReportedForArchiving() {
        BarkSystem barks = new BarkSystem(BarkCatalog.defaultRegistry(), StoryStrings.defaults(),
                                          new StoryProgress(), 17L);
        assertNull(barks.consumeJustDeliveredBarkId(), "nothing said, nothing archived");

        advance(barks, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 1f);
        barks.request(BarkTrigger.FLOOR_ARRIVAL);
        advance(barks, 1f);

        String delivered = barks.consumeJustDeliveredBarkId();
        assertNotNull(delivered);
        assertEquals(delivered, barks.getActiveBarkId());
        assertNull(barks.consumeJustDeliveredBarkId(), "reading it clears it");
    }

    // -------------------------------------------------------------------------
    // PART D — accessibility settings
    // -------------------------------------------------------------------------

    @Test
    void settingsDefaultToTheComfortableEndOfEveryScale() {
        StorySettings settings = new StorySettings();
        assertEquals(StoryTextSize.NORMAL, settings.getTextSize());
        assertFalse(settings.isReduceTextHold(), "the paced reveal is the default mood");
        assertFalse(settings.isReduceMotion());
        assertEquals(StoryUiConstants.STORY_TEXT_SIZE, settings.getStoryTextScale(), 0.001f);
        assertEquals(StoryUiConstants.STORY_LINE_MAX_CHARS, settings.getPanelLineMaxChars());
    }

    @Test
    void settingsPersistAcrossAnAppRestart() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StorySettings before = new StorySettings(store);
        before.setTextSize(StoryTextSize.LARGEST);
        before.setReduceTextHold(true);
        before.setReduceMotion(true);

        StorySettings after = new StorySettings(store);
        assertEquals(StoryTextSize.LARGEST, after.getTextSize());
        assertTrue(after.isReduceTextHold());
        assertTrue(after.isReduceMotion());
    }

    /** Bigger text wraps SOONER — the "wrap, never shrink" rule, applied to the setting. */
    @Test
    void largerTextNarrowsTheWrapWidthRatherThanOverflowing() {
        StorySettings settings = new StorySettings();
        int normalChars = settings.getPanelLineMaxChars();
        float normalScale = settings.getStoryTextScale();

        settings.setTextSize(StoryTextSize.LARGEST);
        assertTrue(settings.getStoryTextScale() > normalScale, "the glyphs grow");
        assertTrue(settings.getPanelLineMaxChars() < normalChars, "so the line must hold fewer");
        assertTrue(settings.getCodexLineMaxChars() >= 1);
    }

    @Test
    void cyclingASettingWalksItsValuesAndComesBack() {
        StorySettings settings = new StorySettings();
        for (int cycle = 0; cycle < StoryTextSize.values().length; cycle++) {
            settings.cycleSetting(0);
        }
        assertEquals(StoryTextSize.NORMAL, settings.getTextSize(), "a full cycle returns home");

        settings.cycleSetting(1);
        assertTrue(settings.isReduceTextHold());
        settings.cycleSetting(1);
        assertFalse(settings.isReduceTextHold());
    }

    /** Every setting's label — name and current value — must resolve, or the strip reads as broken. */
    @Test
    void everySettingLabelResolves() {
        StoryStrings  strings  = StoryStrings.defaults();
        StorySettings settings = new StorySettings();
        for (int settingIndex = 0; settingIndex < StoryUiConstants.STORY_CODEX_SETTING_COUNT; settingIndex++) {
            assertTrue(strings.has(StorySettings.getSettingNameStringId(settingIndex)),
                    "no name for setting " + settingIndex);
            for (int cycle = 0; cycle < StoryTextSize.values().length; cycle++) {
                assertTrue(strings.has(settings.getSettingValueStringId(settingIndex)),
                        "no value label for setting " + settingIndex);
                settings.cycleSetting(settingIndex);
            }
        }
    }

    /** The size multiplier table must cover the enum, or a size would read off the end of it. */
    @Test
    void textSizeMultiplierTableMatchesTheEnum() {
        assertEquals(StoryTextSize.values().length,
                StoryUiConstants.STORY_TEXT_SIZE_MULTIPLIER.length,
                "multiplier table drifted from StoryTextSize");
        for (StoryTextSize size : StoryTextSize.values()) {
            assertTrue(size.getMultiplier() >= 1f, "no size may be SMALLER than the default");
        }
    }

    // -------------------------------------------------------------------------
    // Layout — the archive must fit the screen and stay clear of nothing it shouldn't
    // -------------------------------------------------------------------------

    @Test
    void theCodexFitsOnScreenAndItsBandsDoNotOverlap() {
        assertTrue(StoryUiConstants.STORY_CODEX_X >= 0f);
        assertTrue(StoryUiConstants.STORY_CODEX_TOP_Y <= ge.tbegvadze.toon3d.util.Constants.WORLD_HEIGHT);
        assertTrue(StoryUiConstants.STORY_CODEX_RIGHT_X <= ge.tbegvadze.toon3d.util.Constants.WORLD_WIDTH);

        assertTrue(StoryUiConstants.STORY_CODEX_BODY_TOP_Y > StoryUiConstants.STORY_CODEX_BODY_BOTTOM_Y,
                "the body must have height");
        assertTrue(StoryUiConstants.STORY_CODEX_BODY_BOTTOM_Y
                        > StoryUiConstants.STORY_CODEX_FOOTER_Y
                          + StoryUiConstants.STORY_CODEX_FOOTER_HEIGHT,
                "the body must clear the settings strip");
        assertTrue(StoryUiConstants.STORY_CODEX_BODY_TOP_Y < StoryUiConstants.STORY_CODEX_HEADER_Y,
                "the body must clear the header");
        assertTrue(StoryUiConstants.STORY_CODEX_BODY_X
                        > StoryUiConstants.STORY_CODEX_TAB_X + StoryUiConstants.STORY_CODEX_TAB_WIDTH,
                "the body must clear the tab column");
    }

    /** All six tabs must fit inside the panel — an unreachable tab is a hidden shelf. */
    @Test
    void everyCategoryTabFitsInsideThePanel() {
        int    tabCount   = CodexCategory.values().length;
        float  stackBottom = GameMath.stackedButtonBottomY(StoryUiConstants.STORY_CODEX_TABS_TOP_Y,
                                                           StoryUiConstants.STORY_CODEX_TAB_HEIGHT,
                                                           StoryUiConstants.STORY_CODEX_TAB_GAP,
                                                           tabCount - 1);
        assertTrue(stackBottom >= StoryUiConstants.STORY_CODEX_Y,
                "the tab column runs off the bottom of the panel");
        assertTrue(StoryUiConstants.STORY_CODEX_TAB_HEIGHT
                        >= StoryUiConstants.STORY_CHOICE_BUTTON_MIN_HEIGHT * 0.9f,
                "a tab must stay a comfortable thumb target");
        assertTrue(StoryUiConstants.STORY_CODEX_ROW_HEIGHT >= 60f,
                "an entry row must stay a comfortable thumb target");
    }

    // -------------------------------------------------------------------------
    // PART E — tuning counters
    // -------------------------------------------------------------------------

    @Test
    void telemetrySplitsSkimmedLinesFromReadOnes() {
        StoryTelemetry telemetry = new StoryTelemetry();
        telemetry.recordBarkShown();
        assertTrue(telemetry.recordBarkDismissed(0.2f, "bark.skimmed"));
        telemetry.recordBarkShown();
        assertFalse(telemetry.recordBarkDismissed(
                StoryUiConstants.STORY_TELEMETRY_FAST_DISMISS_SECONDS + 4f, "bark.read"));

        assertEquals(2, telemetry.getBarksShown());
        assertEquals(1, telemetry.getBarksClosedFast());
        assertEquals(1, telemetry.getBarksRead());
        assertEquals(0.5f, telemetry.getFastDismissFraction(), 0.001f);
        // narrative-rework order-9 C: WHICH line was thrown away, not just how many were. A line
        // that was actually read must never overwrite the hand-off, or recovery would chase it.
        assertEquals("bark.skimmed", telemetry.getLastFastDismissedBarkId());

        telemetry.recordExchangeAnswered(ExchangeOptionKind.PROBE);
        assertEquals(1, telemetry.getAnswersOfKind(ExchangeOptionKind.PROBE));
        assertEquals(0, telemetry.getAnswersOfKind(ExchangeOptionKind.STANCE));

        telemetry.reset();
        assertEquals(0, telemetry.getBarksShown());
        assertEquals(0f, telemetry.getFastDismissFraction(), 0.001f);
    }

    private static void advance(BarkSystem barks, float seconds) {
        int frames = Math.max(1, Math.round(seconds / FRAME_SECONDS));
        for (int frame = 0; frame < frames; frame++) {
            barks.update(FRAME_SECONDS);
        }
    }
}

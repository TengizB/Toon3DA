package ge.tbegvadze.toon3d.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * LOCALISATION (Story UI order-7 Part C) — the whole-layer sweep.
 *
 * <p>The rule is absolute: <b>no story string is hardcoded in Java.</b>  Every line the player can
 * read — barks, exchange prompts, the answers themselves, boot-card status lines, codex entries, and
 * the screens' own chrome labels — is referenced by a stable STRING ID and lives in the externalised
 * table ({@code assets/story/story-strings.properties}).  The narrative data carries ids only.
 *
 * <p>The per-order tests each check their own catalog against the asset.  This one is the sweep the
 * others cannot be: every id from every channel AND every chrome id the renderers resolve, gathered
 * in one place and checked against the SHIPPED table.  It is what catches the id that was added to
 * one catalog and to the Java defaults but never to the asset a translator actually receives.
 *
 * <p>Ids are also checked for the property that makes them translatable at all: once shipped, they
 * are stable keys, so they must look like keys — no whitespace, no prose, no upper case.
 */
class StoryLocalizationTest {

    /** Reads the shipped table, or skips when the asset is not reachable from the test's cwd. */
    private static StoryStrings shippedStrings() throws IOException {
        File assetFile = new File("../assets/" + StoryUiConstants.STORY_STRINGS_ASSET_PATH);
        assumeTrue(assetFile.exists(), "string asset not reachable from the test working directory");
        return StoryStrings.fromProperties(
                new String(Files.readAllBytes(assetFile.toPath()), StandardCharsets.UTF_8));
    }

    /**
     * Every id the game can ask for at runtime, from every channel and every piece of chrome.
     * Adding a channel means adding it here — which is the point: a channel nobody swept is a
     * channel that ships an untranslated string.
     */
    private static List<String> everyReferencedStringId() {
        List<String> ids = new ArrayList<>();

        // The speakers themselves — the name on every chip.
        for (Speaker speaker : Speaker.values()) {
            ids.add(speaker.getNameStringId());
        }

        // Order-2: barks.
        for (BarkDefinition row : BarkCatalog.defaultRegistry().getAll()) {
            ids.add(row.getTextStringId());
        }

        // Order-3: boot cards — the machine's status lines and ORA's wake lines.
        BootCardRegistry bootCards = BootCardCatalog.defaultRegistry();
        for (BootCardDefinition card : bootCards.getCards()) {
            // Every BAND of every card: a card may print a different block once the story has gone
            // deep enough (order-2 C), and a band nobody swept is a band that ships a !id! marker.
            for (int blockIndex = 0; blockIndex < card.getStatusBlockCount(); blockIndex++) {
                for (String stringId : card.getStatusBlockLineStringIds(blockIndex)) ids.add(stringId);
            }
        }
        for (BootWakeLine wakeLine : bootCards.getWakeLines()) {
            ids.add(wakeLine.getTextStringId());
        }

        // Order-4: exchanges — the prompt, every answer the player can tap, every reply.
        for (ExchangeDefinition exchange : ExchangeCatalog.defaultRegistry().getAll()) {
            ids.add(exchange.getPromptStringId());
            for (int optionIndex = 0; optionIndex < exchange.getOptionCount(); optionIndex++) {
                ExchangeOption option = exchange.getOption(optionIndex);
                ids.add(option.getPlayerLineStringId());
                if (option.getReplyStringId() != null) ids.add(option.getReplyStringId());
            }
        }

        // Order-6: codex entries and their category tabs.
        for (CodexEntry entry : CodexCatalog.defaultRegistry().getAll()) {
            ids.add(entry.getTitleStringId());
            ids.add(entry.getSourceStringId());
            ids.add(entry.getBodyStringId());
            if (entry.getAiTakeStringId() != null) ids.add(entry.getAiTakeStringId());
        }
        for (CodexCategory category : CodexCategory.values()) {
            ids.add(category.getTitleStringId());
        }

        // narrative-rework order-9 B: the recap page is assembled at runtime, so its region rows and
        // its four block headings belong to no catalog.  Without this they would be the only text in
        // the game nobody could check until a player with the right save opened the right screen.
        ids.addAll(StoryRecap.everyStaticStringId());

        ids.addAll(everyChromeStringId());
        return ids;
    }

    /** The labels the renderers draw that belong to no catalog — buttons, headers, setting names. */
    private static List<String> everyChromeStringId() {
        List<String> ids = new ArrayList<>();
        ids.add(StoryUiConstants.STORY_BOOT_CONTINUE_LABEL_ID);
        ids.add(StoryUiConstants.STORY_EXCHANGE_CONTINUE_LABEL_ID);
        ids.add(StoryUiConstants.STORY_CODEX_TITLE_ID);
        ids.add(StoryUiConstants.STORY_CODEX_BACK_LABEL_ID);
        ids.add(StoryUiConstants.STORY_CODEX_EMPTY_ID);
        ids.add(StoryUiConstants.STORY_CODEX_NEW_LABEL_ID);
        ids.add(StoryUiConstants.STORY_CODEX_COMPLETE_LABEL_ID);
        ids.add(StoryUiConstants.STORY_CODEX_TAKE_LABEL_ID);

        // Order-8: the framing screens — the launch/title menu, the pause and settings screens, the
        // reprint report, and the way off an ending.  Every label the player can read on a screen
        // that book-ends play, including the questions a consequence asks before it acts.
        ids.add(StoryUiConstants.STORY_TITLE_GAME_ID);
        ids.add(StoryUiConstants.STORY_TITLE_TAGLINE_ID);
        ids.add(StoryUiConstants.STORY_PAUSE_TITLE_ID);
        ids.add(StoryUiConstants.STORY_SETTINGS_TITLE_ID);
        ids.add(StoryUiConstants.STORY_SETTINGS_BACK_ID);
        ids.add(StoryUiConstants.STORY_FRAME_CONFIRM_YES_ID);
        ids.add(StoryUiConstants.STORY_FRAME_CONFIRM_NO_ID);
        ids.add(StoryUiConstants.STORY_FRAME_RETURN_ID);
        ids.add(StoryUiConstants.STORY_DEATH_STROKE_ID);
        ids.add(StoryUiConstants.STORY_REPORT_TITLE_ID);
        ids.add(StoryUiConstants.STORY_REPORT_OPEN_ID);
        ids.add(StoryUiConstants.STORY_REPORT_BACK_ID);
        ids.add(StoryUiConstants.STORY_REPORT_FLOOR_ID);
        ids.add(StoryUiConstants.STORY_REPORT_KILLS_ID);
        ids.add(StoryUiConstants.STORY_REPORT_DAMAGE_ID);
        ids.add(StoryUiConstants.STORY_REPORT_TIME_ID);
        ids.add(StoryUiConstants.STORY_REPORT_BEST_ID);
        for (TitleMenuItem item : TitleMenuItem.values()) {
            ids.add(item.getLabelStringId());
            // The wording a row wears before the player has ever been printed (order-2 A).
            if (item.getFirstRunLabelStringId() != null) ids.add(item.getFirstRunLabelStringId());
            if (item.getConfirmStringId() != null) ids.add(item.getConfirmStringId());
        }
        for (PauseMenuItem item : PauseMenuItem.values()) {
            ids.add(item.getLabelStringId());
            if (item.getConfirmStringId() != null) ids.add(item.getConfirmStringId());
        }

        // The accessibility strip: every button's NAME and every VALUE it can cycle to, so a knob
        // added without its labels fails here rather than drawing a !id! marker in the footer.
        StorySettings settings = new StorySettings();
        for (int settingIndex = 0; settingIndex < StoryUiConstants.STORY_CODEX_SETTING_COUNT;
                settingIndex++) {
            ids.add(StorySettings.getSettingNameStringId(settingIndex));
            // Cycle the knob through its whole range, collecting the id of each value on the way.
            for (int cycleIndex = 0; cycleIndex < StoryTextSize.values().length + 1; cycleIndex++) {
                ids.add(settings.getSettingValueStringId(settingIndex));
                settings.cycleSetting(settingIndex);
            }
        }
        return ids;
    }

    // -------------------------------------------------------------------------
    // The sweep
    // -------------------------------------------------------------------------

    @Test
    void everyReferencedIdResolvesInTheShippedTable() throws IOException {
        StoryStrings shipped = shippedStrings();
        for (String stringId : everyReferencedStringId()) {
            assertTrue(shipped.has(stringId),
                    "story-strings.properties is missing " + stringId);
            String text = shipped.get(stringId);
            assertFalse(text.trim().isEmpty(), stringId + " resolves to blank text");
            assertFalse(text.startsWith("!") && text.endsWith("!"),
                    stringId + " resolved to the missing-id marker " + text);
        }
    }

    /** The built-in fallback table must say the same thing as the asset, or a failed load changes the game. */
    @Test
    void javaDefaultsAgreeWithTheShippedTable() throws IOException {
        StoryStrings shipped  = shippedStrings();
        StoryStrings defaults = StoryStrings.defaults();
        for (String stringId : everyReferencedStringId()) {
            assertTrue(defaults.has(stringId),
                    "the built-in fallback table is missing " + stringId);
            assertEquals(defaults.get(stringId), shipped.get(stringId),
                    "asset and Java defaults disagree on " + stringId);
        }
    }

    /**
     * A duplicate key in a properties file silently wins over the first one — the kind of merge
     * accident that changes a line nobody edited.
     */
    @Test
    void theShippedTableHasNoDuplicateKeys() throws IOException {
        File assetFile = new File("../assets/" + StoryUiConstants.STORY_STRINGS_ASSET_PATH);
        assumeTrue(assetFile.exists(), "string asset not reachable from the test working directory");
        Set<String> seenKeys = new HashSet<>();
        for (String rawLine : Files.readAllLines(assetFile.toPath(), StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.indexOf('=') < 0) continue;
            String key = line.substring(0, line.indexOf('=')).trim();
            assertTrue(seenKeys.add(key), "duplicate key in story-strings.properties: " + key);
        }
    }

    /**
     * Ids are baked into every translation the moment they ship, so they must look like keys, not
     * like text: lower case, dotted, no whitespace.  A prose "id" is a hardcoded string wearing a
     * disguise, and this is what catches it.
     */
    @Test
    void everyIdLooksLikeAStableKey() {
        for (String stringId : everyReferencedStringId()) {
            assertFalse(stringId.trim().isEmpty(), "an empty string id was registered");
            assertEquals(stringId.trim(), stringId, "id has surrounding whitespace: " + stringId);
            assertFalse(stringId.contains(" "), "id contains a space (is it prose?): " + stringId);
            assertEquals(stringId.toLowerCase(java.util.Locale.ROOT), stringId,
                    "id is not lower case: " + stringId);
            assertTrue(stringId.startsWith("story."),
                    "id is outside the story namespace: " + stringId);
        }
    }

    /** A missing id must be VISIBLE in playtest, never blank — a hole you can read is a hole you fix. */
    @Test
    void anUnknownIdResolvesToAVisibleMarker() {
        String resolved = StoryStrings.defaults().get("story.does.not.exist");
        assertFalse(resolved.trim().isEmpty(), "an unknown id resolved to blank");
        assertTrue(resolved.contains("story.does.not.exist"),
                "the missing-id marker does not name the id: " + resolved);
    }
}

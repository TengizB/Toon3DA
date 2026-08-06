package ge.tbegvadze.toon3d.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Headless guards for the Story UI order-1 foundation: the string table resolves every
 * speaker + sample id, the sample set covers all four speakers exactly once, wrapping obeys
 * the max-chars rule, the per-speaker colour arrays line up with the enum, and the bark /
 * exchange anchors stay clear of the HUD and thumb touch clusters.
 */
class StoryUiFoundationTest {

    @Test
    void defaultsResolveEverySpeakerName() {
        StoryStrings strings = StoryStrings.defaults();
        for (Speaker speaker : Speaker.values()) {
            String name = strings.get(speaker.getNameStringId());
            assertTrue(strings.has(speaker.getNameStringId()),
                    "missing speaker name id: " + speaker.getNameStringId());
            assertFalse(name.isEmpty(), "empty speaker name for " + speaker);
        }
    }

    /**
     * The showcase fixture supplies its OWN text (narrative-rework order-6 B): the four sample ids are
     * not shipped content, so they are absent from the asset and from {@code defaults()}, and the two
     * dev callers fold them in by hand.  This asserts both halves of that — the fixture covers every
     * one of its ids, and the shipped table does not carry them.
     */
    @Test
    void theShowcaseFixtureSuppliesItsOwnTextAndShipsNone() {
        StoryStrings shipped = StoryStrings.defaults();
        StoryStrings withFixture = StorySampleLines.registerShowcaseText(StoryStrings.defaults());
        for (StoryLine line : StorySampleLines.ALL) {
            assertFalse(shipped.has(line.getTextStringId()),
                    "dev fixture id is shipping in the player-facing table: " + line.getTextStringId());
            assertTrue(withFixture.has(line.getTextStringId()),
                    "the showcase fixture is missing text for " + line.getTextStringId());
            assertFalse(withFixture.get(line.getTextStringId()).trim().isEmpty(),
                    "blank showcase text for " + line.getTextStringId());
        }
    }

    @Test
    void sampleSetHasExactlyOneLinePerSpeaker() {
        assertEquals(Speaker.values().length, StorySampleLines.ALL.length);
        Set<Speaker> covered = EnumSet.noneOf(Speaker.class);
        for (StoryLine line : StorySampleLines.ALL) {
            assertTrue(covered.add(line.getSpeaker()),
                    "duplicate sample line for speaker " + line.getSpeaker());
        }
        assertEquals(EnumSet.allOf(Speaker.class), covered);
    }

    @Test
    void fromPropertiesParsesIdsAndIgnoresCommentsAndBlanks() {
        StoryStrings strings = StoryStrings.fromProperties(
                "# comment\n\nstory.speaker.ai.name=ORA\nstory.sample.ai=Hi there\n");
        assertEquals("ORA", strings.get("story.speaker.ai.name"));
        assertEquals("Hi there", strings.get("story.sample.ai"));
        // A missing id resolves to a visible marker, never null/blank.
        assertEquals("!story.missing!", strings.get("story.missing"));
    }

    @Test
    void wrapNeverExceedsMaxCharsAndLosesNoWords() {
        String text = "Operator. Go down. Purge the contaminant. Restore the yield now.";
        int maxChars = StoryUiConstants.STORY_LINE_MAX_CHARS;
        List<String> lines = StoryText.wrapToMaxChars(text, maxChars);
        assertFalse(lines.isEmpty());
        StringBuilder rejoined = new StringBuilder();
        for (String line : lines) {
            assertTrue(line.length() <= maxChars, "line over cap: '" + line + "'");
            if (rejoined.length() > 0) rejoined.append(' ');
            rejoined.append(line);
        }
        assertEquals(text, rejoined.toString());
    }

    @Test
    void wrapHardBreaksAWordLongerThanTheCap() {
        List<String> lines = StoryText.wrapToMaxChars("abcdefghij", 4);
        for (String line : lines) {
            assertTrue(line.length() <= 4, "hard-break line over cap: '" + line + "'");
        }
        assertEquals("abcdefghij", String.join("", lines));
    }

    @Test
    void accentColourArraysMatchSpeakerCount() {
        assertEquals(Speaker.values().length, StoryUiConstants.SPEAKER_COUNT);
        assertEquals(StoryUiConstants.SPEAKER_COUNT, StoryUiConstants.SPEAKER_ACCENT_R.length);
        assertEquals(StoryUiConstants.SPEAKER_COUNT, StoryUiConstants.SPEAKER_ACCENT_G.length);
        assertEquals(StoryUiConstants.SPEAKER_COUNT, StoryUiConstants.SPEAKER_ACCENT_B.length);
        // Index constants must match the enum's ordinals (per-speaker arrays index by ordinal).
        assertEquals(StoryUiConstants.SPEAKER_INDEX_AI,           Speaker.AI.ordinal());
        assertEquals(StoryUiConstants.SPEAKER_INDEX_PLANET,       Speaker.PLANET.ordinal());
        assertEquals(StoryUiConstants.SPEAKER_INDEX_ORGANIZATION, Speaker.ORGANIZATION.ordinal());
        assertEquals(StoryUiConstants.SPEAKER_INDEX_SYSTEM,       Speaker.SYSTEM.ordinal());
    }

    @Test
    void stingArraysMatchSpeakerCount() {
        assertEquals(StoryUiConstants.SPEAKER_COUNT, StoryUiConstants.STORY_STING_FREQUENCY_HZ.length);
        assertEquals(StoryUiConstants.SPEAKER_COUNT, StoryUiConstants.STORY_STING_DURATION_SEC.length);
        assertEquals(StoryUiConstants.SPEAKER_COUNT, StoryUiConstants.STORY_STING_VOLUME.length);
        assertEquals(StoryUiConstants.SPEAKER_COUNT, StoryUiConstants.STORY_STING_IS_SQUARE.length);
    }

    @Test
    void barkAnchorClearsHudAndTouchClusters() {
        // Bark panel sits entirely above the HUD band.
        assertTrue(StoryUiConstants.STORY_BARK_Y >= HudConstants.HUD_HEIGHT,
                "bark overlaps the HUD band");
        // ...and entirely above where the thumb touch clusters end.
        assertTrue(StoryUiConstants.STORY_BARK_Y >= StoryUiConstants.STORY_TOUCH_CLUSTER_TOP_Y,
                "bark overlaps the touch clusters");
        // ...and within the world horizontally.
        assertTrue(StoryUiConstants.STORY_BARK_X >= 0f);
        assertTrue(StoryUiConstants.STORY_BARK_X + StoryUiConstants.STORY_BARK_WIDTH
                <= Constants.WORLD_WIDTH);
    }

    @Test
    void exchangePanelClearsTheHudAndChoiceButtonIsAThumbTarget() {
        // The exchange (order-4) is a BLOCKING modal: it dims the world and the thumb clusters are
        // not drawn at all while it is up, so — like the boot card's CONTINUE — its answer stack is
        // free to use the lower screen those buttons normally own.  What it must never cover is the
        // HUD band, because health and ammo stay readable while the player decides.  The full stack
        // geometry is asserted in StoryExchangeTest.
        assertTrue(StoryUiConstants.STORY_EXCHANGE_Y >= HudConstants.HUD_HEIGHT,
                "exchange stack overlaps the HUD band");
        // A comfortable thumb target — generous minimum tap height.
        assertTrue(StoryUiConstants.STORY_CHOICE_BUTTON_MIN_HEIGHT >= 64f,
                "choice button too short for a thumb");
    }
}

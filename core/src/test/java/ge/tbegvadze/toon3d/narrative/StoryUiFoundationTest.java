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
    void defaultsResolveEverySpeakerNameAndSampleLine() {
        StoryStrings strings = StoryStrings.defaults();
        for (Speaker speaker : Speaker.values()) {
            String name = strings.get(speaker.getNameStringId());
            assertTrue(strings.has(speaker.getNameStringId()),
                    "missing speaker name id: " + speaker.getNameStringId());
            assertFalse(name.isEmpty(), "empty speaker name for " + speaker);
        }
        for (StoryLine line : StorySampleLines.ALL) {
            assertTrue(strings.has(line.getTextStringId()),
                    "missing sample line id: " + line.getTextStringId());
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
    void exchangePanelClearsTouchClustersAndChoiceButtonIsAThumbTarget() {
        assertTrue(StoryUiConstants.STORY_EXCHANGE_Y >= StoryUiConstants.STORY_TOUCH_CLUSTER_TOP_Y,
                "exchange panel overlaps the touch clusters");
        // A comfortable thumb target — generous minimum tap height.
        assertTrue(StoryUiConstants.STORY_CHOICE_BUTTON_MIN_HEIGHT >= 64f,
                "choice button too short for a thumb");
    }
}

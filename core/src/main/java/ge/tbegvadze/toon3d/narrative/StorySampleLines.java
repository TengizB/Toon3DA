package ge.tbegvadze.toon3d.narrative;

/**
 * The order-1 sample set: exactly one {@link StoryLine} per {@link Speaker}, used by the
 * showcase / definition-of-done to prove all four voices are instantly distinct and
 * readable over the 3D view.  Headless and data-only — the lines reference string ids.
 *
 * <h3>This is a DEV FIXTURE, not shipped content (narrative-rework order-6 B)</h3>
 * These four ids are deliberately absent from {@code assets/story/story-strings.properties} and from
 * {@link StoryStrings#defaults()}: they are a showcase of the four voices, and a row that no channel
 * can ever deliver has no business in the table a translator receives.  Their text lives here, beside
 * the data that uses it, and the two callers that need it ({@code lwjgl3/StoryUiShowcase} and
 * {@code StoryUiFoundationTest}) fold it in with {@link #registerShowcaseText} after loading the real
 * table.  Nothing in the game calls that method.
 *
 * <p>The lines are CALIBRATION for the four voices, so they follow the same rules a shipped row does
 * (docs/narrative-authority.txt SECTION 6): ORA is warm and useful and never scores the player's
 * death — the old sample ("Death number two. We'll keep it a secret.") was the exact tone the voice
 * charter forbids, sitting where a developer would read it as the reference.
 */
public final class StorySampleLines {

    /** One sample line per speaker, in {@link Speaker} declaration order. */
    public static final StoryLine[] ALL = {
        new StoryLine(Speaker.AI,           "story.sample.ai"),
        new StoryLine(Speaker.PLANET,       "story.sample.planet"),
        new StoryLine(Speaker.ORGANIZATION, "story.sample.org"),
        new StoryLine(Speaker.SYSTEM,       "story.sample.system"),
    };

    private StorySampleLines() {}

    /**
     * Adds the showcase text for {@link #ALL} to {@code strings}.  Dev-only: the showcase and the
     * foundation test call it, the game never does.  Returns the same table for chaining.
     */
    public static StoryStrings registerShowcaseText(StoryStrings strings) {
        if (strings == null) return null;
        strings.put("story.sample.ai",     "You're up. I'll talk you through it. That's the job.");
        strings.put("story.sample.planet", "...you smell the same...");
        strings.put("story.sample.org",    "Operator. Go down. Purge the contaminant.");
        strings.put("story.sample.system", "PREVIOUS INSTANCE - TERMINATED. REPRINTING.");
        return strings;
    }
}

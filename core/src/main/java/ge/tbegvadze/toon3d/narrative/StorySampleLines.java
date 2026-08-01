package ge.tbegvadze.toon3d.narrative;

/**
 * The order-1 sample set: exactly one {@link StoryLine} per {@link Speaker}, used by the
 * showcase / definition-of-done to prove all four voices are instantly distinct and
 * readable over the 3D view.  Headless and data-only — references string ids only (their
 * text lives in {@link StoryStrings}).
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
}

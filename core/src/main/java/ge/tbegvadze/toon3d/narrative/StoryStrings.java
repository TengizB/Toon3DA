package ge.tbegvadze.toon3d.narrative;

import java.util.HashMap;
import java.util.Map;

/**
 * The externalised story string table — the ONE place localised story text lives
 * (Story UI order-7's localisation rule, honoured from order-1).  Narrative data
 * ({@link StoryLine}, {@link Speaker}) references stable string ids only; this table maps
 * id → text.  I18NBundle-style, but kept HEADLESS (no LibGDX) so narrative logic and its
 * tests need no render context.
 *
 * The render layer typically builds this from a properties asset
 * ({@code assets/story/story-strings.properties}) via {@link #fromProperties(String)},
 * falling back to {@link #defaults()} if the asset is missing.  Ids never present in the
 * table resolve to a visible {@code !id!} marker so a missing translation is obvious in
 * playtest rather than silently blank.
 */
public final class StoryStrings {

    private final Map<String, String> table;

    public StoryStrings() {
        this.table = new HashMap<>();
    }

    /** Adds or replaces one id → text entry. Returns this for chaining. */
    public StoryStrings put(String stringId, String text) {
        table.put(stringId, text);
        return this;
    }

    /** True when the table has a real entry for this id. */
    public boolean has(String stringId) {
        return table.containsKey(stringId);
    }

    /**
     * Resolves a string id to its text, or to a visible {@code !id!} marker when absent —
     * never null, so callers never crash and missing strings are obvious on screen.
     */
    public String get(String stringId) {
        String text = table.get(stringId);
        return text != null ? text : "!" + stringId + "!";
    }

    /**
     * Parses a properties-style body ({@code key=value} lines, {@code #} comments, blank
     * lines ignored) into a table.  Pure String parsing — no LibGDX — so the render layer
     * can hand it a FileHandle's text and tests can hand it a literal.
     */
    public static StoryStrings fromProperties(String propertiesBody) {
        StoryStrings strings = new StoryStrings();
        if (propertiesBody == null) return strings;
        String[] lines = propertiesBody.split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            String stringId = line.substring(0, separator).trim();
            String text     = line.substring(separator + 1).trim();
            if (!stringId.isEmpty()) strings.put(stringId, text);
        }
        return strings;
    }

    /**
     * The built-in fallback table.  Holds the four speaker names, the four order-1 sample lines
     * (one per speaker) used by the showcase / definition-of-done, and — folded in from
     * {@link BarkStrings}, {@link BootCardStrings} and {@link ExchangeStrings} — every order-2 bark
     * line, every order-3 boot-card line and every order-4 exchange line.  It is the safety net when
     * the properties asset is unavailable.  Keep in sync with
     * {@code assets/story/story-strings.properties}.
     */
    public static StoryStrings defaults() {
        StoryStrings strings = new StoryStrings();

        // Speaker display names (short — read at a glance).
        strings.put("story.speaker.ai.name",     "ORA");
        strings.put("story.speaker.planet.name", "THE PLANET");
        strings.put("story.speaker.org.name",    "OVERSEER");
        strings.put("story.speaker.system.name", "SYSTEM");

        // One sample line per speaker (from story/dialog/*.md), showcasing each voice.
        strings.put("story.sample.ai",     "Hey, you're up. Death number two. We'll keep it a secret.");
        strings.put("story.sample.planet", "...you smell the same...");
        strings.put("story.sample.org",    "Operator. Go down. Purge the contaminant.");
        strings.put("story.sample.system", "PREVIOUS INSTANCE - TERMINATED. REPRINTING.");

        // Order-2 bark layer: every one-liner the bark catalog can deliver.
        BarkStrings.registerDefaults(strings);
        // Order-3 boot card: the machine-voice status cards and ORA's reprint wake-up lines.
        BootCardStrings.registerDefaults(strings);
        // Order-4 exchanges: every blocking prompt, the answers the player may tap, and the replies.
        ExchangeStrings.registerDefaults(strings);
        // Order-6 codex: the archive's entries (the only long-form text in the game) and its chrome.
        CodexStrings.registerDefaults(strings);

        return strings;
    }
}

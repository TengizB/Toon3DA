package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One archived entry in the CODEX (Story UI order-6 Part A) — a title, where it came from, its full
 * text, and (for a log) the one-line take ORA already gave the player on it.
 *
 * <p>Like every other row in the narrative layer this is pure DATA referencing localisation ids
 * only, so adding an entry is one {@code register()} call in {@link CodexCatalog} and never a
 * hardcoded string in Java.
 *
 * <h3>How an entry unlocks</h3>
 * Two ways, and they compose:
 * <ul>
 *   <li><b>By what was said.</b> {@link #getUnlockLineIds()} lists the bark / exchange ids whose
 *       delivery archives this entry.  That is what makes the codex an ARCHIVE rather than a second
 *       body of writing: ORA says one line off a terminal, and the full text of that terminal is
 *       waiting in the codex for anyone who wants it.</li>
 *   <li><b>By being paid for it.</b> An {@code ExchangeOption.unlocksCodexId} names an entry id
 *       directly, which is the reward a PROBE answer hands a curious player — recorded permanently
 *       through {@link StoryProgress#unlockCodexEntry(String)}.</li>
 * </ul>
 * Both paths end in the same persisted unlock set, so a codex entry earned two runs ago is still
 * readable after any number of deaths.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class CodexEntry {

    /**
     * Which page a COMPOSED entry's body is assembled from (narrative-rework order-9).  An ordinary
     * entry is authored once and never changes; these two are written by the game out of live state,
     * which is why they name a composer instead of trusting their body string.
     */
    public enum ComposedPage {
        /** WHERE WE ARE / THE JOB / WHAT WE KNOW / WHAT YOU DECIDED (order-9 B). */
        RECAP,
        /** The critical lines the player closed before reading them (order-9 C). */
        MISSED
    }

    private final String        id;
    private final CodexCategory category;
    private final StoryRegion   region;
    private final String        titleStringId;
    private final String        sourceStringId;
    private final String        bodyStringId;
    private final String        aiTakeStringId;
    private final List<String>  unlockLineIds;
    private final ComposedPage  composedPage;
    private final boolean       pinned;
    private final boolean       alwaysUnlocked;

    private CodexEntry(Builder builder) {
        this.id             = builder.id;
        this.category       = builder.category;
        this.region         = builder.region;
        this.titleStringId  = builder.titleStringId;
        this.sourceStringId = builder.sourceStringId;
        this.bodyStringId   = builder.bodyStringId;
        this.aiTakeStringId = builder.aiTakeStringId;
        this.composedPage   = builder.composedPage;
        this.pinned         = builder.pinned;
        this.alwaysUnlocked = builder.alwaysUnlocked;
        this.unlockLineIds  = builder.unlockLineIds.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.unlockLineIds));
    }

    /** Stable id — the key the unlock set is persisted under.  Never change a shipped one. */
    public String getId() {
        return id;
    }

    /** Which tab this entry files under. */
    public CodexCategory getCategory() {
        return category;
    }

    /**
     * The story region this entry belongs to.  Used only for READING ORDER inside a tab (shallowest
     * first, so a tab reads as a descent) — never as a gate: an entry is visible exactly when it is
     * unlocked, and nothing about the current run can hide one the player already earned.
     */
    public StoryRegion getRegion() {
        return region;
    }

    public String getTitleStringId() {
        return titleStringId;
    }

    /** Where the entry came from ("Maintenance terminal, Habitation Rings"), as a localisation id. */
    public String getSourceStringId() {
        return sourceStringId;
    }

    /** The full text — the ONE place in the game long-form writing is allowed. */
    public String getBodyStringId() {
        return bodyStringId;
    }

    /**
     * ORA's one-line take, shown at the TOP of a log's detail view, or null when the entry never had
     * one.  It is the same string id the bark said out in the world, deliberately: the player reads
     * the line they remember and then the document it was about.
     */
    public String getAiTakeStringId() {
        return aiTakeStringId;
    }

    /** Bark / exchange ids whose delivery archives this entry.  Never null; may be empty. */
    public List<String> getUnlockLineIds() {
        return unlockLineIds;
    }

    /**
     * Which live page assembles this entry's body, or null for the ordinary case — an entry written
     * once by an author (narrative-rework order-9).
     *
     * <p>A composed entry is deliberately outside the archive's collection bookkeeping: it is not
     * something the player RECOVERED, so it counts towards no category's completion and appears in
     * no recap of "what we know".  Two of them exist and there is no reason for a third — the codex
     * is an archive of what was said, and everything else on this axis is a HUD element in disguise.
     */
    public ComposedPage getComposedPage() {
        return composedPage;
    }

    /** True when this entry's body is assembled from live state rather than read from the table. */
    public boolean isComposed() {
        return composedPage != null;
    }

    /**
     * True when this entry sits at the TOP OF THE ARCHIVE, above the open tab's own rows, whichever
     * tab that is.  Exactly one entry is pinned: the page that answers "what is going on", which is
     * useless if a returning player has to find it first.
     */
    public boolean isPinned() {
        return pinned;
    }

    /**
     * True when this entry is never locked.  A player who has been away for a week must not have to
     * have EARNED the page that reminds them where they were.
     */
    public boolean isAlwaysUnlocked() {
        return alwaysUnlocked;
    }

    /** True when {@code lineId} is one of the lines that archives this entry. */
    public boolean isUnlockedByLine(String lineId) {
        if (lineId == null) return false;
        for (int lineIndex = 0; lineIndex < unlockLineIds.size(); lineIndex++) {
            if (unlockLineIds.get(lineIndex).equals(lineId)) return true;
        }
        return false;
    }

    public static Builder builder(String id, CodexCategory category) {
        return new Builder(id, category);
    }

    /** Fluent builder — the shape every narrative catalog row is written in. */
    public static final class Builder {

        private final String        id;
        private final CodexCategory category;
        private StoryRegion         region = StoryRegion.HABITATION_RINGS;
        private String              titleStringId;
        private String              sourceStringId;
        private String              bodyStringId;
        private String              aiTakeStringId;
        private ComposedPage        composedPage;
        private boolean             pinned;
        private boolean             alwaysUnlocked;
        private final List<String>  unlockLineIds = new ArrayList<>();

        private Builder(String id, CodexCategory category) {
            if (id == null || id.isEmpty()) throw new IllegalArgumentException("id must not be empty");
            if (category == null)           throw new IllegalArgumentException("category must not be null");
            this.id       = id;
            this.category = category;
            // Convention: the three text ids mirror the entry id, so a catalog row states only what
            // differs from it.  Any of them may still be overridden explicitly.
            this.titleStringId  = "story." + id + ".title";
            this.sourceStringId = "story." + id + ".source";
            this.bodyStringId   = "story." + id + ".body";
        }

        public Builder region(StoryRegion value)      { this.region         = value; return this; }
        public Builder titleStringId(String value)    { this.titleStringId  = value; return this; }
        public Builder sourceStringId(String value)   { this.sourceStringId = value; return this; }
        public Builder bodyStringId(String value)     { this.bodyStringId   = value; return this; }
        public Builder aiTakeStringId(String value)   { this.aiTakeStringId = value; return this; }

        /**
         * Marks this entry's body as ASSEMBLED from live state (narrative-rework order-9) rather than
         * read from the string table.  The registered body id is kept as the fallback the page shows
         * when the composer has nothing to say, so a composed entry is still a fully translatable row
         * and still fails the localisation sweep if its text is missing.
         */
        public Builder composed(ComposedPage value) {
            this.composedPage = value;
            return this;
        }

        /** Pins this entry above the open tab's rows, on every tab (order-9 B). */
        public Builder pinned(boolean value) {
            this.pinned = value;
            return this;
        }

        /** Makes this entry permanently readable — never locked, never earned (order-9 B). */
        public Builder alwaysUnlocked(boolean value) {
            this.alwaysUnlocked = value;
            return this;
        }

        /** Adds one bark / exchange id whose delivery archives this entry. */
        public Builder unlockedByLine(String lineId) {
            if (lineId != null && !lineId.isEmpty()) unlockLineIds.add(lineId);
            return this;
        }

        /**
         * Adds the VOCABULARY LADDER's naming line for this entry's term (narrative-rework order-3),
         * if a {@link StoryTerm} names it.  So the moment a player is told what a word means, the long
         * version is waiting for them — which is the deal the codex makes: it is where the SHORT thing
         * they already heard gets its full text, never where a fact lives for the first time.
         *
         * <p>The entry -&gt; term pairing is declared exactly once, on {@link StoryTerm}; this reads it
         * back, so there is no second table to keep in step.  A no-op for an entry no term names.
         */
        public Builder unlockedByTermIntro() {
            return unlockedByLine(StoryTermCatalog.introLineForCodexEntry(id));
        }

        public CodexEntry build() {
            if (region == null) throw new IllegalStateException("region must not be null: " + id);
            return new CodexEntry(this);
        }
    }
}

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

    private final String        id;
    private final CodexCategory category;
    private final StoryRegion   region;
    private final String        titleStringId;
    private final String        sourceStringId;
    private final String        bodyStringId;
    private final String        aiTakeStringId;
    private final List<String>  unlockLineIds;

    private CodexEntry(Builder builder) {
        this.id             = builder.id;
        this.category       = builder.category;
        this.region         = builder.region;
        this.titleStringId  = builder.titleStringId;
        this.sourceStringId = builder.sourceStringId;
        this.bodyStringId   = builder.bodyStringId;
        this.aiTakeStringId = builder.aiTakeStringId;
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

        /** Adds one bark / exchange id whose delivery archives this entry. */
        public Builder unlockedByLine(String lineId) {
            if (lineId != null && !lineId.isEmpty()) unlockLineIds.add(lineId);
            return this;
        }

        public CodexEntry build() {
            if (region == null) throw new IllegalStateException("region must not be null: " + id);
            return new CodexEntry(this);
        }
    }
}

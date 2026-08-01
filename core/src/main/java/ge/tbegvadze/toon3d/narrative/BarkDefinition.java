package ge.tbegvadze.toon3d.narrative;

/**
 * One immutable row of the bark catalog: WHO says WHAT, at WHICH moment, in WHICH story regions.
 * Data only — no behaviour, no LibGDX.  Rows are registered once into a {@link BarkRegistry}
 * ({@link BarkCatalog#bootstrap}) and selected at runtime by {@link BarkSystem}; nothing anywhere
 * switches on a hard-coded bark list.
 *
 * <p>Localisation rule (order-7 Part C, honoured from order-1): a row carries a stable
 * {@code textStringId} only — never the story text itself.  The text is resolved at delivery time
 * through {@link StoryStrings}.
 *
 * <p>REGION GATING is the tone engine: {@code firstRegion..lastRegion} is the inclusive band of
 * DEEPEST-REGION-REACHED in which this line may be spoken.  The same trigger therefore yields a
 * cheerful corporate-parroting ORA in the Habitation Rings and a frightened ally in the Wound,
 * without a single conditional in game code.  The planet simply has no rows in Region 1, which is
 * how its "silence -> whispers -> address" staging is expressed.
 *
 * <p>ONE-SHOT rows fire exactly once, keyed by {@link #getId()} in the persistent
 * {@link StoryProgress} seen-set: re-entering a cleared region on a later run never replays them.
 */
public final class BarkDefinition {

    private final String       id;
    private final Speaker      speaker;
    private final String       textStringId;
    private final BarkTrigger  trigger;
    private final StoryRegion  firstRegion;
    private final StoryRegion  lastRegion;
    private final BarkPriority priority;
    private final boolean      oneShot;
    private final String       subjectKey;
    private final BarkTone     tone;
    private final float        weight;

    private BarkDefinition(Builder builder) {
        this.id           = builder.id;
        this.speaker      = builder.speaker;
        this.textStringId = builder.textStringId;
        this.trigger      = builder.trigger;
        this.firstRegion  = builder.firstRegion;
        this.lastRegion   = builder.lastRegion;
        this.priority     = builder.priority;
        this.oneShot      = builder.oneShot;
        this.subjectKey   = builder.subjectKey;
        this.tone         = builder.tone;
        // An explicit weight wins; otherwise the tone sets it, which is how the lore/levity balance
        // is enforced by data rather than by selection code.
        this.weight       = builder.weight > 0f ? builder.weight : builder.tone.getSelectionWeight();
    }

    /** Stable catalog id.  Doubles as the persistent one-shot "seen" key — never change a shipped id. */
    public String getId() { return id; }

    public Speaker getSpeaker() { return speaker; }

    /** Stable localisation id of the line; resolve via {@link StoryStrings#get(String)}. */
    public String getTextStringId() { return textStringId; }

    public BarkTrigger getTrigger() { return trigger; }

    /** Shallowest story region this line may be spoken in (inclusive). */
    public StoryRegion getFirstRegion() { return firstRegion; }

    /** Deepest story region this line may be spoken in (inclusive). */
    public StoryRegion getLastRegion() { return lastRegion; }

    public BarkPriority getPriority() { return priority; }

    /** True when this line fires exactly once, ever (persistent flag keyed by {@link #getId()}). */
    public boolean isOneShot() { return oneShot; }

    /**
     * Optional subject this row is about (e.g. an {@code EnemyFamily.name()} for a
     * first-appearance line).  Null means "matches a request that carries no subject".
     */
    public String getSubjectKey() { return subjectKey; }

    /** Whether this line moves the story ({@link BarkTone#LORE}) or is a dry aside. */
    public BarkTone getTone() { return tone; }

    /**
     * Relative selection weight inside its pool — resolved at build time, so it is always &gt; 0 here:
     * an explicit {@code weight(...)} if one was given, otherwise the {@link BarkTone}'s weight.
     */
    public float getWeight() { return weight; }

    /** True when this row may be spoken with the given deepest-region-reached. */
    public boolean matchesRegion(StoryRegion region) {
        return region != null && region.isWithin(firstRegion, lastRegion);
    }

    /** True when this row is about the requested subject (both null = a subject-less moment). */
    public boolean matchesSubject(String requestedSubjectKey) {
        if (subjectKey == null) return requestedSubjectKey == null;
        return subjectKey.equals(requestedSubjectKey);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder — every row is built once at bootstrap, never per frame. */
    public static final class Builder {

        private final String id;
        private Speaker      speaker      = Speaker.AI;
        private String       textStringId;
        private BarkTrigger  trigger;
        private StoryRegion  firstRegion  = StoryRegion.HABITATION_RINGS;
        private StoryRegion  lastRegion   = StoryRegion.CORE;
        private BarkPriority priority     = BarkPriority.REACTIVE;
        private boolean      oneShot      = false;
        private String       subjectKey   = null;
        private BarkTone     tone         = BarkTone.LORE;   // the channel exists to tell the story
        private float        weight       = 0f;               // 0 = "use the tone's weight"

        private Builder(String id) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("bark id must not be empty");
            }
            this.id = id;
        }

        public Builder speaker(Speaker value)         { this.speaker      = value; return this; }
        public Builder textStringId(String value)     { this.textStringId = value; return this; }
        public Builder trigger(BarkTrigger value)     { this.trigger      = value; return this; }
        public Builder priority(BarkPriority value)   { this.priority     = value; return this; }
        public Builder oneShot(boolean value)         { this.oneShot      = value; return this; }
        public Builder subjectKey(String value)       { this.subjectKey   = value; return this; }
        public Builder tone(BarkTone value)           { this.tone         = value; return this; }
        /** Overrides the tone's default weight.  Leave unset for the standard lore/levity balance. */
        public Builder weight(float value)            { this.weight       = value; return this; }

        /** Restricts this row to a single story region. */
        public Builder region(StoryRegion value) {
            this.firstRegion = value;
            this.lastRegion  = value;
            return this;
        }

        /** Restricts this row to the inclusive band {@code [first, last]}. */
        public Builder regionBand(StoryRegion first, StoryRegion last) {
            this.firstRegion = first;
            this.lastRegion  = last;
            return this;
        }

        /** Allows this row from {@code first} down to the Core. */
        public Builder regionFrom(StoryRegion first) {
            this.firstRegion = first;
            this.lastRegion  = StoryRegion.CORE;
            return this;
        }

        public BarkDefinition build() {
            if (textStringId == null || textStringId.isEmpty()) {
                throw new IllegalArgumentException("bark " + id + " has no text string id");
            }
            if (trigger == null) {
                throw new IllegalArgumentException("bark " + id + " has no trigger");
            }
            if (speaker == null) {
                throw new IllegalArgumentException("bark " + id + " has no speaker");
            }
            if (firstRegion.ordinal() > lastRegion.ordinal()) {
                throw new IllegalArgumentException("bark " + id + " has an inverted region band");
            }
            if (tone == null) {
                throw new IllegalArgumentException("bark " + id + " has no tone");
            }
            // weight 0 is the "inherit the tone's weight" sentinel; a NEGATIVE weight is a bug.
            if (weight < 0f) {
                throw new IllegalArgumentException("bark " + id + " has a negative weight");
            }
            return new BarkDefinition(this);
        }
    }
}

package ge.tbegvadze.toon3d.narrative;

/**
 * One ORA wake-up line for the reprint card (Story UI order-3) — the guaranteed story beat the
 * player gets on every single run.  Data only; no behaviour, no LibGDX.
 *
 * <p>REGION GATING is the whole arc.  {@code firstRegion..lastRegion} is the inclusive band of
 * DEEPEST-REGION-REACHED in which this line may be spoken, exactly as {@link BarkDefinition} works,
 * so the same moment yields a different ORA at each stage of the descent with no conditional in
 * game code (story/dialog/ai-assistant.md):
 * <ul>
 *   <li>Region 1 — cheerful; she counts your deaths like a scoreboard.</li>
 *   <li>Region 2 — distracted; she starts asking what the printers run on.</li>
 *   <li>Region 3 — cracking; she knows what the reserve is and can't un-know it.</li>
 *   <li>Region 4 and below — quiet and protective; she is keeping things off the log for you.</li>
 * </ul>
 *
 * <p>Localisation rule: a row carries a stable {@code textStringId} only.  The resolved text may
 * contain the {@link BootCardSystem#INSTANCE_TOKEN} placeholder, which the system substitutes with
 * the formatted reprint number so a line can say "Reprint 0048" without the catalog knowing it.
 *
 * <h3>RESERVED LINES (narrative-rework order-2 B/C)</h3>
 * A row may additionally be reserved for ONE card, by variant or by an exact reprint number, and a
 * reserved row that matches beats the whole region pool.  That is how the two cards a player only
 * ever sees once get the line they need instead of a random greeting:
 * <ul>
 *   <li>the FIRST PRINT — thirty seconds after a stranger's death notice, where ORA has not
 *       introduced herself yet and the card is no place for a CV;</li>
 *   <li>the player's own FIRST REAL DEATH (and the two prints after it) — the moment the loop is
 *       explained, which the catalog used to spend on a joke about a new record.</li>
 * </ul>
 * Reserving by an exact count is what orders those: the number climbs and never repeats, so each
 * line lands on exactly one card, once, without a one-shot flag of its own.
 */
public final class BootWakeLine {

    /** {@link #getOnlyAtReprintCount()} when a row is not reserved for one particular print. */
    public static final int ANY_REPRINT_COUNT = -1;

    private final String          id;
    private final String          textStringId;
    private final StoryRegion     firstRegion;
    private final StoryRegion     lastRegion;
    private final BootCardVariant onlyOnVariant;
    private final int             onlyAtReprintCount;
    private final boolean         reEntry;

    private BootWakeLine(Builder builder) {
        this.id                 = builder.id;
        this.textStringId       = builder.textStringId;
        this.firstRegion        = builder.firstRegion;
        this.lastRegion         = builder.lastRegion;
        this.onlyOnVariant      = builder.onlyOnVariant;
        this.onlyAtReprintCount = builder.onlyAtReprintCount;
        this.reEntry            = builder.reEntry;
    }

    /** Stable catalog id.  Also the no-repeat memory key — never change a shipped id. */
    public String getId() { return id; }

    /** Stable localisation id of the line; resolve via {@link StoryStrings#get(String)}. */
    public String getTextStringId() { return textStringId; }

    public StoryRegion getFirstRegion() { return firstRegion; }

    public StoryRegion getLastRegion() { return lastRegion; }

    /** The card variant this line is reserved for, or null when it belongs to the general pool. */
    public BootCardVariant getOnlyOnVariant() { return onlyOnVariant; }

    /**
     * The one reprint number this line is reserved for, or {@link #ANY_REPRINT_COUNT} when it
     * belongs to the general pool.
     */
    public int getOnlyAtReprintCount() { return onlyAtReprintCount; }

    /**
     * True when this row is the RE-ENTRY line for its region (narrative-rework order-9 A) — the one
     * sentence a player who has been away for a day gets instead of a greeting: where they are, and
     * what they were last told.
     *
     * <p>It REPLACES the ordinary wake line rather than stacking with it, so the card never grows a
     * fourth block, and it is one-shot per RE-ENTRY EVENT rather than per save.  That is the single
     * deliberate exception to "say it once" in the whole layer, and it is a legitimate one: the
     * evidence for saying it again is a real absence, not a guess about what the player forgot.
     */
    public boolean isReEntry() {
        return reEntry;
    }

    /**
     * True when this row is held back for one particular card rather than pooled by region.  A
     * re-entry row counts: it can only ever be drawn on a card that follows an absence, so it is a
     * single beat rather than pool depth (which is what the pool-depth test measures).
     */
    public boolean isReserved() {
        return onlyOnVariant != null || onlyAtReprintCount != ANY_REPRINT_COUNT || reEntry;
    }

    /** True when this line may be spoken with the given deepest-region-reached. */
    public boolean matchesRegion(StoryRegion region) {
        return region != null && region.isWithin(firstRegion, lastRegion);
    }

    /**
     * True when this row's reservation (if it has one) is satisfied by the card being printed.  An
     * unreserved row matches every card, which is what makes the general pool the fallback.
     *
     * @param variant      the card being printed
     * @param reprintCount the instance number this card is printing (after it has climbed)
     */
    public boolean matchesCard(BootCardVariant variant, int reprintCount) {
        if (onlyOnVariant != null && onlyOnVariant != variant)             return false;
        return onlyAtReprintCount == ANY_REPRINT_COUNT
                || onlyAtReprintCount == reprintCount;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder — every row is built once at bootstrap, never per frame. */
    public static final class Builder {

        private final String    id;
        private String          textStringId;
        private StoryRegion     firstRegion = StoryRegion.HABITATION_RINGS;
        private StoryRegion     lastRegion  = StoryRegion.CORE;
        private BootCardVariant onlyOnVariant;
        private int             onlyAtReprintCount = ANY_REPRINT_COUNT;
        private boolean         reEntry;

        private Builder(String id) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("wake line id must not be empty");
            }
            this.id = id;
        }

        public Builder textStringId(String value) { this.textStringId = value; return this; }

        /** Restricts this line to a single story region. */
        public Builder region(StoryRegion value) {
            this.firstRegion = value;
            this.lastRegion  = value;
            return this;
        }

        /** Restricts this line to the inclusive band {@code [first, last]}. */
        public Builder regionBand(StoryRegion first, StoryRegion last) {
            this.firstRegion = first;
            this.lastRegion  = last;
            return this;
        }

        /** Allows this line from {@code first} down to the Core. */
        public Builder regionFrom(StoryRegion first) {
            this.firstRegion = first;
            this.lastRegion  = StoryRegion.CORE;
            return this;
        }

        /**
         * Reserves this line for ONE card variant — the FIRST PRINT's own greeting.  A reserved row
         * beats the region pool on the card it names and is never drawn on any other.
         */
        public Builder onlyOnVariant(BootCardVariant value) {
            this.onlyOnVariant = value;
            return this;
        }

        /**
         * Reserves this line for the card printing exactly this instance number — the way the first
         * few deaths get the lines that explain what a death IS, in order, once each.
         */
        public Builder onlyAtReprintCount(int value) {
            this.onlyAtReprintCount = value;
            return this;
        }

        /**
         * Marks this row as the RE-ENTRY line for its region (narrative-rework order-9 A).  It is
         * drawn only on a card that follows a real absence, and only one region's row can ever match,
         * so it needs no reprint number and no variant of its own.
         */
        public Builder reEntry(boolean value) {
            this.reEntry = value;
            return this;
        }

        public BootWakeLine build() {
            if (textStringId == null || textStringId.isEmpty()) {
                throw new IllegalArgumentException("wake line " + id + " has no text string id");
            }
            if (firstRegion.ordinal() > lastRegion.ordinal()) {
                throw new IllegalArgumentException("wake line " + id + " has an inverted region band");
            }
            if (onlyAtReprintCount != ANY_REPRINT_COUNT && onlyAtReprintCount < 1) {
                throw new IllegalArgumentException("wake line " + id
                        + " is reserved for an instance number that can never be printed");
            }
            return new BootWakeLine(this);
        }
    }
}

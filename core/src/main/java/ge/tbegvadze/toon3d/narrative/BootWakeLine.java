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
 */
public final class BootWakeLine {

    private final String      id;
    private final String      textStringId;
    private final StoryRegion firstRegion;
    private final StoryRegion lastRegion;

    private BootWakeLine(Builder builder) {
        this.id           = builder.id;
        this.textStringId = builder.textStringId;
        this.firstRegion  = builder.firstRegion;
        this.lastRegion   = builder.lastRegion;
    }

    /** Stable catalog id.  Also the no-repeat memory key — never change a shipped id. */
    public String getId() { return id; }

    /** Stable localisation id of the line; resolve via {@link StoryStrings#get(String)}. */
    public String getTextStringId() { return textStringId; }

    public StoryRegion getFirstRegion() { return firstRegion; }

    public StoryRegion getLastRegion() { return lastRegion; }

    /** True when this line may be spoken with the given deepest-region-reached. */
    public boolean matchesRegion(StoryRegion region) {
        return region != null && region.isWithin(firstRegion, lastRegion);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder — every row is built once at bootstrap, never per frame. */
    public static final class Builder {

        private final String id;
        private String       textStringId;
        private StoryRegion  firstRegion = StoryRegion.HABITATION_RINGS;
        private StoryRegion  lastRegion  = StoryRegion.CORE;

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

        public BootWakeLine build() {
            if (textStringId == null || textStringId.isEmpty()) {
                throw new IllegalArgumentException("wake line " + id + " has no text string id");
            }
            if (firstRegion.ordinal() > lastRegion.ordinal()) {
                throw new IllegalArgumentException("wake line " + id + " has an inverted region band");
            }
            return new BootWakeLine(this);
        }
    }
}

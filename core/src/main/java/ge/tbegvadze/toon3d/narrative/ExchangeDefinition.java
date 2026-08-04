package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * One EXCHANGE, as data (Story UI order-4): who speaks, what they say, and the 2-3 answers the
 * player may tap back.  Immutable, headless, and registered once into an {@link ExchangeRegistry} —
 * nothing in {@code World}, the renderer or {@link ExchangeSystem} switches on a hard-coded
 * exchange list (the same discipline as {@link BarkCatalog} and {@code route/RouteRegistries}).
 *
 * <h3>Bark vs exchange</h3>
 * A bark is non-blocking, choice-less and constant.  An exchange STOPS THE WORLD until the player
 * answers, so it is rare and deliberate — a handful per region.  If exchanges start feeling
 * frequent, cut rows; never add a "skip all" toggle, because the choice IS the engagement.
 *
 * <h3>Gating</h3>
 * Same axis as every other story pool: {@code firstRegion..lastRegion} is the inclusive band of
 * DEEPEST-REGION-REACHED in which this exchange may open, and a {@link #isOneShot() one-shot} row
 * is marked seen on delivery so a later run never replays it.  Tone shifts are expressed purely by
 * which band a row sits in — never by an {@code if (region == …)} in game code.
 */
public final class ExchangeDefinition {

    private final String               id;
    private final String               outcomeId;
    private final Speaker              speaker;
    private final String               promptStringId;
    private final ExchangeTrigger      trigger;
    private final StoryRegion          firstRegion;
    private final StoryRegion          lastRegion;
    private final boolean              oneShot;
    private final Stance               stanceAffinity;
    private final List<ExchangeOption> options;

    private ExchangeDefinition(Builder builder) {
        this.id             = builder.id;
        this.outcomeId      = builder.outcomeId != null ? builder.outcomeId : builder.id;
        this.speaker        = builder.speaker;
        this.promptStringId = builder.promptStringId;
        this.trigger        = builder.trigger;
        this.firstRegion    = builder.firstRegion;
        this.lastRegion     = builder.lastRegion;
        this.oneShot        = builder.oneShot;
        this.stanceAffinity = builder.stanceAffinity;
        this.options        = Collections.unmodifiableList(new ArrayList<>(builder.options));
    }

    /** Stable catalog id.  Doubles as the persistent one-shot key — never change a shipped id. */
    public String getId() { return id; }

    /**
     * The key a CONSEQUENTIAL answer here is recorded under.  Defaults to {@link #getId()}, and is
     * overridden only when several exchanges are two halves of ONE decision — a probe that chains
     * into the same yes/no records under the original beat's key, so order-5 asks one question of
     * {@link StoryProgress#getOutcome(String)} rather than having to know the chain's shape.
     */
    public String getOutcomeId() { return outcomeId; }

    /** Who is speaking the prompt.  The player's answers have no speaker chip: they are buttons. */
    public Speaker getSpeaker() { return speaker; }

    /** Localisation id of the prompt line; resolve via {@link StoryStrings#get(String)}. */
    public String getPromptStringId() { return promptStringId; }

    public ExchangeTrigger getTrigger() { return trigger; }

    public StoryRegion getFirstRegion() { return firstRegion; }

    public StoryRegion getLastRegion() { return lastRegion; }

    /** True when this exchange opens exactly once, ever (persistent flag keyed by {@link #getId()}). */
    public boolean isOneShot() { return oneShot; }

    /**
     * Optional leaning this exchange is written FOR.  When several rows are eligible for the same
     * moment, one matching the player's dominant {@link Stance} is preferred — which is how the
     * hidden model flavours "which lines ORA offers" without ever gating content.  Null = neutral,
     * and a neutral row is always a valid fallback, so no stance can ever leave a moment silent.
     */
    public Stance getStanceAffinity() { return stanceAffinity; }

    /** The 2-3 answers, in the order they are stacked on screen.  Read-only. */
    public List<ExchangeOption> getOptions() { return options; }

    public int getOptionCount() { return options.size(); }

    public ExchangeOption getOption(int optionIndex) {
        if (optionIndex < 0 || optionIndex >= options.size()) return null;
        return options.get(optionIndex);
    }

    /** True when this exchange may open with the given deepest-region-reached. */
    public boolean matchesRegion(StoryRegion region) {
        return region != null && region.isWithin(firstRegion, lastRegion);
    }

    /** True when any answer here changes something the game remembers. */
    public boolean hasConsequentialOption() {
        for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
            if (options.get(optionIndex).getKind() == ExchangeOptionKind.CONSEQUENTIAL) return true;
        }
        return false;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder — every row is built once at bootstrap, never per frame. */
    public static final class Builder {

        private final String id;
        private String          outcomeId;
        private Speaker         speaker        = Speaker.AI;
        private String          promptStringId;
        private ExchangeTrigger trigger        = ExchangeTrigger.MANUAL;
        private StoryRegion     firstRegion    = StoryRegion.HABITATION_RINGS;
        private StoryRegion     lastRegion     = StoryRegion.CORE;
        private boolean         oneShot        = true;   // exchanges are rare BY DEFAULT
        private Stance          stanceAffinity = null;
        private final List<ExchangeOption> options = new ArrayList<>();

        private Builder(String id) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("exchange id must not be empty");
            }
            this.id = id;
        }

        /** Records this exchange's consequential answer under ANOTHER beat's key (see
         *  {@link ExchangeDefinition#getOutcomeId()}).  Leave unset for the normal case. */
        public Builder outcomeId(String value)          { this.outcomeId      = value; return this; }
        public Builder speaker(Speaker value)           { this.speaker        = value; return this; }
        public Builder promptStringId(String value)     { this.promptStringId = value; return this; }
        public Builder trigger(ExchangeTrigger value)   { this.trigger        = value; return this; }
        public Builder oneShot(boolean value)           { this.oneShot        = value; return this; }
        public Builder stanceAffinity(Stance value)     { this.stanceAffinity = value; return this; }

        public Builder option(ExchangeOption value) {
            this.options.add(value);
            return this;
        }

        /** Restricts this exchange to a single story region. */
        public Builder region(StoryRegion value) {
            this.firstRegion = value;
            this.lastRegion  = value;
            return this;
        }

        /** Restricts this exchange to the inclusive band {@code [first, last]}. */
        public Builder regionBand(StoryRegion first, StoryRegion last) {
            this.firstRegion = first;
            this.lastRegion  = last;
            return this;
        }

        /** Allows this exchange from {@code first} down to the Core. */
        public Builder regionFrom(StoryRegion first) {
            this.firstRegion = first;
            this.lastRegion  = StoryRegion.CORE;
            return this;
        }

        public ExchangeDefinition build() {
            if (promptStringId == null || promptStringId.isEmpty()) {
                throw new IllegalArgumentException("exchange " + id + " has no prompt");
            }
            if (speaker == null) {
                throw new IllegalArgumentException("exchange " + id + " has no speaker");
            }
            if (trigger == null) {
                throw new IllegalArgumentException("exchange " + id + " has no trigger");
            }
            if (firstRegion.ordinal() > lastRegion.ordinal()) {
                throw new IllegalArgumentException("exchange " + id + " has an inverted region band");
            }
            // ANTI-SKIP: there is no blank "next" anywhere in this system, so an exchange with fewer
            // than two answers would be a wall of text with a dismiss button — exactly what order-4
            // exists to avoid.  Three is the cap the panel is sized for.
            if (options.size() < StoryUiConstants.STORY_EXCHANGE_MIN_OPTIONS
                    || options.size() > StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS) {
                throw new IllegalArgumentException("exchange " + id + " has " + options.size()
                        + " options; must be between " + StoryUiConstants.STORY_EXCHANGE_MIN_OPTIONS
                        + " and " + StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS);
            }
            return new ExchangeDefinition(this);
        }
    }
}

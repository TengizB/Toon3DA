package ge.tbegvadze.toon3d.route;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One narrative EVENT (route-map order-10): a curated non-combat beat the player finds on an EVENT
 * node — a distress beacon, an abandoned stash, a surviving tech, a corrupted shrine, a data
 * terminal. It is DATA: a title, the narrative lines shown on the choice overlay, and 1-3
 * {@link EventChoice}s. All behaviour lives in each choice's typed {@link EventEffect}, applied by
 * {@code World} through existing systems — the registry stays headless (no LibGDX / World).
 *
 * <p>Rolled per EVENT node from the node's fixed seed by {@link FacilityEventRegistry#rollFor(long)},
 * so a seed always meets the same events in the same places. Weighted LOW at map-gen (order-2, ~6%)
 * so events stay special. Immutable value object built via {@link Builder}.
 */
public final class FacilityEvent {

    /** Hard cap on choices per event — the overlay lays out at most this many cards. */
    public static final int MAX_CHOICES = 3;

    private final String id;
    private final String title;
    private final float  weight;
    private final List<String> narrativeLines;
    private final List<EventChoice> choices;

    private FacilityEvent(Builder builder) {
        this.id             = Objects.requireNonNull(builder.id, "id");
        this.title          = Objects.requireNonNull(builder.title, "title");
        this.weight         = builder.weight;
        this.narrativeLines = Collections.unmodifiableList(new java.util.ArrayList<>(builder.narrativeLines));
        if (builder.choices.isEmpty()) {
            throw new IllegalArgumentException("Event '" + id + "' needs at least one choice");
        }
        if (builder.choices.size() > MAX_CHOICES) {
            throw new IllegalArgumentException(
                    "Event '" + id + "' has " + builder.choices.size() + " choices; max is " + MAX_CHOICES);
        }
        this.choices = Collections.unmodifiableList(new java.util.ArrayList<>(builder.choices));
    }

    /** Stable id (e.g. "distress_beacon") — used for run-stats logging and seed reproducibility. */
    public String id() {
        return id;
    }

    /** Human-facing event title shown at the top of the choice overlay. */
    public String title() {
        return title;
    }

    /** Relative probability this event is rolled onto an EVENT node. */
    public float weight() {
        return weight;
    }

    /** The narrative flavour lines shown above the choices. Unmodifiable; may be empty. */
    public List<String> narrativeLines() {
        return narrativeLines;
    }

    /** The 1-3 choices offered. Unmodifiable, never empty. */
    public List<EventChoice> choices() {
        return choices;
    }

    /** Starts a builder for an event with the given stable id. */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder — keeps a multi-line, multi-choice event legible at the registration site. */
    public static final class Builder {
        private final String id;
        private String title;
        private float  weight = 1f;
        private final List<String> narrativeLines = new java.util.ArrayList<>();
        private final List<EventChoice> choices = new java.util.ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder weight(float weight) {
            this.weight = weight;
            return this;
        }

        /** Adds one or more narrative flavour lines (in display order). */
        public Builder narrative(String... lines) {
            narrativeLines.addAll(Arrays.asList(lines));
            return this;
        }

        public Builder choice(String label, String description, EventEffect effect) {
            choices.add(new EventChoice(label, description, effect));
            return this;
        }

        public FacilityEvent build() {
            return new FacilityEvent(this);
        }
    }
}

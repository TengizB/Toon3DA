package ge.tbegvadze.toon3d.route;

import java.util.Objects;

/**
 * One selectable option on an EVENT node's choice overlay (route-map order-10): a short label, a
 * one-line description of the trade, and the typed {@link EventEffect} it applies when chosen. The
 * EVENT overlay renders the label + description as a card (reusing the level-up card UI pattern);
 * {@code World} applies the effect through existing systems when the player taps it.
 *
 * <p>Immutable value object. Pure / headless — no LibGDX imports.
 */
public final class EventChoice {

    private final String label;
    private final String description;
    private final EventEffect effect;

    public EventChoice(String label, String description, EventEffect effect) {
        this.label       = Objects.requireNonNull(label, "label");
        this.description = Objects.requireNonNull(description, "description");
        this.effect      = Objects.requireNonNull(effect, "effect");
    }

    /** Short button label (e.g. "ANSWER", "FORCE THE LOCKER"). */
    public String label() {
        return label;
    }

    /** One-line description of the trade this choice makes. */
    public String description() {
        return description;
    }

    /** The typed outcome {@code World} applies when this choice is picked. */
    public EventEffect effect() {
        return effect;
    }
}

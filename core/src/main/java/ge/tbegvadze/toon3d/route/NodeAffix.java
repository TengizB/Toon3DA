package ge.tbegvadze.toon3d.route;

import java.util.Objects;

/**
 * A generic modifier attached to a {@link RouteNode} (for example an ELITE hotzone modifier).
 * Kept intentionally minimal here — order-9 defines the concrete affix catalog and how affixes bias
 * a floor. This value object exists so the data model has a stable slot for one without prescribing
 * its contents.
 */
public final class NodeAffix {

    private final String id;
    private final String displayName;

    public NodeAffix(String id, String displayName) {
        this.id          = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    /** Stable string id — used for save / run-stats logging. */
    public String id() {
        return id;
    }

    /** Human-facing label. */
    public String displayName() {
        return displayName;
    }
}

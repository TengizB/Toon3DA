package ge.tbegvadze.toon3d.route;

import java.util.Objects;

/**
 * Immutable metadata for one {@link RouteNodeType}. Built once at startup and registered in the
 * {@link NodeTypeRegistry}. This is DATA, not behaviour — the overlay, icon painter, and level
 * pipeline all read these fields rather than switching on the node type.
 *
 * <p>Deliberately free of any LibGDX import: colours and icons are referenced by string id
 * ({@link #accentColorId()}, {@link #iconPainterId()}) and resolved to concrete
 * {@code com.badlogic.gdx.graphics.Color} / painters in the render layer (order-4/order-5). That
 * keeps the route package headless-testable.
 *
 * <p>Instances are built with the {@link Builder} to keep the many fields readable at registration.
 */
public final class NodeTypeDefinition {

    private final RouteNodeType type;
    private final String id;
    private final String displayName;
    private final String hintLine;
    private final float baseWeight;
    private final DangerTier dangerTier;
    private final String accentColorId;
    private final boolean forced;
    private final String levelProfileId;
    private final String iconPainterId;

    private NodeTypeDefinition(Builder builder) {
        this.type           = Objects.requireNonNull(builder.type, "type");
        this.id             = Objects.requireNonNull(builder.id, "id");
        this.displayName    = Objects.requireNonNull(builder.displayName, "displayName");
        this.hintLine       = Objects.requireNonNull(builder.hintLine, "hintLine");
        this.baseWeight     = builder.baseWeight;
        this.dangerTier     = Objects.requireNonNull(builder.dangerTier, "dangerTier");
        this.accentColorId  = Objects.requireNonNull(builder.accentColorId, "accentColorId");
        this.forced         = builder.forced;
        this.levelProfileId = Objects.requireNonNull(builder.levelProfileId, "levelProfileId");
        this.iconPainterId  = Objects.requireNonNull(builder.iconPainterId, "iconPainterId");
    }

    /** The node kind this definition describes. */
    public RouteNodeType type() {
        return type;
    }

    /** Stable string id (e.g. "combat") — used for save / seed-share / run-stats logging. */
    public String id() {
        return id;
    }

    /** Human-facing name (e.g. "SUPPLY CACHE"). */
    public String displayName() {
        return displayName;
    }

    /** One-line risk/reward hint template (may contain placeholders resolved per node). */
    public String hintLine() {
        return hintLine;
    }

    /** Relative probability this type is rolled into the random pool. Ignored when {@link #forced()}. */
    public float baseWeight() {
        return baseWeight;
    }

    /** Threat band — drives colour language (order-4) and anti-starvation guards (order-2). */
    public DangerTier dangerTier() {
        return dangerTier;
    }

    /** Key into the overlay palette (order-4). NOT a raw Color — resolved in the render layer. */
    public String accentColorId() {
        return accentColorId;
    }

    /** True for convergence nodes the generator injects deliberately (BOSS, REGION_GATE). */
    public boolean forced() {
        return forced;
    }

    /** Which {@code NodeLevelProfile} (order-7) to use when this node is entered. */
    public String levelProfileId() {
        return levelProfileId;
    }

    /** Which procedural {@code IconPainter} (order-5) draws this node. */
    public String iconPainterId() {
        return iconPainterId;
    }

    /** Starts a builder for the given type. */
    public static Builder builder(RouteNodeType type) {
        return new Builder(type);
    }

    /** Fluent builder — keeps the ten-field registration lines legible. */
    public static final class Builder {
        private final RouteNodeType type;
        private String id;
        private String displayName;
        private String hintLine;
        private float baseWeight;
        private DangerTier dangerTier;
        private String accentColorId;
        private boolean forced;
        private String levelProfileId;
        private String iconPainterId;

        private Builder(RouteNodeType type) {
            this.type = type;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder hintLine(String hintLine) {
            this.hintLine = hintLine;
            return this;
        }

        public Builder baseWeight(float baseWeight) {
            this.baseWeight = baseWeight;
            return this;
        }

        public Builder dangerTier(DangerTier dangerTier) {
            this.dangerTier = dangerTier;
            return this;
        }

        public Builder accentColorId(String accentColorId) {
            this.accentColorId = accentColorId;
            return this;
        }

        public Builder forced(boolean forced) {
            this.forced = forced;
            return this;
        }

        public Builder levelProfileId(String levelProfileId) {
            this.levelProfileId = levelProfileId;
            return this;
        }

        public Builder iconPainterId(String iconPainterId) {
            this.iconPainterId = iconPainterId;
            return this;
        }

        public NodeTypeDefinition build() {
            return new NodeTypeDefinition(this);
        }
    }
}

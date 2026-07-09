package ge.tbegvadze.toon3d.route;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One data row of a {@link RegionPlan}: a themed act band of the descent plus its distribution
 * biases. Immutable — the generator reads it, never mutates it. A region owns three things:
 * <ul>
 *   <li>its depth span ({@link #firstDepth()}..{@link #lastDepth()}, the last being a forced
 *       convergence — a boss when {@code GameMath.isBossFloor(lastDepth)});</li>
 *   <li>per-{@link RouteNodeType} weight multipliers that scale the registry's base weights, so an
 *       act can lean toward caches (early) or elites (deep) WITHOUT hard-coding a node list;</li>
 *   <li>per-{@link GeneratorId} weight multipliers that lean the combat-node biome pool. The pool
 *       itself is always the registry's standard pool, so a newly registered generator appears
 *       automatically at weight 1.0 — a region only biases the ids it names.</li>
 * </ul>
 *
 * <p>Pure data — no LibGDX, no rendering. Built via {@link Builder} to keep the optional bias maps
 * readable at the call site (see {@link RegionPlan#defaultPlan()}).
 */
public final class RegionSpec {

    private final int regionIndex;
    private final String displayName;
    private final String themeId;
    private final int firstDepth;
    private final int lastDepth;
    private final boolean endless;
    private final Map<RouteNodeType, Float> nodeTypeMultipliers;
    private final Map<GeneratorId, Float> generatorWeights;

    private RegionSpec(Builder builder) {
        if (builder.firstDepth > builder.lastDepth) {
            throw new IllegalArgumentException(
                    "Region depth span inverted: firstDepth=" + builder.firstDepth
                            + " > lastDepth=" + builder.lastDepth);
        }
        this.regionIndex         = builder.regionIndex;
        this.displayName         = Objects.requireNonNull(builder.displayName, "displayName");
        this.themeId             = Objects.requireNonNull(builder.themeId, "themeId");
        this.firstDepth          = builder.firstDepth;
        this.lastDepth           = builder.lastDepth;
        this.endless             = builder.endless;
        this.nodeTypeMultipliers = new EnumMap<>(builder.nodeTypeMultipliers);
        this.generatorWeights    = new EnumMap<>(builder.generatorWeights);
    }

    public int regionIndex() {
        return regionIndex;
    }

    public String displayName() {
        return displayName;
    }

    public String themeId() {
        return themeId;
    }

    /** First (inclusive) depth in this band. */
    public int firstDepth() {
        return firstDepth;
    }

    /** Last (inclusive) depth — the forced convergence floor (boss or region gate). */
    public int lastDepth() {
        return lastDepth;
    }

    /** Whether this band belongs to the endless post-game cycle (Region D "THE BREACH"). */
    public boolean endless() {
        return endless;
    }

    /** Whether the given floor depth falls within this band. */
    public boolean containsDepth(int depth) {
        return depth >= firstDepth && depth <= lastDepth;
    }

    /**
     * The weight multiplier this region applies to a node type. Unlisted types are unbiased (1.0),
     * so the roll still reads every registered type at its base weight.
     */
    public float nodeTypeMultiplier(RouteNodeType type) {
        Float multiplier = nodeTypeMultipliers.get(type);
        return multiplier != null ? multiplier : 1.0f;
    }

    /**
     * The weight multiplier this region applies to a standard-pool generator. Unlisted generators
     * are unbiased (1.0) and still appear, which is what keeps the generator pool extensible.
     */
    public float generatorWeight(GeneratorId generatorId) {
        Float weight = generatorWeights.get(generatorId);
        return weight != null ? weight : 1.0f;
    }

    /** Projects this spec onto the minimal {@link RouteRegion} the {@link RouteMap} stores. */
    public RouteRegion toRegion() {
        return new RouteRegion(regionIndex, displayName, firstDepth, lastDepth, themeId);
    }

    public static Builder builder(int regionIndex) {
        return new Builder(regionIndex);
    }

    /** Fluent builder — the bias maps are optional and default to empty (fully unbiased). */
    public static final class Builder {
        private final int regionIndex;
        private String displayName;
        private String themeId;
        private int firstDepth;
        private int lastDepth;
        private boolean endless;
        private final Map<RouteNodeType, Float> nodeTypeMultipliers = new EnumMap<>(RouteNodeType.class);
        private final Map<GeneratorId, Float> generatorWeights      = new EnumMap<>(GeneratorId.class);

        private Builder(int regionIndex) {
            this.regionIndex = regionIndex;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder themeId(String themeId) {
            this.themeId = themeId;
            return this;
        }

        public Builder depthSpan(int firstDepth, int lastDepth) {
            this.firstDepth = firstDepth;
            this.lastDepth  = lastDepth;
            return this;
        }

        public Builder endless(boolean endless) {
            this.endless = endless;
            return this;
        }

        /** Weights a node type up (>1) or down (<1) for this region. */
        public Builder nodeTypeMultiplier(RouteNodeType type, float multiplier) {
            nodeTypeMultipliers.put(type, multiplier);
            return this;
        }

        /** Leans the combat biome pool toward a generator id (>1) for this region. */
        public Builder generatorWeight(GeneratorId generatorId, float weight) {
            generatorWeights.put(generatorId, weight);
            return this;
        }

        public RegionSpec build() {
            return new RegionSpec(this);
        }
    }
}

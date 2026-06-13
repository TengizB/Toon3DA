package ge.tbegvadze.toon3d.status;

/**
 * Immutable per-actor resist/immunity table.
 * Multipliers of 1.0 = normal susceptibility; 0.5 = half damage/duration; 0.0 = effectively immune.
 * isImmune() always takes precedence over any multiplier.
 *
 * Build per-archetype instances once at spawn time via the nested Builder.
 */
public final class StatusResistance {

    private static final int TYPE_COUNT = StatusType.values().length;

    private static final StatusResistance DEFAULT_RESISTANCE = buildDefault();

    private final float[]   damageMultipliers;
    private final float[]   durationMultipliers;
    private final boolean[] immunities;

    private StatusResistance(float[] damage, float[] duration, boolean[] immune) {
        this.damageMultipliers   = damage;
        this.durationMultipliers = duration;
        this.immunities          = immune;
    }

    /** Fraction of incoming DoT damage that actually applies (1.0 = full, 0.5 = half). */
    public float damageMultiplier(StatusType type)   { return damageMultipliers[type.ordinal()]; }

    /** True when this host is completely immune to the given effect type. */
    public boolean isImmune(StatusType type)          { return immunities[type.ordinal()]; }

    /** Fraction of incoming effect duration that applies (1.0 = full, 0.5 = half). */
    public float durationMultiplier(StatusType type) { return durationMultipliers[type.ordinal()]; }

    /** Default table: no resists, no immunities. Shared constant — never mutated. */
    public static StatusResistance defaultResistance() { return DEFAULT_RESISTANCE; }

    private static StatusResistance buildDefault() {
        float[]   damage   = new float[TYPE_COUNT];
        float[]   duration = new float[TYPE_COUNT];
        boolean[] immune   = new boolean[TYPE_COUNT];
        for (int index = 0; index < TYPE_COUNT; index++) {
            damage[index]   = 1.0f;
            duration[index] = 1.0f;
            immune[index]   = false;
        }
        return new StatusResistance(damage, duration, immune);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {

        private final float[]   damage   = new float[TYPE_COUNT];
        private final float[]   duration = new float[TYPE_COUNT];
        private final boolean[] immune   = new boolean[TYPE_COUNT];

        Builder() {
            for (int index = 0; index < TYPE_COUNT; index++) {
                damage[index]   = 1.0f;
                duration[index] = 1.0f;
                immune[index]   = false;
            }
        }

        public Builder immune(StatusType type) {
            immune[type.ordinal()] = true;
            return this;
        }

        public Builder damageMultiplier(StatusType type, float multiplier) {
            damage[type.ordinal()] = multiplier;
            return this;
        }

        public Builder durationMultiplier(StatusType type, float multiplier) {
            duration[type.ordinal()] = multiplier;
            return this;
        }

        public StatusResistance build() {
            return new StatusResistance(damage.clone(), duration.clone(), immune.clone());
        }
    }
}

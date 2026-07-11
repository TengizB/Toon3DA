package ge.tbegvadze.toon3d.route;

/**
 * The typed, data-only OUTCOME of picking one {@link EventChoice} on an EVENT node (route-map
 * order-10). Every field routes through an EXISTING game system — heal, armour, ammo, XP, map reveal,
 * next-floor budget — so there is NO bespoke per-event code path: {@code World} reads these flags and
 * applies each through the same path an item / pickup / scanner already uses. This mirrors how a
 * {@link NodeAffixDefinition} is a bundle of typed modifiers rather than a behaviour, which keeps the
 * {@link FacilityEventRegistry} headless (a choice can't reach {@code World} directly — the route
 * package has no LibGDX / World imports by design).
 *
 * <p>A choice may also be a GAMBLE: when {@link #gambleLootChance()} &gt; 0 {@code World} rolls the
 * node's event seed and applies the effect on success, or a small survivable bite
 * ({@link #gambleFailDamageFraction()}) plus the fail sting on failure — the "force the locker: 60%
 * loot / 40% mimic-ambush" beat, kept always survivable (no outcome loses the run).
 *
 * <p>Immutable value object built via {@link Builder}. Pure / headless — no LibGDX imports.
 */
public final class EventEffect {

    private final float   healFraction;
    private final boolean armourToFull;
    private final int     ammoBoxes;
    private final int     grantXp;
    private final boolean revealNextRegion;
    private final float   nextFloorBudgetBonus;
    private final float   gambleLootChance;
    private final float   gambleFailDamageFraction;
    private final String  resultText;
    private final String  failText;
    private final String  logToken;

    private EventEffect(Builder builder) {
        this.healFraction             = builder.healFraction;
        this.armourToFull             = builder.armourToFull;
        this.ammoBoxes                = builder.ammoBoxes;
        this.grantXp                  = builder.grantXp;
        this.revealNextRegion         = builder.revealNextRegion;
        this.nextFloorBudgetBonus     = builder.nextFloorBudgetBonus;
        this.gambleLootChance         = builder.gambleLootChance;
        this.gambleFailDamageFraction = builder.gambleFailDamageFraction;
        this.resultText               = builder.resultText;
        this.failText                 = builder.failText;
        this.logToken                 = builder.logToken;
    }

    /** Fraction of max HP to restore (0 = none, 1 = full heal). Applied via the medkit heal path. */
    public float healFraction() {
        return healFraction;
    }

    /** Whether to refill the player's armour to full (a surviving tech patches your plating). */
    public boolean armourToFull() {
        return armourToFull;
    }

    /** Ammo boxes to grant, spread across the player's OWNED ammo types (cache-style). */
    public int ammoBoxes() {
        return ammoBoxes;
    }

    /** Flat XP to award through the progression system. */
    public int grantXp() {
        return grantXp;
    }

    /** Whether to reveal the NEXT region on the route map (a free scan, the "answer the beacon" pay). */
    public boolean revealNextRegion() {
        return revealNextRegion;
    }

    /** A multiplier BONUS added to the NEXT floor's encounter budget (e.g. escort +10% = 0.10). */
    public float nextFloorBudgetBonus() {
        return nextFloorBudgetBonus;
    }

    /** Probability (0..1) this effect PAYS OFF; 0 = deterministic (always applies). See class doc. */
    public float gambleLootChance() {
        return gambleLootChance;
    }

    /** On a lost gamble, the fraction of max HP the ambush bite costs (kept survivable). */
    public float gambleFailDamageFraction() {
        return gambleFailDamageFraction;
    }

    /** Feedback line shown after the choice resolves (success path), or {@code null}. */
    public String resultText() {
        return resultText;
    }

    /** Feedback line shown on a lost gamble, or {@code null} (falls back to a generic bite). */
    public String failText() {
        return failText;
    }

    /** Telemetry token appended to the run's route story for this choice, or {@code null}. */
    public String logToken() {
        return logToken;
    }

    /** Whether this effect involves a random gamble roll (vs a deterministic apply). */
    public boolean isGamble() {
        return gambleLootChance > 0f;
    }

    /** Starts an empty effect builder (a choice with no effect is a valid "walk away" option). */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder — every field is optional and defaults to "does nothing". */
    public static final class Builder {
        private float   healFraction;
        private boolean armourToFull;
        private int     ammoBoxes;
        private int     grantXp;
        private boolean revealNextRegion;
        private float   nextFloorBudgetBonus;
        private float   gambleLootChance;
        private float   gambleFailDamageFraction;
        private String  resultText;
        private String  failText;
        private String  logToken;

        public Builder heal(float fraction) {
            this.healFraction = fraction;
            return this;
        }

        public Builder armourToFull() {
            this.armourToFull = true;
            return this;
        }

        public Builder ammoBoxes(int ammoBoxes) {
            this.ammoBoxes = ammoBoxes;
            return this;
        }

        public Builder grantXp(int grantXp) {
            this.grantXp = grantXp;
            return this;
        }

        public Builder revealNextRegion() {
            this.revealNextRegion = true;
            return this;
        }

        public Builder nextFloorBudgetBonus(float bonus) {
            this.nextFloorBudgetBonus = bonus;
            return this;
        }

        public Builder gamble(float lootChance, float failDamageFraction) {
            this.gambleLootChance         = lootChance;
            this.gambleFailDamageFraction = failDamageFraction;
            return this;
        }

        public Builder resultText(String resultText) {
            this.resultText = resultText;
            return this;
        }

        public Builder failText(String failText) {
            this.failText = failText;
            return this;
        }

        public Builder logToken(String logToken) {
            this.logToken = logToken;
            return this;
        }

        public EventEffect build() {
            return new EventEffect(this);
        }
    }
}

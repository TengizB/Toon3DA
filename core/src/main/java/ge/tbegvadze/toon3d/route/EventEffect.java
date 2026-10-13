package ge.tbegvadze.toon3d.route;

/**
 * The typed, data-only OUTCOME of picking one {@link EventChoice} on an EVENT node (route-map order-10), and
 * the hidden payout of a MYSTERY outcome. Balance-overhaul order 6 (F15) re-expressed every reward and cost
 * in the LADDER CURRENCIES the rest of the run already speaks — so a choice reads in the same units as the
 * shop, the level-up screen and the weapon ladder:
 * <ul>
 *   <li>REWARDS: weapon LEVELS on the equipped gun (capped at the floor's level + 1, like the shop rung), a
 *       PERK RANK of a tag the player already carries, a HEAL fraction of max HP, an ARMOUR fraction of max
 *       armour, CREDITS measured in floors of a COMBAT floor's income at the depth;</li>
 *   <li>COSTS: an HP fraction, credits (floors of income), and a hotter next floor (a budget bonus);</li>
 *   <li>plus the two non-currency beats the catalog keeps: a free scan of the next region, and the GAMBLE
 *       (a chance the reward pays off, or a survivable bite).</li>
 * </ul>
 * Every field routes through an EXISTING system ({@code World} — the shop's level-up path, the shop's perk
 * grant, the medkit heal path, the credit wallet), so there is NO bespoke per-event code path, and
 * {@code RouteEconomicsModel.ladderRewardPowerPoints} prices any effect in power points for the audit.
 *
 * <p>Immutable value object built via {@link Builder}. Pure / headless — no LibGDX imports.
 */
public final class EventEffect {

    /** The empty effect — a "walk away" choice, or a mystery outcome with no payout. */
    public static final EventEffect NONE = new Builder().build();

    private final int     weaponLevels;
    private final boolean perkRank;
    private final float   healFraction;
    private final float   armourFraction;
    private final float   creditFloors;
    private final float   healthCostFraction;
    private final float   creditCostFloors;
    private final boolean revealNextRegion;
    private final float   nextFloorBudgetBonus;
    private final float   gambleLootChance;
    private final float   gambleFailDamageFraction;
    private final String  resultText;
    private final String  failText;
    private final String  logToken;

    private EventEffect(Builder builder) {
        this.weaponLevels             = builder.weaponLevels;
        this.perkRank                 = builder.perkRank;
        this.healFraction             = builder.healFraction;
        this.armourFraction           = builder.armourFraction;
        this.creditFloors             = builder.creditFloors;
        this.healthCostFraction       = builder.healthCostFraction;
        this.creditCostFloors         = builder.creditCostFloors;
        this.revealNextRegion         = builder.revealNextRegion;
        this.nextFloorBudgetBonus     = builder.nextFloorBudgetBonus;
        this.gambleLootChance         = builder.gambleLootChance;
        this.gambleFailDamageFraction = builder.gambleFailDamageFraction;
        this.resultText               = builder.resultText;
        this.failText                 = builder.failText;
        this.logToken                 = builder.logToken;
    }

    /** Weapon LEVELS added to the equipped gun (each capped at the floor's level + 1, the shop rung's ceiling). */
    public int weaponLevels() { return weaponLevels; }

    /** Whether one PERK RANK of a tag the player already carries is granted (any unmaxed perk as a fallback). */
    public boolean perkRank() { return perkRank; }

    /** Fraction of max HP to restore (0 = none, 1 = full heal). Applied via the medkit heal path. */
    public float healFraction() { return healFraction; }

    /** Fraction of max ARMOUR to restore (1 = plating to full). */
    public float armourFraction() { return armourFraction; }

    /** Credits granted, in floors of a COMBAT floor's modelled income at the depth (BalanceSchema.combatFloorCreditIncome). */
    public float creditFloors() { return creditFloors; }

    /** COST: fraction of max HP the choice takes (never lethal — World floors the player at 1 HP). */
    public float healthCostFraction() { return healthCostFraction; }

    /** COST: credits taken, in floors of income (never below zero credits). */
    public float creditCostFloors() { return creditCostFloors; }

    /** Whether to reveal the NEXT region on the route map (a free scan, the "answer the beacon" pay). */
    public boolean revealNextRegion() { return revealNextRegion; }

    /** COST: a multiplier BONUS added to the NEXT floor's encounter budget (e.g. escort +10% = 0.10). */
    public float nextFloorBudgetBonus() { return nextFloorBudgetBonus; }

    /** Probability (0..1) this effect PAYS OFF; 0 = deterministic (always applies). */
    public float gambleLootChance() { return gambleLootChance; }

    /** On a lost gamble, the fraction of max HP the ambush bite costs (kept survivable). */
    public float gambleFailDamageFraction() { return gambleFailDamageFraction; }

    /** Feedback line shown after the choice resolves (success path), or {@code null}. */
    public String resultText() { return resultText; }

    /** Feedback line shown on a lost gamble, or {@code null} (falls back to a generic bite). */
    public String failText() { return failText; }

    /** Telemetry token appended to the run's route story for this choice, or {@code null}. */
    public String logToken() { return logToken; }

    /** Whether this effect involves a random gamble roll (vs a deterministic apply). */
    public boolean isGamble() { return gambleLootChance > 0f; }

    /** Starts an empty effect builder (a choice with no effect is a valid "walk away" option). */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder — every field is optional and defaults to "does nothing". */
    public static final class Builder {
        private int     weaponLevels;
        private boolean perkRank;
        private float   healFraction;
        private float   armourFraction;
        private float   creditFloors;
        private float   healthCostFraction;
        private float   creditCostFloors;
        private boolean revealNextRegion;
        private float   nextFloorBudgetBonus;
        private float   gambleLootChance;
        private float   gambleFailDamageFraction;
        private String  resultText;
        private String  failText;
        private String  logToken;

        public Builder weaponLevels(int levels)        { this.weaponLevels = levels; return this; }
        public Builder perkRank()                      { this.perkRank = true; return this; }
        public Builder heal(float fraction)            { this.healFraction = fraction; return this; }
        public Builder armour(float fraction)          { this.armourFraction = fraction; return this; }
        public Builder credits(float floorsOfIncome)   { this.creditFloors = floorsOfIncome; return this; }
        public Builder healthCost(float fraction)      { this.healthCostFraction = fraction; return this; }
        public Builder creditCost(float floorsOfIncome) { this.creditCostFloors = floorsOfIncome; return this; }
        public Builder revealNextRegion()              { this.revealNextRegion = true; return this; }
        public Builder nextFloorBudgetBonus(float bonus) { this.nextFloorBudgetBonus = bonus; return this; }

        public Builder gamble(float lootChance, float failDamageFraction) {
            this.gambleLootChance         = lootChance;
            this.gambleFailDamageFraction = failDamageFraction;
            return this;
        }

        public Builder resultText(String resultText) { this.resultText = resultText; return this; }
        public Builder failText(String failText)     { this.failText = failText; return this; }
        public Builder logToken(String logToken)     { this.logToken = logToken; return this; }

        public EventEffect build() {
            return new EventEffect(this);
        }
    }
}

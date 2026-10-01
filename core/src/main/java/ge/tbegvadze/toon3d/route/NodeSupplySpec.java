package ge.tbegvadze.toon3d.route;

import java.util.Objects;

/**
 * What a floor built for one {@link RouteNodeType} CONTAINS (balance-overhaul order 2, rule S10): its
 * threat, its body count, and the supply the {@code level.SupplyPlanner} derives from the roster it
 * actually fields. One immutable row per node type, registered in {@link NodeSupplySpecRegistry} by
 * {@link NodeSupplySpecs#registerAll} — never a switch. Every number comes from
 * {@code util.BalanceConfig} SECTION 21.
 *
 * <p>The spec is the ONE source both the floor builder and the route ledger read: the generators fill
 * the floor from it, and the {@link RouteEconomics} rows for COMBAT / ELITE / CACHE / SHOP are derived
 * from it, so the map cannot promise one floor and build another.
 *
 * <p>Pure / headless — no LibGDX imports.
 */
public final class NodeSupplySpec {

    /** How the encounter planner fills a floor of this node type (which group templates are eligible). */
    public enum EncounterKind {
        /** Ordinary fights: PACK / FIRETEAM / ESCORT / BATTERY / HUNTER. */
        COMBAT,
        /** The risk-for-a-weapon floor: COMBAT's templates plus the WARBAND anchor. */
        ELITE,
        /** Light resistance (CACHE / SHOP): packs and fireteams only, no anchor. */
        CALM,
        /** No planned enemies (REST / EVENT / BOSS / REGION_GATE — a boss is seeded by World). */
        NONE
    }

    private final RouteNodeType type;
    private final EncounterKind encounterKind;
    private final float   threatScale;
    private final float   bodyScale;
    private final float   ammoRatio;
    private final float   vaultAmmoRatio;
    private final float   drainTarget;
    private final float   armourShare;
    private final boolean guaranteedVest;
    private final float   creditScale;
    private final int     weaponDrops;
    private final float   weaponDropChance;
    private final int     weaponLevelOffsetMin;
    private final int     weaponLevelOffsetMax;
    private final int     weaponTierBonus;
    private final boolean weaponBehindAnchor;
    private final boolean healFloorApplies;
    private final boolean shapeRulesApply;
    private final float   densityMin;
    private final float   densityMax;
    private final boolean footprintLowerHalf;
    private final boolean bossArenaAmmo;

    private NodeSupplySpec(Builder builder) {
        this.type                 = Objects.requireNonNull(builder.type, "type");
        this.encounterKind        = Objects.requireNonNull(builder.encounterKind, "encounterKind");
        this.threatScale          = builder.threatScale;
        this.bodyScale            = builder.bodyScale;
        this.ammoRatio            = builder.ammoRatio;
        this.vaultAmmoRatio       = builder.vaultAmmoRatio;
        this.drainTarget          = builder.drainTarget;
        this.armourShare          = builder.armourShare;
        this.guaranteedVest       = builder.guaranteedVest;
        this.creditScale          = builder.creditScale;
        this.weaponDrops          = builder.weaponDrops;
        this.weaponDropChance     = builder.weaponDropChance;
        this.weaponLevelOffsetMin = builder.weaponLevelOffsetMin;
        if (builder.weaponLevelOffsetMax < builder.weaponLevelOffsetMin) {
            throw new IllegalArgumentException("weapon level offset max " + builder.weaponLevelOffsetMax
                    + " < min " + builder.weaponLevelOffsetMin + " for " + builder.type);
        }
        this.weaponLevelOffsetMax = builder.weaponLevelOffsetMax;
        this.weaponTierBonus      = builder.weaponTierBonus;
        this.weaponBehindAnchor   = builder.weaponBehindAnchor;
        this.healFloorApplies     = builder.healFloorApplies;
        this.shapeRulesApply      = builder.shapeRulesApply;
        this.densityMin           = builder.densityMin;
        this.densityMax           = builder.densityMax;
        this.footprintLowerHalf   = builder.footprintLowerHalf;
        this.bossArenaAmmo        = builder.bossArenaAmmo;
    }

    public static Builder builder(RouteNodeType type, EncounterKind encounterKind) {
        return new Builder(type, encounterKind);
    }

    /** The node type this row describes. */
    public RouteNodeType type()                 { return type; }
    /** Which group templates the encounter planner may use. */
    public EncounterKind encounterKind()        { return encounterKind; }
    /** Multiplier on the floor's Threat-Point CAP (the region dial and node affix ride on top). */
    public float   threatScale()                { return threatScale; }
    /** Multiplier on the E1 body target. */
    public float   bodyScale()                  { return bodyScale; }
    /** Planned ammo DAMAGE as a fraction of the roster's total eHP (S2). */
    public float   ammoRatio()                  { return ammoRatio; }
    /** Extra ammo damage (fraction of roster eHP) placed behind the anchor group (ELITE's vault). */
    public float   vaultAmmoRatio()             { return vaultAmmoRatio; }
    /** Share of the modelled incoming damage the heals leave uncovered (S3); negative = net gain. */
    public float   drainTarget()                { return drainTarget; }
    /** Share of the heal value delivered as armour pickups (S3). */
    public float   armourShare()                { return armourShare; }
    /** Whether the armour share is guaranteed to include a full vest 'A' (ELITE). */
    public boolean guaranteedVest()             { return guaranteedVest; }
    /** Multiplier on the per-floor credit-chip count (S8). */
    public float   creditScale()                { return creditScale; }
    /** Weapon drops a floor carries (S9). */
    public int     weaponDrops()                { return weaponDrops; }
    /** Chance (seeded, per drop) that a planned weapon drop actually appears; 1 = always. */
    public float   weaponDropChance()           { return weaponDropChance; }
    /** Lowest weapon level offset from the floor depth a planned drop rolls (S9). */
    public int     weaponLevelOffsetMin()       { return weaponLevelOffsetMin; }
    /** Highest weapon level offset from the floor depth a planned drop rolls (S9). */
    public int     weaponLevelOffsetMax()       { return weaponLevelOffsetMax; }
    /** Tier floor above the region's minimum drop tier, or -1 for the ordinary region band. */
    public int     weaponTierBonus()            { return weaponTierBonus; }
    /** Whether the weapon drop sits in or past the anchor group's room (C2). */
    public boolean weaponBehindAnchor()         { return weaponBehindAnchor; }
    /** Whether the S4 heal floor applies (false for BOSS / REST / REGION_GATE). */
    public boolean healFloorApplies()           { return healFloorApplies; }
    /** Whether the E4 shape and E5 first-contact rules apply (COMBAT / ELITE). */
    public boolean shapeRulesApply()            { return shapeRulesApply; }
    /** E7 density band low end (enemies per 100 walkable tiles); NaN = not audited. */
    public float   densityMin()                 { return densityMin; }
    /** E7 density band high end; NaN = not audited. */
    public float   densityMax()                 { return densityMax; }
    /** Whether the floor builds to the LOWER half of its region's footprint range (E6, ELITE). */
    public boolean footprintLowerHalf()         { return footprintLowerHalf; }
    /** Whether the ammo plan is the boss arena's R-BOSS-AMMO budget instead of a roster demand (BOSS). */
    public boolean bossArenaAmmo()              { return bossArenaAmmo; }
    /** Whether the density band is audited for this node type. */
    public boolean hasDensityBand()             { return !Float.isNaN(densityMin) && !Float.isNaN(densityMax); }

    @Override public String toString() {
        return "NodeSupplySpec[" + type + " " + encounterKind + " threat=" + threatScale
                + " bodies=" + bodyScale + " ammo=" + ammoRatio + " drain=" + drainTarget + "]";
    }

    /** Fluent builder; every field defaults to an empty, inert floor. */
    public static final class Builder {
        private final RouteNodeType type;
        private final EncounterKind encounterKind;
        private float   threatScale          = 1f;
        private float   bodyScale            = 1f;
        private float   ammoRatio            = 0f;
        private float   vaultAmmoRatio       = 0f;
        private float   drainTarget          = 0f;
        private float   armourShare          = 0f;
        private boolean guaranteedVest       = false;
        private float   creditScale          = 0f;
        private int     weaponDrops          = 0;
        private float   weaponDropChance     = 1f;
        private int     weaponLevelOffsetMin = 0;
        private int     weaponLevelOffsetMax = 0;
        private int     weaponTierBonus      = -1;
        private boolean weaponBehindAnchor   = false;
        private boolean healFloorApplies     = true;
        private boolean shapeRulesApply      = false;
        private float   densityMin           = Float.NaN;
        private float   densityMax           = Float.NaN;
        private boolean footprintLowerHalf   = false;
        private boolean bossArenaAmmo        = false;

        private Builder(RouteNodeType type, EncounterKind encounterKind) {
            this.type          = type;
            this.encounterKind = encounterKind;
        }

        public Builder threat(float scale)                 { this.threatScale = scale; return this; }
        public Builder bodies(float scale)                 { this.bodyScale = scale; return this; }
        public Builder ammoRatio(float ratio)              { this.ammoRatio = ratio; return this; }
        public Builder vaultAmmoRatio(float ratio)         { this.vaultAmmoRatio = ratio; return this; }
        public Builder drainTarget(float target)           { this.drainTarget = target; return this; }
        public Builder armourShare(float share)            { this.armourShare = share; return this; }
        public Builder guaranteedVest()                    { this.guaranteedVest = true; return this; }
        public Builder credits(float scale)                { this.creditScale = scale; return this; }
        public Builder weapons(int drops, int offsetMin, int offsetMax) {
            this.weaponDrops          = drops;
            this.weaponLevelOffsetMin = offsetMin;
            this.weaponLevelOffsetMax = offsetMax;
            return this;
        }
        public Builder weaponDropChance(float chance)      { this.weaponDropChance = chance; return this; }
        public Builder weaponTierBonus(int bonus)          { this.weaponTierBonus = bonus; return this; }
        public Builder weaponBehindAnchor()                { this.weaponBehindAnchor = true; return this; }
        public Builder noHealFloor()                       { this.healFloorApplies = false; return this; }
        public Builder shapeRules()                        { this.shapeRulesApply = true; return this; }
        public Builder density(float minimum, float maximum) {
            this.densityMin = minimum;
            this.densityMax = maximum;
            return this;
        }
        public Builder footprintLowerHalf()                { this.footprintLowerHalf = true; return this; }
        public Builder bossArenaAmmo()                     { this.bossArenaAmmo = true; return this; }

        public NodeSupplySpec build() {
            return new NodeSupplySpec(this);
        }
    }
}

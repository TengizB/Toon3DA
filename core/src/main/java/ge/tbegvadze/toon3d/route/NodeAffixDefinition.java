package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.LevelGenConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The BEHAVIOUR of one ELITE affix (order-9) — or, since floor-twists, of one FLOOR TWIST: the row's
 * {@link AffixScope} says which. A twist is the same idiom on a COMBAT node: the plan-time modifiers
 * (budget scale, scattered owned-ammo boxes, stims, armour shards) are folded by
 * {@link DefaultCombatProfile}; the runtime modifiers (no mini-map, a light multiplier, wake perception,
 * a trail hunter named by archetype) are applied by {@code World} / {@code SimWorld} after the floor is
 * built. Every field defaults to "no effect", so a row declares only what it does, and a fourth twist
 * built from these kinds needs no code change anywhere (A12).
 *
 * <p>Original order-9 description: the BEHAVIOUR of one ELITE affix: a bundle of TYPED modifiers that bias a hotzone floor
 * for flavour + a readable twist. This is the affix twin of {@link NodeTypeDefinition} — the
 * lightweight {@link NodeAffix} token stored on a {@link RouteNode} carries only id + display name;
 * the modifiers live here, looked up from the {@link NodeAffixRegistry} by {@link EliteProfile} at
 * resolve time. No affix has a bespoke code path: an affix is only ever the SUM of these modifiers,
 * every one of which routes through an existing system (hazard barrels, cover columns, armour
 * pickups, the encounter-budget scale), so a new affix "drops in by registration".
 *
 * <p>Where a referenced system is not yet implemented, an affix degrades to the nearest available
 * effect (e.g. OVERCLOCKED's "enemies hit harder" is approximated by spending more of the depth
 * Threat budget, since a per-enemy DPT hook does not exist yet). Immutable; built via {@link Builder}.
 * Pure / headless — no LibGDX imports.
 */
public final class NodeAffixDefinition {

    private final String id;
    private final String displayName;
    private final float  weight;
    private final AffixScope scope;
    private final int    minDepth;
    private final String ruleLine;

    // Floor-twist modifiers (floor-twists R5/R7/R9). All "no effect" by default.
    private final boolean minimapDisabled;
    private final float   lightMultiplier;
    private final int     wakeRadiusTiles;
    private final int     wakeSightTiles;
    private final String  hunterEnemyTypeName;
    private final int     scatteredAmmoBoxes;
    private final int     extraStims;
    private final int     extraArmourShards;

    private final float  budgetScaleMultiplier;
    private final float  radioactiveBarrelWeightBonus;
    private final int    extraRadioactiveBarrels;
    private final int    extraExplosiveBarrels;
    private final int    extraColumns;
    private final int    extraArmourPickups;
    private final int    extraVaultAmmoBoxes;

    private NodeAffixDefinition(Builder builder) {
        this.id                           = Objects.requireNonNull(builder.id, "id");
        this.displayName                  = Objects.requireNonNull(builder.displayName, "displayName");
        this.weight                       = builder.weight;
        this.scope                        = Objects.requireNonNull(builder.scope, "scope");
        this.minDepth                     = builder.minDepth;
        this.ruleLine                     = builder.ruleLine == null ? "" : builder.ruleLine;
        this.minimapDisabled              = builder.minimapDisabled;
        this.lightMultiplier              = builder.lightMultiplier;
        this.wakeRadiusTiles              = builder.wakeRadiusTiles;
        this.wakeSightTiles               = builder.wakeSightTiles;
        this.hunterEnemyTypeName          = builder.hunterEnemyTypeName;
        this.scatteredAmmoBoxes           = builder.scatteredAmmoBoxes;
        this.extraStims                   = builder.extraStims;
        this.extraArmourShards            = builder.extraArmourShards;
        this.budgetScaleMultiplier        = builder.budgetScaleMultiplier;
        this.radioactiveBarrelWeightBonus = builder.radioactiveBarrelWeightBonus;
        this.extraRadioactiveBarrels      = builder.extraRadioactiveBarrels;
        this.extraExplosiveBarrels        = builder.extraExplosiveBarrels;
        this.extraColumns                 = builder.extraColumns;
        this.extraArmourPickups           = builder.extraArmourPickups;
        this.extraVaultAmmoBoxes          = builder.extraVaultAmmoBoxes;
    }

    /**
     * Extra owned-ammo boxes this affix adds to the ELITE vault — the REWARD side of the affix.
     * An affix that raises {@link #budgetScaleMultiplier()} must pay for the threat it adds
     * (R-RISK-PREMIUM, new-game-balancr order 7): SWARM and OVERCLOCKED price their vault up with
     * them, so an affixed elite is never a sucker bet. Fulfilled by {@link EliteProfile} through
     * the same owned-aware {@link AmmoCacheRequest} seam the base vault uses.
     */
    public int extraVaultAmmoBoxes() {
        return extraVaultAmmoBoxes;
    }

    /** Stable id — used for the {@link NodeAffix} token, save / run-stats logging, and lookup. */
    public String id() {
        return id;
    }

    /** Human-facing label shown on the ELITE icon's affix tag and appended to the map hint. */
    public String displayName() {
        return displayName;
    }

    /** Relative probability this affix is drawn from the pool. */
    public float weight() {
        return weight;
    }

    /** Which slot this row fills: an ELITE hotzone affix or a COMBAT floor twist. */
    public AffixScope scope() {
        return scope;
    }

    /** The shallowest RAW depth this row may roll on (R3). 1 = any depth. */
    public int minDepth() {
        return minDepth;
    }

    /** The short rule line the route console's confirm bar prints after the name (R14); never null. */
    public String ruleLine() {
        return ruleLine;
    }

    /** R7a: true when the floor's mini-map (and player dot) is replaced by the NAV OFFLINE plate. */
    public boolean minimapDisabled() {
        return minimapDisabled;
    }

    /** R7b: the per-Level tile-brightness multiplier (1.0 = unchanged; the exit is always exempt). */
    public float lightMultiplier() {
        return lightMultiplier;
    }

    /** R7d: the dormant-wake proximity radius in tiles, or -1 for the default. */
    public int wakeRadiusTiles() {
        return wakeRadiusTiles;
    }

    /** R7d: the dormant-wake sight range in tiles, or -1 for the default. */
    public int wakeSightTiles() {
        return wakeSightTiles;
    }

    /** True when either wake-perception value overrides the default. */
    public boolean overridesWakePerception() {
        return wakeRadiusTiles >= 0 || wakeSightTiles >= 0;
    }

    /**
     * R9: the {@code EnemyType} NAME of the trail hunter this twist prints, or {@code null} for none. A name,
     * resolved by World / SimWorld, so the headless route package never imports the enemy package.
     */
    public String hunterEnemyTypeName() {
        return hunterEnemyTypeName;
    }

    /** R5: extra owned-ammo boxes scattered over a twisted COMBAT floor (fulfilled by World's cache path). */
    public int scatteredAmmoBoxes() {
        return scatteredAmmoBoxes;
    }

    /** R5: extra '+' stim packs scattered over a twisted COMBAT floor. */
    public int extraStims() {
        return extraStims;
    }

    /** R5: extra 'a' armour shards scattered over a twisted COMBAT floor. */
    public int extraArmourShards() {
        return extraArmourShards;
    }

    /**
     * R5: the twist's scattered pickups as ordinary guarantees, existing symbols only ('+' stim pack, 'a'
     * armour shard — docs/tile-symbols.txt). The owned-ammo boxes are NOT here: they are an
     * {@link AmmoCacheRequest}, because only World knows the arsenal.
     */
    public List<GuaranteedContent> twistGuarantees(long seed) {
        List<GuaranteedContent> extras = new ArrayList<>();
        if (extraStims > 0) {
            extras.add(Guarantees.pickup('+', extraStims, Placement.SCATTERED, seed));
        }
        if (extraArmourShards > 0) {
            extras.add(Guarantees.pickup('a', extraArmourShards, Placement.SCATTERED, seed));
        }
        return extras;
    }

    /** The lightweight token stored on a {@link RouteNode} once this affix rolls. */
    public NodeAffix toToken() {
        return new NodeAffix(id, displayName);
    }

    /** Multiplier this affix applies on top of the ELITE base encounter-budget scale (1.0 = none). */
    public float budgetScaleMultiplier() {
        return budgetScaleMultiplier;
    }

    /** Folds this affix's config biases into the ELITE floor config (hazard weight, in place). */
    public void applyToConfig(LevelGenConfig config) {
        if (config == null) {
            return;
        }
        if (radioactiveBarrelWeightBonus > 0f) {
            config.radioactiveBarrels    = true;
            config.radioactiveBarrelWeight += radioactiveBarrelWeightBonus;
        }
        if (extraColumns > 0) {
            config.columns = true;
        }
    }

    /**
     * The extra post-generation content this affix promises, stamped like any other guarantee. All
     * symbols are from {@code docs/tile-symbols.txt}: 'g' radioactive barrel, 'E' explosive barrel,
     * 'P' column, 'A'/'a' armour. Scattered so the modifier reads as an environmental twist.
     *
     * @param depth the RAW descent depth (chooses the armour tier)
     * @param seed  the floor seed for deterministic placement
     */
    public List<GuaranteedContent> extraGuarantees(int depth, long seed) {
        List<GuaranteedContent> extras = new ArrayList<>();
        if (extraRadioactiveBarrels > 0) {
            extras.add(Guarantees.prop('g', extraRadioactiveBarrels, Placement.SCATTERED, seed));
        }
        if (extraExplosiveBarrels > 0) {
            extras.add(Guarantees.prop('E', extraExplosiveBarrels, Placement.SCATTERED, seed));
        }
        if (extraColumns > 0) {
            extras.add(Guarantees.prop('P', extraColumns, Placement.SCATTERED, seed));
        }
        if (extraArmourPickups > 0) {
            char armourSymbol = depth >= ge.tbegvadze.toon3d.util.RouteMapConstants.ELITE_ARMOUR_VEST_DEPTH
                    ? 'A' : 'a';
            extras.add(Guarantees.pickup(armourSymbol, extraArmourPickups, Placement.GATED_ROOM, seed));
        }
        return extras;
    }

    /** Starts a builder for an affix with the given stable id + label. */
    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    /** Fluent builder — every modifier defaults to "no effect" so an affix declares only what it does. */
    public static final class Builder {
        private final String id;
        private final String displayName;
        private float weight = ge.tbegvadze.toon3d.util.RouteMapConstants.AFFIX_WEIGHT_DEFAULT;
        private AffixScope scope = AffixScope.ELITE_AFFIX;
        private int     minDepth            = 1;
        private String  ruleLine            = "";
        private boolean minimapDisabled     = false;
        private float   lightMultiplier     = 1f;
        private int     wakeRadiusTiles     = -1;
        private int     wakeSightTiles      = -1;
        private String  hunterEnemyTypeName = null;
        private int     scatteredAmmoBoxes  = 0;
        private int     extraStims          = 0;
        private int     extraArmourShards   = 0;
        private float budgetScaleMultiplier        = 1f;
        private float radioactiveBarrelWeightBonus = 0f;
        private int   extraRadioactiveBarrels      = 0;
        private int   extraExplosiveBarrels        = 0;
        private int   extraColumns                 = 0;
        private int   extraArmourPickups           = 0;
        private int   extraVaultAmmoBoxes          = 0;

        private Builder(String id, String displayName) {
            this.id          = id;
            this.displayName = displayName;
        }

        public Builder weight(float value)                       { this.weight = value; return this; }
        public Builder budgetScaleMultiplier(float value)        { this.budgetScaleMultiplier = value; return this; }
        public Builder radioactiveBarrelWeightBonus(float value) { this.radioactiveBarrelWeightBonus = value; return this; }
        public Builder extraRadioactiveBarrels(int value)        { this.extraRadioactiveBarrels = value; return this; }
        public Builder extraExplosiveBarrels(int value)          { this.extraExplosiveBarrels = value; return this; }
        public Builder extraColumns(int value)                   { this.extraColumns = value; return this; }
        public Builder extraArmourPickups(int value)             { this.extraArmourPickups = value; return this; }
        public Builder extraVaultAmmoBoxes(int value)            { this.extraVaultAmmoBoxes = value; return this; }
        public Builder scope(AffixScope value)                   { this.scope = value; return this; }
        public Builder minDepth(int value)                       { this.minDepth = value; return this; }
        public Builder ruleLine(String value)                    { this.ruleLine = value; return this; }
        public Builder minimapDisabled(boolean value)            { this.minimapDisabled = value; return this; }
        public Builder lightMultiplier(float value)              { this.lightMultiplier = value; return this; }
        public Builder wakePerception(int radiusTiles, int sightTiles) {
            this.wakeRadiusTiles = radiusTiles;
            this.wakeSightTiles  = sightTiles;
            return this;
        }
        public Builder hunterEnemyTypeName(String value)         { this.hunterEnemyTypeName = value; return this; }
        public Builder scatteredAmmoBoxes(int value)             { this.scatteredAmmoBoxes = value; return this; }
        public Builder extraStims(int value)                     { this.extraStims = value; return this; }
        public Builder extraArmourShards(int value)              { this.extraArmourShards = value; return this; }

        public NodeAffixDefinition build() {
            return new NodeAffixDefinition(this);
        }
    }
}

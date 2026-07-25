package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.LevelGenConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The BEHAVIOUR of one ELITE affix (order-9): a bundle of TYPED modifiers that bias a hotzone floor
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

        public NodeAffixDefinition build() {
            return new NodeAffixDefinition(this);
        }
    }
}

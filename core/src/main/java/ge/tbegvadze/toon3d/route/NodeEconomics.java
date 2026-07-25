package ge.tbegvadze.toon3d.route;

import java.util.Objects;

/**
 * The PRICE TAG of one route-map subject — a node type, an ELITE affix, or a MYSTERY outcome
 * (new-game-balancr order 7, "Route Economics"). Registered in {@link NodeEconomicsRegistry}
 * alongside the subject's {@link NodeTypeDefinition} / affix / outcome, exactly the same registry
 * discipline: this is DATA, never a switch.
 *
 * <p>Orders 1-6 priced FLOORS; the player plays a JOURNEY through the branching map, so every
 * node the map can offer must carry a price the balance contract can read. A row declares the
 * three quantities the contract needs, all in units the order-3/4/5 models already speak:
 * <ul>
 *   <li><b>THREAT</b> — {@link #budgetScale()}: the multiplier this subject applies to the raw,
 *       depth-scaled Threat-Point budget (1.0 == a standard COMBAT floor). Never a bypass of the
 *       depth ramp — the route-map DEPTH RAMP INVARIANT still holds.</li>
 *   <li><b>RESOURCES</b> — the guaranteed payoff ({@link #guaranteedAmmoBoxes()},
 *       {@link #guaranteedHealHitPoints()}, {@link #guaranteedHealEffectiveHitPointFraction()},
 *       {@link #creditDelta()}) plus {@link #ordinaryLootScale()}, the share of a standard
 *       floor's ORDINARY room loot this node's floor rolls (a bespoke one-room clinic rolls
 *       far fewer loot slots than a full dungeon).</li>
 *   <li><b>PROGRESS</b> — {@link #upgradeOpportunity()}: the share of one reliable weapon-class
 *       upgrade this node offers. Floor XP is DERIVED from the threat actually faced, so it needs
 *       no field.</li>
 * </ul>
 *
 * <p>{@code ge.tbegvadze.toon3d.util.RouteEconomicsModel} folds a row into one comparable EV
 * (through {@code GameMath.nodeExpectedValue}); {@code BalanceSchema} enforces R-ROUTE-PRICED,
 * R-RISK-PREMIUM, R-CALM-COST, R-MYSTERY-EV, R-PIPS-DERIVED and R-HONEST-SAFE against it.
 * A subject with NO row fails the audit (R-ROUTE-PRICED) — the map can never again gain content
 * the contract cannot see.
 *
 * <p>Immutable, pure / headless — no LibGDX imports. Built with the {@link Builder}, since most
 * rows set only two or three of the fields.
 */
public final class NodeEconomics {

    /** What kind of route subject a row prices. */
    public enum Kind {
        /** A {@link RouteNodeType} — the row is the node's own price tag. */
        NODE,
        /** An ELITE {@link NodeAffix} — the row is a MODIFIER folded onto its host ELITE node. */
        AFFIX,
        /** One {@link MysteryOutcome} row of the hidden table. */
        MYSTERY_OUTCOME
    }

    private final Kind          kind;
    private final String        id;
    private final String        displayName;
    private final RouteNodeType nodeType;
    private final float         budgetScale;
    private final float         ordinaryLootScale;
    private final float         guaranteedAmmoBoxes;
    private final float         guaranteedHealHitPoints;
    private final float         guaranteedHealEffectiveHitPointFraction;
    private final float         creditDelta;
    private final float         guaranteedExperiencePoints;
    private final float         upgradeOpportunity;
    private final boolean       hiddenOutcomeTable;
    private final boolean       forced;
    private final String        delegateId;
    private final float         tableWeight;
    private final String        scanToneId;

    private NodeEconomics(Builder builder) {
        this.kind                = Objects.requireNonNull(builder.kind, "kind");
        this.id                  = Objects.requireNonNull(builder.id, "id");
        this.displayName         = builder.displayName == null ? builder.id : builder.displayName;
        this.nodeType            = builder.nodeType;
        this.budgetScale         = builder.budgetScale;
        this.ordinaryLootScale   = builder.ordinaryLootScale;
        this.guaranteedAmmoBoxes = builder.guaranteedAmmoBoxes;
        this.guaranteedHealHitPoints                 = builder.guaranteedHealHitPoints;
        this.guaranteedHealEffectiveHitPointFraction = builder.guaranteedHealEffectiveHitPointFraction;
        this.creditDelta                = builder.creditDelta;
        this.guaranteedExperiencePoints = builder.guaranteedExperiencePoints;
        this.upgradeOpportunity  = builder.upgradeOpportunity;
        this.hiddenOutcomeTable  = builder.hiddenOutcomeTable;
        this.forced              = builder.forced;
        this.delegateId          = builder.delegateId;
        this.tableWeight         = builder.tableWeight;
        this.scanToneId          = builder.scanToneId;
    }

    /** Which kind of subject this row prices. */
    public Kind kind() {
        return kind;
    }

    /** Stable ledger id — the node type's id ("cache"), the affix id ("swarm"), or the outcome name. */
    public String id() {
        return id;
    }

    /** Human-facing name for the BalanceReport ROUTE ECONOMICS table. */
    public String displayName() {
        return displayName;
    }

    /** The node type this row prices, or {@code null} for AFFIX / MYSTERY_OUTCOME rows. */
    public RouteNodeType nodeType() {
        return nodeType;
    }

    /**
     * Multiplier on the raw depth-scaled Threat-Point budget. For a NODE row this is the node's
     * own scale (1.0 == standard combat); for an AFFIX row it is the MULTIPLIER folded onto the
     * host node's scale (1.0 == threat-neutral).
     */
    public float budgetScale() {
        return budgetScale;
    }

    /** Share of a standard floor's ORDINARY (room + kill sourced) loot this node's floor rolls. */
    public float ordinaryLootScale() {
        return ordinaryLootScale;
    }

    /** Guaranteed ammo boxes this subject stamps (owned-ammo vault / depot payout). */
    public float guaranteedAmmoBoxes() {
        return guaranteedAmmoBoxes;
    }

    /** Guaranteed FLAT healing/armour value in HP (medkits, stims, armour pickups). */
    public float guaranteedHealHitPoints() {
        return guaranteedHealHitPoints;
    }

    /**
     * Guaranteed healing expressed as a FRACTION of the player's effective hit points (the MED-BAY
     * auto-doc heals a share of max HP, so its value rides the player's current survivability
     * rather than staying a flat number as depth scales).
     */
    public float guaranteedHealEffectiveHitPointFraction() {
        return guaranteedHealEffectiveHitPointFraction;
    }

    /** Credits gained (positive) or spent (negative) beyond the ordinary kill + chip income. */
    public float creditDelta() {
        return creditDelta;
    }

    /**
     * XP this subject grants OUTSIDE the floor roster (an EVENT choice's data-terminal payout),
     * authored AT DEPTH 1: the model and {@code World} both re-express it as the same share of a
     * level at the depth actually played (GameMath.depthScaledRewardXp), so a flat number cannot
     * decay to nothing against the geometric level curve. Roster XP needs no field: it is DERIVED
     * from the threat actually faced (order 4's XP_PER_THREAT_POINT model), so it follows
     * {@link #budgetScale()} automatically.
     */
    public float guaranteedExperiencePoints() {
        return guaranteedExperiencePoints;
    }

    /** Share of ONE reliable weapon-class upgrade opportunity this subject offers (0..1). */
    public float upgradeOpportunity() {
        return upgradeOpportunity;
    }

    /** True when the subject resolves through a HIDDEN outcome table (MYSTERY) — drives the GAMBLE tier. */
    public boolean hiddenOutcomeTable() {
        return hiddenOutcomeTable;
    }

    /** True for convergence set-pieces the generator injects (BOSS, REGION_GATE). */
    public boolean forced() {
        return forced;
    }

    /** The ledger id whose economics this row inherits (a delegating MYSTERY outcome), or {@code null}. */
    public String delegateId() {
        return delegateId;
    }

    /** Pool weight inside the hidden table (MYSTERY_OUTCOME rows only; 0 otherwise). */
    public float tableWeight() {
        return tableWeight;
    }

    /** The scan-tone bucket a scanner reveals for this outcome (MYSTERY_OUTCOME rows only). */
    public String scanToneId() {
        return scanToneId;
    }

    /** Starts a NODE row for a registered route node type. */
    public static Builder node(RouteNodeType nodeType, String id) {
        return new Builder(Kind.NODE, id).nodeType(nodeType);
    }

    /** Starts an AFFIX row (a threat/reward modifier folded onto its host node). */
    public static Builder affix(String id) {
        return new Builder(Kind.AFFIX, id).budgetScale(1f).ordinaryLootScale(0f);
    }

    /** Starts a MYSTERY_OUTCOME row of the hidden table. */
    public static Builder mysteryOutcome(String id) {
        return new Builder(Kind.MYSTERY_OUTCOME, id);
    }

    /** Fluent builder — most rows set only two or three fields, so everything else defaults. */
    public static final class Builder {
        private final Kind    kind;
        private final String  id;
        private String        displayName;
        private RouteNodeType nodeType;
        private float         budgetScale       = 1f;
        private float         ordinaryLootScale = 1f;
        private float         guaranteedAmmoBoxes;
        private float         guaranteedHealHitPoints;
        private float         guaranteedHealEffectiveHitPointFraction;
        private float         creditDelta;
        private float         guaranteedExperiencePoints;
        private float         upgradeOpportunity;
        private boolean       hiddenOutcomeTable;
        private boolean       forced;
        private String        delegateId;
        private float         tableWeight;
        private String        scanToneId;

        private Builder(Kind kind, String id) {
            this.kind = kind;
            this.id   = id;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder nodeType(RouteNodeType nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder budgetScale(float budgetScale) {
            this.budgetScale = budgetScale;
            return this;
        }

        public Builder ordinaryLootScale(float ordinaryLootScale) {
            this.ordinaryLootScale = ordinaryLootScale;
            return this;
        }

        public Builder guaranteedAmmoBoxes(float guaranteedAmmoBoxes) {
            this.guaranteedAmmoBoxes = guaranteedAmmoBoxes;
            return this;
        }

        public Builder guaranteedHealHitPoints(float guaranteedHealHitPoints) {
            this.guaranteedHealHitPoints = guaranteedHealHitPoints;
            return this;
        }

        public Builder guaranteedHealEffectiveHitPointFraction(float fraction) {
            this.guaranteedHealEffectiveHitPointFraction = fraction;
            return this;
        }

        public Builder creditDelta(float creditDelta) {
            this.creditDelta = creditDelta;
            return this;
        }

        public Builder guaranteedExperiencePoints(float guaranteedExperiencePoints) {
            this.guaranteedExperiencePoints = guaranteedExperiencePoints;
            return this;
        }

        public Builder upgradeOpportunity(float upgradeOpportunity) {
            this.upgradeOpportunity = upgradeOpportunity;
            return this;
        }

        public Builder hiddenOutcomeTable(boolean hiddenOutcomeTable) {
            this.hiddenOutcomeTable = hiddenOutcomeTable;
            return this;
        }

        public Builder forced(boolean forced) {
            this.forced = forced;
            return this;
        }

        public Builder delegateId(String delegateId) {
            this.delegateId = delegateId;
            return this;
        }

        public Builder tableWeight(float tableWeight) {
            this.tableWeight = tableWeight;
            return this;
        }

        public Builder scanToneId(String scanToneId) {
            this.scanToneId = scanToneId;
            return this;
        }

        public NodeEconomics build() {
            return new NodeEconomics(this);
        }
    }
}

package ge.tbegvadze.toon3d.relic;

import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.progression.PerkEffect;

/**
 * One relic row (R1): id, name, rarity, trigger badge, valuation, optional build-tag affinity, 1..4 typed
 * effects, an effect line and (CURSED only) a cost line. Immutable; built by {@link Builder}.
 */
public final class RelicDefinition {

    private static final int MAX_EFFECTS = 4;

    private final String       id;
    private final String       name;
    private final RelicRarity  rarity;
    private final RelicTrigger trigger;
    private final RelicValuation valuation;
    private final BuildTag     affinity;
    private final PerkEffect[] effects;
    private final String       effectText;
    private final String       costText;

    private RelicDefinition(Builder builder) {
        this.id         = builder.id;
        this.name       = builder.name;
        this.rarity     = builder.rarity;
        this.trigger    = builder.trigger;
        this.valuation  = builder.valuation;
        this.affinity   = builder.affinity;
        this.effects    = builder.effects.clone();
        this.effectText = builder.effectText;
        this.costText   = builder.costText;
    }

    public static Builder builder() { return new Builder(); }

    public String id()                 { return id; }
    public String name()               { return name; }
    public RelicRarity rarity()        { return rarity; }
    public RelicTrigger trigger()      { return trigger; }
    public RelicValuation valuation()  { return valuation; }
    /** The build tag this relic only pays off with, or null. */
    public BuildTag affinity()         { return affinity; }
    /** The effects, as the definition's own array (a copy-free view: callers must not mutate it). */
    public PerkEffect[] effects()      { return effects; }
    public String effectText()         { return effectText; }
    /** The cost line of a CURSED relic; null for every other rarity. */
    public String costText()           { return costText; }

    /** Builder; {@link #build()} throws IllegalStateException on any missing or inconsistent field. */
    public static final class Builder {
        private String         id;
        private String         name;
        private RelicRarity    rarity;
        private RelicTrigger   trigger;
        private RelicValuation valuation;
        private BuildTag       affinity;
        private PerkEffect[]   effects = new PerkEffect[0];
        private String         effectText;
        private String         costText;

        private Builder() { }

        public Builder id(String value)                 { this.id = value; return this; }
        public Builder name(String value)               { this.name = value; return this; }
        public Builder rarity(RelicRarity value)        { this.rarity = value; return this; }
        public Builder trigger(RelicTrigger value)      { this.trigger = value; return this; }
        public Builder valuation(RelicValuation value)  { this.valuation = value; return this; }
        public Builder affinity(BuildTag value)         { this.affinity = value; return this; }
        public Builder effects(PerkEffect... values)    { this.effects = values.clone(); return this; }
        public Builder effectText(String value)         { this.effectText = value; return this; }
        public Builder costText(String value)           { this.costText = value; return this; }

        public RelicDefinition build() {
            require(id != null && !id.isEmpty(), "id");
            require(name != null && !name.isEmpty(), "name");
            require(rarity != null, "rarity");
            require(trigger != null, "trigger");
            require(valuation != null, "valuation");
            require(effectText != null && !effectText.isEmpty(), "effectText");
            require(effects.length >= 1 && effects.length <= MAX_EFFECTS, "effects (1.." + MAX_EFFECTS + ")");
            boolean hasCostText = costText != null && !costText.isEmpty();
            if (rarity.isCursed() && !hasCostText) throw new IllegalStateException("relic '" + id + "': a CURSED relic needs costText");
            if (!rarity.isCursed() && costText != null) throw new IllegalStateException("relic '" + id + "': only a CURSED relic may carry costText");
            return new RelicDefinition(this);
        }

        private void require(boolean condition, String field) {
            if (!condition) throw new IllegalStateException("relic '" + id + "': missing or invalid " + field);
        }
    }
}

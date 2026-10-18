package ge.tbegvadze.toon3d.relic;

import ge.tbegvadze.toon3d.progression.PlayerBuild;
import ge.tbegvadze.toon3d.util.BalanceConfig;

/** The run's held relics (R2): a fixed array, never shrinking during a run. Allocation-free after construction. */
public final class RelicHand {

    private final RelicDefinition[] held = new RelicDefinition[BalanceConfig.RELIC_MAX_HELD];
    private int count;

    /**
     * Bonds a relic: false when it is already held or the hand is full. Otherwise folds its effects into the
     * build ({@link PlayerBuild#addRelicEffects}).
     */
    public boolean add(RelicDefinition definition, PlayerBuild playerBuild) {
        if (count >= held.length || holds(definition)) return false;
        if (!playerBuild.addRelicEffects(definition.effects())) return false;
        held[count++] = definition;
        return true;
    }

    public boolean holds(RelicDefinition definition) {
        for (int index = 0; index < count; index++) {
            if (held[index] == definition) return true;
        }
        return false;
    }

    public RelicDefinition get(int index) { return held[index]; }

    public int count() { return count; }

    /** Empties the hand (a new run). The caller resets the PlayerBuild. */
    public void clear() {
        java.util.Arrays.fill(held, null);
        count = 0;
    }
}

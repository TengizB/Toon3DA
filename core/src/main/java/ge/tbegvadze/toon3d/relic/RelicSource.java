package ge.tbegvadze.toon3d.relic;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * Where an offer comes from (R4). Each constant owns its card count, its allowed rarities, the per-card rarity
 * weights (one row per boss index; aligned with {@link #allowedRarities()}) and whether the offer guarantees a
 * RARE-or-better card. Counts and weights are read from BalanceConfig.
 */
public enum RelicSource {

    RUN_START(BalanceConfig.RELIC_START_OFFER_CARDS,
            new RelicRarity[]{RelicRarity.COMMON},
            new float[][]{{1f}}, false),
    BOSS(BalanceConfig.RELIC_BOSS_OFFER_CARDS,
            new RelicRarity[]{RelicRarity.COMMON, RelicRarity.RARE, RelicRarity.LEGENDARY},
            BalanceConfig.RELIC_BOSS_OFFER_RARITY_WEIGHTS, true),
    MYSTERY_CACHE(BalanceConfig.RELIC_NODE_OFFER_CARDS,
            new RelicRarity[]{RelicRarity.COMMON, RelicRarity.RARE},
            new float[][]{BalanceConfig.RELIC_MYSTERY_OFFER_RARITY_WEIGHTS}, false),
    QUARANTINE_LOCKER(BalanceConfig.RELIC_NODE_OFFER_CARDS,
            new RelicRarity[]{RelicRarity.CURSED},
            new float[][]{{1f}}, false);

    private final int           cardCount;
    private final RelicRarity[] allowedRarities;
    private final float[][]     rarityWeightRows;
    private final boolean       guaranteesRareOrBetter;

    RelicSource(int cardCount, RelicRarity[] allowedRarities, float[][] rarityWeightRows, boolean guaranteesRareOrBetter) {
        this.cardCount              = cardCount;
        this.allowedRarities        = allowedRarities;
        this.rarityWeightRows       = rarityWeightRows;
        this.guaranteesRareOrBetter = guaranteesRareOrBetter;
    }

    public int cardCount() { return cardCount; }

    /** The rarities this source may roll. Shared array: read-only. */
    public RelicRarity[] allowedRarities() { return allowedRarities; }

    /** Per-card rarity weights aligned with {@link #allowedRarities()}; the row is clamped to the table. Read-only. */
    public float[] rarityWeightsFor(int bossIndex) {
        int row = Math.max(0, Math.min(rarityWeightRows.length - 1, bossIndex));
        return rarityWeightRows[row];
    }

    /** True when an offer must contain at least one RARE-or-better card (BOSS). */
    public boolean guaranteesRareOrBetter() { return guaranteesRareOrBetter; }
}

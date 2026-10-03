package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.enemy.EnemyTrait;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The frozen MATCHUP TABLE (balance-overhaul order 3, M3/M4): one multiplier per
 * {@link DamageClass} x {@link EnemyTrait}, looked up by ordinal. Built once by
 * {@link MatchupCatalog} and immutable afterwards, so every read is two array indexes and allocates
 * nothing — safe from the damage path and from {@code render()}.
 *
 * <p>Three views of each cell, all precomputed at construction:
 * <ul>
 *   <li>{@link #rawMultiplier} — the catalog row as registered (BalanceConfig SECTION 22);</li>
 *   <li>{@link #multiplier} — the raw value at the table's strength
 *       ({@link GameMath#matchupMultiplier}); this is what damage applies;</li>
 *   <li>{@link #classify} — the M4 word for the strength-adjusted value.</li>
 * </ul>
 * Plus, per class, the traits it is EFFECTIVE / RESISTED against (the compare card, C5).
 */
public final class MatchupTable {

    private final float[][] rawByTraitAndClass;
    private final float[][] multiplierByTraitAndClass;
    private final MatchupOutcome[][] outcomeByTraitAndClass;
    private final List<List<EnemyTrait>> strongAgainstByClass;
    private final List<List<EnemyTrait>> weakAgainstByClass;

    /**
     * @param rawByTraitAndClass  raw multipliers indexed [trait ordinal][class ordinal]; copied
     * @param strength            the MATCHUP_STRENGTH exponent
     * @param effectiveThreshold  M4 EFFECTIVE threshold (inclusive)
     * @param resistedThreshold   M4 RESISTED threshold (inclusive)
     */
    MatchupTable(float[][] rawByTraitAndClass, float strength,
                 float effectiveThreshold, float resistedThreshold) {
        int traitCount = EnemyTrait.values().length;
        int classCount = DamageClass.values().length;
        this.rawByTraitAndClass = new float[traitCount][classCount];
        this.multiplierByTraitAndClass = new float[traitCount][classCount];
        this.outcomeByTraitAndClass = new MatchupOutcome[traitCount][classCount];
        for (int traitIndex = 0; traitIndex < traitCount; traitIndex++) {
            for (int classIndex = 0; classIndex < classCount; classIndex++) {
                float raw = rawByTraitAndClass[traitIndex][classIndex];
                float adjusted = GameMath.matchupMultiplier(raw, strength);
                this.rawByTraitAndClass[traitIndex][classIndex] = raw;
                this.multiplierByTraitAndClass[traitIndex][classIndex] = adjusted;
                this.outcomeByTraitAndClass[traitIndex][classIndex] = outcomeOf(
                        GameMath.classifyMatchup(adjusted, effectiveThreshold, resistedThreshold));
            }
        }
        List<List<EnemyTrait>> strong = new ArrayList<>(classCount);
        List<List<EnemyTrait>> weak = new ArrayList<>(classCount);
        for (DamageClass damageClass : DamageClass.values()) {
            List<EnemyTrait> strongTraits = new ArrayList<>();
            List<EnemyTrait> weakTraits = new ArrayList<>();
            for (EnemyTrait trait : EnemyTrait.values()) {
                MatchupOutcome outcome = outcomeByTraitAndClass[trait.ordinal()][damageClass.ordinal()];
                if (outcome == MatchupOutcome.EFFECTIVE) {
                    strongTraits.add(trait);
                } else if (outcome == MatchupOutcome.RESISTED) {
                    weakTraits.add(trait);
                }
            }
            strong.add(Collections.unmodifiableList(strongTraits));
            weak.add(Collections.unmodifiableList(weakTraits));
        }
        this.strongAgainstByClass = Collections.unmodifiableList(strong);
        this.weakAgainstByClass = Collections.unmodifiableList(weak);
    }

    private static MatchupOutcome outcomeOf(int classificationCode) {
        if (classificationCode == GameMath.MATCHUP_CLASS_EFFECTIVE) {
            return MatchupOutcome.EFFECTIVE;
        }
        if (classificationCode == GameMath.MATCHUP_CLASS_RESISTED) {
            return MatchupOutcome.RESISTED;
        }
        return MatchupOutcome.NEUTRAL;
    }

    /** The registered (pre-strength) multiplier of a cell; 1.0 for a cell no row registered. */
    public float rawMultiplier(DamageClass damageClass, EnemyTrait trait) {
        return rawByTraitAndClass[trait.ordinal()][damageClass.ordinal()];
    }

    /** The multiplier damage applies: {@code GameMath.matchupMultiplier(raw, MATCHUP_STRENGTH)}. */
    public float multiplier(DamageClass damageClass, EnemyTrait trait) {
        return multiplierByTraitAndClass[trait.ordinal()][damageClass.ordinal()];
    }

    /** The M4 word for this cell's strength-adjusted multiplier. */
    public MatchupOutcome classify(DamageClass damageClass, EnemyTrait trait) {
        return outcomeByTraitAndClass[trait.ordinal()][damageClass.ordinal()];
    }

    /** Traits this class is EFFECTIVE against, in trait ordinal order. Unmodifiable, precomputed. */
    public List<EnemyTrait> strongAgainst(DamageClass damageClass) {
        return strongAgainstByClass.get(damageClass.ordinal());
    }

    /** Traits this class is RESISTED by, in trait ordinal order. Unmodifiable, precomputed. */
    public List<EnemyTrait> weakAgainst(DamageClass damageClass) {
        return weakAgainstByClass.get(damageClass.ordinal());
    }
}

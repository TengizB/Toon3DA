package ge.tbegvadze.toon3d.enemy;

/**
 * The TRAIT an enemy presents to the player's damage (balance-overhaul order 3, rule M2) — the row
 * axis of the matchup table, the column axis being {@link ge.tbegvadze.toon3d.entity.DamageClass}.
 * Declared as data by {@link EnemyFamily#trait()}; {@link EnemyType#trait()} defaults to its
 * family's and may be overridden per archetype (none are today — AS1). Bosses carry their family's.
 *
 * <p><b>Ordinal order is load-bearing:</b> it is the row order of the {@code BalanceConfig}
 * SECTION 22 matchup table. The constant itself is the GLYPH ID the health-bar renderer keys its
 * pre-built procedural glyph by (C2).
 */
public enum EnemyTrait {

    /** Aberrations — the neutral baseline; draws no glyph. */
    FLESH(false),
    /** Machines — hexagon glyph. */
    SHIELDED(true),
    /** Golems — square plate glyph. */
    PLATED(true),
    /** Undead — flame glyph. */
    BURNABLE(true),
    /** Insects — segmented arc glyph. */
    CHITIN(true),
    /** Demons — horned ember glyph. */
    INFERNAL(true);

    private final boolean glyph;

    EnemyTrait(boolean glyph) {
        this.glyph = glyph;
    }

    /** Whether the health bar draws a trait glyph for this trait (false only for {@link #FLESH}). */
    public boolean hasGlyph() {
        return glyph;
    }
}

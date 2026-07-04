package ge.tbegvadze.toon3d.enemy;

/**
 * The v1 catalogue of enemy SPECIAL abilities (strategy-combat-order-5).
 *
 * <p>Each entry is a scripted move an archetype can weave into its {@code moveSet()}
 * ({@link EnemyType#moveSet()}). When an enemy commits one it stores it on its
 * {@link PlannedAction} under the {@link IntentVerb#SPECIAL} verb, so it is telegraphed a full
 * turn ahead exactly like every other committed action (order-1) and drawn inside the purple
 * SPECIAL frame with a per-ability sub-glyph (order-2 / {@code EnemyRenderer}).
 *
 * <p>Abilities deploy the EXISTING status mechanics (EMPOWERED, SLOWED, BLINDED) via the shared
 * {@code StatusEffectController}; this catalogue invents enemy MOVES, not status mechanics. The
 * Vulnerable/Weak combat powers referenced by the design doc are deferred to order-6.
 */
public enum SpecialAbility {

    /**
     * Grants the caster EMPOWERED (a temporary outgoing-damage buff). The classic "priority target"
     * telegraph: kill the buffer before its swing lands. Sub-glyph: up-arrow.
     */
    BUFF_SELF(SubGlyph.BUFF, false, true),

    /**
     * Applies a control debuff (SLOWED / BLINDED, resolved per archetype) to the player down a
     * cardinal line, at range. Counter: break the line or kill it. Sub-glyph: down-arrow.
     */
    DEBUFF_PLAYER(SubGlyph.DEBUFF, false, false),

    /**
     * Spawns 1-2 chaff of an EXISTING type on empty adjacent tiles, bounded by a per-summoner cap and
     * a per-room live-enemy ceiling so the encounter budget stays honest. Sub-glyph: spawn-rune.
     */
    SUMMON(SubGlyph.SUMMON, false, false),

    /**
     * A telegraphed slam that strikes every tile within a small cross radius of the enemy on its next
     * turn — step out of the marked tiles to dodge it. Carries a predicted-damage number. Sub-glyph:
     * burst.
     */
    AREA_STRIKE(SubGlyph.AREA, true, false);

    /** The procedural icon drawn inside the SPECIAL frame so each ability reads distinctly (order-2). */
    public enum SubGlyph { BUFF, DEBUFF, SUMMON, AREA }

    private final SubGlyph subGlyph;
    private final boolean  dealsDamage;
    private final boolean  targetsSelf;

    SpecialAbility(SubGlyph subGlyph, boolean dealsDamage, boolean targetsSelf) {
        this.subGlyph    = subGlyph;
        this.dealsDamage = dealsDamage;
        this.targetsSelf = targetsSelf;
    }

    /** The sub-icon id the intent renderer draws inside the purple SPECIAL frame. */
    public SubGlyph telegraphSubGlyph() { return subGlyph; }

    /** True when the ability carries a predicted-damage number to show above its icon (AREA_STRIKE). */
    public boolean dealsDamage() { return dealsDamage; }

    /** True when the ability affects the caster rather than the player/board (BUFF_SELF). */
    public boolean targetsSelf() { return targetsSelf; }
}

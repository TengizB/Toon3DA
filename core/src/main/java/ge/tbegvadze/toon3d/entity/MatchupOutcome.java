package ge.tbegvadze.toon3d.entity;

/**
 * The three words the matchup layer speaks (balance-overhaul order 3, rule M4). Every piece of
 * matchup communication — hit-word colour, "WEAK POINT"/"RESISTED", the trait glyph tint, the SWITCH
 * hint and the compare card — reads one of these, never a number (AS5).
 */
public enum MatchupOutcome {
    /** Strength-adjusted multiplier at or above {@code BalanceConfig.MATCHUP_EFFECTIVE_THRESHOLD}. */
    EFFECTIVE,
    /** Strictly between the two thresholds. */
    NEUTRAL,
    /** Strength-adjusted multiplier at or below {@code BalanceConfig.MATCHUP_RESISTED_THRESHOLD}. */
    RESISTED
}

package ge.tbegvadze.toon3d.progression;

/**
 * The four core player attributes that drive combat multipliers across all systems.
 *
 * Each attribute feeds one or more derived values (see {@link PlayerStats}):
 *   STRENGTH     → melee damage multiplier
 *   AGILITY      → action duration multiplier + dodge chance
 *   TOUGHNESS    → max HP bonus + flat damage reduction
 *   MARKSMANSHIP → ranged damage multiplier + accuracy multiplier
 *
 * The ordinal of each constant is used as the array index inside PlayerStats.
 * Adding a new attribute requires: adding it here, extending the cap array in
 * PlayerStats, and adding GameBalance constants for its per-point effect values.
 */
public enum Attribute {
    STRENGTH,
    AGILITY,
    TOUGHNESS,
    MARKSMANSHIP
}

package ge.tbegvadze.toon3d.status;

/**
 * The status effect types. Order determines iteration priority in tickAll.
 *
 * <p>The first seven are the roguelike_order_8 core (DoT + control + EMPOWERED). The final three
 * are the strategy-combat-order-6 tactical-modifier POWERS: passive multipliers/flags that carry no
 * per-turn tick action of their own — they are read by the shared damage-mitigation pipeline
 * (docs/balance-rule-system.txt) rather than dealing damage on tick. They reuse the exact same
 * stack/refresh/expiry machinery as the core effects (no new tick engine):
 *   VULNERABLE — the host takes +X% damage from incoming hits (offensive setup power).
 *   WEAK       — the host deals -X% damage while active (defensive debuff).
 *   EXPOSED    — the next hit into the host ignores its Block (the anti-turtle answer).
 *
 * <p>WARNING — anything that indexes a fixed-size array by {@code StatusType.ordinal()} (the
 * per-effect colour tables in {@code HudRenderer} and {@code StatusEffectVignetteRenderer}) must be
 * extended when a value is added here, or it will read out of bounds. HudRenderer has a static
 * fail-fast guard for exactly this.
 */
public enum StatusType {
    BURNING,
    POISONED,
    BLEED,
    STUNNED,
    BLINDED,
    SLOWED,
    EMPOWERED,
    VULNERABLE,
    WEAK,
    EXPOSED
}

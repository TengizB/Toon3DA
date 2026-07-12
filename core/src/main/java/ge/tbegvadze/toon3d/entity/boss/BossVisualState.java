package ge.tbegvadze.toon3d.entity.boss;

/**
 * A lightweight, presentation-only readout of the boss's current tactical beat
 * (boss-fight-mobile-overseer ORDER 8, OPEN QUESTION 1 — the recommended enum flag over reaching into
 * the brain's internals). {@code BossFloorController} sets this each tick from the move it actually
 * EXECUTED, so it always mirrors what the player is seeing this turn. The HUD ({@code BossHudRenderer})
 * reads it to draw the small state label under the boss name; nothing in the simulation reads it, so it
 * never affects gameplay logic.
 */
public enum BossVisualState {
    /** Not yet awoken — no label shown. */
    DORMANT("", false),
    /** Repositioning / closing distance / poking (dash, reposition, melee, planted holds). */
    STALKING("STALKING", false),
    /** Charge wind-up or the lunge itself — the telegraphed line strike. */
    CHARGING("CHARGING", false),
    /** Seeding a fire/toxic hazard footprint (wind-up or the cast). */
    CASTING("CASTING", false),
    /** Summoning a pack of adds. */
    SUMMONING("SUMMONING", false),
    /** Mid one-time repair chain (ORDER 5) — the "go stop it" beat. */
    REPAIRING("REPAIRING", false),
    /** Phase-2 transition — the enraged escalation. */
    ENRAGED("ENRAGED", true);

    /** Short text shown under the boss name in the HUD. Empty means "draw nothing". */
    public final String label;
    /** True for the enraged beat, so the HUD can tint the label hotter. */
    public final boolean hot;

    BossVisualState(String label, boolean hot) {
        this.label = label;
        this.hot   = hot;
    }
}

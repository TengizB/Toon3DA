package ge.tbegvadze.toon3d.entity.boss;

/**
 * One-time self-repair state for a boss (boss-fight-mobile-overseer ORDER 5).
 *
 * <p>The Overseer may heal exactly ONCE per fight. When it drops below the HP threshold (and hasn't
 * healed yet) the brain ({@code HunterKillerPattern}) breaks the boss off with a flee dash, commits the
 * {@link #healUsed} latch, and then emits a HEAL {@link BossMove} each turn for {@code OVERSEER_HEAL_TURNS}
 * turns. Taking ANY damage during the chain calls {@link #interrupt()} (from {@code Boss.applyDamage}),
 * which cancels the remaining repair ticks — and because {@link #healUsed} is already set, the ability is
 * spent for the rest of the fight either way.
 *
 * <p>This state lives on the {@link Boss} rather than inside the brain so that:
 * <ul>
 *   <li>the once-per-fight latch survives the phase-1 &rarr; phase-2 transition (the two
 *       {@code HunterKillerPattern} instances share it through the boss); and</li>
 *   <li>the interrupt in {@code Boss.applyDamage} can reach it without the entity layer importing the
 *       AI brain.</li>
 * </ul>
 *
 * <p>The two {@code healJust*} flags are one-shot signals the entity/AI layer raises for the
 * {@code BossFloorController} to turn into HUD banners + event text (the entity/render split — no LibGDX
 * imports here). The controller consumes and clears them.
 */
public final class RegenState {

    /** Once-per-fight latch: true after the heal has been committed (set on the flee step). */
    public boolean healUsed = false;

    /** True while the boss is in the repair chain (flee step + repair ticks). Drives the interrupt + HUD. */
    public boolean healActive = false;

    /** Remaining repair ticks in the current chain (starts at {@code OVERSEER_HEAL_TURNS}). */
    public int healTurnsLeft = 0;

    /** One-shot signal: the repair chain just started this turn (controller shows the "REPAIRING" banner). */
    public boolean healJustStarted = false;

    /** One-shot signal: the repair chain was just interrupted (controller shows "REPAIR INTERRUPTED"). */
    public boolean healJustInterrupted = false;

    /**
     * Cancels an in-progress repair chain. No-op when no chain is active, so incidental damage outside a
     * heal never raises the interrupt signal. The {@link #healUsed} latch is deliberately left set — the
     * ability stays spent for the rest of the fight.
     */
    public void interrupt() {
        if (!healActive) return;
        healActive          = false;
        healTurnsLeft       = 0;
        healJustInterrupted = true;
    }
}

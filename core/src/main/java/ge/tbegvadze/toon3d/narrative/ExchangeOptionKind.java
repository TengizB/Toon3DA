package ge.tbegvadze.toon3d.narrative;

/**
 * What an exchange option COSTS the story (Story UI order-4).  Most are cheap; a few are not, and
 * keeping the difference explicit in data is what stops the expensive ones multiplying.
 *
 * <p>Headless: no LibGDX imports.
 */
public enum ExchangeOptionKind {

    /**
     * The common case, and the cheap one: how the player answers IN CHARACTER — defiant, curious,
     * obedient, cold.  It changes the immediate reply and nudges the hidden {@link Stance} model.
     * No plot branches.  It exists so the player speaks instead of skipping.
     */
    STANCE,

    /**
     * Curiosity, paid for: the player picks which question to ask.  A probe returns a real answer
     * and may unlock a codex entry or reveal a small reward — reading loots, so reading-averse
     * players read (story/08-storytelling-delivery.md, lever #3).
     */
    PROBE,

    /**
     * Rare and heavy: the answer actually changes something the game remembers.  Recorded as a
     * persistent OUTCOME keyed by the exchange id, which order-5 (which ending fires) and order-8
     * (what surrounds it) read back.  Keep these hand-placed and few.
     */
    CONSEQUENTIAL
}

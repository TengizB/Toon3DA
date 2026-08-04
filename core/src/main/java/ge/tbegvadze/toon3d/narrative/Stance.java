package ge.tbegvadze.toon3d.narrative;

/**
 * The three hidden LEANINGS the player expresses by how they answer an exchange (Story UI order-4).
 *
 * <p>This is what makes a cheap choice feel meaningful.  Most exchange options change nothing about
 * the plot — they change how the player SPEAKS, and a small nudge here is what that speech is worth.
 * The model is deliberately tiny: three signed counters, persisted in {@link StoryProgress}, and
 * that is all.
 *
 * <h3>The rules (mandatory)</h3>
 * <ul>
 *   <li><b>Invisible.</b> There is no meter, no notification, no number on screen anywhere.  The
 *       player feels their leaning; they never manage it.</li>
 *   <li><b>Never a hard gate.</b> Stance must NEVER lock content, block an option or decide an
 *       ending.  It only flavours: which of several eligible exchanges ORA opens
 *       ({@link ExchangeDefinition#getStanceAffinity()}), and — at the core — which ending the run
 *       resonates with.  Every ending stays fully open to every player ("no correct ending",
 *       story/06-endings.md).</li>
 *   <li><b>Persistent, like the rest of the story.</b> It rides {@link StoryProgress}, so a death
 *       does not wipe who the player has been.  See the roguelike story-gating rule.</li>
 * </ul>
 *
 * <p>Headless: no LibGDX imports.
 */
public enum Stance {

    /** Toward the PLANET — empathy, listening, believing the voice below. */
    PLANET,
    /** Toward the ORGANIZATION — obedience, the mission, the briefing's words. */
    ORGANIZATION,
    /** Bond with ORA — trust and warmth toward the one voice that persists across deaths. */
    ORA
}

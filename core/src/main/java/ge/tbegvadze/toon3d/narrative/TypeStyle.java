package ge.tbegvadze.toon3d.narrative;

/**
 * How a speaker's type LOOKS and READS — the "tone of type" column in the Story UI
 * order-1 spec.  Headless: carries only presentation intent flags; the render side
 * (render/StoryPanelRenderer) reads these to pick capitalisation, letter-spacing and
 * jitter.  Legibility always wins — the flags describe subtle accents, never anything
 * that makes the text hard to read on a phone at arm's length.
 *
 *   FRIENDLY        — AI assistant: clean, rounded, friendly.  No transform.
 *   UNSTABLE        — The Planet: slightly spaced + a tiny jitter, "not a person," but
 *                     still fully readable.
 *   CONDENSED_CAPS  — The Organization: hard, condensed, ALL-CAPS.
 *   MONOSPACE       — The System: neutral machine status text (terminal feel).
 */
public enum TypeStyle {
    FRIENDLY      (false, 0f,   false),
    UNSTABLE      (false, 2f,   true),
    CONDENSED_CAPS(true,  1f,   false),
    MONOSPACE     (false, 1.5f, false);

    private final boolean upperCase;
    private final float   extraLetterSpacing;
    private final boolean jitter;

    TypeStyle(boolean upperCase, float extraLetterSpacing, boolean jitter) {
        this.upperCase          = upperCase;
        this.extraLetterSpacing = extraLetterSpacing;
        this.jitter             = jitter;
    }

    /** True when the renderer should uppercase this speaker's text (the Organization). */
    public boolean isUpperCase() { return upperCase; }

    /** Extra spacing (world units) the renderer may add between glyphs for this style. */
    public float getExtraLetterSpacing() { return extraLetterSpacing; }

    /** True when a tiny, bounded vertical jitter is applied (the Planet's "unstable" feel). */
    public boolean hasJitter() { return jitter; }
}

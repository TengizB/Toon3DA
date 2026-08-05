package ge.tbegvadze.toon3d.render;

/**
 * The story layer's non-speaker sounds (Story UI order-7 Part D).  A speaker sting says WHO is
 * talking; a cue says what the INTERFACE just did.
 *
 * <p>Ordinals index the {@code STORY_CUE_*} arrays in
 * {@link ge.tbegvadze.toon3d.util.StoryUiConstants} — add a cue here and its four numbers there,
 * in the same order.  Every cue is redundant polish over something already drawn: the game is fully
 * understandable with sound off, and the whole layer has one switch in the codex's settings strip.
 */
public enum StoryCue {

    /** A story panel took the screen — the exchange opening.  Gentle: an invitation, not an alarm. */
    PANEL_OPEN,

    /** An answer was committed.  Firm and low, so a choice feels like a switch being thrown. */
    CHOICE_PICKED,

    /** One more status line printed on the boot card.  The quietest sound in the game. */
    REVEAL_TICK
}

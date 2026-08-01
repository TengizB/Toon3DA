package ge.tbegvadze.toon3d.narrative;

/**
 * The tiny procedural glyph drawn in a speaker chip so the player recognises WHO is
 * talking without reading the name (Story UI order-1 — the four speakers each get one
 * icon idea).  Headless: this is a pure identity token; the actual ShapeRenderer drawing
 * lives on the render side (render/StoryPanelRenderer), which switches on this value.
 *
 * Icon ideas per speaker (see story/08-storytelling-delivery.md):
 *   FRIENDLY_RING     — AI assistant: a small friendly ring.
 *   CRACKED_EYE       — The Planet: a cracked / watching eye glyph.
 *   CORPORATE_BRACKET — The Organization: a hard corporate bracket pair.
 *   TERMINAL_CARET    — The System: a terminal caret / prompt.
 */
public enum SpeakerIcon {
    FRIENDLY_RING,
    CRACKED_EYE,
    CORPORATE_BRACKET,
    TERMINAL_CARET
}

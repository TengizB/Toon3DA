package ge.tbegvadze.toon3d.narrative;

/**
 * The four voices that talk to the player (story/08-storytelling-delivery.md).  Each one
 * is a single, instantly-recognisable identity: a colour + an icon + a name chip + a way
 * its type reads.  The player must know WHO is talking without reading the name.
 *
 * This enum is HEADLESS (no LibGDX): it carries the identity's data-only parts —
 *   - a stable name STRING ID (localisation rule: never a hardcoded story string in Java;
 *     narrative data references ids only, resolved through {@link StoryStrings}),
 *   - the {@link SpeakerIcon} to draw in the chip,
 *   - the {@link TypeStyle} that governs how the type reads.
 *
 * The per-speaker ACCENT COLOUR and chip GEOMETRY live in util/StoryUiConstants, indexed
 * by this enum's {@link #ordinal()} — so colour stays a render concern and this enum stays
 * headless.  StoryUiConstants documents that index order; it MUST match the declaration
 * order here (AI, PLANET, ORGANIZATION, SYSTEM).  A test guards the two against drift.
 */
public enum Speaker {
    /** The AI assistant — the warm, helpful voice the player lives in.  The main channel. */
    AI          ("story.speaker.ai.name",     SpeakerIcon.FRIENDLY_RING,     TypeStyle.FRIENDLY),
    /** The Planet — fragments and grief, the pull downward.  Feels "not a person." */
    PLANET      ("story.speaker.planet.name",  SpeakerIcon.CRACKED_EYE,       TypeStyle.UNSTABLE),
    /** The Organization — cold, clipped orders and threats.  Rare, so each line lands. */
    ORGANIZATION("story.speaker.org.name",     SpeakerIcon.CORPORATE_BRACKET, TypeStyle.CONDENSED_CAPS),
    /** The System — neutral machine status text: boot cards, pickups, state. */
    SYSTEM      ("story.speaker.system.name",  SpeakerIcon.TERMINAL_CARET,    TypeStyle.MONOSPACE);

    private final String      nameStringId;
    private final SpeakerIcon icon;
    private final TypeStyle    typeStyle;

    Speaker(String nameStringId, SpeakerIcon icon, TypeStyle typeStyle) {
        this.nameStringId = nameStringId;
        this.icon         = icon;
        this.typeStyle    = typeStyle;
    }

    /** Stable localisation id for this speaker's display name (resolve via StoryStrings). */
    public String getNameStringId() { return nameStringId; }

    /** The procedural chip glyph for this speaker. */
    public SpeakerIcon getIcon() { return icon; }

    /** How this speaker's type reads (caps / spacing / jitter). */
    public TypeStyle getTypeStyle() { return typeStyle; }
}

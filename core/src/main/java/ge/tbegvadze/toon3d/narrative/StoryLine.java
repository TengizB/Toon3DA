package ge.tbegvadze.toon3d.narrative;

/**
 * One immutable unit of story: WHO is talking ({@link Speaker}) and a stable localisation
 * STRING ID for what they say.  Headless and data-only.
 *
 * Localisation rule (Story UI order-7, honoured from order-1): narrative data references
 * string ids ONLY — never a hardcoded story string in Java.  The actual text is resolved
 * at delivery time through {@link StoryStrings#get(String)}.
 */
public final class StoryLine {

    private final Speaker speaker;
    private final String  textStringId;

    public StoryLine(Speaker speaker, String textStringId) {
        if (speaker == null)      throw new IllegalArgumentException("speaker must not be null");
        if (textStringId == null) throw new IllegalArgumentException("textStringId must not be null");
        this.speaker      = speaker;
        this.textStringId = textStringId;
    }

    public Speaker getSpeaker()      { return speaker; }

    /** The stable localisation id of the body text; resolve via StoryStrings. */
    public String  getTextStringId() { return textStringId; }
}

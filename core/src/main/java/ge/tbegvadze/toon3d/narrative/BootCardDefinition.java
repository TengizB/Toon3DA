package ge.tbegvadze.toon3d.narrative;

/**
 * One immutable row of the boot-card catalog: what a {@link BootCardVariant} PRINTS (Story UI
 * order-3).  Data only — no behaviour, no LibGDX.  Rows are registered once into a
 * {@link BootCardRegistry} ({@link BootCardCatalog#bootstrap}) and read at runtime by
 * {@link BootCardSystem}; nothing switches on a hard-coded card list.
 *
 * <p>Localisation rule (honoured from order-1): a row carries stable {@code textStringId}s only —
 * never the card text itself.  The lines are resolved at present time through {@link StoryStrings}.
 *
 * <p>THE SYSTEM VOICE NEVER EDITORIALISES.  These lines report machine state and nothing else;
 * their horror is entirely in what the player has learned to read into them, which is why
 * "SOUL RESERVE .......... SUFFICIENT" means nothing on run 1 and everything on run 40
 * (story/dialog/system-cards.md).  Keep any new line flat, technical and unaware.
 */
public final class BootCardDefinition {

    private final BootCardVariant variant;
    private final String[]        systemLineStringIds;
    private final boolean         showsInstanceCounter;
    private final boolean         showsWakeLine;

    private BootCardDefinition(Builder builder) {
        this.variant              = builder.variant;
        this.systemLineStringIds  = builder.systemLineStringIds;
        this.showsInstanceCounter = builder.showsInstanceCounter;
        this.showsWakeLine        = builder.showsWakeLine;
    }

    public BootCardVariant getVariant() { return variant; }

    /**
     * The machine-voice status lines, top to bottom.  Localisation ids — resolve via
     * {@link StoryStrings#get(String)}.  The returned array is the row's own; callers must not
     * mutate it (the system only ever reads it).
     */
    public String[] getSystemLineStringIds() { return systemLineStringIds; }

    public int getSystemLineCount() { return systemLineStringIds.length; }

    /** True when the quiet "INSTANCE #0048" counter is printed under the status lines. */
    public boolean showsInstanceCounter() { return showsInstanceCounter; }

    /**
     * True when ORA speaks over this card.  Only the default reprint does: the endgame cards are
     * the machine talking alone, which is exactly what makes them land.
     */
    public boolean showsWakeLine() { return showsWakeLine; }

    public static Builder builder(BootCardVariant variant) {
        return new Builder(variant);
    }

    /** Fluent builder — every row is built once at bootstrap, never per frame. */
    public static final class Builder {

        private final BootCardVariant variant;
        private String[]              systemLineStringIds  = new String[0];
        private boolean               showsInstanceCounter = true;
        private boolean               showsWakeLine        = false;

        private Builder(BootCardVariant variant) {
            if (variant == null) throw new IllegalArgumentException("boot card variant must not be null");
            this.variant = variant;
        }

        /** The machine-voice status lines, in print order. */
        public Builder systemLines(String... stringIds) {
            this.systemLineStringIds = stringIds != null ? stringIds : new String[0];
            return this;
        }

        public Builder showsInstanceCounter(boolean value) {
            this.showsInstanceCounter = value;
            return this;
        }

        public Builder showsWakeLine(boolean value) {
            this.showsWakeLine = value;
            return this;
        }

        public BootCardDefinition build() {
            if (systemLineStringIds.length == 0) {
                throw new IllegalArgumentException("boot card " + variant + " has no system lines");
            }
            for (String stringId : systemLineStringIds) {
                if (stringId == null || stringId.isEmpty()) {
                    throw new IllegalArgumentException(
                            "boot card " + variant + " has an empty system line string id");
                }
            }
            return new BootCardDefinition(this);
        }
    }
}

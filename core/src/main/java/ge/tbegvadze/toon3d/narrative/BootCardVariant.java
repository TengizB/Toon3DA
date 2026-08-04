package ge.tbegvadze.toon3d.narrative;

/**
 * WHICH reprint card is being shown (Story UI order-3).  The default card frames every single run;
 * the endgame variants exist to SUBVERT the one the player has by then seen a hundred times, and
 * that recognition is the payload (story/dialog/system-cards.md, story/06-endings.md).
 *
 * <p>Each variant is a pure identity token — the status lines it prints, whether it shows the
 * counter and whether ORA speaks over it are all DATA, registered once in {@link BootCardCatalog}
 * as a {@link BootCardDefinition}.  Nothing in the system or the renderer switches on this enum.
 *
 * <p>Two behavioural facts do live here, because they change what the game DOES rather than what it
 * says:
 * <ul>
 *   <li>{@link #showsCard()} — MERGE shows no card at all.  The interface simply stops being the
 *       player's; the absence is the ending.</li>
 *   <li>{@link #allowsReprint()} — FREE and KILL print "NO CHECKPOINT AUTHORIZED FOR REPRINT" and
 *       then do not reload.  There is no CONTINUE on those cards, by design.</li>
 * </ul>
 *
 * <p>Headless: no LibGDX imports.
 */
public enum BootCardVariant {

    /** The card shown on EVERY reprint, including the very first run.  The only one in play today. */
    REPRINT("reprint", true, true),

    /**
     * FREE IT — the soul that fuels the Cradles is spent; the being goes free and nobody is ever
     * reprinted again, the player included.  The card inverts and then nothing reloads.
     */
    ENDING_FREE("free", true, false),

    /**
     * KILL IT — the same absence by the other road: with the being dead there is no reserve left to
     * print from.  Static, then nothing.
     */
    ENDING_KILL("kill", true, false),

    /**
     * OBEY — a cold "reward" card in the Organization's flat voice: reprint priority raised, a
     * standing assignment.  The reward is more of the same, forever, so it still continues.
     */
    ENDING_OBEY("obey", true, true),

    /** MERGE — the card never comes.  The interface stops being the player's; the view is everywhere. */
    ENDING_MERGE("merge", false, false);

    private final String  catalogKey;
    private final boolean showsCard;
    private final boolean allowsReprint;

    BootCardVariant(String catalogKey, boolean showsCard, boolean allowsReprint) {
        this.catalogKey    = catalogKey;
        this.showsCard     = showsCard;
        this.allowsReprint = allowsReprint;
    }

    /**
     * The short, stable token this variant uses inside catalog and localisation ids (e.g. "free" in
     * {@code story.boot.system.free.1}).  NEVER change a shipped one.
     */
    public String getCatalogKey() {
        return catalogKey;
    }

    /** False only for MERGE: no card is presented at all. */
    public boolean showsCard() {
        return showsCard;
    }

    /**
     * False for the FREE and KILL endings: the card renders, then the run does NOT reload.  The
     * renderer draws no CONTINUE and the system never reports a continue request.
     */
    public boolean allowsReprint() {
        return allowsReprint;
    }

    /** True when this variant ends the run — a card the player cannot leave by continuing. */
    public boolean isTerminal() {
        return !allowsReprint;
    }
}

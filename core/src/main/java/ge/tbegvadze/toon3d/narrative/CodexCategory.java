package ge.tbegvadze.toon3d.narrative;

/**
 * The tabs of the CODEX (Story UI order-6 Part A) — the archive's six shelves.
 *
 * <p>The split is by WHAT the player is curious about, not by where an entry came from: someone who
 * wants to know what the Organization actually did opens one tab and reads it end to end, rather
 * than sifting a single chronological pile.  Each tab carries its own localisation id (the
 * localisation rule holds here exactly as it does in every other narrative file — no player-facing
 * text in Java) and a short stable catalog key used inside entry ids and persisted flags.
 *
 * <p>{@link #MEMORIES} is the one that behaves differently: it fills as the block on the player's
 * own past lifts, region by region, so the codex itself shows them reassembling who they were
 * (story/02-player-and-cloning.md).  That is why its entries unlock off region entry rather than off
 * anything the player chose to do.
 *
 * <p>Headless: no LibGDX imports.
 */
public enum CodexCategory {

    /** Facility paperwork — the full text of the terminals ORA reacted to in one line. */
    LOGS("logs", "story.codex.category.logs"),
    /** The crew: who worked here, what they signed, what happened to them. */
    PEOPLE("people", "story.codex.category.people"),
    /** What the planet is, and what it has been trying to say. */
    PLANET("planet", "story.codex.category.planet"),
    /** Deepworks: the business, the words it uses, the ledger it keeps. */
    ORGANIZATION("organization", "story.codex.category.organization"),
    /** ORA herself — what she is, what she is allowed to say, and what she works out anyway. */
    ORA("ora", "story.codex.category.ora"),
    /** The player's original self, recovered a piece at a time as the descent goes deeper. */
    MEMORIES("memories", "story.codex.category.memories");

    private final String catalogKey;
    private final String titleStringId;

    CodexCategory(String catalogKey, String titleStringId) {
        this.catalogKey    = catalogKey;
        this.titleStringId = titleStringId;
    }

    /**
     * The short stable token this category uses inside entry ids and persisted keys.  NEVER change a
     * shipped one — it is baked into saves and into the completion beat's subject key.
     */
    public String getCatalogKey() {
        return catalogKey;
    }

    /** Localisation id of the tab's player-facing name. */
    public String getTitleStringId() {
        return titleStringId;
    }
}

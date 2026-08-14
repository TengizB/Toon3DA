package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.List;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * THE RECAP COMPOSER (narrative-rework order-9 B) — the one piece of writing in this game that the
 * GAME writes rather than an author, and the only dynamic long text the layer owns.
 *
 * <p>The problem it exists for is not a fiction problem.  This is a phone game whose story is told
 * across dozens of sessions, and a player who comes back after a week gets a title screen, a status
 * card and a corridor — nothing anywhere says which region they are in, what they last learned, or
 * what they were told to do.  Every other fact about the story is persisted; their MEMORY of it is
 * the one piece of state the save never held.  This composes the answer out of state the game
 * already keeps.
 *
 * <h3>What it says, in ORA's voice, in this order</h3>
 * <ul>
 *   <li><b>WHERE WE ARE</b> — the deepest region reached, in plain words.</li>
 *   <li><b>THE JOB</b> — the standing order as most recently transmitted.  This text CHANGES across
 *       the descent, which is the point: the Organization's escalation is legible on one page.</li>
 *   <li><b>WHAT WE KNOW</b> — the last few archive entries recovered, by title.</li>
 *   <li><b>WHAT YOU DECIDED</b> — the consequential answers on file, in the player's OWN words (the
 *       option's line, re-resolved), because a decision recap that paraphrases the player is a
 *       decision recap they will argue with.</li>
 * </ul>
 *
 * <h3>What it must never become</h3>
 * A checklist.  No percentages, no completion counts, no "next objective", no markers.  It says
 * where the player IS and what they were TOLD — never what to do next.  The moment a recap acquires
 * an objective it stops being a character summarising and becomes a quest log, which is the one
 * thing the whole series has refused to build (docs/narrative-authority.txt SECTION 7).
 *
 * <p>PURE: state in, string ids out.  It resolves nothing and draws nothing — {@link CodexSystem}
 * turns the ids into text, and it runs once when the page is opened, never on a render path.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class StoryRecap {

    /** How a composed line is drawn.  Two kinds is all a summary needs. */
    public enum RecapLineKind {
        /** One of the four block headings. */
        HEADING,
        /** A line of the block under it. */
        TEXT
    }

    private final CodexRegistry    codexRegistry;
    private final ExchangeRegistry exchangeRegistry;
    private final BarkRegistry     barkRegistry;

    // ---- the composed page, rebuilt on open and never per frame --------------------------------
    private final List<String>        lineStringIds = new ArrayList<>();
    private final List<RecapLineKind> lineKinds     = new ArrayList<>();

    /**
     * @param codexRegistry    resolves a recently-unlocked entry id to its title
     * @param exchangeRegistry resolves a recorded outcome to the answer the player actually gave
     * @param barkRegistry     resolves a missed beat id to the line it would have said
     */
    public StoryRecap(CodexRegistry codexRegistry, ExchangeRegistry exchangeRegistry,
                      BarkRegistry barkRegistry) {
        if (codexRegistry    == null) throw new IllegalArgumentException("codexRegistry must not be null");
        if (exchangeRegistry == null) throw new IllegalArgumentException("exchangeRegistry must not be null");
        if (barkRegistry     == null) throw new IllegalArgumentException("barkRegistry must not be null");
        this.codexRegistry    = codexRegistry;
        this.exchangeRegistry = exchangeRegistry;
        this.barkRegistry     = barkRegistry;
    }

    // -------------------------------------------------------------------------
    // Composing
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the WHERE AM I page from live state.  Called when the entry is opened, so the page a
     * returning player reads is the one their save actually justifies.
     */
    public void composeRecap(StoryProgress progress) {
        lineStringIds.clear();
        lineKinds.clear();
        if (progress == null) return;
        StoryRegion region = progress.getDeepestRegion();

        addHeading(StoryUiConstants.STORY_RECAP_WHERE_HEADING_ID);
        addText(whereStringId(region));

        addHeading(StoryUiConstants.STORY_RECAP_JOB_HEADING_ID);
        addText(jobStringId(region));

        addHeading(StoryUiConstants.STORY_RECAP_KNOW_HEADING_ID);
        addRecentUnlocks(progress);

        addHeading(StoryUiConstants.STORY_RECAP_DECIDED_HEADING_ID);
        addRecordedDecisions(progress);
    }

    /**
     * Rebuilds the "you may have missed this" shelf (order-9 C) — the critical lines the player
     * closed before they could have read them, newest first, in the words they would have read.
     *
     * <p>This is the whole of the STORY half of missed-beat recovery, and it is deliberately the
     * quiet half: a story beat is recovered on an opt-in screen the player had to go and open, and
     * is NEVER re-delivered in play.  Re-showing a story beat is how a narrative layer becomes a
     * thing to escape from; re-showing an INSTRUCTION is how somebody gets unstuck, and that is a
     * different lane entirely (see {@link StoryRecovery}).
     */
    public void composeMissedShelf(StoryProgress progress) {
        lineStringIds.clear();
        lineKinds.clear();
        if (progress == null) return;
        List<String> missed = progress.getMissedBeatIds();
        for (int missedIndex = 0; missedIndex < missed.size(); missedIndex++) {
            BarkDefinition row = barkRegistry.getById(missed.get(missedIndex));
            if (row == null) continue;   // a line cut since the save was written simply drops out
            addText(row.getTextStringId());
        }
        if (lineStringIds.isEmpty()) addText(StoryUiConstants.STORY_RECAP_NOTHING_YET_ID);
    }

    /**
     * The last few entries recovered, by title.  Composed entries are skipped: this page is one of
     * them, and an archive summary that lists itself is a mirror pointed at a mirror.
     */
    private void addRecentUnlocks(StoryProgress progress) {
        List<String> recent = progress.getRecentCodexUnlocks();
        int listed = 0;
        for (int recentIndex = 0; recentIndex < recent.size(); recentIndex++) {
            CodexEntry entry = codexRegistry.getById(recent.get(recentIndex));
            if (entry == null || entry.isComposed()) continue;
            addText(entry.getTitleStringId());
            listed++;
            if (listed >= StoryUiConstants.STORY_RECAP_RECENT_ENTRY_COUNT) break;
        }
        if (listed == 0) addText(StoryUiConstants.STORY_RECAP_NOTHING_YET_ID);
    }

    /**
     * Every consequential answer on file, as the player phrased it.  Walks the exchange CATALOG
     * rather than the outcome store, so nothing else filed under the permanent string record (the
     * ending, the order-9 rings) can ever leak onto this page.
     */
    private void addRecordedDecisions(StoryProgress progress) {
        List<ExchangeDefinition> exchanges = exchangeRegistry.getAll();
        int listed = 0;
        for (int exchangeIndex = 0; exchangeIndex < exchanges.size(); exchangeIndex++) {
            ExchangeDefinition exchange = exchanges.get(exchangeIndex);
            if (!exchange.hasConsequentialOption()) continue;
            String chosenOptionId = progress.getOutcome(exchange.getOutcomeId());
            if (chosenOptionId == null) continue;
            for (int optionIndex = 0; optionIndex < exchange.getOptionCount(); optionIndex++) {
                ExchangeOption option = exchange.getOption(optionIndex);
                if (!chosenOptionId.equals(option.getId())) continue;
                addText(option.getPlayerLineStringId());
                listed++;
                break;
            }
        }
        if (listed == 0) addText(StoryUiConstants.STORY_RECAP_NOTHING_YET_ID);
    }

    /** WHERE WE ARE, one row per region.  Ids are derived, so a sixth region needs no edit here. */
    private static String whereStringId(StoryRegion region) {
        return "story.recap.where." + region.getCatalogKey();
    }

    /** THE JOB as most recently transmitted — one row per region, which IS the escalation. */
    private static String jobStringId(StoryRegion region) {
        return "story.recap.job." + region.getCatalogKey();
    }

    private void addHeading(String stringId) {
        lineStringIds.add(stringId);
        lineKinds.add(RecapLineKind.HEADING);
    }

    private void addText(String stringId) {
        lineStringIds.add(stringId);
        lineKinds.add(RecapLineKind.TEXT);
    }

    // -------------------------------------------------------------------------
    // Read model
    // -------------------------------------------------------------------------

    public int getLineCount() {
        return lineStringIds.size();
    }

    /** The localisation id of one composed line.  Empty string for an index out of range. */
    public String getLineStringId(int lineIndex) {
        return lineIndex >= 0 && lineIndex < lineStringIds.size() ? lineStringIds.get(lineIndex) : "";
    }

    public RecapLineKind getLineKind(int lineIndex) {
        return lineIndex >= 0 && lineIndex < lineKinds.size() ? lineKinds.get(lineIndex)
                                                              : RecapLineKind.TEXT;
    }

    /**
     * Every string id this composer can ever ask for, whatever the save holds — the region rows and
     * the page's own chrome.  The per-save ids (entry titles, the player's own answers, a missed
     * line) belong to the catalogs that own them and are swept there.
     *
     * <p>Exists so the localisation sweep can see a page that is assembled at runtime: without it,
     * the recap is the one screen in the game whose text nobody could check until a player with the
     * right save opened it.
     */
    public static List<String> everyStaticStringId() {
        List<String> ids = new ArrayList<>();
        ids.add(StoryUiConstants.STORY_RECAP_WHERE_HEADING_ID);
        ids.add(StoryUiConstants.STORY_RECAP_JOB_HEADING_ID);
        ids.add(StoryUiConstants.STORY_RECAP_KNOW_HEADING_ID);
        ids.add(StoryUiConstants.STORY_RECAP_DECIDED_HEADING_ID);
        ids.add(StoryUiConstants.STORY_RECAP_NOTHING_YET_ID);
        for (StoryRegion region : StoryRegion.values()) {
            ids.add(whereStringId(region));
            ids.add(jobStringId(region));
        }
        return ids;
    }
}

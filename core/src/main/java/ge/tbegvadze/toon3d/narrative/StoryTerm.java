package ge.tbegvadze.toon3d.narrative;

import java.util.Locale;

/**
 * THE VOCABULARY LADDER (narrative-rework order-3) — every proper noun this fiction owns, with the
 * moment its word is allowed to arrive.
 *
 * <p>Doctrine D2 ({@code docs/narrative-authority.txt}) is <b>plain before proper</b>: no proper noun
 * of this fiction may appear on any channel before its plain-language introduction has been
 * delivered, and that introduction must land at a moment the player can SEE the thing being named.
 * The game used to start at step three — inside the first five minutes it said "instance",
 * "reprint", "soul reserve", "checkpoint", "operator", "contaminant", "cradle", "pattern" and
 * "yield", all undefined, on channels that cannot be re-read (failure F2).  This enum is the fix, and
 * it is DATA rather than vigilance: each row carries
 * <ul>
 *   <li>the tokens that count as "the word appeared" ({@link #matchesToken});</li>
 *   <li>the localisation id of the line that performs the NAMED step;</li>
 *   <li>the earliest {@link StoryRegion} a catalog row may contain the word ({@link #getFreeFrom});</li>
 *   <li>the existing gameplay moment the naming hangs off ({@link #getIntroTrigger});</li>
 *   <li>the codex entry the naming also archives, when one already carries the long version.</li>
 * </ul>
 *
 * <h3>The three steps</h3>
 * <ol>
 *   <li><b>PLAIN</b> — the thing is described in ordinary words, at the moment it first matters.
 *       Most of this step already shipped in order-2 (the cold open states the cloning premise
 *       outright, the first-print card opens on "BACKUP COPY ON FILE / PRINTING NEW BODY").</li>
 *   <li><b>NAMED</b> — the word arrives attached to that description, once.  That is this enum.</li>
 *   <li><b>USED</b> — from then on the word is free, on every channel, including as jargon.</li>
 * </ol>
 *
 * <h3>Why the gate is a BUILD check and not a runtime one</h3>
 * Gating text at delivery time would mean lines silently vanishing in play, which trades a
 * comprehension bug for an invisible-content bug.  Nothing here filters anything at runtime: the
 * region band on the intro row is the only live behaviour, and the ladder itself is audited against
 * the SHIPPED string table (see {@link StoryTermCatalog#firstDisallowedTerm}).
 *
 * <h3>One new idea per panel</h3>
 * A row of any catalog introduces AT MOST ONE unfamiliar thing.  That is why the naming lines below
 * are one line each, why {@link StoryTermCatalog#requestNextIntro} asks for at most one term per
 * gameplay moment, and why "Erebus" waits for the second floor rather than sharing a breath with
 * "the Deepworks".
 *
 * <p>Headless: no LibGDX imports.
 */
public enum StoryTerm {

    /**
     * The loop itself.  Order-2's cold open already does the PLAIN step on run 1 ("You died about an
     * hour ago.  They keep a copy.  This is the new you."), and the first-print card avoids the word
     * entirely — so the naming waits for the run AFTER the player's own first death, which is the
     * first time they have felt what the word means.
     */
    REPRINT(StoryRegion.HABITATION_RINGS, BarkTrigger.RUN_START, null, 2, null,
            "reprint", "reprints", "reprinted", "reprinting",
            "print", "prints", "printed", "printing"),

    /**
     * The job the player holds.  Introduced by USE rather than by a line of its own: the
     * Organization's Region-1 briefing addresses them as one, in the Organization's own voice, which
     * is exactly what the word is — the entry on their file.
     */
    OPERATOR(StoryRegion.HABITATION_RINGS, "story.exchange.rings.briefing.prompt",
             "operator", "operators"),

    /** The facility. */
    DEEPWORKS(StoryRegion.HABITATION_RINGS, BarkTrigger.FLOOR_ARRIVAL, null, 1, null,
              "deepworks"),

    /** The world it is dug into.  Waits a floor, so it never shares a panel with the facility. */
    EREBUS(StoryRegion.HABITATION_RINGS, BarkTrigger.FLOOR_ARRIVAL, null, 1, null,
           "erebus"),

    /**
     * The Organization's word for everything down here that moves.  Named the moment they use it —
     * the standing order at the first region gate — with ORA's aside that it is the word on the
     * form rather than a description.  Catching that is the whole point of D7.
     */
    CONTAMINANT(StoryRegion.HABITATION_RINGS, BarkTrigger.REGION_ENTERED, null, 1, "codex.contaminant",
                "contaminant", "contaminants"),

    /**
     * The company that owns the site, the job and the player.  Like {@link #OPERATOR}, introduced by
     * the briefing that IS them talking; no channel says the word before that.
     */
    ORGANIZATION(StoryRegion.HABITATION_RINGS, "story.exchange.rings.briefing.prompt",
                 "organization", "organisation"),

    /**
     * Whoever is holding the standing channel today.  Named at the first gate order, because the
     * speaker chip on that panel already reads OVERSEER and the player has just been ordered around
     * by it.
     */
    OVERSEER(StoryRegion.HABITATION_RINGS, BarkTrigger.REGION_GATE_ORDER, null, 1, "codex.overseer",
             "overseer", "overseers"),

    /**
     * Which print the player is.  The number's PLAIN explanation is order-2's
     * {@link IntroBeat#REPRINT_COUNTER}, fired at the same quiet stretch; this is the word the
     * paperwork files it under, one panel later.
     */
    INSTANCE(StoryRegion.HABITATION_RINGS, BarkTrigger.CONTROL_HINT, null, 1, "codex.ora.count",
             "instance", "instances"),

    /**
     * The number stamped on operators, the player included.  Named the first time the descent shows
     * them one on something that used to be crew.
     */
    SERIAL(StoryRegion.HABITATION_RINGS, BarkTrigger.ENEMY_FAMILY_FIRST_SEEN, "UNDEAD", 1, null,
           "serial", "serials"),

    /**
     * The stored read they print from.  Held back one run further than {@link #REPRINT}: order-2's
     * reserved wake lines spend a player's first two deaths explaining what a death COSTS, and this
     * is the mechanism behind that, which is a second idea.
     */
    CHECKPOINT(StoryRegion.HABITATION_RINGS, BarkTrigger.RUN_START, null, 3, null,
               "checkpoint", "checkpoints"),

    /** What the harvest floors send up, measured by weight. */
    YIELD(StoryRegion.HARVESTING_GALLERIES, BarkTrigger.REGION_ENTERED, null, 1, "codex.yield.ledger",
          "yield", "yields"),

    /**
     * The metered fuel a print burns.  The idea file bands this to the Reliquary, but order-2 had
     * already put SOUL RESERVE on the reprint card's Harvesting-Galleries status block and given ORA
     * two Galleries wake lines asking what it is — so the word is banded where it actually ships, and
     * the RELIQUARY cradle beat stays the reveal of what the reserve is MADE OF.
     */
    RESERVE(StoryRegion.HARVESTING_GALLERIES, BarkTrigger.FLOOR_ARRIVAL, null, 1, "codex.soul.reserve",
            "reserve", "reserves"),

    /** The machines that print bodies. */
    CRADLE(StoryRegion.RELIQUARY, BarkTrigger.REGION_ENTERED, null, 1, "codex.log.reliquary.service",
           "cradle", "cradles"),

    /** The stored player: the data, not the body. */
    PATTERN(StoryRegion.RELIQUARY, BarkTrigger.REGION_GATE_ORDER, null, 1, null,
            "pattern", "patterns"),

    /** Where the facility stops being facility. */
    WOUND(StoryRegion.WOUND, BarkTrigger.REGION_ENTERED, null, 1, "codex.planet.wound",
          "wound", "wounds");

    private final StoryRegion freeFrom;
    private final String      introStringId;
    private final BarkTrigger introTrigger;
    private final String      momentSubject;
    private final int         minimumReprintCount;
    private final String      codexEntryId;
    private final String[]    matchTokens;

    /**
     * A term whose naming is performed by a line that ALREADY ships on another channel — it borrows
     * that line's string id and registers no bark of its own.  Used where the word introduces itself
     * by use: the Organization's briefing is the Organization talking, and it addresses the player as
     * the operator they are.
     */
    StoryTerm(StoryRegion freeFrom, String borrowedIntroStringId, String... matchTokens) {
        this.freeFrom            = freeFrom;
        this.introStringId       = borrowedIntroStringId;
        this.introTrigger        = null;
        this.momentSubject       = null;
        this.minimumReprintCount = 1;
        this.codexEntryId        = null;
        this.matchTokens         = matchTokens;
    }

    /**
     * A term with a naming line of its own, hung off an EXISTING gameplay moment.  Its string id is
     * derived from the enum name ({@code CRADLE} -&gt; {@code story.term.cradle}), so a row can never
     * be registered against a mistyped id.
     */
    StoryTerm(StoryRegion freeFrom, BarkTrigger introTrigger, String momentSubject,
              int minimumReprintCount, String codexEntryId, String... matchTokens) {
        this.freeFrom            = freeFrom;
        this.introTrigger        = introTrigger;
        this.momentSubject       = momentSubject;
        this.minimumReprintCount = minimumReprintCount;
        this.codexEntryId        = codexEntryId;
        this.matchTokens         = matchTokens;
        this.introStringId       = "story.term." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * The earliest story region in which a catalog row may contain this word.  A row registered in a
     * band that STARTS shallower than this is a plain-before-proper violation, whatever region the
     * player happens to be in when it fires.
     */
    public StoryRegion getFreeFrom() {
        return freeFrom;
    }

    /** Stable localisation id of the line that performs the NAMED step.  Never null. */
    public String getIntroStringId() {
        return introStringId;
    }

    /**
     * The existing gameplay moment the naming hangs off, or null when the word is introduced by a
     * line that already ships on another channel (the Organization's briefing names both itself and
     * the job it is handing out).  No new trigger is introduced for any term.
     */
    public BarkTrigger getIntroTrigger() {
        return introTrigger;
    }

    /**
     * The subject the gameplay moment must carry for this naming to be asked for (an
     * {@code EnemyFamily.name()} for the serial line), or null for "any occurrence of the moment".
     */
    public String getMomentSubject() {
        return momentSubject;
    }

    /**
     * The earliest instance number this naming may be spoken to.  Run 1 is print 1, so a word that
     * only means something to somebody who has died and come back declares 2 or more.
     */
    public int getMinimumReprintCount() {
        return minimumReprintCount;
    }

    /**
     * The codex entry this naming also archives, or null when no shipped entry carries the long
     * version.  {@code CodexEntry.Builder.unlockedByTermIntro()} reads it back, so the pairing is
     * declared exactly once — here.
     */
    public String getCodexEntryId() {
        return codexEntryId;
    }

    /** The short, stable token this term uses inside catalog and localisation ids. */
    public String getCatalogKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The bark row id of the naming line, or null for a borrowed introduction.  Doubles as the
     * persistent one-shot "seen" key, so it must never change once shipped.
     */
    public String getIntroBarkId() {
        return introTrigger != null ? "term." + getCatalogKey() : null;
    }

    /** The subject key the naming row is registered and requested under.  Null when borrowed. */
    public String getSubjectKey() {
        return introTrigger != null ? "TERM_" + name() : null;
    }

    /** True when a save on its {@code reprintCount}-th print is far enough along to hear this. */
    public boolean isAvailableAt(int reprintCount) {
        return reprintCount >= minimumReprintCount;
    }

    /** True when this naming is asked for at a moment carrying {@code requestedSubject}. */
    public boolean matchesMomentSubject(String requestedSubject) {
        if (momentSubject == null) return true;
        return momentSubject.equals(requestedSubject);
    }

    /** True when the deepest region ever reached is deep enough for this word to be free. */
    public boolean isFreeIn(StoryRegion region) {
        return region != null && region.isAtLeast(freeFrom);
    }

    /**
     * True when {@code lowerCaseWord} is one of this term's match tokens.  The caller lower-cases
     * once and strips punctuation, so matching itself is a plain equality scan.
     */
    public boolean matchesToken(String lowerCaseWord) {
        for (int tokenIndex = 0; tokenIndex < matchTokens.length; tokenIndex++) {
            if (matchTokens[tokenIndex].equals(lowerCaseWord)) return true;
        }
        return false;
    }

    /** The tokens that count as "this word appeared", lower case.  Copied — never handed out live. */
    public String[] getMatchTokens() {
        return matchTokens.clone();
    }
}

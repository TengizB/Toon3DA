package ge.tbegvadze.toon3d.narrative;

import java.util.Locale;

/**
 * The VOCABULARY LADDER's catalog half (narrative-rework order-3) — it registers one naming line per
 * {@link StoryTerm}, asks for them at the right moments, and answers the audit question "does this
 * text use a word it has not earned yet".
 *
 * <p>Three jobs, none of which is a switch statement:
 * <ul>
 *   <li><b>Registration.</b> {@link #registerIntroLines} folds one one-shot {@code STORY_CRITICAL}
 *       bark per term into {@link BarkCatalog}'s registry, on the term's own existing trigger.
 *       Adding a term is one row in {@link StoryTerm} plus its string — never an edit here.</li>
 *   <li><b>Scheduling.</b> {@link #requestNextIntro} asks for AT MOST ONE naming per gameplay
 *       moment, which is how "a panel introduces at most one unfamiliar thing" becomes a property of
 *       the schedule rather than a thing a writer has to remember.</li>
 *   <li><b>The audit.</b> {@link #firstDisallowedTerm} scans a resolved line for term tokens and
 *       reports the first one whose word is not free that shallow.  It reads TEXT, so the gate can be
 *       run against the shipped {@code story-strings.properties} rather than against Java —
 *       a writer (or a translator) who puts "Cradle" back into a Region-1 bark is caught with the
 *       term, the id and the region named.</li>
 * </ul>
 *
 * <h3>What is exempt, and why</h3>
 * The naming lines themselves, obviously — a line may say the word it is introducing.  The CODEX is
 * exempt by construction: long text is exactly where terms get explained, and its entries are opt-in
 * and re-readable, which is the opposite of the channel this rule exists to protect.  The game's own
 * TITLE is exempt because a title cannot be gated on a region.  The ENDING cards are exempt because
 * they are only ever reached from the Core, which is deeper than any term's band.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class StoryTermCatalog {

    /** String-id prefix of the archive, which is exempt from the ladder by construction. */
    private static final String CODEX_ID_PREFIX = "story.codex.";
    /** String-id prefix of the endgame status cards, only ever read at the Core. */
    private static final String ENDING_CARD_ID_PREFIX = "story.boot.system.";
    /** The endgame card variants, whose status blocks may use any word the ending has earned. */
    private static final String[] ENDING_CARD_ID_INFIXES = { "free.", "kill.", "obey." };
    /** The game's own name and subtitle — a title cannot wait for a region gate. */
    private static final String[] TITLE_ID_EXEMPTIONS = { "story.title.game", "story.title.tagline" };

    private StoryTermCatalog() {}

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers one naming line per term that has a trigger of its own.  Every row is one-shot for
     * the life of the save and {@link BarkPriority#STORY_CRITICAL}: a definition the player never
     * reads leaves them holding the word for the rest of the game, which is the failure this whole
     * order exists to fix.
     *
     * <p>The band starts at the term's {@code freeFrom} and runs to the Core, never a single region —
     * a player whose first undead is two regions down must still be told what a serial is.
     */
    static void registerIntroLines(BarkRegistry registry) {
        for (StoryTerm term : StoryTerm.values()) {
            if (term.getIntroTrigger() == null) continue;   // introduced by a line that already ships
            registry.register(BarkDefinition.builder(term.getIntroBarkId())
                    .speaker(Speaker.AI)
                    .textStringId(term.getIntroStringId())
                    .trigger(term.getIntroTrigger())
                    .subjectKey(term.getSubjectKey())
                    .regionFrom(term.getFreeFrom())
                    .priority(BarkPriority.STORY_CRITICAL)
                    .tone(BarkTone.LORE)
                    .oneShot(true)
                    .build());
        }
    }

    // -------------------------------------------------------------------------
    // Scheduling
    // -------------------------------------------------------------------------

    /** Convenience for moments that carry no subject. */
    public static boolean requestNextIntro(BarkSystem barkSystem, BarkTrigger trigger) {
        return requestNextIntro(barkSystem, trigger, null);
    }

    /**
     * Asks for the ONE naming line this moment is allowed to carry: the shallowest term still
     * unnamed on {@code trigger} whose word is already free at the depth the story has reached.
     *
     * <p>Stopping at the first UNNAMED term (rather than at the first accepted request) is deliberate
     * — if the bark layer declines the request for a pacing reason, the moment stays quiet and the
     * same term is offered again next time, instead of the schedule skipping ahead to the next word.
     * That is what spreads "the Deepworks" and "Erebus" across the first two floors.
     *
     * @param barkSystem     the bark layer (its {@link StoryProgress} is the one-shot + region gate)
     * @param trigger        the moment that just happened
     * @param momentSubject  what the moment was about (an enemy family name), or null
     * @return true when a naming line was actually queued
     */
    public static boolean requestNextIntro(BarkSystem barkSystem, BarkTrigger trigger,
                                           String momentSubject) {
        if (barkSystem == null || trigger == null) return false;
        StoryProgress progress = barkSystem.getProgress();
        for (StoryTerm term : StoryTerm.values()) {
            if (term.getIntroTrigger() != trigger)                        continue;
            if (!term.matchesMomentSubject(momentSubject))                continue;
            if (!term.isFreeIn(progress.getDeepestRegion()))              continue;
            if (!term.isAvailableAt(progress.getReprintCount()))          continue;
            if (progress.hasSeen(term.getIntroBarkId()))                  continue;
            return barkSystem.request(trigger, term.getSubjectKey());
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // The audit — the rule as data, checked against the SHIPPED table
    // -------------------------------------------------------------------------

    /**
     * The naming line that archives {@code codexEntryId}, or null when no term names that entry.
     * The pairing is declared exactly once, on {@link StoryTerm}; this is how
     * {@code CodexEntry.Builder.unlockedByTermIntro()} reads it back without a second table.
     */
    static String introLineForCodexEntry(String codexEntryId) {
        if (codexEntryId == null) return null;
        for (StoryTerm term : StoryTerm.values()) {
            if (codexEntryId.equals(term.getCodexEntryId())) return term.getIntroBarkId();
        }
        return null;
    }

    /** True when {@code stringId} is a term's own naming line, which may of course say its word. */
    public static boolean isIntroLine(String stringId) {
        if (stringId == null) return false;
        for (StoryTerm term : StoryTerm.values()) {
            if (term.getIntroStringId().equals(stringId)) return true;
        }
        return false;
    }

    /**
     * True when {@code stringId} is outside the ladder's reach: a naming line, the archive, the
     * game's own title, or an endgame status card.  See the class comment for why each one is
     * exempt — none of them is a channel a player meets a word on unprepared.
     */
    public static boolean isExempt(String stringId) {
        if (stringId == null) return true;
        if (isIntroLine(stringId))               return true;
        if (stringId.startsWith(CODEX_ID_PREFIX)) return true;
        for (int titleIndex = 0; titleIndex < TITLE_ID_EXEMPTIONS.length; titleIndex++) {
            if (TITLE_ID_EXEMPTIONS[titleIndex].equals(stringId)) return true;
        }
        if (stringId.startsWith(ENDING_CARD_ID_PREFIX)) {
            for (int infixIndex = 0; infixIndex < ENDING_CARD_ID_INFIXES.length; infixIndex++) {
                if (stringId.startsWith(ENDING_CARD_ID_PREFIX + ENDING_CARD_ID_INFIXES[infixIndex])) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Scans {@code text} for the first term whose word is not yet free at {@code earliestRegion} —
     * the SHALLOWEST region the row carrying this text may be spoken in.
     *
     * <p>Matching is case-insensitive and word-boundary anchored, so "reprint" matches "Reprint" and
     * "reprint." but not "reprinting" unless that form is a declared token, and never a substring of
     * a longer word.  Returns null when the text is clean.
     *
     * @param text          resolved (already localised) line text
     * @param earliestRegion the shallowest region band the row belongs to; null is treated as the
     *                       surface, which is the strictest reading
     * @return the offending term, or null
     */
    public static StoryTerm firstDisallowedTerm(String text, StoryRegion earliestRegion) {
        if (text == null || text.isEmpty()) return null;
        StoryRegion region = earliestRegion != null ? earliestRegion : StoryRegion.HABITATION_RINGS;
        int length     = text.length();
        int wordStart  = -1;
        for (int scanIndex = 0; scanIndex <= length; scanIndex++) {
            boolean isWordCharacter = scanIndex < length && isTermWordCharacter(text.charAt(scanIndex));
            if (isWordCharacter) {
                if (wordStart < 0) wordStart = scanIndex;
                continue;
            }
            if (wordStart < 0) continue;
            String word = text.substring(wordStart, scanIndex).toLowerCase(Locale.ROOT);
            wordStart = -1;
            StoryTerm term = termForWord(stripPossessive(word));
            if (term != null && !region.isAtLeast(term.getFreeFrom())) return term;
        }
        return null;
    }

    /** The term this exact (lower-case) word names, or null when the word belongs to no term. */
    public static StoryTerm termForWord(String lowerCaseWord) {
        if (lowerCaseWord == null || lowerCaseWord.isEmpty()) return null;
        for (StoryTerm term : StoryTerm.values()) {
            if (term.matchesToken(lowerCaseWord)) return term;
        }
        return null;
    }

    /**
     * Word characters for token matching.  Letters plus the apostrophe, so "operator's" scans as one
     * word rather than as "operator" followed by "s" — a possessive is still the word being used.
     */
    private static boolean isTermWordCharacter(char character) {
        return Character.isLetter(character) || character == '\'';
    }

    /**
     * Drops a trailing possessive so "cradle's" and "operators'" match their terms.  Anything else
     * containing an apostrophe ("it's", "you're") is left alone and simply matches no token.
     */
    private static String stripPossessive(String lowerCaseWord) {
        if (lowerCaseWord.endsWith("'s")) return lowerCaseWord.substring(0, lowerCaseWord.length() - 2);
        if (lowerCaseWord.endsWith("'"))  return lowerCaseWord.substring(0, lowerCaseWord.length() - 1);
        return lowerCaseWord;
    }
}

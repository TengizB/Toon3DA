package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.List;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * The CODEX (Story UI order-6 Part A) — the archive's headless brain.  It owns which entries are
 * unlocked, which tab and entry are open, where the body is scrolled, which entries still wear a
 * NEW dot, and when a whole category has been filled.  {@code render/CodexOverlayRenderer} draws
 * whatever it reports and decides nothing.
 *
 * <h3>The rules it enforces</h3>
 * <ul>
 *   <li><b>Nothing here is forced.</b> The codex opens only when the player asks for it, never
 *       interrupts play, and no entry is required to follow the story — every one of them is the
 *       long version of a beat that already landed on an unskippable channel (order-6 Part C).</li>
 *   <li><b>Locked entries are not listed.</b> A tab shows what the player HAS, not a checklist of
 *       what they are missing.  A wall of "???" turns an opt-in archive into homework.</li>
 *   <li><b>Unlocks are permanent.</b> Both paths — a line said in the world, and a PROBE answer that
 *       named an entry — write through {@link StoryProgress}, so a death never takes an entry back,
 *       for the same reason it never rewinds the deepest region.</li>
 *   <li><b>NEW invites, it does not nag.</b> An unlocked-but-unopened entry carries a dot; opening
 *       it clears the dot permanently.  There is no badge count and no unread total.</li>
 *   <li><b>Completion is cosmetic.</b> Filling a category is reported once, for a bark and a mark on
 *       the tab.  It is never gameplay-critical and never required.</li>
 *   <li><b>Vertical scrolling only.</b> Long text wraps to fit the panel; nothing in the codex ever
 *       scrolls sideways or is clipped.</li>
 * </ul>
 *
 * <p>ALLOCATION: the visible-entry list and the detail's display lines are rebuilt only when the
 * selection actually changes — opening a tab, opening an entry — never per frame, so the render
 * path allocates nothing.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class CodexSystem {

    /**
     * Paragraph break inside a codex body.  The string table is one line per id (properties format),
     * so a literal newline is not available; a body says {@code ...one.||Two...} and the detail view
     * turns that into a blank line between paragraphs.
     */
    public static final String PARAGRAPH_SEPARATOR = "||";

    /** How one line of an entry's detail view is drawn.  The renderer switches on this, not on text. */
    public enum DetailLineStyle {
        /** The entry's title, at the top. */
        TITLE,
        /** Where it came from — dim, directly under the title. */
        SOURCE,
        /** The "ORA's note" label above her take. */
        TAKE_LABEL,
        /** ORA's one-line take, in her accent colour — the line the player already heard. */
        TAKE,
        /** Body text. */
        BODY,
        /** A paragraph break. */
        BLANK
    }

    private final CodexRegistry registry;
    private final StoryStrings  strings;
    private final StoryProgress progress;
    private final StorySettings settings;

    // ---- what is on screen ---------------------------------------------------------------------
    private boolean       open;
    private CodexCategory selectedCategory = CodexCategory.values()[0];
    private CodexEntry    openEntry;
    private float         scrollOffset;
    private float         elapsedSeconds;

    // ---- the current tab's unlocked entries, resolved once per selection change ----------------
    private final List<CodexEntry> visibleEntries    = new ArrayList<>();
    private final List<String>     visibleTitles     = new ArrayList<>();
    private final List<String>     visibleSources    = new ArrayList<>();

    // ---- the open entry's display lines, built once when it opens ------------------------------
    private final List<String>          detailLines  = new ArrayList<>();
    private final List<DetailLineStyle> detailStyles = new ArrayList<>();

    // ---- completion beats waiting to be reported to the engine ---------------------------------
    private final List<CodexCategory> pendingCompletions = new ArrayList<>();

    /**
     * @param registry the codex catalog (see {@link CodexCatalog#defaultRegistry})
     * @param strings  the resolved localisation table
     * @param progress persistent narrative state — the unlock set and the per-entry read flags
     * @param settings the accessibility settings (text size drives the wrap width)
     */
    public CodexSystem(CodexRegistry registry, StoryStrings strings, StoryProgress progress,
                       StorySettings settings) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        if (strings  == null) throw new IllegalArgumentException("strings must not be null");
        if (progress == null) throw new IllegalArgumentException("progress must not be null");
        if (settings == null) throw new IllegalArgumentException("settings must not be null");
        this.registry = registry;
        this.strings  = strings;
        this.progress = progress;
        this.settings = settings;
        rebuildVisibleEntries();
    }

    public StoryProgress getProgress() {
        return progress;
    }

    public StorySettings getSettings() {
        return settings;
    }

    // -------------------------------------------------------------------------
    // Unlocking
    // -------------------------------------------------------------------------

    /**
     * Archives every entry that this bark / exchange id unlocks.  Called by the engine the moment a
     * line is actually DELIVERED — not when it is queued — so the archive only ever holds things the
     * player was really told.
     *
     * @return true when at least one entry was newly unlocked
     */
    public boolean noteLineSpoken(String lineId) {
        if (lineId == null) return false;
        boolean unlockedAny = false;
        List<CodexEntry> allEntries = registry.getAll();
        for (int entryIndex = 0; entryIndex < allEntries.size(); entryIndex++) {
            CodexEntry entry = allEntries.get(entryIndex);
            if (!entry.isUnlockedByLine(lineId))            continue;
            if (progress.isCodexUnlocked(entry.getId()))    continue;
            progress.unlockCodexEntry(entry.getId());
            unlockedAny = true;
        }
        if (unlockedAny) {
            noteCompletedCategories();
            rebuildVisibleEntries();
        }
        return unlockedAny;
    }

    /**
     * Re-checks completion after an unlock that did NOT come through this system (a PROBE answer
     * writes straight to {@link StoryProgress}).  Cheap, and the engine calls it when the codex is
     * opened, so a category filled by an exchange still reports its beat.
     */
    public void refresh() {
        noteCompletedCategories();
        rebuildVisibleEntries();
    }

    /** True when this entry is in the persistent unlock set. */
    public boolean isUnlocked(CodexEntry entry) {
        return entry != null && progress.isCodexUnlocked(entry.getId());
    }

    /** How many of a category's entries the player has recovered. */
    public int getUnlockedCount(CodexCategory category) {
        List<CodexEntry> categoryEntries = registry.getForCategory(category);
        int unlockedCount = 0;
        for (int entryIndex = 0; entryIndex < categoryEntries.size(); entryIndex++) {
            if (progress.isCodexUnlocked(categoryEntries.get(entryIndex).getId())) unlockedCount++;
        }
        return unlockedCount;
    }

    /** How many entries a category holds in total. */
    public int getEntryCount(CodexCategory category) {
        return registry.getForCategory(category).size();
    }

    /** True when every entry in a category has been recovered.  An empty category is never complete. */
    public boolean isCategoryComplete(CodexCategory category) {
        int total = getEntryCount(category);
        return total > 0 && getUnlockedCount(category) == total;
    }

    /**
     * Records the one-shot "category filled" beat for any category that just completed.  The flag is
     * persistent, so the completion perk is paid once in the life of a save and never re-celebrated.
     */
    private void noteCompletedCategories() {
        for (CodexCategory category : CodexCategory.values()) {
            if (!isCategoryComplete(category))                     continue;
            String beatId = completionBeatId(category);
            if (progress.hasSeen(beatId))                          continue;
            progress.markSeen(beatId);
            pendingCompletions.add(category);
        }
    }

    /** The persistent one-shot id behind a category's completion beat. */
    public static String completionBeatId(CodexCategory category) {
        return "codex.complete." + category.getCatalogKey();
    }

    /**
     * Takes the next category whose completion beat has not been handed to the engine yet, or null.
     * The engine turns it into a bark; the codex layer never fires one itself, for the same reason
     * the exchange layer never touches the inventory.
     */
    public CodexCategory consumePendingCompletion() {
        if (pendingCompletions.isEmpty()) return null;
        return pendingCompletions.remove(0);
    }

    // -------------------------------------------------------------------------
    // Opening / closing the screen
    // -------------------------------------------------------------------------

    /** Opens the archive on the tab it was last left on, list view, scrolled to the top. */
    public void open() {
        open           = true;
        openEntry      = null;
        scrollOffset   = 0f;
        elapsedSeconds = 0f;
        refresh();
    }

    /** Closes the archive.  Selection is kept, so re-opening lands where the player left off. */
    public void close() {
        open      = false;
        openEntry = null;
    }

    public boolean isOpen() {
        return open;
    }

    /** Advances the fade clock.  The only clock in the codex — nothing here is ever on a timer. */
    public void update(float deltaTime) {
        if (!open) return;
        elapsedSeconds += deltaTime;
    }

    /** 0..1 fade multiplier for the whole screen. */
    public float getVisibleFraction() {
        if (!open) return 0f;
        if (StoryUiConstants.STORY_CODEX_FADE_SECONDS <= 0f) return 1f;
        float fraction = elapsedSeconds / StoryUiConstants.STORY_CODEX_FADE_SECONDS;
        return fraction >= 1f ? 1f : fraction;
    }

    // -------------------------------------------------------------------------
    // Tabs and entries
    // -------------------------------------------------------------------------

    public CodexCategory getSelectedCategory() {
        return selectedCategory;
    }

    /** Switches tab, returning to the list view at the top of it. */
    public void selectCategory(CodexCategory category) {
        if (category == null || category == selectedCategory) {
            // Re-tapping the open tab still backs out of an entry — a second way home that costs
            // nothing to offer and saves a player who missed the BACK button.
            openEntry    = null;
            scrollOffset = 0f;
            return;
        }
        selectedCategory = category;
        openEntry        = null;
        scrollOffset     = 0f;
        rebuildVisibleEntries();
    }

    /** How many entries the open tab lists (unlocked only). */
    public int getVisibleEntryCount() {
        return visibleEntries.size();
    }

    /** The resolved title of a listed entry. */
    public String getVisibleEntryTitle(int listIndex) {
        return isValidListIndex(listIndex) ? visibleTitles.get(listIndex) : "";
    }

    /** The resolved source line of a listed entry. */
    public String getVisibleEntrySource(int listIndex) {
        return isValidListIndex(listIndex) ? visibleSources.get(listIndex) : "";
    }

    /** True while a listed entry still wears its NEW dot (unlocked, never opened). */
    public boolean isVisibleEntryNew(int listIndex) {
        if (!isValidListIndex(listIndex)) return false;
        return !progress.isCodexEntryRead(visibleEntries.get(listIndex).getId());
    }

    private boolean isValidListIndex(int listIndex) {
        return listIndex >= 0 && listIndex < visibleEntries.size();
    }

    /**
     * Opens a listed entry: clears its NEW dot permanently and builds its detail lines once.
     *
     * @return true when an entry actually opened
     */
    public boolean openEntry(int listIndex) {
        if (!isValidListIndex(listIndex)) return false;
        openEntry    = visibleEntries.get(listIndex);
        scrollOffset = 0f;
        progress.markCodexEntryRead(openEntry.getId());
        buildDetailLines(openEntry);
        return true;
    }

    /** Returns from an entry to its tab's list, back at the top. */
    public void closeEntry() {
        openEntry    = null;
        scrollOffset = 0f;
    }

    /** True while an entry's full text is on screen (rather than the tab's list). */
    public boolean isShowingEntry() {
        return openEntry != null;
    }

    /** The entry on screen, or null in list view. */
    public CodexEntry getOpenEntry() {
        return openEntry;
    }

    // -------------------------------------------------------------------------
    // The open entry's display lines
    // -------------------------------------------------------------------------

    public int getDetailLineCount() {
        return detailLines.size();
    }

    public String getDetailLine(int lineIndex) {
        return lineIndex >= 0 && lineIndex < detailLines.size() ? detailLines.get(lineIndex) : "";
    }

    public DetailLineStyle getDetailLineStyle(int lineIndex) {
        return lineIndex >= 0 && lineIndex < detailStyles.size() ? detailStyles.get(lineIndex)
                                                                 : DetailLineStyle.BODY;
    }

    /**
     * Resolves and wraps one entry into display lines: title, source, ORA's take (for a log), then
     * the body's paragraphs.  Runs once, when the entry opens — never on the render path.
     */
    private void buildDetailLines(CodexEntry entry) {
        detailLines.clear();
        detailStyles.clear();
        int maxChars = settings.getCodexLineMaxChars();

        addWrapped(strings.get(entry.getTitleStringId()), maxChars, DetailLineStyle.TITLE);
        addWrapped(strings.get(entry.getSourceStringId()), maxChars, DetailLineStyle.SOURCE);

        if (entry.getAiTakeStringId() != null) {
            addLine("", DetailLineStyle.BLANK);
            addWrapped(strings.get(StoryUiConstants.STORY_CODEX_TAKE_LABEL_ID), maxChars,
                       DetailLineStyle.TAKE_LABEL);
            addWrapped(strings.get(entry.getAiTakeStringId()), maxChars, DetailLineStyle.TAKE);
        }

        String body = strings.get(entry.getBodyStringId());
        int paragraphStart = 0;
        while (paragraphStart <= body.length()) {
            int separatorIndex = body.indexOf(PARAGRAPH_SEPARATOR, paragraphStart);
            int paragraphEnd   = separatorIndex < 0 ? body.length() : separatorIndex;
            addLine("", DetailLineStyle.BLANK);
            addWrapped(body.substring(paragraphStart, paragraphEnd).trim(), maxChars,
                       DetailLineStyle.BODY);
            if (separatorIndex < 0) break;
            paragraphStart = separatorIndex + PARAGRAPH_SEPARATOR.length();
        }
    }

    private void addWrapped(String text, int maxChars, DetailLineStyle style) {
        List<String> wrapped = StoryText.wrapToMaxChars(text, maxChars);
        for (int lineIndex = 0; lineIndex < wrapped.size(); lineIndex++) {
            addLine(wrapped.get(lineIndex), style);
        }
    }

    private void addLine(String text, DetailLineStyle style) {
        detailLines.add(text);
        detailStyles.add(style);
    }

    // -------------------------------------------------------------------------
    // Scrolling (vertical only, and always clamped to real content)
    // -------------------------------------------------------------------------

    /** How tall the current view's content is, in world units. */
    public float getContentHeight() {
        if (openEntry != null) {
            return detailLines.size() * StoryUiConstants.STORY_CODEX_DETAIL_LINE_PITCH;
        }
        int rowCount = visibleEntries.size();
        if (rowCount <= 0) return 0f;
        return rowCount * StoryUiConstants.STORY_CODEX_ROW_HEIGHT
                + (rowCount - 1) * StoryUiConstants.STORY_CODEX_ROW_GAP;
    }

    /** How far the content is scrolled down from its top, in world units.  Always in range. */
    public float getScrollOffset() {
        return scrollOffset;
    }

    /** Scrolls by a drag delta (positive = content moves up, revealing what is below). */
    public void scrollBy(float deltaWorldUnits) {
        setScrollOffset(scrollOffset + deltaWorldUnits);
    }

    /** Sets the scroll position, clamped so the view can never run off either end of the content. */
    public void setScrollOffset(float requestedOffset) {
        scrollOffset = GameMath.clampScrollOffset(requestedOffset, getContentHeight(),
                                                  StoryUiConstants.STORY_CODEX_BODY_HEIGHT);
    }

    /** True when the content is taller than the body — the renderer draws a scroll hint only then. */
    public boolean isScrollable() {
        return getContentHeight() > StoryUiConstants.STORY_CODEX_BODY_HEIGHT;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /** Re-resolves the open tab's unlocked entries.  Runs on a selection change or an unlock only. */
    private void rebuildVisibleEntries() {
        visibleEntries.clear();
        visibleTitles.clear();
        visibleSources.clear();
        List<CodexEntry> categoryEntries = registry.getForCategory(selectedCategory);
        for (int entryIndex = 0; entryIndex < categoryEntries.size(); entryIndex++) {
            CodexEntry entry = categoryEntries.get(entryIndex);
            if (!progress.isCodexUnlocked(entry.getId())) continue;
            visibleEntries.add(entry);
            visibleTitles.add(strings.get(entry.getTitleStringId()));
            visibleSources.add(strings.get(entry.getSourceStringId()));
        }
        setScrollOffset(scrollOffset);   // the list may have shrunk under the current offset
    }
}

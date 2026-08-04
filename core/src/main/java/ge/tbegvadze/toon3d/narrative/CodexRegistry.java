package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The codex catalog: {@link CodexCategory} -&gt; the entries filed under it (Story UI order-6).
 * Registration is the ONLY edit point — adding an entry is one {@link #register} call in
 * {@link CodexCatalog}, and nothing in {@link CodexSystem}, the renderer or the world switches on a
 * hard-coded entry list (the same discipline as {@code route/RouteRegistries} and every other
 * narrative catalog).
 *
 * <p>Entries are grouped by category at registration time and, within a category, kept in
 * SHALLOWEST-REGION-FIRST order so a tab reads top-to-bottom as a descent.  Ties keep registration
 * order, which is what lets a catalog author control the reading order of a single region's entries.
 * The per-category lists are returned READ-ONLY; callers must never mutate them.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class CodexRegistry {

    private final Map<CodexCategory, List<CodexEntry>> byCategory = new EnumMap<>(CodexCategory.class);
    private final Map<String, CodexEntry>              byId       = new HashMap<>();

    /** Registers one entry.  Ids are unique — a duplicate id is a catalog bug, so it throws. */
    public CodexRegistry register(CodexEntry entry) {
        if (entry == null) throw new IllegalArgumentException("entry must not be null");
        if (byId.containsKey(entry.getId())) {
            throw new IllegalStateException("duplicate codex entry id: " + entry.getId());
        }
        byId.put(entry.getId(), entry);
        List<CodexEntry> categoryEntries = byCategory.get(entry.getCategory());
        if (categoryEntries == null) {
            categoryEntries = new ArrayList<>();
            byCategory.put(entry.getCategory(), categoryEntries);
        }
        insertByRegionDepth(categoryEntries, entry);
        return this;
    }

    /**
     * Inserts an entry after every already-registered entry of the same or a shallower region — a
     * stable insertion sort, which keeps registration order inside one region while still reading as
     * a descent overall.  Catalogs are dozens of rows, so the cost is irrelevant and the alternative
     * (sorting later) would need a comparator nobody could see from the catalog.
     */
    private static void insertByRegionDepth(List<CodexEntry> categoryEntries, CodexEntry entry) {
        int insertIndex = categoryEntries.size();
        for (int entryIndex = 0; entryIndex < categoryEntries.size(); entryIndex++) {
            if (categoryEntries.get(entryIndex).getRegion().ordinal() > entry.getRegion().ordinal()) {
                insertIndex = entryIndex;
                break;
            }
        }
        categoryEntries.add(insertIndex, entry);
    }

    /** All entries filed under a category, shallowest region first.  Never null; read-only. */
    public List<CodexEntry> getForCategory(CodexCategory category) {
        List<CodexEntry> categoryEntries = byCategory.get(category);
        return categoryEntries != null ? Collections.unmodifiableList(categoryEntries)
                                       : Collections.<CodexEntry>emptyList();
    }

    /** The entry with this id, or null. */
    public CodexEntry getById(String entryId) {
        return byId.get(entryId);
    }

    /** Every registered entry (tests / audits). */
    public List<CodexEntry> getAll() {
        return new ArrayList<>(byId.values());
    }

    public int size() {
        return byId.size();
    }

    /** True once at least one entry is registered — lets {@link CodexCatalog#bootstrap} be idempotent. */
    public boolean isEmpty() {
        return byId.isEmpty();
    }
}

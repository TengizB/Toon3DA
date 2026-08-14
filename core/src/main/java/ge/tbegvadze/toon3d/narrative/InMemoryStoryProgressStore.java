package ge.tbegvadze.toon3d.narrative;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link StoryProgressStore} that keeps everything in memory.  The default when no engine store
 * is supplied, and the store the headless tests drive: a single instance shared across two
 * {@link StoryProgress} objects reproduces exactly what a real save does across a death.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class InMemoryStoryProgressStore implements StoryProgressStore {

    private final Set<String>              seenBeatIds     = new HashSet<>();
    private final Map<String, Integer>     stanceValues    = new HashMap<>();
    private final Map<String, String>      outcomes        = new HashMap<>();
    private final Set<String>              unlockedCodexIds = new HashSet<>();
    private final Set<String>              readCodexIds     = new HashSet<>();
    private final Map<String, Integer>     settings         = new HashMap<>();
    private int deepestRegionOrdinal;
    private int reprintCount;
    /** Epoch millis of the last recorded session (order-9 A); 0 = this save has never had one. */
    private long lastSessionEpochMillis;
    /** Stamped on the first write, exactly like the real store, so a fresh store reads version 0. */
    private int schemaVersion;

    @Override
    public int loadSchemaVersion() {
        return schemaVersion;
    }

    @Override
    public void wipeNarrativeState() {
        clear();
        schemaVersion = SCHEMA_VERSION;
    }

    /** Every write stamps the schema version, so a save always says which format wrote it. */
    private void stampSchemaVersion() {
        schemaVersion = SCHEMA_VERSION;
    }

    @Override
    public int loadDeepestRegionOrdinal() {
        return deepestRegionOrdinal;
    }

    @Override
    public void saveDeepestRegionOrdinal(int regionOrdinal) {
        this.deepestRegionOrdinal = regionOrdinal;
        stampSchemaVersion();
    }

    @Override
    public boolean isBeatSeen(String beatId) {
        return seenBeatIds.contains(beatId);
    }

    @Override
    public void markBeatSeen(String beatId) {
        seenBeatIds.add(beatId);
        stampSchemaVersion();
    }

    @Override
    public int loadReprintCount() {
        return reprintCount;
    }

    @Override
    public void saveReprintCount(int reprintCount) {
        this.reprintCount = reprintCount;
        stampSchemaVersion();
    }

    @Override
    public int loadStance(String stanceName) {
        Integer value = stanceValues.get(stanceName);
        return value != null ? value.intValue() : 0;
    }

    @Override
    public void saveStance(String stanceName, int value) {
        stanceValues.put(stanceName, Integer.valueOf(value));
        stampSchemaVersion();
    }

    @Override
    public String loadOutcome(String exchangeId) {
        return outcomes.get(exchangeId);
    }

    @Override
    public void saveOutcome(String exchangeId, String optionId) {
        outcomes.put(exchangeId, optionId);
        stampSchemaVersion();
    }

    @Override
    public boolean isCodexUnlocked(String codexId) {
        return unlockedCodexIds.contains(codexId);
    }

    @Override
    public void markCodexUnlocked(String codexId) {
        unlockedCodexIds.add(codexId);
        stampSchemaVersion();
    }

    @Override
    public boolean isCodexEntryRead(String codexId) {
        return readCodexIds.contains(codexId);
    }

    @Override
    public void markCodexEntryRead(String codexId) {
        readCodexIds.add(codexId);
        stampSchemaVersion();
    }

    @Override
    public int loadSettingInt(String settingKey, int defaultValue) {
        Integer value = settings.get(settingKey);
        return value != null ? value.intValue() : defaultValue;
    }

    @Override
    public void saveSettingInt(String settingKey, int value) {
        settings.put(settingKey, Integer.valueOf(value));
        stampSchemaVersion();
    }

    @Override
    public long loadLastSessionEpochMillis() {
        return lastSessionEpochMillis;
    }

    @Override
    public void saveLastSessionEpochMillis(long epochMillis) {
        this.lastSessionEpochMillis = epochMillis;
        stampSchemaVersion();
    }

    /**
     * Wipes everything — the headless half of a "new game" reset.  Kept as its own method (rather
     * than folded into {@link #wipeNarrativeState()}) because tests reset a store without pretending
     * a save format ever wrote to it.
     */
    public void clear() {
        seenBeatIds.clear();
        stanceValues.clear();
        outcomes.clear();
        unlockedCodexIds.clear();
        readCodexIds.clear();
        settings.clear();
        deepestRegionOrdinal   = 0;
        reprintCount           = 0;
        lastSessionEpochMillis = 0L;   // a wiped save has never had a session, so it has no gap
        schemaVersion          = 0;
    }
}

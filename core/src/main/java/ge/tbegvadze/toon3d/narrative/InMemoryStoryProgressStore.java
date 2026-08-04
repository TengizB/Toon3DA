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
    private int deepestRegionOrdinal;
    private int reprintCount;

    @Override
    public int loadDeepestRegionOrdinal() {
        return deepestRegionOrdinal;
    }

    @Override
    public void saveDeepestRegionOrdinal(int regionOrdinal) {
        this.deepestRegionOrdinal = regionOrdinal;
    }

    @Override
    public boolean isBeatSeen(String beatId) {
        return seenBeatIds.contains(beatId);
    }

    @Override
    public void markBeatSeen(String beatId) {
        seenBeatIds.add(beatId);
    }

    @Override
    public int loadReprintCount() {
        return reprintCount;
    }

    @Override
    public void saveReprintCount(int reprintCount) {
        this.reprintCount = reprintCount;
    }

    @Override
    public int loadStance(String stanceName) {
        Integer value = stanceValues.get(stanceName);
        return value != null ? value.intValue() : 0;
    }

    @Override
    public void saveStance(String stanceName, int value) {
        stanceValues.put(stanceName, Integer.valueOf(value));
    }

    @Override
    public String loadOutcome(String exchangeId) {
        return outcomes.get(exchangeId);
    }

    @Override
    public void saveOutcome(String exchangeId, String optionId) {
        outcomes.put(exchangeId, optionId);
    }

    @Override
    public boolean isCodexUnlocked(String codexId) {
        return unlockedCodexIds.contains(codexId);
    }

    @Override
    public void markCodexUnlocked(String codexId) {
        unlockedCodexIds.add(codexId);
    }

    /** Wipes everything — the headless half of a "new game" reset. */
    public void clear() {
        seenBeatIds.clear();
        stanceValues.clear();
        outcomes.clear();
        unlockedCodexIds.clear();
        deepestRegionOrdinal = 0;
        reprintCount         = 0;
    }
}

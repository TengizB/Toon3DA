package ge.tbegvadze.toon3d.narrative;

import java.util.HashSet;
import java.util.Set;

/**
 * A {@link StoryProgressStore} that keeps everything in memory.  The default when no engine store
 * is supplied, and the store the headless tests drive: a single instance shared across two
 * {@link StoryProgress} objects reproduces exactly what a real save does across a death.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class InMemoryStoryProgressStore implements StoryProgressStore {

    private final Set<String> seenBeatIds = new HashSet<>();
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

    /** Wipes everything — the headless half of a "new game" reset. */
    public void clear() {
        seenBeatIds.clear();
        deepestRegionOrdinal = 0;
        reprintCount         = 0;
    }
}

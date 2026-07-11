package ge.tbegvadze.toon3d.level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The chosen tile positions of one procedurally-generated boss arena (boss ORDER 7).
 *
 * <p>Because the arena is now generated per run (seeded jitter of room size, cover, alcoves and
 * lighting) rather than hard-coded, the door / exit / boss-spawn tiles vary and can no longer be
 * compile-time constants. {@link BossArenaGenerator} builds this alongside the {@link Level} and
 * {@code World} injects it into {@code BossFloorController} (which locks the door on awaken and gates
 * the exit until death) and into the boss-creation path (which places the boss on {@code bossColumn/
 * bossRow}).
 *
 * <p>Heal-alcove tiles are recessed pockets against the arena walls where the boss can retreat to
 * repair (ORDER 5's flee target). They are exposed here so the heal-flee logic can prefer them; each
 * pocket is open on its mouth side, never a dead-end the boss can turtle in forever.
 *
 * <p>All coordinates are tile indices; (0,0) = bottom-left tile, Y-up.
 */
public final class BossArenaLayout {

    /** The single lockable arena-entry door tile (sealed on awaken, unlocked on death). */
    public final int doorColumn;
    public final int doorRow;

    /** The exit portal tile (blocked until the boss dies, then restored). */
    public final int exitColumn;
    public final int exitRow;

    /** The boss spawn tile (near the far side from the entrance). */
    public final int bossColumn;
    public final int bossRow;

    /** Heal-alcove tiles ({@code {tileColumn, tileRow}} pairs); may be empty. Unmodifiable. */
    private final List<int[]> healAlcoveTiles;

    public BossArenaLayout(int doorColumn, int doorRow,
                           int exitColumn, int exitRow,
                           int bossColumn, int bossRow,
                           List<int[]> healAlcoveTiles) {
        this.doorColumn      = doorColumn;
        this.doorRow         = doorRow;
        this.exitColumn      = exitColumn;
        this.exitRow         = exitRow;
        this.bossColumn      = bossColumn;
        this.bossRow         = bossRow;
        // Defensive deep copy on the way in (tolerating a null list) so a caller cannot mutate the
        // recorded tiles through its original arrays after construction.
        this.healAlcoveTiles = Collections.unmodifiableList(deepCopyTiles(healAlcoveTiles));
    }

    /**
     * The recorded heal-alcove tiles ({@code {tileColumn, tileRow}} pairs). Never null; may be empty.
     * Returns a fresh unmodifiable deep copy so the caller can never mutate the layout's stored tiles —
     * neither the list nor the {@code int[]} pairs inside it. Called once per floor build (never in a
     * render loop), so the allocation is fine.
     */
    public List<int[]> getHealAlcoveTiles() {
        return Collections.unmodifiableList(deepCopyTiles(healAlcoveTiles));
    }

    /** Clones a list of {@code {column,row}} pairs into a fresh list of fresh arrays; null → empty. */
    private static List<int[]> deepCopyTiles(List<int[]> tiles) {
        List<int[]> copy = new ArrayList<>(tiles == null ? 0 : tiles.size());
        if (tiles != null) {
            for (int[] tile : tiles) {
                copy.add(new int[]{tile[0], tile[1]});
            }
        }
        return copy;
    }
}

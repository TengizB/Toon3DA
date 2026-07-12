package ge.tbegvadze.toon3d.entity.boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks tiles telegraphed as dangerous by a boss attack.
 *
 * On TELEGRAPH: arm() marks a set of tiles with a pending damage value.
 * On RESOLVE:   BossFloorController calls resolve() to deal damage to any actor
 *               standing on a marked tile, then clear() resets the set.
 *
 * The set is pre-allocated (no render-time allocations). A single DangerTileSet
 * lives on each Boss instance for its entire lifespan.
 */
public final class DangerTileSet {

    /**
     * Which threat armed this set (boss-fight-mobile-overseer ORDER 8). Drives the telegraph's floor-quad
     * COLOUR + SHAPE in EnemyRenderer so the player learns one colour-per-threat vocabulary at a glance
     * (Fairness F6). Colour-blind safety: each kind pairs its colour with a distinct floor SHAPE, so the
     * overlay is never colour-only.
     */
    public enum TelegraphKind {
        /** Imminent lunge corridor — RED line. */
        CHARGE_LINE,
        /** Adjacent slam footprint — ORANGE ring. */
        MELEE_RING,
        /** Fire lands here — AMBER/ORANGE flicker patch. */
        HAZARD_FIRE,
        /** Toxic cloud lands here — SICKLY-GREEN patch. */
        HAZARD_TOXIC,
        /** Incoming summons — PURPLE runes. */
        SUMMON_RUNES,
        /** Unclassified telegraph — drawn as a neutral patch tinted with the boss accent. */
        GENERIC
    }

    /** A single marked tile position. */
    public static final class MarkedTile {
        public final int tileColumn;
        public final int tileRow;

        public MarkedTile(int tileColumn, int tileRow) {
            this.tileColumn = tileColumn;
            this.tileRow    = tileRow;
        }
    }

    private final List<MarkedTile> markedTiles = new ArrayList<>();
    private int           damage;
    private boolean       active;
    private TelegraphKind telegraphKind = TelegraphKind.GENERIC;

    public DangerTileSet() {
        this.active = false;
    }

    /**
     * Arms the set with a list of marked tiles and the damage they will deal on resolve, tagging it with
     * the {@link TelegraphKind} that armed it so the render layer can colour + shape the telegraph
     * (ORDER 8). Clears any previous marks first.
     */
    public void arm(List<MarkedTile> tiles, int tileDamage, TelegraphKind kind) {
        markedTiles.clear();
        markedTiles.addAll(tiles);
        damage        = tileDamage;
        telegraphKind = kind;
        active        = true;
    }

    /**
     * Arms the set without a specific threat classification — the telegraph renders as a neutral
     * {@link TelegraphKind#GENERIC} patch. Kept for bosses that have not yet adopted the ORDER 8 colour
     * language.
     */
    public void arm(List<MarkedTile> tiles, int tileDamage) {
        arm(tiles, tileDamage, TelegraphKind.GENERIC);
    }

    /** Which threat armed this set — drives the telegraph overlay's colour + shape (ORDER 8). */
    public TelegraphKind getTelegraphKind() {
        return telegraphKind;
    }

    /** Returns true while tiles are marked and waiting to resolve. */
    public boolean isActive() {
        return active;
    }

    /** Damage value each marked tile deals when the set resolves. */
    public int getDamage() {
        return damage;
    }

    /** Unmodifiable view of the currently marked tiles. */
    public List<MarkedTile> getTiles() {
        return Collections.unmodifiableList(markedTiles);
    }

    /**
     * Returns true if the given tile is currently marked as dangerous.
     * O(n) over the number of marked tiles; sets are small (< 30 tiles).
     */
    public boolean contains(int tileColumn, int tileRow) {
        for (int index = 0; index < markedTiles.size(); index++) {
            MarkedTile tile = markedTiles.get(index);
            if (tile.tileColumn == tileColumn && tile.tileRow == tileRow) return true;
        }
        return false;
    }

    /** Deactivates the set and removes all marks. */
    public void clear() {
        markedTiles.clear();
        active = false;
    }
}

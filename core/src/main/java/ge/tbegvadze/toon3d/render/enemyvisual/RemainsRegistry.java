package ge.tbegvadze.toon3d.render.enemyvisual;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * A killed creature's OWN REMAINS (generated-enemy-art order 3, A4): the last DEATH frame, left standing as
 * a billboard on the tile it died on for the rest of the floor, drawn in place of the generic corpse decal
 * for that tile. One entry per tile (a second death on the same tile replaces the first). Per floor: the
 * registry lives in the per-floor {@link EnemyVisualLayer} and is cleared with it (R5).
 *
 * <p>The simulation's corpse tile stays the authority (R5): this never writes the level. It only changes
 * what is DRAWN — {@code PropRenderer} asks {@link #hasRemainsAt} before drawing a stamped corpse decal.
 * An entry is registered at the moment of death (so the decal is hidden while DEATH still plays) and is
 * revealed once DEATH has played through, where the dying instance stops.
 *
 * <p>Rule R4: fixed capacity, pre-allocated; when it is full the OLDEST remains are recycled.
 */
public final class RemainsRegistry {

    public static final class Remains {
        public boolean   active;
        public EnemyType type;
        public EnemyAtlasCache.CreatureVisual visual;
        public float     worldX, worldY;
        public int       tileColumn, tileRow;
        float            secondsUntilRevealed;
        long             sequence;

        /** True once DEATH has played and the dying instance has handed the body over. */
        public boolean isRevealed() {
            return active && secondsUntilRevealed <= 0f;
        }

        /** The corpse frame: the last DEATH frame. */
        public EnemyClipManifest.Frame corpseFrame() {
            EnemyClipManifest.Clip death = visual.manifest.clip(EnemyClipId.DEATH);
            return death.frames[death.frames.length - 1];
        }
    }

    private final Remains[] slots = new Remains[EffectConstants.ENEMY_VISUAL_REMAINS_CAPACITY];
    private long nextSequence = 0L;

    public RemainsRegistry() {
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) slots[slotIndex] = new Remains();
    }

    /** Registers the remains of a death that will finish playing in {@code revealAfterSeconds}. */
    void add(EnemyType type, EnemyAtlasCache.CreatureVisual visual, float worldX, float worldY,
             int tileColumn, int tileRow, float revealAfterSeconds) {
        if (visual.manifest.clip(EnemyClipId.DEATH) == null) return;
        Remains target = null;
        for (Remains slot : slots) {
            if (slot.active && slot.tileColumn == tileColumn && slot.tileRow == tileRow) { target = slot; break; }
        }
        if (target == null) {
            for (Remains slot : slots) {
                if (!slot.active) { target = slot; break; }
                if (target == null || slot.sequence < target.sequence) target = slot;   // oldest so far
            }
        }
        target.active               = true;
        target.type                 = type;
        target.visual               = visual;
        target.worldX               = worldX;
        target.worldY               = worldY;
        target.tileColumn           = tileColumn;
        target.tileRow              = tileRow;
        target.secondsUntilRevealed = revealAfterSeconds;
        target.sequence             = nextSequence++;
    }

    void advance(float deltaTime) {
        for (Remains slot : slots) {
            if (slot.active && slot.secondsUntilRevealed > 0f) slot.secondsUntilRevealed -= deltaTime;
        }
    }

    /** True when a creature's remains own this tile (revealed or still dying): the generic corpse decal is not drawn. */
    public boolean hasRemainsAt(int tileColumn, int tileRow) {
        for (Remains slot : slots) {
            if (slot.active && slot.tileColumn == tileColumn && slot.tileRow == tileRow) return true;
        }
        return false;
    }

    public int capacity() {
        return slots.length;
    }

    public Remains get(int slotIndex) {
        return slots[slotIndex];
    }

    void clear() {
        for (Remains slot : slots) {
            slot.active = false;
            slot.visual = null;
        }
    }
}

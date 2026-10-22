package ge.tbegvadze.toon3d.render.enemyvisual;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * Visual instances that keep playing DEATH after the simulation has removed the enemy (generated-enemy-art
 * order 2, rule R4). The position, tile and creature are captured at the moment of death; the instance
 * then plays DEATH and, on its last frame, hands the body over to the creature's own REMAINS
 * ({@link RemainsRegistry}, order 3), registered at the same moment with the same position, so the corpse
 * never blinks. Fixed capacity, pre-allocated: when it is full the OLDEST instance is dropped — never an
 * allocation (its remains still appear on time). Cleared with the floor (the layer is per floor).
 */
public final class DyingEnemyPool {

    public static final class DyingEnemy {
        public boolean   active;
        public EnemyType type;
        public EnemyAtlasCache.CreatureVisual visual;
        public float     worldX;
        public float     worldY;
        public int       tileColumn;
        public int       tileRow;
        public float     seconds;
        /** Order 5: the death clip playing - DEATH, or its variant for the cause (DEATH_BURST). */
        public EnemyClipId clip = EnemyClipId.DEATH;
        long             sequence;

        /** The death frame showing now (the last one once the clip has played). */
        public EnemyClipManifest.Frame currentFrame() {
            EnemyClipManifest.Clip death = visual.manifest.clip(clip);
            return death.frames[death.frameIndexAt(seconds)];
        }

        /** How far the death clip has played, 0 .. 1 (order 5: a hovered type eases down to the floor with it). */
        public float progress() {
            float duration = visual.manifest.clip(clip).durationSeconds;
            return duration > 0f ? Math.min(1f, seconds / duration) : 1f;
        }
    }

    private final DyingEnemy[] slots = new DyingEnemy[EffectConstants.ENEMY_VISUAL_DYING_POOL_CAPACITY];
    private long nextSequence = 0L;

    public DyingEnemyPool() {
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) slots[slotIndex] = new DyingEnemy();
    }

    /** Starts a death ({@code clip}) at the captured position. A creature without that clip simply vanishes, as before. */
    void add(EnemyType type, EnemyAtlasCache.CreatureVisual visual, EnemyClipId clip, float worldX, float worldY,
             int tileColumn, int tileRow) {
        if (visual.manifest.clip(clip) == null) return;
        DyingEnemy target = null;
        for (DyingEnemy slot : slots) {
            if (!slot.active) { target = slot; break; }
            if (target == null || slot.sequence < target.sequence) target = slot;   // oldest so far
        }
        target.active     = true;
        target.type       = type;
        target.visual     = visual;
        target.clip       = clip;
        target.worldX     = worldX;
        target.worldY     = worldY;
        target.tileColumn = tileColumn;
        target.tileRow    = tileRow;
        target.seconds    = 0f;
        target.sequence   = nextSequence++;
    }

    void advance(float deltaTime) {
        for (DyingEnemy slot : slots) {
            if (!slot.active) continue;
            slot.seconds += deltaTime;
            float lifetime = slot.visual.manifest.clip(slot.clip).durationSeconds;
            if (slot.seconds >= lifetime) {
                slot.active = false;
                slot.visual = null;
            }
        }
    }

    public int capacity() {
        return slots.length;
    }

    public DyingEnemy get(int slotIndex) {
        return slots[slotIndex];
    }

    void clear() {
        for (DyingEnemy slot : slots) {
            slot.active = false;
            slot.visual = null;
        }
    }
}

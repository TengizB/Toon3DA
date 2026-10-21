package ge.tbegvadze.toon3d.render.enemyvisual;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * Visual instances that keep playing DEATH after the simulation has removed the enemy (generated-enemy-art
 * order 2, rule R4). The position, tile and creature are captured at the moment of death; the instance
 * then plays DEATH, holds its corpse frame for {@code ENEMY_VISUAL_DEATH_HOLD_SECONDS} and hands over to
 * the corpse decal the simulation already stamped. Fixed capacity, pre-allocated: when it is full the
 * OLDEST instance is dropped — never an allocation. Cleared with the floor (the layer is per floor).
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
        long             sequence;

        /** The DEATH frame showing now (the last one once the clip has played). */
        public EnemyClipManifest.Frame currentFrame() {
            EnemyClipManifest.Clip death = visual.manifest.clip(EnemyClipId.DEATH);
            return death.frames[death.frameIndexAt(seconds)];
        }
    }

    private final DyingEnemy[] slots = new DyingEnemy[EffectConstants.ENEMY_VISUAL_DYING_POOL_CAPACITY];
    private long nextSequence = 0L;

    public DyingEnemyPool() {
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) slots[slotIndex] = new DyingEnemy();
    }

    /** Starts a death at the captured position. A creature without a DEATH clip simply vanishes, as before. */
    void add(EnemyType type, EnemyAtlasCache.CreatureVisual visual, float worldX, float worldY,
             int tileColumn, int tileRow) {
        if (visual.manifest.clip(EnemyClipId.DEATH) == null) return;
        DyingEnemy target = null;
        for (DyingEnemy slot : slots) {
            if (!slot.active) { target = slot; break; }
            if (target == null || slot.sequence < target.sequence) target = slot;   // oldest so far
        }
        target.active     = true;
        target.type       = type;
        target.visual     = visual;
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
            float lifetime = slot.visual.manifest.clip(EnemyClipId.DEATH).durationSeconds
                    + EffectConstants.ENEMY_VISUAL_DEATH_HOLD_SECONDS;
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

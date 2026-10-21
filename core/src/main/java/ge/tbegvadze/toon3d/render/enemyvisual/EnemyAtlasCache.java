package ge.tbegvadze.toon3d.render.enemyvisual;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.EnemyConstants;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The generated creatures ONE floor needs (generated-enemy-art order 2, series AS5): the parsed manifest
 * plus the body and glow atlases, loaded for the types that can appear on the floor and disposed with the
 * floor. Two types sharing a creature share one load.
 *
 * <p>Textures are created from files, so LibGDX manages them across a GL context loss exactly like the
 * legacy enemy sheets. Nearest filter on both atlases: the frames are pixel art and the glow rects must
 * register with the body rects pixel for pixel.
 *
 * <p>Rule R5: a creature whose files fail to load is logged ONCE and remembered as failed; its types then
 * report no visual and keep the legacy draw path — never a crash.
 */
public final class EnemyAtlasCache implements Disposable {

    private static final String LOG_TAG = "EnemyAtlasCache";

    /**
     * One loaded creature: its manifest and its two atlases, plus (order 3) its family's effect set, the
     * shared status set and where on this creature each kind of effect comes from. The effect fields are
     * null when the sets failed to load: the creature then animates without effects (never a crash).
     */
    public static final class CreatureVisual {
        public final String            creatureId;
        public final EnemyClipManifest manifest;
        public final Texture           bodyAtlas;
        public final Texture           glowAtlas;
        EnemyEffectSet                 familyEffects;
        EnemyEffectSet                 statusEffects;
        /** Indexed by {@link EnemyEffectSet.PlacementId}; null when the family set has no row for this creature. */
        EnemyEffectSet.Placement[]     placements;

        CreatureVisual(String creatureId, EnemyClipManifest manifest, Texture bodyAtlas, Texture glowAtlas) {
            this.creatureId = creatureId;
            this.manifest   = manifest;
            this.bodyAtlas  = bodyAtlas;
            this.glowAtlas  = glowAtlas;
        }

        /** {@code id} from the family set (or, for a status burst, the status set); null when not shipped. */
        public EnemyEffectSet.Burst burst(EnemyEffectSet.BurstId id) {
            EnemyEffectSet.Burst found = familyEffects != null ? familyEffects.burst(id) : null;
            if (found == null && statusEffects != null) found = statusEffects.burst(id);
            return found;
        }

        /** Where {@code id} effects come from on this creature, or null (then the hit centre is used). */
        public EnemyEffectSet.Placement placement(EnemyEffectSet.PlacementId id) {
            return placements != null ? placements[id.ordinal()] : null;
        }
    }

    private final Map<String, CreatureVisual> creaturesById = new HashMap<>();
    private final Map<String, Boolean>        failedIds     = new HashMap<>();
    /** Effect sets (order 3) by set id, loaded once per floor for the families on it; a failed id maps to null. */
    private final Map<String, EnemyEffectSet> effectSetsById = new HashMap<>();
    /** Per-type resolution, filled by {@link #ensureLoaded}; read every frame without hashing a string. */
    private final EnumMap<EnemyType, CreatureVisual> visualsByType = new EnumMap<>(EnemyType.class);

    public EnemyAtlasCache() {
        EnemyVisualRegistry.bootstrap();
    }

    /**
     * Loads the creature drawing {@code type} if it is migrated and not loaded yet. Render thread only
     * (it creates textures). Cheap when already resolved; a no-op for a legacy type.
     */
    public void ensureLoaded(EnemyType type) {
        if (type == null || visualsByType.containsKey(type)) return;
        String creatureId = EnemyVisualRegistry.creatureIdFor(type);
        if (creatureId == null || failedIds.containsKey(creatureId)) return;
        CreatureVisual visual = creaturesById.get(creatureId);
        if (visual == null) {
            visual = load(creatureId);
            if (visual == null) return;
            creaturesById.put(creatureId, visual);
            attachEffects(visual, type);
        }
        visualsByType.put(type, visual);
    }

    /** The loaded creature for {@code type}, or null (legacy type, not loaded, or failed to load). */
    public CreatureVisual visualFor(EnemyType type) {
        return visualsByType.get(type);
    }

    public int loadedCreatureCount() {
        return creaturesById.size();
    }

    private CreatureVisual load(String creatureId) {
        Texture body = null;
        Texture glow = null;
        try {
            long startNanos = System.nanoTime();
            FileHandle directory = Gdx.files.internal(EnemyConstants.ENEMY_GENERATED_DIRECTORY);
            EnemyClipManifest manifest = EnemyClipManifest.parse(
                    directory.child(creatureId + EnemyConstants.ENEMY_GENERATED_MANIFEST_SUFFIX));
            body = loadNearest(directory.child(manifest.atlasFileName));
            glow = loadNearest(directory.child(manifest.glowFileName));
            Gdx.app.log(LOG_TAG, "loaded " + creatureId + " (" + body.getWidth() + "x" + body.getHeight()
                    + " + glow) in " + (System.nanoTime() - startNanos) / 1_000_000L + " ms");
            return new CreatureVisual(creatureId, manifest, body, glow);
        } catch (RuntimeException failure) {
            if (body != null) body.dispose();
            if (glow != null) glow.dispose();
            failedIds.put(creatureId, Boolean.TRUE);
            Gdx.app.error(LOG_TAG, "creature '" + creatureId + "' failed to load; its types keep the legacy sprite",
                    failure);
            return null;
        }
    }

    /**
     * Order 3: the creature's family effect set ({@code <family>.fx.json}, the EnemyFamily name in lower
     * case) and the shared status set. A set that fails is logged once and its effects are simply absent.
     */
    private void attachEffects(CreatureVisual visual, EnemyType type) {
        visual.familyEffects = effectSet(type.family().name().toLowerCase(Locale.ROOT));
        visual.statusEffects = effectSet(EnemyConstants.ENEMY_EFFECT_STATUS_SET_ID);
        if (visual.familyEffects != null) visual.placements = visual.familyEffects.placementsFor(visual.creatureId);
    }

    private EnemyEffectSet effectSet(String setId) {
        if (effectSetsById.containsKey(setId)) return effectSetsById.get(setId);
        EnemyEffectSet set = null;
        try {
            set = EnemyEffectSet.load(Gdx.files.internal(EnemyConstants.ENEMY_GENERATED_DIRECTORY), setId);
            Gdx.app.log(LOG_TAG, "loaded effect set " + setId + " (" + set.atlas.getWidth() + "x"
                    + set.atlas.getHeight() + ")");
        } catch (RuntimeException failure) {
            Gdx.app.error(LOG_TAG, "effect set '" + setId + "' failed to load; its creatures draw no effects", failure);
        }
        effectSetsById.put(setId, set);
        return set;
    }

    private static Texture loadNearest(FileHandle file) {
        Texture texture = new Texture(file);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    @Override
    public void dispose() {
        for (CreatureVisual visual : creaturesById.values()) {
            visual.bodyAtlas.dispose();
            visual.glowAtlas.dispose();
        }
        for (EnemyEffectSet set : effectSetsById.values()) {
            if (set != null) set.dispose();
        }
        creaturesById.clear();
        visualsByType.clear();
        effectSetsById.clear();
    }
}

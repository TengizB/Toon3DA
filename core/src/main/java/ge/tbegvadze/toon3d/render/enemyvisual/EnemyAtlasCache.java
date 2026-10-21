package ge.tbegvadze.toon3d.render.enemyvisual;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.EnemyConstants;

import java.util.EnumMap;
import java.util.HashMap;
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

    /** One loaded creature: its manifest and its two atlases. */
    public static final class CreatureVisual {
        public final String            creatureId;
        public final EnemyClipManifest manifest;
        public final Texture           bodyAtlas;
        public final Texture           glowAtlas;

        CreatureVisual(String creatureId, EnemyClipManifest manifest, Texture bodyAtlas, Texture glowAtlas) {
            this.creatureId = creatureId;
            this.manifest   = manifest;
            this.bodyAtlas  = bodyAtlas;
            this.glowAtlas  = glowAtlas;
        }
    }

    private final Map<String, CreatureVisual> creaturesById = new HashMap<>();
    private final Map<String, Boolean>        failedIds     = new HashMap<>();
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
        creaturesById.clear();
        visualsByType.clear();
    }
}

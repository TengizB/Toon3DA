package ge.tbegvadze.toon3d.render.enemyvisual;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import ge.tbegvadze.toon3d.util.EnemyConstants;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One parsed EFFECT SET (generated-enemy-art order 3): {@code <set>.fx.json} + its atlas {@code <set>.fx.png},
 * written by the enemy-art skill's {@code effects.py} (guide section 9). A set is a family's effects
 * ("machine", "insect") or the shared "status" set. Load-time only and immutable afterwards: the effect
 * system reads sprites, bursts and placements by reference or by enum index, never by string, per frame.
 *
 * <p>UNITS. Every emitter number is in CANVAS PIXELS of the creature that throws it (px/s, px/s^2, degrees);
 * {@link EnemyEffectSystem} converts with that creature's own pixel scale, so a spark is the size of the
 * sprite's own pixels at any distance. Frame rects are atlas pixels (image space, y down), exactly as the
 * creature manifest's.
 */
public final class EnemyEffectSet implements Disposable {

    public static final int SUPPORTED_VERSION = 1;

    /** The bursts the runtime fires, by name in the manifest. A set ships the subset it needs. */
    public enum BurstId {
        HIT, HIT_HEAVY, RESIST, KILL, DEATH, LEAK, CHARGE, MUZZLE, PROJECTILE, TRAIL, BLEED,
        BURNING, BURNING_ONSET, POISONED, STUNNED;

        public static final int COUNT = values().length;

        static BurstId fromManifestName(String name) {
            for (BurstId id : values()) {
                if (id.name().equals(name)) return id;
            }
            return null;
        }
    }

    /** Where on a creature a kind of effect comes from (the manifest's per-creature placement names). */
    public enum PlacementId {
        LEAK("leak"), BURN("burn"), CHARGE("charge"), STUN("stun"), POISON("poison"), BODY("body");

        public static final int COUNT = values().length;
        private final String manifestName;

        PlacementId(String manifestName) {
            this.manifestName = manifestName;
        }

        static PlacementId fromManifestName(String name) {
            for (PlacementId id : values()) {
                if (id.manifestName.equals(name)) return id;
            }
            return null;
        }
    }

    /** One frame of a particle sprite: its atlas rect and where the particle's centre sits inside it. */
    public static final class SpriteFrame {
        public final int   atlasX, atlasY, width, height;
        public final float pivotX, pivotY;

        SpriteFrame(int atlasX, int atlasY, int width, int height, float pivotX, float pivotY) {
            this.atlasX = atlasX; this.atlasY = atlasY; this.width = width; this.height = height;
            this.pivotX = pivotX; this.pivotY = pivotY;
        }
    }

    /** A particle kind: emissive (drawn ungraded) or lit; frames over its life or looping at {@code fps}. */
    public static final class Sprite {
        public final EnemyEffectSet  owner;
        public final boolean         emissive;
        public final boolean         looping;
        public final float           framesPerSecond;
        /** {@code frames[orientation][frame]}; orientations are spread evenly over 180 degrees. */
        public final SpriteFrame[][] frames;

        Sprite(EnemyEffectSet owner, boolean emissive, boolean looping, float framesPerSecond, SpriteFrame[][] frames) {
            this.owner = owner; this.emissive = emissive; this.looping = looping;
            this.framesPerSecond = framesPerSecond; this.frames = frames;
        }
    }

    /** One emitter of a burst (guide 9): how many particles, how long, how fast, which way. */
    public static final class Emitter {
        public final Sprite sprite;
        public final int    countMin, countMax;
        public final float  lifeMin, lifeMax;
        public final float  speedMin, speedMax;
        public final float  angleDegrees, spreadDegrees;
        public final float  towardMin, towardMax;
        public final float  gravity;
        public final float  drag;
        public final float  radius;
        /** Orbit radius (px) and period (s); radius 0 = the particle flies instead of circling. */
        public final float  orbitRadius, orbitPeriodSeconds;

        Emitter(Sprite sprite, JsonValue json) {
            this.sprite        = sprite;
            JsonValue count    = json.get("count");
            JsonValue life     = json.get("life");
            JsonValue speed    = json.get("speed");
            JsonValue toward   = json.get("toward");
            this.countMin      = count.getInt(0);
            this.countMax      = count.getInt(1);
            this.lifeMin       = life.getFloat(0);
            this.lifeMax       = life.getFloat(1);
            this.speedMin      = speed.getFloat(0);
            this.speedMax      = speed.getFloat(1);
            this.angleDegrees  = json.getFloat("angle");
            this.spreadDegrees = json.getFloat("spread");
            this.towardMin     = toward.getFloat(0);
            this.towardMax     = toward.getFloat(1);
            this.gravity       = json.getFloat("gravity");
            this.drag          = json.getFloat("drag");
            this.radius        = json.getFloat("radius");
            JsonValue orbit    = json.get("orbit");
            this.orbitRadius        = orbit != null ? orbit.getFloat(0) : 0f;
            this.orbitPeriodSeconds = orbit != null ? orbit.getFloat(1) : 0f;
        }
    }

    /** Emitters fired together; a burst with an interval REPEATS while its condition holds. */
    public static final class Burst {
        public final Emitter[] emitters;
        public final float     intervalMin, intervalMax;

        Burst(Emitter[] emitters, float intervalMin, float intervalMax) {
            this.emitters = emitters; this.intervalMin = intervalMin; this.intervalMax = intervalMax;
        }

        public boolean repeats() {
            return intervalMax > 0f;
        }
    }

    /** A manifest anchor of the CURRENT frame + a canvas offset, spread over {@code radius} px. */
    public static final class Placement {
        public final EnemyClipManifest.Anchor anchor;
        public final float offsetX, offsetY, radius;

        Placement(EnemyClipManifest.Anchor anchor, float offsetX, float offsetY, float radius) {
            this.anchor = anchor; this.offsetX = offsetX; this.offsetY = offsetY; this.radius = radius;
        }
    }

    public final String  setId;
    public final Texture atlas;
    private final Burst[] bursts = new Burst[BurstId.COUNT];
    private final Map<String, Placement[]> placementsByCreature = new HashMap<>();

    private EnemyEffectSet(String setId, Texture atlas) {
        this.setId = setId;
        this.atlas = atlas;
    }

    /** The burst {@code id}, or null when this set does not ship it. */
    public Burst burst(BurstId id) {
        return bursts[id.ordinal()];
    }

    /** The placement table of {@code creatureId} (indexed by {@link PlacementId}), or null when the set has none. */
    public Placement[] placementsFor(String creatureId) {
        return placementsByCreature.get(creatureId);
    }

    /**
     * Parses a set and loads its atlas (render thread: it creates a texture). Throws on an unsupported
     * version or an emitter naming an unknown sprite; the caller logs it and draws the creature without
     * effects (never a crash).
     */
    static EnemyEffectSet load(FileHandle directory, String setId) {
        FileHandle manifestFile = directory.child(setId + EnemyConstants.ENEMY_EFFECT_MANIFEST_SUFFIX);
        JsonValue root = new JsonReader().parse(manifestFile);
        int version = root.getInt("version", -1);
        if (version != SUPPORTED_VERSION) {
            throw new IllegalStateException("unsupported fx version " + version + " in " + manifestFile.path());
        }
        Texture atlas = new Texture(directory.child(root.getString("atlas")));
        atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        EnemyEffectSet set = new EnemyEffectSet(setId, atlas);
        try {
            Map<String, Sprite> sprites = new HashMap<>();
            for (JsonValue spriteJson = root.get("sprites").child; spriteJson != null; spriteJson = spriteJson.next) {
                sprites.put(spriteJson.name, parseSprite(set, spriteJson));
            }
            for (JsonValue burstJson = root.get("bursts").child; burstJson != null; burstJson = burstJson.next) {
                BurstId id = BurstId.fromManifestName(burstJson.name);
                if (id == null) continue;   // a burst a later runtime knows; this one skips it
                set.bursts[id.ordinal()] = parseBurst(sprites, burstJson, manifestFile);
            }
            for (JsonValue creatureJson = root.get("creatures").child; creatureJson != null; creatureJson = creatureJson.next) {
                Placement[] placements = new Placement[PlacementId.COUNT];
                for (JsonValue placementJson = creatureJson.child; placementJson != null; placementJson = placementJson.next) {
                    PlacementId id = PlacementId.fromManifestName(placementJson.name);
                    if (id == null) continue;
                    EnemyClipManifest.Anchor anchor = EnemyClipManifest.Anchor.fromManifestName(
                            placementJson.getString("anchor").toLowerCase(Locale.ROOT));
                    if (anchor == null) anchor = anchorBySlotName(placementJson.getString("anchor"));
                    JsonValue offset = placementJson.get("offset");
                    placements[id.ordinal()] = new Placement(anchor, offset.getFloat(0), offset.getFloat(1),
                            placementJson.getFloat("radius"));
                }
                set.placementsByCreature.put(creatureJson.name, placements);
            }
            return set;
        } catch (RuntimeException failure) {
            atlas.dispose();
            throw failure;
        }
    }

    /** "emitter" names the slot (the muzzle or the mouth), not a manifest anchor; anything unknown is the hit centre. */
    private static EnemyClipManifest.Anchor anchorBySlotName(String name) {
        for (EnemyClipManifest.Anchor anchor : EnemyClipManifest.Anchor.values()) {
            if (anchor.name().equalsIgnoreCase(name)) return anchor;
        }
        return EnemyClipManifest.Anchor.HIT_CENTRE;
    }

    private static Sprite parseSprite(EnemyEffectSet set, JsonValue json) {
        JsonValue orientationsJson = json.get("frames");
        SpriteFrame[][] frames = new SpriteFrame[orientationsJson.size][];
        int orientationIndex = 0;
        for (JsonValue rowJson = orientationsJson.child; rowJson != null; rowJson = rowJson.next) {
            SpriteFrame[] row = new SpriteFrame[rowJson.size];
            int frameIndex = 0;
            for (JsonValue frameJson = rowJson.child; frameJson != null; frameJson = frameJson.next) {
                JsonValue rect  = frameJson.get("rect");
                JsonValue pivot = frameJson.get("pivot");
                row[frameIndex++] = new SpriteFrame(rect.getInt(0), rect.getInt(1), rect.getInt(2), rect.getInt(3),
                        pivot.getFloat(0), pivot.getFloat(1));
            }
            frames[orientationIndex++] = row;
        }
        return new Sprite(set, json.getBoolean("emissive"), "loop".equals(json.getString("mode")),
                json.getFloat("fps"), frames);
    }

    private static Burst parseBurst(Map<String, Sprite> sprites, JsonValue json, FileHandle manifestFile) {
        JsonValue emittersJson = json.get("emitters");
        Emitter[] emitters = new Emitter[emittersJson.size];
        int emitterIndex = 0;
        for (JsonValue emitterJson = emittersJson.child; emitterJson != null; emitterJson = emitterJson.next) {
            Sprite sprite = sprites.get(emitterJson.getString("sprite"));
            if (sprite == null) {
                throw new IllegalStateException("burst " + json.name + " names unknown sprite "
                        + emitterJson.getString("sprite") + " in " + manifestFile.path());
            }
            emitters[emitterIndex++] = new Emitter(sprite, emitterJson);
        }
        JsonValue interval = json.get("interval");
        return new Burst(emitters, interval != null ? interval.getFloat(0) : 0f, interval != null ? interval.getFloat(1) : 0f);
    }

    @Override
    public void dispose() {
        atlas.dispose();
    }
}

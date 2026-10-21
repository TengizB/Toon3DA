package ge.tbegvadze.toon3d.render.enemyvisual;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * One creature's parsed {@code <name>.clips.json} (MANIFEST_VERSION 1, guide 7.7). Load-time only and
 * immutable afterwards: the animator reads frames by index and never touches JSON again.
 *
 * <p>Every coordinate is kept exactly as the manifest states it — IMAGE space, origin top-left, y down —
 * on the canvas (offset, anchors) or the atlas (rect). {@link EnemyBodyDrawer} does the one flip into the
 * world's y-up space.
 */
public final class EnemyClipManifest {

    public static final int SUPPORTED_VERSION = 1;

    /** What a clip does when its last frame ends (guide 7.6 {@code mode}). */
    public enum Mode {
        /** Wraps to frame 0. */
        LOOP,
        /** Plays once; the animator then falls back to the ongoing condition. */
        ONCE,
        /** Plays once and holds its last frame (WIND_UP, DEFEND, DEATH). */
        HOLD
    }

    /** The frame the runtime acts on (guide 7.7 {@code event}). */
    public enum FrameEvent { NONE, EMIT, IMPACT }

    /** Anchor slots every creature reports; EMITTER is the muzzle or the mouth, whichever the rig names. */
    public enum Anchor {
        GROUND("ground"), HIT_CENTRE("hit_centre"), EYE("eye"), EMITTER("muzzle", "mouth");

        private final String[] manifestNames;

        Anchor(String... manifestNames) {
            this.manifestNames = manifestNames;
        }

        /** The slot a manifest anchor name fills, or null for a name this runtime does not use. */
        static Anchor fromManifestName(String manifestName) {
            for (Anchor anchor : values()) {
                for (String name : anchor.manifestNames) {
                    if (name.equals(manifestName)) return anchor;
                }
            }
            return null;
        }
    }

    public static final class Frame {
        public final int        atlasX, atlasY, width, height;
        /** Where the trimmed rect's top-left sits on the canvas (y down). */
        public final int        offsetLeft, offsetTop;
        public final float      durationSeconds;
        public final boolean    hasGlow;
        public final FrameEvent event;
        /** Canvas coordinates (y down), {@code [anchor.ordinal() * 2]} = x, {@code + 1} = y; NaN when absent. */
        public final float[]    anchors;

        Frame(int atlasX, int atlasY, int width, int height, int offsetLeft, int offsetTop,
              float durationSeconds, boolean hasGlow, FrameEvent event, float[] anchors) {
            this.atlasX = atlasX; this.atlasY = atlasY; this.width = width; this.height = height;
            this.offsetLeft = offsetLeft; this.offsetTop = offsetTop;
            this.durationSeconds = durationSeconds;
            this.hasGlow = hasGlow;
            this.event   = event;
            this.anchors = anchors;
        }
    }

    public static final class Clip {
        public final EnemyClipId id;
        public final Mode        mode;
        public final Frame[]     frames;
        /** {@code frameEndSeconds[i]} = when frame i stops showing, measured from the clip's start. */
        public final float[]     frameEndSeconds;
        public final float       durationSeconds;

        Clip(EnemyClipId id, Mode mode, Frame[] frames) {
            this.id     = id;
            this.mode   = mode;
            this.frames = frames;
            this.frameEndSeconds = new float[frames.length];
            float elapsed = 0f;
            for (int frameIndex = 0; frameIndex < frames.length; frameIndex++) {
                elapsed += frames[frameIndex].durationSeconds;
                frameEndSeconds[frameIndex] = elapsed;
            }
            this.durationSeconds = elapsed;
        }

        /**
         * The frame showing {@code clipSeconds} after the clip started. A LOOP wraps; ONCE / HOLD clamp to
         * the last frame. A linear scan: clips are 2-18 frames, so this beats a search and never allocates.
         */
        public int frameIndexAt(float clipSeconds) {
            int lastIndex = frames.length - 1;
            if (durationSeconds <= 0f) return lastIndex;
            float time = clipSeconds;
            if (mode == Mode.LOOP) {
                time = clipSeconds % durationSeconds;
                if (time < 0f) time += durationSeconds;
            } else if (time >= durationSeconds) {
                return lastIndex;
            }
            for (int frameIndex = 0; frameIndex < lastIndex; frameIndex++) {
                if (time < frameEndSeconds[frameIndex]) return frameIndex;
            }
            return lastIndex;
        }

        /** True once a non-looping clip has played through (a LOOP never finishes). */
        public boolean isFinishedAt(float clipSeconds) {
            return mode != Mode.LOOP && clipSeconds >= durationSeconds;
        }
    }

    public final String creatureId;
    public final int    canvasWidth;
    public final int    canvasHeight;
    public final int    groundY;
    public final String atlasFileName;
    public final String glowFileName;
    /** Indexed by {@link EnemyClipId#ordinal()}; null for a clip the creature does not ship. */
    private final Clip[] clips;

    private EnemyClipManifest(String creatureId, int canvasWidth, int canvasHeight, int groundY,
                              String atlasFileName, String glowFileName, Clip[] clips) {
        this.creatureId    = creatureId;
        this.canvasWidth   = canvasWidth;
        this.canvasHeight  = canvasHeight;
        this.groundY       = groundY;
        this.atlasFileName = atlasFileName;
        this.glowFileName  = glowFileName;
        this.clips         = clips;
    }

    /** The clip with this id, or null when the creature does not ship it. */
    public Clip clip(EnemyClipId id) {
        return clips[id.ordinal()];
    }

    /**
     * Parses a manifest. Throws {@link IllegalStateException} on an unsupported version or a missing
     * IDLE clip (the caller logs it and keeps the type on the legacy path, rule R5). Clips this runtime
     * does not know by name (an ability clip a later order adds) are skipped, not rejected.
     */
    public static EnemyClipManifest parse(FileHandle manifestFile) {
        JsonValue root = new JsonReader().parse(manifestFile);
        int version = root.getInt("version", -1);
        if (version != SUPPORTED_VERSION) {
            throw new IllegalStateException("unsupported manifest version " + version + " in " + manifestFile.path());
        }
        JsonValue canvas = root.get("canvas");
        Clip[] clips = new Clip[EnemyClipId.COUNT];
        for (JsonValue clipJson = root.get("clips").child; clipJson != null; clipJson = clipJson.next) {
            EnemyClipId id = EnemyClipId.fromManifestName(clipJson.name);
            if (id == null) continue;
            Mode mode = Mode.valueOf(clipJson.getString("mode").toUpperCase());
            JsonValue framesJson = clipJson.get("frames");
            Frame[] frames = new Frame[framesJson.size];
            int frameIndex = 0;
            for (JsonValue frameJson = framesJson.child; frameJson != null; frameJson = frameJson.next) {
                frames[frameIndex++] = parseFrame(frameJson);
            }
            clips[id.ordinal()] = new Clip(id, mode, frames);
        }
        if (clips[EnemyClipId.IDLE.ordinal()] == null) {
            throw new IllegalStateException("manifest has no IDLE clip: " + manifestFile.path());
        }
        return new EnemyClipManifest(root.getString("creature"), canvas.getInt(0), canvas.getInt(1),
                root.getInt("groundY"), root.getString("atlas"), root.getString("glow"), clips);
    }

    private static Frame parseFrame(JsonValue frameJson) {
        JsonValue rect   = frameJson.get("rect");
        JsonValue offset = frameJson.get("offset");
        String eventName = frameJson.getString("event", null);
        FrameEvent event = eventName == null ? FrameEvent.NONE : FrameEvent.valueOf(eventName.toUpperCase());
        float[] anchors = new float[Anchor.values().length * 2];
        java.util.Arrays.fill(anchors, Float.NaN);
        JsonValue anchorsJson = frameJson.get("anchors");
        if (anchorsJson != null) {
            for (JsonValue anchorJson = anchorsJson.child; anchorJson != null; anchorJson = anchorJson.next) {
                Anchor anchor = Anchor.fromManifestName(anchorJson.name);
                if (anchor == null) continue;
                anchors[anchor.ordinal() * 2]     = anchorJson.getFloat(0);
                anchors[anchor.ordinal() * 2 + 1] = anchorJson.getFloat(1);
            }
        }
        return new Frame(rect.getInt(0), rect.getInt(1), rect.getInt(2), rect.getInt(3),
                offset.getInt(0), offset.getInt(1), frameJson.getFloat("duration"),
                frameJson.getBoolean("glow", false), event, anchors);
    }
}

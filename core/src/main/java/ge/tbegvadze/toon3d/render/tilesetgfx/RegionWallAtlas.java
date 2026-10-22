package ge.tbegvadze.toon3d.render.tilesetgfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import ge.tbegvadze.toon3d.tileset.RegionWallSet;
import ge.tbegvadze.toon3d.tileset.RegionWallSets;
import ge.tbegvadze.toon3d.util.RenderConstants;
import ge.tbegvadze.toon3d.util.TilesetConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ONE FLOOR'S REGION WALL ATLAS (generated-wall-art order 2; docs/wall-art-creation-guide.txt sections 12-13):
 * the parsed {@code <region>.walls.json} manifest plus its two textures — the opaque RGB atlas holding every
 * wall, door and column face with its mips 128 / 64 (one bind for the whole wall pass) and the binary-alpha
 * glow atlas with the same packing plus the extra animation frames. Built by World for the floor's region set
 * and disposed with the floor, so only one region is ever resident.
 *
 * <p>Faces are addressed by a dense index (the manifest order: walls, doors, columns) so the renderer's hot path
 * reads plain int arrays — never a map — after {@link #faceIndex(String)} resolved its per-level tables once.
 * Rects are TOP-LEFT pixel corners (row 0 = top), exactly as the manifest states; the renderer adds a texel
 * offset inside the face and passes the result straight to SpriteBatch (srcY = top row, the Y-up rule holds).
 *
 * <p>Rule R7: a manifest that fails to load, or lacks an id the region set names, is logged and the floor uses
 * set A ({@link #loadForFloor}); if even set A cannot load the method returns null and the renderer draws flat
 * walls rather than crash.
 */
public final class RegionWallAtlas implements Disposable {

    /** "animate" values of a face (manifest): none, every face of that sprite, or a seeded ~1 in 6. */
    public static final int ANIMATE_NONE = 0;
    public static final int ANIMATE_ALL  = 1;
    public static final int ANIMATE_SOME = 2;

    private static final String LOG_TAG = "RegionWallAtlas";
    private static final int    MANIFEST_VERSION = 1;

    private final String  setId;
    private final Texture atlasTexture;
    private final Texture glowTexture;
    private final Map<String, Integer> faceIndexById;
    private final int[][]   sourceX;          // [mip][face]
    private final int[][]   sourceY;          // [mip][face]
    private final boolean[] glowing;          // the face has emissive pixels (its glow rects are not empty)
    private final int[]     animateMode;      // ANIMATE_*
    private final int[][]   glowFrameX;       // [face][frame], frame 0 = the face's own glow rect
    private final int[][]   glowFrameY;
    private final float[][] glowFrameSeconds;
    private final float[]   glowCycleSeconds;  // sum of a face's frame durations

    private RegionWallAtlas(String setId, Texture atlasTexture, Texture glowTexture, List<FaceEntry> faces) {
        this.setId           = setId;
        this.atlasTexture    = atlasTexture;
        this.glowTexture     = glowTexture;
        int faceCount = faces.size();
        int mipCount  = RenderConstants.WALL_MAX_MIP + 1;
        faceIndexById    = new HashMap<>();
        sourceX          = new int[mipCount][faceCount];
        sourceY          = new int[mipCount][faceCount];
        glowing          = new boolean[faceCount];
        animateMode      = new int[faceCount];
        glowFrameX       = new int[faceCount][];
        glowFrameY       = new int[faceCount][];
        glowFrameSeconds = new float[faceCount][];
        glowCycleSeconds = new float[faceCount];
        for (int faceIndex = 0; faceIndex < faceCount; faceIndex++) {
            FaceEntry face = faces.get(faceIndex);
            faceIndexById.put(face.id, faceIndex);
            for (int mip = 0; mip < mipCount; mip++) {
                sourceX[mip][faceIndex] = face.rectX[mip];
                sourceY[mip][faceIndex] = face.rectY[mip];
            }
            glowing[faceIndex]          = face.glowing;
            animateMode[faceIndex]      = face.animateMode;
            glowFrameX[faceIndex]       = face.frameX;
            glowFrameY[faceIndex]       = face.frameY;
            glowFrameSeconds[faceIndex] = face.frameSeconds;
            for (float seconds : face.frameSeconds) glowCycleSeconds[faceIndex] += seconds;
        }
    }

    /**
     * Loads the atlas for a floor whose palette names {@code wallSetId} (an alias resolves to the set it borrows;
     * null or unknown = set A). R7: on any failure, logs and loads set A; returns null when set A fails too.
     */
    public static RegionWallAtlas loadForFloor(String wallSetId) {
        RegionWallSet set = RegionWallSets.resolveOrDefault(wallSetId);
        try {
            return load(set);
        } catch (RuntimeException failure) {
            log("region wall set '" + set.id() + "' failed to load (" + failure.getMessage() + ") - using "
                    + TilesetConstants.WALL_SET_DEFAULT);
        }
        RegionWallSet fallback = RegionWallSets.resolveOrDefault(TilesetConstants.WALL_SET_DEFAULT);
        if (fallback == set) return null;
        try {
            return load(fallback);
        } catch (RuntimeException failure) {
            log("fallback wall set failed to load (" + failure.getMessage() + ") - walls draw flat");
            return null;
        }
    }

    private static RegionWallAtlas load(RegionWallSet set) {
        String directory = TilesetConstants.WALL_SET_ASSET_DIRECTORY;
        FileHandle manifestFile = Gdx.files.internal(directory + set.manifestFileName());
        JsonValue manifest = new JsonReader().parse(manifestFile);
        if (manifest.getInt("version", -1) != MANIFEST_VERSION) {
            throw new IllegalStateException("manifest version " + manifest.getInt("version", -1));
        }
        if (manifest.getInt("size", -1) != RenderConstants.WALL_FACE_SIZE
                || manifest.getInt("mips", 0) < RenderConstants.WALL_MAX_MIP + 1) {
            throw new IllegalStateException("manifest face size / mip count do not match the engine");
        }
        List<FaceEntry> faces = new ArrayList<>();
        for (String section : new String[]{ "walls", "doors", "columns" }) {
            for (JsonValue entry = manifest.get(section).child; entry != null; entry = entry.next) {
                faces.add(FaceEntry.parse(entry));
            }
        }
        Map<String, Boolean> present = new HashMap<>();
        for (FaceEntry face : faces) present.put(face.id, Boolean.TRUE);
        List<String> required = new ArrayList<>(set.allWallIds());
        required.addAll(set.doorIds());
        required.addAll(set.columnIds());
        for (String id : required) {
            if (!present.containsKey(id)) throw new IllegalStateException("manifest lacks " + id);
        }
        // The floor/ceiling are drawn by SurfaceMaterials from the set's own file names; the manifest must agree,
        // and is checked before any texture is loaded so a bad manifest can never leak one (R7).
        JsonValue surfaces = manifest.get("surfaces");
        if (surfaces == null
                || !set.floorFileName().equals(surfaces.getString("floor", null))
                || !set.ceilingFileName().equals(surfaces.getString("ceiling", null))) {
            throw new IllegalStateException("manifest surfaces do not match " + set.floorFileName() + " / "
                    + set.ceilingFileName());
        }
        Texture atlas = loadNearest(directory + manifest.getString("atlas"));
        Texture glow;
        try {
            glow = loadNearest(directory + manifest.getString("glow"));
        } catch (RuntimeException failure) {
            atlas.dispose();
            throw failure;
        }
        if (Gdx.app != null) {
            Gdx.app.log(LOG_TAG, "loaded region wall set '" + set.id() + "': " + faces.size() + " faces");
        }
        return new RegionWallAtlas(set.id(), atlas, glow, faces);
    }

    private static Texture loadNearest(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    private static void log(String message) {
        if (Gdx.app != null) Gdx.app.error(LOG_TAG, message);
    }

    /** The REAL set this atlas holds (an alias's borrowed set). */
    public String setId() {
        return setId;
    }

    /** The opaque wall + door + column atlas (one bind for the base pass). */
    public Texture atlasTexture() {
        return atlasTexture;
    }

    /** The binary-alpha glow atlas (same packing, plus animation frames). */
    public Texture glowTexture() {
        return glowTexture;
    }


    /** The dense face index of a manifest id, or -1. Resolve once per level, never per frame. */
    public int faceIndex(String id) {
        Integer index = faceIndexById.get(id);
        return index != null ? index : -1;
    }

    /** Top-left x of {@code face} at {@code mip} (0..WALL_MAX_MIP) in both atlases. */
    public int sourceX(int mip, int face) {
        return sourceX[mip][face];
    }

    /** Top-left y (row 0 = top) of {@code face} at {@code mip} in both atlases. */
    public int sourceY(int mip, int face) {
        return sourceY[mip][face];
    }

    public boolean isGlowing(int face) {
        return glowing[face];
    }

    public int animateMode(int face) {
        return animateMode[face];
    }

    /** Glow frames of {@code face} (1 = static). Frames carry no mips: past mip 0 the static glow mips are drawn. */
    public int glowFrameCount(int face) {
        return glowFrameX[face].length;
    }

    public int glowFrameX(int face, int frame) {
        return glowFrameX[face][frame];
    }

    public int glowFrameY(int face, int frame) {
        return glowFrameY[face][frame];
    }

    /** The frame durations of {@code face}, in seconds (shared array: read-only). */
    public float[] glowFrameSeconds(int face) {
        return glowFrameSeconds[face];
    }

    /** Length of {@code face}'s glow animation cycle, in seconds. */
    public float glowCycleSeconds(int face) {
        return glowCycleSeconds[face];
    }

    @Override
    public void dispose() {
        atlasTexture.dispose();
        glowTexture.dispose();
    }

    /** One manifest entry (a wall, door or column face). */
    private static final class FaceEntry {
        String   id;
        int[]    rectX = new int[RenderConstants.WALL_MAX_MIP + 1];
        int[]    rectY = new int[RenderConstants.WALL_MAX_MIP + 1];
        boolean  glowing;
        int      animateMode;
        int[]    frameX;
        int[]    frameY;
        float[]  frameSeconds;

        static FaceEntry parse(JsonValue entry) {
            FaceEntry face = new FaceEntry();
            face.id = entry.getString("id");
            JsonValue rect = entry.get("rect");
            face.rectX[0] = rect.getInt(0);
            face.rectY[0] = rect.getInt(1);
            JsonValue mipRects = entry.get("mipRects");
            for (int mip = 1; mip <= RenderConstants.WALL_MAX_MIP; mip++) {
                JsonValue mipRect = mipRects.get(mip - 1);
                face.rectX[mip] = mipRect.getInt(0);
                face.rectY[mip] = mipRect.getInt(1);
            }
            JsonValue glowRect = entry.get("glowRect");
            face.glowing = glowRect != null && !glowRect.isNull();
            JsonValue frames = entry.get("glowFrames");
            if (frames != null && !frames.isNull() && frames.size > 1) {
                face.frameX       = new int[frames.size];
                face.frameY       = new int[frames.size];
                face.frameSeconds = new float[frames.size];
                int frameIndex = 0;
                for (JsonValue frame = frames.child; frame != null; frame = frame.next, frameIndex++) {
                    JsonValue frameRect = frame.get("rect");
                    face.frameX[frameIndex]       = frameRect.getInt(0);
                    face.frameY[frameIndex]       = frameRect.getInt(1);
                    face.frameSeconds[frameIndex] = frame.getFloat("duration");
                }
            } else {
                face.frameX       = new int[]{ face.glowing ? glowRect.getInt(0) : face.rectX[0] };
                face.frameY       = new int[]{ face.glowing ? glowRect.getInt(1) : face.rectY[0] };
                face.frameSeconds = new float[]{ 1f };
            }
            String animate = entry.getString("animate", null);
            face.animateMode = "all".equals(animate) ? ANIMATE_ALL : "some".equals(animate) ? ANIMATE_SOME : ANIMATE_NONE;
            return face;
        }
    }
}

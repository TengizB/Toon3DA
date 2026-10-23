package ge.tbegvadze.toon3d.render.propvisual;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import ge.tbegvadze.toon3d.util.GameMath;

import static ge.tbegvadze.toon3d.util.RenderConstants.*;

/**
 * One generated PROP SHEET (generated-prop-art order 1): the body frames and the glow frames of one environment
 * object, baked by the kit's {@code props.py} into a grid (frame k at column k % columns, row k / columns), plus
 * the manifest numbers. A still prop is a one-frame sheet.
 *
 * <p>Both textures are created from their files (so LibGDX reloads them after a GL context loss), with Nearest
 * filtering like every other billboard. The body's per-frame, per-column opaque extent is measured once at load
 * so the prop renderer records only opaque slices in the shared occluder buffer.
 */
public final class GeneratedPropSheet implements Disposable {

    private final String  sheetId;
    private final Texture bodyTexture;
    private final Texture glowTexture;
    private final int     frameWidth;
    private final int     frameHeight;
    private final int     frameCount;
    private final int     columns;
    private final float[] frameDurationsSeconds;
    private final float   cycleSeconds;
    // [frame * frameWidth + column]: the smallest / largest opaque row of that frame column (frame-local), or -1
    private final int[]   opaqueTopRow;
    private final int[]   opaqueBottomRow;
    private final boolean ceilingMounted;

    private GeneratedPropSheet(String sheetId, Texture bodyTexture, Texture glowTexture, int frameWidth,
                               int frameHeight, int frameCount, int columns, float frameSeconds,
                               int[] opaqueTopRow, int[] opaqueBottomRow) {
        this.sheetId         = sheetId;
        this.bodyTexture     = bodyTexture;
        this.glowTexture     = glowTexture;
        this.frameWidth      = frameWidth;
        this.frameHeight     = frameHeight;
        this.frameCount      = frameCount;
        this.columns         = columns;
        this.frameDurationsSeconds = new float[frameCount];
        java.util.Arrays.fill(frameDurationsSeconds, frameSeconds);
        this.cycleSeconds    = frameSeconds * frameCount;
        this.opaqueTopRow    = opaqueTopRow;
        this.opaqueBottomRow = opaqueBottomRow;
        this.ceilingMounted  = GeneratedPropRegistry.isCeilingMounted(sheetId);
    }

    /**
     * Loads {@code <sheetId>.prop.json / .prop.png / .glow.png} from {@code directory}. Render thread only.
     *
     * @throws RuntimeException when a file is missing or the manifest is malformed (the caller logs and skips)
     */
    public static GeneratedPropSheet load(FileHandle directory, String sheetId) {
        JsonValue manifest = new JsonReader().parse(directory.child(sheetId + PROP_SHEET_MANIFEST_SUFFIX));
        int version = manifest.getInt("version");
        if (version != PROP_SHEET_MANIFEST_VERSION) {
            throw new IllegalStateException("prop sheet '" + sheetId + "': manifest version " + version
                    + ", expected " + PROP_SHEET_MANIFEST_VERSION);
        }
        int frameWidth   = manifest.getInt("frameWidth");
        int frameHeight  = manifest.getInt("frameHeight");
        int frameCount   = manifest.getInt("frameCount");
        int columns      = manifest.getInt("columns");
        float frameSeconds = manifest.getFloat("frameSeconds");
        if (frameWidth <= 0 || frameHeight <= 0 || frameCount <= 0 || columns <= 0 || frameSeconds <= 0f) {
            throw new IllegalStateException("prop sheet '" + sheetId + "': degenerate manifest");
        }

        FileHandle bodyFile = directory.child(sheetId + PROP_SHEET_BODY_SUFFIX);
        int[] opaqueTopRow    = new int[frameCount * frameWidth];
        int[] opaqueBottomRow = new int[frameCount * frameWidth];
        Pixmap bodyPixels = new Pixmap(bodyFile);
        try {
            int rows = (frameCount + columns - 1) / columns;
            if (bodyPixels.getWidth() < columns * frameWidth || bodyPixels.getHeight() < rows * frameHeight) {
                throw new IllegalStateException("prop sheet '" + sheetId + "': image smaller than its frame grid");
            }
            for (int frame = 0; frame < frameCount; frame++) {
                int originX = (frame % columns) * frameWidth;
                int originY = (frame / columns) * frameHeight;
                for (int column = 0; column < frameWidth; column++) {
                    int top = -1;
                    int bottom = -1;
                    for (int row = 0; row < frameHeight; row++) {
                        int alpha = bodyPixels.getPixel(originX + column, originY + row) & 0xff;
                        if (alpha >= PROP_SHEET_OPAQUE_ALPHA_THRESHOLD) {
                            if (top == -1) top = row;
                            bottom = row;
                        }
                    }
                    opaqueTopRow[frame * frameWidth + column]    = top;
                    opaqueBottomRow[frame * frameWidth + column] = bottom;
                }
            }
        } finally {
            bodyPixels.dispose();
        }

        Texture body = null;
        Texture glow = null;
        try {
            body = loadNearest(bodyFile);
            glow = loadNearest(directory.child(sheetId + PROP_SHEET_GLOW_SUFFIX));
        } catch (RuntimeException failure) {
            if (body != null) body.dispose();
            throw failure;
        }
        return new GeneratedPropSheet(sheetId, body, glow, frameWidth, frameHeight, frameCount, columns,
                frameSeconds, opaqueTopRow, opaqueBottomRow);
    }

    private static Texture loadNearest(FileHandle file) {
        Texture texture = new Texture(file);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    /**
     * The frame a prop on this tile shows at {@code timeSeconds}: the sheet's loop, offset by a per-tile phase
     * so neighbouring copies never animate in lockstep (rule R2). Allocation-free.
     */
    public int frameIndexAt(float timeSeconds, int tileColumn, int tileRow) {
        if (frameCount <= 1) return 0;
        int bucket = GameMath.wallVariantIndex(tileColumn, tileRow, 0, PROP_SHEET_PHASE_SEED, PROP_SHEET_PHASE_BUCKETS);
        float phaseSeconds = cycleSeconds * bucket / PROP_SHEET_PHASE_BUCKETS;
        return GameMath.wallGlowFrameIndex(timeSeconds, phaseSeconds, frameDurationsSeconds, frameCount);
    }

    /** Texture x of the frame's left edge. */
    public int frameOriginX(int frame) {
        return (frame % columns) * frameWidth;
    }

    /** Texture y of the frame's top edge (row 0 = the image's top). */
    public int frameOriginY(int frame) {
        return (frame / columns) * frameHeight;
    }

    /**
     * True when the frame's column has an opaque pixel inside rows [sourceY, sourceY + sourceHeight) (both
     * frame-local) - the slices worth recording as an occluder.
     */
    public boolean hasOpaqueRun(int frame, int column, int sourceY, int sourceHeight) {
        int index = frame * frameWidth + column;
        int top = opaqueTopRow[index];
        if (top == -1) return false;
        return Math.max(top, sourceY) <= Math.min(opaqueBottomRow[index], sourceY + sourceHeight - 1);
    }

    public String  sheetId()     { return sheetId; }
    /** True when the prop hangs from the ceiling: the prop renderer draws no floor contact shadow for it. */
    public boolean isCeilingMounted() { return ceilingMounted; }
    public Texture bodyTexture() { return bodyTexture; }
    public Texture glowTexture() { return glowTexture; }
    public int     frameWidth()  { return frameWidth; }
    public int     frameHeight() { return frameHeight; }
    public int     frameCount()  { return frameCount; }

    @Override
    public void dispose() {
        bodyTexture.dispose();
        glowTexture.dispose();
    }
}

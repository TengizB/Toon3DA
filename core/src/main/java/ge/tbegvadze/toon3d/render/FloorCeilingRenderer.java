package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RenderResolution;

import static ge.tbegvadze.toon3d.util.Constants.*;
import static ge.tbegvadze.toon3d.util.RenderConstants.*;

/**
 * Renders the textured floor and ceiling on the GPU (render-performance-full-hd), Lodev floor casting adapted
 * for Y-up coordinates, through SpriteBatch's DEFAULT shader only (art-direction rule V2). Nothing is shaded
 * per pixel on the CPU and nothing is uploaded per frame.
 *
 * <p><b>Near region (depth up to FLOOR_MAX_VISIBLE_DISTANCE_CELLS): one quad per floor tile, drawn as a
 * perspective PLANE.</b> The batch's projection is a projective matrix ({@link GameMath#floorPlaneClipMatrix})
 * that maps tile coordinates straight to clip space with w = depth, so the GPU interpolates the material UVs
 * (0..1 across each tile) and the vertex colours perspective-correctly, and clips the plane exactly at the
 * near plane and at the 15-tile clamp. Each tile's colour is the CPU caster's texel factor evaluated at its
 * four corners (shade x tile brightness, the graded tint, the alert terms, the muzzle light - the same GameMath
 * expressions, R5/R7), and its material level comes from the per-row level table at the tile's centre depth.
 * This replaced the plan's one-quad-per-row spans after CP4 measured that the GPU cost of ~2,500 one-pixel-tall
 * full-width quads exceeded the CPU backdrop it replaced (see the checkpoint file).
 *
 * <p><b>Far band (rows at or past the clamp): one horizontal span strip.</b> Past the clamp every row had the
 * same distance in the CPU caster, so the whole band is ONE row-walk ({@link GameMath#floorSpanAxisCrossingParameter},
 * runs of equal tile brightness) drawn at the band's height through the screen camera.
 *
 * <p>Colours above 1: a mood tint can exceed 1.0 per channel. Where the material's brightest texel allows, the
 * channel scale is baked into the per-floor material textures ({@link GameMath#materialChannelBakeScale}) and
 * every vertex colour is divided by it; any factor still above 1 (the muzzle light, an unbakeable tint) is drawn
 * by additive layers ({@link GameMath#colourFactorExcessLayer}, blend SRC_ALPHA, ONE), skipped entirely when no
 * vertex needs them.
 *
 * <p>Row model (R1, identical to the CPU backdrop): the tier's row count (FULL_HD 540, HD 180); floor row r
 * covers world Y [r*k, (r+1)*k), k = WORLD_HEIGHT / rowCount, its ceiling twin is mirrored. Distance, shade,
 * tint, light falloff and material level per row come from tables rebuilt only on a resolution change.
 *
 * <p>Owns one SpriteBatch and the two surfaces' material textures; nothing allocates in {@link #render}.
 */
public class FloorCeilingRenderer implements Renderable, Disposable {

    /** Floats per SpriteBatch quad: 4 vertices x (x, y, packed colour, u, v). */
    private static final int QUAD_FLOATS = 20;
    /**
     * Packed OPAQUE black: an additive corner with no excess. Under (SRC_ALPHA, ONE) it adds nothing, and its alpha of
     * 1 keeps a quad whose other corners do carry excess interpolating LINEARLY (alpha 0 here would scale the added
     * light by the interpolated alpha too and fade the splash edge quadratically).
     */
    private static final float NO_EXCESS_COLOUR = Color.toFloatBits(0f, 0f, 0f, 1f);
    /** The base pass is "layer" -1; additive layers are 0, 1, ... */
    private static final int BASE_LAYER = -1;
    /** Nearest visible floor depth: the bottom screen row (pixelOffset = H/2 -> d = 2 * FLOOR_CAMERA_Z). */
    private static final float NEAREST_VISIBLE_DEPTH = 2f * FLOOR_CAMERA_Z;
    private static final int LATTICE_POINT_CAPACITY =
            (FLOOR_TILE_LATTICE_MAX_EXTENT + 1) * (FLOOR_TILE_LATTICE_MAX_EXTENT + 1);
    private static final int TILE_CAPACITY = FLOOR_TILE_LATTICE_MAX_EXTENT * FLOOR_TILE_LATTICE_MAX_EXTENT;

    private final Level level;
    private final SpriteBatch batch;
    private final float[] quadVertices = new float[QUAD_FLOATS];
    private final Matrix4 planeMatrix = new Matrix4();

    private final LightingMood mood;
    private final SurfaceMaterialTextures floorMaterialTextures;
    private final SurfaceMaterialTextures ceilingMaterialTextures;
    // Per-channel bake scales (>= 1) of each surface's textures; every colour of that surface is divided by them.
    private final float[] floorBakeScales   = new float[3];
    private final float[] ceilingBakeScales = new float[3];

    // Row tables (R1), rebuilt by setRenderResolution() only on a CHANGE - never per frame.
    private RenderResolution renderResolution;
    private int     floorRowCount;
    private int     bandStartRow;
    private float   rowHeightWorldUnits;
    private float[] rowDistanceTable;
    private float[] rowShadeTable;
    private float[] rowTintTable;
    private int[]   rowMipLevelTable;

    // Per-frame lattice of tile CORNERS covering the view wedge to the clamp depth, and per-tile state.
    private final float[] latticeDepth        = new float[LATTICE_POINT_CAPACITY];
    private final float[] latticeShade        = new float[LATTICE_POINT_CAPACITY];
    private final float[] latticeTint         = new float[LATTICE_POINT_CAPACITY * 3];
    private final float[] latticeLightFalloff = new float[LATTICE_POINT_CAPACITY];
    private final boolean[] latticeInsideLeft  = new boolean[LATTICE_POINT_CAPACITY];
    private final boolean[] latticeInsideRight = new boolean[LATTICE_POINT_CAPACITY];
    private final int[]   tileMipLevel  = new int[TILE_CAPACITY];      // -1 = culled this frame
    private final float[] tileBrightness = new float[TILE_CAPACITY];
    private int latticeOriginColumn;
    private int latticeOriginRow;
    private int tileColumnCount;
    private int tileRowCount;
    private float frameMaximumFactor;

    // Per-frame state, written at the top of render().
    private float frameLightAddRed;
    private float frameLightAddGreen;
    private float frameLightAddBlue;
    private float frameLightIntensity;
    private AtmosphereGrade atmosphereGrade;

    private float playerWorldX        = 0f;
    private float playerWorldY        = 0f;
    private float directionX          = 1f;
    private float directionY          = 0f;
    private float fieldOfViewRadians  = PLAYER_FIELD_OF_VIEW_RADIANS;
    private float alertPulse          = 0f;
    // Monotonically increasing facility clock; drives 'f' (flickering) tile brightness.
    private float lightingTimeSeconds = 0f;

    private float framePlaneX;
    private float framePlaneY;
    private float framePlayerTileX;
    private float framePlayerTileY;

    // Band walk state, set by drawBand() before it emits spans (fields, so nothing allocates).
    private int     walkLayer;
    private boolean walkCeiling;
    private Texture walkTexture;
    private float   walkBottomY;
    private float   walkTopY;
    private float   walkOriginX;
    private float   walkOriginY;
    private float   walkDeltaX;
    private float   walkDeltaY;
    private float   walkSegmentLengthTiles;

    public FloorCeilingRenderer(Level level, LightingMood lightingMood) {
        this.level = level;
        this.mood  = lightingMood != null ? lightingMood : LightingMoods.registry().getFallback();
        batch = new SpriteBatch(FLOOR_SPAN_BATCH_SIZE);
        computeBakeScales(mood.floorMaterialId(), floorBakeScales);
        computeBakeScales(mood.ceilingMaterialId(), ceilingBakeScales);
        floorMaterialTextures   = new SurfaceMaterialTextures(mood.floorMaterialId(), floorBakeScales);
        ceilingMaterialTextures = new SurfaceMaterialTextures(mood.ceilingMaterialId(), ceilingBakeScales);
        // Built straight at the default tier: World pushes the player's setting right after, which is a
        // no-op unless they picked the other tier (then only the row tables are rebuilt - no GPU realloc).
        rebuildRowTables(RenderResolution.DEFAULT);
    }

    /*
     * The largest base factor a channel can reach on this floor is MAX_LIGHTING_SHADE x the largest tint over the
     * visible depths (the alert terms only lower green/blue; red is clamped to 1 before the light). The tint is
     * sampled on the same depth range the row tables cover.
     */
    private void computeBakeScales(String materialId, float[] outScales) {
        float[] tint = new float[3];
        float[] maximumTint = {0f, 0f, 0f};
        int samples = FLOOR_BAKE_TINT_SAMPLES;
        for (int sample = 0; sample <= samples; sample++) {
            float depth = FLOOR_MAX_VISIBLE_DISTANCE_CELLS * sample / samples;
            AtmosphereGrade.evaluateInto(mood, depth, ArtDirection.GRADE_STRENGTH_ENVIRONMENT, tint, 0);
            for (int channel = 0; channel < 3; channel++) maximumTint[channel] = Math.max(maximumTint[channel], tint[channel]);
        }
        outScales[0] = 1f;   // red: min(1, ...) before the light, never above 1 in the base factor
        for (int channel = 1; channel < 3; channel++) {
            outScales[channel] = GameMath.materialChannelBakeScale(MAX_LIGHTING_SHADE * maximumTint[channel],
                    SurfaceMaterialTextures.maximumChannel(materialId, channel));
        }
    }

    /**
     * Applies the runtime render-resolution setting. A no-op when the tier is unchanged; otherwise rebuilds the
     * row tables for the tier's row count. No GPU resource depends on the tier, so this never reallocates a
     * texture. Call from the settings path, never from render().
     */
    public void setRenderResolution(RenderResolution renderResolution) {
        if (this.renderResolution == renderResolution) return;
        rebuildRowTables(renderResolution);
    }

    /*
     * R1: row distance (tiles) is a pure function of the row, so shade, fog + tint and the material level are
     * evaluated once per row here - with exactly the mapping and formulas of the CPU backdrop.
     */
    private void rebuildRowTables(RenderResolution resolution) {
        renderResolution = resolution;
        int rowCount = resolution.getFloorCeilingRowCount();
        floorRowCount = rowCount / 2;
        rowHeightWorldUnits = WORLD_HEIGHT / (float) rowCount;

        rowDistanceTable = new float[floorRowCount];
        rowShadeTable    = new float[floorRowCount];
        rowTintTable     = new float[floorRowCount * 3];
        rowMipLevelTable = new int[floorRowCount];

        // Camera plane width at unit distance = 2 * planeScale = 2 * tan(halfFov); the table uses the
        // default player FOV (FOV is constant in play, and the level only picks a blur step).
        float planeWidthAtUnitDistance = 2f * (float) Math.tan(PLAYER_FIELD_OF_VIEW_RADIANS / 2.0);
        int mipReferenceColumns = GameMath.floorMipReferenceColumnCount(rowCount, WORLD_WIDTH, WORLD_HEIGHT);
        bandStartRow = floorRowCount;
        for (int drawY = 0; drawY < floorRowCount; drawY++) {
            float pixelOffset = GameMath.floorPixelOffsetBelowHorizon(drawY * rowHeightWorldUnits, WORLD_HEIGHT);
            float unclampedDistance = GameMath.floorRowDistance(pixelOffset, WORLD_HEIGHT);
            rowDistanceTable[drawY] = Math.min(unclampedDistance, FLOOR_MAX_VISIBLE_DISTANCE_CELLS);
            if (unclampedDistance >= FLOOR_MAX_VISIBLE_DISTANCE_CELLS && bandStartRow == floorRowCount) {
                bandStartRow = drawY;
            }
        }
        float previousDepthFootprintTexels = 0f;
        for (int drawY = 0; drawY < floorRowCount; drawY++) {
            float rowDistance = rowDistanceTable[drawY];
            rowShadeTable[drawY] = GameMath.floorShade(rowDistance, FLOOR_SHADING_FALLOFF);
            AtmosphereGrade.evaluateInto(mood, rowDistance, ArtDirection.GRADE_STRENGTH_ENVIRONMENT,
                                         rowTintTable, drawY * 3);
            // Last row has no neighbour above it: reuse the previous difference.
            float depthFootprintTexels = drawY + 1 < floorRowCount
                    ? GameMath.floorDepthFootprintTexels(rowDistance, rowDistanceTable[drawY + 1], SURFACE_MATERIAL_SIZE)
                    : previousDepthFootprintTexels;
            previousDepthFootprintTexels = depthFootprintTexels;
            float screenFootprintTexels = GameMath.floorScreenFootprintTexels(rowDistance,
                    planeWidthAtUnitDistance, mipReferenceColumns, SURFACE_MATERIAL_SIZE);
            rowMipLevelTable[drawY] = GameMath.surfaceMaterialMipLevel(screenFootprintTexels,
                    depthFootprintTexels, SURFACE_MATERIAL_MIP_BIAS, SURFACE_MATERIAL_MIP_LEVELS);
            // Rows only get farther up the screen, so never fall back to a sharper level: the clamped band
            // past FLOOR_MAX_VISIBLE_DISTANCE_CELLS has zero depth difference and would otherwise un-blur.
            if (drawY > 0 && rowMipLevelTable[drawY] < rowMipLevelTable[drawY - 1]) {
                rowMipLevelTable[drawY] = rowMipLevelTable[drawY - 1];
            }
        }
    }

    public void setPlayerState(float worldX, float worldY,
                               float playerDirectionX, float playerDirectionY,
                               float playerFieldOfViewRadians) {
        this.playerWorldX       = worldX;
        this.playerWorldY       = worldY;
        this.directionX         = playerDirectionX;
        this.directionY         = playerDirectionY;
        this.fieldOfViewRadians = playerFieldOfViewRadians;
    }

    /** Supplies the grade whose muzzle-light snapshot is read each frame (R8). Null disables the term. */
    public void setAtmosphereGrade(AtmosphereGrade grade) {
        this.atmosphereGrade = grade;
    }

    public void setAlertPulse(float pulse) {
        this.alertPulse = pulse;
    }

    public void setLightingTime(float timeSeconds) {
        this.lightingTimeSeconds = timeSeconds;
    }

    /** Bytes uploaded to the GPU by the last render() - the probe's floor upload counter. Always 0 (A3). */
    public long getUploadBytesLastFrame() {
        return 0L;
    }

    @Override
    public void render(OrthographicCamera camera) {
        framePlayerTileX = playerWorldX / CELL_SIZE;
        framePlayerTileY = playerWorldY / CELL_SIZE;
        // Effective FOV per frame (BLINDED narrows it), as the CPU caster did.
        float planeScale = (float) Math.tan(fieldOfViewRadians / 2.0);
        framePlaneX = GameMath.cameraPlaneX(directionY, planeScale);
        framePlaneY = GameMath.cameraPlaneY(directionX, planeScale);

        // Muzzle light snapshot for this frame: intensity x colour. Zero intensity removes the term.
        AtmosphereGrade grade = atmosphereGrade;
        frameLightIntensity = grade != null ? grade.getLightIntensity() : 0f;
        frameLightAddRed    = frameLightIntensity * (grade != null ? grade.getLightRed()   : 0f);
        frameLightAddGreen  = frameLightIntensity * (grade != null ? grade.getLightGreen() : 0f);
        frameLightAddBlue   = frameLightIntensity * (grade != null ? grade.getLightBlue()  : 0f);
        frameMaximumFactor  = 0f;

        buildLattice();

        batch.begin();
        // Opaque materials over a cleared screen: blending buys nothing in the base pass.
        batch.disableBlending();
        drawLayer(camera, BASE_LAYER);
        batch.enableBlending();
        if (frameMaximumFactor > 1f) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            for (int layer = 0; layer < FLOOR_ADDITIVE_MAX_LAYERS && frameMaximumFactor > 1f + layer; layer++) {
                drawLayer(camera, layer);
            }
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        batch.end();
    }

    /** One layer (base or additive) of both surfaces: the far band first, then the near tiles over it. */
    private void drawLayer(OrthographicCamera camera, int layer) {
        batch.setProjectionMatrix(camera.combined);
        drawBand(false, layer);
        drawBand(true, layer);
        for (int surface = 0; surface < 2; surface++) {
            boolean ceiling = surface == 1;
            GameMath.floorPlaneClipMatrix(planeMatrix.val, framePlayerTileX, framePlayerTileY,
                    directionX, directionY, framePlaneX, framePlaneY, ceiling,
                    WORLD_WIDTH, WORLD_HEIGHT, FLOOR_CAMERA_Z,
                    camera.position.x, camera.position.y,
                    camera.viewportWidth * camera.zoom, camera.viewportHeight * camera.zoom,
                    FLOOR_PLANE_NEAR_TILES, FLOOR_MAX_VISIBLE_DISTANCE_CELLS);
            batch.setProjectionMatrix(planeMatrix);
            drawTiles(ceiling, layer);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Near region: perspective tiles
    // ---------------------------------------------------------------------------------------------------------

    /*
     * The lattice covers the bounding box of the view wedge (player, and the two edge rays at the clamp depth).
     * Per corner: depth, colour terms at the clamped depth, and which side of each edge ray it lies on (for the
     * cull). Per tile: brightness and material level, or -1 when the tile is entirely outside the wedge.
     */
    private void buildLattice() {
        float leftRayX  = directionX + framePlaneX;
        float leftRayY  = directionY + framePlaneY;
        float rightRayX = directionX - framePlaneX;
        float rightRayY = directionY - framePlaneY;
        float clampDepthTiles = FLOOR_MAX_VISIBLE_DISTANCE_CELLS;
        float minimumX = Math.min(framePlayerTileX, Math.min(framePlayerTileX + clampDepthTiles * leftRayX, framePlayerTileX + clampDepthTiles * rightRayX));
        float maximumX = Math.max(framePlayerTileX, Math.max(framePlayerTileX + clampDepthTiles * leftRayX, framePlayerTileX + clampDepthTiles * rightRayX));
        float minimumY = Math.min(framePlayerTileY, Math.min(framePlayerTileY + clampDepthTiles * leftRayY, framePlayerTileY + clampDepthTiles * rightRayY));
        float maximumY = Math.max(framePlayerTileY, Math.max(framePlayerTileY + clampDepthTiles * leftRayY, framePlayerTileY + clampDepthTiles * rightRayY));
        latticeOriginColumn = MathUtils.floor(minimumX) - 1;
        latticeOriginRow    = MathUtils.floor(minimumY) - 1;
        tileColumnCount = Math.min(FLOOR_TILE_LATTICE_MAX_EXTENT, MathUtils.floor(maximumX) + 2 - latticeOriginColumn);
        tileRowCount    = Math.min(FLOOR_TILE_LATTICE_MAX_EXTENT, MathUtils.floor(maximumY) + 2 - latticeOriginRow);

        // Which side of each edge ray the view direction itself lies on = "inside".
        float insideLeftSign  = Math.signum(leftRayX * directionY - leftRayY * directionX);
        float insideRightSign = Math.signum(rightRayX * directionY - rightRayY * directionX);

        int latticeColumns = tileColumnCount + 1;
        for (int latticeRow = 0; latticeRow <= tileRowCount; latticeRow++) {
            for (int latticeColumn = 0; latticeColumn <= tileColumnCount; latticeColumn++) {
                int index = latticeRow * latticeColumns + latticeColumn;
                float offsetX = latticeOriginColumn + latticeColumn - framePlayerTileX;
                float offsetY = latticeOriginRow + latticeRow - framePlayerTileY;
                float depth = directionX * offsetX + directionY * offsetY;
                latticeDepth[index] = depth;
                latticeInsideLeft[index]  = (leftRayX * offsetY - leftRayY * offsetX) * insideLeftSign >= 0f;
                latticeInsideRight[index] = (rightRayX * offsetY - rightRayY * offsetX) * insideRightSign >= 0f;
                // The TRUE corner depth (only clamped at 0 and the clamp depth): a corner in front of the nearest
                // visible row must carry its own value, or interpolating toward it undershoots the near rows -
                // exactly where the muzzle splash is brightest.
                float colourDepth = MathUtils.clamp(depth, 0f, clampDepthTiles);
                latticeShade[index] = GameMath.floorShade(colourDepth, FLOOR_SHADING_FALLOFF);
                AtmosphereGrade.evaluateInto(mood, colourDepth, ArtDirection.GRADE_STRENGTH_ENVIRONMENT,
                                             latticeTint, index * 3);
                latticeLightFalloff[index] = GameMath.muzzleLightFalloff(colourDepth,
                        EffectConstants.MUZZLE_LIGHT_RADIUS_TILES);
            }
        }

        for (int tileRow = 0; tileRow < tileRowCount; tileRow++) {
            for (int tileColumn = 0; tileColumn < tileColumnCount; tileColumn++) {
                int tileIndex = tileRow * tileColumnCount + tileColumn;
                int corner = tileRow * latticeColumns + tileColumn;
                int cornerRight = corner + 1;
                int cornerUp = corner + latticeColumns;
                int cornerUpRight = cornerUp + 1;
                float nearestDepth  = Math.min(Math.min(latticeDepth[corner], latticeDepth[cornerRight]),
                                               Math.min(latticeDepth[cornerUp], latticeDepth[cornerUpRight]));
                float farthestDepth = Math.max(Math.max(latticeDepth[corner], latticeDepth[cornerRight]),
                                               Math.max(latticeDepth[cornerUp], latticeDepth[cornerUpRight]));
                boolean outsideLeft  = !latticeInsideLeft[corner] && !latticeInsideLeft[cornerRight]
                        && !latticeInsideLeft[cornerUp] && !latticeInsideLeft[cornerUpRight];
                boolean outsideRight = !latticeInsideRight[corner] && !latticeInsideRight[cornerRight]
                        && !latticeInsideRight[cornerUp] && !latticeInsideRight[cornerUpRight];
                if (farthestDepth < NEAREST_VISIBLE_DEPTH || nearestDepth > clampDepthTiles || outsideLeft || outsideRight) {
                    tileMipLevel[tileIndex] = -1;
                    continue;
                }
                float centreDepth = MathUtils.clamp((nearestDepth + farthestDepth) / 2f, NEAREST_VISIBLE_DEPTH, clampDepthTiles);
                int row = MathUtils.clamp(GameMath.floorRowAtDepth(centreDepth, WORLD_HEIGHT, FLOOR_CAMERA_Z,
                        rowHeightWorldUnits), 0, floorRowCount - 1);
                tileMipLevel[tileIndex]   = rowMipLevelTable[row];
                tileBrightness[tileIndex] = level.getTileBrightness(latticeOriginColumn + tileColumn,
                        latticeOriginRow + tileRow, lightingTimeSeconds);
            }
        }
    }

    /** Every visible tile of one surface, grouped by material level so a surface binds each level once. */
    private void drawTiles(boolean ceiling, int layer) {
        SurfaceMaterialTextures textures = ceiling ? ceilingMaterialTextures : floorMaterialTextures;
        float[] bakeScales = ceiling ? ceilingBakeScales : floorBakeScales;
        float redBoost = ceiling ? ALERT_CEILING_TINT_STRENGTH : ALERT_WALL_RED_BOOST;
        int latticeColumns = tileColumnCount + 1;
        float[] vertices = quadVertices;
        for (int mipLevel = 0; mipLevel < SURFACE_MATERIAL_MIP_LEVELS; mipLevel++) {
            Texture texture = textures.getLevel(mipLevel);
            for (int tileRow = 0; tileRow < tileRowCount; tileRow++) {
                for (int tileColumn = 0; tileColumn < tileColumnCount; tileColumn++) {
                    int tileIndex = tileRow * tileColumnCount + tileColumn;
                    if (tileMipLevel[tileIndex] != mipLevel) continue;
                    float brightness = tileBrightness[tileIndex];
                    int corner = tileRow * latticeColumns + tileColumn;
                    // SpriteBatch quad order: bottom-left, top-left, top-right, bottom-right; UV = the tile's own
                    // 0..1 (texel = frac(tile coordinate) x size, the CPU caster's index - R4 orientation).
                    float colourBottomLeft  = cornerColour(corner, brightness, redBoost, bakeScales, layer);
                    float colourTopLeft     = cornerColour(corner + latticeColumns, brightness, redBoost, bakeScales, layer);
                    float colourTopRight    = cornerColour(corner + latticeColumns + 1, brightness, redBoost, bakeScales, layer);
                    float colourBottomRight = cornerColour(corner + 1, brightness, redBoost, bakeScales, layer);
                    if (layer != BASE_LAYER && colourBottomLeft == NO_EXCESS_COLOUR
                            && colourTopLeft == NO_EXCESS_COLOUR && colourTopRight == NO_EXCESS_COLOUR
                            && colourBottomRight == NO_EXCESS_COLOUR) {
                        continue;
                    }
                    float left   = latticeOriginColumn + tileColumn;
                    float bottom = latticeOriginRow + tileRow;
                    vertices[0]  = left;      vertices[1]  = bottom;      vertices[2]  = colourBottomLeft;  vertices[3]  = 0f; vertices[4]  = 0f;
                    vertices[5]  = left;      vertices[6]  = bottom + 1f; vertices[7]  = colourTopLeft;     vertices[8]  = 0f; vertices[9]  = 1f;
                    vertices[10] = left + 1f; vertices[11] = bottom + 1f; vertices[12] = colourTopRight;    vertices[13] = 1f; vertices[14] = 1f;
                    vertices[15] = left + 1f; vertices[16] = bottom;      vertices[17] = colourBottomRight; vertices[18] = 1f; vertices[19] = 0f;
                    batch.draw(texture, vertices, 0, QUAD_FLOATS);
                }
            }
        }
    }

    /*
     * R5/R7 at one tile corner: the CPU caster's texel factor (shade x brightness, tint, alert, muzzle light),
     * divided by the surface's bake scale. Base layer: clamped to 1. Additive layer: that layer's excess, or
     * NO_EXCESS_COLOUR (opaque black, adds nothing) when there is none.
     */
    private float cornerColour(int latticeIndex, float brightness, float redBoost, float[] bakeScales, int layer) {
        float adjustedShade = GameMath.floorSpanAdjustedShade(latticeShade[latticeIndex], brightness, MAX_LIGHTING_SHADE);
        float falloff = frameLightIntensity != 0f ? latticeLightFalloff[latticeIndex] : 0f;
        int tintIndex = latticeIndex * 3;
        return factorColour(adjustedShade, latticeTint[tintIndex], latticeTint[tintIndex + 1], latticeTint[tintIndex + 2],
                falloff, redBoost, bakeScales, layer);
    }

    private float factorColour(float adjustedShade, float tintRed, float tintGreen, float tintBlue, float lightFalloff,
                               float redBoost, float[] bakeScales, int layer) {
        float factorRed   = GameMath.floorSpanRedFactor(adjustedShade * tintRed, redBoost, alertPulse,
                                                        lightFalloff * frameLightAddRed) / bakeScales[0];
        float factorGreen = GameMath.floorSpanGreenBlueFactor(adjustedShade * tintGreen, ALERT_WALL_GB_DAMPEN,
                                                              alertPulse, lightFalloff * frameLightAddGreen) / bakeScales[1];
        float factorBlue  = GameMath.floorSpanGreenBlueFactor(adjustedShade * tintBlue, ALERT_WALL_GB_DAMPEN,
                                                              alertPulse, lightFalloff * frameLightAddBlue) / bakeScales[2];
        if (layer == BASE_LAYER) {
            frameMaximumFactor = Math.max(frameMaximumFactor, Math.max(factorRed, Math.max(factorGreen, factorBlue)));
            return Color.toFloatBits(GameMath.colourFactorBase(factorRed), GameMath.colourFactorBase(factorGreen),
                                     GameMath.colourFactorBase(factorBlue), 1f);
        }
        float excessRed   = GameMath.colourFactorExcessLayer(factorRed, layer);
        float excessGreen = GameMath.colourFactorExcessLayer(factorGreen, layer);
        float excessBlue  = GameMath.colourFactorExcessLayer(factorBlue, layer);
        if (excessRed + excessGreen + excessBlue <= 0f) return NO_EXCESS_COLOUR;
        return Color.toFloatBits(excessRed, excessGreen, excessBlue, 1f);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Far band: rows at or past the distance clamp, one span strip through the screen camera
    // ---------------------------------------------------------------------------------------------------------

    /*
     * R2 for the band: the band's world segment (the clamp-depth row) from the left ray hit (screen x = 0) to the
     * right ray hit (x = WORLD_WIDTH), walked across the tile grid; a change of tile brightness starts a new span
     * at the exact crossing. It starts FLOOR_BAND_OVERLAP_ROWS below the clamp row and is drawn BEFORE the tiles,
     * which are clipped at the clamp depth and cover the overlap, so the seam can never show the clear colour.
     */
    private void drawBand(boolean ceiling, int layer) {
        if (bandStartRow >= floorRowCount) return;
        SurfaceMaterialTextures textures = ceiling ? ceilingMaterialTextures : floorMaterialTextures;
        int bandRow = bandStartRow;
        float rowDistance = rowDistanceTable[bandRow];
        walkLayer   = layer;
        walkCeiling = ceiling;
        walkTexture = textures.getLevel(rowMipLevelTable[bandRow]);
        float bandBottom = Math.max(0, bandStartRow - FLOOR_BAND_OVERLAP_ROWS) * rowHeightWorldUnits;
        float bandTop    = floorRowCount * rowHeightWorldUnits;
        walkBottomY = ceiling ? WORLD_HEIGHT - bandTop    : bandBottom;
        walkTopY    = ceiling ? WORLD_HEIGHT - bandBottom : bandTop;

        float leftRayX  = directionX + framePlaneX;
        float leftRayY  = directionY + framePlaneY;
        float rightRayX = directionX - framePlaneX;
        float rightRayY = directionY - framePlaneY;
        walkOriginX = GameMath.floorOriginTileComponent(framePlayerTileX, rowDistance, leftRayX);
        walkOriginY = GameMath.floorOriginTileComponent(framePlayerTileY, rowDistance, leftRayY);
        // The whole row's step = per-column step x column count (floorStepTileComponent with one "column").
        walkDeltaX  = GameMath.floorStepTileComponent(rowDistance, rightRayX, leftRayX, 1);
        walkDeltaY  = GameMath.floorStepTileComponent(rowDistance, rightRayY, leftRayY, 1);
        walkSegmentLengthTiles = (float) Math.sqrt(walkDeltaX * walkDeltaX + walkDeltaY * walkDeltaY);

        int cellColumn = MathUtils.floor(walkOriginX);
        int cellRow    = MathUtils.floor(walkOriginY);
        int stepColumn = walkDeltaX > 0f ? 1 : -1;
        int stepRow    = walkDeltaY > 0f ? 1 : -1;
        float crossingColumn = GameMath.floorSpanAxisCrossingParameter(walkOriginX, walkDeltaX, cellColumn);
        float crossingRow    = GameMath.floorSpanAxisCrossingParameter(walkOriginY, walkDeltaY, cellRow);

        float spanStart      = 0f;
        float spanBrightness = level.getTileBrightness(cellColumn, cellRow, lightingTimeSeconds);
        // Bounded by the crossing count (|deltaX| + |deltaY| + 2) and, for safety, by a hard cap.
        for (int crossing = 0; crossing < FLOOR_SPAN_MAX_CROSSINGS_PER_ROW; crossing++) {
            float next = Math.min(crossingColumn, crossingRow);
            if (next >= 1f) break;
            boolean stepsColumn = crossingColumn <= crossingRow;
            boolean stepsRow    = crossingRow <= crossingColumn;
            if (stepsColumn) {
                cellColumn += stepColumn;
                crossingColumn = GameMath.floorSpanAxisCrossingParameter(walkOriginX, walkDeltaX, cellColumn);
            }
            if (stepsRow) {
                cellRow += stepRow;
                crossingRow = GameMath.floorSpanAxisCrossingParameter(walkOriginY, walkDeltaY, cellRow);
            }
            float brightness = level.getTileBrightness(cellColumn, cellRow, lightingTimeSeconds);
            if (brightness != spanBrightness) {
                emitBandSpan(spanStart, next, spanBrightness, bandRow);
                spanStart      = next;
                spanBrightness = brightness;
            }
        }
        emitBandSpan(spanStart, 1f, spanBrightness, bandRow);
    }

    /** One run of equal brightness over band parameters [spanStart, spanEnd], split at FLOOR_SPAN_MAX_TILES (R3). */
    private void emitBandSpan(float spanStart, float spanEnd, float tileBrightness, int bandRow) {
        if (!(spanEnd > spanStart)) return;
        float adjustedShade = GameMath.floorSpanAdjustedShade(rowShadeTable[bandRow], tileBrightness, MAX_LIGHTING_SHADE);
        int tintIndex = bandRow * 3;
        // The muzzle light reaches MUZZLE_LIGHT_RADIUS_TILES, far short of the clamp depth: no light term here.
        float colourBits = factorColour(adjustedShade, rowTintTable[tintIndex], rowTintTable[tintIndex + 1],
                rowTintTable[tintIndex + 2], 0f, walkCeiling ? ALERT_CEILING_TINT_STRENGTH : ALERT_WALL_RED_BOOST,
                walkCeiling ? ceilingBakeScales : floorBakeScales, walkLayer);
        if (walkLayer != BASE_LAYER && colourBits == NO_EXCESS_COLOUR) return;

        int pieceCount = Math.max(1, MathUtils.ceil((spanEnd - spanStart) * walkSegmentLengthTiles / FLOOR_SPAN_MAX_TILES));
        float spanLength = spanEnd - spanStart;
        float pieceStart = spanStart;
        for (int piece = 1; piece <= pieceCount; piece++) {
            // The last piece ends exactly on spanEnd, so neighbouring spans share their edge bit-for-bit.
            float pieceEnd = piece == pieceCount ? spanEnd : spanStart + spanLength * piece / pieceCount;
            emitBandQuad(pieceStart, pieceEnd, colourBits);
            pieceStart = pieceEnd;
        }
    }

    private void emitBandQuad(float pieceStart, float pieceEnd, float colourBits) {
        float leftX  = pieceStart * WORLD_WIDTH;
        float rightX = pieceEnd   * WORLD_WIDTH;
        float leftU  = walkOriginX + pieceStart * walkDeltaX;
        float rightU = walkOriginX + pieceEnd   * walkDeltaX;
        float leftV  = walkOriginY + pieceStart * walkDeltaY;
        float rightV = walkOriginY + pieceEnd   * walkDeltaY;
        // R3: integer shift per axis keeps every coordinate small for mediump varyings.
        float shiftU = GameMath.floorSpanUvRebaseShift(leftU);
        float shiftV = GameMath.floorSpanUvRebaseShift(leftV);
        leftU  -= shiftU;
        rightU -= shiftU;
        leftV  -= shiftV;
        rightV -= shiftV;

        float[] vertices = quadVertices;
        // SpriteBatch quad order: bottom-left, top-left, top-right, bottom-right. The band is at one depth, so
        // the bottom and top edges carry the same UVs.
        vertices[0]  = leftX;  vertices[1]  = walkBottomY; vertices[2]  = colourBits; vertices[3]  = leftU;  vertices[4]  = leftV;
        vertices[5]  = leftX;  vertices[6]  = walkTopY;    vertices[7]  = colourBits; vertices[8]  = leftU;  vertices[9]  = leftV;
        vertices[10] = rightX; vertices[11] = walkTopY;    vertices[12] = colourBits; vertices[13] = rightU; vertices[14] = rightV;
        vertices[15] = rightX; vertices[16] = walkBottomY; vertices[17] = colourBits; vertices[18] = rightU; vertices[19] = rightV;
        batch.draw(walkTexture, vertices, 0, QUAD_FLOATS);
    }

    @Override
    public void dispose() {
        batch.dispose();
        floorMaterialTextures.dispose();
        ceilingMaterialTextures.dispose();
    }
}

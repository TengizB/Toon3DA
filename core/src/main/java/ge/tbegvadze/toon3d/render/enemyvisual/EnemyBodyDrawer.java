package ge.tbegvadze.toon3d.render.enemyvisual;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import ge.tbegvadze.toon3d.render.SpriteOccluderBuffer;
import ge.tbegvadze.toon3d.render.WallRenderer;
import ge.tbegvadze.toon3d.util.GameMath;

import static ge.tbegvadze.toon3d.util.RenderConstants.WALL_PROJECTION_SCREEN_HEIGHT;

/**
 * Draws ONE animated frame as a billboard (generated-enemy-art order 2), with the same per-screen-column
 * machinery the legacy body uses: each column is z-tested against the wall buffer and drawn as a one-texel
 * strip of the frame. {@link #prepare} places the frame; {@link #drawColumns} then draws it from a texture
 * — the body atlas for the body and the muzzle light, the glow atlas for the glow pass, which share one
 * packing, so the three passes register pixel for pixel.
 *
 * <p>PLACEMENT. The whole 160x240 CANVAS is the billboard: {@code spriteScreenHeight} tall (heightMultiplier
 * walls), bottom on the floor line, centred on the enemy. The frame is the trimmed rect inside it, put back
 * at its manifest {@code offset}, so the ground line never moves between frames or clips (guide 6, 7.7).
 * The canvas is y-DOWN, the world y-UP: this is the one place the two are reconciled.
 *
 * <p>One instance is reused for every enemy of a frame; its fields are the scratch of the last
 * {@link #prepare} call. Render thread only, no allocation.
 */
public final class EnemyBodyDrawer {

    private EnemyClipManifest.Frame frame;
    private float leftColumnPosition;   // frame edges in COLUMN space (fractional)
    private float rightColumnPosition;
    private float clampedBottom;
    private float clampedTop;
    private int   sourceY;
    private int   sourceHeight;
    private float columnWidth;
    private int   firstColumn;
    private int   lastColumn;
    private float canvasTop;                  // world y of the canvas top (order 3: the shimmer band)
    private float worldUnitsPerPixel;

    /**
     * Places {@code frameToDraw} for a billboard whose canvas is {@code spriteScreenHeight} world units tall,
     * centred on {@code worldCenterX} with its top at {@code canvasTopY}. Returns false when nothing of it is
     * on screen. {@link #getFirstColumn()} / {@link #getLastColumn()} then name the columns it covers.
     */
    public boolean prepare(EnemyClipManifest.Frame frameToDraw, int canvasWidth, int canvasHeight,
                           float worldCenterX, float canvasTopY, float spriteScreenHeight,
                           float projectionColumnWidth, int projectionColumnCount) {
        frame       = frameToDraw;
        columnWidth = projectionColumnWidth;
        float worldUnitsPerCanvasPixel = spriteScreenHeight / canvasHeight;
        canvasTop          = canvasTopY;
        worldUnitsPerPixel = worldUnitsPerCanvasPixel;
        float canvasLeftX = worldCenterX - canvasWidth * worldUnitsPerCanvasPixel * 0.5f;
        float frameLeftX  = canvasLeftX + frameToDraw.offsetLeft * worldUnitsPerCanvasPixel;
        float frameRightX = frameLeftX  + frameToDraw.width      * worldUnitsPerCanvasPixel;
        float frameTopY    = canvasTopY - frameToDraw.offsetTop * worldUnitsPerCanvasPixel;
        float frameHeightY = frameToDraw.height * worldUnitsPerCanvasPixel;
        float frameBottomY = frameTopY - frameHeightY;

        leftColumnPosition  = frameLeftX  / projectionColumnWidth;
        rightColumnPosition = frameRightX / projectionColumnWidth;
        firstColumn = Math.max(0, (int) Math.floor(leftColumnPosition));
        lastColumn  = Math.min(projectionColumnCount - 1, (int) Math.ceil(rightColumnPosition) - 1);
        if (lastColumn < firstColumn || rightColumnPosition <= leftColumnPosition) return false;

        clampedBottom = Math.max(0f, frameBottomY);
        clampedTop    = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, frameTopY);
        if (clampedTop <= clampedBottom) return false;

        // Rows clipped off the top of the screen, then the visible row count — the wall-stripe formulas.
        int localSourceY = GameMath.wallTextureClipSrcY(frameTopY, WALL_PROJECTION_SCREEN_HEIGHT,
                frameHeightY, frameToDraw.height);
        int visibleRows  = GameMath.wallTextureClipSrcHeight(clampedTop, clampedBottom,
                frameHeightY, frameToDraw.height);
        sourceY      = frameToDraw.atlasY + localSourceY;
        sourceHeight = Math.max(1, Math.min(visibleRows, frameToDraw.height - localSourceY));
        return true;
    }

    /**
     * Draws the prepared frame from {@code texture} in the batch's current colour and blend mode. Every
     * column behind a wall nearer than {@code depth} is skipped; when {@code occluderBuffer} is non-null,
     * so is every column behind a nearer recorded prop (the additive passes — the body itself is covered
     * by the occluder's redraw instead, exactly like the legacy body).
     */
    public void drawColumns(SpriteBatch batch, Texture texture, float depth, WallRenderer wallRenderer,
                            SpriteOccluderBuffer occluderBuffer) {
        float spanColumns = rightColumnPosition - leftColumnPosition;
        for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
            if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
            if (occluderBuffer != null && depth > occluderBuffer.getNearestDepth(screenColumn)) continue;
            // Sample the texel under the column's centre.
            float across     = (screenColumn + 0.5f - leftColumnPosition) / spanColumns;
            int   localSourceX = MathUtils.clamp((int) (across * frame.width), 0, frame.width - 1);
            batch.draw(texture,
                       screenColumn * columnWidth, clampedBottom,
                       columnWidth, clampedTop - clampedBottom,
                       frame.atlasX + localSourceX, sourceY, 1, sourceHeight,
                       false, false);
        }
    }

    /**
     * Order 3 (the BLOCKED shimmer): draws only the prepared frame's rows that fall inside the canvas band
     * [{@code bandTopCanvasY}, {@code bandBottomCanvasY}) (canvas pixels, y down), same columns and tests as
     * {@link #drawColumns}. Used as a light pass (additive, the body atlas tinted), like the muzzle light.
     */
    public void drawBand(SpriteBatch batch, Texture texture, float depth, WallRenderer wallRenderer,
                         SpriteOccluderBuffer occluderBuffer, float bandTopCanvasY, float bandBottomCanvasY) {
        float top    = Math.max(bandTopCanvasY, frame.offsetTop);
        float bottom = Math.min(bandBottomCanvasY, frame.offsetTop + frame.height);
        if (bottom <= top) return;
        int rowStart = (int) (top - frame.offsetTop);
        int rowEnd   = Math.min(frame.height, (int) Math.ceil(bottom - frame.offsetTop));
        float bandTopY    = canvasTop - (frame.offsetTop + rowStart) * worldUnitsPerPixel;
        float bandBottomY = canvasTop - (frame.offsetTop + rowEnd) * worldUnitsPerPixel;
        float clippedTop    = Math.min(bandTopY, (float) WALL_PROJECTION_SCREEN_HEIGHT);
        float clippedBottom = Math.max(bandBottomY, 0f);
        if (clippedTop <= clippedBottom) return;
        rowStart += (int) ((bandTopY - clippedTop) / worldUnitsPerPixel);
        rowEnd   -= (int) ((clippedBottom - bandBottomY) / worldUnitsPerPixel);
        if (rowEnd <= rowStart) return;
        float spanColumns = rightColumnPosition - leftColumnPosition;
        for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
            if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
            if (occluderBuffer != null && depth > occluderBuffer.getNearestDepth(screenColumn)) continue;
            float across       = (screenColumn + 0.5f - leftColumnPosition) / spanColumns;
            int   localSourceX = MathUtils.clamp((int) (across * frame.width), 0, frame.width - 1);
            batch.draw(texture,
                       screenColumn * columnWidth, clippedBottom,
                       columnWidth, clippedTop - clippedBottom,
                       frame.atlasX + localSourceX, frame.atlasY + rowStart, 1, rowEnd - rowStart,
                       false, false);
        }
    }

    public int getFirstColumn() {
        return firstColumn;
    }

    public int getLastColumn() {
        return lastColumn;
    }
}

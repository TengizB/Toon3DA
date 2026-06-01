package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.door.DoorState;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameMath;

import static ge.tbegvadze.toon3d.util.Constants.*;

public class LevelRenderer implements Renderable, Disposable {

    private static final Color GRID_COLOR      = new Color(0.2f, 0.2f, 0.2f, 1f);
    private static final Color WALL_COLOR      = Color.WHITE;
    private static final Color BORDER_COLOR    = new Color(0.6f, 0.6f, 0.6f, 1f);
    private static final Color RAY_HIT_COLOR   = new Color(1f, 0.8f, 0f, 0.8f);
    private static final Color RAY_MISS_COLOR  = new Color(0f, 0.7f, 0.7f, 0.3f);

    // Half the visible tile span — rays beyond this radius are clipped to the mini-map boundary.
    private static final float MINI_MAP_HALF_TILE_SPAN = MINI_MAP_TILE_COUNT / 2f;

    // One extra ring of tiles beyond the visible radius to fill partial cells at edges
    private static final int RENDER_TILE_RADIUS = MINI_MAP_TILE_RADIUS + 1;

    private final Level level;
    private final DoorManager doorManager;
    private final ShapeRenderer shapes;

    private float playerWorldX = 0f;
    private float playerWorldY = 0f;
    private RayCastResult[] rayResults = null;

    public LevelRenderer(Level level, DoorManager doorManager) {
        this.level       = level;
        this.doorManager = doorManager;
        this.shapes      = new ShapeRenderer();
    }

    public void setPlayerWorldPosition(float worldX, float worldY) {
        this.playerWorldX = worldX;
        this.playerWorldY = worldY;
    }

    public void setRayResults(RayCastResult[] rayResults) {
        this.rayResults = rayResults;
    }

    @Override
    public void render(OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);

        // Fractional tile position of the player
        int   baseTileColumn = MathUtils.floor(playerWorldX / CELL_SIZE);
        int   baseTileRow    = MathUtils.floor(playerWorldY / CELL_SIZE);
        // Sub-tile pixel offset: how far into the base tile the player currently sits
        float subTileOffsetX = (playerWorldX / CELL_SIZE - baseTileColumn) * MINI_MAP_CELL_SIZE;
        float subTileOffsetY = (playerWorldY / CELL_SIZE - baseTileRow)    * MINI_MAP_CELL_SIZE;
        float miniMapCenterX = MINI_MAP_ORIGIN_X + MINI_MAP_WORLD_SIZE / 2f;
        float miniMapCenterY = MINI_MAP_ORIGIN_Y + MINI_MAP_WORLD_SIZE / 2f;
        float miniMapLeft    = MINI_MAP_ORIGIN_X;
        float miniMapBottom  = MINI_MAP_ORIGIN_Y;
        float miniMapRight   = MINI_MAP_ORIGIN_X + MINI_MAP_WORLD_SIZE;
        float miniMapTop     = MINI_MAP_ORIGIN_Y + MINI_MAP_WORLD_SIZE;

        // Filled pass — walls and doors clipped to mini-map bounds
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int deltaRow = -RENDER_TILE_RADIUS; deltaRow <= RENDER_TILE_RADIUS; deltaRow++) {
            for (int deltaColumn = -RENDER_TILE_RADIUS; deltaColumn <= RENDER_TILE_RADIUS; deltaColumn++) {
                int levelColumn = baseTileColumn + deltaColumn;
                int levelRow    = baseTileRow    + deltaRow;
                char cell = level.getCell(levelColumn, levelRow);

                if (Level.isWall(cell)) {
                    shapes.setColor(WALL_COLOR);
                } else if (Level.isDoor(cell)) {
                    DoorState doorState = doorManager.getStateAt(levelColumn, levelRow);
                    if (doorState == DoorState.OPEN) continue; // Open doorway shows as floor
                    float openFraction = doorManager.getOpenFractionAt(levelColumn, levelRow);
                    // Lerp between closed steel and open cyan colour based on open fraction.
                    shapes.setColor(
                            GameMath.lerp(DOOR_MINIMAP_CLOSED_R, DOOR_MINIMAP_OPEN_R, openFraction),
                            GameMath.lerp(DOOR_MINIMAP_CLOSED_G, DOOR_MINIMAP_OPEN_G, openFraction),
                            GameMath.lerp(DOOR_MINIMAP_CLOSED_B, DOOR_MINIMAP_OPEN_B, openFraction),
                            1f);
                } else {
                    continue;
                }

                float tileLeft   = miniMapCenterX - subTileOffsetX + deltaColumn * MINI_MAP_CELL_SIZE;
                float tileBottom = miniMapCenterY - subTileOffsetY + deltaRow    * MINI_MAP_CELL_SIZE;
                float clippedLeft   = Math.max(miniMapLeft,   tileLeft);
                float clippedBottom = Math.max(miniMapBottom, tileBottom);
                float clippedRight  = Math.min(miniMapRight,  tileLeft   + MINI_MAP_CELL_SIZE);
                float clippedTop    = Math.min(miniMapTop,    tileBottom + MINI_MAP_CELL_SIZE);
                if (clippedRight > clippedLeft && clippedTop > clippedBottom) {
                    shapes.rect(clippedLeft, clippedBottom,
                                clippedRight - clippedLeft, clippedTop - clippedBottom);
                }
            }
        }
        shapes.end();

        // Line pass — scrolling grid clipped to mini-map bounds, then border
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(GRID_COLOR);
        for (int deltaColumn = -RENDER_TILE_RADIUS; deltaColumn <= RENDER_TILE_RADIUS + 1; deltaColumn++) {
            float lineX = miniMapCenterX - subTileOffsetX + deltaColumn * MINI_MAP_CELL_SIZE;
            if (lineX >= miniMapLeft && lineX <= miniMapRight) {
                shapes.line(lineX, miniMapBottom, lineX, miniMapTop);
            }
        }
        for (int deltaRow = -RENDER_TILE_RADIUS; deltaRow <= RENDER_TILE_RADIUS + 1; deltaRow++) {
            float lineY = miniMapCenterY - subTileOffsetY + deltaRow * MINI_MAP_CELL_SIZE;
            if (lineY >= miniMapBottom && lineY <= miniMapTop) {
                shapes.line(miniMapLeft, lineY, miniMapRight, lineY);
            }
        }
        shapes.setColor(BORDER_COLOR);
        shapes.rect(miniMapLeft, miniMapBottom, MINI_MAP_WORLD_SIZE, MINI_MAP_WORLD_SIZE);
        shapes.end();

        // Ray pass — draw DDA rays on the mini-map, clipped to the visible tile area
        if (rayResults != null) {
            float playerTileX = playerWorldX / CELL_SIZE;
            float playerTileY = playerWorldY / CELL_SIZE;

            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(RAY_HIT_COLOR);
            for (RayCastResult ray : rayResults) {
                if (!ray.hitWall) continue;
                float clampedEndX = miniMapCenterX + clampRayDelta(ray.endTileX - playerTileX, ray.endTileY - playerTileY, true)  * MINI_MAP_CELL_SIZE;
                float clampedEndY = miniMapCenterY + clampRayDelta(ray.endTileX - playerTileX, ray.endTileY - playerTileY, false) * MINI_MAP_CELL_SIZE;
                shapes.line(miniMapCenterX, miniMapCenterY, clampedEndX, clampedEndY);
            }
            shapes.end();

            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(RAY_MISS_COLOR);
            for (RayCastResult ray : rayResults) {
                if (ray.hitWall) continue;
                float clampedEndX = miniMapCenterX + clampRayDelta(ray.endTileX - playerTileX, ray.endTileY - playerTileY, true)  * MINI_MAP_CELL_SIZE;
                float clampedEndY = miniMapCenterY + clampRayDelta(ray.endTileX - playerTileX, ray.endTileY - playerTileY, false) * MINI_MAP_CELL_SIZE;
                shapes.line(miniMapCenterX, miniMapCenterY, clampedEndX, clampedEndY);
            }
            shapes.end();
        }
    }

    /**
     * Scales down the ray delta so neither axis exceeds the mini-map half-span, then returns the
     * requested axis component. Both components must be passed together so the scale applied to X
     * is the same as the one applied to Y, preserving the ray direction.
     *
     * @param returnX true to return the X component, false for Y
     */
    private static float clampRayDelta(float deltaTileX, float deltaTileY, boolean returnX) {
        float absoluteDeltaX = Math.abs(deltaTileX);
        float absoluteDeltaY = Math.abs(deltaTileY);
        if (absoluteDeltaX > MINI_MAP_HALF_TILE_SPAN || absoluteDeltaY > MINI_MAP_HALF_TILE_SPAN) {
            float scaleFactor = Math.min(
                absoluteDeltaX > 0f ? MINI_MAP_HALF_TILE_SPAN / absoluteDeltaX : Float.MAX_VALUE,
                absoluteDeltaY > 0f ? MINI_MAP_HALF_TILE_SPAN / absoluteDeltaY : Float.MAX_VALUE
            );
            deltaTileX *= scaleFactor;
            deltaTileY *= scaleFactor;
        }
        return returnX ? deltaTileX : deltaTileY;
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}

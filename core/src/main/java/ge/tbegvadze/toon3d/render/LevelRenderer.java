package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.door.DoorState;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.level.FogOfWarMap;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.Collections;
import java.util.List;

import static ge.tbegvadze.toon3d.util.Constants.*;
import static ge.tbegvadze.toon3d.util.RenderConstants.*;

public class LevelRenderer implements Renderable, Disposable {

    private static final Color GRID_COLOR        = new Color(0.2f, 0.2f, 0.2f, 1f);
    private static final Color WALL_COLOR        = Color.WHITE;
    private static final Color BORDER_COLOR      = new Color(0.6f, 0.6f, 0.6f, 1f);
    private static final Color WEAPON_DOT_COLOR  = new Color(1.0f, 0.75f, 0.1f, 1f);

    // One extra ring of tiles beyond the visible radius to fill partial cells at edges
    private static final int RENDER_TILE_RADIUS = MINI_MAP_TILE_RADIUS + 1;

    private final Level level;
    private final DoorManager doorManager;
    private final ShapeRenderer shapes;

    private float playerWorldX = 0f;
    private float playerWorldY = 0f;
    private List<GroundItem> groundItems = Collections.emptyList();
    private FogOfWarMap fogOfWarMap = null;

    public LevelRenderer(Level level, DoorManager doorManager) {
        this.level       = level;
        this.doorManager = doorManager;
        this.shapes      = new ShapeRenderer();
    }

    public void setFogOfWarMap(FogOfWarMap map) {
        this.fogOfWarMap = map;
    }

    public void setPlayerWorldPosition(float worldX, float worldY) {
        this.playerWorldX = worldX;
        this.playerWorldY = worldY;
    }

    public void setGroundItems(List<GroundItem> items) {
        this.groundItems = (items != null) ? items : Collections.emptyList();
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

        // Filled pass — fog of war, walls, doors, and weapon pickup dots clipped to mini-map bounds
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int deltaRow = -RENDER_TILE_RADIUS; deltaRow <= RENDER_TILE_RADIUS; deltaRow++) {
            for (int deltaColumn = -RENDER_TILE_RADIUS; deltaColumn <= RENDER_TILE_RADIUS; deltaColumn++) {
                int levelColumn = baseTileColumn + deltaColumn;
                int levelRow    = baseTileRow    + deltaRow;

                float tileLeft   = miniMapCenterX - subTileOffsetX + deltaColumn * MINI_MAP_CELL_SIZE;
                float tileBottom = miniMapCenterY - subTileOffsetY + deltaRow    * MINI_MAP_CELL_SIZE;
                float clippedLeft   = Math.max(miniMapLeft,   tileLeft);
                float clippedBottom = Math.max(miniMapBottom, tileBottom);
                float clippedRight  = Math.min(miniMapRight,  tileLeft   + MINI_MAP_CELL_SIZE);
                float clippedTop    = Math.min(miniMapTop,    tileBottom + MINI_MAP_CELL_SIZE);
                if (clippedRight <= clippedLeft || clippedTop <= clippedBottom) continue;

                if (fogOfWarMap != null && !fogOfWarMap.isRevealed(levelColumn, levelRow)) {
                    shapes.setColor(FOG_MINIMAP_UNEXPLORED_R, FOG_MINIMAP_UNEXPLORED_G,
                                    FOG_MINIMAP_UNEXPLORED_B, 1f);
                    shapes.rect(clippedLeft, clippedBottom,
                                clippedRight - clippedLeft, clippedTop - clippedBottom);
                    continue;
                }

                char cell = level.getCell(levelColumn, levelRow);
                if (Level.isWall(cell)) {
                    shapes.setColor(WALL_COLOR);
                } else if (Level.isDoor(cell)) {
                    DoorState doorState = doorManager.getStateAt(levelColumn, levelRow);
                    if (doorState == DoorState.OPEN) continue; // Open doorway shows as floor
                    float openFraction = doorManager.getOpenFractionAt(levelColumn, levelRow);
                    shapes.setColor(
                            GameMath.lerp(DOOR_MINIMAP_CLOSED_R, DOOR_MINIMAP_OPEN_R, openFraction),
                            GameMath.lerp(DOOR_MINIMAP_CLOSED_G, DOOR_MINIMAP_OPEN_G, openFraction),
                            GameMath.lerp(DOOR_MINIMAP_CLOSED_B, DOOR_MINIMAP_OPEN_B, openFraction),
                            1f);
                } else {
                    continue;
                }
                shapes.rect(clippedLeft, clippedBottom,
                            clippedRight - clippedLeft, clippedTop - clippedBottom);
            }
        }

        // Weapon pickup dots — gold dot centred in each revealed tile that holds a ground item
        shapes.setColor(WEAPON_DOT_COLOR);
        float dotSize = MINI_MAP_CELL_SIZE * 0.45f;
        for (int itemIndex = 0; itemIndex < groundItems.size(); itemIndex++) {
            GroundItem item = groundItems.get(itemIndex);
            if (fogOfWarMap != null && !fogOfWarMap.isRevealed(item.tileColumn, item.tileRow)) continue;
            int deltaColumn = item.tileColumn - baseTileColumn;
            int deltaRow    = item.tileRow    - baseTileRow;
            float tileLeft   = miniMapCenterX - subTileOffsetX + deltaColumn * MINI_MAP_CELL_SIZE;
            float tileBottom = miniMapCenterY - subTileOffsetY + deltaRow    * MINI_MAP_CELL_SIZE;
            float dotLeft    = tileLeft   + (MINI_MAP_CELL_SIZE - dotSize) / 2f;
            float dotBottom  = tileBottom + (MINI_MAP_CELL_SIZE - dotSize) / 2f;
            float dotRight   = dotLeft   + dotSize;
            float dotTop     = dotBottom + dotSize;
            if (dotRight > miniMapLeft && dotLeft < miniMapRight
                    && dotTop > miniMapBottom && dotBottom < miniMapTop) {
                float clippedLeft   = Math.max(miniMapLeft,   dotLeft);
                float clippedBottom = Math.max(miniMapBottom, dotBottom);
                float clippedRight  = Math.min(miniMapRight,  dotRight);
                float clippedTop    = Math.min(miniMapTop,    dotTop);
                shapes.rect(clippedLeft, clippedBottom,
                            clippedRight - clippedLeft, clippedTop - clippedBottom);
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
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}

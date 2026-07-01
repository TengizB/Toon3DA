package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.door.DoorState;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.level.KeycardColor;
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

    // Overlay marker palette — full visibility pass (props, hazards, pickups, exit, threats)
    private static final Color EXIT_COLOR          = new Color(MINIMAP_EXIT_R, MINIMAP_EXIT_G, MINIMAP_EXIT_B, 1f);
    private static final Color PROP_COLOR          = new Color(MINIMAP_PROP_R, MINIMAP_PROP_G, MINIMAP_PROP_B, 1f);
    private static final Color BARREL_COLOR        = new Color(MINIMAP_BARREL_R, MINIMAP_BARREL_G, MINIMAP_BARREL_B, 1f);
    private static final Color HEALTH_PICKUP_COLOR = new Color(MINIMAP_HEALTH_PICKUP_R, MINIMAP_HEALTH_PICKUP_G, MINIMAP_HEALTH_PICKUP_B, 1f);
    private static final Color ARMOR_PICKUP_COLOR  = new Color(MINIMAP_ARMOR_PICKUP_R, MINIMAP_ARMOR_PICKUP_G, MINIMAP_ARMOR_PICKUP_B, 1f);
    private static final Color AMMO_PICKUP_COLOR   = new Color(MINIMAP_AMMO_PICKUP_R, MINIMAP_AMMO_PICKUP_G, MINIMAP_AMMO_PICKUP_B, 1f);
    private static final Color ENEMY_THREAT_COLOR  = new Color(MINIMAP_ENEMY_THREAT_R, MINIMAP_ENEMY_THREAT_G, MINIMAP_ENEMY_THREAT_B, 1f);
    private static final Color KEYCARD_RED_COLOR    = new Color(KEYCARD_AURA_RED_R, KEYCARD_AURA_RED_G, KEYCARD_AURA_RED_B, 1f);
    private static final Color KEYCARD_YELLOW_COLOR = new Color(KEYCARD_AURA_YELLOW_R, KEYCARD_AURA_YELLOW_G, KEYCARD_AURA_YELLOW_B, 1f);
    private static final Color KEYCARD_BLUE_COLOR   = new Color(KEYCARD_AURA_BLUE_R, KEYCARD_AURA_BLUE_G, KEYCARD_AURA_BLUE_B, 1f);

    // One extra ring of tiles beyond the visible radius to fill partial cells at edges
    private static final int RENDER_TILE_RADIUS = MINI_MAP_TILE_RADIUS + 1;

    private final Level level;
    private final DoorManager doorManager;
    private final ShapeRenderer shapes;

    private float playerWorldX = 0f;
    private float playerWorldY = 0f;
    private List<GroundItem> groundItems = Collections.emptyList();
    private List<Enemy> enemies = Collections.emptyList();
    private float animationClockSeconds = 0f;

    public LevelRenderer(Level level, DoorManager doorManager) {
        this.level       = level;
        this.doorManager = doorManager;
        this.shapes      = new ShapeRenderer();
    }

    public void setPlayerWorldPosition(float worldX, float worldY) {
        this.playerWorldX = worldX;
        this.playerWorldY = worldY;
    }

    public void setGroundItems(List<GroundItem> items) {
        this.groundItems = (items != null) ? items : Collections.emptyList();
    }

    /** Live enemy list reference (same one EnemyRenderer reads); only alerted, alive enemies draw a threat marker. */
    public void setEnemies(List<Enemy> enemies) {
        this.enemies = (enemies != null) ? enemies : Collections.emptyList();
    }

    /** Monotonically increasing clock used to pulse the exit marker and enemy threat chevrons. */
    public void setAnimationClock(float clockSeconds) {
        this.animationClockSeconds = clockSeconds;
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

        float pulseBrightness = GameMath.pulseMultiplier(animationClockSeconds, MINI_MAP_PULSE_HZ,
                MINIMAP_PULSE_MIN_BRIGHTNESS, MINIMAP_PULSE_MAX_BRIGHTNESS);

        // Filled pass — tile backgrounds (walls, doors), overlay markers (props, hazards, pickups,
        // exit, enemy threats), and weapon pickup dots, all clipped to mini-map bounds.
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int deltaRow = -RENDER_TILE_RADIUS; deltaRow <= RENDER_TILE_RADIUS; deltaRow++) {
            for (int deltaColumn = -RENDER_TILE_RADIUS; deltaColumn <= RENDER_TILE_RADIUS; deltaColumn++) {
                int levelColumn = baseTileColumn + deltaColumn;
                int levelRow    = baseTileRow    + deltaRow;
                char cell = level.getCell(levelColumn, levelRow);

                float tileLeft   = miniMapCenterX - subTileOffsetX + deltaColumn * MINI_MAP_CELL_SIZE;
                float tileBottom = miniMapCenterY - subTileOffsetY + deltaRow    * MINI_MAP_CELL_SIZE;

                if (Level.isWall(cell) || Level.isColumn(cell)) {
                    shapes.setColor(WALL_COLOR);
                    drawClippedRect(tileLeft, tileBottom, MINI_MAP_CELL_SIZE, MINI_MAP_CELL_SIZE,
                            miniMapLeft, miniMapBottom, miniMapRight, miniMapTop);
                } else if (Level.isDoor(cell)) {
                    DoorState doorState = doorManager.getStateAt(levelColumn, levelRow);
                    if (doorState == DoorState.OPEN) continue; // Open doorway shows as floor
                    float openFraction = doorManager.getOpenFractionAt(levelColumn, levelRow);
                    // Lerp between closed steel and open cyan colour based on open fraction.
                    float doorRed   = GameMath.lerp(DOOR_MINIMAP_CLOSED_R, DOOR_MINIMAP_OPEN_R, openFraction);
                    float doorGreen = GameMath.lerp(DOOR_MINIMAP_CLOSED_G, DOOR_MINIMAP_OPEN_G, openFraction);
                    float doorBlue  = GameMath.lerp(DOOR_MINIMAP_CLOSED_B, DOOR_MINIMAP_OPEN_B, openFraction);
                    if (Level.isLockedDoor(cell)) {
                        // Tint locked doors toward their required keycard colour so the player can
                        // read the lock at a glance and match it against keycard pickups on the map.
                        Color keycardColor = keycardMarkerColor(Level.keycardColorOfDoor(cell));
                        doorRed   = GameMath.lerp(doorRed,   keycardColor.r, DOOR_MINIMAP_LOCK_TINT_FRACTION);
                        doorGreen = GameMath.lerp(doorGreen, keycardColor.g, DOOR_MINIMAP_LOCK_TINT_FRACTION);
                        doorBlue  = GameMath.lerp(doorBlue,  keycardColor.b, DOOR_MINIMAP_LOCK_TINT_FRACTION);
                    }
                    shapes.setColor(doorRed, doorGreen, doorBlue, 1f);
                    drawClippedRect(tileLeft, tileBottom, MINI_MAP_CELL_SIZE, MINI_MAP_CELL_SIZE,
                            miniMapLeft, miniMapBottom, miniMapRight, miniMapTop);
                } else {
                    // Floor tile — draw an overlay marker only when the whole tile fits inside the
                    // mini-map bounds. Overlay shapes (circles, triangles) are not clip-rectangled
                    // like the tile fills above, so this guard is what keeps the outer buffer ring
                    // (RENDER_TILE_RADIUS) from bleeding markers past the map border.
                    boolean tileFullyVisible = tileLeft >= miniMapLeft && tileBottom >= miniMapBottom
                            && tileLeft + MINI_MAP_CELL_SIZE <= miniMapRight
                            && tileBottom + MINI_MAP_CELL_SIZE <= miniMapTop;
                    if (!tileFullyVisible) continue;

                    float markerCenterX = tileLeft   + MINI_MAP_CELL_SIZE / 2f;
                    float markerCenterY = tileBottom + MINI_MAP_CELL_SIZE / 2f;
                    if (Level.isStairsDown(cell)) {
                        drawExitMarker(markerCenterX, markerCenterY, pulseBrightness);
                    } else if (Level.isPropSolid(cell)) {
                        drawPropMarker(markerCenterX, markerCenterY, cell);
                    } else if (Level.isKeycardPickup(cell)) {
                        drawCircleMarker(markerCenterX, markerCenterY, keycardMarkerColor(Level.keycardColorOfPickup(cell)));
                    } else if (Level.isMedicalPickup(cell)) {
                        drawCircleMarker(markerCenterX, markerCenterY, HEALTH_PICKUP_COLOR);
                    } else if (Level.isArmourPickup(cell)) {
                        drawCircleMarker(markerCenterX, markerCenterY, ARMOR_PICKUP_COLOR);
                    } else if (Level.isAmmoPickup(cell)) {
                        drawCircleMarker(markerCenterX, markerCenterY, AMMO_PICKUP_COLOR);
                    }
                }
            }
        }

        // Enemy threat markers — only enemies actively engaging the player (never DORMANT ones),
        // so the mini-map never leaks free intel about enemies that haven't noticed the player yet.
        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (!enemy.isAlive() || !enemy.isAlerted()) continue;

            int deltaColumn = enemy.tileColumn - baseTileColumn;
            int deltaRow    = enemy.tileRow    - baseTileRow;
            if (deltaColumn < -RENDER_TILE_RADIUS || deltaColumn > RENDER_TILE_RADIUS
                    || deltaRow < -RENDER_TILE_RADIUS || deltaRow > RENDER_TILE_RADIUS) continue;

            float tileLeft   = miniMapCenterX - subTileOffsetX + deltaColumn * MINI_MAP_CELL_SIZE;
            float tileBottom = miniMapCenterY - subTileOffsetY + deltaRow    * MINI_MAP_CELL_SIZE;
            boolean tileFullyVisible = tileLeft >= miniMapLeft && tileBottom >= miniMapBottom
                    && tileLeft + MINI_MAP_CELL_SIZE <= miniMapRight
                    && tileBottom + MINI_MAP_CELL_SIZE <= miniMapTop;
            if (!tileFullyVisible) continue;

            drawEnemyThreatMarker(tileLeft + MINI_MAP_CELL_SIZE / 2f, tileBottom + MINI_MAP_CELL_SIZE / 2f,
                    pulseBrightness);
        }

        // Weapon pickup dots — gold dot centred in each tile that holds a ground item
        shapes.setColor(WEAPON_DOT_COLOR);
        float dotSize = MINI_MAP_CELL_SIZE * 0.45f;
        for (int itemIndex = 0; itemIndex < groundItems.size(); itemIndex++) {
            GroundItem item = groundItems.get(itemIndex);
            int deltaColumn = item.tileColumn - baseTileColumn;
            int deltaRow    = item.tileRow    - baseTileRow;
            float tileLeft   = miniMapCenterX - subTileOffsetX + deltaColumn * MINI_MAP_CELL_SIZE;
            float tileBottom = miniMapCenterY - subTileOffsetY + deltaRow    * MINI_MAP_CELL_SIZE;
            float dotLeft    = tileLeft   + (MINI_MAP_CELL_SIZE - dotSize) / 2f;
            float dotBottom  = tileBottom + (MINI_MAP_CELL_SIZE - dotSize) / 2f;
            shapes.setColor(WEAPON_DOT_COLOR);
            drawClippedRect(dotLeft, dotBottom, dotSize, dotSize,
                    miniMapLeft, miniMapBottom, miniMapRight, miniMapTop);
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

    /** Clips an axis-aligned rect to the mini-map bounds and draws it, skipping degenerate results. */
    private void drawClippedRect(float left, float bottom, float width, float height,
                                  float miniMapLeft, float miniMapBottom, float miniMapRight, float miniMapTop) {
        float clippedLeft   = Math.max(miniMapLeft,   left);
        float clippedBottom = Math.max(miniMapBottom, bottom);
        float clippedRight  = Math.min(miniMapRight,  left   + width);
        float clippedTop    = Math.min(miniMapTop,    bottom + height);
        if (clippedRight > clippedLeft && clippedTop > clippedBottom) {
            shapes.rect(clippedLeft, clippedBottom, clippedRight - clippedLeft, clippedTop - clippedBottom);
        }
    }

    /** Generic obstacle (grey) vs. shootable barrel (hazard orange, chars 'g'/'E') — caller guarantees the tile is fully on-map. */
    private void drawPropMarker(float centerX, float centerY, char cell) {
        boolean isBarrel = cell == 'g' || cell == 'E';
        shapes.setColor(isBarrel ? BARREL_COLOR : PROP_COLOR);
        float half = MINI_MAP_PROP_SIZE / 2f;
        shapes.rect(centerX - half, centerY - half, MINI_MAP_PROP_SIZE, MINI_MAP_PROP_SIZE);
    }

    /** Small filled circle for a floor-decal pickup — caller guarantees the tile is fully on-map. */
    private void drawCircleMarker(float centerX, float centerY, Color color) {
        shapes.setColor(color);
        shapes.circle(centerX, centerY, MINI_MAP_MARKER_SIZE / 2f);
    }

    /** Pulsing green diamond marking the level exit — caller guarantees the tile is fully on-map. */
    private void drawExitMarker(float centerX, float centerY, float pulseBrightness) {
        float half = MINI_MAP_MARKER_SIZE / 2f;
        shapes.setColor(EXIT_COLOR.r * pulseBrightness, EXIT_COLOR.g * pulseBrightness, EXIT_COLOR.b * pulseBrightness, 1f);
        shapes.triangle(centerX, centerY + half, centerX + half, centerY, centerX, centerY - half);
        shapes.triangle(centerX, centerY + half, centerX, centerY - half, centerX - half, centerY);
    }

    /** Pulsing red chevron marking an alerted enemy — caller guarantees the tile is fully on-map. */
    private void drawEnemyThreatMarker(float centerX, float centerY, float pulseBrightness) {
        float halfWidth = MINI_MAP_MARKER_SIZE / 2f;
        float halfHeight = MINI_MAP_MARKER_SIZE * 0.45f;
        shapes.setColor(ENEMY_THREAT_COLOR.r * pulseBrightness, ENEMY_THREAT_COLOR.g * pulseBrightness,
                ENEMY_THREAT_COLOR.b * pulseBrightness, 1f);
        shapes.triangle(centerX, centerY + halfHeight,
                         centerX - halfWidth, centerY - halfHeight,
                         centerX + halfWidth, centerY - halfHeight);
    }

    private static Color keycardMarkerColor(KeycardColor keycardColor) {
        switch (keycardColor) {
            case RED:    return KEYCARD_RED_COLOR;
            case YELLOW: return KEYCARD_YELLOW_COLOR;
            default:     return KEYCARD_BLUE_COLOR;
        }
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}

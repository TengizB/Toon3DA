package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.door.DoorState;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.HeavyAttack;
import ge.tbegvadze.toon3d.enemy.IntentVerb;
import ge.tbegvadze.toon3d.enemy.PlannedAction;
import ge.tbegvadze.toon3d.hazard.ExplosiveBarrelManager;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.level.KeycardColor;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.CombatPalette;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.Collections;
import java.util.List;

import static ge.tbegvadze.toon3d.util.Constants.*;
import static ge.tbegvadze.toon3d.util.EnemyConstants.*;
import static ge.tbegvadze.toon3d.util.RenderConstants.*;

public class LevelRenderer implements Renderable, Disposable {

    // SUIT ink (visual-identity-order-4 R11): neutrals and the player's own things from ArtDirection, harm in
    // CombatPalette.DAMAGE, the facility's exit in FACILITY_CYAN. Keycard doors keep their key colours.
    private static final Color WALL_COLOR        = new Color(ArtDirection.UI_TEXT_SECONDARY_RED,
            ArtDirection.UI_TEXT_SECONDARY_GREEN, ArtDirection.UI_TEXT_SECONDARY_BLUE, MINIMAP_WALL_ALPHA);
    private static final Color GRID_COLOR        = new Color(ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN,
            ArtDirection.UI_LINE_BLUE, MINIMAP_GRID_ALPHA);
    private static final Color WEAPON_DOT_COLOR  = new Color(ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN,
            ArtDirection.SUIT_AMBER_BLUE, 1f);

    // Overlay marker palette — full visibility pass (props, hazards, pickups, exit, threats)
    private static final Color EXIT_COLOR          = new Color(ArtDirection.FACILITY_CYAN_RED,
            ArtDirection.FACILITY_CYAN_GREEN, ArtDirection.FACILITY_CYAN_BLUE, 1f);
    private static final Color PROP_COLOR          = new Color(ArtDirection.UI_TEXT_DISABLED_RED,
            ArtDirection.UI_TEXT_DISABLED_GREEN, ArtDirection.UI_TEXT_DISABLED_BLUE, 1f);
    private static final Color BARREL_COLOR        = new Color(MINIMAP_BARREL_R, MINIMAP_BARREL_G, MINIMAP_BARREL_B, 1f);
    private static final Color BARREL_DOT_COLOR    = new Color(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN,
            CombatPalette.DAMAGE_BLUE, 1f);
    private static final Color SHOP_COLOR          = new Color(ArtDirection.CREDITS_GOLD_RED,
            ArtDirection.CREDITS_GOLD_GREEN, ArtDirection.CREDITS_GOLD_BLUE, 1f);
    private static final Color HEALTH_PICKUP_COLOR = new Color(ArtDirection.HEALTH_GREEN_RED,
            ArtDirection.HEALTH_GREEN_GREEN, ArtDirection.HEALTH_GREEN_BLUE, 1f);
    private static final Color ARMOR_PICKUP_COLOR  = new Color(ArtDirection.ARMOUR_BLUE_RED,
            ArtDirection.ARMOUR_BLUE_GREEN, ArtDirection.ARMOUR_BLUE_BLUE, 1f);
    private static final Color AMMO_PICKUP_COLOR   = new Color(MINIMAP_AMMO_PICKUP_R, MINIMAP_AMMO_PICKUP_G, MINIMAP_AMMO_PICKUP_B, 1f);
    private static final Color ENEMY_THREAT_COLOR  = new Color(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN,
            CombatPalette.DAMAGE_BLUE, 1f);
    private static final Color KEYCARD_RED_COLOR    = new Color(KEYCARD_AURA_RED_R, KEYCARD_AURA_RED_G, KEYCARD_AURA_RED_B, 1f);
    private static final Color KEYCARD_YELLOW_COLOR = new Color(KEYCARD_AURA_YELLOW_R, KEYCARD_AURA_YELLOW_G, KEYCARD_AURA_YELLOW_B, 1f);
    private static final Color KEYCARD_BLUE_COLOR   = new Color(KEYCARD_AURA_BLUE_R, KEYCARD_AURA_BLUE_G, KEYCARD_AURA_BLUE_B, 1f);

    // One extra ring of tiles beyond the visible radius to fill partial cells at edges
    private static final int RENDER_TILE_RADIUS = MINI_MAP_TILE_RADIUS + 1;

    private final Level level;
    private final DoorManager doorManager;
    private final ShapeRenderer shapes;

    // Octagon clip scratch (a rect clipped by four diagonal half-planes has at most 8 vertices) — no allocation.
    private static final int CLIP_VERTEX_CAPACITY = 12;
    private final float[] clipFirstX  = new float[CLIP_VERTEX_CAPACITY];
    private final float[] clipFirstY  = new float[CLIP_VERTEX_CAPACITY];
    private final float[] clipSecondX = new float[CLIP_VERTEX_CAPACITY];
    private final float[] clipSecondY = new float[CLIP_VERTEX_CAPACITY];

    private float playerWorldX = 0f;
    private float playerWorldY = 0f;
    private List<GroundItem> groundItems = Collections.emptyList();
    private List<Enemy> enemies = Collections.emptyList();
    private float animationClockSeconds = 0f;
    /** Sapper r1 (BuildStats.barrelsOnMinimap): explosive barrels also get a bright pulsing dot. */
    private boolean barrelsHighlighted = false;

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

    /** Balance-overhaul order 5 (Sapper r1): when true every explosive barrel is marked with a pulsing dot. */
    public void setBarrelsHighlighted(boolean barrelsHighlighted) {
        this.barrelsHighlighted = barrelsHighlighted;
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
        Gdx.gl.glEnable(GL20.GL_BLEND); // heavy danger fill is translucent
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // R11 frame fill: a UI_INK_BLACK chamfered slab under the map (its border is drawn last, on top).
        UiKit.panel(shapes, miniMapLeft, miniMapBottom, MINI_MAP_WORLD_SIZE, MINI_MAP_WORLD_SIZE, ArtDirection.UI_CHAMFER,
                ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE,
                MINIMAP_FRAME_FILL_ALPHA, 0f, 0f, 0f, 0f, 0f);
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
                    } else if (Level.isShop(cell)) {
                        drawShopMarker(markerCenterX, markerCenterY, pulseBrightness);
                    } else if (Level.isPropSolid(cell)) {
                        drawPropMarker(markerCenterX, markerCenterY, cell);
                        if (barrelsHighlighted && ExplosiveBarrelManager.isBarrelCell(cell)) {
                            drawBarrelDot(markerCenterX, markerCenterY, pulseBrightness);
                        }
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

        // Heavy danger tiles (balance-overhaul order 4, H15): red fill under the enemy markers. Reads the
        // committed plan only, so it never reveals more than the first-person floor markers do.
        shapes.setColor(HEAVY_MARKER_R, HEAVY_MARKER_G, HEAVY_MARKER_B, HEAVY_MINIMAP_ALPHA);
        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (!enemy.isAlive()) continue;
            PlannedAction plan = enemy.plannedAction;
            if (plan.heavyAttack == null || plan.verb == IntentVerb.STUNNED) continue;
            int firstTileIndex = (plan.heavyAttack.dangerShape() == HeavyAttack.DangerShape.CROSS) ? 1 : 0;
            for (int tileIndex = firstTileIndex; tileIndex < plan.dangerTileCount; tileIndex++) {
                int deltaColumn = plan.dangerTileColumns[tileIndex] - baseTileColumn;
                int deltaRow    = plan.dangerTileRows[tileIndex]    - baseTileRow;
                if (deltaColumn < -RENDER_TILE_RADIUS || deltaColumn > RENDER_TILE_RADIUS
                        || deltaRow < -RENDER_TILE_RADIUS || deltaRow > RENDER_TILE_RADIUS) continue;
                float tileLeft   = miniMapCenterX - subTileOffsetX + deltaColumn * MINI_MAP_CELL_SIZE;
                float tileBottom = miniMapCenterY - subTileOffsetY + deltaRow    * MINI_MAP_CELL_SIZE;
                drawClippedRect(tileLeft, tileBottom, MINI_MAP_CELL_SIZE, MINI_MAP_CELL_SIZE,
                        miniMapLeft, miniMapBottom, miniMapRight, miniMapTop);
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

        // Scrolling grid — thin filled rects (clipped to the chamfered frame like every tile), then the frame's
        // UI_LINE border and SUIT_AMBER_DIM corner brackets on top.
        shapes.setColor(GRID_COLOR);
        for (int deltaColumn = -RENDER_TILE_RADIUS; deltaColumn <= RENDER_TILE_RADIUS + 1; deltaColumn++) {
            float lineX = miniMapCenterX - subTileOffsetX + deltaColumn * MINI_MAP_CELL_SIZE;
            if (lineX >= miniMapLeft && lineX <= miniMapRight) {
                drawClippedRect(lineX, miniMapBottom, MINIMAP_GRID_LINE_THICKNESS, MINI_MAP_WORLD_SIZE,
                        miniMapLeft, miniMapBottom, miniMapRight, miniMapTop);
            }
        }
        for (int deltaRow = -RENDER_TILE_RADIUS; deltaRow <= RENDER_TILE_RADIUS + 1; deltaRow++) {
            float lineY = miniMapCenterY - subTileOffsetY + deltaRow * MINI_MAP_CELL_SIZE;
            if (lineY >= miniMapBottom && lineY <= miniMapTop) {
                drawClippedRect(miniMapLeft, lineY, MINI_MAP_WORLD_SIZE, MINIMAP_GRID_LINE_THICKNESS,
                        miniMapLeft, miniMapBottom, miniMapRight, miniMapTop);
            }
        }
        UiKit.panel(shapes, miniMapLeft, miniMapBottom, MINI_MAP_WORLD_SIZE, MINI_MAP_WORLD_SIZE, ArtDirection.UI_CHAMFER,
                0f, 0f, 0f, 0f,
                ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f,
                ArtDirection.UI_BORDER_THICKNESS);
        UiKit.brackets(shapes, miniMapLeft, miniMapBottom, MINI_MAP_WORLD_SIZE, MINI_MAP_WORLD_SIZE,
                ArtDirection.SUIT_AMBER_DIM_RED, ArtDirection.SUIT_AMBER_DIM_GREEN, ArtDirection.SUIT_AMBER_DIM_BLUE, 1f);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Clips an axis-aligned rect to the mini-map's chamfered frame (the bounds, then the four UI_CHAMFER corner
     * cuts) and draws it, skipping degenerate results. A rect clear of every cut is drawn as a plain rect; one
     * that crosses a cut is clipped to a convex polygon and fan-triangulated.
     */
    private void drawClippedRect(float left, float bottom, float width, float height,
                                  float miniMapLeft, float miniMapBottom, float miniMapRight, float miniMapTop) {
        float clippedLeft   = Math.max(miniMapLeft,   left);
        float clippedBottom = Math.max(miniMapBottom, bottom);
        float clippedRight  = Math.min(miniMapRight,  left   + width);
        float clippedTop    = Math.min(miniMapTop,    bottom + height);
        if (clippedRight <= clippedLeft || clippedTop <= clippedBottom) return;

        float chamfer = ArtDirection.UI_CHAMFER;
        float bottomLeftThreshold  = miniMapLeft + miniMapBottom + chamfer;    //  x + y
        float bottomRightThreshold = -miniMapRight + miniMapBottom + chamfer;  // -x + y
        float topRightThreshold    = -miniMapRight - miniMapTop + chamfer;     // -x - y
        float topLeftThreshold     = miniMapLeft - miniMapTop + chamfer;       //  x - y
        if (clippedLeft + clippedBottom >= bottomLeftThreshold
                && -clippedRight + clippedBottom >= bottomRightThreshold
                && -clippedRight - clippedTop >= topRightThreshold
                && clippedLeft - clippedTop >= topLeftThreshold) {
            shapes.rect(clippedLeft, clippedBottom, clippedRight - clippedLeft, clippedTop - clippedBottom);
            return;
        }
        clipFirstX[0] = clippedLeft;  clipFirstY[0] = clippedBottom;
        clipFirstX[1] = clippedRight; clipFirstY[1] = clippedBottom;
        clipFirstX[2] = clippedRight; clipFirstY[2] = clippedTop;
        clipFirstX[3] = clippedLeft;  clipFirstY[3] = clippedTop;
        int vertexCount = GameMath.clipPolygonToHalfPlane(clipFirstX, clipFirstY, 4, 1f, 1f,
                bottomLeftThreshold, clipSecondX, clipSecondY);
        vertexCount = GameMath.clipPolygonToHalfPlane(clipSecondX, clipSecondY, vertexCount, -1f, 1f,
                bottomRightThreshold, clipFirstX, clipFirstY);
        vertexCount = GameMath.clipPolygonToHalfPlane(clipFirstX, clipFirstY, vertexCount, -1f, -1f,
                topRightThreshold, clipSecondX, clipSecondY);
        vertexCount = GameMath.clipPolygonToHalfPlane(clipSecondX, clipSecondY, vertexCount, 1f, -1f,
                topLeftThreshold, clipFirstX, clipFirstY);
        for (int vertexIndex = 1; vertexIndex + 1 < vertexCount; vertexIndex++) {
            shapes.triangle(clipFirstX[0], clipFirstY[0], clipFirstX[vertexIndex], clipFirstY[vertexIndex],
                    clipFirstX[vertexIndex + 1], clipFirstY[vertexIndex + 1]);
        }
    }

    /** Generic obstacle (grey) vs. shootable barrel (hazard orange, chars 'g'/'E') — caller guarantees the tile is fully on-map. */
    private void drawPropMarker(float centerX, float centerY, char cell) {
        boolean isBarrel = ExplosiveBarrelManager.isBarrelCell(cell);
        shapes.setColor(isBarrel ? BARREL_COLOR : PROP_COLOR);
        float half = MINI_MAP_PROP_SIZE / 2f;
        shapes.rect(centerX - half, centerY - half, MINI_MAP_PROP_SIZE, MINI_MAP_PROP_SIZE);
    }

    /** Pulsing bright dot over a barrel tile (Sapper r1) — caller guarantees the tile is fully on-map. */
    private void drawBarrelDot(float centerX, float centerY, float pulseBrightness) {
        shapes.setColor(BARREL_DOT_COLOR.r * pulseBrightness, BARREL_DOT_COLOR.g * pulseBrightness,
                BARREL_DOT_COLOR.b * pulseBrightness, 1f);
        shapes.circle(centerX, centerY, MINI_MAP_MARKER_SIZE / 2f);
    }

    /**
     * Bright pulsing CREDITS_GOLD square with a dim outer glow halo, marking a shop vending machine
     * ('@') distinctly from generic solid props (which render in dull grey via drawPropMarker).
     * No GL blending is enabled in this render pass, so the "glow" is an opaque, dimmer square
     * beneath a smaller pulsing bright core rather than a translucent overlay.
     */
    private void drawShopMarker(float centerX, float centerY, float pulseBrightness) {
        float glowHalf = (MINI_MAP_PROP_SIZE * MINIMAP_SHOP_GLOW_SCALE) / 2f;
        shapes.setColor(SHOP_COLOR.r * MINIMAP_SHOP_GLOW_BRIGHTNESS, SHOP_COLOR.g * MINIMAP_SHOP_GLOW_BRIGHTNESS,
                SHOP_COLOR.b * MINIMAP_SHOP_GLOW_BRIGHTNESS, 1f);
        shapes.rect(centerX - glowHalf, centerY - glowHalf, glowHalf * 2f, glowHalf * 2f);

        float coreHalf = MINI_MAP_PROP_SIZE / 2f;
        shapes.setColor(SHOP_COLOR.r * pulseBrightness, SHOP_COLOR.g * pulseBrightness,
                SHOP_COLOR.b * pulseBrightness, 1f);
        shapes.rect(centerX - coreHalf, centerY - coreHalf, coreHalf * 2f, coreHalf * 2f);
    }

    /** Small filled circle for a floor-decal pickup — caller guarantees the tile is fully on-map. */
    private void drawCircleMarker(float centerX, float centerY, Color color) {
        shapes.setColor(color);
        shapes.circle(centerX, centerY, MINI_MAP_MARKER_SIZE / 2f);
    }

    /** Pulsing FACILITY_CYAN diamond marking the level exit (the facility's way out, R8) — caller guarantees the tile is fully on-map. */
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

package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ge.tbegvadze.toon3d.util.Constants.*;

/**
 * Renders billboard prop sprites (barrels, terminals, lockers, etc.) after the wall pass.
 *
 * Pipeline per frame:
 *   1. Compute perpendicular depth for every prop in the level.
 *   2. Cull props behind the player and beyond MAX_PROP_DRAW_DISTANCE_TILES.
 *   3. Insertion-sort surviving props farthest-to-nearest (painter's algorithm).
 *   4. For each prop, project onto the screen and draw 1-pixel-wide column slices,
 *      clipping each column against the wall z-buffer from WallRenderer.
 *
 * Zero per-frame allocations: all scratch arrays are pre-allocated at construction.
 * The SpriteBatch auto-flushes when the texture changes between prop types.
 */
public class PropRenderer implements Renderable, Disposable {

    // Per-prop height multiplier relative to a full wall stripe (WORLD_HEIGHT / depth).
    // Keeps shorter objects (barrels, crates) below full wall height.
    private static float propHeightMultiplier(char propChar) {
        switch (propChar) {
            case 'g': return 0.65f;   // radioactive barrel
            case 'E': return 0.65f;   // explosive barrel
            case 'T': return 0.75f;
            case 'L': return 0.90f;
            case 'C': return 0.50f;
            case 'm': return 0.35f;
            case 's': return 0.22f;
            case '.': return 0.18f;
            case 'O': return 0.18f;
            case 'r': return 0.18f;   // red keycard pickup
            case 'y': return 0.18f;   // yellow keycard pickup
            case 'b': return 0.18f;   // blue keycard pickup
            case '+': return MEDKIT_STIM_SPRITE_HEIGHT;
            case 'H': return MEDKIT_FULL_SPRITE_HEIGHT;
            case 'a': return ARMOUR_SHARD_SPRITE_HEIGHT;
            case 'A': return ARMOUR_VEST_SPRITE_HEIGHT;
            case '>': return STAIRS_SPRITE_HEIGHT;
            default:  return 0.70f;
        }
    }

    private final Level                   level;
    private final WallRenderer            wallRenderer;
    private final List<PropPlacement>     propPlacements;
    private final Map<Character, Texture> textures;
    private final SpriteBatch             batch;

    // Pre-allocated scratch buffers for depth-sorting — sized to total prop count.
    private final int[]   sortedIndices;
    private final float[] sortedDepths;

    private float playerWorldX   = 0f;
    private float playerWorldY   = 0f;
    private float directionX     = 1f;
    private float directionY     = 0f;
    private float planeX         = 0f;
    private float planeY         = 1f;
    private float alertPulse     = 0f;

    public PropRenderer(Level level, WallRenderer wallRenderer) {
        this.level          = level;
        this.wallRenderer   = wallRenderer;
        this.propPlacements = buildPropPlacements(level);
        int propCount       = propPlacements.size();
        this.sortedIndices  = new int[propCount];
        this.sortedDepths   = new float[propCount];
        // SpriteBatch capacity = one sprite per screen column (1-pixel-wide column draws).
        this.batch    = new SpriteBatch(WALL_PROJECTION_SCREEN_WIDTH);
        this.textures = buildTextures();
    }

    /** Scans the level grid once at startup and records every prop tile's position. */
    private static List<PropPlacement> buildPropPlacements(Level level) {
        List<PropPlacement> list = new ArrayList<>();
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                char cell = level.getCell(tileColumn, tileRow);
                if (Level.isProp(cell)) {
                    float worldCenterX = tileColumn * CELL_SIZE + CELL_SIZE / 2f;
                    float worldCenterY = tileRow    * CELL_SIZE + CELL_SIZE / 2f;
                    list.add(new PropPlacement(tileColumn, tileRow, cell,
                                               worldCenterX, worldCenterY));
                }
            }
        }
        return list;
    }

    public void setPlayerState(float worldX, float worldY,
                               float playerDirectionX, float playerDirectionY,
                               float fieldOfViewRadians) {
        this.playerWorldX = worldX;
        this.playerWorldY = worldY;
        this.directionX   = playerDirectionX;
        this.directionY   = playerDirectionY;
        float planeScale  = (float) Math.tan(fieldOfViewRadians / 2.0);
        this.planeX       = GameMath.cameraPlaneX(playerDirectionY, planeScale);
        this.planeY       = GameMath.cameraPlaneY(playerDirectionX, planeScale);
    }

    public void setAlertPulse(float pulse) {
        this.alertPulse = pulse;
    }

    @Override
    public void render(OrthographicCamera camera) {
        int propCount = propPlacements.size();
        if (propCount == 0) return;

        // --- Cull and collect visible props, computing depth for each. ---
        int visibleCount = 0;
        for (int propIndex = 0; propIndex < propCount; propIndex++) {
            PropPlacement prop = propPlacements.get(propIndex);
            // Skip props that have been consumed from the level grid (e.g. picked-up keycards).
            if (level.getCell(prop.tileColumn, prop.tileRow) != prop.propChar) continue;
            float tileOffsetX = (prop.worldCenterX - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (prop.worldCenterY - playerWorldY) / CELL_SIZE;
            float depth       = GameMath.spriteDepth(tileOffsetX, tileOffsetY,
                                                     directionX, directionY);
            if (depth <= PROP_BEHIND_PLAYER_EPSILON_TILES) continue;
            if (depth > MAX_PROP_DRAW_DISTANCE_TILES)      continue;
            sortedIndices[visibleCount] = propIndex;
            sortedDepths[visibleCount]  = depth;
            visibleCount++;
        }

        if (visibleCount == 0) return;

        // --- Insertion-sort farthest first (painters algorithm, no allocations). ---
        for (int sortPass = 1; sortPass < visibleCount; sortPass++) {
            int   insertIndex = sortedIndices[sortPass];
            float insertDepth = sortedDepths[sortPass];
            int   insertAt    = sortPass - 1;
            while (insertAt >= 0 && sortedDepths[insertAt] < insertDepth) {
                sortedIndices[insertAt + 1] = sortedIndices[insertAt];
                sortedDepths[insertAt + 1]  = sortedDepths[insertAt];
                insertAt--;
            }
            sortedIndices[insertAt + 1] = insertIndex;
            sortedDepths[insertAt + 1]  = insertDepth;
        }

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
            int           propIndex = sortedIndices[sortedPosition];
            float         depth     = sortedDepths[sortedPosition];
            PropPlacement prop      = propPlacements.get(propIndex);

            Texture texture = textures.get(prop.propChar);
            if (texture == null) continue;

            float tileOffsetX = (prop.worldCenterX - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (prop.worldCenterY - playerWorldY) / CELL_SIZE;

            float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                    tileOffsetX, tileOffsetY, directionX, directionY,
                    planeX, planeY, WALL_PROJECTION_SCREEN_WIDTH);

            float heightMultiplier   = propHeightMultiplier(prop.propChar);
            float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, depth);
            float spriteScreenHeight = fullWallLineHeight * heightMultiplier;
            float aspectRatio        = (float) texture.getWidth() / texture.getHeight();
            float spriteScreenWidth  = spriteScreenHeight * aspectRatio;

            int leftScreenColumn  = (int)(screenCenterColumn - spriteScreenWidth / 2f);
            int rightScreenColumn = (int)(screenCenterColumn + spriteScreenWidth / 2f);
            int columnSpan        = rightScreenColumn - leftScreenColumn;
            if (columnSpan <= 0) continue;

            float drawBottom = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, fullWallLineHeight);
            float drawTop    = drawBottom + spriteScreenHeight;
            float clampedBottom   = Math.max(0f, drawBottom);
            float clampedTop      = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, drawTop);
            if (clampedTop <= clampedBottom) continue;

            // Texture row clipping (same derivation as WallRenderer's wall stripe clipping).
            int textureWidth  = texture.getWidth();
            int textureHeight = texture.getHeight();
            int texSrcY       = GameMath.wallTextureClipSrcY(
                                    drawTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                    spriteScreenHeight, textureHeight);
            int texSrcHeight  = GameMath.wallTextureClipSrcHeight(
                                    clampedTop, clampedBottom,
                                    spriteScreenHeight, textureHeight);
            texSrcHeight = Math.min(texSrcHeight, textureHeight - texSrcY);
            texSrcHeight = Math.max(1, texSrcHeight);

            // Distance + alert shading — same formula as WallRenderer for visual consistency.
            float shade       = GameMath.wallShade(depth, WALL_SHADING_FALLOFF);
            float spriteRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
            float spriteGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            float spriteBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            batch.setColor(spriteRed, spriteGreen, spriteBlue, 1f);

            // Per-column draw loop with z-buffer occlusion test.
            int firstColumn = Math.max(0, leftScreenColumn);
            int lastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, rightScreenColumn);
            for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
                // Skip columns where a wall (or closer prop) is in front.
                if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;

                int texSrcX = (screenColumn - leftScreenColumn) * textureWidth / columnSpan;
                texSrcX = MathUtils.clamp(texSrcX, 0, textureWidth - 1);

                batch.draw(texture,
                           screenColumn * WALL_COLUMN_WIDTH, clampedBottom,
                           WALL_COLUMN_WIDTH, clampedTop - clampedBottom,
                           texSrcX, texSrcY, 1, texSrcHeight,
                           false, false);
            }
        }

        batch.setColor(Color.WHITE);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        for (Texture texture : textures.values()) {
            texture.dispose();
        }
    }

    // -------------------------------------------------------------------------
    // Placeholder texture generation
    // Each prop type gets a distinct colour and simple pattern so it is visually
    // identifiable before real art is provided. Drop a PNG at the matching path
    // (textures/props/<name>.png) to replace a generated texture — but for now
    // all textures are generated at startup.
    // -------------------------------------------------------------------------

    // Loads a texture from an asset file; falls back to the procedural generator when
    // the file is absent so the game always runs even without real art assets.
    private static Texture loadOrGenerate(String assetPath, Texture proceduralFallback) {
        if (Gdx.files.internal(assetPath).exists()) {
            Texture loaded = new Texture(Gdx.files.internal(assetPath));
            loaded.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            proceduralFallback.dispose();
            return loaded;
        }
        return proceduralFallback;
    }

    private static Map<Character, Texture> buildTextures() {
        Map<Character, Texture> map = new HashMap<>();
        map.put('g', loadOrGenerate(PROP_BARREL_RADIOACTIVE_PATH, generateBarrelTexture(false)));
        map.put('E', generateBarrelTexture(true));
        map.put('T', generateTerminalTexture());
        map.put('L', generateLockerTexture());
        map.put('C', generateCrateTexture());
        map.put('m', generateCorpseTexture());
        map.put('s', generateShotgunTexture());
        map.put('.', generateBloodTexture());
        map.put('O', generateOilTexture());
        map.put('r', generateKeycardTexture(0.85f, 0.10f, 0.10f));
        map.put('y', generateKeycardTexture(0.85f, 0.80f, 0.05f));
        map.put('b', generateKeycardTexture(0.05f, 0.50f, 0.90f));
        map.put('+', generateStimTexture());
        map.put('H', generateFieldMedkitTexture());
        map.put('a', generateArmourShardTexture());
        map.put('A', generateSecurityVestTexture());
        map.put('>', generateStairsTexture());
        return map;
    }

    private static Texture finalize(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    // Draws a solid filled rectangle leaving a 2-pixel black border around all edges.
    private static void fillBody(Pixmap pixmap, float red, float green, float blue) {
        int width  = pixmap.getWidth();
        int height = pixmap.getHeight();
        // Background transparent
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Body fill
        pixmap.setColor(red, green, blue, 1f);
        pixmap.fillRectangle(2, 2, width - 4, height - 4);
    }

    // Draws a horizontal band across the body at a given Y range.
    private static void drawBand(Pixmap pixmap, int yTop, int height,
                                  float red, float green, float blue) {
        pixmap.setColor(red, green, blue, 1f);
        pixmap.fillRectangle(2, yTop, pixmap.getWidth() - 4, height);
    }

    private static Texture generateBarrelTexture(boolean explosive) {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        if (explosive) {
            fillBody(pixmap, 0.75f, 0.18f, 0.05f); // dark orange-red
            drawBand(pixmap, 10, 5, 0.90f, 0.80f, 0.05f); // yellow ring
            drawBand(pixmap, 28, 5, 0.90f, 0.80f, 0.05f);
            drawBand(pixmap, 46, 5, 0.90f, 0.80f, 0.05f);
        } else {
            fillBody(pixmap, 0.13f, 0.38f, 0.13f); // dark green
            drawBand(pixmap, 10, 4, 0.08f, 0.25f, 0.08f); // darker rings
            drawBand(pixmap, 30, 4, 0.08f, 0.25f, 0.08f);
            drawBand(pixmap, 50, 4, 0.08f, 0.25f, 0.08f);
        }
        return finalize(pixmap);
    }

    private static Texture generateTerminalTexture() {
        Pixmap pixmap = new Pixmap(64, 96, Pixmap.Format.RGBA8888);
        fillBody(pixmap, 0.12f, 0.16f, 0.20f); // dark charcoal-blue
        // Screen area: upper 40% of the face, inset 6px on each side
        pixmap.setColor(0.05f, 0.55f, 0.70f, 1f); // cyan screen
        pixmap.fillRectangle(6, 6, 52, 34);
        // Screen inner text lines (decorative)
        pixmap.setColor(0.10f, 0.90f, 0.90f, 1f);
        pixmap.fillRectangle(10, 12, 44, 3);
        pixmap.fillRectangle(10, 20, 32, 3);
        pixmap.fillRectangle(10, 28, 20, 3);
        // Keyboard ledge
        pixmap.setColor(0.18f, 0.22f, 0.28f, 1f);
        pixmap.fillRectangle(6, 60, 52, 10);
        return finalize(pixmap);
    }

    private static Texture generateLockerTexture() {
        Pixmap pixmap = new Pixmap(48, 96, Pixmap.Format.RGBA8888);
        fillBody(pixmap, 0.27f, 0.32f, 0.38f); // steel blue-gray
        // Vertical centre seam
        pixmap.setColor(0.15f, 0.18f, 0.22f, 1f);
        pixmap.fillRectangle(22, 2, 4, 92);
        // Handle dots (left door)
        pixmap.setColor(0.55f, 0.60f, 0.65f, 1f);
        pixmap.fillRectangle(14, 44, 4, 8);
        // Handle dots (right door)
        pixmap.fillRectangle(30, 44, 4, 8);
        // Vent slits near top
        pixmap.setColor(0.15f, 0.18f, 0.22f, 1f);
        for (int slit = 0; slit < 4; slit++) {
            pixmap.fillRectangle(6, 10 + slit * 6, 10, 3);
            pixmap.fillRectangle(32, 10 + slit * 6, 10, 3);
        }
        return finalize(pixmap);
    }

    private static Texture generateCrateTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        fillBody(pixmap, 0.38f, 0.24f, 0.10f); // warm brown wood
        // Cross-plank pattern
        pixmap.setColor(0.25f, 0.15f, 0.06f, 1f);
        pixmap.fillRectangle(2, 30, 60, 4);  // horizontal plank
        pixmap.fillRectangle(30, 2, 4, 60);  // vertical plank
        // Corner nails (lighter spots)
        pixmap.setColor(0.55f, 0.45f, 0.28f, 1f);
        pixmap.fillRectangle(6,  6,  5, 5);
        pixmap.fillRectangle(53, 6,  5, 5);
        pixmap.fillRectangle(6,  53, 5, 5);
        pixmap.fillRectangle(53, 53, 5, 5);
        return finalize(pixmap);
    }

    private static Texture generateCorpseTexture() {
        Pixmap pixmap = new Pixmap(96, 48, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Crumpled body silhouette: dark reddish-brown
        pixmap.setColor(0.22f, 0.08f, 0.08f, 1f);
        pixmap.fillRectangle(4, 12, 88, 24);
        // Head
        pixmap.fillRectangle(76, 8, 16, 30);
        // Arm suggestion
        pixmap.setColor(0.18f, 0.06f, 0.06f, 1f);
        pixmap.fillRectangle(4, 8, 40, 8);
        // Blood pool
        pixmap.setColor(0.45f, 0.02f, 0.02f, 1f);
        pixmap.fillRectangle(2, 34, 60, 8);
        return finalize(pixmap);
    }

    private static Texture generateShotgunTexture() {
        Pixmap pixmap = new Pixmap(96, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Stock (right side, wider)
        pixmap.setColor(0.35f, 0.22f, 0.10f, 1f); // dark wood
        pixmap.fillRectangle(54, 10, 38, 16);
        // Barrel (left side, narrow)
        pixmap.setColor(0.22f, 0.22f, 0.22f, 1f); // dark metal
        pixmap.fillRectangle(4, 12, 54, 10);
        // Trigger guard
        pixmap.setColor(0.28f, 0.28f, 0.28f, 1f);
        pixmap.fillRectangle(60, 18, 10, 8);
        return finalize(pixmap);
    }

    private static Texture generateBloodTexture() {
        Pixmap pixmap = new Pixmap(64, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Main splatter pool
        pixmap.setColor(0.45f, 0.02f, 0.02f, 1f);
        pixmap.fillRectangle(8, 8, 48, 16);
        // Splatter drops
        pixmap.fillRectangle(2,  4,  10, 10);
        pixmap.fillRectangle(54, 6,  8,  8);
        pixmap.fillRectangle(20, 2,  14, 6);
        pixmap.fillRectangle(40, 20, 16, 8);
        // Darker centre
        pixmap.setColor(0.30f, 0.01f, 0.01f, 1f);
        pixmap.fillRectangle(18, 10, 28, 12);
        return finalize(pixmap);
    }

    private static Texture generateOilTexture() {
        Pixmap pixmap = new Pixmap(64, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Oil pool — dark teal with slight iridescent highlight bands
        pixmap.setColor(0.04f, 0.14f, 0.14f, 1f);
        pixmap.fillRectangle(6, 6, 52, 20);
        // Iridescent sheen bands
        pixmap.setColor(0.08f, 0.25f, 0.30f, 1f);
        pixmap.fillRectangle(10, 8,  18, 4);
        pixmap.setColor(0.12f, 0.18f, 0.35f, 1f);
        pixmap.fillRectangle(34, 12, 16, 4);
        return finalize(pixmap);
    }

    // Stim-pack ('+') — small white injector box with a red cross, cyan UAC glow pool at base.
    private static Texture generateStimTexture() {
        Pixmap pixmap = new Pixmap(32, 40, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Body: white box
        pixmap.setColor(0.90f, 0.92f, 0.92f, 1f);
        pixmap.fillRectangle(4, 8, 24, 26);
        // Red cross — vertical bar
        pixmap.setColor(0.85f, 0.08f, 0.08f, 1f);
        pixmap.fillRectangle(13, 11, 6, 20);
        // Red cross — horizontal bar
        pixmap.fillRectangle(7, 18, 18, 6);
        // Cyan glow pool at base
        pixmap.setColor(0.10f, 0.80f, 0.90f, 1f);
        pixmap.fillRectangle(2, 36, 28, 3);
        return finalize(pixmap);
    }

    // Field medkit ('H') — olive/white hard case, bold red cross, green status LED.
    private static Texture generateFieldMedkitTexture() {
        Pixmap pixmap = new Pixmap(48, 56, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Main case body: olive drab
        pixmap.setColor(0.42f, 0.46f, 0.28f, 1f);
        pixmap.fillRectangle(2, 4, 44, 44);
        // White lid panel
        pixmap.setColor(0.88f, 0.90f, 0.88f, 1f);
        pixmap.fillRectangle(6, 8, 36, 28);
        // Red cross on lid — vertical
        pixmap.setColor(0.85f, 0.08f, 0.08f, 1f);
        pixmap.fillRectangle(20, 11, 8, 22);
        // Red cross on lid — horizontal
        pixmap.fillRectangle(9,  19, 30, 8);
        // Green status LED (bottom-right corner of case)
        pixmap.setColor(0.10f, 0.95f, 0.25f, 1f);
        pixmap.fillRectangle(36, 40, 6, 5);
        // Handle ridge at top
        pixmap.setColor(0.28f, 0.30f, 0.18f, 1f);
        pixmap.fillRectangle(14, 2, 20, 4);
        // Cyan glow pool at base
        pixmap.setColor(0.10f, 0.80f, 0.90f, 1f);
        pixmap.fillRectangle(2, 50, 44, 4);
        return finalize(pixmap);
    }

    // Armour shard ('a') — angular cyan/steel plate fragment with bright-cyan rim.
    private static Texture generateArmourShardTexture() {
        Pixmap pixmap = new Pixmap(40, 40, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Steel-grey plate body (parallelogram silhouette approximated as rectangle)
        pixmap.setColor(0.28f, 0.34f, 0.38f, 1f);
        pixmap.fillRectangle(6, 8, 28, 22);
        // Diagonal scratch line (lighter band)
        pixmap.setColor(0.45f, 0.52f, 0.56f, 1f);
        pixmap.fillRectangle(8, 14, 24, 3);
        // Cyan rim-light on top edge
        pixmap.setColor(0.15f, 0.85f, 0.95f, 1f);
        pixmap.fillRectangle(6, 8, 28, 2);
        // Cyan glow pool at base
        pixmap.fillRectangle(4, 32, 32, 3);
        return finalize(pixmap);
    }

    // Security vest ('A') — gunmetal torso vest with cyan trim plates and UAC stencil glow.
    private static Texture generateSecurityVestTexture() {
        Pixmap pixmap = new Pixmap(56, 72, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Main vest body: gunmetal grey
        pixmap.setColor(0.22f, 0.26f, 0.28f, 1f);
        pixmap.fillRectangle(4, 4, 48, 56);
        // Shoulder plates (wider at top)
        pixmap.setColor(0.18f, 0.22f, 0.24f, 1f);
        pixmap.fillRectangle(2, 4, 52, 14);
        // Cyan trim plate — left chest
        pixmap.setColor(0.08f, 0.72f, 0.85f, 1f);
        pixmap.fillRectangle(6, 22, 18, 20);
        // Cyan trim plate — right chest
        pixmap.fillRectangle(32, 22, 18, 20);
        // Central UAC stencil (light cyan rectangle suggesting logo)
        pixmap.setColor(0.50f, 0.90f, 1.00f, 1f);
        pixmap.fillRectangle(22, 26, 12, 12);
        // Straps / buckles (dark horizontal bands)
        pixmap.setColor(0.12f, 0.14f, 0.15f, 1f);
        pixmap.fillRectangle(4, 46, 48, 4);
        // Cyan glow pool at base
        pixmap.setColor(0.08f, 0.72f, 0.85f, 1f);
        pixmap.fillRectangle(4, 62, 48, 5);
        return finalize(pixmap);
    }

    // Stairs-down grate ('>') — descent shaft set into the floor.
    // Dark steel frame, UAC hazard chevrons on the rim, black recessed pit with grate bars,
    // red emissive border that marks this tile as the exit from a distance.
    private static Texture generateStairsTexture() {
        Pixmap pixmap = new Pixmap(48, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Outer steel frame
        pixmap.setColor(0.20f, 0.20f, 0.23f, 1f);
        pixmap.fillRectangle(0, 0, 48, 32);
        // Recessed black pit
        pixmap.setColor(0.05f, 0.04f, 0.05f, 1f);
        pixmap.fillRectangle(6, 4, 36, 24);
        // Three horizontal grate bars across the pit
        pixmap.setColor(0.30f, 0.30f, 0.33f, 1f);
        pixmap.fillRectangle(6,  8, 36, 3);
        pixmap.fillRectangle(6, 14, 36, 3);
        pixmap.fillRectangle(6, 20, 36, 3);
        // Red emissive border around the pit — the beacon colour visible down corridors
        pixmap.setColor(0.82f, 0.16f, 0.16f, 1f);
        pixmap.fillRectangle(4,  2, 40,  2);  // top edge
        pixmap.fillRectangle(4, 28, 40,  2);  // bottom edge
        pixmap.fillRectangle(4,  2,  2, 28);  // left edge
        pixmap.fillRectangle(42, 2,  2, 28);  // right edge
        // Hazard chevrons on the top rim — alternating yellow / black 6-pixel blocks
        for (int chevronIndex = 0; chevronIndex < 4; chevronIndex++) {
            boolean isYellow = chevronIndex % 2 == 0;
            pixmap.setColor(isYellow ? 0.90f : 0.08f, isYellow ? 0.78f : 0.08f, 0.05f, 1f);
            pixmap.fillRectangle(6 + chevronIndex * 9, 0, 9, 2);
        }
        return finalize(pixmap);
    }

    /** Generates a flat keycard pickup sprite in the given tier color. */
    private static Texture generateKeycardTexture(float red, float green, float blue) {
        Pixmap pixmap = new Pixmap(48, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Card body
        pixmap.setColor(red * 0.6f, green * 0.6f, blue * 0.6f, 1f);
        pixmap.fillRectangle(4, 8, 40, 16);
        // Bright highlight stripe (top edge)
        pixmap.setColor(Math.min(1f, red + 0.3f), Math.min(1f, green + 0.3f), Math.min(1f, blue + 0.3f), 1f);
        pixmap.fillRectangle(4, 8, 40, 3);
        // Magnetic strip (dark horizontal band)
        pixmap.setColor(0.10f, 0.10f, 0.10f, 1f);
        pixmap.fillRectangle(4, 18, 40, 4);
        // Chip square (small bright square, left-center)
        pixmap.setColor(Math.min(1f, red + 0.15f), Math.min(1f, green + 0.15f), Math.min(1f, blue + 0.15f), 1f);
        pixmap.fillRectangle(8, 10, 8, 6);
        return finalize(pixmap);
    }
}

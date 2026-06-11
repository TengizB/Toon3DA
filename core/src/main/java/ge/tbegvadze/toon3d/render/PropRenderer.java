package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.Collections;
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
            case '>': return PORTAL_SPRITE_HEIGHT;
            case '6': return AMMO_PICKUP_HEIGHT_FRACTION;
            case '7': return AMMO_PICKUP_HEIGHT_FRACTION;
            case '8': return AMMO_PICKUP_HEIGHT_FRACTION;
            case '9': return AMMO_PICKUP_HEIGHT_FRACTION;
            case '0': return AMMO_PICKUP_HEIGHT_FRACTION;
            case '#': return PROP_CAMERA_HEIGHT;
            case '%': return PROP_GENERATOR_HEIGHT;
            case '&': return PROP_BIOPOD_HEIGHT;
            case '=': return PROP_RACK_HEIGHT;
            case '@': return PROP_VENDOR_HEIGHT;
            case 'I': return PROP_HEIGHT_SPECIMEN_TANK;
            case 'W': return PROP_HEIGHT_HOLO_WORKSTATION;
            case 'J': return PROP_HEIGHT_AICORE_NODE;
            case 'e': return PROP_HEIGHT_ENERGY_SCORCH;
            default:  return 0.70f;
        }
    }

    private final Level                   level;
    private final WallRenderer            wallRenderer;
    private final List<PropPlacement>     propPlacements;
    private final Map<Character, Texture> textures;
    private final Texture                 weaponPickupTexture;
    private final SpriteBatch             batch;

    // Weapon ground items (placed by LevelGenerator, consumed on player pickup).
    // Injected by World after level build; never null — starts empty.
    private List<GroundItem> groundItems = Collections.emptyList();

    // Dynamically-added prop placements (enemy ammo/corpse drops placed after level load).
    // Each entry is valid until the level tile no longer matches the stored propChar.
    private final List<PropPlacement> dynamicPropPlacements = new ArrayList<>();

    // Pre-allocated scratch buffers for depth-sorting — sized to total prop count.
    private final int[]   sortedIndices;
    private final float[] sortedDepths;

    // Per-column depth written during render — lets EnemyRenderer occlude against props.
    private final float[] propSpriteZBuffer;

    private float playerWorldX      = 0f;
    private float playerWorldY      = 0f;
    private float directionX        = 1f;
    private float directionY        = 0f;
    private float planeX            = 0f;
    private float planeY            = 1f;
    private float alertPulse        = 0f;
    private float lightingTimeSeconds = 0f;

    public PropRenderer(Level level, WallRenderer wallRenderer) {
        this.level          = level;
        this.wallRenderer   = wallRenderer;
        this.propPlacements = buildPropPlacements(level);
        int propCount       = propPlacements.size();
        this.sortedIndices      = new int[propCount];
        this.sortedDepths       = new float[propCount];
        this.propSpriteZBuffer  = new float[WALL_PROJECTION_SCREEN_WIDTH];
        // SpriteBatch capacity = one sprite per screen column (1-pixel-wide column draws).
        this.batch              = new SpriteBatch(WALL_PROJECTION_SCREEN_WIDTH);
        this.textures           = buildTextures();
        this.weaponPickupTexture = generateWeaponPickupTexture();
    }

    /** Replaces the ground item list; called by World after each level build. */
    public void setGroundItems(List<GroundItem> items) {
        this.groundItems = (items != null) ? items : Collections.emptyList();
    }

    /**
     * Registers a dynamically-placed prop (e.g. an enemy ammo drop or corpse decal)
     * that was not present in the level grid at construction time.
     * The entry becomes invisible as soon as the tile is consumed or overwritten.
     */
    public void addDynamicProp(int tileColumn, int tileRow, char propChar) {
        float worldCenterX = tileColumn * CELL_SIZE + CELL_SIZE / 2f;
        float worldCenterY = tileRow    * CELL_SIZE + CELL_SIZE / 2f;
        dynamicPropPlacements.add(new PropPlacement(tileColumn, tileRow, propChar,
                                                     worldCenterX, worldCenterY));
    }

    /** Clears all dynamic prop placements (called when loading a new level). */
    public void clearDynamicProps() {
        dynamicPropPlacements.clear();
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

    public void setLightingTime(float timeSeconds) {
        this.lightingTimeSeconds = timeSeconds;
    }

    /** Returns the per-column depth buffer written during render so EnemyRenderer can occlude against props. */
    public float[] getPropSpriteZBuffer() {
        return propSpriteZBuffer;
    }

    @Override
    public void render(OrthographicCamera camera) {
        int propCount = propPlacements.size();
        // Reset z-buffer every frame so stale depths from the previous frame don't occlude.
        java.util.Arrays.fill(propSpriteZBuffer, Float.MAX_VALUE);
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

            // Distance shading × tile brightness (lit/normal/unlit/flickering) — matches
            // the formula WallRenderer uses so props integrate seamlessly into their environment.
            float tileBrightness = level.getTileBrightness(prop.tileColumn, prop.tileRow, lightingTimeSeconds);
            float shade          = Math.min(GameMath.wallShade(depth, WALL_SHADING_FALLOFF) * tileBrightness,
                                            MAX_LIGHTING_SHADE);
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
                // Skip columns where a nearer prop was already drawn (painter's order, far→near,
                // so a shallower depth here means a previous iteration already wrote it).
                if (depth >= propSpriteZBuffer[screenColumn]) continue;
                // Only solid props (barrels, terminals, lockers, crates) write to the z-buffer.
                // Decal props (medkits, corpses, blood, oil, keycards, stairs) are flat floor items;
                // an enemy standing on the same tile must render in front of them.
                if (Level.isPropSolid(prop.propChar)) {
                    propSpriteZBuffer[screenColumn] = depth;
                }

                int texSrcX = (screenColumn - leftScreenColumn) * textureWidth / columnSpan;
                texSrcX = MathUtils.clamp(texSrcX, 0, textureWidth - 1);

                batch.draw(texture,
                           screenColumn * WALL_COLUMN_WIDTH, clampedBottom,
                           WALL_COLUMN_WIDTH, clampedTop - clampedBottom,
                           texSrcX, texSrcY, 1, texSrcHeight,
                           false, false);
            }
        }

        // Ground items: weapon pickups spawned by the level generator. These are entity-side
        // objects (not tile chars) so they need a separate render pass. Typically at most one
        // per level — no depth sort needed; wall z-buffer still provides correct occlusion.
        for (GroundItem groundItem : groundItems) {
            float itemWorldCenterX = groundItem.tileColumn * CELL_SIZE + CELL_SIZE / 2f;
            float itemWorldCenterY = groundItem.tileRow    * CELL_SIZE + CELL_SIZE / 2f;
            float tileOffsetX = (itemWorldCenterX - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (itemWorldCenterY - playerWorldY) / CELL_SIZE;
            float depth = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
            if (depth <= PROP_BEHIND_PLAYER_EPSILON_TILES) continue;
            if (depth > MAX_PROP_DRAW_DISTANCE_TILES)      continue;

            float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                    tileOffsetX, tileOffsetY, directionX, directionY,
                    planeX, planeY, WALL_PROJECTION_SCREEN_WIDTH);

            float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, depth);
            float spriteScreenHeight = fullWallLineHeight * WEAPON_PICKUP_HEIGHT_FRACTION;
            float spriteScreenWidth  = spriteScreenHeight; // square procedural texture

            int leftScreenColumn  = (int)(screenCenterColumn - spriteScreenWidth / 2f);
            int rightScreenColumn = (int)(screenCenterColumn + spriteScreenWidth / 2f);
            int columnSpan        = rightScreenColumn - leftScreenColumn;
            if (columnSpan <= 0) continue;

            float drawBottom    = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, fullWallLineHeight);
            float drawTop       = drawBottom + spriteScreenHeight;
            float clampedBottom = Math.max(0f, drawBottom);
            float clampedTop    = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, drawTop);
            if (clampedTop <= clampedBottom) continue;

            int textureWidth  = weaponPickupTexture.getWidth();
            int textureHeight = weaponPickupTexture.getHeight();
            int texSrcY       = GameMath.wallTextureClipSrcY(
                                    drawTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                    spriteScreenHeight, textureHeight);
            int texSrcHeight  = GameMath.wallTextureClipSrcHeight(
                                    clampedTop, clampedBottom,
                                    spriteScreenHeight, textureHeight);
            texSrcHeight = Math.min(texSrcHeight, textureHeight - texSrcY);
            texSrcHeight = Math.max(1, texSrcHeight);

            float tileBrightness = level.getTileBrightness(groundItem.tileColumn, groundItem.tileRow, lightingTimeSeconds);
            float shade          = Math.min(GameMath.wallShade(depth, WALL_SHADING_FALLOFF) * tileBrightness,
                                            MAX_LIGHTING_SHADE);
            float spriteRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
            float spriteGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            float spriteBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            batch.setColor(spriteRed, spriteGreen, spriteBlue, 1f);

            int firstColumn = Math.max(0, leftScreenColumn);
            int lastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, rightScreenColumn);
            for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
                if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
                if (depth >= propSpriteZBuffer[screenColumn]) continue;
                int texSrcX = (screenColumn - leftScreenColumn) * textureWidth / columnSpan;
                texSrcX = MathUtils.clamp(texSrcX, 0, textureWidth - 1);
                batch.draw(weaponPickupTexture,
                           screenColumn * WALL_COLUMN_WIDTH, clampedBottom,
                           WALL_COLUMN_WIDTH, clampedTop - clampedBottom,
                           texSrcX, texSrcY, 1, texSrcHeight,
                           false, false);
            }
        }

        // Dynamic props: enemy drops placed after level load (ammo boxes, corpse decals).
        // Rendered in a separate unsorted pass — wall z-buffer still provides correct occlusion.
        for (int dynamicIndex = 0; dynamicIndex < dynamicPropPlacements.size(); dynamicIndex++) {
            PropPlacement prop = dynamicPropPlacements.get(dynamicIndex);
            // Skip once the tile has been consumed (player picked up the drop).
            if (level.getCell(prop.tileColumn, prop.tileRow) != prop.propChar) continue;

            float tileOffsetX = (prop.worldCenterX - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (prop.worldCenterY - playerWorldY) / CELL_SIZE;
            float depth = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
            if (depth <= PROP_BEHIND_PLAYER_EPSILON_TILES) continue;
            if (depth > MAX_PROP_DRAW_DISTANCE_TILES)      continue;

            Texture texture = textures.get(prop.propChar);
            if (texture == null) continue;

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

            float drawBottom    = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, fullWallLineHeight);
            float drawTop       = drawBottom + spriteScreenHeight;
            float clampedBottom = Math.max(0f, drawBottom);
            float clampedTop    = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, drawTop);
            if (clampedTop <= clampedBottom) continue;

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

            float tileBrightness = level.getTileBrightness(prop.tileColumn, prop.tileRow, lightingTimeSeconds);
            float shade          = Math.min(GameMath.wallShade(depth, WALL_SHADING_FALLOFF) * tileBrightness,
                                            MAX_LIGHTING_SHADE);
            float spriteRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
            float spriteGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            float spriteBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            batch.setColor(spriteRed, spriteGreen, spriteBlue, 1f);

            int firstColumn = Math.max(0, leftScreenColumn);
            int lastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, rightScreenColumn);
            for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
                if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
                if (depth >= propSpriteZBuffer[screenColumn]) continue;
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
        weaponPickupTexture.dispose();
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
        map.put('>', generatePortalTexture());
        map.put('6', generateAmmoBoxTexture(0.72f, 0.48f, 0.18f));  // BULLETS — copper
        map.put('7', generateAmmoBoxTexture(0.78f, 0.68f, 0.12f));  // SHELLS  — brass
        map.put('8', generateAmmoBoxTexture(0.10f, 0.80f, 0.90f));  // CELLS   — cyan
        map.put('9', generateAmmoBoxTexture(0.45f, 0.55f, 0.20f));  // ROCKETS — olive
        map.put('0', generateAmmoBoxTexture(0.85f, 0.90f, 0.95f));  // SLUGS   — silver
        map.put('#', generateCameraTexture());
        map.put('%', generateGeneratorTexture());
        map.put('&', generateBioPodTexture());
        map.put('=', generateWeaponRackTexture());
        map.put('@', generateVendorTexture());
        map.put('I', generateSpecimenTankTexture());
        map.put('W', generateHoloWorkstationTexture());
        map.put('J', generateAiCoreNodeTexture());
        map.put('e', generateEnergyScorchTexture());
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
            // Transparent background
            pixmap.setColor(0f, 0f, 0f, 0f);
            pixmap.fill();
            // Cylinder gradient shading: left third darker, centre mid, right edge lighter
            for (int column = 2; column < 62; column++) {
                float red;
                float green;
                float blue;
                if (column < 22) {
                    red   = 0.08f;
                    green = 0.22f;
                    blue  = 0.08f;
                } else if (column < 44) {
                    red   = 0.14f;
                    green = 0.42f;
                    blue  = 0.14f;
                } else {
                    red   = 0.20f;
                    green = 0.52f;
                    blue  = 0.18f;
                }
                pixmap.setColor(red, green, blue, 1f);
                pixmap.fillRectangle(column, 2, 1, 60);
            }
            // Two yellow warning rings
            drawBand(pixmap, 12, 4, 0.88f, 0.76f, 0.05f);
            drawBand(pixmap, 42, 4, 0.88f, 0.76f, 0.05f);
            // Rim lip at top
            pixmap.setColor(0.12f, 0.35f, 0.12f, 1f);
            pixmap.fillRectangle(2, 2, 60, 4);
            // Radiation trefoil: three 6px filled discs at 0°/120°/240° around centre, plus centre disc
            float trefoilCenterX  = 32f;
            float trefoilCenterY  = 27f;
            float trefoilOrbitRadius = 8f;
            pixmap.setColor(0.05f, 0.05f, 0.05f, 1f);
            for (int lobeIndex = 0; lobeIndex < 3; lobeIndex++) {
                float lobeAngleRadians = (float)(lobeIndex * 2.0 * Math.PI / 3.0);
                int lobeX = (int)(trefoilCenterX + trefoilOrbitRadius * (float)Math.cos(lobeAngleRadians)) - 3;
                int lobeY = (int)(trefoilCenterY + trefoilOrbitRadius * (float)Math.sin(lobeAngleRadians)) - 3;
                pixmap.fillCircle(lobeX + 3, lobeY + 3, 3);
            }
            // Centre disc of trefoil
            pixmap.fillCircle((int)trefoilCenterX, (int)trefoilCenterY, 2);
            // Ooze drip streak from row 22 down 8 rows at column 20
            pixmap.setColor(0.14f, 0.45f, 0.08f, 1f);
            pixmap.fillRectangle(20, 22, 2, 8);
            // Base glow puddle at bottom
            pixmap.setColor(0.12f, 0.40f, 0.12f, 1f);
            pixmap.fillRectangle(4, 59, 56, 3);
        }
        return finalize(pixmap);
    }

    private static Texture generateTerminalTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        // Cabinet body: charcoal
        fillBody(pixmap, 0.18f, 0.19f, 0.22f);
        // Monitor bezel: darker frame
        pixmap.setColor(0.12f, 0.13f, 0.16f, 1f);
        pixmap.fillRectangle(8, 8, 48, 36);
        // Screen fill: cyan-teal
        pixmap.setColor(0.10f, 0.40f, 0.44f, 1f);
        pixmap.fillRectangle(12, 12, 40, 28);
        // Scanlines: every 3rd row within screen area
        pixmap.setColor(0.06f, 0.30f, 0.34f, 1f);
        for (int row = 12; row < 40; row += 3) {
            pixmap.fillRectangle(12, row, 40, 1);
        }
        // Readout glyphs: faked text bars at rows 16, 22, 28
        pixmap.setColor(0.30f, 0.80f, 0.85f, 1f);
        pixmap.fillRectangle(14, 16, 28, 2);
        pixmap.fillRectangle(14, 22, 20, 2);
        pixmap.fillRectangle(14, 28, 34, 2);
        // Cursor block
        pixmap.setColor(0.50f, 0.95f, 0.98f, 1f);
        pixmap.fillRectangle(13, 32, 4, 5);
        // Keyboard ledge
        pixmap.setColor(0.22f, 0.24f, 0.28f, 1f);
        pixmap.fillRectangle(8, 46, 48, 6);
        // Status LED: green operational
        pixmap.setColor(0.20f, 0.88f, 0.30f, 1f);
        pixmap.fillRectangle(52, 44, 4, 4);
        return finalize(pixmap);
    }

    private static Texture generateLockerTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        // Body fill
        fillBody(pixmap, 0.30f, 0.34f, 0.42f);
        // Top/left lit bevel edges
        pixmap.setColor(0.50f, 0.54f, 0.62f, 1f);
        pixmap.fillRectangle(2, 2, 60, 2);   // top bevel
        pixmap.fillRectangle(2, 2, 2, 60);   // left bevel
        // Bottom/right shadow bevel edges
        pixmap.setColor(0.18f, 0.20f, 0.26f, 1f);
        pixmap.fillRectangle(2, 60, 60, 2);  // bottom shadow
        pixmap.fillRectangle(60, 2, 2, 60);  // right shadow
        // Vertical door seam: 1px dark then 1px highlight
        pixmap.setColor(0.12f, 0.14f, 0.18f, 1f);
        pixmap.fillRectangle(31, 2, 1, 60);
        pixmap.setColor(0.44f, 0.48f, 0.56f, 1f);
        pixmap.fillRectangle(32, 2, 1, 60);
        // Latch nubs (door handles)
        pixmap.setColor(0.42f, 0.46f, 0.54f, 1f);
        pixmap.fillRectangle(25, 28, 5, 6);
        pixmap.fillRectangle(34, 28, 5, 6);
        // Louvered vent slits near top (alternating dark/mid across full width)
        for (int row = 10; row <= 18; row += 3) {
            boolean isDarkRow = ((row - 10) % 6 == 0);
            if (isDarkRow) {
                pixmap.setColor(0.12f, 0.14f, 0.18f, 1f);
            } else {
                pixmap.setColor(0.26f, 0.30f, 0.38f, 1f);
            }
            pixmap.fillRectangle(6, row, 52, 2);
        }
        // ID label plate: small metallic label bottom-left
        pixmap.setColor(0.60f, 0.62f, 0.68f, 1f);
        pixmap.fillRectangle(14, 46, 18, 8);
        // Horizontal lines inside label plate suggesting text
        pixmap.setColor(0.30f, 0.32f, 0.36f, 1f);
        pixmap.fillRectangle(16, 49, 14, 1);
        pixmap.fillRectangle(16, 52, 10, 1);
        // Red biohazard sticker bottom-right
        pixmap.setColor(0.72f, 0.12f, 0.12f, 1f);
        pixmap.fillRectangle(42, 46, 12, 10);
        // Tiny trefoil dots on sticker
        pixmap.setColor(0.05f, 0.05f, 0.05f, 1f);
        pixmap.fillRectangle(46, 49, 2, 2);
        pixmap.fillRectangle(50, 49, 2, 2);
        pixmap.fillRectangle(48, 52, 2, 2);
        // Scattered dents on body surface
        pixmap.setColor(0.18f, 0.20f, 0.26f, 1f);
        pixmap.fillRectangle(10, 36, 1, 1);
        pixmap.fillRectangle(22, 42, 1, 1);
        pixmap.fillRectangle(38, 20, 1, 1);
        pixmap.fillRectangle(55, 38, 1, 1);
        pixmap.fillRectangle(16, 58, 1, 1);
        return finalize(pixmap);
    }

    private static Texture generateCrateTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        // Box base: warm brown
        fillBody(pixmap, 0.44f, 0.30f, 0.15f);
        // Lit top-edge plank shading
        pixmap.setColor(0.55f, 0.40f, 0.22f, 1f);
        pixmap.fillRectangle(2, 2, 60, 4);
        // Shadow bottom-edge plank shading
        pixmap.setColor(0.28f, 0.18f, 0.08f, 1f);
        pixmap.fillRectangle(2, 57, 60, 5);
        // Horizontal plank seams
        pixmap.setColor(0.22f, 0.14f, 0.06f, 1f);
        pixmap.fillRectangle(2, 20, 60, 1);
        pixmap.fillRectangle(2, 40, 60, 1);
        // Vertical plank seams
        pixmap.fillRectangle(18, 2, 1, 60);
        pixmap.fillRectangle(44, 2, 1, 60);
        // Corner metal brackets (5×5) with 3×3 bolt inside each
        pixmap.setColor(0.42f, 0.44f, 0.50f, 1f);
        pixmap.fillRectangle(4,  4,  5, 5);
        pixmap.fillRectangle(55, 4,  5, 5);
        pixmap.fillRectangle(4,  55, 5, 5);
        pixmap.fillRectangle(55, 55, 5, 5);
        pixmap.setColor(0.60f, 0.62f, 0.68f, 1f);
        pixmap.fillRectangle(5,  5,  3, 3);
        pixmap.fillRectangle(56, 5,  3, 3);
        pixmap.fillRectangle(5,  56, 3, 3);
        pixmap.fillRectangle(56, 56, 3, 3);
        // UAC stencil: dot-matrix "UAC" at centre using small rects
        pixmap.setColor(0.60f, 0.55f, 0.30f, 1f);
        // U: two vertical bars + bottom connector
        pixmap.fillRectangle(22, 27, 2, 8);
        pixmap.fillRectangle(26, 27, 2, 8);
        pixmap.fillRectangle(22, 33, 6, 2);
        // A: two diagonals approximated + crossbar
        pixmap.fillRectangle(30, 27, 2, 10);
        pixmap.fillRectangle(36, 27, 2, 10);
        pixmap.fillRectangle(30, 30, 8, 2);
        // C: vertical bar + top/bottom serifs
        pixmap.fillRectangle(40, 27, 2, 10);
        pixmap.fillRectangle(40, 27, 5, 2);
        pixmap.fillRectangle(40, 35, 5, 2);
        // Damage crack: seeded diagonal across right plank area
        pixmap.setColor(0.12f, 0.08f, 0.04f, 1f);
        for (int crackStep = 0; crackStep < 10; crackStep++) {
            pixmap.fillRectangle(46 + crackStep, 22 + crackStep, 1, 1);
        }
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

    // UAC Interdimensional Rift Portal ('>') — full-height gateway standing as tall as walls.
    // Dark steel frame with bevel, swirling concentric energy rings (void-core → indigo → electric
    // blue → cyan → near-white edge flash), corner energy nodes, vertical conduit lines on the
    // frame sides, top energy emitters, and UAC hazard chevrons at the base. Beacon visible from
    // the far end of any corridor.
    private static Texture generatePortalTexture() {
        int textureWidth  = 48;
        int textureHeight = 64;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);

        // --- Steel frame base ---
        pixmap.setColor(0.17f, 0.18f, 0.22f, 1f);
        pixmap.fill();

        // Bevel highlight: top and left edges lighter
        pixmap.setColor(0.32f, 0.34f, 0.40f, 1f);
        pixmap.fillRectangle(0, 0, textureWidth, 1);
        pixmap.fillRectangle(0, 0, 1, textureHeight);

        // Bevel shadow: bottom and right edges darker
        pixmap.setColor(0.08f, 0.08f, 0.10f, 1f);
        pixmap.fillRectangle(0, textureHeight - 1, textureWidth, 1);
        pixmap.fillRectangle(textureWidth - 1, 0, 1, textureHeight);

        // --- Hazard base stripe (bottom 7 rows) — UAC yellow / black alternating 4-px columns ---
        for (int column = 0; column < textureWidth; column++) {
            boolean isYellow = (column / 4) % 2 == 0;
            pixmap.setColor(isYellow ? 0.92f : 0.06f,
                            isYellow ? 0.80f : 0.06f,
                            isYellow ? 0.02f : 0.05f,
                            1f);
            pixmap.fillRectangle(column, 57, 1, 6);
        }

        // --- Portal opening bounds (inside the steel frame) ---
        int portalLeft   = 4;
        int portalTop    = 4;
        int portalRight  = 44;  // exclusive — column 44 is the right frame pixel
        int portalBottom = 57;  // exclusive — row 57 is the hazard base
        int portalWidth  = portalRight  - portalLeft;   // 40
        int portalHeight = portalBottom - portalTop;    // 53

        float centerX = portalLeft + portalWidth  / 2f - 0.5f;   // 23.5
        float centerY = portalTop  + portalHeight / 2f - 0.5f;   // 30.5
        float halfW   = portalWidth  / 2f;                        // 20.0
        float halfH   = portalHeight / 2f;                        // 26.5

        // --- Energy field: pixel-by-pixel distance-based coloring ---
        for (int pixelRow = portalTop; pixelRow < portalBottom; pixelRow++) {
            for (int pixelColumn = portalLeft; pixelColumn < portalRight; pixelColumn++) {
                float normalizedX = (pixelColumn - centerX) / halfW;
                float normalizedY = (pixelRow    - centerY) / halfH;
                float dist = (float) Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);

                float red, green, blue;
                if (dist < 0.12f) {
                    // Void core
                    red = 0.01f; green = 0.005f; blue = 0.05f;
                } else if (dist < 0.30f) {
                    float interpolationFactor = (dist - 0.12f) / 0.18f;
                    red   = 0.01f  + interpolationFactor * 0.19f;
                    green = 0.005f + interpolationFactor * 0.045f;
                    blue  = 0.05f  + interpolationFactor * 0.60f;
                } else if (dist < 0.50f) {
                    float interpolationFactor = (dist - 0.30f) / 0.20f;
                    red   = 0.20f + interpolationFactor * (-0.12f);
                    green = 0.05f + interpolationFactor * 0.10f;
                    blue  = 0.65f + interpolationFactor * 0.25f;
                } else if (dist < 0.70f) {
                    float interpolationFactor = (dist - 0.50f) / 0.20f;
                    red   = 0.08f + interpolationFactor * (-0.03f);
                    green = 0.15f + interpolationFactor * 0.40f;
                    blue  = 0.90f + interpolationFactor * 0.08f;
                } else if (dist < 0.85f) {
                    float interpolationFactor = (dist - 0.70f) / 0.15f;
                    red   = 0.05f + interpolationFactor * 0.35f;
                    green = 0.55f + interpolationFactor * 0.30f;
                    blue  = 0.98f + interpolationFactor * 0.02f;
                } else if (dist <= 1.0f) {
                    float interpolationFactor = (dist - 0.85f) / 0.15f;
                    red   = 0.40f + interpolationFactor * 0.50f;
                    green = 0.85f + interpolationFactor * 0.12f;
                    blue  = 1.00f;
                } else {
                    // Corner pixels (outside ellipse but inside rectangle) — frame colour
                    red = 0.17f; green = 0.18f; blue = 0.22f;
                }

                // Concentric ring shimmer: cos modulation fades toward the edge
                if (dist <= 1.0f) {
                    // 4 shimmer peaks across the radius (dist 0→1 = 4 half-cycles)
                    float ringShimmer      = (float) Math.cos(dist * Math.PI * 8) * 0.5f + 0.5f;
                    float shimmerStrength  = 0.15f * (1.0f - dist);
                    float factor = 1.0f - shimmerStrength + ringShimmer * shimmerStrength * 2f;
                    red   = Math.min(1f, red   * factor);
                    green = Math.min(1f, green * factor);
                    blue  = Math.min(1f, blue  * factor);
                }

                pixmap.setColor(red, green, blue, 1f);
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }

        // --- Bright cyan energy border around the portal opening ---
        pixmap.setColor(0.55f, 0.88f, 1.00f, 1f);
        pixmap.fillRectangle(portalLeft - 1, portalTop - 1, portalWidth + 2, 1);   // top
        pixmap.fillRectangle(portalLeft - 1, portalBottom,  portalWidth + 2, 1);   // bottom
        pixmap.fillRectangle(portalLeft - 1, portalTop - 1, 1, portalHeight + 2);  // left
        pixmap.fillRectangle(portalRight,    portalTop - 1, 1, portalHeight + 2);  // right

        // --- Corner energy nodes (bright 3×3 squares at portal mouth corners) ---
        pixmap.setColor(0.95f, 0.98f, 1.00f, 1f);
        pixmap.fillRectangle(portalLeft  - 2, portalTop    - 2, 3, 3);  // top-left
        pixmap.fillRectangle(portalRight - 1, portalTop    - 2, 3, 3);  // top-right
        pixmap.fillRectangle(portalLeft  - 2, portalBottom - 1, 3, 3);  // bottom-left
        pixmap.fillRectangle(portalRight - 1, portalBottom - 1, 3, 3);  // bottom-right

        // --- Vertical energy conduit lines on inner frame edges ---
        pixmap.setColor(0.25f, 0.75f, 1.00f, 1f);
        pixmap.fillRectangle(1,              portalTop, 1, portalHeight);  // left conduit
        pixmap.fillRectangle(textureWidth - 2, portalTop, 1, portalHeight);  // right conduit

        // --- Top arch energy emitters (three discharge ports) ---
        pixmap.setColor(0.60f, 0.90f, 1.00f, 1f);
        pixmap.fillRectangle(12, 0, 5, 3);   // left emitter
        pixmap.fillRectangle(31, 0, 5, 3);   // right emitter
        pixmap.setColor(0.85f, 0.96f, 1.00f, 1f);
        pixmap.fillRectangle(21, 0, 6, 2);   // centre emitter (brighter)

        // --- Hazard divider line at the portal-base boundary ---
        pixmap.setColor(0.55f, 0.88f, 1.00f, 1f);
        pixmap.fillRectangle(0, 56, textureWidth, 1);

        // --- Energy spark accents near the portal border ---
        pixmap.setColor(1.00f, 1.00f, 1.00f, 1f);
        pixmap.drawPixel(7,  8);
        pixmap.drawPixel(40, 8);
        pixmap.drawPixel(7,  52);
        pixmap.drawPixel(40, 52);
        pixmap.setColor(0.80f, 0.95f, 1.00f, 1f);
        pixmap.drawPixel(5,  20);
        pixmap.drawPixel(42, 20);
        pixmap.drawPixel(5,  40);
        pixmap.drawPixel(42, 40);

        return finalize(pixmap);
    }

    private static Texture generateCameraTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Mounting post
        pixmap.setColor(0.14f, 0.15f, 0.17f, 1f);
        pixmap.fillRectangle(29, 40, 6, 22);
        // Horizontal bracket arm
        pixmap.setColor(0.22f, 0.23f, 0.26f, 1f);
        pixmap.fillRectangle(20, 34, 24, 6);
        // Camera housing body
        pixmap.setColor(0.18f, 0.19f, 0.22f, 1f);
        pixmap.fillRectangle(18, 16, 28, 19);
        // Housing trim border
        pixmap.setColor(0.30f, 0.32f, 0.36f, 1f);
        pixmap.fillRectangle(18, 16, 28, 1);
        pixmap.fillRectangle(18, 34, 28, 1);
        pixmap.fillRectangle(18, 16, 1, 19);
        pixmap.fillRectangle(45, 16, 1, 19);
        // Lens disc (black)
        pixmap.setColor(0.03f, 0.03f, 0.04f, 1f);
        pixmap.fillCircle(31, 25, 5);
        // Lens specular glint
        pixmap.setColor(0.40f, 0.45f, 0.50f, 1f);
        pixmap.drawPixel(28, 22);
        // Red status LED
        pixmap.setColor(0.95f, 0.20f, 0.18f, 1f);
        pixmap.fillRectangle(39, 18, 3, 3);
        return finalize(pixmap);
    }

    private static Texture generateGeneratorTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Cabinet body
        pixmap.setColor(0.22f, 0.24f, 0.27f, 1f);
        pixmap.fillRectangle(8, 4, 48, 56);
        // Side cooling fins (left)
        pixmap.setColor(0.16f, 0.17f, 0.19f, 1f);
        for (int finRow = 14; finRow <= 46; finRow += 10) {
            pixmap.fillRectangle(4, finRow, 4, 3);
        }
        // Side cooling fins (right)
        for (int finRow = 14; finRow <= 46; finRow += 10) {
            pixmap.fillRectangle(56, finRow, 4, 3);
        }
        // Base chevron stripe — alternating yellow/dark diagonal bands
        for (int column = 8; column < 56; column++) {
            for (int row = 51; row < 60; row++) {
                boolean isYellow = ((row + column) % 8) < 4;
                if (isYellow) {
                    pixmap.setColor(0.85f, 0.72f, 0.18f, 1f);
                } else {
                    pixmap.setColor(0.14f, 0.15f, 0.17f, 1f);
                }
                pixmap.drawPixel(column, row);
            }
        }
        // Core window frame
        pixmap.setColor(0.10f, 0.11f, 0.12f, 1f);
        pixmap.fillRectangle(16, 16, 32, 26);
        // Core glow: green
        pixmap.setColor(0.30f, 0.85f, 0.40f, 1f);
        pixmap.fillRectangle(18, 18, 28, 22);
        // Core bright hot center
        pixmap.setColor(0.75f, 1.00f, 0.80f, 1f);
        pixmap.fillRectangle(26, 24, 12, 10);
        // Top conduit pipes
        pixmap.setColor(0.30f, 0.31f, 0.34f, 1f);
        pixmap.fillRectangle(18, 0, 8, 6);
        pixmap.fillRectangle(38, 0, 8, 6);
        return finalize(pixmap);
    }

    private static Texture generateBioPodTexture() {
        Pixmap pixmap = new Pixmap(64, 96, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Bottom base
        pixmap.setColor(0.26f, 0.28f, 0.32f, 1f);
        pixmap.fillRectangle(12, 86, 40, 9);
        // Top dome cap
        pixmap.fillRectangle(12, 2, 40, 9);
        // Inner cap ring detail
        pixmap.setColor(0.36f, 0.38f, 0.44f, 1f);
        pixmap.fillRectangle(20, 3, 24, 4);
        // Cyan readout strip at base of cap
        pixmap.setColor(0.30f, 0.80f, 0.85f, 1f);
        pixmap.fillRectangle(16, 11, 32, 4);
        // Fluid cylinder: greenish fluid
        pixmap.setColor(0.20f, 0.55f, 0.42f, 1f);
        pixmap.fillRectangle(16, 15, 32, 71);
        // Glass sheen streak: diagonal 1px line
        pixmap.setColor(0.75f, 0.92f, 0.88f, 1f);
        for (int sheen = 0; sheen < 70; sheen++) {
            int sheenColumn = 17 + sheen / 10;
            int sheenRow    = 15 + sheen;
            if (sheenColumn < 47 && sheenRow < 85) {
                pixmap.drawPixel(sheenColumn, sheenRow);
            }
        }
        // Specimen silhouette: curled organic blob
        pixmap.setColor(0.10f, 0.22f, 0.16f, 1f);
        pixmap.fillCircle(32, 67, 10);
        // Ascending bubbles
        pixmap.setColor(0.70f, 0.90f, 0.82f, 1f);
        int[] bubbleColumns = { 24, 38, 30, 20, 44 };
        int[] bubbleRows    = { 68, 55, 40, 30, 22 };
        for (int bubbleIndex = 0; bubbleIndex < 5; bubbleIndex++) {
            pixmap.fillRectangle(bubbleColumns[bubbleIndex], bubbleRows[bubbleIndex], 2, 2);
        }
        return finalize(pixmap);
    }

    private static Texture generateWeaponRackTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Rack frame: two vertical bars and two horizontal bars
        pixmap.setColor(0.24f, 0.26f, 0.29f, 1f);
        pixmap.fillRectangle(10, 8, 4, 44);
        pixmap.fillRectangle(50, 8, 4, 44);
        pixmap.fillRectangle(10, 8, 44, 3);
        pixmap.fillRectangle(10, 49, 44, 3);
        // Weapon slot 1 — wood stock + metal barrel
        pixmap.setColor(0.34f, 0.22f, 0.12f, 1f);
        pixmap.fillRectangle(16, 17, 14, 8);
        pixmap.setColor(0.40f, 0.42f, 0.46f, 1f);
        pixmap.fillRectangle(16, 17, 14, 1);  // top highlight
        pixmap.setColor(0.10f, 0.11f, 0.12f, 1f);
        pixmap.fillRectangle(30, 19, 18, 3);
        // Weapon slot 2 — wood stock + metal barrel
        pixmap.setColor(0.34f, 0.22f, 0.12f, 1f);
        pixmap.fillRectangle(16, 29, 14, 8);
        pixmap.setColor(0.40f, 0.42f, 0.46f, 1f);
        pixmap.fillRectangle(16, 29, 14, 1);
        pixmap.setColor(0.10f, 0.11f, 0.12f, 1f);
        pixmap.fillRectangle(30, 31, 18, 3);
        // Empty slot 3 — faint outline only
        pixmap.setColor(0.18f, 0.20f, 0.23f, 1f);
        pixmap.fillRectangle(16, 40, 30, 6);
        // Ammo crate at base
        pixmap.setColor(0.30f, 0.34f, 0.22f, 1f);
        pixmap.fillRectangle(14, 52, 22, 10);
        // Ammo stencil lettering — simple dot-rect "AMMO"
        pixmap.setColor(0.80f, 0.78f, 0.40f, 1f);
        pixmap.fillRectangle(16, 55, 3, 5);
        pixmap.fillRectangle(20, 55, 3, 5);
        pixmap.fillRectangle(24, 55, 3, 5);
        pixmap.fillRectangle(28, 55, 3, 5);
        return finalize(pixmap);
    }

    private static Texture generateVendorTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Cabinet body: plum-grey
        pixmap.setColor(0.26f, 0.22f, 0.30f, 1f);
        pixmap.fillRectangle(6, 2, 52, 60);
        // Front glass panel: dark
        pixmap.setColor(0.10f, 0.14f, 0.20f, 1f);
        pixmap.fillRectangle(10, 8, 34, 40);
        // Item rows behind glass — alternating red/blue 6×5 squares
        for (int itemRow = 0; itemRow < 3; itemRow++) {
            for (int itemColumn = 0; itemColumn < 3; itemColumn++) {
                boolean isRed = (itemRow + itemColumn) % 2 == 0;
                if (isRed) {
                    pixmap.setColor(0.85f, 0.30f, 0.30f, 1f);
                } else {
                    pixmap.setColor(0.30f, 0.55f, 0.85f, 1f);
                }
                pixmap.fillRectangle(12 + itemColumn * 10, 12 + itemRow * 10, 7, 7);
            }
        }
        // Keypad area: dark base
        pixmap.setColor(0.10f, 0.25f, 0.14f, 1f);
        pixmap.fillRectangle(46, 12, 10, 22);
        // Keypad buttons: 2×3 grid of small glowing squares
        pixmap.setColor(0.40f, 0.85f, 0.55f, 1f);
        for (int buttonRow = 0; buttonRow < 3; buttonRow++) {
            for (int buttonColumn = 0; buttonColumn < 2; buttonColumn++) {
                pixmap.fillRectangle(47 + buttonColumn * 4, 14 + buttonRow * 6, 3, 3);
            }
        }
        // UAC stencil label strip
        pixmap.setColor(0.22f, 0.18f, 0.26f, 1f);
        pixmap.fillRectangle(10, 50, 34, 8);
        pixmap.setColor(0.85f, 0.82f, 0.50f, 1f);
        // U: two vertical bars + bottom
        pixmap.fillRectangle(13, 52, 2, 5);
        pixmap.fillRectangle(17, 52, 2, 5);
        pixmap.fillRectangle(13, 56, 6, 1);
        // A: two verticals + crossbar
        pixmap.fillRectangle(21, 52, 2, 5);
        pixmap.fillRectangle(25, 52, 2, 5);
        pixmap.fillRectangle(21, 54, 6, 1);
        // C: left bar + top/bottom serifs
        pixmap.fillRectangle(29, 52, 2, 5);
        pixmap.fillRectangle(29, 52, 5, 1);
        pixmap.fillRectangle(29, 56, 5, 1);
        // Dispenser tray at base
        pixmap.setColor(0.14f, 0.15f, 0.17f, 1f);
        pixmap.fillRectangle(10, 60, 46, 4);
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

    private static Texture generateSpecimenTankTexture() {
        Pixmap pixmap = new Pixmap(64, 128, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Steel base and top caps
        pixmap.setColor(0.20f, 0.22f, 0.26f, 1f);
        pixmap.fillRectangle(8, 108, 48, 20);
        pixmap.fillRectangle(8, 0,   48, 10);
        // Fluid body — cylindrical sheen via column-by-column tinting
        for (int column = 10; column < 54; column++) {
            float fraction = (float)(column - 10) / 43f;
            float rim = (fraction < 0.20f || fraction > 0.80f) ? 0f : 1f;
            float fluidRed   = 0.12f + 0.18f * rim;
            float fluidGreen = 0.45f + 0.20f * rim;
            float fluidBlue  = 0.55f + 0.40f * rim;
            pixmap.setColor(fluidRed, fluidGreen, fluidBlue, 1f);
            pixmap.fillRectangle(column, 10, 1, 98);
        }
        // Specimen silhouette — dark oval centre
        pixmap.setColor(0.06f, 0.10f, 0.08f, 1f);
        pixmap.fillRectangle(20, 40, 24, 46);
        // Glass highlight streak (left)
        pixmap.setColor(0.80f, 0.92f, 0.95f, 1f);
        pixmap.fillRectangle(11, 12, 3, 94);
        // Rising bubble dots
        pixmap.setColor(0.80f, 0.95f, 1.00f, 1f);
        for (int bubbleRow = 20; bubbleRow < 100; bubbleRow += 14) {
            pixmap.fillRectangle(32, bubbleRow,     2, 2);
            pixmap.fillRectangle(40, bubbleRow + 7, 2, 2);
        }
        return finalize(pixmap);
    }

    private static Texture generateHoloWorkstationTexture() {
        Pixmap pixmap = new Pixmap(80, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Console body (lower portion)
        pixmap.setColor(0.18f, 0.20f, 0.24f, 1f);
        pixmap.fillRectangle(4, 44, 72, 18);
        // Cyan control strip
        pixmap.setColor(0.35f, 0.95f, 1.00f, 1f);
        pixmap.fillRectangle(8, 42, 64, 3);
        // Hologram projection cone — fanning lines from console top upward
        for (int column = 20; column <= 60; column += 5) {
            float brightness = 1f - Math.abs(column - 40) / 25f;
            pixmap.setColor(0.55f * brightness, 0.95f * brightness, 1.00f * brightness, 1f);
            int topY = (int)(4 + (1f - brightness) * 18);
            pixmap.fillRectangle(column, topY, 2, 42 - topY);
        }
        // Wireframe ring at hologram apex
        pixmap.setColor(0.35f, 0.95f, 1.00f, 1f);
        pixmap.fillRectangle(26, 6,  28, 2);
        pixmap.fillRectangle(26, 18, 28, 2);
        pixmap.fillRectangle(26, 6,  2,  14);
        pixmap.fillRectangle(52, 6,  2,  14);
        return finalize(pixmap);
    }

    private static Texture generateAiCoreNodeTexture() {
        Pixmap pixmap = new Pixmap(48, 128, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Heatsink frame
        pixmap.setColor(0.12f, 0.13f, 0.16f, 1f);
        pixmap.fillRectangle(2, 2, 44, 124);
        // 5 stacked processing slabs
        int slabCount  = 5;
        int slabHeight = 20;
        int slabGap    = 4;
        for (int slabIndex = 0; slabIndex < slabCount; slabIndex++) {
            int slabTop = 6 + slabIndex * (slabHeight + slabGap);
            // Slab body
            pixmap.setColor(0.20f, 0.55f, 0.70f, 1f);
            pixmap.fillRectangle(6, slabTop, 36, slabHeight);
            // Central light seam
            pixmap.setColor(0.60f, 0.95f, 1.00f, 1f);
            pixmap.fillRectangle(22, slabTop, 4, slabHeight);
            // Status LEDs — amber on slab 1, cyan elsewhere
            if (slabIndex == 1) {
                pixmap.setColor(1.00f, 0.65f, 0.15f, 1f);
            } else {
                pixmap.setColor(0.35f, 0.95f, 1.00f, 1f);
            }
            pixmap.fillRectangle(8,  slabTop + 8, 4, 4);
            pixmap.fillRectangle(36, slabTop + 8, 4, 4);
        }
        return finalize(pixmap);
    }

    /** Gold star silhouette — marks a weapon pickup placed by the level generator. */
    private static Texture generateWeaponPickupTexture() {
        Pixmap pixmap = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Outer gold ring
        pixmap.setColor(0.92f, 0.75f, 0.10f, 1f);
        pixmap.fillCircle(16, 16, 14);
        // Inner dark recess
        pixmap.setColor(0.50f, 0.35f, 0.05f, 1f);
        pixmap.fillCircle(16, 16, 9);
        // Bright centre highlight
        pixmap.setColor(1.00f, 0.95f, 0.60f, 1f);
        pixmap.fillCircle(16, 16, 5);
        return finalize(pixmap);
    }

    private static Texture generateEnergyScorchTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Outer desaturated scorch ring
        pixmap.setColor(0.20f, 0.18f, 0.16f, 1f);
        pixmap.fillRectangle(6, 6, 52, 52);
        // Blackened centre
        pixmap.setColor(0.05f, 0.05f, 0.06f, 1f);
        pixmap.fillRectangle(14, 14, 36, 36);
        // Radiating electric-blue arc filaments from centre
        pixmap.setColor(0.40f, 0.75f, 1.00f, 1f);
        for (int step = 0; step < 20; step++) {
            pixmap.drawPixel(32 + step,          32 - step);
            pixmap.drawPixel(32 - step,          32 - step);
            pixmap.drawPixel(32 + step,          32 + step / 2);
            pixmap.drawPixel(32 - step,          32 + step / 2);
        }
        // Hot white flecks at burn centre
        pixmap.setColor(0.90f, 0.96f, 1.00f, 1f);
        pixmap.fillRectangle(30, 30, 4, 4);
        pixmap.fillRectangle(28, 28, 2, 2);
        pixmap.fillRectangle(34, 34, 2, 2);
        return finalize(pixmap);
    }

    /** Generates a flat ammo-box pickup sprite tinted with the given colour. */
    private static Texture generateAmmoBoxTexture(float red, float green, float blue) {
        Pixmap pixmap = new Pixmap(48, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        // Box body — slightly darker base tint
        pixmap.setColor(red * 0.55f, green * 0.55f, blue * 0.55f, 1f);
        pixmap.fillRectangle(2, 6, 44, 20);
        // Top highlight edge
        pixmap.setColor(Math.min(1f, red + 0.25f), Math.min(1f, green + 0.25f), Math.min(1f, blue + 0.25f), 1f);
        pixmap.fillRectangle(2, 6, 44, 3);
        // Stencil warning stripes — dark vertical bars across the body
        pixmap.setColor(0.05f, 0.05f, 0.05f, 1f);
        for (int barOffset = 0; barOffset < 44; barOffset += 8) {
            pixmap.fillRectangle(2 + barOffset, 9, 4, 14);
        }
        // Lid seam line
        pixmap.setColor(0.10f, 0.10f, 0.10f, 1f);
        pixmap.fillRectangle(2, 16, 44, 2);
        // Latch dot — bright accent at centre
        pixmap.setColor(Math.min(1f, red + 0.30f), Math.min(1f, green + 0.30f), Math.min(1f, blue + 0.30f), 1f);
        pixmap.fillRectangle(22, 14, 4, 4);
        return finalize(pixmap);
    }

}

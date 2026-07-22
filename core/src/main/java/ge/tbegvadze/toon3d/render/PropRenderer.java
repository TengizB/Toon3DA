package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.WeaponTier;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.render.tilesetgfx.EnvironmentTextureSet;
import ge.tbegvadze.toon3d.render.tilesetgfx.TextureGeneratorRegistry;
import ge.tbegvadze.toon3d.tileset.EnvironmentSpriteRegistry;
import ge.tbegvadze.toon3d.tileset.TilesetRegistries;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static ge.tbegvadze.toon3d.util.Constants.*;
import static ge.tbegvadze.toon3d.util.RenderConstants.*;
import static ge.tbegvadze.toon3d.util.ItemConstants.*;
import static ge.tbegvadze.toon3d.util.WeaponConstants.*;

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

    // TILESET SYSTEM (order-7): the ENVIRONMENT prop/decal art (SOLID_PROP + FLOOR_DECAL categories) is
    // no longer owned or height-tabled here. A prop's texture is resolved symbol -> sprite id (this level's
    // LevelPalette) -> Texture (the per-level EnvironmentTextureSet), and its height comes from the sprite
    // DEFINITION (EnvironmentSpriteRegistry.heightMultiplier(), copied literal-for-literal from the same
    // RenderConstants this switch used, so the look is unchanged). This switch now covers only the FIXED
    // gameplay props — pickups, ammo, portal, and the animated hazards — which are not tileset sprites and
    // keep their dedicated handling. See docs/environment-tileset-system.txt (section 7 / order-7).
    // Per-prop height multiplier relative to a full wall stripe (WORLD_HEIGHT / depth); keeps shorter
    // objects (pickups, ammo) below full wall height.
    private float propHeightMultiplier(char propChar) {
        String spriteId = level.getPalette().spriteIdOf(propChar);
        if (spriteId != null) {
            // Environment prop/decal — height from the sprite definition (SOLID_PROP + FLOOR_DECAL).
            return spriteRegistry.get(spriteId).heightMultiplier();
        }
        return fixedPropHeightMultiplier(propChar);
    }

    /** Height fraction for the FIXED gameplay props (pickups, ammo, portal, hazards) — not tileset art. */
    private static float fixedPropHeightMultiplier(char propChar) {
        switch (propChar) {
            case 'r': return KEYCARD_PICKUP_SPRITE_HEIGHT;   // red keycard pickup
            case 'y': return KEYCARD_PICKUP_SPRITE_HEIGHT;   // yellow keycard pickup
            case 'b': return KEYCARD_PICKUP_SPRITE_HEIGHT;   // blue keycard pickup
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
            case 'i': return PROP_HEIGHT_HAZARD_FIRE;    // fire hazard tile
            case 'q': return PROP_HEIGHT_HAZARD_TOXIC;   // toxic hazard pool
            default:  return 0.70f;
        }
    }

    private final Level                   level;
    private final WallRenderer            wallRenderer;
    private final List<PropPlacement>     propPlacements;
    // FIXED-gameplay prop textures only (pickups, ammo, portal) — the ENVIRONMENT prop/decal art now lives
    // in the per-level EnvironmentTextureSet (order-7), not here. Owned + disposed by this renderer.
    private final Map<Character, Texture> textures;
    // The shared environment-sprite catalog (order-1), read for environment prop/decal heightMultipliers.
    // Bootstrapped in World.create() before any PropRenderer is built.
    private final EnvironmentSpriteRegistry spriteRegistry = TilesetRegistries.sprites();
    // Animated hazard frames — fire ('i') and toxic ('q') cycle through these instead of using a
    // single static entry in {@link #textures}. Built once at construction, disposed in dispose().
    private final Texture[]               hazardFireFrames;
    private final Texture[]               hazardToxicFrames;
    // Per-weapon ground billboard textures — keyed by ItemType, built once at startup.
    private final Map<ItemType, Texture>  weaponPickupTextures;
    private final Texture                 genericWeaponFallbackTexture;
    // Radial gradient used as the additive glow halo behind tier-colored weapon pickups.
    private final Texture                 weaponPickupGlowTexture;
    // Per-tier credit chip textures — procedurally generated, keyed by ItemType.
    private final Map<ItemType, Texture>  creditChipTextures;
    private final SpriteBatch             batch;

    // Weapon ground items (placed by LevelGenerator, consumed on player pickup).
    // Injected by World after level build; never null — starts empty.
    private List<GroundItem> groundItems = Collections.emptyList();

    // Tier tinting map — maps weapon ItemType to its WeaponTier for billboard tinting.
    // Injected by World after arsenal setup; defaults to empty (no tint).
    private Map<ItemType, WeaponTier> weaponTierMap = Collections.emptyMap();

    // Dynamically-added prop placements (enemy ammo/corpse drops placed after level load).
    // Each entry is valid until the level tile no longer matches the stored propChar.
    private final List<PropPlacement> dynamicPropPlacements = new ArrayList<>();

    // Pre-allocated scratch buffers for depth-sorting — sized to total prop count.
    private final int[]   sortedIndices;
    private final float[] sortedDepths;

    // Per-column depth written during render — lets EnemyRenderer occlude against props.
    private final float[] propSpriteZBuffer;
    // Per-column vertical bounds of the nearest occluding prop — used by EnemyRenderer to
    // draw enemy segments above and below a small prop (e.g. ammo) rather than skipping the
    // entire column height when only the center is blocked.
    private final float[] propSpriteColumnBottom;
    private final float[] propSpriteColumnTop;

    private float playerWorldX      = 0f;
    private float playerWorldY      = 0f;
    private float directionX        = 1f;
    private float directionY        = 0f;
    private float planeX            = 0f;
    private float planeY            = 1f;
    private float alertPulse        = 0f;
    private float lightingTimeSeconds = 0f;

    // TILESET SYSTEM (order-6): the per-level texture supply realized from this level's palette. Set
    // each level by World via setEnvironmentTextureSet(); order-6 only wires the plumbing + dispose path —
    // order-7 flips the prop draw to read prop/decal textures FROM this set instead of the constructor-
    // built {@link #textures} map. Never disposed here: the set is owned/disposed by World.
    private EnvironmentTextureSet environmentTextureSet;

    public PropRenderer(Level level, WallRenderer wallRenderer) {
        this.level          = level;
        this.wallRenderer   = wallRenderer;
        this.propPlacements = buildPropPlacements(level);
        int propCount       = propPlacements.size();
        this.sortedIndices          = new int[propCount];
        this.sortedDepths           = new float[propCount];
        this.propSpriteZBuffer      = new float[WALL_PROJECTION_SCREEN_WIDTH];
        this.propSpriteColumnBottom = new float[WALL_PROJECTION_SCREEN_WIDTH];
        this.propSpriteColumnTop    = new float[WALL_PROJECTION_SCREEN_WIDTH];
        // SpriteBatch capacity = one sprite per screen column (1-pixel-wide column draws).
        this.batch                        = new SpriteBatch(WALL_PROJECTION_SCREEN_WIDTH);
        this.textures                     = buildTextures();
        this.weaponPickupTextures         = buildWeaponPickupTextures();
        this.genericWeaponFallbackTexture = generateWeaponPickupTexture();
        this.weaponPickupGlowTexture      = generateGlowTexture();
        this.creditChipTextures           = buildCreditChipTextures();
        this.hazardFireFrames             = buildHazardFireFrames();
        this.hazardToxicFrames            = buildHazardToxicFrames();
    }

    private static Texture[] buildHazardFireFrames() {
        Texture[] frames = new Texture[HAZARD_ANIMATION_FRAME_COUNT];
        for (int frameIndex = 0; frameIndex < frames.length; frameIndex++) {
            frames[frameIndex] = generateHazardFireFrame(frameIndex, frames.length);
        }
        return frames;
    }

    private static Texture[] buildHazardToxicFrames() {
        Texture[] frames = new Texture[HAZARD_ANIMATION_FRAME_COUNT];
        for (int frameIndex = 0; frameIndex < frames.length; frameIndex++) {
            frames[frameIndex] = generateHazardToxicFrame(frameIndex, frames.length);
        }
        return frames;
    }

    /**
     * Resolves the texture to draw for a prop tile. Animated hazards ('i' fire, 'q' toxic) return the
     * current cycling frame (with a per-tile phase offset). ENVIRONMENT props/decals (SOLID_PROP +
     * FLOOR_DECAL) resolve symbol -> sprite id (this level's LevelPalette) -> Texture (the per-level
     * EnvironmentTextureSet) — this is the order-7 flip that lets a level's palette change what its props
     * look like. FIXED gameplay props (pickups, ammo, portal) return their own owned texture. Returns null
     * when the char has no texture.
     */
    private Texture resolvePropTexture(char propChar, int tileColumn, int tileRow) {
        if (propChar == 'i') {
            return hazardFireFrames[hazardFrameIndex(tileColumn, tileRow, hazardFireFrames.length)];
        }
        if (propChar == 'q') {
            return hazardToxicFrames[hazardFrameIndex(tileColumn, tileRow, hazardToxicFrames.length)];
        }
        String spriteId = level.getPalette().spriteIdOf(propChar);
        if (spriteId != null) {
            return environmentTextureSet.textureFor(spriteId);
        }
        return textures.get(propChar);
    }

    /**
     * Frame index for an animated hazard tile at the current animation time. A per-tile phase
     * offset (from the tile coordinates) desynchronises neighbouring tiles so a field of fire does
     * not pulse in lockstep. Uses {@link Math#floorMod} so the result is always in [0, frameCount).
     */
    private int hazardFrameIndex(int tileColumn, int tileRow, int frameCount) {
        int phaseOffset = tileColumn * 5 + tileRow * 11;
        int baseFrame   = (int) (lightingTimeSeconds * HAZARD_ANIMATION_FPS);
        return Math.floorMod(baseFrame + phaseOffset, frameCount);
    }

    /** Replaces the ground item list; called by World after each level build. */
    public void setGroundItems(List<GroundItem> items) {
        this.groundItems = (items != null) ? items : Collections.emptyList();
    }

    /** Supplies the weapon tier lookup; called by World after arsenal setup. */
    public void setWeaponTierMap(Map<ItemType, WeaponTier> tierMap) {
        this.weaponTierMap = (tierMap != null) ? tierMap : Collections.emptyMap();
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

    /**
     * Supplies this level's realized texture set (symbol/sprite-reuse order-6). Called once per level by
     * World, right after the set is built from the level's palette. order-6 only stores the reference; the
     * prop draw still reads the constructor-built {@link #textures} map until order-7 rewires it to pull
     * prop/decal textures from this set.
     */
    public void setEnvironmentTextureSet(EnvironmentTextureSet textureSet) {
        this.environmentTextureSet = textureSet;
    }

    /** The current per-level texture set, or null before the first level is wired (order-6 plumbing). */
    public EnvironmentTextureSet getEnvironmentTextureSet() {
        return environmentTextureSet;
    }

    /** Returns the per-column depth buffer written during render so EnemyRenderer can occlude against props. */
    public float[] getPropSpriteZBuffer() {
        return propSpriteZBuffer;
    }

    /** Returns the per-column screen-bottom of the nearest occluding prop, parallel to {@link #getPropSpriteZBuffer()}. */
    public float[] getPropSpriteColumnBottom() {
        return propSpriteColumnBottom;
    }

    /** Returns the per-column screen-top of the nearest occluding prop, parallel to {@link #getPropSpriteZBuffer()}. */
    public float[] getPropSpriteColumnTop() {
        return propSpriteColumnTop;
    }

    @Override
    public void render(OrthographicCamera camera) {
        int propCount = propPlacements.size();
        // Reset z-buffer every frame so stale depths from the previous frame don't occlude.
        java.util.Arrays.fill(propSpriteZBuffer, Float.MAX_VALUE);
        java.util.Arrays.fill(propSpriteColumnBottom, 0f);
        java.util.Arrays.fill(propSpriteColumnTop,    0f);

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

            Texture texture = resolvePropTexture(prop.propChar, prop.tileColumn, prop.tileRow);
            if (texture == null) continue;

            float tileOffsetX = (prop.worldCenterX - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (prop.worldCenterY - playerWorldY) / CELL_SIZE;

            float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                    tileOffsetX, tileOffsetY, directionX, directionY,
                    planeX, planeY, WALL_PROJECTION_SCREEN_WIDTH);

            float heightMultiplier   = propHeightMultiplier(prop.propChar);
            float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, depth);
            float spriteScreenHeight = fullWallLineHeight * heightMultiplier;
            float spriteScreenWidth;
            if (Level.isHazardDecal(prop.propChar)) {
                // Hazards span the full tile footprint, decoupled from the source texture aspect.
                spriteScreenWidth = fullWallLineHeight * HAZARD_CELL_WIDTH_FILL;
            } else {
                float aspectRatio = (float) texture.getWidth() / texture.getHeight();
                spriteScreenWidth = spriteScreenHeight * aspectRatio;
            }

            int leftScreenColumn  = (int)(screenCenterColumn - spriteScreenWidth / 2f);
            int rightScreenColumn = (int)(screenCenterColumn + spriteScreenWidth / 2f);
            int columnSpan        = rightScreenColumn - leftScreenColumn;
            if (columnSpan <= 0) continue;

            float drawBottom;
            if (Level.isMedicalPickup(prop.propChar)
                    || Level.isArmourPickup(prop.propChar)
                    || Level.isAmmoPickup(prop.propChar)
                    || Level.isKeycardPickup(prop.propChar)) {
                float bobPhase  = prop.propChar * PICKUP_ITEM_BOB_PHASE_STEP;
                float bobOffset = GameMath.pickupBobOffset(lightingTimeSeconds,
                        PICKUP_ITEM_BOB_SPEED, PICKUP_ITEM_BOB_AMPLITUDE_FRACTION,
                        bobPhase, spriteScreenHeight);
                drawBottom = GameMath.spriteDrawBottomCentered(WALL_PROJECTION_SCREEN_HEIGHT, spriteScreenHeight)
                        + bobOffset
                        - PICKUP_ITEM_GROUND_OFFSET_FRACTION * spriteScreenHeight;
            } else if (Level.isStairsDown(prop.propChar)) {
                drawBottom = GameMath.spriteDrawBottomCentered(WALL_PROJECTION_SCREEN_HEIGHT, spriteScreenHeight);
            } else {
                drawBottom = WALL_PROJECTION_SCREEN_HEIGHT / 2f - fullWallLineHeight / 2f;
            }
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

            // Keycards float with a pulsing additive aura tinted to the door they unlock,
            // drawn behind the card (mirrors the weapon/credit pickup glow pipeline).
            if (Level.isKeycardPickup(prop.propChar)) {
                float keycardAuraRed, keycardAuraGreen, keycardAuraBlue;
                switch (prop.propChar) {
                    case 'r':  keycardAuraRed = KEYCARD_AURA_RED_R;    keycardAuraGreen = KEYCARD_AURA_RED_G;    keycardAuraBlue = KEYCARD_AURA_RED_B;    break;
                    case 'y':  keycardAuraRed = KEYCARD_AURA_YELLOW_R; keycardAuraGreen = KEYCARD_AURA_YELLOW_G; keycardAuraBlue = KEYCARD_AURA_YELLOW_B; break;
                    default:   keycardAuraRed = KEYCARD_AURA_BLUE_R;   keycardAuraGreen = KEYCARD_AURA_BLUE_G;   keycardAuraBlue = KEYCARD_AURA_BLUE_B;   break;
                }
                float auraPhase  = prop.propChar * PICKUP_ITEM_BOB_PHASE_STEP;
                float auraAlpha  = Math.max(0f, KEYCARD_AURA_BASE_ALPHA
                        + KEYCARD_AURA_PULSE_AMPLITUDE * MathUtils.sin(lightingTimeSeconds * KEYCARD_AURA_PULSE_SPEED + auraPhase));
                float auraHeight  = spriteScreenHeight * KEYCARD_AURA_SIZE_MULTIPLIER;
                float cardCenterY = drawBottom + spriteScreenHeight / 2f;
                int   auraLeft    = (int)(screenCenterColumn - auraHeight / 2f);
                int   auraRight   = (int)(screenCenterColumn + auraHeight / 2f);
                int   auraSpan    = auraRight - auraLeft;
                float auraDrawBottom = cardCenterY - auraHeight / 2f;
                float auraDrawTop    = auraDrawBottom + auraHeight;
                float auraClampBottom = Math.max(0f, auraDrawBottom);
                float auraClampTop    = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, auraDrawTop);
                if (auraSpan > 0 && auraClampTop > auraClampBottom) {
                    int auraGlowHeight   = weaponPickupGlowTexture.getHeight();
                    int auraGlowWidth    = weaponPickupGlowTexture.getWidth();
                    int auraTexSrcY      = GameMath.wallTextureClipSrcY(
                            auraDrawTop, WALL_PROJECTION_SCREEN_HEIGHT, auraHeight, auraGlowHeight);
                    int auraTexSrcHeight = GameMath.wallTextureClipSrcHeight(
                            auraClampTop, auraClampBottom, auraHeight, auraGlowHeight);
                    auraTexSrcHeight = Math.min(auraTexSrcHeight, auraGlowHeight - auraTexSrcY);
                    auraTexSrcHeight = Math.max(1, auraTexSrcHeight);
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                    batch.setColor(keycardAuraRed * shade, keycardAuraGreen * shade, keycardAuraBlue * shade, auraAlpha);
                    int auraFirstColumn = Math.max(0, auraLeft);
                    int auraLastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, auraRight);
                    for (int screenColumn = auraFirstColumn; screenColumn <= auraLastColumn; screenColumn++) {
                        if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
                        if (depth >= propSpriteZBuffer[screenColumn]) continue;
                        int auraTexSrcX = (screenColumn - auraLeft) * auraGlowWidth / auraSpan;
                        auraTexSrcX = MathUtils.clamp(auraTexSrcX, 0, auraGlowWidth - 1);
                        batch.draw(weaponPickupGlowTexture,
                                   screenColumn * WALL_COLUMN_WIDTH, auraClampBottom,
                                   WALL_COLUMN_WIDTH, auraClampTop - auraClampBottom,
                                   auraTexSrcX, auraTexSrcY, 1, auraTexSrcHeight,
                                   false, false);
                    }
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                }
            }

            float spriteRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
            float spriteGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            float spriteBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            batch.setColor(spriteRed, spriteGreen, spriteBlue, 1f);

            // Per-column draw loop with z-buffer occlusion test.
            ColumnOpacityMask opacityMask = COLUMN_OPACITY.get(texture);
            int firstColumn = Math.max(0, leftScreenColumn);
            int lastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, rightScreenColumn);
            for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
                // Skip columns where a wall (or closer prop) is in front.
                if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
                // Skip columns where a nearer prop was already drawn (painter's order, far→near,
                // so a shallower depth here means a previous iteration already wrote it).
                if (depth >= propSpriteZBuffer[screenColumn]) continue;

                int texSrcX = (screenColumn - leftScreenColumn) * textureWidth / columnSpan;
                texSrcX = MathUtils.clamp(texSrcX, 0, textureWidth - 1);

                // Elevated billboards (solid props + pickup items + stairs) write to the z-buffer
                // so EnemyRenderer knows to draw enemies behind them when they are farther away.
                // Only opaque columns occlude; transparent sprite columns leave the enemy visible.
                // Pure floor stains (corpse, blood, oil, energy scorch, dropped-shotgun decal)
                // are excluded — enemies visually walk over those flat decals.
                recordPropOccluderColumn(screenColumn, depth, prop.propChar, opacityMask,
                        texSrcX, texSrcY, texSrcHeight, clampedBottom, clampedTop);

                batch.draw(texture,
                           screenColumn * WALL_COLUMN_WIDTH, clampedBottom,
                           WALL_COLUMN_WIDTH, clampedTop - clampedBottom,
                           texSrcX, texSrcY, 1, texSrcHeight,
                           false, false);
            }
        }

        // Ground items: weapon pickups and credit chips spawned by the level generator.
        // These are entity-side objects (not tile chars) so they need a separate render pass.
        for (GroundItem groundItem : groundItems) {
            float itemWorldCenterX = groundItem.tileColumn * CELL_SIZE + CELL_SIZE / 2f;
            float itemWorldCenterY = groundItem.tileRow    * CELL_SIZE + CELL_SIZE / 2f;
            float tileOffsetX = (itemWorldCenterX - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (itemWorldCenterY - playerWorldY) / CELL_SIZE;
            float depth = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
            if (depth <= 0f) continue;
            if (depth > MAX_PROP_DRAW_DISTANCE_TILES) continue;
            float renderDepth = Math.max(depth, PROP_BEHIND_PLAYER_EPSILON_TILES);

            float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                    tileOffsetX, tileOffsetY, directionX, directionY,
                    planeX, planeY, WALL_PROJECTION_SCREEN_WIDTH);

            float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, renderDepth);
            ItemType itemType        = groundItem.stack.getType();

            float tileBrightness = level.getTileBrightness(groundItem.tileColumn, groundItem.tileRow, lightingTimeSeconds);
            float shade          = Math.min(GameMath.wallShade(renderDepth, WALL_SHADING_FALLOFF) * tileBrightness,
                                            MAX_LIGHTING_SHADE);

            // ── Credit chip path ──────────────────────────────────────────────────
            if (isCreditChip(itemType)) {
                float chipScreenHeight = fullWallLineHeight * CREDIT_PICKUP_HEIGHT_FRACTION;
                int   chipLeft  = (int)(screenCenterColumn - chipScreenHeight / 2f);
                int   chipRight = (int)(screenCenterColumn + chipScreenHeight / 2f);
                int   chipSpan  = chipRight - chipLeft;
                if (chipSpan <= 0) continue;

                float chipBobPhase   = itemType.ordinal() * CREDIT_PICKUP_PHASE_STEP;
                float chipBobOffset  = GameMath.pickupBobOffset(lightingTimeSeconds,
                        CREDIT_PICKUP_BOB_SPEED, CREDIT_PICKUP_BOB_AMPLITUDE_FRACTION,
                        chipBobPhase, chipScreenHeight);
                float chipFloorY     = WALL_PROJECTION_SCREEN_HEIGHT / 2f - fullWallLineHeight / 2f;
                float chipCenterY    = chipFloorY + CREDIT_PICKUP_CENTER_HEIGHT_FRACTION * fullWallLineHeight;
                float chipDrawBottom = chipCenterY - chipScreenHeight / 2f + chipBobOffset;
                float chipDrawTop    = chipDrawBottom + chipScreenHeight;
                float chipClampBot   = Math.max(0f, chipDrawBottom);
                float chipClampTop   = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, chipDrawTop);
                if (chipClampTop <= chipClampBot) continue;

                float auraR, auraG, auraB, auraRadius, auraBaseAlpha, auraPulseAmp, auraPulseSpeed;
                if (itemType == ItemType.CREDIT_LARGE) {
                    auraR = CREDIT_AURA_LARGE_R;   auraG = CREDIT_AURA_LARGE_G;   auraB = CREDIT_AURA_LARGE_B;
                    auraRadius = CREDIT_AURA_LARGE_RADIUS;   auraBaseAlpha = CREDIT_AURA_LARGE_BASE_ALPHA;
                    auraPulseAmp = CREDIT_AURA_LARGE_PULSE_AMP;   auraPulseSpeed = CREDIT_AURA_LARGE_PULSE_SPEED;
                } else if (itemType == ItemType.CREDIT_MEDIUM) {
                    auraR = CREDIT_AURA_MEDIUM_R;  auraG = CREDIT_AURA_MEDIUM_G;  auraB = CREDIT_AURA_MEDIUM_B;
                    auraRadius = CREDIT_AURA_MEDIUM_RADIUS;  auraBaseAlpha = CREDIT_AURA_MEDIUM_BASE_ALPHA;
                    auraPulseAmp = CREDIT_AURA_MEDIUM_PULSE_AMP;  auraPulseSpeed = CREDIT_AURA_MEDIUM_PULSE_SPEED;
                } else {
                    auraR = CREDIT_AURA_SMALL_R;   auraG = CREDIT_AURA_SMALL_G;   auraB = CREDIT_AURA_SMALL_B;
                    auraRadius = CREDIT_AURA_SMALL_RADIUS;   auraBaseAlpha = CREDIT_AURA_SMALL_BASE_ALPHA;
                    auraPulseAmp = CREDIT_AURA_SMALL_PULSE_AMP;   auraPulseSpeed = CREDIT_AURA_SMALL_PULSE_SPEED;
                }
                float auraAlpha   = auraBaseAlpha + auraPulseAmp * MathUtils.sin(lightingTimeSeconds * auraPulseSpeed + chipBobPhase);
                float auraHeight  = chipScreenHeight * auraRadius;
                int   auraLeft    = (int)(screenCenterColumn - auraHeight / 2f);
                int   auraRight   = (int)(screenCenterColumn + auraHeight / 2f);
                int   auraSpan    = auraRight - auraLeft;
                float auraDrawBot = chipCenterY - auraHeight / 2f + chipBobOffset;
                float auraDrawTop = auraDrawBot + auraHeight;
                float auraClampBot = Math.max(0f, auraDrawBot);
                float auraClampTop = Math.min(WALL_PROJECTION_SCREEN_HEIGHT, auraDrawTop);
                if (auraSpan > 0 && auraClampTop > auraClampBot) {
                    int auraTexSrcY = GameMath.wallTextureClipSrcY(
                            auraDrawTop, WALL_PROJECTION_SCREEN_HEIGHT,
                            auraHeight, weaponPickupGlowTexture.getHeight());
                    int auraTexSrcH = GameMath.wallTextureClipSrcHeight(
                            auraClampTop, auraClampBot, auraHeight, weaponPickupGlowTexture.getHeight());
                    auraTexSrcH = Math.min(auraTexSrcH, weaponPickupGlowTexture.getHeight() - auraTexSrcY);
                    auraTexSrcH = Math.max(1, auraTexSrcH);
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                    batch.setColor(auraR * shade, auraG * shade, auraB * shade, auraAlpha);
                    int auraFirstCol = Math.max(0, auraLeft);
                    int auraLastCol  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, auraRight);
                    for (int screenColumn = auraFirstCol; screenColumn <= auraLastCol; screenColumn++) {
                        if (renderDepth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
                        if (renderDepth >= propSpriteZBuffer[screenColumn]) continue;
                        int auraTexSrcX = (screenColumn - auraLeft) * weaponPickupGlowTexture.getWidth() / auraSpan;
                        auraTexSrcX = MathUtils.clamp(auraTexSrcX, 0, weaponPickupGlowTexture.getWidth() - 1);
                        batch.draw(weaponPickupGlowTexture,
                                   screenColumn * WALL_COLUMN_WIDTH, auraClampBot,
                                   WALL_COLUMN_WIDTH, auraClampTop - auraClampBot,
                                   auraTexSrcX, auraTexSrcY, 1, auraTexSrcH,
                                   false, false);
                    }
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                }

                Texture chipTexture = creditChipTextures.getOrDefault(itemType, genericWeaponFallbackTexture);
                int chipTexW   = chipTexture.getWidth();
                int chipTexH   = chipTexture.getHeight();
                int chipTexSrcY = GameMath.wallTextureClipSrcY(
                        chipDrawTop, WALL_PROJECTION_SCREEN_HEIGHT, chipScreenHeight, chipTexH);
                int chipTexSrcH = GameMath.wallTextureClipSrcHeight(
                        chipClampTop, chipClampBot, chipScreenHeight, chipTexH);
                chipTexSrcH = Math.min(chipTexSrcH, chipTexH - chipTexSrcY);
                chipTexSrcH = Math.max(1, chipTexSrcH);
                float chipRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
                float chipGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                float chipBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                batch.setColor(chipRed, chipGreen, chipBlue, 1f);
                int chipFirstCol = Math.max(0, chipLeft);
                int chipLastCol  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, chipRight);
                for (int screenColumn = chipFirstCol; screenColumn <= chipLastCol; screenColumn++) {
                    if (renderDepth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
                    if (renderDepth >= propSpriteZBuffer[screenColumn]) continue;
                    propSpriteZBuffer[screenColumn]      = renderDepth;
                    propSpriteColumnBottom[screenColumn] = chipClampBot;
                    propSpriteColumnTop[screenColumn]    = chipClampTop;
                    int chipTexSrcX = (screenColumn - chipLeft) * chipTexW / chipSpan;
                    chipTexSrcX = MathUtils.clamp(chipTexSrcX, 0, chipTexW - 1);
                    batch.draw(chipTexture,
                               screenColumn * WALL_COLUMN_WIDTH, chipClampBot,
                               WALL_COLUMN_WIDTH, chipClampTop - chipClampBot,
                               chipTexSrcX, chipTexSrcY, 1, chipTexSrcH,
                               false, false);
                }
                continue;
            }

            // ── Weapon pickup path ────────────────────────────────────────────────
            float spriteScreenHeight = fullWallLineHeight * WEAPON_PICKUP_HEIGHT_FRACTION;
            float spriteScreenWidth  = spriteScreenHeight; // square procedural texture

            int leftScreenColumn  = (int)(screenCenterColumn - spriteScreenWidth / 2f);
            int rightScreenColumn = (int)(screenCenterColumn + spriteScreenWidth / 2f);
            int columnSpan        = rightScreenColumn - leftScreenColumn;
            if (columnSpan <= 0) continue;

            float bobPhase   = itemType.ordinal() * WEAPON_PICKUP_PHASE_STEP;
            float bobOffset  = GameMath.pickupBobOffset(
                                   lightingTimeSeconds, WEAPON_PICKUP_BOB_SPEED,
                                   WEAPON_PICKUP_BOB_AMPLITUDE_FRACTION, bobPhase,
                                   spriteScreenHeight);
            float visualFloorY  = WALL_PROJECTION_SCREEN_HEIGHT / 2f - fullWallLineHeight / 2f;
            float weaponCenterY = visualFloorY + WEAPON_PICKUP_CENTER_HEIGHT_FRACTION * fullWallLineHeight;
            float drawBottom    = weaponCenterY - spriteScreenHeight / 2f + bobOffset;
            float drawTop       = drawBottom + spriteScreenHeight;
            float clampedBottom = Math.max(0f, drawBottom);
            float clampedTop    = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, drawTop);
            if (clampedTop <= clampedBottom) continue;

            Texture pickupTexture = weaponPickupTextures.getOrDefault(itemType, genericWeaponFallbackTexture);
            int textureWidth  = pickupTexture.getWidth();
            int textureHeight = pickupTexture.getHeight();
            int texSrcY       = GameMath.wallTextureClipSrcY(
                                    drawTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                    spriteScreenHeight, textureHeight);
            int texSrcHeight  = GameMath.wallTextureClipSrcHeight(
                                    clampedTop, clampedBottom,
                                    spriteScreenHeight, textureHeight);
            texSrcHeight = Math.min(texSrcHeight, textureHeight - texSrcY);
            texSrcHeight = Math.max(1, texSrcHeight);

            // Tier glow aura — additive halo drawn behind the weapon so the sprite keeps its true colours.
            // Use the ground item's own rolled tier first; fall back to the arsenal map only when the
            // ground item has no roll (e.g. weapon type not yet in the player's arsenal).
            WeaponTier weaponTier = (groundItem.weaponRoll != null && groundItem.weaponRoll.tier != null)
                    ? groundItem.weaponRoll.tier
                    : weaponTierMap.get(itemType);
            if (weaponTier != null) {
                float glowHeight      = spriteScreenHeight * WEAPON_PICKUP_GLOW_SIZE_MULTIPLIER;
                int   glowLeft        = (int)(screenCenterColumn - glowHeight / 2f);
                int   glowRight       = (int)(screenCenterColumn + glowHeight / 2f);
                int   glowColumnSpan  = glowRight - glowLeft;
                float glowDrawBottom  = weaponCenterY - glowHeight / 2f + bobOffset;
                float glowDrawTop     = glowDrawBottom + glowHeight;
                float glowClampBottom = Math.max(0f, glowDrawBottom);
                float glowClampTop    = Math.min(WALL_PROJECTION_SCREEN_HEIGHT, glowDrawTop);
                if (glowColumnSpan > 0 && glowClampTop > glowClampBottom) {
                    int glowTexSrcY = GameMath.wallTextureClipSrcY(
                            glowDrawTop, WALL_PROJECTION_SCREEN_HEIGHT,
                            glowHeight, weaponPickupGlowTexture.getHeight());
                    int glowTexSrcHeight = GameMath.wallTextureClipSrcHeight(
                            glowClampTop, glowClampBottom,
                            glowHeight, weaponPickupGlowTexture.getHeight());
                    glowTexSrcHeight = Math.min(glowTexSrcHeight, weaponPickupGlowTexture.getHeight() - glowTexSrcY);
                    glowTexSrcHeight = Math.max(1, glowTexSrcHeight);
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                    batch.setColor(weaponTier.colorRed * shade, weaponTier.colorGreen * shade,
                                   weaponTier.colorBlue * shade, WEAPON_PICKUP_GLOW_ALPHA);
                    int glowFirstColumn = Math.max(0, glowLeft);
                    int glowLastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, glowRight);
                    for (int screenColumn = glowFirstColumn; screenColumn <= glowLastColumn; screenColumn++) {
                        if (renderDepth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
                        if (renderDepth >= propSpriteZBuffer[screenColumn]) continue;
                        int glowTexSrcX = (screenColumn - glowLeft) * weaponPickupGlowTexture.getWidth() / glowColumnSpan;
                        glowTexSrcX = MathUtils.clamp(glowTexSrcX, 0, weaponPickupGlowTexture.getWidth() - 1);
                        batch.draw(weaponPickupGlowTexture,
                                   screenColumn * WALL_COLUMN_WIDTH, glowClampBottom,
                                   WALL_COLUMN_WIDTH, glowClampTop - glowClampBottom,
                                   glowTexSrcX, glowTexSrcY, 1, glowTexSrcHeight,
                                   false, false);
                    }
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                }
            }

            float spriteRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
            float spriteGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            float spriteBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            batch.setColor(spriteRed, spriteGreen, spriteBlue, 1f);

            int firstColumn = Math.max(0, leftScreenColumn);
            int lastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, rightScreenColumn);
            for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
                if (renderDepth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
                if (renderDepth >= propSpriteZBuffer[screenColumn]) continue;
                propSpriteZBuffer[screenColumn]      = renderDepth;
                propSpriteColumnBottom[screenColumn] = clampedBottom;
                propSpriteColumnTop[screenColumn]    = clampedTop;
                int texSrcX = (screenColumn - leftScreenColumn) * textureWidth / columnSpan;
                texSrcX = MathUtils.clamp(texSrcX, 0, textureWidth - 1);
                batch.draw(pickupTexture,
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

            Texture texture = resolvePropTexture(prop.propChar, prop.tileColumn, prop.tileRow);
            if (texture == null) continue;

            float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                    tileOffsetX, tileOffsetY, directionX, directionY,
                    planeX, planeY, WALL_PROJECTION_SCREEN_WIDTH);

            float heightMultiplier   = propHeightMultiplier(prop.propChar);
            float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, depth);
            float spriteScreenHeight = fullWallLineHeight * heightMultiplier;
            float spriteScreenWidth;
            if (Level.isHazardDecal(prop.propChar)) {
                // Hazards span the full tile footprint, decoupled from the source texture aspect.
                spriteScreenWidth = fullWallLineHeight * HAZARD_CELL_WIDTH_FILL;
            } else {
                float aspectRatio = (float) texture.getWidth() / texture.getHeight();
                spriteScreenWidth = spriteScreenHeight * aspectRatio;
            }

            int leftScreenColumn  = (int)(screenCenterColumn - spriteScreenWidth / 2f);
            int rightScreenColumn = (int)(screenCenterColumn + spriteScreenWidth / 2f);
            int columnSpan        = rightScreenColumn - leftScreenColumn;
            if (columnSpan <= 0) continue;

            float drawBottom;
            if (Level.isMedicalPickup(prop.propChar)
                    || Level.isArmourPickup(prop.propChar)
                    || Level.isAmmoPickup(prop.propChar)
                    || Level.isKeycardPickup(prop.propChar)) {
                float bobPhase         = prop.propChar * PICKUP_ITEM_BOB_PHASE_STEP;
                float dynamicBobOffset = GameMath.pickupBobOffset(lightingTimeSeconds,
                        PICKUP_ITEM_BOB_SPEED, PICKUP_ITEM_BOB_AMPLITUDE_FRACTION,
                        bobPhase, spriteScreenHeight);
                drawBottom = GameMath.spriteDrawBottomCentered(WALL_PROJECTION_SCREEN_HEIGHT, spriteScreenHeight)
                        + dynamicBobOffset
                        - PICKUP_ITEM_GROUND_OFFSET_FRACTION * spriteScreenHeight;
            } else if (Level.isStairsDown(prop.propChar)) {
                drawBottom = GameMath.spriteDrawBottomCentered(WALL_PROJECTION_SCREEN_HEIGHT, spriteScreenHeight);
            } else {
                drawBottom = WALL_PROJECTION_SCREEN_HEIGHT / 2f - fullWallLineHeight / 2f;
            }
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

            ColumnOpacityMask opacityMask = COLUMN_OPACITY.get(texture);
            int firstColumn = Math.max(0, leftScreenColumn);
            int lastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, rightScreenColumn);
            for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
                if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
                if (depth >= propSpriteZBuffer[screenColumn]) continue;
                int texSrcX = (screenColumn - leftScreenColumn) * textureWidth / columnSpan;
                texSrcX = MathUtils.clamp(texSrcX, 0, textureWidth - 1);
                recordPropOccluderColumn(screenColumn, depth, prop.propChar, opacityMask,
                        texSrcX, texSrcY, texSrcHeight, clampedBottom, clampedTop);
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
        disposeProp(genericWeaponFallbackTexture);
        disposeProp(weaponPickupGlowTexture);
        for (Texture texture : weaponPickupTextures.values()) {
            disposeProp(texture);
        }
        for (Texture texture : creditChipTextures.values()) {
            disposeProp(texture);
        }
        for (Texture texture : textures.values()) {
            disposeProp(texture);
        }
        for (Texture frame : hazardFireFrames) {
            disposeProp(frame);
        }
        for (Texture frame : hazardToxicFrames) {
            disposeProp(frame);
        }
    }

    // Disposes a prop texture and drops its opacity mask so the static COLUMN_OPACITY
    // cache does not grow unbounded across level reloads (each reload builds a new
    // PropRenderer with freshly generated textures).
    private static void disposeProp(Texture texture) {
        COLUMN_OPACITY.remove(texture);
        texture.dispose();
    }

    /**
     * Disposes a texture produced by this class's prop/decal generators AND prunes its entry from the
     * static {@link #COLUMN_OPACITY} cache (symbol/sprite-reuse order-6). The per-level
     * {@code EnvironmentTextureSet} realizes prop/decal textures through those generators (which register
     * an opacity mask via {@code finalize()}), so it must free them the same way this renderer frees its
     * own — otherwise the cache would leak one mask per prop sprite per level transition. Safe to call on
     * a wall/column texture too: those were never registered, so the {@code remove} is a harmless no-op.
     */
    public static void disposeGeneratedTexture(Texture texture) {
        disposeProp(texture);
    }

    /**
     * Registers this renderer's solid-prop + floor-decal texture generators into the tileset
     * texture-generator registry (symbol/sprite-reuse order-6). One line per sprite id, each delegating to
     * the SAME procedural routine the constructor's {@link #buildTextures()} uses today, so realized
     * textures are byte-identical to the live prop map — order-6 changes WHERE textures live, not how they
     * look. Called once at startup by {@code TilesetGfxBootstrap}; the ids registered here must match the
     * SOLID_PROP/FLOOR_DECAL sprite ids in {@code TilesetRegistries} (the startup assertion enforces it).
     *
     * <p>The radioactive barrel reproduces {@code buildTextures()}'s load-or-generate for its optional
     * disk asset; the explosive barrel shares the same generator with {@code explosive=true}.
     */
    public static void registerTextureGenerators(TextureGeneratorRegistry registry) {
        // Solid props (registration order matches TilesetRegistries.registerSolidProps).
        registry.register("prop_radioactive_barrel",
                () -> loadOrGenerate(PROP_BARREL_RADIOACTIVE_PATH, generateBarrelTexture(false)));
        registry.register("prop_explosive_barrel",   () -> generateBarrelTexture(true));
        registry.register("prop_terminal",           PropRenderer::generateTerminalTexture);
        registry.register("prop_locker",             PropRenderer::generateLockerTexture);
        registry.register("prop_crate",              PropRenderer::generateCrateTexture);
        registry.register("prop_camera",             PropRenderer::generateCameraTexture);
        registry.register("prop_generator",          PropRenderer::generateGeneratorTexture);
        registry.register("prop_biopod",             PropRenderer::generateBioPodTexture);
        registry.register("prop_weapon_rack",        PropRenderer::generateWeaponRackTexture);
        registry.register("prop_vendor",             PropRenderer::generateVendorTexture);
        registry.register("prop_specimen_tank",      PropRenderer::generateSpecimenTankTexture);
        registry.register("prop_ai_core",            PropRenderer::generateAiCoreNodeTexture);
        registry.register("prop_holo_workstation",   PropRenderer::generateHoloWorkstationTexture);
        registry.register("prop_gravity_well_core",  PropRenderer::generateGravityWellCoreTexture);
        registry.register("prop_floating_cargo_crate", PropRenderer::generateFloatingCargoCrateTexture);
        registry.register("prop_docking_strut_pylon",  PropRenderer::generateDockingStrutPylonTexture);
        registry.register("prop_astro_nav_table",    PropRenderer::generateAstroNavHoloTableTexture);
        // Floor decals (registration order matches TilesetRegistries.registerDecals).
        registry.register("decal_corpse",            PropRenderer::generateCorpseTexture);
        registry.register("decal_blood_alt",         PropRenderer::generateShotgunTexture);
        registry.register("decal_blood",             PropRenderer::generateBloodTexture);
        registry.register("decal_oil",               PropRenderer::generateOilTexture);
        registry.register("decal_energy_scorch",     PropRenderer::generateEnergyScorchTexture);
        registry.register("decal_magnetic_deck",     PropRenderer::generateMagneticDeckPlatingTexture);
        registry.register("decal_zerog_debris",      PropRenderer::generateZeroGDebrisScatterTexture);
        registry.register("decal_starlight_seam",    PropRenderer::generateStarlightSeamStripTexture);
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
            disposeProp(proceduralFallback);
            // A file-loaded texture has no generated opacity mask; build one so its
            // transparent regions also stop occluding enemies behind it.
            Pixmap loadedPixels = new Pixmap(Gdx.files.internal(assetPath));
            COLUMN_OPACITY.put(loaded, computeColumnOpacity(loadedPixels));
            loadedPixels.dispose();
            return loaded;
        }
        return proceduralFallback;
    }

    /**
     * Builds ONLY the FIXED-gameplay prop textures this renderer still owns (keycards, medical, armour,
     * portal, ammo). The ENVIRONMENT prop/decal art (solid props like barrels/terminals/lockers and floor
     * decals like blood/oil/corpse — the SOLID_PROP + FLOOR_DECAL tileset categories) is realized per level
     * into the {@code EnvironmentTextureSet} and resolved by {@link #resolvePropTexture} through the
     * palette, so it is no longer generated or owned here (order-7). Their generators still live in this
     * class, reached only through {@link #registerTextureGenerators}.
     */
    private static Map<Character, Texture> buildTextures() {
        Map<Character, Texture> map = new HashMap<>();
        map.put('r', generateKeycardTexture(0.95f, 0.16f, 0.16f));
        map.put('y', generateKeycardTexture(0.97f, 0.84f, 0.14f));
        map.put('b', generateKeycardTexture(0.18f, 0.52f, 0.97f));
        map.put('+', generateStimTexture());
        map.put('H', generateFieldMedkitTexture());
        map.put('a', generateArmourShardTexture());
        map.put('A', generateSecurityVestTexture());
        map.put('>', generatePortalTexture());
        map.put('6', generateBulletClipTexture());    // BULLETS — copper rifle cartridges in a clip
        map.put('7', generateShotgunShellsTexture());  // SHELLS  — red-hull shells on brass bases
        map.put('8', generatePlasmaCellTexture());     // CELLS   — glowing cyan energy canister
        map.put('9', generateRocketTexture());         // ROCKETS — olive warhead with fins
        map.put('0', generateRailSlugsTexture());      // SLUGS   — heavy silver rail rounds
        // 'i' (fire) and 'q' (toxic) are NOT static entries — they are animated and resolved
        // per-frame from hazardFireFrames / hazardToxicFrames via resolvePropTexture().
        return map;
    }

    // ── Per-column opacity masks ───────────────────────────────────────────
    // Prop billboards are drawn as 1-pixel-wide column slices and record their
    // depth into a per-column z-buffer so EnemyRenderer can carve an enemy around
    // a nearer prop. A sprite with wide transparent margins (e.g. the ammo
    // pickups) must NOT occlude through its see-through pixels, otherwise an enemy
    // behind it is clipped where the prop is actually transparent. For every
    // generated texture we therefore record, per texture column, the topmost and
    // bottommost opaque pixel row (or -1 when the whole column is transparent).
    // Keyed by Texture identity; populated in finalize(), pruned on dispose().
    private static final IdentityHashMap<Texture, ColumnOpacityMask> COLUMN_OPACITY = new IdentityHashMap<>();

    // A pixel counts as opaque (and therefore occluding) when its alpha clears this
    // 0–255 threshold. Generated sprites use only fully-opaque (255) or fully
    // transparent (0) pixels, so any modest cutoff separates art from background.
    private static final int OPAQUE_ALPHA_THRESHOLD = 8;

    private static final class ColumnOpacityMask {
        final int[] opaqueTopRow;     // smallest opaque row index per column (0 = texture top), or -1
        final int[] opaqueBottomRow;  // largest opaque row index per column, or -1
        ColumnOpacityMask(int[] opaqueTopRow, int[] opaqueBottomRow) {
            this.opaqueTopRow    = opaqueTopRow;
            this.opaqueBottomRow  = opaqueBottomRow;
        }
    }

    // Scans a pixmap column-by-column for its opaque vertical extent. Pixmap row 0
    // is the top edge, matching the texture-row convention used during the draw.
    private static ColumnOpacityMask computeColumnOpacity(Pixmap pixmap) {
        int width  = pixmap.getWidth();
        int height = pixmap.getHeight();
        int[] opaqueTopRow    = new int[width];
        int[] opaqueBottomRow = new int[width];
        for (int column = 0; column < width; column++) {
            int top = -1, bottom = -1;
            for (int row = 0; row < height; row++) {
                int alpha = pixmap.getPixel(column, row) & 0xff;
                if (alpha >= OPAQUE_ALPHA_THRESHOLD) {
                    if (top == -1) top = row;
                    bottom = row;
                }
            }
            opaqueTopRow[column]    = top;
            opaqueBottomRow[column] = bottom;
        }
        return new ColumnOpacityMask(opaqueTopRow, opaqueBottomRow);
    }

    private static Texture finalize(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        COLUMN_OPACITY.put(texture, computeColumnOpacity(pixmap));
        pixmap.dispose();
        return texture;
    }

    // Records the nearest occluding prop's depth and opaque vertical extent for one
    // screen column, but only where the sprite column actually has opaque pixels.
    // Columns that are transparent (margins, gaps between rounds, the empty space
    // above/below a horizontal sprite) are skipped, so the enemy behind stays
    // visible through them. occBottom/occTop are the screen-space (Y-up) bounds of
    // just the opaque run, letting EnemyRenderer carve a tight gap.
    private void recordPropOccluderColumn(int screenColumn, float depth, char propChar,
                                          ColumnOpacityMask opacityMask, int texSrcX,
                                          int texSrcY, int texSrcHeight,
                                          float clampedBottom, float clampedTop) {
        if (!Level.isPropOccluder(propChar)) return;
        float occBottom = clampedBottom;
        float occTop    = clampedTop;
        if (opacityMask != null) {
            int topRow    = opacityMask.opaqueTopRow[texSrcX];
            if (topRow == -1) return;                              // fully transparent column
            int bottomRow = opacityMask.opaqueBottomRow[texSrcX];
            int visibleTop    = Math.max(topRow, texSrcY);          // clip to the drawn texture region
            int visibleBottom = Math.min(bottomRow, texSrcY + texSrcHeight - 1);
            if (visibleTop > visibleBottom) return;                // opaque run lies outside the visible rows
            // Texture row → screen Y: row texSrcY maps to clampedTop, row texSrcY+texSrcHeight to clampedBottom.
            float columnHeightSpan = clampedTop - clampedBottom;
            occTop    = clampedTop - (visibleTop - texSrcY)        / (float) texSrcHeight * columnHeightSpan;
            occBottom = clampedTop - (visibleBottom + 1 - texSrcY) / (float) texSrcHeight * columnHeightSpan;
        }
        propSpriteZBuffer[screenColumn]      = depth;
        propSpriteColumnBottom[screenColumn] = occBottom;
        propSpriteColumnTop[screenColumn]    = occTop;
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

    // ── Shared prop-art helpers ────────────────────────────────────────────
    // Fills a panel then adds a 1px lit bevel (top + left) and shadow bevel
    // (bottom + right) so flat boxes read as having depth under flat lighting.
    private static void beveledPanel(Pixmap pixmap, int x, int y, int width, int height,
                                     float red, float green, float blue,
                                     float litFactor, float shadowFactor) {
        pixmap.setColor(red, green, blue, 1f);
        pixmap.fillRectangle(x, y, width, height);
        pixmap.setColor(Math.min(1f, red * litFactor),
                        Math.min(1f, green * litFactor),
                        Math.min(1f, blue * litFactor), 1f);
        pixmap.fillRectangle(x, y, width, 1);   // top highlight
        pixmap.fillRectangle(x, y, 1, height);  // left highlight
        pixmap.setColor(red * shadowFactor, green * shadowFactor, blue * shadowFactor, 1f);
        pixmap.fillRectangle(x, y + height - 1, width, 1);  // bottom shadow
        pixmap.fillRectangle(x + width - 1, y, 1, height);  // right shadow
    }

    // Small 3×3 bolt stud: dark seat ring with a single bright specular pixel.
    private static void boltStud(Pixmap pixmap, int centerX, int centerY) {
        pixmap.setColor(0.08f, 0.08f, 0.10f, 1f);
        pixmap.fillRectangle(centerX - 1, centerY - 1, 3, 3);
        pixmap.setColor(0.58f, 0.61f, 0.67f, 1f);
        pixmap.drawPixel(centerX, centerY);
    }

    // Diagonal hazard stripe band (caution yellow / near-black) across a region.
    private static void hazardStripeBand(Pixmap pixmap, int x, int y, int width, int height,
                                         float yellowRed, float yellowGreen, float yellowBlue) {
        for (int column = x; column < x + width; column++) {
            for (int row = y; row < y + height; row++) {
                boolean bright = ((((column - row) % 12) + 12) % 12) < 6;
                if (bright) pixmap.setColor(yellowRed, yellowGreen, yellowBlue, 1f);
                else        pixmap.setColor(0.09f, 0.08f, 0.05f, 1f);
                pixmap.drawPixel(column, row);
            }
        }
    }

    // Small round analogue gauge with a tinted needle, used on machinery panels.
    private static void drawGauge(Pixmap pixmap, int centerX, int centerY,
                                  float needleRed, float needleGreen, float needleBlue) {
        pixmap.setColor(0.10f, 0.11f, 0.13f, 1f);
        pixmap.fillCircle(centerX, centerY, 7);
        pixmap.setColor(0.34f, 0.36f, 0.40f, 1f);
        pixmap.fillCircle(centerX, centerY, 6);
        pixmap.setColor(0.06f, 0.07f, 0.09f, 1f);
        pixmap.fillCircle(centerX, centerY, 5);
        pixmap.setColor(0.55f, 0.58f, 0.62f, 1f);
        pixmap.drawPixel(centerX, centerY - 4);
        pixmap.drawPixel(centerX - 4, centerY);
        pixmap.drawPixel(centerX + 4, centerY);
        pixmap.setColor(needleRed, needleGreen, needleBlue, 1f);
        pixmap.fillRectangle(centerX, centerY - 4, 1, 4);
        pixmap.drawPixel(centerX + 1, centerY - 3);
        pixmap.setColor(0.82f, 0.84f, 0.88f, 1f);
        pixmap.drawPixel(centerX, centerY);
    }

    // Soft elliptical light pool cast on the floor beneath a hovering pickup. Alpha
    // falls off quadratically toward the rim so the pool fades smoothly into the
    // transparent background. Draw this before the body so the sprite sits on top of it.
    private static void glowPool(Pixmap pixmap, int centerX, int centerY,
                                 int radiusX, int radiusY,
                                 float red, float green, float blue, float coreAlpha) {
        for (int offsetY = -radiusY; offsetY <= radiusY; offsetY++) {
            for (int offsetX = -radiusX; offsetX <= radiusX; offsetX++) {
                float normalizedDistanceSquared =
                        (offsetX * (float) offsetX) / (float) (radiusX * radiusX)
                      + (offsetY * (float) offsetY) / (float) (radiusY * radiusY);
                if (normalizedDistanceSquared > 1f) continue;
                float alpha = coreAlpha * (1f - normalizedDistanceSquared);
                pixmap.setColor(red, green, blue, alpha);
                pixmap.drawPixel(centerX + offsetX, centerY + offsetY);
            }
        }
    }

    // Fills a rectangle with a top-lit vertical gradient: brighter at the top edge,
    // darker toward the bottom, giving flat panels a sense of volume under overhead
    // light. baseRed/Green/Blue is the mid-tone; topBoost lightens the top row and
    // bottomDrop darkens the bottom row (both fractions of the base tone).
    private static void verticalGradientPanel(Pixmap pixmap, int x, int y,
                                              int width, int height,
                                              float baseRed, float baseGreen, float baseBlue,
                                              float topBoost, float bottomDrop) {
        for (int pixelRow = 0; pixelRow < height; pixelRow++) {
            float verticalFraction = pixelRow / (float) Math.max(1, height - 1);
            float factor = (1f + topBoost) - (topBoost + bottomDrop) * verticalFraction;
            pixmap.setColor(Math.min(1f, baseRed * factor),
                            Math.min(1f, baseGreen * factor),
                            Math.min(1f, baseBlue * factor), 1f);
            pixmap.fillRectangle(x, y + pixelRow, width, 1);
        }
    }

    // Fills an arbitrary (possibly rotated/skewed) quadrilateral by splitting it into two
    // triangles sharing the (x1,y1)->(x3,y3) diagonal. Corners must be supplied in either
    // clockwise or counter-clockwise winding order. Used to draw tilted billboard faces
    // (e.g. the floating cargo crate) that a plain fillRectangle cannot represent.
    private static void fillQuad(Pixmap pixmap, float red, float green, float blue, float alpha,
                                 int x1, int y1, int x2, int y2,
                                 int x3, int y3, int x4, int y4) {
        pixmap.setColor(red, green, blue, alpha);
        pixmap.fillTriangle(x1, y1, x2, y2, x3, y3);
        pixmap.fillTriangle(x1, y1, x3, y3, x4, y4);
    }

    // Erases the four corners of a filled rectangle with transparent right-triangles,
    // turning it into a chamfered (angular) plate. Each leg parameter is the length of
    // the cut along both edges meeting at that corner. Switches Pixmap blending to None
    // so the corner pixels become fully transparent, then restores the previous mode.
    private static void chamferCorners(Pixmap pixmap, int left, int top, int right, int bottom,
                                       int topLeftLeg, int topRightLeg,
                                       int bottomRightLeg, int bottomLeftLeg) {
        Pixmap.Blending previousBlending = pixmap.getBlending();
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fillTriangle(left,  top,    left + topLeftLeg,  top,    left,  top + topLeftLeg);
        pixmap.fillTriangle(right, top,    right - topRightLeg, top,   right, top + topRightLeg);
        pixmap.fillTriangle(right, bottom, right - bottomRightLeg, bottom, right, bottom - bottomRightLeg);
        pixmap.fillTriangle(left,  bottom, left + bottomLeftLeg, bottom, left, bottom - bottomLeftLeg);
        pixmap.setBlending(previousBlending);
    }

    private static Texture generateBarrelTexture(boolean explosive) {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        if (explosive) {
            // Volatile fuel drum — rounded crimson cylinder, hazard hoops, a stamped
            // explosion-burst warning and hot seams where the contents leak light.
            pixmap.setColor(0f, 0f, 0f, 0f);
            pixmap.fill();

            final int bodyLeft = 8, bodyRight = 56, bodyTop = 7, bodyBottom = 61;
            // Cylindrical steel body: bright highlight band sits left-of-centre.
            for (int column = bodyLeft; column < bodyRight; column++) {
                float across = (column - bodyLeft) / (float) (bodyRight - bodyLeft - 1);
                float curve  = 1f - Math.min(1f, Math.abs(across - 0.38f) * 2.0f);
                float bodyRed   = 0.24f + 0.60f * curve;
                float bodyGreen = 0.05f + 0.16f * curve;
                float bodyBlue  = 0.04f + 0.10f * curve;
                pixmap.setColor(bodyRed, bodyGreen, bodyBlue, 1f);
                pixmap.fillRectangle(column, bodyTop, 1, bodyBottom - bodyTop);
            }
            // Rolled hoop rings — darker steel with a top highlight and spaced rivets.
            int[] hoopRows = { 9, 20, 44, 55 };
            for (int hoopRow : hoopRows) {
                pixmap.setColor(0.16f, 0.05f, 0.04f, 1f);
                pixmap.fillRectangle(bodyLeft, hoopRow, bodyRight - bodyLeft, 4);
                pixmap.setColor(0.70f, 0.30f, 0.16f, 1f);
                pixmap.fillRectangle(bodyLeft, hoopRow, bodyRight - bodyLeft, 1);
                for (int rivetColumn = bodyLeft + 4; rivetColumn < bodyRight - 2; rivetColumn += 10) {
                    pixmap.setColor(0.85f, 0.55f, 0.25f, 1f);
                    pixmap.drawPixel(rivetColumn, hoopRow + 2);
                }
            }
            // Top lid with a central bung valve.
            pixmap.setColor(0.52f, 0.20f, 0.12f, 1f);
            pixmap.fillRectangle(bodyLeft + 2, bodyTop - 2, bodyRight - bodyLeft - 4, 4);
            pixmap.setColor(0.78f, 0.34f, 0.18f, 1f);
            pixmap.fillRectangle(bodyLeft + 2, bodyTop - 2, bodyRight - bodyLeft - 4, 1);
            pixmap.setColor(0.30f, 0.10f, 0.06f, 1f);
            pixmap.fillCircle(32, bodyTop, 3);
            pixmap.setColor(0.85f, 0.45f, 0.20f, 1f);
            pixmap.fillCircle(32, bodyTop, 1);
            // Bottom shadow lip.
            pixmap.setColor(0.10f, 0.03f, 0.03f, 1f);
            pixmap.fillRectangle(bodyLeft, bodyBottom - 2, bodyRight - bodyLeft, 2);

            // Hot volatile seams: glowing cracks framing the warning plate.
            int[] seamColumns = { 17, 46 };
            for (int seamColumn : seamColumns) {
                for (int seamRow = 23; seamRow < 42; seamRow++) {
                    int crackColumn = seamColumn + (((seamRow / 4) % 3) - 1);
                    pixmap.setColor(0.80f, 0.30f, 0.05f, 1f);
                    pixmap.drawPixel(crackColumn - 1, seamRow);
                    pixmap.drawPixel(crackColumn + 1, seamRow);
                    pixmap.setColor(1.00f, 0.85f, 0.35f, 1f);
                    pixmap.drawPixel(crackColumn, seamRow);
                }
            }
            // Central warning plate with a stamped explosion burst.
            pixmap.setColor(0.08f, 0.07f, 0.05f, 1f);
            pixmap.fillRectangle(20, 24, 24, 16);
            pixmap.setColor(0.92f, 0.78f, 0.06f, 1f);
            drawRectOutline(pixmap, 20, 24, 24, 16);
            int burstCenterX = 32, burstCenterY = 32;
            pixmap.setColor(0.95f, 0.80f, 0.10f, 1f);
            for (int spike = 0; spike < 8; spike++) {
                double spikeAngleRadians = spike * Math.PI / 4.0;
                int tipX = burstCenterX + (int) Math.round(Math.cos(spikeAngleRadians) * 9);
                int tipY = burstCenterY + (int) Math.round(Math.sin(spikeAngleRadians) * 7);
                double perpendicularRadians = spikeAngleRadians + Math.PI / 2.0;
                int baseOffsetX = (int) Math.round(Math.cos(perpendicularRadians) * 2);
                int baseOffsetY = (int) Math.round(Math.sin(perpendicularRadians) * 2);
                pixmap.fillTriangle(burstCenterX + baseOffsetX, burstCenterY + baseOffsetY,
                                    burstCenterX - baseOffsetX, burstCenterY - baseOffsetY,
                                    tipX, tipY);
            }
            pixmap.setColor(0.98f, 0.45f, 0.08f, 1f);
            pixmap.fillCircle(burstCenterX, burstCenterY, 3);
            pixmap.setColor(1.00f, 0.92f, 0.55f, 1f);
            pixmap.fillCircle(burstCenterX, burstCenterY, 1);
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
        Pixmap pixmap = new Pixmap(96, 96, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // ── Lower cabinet ───────────────────────────────────────────────────
        beveledPanel(pixmap, 12, 40, 72, 54, 0.17f, 0.19f, 0.24f, 1.7f, 0.45f);
        // Cooling vent slits.
        pixmap.setColor(0.09f, 0.10f, 0.13f, 1f);
        for (int ventRow = 66; ventRow <= 80; ventRow += 4) {
            pixmap.fillRectangle(20, ventRow, 36, 2);
        }
        // ID label plate with fake engraved text.
        pixmap.setColor(0.45f, 0.47f, 0.52f, 1f);
        pixmap.fillRectangle(60, 66, 18, 14);
        pixmap.setColor(0.16f, 0.17f, 0.20f, 1f);
        pixmap.fillRectangle(62, 69, 14, 1);
        pixmap.fillRectangle(62, 72, 11, 1);
        pixmap.fillRectangle(62, 75, 14, 1);
        // Cabinet bolts + hazard trim.
        boltStud(pixmap, 16, 44); boltStud(pixmap, 80, 44);
        boltStud(pixmap, 16, 86); boltStud(pixmap, 80, 86);
        hazardStripeBand(pixmap, 12, 88, 72, 4, 0.86f, 0.72f, 0.10f);

        // ── Monitor housing ─────────────────────────────────────────────────
        beveledPanel(pixmap, 8, 6, 80, 40, 0.22f, 0.24f, 0.30f, 1.6f, 0.5f);
        pixmap.setColor(0.07f, 0.08f, 0.10f, 1f);
        pixmap.fillRectangle(14, 10, 68, 32);
        // Screen — cyan-teal vertical gradient (brighter toward the top).
        for (int screenRow = 12; screenRow < 40; screenRow++) {
            float toBottom = (screenRow - 12) / 27f;
            pixmap.setColor(0.06f + 0.06f * (1f - toBottom),
                            0.34f + 0.16f * (1f - toBottom),
                            0.40f + 0.18f * (1f - toBottom), 1f);
            pixmap.fillRectangle(16, screenRow, 64, 1);
        }
        // Scanlines.
        pixmap.setColor(0.04f, 0.20f, 0.24f, 0.5f);
        for (int scanRow = 12; scanRow < 40; scanRow += 2) {
            pixmap.fillRectangle(16, scanRow, 64, 1);
        }
        // Header bar + readout text rows of varied length.
        pixmap.setColor(0.45f, 0.92f, 0.98f, 1f);
        pixmap.fillRectangle(18, 13, 60, 2);
        pixmap.setColor(0.30f, 0.80f, 0.86f, 1f);
        int[] textRowY     = { 18, 22, 26, 30 };
        int[] textRowWidth = { 40, 30, 52, 24 };
        for (int textIndex = 0; textIndex < textRowY.length; textIndex++) {
            pixmap.fillRectangle(18, textRowY[textIndex], textRowWidth[textIndex], 2);
        }
        // Waveform blips along the bottom of the screen.
        pixmap.setColor(0.55f, 0.95f, 1.00f, 1f);
        int[] waveHeight = { 2, 5, 3, 6, 2, 4, 7, 3 };
        for (int waveIndex = 0; waveIndex < waveHeight.length; waveIndex++) {
            pixmap.fillRectangle(18 + waveIndex * 5, 38 - waveHeight[waveIndex], 2, waveHeight[waveIndex]);
        }
        // Cursor block + glowing screen rim.
        pixmap.setColor(0.60f, 1.00f, 1.00f, 1f);
        pixmap.fillRectangle(44, 30, 5, 2);
        pixmap.setColor(0.30f, 0.85f, 0.95f, 0.5f);
        drawRectOutline(pixmap, 15, 11, 66, 30);
        // Indicator LED stack on the housing's right edge.
        pixmap.setColor(0.25f, 0.90f, 0.35f, 1f); pixmap.fillRectangle(83, 12, 3, 3);
        pixmap.setColor(0.95f, 0.70f, 0.15f, 1f); pixmap.fillRectangle(83, 18, 3, 3);
        pixmap.setColor(0.90f, 0.25f, 0.20f, 1f); pixmap.fillRectangle(83, 24, 3, 3);

        // ── Control deck between monitor and cabinet ────────────────────────
        pixmap.setColor(0.26f, 0.28f, 0.34f, 1f);
        pixmap.fillRectangle(14, 48, 68, 12);
        pixmap.setColor(0.14f, 0.15f, 0.19f, 1f);
        for (int keyRow = 0; keyRow < 2; keyRow++) {
            for (int keyColumn = 0; keyColumn < 10; keyColumn++) {
                pixmap.fillRectangle(18 + keyColumn * 6, 50 + keyRow * 5, 4, 3);
            }
        }
        // Function buttons (red / amber / green).
        pixmap.setColor(0.90f, 0.25f, 0.20f, 1f); pixmap.fillRectangle(64, 50, 5, 4);
        pixmap.setColor(0.95f, 0.70f, 0.15f, 1f); pixmap.fillRectangle(71, 50, 5, 4);
        pixmap.setColor(0.25f, 0.90f, 0.35f, 1f); pixmap.fillRectangle(64, 55, 5, 4);
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
        Pixmap pixmap = new Pixmap(96, 96, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Reinforced olive-drab munitions box with a bevelled shell.
        beveledPanel(pixmap, 6, 6, 84, 84, 0.30f, 0.34f, 0.20f, 1.5f, 0.55f);
        // Two recessed front panels split by a centre seam.
        pixmap.setColor(0.25f, 0.28f, 0.16f, 1f);
        pixmap.fillRectangle(12, 14, 34, 50);
        pixmap.fillRectangle(50, 14, 34, 50);
        pixmap.setColor(0.38f, 0.42f, 0.26f, 1f);   // panel lit inner edges
        pixmap.fillRectangle(12, 14, 34, 1); pixmap.fillRectangle(12, 14, 1, 50);
        pixmap.fillRectangle(50, 14, 34, 1); pixmap.fillRectangle(50, 14, 1, 50);
        pixmap.setColor(0.16f, 0.18f, 0.10f, 1f);   // panel shadow inner edges
        pixmap.fillRectangle(12, 63, 34, 1); pixmap.fillRectangle(45, 14, 1, 50);
        pixmap.fillRectangle(50, 63, 34, 1); pixmap.fillRectangle(83, 14, 1, 50);
        // Raised horizontal reinforcing ribs.
        for (int ribRow : new int[]{ 30, 48 }) {
            pixmap.setColor(0.36f, 0.40f, 0.24f, 1f);
            pixmap.fillRectangle(8, ribRow, 80, 4);
            pixmap.setColor(0.44f, 0.48f, 0.30f, 1f);
            pixmap.fillRectangle(8, ribRow, 80, 1);
            pixmap.setColor(0.16f, 0.18f, 0.10f, 1f);
            pixmap.fillRectangle(8, ribRow + 3, 80, 1);
        }
        // Steel corner brackets with bolts.
        pixmap.setColor(0.40f, 0.42f, 0.46f, 1f);
        pixmap.fillRectangle(6,  6,  8, 8);
        pixmap.fillRectangle(82, 6,  8, 8);
        pixmap.fillRectangle(6,  82, 8, 8);
        pixmap.fillRectangle(82, 82, 8, 8);
        boltStud(pixmap, 10, 10); boltStud(pixmap, 85, 10);
        boltStud(pixmap, 10, 85); boltStud(pixmap, 85, 85);
        // Hazard stripe band across the centre seam.
        hazardStripeBand(pixmap, 8, 40, 80, 6, 0.88f, 0.74f, 0.12f);
        // Stencil "UAC" on the upper-left panel.
        pixmap.setColor(0.78f, 0.80f, 0.55f, 1f);
        pixmap.fillRectangle(20, 20, 2, 8); pixmap.fillRectangle(26, 20, 2, 8); pixmap.fillRectangle(20, 26, 8, 2);
        pixmap.fillRectangle(32, 20, 2, 8); pixmap.fillRectangle(38, 20, 2, 8); pixmap.fillRectangle(32, 20, 8, 2); pixmap.fillRectangle(32, 23, 8, 2);
        pixmap.fillRectangle(44, 20, 2, 8); pixmap.fillRectangle(44, 20, 7, 2); pixmap.fillRectangle(44, 26, 7, 2);
        // Cartridge icon stamped on the right panel.
        pixmap.setColor(0.80f, 0.62f, 0.20f, 1f);
        pixmap.fillRectangle(60, 20, 16, 6);
        pixmap.setColor(0.92f, 0.80f, 0.40f, 1f);
        pixmap.fillRectangle(72, 20, 4, 6);
        // Side clasp latches.
        pixmap.setColor(0.46f, 0.48f, 0.52f, 1f);
        pixmap.fillRectangle(28, 70, 12, 8);
        pixmap.fillRectangle(56, 70, 12, 8);
        pixmap.setColor(0.20f, 0.21f, 0.24f, 1f);
        pixmap.fillRectangle(31, 73, 6, 2);
        pixmap.fillRectangle(59, 73, 6, 2);
        // Surface wear — scuffs and a dented corner.
        pixmap.setColor(0.18f, 0.20f, 0.12f, 1f);
        pixmap.drawPixel(70, 58); pixmap.drawPixel(71, 57); pixmap.drawPixel(72, 57);
        pixmap.drawPixel(18, 80); pixmap.drawPixel(19, 81);
        return finalize(pixmap);
    }

    // ── Organic decal helpers ──────────────────────────────────────────────
    // Builds an irregular organic blob by stacking jittered filled circles, so
    // gore / blood / oil pools get ragged natural edges instead of a hard box.
    // The blob is squashed slightly in Y to read as a puddle on a perspective floor.
    private static void organicBlob(Pixmap pixmap, java.util.Random random,
                                    int centerX, int centerY, int baseRadius, int lobeCount,
                                    float red, float green, float blue, float alpha) {
        pixmap.setColor(red, green, blue, alpha);
        for (int lobeIndex = 0; lobeIndex < lobeCount; lobeIndex++) {
            int offsetX    = Math.round((random.nextFloat() - 0.5f) * baseRadius * 1.7f);
            int offsetY    = Math.round((random.nextFloat() - 0.5f) * baseRadius * 1.0f);
            int lobeRadius = Math.max(1, Math.round(baseRadius * (0.35f + random.nextFloat() * 0.65f)));
            pixmap.fillCircle(centerX + offsetX, centerY + offsetY, lobeRadius);
        }
    }

    // Scatters small round droplets in a flattened radial spray around a point —
    // the cast-off spatter that rings a fresh wound, splash, or impact.
    private static void scatterDroplets(Pixmap pixmap, java.util.Random random,
                                        int centerX, int centerY, int spreadRadius, int dropletCount,
                                        float red, float green, float blue) {
        pixmap.setColor(red, green, blue, 1f);
        for (int dropletIndex = 0; dropletIndex < dropletCount; dropletIndex++) {
            float angleRadians = random.nextFloat() * MathUtils.PI2;
            float distance     = spreadRadius * (0.45f + random.nextFloat() * 0.55f);
            int dropletRadius  = random.nextInt(2) + 1;
            int dropletX = centerX + Math.round(MathUtils.cos(angleRadians) * distance);
            int dropletY = centerY + Math.round(MathUtils.sin(angleRadians) * distance * 0.55f);
            // Keep the whole droplet inside the pixmap so spatter never clips at the tile edge.
            dropletX = MathUtils.clamp(dropletX, dropletRadius, pixmap.getWidth()  - 1 - dropletRadius);
            dropletY = MathUtils.clamp(dropletY, dropletRadius, pixmap.getHeight() - 1 - dropletRadius);
            pixmap.fillCircle(dropletX, dropletY, dropletRadius);
        }
    }

    // Corpse decal ('m') — a flattened mess of blood and torn flesh: a wide dark
    // pool, clotted gore mass, exposed pink muscle chunks, splintered bone shards,
    // and glossy fresh-blood specular flecks, ringed by cast-off spatter.
    private static Texture generateCorpseTexture() {
        Pixmap pixmap = new Pixmap(128, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        java.util.Random random = new java.util.Random(0xB10D9011EAD7L);
        int poolCenterX = 60;
        int poolCenterY = 40;

        // Far cast-off spatter — drawn first so the pool mass overlaps the inner drops.
        scatterDroplets(pixmap, random, poolCenterX, 34, 58, 70, 0.30f, 0.02f, 0.02f);
        // Wide thin blood pool spreading on the floor (darkest, broadest).
        organicBlob(pixmap, random, poolCenterX, poolCenterY, 30, 22, 0.26f, 0.02f, 0.02f, 1f);
        // Brighter wet pool interior.
        organicBlob(pixmap, random, poolCenterX, poolCenterY, 22, 18, 0.40f, 0.03f, 0.03f, 1f);
        // Clotted gore mass — the body remains, dark maroon, sitting higher (smaller Y).
        organicBlob(pixmap, random, 58, 30, 24, 20, 0.20f, 0.04f, 0.05f, 1f);
        organicBlob(pixmap, random, 78, 26, 14, 12, 0.17f, 0.03f, 0.04f, 1f);
        // Torn flesh chunks — exposed pink muscle scattered across the mass.
        pixmap.setColor(0.56f, 0.20f, 0.22f, 1f);
        for (int chunkIndex = 0; chunkIndex < 9; chunkIndex++) {
            int chunkX = 36 + random.nextInt(64);
            int chunkY = 18 + random.nextInt(26);
            pixmap.fillCircle(chunkX, chunkY, random.nextInt(3) + 2);
        }
        // Brighter muscle striations / fresh meat highlights.
        pixmap.setColor(0.66f, 0.26f, 0.26f, 1f);
        for (int striationIndex = 0; striationIndex < 7; striationIndex++) {
            int striationX = 40 + random.nextInt(56);
            int striationY = 20 + random.nextInt(22);
            pixmap.fillCircle(striationX, striationY, 1 + random.nextInt(2));
        }
        // Darkest congealed clots pocked through the gore.
        pixmap.setColor(0.11f, 0.02f, 0.03f, 1f);
        for (int clotIndex = 0; clotIndex < 8; clotIndex++) {
            int clotX = 40 + random.nextInt(56);
            int clotY = 20 + random.nextInt(24);
            pixmap.fillCircle(clotX, clotY, 1 + random.nextInt(3));
        }
        // Splintered bone shards — pale angular fragments with a dark seated edge.
        for (int boneIndex = 0; boneIndex < 4; boneIndex++) {
            int boneX = 46 + random.nextInt(48);
            int boneY = 22 + random.nextInt(16);
            int boneLength = 4 + random.nextInt(5);
            pixmap.setColor(0.10f, 0.06f, 0.06f, 1f);
            pixmap.fillRectangle(boneX - 1, boneY - 1, boneLength + 2, 4);
            pixmap.setColor(0.84f, 0.79f, 0.66f, 1f);
            pixmap.fillRectangle(boneX, boneY, boneLength, 2);
        }
        // Glossy fresh-blood specular flecks — the wet sheen catching light.
        pixmap.setColor(0.72f, 0.12f, 0.12f, 1f);
        for (int sheenIndex = 0; sheenIndex < 12; sheenIndex++) {
            pixmap.drawPixel(38 + random.nextInt(60), 22 + random.nextInt(22));
        }
        pixmap.setColor(0.88f, 0.55f, 0.55f, 1f);
        for (int glintIndex = 0; glintIndex < 5; glintIndex++) {
            pixmap.drawPixel(44 + random.nextInt(50), 24 + random.nextInt(16));
        }
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

    // Blood stain ('.') — an irregular pooled splatter: a dark dried rim, a wetter
    // red body, a near-black congealed centre, glossy specular highlights, and a
    // radial spray of cast-off droplets thrown outward from the impact.
    private static Texture generateBloodTexture() {
        Pixmap pixmap = new Pixmap(96, 48, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        java.util.Random random = new java.util.Random(0x5318DEADL);
        int centerX = 48;
        int centerY = 24;

        // Outer cast-off droplet spray (under the pool so the body overlaps inner drops).
        scatterDroplets(pixmap, random, centerX, centerY, 44, 46, 0.34f, 0.02f, 0.02f);
        // Dried, darkened outer rim of the pool.
        organicBlob(pixmap, random, centerX, centerY, 22, 18, 0.24f, 0.02f, 0.02f, 1f);
        // Wet red body of the splatter.
        organicBlob(pixmap, random, centerX, centerY, 17, 16, 0.44f, 0.03f, 0.03f, 1f);
        // Congealed near-black centre where the blood pooled deepest.
        organicBlob(pixmap, random, centerX, centerY, 9, 10, 0.18f, 0.01f, 0.01f, 1f);
        // A couple of thin trailing smears running off the main pool.
        pixmap.setColor(0.30f, 0.02f, 0.02f, 1f);
        for (int smearIndex = 0; smearIndex < 3; smearIndex++) {
            float angleRadians = random.nextFloat() * MathUtils.PI2;
            int endX = centerX + Math.round(MathUtils.cos(angleRadians) * (24 + random.nextInt(14)));
            int endY = centerY + Math.round(MathUtils.sin(angleRadians) * (10 + random.nextInt(6)));
            pixmap.drawLine(centerX, centerY, endX, endY);
        }
        // Glossy wet-sheen specular flecks catching the light.
        pixmap.setColor(0.66f, 0.10f, 0.10f, 1f);
        for (int sheenIndex = 0; sheenIndex < 9; sheenIndex++) {
            pixmap.drawPixel(centerX - 14 + random.nextInt(28), centerY - 8 + random.nextInt(16));
        }
        pixmap.setColor(0.82f, 0.45f, 0.45f, 1f);
        pixmap.drawPixel(centerX - 4, centerY - 3);
        pixmap.drawPixel(centerX + 3, centerY + 2);
        return finalize(pixmap);
    }

    // Oil / fluid pool ('O') — a viscous near-black puddle with a glossy reflective
    // surface: an iridescent rainbow sheen swirling across the body, bright specular
    // highlights for the wet gloss, and a thin lighter meniscus around the rim.
    private static Texture generateOilTexture() {
        Pixmap pixmap = new Pixmap(96, 48, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        java.util.Random random = new java.util.Random(0x011CAFEL);
        int centerX = 48;
        int centerY = 24;

        // Stray flung droplets around the pool.
        scatterDroplets(pixmap, random, centerX, centerY, 42, 28, 0.05f, 0.07f, 0.08f);
        // Lighter meniscus / outer film of the spreading pool.
        organicBlob(pixmap, random, centerX, centerY, 24, 18, 0.08f, 0.10f, 0.11f, 1f);
        // Deep viscous near-black body.
        organicBlob(pixmap, random, centerX, centerY, 19, 18, 0.03f, 0.04f, 0.05f, 1f);

        // Iridescent rainbow sheen — translucent swirls of shifting hue across the surface.
        float[][] sheenPalette = {
                {0.45f, 0.12f, 0.55f},  // violet
                {0.10f, 0.25f, 0.62f},  // blue
                {0.10f, 0.55f, 0.45f},  // teal-green
                {0.62f, 0.45f, 0.12f},  // amber
        };
        for (int swirlIndex = 0; swirlIndex < 16; swirlIndex++) {
            float[] hue = sheenPalette[random.nextInt(sheenPalette.length)];
            int swirlX = centerX - 16 + random.nextInt(32);
            int swirlY = centerY - 8 + random.nextInt(16);
            int swirlRadius = 2 + random.nextInt(4);
            pixmap.setColor(hue[0], hue[1], hue[2], 0.30f);
            pixmap.fillCircle(swirlX, swirlY, swirlRadius);
        }
        // Bright wet specular highlights — the gloss reflecting overhead light.
        pixmap.setColor(0.55f, 0.70f, 0.80f, 0.55f);
        for (int glossIndex = 0; glossIndex < 6; glossIndex++) {
            pixmap.fillCircle(centerX - 12 + random.nextInt(24), centerY - 6 + random.nextInt(10), 1);
        }
        pixmap.setColor(0.85f, 0.92f, 1.0f, 0.7f);
        pixmap.drawPixel(centerX - 8, centerY - 4);
        pixmap.drawPixel(centerX + 6, centerY + 3);
        return finalize(pixmap);
    }

    // Small first-aid kit ('+') — compact white medical case with a relief red cross,
    // top-lit gradient shell, lid seam, recessed latch, corner rivets and a soft
    // medical-green floor pool. 128×160 (4× the legacy 32×40, identical 0.8 aspect so
    // the on-screen billboard size is unchanged).
    // Package-private (not private): reused by ItemIconTextures to show the
    // same ground-pickup sprite inside the inventory menu.
    static Texture generateStimTexture() {
        final int textureWidth = 128, textureHeight = 160;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Floor light pool (medical green-cyan).
        glowPool(pixmap, 64, 150, 56, 10, 0.20f, 0.85f, 0.70f, 0.55f);

        final int bodyLeft = 22, bodyRight = 106, bodyTop = 16, bodyBottom = 140;
        final int bodyWidth = bodyRight - bodyLeft, bodyHeight = bodyBottom - bodyTop;

        // Contact shadow beneath the case.
        pixmap.setColor(0f, 0f, 0f, 0.30f);
        pixmap.fillRectangle(bodyLeft + 6, bodyBottom, bodyWidth - 8, 5);

        // Case shell: dark outline + top-lit white gradient.
        pixmap.setColor(0.10f, 0.11f, 0.12f, 1f);
        pixmap.fillRectangle(bodyLeft - 2, bodyTop - 2, bodyWidth + 4, bodyHeight + 4);
        verticalGradientPanel(pixmap, bodyLeft, bodyTop, bodyWidth, bodyHeight,
                              0.84f, 0.87f, 0.88f, 0.12f, 0.22f);
        // Left highlight / right shadow bevel.
        pixmap.setColor(1f, 1f, 1f, 0.55f);
        pixmap.fillRectangle(bodyLeft, bodyTop, 3, bodyHeight);
        pixmap.setColor(0f, 0f, 0f, 0.28f);
        pixmap.fillRectangle(bodyRight - 3, bodyTop, 3, bodyHeight);

        // Lid seam across the upper third.
        pixmap.setColor(0.30f, 0.32f, 0.34f, 1f);
        pixmap.fillRectangle(bodyLeft, bodyTop + 34, bodyWidth, 3);
        pixmap.setColor(1f, 1f, 1f, 0.40f);
        pixmap.fillRectangle(bodyLeft, bodyTop + 37, bodyWidth, 1);

        // Recessed latch at front-centre.
        pixmap.setColor(0.18f, 0.19f, 0.21f, 1f);
        pixmap.fillRectangle(54, bodyBottom - 22, 20, 14);
        pixmap.setColor(0.55f, 0.58f, 0.62f, 1f);
        pixmap.fillRectangle(57, bodyBottom - 19, 14, 4);

        // Red cross — dark outline, bright fill, inner highlight for slight relief.
        final int centerX = 64, centerY = bodyTop + 70;
        final int armHalf = 12, armReach = 34;
        pixmap.setColor(0.45f, 0.04f, 0.04f, 1f);
        pixmap.fillRectangle(centerX - armHalf - 2, centerY - armReach - 2, (armHalf + 2) * 2, (armReach + 2) * 2);
        pixmap.fillRectangle(centerX - armReach - 2, centerY - armHalf - 2, (armReach + 2) * 2, (armHalf + 2) * 2);
        pixmap.setColor(0.86f, 0.10f, 0.10f, 1f);
        pixmap.fillRectangle(centerX - armHalf, centerY - armReach, armHalf * 2, armReach * 2);
        pixmap.fillRectangle(centerX - armReach, centerY - armHalf, armReach * 2, armHalf * 2);
        pixmap.setColor(1f, 0.34f, 0.30f, 0.60f);
        pixmap.fillRectangle(centerX - armHalf + 2, centerY - armReach + 2, 4, armReach * 2 - 4);
        pixmap.fillRectangle(centerX - armReach + 2, centerY - armHalf + 2, armReach * 2 - 4, 4);

        // Corner rivets.
        boltStud(pixmap, bodyLeft + 6,  bodyTop + 6);
        boltStud(pixmap, bodyRight - 6, bodyTop + 6);
        boltStud(pixmap, bodyLeft + 6,  bodyBottom - 6);
        boltStud(pixmap, bodyRight - 6, bodyBottom - 6);

        return finalize(pixmap);
    }

    // Field medkit ('H') — heavy olive-drab hard case: recessed white cross panel,
    // carry handle, twin clasps, green status LED with bloom, hazard-stripe foot and a
    // teal floor pool. 192×224 (4× the legacy 48×56, identical 0.857 aspect).
    // Package-private (not private): reused by ItemIconTextures.
    static Texture generateFieldMedkitTexture() {
        final int textureWidth = 192, textureHeight = 224;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        glowPool(pixmap, 96, 210, 84, 12, 0.20f, 0.85f, 0.70f, 0.55f);

        // Carry handle above the case (with grip cut-out).
        pixmap.setColor(0.16f, 0.17f, 0.12f, 1f);
        pixmap.fillRectangle(66, 6, 60, 16);
        pixmap.setColor(0f, 0f, 0f, 0f);
        Pixmap.Blending previousHandleBlending = pixmap.getBlending();
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.fillRectangle(74, 12, 44, 10);
        pixmap.setBlending(previousHandleBlending);
        pixmap.setColor(0.34f, 0.36f, 0.22f, 1f);
        pixmap.fillRectangle(66, 6, 60, 3);

        final int bodyLeft = 16, bodyRight = 176, bodyTop = 24, bodyBottom = 200;
        final int bodyWidth = bodyRight - bodyLeft, bodyHeight = bodyBottom - bodyTop;

        // Case: dark outline + olive vertical gradient.
        pixmap.setColor(0.08f, 0.09f, 0.06f, 1f);
        pixmap.fillRectangle(bodyLeft - 3, bodyTop - 3, bodyWidth + 6, bodyHeight + 6);
        verticalGradientPanel(pixmap, bodyLeft, bodyTop, bodyWidth, bodyHeight,
                              0.40f, 0.45f, 0.27f, 0.18f, 0.28f);
        // Bevels.
        pixmap.setColor(1f, 1f, 1f, 0.18f);
        pixmap.fillRectangle(bodyLeft, bodyTop, bodyWidth, 3);
        pixmap.fillRectangle(bodyLeft, bodyTop, 3, bodyHeight);
        pixmap.setColor(0f, 0f, 0f, 0.30f);
        pixmap.fillRectangle(bodyLeft, bodyBottom - 3, bodyWidth, 3);
        pixmap.fillRectangle(bodyRight - 3, bodyTop, 3, bodyHeight);

        // Lid seam + clasps.
        pixmap.setColor(0.12f, 0.13f, 0.09f, 1f);
        pixmap.fillRectangle(bodyLeft, bodyTop + 96, bodyWidth, 4);
        pixmap.setColor(0.62f, 0.64f, 0.40f, 1f);
        pixmap.fillRectangle(40, bodyTop + 90, 16, 16);
        pixmap.fillRectangle(136, bodyTop + 90, 16, 16);

        // Recessed white cross panel on the upper lid.
        final int panelLeft = 56, panelTop = bodyTop + 14, panelSize = 80;
        pixmap.setColor(0.10f, 0.11f, 0.10f, 1f);
        pixmap.fillRectangle(panelLeft - 3, panelTop - 3, panelSize + 6, panelSize + 6);
        verticalGradientPanel(pixmap, panelLeft, panelTop, panelSize, panelSize,
                              0.86f, 0.88f, 0.86f, 0.10f, 0.18f);

        // Bold red cross on the panel.
        final int centerX = panelLeft + panelSize / 2;
        final int centerY = panelTop + panelSize / 2;
        final int armHalf = 12, armReach = 32;
        pixmap.setColor(0.45f, 0.04f, 0.04f, 1f);
        pixmap.fillRectangle(centerX - armHalf - 2, centerY - armReach - 2, (armHalf + 2) * 2, (armReach + 2) * 2);
        pixmap.fillRectangle(centerX - armReach - 2, centerY - armHalf - 2, (armReach + 2) * 2, (armHalf + 2) * 2);
        pixmap.setColor(0.86f, 0.10f, 0.10f, 1f);
        pixmap.fillRectangle(centerX - armHalf, centerY - armReach, armHalf * 2, armReach * 2);
        pixmap.fillRectangle(centerX - armReach, centerY - armHalf, armReach * 2, armHalf * 2);
        pixmap.setColor(1f, 0.34f, 0.30f, 0.55f);
        pixmap.fillRectangle(centerX - armHalf + 2, centerY - armReach + 2, 4, armReach * 2 - 4);
        pixmap.fillRectangle(centerX - armReach + 2, centerY - armHalf + 2, armReach * 2 - 4, 4);

        // Status LED (green) with soft bloom, lower-right.
        pixmap.setColor(0.10f, 0.95f, 0.30f, 0.35f);
        pixmap.fillCircle(150, bodyBottom - 28, 10);
        pixmap.setColor(0.20f, 1f, 0.40f, 1f);
        pixmap.fillCircle(150, bodyBottom - 28, 5);
        pixmap.setColor(0.80f, 1f, 0.85f, 1f);
        pixmap.fillCircle(149, bodyBottom - 29, 2);

        // Hazard-stripe foot band.
        hazardStripeBand(pixmap, bodyLeft + 6, bodyBottom - 18, bodyWidth - 12, 10,
                         0.85f, 0.72f, 0.10f);

        // Reinforced corner rivets.
        boltStud(pixmap, bodyLeft + 8,  bodyTop + 8);
        boltStud(pixmap, bodyRight - 8, bodyTop + 8);
        boltStud(pixmap, bodyLeft + 8,  bodyBottom - 8);
        boltStud(pixmap, bodyRight - 8, bodyBottom - 8);

        return finalize(pixmap);
    }

    // Armour shard ('a') — a fractured chamfered steel plate with a diagonal brushed
    // gradient, gouged scratches, a charged cyan rim along the broken edges, a central
    // power cell and a cyan floor pool. 160×160 (4× the legacy 40×40, identical aspect).
    private static Texture generateArmourShardTexture() {
        final int textureWidth = 160, textureHeight = 160;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        glowPool(pixmap, 80, 142, 58, 11, 0.10f, 0.80f, 0.92f, 0.60f);

        final int left = 26, right = 134, top = 18, bottom = 126;
        final int topLeftLeg = 34, topRightLeg = 16, bottomRightLeg = 26, bottomLeftLeg = 30;

        // Dark backing plate (slightly larger, same chamfer) reads as a thin outline.
        pixmap.setColor(0.05f, 0.08f, 0.09f, 1f);
        pixmap.fillRectangle(left - 3, top - 3, (right - left) + 6, (bottom - top) + 6);
        chamferCorners(pixmap, left - 3, top - 3, right + 3, bottom + 3,
                       topLeftLeg + 3, topRightLeg + 3, bottomRightLeg + 3, bottomLeftLeg + 3);

        // Steel face: diagonal brushed gradient (lighter toward the top-left).
        for (int pixelRow = top; pixelRow < bottom; pixelRow++) {
            float verticalFraction = (pixelRow - top) / (float) (bottom - top);
            for (int pixelColumn = left; pixelColumn < right; pixelColumn++) {
                float horizontalFraction = (pixelColumn - left) / (float) (right - left);
                float diagonal = (horizontalFraction + verticalFraction) * 0.5f;
                float brush = (portalNoise(pixelColumn, pixelRow) - 0.5f) * 0.06f;
                float shade = 0.50f - diagonal * 0.24f + brush;
                pixmap.setColor(shade * 0.74f, shade * 0.90f, shade, 1f);
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }
        chamferCorners(pixmap, left, top, right, bottom,
                       topLeftLeg, topRightLeg, bottomRightLeg, bottomLeftLeg);

        // Brushed scratch + deep gouge across the face.
        pixmap.setColor(0.62f, 0.74f, 0.80f, 0.50f);
        pixmap.fillRectangle(left + 14, top + 40, (right - left) - 30, 2);
        pixmap.setColor(0.16f, 0.22f, 0.26f, 1f);
        pixmap.fillRectangle(left + 20, top + 64, (right - left) - 44, 3);

        // Charged cyan rim along the upper chamfer edges + top.
        pixmap.setColor(0.30f, 0.95f, 1.0f, 1f);
        pixmap.drawLine(left + topLeftLeg, top, left, top + topLeftLeg);
        pixmap.drawLine(left + topLeftLeg, top + 1, left + 1, top + topLeftLeg);
        pixmap.drawLine(right - topRightLeg, top, right, top + topRightLeg);
        pixmap.drawLine(left + topLeftLeg, top, right - topRightLeg, top);
        // Dimmer cyan along the lower chamfers.
        pixmap.setColor(0.12f, 0.62f, 0.74f, 1f);
        pixmap.drawLine(right, bottom - bottomRightLeg, right - bottomRightLeg, bottom);
        pixmap.drawLine(left, bottom - bottomLeftLeg, left + bottomLeftLeg, bottom);

        // Central cyan power cell with bloom and specular pip.
        pixmap.setColor(0.10f, 0.80f, 0.92f, 0.30f);
        pixmap.fillCircle(80, 78, 16);
        pixmap.setColor(0.30f, 0.95f, 1.0f, 1f);
        pixmap.fillCircle(80, 78, 8);
        pixmap.setColor(0.85f, 1.0f, 1.0f, 1f);
        pixmap.fillCircle(78, 76, 3);

        return finalize(pixmap);
    }

    // Security vest ('A') — a tactical body-armour rig: gunmetal torso with a V-collar,
    // shoulder pads, cyan ballistic chest plates, a glowing UAC diamond emblem, tactical
    // straps with buckles, MOLLE webbing and a cyan floor pool. 224×288 (4× the legacy
    // 56×72, identical 0.778 aspect).
    private static Texture generateSecurityVestTexture() {
        final int textureWidth = 224, textureHeight = 288;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        glowPool(pixmap, 112, 272, 92, 13, 0.10f, 0.78f, 0.90f, 0.55f);

        // Shoulder pads (outline + gradient + ridge highlight).
        pixmap.setColor(0.08f, 0.09f, 0.10f, 1f);
        pixmap.fillRectangle(16, 24, 84, 50);
        pixmap.fillRectangle(124, 24, 84, 50);
        verticalGradientPanel(pixmap, 20, 28, 76, 42, 0.24f, 0.27f, 0.30f, 0.20f, 0.30f);
        verticalGradientPanel(pixmap, 128, 28, 76, 42, 0.24f, 0.27f, 0.30f, 0.20f, 0.30f);
        pixmap.setColor(0.42f, 0.46f, 0.50f, 1f);
        pixmap.fillRectangle(20, 28, 76, 3);
        pixmap.fillRectangle(128, 28, 76, 3);

        final int bodyLeft = 40, bodyRight = 184, bodyTop = 52, bodyBottom = 258;
        final int bodyWidth = bodyRight - bodyLeft, bodyHeight = bodyBottom - bodyTop;

        // Torso: dark outline + gunmetal vertical gradient.
        pixmap.setColor(0.06f, 0.07f, 0.08f, 1f);
        pixmap.fillRectangle(bodyLeft - 4, bodyTop - 2, bodyWidth + 8, bodyHeight + 6);
        verticalGradientPanel(pixmap, bodyLeft, bodyTop, bodyWidth, bodyHeight,
                              0.22f, 0.25f, 0.28f, 0.22f, 0.30f);

        // Collar V-notch (transparent cut at top-centre) with a lit rim.
        Pixmap.Blending previousCollarBlending = pixmap.getBlending();
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fillTriangle(92, bodyTop - 2, 132, bodyTop - 2, 112, bodyTop + 34);
        pixmap.setBlending(previousCollarBlending);
        pixmap.setColor(0.40f, 0.44f, 0.48f, 1f);
        pixmap.drawLine(92, bodyTop, 112, bodyTop + 34);
        pixmap.drawLine(132, bodyTop, 112, bodyTop + 34);

        // Side flank panels (darker recesses).
        pixmap.setColor(0.16f, 0.18f, 0.20f, 1f);
        pixmap.fillRectangle(bodyLeft, bodyTop + 40, 16, bodyHeight - 70);
        pixmap.fillRectangle(bodyRight - 16, bodyTop + 40, 16, bodyHeight - 70);

        // Central seam.
        pixmap.setColor(0.10f, 0.11f, 0.12f, 1f);
        pixmap.fillRectangle(110, bodyTop + 36, 4, bodyHeight - 60);

        // Cyan ballistic chest plates with inner glow lines.
        beveledPanel(pixmap, 56, 96, 50, 78, 0.08f, 0.62f, 0.74f, 1.4f, 0.55f);
        beveledPanel(pixmap, 118, 96, 50, 78, 0.08f, 0.62f, 0.74f, 1.4f, 0.55f);
        pixmap.setColor(0.30f, 0.92f, 1.0f, 0.80f);
        pixmap.fillRectangle(62, 104, 38, 3);
        pixmap.fillRectangle(124, 104, 38, 3);

        // Tactical straps + buckles.
        pixmap.setColor(0.10f, 0.11f, 0.12f, 1f);
        pixmap.fillRectangle(bodyLeft + 4, 182, bodyWidth - 8, 10);
        pixmap.fillRectangle(bodyLeft + 4, 244, bodyWidth - 8, 10);
        pixmap.setColor(0.46f, 0.48f, 0.50f, 1f);
        pixmap.fillRectangle(104, 180, 16, 14);
        pixmap.fillRectangle(104, 242, 16, 14);

        // MOLLE webbing ticks across the lower torso.
        pixmap.setColor(0.13f, 0.14f, 0.16f, 1f);
        for (int tickColumn = bodyLeft + 12; tickColumn < bodyRight - 12; tickColumn += 18) {
            pixmap.fillRectangle(tickColumn, 204, 3, 30);
        }

        // Central UAC emblem: cyan-rimmed diamond with a bright glowing core (drawn last).
        pixmap.setColor(0.05f, 0.40f, 0.50f, 1f);
        pixmap.fillTriangle(112, 188, 92, 212, 112, 236);
        pixmap.fillTriangle(112, 188, 132, 212, 112, 236);
        pixmap.setColor(0.40f, 0.95f, 1.0f, 1f);
        pixmap.fillTriangle(112, 196, 100, 212, 112, 228);
        pixmap.fillTriangle(112, 196, 124, 212, 112, 228);
        pixmap.setColor(0.85f, 1.0f, 1.0f, 1f);
        pixmap.fillCircle(112, 212, 4);

        // Rivets: shoulder pads + lower torso corners.
        boltStud(pixmap, 26, 34);  boltStud(pixmap, 90, 34);
        boltStud(pixmap, 134, 34); boltStud(pixmap, 198, 34);
        boltStud(pixmap, bodyLeft + 8, bodyBottom - 10);
        boltStud(pixmap, bodyRight - 8, bodyBottom - 10);

        return finalize(pixmap);
    }

    // UAC Interdimensional Rift Portal ('>') — full-height gateway standing as tall as walls.
    // High-resolution 192×256 render: gradient-bevelled steel frame with rivets and a recessed
    // inner channel, a swirling 3-arm energy vortex (void-core → indigo → deep blue → electric
    // blue → cyan → near-white edge flash) carved by an event-horizon dark ring and fine
    // concentric shimmer rings, glowing corner energy nodes, segmented vertical conduits, a row of
    // top discharge emitters, and diagonal UAC hazard chevrons at the base. Beacon visible from the
    // far end of any corridor; the 3:4 aspect ratio matches the previous 48×64 sprite exactly.
    private static Texture generatePortalTexture() {
        int textureWidth  = 192;
        int textureHeight = 256;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);

        // --- Steel frame base with a diagonal brushed gradient (lighter top-left) ---
        for (int pixelRow = 0; pixelRow < textureHeight; pixelRow++) {
            for (int pixelColumn = 0; pixelColumn < textureWidth; pixelColumn++) {
                float diagonal = (pixelColumn + pixelRow) / (float)(textureWidth + textureHeight);
                float brushNoise = (portalNoise(pixelColumn, pixelRow) - 0.5f) * 0.04f;
                float shade = 0.30f - diagonal * 0.16f + brushNoise;
                pixmap.setColor(shade * 0.92f, shade * 0.98f, shade * 1.15f, 1f);
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }

        // Bevel highlight: top and left edges lighter (4 px)
        pixmap.setColor(0.34f, 0.36f, 0.42f, 1f);
        pixmap.fillRectangle(0, 0, textureWidth, 4);
        pixmap.fillRectangle(0, 0, 4, textureHeight);
        // Bevel shadow: bottom and right edges darker (4 px)
        pixmap.setColor(0.07f, 0.07f, 0.09f, 1f);
        pixmap.fillRectangle(0, textureHeight - 4, textureWidth, 4);
        pixmap.fillRectangle(textureWidth - 4, 0, 4, textureHeight);

        // --- Frame rivets — bolt heads with a highlight dot, set into the visible steel bands
        // (left edge, right edge, top lintel) so the energy field never paints over them ---
        int[][] rivetPositions = {
                // top lintel
                { 8, 8 }, { 64, 8 }, { 96, 8 }, { 128, 8 }, { 184, 8 },
                // left edge
                { 8, 56 }, { 8, 112 }, { 8, 168 }, { 8, 218 },
                // right edge
                { 184, 56 }, { 184, 112 }, { 184, 168 }, { 184, 218 }
        };
        for (int[] rivet : rivetPositions) {
            pixmap.setColor(0.06f, 0.06f, 0.08f, 1f);
            pixmap.fillCircle(rivet[0], rivet[1], 4);
            pixmap.setColor(0.40f, 0.42f, 0.48f, 1f);
            pixmap.fillCircle(rivet[0], rivet[1], 2);
            pixmap.setColor(0.62f, 0.64f, 0.70f, 1f);
            pixmap.drawPixel(rivet[0] - 1, rivet[1] - 1);
        }

        // --- Portal opening bounds (inside the steel frame) ---
        int portalLeft   = 16;
        int portalTop    = 16;
        int portalRight  = 176;  // exclusive — columns 176..191 are the right frame
        int portalBottom = 228;  // exclusive — rows 228..255 are the hazard base
        int portalWidth  = portalRight  - portalLeft;   // 160
        int portalHeight = portalBottom - portalTop;    // 212

        // Recessed inner channel: a dark groove framing the opening
        pixmap.setColor(0.05f, 0.05f, 0.07f, 1f);
        pixmap.fillRectangle(portalLeft - 3, portalTop - 3, portalWidth + 6, 3);
        pixmap.fillRectangle(portalLeft - 3, portalBottom,    portalWidth + 6, 3);
        pixmap.fillRectangle(portalLeft - 3, portalTop - 3, 3, portalHeight + 6);
        pixmap.fillRectangle(portalRight,    portalTop - 3, 3, portalHeight + 6);

        float centerX = portalLeft + portalWidth  / 2f - 0.5f;
        float centerY = portalTop  + portalHeight / 2f - 0.5f;
        float halfWidth  = portalWidth  / 2f;
        float halfHeight = portalHeight / 2f;

        // --- Energy field: pixel-by-pixel swirling vortex ---
        for (int pixelRow = portalTop; pixelRow < portalBottom; pixelRow++) {
            for (int pixelColumn = portalLeft; pixelColumn < portalRight; pixelColumn++) {
                float normalizedX = (pixelColumn - centerX) / halfWidth;
                float normalizedY = (pixelRow    - centerY) / halfHeight;
                float distanceFromCenter =
                        (float) Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
                float angleRadians = (float) Math.atan2(normalizedY, normalizedX);

                float red, green, blue;
                if (distanceFromCenter < 0.10f) {
                    // Void core
                    red = 0.01f; green = 0.005f; blue = 0.05f;
                } else if (distanceFromCenter < 0.28f) {
                    float interpolationFactor = (distanceFromCenter - 0.10f) / 0.18f;
                    red   = 0.01f  + interpolationFactor * 0.17f;
                    green = 0.005f + interpolationFactor * 0.035f;
                    blue  = 0.05f  + interpolationFactor * 0.55f;
                } else if (distanceFromCenter < 0.48f) {
                    float interpolationFactor = (distanceFromCenter - 0.28f) / 0.20f;
                    red   = 0.18f + interpolationFactor * (-0.10f);
                    green = 0.04f + interpolationFactor * 0.11f;
                    blue  = 0.60f + interpolationFactor * 0.30f;
                } else if (distanceFromCenter < 0.66f) {
                    float interpolationFactor = (distanceFromCenter - 0.48f) / 0.18f;
                    red   = 0.08f + interpolationFactor * (-0.03f);
                    green = 0.15f + interpolationFactor * 0.40f;
                    blue  = 0.90f + interpolationFactor * 0.08f;
                } else if (distanceFromCenter < 0.84f) {
                    float interpolationFactor = (distanceFromCenter - 0.66f) / 0.18f;
                    red   = 0.05f + interpolationFactor * 0.35f;
                    green = 0.55f + interpolationFactor * 0.30f;
                    blue  = 0.98f + interpolationFactor * 0.02f;
                } else if (distanceFromCenter <= 1.0f) {
                    float interpolationFactor = (distanceFromCenter - 0.84f) / 0.16f;
                    red   = 0.40f + interpolationFactor * 0.55f;
                    green = 0.85f + interpolationFactor * 0.13f;
                    blue  = 1.00f;
                } else {
                    // Corner pixels (outside ellipse but inside rectangle) — recessed groove
                    red = 0.05f; green = 0.05f; blue = 0.07f;
                }

                if (distanceFromCenter <= 1.0f) {
                    // Swirling spiral arms: 3 arms wound by distance into a vortex
                    float spiralPhase = angleRadians * 3f + distanceFromCenter * 11f;
                    float spiralArm   = (float) Math.cos(spiralPhase) * 0.5f + 0.5f;
                    float armStrength = 0.30f * (1.0f - distanceFromCenter * 0.4f);
                    float spiralFactor = 1.0f - armStrength + spiralArm * armStrength * 2f;

                    // Fine concentric shimmer rings layered on top of the swirl
                    float ringShimmer = (float) Math.cos(distanceFromCenter * Math.PI * 16) * 0.5f + 0.5f;
                    float ringStrength = 0.12f * (1.0f - distanceFromCenter);
                    float ringFactor = 1.0f - ringStrength + ringShimmer * ringStrength * 2f;

                    // Per-pixel plasma grain
                    float grain = 0.94f + portalNoise(pixelColumn, pixelRow) * 0.12f;

                    float factor = spiralFactor * ringFactor * grain;
                    red   = Math.min(1f, red   * factor);
                    green = Math.min(1f, green * factor);
                    blue  = Math.min(1f, blue  * factor);

                    // Event-horizon dark ring: dims a thin band so the core reads as a throat
                    float horizon = Math.abs(distanceFromCenter - 0.40f);
                    if (horizon < 0.05f) {
                        float dim = 0.45f + horizon / 0.05f * 0.55f;
                        red *= dim; green *= dim; blue *= dim;
                    }
                }

                pixmap.setColor(red, green, blue, 1f);
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }

        // --- Bright cyan energy border around the portal opening (2 px) ---
        pixmap.setColor(0.55f, 0.88f, 1.00f, 1f);
        pixmap.fillRectangle(portalLeft, portalTop, portalWidth, 2);                 // top
        pixmap.fillRectangle(portalLeft, portalBottom - 2, portalWidth, 2);          // bottom
        pixmap.fillRectangle(portalLeft, portalTop, 2, portalHeight);                // left
        pixmap.fillRectangle(portalRight - 2, portalTop, 2, portalHeight);           // right

        // --- Corner energy nodes (bright glowing discs at portal mouth corners) ---
        int[][] cornerNodes = {
                { portalLeft,  portalTop    },
                { portalRight, portalTop    },
                { portalLeft,  portalBottom },
                { portalRight, portalBottom }
        };
        for (int[] node : cornerNodes) {
            pixmap.setColor(0.40f, 0.80f, 1.00f, 1f);
            pixmap.fillCircle(node[0], node[1], 6);
            pixmap.setColor(0.95f, 0.99f, 1.00f, 1f);
            pixmap.fillCircle(node[0], node[1], 3);
        }

        // --- Segmented vertical energy conduits on the inner frame edges ---
        for (int conduitRow = portalTop; conduitRow < portalBottom; conduitRow += 16) {
            boolean bright = ((conduitRow - portalTop) / 16) % 2 == 0;
            pixmap.setColor(bright ? 0.45f : 0.20f, bright ? 0.85f : 0.55f, 1.00f, 1f);
            pixmap.fillRectangle(7,                  conduitRow, 3, 12);  // left conduit
            pixmap.fillRectangle(textureWidth - 10,  conduitRow, 3, 12);  // right conduit
        }

        // --- Top arch energy emitters (row of discharge ports across the lintel) ---
        for (int emitterIndex = 0; emitterIndex < 5; emitterIndex++) {
            int emitterX = 28 + emitterIndex * 32;
            boolean center = emitterIndex == 2;
            pixmap.setColor(center ? 0.85f : 0.60f, center ? 0.96f : 0.90f, 1.00f, 1f);
            pixmap.fillRectangle(emitterX, 0, center ? 24 : 18, center ? 10 : 8);
            // Discharge glow bleeding down into the frame
            pixmap.setColor(0.30f, 0.60f, 0.95f, 1f);
            pixmap.fillRectangle(emitterX + 4, center ? 10 : 8, center ? 16 : 10, 4);
        }

        // --- Diagonal UAC hazard chevrons at the base (yellow / black) ---
        int hazardCenterColumn = textureWidth / 2;
        for (int hazardRow = portalBottom; hazardRow < textureHeight - 4; hazardRow++) {
            for (int hazardColumn = 4; hazardColumn < textureWidth - 4; hazardColumn++) {
                int mirrored = Math.abs(hazardColumn - hazardCenterColumn);
                boolean isYellow = (((mirrored + hazardRow) / 12) % 2) == 0;
                pixmap.setColor(isYellow ? 0.92f : 0.07f,
                                isYellow ? 0.78f : 0.07f,
                                isYellow ? 0.03f : 0.06f,
                                1f);
                pixmap.drawPixel(hazardColumn, hazardRow);
            }
        }
        // Bright divider line at the portal-base boundary
        pixmap.setColor(0.55f, 0.88f, 1.00f, 1f);
        pixmap.fillRectangle(0, portalBottom, textureWidth, 2);

        // --- Energy spark accents scattered near the portal border ---
        int[][] sparks = {
                { 28, 32 }, { 162, 32 }, { 28, 200 }, { 162, 200 },
                { 20, 96 }, { 170, 96 }, { 20, 150 }, { 170, 150 },
                { 96, 24 }, { 96, 214 }
        };
        for (int[] spark : sparks) {
            pixmap.setColor(0.80f, 0.95f, 1.00f, 1f);
            pixmap.fillCircle(spark[0], spark[1], 2);
            pixmap.setColor(1.00f, 1.00f, 1.00f, 1f);
            pixmap.drawPixel(spark[0], spark[1]);
        }

        return finalize(pixmap);
    }

    // Deterministic value noise in [0,1] for procedural texture grain. Pure function of (x, y) so
    // the portal sprite is identical on every run; uses an integer hash to avoid object allocation.
    private static float portalNoise(int x, int y) {
        long hash = (long)x * 374761393 + (long)y * 668265263;
        hash = (hash ^ (hash >>> 13)) * 1274126177;
        hash = hash ^ (hash >>> 16);
        return ((int)(hash & 0xFFFF)) / 65535f;
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
        Pixmap pixmap = new Pixmap(96, 96, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Cooling-fin stacks flanking the cabinet.
        pixmap.setColor(0.14f, 0.15f, 0.18f, 1f);
        for (int finRow = 14; finRow <= 56; finRow += 6) {
            pixmap.fillRectangle(2, finRow, 12, 4);
            pixmap.fillRectangle(82, finRow, 12, 4);
        }
        // Main cabinet.
        beveledPanel(pixmap, 12, 6, 72, 84, 0.20f, 0.22f, 0.26f, 1.6f, 0.5f);
        // Top conduit pipes with stacked ceramic insulator discs.
        pixmap.setColor(0.30f, 0.31f, 0.35f, 1f);
        pixmap.fillRectangle(26, 0, 8, 8);
        pixmap.fillRectangle(62, 0, 8, 8);
        pixmap.setColor(0.55f, 0.50f, 0.42f, 1f);
        for (int discY = 1; discY <= 6; discY += 2) {
            pixmap.fillRectangle(25, discY, 10, 1);
            pixmap.fillRectangle(61, discY, 10, 1);
        }

        // ── Glowing reactor core (radial green-hot glow) ────────────────────
        int coreCenterX = 48, coreCenterY = 32, coreRadius = 20;
        pixmap.setColor(0.06f, 0.07f, 0.08f, 1f);
        pixmap.fillRectangle(coreCenterX - 22, coreCenterY - 22, 44, 44);
        for (int pixelRow = coreCenterY - coreRadius; pixelRow <= coreCenterY + coreRadius; pixelRow++) {
            for (int pixelColumn = coreCenterX - coreRadius; pixelColumn <= coreCenterX + coreRadius; pixelColumn++) {
                float normalizedX = (pixelColumn - coreCenterX) / (float) coreRadius;
                float normalizedY = (pixelRow - coreCenterY) / (float) coreRadius;
                float distance = (float) Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
                if (distance > 1f) continue;
                float red, green, blue;
                if (distance < 0.25f) {
                    red = 0.85f; green = 1.00f; blue = 0.80f;
                } else if (distance < 0.55f) {
                    float interpolationFactor = (distance - 0.25f) / 0.30f;
                    red   = 0.85f - 0.55f * interpolationFactor;
                    green = 1.00f - 0.10f * interpolationFactor;
                    blue  = 0.80f - 0.45f * interpolationFactor;
                } else {
                    float interpolationFactor = (distance - 0.55f) / 0.45f;
                    red   = 0.30f - 0.22f * interpolationFactor;
                    green = 0.90f - 0.62f * interpolationFactor;
                    blue  = 0.35f - 0.26f * interpolationFactor;
                }
                float ring   = (float) (Math.cos(distance * Math.PI * 5) * 0.5 + 0.5);
                float factor = 0.80f + 0.20f * ring;
                pixmap.setColor(red * factor, green * factor, blue * factor, 1f);
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }
        // Core housing frame + bolts.
        pixmap.setColor(0.32f, 0.34f, 0.38f, 1f);
        drawRectOutline(pixmap, coreCenterX - 22, coreCenterY - 22, 44, 44);
        boltStud(pixmap, coreCenterX - 20, coreCenterY - 20);
        boltStud(pixmap, coreCenterX + 20, coreCenterY - 20);
        boltStud(pixmap, coreCenterX - 20, coreCenterY + 20);
        boltStud(pixmap, coreCenterX + 20, coreCenterY + 20);
        // Electric arc filaments escaping the core.
        pixmap.setColor(0.70f, 1.00f, 0.85f, 1f);
        pixmap.drawPixel(coreCenterX + 10, coreCenterY - 14);
        pixmap.drawPixel(coreCenterX + 12, coreCenterY - 16);
        pixmap.drawPixel(coreCenterX - 12, coreCenterY + 12);
        pixmap.drawPixel(coreCenterX - 14, coreCenterY + 13);

        // ── Lower control panel ─────────────────────────────────────────────
        pixmap.setColor(0.13f, 0.14f, 0.17f, 1f);
        pixmap.fillRectangle(16, 60, 64, 26);
        // Twin gauges flanking a breaker bank.
        drawGauge(pixmap, 28, 72, 0.35f, 0.90f, 0.40f);
        drawGauge(pixmap, 68, 72, 0.95f, 0.75f, 0.20f);
        for (int breakerIndex = 0; breakerIndex < 4; breakerIndex++) {
            boolean on = (breakerIndex % 2 == 0);
            pixmap.setColor(on ? 0.30f : 0.20f, on ? 0.85f : 0.22f, on ? 0.40f : 0.25f, 1f);
            pixmap.fillRectangle(40 + breakerIndex * 6, 66, 4, 12);
        }
        // Hazard chevron at the base.
        hazardStripeBand(pixmap, 12, 84, 72, 6, 0.85f, 0.72f, 0.18f);
        return finalize(pixmap);
    }

    private static Texture generateBioPodTexture() {
        Pixmap pixmap = new Pixmap(96, 144, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        final int glassLeft = 20, glassRight = 76, glassTop = 22, glassBottom = 124;

        // ── Base pedestal with control panel ────────────────────────────────
        beveledPanel(pixmap, 10, 124, 76, 20, 0.24f, 0.26f, 0.30f, 1.5f, 0.5f);
        pixmap.setColor(0.08f, 0.20f, 0.24f, 1f);
        pixmap.fillRectangle(16, 128, 30, 12);            // readout screen
        pixmap.setColor(0.30f, 0.85f, 0.92f, 1f);
        pixmap.fillRectangle(18, 130, 24, 2);
        pixmap.fillRectangle(18, 133, 16, 2);
        pixmap.fillRectangle(18, 136, 20, 2);
        pixmap.setColor(0.30f, 0.95f, 0.40f, 1f); pixmap.fillRectangle(52, 129, 4, 4);
        pixmap.setColor(0.95f, 0.70f, 0.20f, 1f); pixmap.fillRectangle(60, 129, 4, 4);
        pixmap.setColor(0.90f, 0.25f, 0.20f, 1f); pixmap.fillRectangle(68, 129, 4, 4);
        pixmap.setColor(0.40f, 0.42f, 0.48f, 1f); pixmap.fillRectangle(52, 135, 20, 4);
        boltStud(pixmap, 14, 128); boltStud(pixmap, 82, 128);

        // ── Top cap dome with valve + emitter strip ─────────────────────────
        beveledPanel(pixmap, 14, 4, 68, 18, 0.26f, 0.28f, 0.32f, 1.5f, 0.5f);
        pixmap.setColor(0.36f, 0.38f, 0.44f, 1f);
        pixmap.fillRectangle(22, 6, 52, 4);
        pixmap.setColor(0.30f, 0.32f, 0.36f, 1f);
        pixmap.fillRectangle(40, 0, 16, 6);
        pixmap.setColor(0.55f, 0.90f, 0.95f, 1f);
        pixmap.fillRectangle(34, 17, 28, 3);
        boltStud(pixmap, 18, 8); boltStud(pixmap, 78, 8);

        // ── Vertical frame struts joining the caps ──────────────────────────
        pixmap.setColor(0.22f, 0.24f, 0.28f, 1f);
        pixmap.fillRectangle(glassLeft - 4, glassTop, 4, glassBottom - glassTop);
        pixmap.fillRectangle(glassRight,    glassTop, 4, glassBottom - glassTop);

        // ── Glass tube fluid (cylindrical green shading) ────────────────────
        for (int column = glassLeft; column < glassRight; column++) {
            float across = (column - glassLeft) / (float) (glassRight - glassLeft - 1);
            float curve  = 1f - Math.min(1f, Math.abs(across - 0.40f) * 1.9f);
            pixmap.setColor(0.10f + 0.16f * curve, 0.34f + 0.34f * curve, 0.28f + 0.26f * curve, 1f);
            pixmap.fillRectangle(column, glassTop, 1, glassBottom - glassTop);
        }
        // Cyan containment glow rings around the glass.
        pixmap.setColor(0.40f, 0.90f, 0.95f, 0.8f);
        for (int ringY = glassTop + 14; ringY < glassBottom - 8; ringY += 28) {
            pixmap.fillRectangle(glassLeft, ringY, glassRight - glassLeft, 2);
        }
        // Curled specimen silhouette with a faint internal glow.
        int specimenX = 48, specimenY = 78;
        pixmap.setColor(0.07f, 0.16f, 0.12f, 1f);
        pixmap.fillCircle(specimenX, specimenY, 16);
        pixmap.fillCircle(specimenX + 8, specimenY - 12, 8);
        pixmap.fillTriangle(specimenX - 14, specimenY + 2, specimenX - 2, specimenY + 6, specimenX - 6, specimenY + 18);
        pixmap.fillTriangle(specimenX + 10, specimenY + 8, specimenX + 18, specimenY + 4, specimenX + 18, specimenY + 20);
        pixmap.setColor(0.20f, 0.45f, 0.32f, 1f);
        pixmap.fillCircle(specimenX + 2, specimenY - 2, 6);
        pixmap.setColor(0.85f, 0.40f, 0.45f, 1f);
        pixmap.drawPixel(specimenX + 9, specimenY - 13);
        // Glass highlight streaks (strong left, faint right).
        pixmap.setColor(0.80f, 0.95f, 0.92f, 0.7f);
        pixmap.fillRectangle(glassLeft + 3, glassTop + 2, 3, glassBottom - glassTop - 6);
        pixmap.setColor(0.60f, 0.80f, 0.78f, 0.4f);
        pixmap.fillRectangle(glassRight - 6, glassTop + 6, 1, glassBottom - glassTop - 14);
        // Rising bubbles of varied size.
        pixmap.setColor(0.80f, 0.95f, 0.90f, 1f);
        int[] bubbleColumns = { 34, 60, 44, 30, 64, 50, 38 };
        int[] bubbleRows    = { 110, 100, 90, 70, 64, 48, 36 };
        int[] bubbleRadii   = { 2, 1, 2, 1, 2, 1, 2 };
        for (int bubbleIndex = 0; bubbleIndex < bubbleColumns.length; bubbleIndex++) {
            pixmap.fillCircle(bubbleColumns[bubbleIndex], bubbleRows[bubbleIndex], bubbleRadii[bubbleIndex]);
        }
        // Condensation frost patches near the top of the glass.
        pixmap.setColor(0.70f, 0.85f, 0.88f, 0.5f);
        pixmap.fillRectangle(glassLeft + 2, glassTop + 2, 10, 6);
        pixmap.fillRectangle(glassRight - 14, glassTop + 4, 10, 5);
        return finalize(pixmap);
    }

    // Draws a single rifle standing vertically (barrel up) centred on slotColumn,
    // spanning rows ~14..60. Two palettes let each rack slot hold a distinct weapon.
    private static void drawRackRifle(Pixmap pixmap, int slotColumn,
                                      float stockRed, float stockGreen, float stockBlue,
                                      float metalRed, float metalGreen, float metalBlue) {
        // Barrel (upper, thin) with an edge highlight.
        pixmap.setColor(metalRed, metalGreen, metalBlue, 1f);
        pixmap.fillRectangle(slotColumn - 1, 14, 3, 22);
        pixmap.setColor(Math.min(1f, metalRed + 0.2f), Math.min(1f, metalGreen + 0.2f), Math.min(1f, metalBlue + 0.2f), 1f);
        pixmap.fillRectangle(slotColumn - 1, 14, 1, 22);
        // Receiver body (mid, wider).
        pixmap.setColor(metalRed * 0.8f + 0.04f, metalGreen * 0.8f + 0.04f, metalBlue * 0.8f + 0.04f, 1f);
        pixmap.fillRectangle(slotColumn - 3, 34, 7, 12);
        // Stock (lower, wood/poly) with a lit edge.
        pixmap.setColor(stockRed, stockGreen, stockBlue, 1f);
        pixmap.fillRectangle(slotColumn - 3, 46, 6, 14);
        pixmap.setColor(Math.min(1f, stockRed + 0.12f), Math.min(1f, stockGreen + 0.12f), Math.min(1f, stockBlue + 0.12f), 1f);
        pixmap.fillRectangle(slotColumn - 3, 46, 1, 14);
        // Magazine sitting in front of the receiver.
        pixmap.setColor(0.12f, 0.13f, 0.16f, 1f);
        pixmap.fillRectangle(slotColumn - 2, 44, 4, 9);
        // Trigger guard.
        pixmap.setColor(0.18f, 0.19f, 0.22f, 1f);
        pixmap.fillRectangle(slotColumn + 1, 50, 3, 4);
    }

    private static Texture generateWeaponRackTexture() {
        Pixmap pixmap = new Pixmap(96, 96, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Back pegboard panel.
        beveledPanel(pixmap, 6, 6, 84, 84, 0.20f, 0.21f, 0.25f, 1.5f, 0.55f);
        pixmap.setColor(0.12f, 0.13f, 0.16f, 1f);
        for (int holeRow = 12; holeRow < 84; holeRow += 8) {
            for (int holeColumn = 12; holeColumn < 84; holeColumn += 8) {
                pixmap.drawPixel(holeColumn, holeRow);
            }
        }
        // Top rail + bottom shelf.
        pixmap.setColor(0.30f, 0.32f, 0.36f, 1f);
        pixmap.fillRectangle(8, 10, 80, 4);
        pixmap.fillRectangle(8, 62, 80, 4);
        pixmap.setColor(0.42f, 0.44f, 0.48f, 1f);
        pixmap.fillRectangle(8, 10, 80, 1);
        pixmap.fillRectangle(8, 62, 80, 1);

        // Three rifles standing in the upper slots.
        drawRackRifle(pixmap, 20, 0.34f, 0.22f, 0.12f, 0.18f, 0.19f, 0.22f); // wood + steel
        drawRackRifle(pixmap, 40, 0.16f, 0.17f, 0.20f, 0.10f, 0.45f, 0.55f); // tactical + cyan
        drawRackRifle(pixmap, 60, 0.20f, 0.16f, 0.10f, 0.40f, 0.20f, 0.10f); // worn

        // Pistol hung on the right.
        pixmap.setColor(0.16f, 0.17f, 0.20f, 1f);
        pixmap.fillRectangle(76, 24, 10, 5);
        pixmap.fillRectangle(78, 29, 4, 7);
        pixmap.setColor(0.30f, 0.32f, 0.36f, 1f);
        pixmap.fillRectangle(76, 24, 10, 1);

        // Bottom shelf: grenades, magazines, ammo crate.
        pixmap.setColor(0.22f, 0.30f, 0.16f, 1f);
        pixmap.fillCircle(20, 74, 5); pixmap.fillCircle(32, 74, 5);
        pixmap.setColor(0.35f, 0.45f, 0.24f, 1f);
        pixmap.drawPixel(18, 72); pixmap.drawPixel(30, 72);
        pixmap.setColor(0.50f, 0.52f, 0.40f, 1f);
        pixmap.fillRectangle(19, 68, 3, 3); pixmap.fillRectangle(31, 68, 3, 3);  // grenade spoons
        pixmap.setColor(0.14f, 0.15f, 0.18f, 1f);
        pixmap.fillRectangle(44, 70, 5, 14); pixmap.fillRectangle(52, 70, 5, 14);
        pixmap.setColor(0.30f, 0.32f, 0.36f, 1f);
        pixmap.fillRectangle(44, 70, 5, 1); pixmap.fillRectangle(52, 70, 5, 1);
        pixmap.setColor(0.30f, 0.34f, 0.20f, 1f);
        pixmap.fillRectangle(64, 70, 22, 14);
        pixmap.setColor(0.40f, 0.44f, 0.28f, 1f);
        pixmap.fillRectangle(64, 70, 22, 1);
        pixmap.setColor(0.80f, 0.78f, 0.40f, 1f);
        pixmap.fillRectangle(67, 74, 3, 6); pixmap.fillRectangle(71, 74, 3, 6);
        pixmap.fillRectangle(75, 74, 3, 6); pixmap.fillRectangle(79, 74, 3, 6);  // "AMMO" stencil ticks

        // Corner bolts.
        boltStud(pixmap, 10, 10); boltStud(pixmap, 86, 10);
        boltStud(pixmap, 10, 86); boltStud(pixmap, 86, 86);
        return finalize(pixmap);
    }

    // Tall capped plasma canister (glass tube) for the advanced machine: machined
    // end caps, a vertical glow gradient, a left glass-sheen streak, hoop bands and
    // a few rising bubbles. Spans rows [topRow, bottomRow) at the given column.
    private static void drawTallPlasmaCanister(Pixmap pixmap, int x, int topRow, int bottomRow,
                                               float red, float green, float blue) {
        final int tubeWidth = 12;
        pixmap.setColor(0.28f, 0.30f, 0.34f, 1f);
        pixmap.fillRectangle(x - 1, topRow - 4, tubeWidth + 2, 5);   // top cap
        pixmap.fillRectangle(x - 1, bottomRow,  tubeWidth + 2, 5);   // bottom cap
        pixmap.setColor(0.40f, 0.42f, 0.47f, 1f);
        pixmap.fillRectangle(x - 1, topRow - 4, tubeWidth + 2, 1);   // cap highlight
        // Glowing fluid column (brighter at the top).
        for (int row = topRow; row < bottomRow; row++) {
            float toBottom   = (row - topRow) / (float) (bottomRow - topRow);
            float brightness = 0.55f + 0.45f * (1f - toBottom);
            pixmap.setColor(red * brightness, green * brightness, blue * brightness, 1f);
            pixmap.fillRectangle(x, row, tubeWidth, 1);
        }
        // Bright glass sheen streak down the left edge.
        pixmap.setColor(Math.min(1f, red + 0.35f), Math.min(1f, green + 0.35f), Math.min(1f, blue + 0.35f), 1f);
        pixmap.fillRectangle(x + 1, topRow, 2, bottomRow - topRow);
        // Hoop bands.
        pixmap.setColor(0.22f, 0.24f, 0.28f, 1f);
        for (int hoopRow = topRow + 14; hoopRow < bottomRow - 6; hoopRow += 24) {
            pixmap.fillRectangle(x, hoopRow, tubeWidth, 2);
        }
        // Rising bubbles.
        pixmap.setColor(1f, 1f, 1f, 0.8f);
        for (int bubbleIndex = 0; bubbleIndex < 5; bubbleIndex++) {
            int bubbleRow    = bottomRow - 8 - bubbleIndex * ((bottomRow - topRow) / 6);
            int bubbleColumn = x + 4 + (bubbleIndex % 3) * 3;
            pixmap.drawPixel(bubbleColumn, bubbleRow);
        }
    }

    // Special equipment ('@') — an advanced UAC machine: beveled violet-grey chassis,
    // a vented top emitter housing with a violet glow port, twin tall plasma canisters
    // flanking a central violet→cyan energy aperture (radial glow, orbiting containment
    // ring, suspended core orb, corner emitter nodes), and a lower control panel with a
    // readout screen, keypad, gauge, dispenser tray and a hazard band.
    private static Texture generateVendorTexture() {
        final int textureWidth = 128, textureHeight = 128;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // ── Main chassis (dark tech violet-grey) ──
        beveledPanel(pixmap, 10, 6, 108, 116, 0.19f, 0.17f, 0.25f, 1.6f, 0.5f);
        // Top emitter housing with vents + violet glow port.
        pixmap.setColor(0.14f, 0.13f, 0.20f, 1f);
        pixmap.fillRectangle(38, 0, 52, 8);
        pixmap.setColor(0.66f, 0.46f, 0.96f, 1f);
        pixmap.fillRectangle(58, 0, 12, 5);
        pixmap.setColor(0.30f, 0.28f, 0.38f, 1f);
        for (int ventColumn = 42; ventColumn < 86; ventColumn += 6) {
            pixmap.fillRectangle(ventColumn, 2, 2, 4);
        }

        // ── Side plasma canisters ──
        drawTallPlasmaCanister(pixmap, 16,  26, 98, 0.88f, 0.30f, 0.34f);  // red plasma
        drawTallPlasmaCanister(pixmap, 100, 26, 98, 0.32f, 0.56f, 0.92f);  // blue plasma

        // ── Central energy aperture (radial violet→cyan glow) ──
        int apertureCenterX = 64, apertureCenterY = 50, apertureRadius = 30;
        pixmap.setColor(0.05f, 0.05f, 0.08f, 1f);
        pixmap.fillRectangle(apertureCenterX - 32, apertureCenterY - 32, 64, 64);
        for (int pixelRow = apertureCenterY - apertureRadius; pixelRow <= apertureCenterY + apertureRadius; pixelRow++) {
            for (int pixelColumn = apertureCenterX - apertureRadius; pixelColumn <= apertureCenterX + apertureRadius; pixelColumn++) {
                float normalizedX = (pixelColumn - apertureCenterX) / (float) apertureRadius;
                float normalizedY = (pixelRow - apertureCenterY) / (float) apertureRadius;
                float distance = (float) Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
                if (distance > 1f) continue;
                float red, green, blue;
                if (distance < 0.22f) {
                    red = 0.95f; green = 0.98f; blue = 1.00f;
                } else if (distance < 0.55f) {
                    float interpolationFactor = (distance - 0.22f) / 0.33f;
                    red   = 0.95f - 0.55f * interpolationFactor;
                    green = 0.98f - 0.40f * interpolationFactor;
                    blue  = 1.00f - 0.05f * interpolationFactor;
                } else {
                    float interpolationFactor = (distance - 0.55f) / 0.45f;
                    red   = 0.40f + 0.20f * interpolationFactor;
                    green = 0.58f - 0.40f * interpolationFactor;
                    blue  = 0.95f - 0.10f * interpolationFactor;
                }
                float ring   = (float) (Math.cos(distance * Math.PI * 7) * 0.5 + 0.5);
                float factor = 0.82f + 0.18f * ring;
                pixmap.setColor(red * factor, green * factor, blue * factor, 1f);
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }
        // Orbiting containment ring (flattened ellipse outline).
        pixmap.setColor(0.80f, 0.70f, 1.00f, 0.9f);
        for (int degree = 0; degree < 360; degree += 5) {
            double radians = Math.toRadians(degree);
            int pointColumn = apertureCenterX + (int) Math.round(apertureRadius * 0.78f * Math.cos(radians));
            int pointRow    = apertureCenterY + (int) Math.round(apertureRadius * 0.30f * Math.sin(radians));
            pixmap.drawPixel(pointColumn, pointRow);
        }
        // Suspended core orb.
        pixmap.setColor(0.88f, 0.74f, 1.00f, 1f);
        pixmap.fillCircle(apertureCenterX, apertureCenterY, 6);
        pixmap.setColor(1.00f, 1.00f, 1.00f, 1f);
        pixmap.fillCircle(apertureCenterX, apertureCenterY, 3);
        // Aperture frame + corner emitter nodes.
        pixmap.setColor(0.34f, 0.30f, 0.42f, 1f);
        drawRectOutline(pixmap, apertureCenterX - 32, apertureCenterY - 32, 64, 64);
        pixmap.setColor(0.72f, 0.56f, 1.00f, 1f);
        pixmap.fillRectangle(apertureCenterX - 34, apertureCenterY - 34, 4, 4);
        pixmap.fillRectangle(apertureCenterX + 30, apertureCenterY - 34, 4, 4);
        pixmap.fillRectangle(apertureCenterX - 34, apertureCenterY + 30, 4, 4);
        pixmap.fillRectangle(apertureCenterX + 30, apertureCenterY + 30, 4, 4);

        // ── Lower control panel ──
        pixmap.setColor(0.11f, 0.11f, 0.15f, 1f);
        pixmap.fillRectangle(18, 92, 92, 24);
        pixmap.setColor(0.06f, 0.18f, 0.24f, 1f);
        pixmap.fillRectangle(22, 95, 34, 18);              // readout screen
        pixmap.setColor(0.40f, 0.88f, 0.98f, 1f);
        pixmap.fillRectangle(25, 98, 28, 2);
        pixmap.fillRectangle(25, 102, 18, 2);
        pixmap.fillRectangle(25, 106, 24, 2);
        pixmap.fillRectangle(25, 110, 12, 2);
        pixmap.setColor(0.62f, 0.52f, 0.88f, 1f);          // keypad
        for (int keyRow = 0; keyRow < 3; keyRow++) {
            for (int keyColumn = 0; keyColumn < 3; keyColumn++) {
                pixmap.fillRectangle(64 + keyColumn * 10, 96 + keyRow * 6, 7, 4);
            }
        }
        drawGauge(pixmap, 96, 100, 0.70f, 0.55f, 1.00f);   // tuning gauge
        // Dispenser tray + hazard band + corner bolts.
        pixmap.setColor(0.07f, 0.07f, 0.09f, 1f);
        pixmap.fillRectangle(18, 116, 92, 5);
        hazardStripeBand(pixmap, 12, 121, 104, 5, 0.80f, 0.68f, 0.16f);
        boltStud(pixmap, 14, 10);  boltStud(pixmap, 114, 10);
        boltStud(pixmap, 14, 118); boltStud(pixmap, 114, 118);
        return finalize(pixmap);
    }

    /** Generates a flat keycard pickup sprite in the given tier color. */
    // Keycard pickup ('r'/'y'/'b') — a high-resolution security access card: tinted body with a
    // beveled metallic frame, a glowing colour-matched border, a gold smart-chip with contact
    // grid, a dark magnetic stripe, a punched lanyard hole, and a diagonal sheen streak. The
    // saturated colour identifies the tier and is echoed by the floating aura halo behind it.
    private static Texture generateKeycardTexture(float red, float green, float blue) {
        final int width = 112, height = 72;
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        final int cardX = 8, cardY = 10, cardWidth = width - 16, cardHeight = height - 20;
        float bodyRed   = Math.min(1f, red   * 0.62f + 0.05f);
        float bodyGreen = Math.min(1f, green * 0.62f + 0.05f);
        float bodyBlue  = Math.min(1f, blue  * 0.62f + 0.05f);
        float glowRed   = Math.min(1f, red   * 0.6f + 0.4f);
        float glowGreen = Math.min(1f, green * 0.6f + 0.4f);
        float glowBlue  = Math.min(1f, blue  * 0.6f + 0.4f);

        // ── Dark outer frame, then the glowing colour-matched border and tinted body ──
        pixmap.setColor(0.06f, 0.06f, 0.08f, 1f);
        pixmap.fillRectangle(cardX - 2, cardY - 2, cardWidth + 4, cardHeight + 4);
        pixmap.setColor(glowRed, glowGreen, glowBlue, 1f);                 // glowing edge
        pixmap.fillRectangle(cardX, cardY, cardWidth, cardHeight);
        pixmap.setColor(bodyRed, bodyGreen, bodyBlue, 1f);                 // inner body
        pixmap.fillRectangle(cardX + 3, cardY + 3, cardWidth - 6, cardHeight - 6);

        // ── Top bevel highlight / bottom bevel shadow for a metallic, embossed read ──
        pixmap.setColor(Math.min(1f, bodyRed + 0.22f), Math.min(1f, bodyGreen + 0.22f), Math.min(1f, bodyBlue + 0.22f), 1f);
        pixmap.fillRectangle(cardX + 3, cardY + 3, cardWidth - 6, 3);
        pixmap.setColor(bodyRed * 0.5f, bodyGreen * 0.5f, bodyBlue * 0.5f, 1f);
        pixmap.fillRectangle(cardX + 3, cardY + cardHeight - 6, cardWidth - 6, 3);

        // ── Magnetic stripe (dark band across the lower third) ──
        int stripeY = cardY + cardHeight - 20;
        pixmap.setColor(0.09f, 0.09f, 0.11f, 1f);
        pixmap.fillRectangle(cardX + 3, stripeY, cardWidth - 6, 9);
        pixmap.setColor(0.16f, 0.16f, 0.19f, 1f);                          // stripe sheen line
        pixmap.fillRectangle(cardX + 3, stripeY + 1, cardWidth - 6, 1);

        // ── Gold smart-chip with contact grid (upper-left) ──
        int chipX = cardX + 8, chipY = cardY + 8, chipW = 22, chipH = 16;
        pixmap.setColor(0.78f, 0.60f, 0.16f, 1f);
        pixmap.fillRectangle(chipX, chipY, chipW, chipH);
        pixmap.setColor(0.95f, 0.82f, 0.34f, 1f);                          // chip highlight
        pixmap.fillRectangle(chipX, chipY, chipW, 2);
        pixmap.setColor(0.30f, 0.22f, 0.05f, 1f);                          // contact grid lines
        pixmap.fillRectangle(chipX + chipW / 2 - 1, chipY, 2, chipH);
        pixmap.fillRectangle(chipX, chipY + chipH / 2 - 1, chipW, 2);

        // ── Colour-matched data lines to the right of the chip (suggesting printed text) ──
        pixmap.setColor(glowRed, glowGreen, glowBlue, 1f);
        for (int line = 0; line < 3; line++) {
            pixmap.fillRectangle(chipX + chipW + 6, chipY + 1 + line * 6, cardWidth - chipW - 26, 2);
        }

        // ── Punched lanyard hole (top-right) — dark bore with a lighter rim for depth ──
        int holeX = cardX + cardWidth - 11, holeY = cardY + 9;
        pixmap.setColor(0.34f, 0.35f, 0.40f, 1f);   // metallic rim
        pixmap.fillCircle(holeX, holeY, 5);
        pixmap.setColor(0.05f, 0.05f, 0.07f, 1f);   // dark bore
        pixmap.fillCircle(holeX, holeY, 3);

        // ── Diagonal sheen streak across the body for a glossy laminate finish ──
        pixmap.setColor(1f, 1f, 1f, 0.18f);
        for (int offset = 0; offset < cardHeight; offset++) {
            int sheenColumn = cardX + 6 + offset;
            if (sheenColumn >= cardX + cardWidth - 4) break;
            pixmap.drawPixel(sheenColumn, cardY + 4 + offset);
            pixmap.drawPixel(sheenColumn + 1, cardY + 4 + offset);
        }

        return finalize(pixmap);
    }

    // Specimen tank ('I') — a tall research cylinder: machined steel plinth with a
    // readout panel and vents, a capped collar with a cyan emitter ring, a glass tube
    // of teal cryo-fluid with an air gap and a meniscus line, glowing containment
    // bands, a curled bioluminescent specimen suspended at centre, glass sheen
    // streaks, rising bubbles, and condensation frost near the surface.
    private static Texture generateSpecimenTankTexture() {
        final int textureWidth = 96, textureHeight = 192;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        final int glassLeft = 18, glassRight = 78;       // inner fluid column bounds
        final int glassTop  = 28, glassBottom = 158;
        final int fluidTop  = 38;                          // liquid surface (air gap above)

        // ── Base plinth: heavy steel pedestal with readout, lamps and vents ──
        beveledPanel(pixmap, 6, 158, 84, 34, 0.22f, 0.24f, 0.28f, 1.5f, 0.5f);
        pixmap.setColor(0.05f, 0.16f, 0.20f, 1f);
        pixmap.fillRectangle(12, 164, 34, 18);             // readout screen
        pixmap.setColor(0.30f, 0.85f, 0.95f, 1f);
        pixmap.fillRectangle(15, 167, 28, 2);
        pixmap.fillRectangle(15, 171, 18, 2);
        pixmap.fillRectangle(15, 175, 24, 2);
        pixmap.fillRectangle(15, 179, 12, 2);
        pixmap.setColor(0.30f, 0.95f, 0.40f, 1f); pixmap.fillRectangle(54, 165, 5, 5);   // status lamps
        pixmap.setColor(0.95f, 0.72f, 0.20f, 1f); pixmap.fillRectangle(63, 165, 5, 5);
        pixmap.setColor(0.90f, 0.26f, 0.20f, 1f); pixmap.fillRectangle(72, 165, 5, 5);
        pixmap.setColor(0.12f, 0.13f, 0.16f, 1f);
        for (int ventRow = 174; ventRow < 188; ventRow += 4) {   // ventilation louvres
            pixmap.fillRectangle(52, ventRow, 30, 2);
        }
        boltStud(pixmap, 10, 162); boltStud(pixmap, 86, 162);
        boltStud(pixmap, 10, 188); boltStud(pixmap, 86, 188);

        // ── Top cap: machined collar, valve stack and cyan emitter ring ──
        beveledPanel(pixmap, 10, 4, 76, 24, 0.26f, 0.28f, 0.33f, 1.5f, 0.5f);
        pixmap.setColor(0.34f, 0.36f, 0.42f, 1f);
        pixmap.fillRectangle(16, 8, 64, 5);                // ribbed collar band
        pixmap.setColor(0.16f, 0.17f, 0.20f, 1f);
        pixmap.fillRectangle(40, 0, 16, 6);                // valve stack
        pixmap.setColor(0.45f, 0.90f, 0.98f, 1f);
        pixmap.fillRectangle(20, 23, 56, 4);               // cyan emitter ring under cap
        boltStud(pixmap, 15, 9); boltStud(pixmap, 81, 9);

        // ── Vertical frame struts joining the caps ──
        pixmap.setColor(0.24f, 0.26f, 0.30f, 1f);
        pixmap.fillRectangle(glassLeft - 6, glassTop, 6, glassBottom - glassTop);
        pixmap.fillRectangle(glassRight,    glassTop, 6, glassBottom - glassTop);
        pixmap.setColor(0.36f, 0.38f, 0.44f, 1f);          // strut highlights
        pixmap.fillRectangle(glassLeft - 6, glassTop, 1, glassBottom - glassTop);
        pixmap.fillRectangle(glassRight,    glassTop, 1, glassBottom - glassTop);

        // ── Glass cylinder fluid (cylindrical teal shading) ──
        for (int column = glassLeft; column < glassRight; column++) {
            float across = (column - glassLeft) / (float) (glassRight - glassLeft - 1);
            float curve  = 1f - Math.min(1f, Math.abs(across - 0.40f) * 1.9f);
            float fluidRed   = 0.06f + 0.12f * curve;
            float fluidGreen = 0.34f + 0.34f * curve;
            float fluidBlue  = 0.42f + 0.40f * curve;
            pixmap.setColor(fluidRed, fluidGreen, fluidBlue, 1f);
            pixmap.fillRectangle(column, fluidTop, 1, glassBottom - fluidTop);
        }
        // Dark air gap above the liquid + bright meniscus line.
        pixmap.setColor(0.09f, 0.15f, 0.17f, 1f);
        pixmap.fillRectangle(glassLeft, glassTop, glassRight - glassLeft, fluidTop - glassTop);
        pixmap.setColor(0.70f, 0.95f, 1.00f, 0.9f);
        pixmap.fillRectangle(glassLeft, fluidTop, glassRight - glassLeft, 2);
        // Cyan containment glow rings banding the tube.
        pixmap.setColor(0.40f, 0.92f, 0.98f, 0.85f);
        for (int ringRow = fluidTop + 24; ringRow < glassBottom - 10; ringRow += 34) {
            pixmap.fillRectangle(glassLeft, ringRow, glassRight - glassLeft, 2);
        }

        // ── Suspended specimen: curled bioluminescent silhouette ──
        int specimenCenterX = 47, specimenCenterY = 98;
        pixmap.setColor(0.20f, 0.60f, 0.55f, 0.40f);
        pixmap.fillCircle(specimenCenterX, specimenCenterY, 24);          // glow aura
        pixmap.setColor(0.05f, 0.13f, 0.12f, 1f);
        pixmap.fillCircle(specimenCenterX, specimenCenterY + 4, 16);      // torso
        pixmap.fillCircle(specimenCenterX + 4, specimenCenterY - 14, 9);  // head
        pixmap.fillTriangle(specimenCenterX - 16, specimenCenterY, specimenCenterX - 2, specimenCenterY + 4, specimenCenterX - 8, specimenCenterY + 22);
        pixmap.fillTriangle(specimenCenterX + 12, specimenCenterY + 6, specimenCenterX + 22, specimenCenterY + 2, specimenCenterX + 18, specimenCenterY + 24);
        pixmap.fillTriangle(specimenCenterX - 10, specimenCenterY - 8, specimenCenterX - 18, specimenCenterY - 2, specimenCenterX - 20, specimenCenterY + 14);
        pixmap.setColor(0.18f, 0.48f, 0.40f, 1f);
        pixmap.fillCircle(specimenCenterX + 2, specimenCenterY, 7);       // subdermal glow
        pixmap.setColor(0.30f, 0.70f, 0.62f, 1f);
        pixmap.fillRectangle(specimenCenterX, specimenCenterY - 10, 2, 22);   // spine
        pixmap.setColor(0.95f, 0.45f, 0.55f, 1f);
        pixmap.drawPixel(specimenCenterX + 1, specimenCenterY - 15);      // eyes
        pixmap.drawPixel(specimenCenterX + 6, specimenCenterY - 14);

        // ── Glass sheen streaks (strong left, faint right) ──
        pixmap.setColor(0.85f, 0.97f, 1.00f, 0.65f);
        pixmap.fillRectangle(glassLeft + 3, glassTop + 4, 4, glassBottom - glassTop - 10);
        pixmap.setColor(0.60f, 0.82f, 0.88f, 0.35f);
        pixmap.fillRectangle(glassRight - 7, glassTop + 8, 2, glassBottom - glassTop - 20);

        // ── Rising bubbles of varied size ──
        pixmap.setColor(0.82f, 0.96f, 1.00f, 1f);
        int[] bubbleColumns = { 30, 62, 40, 70, 34, 58, 46, 66, 38 };
        int[] bubbleRows    = { 150, 140, 128, 120, 108, 96, 80, 66, 52 };
        int[] bubbleRadii   = { 2, 1, 3, 1, 2, 1, 2, 1, 2 };
        for (int bubbleIndex = 0; bubbleIndex < bubbleColumns.length; bubbleIndex++) {
            pixmap.fillCircle(bubbleColumns[bubbleIndex], bubbleRows[bubbleIndex], bubbleRadii[bubbleIndex]);
        }

        // ── Condensation frost patches near the liquid surface ──
        pixmap.setColor(0.72f, 0.88f, 0.92f, 0.5f);
        pixmap.fillRectangle(glassLeft + 2, fluidTop + 2, 12, 7);
        pixmap.fillRectangle(glassRight - 16, fluidTop + 4, 12, 6);

        return finalize(pixmap);
    }

    // Holo-workstation ('W') — a low control console (beveled body, angled desk top,
    // keypad deck, a live waveform screen and status LEDs) topped by a holo-emitter
    // disc that projects a translucent cyan wireframe globe: scan-lined volume fill,
    // latitude/longitude arcs, fanning projection beams, and floating data glyphs.
    private static Texture generateHoloWorkstationTexture() {
        final int textureWidth = 120, textureHeight = 96;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        final int emitterCenterX = 60, emitterTopRow = 52;

        // ── Console base body with angled desk top ──
        beveledPanel(pixmap, 8, 56, 104, 36, 0.18f, 0.20f, 0.25f, 1.6f, 0.5f);
        pixmap.setColor(0.24f, 0.27f, 0.32f, 1f);
        pixmap.fillRectangle(8, 56, 104, 8);
        pixmap.setColor(0.34f, 0.38f, 0.44f, 1f);
        pixmap.fillRectangle(8, 56, 104, 1);

        // ── Keypad deck (left) ──
        pixmap.setColor(0.10f, 0.11f, 0.14f, 1f);
        pixmap.fillRectangle(14, 66, 40, 22);
        pixmap.setColor(0.30f, 0.55f, 0.65f, 1f);
        for (int keyRow = 0; keyRow < 3; keyRow++) {
            for (int keyColumn = 0; keyColumn < 5; keyColumn++) {
                pixmap.fillRectangle(17 + keyColumn * 7, 69 + keyRow * 6, 5, 4);
            }
        }
        // ── Live waveform screen (right) ──
        pixmap.setColor(0.05f, 0.18f, 0.22f, 1f);
        pixmap.fillRectangle(60, 66, 46, 22);
        pixmap.setColor(0.35f, 0.92f, 1.00f, 1f);
        int[] waveHeights = { 3, 6, 2, 8, 4, 7, 3, 5, 2, 6 };
        for (int waveIndex = 0; waveIndex < waveHeights.length; waveIndex++) {
            int barColumn = 63 + waveIndex * 4;
            pixmap.fillRectangle(barColumn, 84 - waveHeights[waveIndex], 2, waveHeights[waveIndex]);
        }
        pixmap.setColor(0.30f, 0.95f, 0.40f, 1f); pixmap.fillRectangle(14, 89, 6, 2);   // status LEDs
        pixmap.setColor(0.95f, 0.72f, 0.20f, 1f); pixmap.fillRectangle(24, 89, 6, 2);
        boltStud(pixmap, 12, 60); boltStud(pixmap, 108, 60);

        // ── Holo-emitter disc on the desk ──
        pixmap.setColor(0.30f, 0.33f, 0.40f, 1f);
        pixmap.fillRectangle(emitterCenterX - 16, emitterTopRow, 32, 5);
        pixmap.setColor(0.45f, 0.95f, 1.00f, 1f);
        pixmap.fillRectangle(emitterCenterX - 12, emitterTopRow + 1, 24, 2);

        // ── Projection beams fanning up from the emitter ──
        for (int beamIndex = -3; beamIndex <= 3; beamIndex++) {
            float brightness = 1f - Math.abs(beamIndex) / 4.5f;
            pixmap.setColor(0.30f * brightness, 0.80f * brightness, 0.95f * brightness, 0.5f);
            for (int beamRow = 8; beamRow < emitterTopRow; beamRow++) {
                float fraction = (beamRow - 8) / (float) (emitterTopRow - 8);
                int beamColumn = Math.round(emitterCenterX + beamIndex * 12 * fraction);
                pixmap.drawPixel(beamColumn, beamRow);
            }
        }

        // ── Holographic wireframe globe ──
        int holoCenterX = 60, holoCenterY = 28, holoRadius = 18;
        // Faint scan-lined volume fill.
        for (int pixelRow = holoCenterY - holoRadius; pixelRow <= holoCenterY + holoRadius; pixelRow++) {
            for (int pixelColumn = holoCenterX - holoRadius; pixelColumn <= holoCenterX + holoRadius; pixelColumn++) {
                float normalizedX = (pixelColumn - holoCenterX) / (float) holoRadius;
                float normalizedY = (pixelRow - holoCenterY) / (float) holoRadius;
                float distance = (float) Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
                if (distance > 1f) continue;
                if ((pixelRow % 2) == 0) continue;          // scan-line gaps
                pixmap.setColor(0.35f, 0.85f, 1.00f, 0.18f * (1f - distance));
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }
        // Outer rim + longitude meridians (vertical ellipses).
        pixmap.setColor(0.55f, 0.95f, 1.00f, 0.9f);
        pixmap.drawCircle(holoCenterX, holoCenterY, holoRadius);
        float[] meridianWidths = { holoRadius, holoRadius * 0.55f, 0f };
        for (float meridianWidth : meridianWidths) {
            for (int degree = 0; degree < 360; degree += 6) {
                double radians = Math.toRadians(degree);
                int pointColumn = holoCenterX + (int) Math.round(meridianWidth * Math.sin(radians));
                int pointRow    = holoCenterY + (int) Math.round(holoRadius * Math.cos(radians));
                pixmap.drawPixel(pointColumn, pointRow);
            }
        }
        // Latitude rings (flattened horizontal ellipses).
        int[] latitudeOffsets = { -9, 0, 9 };
        for (int latitudeOffset : latitudeOffsets) {
            float halfWidth  = (float) Math.sqrt(Math.max(0, holoRadius * holoRadius - latitudeOffset * latitudeOffset));
            float halfHeight = halfWidth * 0.30f;
            for (int degree = 0; degree < 360; degree += 6) {
                double radians = Math.toRadians(degree);
                int pointColumn = holoCenterX + (int) Math.round(halfWidth * Math.cos(radians));
                int pointRow    = holoCenterY + latitudeOffset + (int) Math.round(halfHeight * Math.sin(radians));
                pixmap.drawPixel(pointColumn, pointRow);
            }
        }

        // ── Floating data glyphs flanking the projection ──
        pixmap.setColor(0.40f, 0.90f, 1.00f, 0.85f);
        pixmap.fillRectangle(20, 14, 10, 2);
        pixmap.fillRectangle(20, 18, 6, 2);
        pixmap.fillRectangle(20, 22, 8, 2);
        pixmap.fillRectangle(92, 16, 10, 2);
        pixmap.fillRectangle(94, 20, 6, 2);
        pixmap.fillRectangle(90, 24, 8, 2);
        pixmap.setColor(0.85f, 0.98f, 1.00f, 1f);
        pixmap.fillRectangle(holoCenterX + holoRadius, holoCenterY - 2, 2, 2);
        pixmap.fillRectangle(holoCenterX - holoRadius - 2, holoCenterY + 6, 2, 2);

        return finalize(pixmap);
    }

    // AI core node ('J') — a tall server tower: beveled chassis with edge cooling
    // fins, a radial cyan-white "core" glow orb at the crown, a glowing vertical data
    // spine threading a stack of translucent processing slabs (each with an internal
    // glow gradient, bright central seam and edge LEDs), and a base unit with a
    // readout panel and vents.
    private static Texture generateAiCoreNodeTexture() {
        final int textureWidth = 72, textureHeight = 192;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // ── Outer chassis frame + edge cooling fins ──
        beveledPanel(pixmap, 6, 2, 60, 188, 0.12f, 0.13f, 0.17f, 1.6f, 0.5f);
        pixmap.setColor(0.16f, 0.17f, 0.21f, 1f);
        for (int finRow = 40; finRow < 168; finRow += 7) {
            pixmap.fillRectangle(2, finRow, 6, 4);
            pixmap.fillRectangle(64, finRow, 6, 4);
        }

        // ── Crown AI core orb (radial cyan-white glow) ──
        int coreCenterX = 36, coreCenterY = 26, coreRadius = 18;
        pixmap.setColor(0.05f, 0.07f, 0.10f, 1f);
        pixmap.fillRectangle(coreCenterX - 20, 6, 40, 40);
        for (int pixelRow = coreCenterY - coreRadius; pixelRow <= coreCenterY + coreRadius; pixelRow++) {
            for (int pixelColumn = coreCenterX - coreRadius; pixelColumn <= coreCenterX + coreRadius; pixelColumn++) {
                float normalizedX = (pixelColumn - coreCenterX) / (float) coreRadius;
                float normalizedY = (pixelRow - coreCenterY) / (float) coreRadius;
                float distance = (float) Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
                if (distance > 1f) continue;
                float red, green, blue;
                if (distance < 0.25f) {
                    red = 0.92f; green = 0.99f; blue = 1.00f;
                } else if (distance < 0.6f) {
                    float interpolationFactor = (distance - 0.25f) / 0.35f;
                    red   = 0.92f - 0.62f * interpolationFactor;
                    green = 0.99f - 0.24f * interpolationFactor;
                    blue  = 1.00f - 0.05f * interpolationFactor;
                } else {
                    float interpolationFactor = (distance - 0.6f) / 0.4f;
                    red   = 0.30f - 0.20f * interpolationFactor;
                    green = 0.75f - 0.45f * interpolationFactor;
                    blue  = 0.95f - 0.55f * interpolationFactor;
                }
                float ring   = (float) (Math.cos(distance * Math.PI * 5) * 0.5 + 0.5);
                float factor = 0.82f + 0.18f * ring;
                pixmap.setColor(red * factor, green * factor, blue * factor, 1f);
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }
        pixmap.setColor(0.30f, 0.34f, 0.40f, 1f);
        drawRectOutline(pixmap, coreCenterX - 20, 6, 40, 40);
        boltStud(pixmap, coreCenterX - 18, 8); boltStud(pixmap, coreCenterX + 17, 8);

        // ── Central data spine (glowing cyan conduit through the stack) ──
        pixmap.setColor(0.10f, 0.40f, 0.50f, 1f);
        pixmap.fillRectangle(coreCenterX - 3, 46, 6, 122);
        pixmap.setColor(0.55f, 0.95f, 1.00f, 1f);
        pixmap.fillRectangle(coreCenterX - 1, 46, 2, 122);

        // ── Stacked translucent processing slabs ──
        int slabTopStart = 50, slabHeight = 18, slabGap = 6, slabCount = 5;
        for (int slabIndex = 0; slabIndex < slabCount; slabIndex++) {
            int slabTop = slabTopStart + slabIndex * (slabHeight + slabGap);
            beveledPanel(pixmap, 12, slabTop, 48, slabHeight, 0.16f, 0.42f, 0.55f, 1.4f, 0.5f);
            // Internal glow gradient — brightest toward the slab's mid-line.
            for (int rowOffset = 2; rowOffset < slabHeight - 2; rowOffset++) {
                float vertical = 1f - Math.abs(rowOffset - slabHeight / 2f) / (slabHeight / 2f);
                pixmap.setColor(0.25f * vertical + 0.10f, 0.65f * vertical + 0.20f, 0.80f * vertical + 0.25f, 1f);
                pixmap.fillRectangle(15, slabTop + rowOffset, 42, 1);
            }
            // Bright central seam aligned with the data spine.
            pixmap.setColor(0.70f, 0.98f, 1.00f, 1f);
            pixmap.fillRectangle(coreCenterX - 2, slabTop, 4, slabHeight);
            // Edge status LEDs — amber on the second slab, cyan elsewhere.
            if (slabIndex == 1) pixmap.setColor(1.00f, 0.66f, 0.16f, 1f);
            else                pixmap.setColor(0.40f, 0.95f, 1.00f, 1f);
            pixmap.fillRectangle(14, slabTop + 7, 4, 4);
            pixmap.fillRectangle(54, slabTop + 7, 4, 4);
        }

        // ── Base unit with readout, lamps and vents ──
        pixmap.setColor(0.14f, 0.15f, 0.19f, 1f);
        pixmap.fillRectangle(10, 168, 52, 22);
        pixmap.setColor(0.05f, 0.16f, 0.20f, 1f);
        pixmap.fillRectangle(14, 172, 26, 14);
        pixmap.setColor(0.35f, 0.90f, 1.00f, 1f);
        pixmap.fillRectangle(16, 174, 22, 2);
        pixmap.fillRectangle(16, 178, 14, 2);
        pixmap.fillRectangle(16, 182, 18, 2);
        pixmap.setColor(0.30f, 0.95f, 0.40f, 1f); pixmap.fillRectangle(44, 172, 5, 5);
        pixmap.setColor(0.95f, 0.72f, 0.20f, 1f); pixmap.fillRectangle(52, 172, 5, 5);
        pixmap.setColor(0.12f, 0.13f, 0.16f, 1f);
        for (int ventRow = 180; ventRow < 188; ventRow += 3) {
            pixmap.fillRectangle(44, ventRow, 14, 1);
        }
        boltStud(pixmap, 10, 170); boltStud(pixmap, 62, 170);
        boltStud(pixmap, 10, 188); boltStud(pixmap, 62, 188);

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

    // Energy scorch ('e') — a plasma burn crater: soot streaks radiating outward, a
    // charred blackened core, glowing ember cracks, jagged electric-blue arc filaments
    // crawling from the impact, and a hot white-blue molten centre.
    private static Texture generateEnergyScorchTexture() {
        Pixmap pixmap = new Pixmap(80, 80, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        java.util.Random random = new java.util.Random(0x5C04C4ED1L);
        int centerX = 40;
        int centerY = 40;

        // Radial soot streaks feathering out from the blast.
        pixmap.setColor(0.15f, 0.13f, 0.12f, 1f);
        for (int streakIndex = 0; streakIndex < 30; streakIndex++) {
            float angleRadians = random.nextFloat() * MathUtils.PI2;
            float length = 22f + random.nextFloat() * 15f;
            int endX = centerX + Math.round(MathUtils.cos(angleRadians) * length);
            int endY = centerY + Math.round(MathUtils.sin(angleRadians) * length);
            pixmap.drawLine(centerX, centerY, endX, endY);
        }
        // Outer desaturated scorch ring.
        organicBlob(pixmap, random, centerX, centerY, 27, 20, 0.19f, 0.16f, 0.15f, 1f);
        // Mid charred ring.
        organicBlob(pixmap, random, centerX, centerY, 18, 16, 0.09f, 0.08f, 0.09f, 1f);
        // Blackened crater core.
        organicBlob(pixmap, random, centerX, centerY, 11, 12, 0.03f, 0.03f, 0.05f, 1f);

        // Glowing ember cracks radiating from the molten core.
        pixmap.setColor(0.95f, 0.42f, 0.10f, 1f);
        for (int crackIndex = 0; crackIndex < 9; crackIndex++) {
            float angleRadians = random.nextFloat() * MathUtils.PI2;
            float length = 9f + random.nextFloat() * 11f;
            int endX = centerX + Math.round(MathUtils.cos(angleRadians) * length);
            int endY = centerY + Math.round(MathUtils.sin(angleRadians) * length);
            pixmap.drawLine(centerX, centerY, endX, endY);
        }
        // Soft hot ember glow over the core.
        pixmap.setColor(1.0f, 0.62f, 0.20f, 0.75f);
        pixmap.fillCircle(centerX, centerY, 5);

        // Jagged electric-blue arc filaments crawling outward in stepped segments.
        pixmap.setColor(0.45f, 0.78f, 1.0f, 1f);
        for (int arcIndex = 0; arcIndex < 7; arcIndex++) {
            float baseAngleRadians = random.nextFloat() * MathUtils.PI2;
            int previousX = centerX;
            int previousY = centerY;
            for (int segment = 1; segment <= 5; segment++) {
                float radius        = segment * (5f + random.nextFloat() * 2f);
                float wobbleRadians  = (random.nextFloat() - 0.5f) * 0.7f;
                float segmentAngle  = baseAngleRadians + wobbleRadians;
                int nextX = centerX + Math.round(MathUtils.cos(segmentAngle) * radius);
                int nextY = centerY + Math.round(MathUtils.sin(segmentAngle) * radius);
                pixmap.drawLine(previousX, previousY, nextX, nextY);
                previousX = nextX;
                previousY = nextY;
            }
        }
        // Hot white-blue molten flecks at the burn centre.
        pixmap.setColor(0.90f, 0.96f, 1.0f, 1f);
        pixmap.fillCircle(centerX, centerY, 3);
        for (int fleckIndex = 0; fleckIndex < 5; fleckIndex++) {
            pixmap.drawPixel(centerX - 4 + random.nextInt(9), centerY - 4 + random.nextInt(9));
        }
        return finalize(pixmap);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STELLAR_OBSERVATORY — Gravity Well Rotunda props + decals (';' '\' '|' '<' '?' ']' '-').
    // See .claude/agents/ideas/stellar-observatory-gravity-well-room.txt "TEXTURE / SPRITE
    // SPEC" for the full authored description. Palette discipline: titanium/gunmetal metal +
    // deep-space void black + emissive cyan/violet/ice-blue only (STELLAR_* constants in
    // RenderConstants.java) — no hazard orange, no rust, no gore, no red glow anywhere except
    // the single warm amber service lamp on the docking strut pylon.
    // ═══════════════════════════════════════════════════════════════════════════

    // Debris chunk silhouettes used by the gravity well core's containment ring.
    private static final int DEBRIS_SHAPE_ROCK    = 0;
    private static final int DEBRIS_SHAPE_ANTENNA = 1;
    private static final int DEBRIS_SHAPE_PLATE   = 2;
    private static final int DEBRIS_SHAPE_BOLTS   = 3;

    // Draws orbiting debris chunks (array index range [fromIndex, toIndex)) along the
    // elliptical containment ring at fixed angle/shape/radius slots — a rock fleck, snapped
    // antenna, torn plate shard and bolt cluster — each with a small hub-facing rim-light
    // and a faint motion-blur tail along its direction of travel. The caller splits the six
    // slots into two draw passes (before/after the hub sphere fill) so the portion of the
    // ring nearest the vertical axis correctly reads as passing behind the hub, while the
    // wide side slots pass visibly in front. A fixed (non-random) layout matches the "static
    // orbit snapshot" note in the design doc — any future spin animation must tint-scale or
    // swap pre-baked frames via the tick hook, never reallocate this Pixmap per frame.
    private static void drawOrbitDebris(Pixmap pixmap, int hubCenterX, int hubCenterY,
                                        int ringRadiusX, int ringRadiusY,
                                        int fromIndex, int toIndex) {
        int[]   shapeKinds     = { DEBRIS_SHAPE_ROCK, DEBRIS_SHAPE_ANTENNA, DEBRIS_SHAPE_PLATE,
                                   DEBRIS_SHAPE_BOLTS, DEBRIS_SHAPE_ROCK,   DEBRIS_SHAPE_PLATE };
        float[] angleDegrees   = { 250f, 290f, 330f, 30f, 90f, 150f };
        float[] radiusFraction = { 0.55f, 0.60f, 0.85f, 0.90f, 0.55f, 0.80f };
        for (int debrisIndex = fromIndex; debrisIndex < toIndex; debrisIndex++) {
            double radians = Math.toRadians(angleDegrees[debrisIndex]);
            int chunkX = hubCenterX + Math.round(ringRadiusX * radiusFraction[debrisIndex] * (float) Math.cos(radians));
            int chunkY = hubCenterY + Math.round(ringRadiusY * radiusFraction[debrisIndex] * (float) Math.sin(radians));

            // Faint motion-blur tail trailing along the orbit's tangential direction.
            double tangentRadians = radians + Math.PI / 2.0;
            int tailX = chunkX - Math.round((float) Math.cos(tangentRadians) * 5);
            int tailY = chunkY - Math.round((float) Math.sin(tangentRadians) * 2);
            pixmap.setColor(STELLAR_ICE_BLUE_R, STELLAR_ICE_BLUE_G, STELLAR_ICE_BLUE_B, 0.25f);
            pixmap.drawLine(tailX, tailY, chunkX, chunkY);

            switch (shapeKinds[debrisIndex]) {
                case DEBRIS_SHAPE_ROCK:
                    pixmap.setColor(0.24f, 0.25f, 0.28f, 1f);
                    pixmap.fillCircle(chunkX, chunkY, 3);
                    pixmap.setColor(0.14f, 0.15f, 0.18f, 1f);
                    pixmap.drawPixel(chunkX + 1, chunkY + 1);
                    break;
                case DEBRIS_SHAPE_PLATE:
                    pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
                    pixmap.fillRectangle(chunkX - 3, chunkY - 2, 6, 4);
                    pixmap.setColor(STELLAR_HULL_TITANIUM_R, STELLAR_HULL_TITANIUM_G, STELLAR_HULL_TITANIUM_B, 1f);
                    pixmap.fillRectangle(chunkX - 3, chunkY - 2, 6, 1);
                    break;
                case DEBRIS_SHAPE_ANTENNA:
                    pixmap.setColor(STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 1f);
                    pixmap.drawLine(chunkX - 4, chunkY + 3, chunkX + 3, chunkY - 4);
                    pixmap.drawPixel(chunkX + 3, chunkY - 4);
                    break;
                default: // DEBRIS_SHAPE_BOLTS — a tiny loose bolt cluster
                    pixmap.setColor(STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 1f);
                    pixmap.drawPixel(chunkX - 1, chunkY - 1);
                    pixmap.drawPixel(chunkX + 1, chunkY);
                    pixmap.drawPixel(chunkX, chunkY + 1);
                    break;
            }
            // Rim-light on the side facing the hub.
            double hubwardRadians = radians + Math.PI;
            int rimX = chunkX + Math.round((float) Math.cos(hubwardRadians) * 2);
            int rimY = chunkY + Math.round((float) Math.sin(hubwardRadians) * 2);
            pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 0.9f);
            pixmap.drawPixel(rimX, rimY);
        }
    }

    // Gravity well core (';') — CENTERPIECE of the rotunda: a dark spherical anti-grav
    // emitter hub floats above a hazard-banded pedestal plinth, joined by a translucent
    // force column. An elliptical containment ring of blue energy encircles the hub with
    // orbiting debris chunks at varied radii, and a soft caustic wash lights the deck below.
    // Cool cyan/blue only — the tonal opposite of every other biome's warm-orange centerpiece.
    private static Texture generateGravityWellCoreTexture() {
        final int textureWidth = 140, textureHeight = 224;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        final int hubCenterX = 70, hubCenterY = 54, hubRadius = 26;
        final int ringRadiusX = 58, ringRadiusY = 16;

        // ── Caustic wash on the deck beneath the plinth ──
        glowPool(pixmap, hubCenterX, 210, 60, 12, STELLAR_CORE_BLUE_R, STELLAR_CORE_BLUE_G, STELLAR_CORE_BLUE_B, 0.5f);

        // ── Pedestal plinth: steel-dark body with a cyan readout, keypad and hazard band ──
        beveledPanel(pixmap, 26, 186, 88, 36,
                     STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1.6f, 0.5f);
        pixmap.setColor(0.05f, 0.14f, 0.18f, 1f);
        pixmap.fillRectangle(32, 192, 32, 12);
        pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 1f);
        pixmap.fillRectangle(34, 194, 24, 2);
        pixmap.fillRectangle(34, 197, 16, 2);
        pixmap.fillRectangle(34, 200, 20, 2);
        pixmap.setColor(0.14f, 0.15f, 0.18f, 1f);
        for (int keyColumn = 0; keyColumn < 4; keyColumn++) {
            pixmap.fillRectangle(70 + keyColumn * 8, 194, 6, 6);
        }
        boltStud(pixmap, 30, 190); boltStud(pixmap, 110, 190);
        // Hazard band chevrons — cyan/dark, never orange, per the room's cool-only discipline.
        hazardStripeBand(pixmap, 26, 216, 88, 6, STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B);

        // ── Force column: translucent pale-blue shaft rising from the plinth to the hub ──
        int columnTop = hubCenterY + hubRadius - 6;
        for (int row = columnTop; row < 186; row++) {
            float fade = 1f - (row - columnTop) / (float) (186 - columnTop);
            pixmap.setColor(STELLAR_CORE_BLUE_R, STELLAR_CORE_BLUE_G, STELLAR_CORE_BLUE_B, 0.10f + 0.20f * fade);
            pixmap.fillRectangle(hubCenterX - 5, row, 10, 1);
            pixmap.setColor(STELLAR_ICE_BLUE_R, STELLAR_ICE_BLUE_G, STELLAR_ICE_BLUE_B, 0.25f + 0.30f * fade);
            pixmap.fillRectangle(hubCenterX - 1, row, 2, 1);
        }

        // ── Containment ring debris on the far side of the orbit, drawn before the hub ──
        drawOrbitDebris(pixmap, hubCenterX, hubCenterY, ringRadiusX, ringRadiusY, 0, 3);

        // ── Containment ring: thin bright ellipse band of blue energy, brightest at its
        //    front (lower) arc — drawn before the hub so the near-vertical portion of the
        //    ellipse is naturally occluded by the hub fill below. ──
        pixmap.setColor(STELLAR_CORE_BLUE_R, STELLAR_CORE_BLUE_G, STELLAR_CORE_BLUE_B, 0.55f);
        for (int degree = 0; degree < 360; degree += 3) {
            double radians = Math.toRadians(degree);
            int pointX = hubCenterX + Math.round(ringRadiusX * (float) Math.cos(radians));
            int pointY = hubCenterY + Math.round(ringRadiusY * (float) Math.sin(radians));
            pixmap.drawPixel(pointX, pointY);
        }
        pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 0.85f);
        for (int degree = 0; degree < 360; degree += 3) {
            double radians = Math.toRadians(degree);
            float brightness = (float) (Math.sin(radians) * 0.5 + 0.5);
            if (brightness < 0.4f) continue;
            int pointX = hubCenterX + Math.round((ringRadiusX - 1) * (float) Math.cos(radians));
            int pointY = hubCenterY + Math.round((ringRadiusY - 1) * (float) Math.sin(radians));
            pixmap.drawPixel(pointX, pointY);
        }

        // ── Emitter hub: dark spherical shading (void black centre -> steel-dark rim) ──
        for (int pixelRow = hubCenterY - hubRadius; pixelRow <= hubCenterY + hubRadius; pixelRow++) {
            for (int pixelColumn = hubCenterX - hubRadius; pixelColumn <= hubCenterX + hubRadius; pixelColumn++) {
                float normalizedX = (pixelColumn - hubCenterX) / (float) hubRadius;
                float normalizedY = (pixelRow - hubCenterY) / (float) hubRadius;
                float distance = (float) Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
                if (distance > 1f) continue;
                float interpolationFactor = Math.min(1f, distance);
                float red   = STELLAR_VOID_BLACK_R + (STELLAR_HULL_STEEL_DARK_R - STELLAR_VOID_BLACK_R) * interpolationFactor;
                float green = STELLAR_VOID_BLACK_G + (STELLAR_HULL_STEEL_DARK_G - STELLAR_VOID_BLACK_G) * interpolationFactor;
                float blue  = STELLAR_VOID_BLACK_B + (STELLAR_HULL_STEEL_DARK_B - STELLAR_VOID_BLACK_B) * interpolationFactor;
                pixmap.setColor(red, green, blue, 1f);
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }
        // Bright cyan aperture ring around the hub's equator.
        pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 1f);
        pixmap.fillRectangle(hubCenterX - hubRadius + 3, hubCenterY - 2, (hubRadius - 3) * 2, 4);
        pixmap.setColor(STELLAR_ICE_BLUE_R, STELLAR_ICE_BLUE_G, STELLAR_ICE_BLUE_B, 1f);
        pixmap.fillRectangle(hubCenterX - hubRadius + 3, hubCenterY - 1, (hubRadius - 3) * 2, 1);
        // Edge shimmer noise along the hub silhouette.
        pixmap.setColor(STELLAR_ICE_BLUE_R, STELLAR_ICE_BLUE_G, STELLAR_ICE_BLUE_B, 0.5f);
        for (int degree = 0; degree < 360; degree += 11) {
            double radians = Math.toRadians(degree);
            int pointX = hubCenterX + Math.round(hubRadius * (float) Math.cos(radians));
            int pointY = hubCenterY + Math.round(hubRadius * (float) Math.sin(radians));
            pixmap.drawPixel(pointX, pointY);
        }

        // ── Containment ring debris on the near side of the orbit, drawn after the hub ──
        drawOrbitDebris(pixmap, hubCenterX, hubCenterY, ringRadiusX, ringRadiusY, 3, 6);

        return finalize(pixmap);
    }

    // Floating cargo crate ('\') — a magnetically clamped supply crate tilted ~20 degrees and
    // hovering a hand's-width above the deck: visibly WEIGHTLESS. A thin cyan tether-beam
    // anchors its lowest corner to a small deck clamp; the ground shadow sits offset and
    // gapped beneath it (never touching) to sell the zero-g read.
    private static Texture generateFloatingCargoCrateTexture() {
        final int textureWidth = 112, textureHeight = 128;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Offset, gapped ground shadow — the weightless tell; never touches the crate above it.
        glowPool(pixmap, 58, 122, 34, 5, 0f, 0f, 0f, 0.35f);

        // Faint secondary crate hovering behind/above, at reduced alpha.
        fillQuad(pixmap, STELLAR_HULL_TITANIUM_R, STELLAR_HULL_TITANIUM_G, STELLAR_HULL_TITANIUM_B, 0.30f,
                 10, 8, 44, 20, 36, 40, 2, 28);

        // Front face — a rectangle rotated ~20 degrees to sell the tilt.
        final int frontTopLeftX = 37,     frontTopLeftY = 24;
        final int frontTopRightX = 90,    frontTopRightY = 43;
        final int frontBottomRightX = 75, frontBottomRightY = 84;
        final int frontBottomLeftX = 22,  frontBottomLeftY = 65;
        fillQuad(pixmap, STELLAR_HULL_TITANIUM_R, STELLAR_HULL_TITANIUM_G, STELLAR_HULL_TITANIUM_B, 1f,
                 frontTopLeftX, frontTopLeftY, frontTopRightX, frontTopRightY,
                 frontBottomRightX, frontBottomRightY, frontBottomLeftX, frontBottomLeftY);

        // Top face — brighter, receding up-and-left, so the canted top reads clearly.
        final int topBackLeftX  = frontTopLeftX - 12,  topBackLeftY  = frontTopLeftY - 14;
        final int topBackRightX = frontTopRightX - 12, topBackRightY = frontTopRightY - 14;
        fillQuad(pixmap, STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 1f,
                 frontTopLeftX, frontTopLeftY, frontTopRightX, frontTopRightY,
                 topBackRightX, topBackRightY, topBackLeftX, topBackLeftY);

        // Panel edges — dark seams along every side of both faces.
        pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
        pixmap.drawLine(frontTopLeftX, frontTopLeftY, frontTopRightX, frontTopRightY);
        pixmap.drawLine(frontTopRightX, frontTopRightY, frontBottomRightX, frontBottomRightY);
        pixmap.drawLine(frontBottomRightX, frontBottomRightY, frontBottomLeftX, frontBottomLeftY);
        pixmap.drawLine(frontBottomLeftX, frontBottomLeftY, frontTopLeftX, frontTopLeftY);
        pixmap.drawLine(topBackLeftX, topBackLeftY, topBackRightX, topBackRightY);
        pixmap.drawLine(frontTopLeftX, frontTopLeftY, topBackLeftX, topBackLeftY);
        pixmap.drawLine(frontTopRightX, frontTopRightY, topBackRightX, topBackRightY);

        // Corner brackets — small bright steel L-brackets at opposing front-face corners.
        pixmap.setColor(STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 1f);
        pixmap.fillRectangle(frontTopLeftX - 1,     frontTopLeftY,          6, 2);
        pixmap.fillRectangle(frontTopLeftX - 1,     frontTopLeftY,          2, 6);
        pixmap.fillRectangle(frontBottomRightX - 5, frontBottomRightY - 1, 6, 2);
        pixmap.fillRectangle(frontBottomRightX - 1, frontBottomRightY - 5, 2, 6);

        // Small UAC stencil mark on the front face.
        pixmap.setColor(0.30f, 0.32f, 0.36f, 1f);
        pixmap.fillRectangle(48, 48, 2, 8); pixmap.fillRectangle(54, 48, 2, 8); pixmap.fillRectangle(48, 54, 8, 2);
        pixmap.fillRectangle(60, 48, 2, 8); pixmap.fillRectangle(66, 48, 2, 8);
        pixmap.fillRectangle(60, 48, 8, 2); pixmap.fillRectangle(60, 51, 8, 2);

        // Deck clamp puck, gapped beneath the crate's lowest corner.
        pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
        pixmap.fillRectangle(52, 116, 12, 6);
        pixmap.setColor(STELLAR_HULL_TITANIUM_R, STELLAR_HULL_TITANIUM_G, STELLAR_HULL_TITANIUM_B, 1f);
        pixmap.fillRectangle(52, 116, 12, 1);
        boltStud(pixmap, 58, 119);

        // Tether-beam: thin cyan light connecting the crate's lowest corner to the clamp —
        // the only thing keeping the crate from drifting away.
        pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 0.30f);
        pixmap.drawLine(frontBottomRightX - 1, frontBottomRightY, 57, 116);
        pixmap.drawLine(frontBottomRightX + 1, frontBottomRightY, 59, 116);
        pixmap.setColor(STELLAR_ICE_BLUE_R, STELLAR_ICE_BLUE_G, STELLAR_ICE_BLUE_B, 0.80f);
        pixmap.drawLine(frontBottomRightX, frontBottomRightY, 58, 116);

        return finalize(pixmap);
    }

    // Docking strut pylon ('|') — a slim tapered vertical titanium column with a wide clamp
    // collar at ~55% height, cable conduits running up one side, a vertical ladder of green
    // status LEDs, and one small warm amber service lamp near the top — the ONLY warm accent
    // anywhere in the room. No hazard stripes; this reads as clean, load-bearing ship
    // architecture, not battle armour.
    private static Texture generateDockingStrutPylonTexture() {
        final int textureWidth = 48, textureHeight = 192;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        final int centerX = 24;
        final int columnTop = 6, columnBottom = 186;
        final int bottomHalfWidth = 15, topHalfWidth = 7;

        // Tapered titanium shaft — dark shadow side (left), lit body (right), bright edge.
        for (int row = columnTop; row < columnBottom; row++) {
            float toBottom = (row - columnTop) / (float) (columnBottom - columnTop);
            int halfWidth  = Math.round(topHalfWidth + (bottomHalfWidth - topHalfWidth) * toBottom);
            pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
            pixmap.fillRectangle(centerX - halfWidth, row, halfWidth, 1);
            pixmap.setColor(STELLAR_HULL_TITANIUM_R, STELLAR_HULL_TITANIUM_G, STELLAR_HULL_TITANIUM_B, 1f);
            pixmap.fillRectangle(centerX, row, halfWidth, 1);
            pixmap.setColor(STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 1f);
            pixmap.fillRectangle(centerX + halfWidth - 1, row, 1, 1);
        }
        // Base foot plate + top cap plate.
        beveledPanel(pixmap, centerX - bottomHalfWidth - 3, columnBottom, bottomHalfWidth * 2 + 6, 6,
                     STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1.5f, 0.5f);
        beveledPanel(pixmap, centerX - topHalfWidth - 2, columnTop - 6, topHalfWidth * 2 + 4, 6,
                     STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1.5f, 0.5f);

        // Clamp collar at ~55% height (from the base) — a wide dark ring with bolt dots.
        int collarRow = columnTop + Math.round((columnBottom - columnTop) * 0.45f);
        int collarHalfWidth = Math.round(topHalfWidth + (bottomHalfWidth - topHalfWidth)
                             * ((collarRow - columnTop) / (float) (columnBottom - columnTop))) + 3;
        pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
        pixmap.fillRectangle(centerX - collarHalfWidth, collarRow - 5, collarHalfWidth * 2, 10);
        for (int boltIndex = 0; boltIndex < 5; boltIndex++) {
            int boltX = centerX - collarHalfWidth + 3 + boltIndex * (collarHalfWidth * 2 - 6) / 4;
            boltStud(pixmap, boltX, collarRow);
        }

        // Cable conduits — two thin dark lines running up one side of the shaft.
        pixmap.setColor(0.10f, 0.11f, 0.13f, 1f);
        pixmap.fillRectangle(centerX - 3, columnTop + 10, 1, columnBottom - columnTop - 20);
        pixmap.fillRectangle(centerX - 6, columnTop + 16, 1, columnBottom - columnTop - 32);

        // Vertical ladder of green status LEDs climbing the shaft (skipping the collar band).
        pixmap.setColor(STELLAR_LED_GREEN_R, STELLAR_LED_GREEN_G, STELLAR_LED_GREEN_B, 1f);
        for (int ledRow = columnTop + 16; ledRow < columnBottom - 10; ledRow += 18) {
            if (Math.abs(ledRow - collarRow) < 8) continue;
            pixmap.fillRectangle(centerX + 2, ledRow, 3, 3);
        }

        // The single warm accent in the whole room — a small amber maintenance lamp near the top.
        pixmap.setColor(STELLAR_LED_AMBER_R, STELLAR_LED_AMBER_G, STELLAR_LED_AMBER_B, 0.35f);
        pixmap.fillCircle(centerX - 5, columnTop + 14, 5);
        pixmap.setColor(STELLAR_LED_AMBER_R, STELLAR_LED_AMBER_G, STELLAR_LED_AMBER_B, 1f);
        pixmap.fillCircle(centerX - 5, columnTop + 14, 2);

        return finalize(pixmap);
    }

    // Astro-nav holo table ('<') — a low ring-shaped console deck with cyan readout strips
    // around its rim, projecting a translucent wireframe star-chart globe above: latitude and
    // longitude arcs, plotted route dots with connecting orbital arcs, and a couple of tiny
    // drifting data glyphs. A cooler, navigation-flavoured cousin of the holo-workstation 'W'.
    private static Texture generateAstroNavHoloTableTexture() {
        final int textureWidth = 120, textureHeight = 108;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        final int deckCenterX = 60, deckCenterY = 90, deckRadiusX = 46, deckRadiusY = 14;

        // ── Ring-shaped console deck ──
        for (int pixelRow = deckCenterY - deckRadiusY; pixelRow <= deckCenterY + deckRadiusY; pixelRow++) {
            for (int pixelColumn = deckCenterX - deckRadiusX; pixelColumn <= deckCenterX + deckRadiusX; pixelColumn++) {
                float normalizedX = (pixelColumn - deckCenterX) / (float) deckRadiusX;
                float normalizedY = (pixelRow - deckCenterY) / (float) deckRadiusY;
                float distance = normalizedX * normalizedX + normalizedY * normalizedY;
                if (distance > 1f) continue;
                pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }
        // Titanium rim ring.
        pixmap.setColor(STELLAR_HULL_TITANIUM_R, STELLAR_HULL_TITANIUM_G, STELLAR_HULL_TITANIUM_B, 1f);
        for (int degree = 0; degree < 360; degree += 2) {
            double radians = Math.toRadians(degree);
            int rimX = deckCenterX + Math.round(deckRadiusX * (float) Math.cos(radians));
            int rimY = deckCenterY + Math.round(deckRadiusY * (float) Math.sin(radians));
            pixmap.drawPixel(rimX, rimY);
        }
        // Cyan readout strips around the front half of the rim (facing the player).
        pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 0.85f);
        for (int degree = 20; degree <= 160; degree += 20) {
            double radians = Math.toRadians(degree);
            int stripX = deckCenterX + Math.round((deckRadiusX - 6) * (float) Math.cos(radians));
            int stripY = deckCenterY + Math.round((deckRadiusY - 3) * (float) Math.sin(radians));
            pixmap.fillRectangle(stripX - 1, stripY, 2, 2);
        }

        // ── Faint upward emitter cone from the deck to the globe ──
        final int globeCenterX = 60, globeCenterY = 46, globeRadius = 32;
        for (int beamIndex = -3; beamIndex <= 3; beamIndex++) {
            float brightness = 1f - Math.abs(beamIndex) / 4.5f;
            pixmap.setColor(STELLAR_CYAN_GLOW_R * brightness, STELLAR_CYAN_GLOW_G * brightness,
                            STELLAR_CYAN_GLOW_B * brightness, 0.35f);
            int beamBottomRow = globeCenterY + globeRadius;
            int beamTopRow    = deckCenterY - 4;
            for (int beamRow = beamBottomRow; beamRow < beamTopRow; beamRow++) {
                float fraction = (beamRow - beamBottomRow) / (float) (beamTopRow - beamBottomRow);
                int beamColumn = Math.round(deckCenterX + beamIndex * 10 * (1f - fraction));
                pixmap.drawPixel(beamColumn, beamRow);
            }
        }

        // ── Wireframe star-chart globe: scan-lined volume fill + longitude/latitude arcs ──
        for (int pixelRow = globeCenterY - globeRadius; pixelRow <= globeCenterY + globeRadius; pixelRow++) {
            for (int pixelColumn = globeCenterX - globeRadius; pixelColumn <= globeCenterX + globeRadius; pixelColumn++) {
                float normalizedX = (pixelColumn - globeCenterX) / (float) globeRadius;
                float normalizedY = (pixelRow - globeCenterY) / (float) globeRadius;
                float distance = (float) Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
                if (distance > 1f) continue;
                if ((pixelRow % 2) == 0) continue;
                pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 0.12f * (1f - distance));
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }
        pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 0.75f);
        pixmap.drawCircle(globeCenterX, globeCenterY, globeRadius);
        float[] longitudeWidths = { globeRadius, globeRadius * 0.55f, 0f };
        for (float longitudeWidth : longitudeWidths) {
            for (int degree = 0; degree < 360; degree += 8) {
                double radians = Math.toRadians(degree);
                int pointColumn = globeCenterX + Math.round(longitudeWidth * (float) Math.sin(radians));
                int pointRow    = globeCenterY + Math.round(globeRadius * (float) Math.cos(radians));
                pixmap.drawPixel(pointColumn, pointRow);
            }
        }
        int[] latitudeOffsets = { -14, 0, 14 };
        for (int latitudeOffset : latitudeOffsets) {
            float halfWidth  = (float) Math.sqrt(Math.max(0, globeRadius * globeRadius - latitudeOffset * latitudeOffset));
            float halfHeight = halfWidth * 0.30f;
            for (int degree = 0; degree < 360; degree += 8) {
                double radians = Math.toRadians(degree);
                int pointColumn = globeCenterX + Math.round(halfWidth * (float) Math.cos(radians));
                int pointRow    = globeCenterY + latitudeOffset + Math.round(halfHeight * (float) Math.sin(radians));
                pixmap.drawPixel(pointColumn, pointRow);
            }
        }

        // ── Plotted route dots + connecting orbital arcs ──
        int[] routeDotColumns = { 44, 58, 74, 50, 68 };
        int[] routeDotRows    = { 36, 26, 40, 56, 60 };
        pixmap.setColor(STELLAR_ICE_BLUE_R, STELLAR_ICE_BLUE_G, STELLAR_ICE_BLUE_B, 1f);
        for (int dotIndex = 0; dotIndex < routeDotColumns.length; dotIndex++) {
            pixmap.fillCircle(routeDotColumns[dotIndex], routeDotRows[dotIndex], 2);
        }
        pixmap.setColor(STELLAR_ICE_BLUE_R, STELLAR_ICE_BLUE_G, STELLAR_ICE_BLUE_B, 0.6f);
        pixmap.drawLine(routeDotColumns[0], routeDotRows[0], routeDotColumns[1], routeDotRows[1]);
        pixmap.drawLine(routeDotColumns[1], routeDotRows[1], routeDotColumns[2], routeDotRows[2]);
        pixmap.drawLine(routeDotColumns[3], routeDotRows[3], routeDotColumns[4], routeDotRows[4]);

        // ── Drifting data glyphs flanking the globe ──
        pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 0.85f);
        pixmap.fillRectangle(12, 20, 8, 2);
        pixmap.fillRectangle(14, 24, 5, 2);
        pixmap.fillRectangle(100, 22, 8, 2);
        pixmap.fillRectangle(98, 26, 6, 2);

        return finalize(pixmap);
    }

    // Magnetic deck plating ('?') — a walkable grated panel marking the rotunda's "official"
    // walking lanes: a diamond grating pattern in cool gunmetal, a thin cyan alignment line
    // down the centre, and light wear scuffs. Same low-profile ground band as '.'/'O'/'e'.
    private static Texture generateMagneticDeckPlatingTexture() {
        final int textureWidth = 112, textureHeight = 56;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        java.util.Random random = new java.util.Random(0x4D41474445434BL); // "MAGDECK" (truncated ASCII)

        final int panelLeft = 6, panelTop = 6, panelWidth = 100, panelHeight = 44;
        pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
        pixmap.fillRectangle(panelLeft, panelTop, panelWidth, panelHeight);

        // Diamond grating pattern — two crossing families of diagonal hairlines, drawn with a
        // bounded per-pixel modulo test (same technique as hazardStripeBand) so every pixel
        // stays inside the panel rectangle.
        for (int row = panelTop; row < panelTop + panelHeight; row++) {
            for (int column = panelLeft; column < panelLeft + panelWidth; column++) {
                boolean onDiagonalA = (((column - row) % 10) + 10) % 10 == 0;
                boolean onDiagonalB = (((column + row) % 10) + 10) % 10 == 0;
                if (onDiagonalA || onDiagonalB) {
                    pixmap.setColor(STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 0.5f);
                    pixmap.drawPixel(column, row);
                }
            }
        }
        // Magnetic-lock seams dividing the panel into 3 sub-plates.
        pixmap.setColor(STELLAR_VOID_BLACK_R, STELLAR_VOID_BLACK_G, STELLAR_VOID_BLACK_B, 1f);
        pixmap.fillRectangle(panelLeft, panelTop, panelWidth, 1);
        pixmap.fillRectangle(panelLeft, panelTop + panelHeight - 1, panelWidth, 1);
        pixmap.fillRectangle(panelLeft + panelWidth / 3, panelTop, 1, panelHeight);
        pixmap.fillRectangle(panelLeft + panelWidth * 2 / 3, panelTop, 1, panelHeight);

        // Thin cyan alignment line down the centre of the walking lane.
        pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 0.7f);
        pixmap.fillRectangle(panelLeft, panelTop + panelHeight / 2 - 1, panelWidth, 2);

        // Wear scuffs — bright random specks.
        pixmap.setColor(STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 0.8f);
        for (int scuffIndex = 0; scuffIndex < 14; scuffIndex++) {
            pixmap.drawPixel(panelLeft + random.nextInt(panelWidth), panelTop + random.nextInt(panelHeight));
        }
        return finalize(pixmap);
    }

    // Zero-g debris scatter (']') — a handful of tiny objects resting near-weightlessly on
    // the deck: bolts, a small data-slate with a cyan screen line, a pale insulation fluff
    // tuft, and glass shard glints. Each has a small drop-shadow GAPPED beneath it (never
    // touching) to imply micro-gravity, and a few carry a faint drift-blur streak. Cool metal
    // tones only — no organic content, clearly distinct from a corpse/blood decal.
    private static Texture generateZeroGDebrisScatterTexture() {
        final int textureWidth = 112, textureHeight = 56;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Object positions/kinds: 0=bolt, 1=data-slate, 2=fluff tuft, 3=glass shard.
        int[] objectColumns = { 18, 34, 50, 66, 82, 96, 26, 58, 74, 44 };
        int[] objectRows    = { 30, 22, 34, 20, 30, 24, 40, 42, 38, 14 };
        int[] objectKinds   = {  0,  1,  2,  3,  0,  1,  2,  0,  3,  1 };

        for (int objectIndex = 0; objectIndex < objectColumns.length; objectIndex++) {
            int objectX = objectColumns[objectIndex];
            int objectY = objectRows[objectIndex];

            // Gapped drop-shadow — sits a couple of pixels below the object, never touching it.
            pixmap.setColor(0f, 0f, 0f, 0.30f);
            pixmap.fillCircle(objectX, objectY + 4, 2);

            switch (objectKinds[objectIndex]) {
                case 0: // bolt
                    pixmap.setColor(STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 1f);
                    pixmap.fillCircle(objectX, objectY, 2);
                    pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
                    pixmap.drawPixel(objectX, objectY);
                    break;
                case 1: // small data-slate with a cyan screen line
                    pixmap.setColor(0.10f, 0.11f, 0.14f, 1f);
                    pixmap.fillRectangle(objectX - 3, objectY - 2, 6, 4);
                    pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 1f);
                    pixmap.fillRectangle(objectX - 2, objectY - 1, 4, 1);
                    break;
                case 2: // pale insulation fluff tuft
                    pixmap.setColor(0.55f, 0.56f, 0.52f, 0.75f);
                    pixmap.fillCircle(objectX, objectY, 3);
                    pixmap.setColor(0.70f, 0.70f, 0.66f, 0.60f);
                    pixmap.fillCircle(objectX + 2, objectY - 1, 2);
                    break;
                default: // glass shard glint
                    pixmap.setColor(STELLAR_STAR_BLUE_R, STELLAR_STAR_BLUE_G, STELLAR_STAR_BLUE_B, 0.9f);
                    pixmap.fillTriangle(objectX - 3, objectY + 2, objectX + 3, objectY + 1, objectX, objectY - 3);
                    pixmap.setColor(1f, 1f, 1f, 0.8f);
                    pixmap.drawPixel(objectX, objectY - 1);
                    break;
            }
        }

        // Faint 2px drift-blur streaks on a few objects — implies near-weightless drift.
        pixmap.setColor(STELLAR_ICE_BLUE_R, STELLAR_ICE_BLUE_G, STELLAR_ICE_BLUE_B, 0.25f);
        pixmap.drawLine(objectColumns[1] - 5, objectRows[1],     objectColumns[1], objectRows[1]);
        pixmap.drawLine(objectColumns[4] - 5, objectRows[4] - 1, objectColumns[4], objectRows[4] - 1);
        pixmap.drawLine(objectColumns[8] + 5, objectRows[8],     objectColumns[8], objectRows[8]);

        return finalize(pixmap);
    }

    // Starlight seam strip ('-') — a narrow inlaid deck light channel running the tile's
    // length: a 2-3px emissive core (white centre fading to blue at the edges), a soft bloom
    // wash either side, a faint chromatic-aberration fringe, and end caps that taper it into
    // a thin dark recess so it reads as inlaid fibre-optic guide, not a floating line.
    private static Texture generateStarlightSeamStripTexture() {
        final int textureWidth = 112, textureHeight = 24;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        final int stripLeft = 4, stripRight = 108, centerRow = 12;

        // Dark recessed channel the light sits in.
        pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
        pixmap.fillRectangle(stripLeft, centerRow - 5, stripRight - stripLeft, 10);

        // Soft bloom either side of the core.
        pixmap.setColor(STELLAR_STAR_BLUE_R, STELLAR_STAR_BLUE_G, STELLAR_STAR_BLUE_B, 0.28f);
        pixmap.fillRectangle(stripLeft, centerRow - 4, stripRight - stripLeft, 8);

        // Faint chromatic-aberration fringe — a hint of magenta above, cyan below the core.
        pixmap.setColor(0.55f, 0.22f, 0.55f, 0.30f);
        pixmap.fillRectangle(stripLeft, centerRow - 2, stripRight - stripLeft, 1);
        pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 0.30f);
        pixmap.fillRectangle(stripLeft, centerRow + 1, stripRight - stripLeft, 1);

        // Emissive core: white centre fading to blue at the very edge of the 3px channel.
        pixmap.setColor(STELLAR_STAR_BLUE_R, STELLAR_STAR_BLUE_G, STELLAR_STAR_BLUE_B, 1f);
        pixmap.fillRectangle(stripLeft, centerRow - 1, stripRight - stripLeft, 3);
        pixmap.setColor(STELLAR_SEAM_WHITE_R, STELLAR_SEAM_WHITE_G, STELLAR_SEAM_WHITE_B, 1f);
        pixmap.fillRectangle(stripLeft, centerRow, stripRight - stripLeft, 1);

        // End caps taper the channel into the recess so it reads as inlaid, not floating.
        pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
        pixmap.fillRectangle(stripLeft, centerRow - 5, 2, 10);
        pixmap.fillRectangle(stripRight - 2, centerRow - 5, 2, 10);

        return finalize(pixmap);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Animated terrain hazards — fire ('i') and toxic ('q').
    //
    // Both are authored as a short loop of procedurally-generated frames (built once at
    // construction, cycled by PropRenderer at HAZARD_ANIMATION_FPS). Each frame is high
    // resolution and authored to span the full tile footprint when billboarded.
    //
    // Pixmap convention: row 0 = top edge, so the tile floor is the LAST row (height-1) and
    // anything that rises (flames, gas) moves toward row 0.
    // ═══════════════════════════════════════════════════════════════════════════

    // Fire frame canvas — wider than tall so a full-width bed of spiky tongues fits.
    private static final int HAZARD_FIRE_FRAME_WIDTH  = 128;
    private static final int HAZARD_FIRE_FRAME_HEIGHT = 112;
    // Toxic frame canvas — wide and shallow so the gas cloud reads as a low hanging billow.
    private static final int HAZARD_TOXIC_FRAME_WIDTH  = 160;
    private static final int HAZARD_TOXIC_FRAME_HEIGHT = 96;

    // Fire hazard ('i') — one animation frame. A full-width glowing ember bed topped by many
    // sharply-tapered (spiky) flame tongues that lick upward, plus drifting embers. Tongue
    // heights and sway are driven by the frame phase so the loop reads as living, flickering fire.
    private static Texture generateHazardFireFrame(int frameIndex, int frameCount) {
        int width  = HAZARD_FIRE_FRAME_WIDTH;
        int height = HAZARD_FIRE_FRAME_HEIGHT;
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        java.util.Random random = new java.util.Random(0xF12EB1A2EL + frameIndex * 0x9E3779B9L);
        int   floorY = height - 1;
        float phase  = frameIndex / (float) frameCount * MathUtils.PI2;

        // Glowing ember bed across the full width — hottest (yellow-white) at the floor, fading to
        // deep red as it rises, with a wavy animated top edge.
        int bedHeight = 30;
        for (int column = 0; column < width; column++) {
            float wave = MathUtils.sin(phase + column * 0.20f) * 3f
                       + MathUtils.sin(phase * 1.7f + column * 0.07f) * 2f;
            int bedTop = floorY - bedHeight - Math.round(wave);
            for (int row = floorY; row >= bedTop && row >= 0; row--) {
                float fraction = (floorY - row) / (float) Math.max(1, floorY - bedTop);
                float red   = 1f;
                float green = 0.62f - 0.48f * fraction;
                float blue  = 0.14f * (1f - fraction);
                float alpha = 1f - fraction * 0.45f;
                pixmap.setColor(red, green, blue, alpha);
                pixmap.drawPixel(column, row);
            }
        }

        // Primary tongues — one per evenly-spaced slot so flames cover the whole tile width.
        int tongueCount = 13;
        for (int tongueIndex = 0; tongueIndex < tongueCount; tongueIndex++) {
            float slot      = (tongueIndex + 0.5f) / tongueCount;
            int   baseX     = Math.round(slot * width
                              + (random.nextFloat() - 0.5f) * (width / (float) tongueCount));
            float seed      = tongueIndex * 1.3f;
            // Animated height: rises and falls with the frame phase; a mild centre bell keeps the
            // middle of the fire taller than the edges for a natural flame silhouette.
            float pulse     = 0.5f + 0.5f * MathUtils.sin(phase + seed);
            float bell      = 0.70f + 0.30f * MathUtils.sin(slot * MathUtils.PI);
            int   tongueHeight   = Math.round(height * (0.40f + 0.48f * pulse) * bell);
            float baseHalfWidth  = width / (float) tongueCount * (0.55f + random.nextFloat() * 0.35f);
            float swayAmplitude  = 6f + random.nextFloat() * 6f;
            drawFlameTongue(pixmap, baseX, floorY, tongueHeight, baseHalfWidth, swayAmplitude,
                            phase + seed);
        }

        // Secondary spikes — thin tall licks scattered on top for extra spikiness.
        for (int spikeIndex = 0; spikeIndex < 8; spikeIndex++) {
            int   baseX        = random.nextInt(width);
            float pulse        = 0.5f + 0.5f * MathUtils.sin(phase * 1.3f + spikeIndex);
            int   tongueHeight = Math.round(height * (0.55f + 0.40f * pulse));
            drawFlameTongue(pixmap, baseX, floorY, tongueHeight, 3f + random.nextFloat() * 2f,
                            9f, phase + spikeIndex);
        }

        // Drifting embers rising off the fire.
        for (int emberIndex = 0; emberIndex < 16; emberIndex++) {
            float emberFraction = random.nextFloat();
            int   emberX = random.nextInt(width);
            int   emberY = floorY - Math.round((0.20f + 0.75f * emberFraction) * height)
                           - Math.round(MathUtils.sin(phase + emberIndex) * 4f);
            if (emberY < 0 || emberY >= height) continue;
            pixmap.setColor(1f, 0.85f + 0.10f * random.nextFloat(), 0.40f, 1f - emberFraction);
            pixmap.drawPixel(emberX, emberY);
        }
        return finalize(pixmap);
    }

    // Draws a single spiky flame tongue rising from (baseX, floorY). The tongue tapers sharply to a
    // point (spiky) and sways sideways more toward the tip; colour runs white-yellow hot at the
    // base through orange to deep red at the fading tip.
    private static void drawFlameTongue(Pixmap pixmap, int baseX, int floorY, int tongueHeight,
                                        float baseHalfWidth, float swayAmplitude, float swayPhase) {
        if (tongueHeight <= 0) return;
        for (int step = 0; step <= tongueHeight; step++) {
            float fraction = step / (float) tongueHeight;          // 0 at base, 1 at the tip
            int   drawY    = floorY - step;                        // rise toward the top of the image
            if (drawY < 0) break;
            // Sharp power-law taper collapses the width fast near the tip → a pointed, spiky flame.
            float taper      = (float) Math.pow(1f - fraction, 1.6f);
            int   halfPixels = Math.max(0, Math.round(baseHalfWidth * taper));
            float sway       = swayAmplitude * fraction * MathUtils.sin(swayPhase + fraction * 3.1f);
            int   centerX    = Math.round(baseX + sway);
            float red, green, blue;
            if (fraction < 0.45f) {
                float localFraction = fraction / 0.45f;            // white-yellow → orange
                red   = 1f;
                green = 0.95f - 0.35f * localFraction;
                blue  = 0.55f - 0.45f * localFraction;
            } else {
                float localFraction = (fraction - 0.45f) / 0.55f;  // orange → deep red
                red   = 1f - 0.15f * localFraction;
                green = 0.60f - 0.50f * localFraction;
                blue  = 0.10f * (1f - localFraction);
            }
            float alpha = 1f - fraction * fraction * 0.70f;         // soft fade toward the tip
            pixmap.setColor(red, green, blue, alpha);
            pixmap.fillRectangle(centerX - halfPixels, drawY, halfPixels * 2 + 1, 1);
            // Brighter inner core on the lower half of the tongue.
            if (fraction < 0.60f && halfPixels > 1) {
                int coreHalf = halfPixels / 2;
                pixmap.setColor(1f, 0.98f, 0.78f, alpha);
                pixmap.fillRectangle(centerX - coreHalf, drawY, coreHalf * 2 + 1, 1);
            }
        }
    }

    // Toxic hazard ('q') — one animation frame. A billowing, full-width gas cloud built from many
    // overlapping soft puffs (denser near the floor, wispier up top), brighter acid-green
    // highlights, and rising bubbles. Puff positions drift with the frame phase so the cloud rolls.
    private static Texture generateHazardToxicFrame(int frameIndex, int frameCount) {
        int width  = HAZARD_TOXIC_FRAME_WIDTH;
        int height = HAZARD_TOXIC_FRAME_HEIGHT;
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        java.util.Random random = new java.util.Random(0x70C1CAC1DL + frameIndex * 0x9E3779B9L);
        int   floorY = height - 1;
        float phase  = frameIndex / (float) frameCount * MathUtils.PI2;

        // Main body — many large soft puffs, biased toward the floor so the cloud is densest low
        // and thins out as it billows upward.
        int puffCount = 44;
        for (int puffIndex = 0; puffIndex < puffCount; puffIndex++) {
            float slotX      = random.nextFloat();
            float heightBias = random.nextFloat() * random.nextFloat();   // skewed toward 0 → low
            float drift      = MathUtils.sin(phase + slotX * MathUtils.PI2) * 6f;
            float bob        = MathUtils.sin(phase * 1.3f + puffIndex) * 3f;
            int   centerX    = Math.round(slotX * width + drift);
            int   centerY    = floorY - Math.round(heightBias * height + bob);
            int   radius     = 10 + random.nextInt(16);
            float red        = 0.20f + 0.16f * random.nextFloat();
            float green      = 0.52f + 0.30f * random.nextFloat();
            float blue       = 0.08f + 0.10f * random.nextFloat();
            float coreAlpha  = 0.18f + 0.16f * random.nextFloat();
            softPuff(pixmap, centerX, centerY, radius, red, green, blue, coreAlpha);
        }

        // Bright acid-green highlight puffs — smaller, near the dense lower mass.
        for (int highlightIndex = 0; highlightIndex < 16; highlightIndex++) {
            float slotX   = random.nextFloat();
            float drift   = MathUtils.sin(phase + slotX * MathUtils.PI2) * 5f;
            int   centerX = Math.round(slotX * width + drift);
            int   centerY = floorY - Math.round(random.nextFloat() * height * 0.55f);
            int   radius  = 5 + random.nextInt(8);
            softPuff(pixmap, centerX, centerY, radius, 0.52f, 0.92f, 0.26f, 0.22f);
        }

        // Rising bubbles — small bright ringed dots drifting up through the cloud.
        for (int bubbleIndex = 0; bubbleIndex < 11; bubbleIndex++) {
            int bubbleX = random.nextInt(width);
            int bubbleY = floorY - Math.round(random.nextFloat() * height * 0.7f)
                          - Math.round(MathUtils.sin(phase + bubbleIndex) * 4f);
            if (bubbleY < 0 || bubbleY >= height) continue;
            int bubbleRadius = 1 + random.nextInt(3);
            pixmap.setColor(0.80f, 1.0f, 0.45f, 0.85f);
            pixmap.drawCircle(bubbleX, bubbleY, bubbleRadius);
            pixmap.setColor(0.92f, 1.0f, 0.60f, 1f);
            pixmap.drawPixel(bubbleX, bubbleY);
        }
        return finalize(pixmap);
    }

    // Draws a soft round gas puff: concentric filled discs from the outer radius inward, each with a
    // quadratic alpha falloff. Relies on the Pixmap's default SourceOver blending so the overlaps
    // accumulate into a dense core with a feathered, cloudy edge.
    private static void softPuff(Pixmap pixmap, int centerX, int centerY, int radius,
                                 float red, float green, float blue, float coreAlpha) {
        for (int ring = radius; ring >= 1; ring--) {
            float edgeFraction = ring / (float) radius;            // 1 at the edge → 0 at the centre
            float alpha        = coreAlpha * (1f - edgeFraction) * (1f - edgeFraction);
            if (alpha <= 0.003f) continue;
            pixmap.setColor(red, green, blue, alpha);
            pixmap.fillCircle(centerX, centerY, ring);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Ammo pickup pixel art — one distinct silhouette per ammo type.
    //
    // Every ammo type used to share a single tinted box sprite, telling the five
    // reserves apart by colour alone. Each now has its own readable shape so a
    // player can identify ammo at a glance even before the colour registers:
    //
    //   BULLETS ('6') — a stripper clip of slim copper rifle cartridges (upright)
    //   SHELLS  ('7') — a row of red-hulled shotgun shells on brass bases
    //   CELLS   ('8') — a glowing cyan plasma energy canister with a bolt glyph
    //   ROCKETS ('9') — an olive warhead with a red tip and tail fins (side-on)
    //   SLUGS   ('0') — a stack of heavy blunt silver rail rounds (side-on)
    //
    // All five are authored on a 96×64 RGBA8888 canvas (same 3:2 ratio as the old
    // 48×32 box, so the on-screen billboard footprint is unchanged) with a
    // transparent background and Nearest filtering. Pixmap Y=0 is the top edge.
    // ═══════════════════════════════════════════════════════════════════════════

    private static final int AMMO_SPRITE_WIDTH  = 96;
    private static final int AMMO_SPRITE_HEIGHT = 64;

    // Fills a rounded vertical "cylinder" body: a flat mid tone, a bright specular
    // strip left of centre and a shaded right flank, so a flat rectangle reads as a
    // round metal/plastic case under the game's flat lighting.
    private static void shadedVerticalCylinder(Pixmap pixmap, int x, int y, int width, int height,
                                               float red, float green, float blue,
                                               float litFactor, float shadeFactor) {
        pixmap.setColor(red, green, blue, 1f);
        pixmap.fillRectangle(x, y, width, height);
        // Shaded right flank.
        int flankWidth = Math.max(1, width / 4);
        pixmap.setColor(red * shadeFactor, green * shadeFactor, blue * shadeFactor, 1f);
        pixmap.fillRectangle(x + width - flankWidth, y, flankWidth, height);
        // Dark left edge.
        pixmap.setColor(red * shadeFactor * 0.85f, green * shadeFactor * 0.85f, blue * shadeFactor * 0.85f, 1f);
        pixmap.fillRectangle(x, y, 1, height);
        // Bright specular highlight strip left of centre.
        int highlightX     = x + Math.max(1, width / 4);
        int highlightWidth = Math.max(1, width / 6);
        pixmap.setColor(Math.min(1f, red * litFactor), Math.min(1f, green * litFactor), Math.min(1f, blue * litFactor), 1f);
        pixmap.fillRectangle(highlightX, y, highlightWidth, height);
    }

    // Fills a rounded horizontal "cylinder" body: lit top strip, shaded underside.
    private static void shadedHorizontalCylinder(Pixmap pixmap, int x, int y, int width, int height,
                                                 float red, float green, float blue,
                                                 float litFactor, float shadeFactor) {
        pixmap.setColor(red, green, blue, 1f);
        pixmap.fillRectangle(x, y, width, height);
        // Shaded underside.
        int flankHeight = Math.max(1, height / 4);
        pixmap.setColor(red * shadeFactor, green * shadeFactor, blue * shadeFactor, 1f);
        pixmap.fillRectangle(x, y + height - flankHeight, width, flankHeight);
        // Bright specular highlight strip above centre.
        int highlightY      = y + Math.max(1, height / 5);
        int highlightHeight = Math.max(1, height / 6);
        pixmap.setColor(Math.min(1f, red * litFactor), Math.min(1f, green * litFactor), Math.min(1f, blue * litFactor), 1f);
        pixmap.fillRectangle(x, highlightY, width, highlightHeight);
    }

    /** BULLETS — a stripper clip of five slim copper rifle cartridges, standing upright. */
    // Package-private (not private): reused by ItemIconTextures.
    static Texture generateBulletClipTexture() {
        Pixmap pixmap = new Pixmap(AMMO_SPRITE_WIDTH, AMMO_SPRITE_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Brass case palette.
        float brassRed = 0.74f, brassGreen = 0.58f, brassBlue = 0.20f;
        // Copper bullet-tip palette.
        float copperRed = 0.80f, copperGreen = 0.46f, copperBlue = 0.22f;

        final int cartridgeCount = 5;
        final int cartridgeWidth = 12;
        final int spacing        = 16;
        final int firstLeftX     = 8;

        for (int cartridgeIndex = 0; cartridgeIndex < cartridgeCount; cartridgeIndex++) {
            int leftX   = firstLeftX + cartridgeIndex * spacing;
            int centerX = leftX + cartridgeWidth / 2;

            // Brass case body.
            shadedVerticalCylinder(pixmap, leftX, 26, cartridgeWidth, 28,
                    brassRed, brassGreen, brassBlue, 1.35f, 0.55f);
            // Case rim/base ring.
            pixmap.setColor(brassRed * 0.4f, brassGreen * 0.4f, brassBlue * 0.4f, 1f);
            pixmap.fillRectangle(leftX, 52, cartridgeWidth, 2);

            // Shoulder taper into the neck (two stepped rows narrowing toward the tip).
            shadedVerticalCylinder(pixmap, leftX + 2, 22, cartridgeWidth - 4, 4,
                    brassRed, brassGreen, brassBlue, 1.35f, 0.55f);

            // Copper bullet ogive — a tapered tip narrowing to an apex.
            pixmap.setColor(copperRed * 0.55f, copperGreen * 0.55f, copperBlue * 0.55f, 1f);
            pixmap.fillTriangle(leftX + 2, 22, leftX + cartridgeWidth - 2, 22, centerX, 8);
            // Lit copper face on the left half of the ogive.
            pixmap.setColor(Math.min(1f, copperRed * 1.3f), Math.min(1f, copperGreen * 1.3f), Math.min(1f, copperBlue * 1.3f), 1f);
            pixmap.fillTriangle(leftX + 3, 22, centerX, 22, centerX, 10);
        }

        // Stripper clip — a dark metal band gripping the cartridge bases.
        pixmap.setColor(0.26f, 0.27f, 0.30f, 1f);
        pixmap.fillRectangle(4, 44, AMMO_SPRITE_WIDTH - 8, 9);
        // Clip top bevel highlight.
        pixmap.setColor(0.42f, 0.44f, 0.48f, 1f);
        pixmap.fillRectangle(4, 44, AMMO_SPRITE_WIDTH - 8, 1);
        // Clip rivets.
        for (int cartridgeIndex = 0; cartridgeIndex < cartridgeCount; cartridgeIndex++) {
            boltStud(pixmap, firstLeftX + cartridgeIndex * spacing + cartridgeWidth / 2, 48);
        }
        return finalize(pixmap);
    }

    /** SHELLS — a row of four red-plastic shotgun shells seated on brass bases. */
    // Package-private (not private): reused by ItemIconTextures.
    static Texture generateShotgunShellsTexture() {
        Pixmap pixmap = new Pixmap(AMMO_SPRITE_WIDTH, AMMO_SPRITE_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Red plastic hull palette.
        float hullRed = 0.78f, hullGreen = 0.16f, hullBlue = 0.14f;
        // Brass head palette.
        float brassRed = 0.80f, brassGreen = 0.66f, brassBlue = 0.24f;

        final int shellCount = 4;
        final int shellWidth = 16;
        final int spacing    = 22;
        final int firstLeftX = 8;

        for (int shellIndex = 0; shellIndex < shellCount; shellIndex++) {
            int leftX   = firstLeftX + shellIndex * spacing;
            int centerX = leftX + shellWidth / 2;

            // Red plastic hull (upper ~60%).
            shadedVerticalCylinder(pixmap, leftX, 16, shellWidth, 26,
                    hullRed, hullGreen, hullBlue, 1.35f, 0.5f);
            // Crimped rounded top — a darker domed cap with a folded-star centre.
            pixmap.setColor(hullRed * 0.6f, hullGreen * 0.6f, hullBlue * 0.6f, 1f);
            pixmap.fillTriangle(leftX, 18, leftX + shellWidth, 18, centerX, 9);
            pixmap.setColor(hullRed * 0.35f, hullGreen * 0.35f, hullBlue * 0.35f, 1f);
            pixmap.drawLine(centerX, 11, centerX, 17);
            pixmap.drawLine(centerX - 3, 14, centerX + 3, 14);

            // Brass head (lower).
            shadedVerticalCylinder(pixmap, leftX, 42, shellWidth, 14,
                    brassRed, brassGreen, brassBlue, 1.3f, 0.5f);
            // Knurl/crimp seam between hull and brass.
            pixmap.setColor(brassRed * 0.45f, brassGreen * 0.45f, brassBlue * 0.45f, 1f);
            pixmap.fillRectangle(leftX, 42, shellWidth, 2);
            // Primer dot at the base centre.
            pixmap.setColor(0.30f, 0.26f, 0.12f, 1f);
            pixmap.fillCircle(centerX, 53, 2);
        }
        return finalize(pixmap);
    }

    /** CELLS — a glowing cyan plasma energy canister with metal caps and a bolt glyph. */
    // Package-private (not private): reused by ItemIconTextures.
    static Texture generatePlasmaCellTexture() {
        Pixmap pixmap = new Pixmap(AMMO_SPRITE_WIDTH, AMMO_SPRITE_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        final int bodyLeft   = 26;
        final int bodyWidth  = 44;
        final int centerX    = bodyLeft + bodyWidth / 2;

        // Brushed-metal casing.
        beveledPanel(pixmap, bodyLeft, 10, bodyWidth, 44, 0.40f, 0.43f, 0.48f, 1.4f, 0.55f);
        // Top and bottom end caps (darker bands).
        pixmap.setColor(0.22f, 0.24f, 0.28f, 1f);
        pixmap.fillRectangle(bodyLeft, 10, bodyWidth, 6);
        pixmap.fillRectangle(bodyLeft, 48, bodyWidth, 6);
        // Two positive/negative terminal nubs poking out of the top cap.
        pixmap.setColor(0.55f, 0.58f, 0.63f, 1f);
        pixmap.fillRectangle(centerX - 9, 6, 5, 4);
        pixmap.fillRectangle(centerX + 4, 6, 5, 4);

        // Glowing core window — a cyan vertical gradient, brightest at the centre.
        int windowLeft = bodyLeft + 6, windowTop = 18, windowWidth = bodyWidth - 12, windowHeight = 28;
        for (int row = 0; row < windowHeight; row++) {
            // Distance from the vertical centre of the window (0 at centre → 1 at edge).
            float edgeFactor = Math.abs((row - windowHeight / 2f) / (windowHeight / 2f));
            float glow = 1f - 0.6f * edgeFactor;
            pixmap.setColor(0.10f * glow + 0.02f, 0.70f * glow + 0.10f, 0.85f * glow + 0.12f, 1f);
            pixmap.fillRectangle(windowLeft, windowTop + row, windowWidth, 1);
        }
        // Bright vertical core filament.
        pixmap.setColor(0.75f, 0.98f, 1f, 1f);
        pixmap.fillRectangle(centerX - 1, windowTop + 2, 3, windowHeight - 4);
        // Energy scanlines across the window.
        pixmap.setColor(0.05f, 0.35f, 0.45f, 1f);
        for (int scanRow = windowTop + 3; scanRow < windowTop + windowHeight; scanRow += 5) {
            pixmap.fillRectangle(windowLeft, scanRow, windowWidth, 1);
        }
        // Lightning-bolt charge glyph over the core (bright white-cyan zig-zag).
        pixmap.setColor(0.90f, 1f, 1f, 1f);
        pixmap.fillTriangle(centerX + 3, windowTop + 4, centerX - 4, windowTop + 16, centerX, windowTop + 16);
        pixmap.fillTriangle(centerX, windowTop + 12, centerX + 4, windowTop + 12, centerX - 3, windowTop + 24);
        return finalize(pixmap);
    }

    /** ROCKETS — a side-on olive rocket: red warhead, finned tail, exhaust nozzle. */
    // Package-private (not private): reused by ItemIconTextures.
    static Texture generateRocketTexture() {
        Pixmap pixmap = new Pixmap(AMMO_SPRITE_WIDTH, AMMO_SPRITE_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Olive body palette.
        float oliveRed = 0.46f, oliveGreen = 0.50f, oliveBlue = 0.22f;

        final int bodyTop    = 24;
        final int bodyHeight = 16;
        final int centerY    = bodyTop + bodyHeight / 2;
        final int bodyLeft   = 22;
        final int bodyRight  = 74;   // body/warhead junction

        // Cylindrical olive body.
        shadedHorizontalCylinder(pixmap, bodyLeft, bodyTop, bodyRight - bodyLeft, bodyHeight,
                oliveRed, oliveGreen, oliveBlue, 1.4f, 0.55f);

        // Red warhead nose cone (points right to an apex).
        pixmap.setColor(0.62f, 0.16f, 0.12f, 1f);
        pixmap.fillTriangle(bodyRight, bodyTop, bodyRight, bodyTop + bodyHeight, 92, centerY);
        // Lit upper face of the cone.
        pixmap.setColor(0.82f, 0.28f, 0.22f, 1f);
        pixmap.fillTriangle(bodyRight, bodyTop, bodyRight, centerY, 90, centerY);
        // Yellow hazard band where the warhead meets the body.
        hazardStripeBand(pixmap, bodyRight - 8, bodyTop, 8, bodyHeight, 0.85f, 0.74f, 0.12f);

        // Tail fins — triangles above and below the rear of the body.
        pixmap.setColor(oliveRed * 0.6f, oliveGreen * 0.6f, oliveBlue * 0.6f, 1f);
        pixmap.fillTriangle(bodyLeft + 10, bodyTop, bodyLeft - 4, bodyTop - 9, bodyLeft + 2, bodyTop);
        pixmap.fillTriangle(bodyLeft + 10, bodyTop + bodyHeight, bodyLeft - 4, bodyTop + bodyHeight + 9, bodyLeft + 2, bodyTop + bodyHeight);

        // Exhaust nozzle ring at the tail.
        pixmap.setColor(0.20f, 0.20f, 0.22f, 1f);
        pixmap.fillRectangle(bodyLeft - 6, bodyTop + 2, 6, bodyHeight - 4);
        pixmap.setColor(0.08f, 0.08f, 0.09f, 1f);
        pixmap.fillRectangle(bodyLeft - 6, centerY - 2, 4, 4);
        return finalize(pixmap);
    }

    /** SLUGS — a stack of three heavy blunt silver rail rounds, side-on. */
    // Package-private (not private): reused by ItemIconTextures.
    static Texture generateRailSlugsTexture() {
        Pixmap pixmap = new Pixmap(AMMO_SPRITE_WIDTH, AMMO_SPRITE_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Silver palette.
        float silverRed = 0.80f, silverGreen = 0.84f, silverBlue = 0.90f;

        final int slugCount  = 3;
        final int slugHeight = 14;
        final int spacing    = 18;
        final int firstTop   = 8;
        final int bodyLeft   = 14;
        final int bodyRight  = 70;   // body/nose junction

        for (int slugIndex = 0; slugIndex < slugCount; slugIndex++) {
            int top     = firstTop + slugIndex * spacing;
            int centerY = top + slugHeight / 2;

            // Heavy cylindrical body.
            shadedHorizontalCylinder(pixmap, bodyLeft, top, bodyRight - bodyLeft, slugHeight,
                    silverRed, silverGreen, silverBlue, 1.2f, 0.6f);
            // Flat driving-band groove near the base.
            pixmap.setColor(silverRed * 0.55f, silverGreen * 0.55f, silverBlue * 0.6f, 1f);
            pixmap.fillRectangle(bodyLeft + 6, top, 3, slugHeight);
            // Base rim cap.
            pixmap.setColor(silverRed * 0.45f, silverGreen * 0.45f, silverBlue * 0.5f, 1f);
            pixmap.fillRectangle(bodyLeft, top, 2, slugHeight);

            // Blunt rounded nose (a short trapezoid tapering toward the right).
            pixmap.setColor(silverRed * 0.7f, silverGreen * 0.7f, silverBlue * 0.75f, 1f);
            pixmap.fillTriangle(bodyRight, top, bodyRight, centerY, 82, centerY - 3);
            pixmap.fillTriangle(bodyRight, top + slugHeight, bodyRight, centerY, 82, centerY + 3);
            pixmap.fillRectangle(bodyRight, centerY - 4, 12, 8);
            // Electric-blue rail sheen — a bright glint line along the lit top.
            pixmap.setColor(0.55f, 0.85f, 1f, 1f);
            pixmap.fillRectangle(bodyLeft + 10, top + 2, bodyRight - bodyLeft - 16, 1);
        }
        return finalize(pixmap);
    }

    // -------------------------------------------------------------------------
    // Per-weapon ground pickup sprite generators
    //
    // All sprites are 64×64 RGBA8888 with a transparent background. The layout
    // convention is: barrel on the left (small X), stock/body on the right (large X),
    // weapon centred vertically at Y≈28 (Pixmap Y=0 is top). Colours match each
    // weapon's WeaponHudRenderer palette so the floor icon and the first-person
    // sprite share the same identity.
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Credit chip ground pickup sprite generators
    //
    // Three 64×64 RGBA8888 procedural textures — SMALL (gold disc), MEDIUM (stacked
    // discs with cyan band), LARGE (emerald hexagon). Generated once at startup.
    // -------------------------------------------------------------------------

    private static boolean isCreditChip(ItemType type) {
        return type == ItemType.CREDIT_SMALL
                || type == ItemType.CREDIT_MEDIUM
                || type == ItemType.CREDIT_LARGE;
    }

    private static Map<ItemType, Texture> buildCreditChipTextures() {
        Map<ItemType, Texture> map = new HashMap<>();
        map.put(ItemType.CREDIT_SMALL,  generateCreditSmallTexture());
        map.put(ItemType.CREDIT_MEDIUM, generateCreditMediumTexture());
        map.put(ItemType.CREDIT_LARGE,  generateCreditLargeTexture());
        return map;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Credit pickup pixel art — US-dollar "cash" theme. All three tiers share one
    // green palette and a toolkit of helpers (banknote face, dollar glyph, strapped
    // bundle, silhouette outline). Authored for a 96×96 Nearest-filtered sprite.
    //
    // Tiers:  SMALL  = one loose banknote
    //         MEDIUM = one strap-banded bundle of bills
    //         LARGE  = a pyramid of three strapped bundles
    // ═══════════════════════════════════════════════════════════════════════════

    // Banknote palette (top-lit dollar green) — reused by every credit sprite.
    private static final float MONEY_OUTLINE_R = 0.03f, MONEY_OUTLINE_G = 0.09f, MONEY_OUTLINE_B = 0.05f; // silhouette ring
    private static final float MONEY_DARK_R    = 0.08f, MONEY_DARK_G    = 0.30f, MONEY_DARK_B    = 0.15f; // shaded / lower face
    private static final float MONEY_MID_R     = 0.15f, MONEY_MID_G     = 0.47f, MONEY_MID_B     = 0.23f; // main face
    private static final float MONEY_LITE_R    = 0.27f, MONEY_LITE_G    = 0.63f, MONEY_LITE_B    = 0.33f; // lit upper face
    private static final float MONEY_GLOW_R    = 0.58f, MONEY_GLOW_G    = 0.90f, MONEY_GLOW_B    = 0.58f; // top-edge glint
    private static final float MONEY_INK_R     = 0.05f, MONEY_INK_G     = 0.19f, MONEY_INK_B     = 0.10f; // engraving / frame
    private static final float MONEY_NUMERAL_R = 0.85f, MONEY_NUMERAL_G = 0.92f, MONEY_NUMERAL_B = 0.72f; // pale printed ink ($)
    private static final float STRAP_R         = 0.93f, STRAP_G         = 0.91f, STRAP_B         = 0.78f; // cream paper band
    private static final float STRAP_SHADOW_R  = 0.66f, STRAP_SHADOW_G  = 0.64f, STRAP_SHADOW_B  = 0.52f; // band underside

    // Draws a 1-pixel rectangle outline (left/right/top/bottom edges) in the current colour.
    private static void drawRectOutline(Pixmap p, int x, int y, int w, int h) {
        p.fillRectangle(x, y, w, 1);
        p.fillRectangle(x, y + h - 1, w, 1);
        p.fillRectangle(x, y, 1, h);
        p.fillRectangle(x + w - 1, y, 1, h);
    }

    // Draws a stylised "$" — an S of three bars plus a vertical pipe through the centre.
    private static void drawDollarGlyph(Pixmap p, int centerX, int centerY,
                                        int glyphWidth, int glyphHeight,
                                        float red, float green, float blue) {
        int thickness = Math.max(2, glyphHeight / 7);
        int left   = centerX - glyphWidth / 2;
        int right  = centerX + glyphWidth / 2 - 1;
        int top    = centerY - glyphHeight / 2;
        int bottom = centerY + glyphHeight / 2 - 1;
        p.setColor(red, green, blue, 1f);
        p.fillRectangle(left, top, glyphWidth, thickness);                       // top bar
        p.fillRectangle(left, top, thickness, glyphHeight / 2);                  // upper-left riser
        p.fillRectangle(left, centerY - thickness / 2, glyphWidth, thickness);   // middle bar
        p.fillRectangle(right - thickness + 1, centerY, thickness, glyphHeight / 2); // lower-right riser
        p.fillRectangle(left, bottom - thickness + 1, glyphWidth, thickness);    // bottom bar
        p.fillRectangle(centerX - thickness / 2, top - Math.max(1, thickness / 2),
                        thickness, glyphHeight + thickness);                     // central pipe
    }

    // Draws a single banknote face filling (x, y, width, height) with top-lit shading,
    // an engraved inner frame and — when large enough — a portrait medallion, a central
    // "$" and corner denomination pips.
    private static void drawBanknoteFace(Pixmap p, int x, int y, int width, int height,
                                         boolean detailed) {
        // Border
        p.setColor(MONEY_DARK_R, MONEY_DARK_G, MONEY_DARK_B, 1f);
        p.fillRectangle(x, y, width, height);
        // Vertical shading: lit top third, mid centre, shaded bottom third
        int innerX = x + 1, innerY = y + 1, innerW = width - 2, innerH = height - 2;
        int third  = Math.max(1, innerH / 3);
        p.setColor(MONEY_LITE_R, MONEY_LITE_G, MONEY_LITE_B, 1f);
        p.fillRectangle(innerX, innerY, innerW, third);
        p.setColor(MONEY_MID_R, MONEY_MID_G, MONEY_MID_B, 1f);
        p.fillRectangle(innerX, innerY + third, innerW, innerH - 2 * third);
        p.setColor(MONEY_DARK_R, MONEY_DARK_G, MONEY_DARK_B, 1f);
        p.fillRectangle(innerX, innerY + innerH - third, innerW, third);
        // Top-edge glint
        p.setColor(MONEY_GLOW_R, MONEY_GLOW_G, MONEY_GLOW_B, 1f);
        p.fillRectangle(innerX, innerY, innerW, 1);
        // Engraved inner frame
        if (width >= 14 && height >= 10) {
            p.setColor(MONEY_INK_R, MONEY_INK_G, MONEY_INK_B, 1f);
            drawRectOutline(p, x + 3, y + 3, width - 6, height - 6);
        }
        if (detailed && width >= 22 && height >= 14) {
            // Portrait medallion (left)
            int medallionX = x + width / 4;
            int medallionY = y + height / 2;
            int medallionR = Math.max(3, height / 5);
            p.setColor(MONEY_INK_R, MONEY_INK_G, MONEY_INK_B, 1f);
            p.fillCircle(medallionX, medallionY, medallionR);
            p.setColor(MONEY_LITE_R, MONEY_LITE_G, MONEY_LITE_B, 1f);
            p.fillCircle(medallionX, medallionY, Math.max(1, medallionR - 2));
            // Central dollar emblem (right of medallion)
            drawDollarGlyph(p, x + width * 3 / 5, y + height / 2,
                    Math.max(7, width / 6), Math.max(11, height * 3 / 5),
                    MONEY_NUMERAL_R, MONEY_NUMERAL_G, MONEY_NUMERAL_B);
            // Corner denomination pips
            p.setColor(MONEY_NUMERAL_R, MONEY_NUMERAL_G, MONEY_NUMERAL_B, 1f);
            p.fillRectangle(x + 3, y + 3, 3, 3);
            p.fillRectangle(x + width - 6, y + height - 6, 3, 3);
        }
    }

    // Draws a strap-banded bundle of bills filling (x, y, width, height): the lower band
    // shows layered bill edges (stack thickness), the upper region is the printed top
    // bill, and a cream paper strap wraps horizontally across the middle.
    private static void drawBundle(Pixmap p, int x, int y, int width, int height) {
        int edgeBandHeight = Math.max(6, height / 3);
        int faceHeight     = height - edgeBandHeight;
        int edgeTop        = y + faceHeight;
        // Layered bill edges — alternating shades sell the "many bills" thickness
        int layerIndex = 0;
        for (int layerY = edgeTop; layerY < y + height; layerY += 2) {
            if ((layerIndex & 1) == 0) p.setColor(MONEY_MID_R, MONEY_MID_G, MONEY_MID_B, 1f);
            else                       p.setColor(MONEY_DARK_R, MONEY_DARK_G, MONEY_DARK_B, 1f);
            p.fillRectangle(x, layerY, width, 2);
            layerIndex++;
        }
        p.setColor(MONEY_INK_R, MONEY_INK_G, MONEY_INK_B, 1f);
        drawRectOutline(p, x, edgeTop, width, edgeBandHeight);
        // Printed top bill
        drawBanknoteFace(p, x, y, width, faceHeight, true);
        // Cream paper strap, wrapping slightly past both side edges
        int strapHeight = Math.max(5, height / 5);
        int strapY      = y + (int) (height * 0.42f);
        p.setColor(STRAP_R, STRAP_G, STRAP_B, 1f);
        p.fillRectangle(x - 1, strapY, width + 2, strapHeight);
        p.setColor(1f, 1f, 0.94f, 1f);
        p.fillRectangle(x - 1, strapY, width + 2, 1);                        // sheen
        p.setColor(STRAP_SHADOW_R, STRAP_SHADOW_G, STRAP_SHADOW_B, 1f);
        p.fillRectangle(x - 1, strapY + strapHeight - 1, width + 2, 1);      // underside
        // Strap denomination mark, only when the strap is wide enough to read
        if (width >= 56) {
            drawDollarGlyph(p, x + width / 2, strapY + strapHeight / 2,
                    Math.max(5, width / 11), strapHeight + 1,
                    MONEY_INK_R, MONEY_INK_G, MONEY_INK_B);
        }
    }

    // Post-pass: grows a dark outline around every fully-opaque shape so the sprite
    // reads cleanly against the 3D scene. Reads from a snapshot each pass so a 1-pixel
    // ring is added per iteration (thickness passes → thickness-pixel border).
    private static void addSilhouetteOutline(Pixmap target, int thickness) {
        int width  = target.getWidth();
        int height = target.getHeight();
        int outline = Color.rgba8888(MONEY_OUTLINE_R, MONEY_OUTLINE_G, MONEY_OUTLINE_B, 1f);
        for (int pass = 0; pass < thickness; pass++) {
            Pixmap snapshot = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            snapshot.drawPixmap(target, 0, 0);
            for (int pixelY = 0; pixelY < height; pixelY++) {
                for (int pixelX = 0; pixelX < width; pixelX++) {
                    if ((snapshot.getPixel(pixelX, pixelY) & 0xFF) >= 250) continue; // already solid
                    if (isOpaquePixel(snapshot, pixelX - 1, pixelY)
                            || isOpaquePixel(snapshot, pixelX + 1, pixelY)
                            || isOpaquePixel(snapshot, pixelX, pixelY - 1)
                            || isOpaquePixel(snapshot, pixelX, pixelY + 1)) {
                        target.drawPixel(pixelX, pixelY, outline);
                    }
                }
            }
            snapshot.dispose();
        }
    }

    private static boolean isOpaquePixel(Pixmap source, int x, int y) {
        if (x < 0 || y < 0 || x >= source.getWidth() || y >= source.getHeight()) return false;
        return (source.getPixel(x, y) & 0xFF) >= 250;
    }

    private static Texture generateCreditSmallTexture() {
        // One loose banknote, landscape, centred in the sprite.
        int size = CREDIT_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f); p.fill();
        int billWidth  = 68;
        int billHeight = 36;
        int billX = (size - billWidth) / 2;
        int billY = (size - billHeight) / 2;
        drawBanknoteFace(p, billX, billY, billWidth, billHeight, true);
        addSilhouetteOutline(p, 2);
        return finalize(p);
    }

    private static Texture generateCreditMediumTexture() {
        // One strap-banded bundle of bills.
        int size = CREDIT_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f); p.fill();
        int bundleWidth  = 70;
        int bundleHeight = 48;
        int bundleX = (size - bundleWidth) / 2;
        int bundleY = (size - bundleHeight) / 2;
        drawBundle(p, bundleX, bundleY, bundleWidth, bundleHeight);
        addSilhouetteOutline(p, 2);
        return finalize(p);
    }

    private static Texture generateCreditLargeTexture() {
        // A pyramid of three strapped bundles — two on the base, one on top.
        int size = CREDIT_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f); p.fill();
        int bundleWidth  = 46;
        int bundleHeight = 32;
        // Base row (drawn first so the top bundle overlaps them)
        drawBundle(p,  2, 54, bundleWidth, bundleHeight); // base left
        drawBundle(p, 48, 54, bundleWidth, bundleHeight); // base right
        // Apex bundle, centred over the seam
        drawBundle(p, 25, 22, bundleWidth, bundleHeight);
        addSilhouetteOutline(p, 2);
        return finalize(p);
    }

    private static Texture generateGlowTexture() {
        int size = WEAPON_PICKUP_GLOW_TEXTURE_SIZE;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float centerX = size / 2f;
        float centerY = size / 2f;
        float radius  = size / 2f;
        for (int pixelY = 0; pixelY < size; pixelY++) {
            for (int pixelX = 0; pixelX < size; pixelX++) {
                float distX = (pixelX + 0.5f - centerX) / radius;
                float distY = (pixelY + 0.5f - centerY) / radius;
                float normalizedDistance = MathUtils.clamp(
                        (float) Math.sqrt(distX * distX + distY * distY), 0f, 1f);
                float falloff = (1f - normalizedDistance) * (1f - normalizedDistance);
                int alpha = (int)(falloff * 255f);
                pixmap.drawPixel(pixelX, pixelY, (255 << 24) | (255 << 16) | (255 << 8) | alpha);
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static Map<ItemType, Texture> buildWeaponPickupTextures() {
        Map<ItemType, Texture> map = new HashMap<>();
        map.put(ItemType.WEAPON_PISTOL,        generateWeaponPistolGroundTexture());
        map.put(ItemType.WEAPON_SHOTGUN,       generateWeaponShotgunGroundTexture());
        map.put(ItemType.WEAPON_DOUBLE_BARREL, generateWeaponDoubleBarrelGroundTexture());
        map.put(ItemType.WEAPON_CHAINGUN,      generateWeaponChaingunGroundTexture());
        map.put(ItemType.WEAPON_ASSAULT_RIFLE, generateWeaponAssaultRifleGroundTexture());
        map.put(ItemType.WEAPON_PLASMA,        generateWeaponPlasmaGroundTexture());
        map.put(ItemType.WEAPON_RAILGUN,       generateWeaponRailgunGroundTexture());
        map.put(ItemType.WEAPON_INCINERATOR,   generateWeaponIncineratorGroundTexture());
        map.put(ItemType.WEAPON_ARC_CANNON,    generateWeaponArcCannonGroundTexture());
        map.put(ItemType.WEAPON_ROCKET,        generateWeaponRocketGroundTexture());
        map.put(ItemType.WEAPON_FIST,          generateWeaponFistGroundTexture());
        map.put(ItemType.WEAPON_KNIFE,         generateWeaponKnifeGroundTexture());
        map.put(ItemType.WEAPON_HAMMER,        generateWeaponHammerGroundTexture());
        map.put(ItemType.WEAPON_CHAINSAW,      generateWeaponChainsawGroundTexture());
        return map;
    }

    /**
     * Pistol — compact 9mm sidearm.
     * Barrel: thin grey x=4..30, y=27..34. Slide: darker grey x=28..50, y=22..34.
     * Ejection port cutout on slide. Frame below slide. Trigger guard loop.
     * Grip: near-black dropping to y=58.
     */
    private static Texture generateWeaponPistolGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // BARREL
        p.setColor(0.32f, 0.32f, 0.34f, 1f); p.fillRectangle(4, 27, 26, 8);
        p.setColor(0.44f, 0.44f, 0.46f, 1f); p.fillRectangle(4, 27, 26, 1);  // top highlight
        p.setColor(0.20f, 0.20f, 0.22f, 1f); p.fillRectangle(4, 34, 26, 1);  // bottom shadow
        p.setColor(0.52f, 0.52f, 0.55f, 1f); p.fillRectangle(4, 27, 2, 8);   // muzzle face
        // SLIDE / UPPER RECEIVER
        p.setColor(0.24f, 0.24f, 0.26f, 1f); p.fillRectangle(28, 22, 22, 14);
        p.setColor(0.36f, 0.36f, 0.38f, 1f); p.fillRectangle(28, 22, 22, 1); // top highlight
        p.setColor(0.14f, 0.14f, 0.16f, 1f); p.fillRectangle(28, 35, 22, 1); // bottom shadow
        p.setColor(0.08f, 0.08f, 0.09f, 1f); p.fillRectangle(34, 24, 10, 7); // ejection port
        // FRAME / LOWER RECEIVER
        p.setColor(0.18f, 0.18f, 0.20f, 1f); p.fillRectangle(28, 36, 24, 5);
        // TRIGGER GUARD (rectangular loop)
        p.setColor(0.22f, 0.22f, 0.24f, 1f);
        p.fillRectangle(28, 41, 2, 10); // front post
        p.fillRectangle(28, 50, 14, 2); // bottom bar
        p.fillRectangle(40, 41, 2,  9); // rear post
        // GRIP
        p.setColor(0.11f, 0.11f, 0.12f, 1f); p.fillRectangle(40, 36, 12, 22);
        p.setColor(0.08f, 0.08f, 0.09f, 1f); // checkering
        p.fillRectangle(41, 41, 10, 1); p.fillRectangle(41, 46, 10, 1); p.fillRectangle(41, 51, 10, 1);
        p.setColor(0.16f, 0.16f, 0.18f, 1f); p.fillRectangle(40, 57, 12, 2); // mag base
        // Cool-white interactable shimmer
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(4, 27, 2, 2);
        return finalize(p);
    }

    /**
     * Shotgun — pump-action combat long gun, side profile (muzzle left, stock right).
     *
     * Identity (distinct from the twin-tube double-barrel): a SINGLE barrel fitted with a
     * ventilated heat-shield (a row of dark cooling slots over the barrel), a wide ribbed
     * pump fore-grip below the barrel, a steel receiver with an ejection port, and a warm
     * wood butt-stock with a pistol grip and trigger guard.
     *
     * Horizontal bands (64-wide canvas, x grows left→right):
     *   Barrel + heat-shield  x= 2..44   muzzle at far left, shield slots on top
     *   Pump fore-grip        x=18..36   ribbed slide hanging below the barrel
     *   Receiver              x=42..58   ejection port cut into the right flank
     *   Wood stock            x=56..62   butt-stock at far right
     *   Pistol grip + guard   x=46..60   drops below the receiver
     */
    private static Texture generateWeaponShotgunGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // BARREL (single long tube)
        p.setColor(0.27f, 0.28f, 0.31f, 1f); p.fillRectangle(2, 27, 42, 8);
        p.setColor(0.42f, 0.44f, 0.48f, 1f); p.fillRectangle(2, 27, 42, 1); // crown highlight
        p.setColor(0.14f, 0.15f, 0.18f, 1f); p.fillRectangle(2, 34, 42, 1); // bottom shadow
        p.setColor(0.50f, 0.52f, 0.56f, 1f); p.fillRectangle(2, 27, 2, 8);  // muzzle rim cap
        // VENTILATED HEAT-SHIELD (cooling slots over the barrel — the pump-gun identity)
        p.setColor(0.30f, 0.32f, 0.36f, 1f); p.fillRectangle(8, 24, 34, 4); // shield band
        p.setColor(0.44f, 0.46f, 0.50f, 1f); p.fillRectangle(8, 24, 34, 1); // shield top highlight
        p.setColor(0.05f, 0.05f, 0.06f, 1f);                                // cooling slots
        p.fillRectangle(11, 25, 3, 2); p.fillRectangle(17, 25, 3, 2);
        p.fillRectangle(23, 25, 3, 2); p.fillRectangle(29, 25, 3, 2);
        p.fillRectangle(35, 25, 3, 2);
        // FRONT BEAD SIGHT (raised post near the muzzle)
        p.setColor(0.62f, 0.64f, 0.70f, 1f); p.fillRectangle(6, 22, 2, 3);
        // PUMP FORE-GRIP (ribbed slide hanging below the barrel)
        p.setColor(0.24f, 0.25f, 0.28f, 1f); p.fillRectangle(18, 35, 18, 9);
        p.setColor(0.36f, 0.38f, 0.42f, 1f); p.fillRectangle(18, 35, 18, 1); // top highlight
        p.setColor(0.12f, 0.13f, 0.15f, 1f); p.fillRectangle(18, 43, 18, 1); // bottom shadow
        p.setColor(0.13f, 0.14f, 0.16f, 1f);                                 // grip ribs
        p.fillRectangle(20, 37, 1, 6); p.fillRectangle(24, 37, 1, 6);
        p.fillRectangle(28, 37, 1, 6); p.fillRectangle(32, 37, 1, 6);
        // RECEIVER
        p.setColor(0.22f, 0.23f, 0.26f, 1f); p.fillRectangle(42, 24, 16, 18);
        p.setColor(0.34f, 0.36f, 0.40f, 1f); p.fillRectangle(42, 24, 16, 1); // top highlight
        p.setColor(0.11f, 0.12f, 0.14f, 1f); p.fillRectangle(42, 41, 16, 1); // bottom shadow
        // EJECTION PORT (dark recessed slot on the receiver flank)
        p.setColor(0.06f, 0.07f, 0.09f, 1f); p.fillRectangle(45, 29, 10, 6);
        p.setColor(0.40f, 0.42f, 0.48f, 1f); p.fillRectangle(45, 34, 10, 1); // port lower lip shine
        // WOOD BUTT-STOCK (warm brown, far right)
        p.setColor(0.44f, 0.28f, 0.12f, 1f); p.fillRectangle(56, 22, 6, 20);
        p.setColor(0.56f, 0.38f, 0.18f, 1f); p.fillRectangle(56, 22, 6, 1);  // top highlight
        p.setColor(0.34f, 0.20f, 0.08f, 1f);                                 // wood grain
        p.fillRectangle(57, 26, 4, 1); p.fillRectangle(57, 30, 4, 1);
        p.fillRectangle(57, 34, 4, 1); p.fillRectangle(57, 38, 4, 1);
        // PISTOL GRIP (drops below receiver)
        p.setColor(0.16f, 0.17f, 0.19f, 1f); p.fillRectangle(48, 42, 12, 16);
        p.setColor(0.11f, 0.12f, 0.14f, 1f); p.fillRectangle(48, 56, 12, 2); // grip butt shadow
        p.setColor(0.10f, 0.11f, 0.13f, 1f);                                 // grip checkering
        p.fillRectangle(50, 46, 8, 1); p.fillRectangle(50, 50, 8, 1);
        // TRIGGER GUARD (loop under the receiver)
        p.setColor(0.20f, 0.21f, 0.24f, 1f);
        p.fillRectangle(44, 42, 2, 9); p.fillRectangle(44, 50, 12, 2); p.fillRectangle(54, 42, 2, 8);
        // Cool-white interactable shimmer (muzzle highlight)
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(2, 27, 2, 2);
        return finalize(p);
    }

    /**
     * Double-Barrel Shotgun — break-action.
     * Two parallel barrel tubes separated by dark gap (the identity). Break-action hinge.
     * Short dark-brown stock. Pistol grip drops below receiver.
     */
    private static Texture generateWeaponDoubleBarrelGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // UPPER BARREL TUBE
        p.setColor(0.30f, 0.30f, 0.32f, 1f); p.fillRectangle(4, 18, 42, 9);
        p.setColor(0.42f, 0.42f, 0.44f, 1f); p.fillRectangle(4, 18, 42, 1); // top highlight
        p.setColor(0.48f, 0.48f, 0.50f, 1f); p.fillRectangle(4, 18, 2, 9);  // upper muzzle face
        // GAP BETWEEN BARRELS
        p.setColor(0.05f, 0.05f, 0.06f, 1f); p.fillRectangle(4, 27, 42, 5);
        // LOWER BARREL TUBE
        p.setColor(0.24f, 0.24f, 0.26f, 1f); p.fillRectangle(4, 32, 42, 9);
        p.setColor(0.14f, 0.14f, 0.16f, 1f); p.fillRectangle(4, 40, 42, 1); // bottom shadow
        p.setColor(0.40f, 0.40f, 0.42f, 1f); p.fillRectangle(4, 32, 2, 9);  // lower muzzle face
        // RECEIVER BLOCK
        p.setColor(0.20f, 0.20f, 0.22f, 1f); p.fillRectangle(42, 16, 16, 27);
        p.setColor(0.30f, 0.30f, 0.32f, 1f); p.fillRectangle(42, 16, 16, 1); // top highlight
        p.setColor(0.12f, 0.12f, 0.14f, 1f); p.fillRectangle(42, 42, 16, 1); // bottom shadow
        // BREAK-ACTION HINGE PIN (bright silver)
        p.setColor(0.60f, 0.62f, 0.66f, 1f); p.fillRectangle(40, 25, 6, 9);
        // WALNUT STOCK
        p.setColor(0.32f, 0.20f, 0.10f, 1f); p.fillRectangle(54, 16, 8, 28);
        p.setColor(0.42f, 0.28f, 0.14f, 1f); p.fillRectangle(54, 16, 8, 1); // top highlight
        p.setColor(0.26f, 0.16f, 0.07f, 1f); // wood grain
        p.fillRectangle(55, 21, 6, 1); p.fillRectangle(55, 27, 6, 1);
        p.fillRectangle(55, 33, 6, 1); p.fillRectangle(55, 39, 6, 1);
        // PISTOL GRIP
        p.setColor(0.26f, 0.16f, 0.08f, 1f); p.fillRectangle(48, 43, 10, 16);
        p.setColor(0.18f, 0.10f, 0.04f, 1f); p.fillRectangle(48, 57, 10, 2); // bottom shadow
        // TRIGGER GUARD
        p.setColor(0.18f, 0.18f, 0.20f, 1f);
        p.fillRectangle(42, 43, 2, 10); p.fillRectangle(42, 52, 12, 2); p.fillRectangle(52, 43, 2, 9);
        // Cool-white shimmer
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(4, 18, 2, 2);
        return finalize(p);
    }

    /**
     * Chaingun — rotary cannon.
     * Four stacked silver barrel tubes with dark gaps + vertical muzzle plate + charcoal body.
     * Olive ammo-feed nub top-right. Pistol grip drops below body.
     */
    private static Texture generateWeaponChaingunGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // ROTARY MOTOR HOUSING (center)
        p.setColor(0.18f, 0.19f, 0.22f, 1f); p.fillRectangle(32, 14, 14, 34);
        p.setColor(0.26f, 0.28f, 0.32f, 1f); p.fillRectangle(32, 14, 14, 1); // top highlight
        // BARREL TUBE 1 (top): brightest silver
        p.setColor(0.70f, 0.72f, 0.78f, 1f); p.fillRectangle(4, 16, 32, 7);
        p.setColor(0.82f, 0.84f, 0.90f, 1f); p.fillRectangle(4, 16, 32, 1);  // top highlight
        p.setColor(0.54f, 0.56f, 0.62f, 1f); p.fillRectangle(4, 22, 32, 1);  // bottom shadow
        // GAP 1
        p.setColor(0.08f, 0.08f, 0.09f, 1f); p.fillRectangle(4, 23, 32, 2);
        // BARREL TUBE 2: silver
        p.setColor(0.66f, 0.68f, 0.74f, 1f); p.fillRectangle(4, 25, 32, 7);
        p.setColor(0.78f, 0.80f, 0.86f, 1f); p.fillRectangle(4, 25, 32, 1);
        p.setColor(0.52f, 0.54f, 0.60f, 1f); p.fillRectangle(4, 31, 32, 1);
        // GAP 2
        p.setColor(0.08f, 0.08f, 0.09f, 1f); p.fillRectangle(4, 32, 32, 2);
        // BARREL TUBE 3: silver
        p.setColor(0.64f, 0.66f, 0.72f, 1f); p.fillRectangle(4, 34, 32, 7);
        p.setColor(0.76f, 0.78f, 0.84f, 1f); p.fillRectangle(4, 34, 32, 1);
        p.setColor(0.50f, 0.52f, 0.58f, 1f); p.fillRectangle(4, 40, 32, 1);
        // GAP 3
        p.setColor(0.08f, 0.08f, 0.09f, 1f); p.fillRectangle(4, 41, 32, 2);
        // BARREL TUBE 4 (bottom): slightly darker
        p.setColor(0.62f, 0.64f, 0.70f, 1f); p.fillRectangle(4, 43, 32, 7);
        p.setColor(0.50f, 0.52f, 0.58f, 1f); p.fillRectangle(4, 49, 32, 1); // bottom shadow
        // MUZZLE PLATE (vertical plate linking all four barrel ends)
        p.setColor(0.74f, 0.76f, 0.82f, 1f); p.fillRectangle(2, 14, 4, 38);
        p.setColor(0.86f, 0.88f, 0.94f, 1f); p.fillRectangle(2, 14, 1, 38); // face highlight
        // MAIN BODY / RECEIVER
        p.setColor(0.16f, 0.17f, 0.19f, 1f); p.fillRectangle(44, 14, 18, 34);
        p.setColor(0.24f, 0.26f, 0.30f, 1f); p.fillRectangle(44, 14, 18, 1); // top highlight
        p.setColor(0.10f, 0.11f, 0.13f, 1f); p.fillRectangle(44, 47, 18, 1); // bottom shadow
        // AMMO-FEED NUB (olive)
        p.setColor(0.38f, 0.40f, 0.20f, 1f); p.fillRectangle(52, 14, 10, 8);
        p.setColor(0.48f, 0.52f, 0.26f, 1f); p.fillRectangle(52, 14, 10, 1); // top highlight
        // PISTOL GRIP
        p.setColor(0.12f, 0.12f, 0.14f, 1f); p.fillRectangle(50, 48, 12, 14);
        p.setColor(0.09f, 0.09f, 0.10f, 1f); // checkering
        p.fillRectangle(51, 52, 10, 1); p.fillRectangle(51, 57, 10, 1);
        // Cool-white shimmer
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(2, 16, 2, 2);
        return finalize(p);
    }

    /**
     * Assault Rifle — gunmetal service rifle.
     * Barrel extends LEFT from a center-right gunmetal body. Slotted handguard over the barrel.
     * SIGNATURE feature = a CURVED MAGAZINE dropping below the body (banana mag). Charcoal pistol
     * grip and a U-shape trigger guard below the receiver.
     */
    private static Texture generateWeaponAssaultRifleGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // BARREL (thin gunmetal tube extending left)
        p.setColor(0.30f, 0.32f, 0.36f, 1f); p.fillRectangle(4, 28, 22, 6);
        p.setColor(0.42f, 0.44f, 0.48f, 1f); p.fillRectangle(4, 28, 22, 1);  // top highlight
        p.setColor(0.22f, 0.24f, 0.28f, 1f); p.fillRectangle(4, 33, 22, 1);  // bottom shadow
        p.setColor(0.50f, 0.54f, 0.60f, 1f); p.fillRectangle(4, 28, 2, 6);   // muzzle face (catches light)
        // SLOTTED HANDGUARD (over the barrel base — cooling slots)
        p.setColor(0.27f, 0.29f, 0.34f, 1f); p.fillRectangle(18, 26, 14, 10);
        p.setColor(0.40f, 0.42f, 0.48f, 1f); p.fillRectangle(18, 26, 14, 1);  // top highlight
        p.setColor(0.07f, 0.08f, 0.10f, 1f);                                  // cooling slots
        p.fillRectangle(20, 29, 2, 4); p.fillRectangle(24, 29, 2, 4); p.fillRectangle(28, 29, 2, 4);
        // MAIN BODY / RECEIVER (center-right gunmetal)
        p.setColor(0.30f, 0.32f, 0.36f, 1f); p.fillRectangle(30, 24, 28, 16);
        p.setColor(0.42f, 0.44f, 0.48f, 1f); p.fillRectangle(30, 24, 28, 1);  // top highlight
        p.setColor(0.22f, 0.24f, 0.28f, 1f); p.fillRectangle(30, 39, 28, 1);  // bottom shadow
        // EJECTION PORT (dark cutout on top-right of receiver)
        p.setColor(0.08f, 0.09f, 0.11f, 1f); p.fillRectangle(44, 26, 10, 5);
        p.setColor(0.40f, 0.44f, 0.50f, 1f); p.fillRectangle(44, 30, 10, 1);  // port lower lip shine
        // CARRY-HANDLE REAR SIGHT (small housing on top of receiver)
        p.setColor(0.16f, 0.18f, 0.22f, 1f); p.fillRectangle(34, 20, 12, 5);
        p.setColor(0.34f, 0.38f, 0.44f, 1f); p.fillRectangle(34, 20, 12, 1);  // housing highlight
        // SIGNATURE: CURVED MAGAZINE (banana mag dropping/curving below the body)
        p.setColor(0.20f, 0.21f, 0.24f, 1f);                                  // dark charcoal mag
        p.fillRectangle(34, 40, 12, 5);   // mag top (seats into receiver)
        p.fillRectangle(31, 44, 12, 5);   // curve segment 1
        p.fillRectangle(28, 48, 12, 6);   // curve segment 2 (sweeps left + down)
        p.setColor(0.30f, 0.32f, 0.36f, 1f);                                  // front-edge highlight
        p.fillRectangle(44, 40, 2, 5); p.fillRectangle(41, 44, 2, 5); p.fillRectangle(38, 48, 2, 6);
        p.setColor(0.12f, 0.13f, 0.15f, 1f);                                  // back-edge shadow
        p.fillRectangle(34, 40, 1, 5); p.fillRectangle(31, 44, 1, 5); p.fillRectangle(28, 48, 1, 6);
        // PISTOL GRIP (charcoal, drops below the receiver on the right)
        p.setColor(0.14f, 0.15f, 0.18f, 1f); p.fillRectangle(50, 40, 10, 16);
        p.setColor(0.10f, 0.11f, 0.13f, 1f);                                  // checkering
        p.fillRectangle(51, 45, 8, 1); p.fillRectangle(51, 50, 8, 1);
        // TRIGGER GUARD (U-shape below the receiver, ahead of the grip)
        p.setColor(0.24f, 0.26f, 0.30f, 1f);
        p.fillRectangle(46, 40, 2, 8);    // front post
        p.fillRectangle(46, 47, 6, 2);    // bottom bar
        p.fillRectangle(50, 40, 2, 8);    // rear post
        // Cool-white shimmer on the muzzle face
        p.setColor(0.90f, 0.94f, 1.00f, 1f); p.fillRectangle(4, 28, 2, 2);
        return finalize(p);
    }

    /**
     * Plasma Rifle — energy weapon.
     * BRIGHT CYAN layered emitter at muzzle (identity). Dark blue-grey body with 3 cyan coil lines.
     * Green targeting scope on upper receiver. Pistol grip drops below body.
     */
    private static Texture generateWeaponPlasmaGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // BODY (dark blue-grey)
        p.setColor(0.14f, 0.18f, 0.26f, 1f); p.fillRectangle(20, 20, 40, 22);
        p.setColor(0.22f, 0.28f, 0.38f, 1f); p.fillRectangle(20, 20, 40, 1); // top highlight
        p.setColor(0.10f, 0.13f, 0.19f, 1f); p.fillRectangle(20, 41, 40, 1); // bottom shadow
        // ENERGY COILS (3 bright cyan lines across body)
        p.setColor(0.00f, 0.84f, 0.96f, 1f);
        p.fillRectangle(20, 26, 40, 2); p.fillRectangle(20, 31, 40, 2); p.fillRectangle(20, 36, 40, 2);
        // TARGETING SCOPE (small housing with green lens)
        p.setColor(0.10f, 0.13f, 0.20f, 1f); p.fillRectangle(34, 17, 16, 5);
        p.setColor(0.10f, 0.75f, 0.50f, 0.90f); p.fillRectangle(36, 18, 12, 3);
        // EMITTER HEAD (layered CYAN — outermost housing -> mid glow -> hot core -> near-white spot)
        p.setColor(0.10f, 0.70f, 0.82f, 1f); p.fillRectangle(4, 17, 20, 28); // housing
        p.setColor(0.20f, 0.86f, 0.96f, 1f); p.fillRectangle(6, 19, 16, 24); // mid glow
        p.setColor(0.50f, 0.96f, 1.00f, 1f); p.fillRectangle(8, 22, 12, 18); // bright core
        p.setColor(0.90f, 0.99f, 1.00f, 1f); p.fillRectangle(11, 27, 6, 8);  // near-white hot spot
        p.setColor(0.30f, 0.92f, 1.00f, 1f); // emitter top + left face highlights
        p.fillRectangle(4, 17, 20, 1); p.fillRectangle(4, 17, 1, 28);
        // LEFT FLANK PANEL
        p.setColor(0.10f, 0.14f, 0.22f, 1f); p.fillRectangle(20, 20, 4, 22);
        // PISTOL GRIP
        p.setColor(0.10f, 0.13f, 0.20f, 1f); p.fillRectangle(50, 42, 12, 20);
        p.setColor(0.07f, 0.09f, 0.14f, 1f); // checkering
        p.fillRectangle(51, 47, 10, 1); p.fillRectangle(51, 52, 10, 1); p.fillRectangle(51, 57, 10, 1);
        // TRIGGER GUARD
        p.setColor(0.14f, 0.18f, 0.26f, 1f);
        p.fillRectangle(44, 42, 2, 10); p.fillRectangle(44, 51, 14, 2); p.fillRectangle(56, 42, 2, 9);
        // Cool-white shimmer (also reads as plasma glow)
        p.setColor(0.92f, 0.98f, 1.00f, 1f); p.fillRectangle(4, 17, 2, 2);
        return finalize(p);
    }

    /**
     * Arc Cannon — chain-lightning energy weapon.
     * Steel-blue body threaded with cyan coils; twin electrode PRONGS at the muzzle with a bright
     * cyan Tesla emitter arcing between them (identity). Pistol grip below.
     */
    private static Texture generateWeaponArcCannonGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // BODY (steel blue-grey)
        p.setColor(0.16f, 0.20f, 0.28f, 1f); p.fillRectangle(20, 21, 38, 22);
        p.setColor(0.28f, 0.34f, 0.44f, 1f); p.fillRectangle(20, 21, 38, 1); // top highlight
        p.setColor(0.11f, 0.14f, 0.20f, 1f); p.fillRectangle(20, 42, 38, 1); // bottom shadow
        // ENERGY COILS (3 bright cyan lines across body)
        p.setColor(0.00f, 0.88f, 1.00f, 1f);
        p.fillRectangle(22, 26, 34, 2); p.fillRectangle(22, 31, 34, 2); p.fillRectangle(22, 36, 34, 2);
        // CAPACITOR POD (flank)
        p.setColor(0.12f, 0.16f, 0.24f, 1f); p.fillRectangle(50, 20, 8, 24);
        p.setColor(0.30f, 0.82f, 1.00f, 1f); p.fillRectangle(52, 23, 4, 18);
        // EMITTER BARREL (short steel muzzle)
        p.setColor(0.24f, 0.28f, 0.38f, 1f); p.fillRectangle(10, 25, 12, 14);
        p.setColor(0.40f, 0.46f, 0.58f, 1f); p.fillRectangle(10, 25, 12, 1); // barrel top highlight
        // ELECTRODE PRONGS (twin cyan-tipped flanges)
        p.setColor(0.22f, 0.26f, 0.34f, 1f); p.fillRectangle(4, 22, 4, 6); p.fillRectangle(4, 36, 4, 6);
        p.setColor(0.40f, 0.90f, 1.00f, 1f); p.fillRectangle(4, 22, 3, 2); p.fillRectangle(4, 40, 3, 2);
        // TESLA EMITTER CORE (layered cyan arcing between the prongs)
        p.setColor(0.08f, 0.52f, 1.00f, 1f); p.fillRectangle(5, 28, 8, 8);
        p.setColor(0.50f, 0.96f, 1.00f, 1f); p.fillRectangle(7, 30, 4, 4);
        p.setColor(1.00f, 1.00f, 1.00f, 1f); p.fillRectangle(8, 31, 2, 2); // hot white centre
        // PISTOL GRIP
        p.setColor(0.11f, 0.14f, 0.20f, 1f); p.fillRectangle(48, 43, 12, 19);
        p.setColor(0.08f, 0.10f, 0.15f, 1f); // checkering
        p.fillRectangle(49, 48, 10, 1); p.fillRectangle(49, 53, 10, 1); p.fillRectangle(49, 58, 10, 1);
        return finalize(p);
    }

    /**
     * Railgun — magnetic accelerator.
     * Two ELECTRIC BLUE rails spanning full body length (identity). Three coil accelerators
     * with white arc sparks between rails. Metallic grey body. Pistol grip.
     */
    private static Texture generateWeaponRailgunGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // BODY (metallic grey, long)
        p.setColor(0.34f, 0.36f, 0.40f, 1f); p.fillRectangle(4, 22, 56, 20);
        p.setColor(0.46f, 0.48f, 0.54f, 1f); p.fillRectangle(4, 22, 56, 1); // top highlight
        p.setColor(0.22f, 0.24f, 0.28f, 1f); p.fillRectangle(4, 41, 56, 1); // bottom shadow
        // RAIL 1 (upper ELECTRIC BLUE)
        p.setColor(0.20f, 0.50f, 1.00f, 1f); p.fillRectangle(4, 24, 56, 3);
        p.setColor(0.40f, 0.70f, 1.00f, 1f); p.fillRectangle(4, 24, 56, 1); // rail top glint
        // RAIL 2 (lower ELECTRIC BLUE)
        p.setColor(0.20f, 0.50f, 1.00f, 1f); p.fillRectangle(4, 37, 56, 3);
        p.setColor(0.14f, 0.36f, 0.80f, 1f); p.fillRectangle(4, 39, 56, 1); // rail bottom shadow
        // COIL ACCELERATORS (3 dark blocks between rails)
        p.setColor(0.18f, 0.20f, 0.24f, 1f);
        p.fillRectangle(10, 27, 10, 10); p.fillRectangle(27, 27, 10, 10); p.fillRectangle(44, 27, 10, 10);
        // ARC SPARKS between rails at each coil (white)
        p.setColor(0.90f, 0.96f, 1.00f, 1f);
        p.fillRectangle(14, 27, 2, 10); p.fillRectangle(31, 27, 2, 10); p.fillRectangle(48, 27, 2, 10);
        // MUZZLE CAP
        p.setColor(0.50f, 0.52f, 0.58f, 1f); p.fillRectangle(2, 22, 3, 20);
        p.setColor(0.62f, 0.64f, 0.70f, 1f); p.fillRectangle(2, 22, 1, 20); // face highlight
        // PISTOL GRIP
        p.setColor(0.20f, 0.22f, 0.26f, 1f); p.fillRectangle(52, 42, 12, 18);
        p.setColor(0.14f, 0.16f, 0.20f, 1f); p.fillRectangle(52, 58, 12, 2); // bottom shadow
        p.setColor(0.15f, 0.17f, 0.20f, 1f); // checkering
        p.fillRectangle(53, 47, 10, 1); p.fillRectangle(53, 52, 10, 1);
        // TRIGGER GUARD
        p.setColor(0.28f, 0.30f, 0.34f, 1f);
        p.fillRectangle(46, 42, 2, 10); p.fillRectangle(46, 51, 14, 2); p.fillRectangle(58, 42, 2, 9);
        // Cool-white shimmer
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(2, 22, 2, 2);
        return finalize(p);
    }

    /**
     * Incinerator — short-range flamethrower.
     *
     * Refined to match the reworked first-person sprite: a wide nozzle assembly with a
     * layered ORANGE muzzle (dark outer housing → mid orange → bright core) and a small
     * pilot flame lick at the tip (the signature "always live" read), a dark gunmetal body
     * tube with a venting heat slat, an off-axis RUST-RED fuel tank behind/below the body
     * carrying a yellow/black hazard band, two steel retaining hoops and a fuel gauge, plus
     * the conventional dark grip and trigger guard. Palette mirrors WeaponHudRenderer:
     * body 0.20,0.22,0.26 / orange 0.95,0.46,0.10 / tank 0.55,0.20,0.12 / hazard 0.88,0.72,0.10.
     */
    private static Texture generateWeaponIncineratorGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();

        // BODY TUBE — dark gunmetal, center-right, with crown highlight and base shadow.
        p.setColor(0.20f, 0.22f, 0.26f, 1f); p.fillRectangle(22, 23, 38, 16);
        p.setColor(0.34f, 0.37f, 0.41f, 1f); p.fillRectangle(22, 23, 38, 2);  // top crown highlight
        p.setColor(0.11f, 0.12f, 0.14f, 1f); p.fillRectangle(22, 37, 38, 2);  // bottom shadow
        // Heat-vent slats on the body top (industrial read, mirrors FP shoulder vents).
        p.setColor(0.09f, 0.10f, 0.11f, 1f);
        p.fillRectangle(38, 26, 16, 1); p.fillRectangle(38, 29, 16, 1); p.fillRectangle(38, 32, 16, 1);
        p.setColor(0.70f, 0.32f, 0.06f, 1f); // faint heat glow leaking between slats
        p.fillRectangle(38, 27, 16, 1); p.fillRectangle(38, 30, 16, 1);

        // NOZZLE ASSEMBLY (layered ORANGE — dark outer housing → mid orange → bright core).
        p.setColor(0.80f, 0.35f, 0.06f, 1f); p.fillRectangle(6, 18, 22, 28);  // outer housing
        p.setColor(0.95f, 0.46f, 0.10f, 1f); p.fillRectangle(8, 20, 18, 24);  // mid orange
        p.setColor(1.00f, 0.60f, 0.20f, 1f); p.fillRectangle(10, 23, 14, 18); // bright core
        p.setColor(1.00f, 0.70f, 0.30f, 1f); p.fillRectangle(6, 18, 22, 2);   // top highlight
        // Steel retaining hoops banding the nozzle (two vertical bright bands).
        p.setColor(0.42f, 0.45f, 0.50f, 1f);
        p.fillRectangle(15, 18, 2, 28); p.fillRectangle(22, 18, 2, 28);
        // Igniter collar at the muzzle face (bright steel rim where the pilot light sits).
        p.setColor(0.50f, 0.53f, 0.58f, 1f); p.fillRectangle(4, 22, 3, 20);
        p.setColor(0.62f, 0.65f, 0.70f, 1f); p.fillRectangle(4, 22, 1, 20);  // face highlight

        // PILOT FLAME lick at the nozzle tip — layered orange→yellow→white (always live).
        p.setColor(0.96f, 0.22f, 0.05f, 1f); p.fillRectangle(1, 26, 4, 12);  // red outer
        p.setColor(1.00f, 0.55f, 0.12f, 1f); p.fillRectangle(1, 28, 3, 8);   // orange mid
        p.setColor(1.00f, 0.85f, 0.30f, 1f); p.fillRectangle(1, 30, 2, 4);   // yellow-white core

        // FUEL TANK — rust-red, off-axis behind/below the body, with cylindrical shading.
        p.setColor(0.55f, 0.20f, 0.12f, 1f); p.fillRectangle(26, 39, 28, 17);
        p.setColor(0.72f, 0.30f, 0.18f, 1f); p.fillRectangle(26, 39, 28, 2);  // top sheen
        p.setColor(0.34f, 0.11f, 0.06f, 1f); p.fillRectangle(26, 54, 28, 2);  // bottom shadow
        p.setColor(0.34f, 0.11f, 0.06f, 1f); p.fillRectangle(26, 39, 2, 17);  // left-edge shadow
        // Hazard band — yellow base with black diagonal ticks.
        p.setColor(0.88f, 0.72f, 0.10f, 1f); p.fillRectangle(28, 45, 24, 5);
        p.setColor(0.10f, 0.09f, 0.07f, 1f);
        p.fillRectangle(30, 45, 2, 5); p.fillRectangle(36, 45, 2, 5);
        p.fillRectangle(42, 45, 2, 5); p.fillRectangle(48, 45, 2, 5);
        // Steel retaining hoop + fuel gauge window on the tank.
        p.setColor(0.40f, 0.42f, 0.46f, 1f); p.fillRectangle(28, 51, 24, 1);  // hoop
        p.setColor(0.12f, 0.05f, 0.03f, 1f); p.fillRectangle(30, 40, 8, 3);   // gauge recess
        p.setColor(0.95f, 0.42f, 0.12f, 1f); p.fillRectangle(30, 40, 4, 3);   // gauge fill (half)

        // GRIP — dark charcoal, below the body on the right.
        p.setColor(0.16f, 0.17f, 0.20f, 1f); p.fillRectangle(54, 39, 10, 18);
        p.setColor(0.10f, 0.11f, 0.13f, 1f); // checkering grooves
        p.fillRectangle(55, 44, 8, 1); p.fillRectangle(55, 49, 8, 1); p.fillRectangle(55, 54, 8, 1);
        // TRIGGER GUARD.
        p.setColor(0.24f, 0.26f, 0.30f, 1f);
        p.fillRectangle(48, 39, 2, 10); p.fillRectangle(48, 48, 12, 2); p.fillRectangle(58, 39, 2, 9);

        // Cool-white shimmer at the muzzle face.
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(4, 22, 2, 2);
        return finalize(p);
    }

    /**
     * Grenade Launcher — single-shot tube launcher.
     * FAT OLIVE tube barrel — widest bore of any weapon (identity). Large dark bore circle
     * at muzzle. Grey receiver with loading port. Dark olive pistol grip.
     */
    private static Texture generateWeaponRocketGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // LAUNCH TUBE (FAT OLIVE — the defining wide barrel)
        p.setColor(0.28f, 0.34f, 0.14f, 1f); p.fillRectangle(4, 14, 42, 32);
        p.setColor(0.38f, 0.46f, 0.20f, 1f); p.fillRectangle(4, 14, 42, 1);  // top highlight
        p.setColor(0.18f, 0.22f, 0.08f, 1f); p.fillRectangle(4, 45, 42, 1);  // bottom shadow
        // BORE OPENING (large dark circles — shows the big calibre)
        p.setColor(0.04f, 0.04f, 0.05f, 1f); p.fillCircle(14, 30, 10); // outer bore rim
        p.setColor(0.01f, 0.01f, 0.02f, 1f); p.fillCircle(14, 30,  7); // inner bore
        // RECEIVER BLOCK
        p.setColor(0.28f, 0.30f, 0.34f, 1f); p.fillRectangle(42, 19, 18, 24);
        p.setColor(0.38f, 0.40f, 0.46f, 1f); p.fillRectangle(42, 19, 18, 1); // top highlight
        p.setColor(0.18f, 0.20f, 0.24f, 1f); p.fillRectangle(42, 42, 18, 1); // bottom shadow
        p.setColor(0.14f, 0.15f, 0.17f, 1f); p.fillRectangle(44, 22, 10, 10);// loading port
        // PISTOL GRIP (dark olive)
        p.setColor(0.18f, 0.22f, 0.10f, 1f); p.fillRectangle(50, 43, 10, 18);
        p.setColor(0.13f, 0.16f, 0.07f, 1f); // checkering
        p.fillRectangle(51, 48, 8, 1); p.fillRectangle(51, 53, 8, 1); p.fillRectangle(51, 58, 8, 1);
        // TRIGGER GUARD
        p.setColor(0.24f, 0.26f, 0.30f, 1f);
        p.fillRectangle(42, 43, 2, 10); p.fillRectangle(42, 52, 14, 2); p.fillRectangle(54, 43, 2, 9);
        // Cool-white shimmer
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(4, 14, 2, 2);
        return finalize(p);
    }

    /**
     * Fist — two armoured gauntlets side-by-side.
     * Left gauntlet: x=8..26, y=20..44. Right gauntlet: x=30..48, y=20..44.
     * Knuckle highlights on far edge (top in Pixmap = near edge of each block).
     */
    private static Texture generateWeaponFistGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // LEFT GAUNTLET body
        p.setColor(0.30f, 0.32f, 0.36f, 1f); p.fillRectangle(8, 20, 18, 24);
        p.setColor(0.44f, 0.46f, 0.52f, 1f); p.fillRectangle(8, 20, 18, 2);  // far-edge highlight
        p.setColor(0.18f, 0.20f, 0.22f, 1f); p.fillRectangle(8, 42, 18, 2);  // near-edge shadow
        // Left knuckle ridge bumps
        p.setColor(0.38f, 0.40f, 0.44f, 1f);
        p.fillRectangle(9,  22, 3, 2); p.fillRectangle(13, 22, 3, 2);
        p.fillRectangle(17, 22, 3, 2); p.fillRectangle(21, 22, 3, 2);
        // RIGHT GAUNTLET body (mirror)
        p.setColor(0.30f, 0.32f, 0.36f, 1f); p.fillRectangle(30, 20, 18, 24);
        p.setColor(0.44f, 0.46f, 0.52f, 1f); p.fillRectangle(30, 20, 18, 2);
        p.setColor(0.18f, 0.20f, 0.22f, 1f); p.fillRectangle(30, 42, 18, 2);
        p.setColor(0.38f, 0.40f, 0.44f, 1f);
        p.fillRectangle(31, 22, 3, 2); p.fillRectangle(35, 22, 3, 2);
        p.fillRectangle(39, 22, 3, 2); p.fillRectangle(43, 22, 3, 2);
        // GAP between fists — near-black
        p.setColor(0.06f, 0.06f, 0.08f, 1f); p.fillRectangle(26, 20, 4, 24);
        // Cool-white shimmer
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(8, 20, 2, 2);
        return finalize(p);
    }

    /**
     * Combat Knife — horizontal blade pointing left, handle right.
     * Blade: x=4..38, y=26..32. Guard: x=36..40, y=22..38. Handle wrap: x=40..58, y=25..33.
     */
    private static Texture generateWeaponKnifeGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // BLADE
        p.setColor(0.70f, 0.72f, 0.78f, 1f); p.fillRectangle(4, 26, 34, 8);  // silver steel
        p.setColor(0.86f, 0.88f, 0.94f, 1f); p.fillRectangle(4, 26, 34, 1);  // top edge highlight
        p.setColor(0.48f, 0.50f, 0.56f, 1f); p.fillRectangle(4, 33, 34, 1);  // bottom edge shadow
        // Blade taper — blade narrows toward tip at far left
        p.setColor(0f, 0f, 0f, 0f); p.fillRectangle(4, 26, 1, 1); // clip corner
        p.setColor(0f, 0f, 0f, 0f); p.fillRectangle(4, 33, 1, 1);
        // Tip shimmer
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(4, 28, 2, 2);
        // CROSS GUARD
        p.setColor(0.24f, 0.26f, 0.30f, 1f); p.fillRectangle(36, 22, 4, 18);
        p.setColor(0.36f, 0.38f, 0.44f, 1f); p.fillRectangle(36, 22, 4, 1);
        p.setColor(0.14f, 0.16f, 0.18f, 1f); p.fillRectangle(36, 39, 4, 1);
        // HANDLE WRAP (dark leather)
        p.setColor(0.18f, 0.12f, 0.08f, 1f); p.fillRectangle(40, 25, 18, 10);
        p.setColor(0.12f, 0.08f, 0.05f, 1f); // wrap grooves
        p.fillRectangle(44, 25, 1, 10); p.fillRectangle(48, 25, 1, 10); p.fillRectangle(52, 25, 1, 10);
        // POMMEL
        p.setColor(0.34f, 0.36f, 0.40f, 1f); p.fillRectangle(57, 26, 5, 8);
        p.setColor(0.46f, 0.48f, 0.54f, 1f); p.fillRectangle(57, 26, 5, 1);
        return finalize(p);
    }

    /**
     * Hammer — head at left, wooden handle at right.
     * Head block: x=4..20, y=16..46. Handle: x=20..56, y=28..36.
     * Striking faces on left and right edges of head; wooden shaft extends right.
     */
    private static Texture generateWeaponHammerGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // HANDLE — mahogany wood
        p.setColor(0.40f, 0.22f, 0.08f, 1f); p.fillRectangle(20, 28, 36, 8);
        p.setColor(0.52f, 0.30f, 0.12f, 1f); p.fillRectangle(20, 28, 36, 1);  // top highlight
        p.setColor(0.26f, 0.13f, 0.04f, 1f); p.fillRectangle(20, 35, 36, 1);  // bottom shadow
        p.setColor(0.28f, 0.14f, 0.05f, 1f); // grain lines
        p.fillRectangle(28, 29, 1, 6); p.fillRectangle(38, 29, 1, 6); p.fillRectangle(48, 29, 1, 6);
        // HAMMER HEAD body
        p.setColor(0.30f, 0.32f, 0.36f, 1f); p.fillRectangle(6, 16, 14, 30);
        p.setColor(0.42f, 0.44f, 0.50f, 1f); p.fillRectangle(6, 16, 14, 2);   // far-edge highlight
        p.setColor(0.18f, 0.20f, 0.22f, 1f); p.fillRectangle(6, 44, 14, 2);   // near-edge shadow
        // Centre groove on head top face
        p.setColor(0.22f, 0.24f, 0.28f, 1f); p.fillRectangle(7, 31, 12, 1);
        // STRIKING FACES — bright steel on left and right edges of head
        p.setColor(0.58f, 0.60f, 0.66f, 1f);
        p.fillRectangle(4,  16, 3, 30);   // left striking face
        p.fillRectangle(19, 16, 3, 30);   // right (handle-side) face
        // Cool-white shimmer on striking face tip
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(4, 16, 2, 2);
        return finalize(p);
    }

    /**
     * Chainsaw — guide bar at left, motor body at right.
     * Guide bar: x=4..34, y=27..37. Motor: x=32..58, y=17..47.
     * Chain teeth as small marks on top/bottom edges of guide bar. Orange hazard stripe on motor.
     */
    private static Texture generateWeaponChainsawGroundTexture() {
        int S = WEAPON_PICKUP_TEXTURE_SIZE;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0f, 0f, 0f, 0f);
        p.fill();
        // MOTOR BODY
        p.setColor(0.28f, 0.30f, 0.34f, 1f); p.fillRectangle(32, 17, 26, 30);
        p.setColor(0.40f, 0.42f, 0.48f, 1f); p.fillRectangle(32, 17, 26, 2);  // top highlight
        p.setColor(0.16f, 0.18f, 0.20f, 1f); p.fillRectangle(32, 45, 26, 2);  // bottom shadow
        // Orange hazard stripe on motor
        p.setColor(0.88f, 0.52f, 0.04f, 1f); p.fillRectangle(34, 20, 22, 5);
        p.setColor(0.60f, 0.34f, 0.02f, 1f); p.fillRectangle(34, 20, 22, 1);  // stripe shadow
        // Air-slot vents on motor body
        p.setColor(0.14f, 0.15f, 0.17f, 1f);
        p.fillRectangle(34, 28, 22, 2); p.fillRectangle(34, 33, 22, 2); p.fillRectangle(34, 38, 22, 2);
        // GUIDE BAR
        p.setColor(0.34f, 0.36f, 0.40f, 1f); p.fillRectangle(4, 27, 30, 10);
        p.setColor(0.46f, 0.48f, 0.54f, 1f); p.fillRectangle(4, 27, 30, 1);   // top highlight
        p.setColor(0.20f, 0.22f, 0.26f, 1f); p.fillRectangle(4, 36, 30, 1);   // bottom shadow
        // Chain teeth — 1×2 dark marks along top and bottom of guide bar
        p.setColor(0.14f, 0.15f, 0.17f, 1f);
        for (int toothX = 6; toothX < 32; toothX += 5) {
            p.fillRectangle(toothX, 27, 2, 2);  // top chain
            p.fillRectangle(toothX, 35, 2, 2);  // bottom chain
        }
        // Guide bar nose cap (tip at far left)
        p.setColor(0.52f, 0.54f, 0.60f, 1f); p.fillRectangle(4, 27, 2, 10);
        // Cool-white shimmer on nose tip
        p.setColor(0.92f, 0.96f, 1.00f, 1f); p.fillRectangle(4, 27, 2, 2);
        return finalize(p);
    }

}

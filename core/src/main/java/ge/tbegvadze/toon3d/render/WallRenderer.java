package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.Random;

import static ge.tbegvadze.toon3d.util.Constants.*;

/**
 * Renders the Doom-style 3D wall projection for one frame.
 *
 * Pipeline per frame (single SpriteBatch, two GPU draw calls):
 *   1. Ceiling and floor — two rects using a 1×1 white pixel texture tinted to the correct colour.
 *   2. Wall columns — one draw per screen column, sampling the wall texture at the computed
 *      texture column (wallHitPosition → texColumn). The texture switch from the white pixel to
 *      the wall texture triggers the only automatic batch flush; all 1280 wall draws then drain
 *      into one GPU call at batch.end().
 *
 * Texture column derivation (see GameMath for full proofs):
 *   wallHitPosition  — fractional position [0,1) along the struck wall face
 *   wallHitMirrored  — corrected for direction so the texture reads consistently from all sides
 *   texColumn        — (int)(wallHitMirrored × textureWidth), clamped to [0, width−1]
 *
 * Camera-plane sign convention (Y-up world):
 *   planeX = −dirY × scale,  planeY = dirX × scale      (CCW-rotated direction)
 *   cameraParameter = 1 − 2 × column / screenWidth       (INVERTED vs Lodev Y-down)
 *   See GameMath.cameraPlaneX/Y and GameMath.cameraPlaneParameter for derivations.
 */
public class WallRenderer implements Renderable, Disposable {

    private final Level level;
    private final DoorManager doorManager;
    private final SpriteBatch batch;
    private final Texture wallTexturePlain;
    private final Texture wallTextureConduit;
    private final Texture wallTextureVent;
    private final Texture wallTextureTerminal;
    private final Texture wallTextureWires;
    private final Texture wallTextureHazard;
    private final Texture wallTextureRust;
    private final Texture wallTextureGore;
    private final Texture wallTextureBulkhead;
    private final Texture doorTexture;
    private final Texture doorTextureRed;
    private final Texture doorTextureYellow;
    private final Texture doorTextureBlue;
    private final Texture columnTexture;
    private final Texture whitePixelTexture;
    // Perpendicular wall distance per screen column — used for sprite depth-clipping later.
    private final float[] zBuffer;

    // Cached at load time to avoid calling texture.getWidth()/getHeight() inside render().
    private final int wallTexturePlainWidth;
    private final int wallTexturePlainHeight;
    private final int wallTextureConduitWidth;
    private final int wallTextureConduitHeight;
    private final int wallTextureVentWidth;
    private final int wallTextureVentHeight;
    private final int wallTextureTerminalWidth;
    private final int wallTextureTerminalHeight;
    private final int wallTextureWiresWidth;
    private final int wallTextureWiresHeight;
    private final int wallTextureHazardWidth;
    private final int wallTextureHazardHeight;
    private final int wallTextureRustWidth;
    private final int wallTextureRustHeight;
    private final int wallTextureGoreWidth;
    private final int wallTextureGoreHeight;
    private final int wallTextureBulkheadWidth;
    private final int wallTextureBulkheadHeight;
    private final int doorTextureWidth;
    private final int doorTextureHeight;
    private final int columnTextureWidth;
    private final int columnTextureHeight;

    private float playerWorldX        = 0f;
    private float playerWorldY        = 0f;
    private float directionX          = 1f;
    private float directionY          = 0f;
    // Camera-plane vectors cached in setPlayerState() to avoid Math.tan() inside render().
    private float cachedPlaneX        = 0f;
    private float cachedPlaneY        = CAMERA_PLANE_SCALE;
    // Current alert pulse intensity [0, 1]; 0 = normal lighting, 1 = full red wash.
    private float alertPulse          = 0f;
    // Monotonically increasing facility clock used to evaluate 'f' (flickering) tile brightness.
    private float lightingTimeSeconds = 0f;

    public WallRenderer(Level level, DoorManager doorManager) {
        this.level       = level;
        this.doorManager = doorManager;
        // Each column can produce up to 2 draws (background surface + door panel overlay).
        this.batch   = new SpriteBatch(2 * WALL_PROJECTION_SCREEN_WIDTH + 2);
        this.zBuffer = new float[WALL_PROJECTION_SCREEN_WIDTH];

        wallTexturePlain    = loadWallTexture(LAB_WALL_PLAIN_PATH);
        wallTextureConduit  = loadWallTexture(LAB_WALL_CONDUIT_PATH);
        wallTextureVent     = loadWallTexture(LAB_WALL_VENT_PATH);
        wallTextureTerminal = loadWallTexture(LAB_WALL_TERMINAL_PATH);
        wallTextureWires    = loadWallTexture(LAB_WALL_WIRES_PATH);
        wallTextureHazard   = Gdx.files.internal(LAB_WALL_HAZARD_PATH).exists()
                              ? loadWallTexture(LAB_WALL_HAZARD_PATH)
                              : generateHazardWallTexture();

        wallTexturePlainWidth    = wallTexturePlain.getWidth();
        wallTexturePlainHeight   = wallTexturePlain.getHeight();
        wallTextureConduitWidth  = wallTextureConduit.getWidth();
        wallTextureConduitHeight = wallTextureConduit.getHeight();
        wallTextureVentWidth     = wallTextureVent.getWidth();
        wallTextureVentHeight    = wallTextureVent.getHeight();
        wallTextureTerminalWidth  = wallTextureTerminal.getWidth();
        wallTextureTerminalHeight = wallTextureTerminal.getHeight();
        wallTextureWiresWidth    = wallTextureWires.getWidth();
        wallTextureWiresHeight   = wallTextureWires.getHeight();
        wallTextureHazardWidth   = wallTextureHazard.getWidth();
        wallTextureHazardHeight  = wallTextureHazard.getHeight();

        wallTextureRust     = generateRustWallTexture();
        wallTextureGore     = generateGoreWallTexture();
        wallTextureBulkhead = generateBulkheadWallTexture();

        wallTextureRustWidth      = wallTextureRust.getWidth();
        wallTextureRustHeight     = wallTextureRust.getHeight();
        wallTextureGoreWidth      = wallTextureGore.getWidth();
        wallTextureGoreHeight     = wallTextureGore.getHeight();
        wallTextureBulkheadWidth  = wallTextureBulkhead.getWidth();
        wallTextureBulkheadHeight = wallTextureBulkhead.getHeight();

        doorTexture      = loadOrGenerateDoorTexture(LAB_DOOR_CLOSED_PATH, 0f, 0f, 0f);
        doorTextureWidth  = doorTexture.getWidth();
        doorTextureHeight = doorTexture.getHeight();

        doorTextureRed    = loadOrGenerateDoorTexture(LAB_DOOR_RED_PATH,    0.55f, 0f,    0f);
        doorTextureYellow = loadOrGenerateDoorTexture(LAB_DOOR_YELLOW_PATH, 0.50f, 0.40f, 0f);
        doorTextureBlue   = loadOrGenerateDoorTexture(LAB_DOOR_BLUE_PATH,   0f,    0.20f, 0.55f);

        columnTexture      = generateColumnTexture();
        columnTextureWidth  = columnTexture.getWidth();
        columnTextureHeight = columnTexture.getHeight();

        Pixmap whitePixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        whitePixel.setColor(Color.WHITE);
        whitePixel.fill();
        whitePixelTexture = new Texture(whitePixel);
        whitePixel.dispose();
    }

    private static Texture loadWallTexture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    /**
     * Tries to load a door texture from disk; generates a procedural placeholder if absent.
     * accentRed/Green/Blue is the stripe color added to the procedural fallback (0 = plain door).
     */
    private static Texture loadOrGenerateDoorTexture(String path,
                                                     float accentRed,
                                                     float accentGreen,
                                                     float accentBlue) {
        if (Gdx.files.internal(path).exists()) {
            return loadWallTexture(path);
        }
        return generateProceduralDoorTexture(accentRed, accentGreen, accentBlue);
    }

    /**
     * Generates a procedural door texture with an optional color-accent stripe.
     * Plain door: pass (0, 0, 0). Keycard doors pass the tier color.
     */
    private static Texture generateProceduralDoorTexture(float accentRed,
                                                         float accentGreen,
                                                         float accentBlue) {
        int textureSize = 128;
        Pixmap pixmap = new Pixmap(textureSize, textureSize, Pixmap.Format.RGBA8888);
        for (int row = 0; row < textureSize; row++) {
            for (int column = 0; column < textureSize; column++) {
                boolean isBorder       = row < 4 || row >= textureSize - 4
                                      || column < 4 || column >= textureSize - 4;
                boolean isPanelDivider = row == textureSize / 3 || row == textureSize / 3 + 1
                                      || row == 2 * textureSize / 3 || row == 2 * textureSize / 3 + 1;
                boolean isHandle       = column >= textureSize / 2 - 5 && column <= textureSize / 2 + 5
                                      && row >= textureSize / 2 - 10 && row <= textureSize / 2 + 10;
                // Vertical accent stripe: left 8 pixels carry the tier color
                boolean isAccentStripe = column < 8;
                float red; float green; float blue;
                if (isAccentStripe) {
                    red   = 0.18f + accentRed;
                    green = 0.18f + accentGreen;
                    blue  = 0.22f + accentBlue;
                } else if (isBorder || isPanelDivider) {
                    red = 0x4C / 255f; green = 0x4C / 255f; blue = 0x55 / 255f;
                } else if (isHandle) {
                    red = 0x55 / 255f; green = 0x55 / 255f; blue = 0x60 / 255f;
                } else {
                    red = 0x2E / 255f; green = 0x2E / 255f; blue = 0x36 / 255f;
                }
                pixmap.setColor(Math.min(1f, red), Math.min(1f, green), Math.min(1f, blue), 1f);
                pixmap.drawPixel(column, row);
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    private static Texture generateColumnTexture() {
        int textureWidth  = 64;
        int textureHeight = 128;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        // Stone base: medium gray with a subtle pseudo-random variation per pixel.
        for (int row = 0; row < textureHeight; row++) {
            for (int column = 0; column < textureWidth; column++) {
                float variation = ((row * 7 + column * 11) % 13 - 6) / 90f;
                float gray = 0.50f + variation;
                pixmap.setColor(gray, gray, gray + 0.02f, 1f);
                pixmap.drawPixel(column, row);
            }
        }
        // Horizontal mortar joints every 20 rows (stone course lines).
        pixmap.setColor(0.28f, 0.28f, 0.30f, 1f);
        for (int row = 20; row < textureHeight; row += 20) {
            pixmap.fillRectangle(0, row, textureWidth, 2);
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a yellow/black diagonal hazard stripe wall texture.
     * The pattern emulates industrial caution tape: alternating 45° bands inside a
     * dark-steel border frame with corner rivets. No asset file required.
     */
    private static Texture generateHazardWallTexture() {
        int textureSize = 128;
        Pixmap pixmap = new Pixmap(textureSize, textureSize, Pixmap.Format.RGBA8888);

        int bandWidth = 22; // pixels per yellow+black diagonal band pair

        for (int row = 0; row < textureSize; row++) {
            for (int column = 0; column < textureSize; column++) {
                int diagonalPosition = ((row + column) % bandWidth + bandWidth) % bandWidth;
                boolean isYellow = diagonalPosition < (bandWidth / 2);
                float noise = ((row * 7 + column * 11) % 13 - 6) / 120f;

                if (isYellow) {
                    float red   = Math.min(1f, 0.93f + noise * 0.04f);
                    float green = Math.min(1f, 0.79f + noise * 0.07f);
                    float blue  = Math.max(0f, 0.04f + noise * 0.02f);
                    pixmap.setColor(red, green, blue, 1f);
                } else {
                    float gray = Math.max(0f, 0.09f + noise * 0.03f);
                    pixmap.setColor(gray, gray, gray + 0.02f, 1f);
                }
                pixmap.drawPixel(column, row);
            }
        }

        // Dark-steel border frame (4 px each side)
        pixmap.setColor(0x33 / 255f, 0x33 / 255f, 0x3A / 255f, 1f);
        pixmap.fillRectangle(0, 0, textureSize, 4);
        pixmap.fillRectangle(0, textureSize - 4, textureSize, 4);
        pixmap.fillRectangle(0, 0, 4, textureSize);
        pixmap.fillRectangle(textureSize - 4, 0, 4, textureSize);

        // Corner rivets
        pixmap.setColor(0x58 / 255f, 0x58 / 255f, 0x64 / 255f, 1f);
        int rivetSize   = 5;
        int rivetOffset = 7;
        pixmap.fillRectangle(rivetOffset, rivetOffset, rivetSize, rivetSize);
        pixmap.fillRectangle(textureSize - rivetOffset - rivetSize, rivetOffset, rivetSize, rivetSize);
        pixmap.fillRectangle(rivetOffset, textureSize - rivetOffset - rivetSize, rivetSize, rivetSize);
        pixmap.fillRectangle(textureSize - rivetOffset - rivetSize,
                             textureSize - rivetOffset - rivetSize, rivetSize, rivetSize);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a corroded steel wall texture.
     * Steel base overpainted with organic rust blotches (maroon → orange → ochre),
     * heaviest at the bottom (water wicks up). Panel seams at 1/3 and 2/3 height,
     * corroded rivet heads with drip streaks, and black perforation pits.
     */
    private static Texture generateRustWallTexture() {
        int    size   = RUST_WALL_TEXTURE_SIZE;
        Random random = new Random(RUST_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        int[] blobCenterX = new int[RUST_BLOB_COUNT];
        int[] blobCenterY = new int[RUST_BLOB_COUNT];
        int[] blobRadius  = new int[RUST_BLOB_COUNT];
        int[] blobTone    = new int[RUST_BLOB_COUNT]; // 0=deep, 1=mid, 2=pale
        for (int blobIndex = 0; blobIndex < RUST_BLOB_COUNT; blobIndex++) {
            blobCenterX[blobIndex] = random.nextInt(size);
            blobCenterY[blobIndex] = random.nextInt(size);
            blobRadius[blobIndex]  = 8 + random.nextInt(21);
            blobTone[blobIndex]    = random.nextInt(3);
        }

        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float bottomFactor = (float) row / (size - 1);
                float grain = ((row * 7 + column * 11) % 13 - 6) / 100f;

                float totalRustWeight = 0f;
                float blendRed = 0f, blendGreen = 0f, blendBlue = 0f;
                for (int blobIndex = 0; blobIndex < RUST_BLOB_COUNT; blobIndex++) {
                    float differenceX = column - blobCenterX[blobIndex];
                    float differenceY = row    - blobCenterY[blobIndex];
                    float distance    = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
                    float weight      = GameMath.radialFalloff(distance, blobRadius[blobIndex]);
                    if (weight > 0f) {
                        float toneRed, toneGreen, toneBlue;
                        if (blobTone[blobIndex] == 0) {
                            toneRed = 0.34f; toneGreen = 0.16f; toneBlue = 0.09f; // deep maroon
                        } else if (blobTone[blobIndex] == 1) {
                            toneRed = 0.55f; toneGreen = 0.27f; toneBlue = 0.10f; // rust orange
                        } else {
                            toneRed = 0.62f; toneGreen = 0.45f; toneBlue = 0.22f; // pale ochre
                        }
                        blendRed   += weight * toneRed;
                        blendGreen += weight * toneGreen;
                        blendBlue  += weight * toneBlue;
                        totalRustWeight += weight;
                    }
                }
                float rustAmount = Math.min(1f, totalRustWeight) * bottomFactor;
                if (totalRustWeight > 0f) {
                    blendRed   /= totalRustWeight;
                    blendGreen /= totalRustWeight;
                    blendBlue  /= totalRustWeight;
                }

                float steelRed   = 0.30f + grain;
                float steelGreen = 0.31f + grain;
                float steelBlue  = 0.34f + grain;
                float red   = steelRed   + rustAmount * (blendRed   - steelRed);
                float green = steelGreen + rustAmount * (blendGreen - steelGreen);
                float blue  = steelBlue  + rustAmount * (blendBlue  - steelBlue);
                pixmap.setColor(Math.max(0f, Math.min(1f, red)),
                                Math.max(0f, Math.min(1f, green)),
                                Math.max(0f, Math.min(1f, blue)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Panel seams at 1/3 and 2/3 height
        pixmap.setColor(0.14f, 0.13f, 0.13f, 1f);
        pixmap.fillRectangle(0, size / 3, size, 2);
        pixmap.fillRectangle(0, 2 * size / 3, size, 2);

        // Corroded rivets and drip streaks at each seam
        int[] seamRows = { size / 3, 2 * size / 3 };
        for (int seamRow : seamRows) {
            for (int rivetX = size / 4; rivetX < size; rivetX += size / 4) {
                pixmap.setColor(0.20f, 0.14f, 0.10f, 1f);
                pixmap.fillRectangle(rivetX - 1, seamRow - 1, 3, 3);
                int dripLength = 8 + random.nextInt(16);
                for (int dripRow = seamRow + 3; dripRow < Math.min(size, seamRow + 3 + dripLength); dripRow++) {
                    float dripFade = 1f - (float)(dripRow - seamRow - 3) / dripLength;
                    float dripDark = 0.26f * dripFade;
                    pixmap.setColor(Math.max(0f, dripDark),
                                    Math.max(0f, dripDark * 0.5f),
                                    Math.max(0f, dripDark * 0.27f), 1f);
                    pixmap.drawPixel(rivetX, dripRow);
                }
            }
        }

        // Perforation pits in the heavier bottom rust zone
        for (int pitIndex = 0; pitIndex < RUST_PIT_COUNT; pitIndex++) {
            int pitColumn = random.nextInt(size);
            int pitRow    = size / 2 + random.nextInt(size / 2);
            pixmap.setColor(0.05f, 0.04f, 0.04f, 1f);
            pixmap.fillRectangle(pitColumn, pitRow, 2, 2);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a demonic flesh-infestation wall texture.
     * Grey steel base overgrown with corner-anchored fleshy blobs (dark meat → bright flesh),
     * sinew veins via random walks, wet specular glints, pale bone nodules, and radiating cracks.
     */
    private static Texture generateGoreWallTexture() {
        int    size   = GORE_WALL_TEXTURE_SIZE;
        Random random = new Random(GORE_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        int[] blobCenterX = new int[GORE_BLOB_COUNT];
        int[] blobCenterY = new int[GORE_BLOB_COUNT];
        int[] blobRadius  = new int[GORE_BLOB_COUNT];
        for (int blobIndex = 0; blobIndex < GORE_BLOB_COUNT; blobIndex++) {
            blobCenterX[blobIndex] = (int)(random.nextFloat() * size * 0.70f);
            blobCenterY[blobIndex] = (int)(random.nextFloat() * size * 0.70f);
            blobRadius[blobIndex]  = 10 + random.nextInt(21);
        }

        float[] fleshWeightMap = new float[size * size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float totalWeight = 0f;
                for (int blobIndex = 0; blobIndex < GORE_BLOB_COUNT; blobIndex++) {
                    float differenceX = column - blobCenterX[blobIndex];
                    float differenceY = row    - blobCenterY[blobIndex];
                    float distance    = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
                    totalWeight += GameMath.radialFalloff(distance, blobRadius[blobIndex]);
                }
                fleshWeightMap[row * size + column] = Math.min(1f, totalWeight);
            }
        }

        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float fleshWeight = fleshWeightMap[row * size + column];
                float grain = ((row * 7 + column * 11) % 13 - 6) / 120f;
                float red, green, blue;
                if (fleshWeight > GORE_FLESH_THRESHOLD) {
                    float interpolationFactor = (fleshWeight - GORE_FLESH_THRESHOLD) / (1f - GORE_FLESH_THRESHOLD);
                    red   = Math.max(0f, Math.min(1f, 0.20f + interpolationFactor * 0.25f + grain));
                    green = Math.max(0f, Math.min(1f, 0.04f + interpolationFactor * 0.04f));
                    blue  = Math.max(0f, Math.min(1f, 0.05f + interpolationFactor * 0.04f));
                } else {
                    red   = Math.max(0f, Math.min(1f, 0.26f + grain));
                    green = Math.max(0f, Math.min(1f, 0.27f + grain));
                    blue  = Math.max(0f, Math.min(1f, 0.29f + grain));
                }
                pixmap.setColor(red, green, blue, 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Veins — random walks through flesh region
        for (int veinIndex = 0; veinIndex < GORE_VEIN_COUNT; veinIndex++) {
            int   veinColumn = random.nextInt(size / 2);
            int   veinRow    = random.nextInt(size / 2);
            float veinAngle  = random.nextFloat() * (float)(Math.PI * 2);
            for (int step = 0; step < 30; step++) {
                if (veinColumn < 0 || veinColumn >= size || veinRow < 0 || veinRow >= size) break;
                if (fleshWeightMap[veinRow * size + veinColumn] > 0.40f) {
                    pixmap.setColor(0.72f, 0.12f, 0.14f, 1f);
                    pixmap.drawPixel(veinColumn, veinRow);
                }
                veinAngle  += (random.nextFloat() - 0.5f) * 0.8f;
                veinColumn += (int) Math.round(Math.cos(veinAngle));
                veinRow    += (int) Math.round(Math.sin(veinAngle));
            }
        }

        // Wet specular glints
        for (int glintIndex = 0; glintIndex < GORE_GLINT_COUNT; glintIndex++) {
            int glintColumn = random.nextInt(size);
            int glintRow    = random.nextInt(size);
            if (fleshWeightMap[glintRow * size + glintColumn] > 0.50f) {
                pixmap.setColor(0.95f, 0.55f, 0.55f, 1f);
                pixmap.drawPixel(glintColumn, glintRow);
            }
        }

        // Bone nodules pushing through meat
        for (int boneIndex = 0; boneIndex < GORE_BONE_COUNT; boneIndex++) {
            int boneColumn = random.nextInt(size / 2);
            int boneRow    = random.nextInt(size / 2);
            if (fleshWeightMap[boneRow * size + boneColumn] > 0.60f) {
                pixmap.setColor(0.20f, 0.04f, 0.05f, 1f);
                pixmap.fillRectangle(boneColumn - 2, boneRow - 2, 5, 5);
                pixmap.setColor(0.80f, 0.76f, 0.66f, 1f);
                pixmap.fillRectangle(boneColumn - 1, boneRow - 1, 3, 3);
            }
        }

        // Cracks radiating from top-left corner across the steel/flesh boundary
        for (int crackIndex = 0; crackIndex < 3; crackIndex++) {
            int   crackColumn = crackIndex * 5;
            int   crackRow    = crackIndex * 4;
            float crackAngle  = (float)(Math.PI / 4) + (random.nextFloat() - 0.5f) * 0.6f;
            for (int step = 0; step < 40; step++) {
                if (crackColumn < 0 || crackColumn >= size || crackRow < 0 || crackRow >= size) break;
                pixmap.setColor(0.03f, 0.02f, 0.02f, 1f);
                pixmap.drawPixel(crackColumn, crackRow);
                crackAngle  += (random.nextFloat() - 0.5f) * 0.4f;
                crackColumn += (int) Math.round(Math.cos(crackAngle));
                crackRow    += (int) Math.round(Math.sin(crackAngle));
            }
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a heavy armored bulkhead plate texture.
     * Gunmetal blue-grey field with a beveled recessed center panel, a dense bolt grid
     * around the inner frame, a hydraulic door seam splitting the panel, and a faded
     * stencil chevron hull-section marker.
     */
    private static Texture generateBulkheadWallTexture() {
        int    size   = BULKHEAD_WALL_TEXTURE_SIZE;
        int    border = BULKHEAD_FRAME_WIDTH;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Gunmetal base with fine grain
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float grain = ((row * 7 + column * 11) % 13 - 6) / 150f;
                pixmap.setColor(Math.max(0f, Math.min(1f, 0.30f + grain)),
                                Math.max(0f, Math.min(1f, 0.33f + grain)),
                                Math.max(0f, Math.min(1f, 0.39f + grain)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Recessed center panel (darker)
        pixmap.setColor(0.24f, 0.27f, 0.33f, 1f);
        pixmap.fillRectangle(border, border, size - 2 * border, size - 2 * border);

        // Bevel: top-left lit edge (raised lip), bottom-right shadow edge
        pixmap.setColor(0.46f, 0.50f, 0.57f, 1f);
        pixmap.fillRectangle(border - 2, border - 2, size - 2 * border + 4, 2); // top highlight
        pixmap.fillRectangle(border - 2, border - 2, 2, size - 2 * border + 4); // left highlight
        pixmap.setColor(0.13f, 0.15f, 0.19f, 1f);
        pixmap.fillRectangle(border - 2, size - border, size - 2 * border + 4, 2); // bottom shadow
        pixmap.fillRectangle(size - border, border - 2, 2, size - 2 * border + 4); // right shadow

        // Bolt grid: shadow offset then lit head
        int boltSpacing = BULKHEAD_BOLT_SPACING;
        // Top and bottom rows
        for (int boltX = border + boltSpacing / 2; boltX < size - border; boltX += boltSpacing) {
            pixmap.setColor(0.08f, 0.09f, 0.11f, 1f);
            pixmap.fillRectangle(boltX + 1, border + 2, 3, 3);
            pixmap.fillRectangle(boltX + 1, size - border - 4, 3, 3);
            pixmap.setColor(0.58f, 0.62f, 0.68f, 1f);
            pixmap.fillRectangle(boltX, border + 1, 3, 3);
            pixmap.fillRectangle(boltX, size - border - 5, 3, 3);
        }
        // Left and right columns
        for (int boltY = border + boltSpacing / 2; boltY < size - border; boltY += boltSpacing) {
            pixmap.setColor(0.08f, 0.09f, 0.11f, 1f);
            pixmap.fillRectangle(border + 2, boltY + 1, 3, 3);
            pixmap.fillRectangle(size - border - 4, boltY + 1, 3, 3);
            pixmap.setColor(0.58f, 0.62f, 0.68f, 1f);
            pixmap.fillRectangle(border + 1, boltY, 3, 3);
            pixmap.fillRectangle(size - border - 5, boltY, 3, 3);
        }

        // Hydraulic seam at center of panel
        pixmap.setColor(0.06f, 0.07f, 0.09f, 1f);
        pixmap.fillRectangle(border, size / 2, size - 2 * border, 2);
        pixmap.setColor(0.46f, 0.50f, 0.57f, 1f);
        pixmap.fillRectangle(border, size / 2 - 1, size - 2 * border, 1); // highlight above seam

        // Faded stencil chevron in top-right of center panel
        int stencilX = size - border - 18;
        int stencilY = border + 6;
        // Blend stencil color (70% yellow-gold, 30% panel dark)
        pixmap.setColor(0.70f * 0.70f + 0.24f * 0.30f, 0.66f * 0.70f + 0.27f * 0.30f,
                        0.30f * 0.70f + 0.33f * 0.30f, 1f);
        for (int chevronRow = 0; chevronRow < 7; chevronRow++) {
            int indent = chevronRow <= 3 ? chevronRow : 6 - chevronRow;
            pixmap.drawPixel(stencilX + indent, stencilY + chevronRow);
            if (indent > 0) pixmap.drawPixel(stencilX + indent - 1, stencilY + chevronRow);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    private Texture selectDoorTexture(char cell) {
        switch (cell) {
            case 'R': return doorTextureRed;
            case 'Y': return doorTextureYellow;
            case 'B': return doorTextureBlue;
            default:  return doorTexture;
        }
    }

    private Texture selectWallTexture(char cell) {
        switch (cell) {
            case 'c': return wallTextureConduit;
            case 'v': return wallTextureVent;
            case 't': return wallTextureTerminal;
            case 'w': return wallTextureWires;
            case 'h': return wallTextureHazard;
            case 'r': return wallTextureRust;
            case 'G': return wallTextureGore;
            case 'k': return wallTextureBulkhead;
            default:  return wallTexturePlain;
        }
    }

    private int selectWallTextureWidth(char cell) {
        switch (cell) {
            case 'c': return wallTextureConduitWidth;
            case 'v': return wallTextureVentWidth;
            case 't': return wallTextureTerminalWidth;
            case 'w': return wallTextureWiresWidth;
            case 'h': return wallTextureHazardWidth;
            case 'r': return wallTextureRustWidth;
            case 'G': return wallTextureGoreWidth;
            case 'k': return wallTextureBulkheadWidth;
            default:  return wallTexturePlainWidth;
        }
    }

    private int selectWallTextureHeight(char cell) {
        switch (cell) {
            case 'c': return wallTextureConduitHeight;
            case 'v': return wallTextureVentHeight;
            case 't': return wallTextureTerminalHeight;
            case 'w': return wallTextureWiresHeight;
            case 'h': return wallTextureHazardHeight;
            case 'r': return wallTextureRustHeight;
            case 'G': return wallTextureGoreHeight;
            case 'k': return wallTextureBulkheadHeight;
            default:  return wallTexturePlainHeight;
        }
    }

    public void setPlayerState(float worldX, float worldY,
                               float playerDirectionX, float playerDirectionY,
                               float playerFieldOfViewRadians) {
        this.playerWorldX = worldX;
        this.playerWorldY = worldY;
        this.directionX   = playerDirectionX;
        this.directionY   = playerDirectionY;
        float planeScale  = (float) Math.tan(playerFieldOfViewRadians / 2.0);
        this.cachedPlaneX = GameMath.cameraPlaneX(playerDirectionY, planeScale);
        this.cachedPlaneY = GameMath.cameraPlaneY(playerDirectionX, planeScale);
    }

    public void setAlertPulse(float pulse) {
        this.alertPulse = pulse;
    }

    public void setLightingTime(float timeSeconds) {
        this.lightingTimeSeconds = timeSeconds;
    }

    /** Returns the perpendicular wall distance (in tile units) for a given screen column. */
    public float getZBufferAt(int screenColumn) {
        return zBuffer[MathUtils.clamp(screenColumn, 0, WALL_PROJECTION_SCREEN_WIDTH - 1)];
    }

    @Override
    public void render(OrthographicCamera camera) {
        batch.setProjectionMatrix(camera.combined);

        float playerTileX = playerWorldX / CELL_SIZE;
        float playerTileY = playerWorldY / CELL_SIZE;

        float planeX = cachedPlaneX;
        float planeY = cachedPlaneY;

        batch.begin();

        for (int screenColumn = 0; screenColumn < WALL_PROJECTION_SCREEN_WIDTH; screenColumn++) {
            float cameraParameter = GameMath.cameraPlaneParameter(screenColumn, WALL_PROJECTION_SCREEN_WIDTH);
            float rayDirectionX   = GameMath.cameraPlaneRayDirectionX(directionX, planeX, cameraParameter);
            float rayDirectionY   = GameMath.cameraPlaneRayDirectionY(directionY, planeY, cameraParameter);

            float deltaDistanceX = GameMath.ddaDeltaDistanceX(rayDirectionX);
            float deltaDistanceY = GameMath.ddaDeltaDistanceY(rayDirectionY);

            int tileColumn = MathUtils.floor(playerTileX);
            int tileRow    = MathUtils.floor(playerTileY);

            float sideDistanceX = GameMath.ddaInitialSideDistanceX(playerTileX, tileColumn, rayDirectionX, deltaDistanceX);
            float sideDistanceY = GameMath.ddaInitialSideDistanceY(playerTileY, tileRow,    rayDirectionY, deltaDistanceY);

            int stepColumn = (rayDirectionX < 0f) ? -1 : 1;
            int stepRow    = (rayDirectionY < 0f) ? -1 : 1;

            boolean hitWall             = false;
            boolean crossedVerticalLine = false;
            float   perpWallDistance    = RAY_MAX_LENGTH_CELLS;
            char    hitWallCell         = 'x';
            // When the ray passes through a mid-animation door we save its data here and
            // continue casting so we can render the background surface behind it too.
            boolean hitPartialDoor             = false;
            float   doorPerpWallDistance       = 0f;
            int     doorHitTileColumn          = 0;
            int     doorHitTileRow             = 0;
            boolean doorHitCrossedVerticalLine = false;
            float   doorHitOpenFraction        = 0f;
            char    doorHitCell                = 'd';

            while (true) {
                if (sideDistanceX < sideDistanceY) {
                    sideDistanceX      += deltaDistanceX;
                    tileColumn         += stepColumn;
                    crossedVerticalLine = true;
                } else {
                    sideDistanceY      += deltaDistanceY;
                    tileRow            += stepRow;
                    crossedVerticalLine = false;
                }

                perpWallDistance = crossedVerticalLine
                        ? GameMath.perpWallDistance(sideDistanceX, deltaDistanceX)
                        : GameMath.perpWallDistance(sideDistanceY, deltaDistanceY);

                if (perpWallDistance >= RAY_MAX_LENGTH_CELLS) {
                    perpWallDistance = RAY_MAX_LENGTH_CELLS;
                    break;
                }

                char cell = level.getCell(tileColumn, tileRow);
                if (Level.isColumn(cell)) {
                    float columnCenterTileX = tileColumn + 0.5f;
                    float columnCenterTileY = tileRow    + 0.5f;
                    float columnHitDistance = GameMath.rayCircleIntersection(
                            playerTileX, playerTileY,
                            rayDirectionX, rayDirectionY,
                            columnCenterTileX, columnCenterTileY,
                            COLUMN_RADIUS_TILES);
                    if (columnHitDistance > 0f && columnHitDistance < RAY_MAX_LENGTH_CELLS) {
                        hitWall          = true;
                        hitWallCell      = cell;
                        perpWallDistance = columnHitDistance;
                        break;
                    }
                    continue; // Ray misses column — passes around it into the cell beyond.
                }
                if (Level.isWall(cell)) {
                    hitWall     = true;
                    hitWallCell = cell;
                    break;
                }
                if (Level.isDoor(cell)) {
                    float openFraction = doorManager.getOpenFractionAt(tileColumn, tileRow);
                    if (openFraction >= DOOR_OPEN_THROUGH_THRESHOLD) {
                        continue; // Fully open — ray passes through into the room beyond.
                    }
                    if (!hitPartialDoor) {
                        hitPartialDoor             = true;
                        doorPerpWallDistance       = perpWallDistance;
                        doorHitTileColumn          = tileColumn;
                        doorHitTileRow             = tileRow;
                        doorHitCrossedVerticalLine = crossedVerticalLine;
                        doorHitOpenFraction        = openFraction;
                        doorHitCell                = cell;
                    }
                    continue; // Keep casting to find the surface behind the door.
                }
            }

            // Use the door's (closer) distance for Z-buffer so sprites don't bleed through the panel.
            zBuffer[screenColumn] = hitPartialDoor ? doorPerpWallDistance : perpWallDistance;

            if (!hitWall && !hitPartialDoor) continue;

            // --- Step 1: Render the background surface (wall or column) ---
            // Drawn first so the door panel, rendered in step 2, composites on top.
            if (hitWall) {
                float lineHeight      = GameMath.wallStripeHeight(WALL_PROJECTION_SCREEN_HEIGHT, perpWallDistance);
                float unclampedBottom = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, lineHeight);
                float unclampedTop    = GameMath.wallStripeDrawTop(WALL_PROJECTION_SCREEN_HEIGHT, lineHeight);
                float drawBottom      = Math.max(0f, unclampedBottom);
                float drawTop         = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, unclampedTop);

                if (Level.isColumn(hitWallCell)) {
                    float columnCenterTileX = tileColumn + 0.5f;
                    float columnCenterTileY = tileRow    + 0.5f;
                    float hitTileX          = playerTileX + perpWallDistance * rayDirectionX;
                    float hitTileY          = playerTileY + perpWallDistance * rayDirectionY;

                    float columnU         = GameMath.columnTextureU(hitTileX, hitTileY,
                                                                     columnCenterTileX, columnCenterTileY);
                    int   columnTexColumn = GameMath.textureColumn(columnU, columnTextureWidth);

                    // Lambert shading: dot of the outward surface normal with the world light direction.
                    float normalX          = (hitTileX - columnCenterTileX) / COLUMN_RADIUS_TILES;
                    float normalY          = (hitTileY - columnCenterTileY) / COLUMN_RADIUS_TILES;
                    float lambertian       = Math.max(0f,
                                                     normalX * COLUMN_LIGHT_DIRECTION_X
                                                   + normalY * COLUMN_LIGHT_DIRECTION_Y);
                    float cylindricalShade = COLUMN_SHADE_MIN + (1f - COLUMN_SHADE_MIN) * lambertian;

                    float columnTileBrightness = level.getTileBrightness(tileColumn, tileRow, lightingTimeSeconds);
                    float shade = Math.min(
                            GameMath.wallShade(perpWallDistance, WALL_SHADING_FALLOFF)
                            * cylindricalShade * columnTileBrightness,
                            MAX_LIGHTING_SHADE);
                    float columnRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
                    float columnGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                    float columnBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                    batch.setColor(columnRed, columnGreen, columnBlue, 1f);

                    int columnTexSrcY      = GameMath.wallTextureClipSrcY(
                                                unclampedTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                                lineHeight, columnTextureHeight);
                    int columnTexSrcHeight = GameMath.wallTextureClipSrcHeight(
                                                drawTop, drawBottom,
                                                lineHeight, columnTextureHeight);
                    columnTexSrcHeight = Math.min(columnTexSrcHeight, columnTextureHeight - columnTexSrcY);
                    columnTexSrcHeight = Math.max(1, columnTexSrcHeight);

                    batch.draw(columnTexture,
                               screenColumn * WALL_COLUMN_WIDTH, drawBottom,
                               WALL_COLUMN_WIDTH, drawTop - drawBottom,
                               columnTexColumn, columnTexSrcY, 1, columnTexSrcHeight,
                               false, false);
                } else {
                    float wallHitFraction         = GameMath.wallHitPosition(playerTileX, playerTileY,
                                                                             rayDirectionX, rayDirectionY,
                                                                             perpWallDistance, crossedVerticalLine);
                    float wallHitFractionMirrored = GameMath.wallHitPositionMirrored(wallHitFraction,
                                                                                      rayDirectionX, rayDirectionY,
                                                                                      crossedVerticalLine);

                    // Floor tile adjacent to the hit wall face — its brightness spills onto the wall.
                    int   adjacentFloorColumn = crossedVerticalLine ? tileColumn - stepColumn : tileColumn;
                    int   adjacentFloorRow    = crossedVerticalLine ? tileRow                 : tileRow - stepRow;
                    float wallTileBrightness  = level.getTileBrightness(adjacentFloorColumn, adjacentFloorRow, lightingTimeSeconds);

                    Texture selectedTexture       = selectWallTexture(hitWallCell);
                    int     selectedTextureWidth  = selectWallTextureWidth(hitWallCell);
                    int     selectedTextureHeight = selectWallTextureHeight(hitWallCell);
                    int     wallTextureColumn     = GameMath.textureColumn(wallHitFractionMirrored, selectedTextureWidth);

                    // Distance shading + directional lighting + tile-brightness + alert tint.
                    // batch.setColor() embeds tint in vertex data and does NOT flush.
                    float shade = GameMath.wallShade(perpWallDistance, WALL_SHADING_FALLOFF);
                    if (!crossedVerticalLine) shade *= HORIZONTAL_FACE_SHADE_MULTIPLIER;
                    shade = Math.min(shade * wallTileBrightness, MAX_LIGHTING_SHADE);
                    float wallRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
                    float wallGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                    float wallBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                    batch.setColor(wallRed, wallGreen, wallBlue, 1f);

                    // Texture clipping — when the stripe overflows the screen sample only the
                    // texel rows corresponding to the visible portion.
                    int texSrcY      = GameMath.wallTextureClipSrcY(
                                           unclampedTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                           lineHeight, selectedTextureHeight);
                    int texSrcHeight = GameMath.wallTextureClipSrcHeight(
                                           drawTop, drawBottom,
                                           lineHeight, selectedTextureHeight);
                    texSrcHeight = Math.min(texSrcHeight, selectedTextureHeight - texSrcY);
                    texSrcHeight = Math.max(1, texSrcHeight);

                    batch.draw(selectedTexture,
                               screenColumn * WALL_COLUMN_WIDTH, drawBottom,
                               WALL_COLUMN_WIDTH, drawTop - drawBottom,
                               wallTextureColumn, texSrcY, 1, texSrcHeight,
                               false, false);
                }
            }

            // --- Step 2: Render the door panel on top of the background ---
            // The panel is anchored at the ceiling; its bottom edge rises as the door opens,
            // so the background rendered in step 1 is progressively revealed below it.
            if (hitPartialDoor) {
                float doorLineHeight      = GameMath.wallStripeHeight(WALL_PROJECTION_SCREEN_HEIGHT, doorPerpWallDistance);
                float doorUnclampedBottom = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, doorLineHeight);
                float doorUnclampedTop    = GameMath.wallStripeDrawTop(WALL_PROJECTION_SCREEN_HEIGHT, doorLineHeight);
                float doorDrawTop         = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, doorUnclampedTop);

                float doorWallHitFraction         = GameMath.wallHitPosition(playerTileX, playerTileY,
                                                                             rayDirectionX, rayDirectionY,
                                                                             doorPerpWallDistance,
                                                                             doorHitCrossedVerticalLine);
                float doorWallHitFractionMirrored = GameMath.wallHitPositionMirrored(doorWallHitFraction,
                                                                                      rayDirectionX, rayDirectionY,
                                                                                      doorHitCrossedVerticalLine);

                int   doorAdjacentFloorColumn = doorHitCrossedVerticalLine
                        ? doorHitTileColumn - stepColumn : doorHitTileColumn;
                int   doorAdjacentFloorRow    = doorHitCrossedVerticalLine
                        ? doorHitTileRow : doorHitTileRow - stepRow;
                float doorTileBrightness = level.getTileBrightness(
                        doorAdjacentFloorColumn, doorAdjacentFloorRow, lightingTimeSeconds);

                float panelHeight = GameMath.doorPanelHeight(doorLineHeight, doorHitOpenFraction);
                if (panelHeight >= 1f) {
                    float unclampedPanelBottom = GameMath.doorPanelBottom(
                            doorUnclampedBottom, doorUnclampedTop, doorHitOpenFraction);
                    float clampedPanelBottom   = Math.max(0f, unclampedPanelBottom);
                    float clampedPanelTop      = doorDrawTop;

                    if (clampedPanelTop > clampedPanelBottom) {
                        int doorTexSrcY      = GameMath.wallTextureClipSrcY(
                                                   doorUnclampedTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                                   panelHeight, doorTextureHeight);
                        int doorTexSrcHeight = GameMath.wallTextureClipSrcHeight(
                                                   clampedPanelTop, clampedPanelBottom,
                                                   panelHeight, doorTextureHeight);
                        doorTexSrcHeight = Math.min(doorTexSrcHeight, doorTextureHeight - doorTexSrcY);
                        doorTexSrcHeight = Math.max(1, doorTexSrcHeight);

                        Texture selectedDoorTexture = selectDoorTexture(doorHitCell);
                        int   doorTexColumn = GameMath.textureColumn(doorWallHitFractionMirrored, doorTextureWidth);
                        float shade = GameMath.wallShade(doorPerpWallDistance, WALL_SHADING_FALLOFF);
                        if (!doorHitCrossedVerticalLine) shade *= HORIZONTAL_FACE_SHADE_MULTIPLIER;
                        shade = Math.min(shade * doorTileBrightness, MAX_LIGHTING_SHADE);
                        batch.setColor(
                                Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST)),
                                shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN),
                                shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN),
                                1f);
                        batch.draw(selectedDoorTexture,
                                   screenColumn * WALL_COLUMN_WIDTH, clampedPanelBottom,
                                   WALL_COLUMN_WIDTH, clampedPanelTop - clampedPanelBottom,
                                   doorTexColumn, doorTexSrcY, 1, doorTexSrcHeight,
                                   false, false);
                    }
                }
            }
        }

        // Reset to white so no alert tint leaks into other renderers that share GL state.
        batch.setColor(Color.WHITE);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        wallTexturePlain.dispose();
        wallTextureConduit.dispose();
        wallTextureVent.dispose();
        wallTextureTerminal.dispose();
        wallTextureWires.dispose();
        wallTextureHazard.dispose();
        wallTextureRust.dispose();
        wallTextureGore.dispose();
        wallTextureBulkhead.dispose();
        doorTexture.dispose();
        doorTextureRed.dispose();
        doorTextureYellow.dispose();
        doorTextureBlue.dispose();
        columnTexture.dispose();
        whitePixelTexture.dispose();
    }
}

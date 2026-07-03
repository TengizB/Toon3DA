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
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.shop.VendingMachine;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.Collections;
import java.util.List;

import static ge.tbegvadze.toon3d.util.Constants.*;
import static ge.tbegvadze.toon3d.util.RenderConstants.*;

/**
 * Draws the UAC Fabricator vending machines (shop_order_6) as unique, high-detail, ANIMATED
 * billboard sprites — one per {@link VendingMachine} on the current floor.
 *
 * <p>Two-tier pipeline (see the shop_order_6 design doc):
 * <ul>
 *   <li><b>Tier A</b> — the detailed, non-moving steel chassis (window, brand plate, tray, rivets,
 *       hazard stripe, status housing) is baked ONCE into a high-resolution {@link Texture} at
 *       construction and reused every frame.</li>
 *   <li><b>Tier B</b> — a small set of animated overlays (ambient aura, breathing product-window
 *       glow, pulsing state-coloured status light, flickering holo sign, and a one-shot DISPENSE
 *       flash) are drawn per frame as additive billboard quads whose alpha/position come from
 *       {@code sin}/{@code lerp} of the shared facility clock and the machine's state. Their small
 *       glow textures are pre-generated once, so there are NO per-frame allocations.</li>
 * </ul>
 *
 * <p>The machine does not bob or float — it is wall-bolted, so all motion lives in its lights, glow,
 * sign and dispense flash. Billboard projection, depth culling and wall occlusion reuse the same
 * {@link WallRenderer} z-buffer path {@link PropRenderer} uses; the body also writes into the prop
 * z-buffer so {@code EnemyRenderer} occludes enemies that stand behind a machine.
 */
public class ShopMachineRenderer implements Renderable, Disposable {

    private final Level        level;
    private final WallRenderer wallRenderer;
    private final SpriteBatch  batch;

    // Tier-A: the baked high-resolution chassis, shared by every machine on the floor.
    private final Texture bodyTexture;
    // Tier-B: pre-generated radial glow (aura / window glow / status light) and the holo sign.
    private final Texture glowTexture;
    private final Texture holoSignTexture;

    // Machines to draw — injected by World after seeding; never null.
    private List<VendingMachine> machines = Collections.emptyList();

    // Prop z-buffers, shared from PropRenderer: read to be occluded by nearer props, and the body
    // writes into them so EnemyRenderer (which runs after us) occludes enemies behind the machine.
    private float[] propZBuffer;
    private float[] propColumnBottom;
    private float[] propColumnTop;

    // Camera / player state (mirrors PropRenderer).
    private float playerWorldX = 0f;
    private float playerWorldY = 0f;
    private float directionX   = 1f;
    private float directionY   = 0f;
    private float planeX       = 0f;
    private float planeY       = 1f;
    private float lightingTimeSeconds = 0f;
    private float alertPulse          = 0f;

    // Scratch for far→near sort — sized to the max machines a floor can hold (kept generous).
    private static final int MAX_MACHINES = 8;
    private final int[]   sortedIndices = new int[MAX_MACHINES];
    private final float[] sortedDepths  = new float[MAX_MACHINES];

    public ShopMachineRenderer(Level level, WallRenderer wallRenderer) {
        this.level        = level;
        this.wallRenderer = wallRenderer;
        this.batch        = new SpriteBatch(WALL_PROJECTION_SCREEN_WIDTH);
        this.bodyTexture     = bakeBodyTexture();
        this.glowTexture     = generateRadialGlowTexture();
        this.holoSignTexture = generateHoloSignTexture();
    }

    /** Supplies the floor's machine list (shop_order_1); called by World after seeding. */
    public void setMachines(List<VendingMachine> machines) {
        this.machines = (machines != null) ? machines : Collections.emptyList();
    }

    /**
     * Shares PropRenderer's per-column z-buffers so the machine is occluded by nearer props and,
     * in turn, occludes enemies drawn behind it. Called once after both renderers are built.
     */
    public void setPropRenderer(PropRenderer propRenderer) {
        this.propZBuffer      = propRenderer.getPropSpriteZBuffer();
        this.propColumnBottom = propRenderer.getPropSpriteColumnBottom();
        this.propColumnTop    = propRenderer.getPropSpriteColumnTop();
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

    public void setLightingTime(float timeSeconds) { this.lightingTimeSeconds = timeSeconds; }
    public void setAlertPulse(float pulse)         { this.alertPulse = pulse; }

    @Override
    public void render(OrthographicCamera camera) {
        int machineCount = Math.min(machines.size(), MAX_MACHINES);
        if (machineCount == 0) return;

        // Cull machines behind the player / beyond draw distance and record their depth.
        int visibleCount = 0;
        for (int machineIndex = 0; machineIndex < machineCount; machineIndex++) {
            VendingMachine machine = machines.get(machineIndex);
            float tileOffsetX = tileOffsetX(machine);
            float tileOffsetY = tileOffsetY(machine);
            float depth = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
            if (depth <= PROP_BEHIND_PLAYER_EPSILON_TILES) continue;
            if (depth > MAX_PROP_DRAW_DISTANCE_TILES)      continue;
            sortedIndices[visibleCount] = machineIndex;
            sortedDepths[visibleCount]  = depth;
            visibleCount++;
        }
        if (visibleCount == 0) return;

        // Insertion-sort farthest first (painter's order) so nearer machines occlude correctly.
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
            drawMachine(machines.get(sortedIndices[sortedPosition]), sortedDepths[sortedPosition]);
        }
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.end();
    }

    // -------------------------------------------------------------------------
    // Per-machine draw
    // -------------------------------------------------------------------------

    private void drawMachine(VendingMachine machine, float depth) {
        float tileOffsetX = tileOffsetX(machine);
        float tileOffsetY = tileOffsetY(machine);

        float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                tileOffsetX, tileOffsetY, directionX, directionY,
                planeX, planeY, WALL_PROJECTION_SCREEN_WIDTH);

        float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, depth);
        float spriteHeight = fullWallLineHeight * SHOP_SPRITE_HEIGHT_FRACTION;
        float spriteWidth  = spriteHeight * ((float) SHOP_SPRITE_TEXTURE_WIDTH / SHOP_SPRITE_TEXTURE_HEIGHT);
        // Ground-anchored: the cabinet base sits on the floor line (bottom of a full wall stripe).
        float drawBottom  = WALL_PROJECTION_SCREEN_HEIGHT / 2f - fullWallLineHeight / 2f;
        float drawTop     = drawBottom + spriteHeight;
        float bodyCenterY = drawBottom + spriteHeight / 2f;

        // Lighting: distance shade × tile brightness, matching walls/props so the unit sits in its hall.
        float tileBrightness = level.getTileBrightness(machine.tileColumn, machine.tileRow, lightingTimeSeconds);
        float shade = Math.min(GameMath.wallShade(depth, WALL_SHADING_FALLOFF) * tileBrightness, MAX_LIGHTING_SHADE);

        // Per-machine phase so neighbouring machines don't pulse in lockstep.
        float phase = machine.tileColumn * 0.7f + machine.tileRow * 1.3f;

        // Machine visual state → status light colour.
        boolean depleted = machine.isDepleted();
        boolean lowStock = machine.isLowStock();
        float statusRed, statusGreen, statusBlue;
        if (depleted) {
            statusRed = SHOP_STATUS_DEPLETED_R; statusGreen = SHOP_STATUS_DEPLETED_G; statusBlue = SHOP_STATUS_DEPLETED_B;
        } else if (lowStock) {
            statusRed = SHOP_STATUS_LOW_R;      statusGreen = SHOP_STATUS_LOW_G;      statusBlue = SHOP_STATUS_LOW_B;
        } else {
            statusRed = SHOP_STATUS_INSTOCK_R;  statusGreen = SHOP_STATUS_INSTOCK_G;  statusBlue = SHOP_STATUS_INSTOCK_B;
        }

        // One-shot dispense state (shop_order_5 trigger): 0..1 window flash + status blink.
        float dispenseProgress = machine.dispenseProgress(lightingTimeSeconds, SHOP_DISPENSE_DURATION_SECONDS);
        boolean dispensing     = dispenseProgress >= 0f && dispenseProgress < 1f;
        float   dispenseFlash  = dispensing ? MathUtils.sin(dispenseProgress * MathUtils.PI) : 0f;

        // ── 1. Ambient aura (additive, tile-brightness aware) behind the whole unit ──
        // Depleted machines stop advertising, so the aura fades to a dim ember.
        float auraAlpha = SHOP_AURA_BASE_ALPHA
                + SHOP_AURA_PULSE_AMPLITUDE * MathUtils.sin(lightingTimeSeconds * SHOP_AURA_PULSE_SPEED + phase);
        if (depleted) auraAlpha *= 0.35f;
        float auraSize = spriteWidth * SHOP_AURA_RADIUS_MULTIPLIER;
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.setColor(SHOP_AURA_R * shade, SHOP_AURA_G * shade, SHOP_AURA_B * shade, Math.max(0f, auraAlpha));
        drawLayer(glowTexture, screenCenterColumn, bodyCenterY, auraSize, auraSize, depth, false);

        // ── 2. Tier-A body (alpha blend, ground-anchored, writes prop z-buffer for enemy occlusion) ──
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        float bodyRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
        float bodyGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
        float bodyBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
        batch.setColor(bodyRed, bodyGreen, bodyBlue, 1f);
        drawLayer(bodyTexture, screenCenterColumn, bodyCenterY, spriteWidth, spriteHeight, depth, true);

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        // ── 3. Product-window breathing glow (cyan fabrication energy) ──
        float windowCenterY = drawTop - SHOP_WINDOW_CENTER_FRACTION_FROM_TOP * spriteHeight;
        float windowAlpha = SHOP_WINDOW_GLOW_BASE_ALPHA
                + SHOP_WINDOW_GLOW_PULSE_AMPLITUDE * MathUtils.sin(lightingTimeSeconds * SHOP_WINDOW_GLOW_PULSE_SPEED + phase);
        if (depleted) windowAlpha *= 0.25f;                 // window goes near-dark when looted
        windowAlpha = Math.max(0f, windowAlpha) + dispenseFlash * SHOP_DISPENSE_FLASH_ALPHA;
        float windowSize = spriteHeight * SHOP_WINDOW_GLOW_SIZE_FRACTION;
        batch.setColor(SHOP_WINDOW_GLOW_R * shade, SHOP_WINDOW_GLOW_G * shade, SHOP_WINDOW_GLOW_B * shade,
                       Math.min(1f, windowAlpha));
        drawLayer(glowTexture, screenCenterColumn, windowCenterY, windowSize, windowSize, depth, false);

        // ── 4. Status light halo (state-coloured; dim & steady when depleted, blinks on dispense) ──
        float statusCenterY = drawTop - SHOP_STATUS_CENTER_FRACTION_FROM_TOP * spriteHeight;
        float statusCenterX = screenCenterColumn + (SHOP_STATUS_CENTER_FRACTION_FROM_LEFT - 0.5f) * spriteWidth;
        float statusAlpha;
        if (depleted) {
            statusAlpha = SHOP_STATUS_BASE_ALPHA * 0.5f;    // dim red, no pulse
        } else {
            statusAlpha = SHOP_STATUS_BASE_ALPHA
                    + SHOP_STATUS_PULSE_AMPLITUDE * MathUtils.sin(lightingTimeSeconds * SHOP_STATUS_PULSE_SPEED + phase);
        }
        if (dispensing) statusAlpha = SHOP_STATUS_BASE_ALPHA + SHOP_STATUS_PULSE_AMPLITUDE;   // bright blink
        float statusSize = spriteHeight * SHOP_STATUS_SIZE_FRACTION;
        batch.setColor(statusRed * shade, statusGreen * shade, statusBlue * shade, Math.max(0f, statusAlpha));
        drawLayer(glowTexture, statusCenterX, statusCenterY, statusSize, statusSize, depth, false);

        // ── 5. Dispensed-product glow dropping into the tray during a purchase ──
        if (dispensing) {
            float dropTop    = windowCenterY;
            float dropBottom = drawBottom + spriteHeight * 0.08f;        // the recessed tray lip
            float dropCenterY = MathUtils.lerp(dropTop, dropBottom, dispenseProgress);
            float dropSize    = spriteWidth * 0.22f;
            batch.setColor(SHOP_WINDOW_GLOW_R * shade, SHOP_WINDOW_GLOW_G * shade, SHOP_WINDOW_GLOW_B * shade,
                           dispenseFlash);
            drawLayer(glowTexture, screenCenterColumn, dropCenterY, dropSize, dropSize, depth, false);
        }

        // ── 6. Flickering holographic sign floating above the unit ──
        float holoHeight  = spriteHeight * SHOP_HOLO_HEIGHT_FRACTION;
        float holoWidth   = holoHeight * ((float) SHOP_HOLO_TEXTURE_WIDTH / SHOP_HOLO_TEXTURE_HEIGHT);
        float holoCenterY = drawTop + spriteHeight * SHOP_HOLO_LIFT_FRACTION + holoHeight / 2f;
        float holoAlpha;
        if (depleted) {
            // "EMPTY" — a faint, slow flicker guttering out.
            holoAlpha = SHOP_HOLO_BASE_ALPHA * 0.4f
                    * (0.6f + 0.4f * MathUtils.sin(lightingTimeSeconds * SHOP_HOLO_FLICKER_SPEED * 0.4f + phase));
        } else {
            holoAlpha = SHOP_HOLO_BASE_ALPHA
                    + SHOP_HOLO_PULSE_AMPLITUDE * MathUtils.sin(lightingTimeSeconds * SHOP_HOLO_FLICKER_SPEED + phase);
        }
        float holoRed   = depleted ? SHOP_STATUS_DEPLETED_R : SHOP_HOLO_R;
        float holoGreen = depleted ? SHOP_STATUS_DEPLETED_G : SHOP_HOLO_G;
        float holoBlue  = depleted ? SHOP_STATUS_DEPLETED_B : SHOP_HOLO_B;
        batch.setColor(holoRed * shade, holoGreen * shade, holoBlue * shade, Math.max(0f, holoAlpha));
        drawLayer(holoSignTexture, screenCenterColumn, holoCenterY, holoWidth, holoHeight, depth, false);
    }

    /**
     * Draws one billboard layer as vertical 1-pixel columns, clipping each against the wall z-buffer
     * (and the shared prop z-buffer) so walls and nearer props occlude it. Blend mode and colour must
     * be set by the caller. When {@code writeOccluder} is true the drawn columns are recorded in the
     * prop z-buffer so EnemyRenderer occludes enemies standing behind the cabinet.
     */
    private void drawLayer(Texture texture, float screenCenterColumn, float centerY,
                           float layerWidth, float layerHeight, float depth, boolean writeOccluder) {
        int leftColumn  = (int) (screenCenterColumn - layerWidth / 2f);
        int rightColumn = (int) (screenCenterColumn + layerWidth / 2f);
        int columnSpan  = rightColumn - leftColumn;
        if (columnSpan <= 0) return;

        float layerBottom = centerY - layerHeight / 2f;
        float layerTop    = layerBottom + layerHeight;
        float clampedBottom = Math.max(0f, layerBottom);
        float clampedTop    = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, layerTop);
        if (clampedTop <= clampedBottom) return;

        int textureWidth  = texture.getWidth();
        int textureHeight = texture.getHeight();
        int texSrcY = GameMath.wallTextureClipSrcY(layerTop, WALL_PROJECTION_SCREEN_HEIGHT, layerHeight, textureHeight);
        int texSrcHeight = GameMath.wallTextureClipSrcHeight(clampedTop, clampedBottom, layerHeight, textureHeight);
        texSrcHeight = Math.min(texSrcHeight, textureHeight - texSrcY);
        texSrcHeight = Math.max(1, texSrcHeight);

        int firstColumn = Math.max(0, leftColumn);
        int lastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, rightColumn);
        for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
            if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
            if (propZBuffer != null && depth >= propZBuffer[screenColumn]) continue;
            if (writeOccluder && propZBuffer != null) {
                propZBuffer[screenColumn]      = depth;
                propColumnBottom[screenColumn] = clampedBottom;
                propColumnTop[screenColumn]    = clampedTop;
            }
            int texSrcX = (screenColumn - leftColumn) * textureWidth / columnSpan;
            texSrcX = MathUtils.clamp(texSrcX, 0, textureWidth - 1);
            batch.draw(texture,
                       screenColumn * WALL_COLUMN_WIDTH, clampedBottom,
                       WALL_COLUMN_WIDTH, clampedTop - clampedBottom,
                       texSrcX, texSrcY, 1, texSrcHeight,
                       false, false);
        }
    }

    private float tileOffsetX(VendingMachine machine) {
        float worldCenterX = machine.tileColumn * CELL_SIZE + CELL_SIZE / 2f;
        return (worldCenterX - playerWorldX) / CELL_SIZE;
    }

    private float tileOffsetY(VendingMachine machine) {
        float worldCenterY = machine.tileRow * CELL_SIZE + CELL_SIZE / 2f;
        return (worldCenterY - playerWorldY) / CELL_SIZE;
    }

    @Override
    public void dispose() {
        batch.dispose();
        bodyTexture.dispose();
        glowTexture.dispose();
        holoSignTexture.dispose();
    }

    // =========================================================================
    // Tier-A body bake — high-resolution procedural chassis (Pixmap → Texture)
    // =========================================================================

    /**
     * Bakes the detailed, non-moving UAC Fabricator kiosk once. Redesigned to stand apart from the
     * grey stone/metal environment and read as clearly interactable: a DARK blue-steel chassis wrapped
     * in a bright EMISSIVE AMBER FRAME (a lit tube border), a large cyan-glass product display with
     * stock silhouettes, a UAC brand plate + hazard stripe, an ILLUMINATED KEYPAD with a big green
     * "vend" button (the "operate me" cue), a recessed status-light housing, a cyan-lit dispenser tray,
     * grille vents and corner rivets. Portrait, Pixmap y-down (row 0 = visual top).
     */
    private static Texture bakeBodyTexture() {
        final int width  = SHOP_SPRITE_TEXTURE_WIDTH;
        final int height = SHOP_SPRITE_TEXTURE_HEIGHT;
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // The glowing amber frame wraps a dark chassis recessed inside it. Frame first, body within.
        final int frameLeft = 24, frameRight = 232, frameTop = 18, frameBottom = 504;
        final int frameInset = 11;
        final int bodyLeft  = frameLeft + frameInset,  bodyRight  = frameRight - frameInset;
        final int bodyTop   = frameTop  + frameInset,  bodyBottom = frameBottom - frameInset;

        // ── Drop shadow so the cabinet visibly detaches from the wall behind it ──
        pixmap.setColor(0.03f, 0.03f, 0.05f, 1f);
        pixmap.fillRectangle(frameLeft - 6, frameTop - 6, (frameRight - frameLeft) + 12, (frameBottom - frameTop) + 12);

        // ── Emissive amber frame — the primary "interactable / lit" cue; two tones read as a glow tube ──
        pixmap.setColor(0.78f, 0.42f, 0.10f, 1f);                       // deep amber base
        pixmap.fillRectangle(frameLeft, frameTop, frameRight - frameLeft, frameBottom - frameTop);
        pixmap.setColor(1.00f, 0.70f, 0.24f, 1f);                       // bright amber core band
        pixmap.fillRectangle(frameLeft + 3, frameTop + 3, (frameRight - frameLeft) - 6, (frameBottom - frameTop) - 6);
        pixmap.setColor(1.00f, 0.86f, 0.48f, 1f);                       // hot highlight along the frame top
        pixmap.fillRectangle(frameLeft + 3, frameTop + 3, (frameRight - frameLeft) - 6, 3);

        // ── Dark blue-steel chassis recessed inside the frame (contrasts hard with grey walls) ──
        pixmap.setColor(0.05f, 0.05f, 0.07f, 1f);                       // socket shadow under the frame
        pixmap.fillRectangle(bodyLeft - 3, bodyTop - 3, (bodyRight - bodyLeft) + 6, (bodyBottom - bodyTop) + 6);
        pixmap.setColor(0.13f, 0.14f, 0.18f, 1f);                       // body
        pixmap.fillRectangle(bodyLeft, bodyTop, bodyRight - bodyLeft, bodyBottom - bodyTop);
        pixmap.setColor(0.17f, 0.18f, 0.23f, 1f);                       // top cap
        pixmap.fillRectangle(bodyLeft, bodyTop, bodyRight - bodyLeft, 34);
        pixmap.setColor(0.09f, 0.09f, 0.12f, 1f);                       // base plinth
        pixmap.fillRectangle(bodyLeft, bodyBottom - 30, bodyRight - bodyLeft, 30);

        // Subtle vertical striations for a machined-metal read.
        pixmap.setColor(0.18f, 0.19f, 0.24f, 0.5f);
        for (int column = bodyLeft + 8; column < bodyRight - 6; column += 12) {
            pixmap.fillRectangle(column, bodyTop + 40, 2, (bodyBottom - 30) - (bodyTop + 40));
        }

        // ── Product window: bright cyan-glass display with faint stock silhouettes ──
        final int windowLeft = 52, windowRight = 204, windowTop = 78, windowBottom = 224;
        pixmap.setColor(0.36f, 0.78f, 0.92f, 1f);                       // bright cyan bezel
        pixmap.fillRectangle(windowLeft - 5, windowTop - 5, (windowRight - windowLeft) + 10, (windowBottom - windowTop) + 10);
        pixmap.setColor(0.05f, 0.14f, 0.19f, 1f);                       // dark glass
        pixmap.fillRectangle(windowLeft, windowTop, windowRight - windowLeft, windowBottom - windowTop);
        // Faint goods behind the glass: an ammo box, a stim cross and a credit chip.
        pixmap.setColor(0.16f, 0.42f, 0.50f, 1f);
        pixmap.fillRectangle(windowLeft + 18, windowBottom - 56, 32, 36);           // ammo box
        pixmap.setColor(0.22f, 0.52f, 0.60f, 1f);
        pixmap.fillRectangle(windowLeft + 68, windowBottom - 62, 14, 48);           // stim cross vertical
        pixmap.fillRectangle(windowLeft + 56, windowBottom - 46, 38, 14);           // stim cross horizontal
        pixmap.setColor(0.20f, 0.48f, 0.56f, 1f);
        pixmap.fillCircle(windowLeft + 120, windowBottom - 30, 16);                 // credit chip
        // Diagonal glass sheen.
        pixmap.setColor(0.70f, 0.90f, 1.00f, 0.20f);
        for (int offset = 0; offset < (windowBottom - windowTop); offset++) {
            int sheenColumn = windowLeft + 12 + offset;
            if (sheenColumn >= windowRight - 4) break;
            pixmap.drawPixel(sheenColumn, windowTop + 6 + offset);
            pixmap.drawPixel(sheenColumn + 1, windowTop + 6 + offset);
        }

        // ── UAC brand plate (orange corporate stamp) below the window ──
        pixmap.setColor(0.08f, 0.07f, 0.07f, 1f);
        pixmap.fillRectangle(88, 232, 80, 28);
        pixmap.setColor(0.92f, 0.52f, 0.16f, 1f);
        pixmap.fillRectangle(92, 236, 72, 20);
        pixmap.setColor(0.10f, 0.08f, 0.06f, 1f);                       // "UAC" bars suggestion
        pixmap.fillRectangle(100, 241, 10, 10);
        pixmap.fillRectangle(116, 241, 10, 10);
        pixmap.fillRectangle(132, 241, 10, 10);

        // ── Recessed status-light housing (Tier-B pulsing light sits here) ──
        int housingCenterX = Math.round(SHOP_STATUS_CENTER_FRACTION_FROM_LEFT * width);
        int housingCenterY = Math.round(SHOP_STATUS_CENTER_FRACTION_FROM_TOP  * height);
        pixmap.setColor(0.10f, 0.11f, 0.13f, 1f);
        pixmap.fillCircle(housingCenterX, housingCenterY, 20);
        pixmap.setColor(0.04f, 0.04f, 0.06f, 1f);
        pixmap.fillCircle(housingCenterX, housingCenterY, 13);

        // ── UAC-amber hazard stripe band across the mid body ──
        hazardStripeBand(pixmap, bodyLeft + 4, 268, (bodyRight - bodyLeft) - 8, 20,
                         0.95f, 0.66f, 0.16f);

        // ── Illuminated interaction keypad — a lit button grid that says "operate me" ──
        pixmap.setColor(0.06f, 0.06f, 0.08f, 1f);                       // recessed panel
        pixmap.fillRectangle(44, 300, 92, 108);
        pixmap.setColor(0.30f, 0.33f, 0.38f, 1f);                       // panel bezel highlight
        pixmap.drawRectangle(44, 300, 92, 108);
        int[] keyColumns = {56, 84, 112};
        int[] keyRows    = {312, 340, 368};
        for (int keyRow : keyRows) {
            for (int keyColumn : keyColumns) {
                glowButton(pixmap, keyColumn, keyRow, 18, 0.28f, 0.78f, 0.95f);   // cyan keys
            }
        }
        // A big green "confirm / vend" button beside the keypad — the clearest interaction affordance.
        pixmap.setColor(0.05f, 0.06f, 0.06f, 1f);
        pixmap.fillRectangle(150, 300, 60, 108);
        pixmap.setColor(0.10f, 0.36f, 0.18f, 1f);
        pixmap.fillRectangle(158, 318, 44, 72);
        pixmap.setColor(0.34f, 0.98f, 0.48f, 1f);                       // bright green lens
        pixmap.fillRectangle(162, 322, 36, 64);
        pixmap.setColor(0.70f, 1.00f, 0.80f, 1f);                       // hot centre highlight
        pixmap.fillRectangle(168, 330, 24, 20);

        // ── Panel seam above the tray ──
        pixmap.setColor(0.06f, 0.06f, 0.08f, 1f);
        pixmap.fillRectangle(bodyLeft, 418, bodyRight - bodyLeft, 3);

        // ── Dispenser tray: a recessed dark slot with a cyan-lit lip (where DISPENSE drops product) ──
        pixmap.setColor(0.05f, 0.05f, 0.07f, 1f);
        pixmap.fillRectangle(46, 428, 164, 44);
        pixmap.setColor(0.02f, 0.02f, 0.03f, 1f);
        pixmap.fillRectangle(54, 436, 148, 26);
        pixmap.setColor(0.36f, 0.78f, 0.92f, 1f);                       // cyan-lit tray lip
        pixmap.fillRectangle(46, 428, 164, 3);

        // ── Grille / hum vents low on the body ──
        pixmap.setColor(0.07f, 0.08f, 0.10f, 1f);
        for (int ventRow = 478; ventRow < 494; ventRow += 5) {
            pixmap.fillRectangle(70, ventRow, 116, 2);
        }

        // ── Rivets on the amber frame corners ──
        rivet(pixmap, frameLeft + 8, frameTop + 8);
        rivet(pixmap, frameRight - 8, frameTop + 8);
        rivet(pixmap, frameLeft + 8, frameBottom - 8);
        rivet(pixmap, frameRight - 8, frameBottom - 8);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /** A small metallic rivet stud: dark rim with a lighter centre highlight. */
    private static void rivet(Pixmap pixmap, int centerX, int centerY) {
        pixmap.setColor(0.10f, 0.10f, 0.12f, 1f);
        pixmap.fillCircle(centerX, centerY, 4);
        pixmap.setColor(0.72f, 0.62f, 0.40f, 1f);
        pixmap.fillCircle(centerX, centerY, 2);
    }

    /** A small illuminated keypad button: dark socket, coloured lens, brighter centre highlight. */
    private static void glowButton(Pixmap pixmap, int centerX, int centerY, int size,
                                   float red, float green, float blue) {
        int half = size / 2;
        pixmap.setColor(0.03f, 0.03f, 0.04f, 1f);
        pixmap.fillRectangle(centerX - half - 1, centerY - half - 1, size + 2, size + 2);
        pixmap.setColor(red, green, blue, 1f);
        pixmap.fillRectangle(centerX - half, centerY - half, size, size);
        pixmap.setColor(Math.min(1f, red + 0.40f), Math.min(1f, green + 0.20f), Math.min(1f, blue + 0.20f), 1f);
        pixmap.fillRectangle(centerX - half + 3, centerY - half + 3, size - 8, size - 8);
    }

    /** A diagonal hazard-stripe band (alternating tinted / dark chevrons) for brand identity. */
    private static void hazardStripeBand(Pixmap pixmap, int x, int y, int width, int height,
                                         float red, float green, float blue) {
        pixmap.setColor(0.08f, 0.08f, 0.09f, 1f);
        pixmap.fillRectangle(x, y, width, height);
        pixmap.setColor(red, green, blue, 1f);
        int stripeStep = 16;
        for (int stripeStart = 0; stripeStart < width + height; stripeStart += stripeStep * 2) {
            for (int pixelRow = 0; pixelRow < height; pixelRow++) {
                int rowStart = stripeStart + pixelRow;
                for (int pixelColumn = rowStart; pixelColumn < rowStart + stripeStep; pixelColumn++) {
                    if (pixelColumn < 0 || pixelColumn >= width) continue;
                    pixmap.drawPixel(x + pixelColumn, y + pixelRow);
                }
            }
        }
    }

    // =========================================================================
    // Tier-B overlay textures — pre-generated once
    // =========================================================================

    /** Soft radial glow (white, squared-falloff alpha) reused for aura, window glow and status light. */
    private static Texture generateRadialGlowTexture() {
        int size = SHOP_GLOW_TEXTURE_SIZE;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float center = size / 2f;
        float radius = size / 2f;
        for (int pixelY = 0; pixelY < size; pixelY++) {
            for (int pixelX = 0; pixelX < size; pixelX++) {
                float distX = (pixelX + 0.5f - center) / radius;
                float distY = (pixelY + 0.5f - center) / radius;
                float normalizedDistance = MathUtils.clamp(
                        (float) Math.sqrt(distX * distX + distY * distY), 0f, 1f);
                float falloff = (1f - normalizedDistance) * (1f - normalizedDistance);
                int alpha = (int) (falloff * 255f);
                pixmap.drawPixel(pixelX, pixelY, (255 << 24) | (255 << 16) | (255 << 8) | alpha);
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    /**
     * A stylised holographic sign panel: a soft rounded frame with abstract glyph rows suggesting a
     * "VEND / REQUISITION" readout and a downward chevron pointing at the machine. White with an
     * alpha profile; the flickering cyan (or dim-red, when depleted) tint is applied at draw time.
     */
    private static Texture generateHoloSignTexture() {
        int width  = SHOP_HOLO_TEXTURE_WIDTH;
        int height = SHOP_HOLO_TEXTURE_HEIGHT;
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // Soft translucent backing panel.
        pixmap.setColor(1f, 1f, 1f, 0.16f);
        pixmap.fillRectangle(6, 6, width - 12, height - 22);
        // Bright frame outline.
        pixmap.setColor(1f, 1f, 1f, 0.55f);
        pixmap.drawRectangle(6, 6, width - 12, height - 22);
        pixmap.drawRectangle(7, 7, width - 14, height - 24);

        // Abstract "text" glyph rows.
        pixmap.setColor(1f, 1f, 1f, 0.95f);
        int[] glyphStarts = {18, 40, 62, 84};
        int glyphRowTop = 14, glyphRowHeight = 12;
        for (int glyphStart : glyphStarts) {
            pixmap.fillRectangle(glyphStart, glyphRowTop, 14, glyphRowHeight);
        }

        // Downward chevron pointing at the machine below the panel.
        pixmap.setColor(1f, 1f, 1f, 0.85f);
        int chevronCenterX = width / 2;
        int chevronTop = height - 14;
        for (int depthPixel = 0; depthPixel < 8; depthPixel++) {
            int span = 8 - depthPixel;
            pixmap.fillRectangle(chevronCenterX - span, chevronTop + depthPixel, span * 2, 1);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }
}

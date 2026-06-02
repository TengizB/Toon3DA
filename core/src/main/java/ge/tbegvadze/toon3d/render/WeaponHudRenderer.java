package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.DoubleBarrelShotgun;
import ge.tbegvadze.toon3d.entity.PlasmaRifle;
import ge.tbegvadze.toon3d.entity.Shotgun;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.entity.WeaponVisualState;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Draws the equipped weapon sprite as a bottom-centre HUD element.
 *
 * State-driven effects replace static texture swaps:
 *   FIRING    — instant recoil kick + layered flame cone (red base, orange mid, yellow tip)
 *               that fades and shrinks over FIRE_FLASH_DURATION.
 *   RELOADING — weapon lerps downward by WEAPON_RELOAD_SLIDE_Y (mostly off screen);
 *               returns smoothly to 0 when state goes back to NORMAL.
 */
public class WeaponHudRenderer implements Renderable, Disposable {

    private final Weapon        equippedWeapon;
    private final Texture       normalTexture;
    private final SpriteBatch   batch;
    private final ShapeRenderer shapeRenderer;
    private final float         drawX;

    private WeaponVisualState previousState  = WeaponVisualState.NORMAL;
    private float             animationTimer = 0f;
    private float             currentOffsetY = 0f;

    public WeaponHudRenderer(Weapon weapon) {
        this.equippedWeapon = weapon;
        this.normalTexture  = loadOrGenerateNormalTexture(weapon);
        this.batch          = new SpriteBatch();
        this.shapeRenderer  = new ShapeRenderer();
        this.drawX          = (Constants.WORLD_WIDTH - Constants.WEAPON_HUD_WIDTH) / 2f;
    }

    /**
     * Tries to load the weapon's normal texture from disk; falls back to procedural
     * generation when the asset file is absent. Allows weapons to ship with no art.
     */
    private static Texture loadOrGenerateNormalTexture(Weapon weapon) {
        String path = weapon.getNormalTexturePath();
        if (Gdx.files.internal(path).exists()) {
            return new Texture(Gdx.files.internal(path));
        }
        if (weapon instanceof PlasmaRifle) {
            return generatePlasmaRifleTexture();
        }
        if (weapon instanceof DoubleBarrelShotgun) {
            return generateDoubleBarrelShotgunTexture();
        }
        if (weapon instanceof Shotgun) {
            return generateShotgunTexture();
        }
        return generateFallbackWeaponTexture();
    }

    /**
     * Generates a symmetric plasma rifle sprite by rendering into an offscreen FrameBuffer
     * with ShapeRenderer.  Quake-1 style: camera sits slightly above-and-behind the weapon.
     * The grip/trigger area is NOT drawn — cut off below the screen edge.
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (grip region — transparent, cut off)
     *   Y = 134 → top of canvas (muzzle emitter pointing into the level)
     *
     * Layout zones:
     *   Y  0– 16  transparent — grip cut off below screen
     *   Y 16– 72  main body (top surface): steel-blue with cyan energy coils
     *   Y 70– 88  upper receiver stepped section
     *   Y 86–120  barrel (tapered, top surface)
     *   Y 110–134 muzzle prongs + emitter
     *
     * Layers (back-to-front):
     *   1. Main body          — wide steel-blue trapezoid, top-surface perspective
     *   2. Energy coils       — 4 cyan horizontal bands + soft fringe glow
     *   3. Power cell bars    — symmetric pair of charge indicators on flanks
     *   4. Upper receiver     — stepped section above body with bevel
     *   5. Targeting scope    — centred housing with green lens
     *   6. Barrel             — tapered trapezoid seen from above
     *   7. Muzzle prongs      — flanking flanges at barrel tip
     *   8. Muzzle emitter     — layered concentric ellipses, blue → white
     */
    private static Texture generatePlasmaRifleTexture() {
        int canvasWidth  = Constants.PLASMA_RIFLE_CANVAS_WIDTH;
        int canvasHeight = Constants.PLASMA_RIFLE_CANVAS_HEIGHT;

        FrameBuffer   frameBuffer            = new FrameBuffer(Pixmap.Format.RGBA8888, canvasWidth, canvasHeight, false);
        ShapeRenderer temporaryShapeRenderer = new ShapeRenderer();
        OrthographicCamera camera            = new OrthographicCamera(canvasWidth, canvasHeight);
        camera.position.set(canvasWidth / 2f, canvasHeight / 2f, 0f);
        camera.update();

        frameBuffer.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        temporaryShapeRenderer.setProjectionMatrix(camera.combined);
        temporaryShapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawPlasmaRifleShape(temporaryShapeRenderer, canvasWidth / 2f);
        temporaryShapeRenderer.end();

        // glReadPixels returns rows with GL Y=0 at bottom; must flip before Texture upload.
        Pixmap rawPixmap = new Pixmap(canvasWidth, canvasHeight, Pixmap.Format.RGBA8888);
        Gdx.gl.glReadPixels(0, 0, canvasWidth, canvasHeight,
                            GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, rawPixmap.getPixels());

        Gdx.gl.glDisable(GL20.GL_BLEND);
        frameBuffer.end();
        frameBuffer.dispose();
        temporaryShapeRenderer.dispose();

        Pixmap flippedPixmap = flipPixmapVertically(rawPixmap);
        rawPixmap.dispose();

        Texture texture = new Texture(flippedPixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        flippedPixmap.dispose();
        return texture;
    }

    /**
     * Draws the plasma rifle silhouette in Quake-1 top-down-angle perspective.
     * The grip and trigger guard are NOT drawn — cut off below Y=16.
     * Energy coils span the full body width as seen from above.
     * Power cell indicators appear as symmetric flanking panels.
     *
     * All coordinates in canvas pixel space; Y=0 = near/grip (cut off), Y=134 = far/muzzle.
     * centerX = 96 keeps the sprite perfectly symmetric on the 192-wide canvas.
     */
    private static void drawPlasmaRifleShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Main body — wide steel-blue trapezoid, top-surface perspective
        shapeRenderer.setColor(0.28f, 0.32f, 0.42f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 60f, 16f, 52f, 72f);
        // Far-edge highlight
        shapeRenderer.setColor(0.40f, 0.46f, 0.58f, 1f);
        shapeRenderer.rect(centerX - 52f, 69f, 104f, 3f);
        // Near-edge shadow
        shapeRenderer.setColor(0.18f, 0.21f, 0.29f, 1f);
        shapeRenderer.rect(centerX - 60f, 16f, 120f, 3f);
        // Mid-surface groove
        shapeRenderer.setColor(0.20f, 0.24f, 0.32f, 1f);
        shapeRenderer.rect(centerX - 56f, 44f, 112f, 2f);

        // 2. Energy coils — 4 bright cyan horizontal bands across body (seen from above)
        float[] coilYPositions = {24f, 34f, 46f, 58f};
        shapeRenderer.setColor(0.00f, 0.88f, 1.00f, 1f);
        for (float coilY : coilYPositions) {
            shapeRenderer.rect(centerX - 56f, coilY, 112f, 3f);
        }
        shapeRenderer.setColor(0.00f, 0.62f, 0.90f, 0.50f);
        for (float coilY : coilYPositions) {
            shapeRenderer.rect(centerX - 56f, coilY - 1f, 112f, 1f);  // glow fringe below
            shapeRenderer.rect(centerX - 56f, coilY + 3f, 112f, 1f);  // glow fringe above
        }

        // 3. Power cell indicators — symmetric flanking panels on both sides of body
        shapeRenderer.setColor(0.16f, 0.20f, 0.28f, 1f);
        shapeRenderer.rect(centerX - 60f, 22f, 10f, 36f);   // left cell housing
        shapeRenderer.rect(centerX + 50f, 22f, 10f, 36f);   // right cell housing
        shapeRenderer.setColor(0.00f, 0.72f, 1.00f, 0.95f);
        shapeRenderer.rect(centerX - 58f, 26f, 6f, 5f);     // left charge bar 1
        shapeRenderer.rect(centerX - 58f, 33f, 6f, 5f);     // left charge bar 2
        shapeRenderer.rect(centerX - 58f, 40f, 6f, 5f);     // left charge bar 3
        shapeRenderer.rect(centerX - 58f, 47f, 6f, 5f);     // left charge bar 4
        shapeRenderer.rect(centerX + 52f, 26f, 6f, 5f);     // right charge bar 1
        shapeRenderer.rect(centerX + 52f, 33f, 6f, 5f);     // right charge bar 2
        shapeRenderer.rect(centerX + 52f, 40f, 6f, 5f);     // right charge bar 3
        shapeRenderer.rect(centerX + 52f, 47f, 6f, 5f);     // right charge bar 4

        // 4. Upper receiver — stepped section above main body
        shapeRenderer.setColor(0.26f, 0.30f, 0.40f, 1f);
        shapeRenderer.rect(centerX - 44f, 70f, 88f, 18f);
        // Far-edge bevel
        shapeRenderer.setColor(0.38f, 0.44f, 0.56f, 1f);
        shapeRenderer.rect(centerX - 44f, 85f, 88f, 3f);
        // Near-edge shadow
        shapeRenderer.setColor(0.16f, 0.18f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 44f, 70f, 88f, 3f);

        // 5. Targeting scope — centred on upper receiver (prominent from top view)
        shapeRenderer.setColor(0.16f, 0.19f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 18f, 72f, 36f, 16f);   // scope housing
        shapeRenderer.setColor(0.10f, 0.82f, 0.58f, 0.90f);
        shapeRenderer.rect(centerX - 14f, 74f, 28f, 12f);   // green lens surface
        shapeRenderer.setColor(0.40f, 1.00f, 0.75f, 0.55f);
        shapeRenderer.rect(centerX - 14f, 84f, 28f,  2f);   // lens far-edge highlight

        // 6. Barrel — tapered trapezoid viewed from above (wide near, narrow far)
        shapeRenderer.setColor(0.28f, 0.32f, 0.42f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 26f, 86f, 14f, 118f);
        // Near-face highlight
        shapeRenderer.setColor(0.38f, 0.44f, 0.56f, 1f);
        shapeRenderer.rect(centerX - 24f, 114f, 48f, 2f);
        // Mid-barrel groove
        shapeRenderer.setColor(0.16f, 0.18f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 20f, 102f, 40f, 2f);

        // 7. Muzzle prongs — symmetric flanging flanges at barrel tip
        shapeRenderer.setColor(0.22f, 0.26f, 0.34f, 1f);
        shapeRenderer.rect(centerX - 26f, 110f, 8f, 10f);   // left prong
        shapeRenderer.rect(centerX + 18f, 110f, 8f, 10f);   // right prong

        // 8. Muzzle emitter — concentric layered ellipses: deep blue → bright cyan → white
        shapeRenderer.setColor(0.18f, 0.22f, 0.30f, 1f);
        shapeRenderer.ellipse(centerX - 13f, 116f, 26f, 14f);  // dark outer housing
        shapeRenderer.setColor(0.08f, 0.52f, 1.00f, 0.95f);
        shapeRenderer.ellipse(centerX - 11f, 118f, 22f, 11f);  // outer blue glow
        shapeRenderer.setColor(0.30f, 0.82f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX -  8f, 120f, 16f,  9f);  // mid bright-cyan
        shapeRenderer.setColor(0.75f, 0.97f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX -  5f, 122f, 10f,  7f);  // hot white-cyan
        shapeRenderer.setColor(1.00f, 1.00f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX -  3f, 124f,  6f,  5f);  // hottest pinpoint
        shapeRenderer.setColor(0.08f, 0.52f, 1.00f, 0.30f);
        shapeRenderer.ellipse(centerX - 16f, 112f, 32f, 10f);  // under-glow halo
    }

    /**
     * Generates a break-action double-barrel shotgun sprite using ShapeRenderer into an
     * offscreen FrameBuffer.  Quake-1 style: camera sits slightly above-and-behind.
     * The grip/stock is NOT drawn — cut off below screen.
     *
     * Visually distinct from the pump-action Shotgun:
     *   - Walnut wood receiver (warm brown) vs. steel pump receiver
     *   - Break-action hinge line divides receiver from barrel breech
     *   - Top-lever latch knob centred on breech
     *   - Shorter, slightly wider barrel tubes with a narrower centre gap
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (grip region — transparent, cut off)
     *   Y = 134 → top of canvas (barrel muzzles pointing into the level)
     *
     * Layout zones:
     *   Y  0– 14  transparent — grip cut off below screen
     *   Y 14– 60  walnut receiver body (top surface)
     *   Y 58– 74  gunmetal breech block with break-action hinge
     *   Y 72–122  barrel tubes (top surface, shorter than pump-action)
     *   Y 120–134 muzzle end-caps + bore openings
     *
     * Layers drawn back-to-front:
     *   1. Walnut receiver    — warm brown trapezoid with horizontal grain lines
     *   2. Gunmetal breech    — block above receiver with hinge groove and top-lever
     *   3. Left barrel        — top-surface cylinder: outer/inner shadow + crown highlight
     *   4. Right barrel       — mirror of left barrel
     *   5. Centre channel     — tight shadow gap + narrow sighting rib
     *   6. Muzzle end-caps    — flat plates closing each barrel tube
     *   7. Bore openings      — foreshortened dark ellipses + rim-shine fringe
     */
    private static Texture generateDoubleBarrelShotgunTexture() {
        int canvasWidth  = Constants.DBL_SHOTGUN_CANVAS_WIDTH;
        int canvasHeight = Constants.DBL_SHOTGUN_CANVAS_HEIGHT;

        FrameBuffer   frameBuffer            = new FrameBuffer(Pixmap.Format.RGBA8888, canvasWidth, canvasHeight, false);
        ShapeRenderer temporaryShapeRenderer = new ShapeRenderer();
        OrthographicCamera camera            = new OrthographicCamera(canvasWidth, canvasHeight);
        camera.position.set(canvasWidth / 2f, canvasHeight / 2f, 0f);
        camera.update();

        frameBuffer.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        temporaryShapeRenderer.setProjectionMatrix(camera.combined);
        temporaryShapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawDoubleBarrelShotgunShape(temporaryShapeRenderer, canvasWidth / 2f);
        temporaryShapeRenderer.end();

        Pixmap rawPixmap = new Pixmap(canvasWidth, canvasHeight, Pixmap.Format.RGBA8888);
        Gdx.gl.glReadPixels(0, 0, canvasWidth, canvasHeight,
                            GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, rawPixmap.getPixels());

        Gdx.gl.glDisable(GL20.GL_BLEND);
        frameBuffer.end();
        frameBuffer.dispose();
        temporaryShapeRenderer.dispose();

        Pixmap flippedPixmap = flipPixmapVertically(rawPixmap);
        rawPixmap.dispose();

        Texture texture = new Texture(flippedPixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        flippedPixmap.dispose();
        return texture;
    }

    /**
     * Draws a break-action double-barrel shotgun in Quake-1 top-down-angle perspective.
     * Visually distinct from the pump-action Shotgun: warm walnut receiver, break-action
     * hinge visible from above, top-lever latch knob centred on breech, shorter + wider
     * barrel tubes with a tighter gap between them.
     *
     * All coordinates in canvas pixel space; Y=0 = near/grip (cut off), Y=134 = far/muzzle.
     * centerX = 96 keeps the sprite perfectly symmetric on the 192-wide canvas.
     *
     * Symmetry reference (all dimensions from centerX=96):
     *   Barrel outer edge:    ±22 px  (X 74 / 118)
     *   Barrel inner edge:    ± 4 px  (X 92 / 100)
     *   Centre gap:             8 px wide (X 92–100)
     */
    private static void drawDoubleBarrelShotgunShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Walnut receiver — warm brown trapezoid, top-surface perspective taper
        //    Darker and deeper than pump-action's steel receiver — key visual differentiator
        shapeRenderer.setColor(0.38f, 0.18f, 0.06f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 56f, 14f, 46f, 60f);
        // Grain lines (horizontal wood-grain bands across full receiver width)
        shapeRenderer.setColor(0.27f, 0.12f, 0.04f, 1f);
        shapeRenderer.rect(centerX - 52f, 20f, 104f, 2f);
        shapeRenderer.rect(centerX - 50f, 30f, 100f, 2f);
        shapeRenderer.rect(centerX - 48f, 42f, 96f,  2f);
        shapeRenderer.rect(centerX - 46f, 52f, 92f,  2f);
        // Near-edge shadow
        shapeRenderer.setColor(0.22f, 0.10f, 0.03f, 1f);
        shapeRenderer.rect(centerX - 56f, 14f, 112f, 3f);

        // 2. Gunmetal breech block — sits above receiver, houses the break-action mechanism
        shapeRenderer.setColor(0.26f, 0.28f, 0.34f, 1f);
        shapeRenderer.rect(centerX - 46f, 58f, 92f, 16f);
        // Far-edge highlight
        shapeRenderer.setColor(0.42f, 0.46f, 0.54f, 1f);
        shapeRenderer.rect(centerX - 46f, 71f, 92f, 3f);
        // Near-edge shadow
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        shapeRenderer.rect(centerX - 46f, 58f, 92f, 3f);
        // Break-action hinge groove (horizontal line dividing receiver from breech)
        shapeRenderer.setColor(0.14f, 0.15f, 0.19f, 1f);
        shapeRenderer.rect(centerX - 46f, 70f, 92f, 2f);
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 46f, 72f, 92f, 1f);  // bright seam above groove
        // Top-lever latch knob — centred, oval (break-action actuator seen from above)
        shapeRenderer.setColor(0.34f, 0.38f, 0.46f, 1f);
        shapeRenderer.ellipse(centerX - 10f, 61f, 20f, 10f);
        shapeRenderer.setColor(0.52f, 0.58f, 0.66f, 1f);
        shapeRenderer.ellipse(centerX -  7f, 64f, 14f,  6f);  // latch highlight

        // 3. Left barrel — NARROW (18px) × TALL (50px): top surface of tube pointing away
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        shapeRenderer.rect(centerX - 22f, 72f, 18f, 50f);   // barrel body
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(centerX - 22f, 72f,  3f, 50f);   // outer-edge shadow
        shapeRenderer.setColor(0.44f, 0.48f, 0.54f, 1f);
        shapeRenderer.rect(centerX - 19f, 72f,  7f, 50f);   // crown highlight
        shapeRenderer.setColor(0.12f, 0.13f, 0.16f, 1f);
        shapeRenderer.rect(centerX -  7f, 72f,  3f, 50f);   // inner-edge shadow

        // 4. Right barrel — mirror of left
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        shapeRenderer.rect(centerX +  4f, 72f, 18f, 50f);   // barrel body
        shapeRenderer.setColor(0.12f, 0.13f, 0.16f, 1f);
        shapeRenderer.rect(centerX +  4f, 72f,  3f, 50f);   // inner-edge shadow
        shapeRenderer.setColor(0.44f, 0.48f, 0.54f, 1f);
        shapeRenderer.rect(centerX + 12f, 72f,  7f, 50f);   // crown highlight
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(centerX + 19f, 72f,  3f, 50f);   // outer-edge shadow

        // 5. Centre channel — 8px dark gap + narrow sighting rib
        shapeRenderer.setColor(0.06f, 0.07f, 0.08f, 1f);
        shapeRenderer.rect(centerX -  4f, 72f,  8f, 50f);
        shapeRenderer.setColor(0.20f, 0.22f, 0.26f, 1f);
        shapeRenderer.rect(centerX -  2f, 72f,  4f, 50f);

        // 6. Muzzle end-caps (18px wide, matching barrel width)
        shapeRenderer.setColor(0.18f, 0.19f, 0.23f, 1f);
        shapeRenderer.rect(centerX - 22f, 120f, 18f, 4f);   // left cap
        shapeRenderer.rect(centerX +  4f, 120f, 18f, 4f);   // right cap

        // 7. Bore openings — nearly circular (18×14): heavier gauge = slightly wider than pump
        shapeRenderer.setColor(0.04f, 0.04f, 0.05f, 0.95f);
        shapeRenderer.ellipse(centerX - 22f, 114f, 18f, 14f);  // left bore
        shapeRenderer.ellipse(centerX +  4f, 114f, 18f, 14f);  // right bore
        // Rim shine
        shapeRenderer.setColor(0.30f, 0.32f, 0.36f, 0.60f);
        shapeRenderer.ellipse(centerX - 22f, 126f, 18f,  3f);
        shapeRenderer.ellipse(centerX +  4f, 126f, 18f,  3f);
    }

    /**
     * Generates a symmetric pump-action shotgun sprite using ShapeRenderer into an
     * offscreen FrameBuffer.
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (grip, fully off-screen at runtime)
     *   Y = 134 → top of canvas (muzzle bores, pointing toward the horizon)
     *
     * First-person above-horizon perspective: the player's eye is above the gun;
     * grip and stock are cut off below the screen. Each barrel appears NARROW (16px)
     * and TALL (50px) because you see only the thin top surface of a tube pointing away.
     * The bore openings face nearly straight at the viewer → nearly circular (16×14).
     *
     * Layout zones:
     *   Y  0– 14  transparent — grip cut off below screen
     *   Y 14– 62  receiver body (top surface)
     *   Y 60– 74  pump fore-end slide
     *   Y 72–122  barrel tubes — NARROW (16px) × TALL (50px)
     *   Y 120–128 muzzle caps + bore openings
     *
     * Layers drawn back-to-front:
     *   1. Receiver body    — wide gunmetal trapezoid from above; Y=14..62
     *   2. Fore-end pump    — slide with symmetric ridges; Y=60..74
     *   3. Left barrel      — narrow (16px) × tall (50px) tube top surface; Y=72..122
     *   4. Right barrel     — mirror of left barrel
     *   5. Inter-barrel gap — dark channel + thin sighting rib
     *   6. Barrel band      — steel ring clamping both tubes; Y=96..101
     *   7. Muzzle caps      — flat steel plates at barrel ends; Y=120..124
     *   8. Bore openings    — nearly circular (16×14) ellipses + rim-shine; Y=114..128
     */
    private static Texture generateShotgunTexture() {
        int canvasWidth  = Constants.SHOTGUN_CANVAS_WIDTH;
        int canvasHeight = Constants.SHOTGUN_CANVAS_HEIGHT;

        FrameBuffer   frameBuffer            = new FrameBuffer(Pixmap.Format.RGBA8888, canvasWidth, canvasHeight, false);
        ShapeRenderer temporaryShapeRenderer = new ShapeRenderer();
        OrthographicCamera camera            = new OrthographicCamera(canvasWidth, canvasHeight);
        camera.position.set(canvasWidth / 2f, canvasHeight / 2f, 0f);
        camera.update();

        frameBuffer.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        temporaryShapeRenderer.setProjectionMatrix(camera.combined);
        temporaryShapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawShotgunShape(temporaryShapeRenderer, canvasWidth / 2f);
        temporaryShapeRenderer.end();

        Pixmap rawPixmap = new Pixmap(canvasWidth, canvasHeight, Pixmap.Format.RGBA8888);
        Gdx.gl.glReadPixels(0, 0, canvasWidth, canvasHeight,
                            GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, rawPixmap.getPixels());

        Gdx.gl.glDisable(GL20.GL_BLEND);
        frameBuffer.end();
        frameBuffer.dispose();
        temporaryShapeRenderer.dispose();

        Pixmap flippedPixmap = flipPixmapVertically(rawPixmap);
        rawPixmap.dispose();

        Texture texture = new Texture(flippedPixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        flippedPixmap.dispose();
        return texture;
    }

    /**
     * Draws a double-barrel pump-action shotgun from the first-person above-horizon view.
     * The player's eye is above the gun; grip is fully off-screen. Each barrel appears
     * NARROW (16px wide) and TALL (50px high) — you see the thin top surface of a tube
     * pointing toward the horizon. Bore openings face nearly straight at the viewer
     * (16×14px ≈ circular). All elements symmetric about centerX = 96.
     *
     * Symmetry reference (all dimensions from centerX=96):
     *   Barrel outer edge:    ±22 px  (X 74 / 118)
     *   Barrel inner edge:    ± 6 px  (X 90 / 102)
     *   Centre gap:            12 px wide (X 90–102)
     */
    private static void drawShotgunShape(ShapeRenderer shapeRenderer, float centerX) {

        // Y=0..14 left transparent — grip cut off below screen (first-person: eyes above gun)

        // 1. Receiver body — wide gunmetal trapezoid, wider at bottom (closer to viewer)
        shapeRenderer.setColor(0.24f, 0.26f, 0.30f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 46f, 14f, 40f, 62f);
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        shapeRenderer.rect(centerX - 40f, 59f, 80f, 3f);    // top highlight strip
        shapeRenderer.setColor(0.12f, 0.13f, 0.16f, 1f);
        shapeRenderer.rect(centerX - 46f, 14f, 92f, 3f);    // bottom shadow strip
        shapeRenderer.setColor(0.16f, 0.17f, 0.20f, 1f);
        shapeRenderer.rect(centerX - 42f, 38f, 84f, 2f);    // mid groove accent

        // 2. Fore-end pump slide — connects receiver to barrel assembly (Y=60..74)
        shapeRenderer.setColor(0.28f, 0.30f, 0.35f, 1f);
        shapeRenderer.rect(centerX - 36f, 60f, 72f, 14f);
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        shapeRenderer.rect(centerX - 36f, 72f, 72f,  2f);   // top highlight
        // Pump ridges — symmetric left/centre/right
        shapeRenderer.setColor(0.16f, 0.17f, 0.20f, 1f);
        shapeRenderer.rect(centerX - 16f, 62f, 4f, 10f);    // left ridge
        shapeRenderer.rect(centerX -  2f, 62f, 4f, 10f);    // centre ridge
        shapeRenderer.rect(centerX + 12f, 62f, 4f, 10f);    // right ridge

        // 3. Left barrel — NARROW (16px) × TALL (50px): top surface of tube pointing away
        //    Cylinder shading: outer-edge shadow → crown highlight → inner-edge shadow
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        shapeRenderer.rect(centerX - 22f, 72f, 16f, 50f);   // barrel body
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(centerX - 22f, 72f,  3f, 50f);   // outer-edge shadow
        shapeRenderer.setColor(0.45f, 0.49f, 0.56f, 1f);
        shapeRenderer.rect(centerX - 19f, 72f,  5f, 50f);   // crown highlight (top surface)
        shapeRenderer.setColor(0.12f, 0.13f, 0.16f, 1f);
        shapeRenderer.rect(centerX -  9f, 72f,  3f, 50f);   // inner-edge shadow

        // 4. Right barrel — perfect mirror of left
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        shapeRenderer.rect(centerX +  6f, 72f, 16f, 50f);   // barrel body
        shapeRenderer.setColor(0.12f, 0.13f, 0.16f, 1f);
        shapeRenderer.rect(centerX +  6f, 72f,  3f, 50f);   // inner-edge shadow
        shapeRenderer.setColor(0.45f, 0.49f, 0.56f, 1f);
        shapeRenderer.rect(centerX + 14f, 72f,  5f, 50f);   // crown highlight
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(centerX + 19f, 72f,  3f, 50f);   // outer-edge shadow

        // 5. Inter-barrel gap — dark channel with thin centre sighting rib
        shapeRenderer.setColor(0.08f, 0.08f, 0.10f, 1f);
        shapeRenderer.rect(centerX -  6f, 72f, 12f, 50f);   // dark channel
        shapeRenderer.setColor(0.18f, 0.19f, 0.22f, 1f);
        shapeRenderer.rect(centerX -  2f, 72f,  4f, 50f);   // centre sighting rib

        // 6. Barrel band — steel ring clamping both barrels together (Y=96..101)
        shapeRenderer.setColor(0.30f, 0.32f, 0.38f, 1f);
        shapeRenderer.rect(centerX - 22f,  96f, 44f, 5f);
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 22f, 100f, 44f, 1f);   // top highlight
        shapeRenderer.setColor(0.12f, 0.13f, 0.16f, 1f);
        shapeRenderer.rect(centerX - 22f,  96f, 44f, 1f);   // bottom shadow

        // 7. Muzzle caps — flat steel plates closing each barrel tube (Y=120..124)
        shapeRenderer.setColor(0.18f, 0.19f, 0.23f, 1f);
        shapeRenderer.rect(centerX - 22f, 120f, 16f, 4f);   // left cap
        shapeRenderer.rect(centerX +  6f, 120f, 16f, 4f);   // right cap

        // 8. Bore openings — nearly circular (16×14): looking almost straight at muzzle
        shapeRenderer.setColor(0.04f, 0.04f, 0.05f, 0.95f);
        shapeRenderer.ellipse(centerX - 22f, 114f, 16f, 14f);   // left bore
        shapeRenderer.ellipse(centerX +  6f, 114f, 16f, 14f);   // right bore
        // Rim shine — dim bright fringe at the top of each bore opening
        shapeRenderer.setColor(0.32f, 0.34f, 0.38f, 0.50f);
        shapeRenderer.ellipse(centerX - 22f, 126f, 16f,  3f);   // left rim
        shapeRenderer.ellipse(centerX +  6f, 126f, 16f,  3f);   // right rim
    }

    /**
     * Draws a symmetric trapezoid centred on centerX.
     * Bottom edge half-width = bottomHalfWidth at y = bottomY.
     * Top edge half-width   = topHalfWidth   at y = topY.
     * Decomposed into two triangles sharing the bottom-left → top-right diagonal.
     */
    private static void drawSymmetricTrapezoid(ShapeRenderer shapeRenderer,
                                                float centerX,
                                                float bottomHalfWidth, float bottomY,
                                                float topHalfWidth,    float topY) {
        float leftBottom  = centerX - bottomHalfWidth;
        float rightBottom = centerX + bottomHalfWidth;
        float leftTop     = centerX - topHalfWidth;
        float rightTop    = centerX + topHalfWidth;
        shapeRenderer.triangle(leftBottom, bottomY, rightBottom, bottomY, rightTop, topY);
        shapeRenderer.triangle(leftBottom, bottomY, rightTop,    topY,    leftTop,  topY);
    }

    /**
     * Flips a Pixmap vertically so that GL Y=0 (bottom row from glReadPixels) becomes
     * image row 0 (visual top, as expected by Texture upload).
     */
    private static Pixmap flipPixmapVertically(Pixmap source) {
        int width  = source.getWidth();
        int height = source.getHeight();
        Pixmap flipped = new Pixmap(width, height, source.getFormat());
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                flipped.drawPixel(col, row, source.getPixel(col, height - 1 - row));
            }
        }
        return flipped;
    }

    /**
     * Minimal grey silhouette shown when a weapon has no asset file and no specific
     * procedural generator. Prevents a crash; replace with a real sprite later.
     */
    private static Texture generateFallbackWeaponTexture() {
        int pixmapWidth  = 192;
        int pixmapHeight = 134;
        Pixmap pixmap = new Pixmap(pixmapWidth, pixmapHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(0.35f, 0.35f, 0.40f, 1f);
        pixmap.fillRectangle(40, 40, 110, 24);
        pixmap.fillRectangle(110, 60, 28, 60);
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void render(OrthographicCamera camera) {
        float deltaTime = Gdx.graphics.getDeltaTime();
        WeaponVisualState state = equippedWeapon.getVisualState();

        if (state != previousState) {
            animationTimer = 0f;
            previousState  = state;
        }
        animationTimer += deltaTime;

        advanceOffsetY(state, deltaTime);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.draw(normalTexture, drawX, currentOffsetY,
                   Constants.WEAPON_HUD_WIDTH, Constants.WEAPON_HUD_HEIGHT);
        batch.end();

        if (state == WeaponVisualState.FIRING) {
            float normalizedTime = Math.min(animationTimer / Constants.FIRE_FLASH_DURATION, 1f);
            if (normalizedTime < 1f) {
                if (equippedWeapon instanceof PlasmaRifle) {
                    renderPlasmaEffect(camera, normalizedTime);
                } else {
                    renderFlameEffect(camera, normalizedTime);
                }
            }
        }
    }

    private void advanceOffsetY(WeaponVisualState state, float deltaTime) {
        if (state == WeaponVisualState.FIRING) {
            float normalizedTime = Math.min(animationTimer / Constants.FIRE_FLASH_DURATION, 1f);
            currentOffsetY = -Constants.WEAPON_RECOIL_OFFSET_Y * (1f - normalizedTime);
        } else {
            float targetOffsetY = (state == WeaponVisualState.RELOADING)
                                  ? -Constants.WEAPON_RELOAD_SLIDE_Y
                                  : 0f;
            currentOffsetY += (targetOffsetY - currentOffsetY)
                              * Math.min(deltaTime * Constants.WEAPON_OFFSET_LERP_SPEED, 1f);
        }
    }

    /**
     * Draws a plasma bolt burst: expanding cyan-blue disc with side energy arcs
     * and a rising white-cyan lance. Replaces the shotgun flame for PlasmaRifle.
     * All layers fade and contract as normalizedTime approaches 1.
     */
    private void renderPlasmaEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha   = 1f - normalizedTime;
        float scale   = 1f - normalizedTime * 0.60f;
        float barrelX = Constants.WORLD_WIDTH / 2f;
        float barrelY = currentOffsetY + Constants.WEAPON_HUD_HEIGHT
                        * Constants.WEAPON_BARREL_TIP_Y_FRACTION;
        float radius  = Constants.PLASMA_BLAST_RADIUS * scale;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Outer translucent blue ring
        shapeRenderer.setColor(0.00f, 0.55f, 1.00f, alpha * 0.28f);
        shapeRenderer.ellipse(barrelX - radius, barrelY - radius * 0.55f,
                              radius * 2f, radius * 1.10f);

        // Main plasma burst disc (bright cyan)
        shapeRenderer.setColor(0.00f, 0.88f, 1.00f, alpha * 0.72f);
        shapeRenderer.ellipse(barrelX - radius * 0.70f, barrelY - radius * 0.35f,
                              radius * 1.40f, radius * 0.70f);

        // Hot inner core (white-cyan)
        shapeRenderer.setColor(0.75f, 0.97f, 1.00f, alpha * 0.90f);
        shapeRenderer.ellipse(barrelX - radius * 0.28f, barrelY - radius * 0.14f,
                              radius * 0.56f, radius * 0.28f);

        // Left energy arc
        float arcHalfBase = radius * 0.46f;
        shapeRenderer.setColor(0.28f, 0.72f, 1.00f, alpha * 0.62f);
        shapeRenderer.triangle(
            barrelX - arcHalfBase * 1.20f, barrelY - arcHalfBase * 0.30f,
            barrelX - arcHalfBase * 0.40f, barrelY,
            barrelX - arcHalfBase * 0.90f, barrelY + arcHalfBase * 0.75f
        );

        // Right energy arc
        shapeRenderer.triangle(
            barrelX + arcHalfBase * 0.40f, barrelY,
            barrelX + arcHalfBase * 1.20f, barrelY - arcHalfBase * 0.30f,
            barrelX + arcHalfBase * 0.90f, barrelY + arcHalfBase * 0.75f
        );

        // Rising plasma lance (wide cyan cone)
        shapeRenderer.setColor(0.50f, 0.94f, 1.00f, alpha * 0.82f);
        shapeRenderer.triangle(
            barrelX - arcHalfBase * 0.22f, barrelY,
            barrelX + arcHalfBase * 0.22f, barrelY,
            barrelX,                        barrelY + radius * 1.75f
        );
        // Lance hot core (white)
        shapeRenderer.setColor(1.00f, 1.00f, 1.00f, alpha * 0.58f);
        shapeRenderer.triangle(
            barrelX - arcHalfBase * 0.09f, barrelY,
            barrelX + arcHalfBase * 0.09f, barrelY,
            barrelX,                        barrelY + radius * 1.15f
        );

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Draws a layered flame cone rising from the barrel tip.
     * Layers from outermost to innermost: red side tongues → red base cone →
     * orange cone → yellow cone → white-yellow hot tip.
     * All layers shrink and fade as normalizedTime approaches 1.
     */
    private void renderFlameEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha    = 1f - normalizedTime;
        float scale    = 1f - normalizedTime * 0.55f;
        float barrelX  = Constants.WORLD_WIDTH / 2f;
        float barrelY  = currentOffsetY + Constants.WEAPON_HUD_HEIGHT
                         * Constants.WEAPON_BARREL_TIP_Y_FRACTION;
        float height   = Constants.WEAPON_FLAME_HEIGHT * scale;
        float halfBase = Constants.WEAPON_FLAME_BASE_WIDTH * scale;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Horizontal muzzle blast disc at barrel mouth
        shapeRenderer.setColor(1f, 0.6f, 0.1f, alpha * 0.50f);
        shapeRenderer.ellipse(barrelX - halfBase * 0.9f, barrelY - halfBase * 0.2f,
                              halfBase * 1.8f, halfBase * 0.4f);

        // Left side red tongue (angled outward)
        shapeRenderer.setColor(0.90f, 0.08f, 0f, alpha * 0.70f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.65f, barrelY,
            barrelX - halfBase * 0.05f, barrelY,
            barrelX - halfBase * 0.55f, barrelY + height * 0.50f
        );

        // Right side red tongue (angled outward)
        shapeRenderer.setColor(0.90f, 0.08f, 0f, alpha * 0.70f);
        shapeRenderer.triangle(
            barrelX + halfBase * 0.05f, barrelY,
            barrelX + halfBase * 0.65f, barrelY,
            barrelX + halfBase * 0.55f, barrelY + height * 0.50f
        );

        // Wide red base cone
        shapeRenderer.setColor(0.85f, 0.12f, 0f, alpha * 0.75f);
        shapeRenderer.triangle(
            barrelX - halfBase,        barrelY,
            barrelX + halfBase,        barrelY,
            barrelX,                   barrelY + height * 0.58f
        );

        // Orange middle cone
        shapeRenderer.setColor(1f, 0.48f, 0f, alpha * 0.82f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.62f, barrelY,
            barrelX + halfBase * 0.62f, barrelY,
            barrelX,                    barrelY + height * 0.78f
        );

        // Yellow tall inner cone
        shapeRenderer.setColor(1f, 0.88f, 0.08f, alpha * 0.88f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.33f, barrelY,
            barrelX + halfBase * 0.33f, barrelY,
            barrelX,                    barrelY + height
        );

        // White-yellow hot core tip
        shapeRenderer.setColor(1f, 1f, 0.85f, alpha * 0.65f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.16f, barrelY,
            barrelX + halfBase * 0.16f, barrelY,
            barrelX,                    barrelY + height * 0.72f
        );

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void dispose() {
        normalTexture.dispose();
        batch.dispose();
        shapeRenderer.dispose();
    }
}

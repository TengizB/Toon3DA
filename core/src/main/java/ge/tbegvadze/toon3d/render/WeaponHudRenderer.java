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
import ge.tbegvadze.toon3d.entity.Chaingun;
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

    private WeaponVisualState previousState       = WeaponVisualState.NORMAL;
    private float             animationTimer      = 0f;
    private float             currentOffsetY      = Constants.WEAPON_HUD_BASE_Y;
    private int               lastFlashCycleCount = 0;

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
        if (weapon instanceof Chaingun) {
            return generateChaingunTexture();
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
     *   - Nearly face-on view: two large bore openings dominate the upper half
     *   - Each barrel 78 px wide; bore is a large 66×50 dark ellipse inside a 78×58 steel rim
     *   - Walnut receiver and steel breech visible below; bottom cut off by screen edge
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (grip region — transparent, cut off)
     *   Y = 134 → top of canvas (barrel muzzles pointing toward the horizon)
     *
     * Layout zones:
     *   Y  0– 14  transparent  — grip cut off below screen
     *   Y 14– 38  walnut receiver (warm brown, partially cut off)
     *   Y 36– 56  steel breech block with break-action hinge groove
     *   Y 52–130  barrel bodies — 78 px wide each, gunmetal with cylinder shading
     *   Y 74–132  bore openings — large nearly-circular (78×58 rim, 66×50 bore)
     *
     * Layers drawn back-to-front:
     *   1. Walnut receiver    — warm brown trapezoid with horizontal grain lines
     *   2. Gunmetal breech    — block above receiver with hinge groove
     *   3. Left barrel        — wide gunmetal body; outer/inner shadow + crown highlight
     *   4. Right barrel       — mirror of left barrel
     *   5. Centre gap         — deep shadow channel between barrels
     *   6. Retaining band     — steel collar ring near muzzle
     *   7. Left bore          — large nearly-circular dark opening + top-rim shine
     *   8. Right bore         — mirror of left bore
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
     * Draws a break-action double-barrel shotgun in Quake-1 first-person style.
     *
     * The gun is viewed nearly face-on from slightly above. The two large bore openings
     * dominate the upper half of the sprite — each barrel is 78 px wide and the bore
     * diameter is the primary visual element, ringed by a thin steel rim.
     *
     * Layout zones (Y-up, Y=0=grip cut-off, Y=134=muzzle):
     *   Y  0– 14  transparent  — grip cut off below screen
     *   Y 14– 38  walnut receiver (warm brown, partially cut off)
     *   Y 36– 56  steel breech block with break-action hinge
     *   Y 52–130  barrel bodies  — 78 px wide each, gunmetal with cylinder shading
     *   Y 74–132  bore openings  — large 78×58 ellipse (steel rim) + 66×50 dark bore
     *
     * Symmetry reference (all dimensions from centerX=96):
     *   Barrel outer edge:  ±82 px  (X 14 / 178)
     *   Barrel inner edge:  ± 4 px  (X 92 / 100)
     *   Centre gap:           8 px wide (X 92–100)
     *   Bore center:         ±43 px  (X 53 / 139)
     */
    private static void drawDoubleBarrelShotgunShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Walnut receiver — warm brown, partially cut off at screen bottom
        shapeRenderer.setColor(0.42f, 0.22f, 0.08f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 54f, 14f, 42f, 38f);
        shapeRenderer.setColor(0.34f, 0.16f, 0.05f, 1f);
        shapeRenderer.rect(centerX - 50f, 18f, 100f, 2f);
        shapeRenderer.rect(centerX - 46f, 26f,  92f, 2f);
        shapeRenderer.rect(centerX - 42f, 34f,  84f, 2f);
        shapeRenderer.setColor(0.22f, 0.10f, 0.03f, 1f);
        shapeRenderer.rect(centerX - 54f, 14f, 108f, 3f);  // near-edge shadow

        // 2. Steel breech block — full width of both barrels, break-action hinge groove
        shapeRenderer.setColor(0.24f, 0.26f, 0.32f, 1f);
        shapeRenderer.rect(centerX - 50f, 36f, 100f, 20f);
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 50f, 53f, 100f,  3f);  // far-edge highlight
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        shapeRenderer.rect(centerX - 50f, 36f, 100f,  3f);  // near-edge shadow
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(centerX - 50f, 50f, 100f,  2f);  // hinge groove
        shapeRenderer.setColor(0.38f, 0.42f, 0.50f, 1f);
        shapeRenderer.rect(centerX - 50f, 52f, 100f,  1f);  // groove shine

        // 3–4. Barrel tubes — perspective-tapered (base Y=52, scale 1.0 → muzzle Y=130, scale 0.80).
        //   Face-on view: outer edges converge from ±82px to ±66px; inner edges from ±4px to ±3px.

        // 3. Left barrel body — perspective-tapered gunmetal tube
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 82f, centerX - 4f, 52f,
                                            centerX - 66f, centerX - 3f, 130f);
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 82f, centerX - 75f, 52f,
                                            centerX - 66f, centerX - 60f, 130f);  // outer-edge shadow
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 53f, centerX - 33f, 52f,
                                            centerX - 42f, centerX - 26f, 130f);  // crown highlight
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 10f, centerX - 4f, 52f,
                                            centerX -  8f, centerX - 3f, 130f);   // inner-edge shadow

        // 4. Right barrel body — mirror of left
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 4f, centerX + 82f, 52f,
                                            centerX + 3f, centerX + 66f, 130f);
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 4f, centerX + 10f, 52f,
                                            centerX + 3f, centerX +  8f, 130f);   // inner-edge shadow
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 33f, centerX + 53f, 52f,
                                            centerX + 26f, centerX + 42f, 130f);  // crown highlight
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 75f, centerX + 82f, 52f,
                                            centerX + 60f, centerX + 66f, 130f);  // outer-edge shadow

        // 5. Centre gap between barrels — tapered deep shadow channel
        shapeRenderer.setColor(0.06f, 0.07f, 0.09f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 4f, centerX + 4f, 52f,
                                            centerX - 3f, centerX + 3f, 130f);

        // 6. Muzzle retaining band — tapered to match barrels (Y=106..114, scale ≈0.85)
        //    Outer edges at Y=110: 1.0 - 0.20×58/78 = 0.851 → ±82×0.851 = ±70px
        shapeRenderer.setColor(0.30f, 0.33f, 0.40f, 1f);
        shapeRenderer.rect(centerX - 70f, 106f, 67f, 8f);   // left barrel band
        shapeRenderer.rect(centerX +  3f, 106f, 67f, 8f);   // right barrel band
        shapeRenderer.setColor(0.50f, 0.54f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 70f, 112f, 67f, 2f);   // left band highlight
        shapeRenderer.rect(centerX +  3f, 112f, 67f, 2f);   // right band highlight

        // 7. Left bore — large nearly-circular dark opening scaled to tapered barrel
        //    At bore-centre Y≈103: scale ≈0.87 → rim 68px wide (was 78), bore 57px (was 66)
        shapeRenderer.setColor(0.18f, 0.20f, 0.24f, 1f);
        shapeRenderer.ellipse(centerX - 71f, 74f, 68f, 58f);   // steel barrel face / outer rim
        shapeRenderer.setColor(0.04f, 0.04f, 0.05f, 0.97f);
        shapeRenderer.ellipse(centerX - 66f, 78f, 57f, 50f);   // bore darkness
        shapeRenderer.setColor(0.44f, 0.48f, 0.58f, 0.65f);
        shapeRenderer.ellipse(centerX - 64f, 116f, 56f, 14f);  // top-rim shine crescent

        // 8. Right bore — mirror of left
        shapeRenderer.setColor(0.18f, 0.20f, 0.24f, 1f);
        shapeRenderer.ellipse(centerX +  3f, 74f, 68f, 58f);
        shapeRenderer.setColor(0.04f, 0.04f, 0.05f, 0.97f);
        shapeRenderer.ellipse(centerX +  8f, 78f, 57f, 50f);
        shapeRenderer.setColor(0.44f, 0.48f, 0.58f, 0.65f);
        shapeRenderer.ellipse(centerX +  8f, 116f, 56f, 14f);
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
     * Draws a pump-action shotgun from the Quake-1 top-down first-person perspective.
     *
     * Top-down view: the player's eye is above and slightly behind the weapon.
     * The barrel tubes point AWAY from the camera toward the horizon.
     * Therefore the bore holes face away and are completely invisible — no bore ellipses drawn.
     * The only evidence of the muzzle is a 2px bright steel cap at the barrel tip Y.
     *
     * All coordinates in canvas pixel space; Y=0 = near/grip (transparent, cut off below screen).
     * Y=134 = far/muzzle. centerX = 96 keeps the sprite perfectly symmetric on the 192-wide canvas.
     *
     * Convergence factor 0.65: offset_at_muzzle = offset_at_base × 0.65.
     * Barrel layout (offsets from centerX):
     *   Left:  base outer CX-22, inner CX-6  → muzzle outer CX-14, inner CX-4
     *   Right: base inner CX+6,  outer CX+22 → muzzle inner CX+4,  outer CX+14
     *
     * Layer order (back-to-front):
     *   1. Receiver body           Y=14..62  — wide gunmetal trapezoid, top surface
     *   2. Receiver highlights     Y=59..62  — top/bottom edge shading strips
     *   3. Pump fore-end slide     Y=60..74  — sliding action assembly with ridges
     *   4. Left barrel             Y=72..122 — perspective-tapered gunmetal tube
     *   5. Right barrel            Y=72..122 — mirror of left barrel
     *   6. Inter-barrel gap        Y=72..122 — dark channel + sighting rib
     *   7. Barrel retaining band   Y=96..101 — steel ring clamping both tubes
     *   8. Muzzle caps             Y=120..122 — 2px bright steel rim at barrel tips (NO bore holes)
     */
    private static void drawShotgunShape(ShapeRenderer shapeRenderer, float centerX) {

        // Y=0..14 left transparent — grip cut off below screen (first-person: eyes above gun)

        // 1. Receiver body — wide gunmetal trapezoid, wider at near end (closer to viewer)
        //    Top surface of the receiver block as seen from slightly above
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 46f, 14f, 40f, 62f);

        // 2. Receiver edge highlights — far edge brighter (top surface faces camera), near edge darker
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 40f, 59f, 80f, 3f);    // far-edge top highlight
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(centerX - 46f, 14f, 92f, 3f);    // near-edge bottom shadow
        shapeRenderer.setColor(0.16f, 0.18f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 42f, 38f, 84f, 2f);    // mid-body groove

        // 3. Pump fore-end slide — connects receiver to barrel assembly (Y=60..74)
        shapeRenderer.setColor(0.26f, 0.29f, 0.34f, 1f);
        shapeRenderer.rect(centerX - 36f, 60f, 72f, 14f);
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 36f, 72f, 72f,  2f);   // far-edge highlight
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(centerX - 36f, 60f, 72f,  2f);   // near-edge shadow
        // Pump ridges — 3 symmetric grooves showing the textured grip surface
        shapeRenderer.setColor(0.16f, 0.18f, 0.21f, 1f);
        shapeRenderer.rect(centerX - 16f, 62f, 4f, 10f);    // left ridge
        shapeRenderer.rect(centerX -  2f, 62f, 4f, 10f);    // centre ridge
        shapeRenderer.rect(centerX + 12f, 62f, 4f, 10f);    // right ridge

        // 4–5. Barrel tubes — perspective-tapered (base Y=72, muzzle Y=122, factor 0.65).
        //   All x-offsets from centerX converge: offset_muzzle = offset_base × 0.65.
        //   Left:  base [CX-22, CX-6]  → muzzle [CX-14, CX-4]   (16px→10px wide)
        //   Right: base [CX+6,  CX+22] → muzzle [CX+4,  CX+14]  (16px→10px wide)
        //   Cylinders are viewed from above: see narrow TOP surface of tube pointing away.
        //   Shading: outer-edge shadow → crown highlight → inner-edge shadow, all tapered.

        // 4. Left barrel body — perspective-tapered gunmetal tube
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 22f, centerX - 6f, 72f,
                                            centerX - 14f, centerX - 4f, 122f);
        // Outer-edge shadow (3px at base → 2px at muzzle): surface curves away from camera
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 22f, centerX - 19f, 72f,
                                            centerX - 14f, centerX - 12f, 122f);
        // Crown highlight (5px at base → 3px at muzzle): very top of cylinder faces camera
        shapeRenderer.setColor(0.45f, 0.49f, 0.56f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 19f, centerX - 14f, 72f,
                                            centerX - 12f, centerX -  9f, 122f);
        // Inner-edge shadow (3px at base → 2px at muzzle): inner curve away from camera
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX -  9f, centerX -  6f, 72f,
                                            centerX -  6f, centerX -  4f, 122f);

        // 5. Right barrel — mirror of left
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 6f, centerX + 22f, 72f,
                                            centerX + 4f, centerX + 14f, 122f);
        // Inner-edge shadow
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 6f, centerX +  9f, 72f,
                                            centerX + 4f, centerX +  6f, 122f);
        // Crown highlight
        shapeRenderer.setColor(0.45f, 0.49f, 0.56f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 14f, centerX + 19f, 72f,
                                            centerX +  9f, centerX + 12f, 122f);
        // Outer-edge shadow
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 19f, centerX + 22f, 72f,
                                            centerX + 12f, centerX + 14f, 122f);

        // 6. Inter-barrel gap — dark recessed channel between the two barrels, with thin sighting rib
        shapeRenderer.setColor(0.08f, 0.08f, 0.10f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 6f, centerX + 6f, 72f,
                                            centerX - 4f, centerX + 4f, 122f);
        // Thin centre sighting rib (raised rail between barrels)
        shapeRenderer.setColor(0.20f, 0.22f, 0.26f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 2f, centerX + 2f, 72f,
                                            centerX - 1f, centerX + 1f, 122f);

        // 7. Barrel retaining band — tapered steel ring clamping both tubes (Y=96..101)
        //    Width at Y=98: scale = 1.0 - (1-0.65) × (98-72) / (122-72) = 1.0 - 0.35 × 0.52 = 0.818
        //    Outer edges: ±22 × 0.818 ≈ ±18px; inner gap inner edge: ±4 × 0.818 ≈ ±3px
        shapeRenderer.setColor(0.30f, 0.32f, 0.38f, 1f);
        shapeRenderer.rect(centerX - 18f,  96f, 15f, 5f);   // left barrel band segment
        shapeRenderer.rect(centerX +  3f,  96f, 15f, 5f);   // right barrel band segment
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 18f, 100f, 15f, 1f);   // left band top highlight
        shapeRenderer.rect(centerX +  3f, 100f, 15f, 1f);   // right band top highlight
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        shapeRenderer.rect(centerX - 18f,  96f, 15f, 1f);   // left band bottom shadow
        shapeRenderer.rect(centerX +  3f,  96f, 15f, 1f);   // right band bottom shadow

        // 8. Muzzle caps — the circular RIM EDGE is visible at the barrel tip even on a pointed-away tube.
        //    Like a pencil held horizontally away from you: you see the circumference ring, not the face.
        //    This is NOT a bore hole — it is the metallic rim at the muzzle end seen from the side.
        //    Width = muzzle barrel width: outer at CX±14, inner at CX±4 → 10px each.
        //    NO bore ellipses — barrels face away, bore holes are completely invisible.
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 14f, 120f, 10f, 2f);   // left muzzle cap
        shapeRenderer.rect(centerX +  4f, 120f, 10f, 2f);   // right muzzle cap
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
     * Draws a general (asymmetric) trapezoid from four explicit corners.
     * Used for perspective-tapered shapes where left and right edges converge
     * toward a vanishing point as Y increases (barrel tubes pointing away from player).
     * Decomposed into two triangles sharing the bottom-left → top-right diagonal.
     */
    private static void drawGeneralTrapezoid(ShapeRenderer shapeRenderer,
                                              float leftBottom, float rightBottom, float bottomY,
                                              float leftTop,    float rightTop,    float topY) {
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
     * Generates a triple-barrel rotary chaingun sprite using ShapeRenderer into an
     * offscreen FrameBuffer.  Top-down perspective: player looks slightly downward
     * along the barrel tubes; muzzles point away, bores are invisible.
     * The grip is NOT drawn — cut off below screen edge.
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (grip region — transparent, cut off)
     *   Y = 134 → top of canvas (muzzle tips, farthest from player)
     *
     * Orientation convention for this weapon:
     *   Barrels point AWAY from the player (muzzle at high Y = top of screen).
     *   The player sees the curved top surfaces of three tapered cylinder tubes.
     *   Bore openings face away and are completely invisible — do not draw them.
     *   Perspective taper: near end (Y=82) wide, muzzle end (Y=128) narrow (~39%).
     *
     * Layout zones:
     *   Y  0– 14  transparent   — grip cut off below screen
     *   Y 14– 50  receiver body — military olive-green trapezoid
     *   Y 46– 70  motor drum    — bronze/copper housing
     *   Y 68– 86  shroud collar — steel ring with amber accent
     *   Y 82–128  barrel tubes  — three perspective-tapered cylinders (top-down view)
     *   Y 126–128 muzzle caps   — thin bright steel rim at barrel tips (no bore)
     *
     * Three-barrel layout (centerX=96, taper ~39%, gap 6px→4px):
     *   Left   barrel: base CX−34..CX−16 (18px) → muzzle CX−21..CX−10 (11px)
     *   Center barrel: base CX−10..CX+10 (20px) → muzzle CX−6..CX+6   (12px)
     *   Right  barrel: base CX+16..CX+34 (18px) → muzzle CX+10..CX+21 (11px)
     *
     * Layers (back-to-front):
     *   1. Receiver body    — olive-green trapezoid + hazard stripe
     *   2. Motor drum       — bronze/copper housing with concentric ring detail
     *   3. Shroud collar    — steel ring with amber accent line
     *   4. Barrel bodies    — three tapered gunmetal trapezoids
     *   5. Gap channels     — deep shadow between barrels (also tapered)
     *   6. Cylinder shading — outer shadow / crown highlight / mid-dark per barrel
     *   7. Muzzle caps      — thin bright steel lip at barrel tips
     */
    private static Texture generateChaingunTexture() {
        int canvasWidth  = Constants.CHAINGUN_CANVAS_WIDTH;
        int canvasHeight = Constants.CHAINGUN_CANVAS_HEIGHT;

        FrameBuffer        frameBuffer            = new FrameBuffer(Pixmap.Format.RGBA8888, canvasWidth, canvasHeight, false);
        ShapeRenderer      temporaryShapeRenderer = new ShapeRenderer();
        OrthographicCamera camera                 = new OrthographicCamera(canvasWidth, canvasHeight);
        camera.position.set(canvasWidth / 2f, canvasHeight / 2f, 0f);
        camera.update();

        frameBuffer.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        temporaryShapeRenderer.setProjectionMatrix(camera.combined);
        temporaryShapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawChaingunShape(temporaryShapeRenderer, canvasWidth / 2f);
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
     * Draws a triple-barrel rotary chaingun in Quake-1 first-person style.
     *
     * Top-down perspective: the barrels point away from the camera.
     * The player sees the outer curved surfaces of three tapered cylinder tubes.
     * Near end (Y=82, bottom) is widest; muzzle (Y=128, top) is narrowest.
     * Bore openings face away and are completely invisible.
     *
     * Barrel layout (offsets from centerX=96, taper ~39%):
     *   Left:   base CX−34..CX−16 (18px) → muzzle CX−21..CX−10 (11px)
     *   Center: base CX−10..CX+10 (20px) → muzzle CX−6..CX+6   (12px)
     *   Right:  base CX+16..CX+34 (18px) → muzzle CX+10..CX+21 (11px)
     *   Gaps:   base 6px → muzzle 4px (same vanishing point)
     */
    private static void drawChaingunShape(ShapeRenderer shapeRenderer, float centerX) {
        // Top-down view: barrels point away from the camera (muzzle at top/far, base at bottom/near).
        // Perspective taper: near end wide, far end narrow. No bore holes visible.

        // 1. Receiver body — military olive-green trapezoid (Y=14..50), wider at near end
        shapeRenderer.setColor(0.18f, 0.20f, 0.13f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 48f, 14f, 38f, 50f);
        shapeRenderer.setColor(0.28f, 0.31f, 0.20f, 1f);
        shapeRenderer.rect(centerX - 38f, 47f, 76f, 3f);    // far-edge highlight
        shapeRenderer.setColor(0.10f, 0.11f, 0.07f, 1f);
        shapeRenderer.rect(centerX - 48f, 14f, 96f, 3f);    // near-edge shadow
        shapeRenderer.setColor(0.85f, 0.50f, 0.05f, 1f);   // amber hazard stripe
        shapeRenderer.rect(centerX - 36f, 30f, 72f, 5f);
        shapeRenderer.setColor(0.10f, 0.10f, 0.08f, 1f);   // stripe dividers
        shapeRenderer.rect(centerX - 24f, 30f, 3f, 5f);
        shapeRenderer.rect(centerX -  6f, 30f, 3f, 5f);
        shapeRenderer.rect(centerX +  9f, 30f, 3f, 5f);
        shapeRenderer.rect(centerX + 24f, 30f, 3f, 5f);

        // 2. Bronze/copper motor drum housing (Y=46..70) with concentric ring detail
        shapeRenderer.setColor(0.38f, 0.26f, 0.09f, 1f);
        shapeRenderer.rect(centerX - 36f, 46f, 72f, 24f);
        shapeRenderer.setColor(0.52f, 0.36f, 0.13f, 1f);
        shapeRenderer.rect(centerX - 36f, 67f, 72f, 3f);   // far highlight
        shapeRenderer.setColor(0.22f, 0.14f, 0.05f, 1f);
        shapeRenderer.rect(centerX - 36f, 46f, 72f, 3f);   // near shadow
        shapeRenderer.setColor(0.28f, 0.18f, 0.06f, 1f);
        shapeRenderer.rect(centerX - 28f, 51f, 56f, 14f);  // inner dark band
        shapeRenderer.setColor(0.46f, 0.32f, 0.11f, 1f);
        shapeRenderer.rect(centerX - 22f, 54f, 44f, 8f);   // inner lighter ring

        // 3. Barrel shroud collar (Y=68..86) — steel ring with amber accent line
        shapeRenderer.setColor(0.28f, 0.30f, 0.36f, 1f);
        shapeRenderer.rect(centerX - 34f, 68f, 68f, 18f);
        shapeRenderer.setColor(0.85f, 0.50f, 0.05f, 1f);
        shapeRenderer.rect(centerX - 34f, 80f, 68f, 2f);   // amber accent
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 34f, 82f, 68f, 1f);   // top highlight
        shapeRenderer.setColor(0.15f, 0.16f, 0.20f, 1f);
        shapeRenderer.rect(centerX - 34f, 68f, 68f, 2f);   // bottom shadow

        // 4. Three barrel bodies — perspective-tapered cylinders pointing away from camera.
        //    Near end (Y=82): left CX-34..CX-16 (18px), center CX-10..CX+10 (20px), right CX+16..CX+34 (18px)
        //    Far end  (Y=128): left CX-21..CX-10 (11px), center CX-6..CX+6 (12px), right CX+10..CX+21 (11px)
        //    Taper ~39%: matches a vanishing point roughly 6x the barrel length above the canvas.
        shapeRenderer.setColor(0.20f, 0.22f, 0.26f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 34f, centerX - 16f, 82f,
            centerX - 21f, centerX - 10f, 128f);  // left barrel
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 10f, centerX + 10f, 82f,
            centerX -  6f, centerX +  6f, 128f);  // center barrel
        drawGeneralTrapezoid(shapeRenderer,
            centerX + 16f, centerX + 34f, 82f,
            centerX + 10f, centerX + 21f, 128f);  // right barrel

        // 5. Inter-barrel gap shadow channels — recessed grooves that taper with the same vanishing point
        shapeRenderer.setColor(0.05f, 0.06f, 0.07f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 16f, centerX - 10f, 82f,
            centerX - 10f, centerX -  6f, 128f);  // left gap (6px→4px)
        drawGeneralTrapezoid(shapeRenderer,
            centerX + 10f, centerX + 16f, 82f,
            centerX +  6f, centerX + 10f, 128f);  // right gap (6px→4px)

        // 6. Cylinder shading — each barrel is a cylinder whose curved top surface faces the camera.
        //    Shadow on both outer edges (surface curves away); bright crown left-of-centre (light from upper-left).
        //    All shading strips use the same taper ratio so they stay parallel in perspective.

        // Left barrel (18px base → 11px muzzle): 3px outer | 8px crown | 4px mid | 3px inner
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 34f, centerX - 31f, 82f,
            centerX - 21f, centerX - 19f, 128f);  // outer shadow (3px→2px)
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 19f, centerX - 16f, 82f,
            centerX - 12f, centerX - 10f, 128f);  // inner shadow (3px→2px)
        shapeRenderer.setColor(0.38f, 0.42f, 0.50f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 31f, centerX - 23f, 82f,
            centerX - 19f, centerX - 14f, 128f);  // crown highlight (8px→5px)
        shapeRenderer.setColor(0.14f, 0.16f, 0.19f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 23f, centerX - 19f, 82f,
            centerX - 14f, centerX - 12f, 128f);  // mid-dark transition (4px→2px)

        // Center barrel (20px base → 12px muzzle): 3px left | 14px crown | 3px right
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 10f, centerX -  7f, 82f,
            centerX -  6f, centerX -  4f, 128f);  // left shadow (3px→2px)
        drawGeneralTrapezoid(shapeRenderer,
            centerX +  7f, centerX + 10f, 82f,
            centerX +  4f, centerX +  6f, 128f);  // right shadow (3px→2px)
        shapeRenderer.setColor(0.38f, 0.42f, 0.50f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX -  7f, centerX +  7f, 82f,
            centerX -  4f, centerX +  4f, 128f);  // crown highlight (14px→8px)

        // Right barrel (18px base → 11px muzzle) — mirror of left
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX + 16f, centerX + 19f, 82f,
            centerX + 10f, centerX + 12f, 128f);  // inner shadow (3px→2px)
        drawGeneralTrapezoid(shapeRenderer,
            centerX + 31f, centerX + 34f, 82f,
            centerX + 19f, centerX + 21f, 128f);  // outer shadow (3px→2px)
        shapeRenderer.setColor(0.38f, 0.42f, 0.50f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX + 23f, centerX + 31f, 82f,
            centerX + 14f, centerX + 19f, 128f);  // crown highlight (8px→5px)
        shapeRenderer.setColor(0.14f, 0.16f, 0.19f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX + 19f, centerX + 23f, 82f,
            centerX + 12f, centerX + 14f, 128f);  // mid-dark transition (4px→2px)

        // 7. Muzzle steel cap band (Y=126..128) — the circular rim edge is visible from the side
        //    even on a pointed-away barrel, like the tip of a pencil viewed from above.
        //    A thin bright strip confirms barrel length without exposing any bore.
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 21f, 126f, 11f, 2f);  // left muzzle cap
        shapeRenderer.rect(centerX -  6f, 126f, 12f, 2f);  // center muzzle cap
        shapeRenderer.rect(centerX + 10f, 126f, 11f, 2f);  // right muzzle cap
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

        // Reset the flash animation on every individual shot, including burst bullets that
        // keep the weapon in FIRING state without a NORMAL transition.
        int currentFlashCycle = equippedWeapon.getFlashCycleCount();
        if (currentFlashCycle != lastFlashCycleCount) {
            animationTimer      = 0f;
            lastFlashCycleCount = currentFlashCycle;
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
            currentOffsetY = Constants.WEAPON_HUD_BASE_Y
                             - Constants.WEAPON_RECOIL_OFFSET_Y * (1f - normalizedTime);
        } else {
            float targetOffsetY = (state == WeaponVisualState.RELOADING)
                                  ? Constants.WEAPON_HUD_BASE_Y - Constants.WEAPON_RELOAD_SLIDE_Y
                                  : Constants.WEAPON_HUD_BASE_Y;
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

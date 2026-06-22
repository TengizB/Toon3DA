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
import ge.tbegvadze.toon3d.entity.CombatKnife;
import ge.tbegvadze.toon3d.entity.DoubleBarrelShotgun;
import ge.tbegvadze.toon3d.entity.Fist;
import ge.tbegvadze.toon3d.entity.GrenadeLauncher;
import ge.tbegvadze.toon3d.entity.Incinerator;
import ge.tbegvadze.toon3d.entity.MeleeChainsaw;
import ge.tbegvadze.toon3d.entity.PlasmaRifle;
import ge.tbegvadze.toon3d.entity.Railgun;
import ge.tbegvadze.toon3d.entity.Shotgun;
import ge.tbegvadze.toon3d.entity.Hammer;
import ge.tbegvadze.toon3d.entity.MeleeWeapon;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.entity.WeaponVisualState;
import ge.tbegvadze.toon3d.util.Constants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ge.tbegvadze.toon3d.util.WeaponConstants;

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

    // All weapon textures are generated once at construction so that no FrameBuffer
    // operations happen during gameplay. FrameBuffer.end() resets the GL viewport to
    // the full back-buffer size, which would overwrite the FitViewport letterbox and
    // cause visual deformation + touch-coordinate misalignment for the rest of the frame.
    private final Map<Class<? extends Weapon>, Texture> weaponTextureCache;
    private Weapon              equippedWeapon;
    private Texture             normalTexture;
    private final SpriteBatch   batch;
    private final ShapeRenderer shapeRenderer;
    private final float         drawX;

    private WeaponVisualState previousState       = WeaponVisualState.NORMAL;
    private float             animationTimer      = 0f;
    private float             currentOffsetY      = WeaponConstants.WEAPON_HUD_BASE_Y;
    private int               lastFlashCycleCount = 0;

    // Chaingun spark positions — static to avoid per-frame heap allocation.
    // Each pair (sparkFractionsX[i], sparkFractionsY[i]) is a fraction of the spark
    // spread constants applied from the barrel origin. Ten fixed positions produce
    // an asymmetric scatter that reads as random without using java.util.Random.
    private static final float[] CHAINGUN_SPARK_FRACTIONS_X = {
        -0.82f,  0.55f, -0.40f,  0.72f, -0.62f,
         0.30f, -0.90f,  0.48f, -0.18f,  0.65f
    };
    private static final float[] CHAINGUN_SPARK_FRACTIONS_Y = {
         0.45f,  0.72f,  0.88f,  0.38f,  0.62f,
         0.92f,  0.55f,  0.78f,  0.42f,  0.68f
    };

    // Railgun lightning branch jitter — static to avoid per-frame heap allocation.
    // Each value is a signed fraction of RAILGUN_EFFECT_BRANCH_JITTER applied as a
    // perpendicular kink at successive segments along a branching arc, producing a
    // zig-zag lightning shape without using java.util.Random. Indexed by segment.
    private static final float[] RAILGUN_BRANCH_JITTER_FRACTIONS = {
        0.0f, 0.85f, -0.55f, 0.40f, -0.20f
    };

    /**
     * Pre-generates normal textures for every weapon in the arsenal.
     * All FrameBuffer work happens here, at startup, before the game loop begins.
     * Arsenal may be empty when the player starts unarmed (start room); renderer handles null weapon.
     */
    public WeaponHudRenderer(List<Weapon> arsenal) {
        weaponTextureCache = new HashMap<>();
        for (Weapon weapon : arsenal) {
            weaponTextureCache.put(weapon.getClass(), loadOrGenerateNormalTexture(weapon));
        }
        this.equippedWeapon = arsenal.isEmpty() ? null : arsenal.get(0);
        this.normalTexture  = (equippedWeapon != null) ? weaponTextureCache.get(equippedWeapon.getClass()) : null;
        this.batch          = new SpriteBatch();
        this.shapeRenderer  = new ShapeRenderer();
        this.drawX          = (Constants.WORLD_WIDTH - WeaponConstants.WEAPON_HUD_WIDTH) / 2f;
    }

    /**
     * Swaps to a different weapon. Passing null clears the equipped weapon (player is unarmed).
     * Looks up the pre-generated texture — no GL operations.
     * The weapon sprite slides in from below to signal the switch.
     */
    public void setEquippedWeapon(Weapon weapon) {
        equippedWeapon = weapon;
        if (weapon == null) {
            normalTexture = null;
            return;
        }
        normalTexture = weaponTextureCache.get(weapon.getClass());
        if (normalTexture == null) {
            // Weapon class not pre-registered — generate and cache its texture now so
            // the HUD never passes null to batch.draw(). Callers should ideally call
            // registerAdditionalWeapon() before setEquippedWeapon() for new weapon types.
            registerAdditionalWeapon(weapon);
            normalTexture = weaponTextureCache.get(weapon.getClass());
        }
        previousState       = WeaponVisualState.NORMAL;
        animationTimer      = 0f;
        lastFlashCycleCount = weapon.getFlashCycleCount();
        currentOffsetY      = WeaponConstants.WEAPON_HUD_BASE_Y - WeaponConstants.WEAPON_RELOAD_SLIDE_Y;
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
        if (weapon instanceof Railgun) {
            return generateRailgunTexture();
        }
        if (weapon instanceof Incinerator) {
            return generateIncineratorTexture();
        }
        if (weapon instanceof GrenadeLauncher) {
            return generateGrenadeLauncherTexture();
        }
        if (weapon instanceof Fist) {
            return generateFistTexture();
        }
        if (weapon instanceof CombatKnife) {
            return generateCombatKnifeTexture();
        }
        if (weapon instanceof Hammer) {
            return generateHammerTexture();
        }
        if (weapon instanceof MeleeChainsaw) {
            return generateMeleeChainsawTexture();
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
        int canvasWidth  = WeaponConstants.PLASMA_RIFLE_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.PLASMA_RIFLE_CANVAS_HEIGHT;

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
     * Draws a plasma rifle in Quake-1 top-down first-person perspective.
     *
     * Energy weapon, top-down view. No bore holes. The muzzle ends with a layered
     * concentric emitter (deep-blue outer ring through white-cyan core). The player
     * sees the flat top surface of a tapered steel-blue body with bright cyan coils.
     *
     * Convergence: barrel tapers from 26px half-width at Y=86 to 14px at Y=118.
     * Y=0..14  transparent (grip cut off below screen)
     * Y=14..72 main body with energy coils and power cell flanks
     * Y=70..88 upper receiver stepped section with scope
     * Y=86..118 barrel tapered trapezoid
     * Y=118..132 muzzle prongs and layered emitter
     */
    private static void drawPlasmaRifleShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Main body — wide steel-blue trapezoid, top-surface perspective
        //    Wider at near end (Y=14) than far end (Y=72, upper receiver join)
        shapeRenderer.setColor(0.28f, 0.32f, 0.42f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 60f, 14f, 52f, 72f);
        // Far-edge highlight: top surface faces upward toward camera
        shapeRenderer.setColor(0.40f, 0.46f, 0.58f, 1f);
        shapeRenderer.rect(centerX - 52f, 69f, 104f, 3f);
        // Near-edge shadow: underside curves away from camera
        shapeRenderer.setColor(0.18f, 0.21f, 0.29f, 1f);
        shapeRenderer.rect(centerX - 60f, 14f, 120f, 3f);
        // Mid-surface groove: lateral slot visible from above
        shapeRenderer.setColor(0.20f, 0.24f, 0.32f, 1f);
        shapeRenderer.rect(centerX - 56f, 42f, 112f, 2f);

        // 2. Energy coils — 4 bright cyan horizontal bands across body (seen from above)
        float[] coilYPositions = {22f, 32f, 44f, 56f};
        shapeRenderer.setColor(0.00f, 0.88f, 1.00f, 1f);
        for (float coilY : coilYPositions) {
            shapeRenderer.rect(centerX - 54f, coilY, 108f, 3f);
        }
        // Soft glow fringe above and below each coil
        shapeRenderer.setColor(0.00f, 0.62f, 0.90f, 0.50f);
        for (float coilY : coilYPositions) {
            shapeRenderer.rect(centerX - 54f, coilY - 1f, 108f, 1f);  // fringe below
            shapeRenderer.rect(centerX - 54f, coilY + 3f, 108f, 1f);  // fringe above
        }

        // 3. Power cell indicators — symmetric flanking panels on both sides of body.
        //    Housing outer edge: ±62px from centerX. Housing inner edge: ±52px from centerX.
        //    Charge bar outer edge: ±60px. Charge bar inner edge: ±54px. Width: 6px.
        //    Symmetry invariant: left rect x = centerX - outerEdge; right rect x = centerX + innerEdge.
        //    left housing:  x = CX-62, width=10  → spans CX-62..CX-52  (outer=-62, inner=-52)
        //    right housing: x = CX+52, width=10  → spans CX+52..CX+62  (inner=+52, outer=+62) ✓ symmetric
        //    left bars:     x = CX-60, width=6   → spans CX-60..CX-54  (outer=-60, inner=-54)
        //    right bars:    x = CX+54, width=6   → spans CX+54..CX+60  (inner=+54, outer=+60) ✓ symmetric
        shapeRenderer.setColor(0.16f, 0.20f, 0.28f, 1f);
        shapeRenderer.rect(centerX - 62f, 20f, 10f, 38f);   // left  housing  [CX-62 .. CX-52]
        shapeRenderer.rect(centerX + 52f, 20f, 10f, 38f);   // right housing  [CX+52 .. CX+62]
        shapeRenderer.setColor(0.00f, 0.72f, 1.00f, 0.95f);
        shapeRenderer.rect(centerX - 60f, 24f, 6f, 5f);     // left  bar 1    [CX-60 .. CX-54]
        shapeRenderer.rect(centerX - 60f, 31f, 6f, 5f);     // left  bar 2    [CX-60 .. CX-54]
        shapeRenderer.rect(centerX - 60f, 38f, 6f, 5f);     // left  bar 3    [CX-60 .. CX-54]
        shapeRenderer.rect(centerX - 60f, 45f, 6f, 5f);     // left  bar 4    [CX-60 .. CX-54]
        shapeRenderer.rect(centerX + 54f, 24f, 6f, 5f);     // right bar 1    [CX+54 .. CX+60]
        shapeRenderer.rect(centerX + 54f, 31f, 6f, 5f);     // right bar 2    [CX+54 .. CX+60]
        shapeRenderer.rect(centerX + 54f, 38f, 6f, 5f);     // right bar 3    [CX+54 .. CX+60]
        shapeRenderer.rect(centerX + 54f, 45f, 6f, 5f);     // right bar 4    [CX+54 .. CX+60]

        // 4. Upper receiver — stepped section between body and barrel
        shapeRenderer.setColor(0.26f, 0.30f, 0.40f, 1f);
        shapeRenderer.rect(centerX - 44f, 70f, 88f, 18f);
        shapeRenderer.setColor(0.38f, 0.44f, 0.56f, 1f);
        shapeRenderer.rect(centerX - 44f, 85f, 88f, 3f);    // far-edge bevel highlight
        shapeRenderer.setColor(0.16f, 0.18f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 44f, 70f, 88f, 3f);    // near-edge shadow

        // 5. Targeting scope — centred housing on upper receiver, visible from above
        shapeRenderer.setColor(0.16f, 0.19f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 18f, 72f, 36f, 16f);   // scope housing
        shapeRenderer.setColor(0.10f, 0.82f, 0.58f, 0.90f);
        shapeRenderer.rect(centerX - 14f, 74f, 28f, 12f);   // green lens surface
        shapeRenderer.setColor(0.40f, 1.00f, 0.75f, 0.55f);
        shapeRenderer.rect(centerX - 14f, 84f, 28f,  2f);   // lens far-edge highlight

        // 6. Barrel — tapered trapezoid viewed from above (wide near Y=86, narrow far Y=118)
        shapeRenderer.setColor(0.28f, 0.32f, 0.42f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 26f, 86f, 14f, 118f);
        shapeRenderer.setColor(0.38f, 0.44f, 0.56f, 1f);
        shapeRenderer.rect(centerX - 26f, 114f, 52f, 2f);   // barrel top-edge highlight
        shapeRenderer.setColor(0.16f, 0.18f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 20f, 100f, 40f, 2f);   // mid-barrel lateral groove

        // 7. Muzzle prongs — symmetric structural flanges flanking the emitter
        shapeRenderer.setColor(0.22f, 0.26f, 0.34f, 1f);
        shapeRenderer.rect(centerX - 26f, 112f, 8f, 10f);   // left prong
        shapeRenderer.rect(centerX + 18f, 112f, 8f, 10f);   // right prong

        // 8. Muzzle emitter — concentric ellipses deep-blue -> bright-cyan -> white-cyan -> white
        //    Energy weapon: emitter face replaces bore concept entirely
        shapeRenderer.setColor(0.08f, 0.52f, 1.00f, 0.30f);
        shapeRenderer.ellipse(centerX - 16f, 112f, 32f, 10f);  // under-glow halo (drawn first)
        shapeRenderer.setColor(0.18f, 0.22f, 0.30f, 1f);
        shapeRenderer.ellipse(centerX - 13f, 117f, 26f, 14f);  // dark outer housing ring
        shapeRenderer.setColor(0.08f, 0.52f, 1.00f, 0.95f);
        shapeRenderer.ellipse(centerX - 11f, 119f, 22f, 11f);  // outer deep-blue glow
        shapeRenderer.setColor(0.30f, 0.82f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX -  8f, 121f, 16f,  9f);  // mid bright-cyan ring
        shapeRenderer.setColor(0.75f, 0.97f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX -  5f, 123f, 10f,  7f);  // hot white-cyan core
        shapeRenderer.setColor(1.00f, 1.00f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX -  3f, 125f,  6f,  5f);  // hottest pinpoint
    }

    /**
     * Generates a break-action double-barrel shotgun sprite using ShapeRenderer into an
     * offscreen FrameBuffer.  Quake-1 style: camera sits slightly above-and-behind.
     * The grip/stock is NOT drawn — cut off below screen.
     *
     * Visually distinct from the pump-action Shotgun (sawn-off side-by-side double):
     *   - Two fat parallel barrel tubes with a continuous top sighting rib
     *   - Beefy steel receiver carrying the break-action hinge pin and top-lever
     *   - Warm walnut forestock band (with diamond checkering) wrapping the barrels
     *   - Twin bore openings at the muzzle (face-on, partly visible)
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (grip region — transparent, cut off)
     *   Y = 134 → top of canvas (barrel muzzles pointing toward the horizon)
     *
     * Layout zones:
     *   Y  0– 14  transparent       — wrist/grip cut off below screen
     *   Y 14– 40  walnut receiver wrist (warm brown, partially cut off)
     *   Y 38– 64  steel receiver block — break-action hinge pin + top-lever
     *   Y 58– 80  walnut forestock band wrapping the barrels (checkering)
     *   Y 64–132  twin barrel tubes — gunmetal, tapered, cylinder shading
     *   Y 114–132 twin bore openings + muzzle caps + front bead on top rib
     *
     * Full layer breakdown is documented on drawDoubleBarrelShotgunShape().
     */
    private static Texture generateDoubleBarrelShotgunTexture() {
        int canvasWidth  = WeaponConstants.DBL_SHOTGUN_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.DBL_SHOTGUN_CANVAS_HEIGHT;

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
     * Draws a sawn-off side-by-side double-barrel shotgun in Quake-1 first-person style,
     * redesigned from scratch to read unmistakably as a break-action twin-bore.
     *
     * The gun is viewed nearly face-on from slightly above (convergence factor 0.80): the
     * two barrels are angled toward the camera so both muzzle bores are partly visible. The
     * defining silhouette elements — two fat parallel barrel tubes, a continuous top rib, a
     * beefy steel receiver carrying the break-action hinge pin and top-lever, twin bores, and
     * a warm walnut forestock band wrapping the barrels — are all emphasised so the weapon
     * reads clearly as a double-barrel at HUD scale.
     *
     * Layout zones (Y-up, Y=0 = grip cut-off, Y=134 = muzzle):
     *   Y  0– 14  transparent       — wrist/grip cut off below screen
     *   Y 14– 40  walnut receiver wrist (warm brown, partially cut off, grain lines)
     *   Y 38– 64  steel receiver block — break-action hinge pin + top-lever
     *   Y 58– 80  walnut forestock band wrapping the barrels (warm brown, checkering)
     *   Y 64–132  twin barrel tubes  — tapered, gunmetal, cylinder shading per tube
     *   Y 80– 85  rear retaining band   (steel collar ring)
     *   Y 104–110 front retaining band  (steel collar ring near muzzle)
     *   Y 114–132 twin bore openings    + muzzle caps + front bead on the top rib
     *
     * Perspective convergence factor 0.80 (face-on). Symmetry reference (from centerX=96):
     *   Barrel outer edge:  base ±46 px → muzzle ±37 px  (46 × 0.80 = 36.8 ≈ 37)
     *   Barrel inner edge:  base  ±5 px → muzzle  ±4 px  ( 5 × 0.80 =  4.0)
     *   Centre gap channel: base 10 px  → muzzle  8 px wide
     *   Bore centre:        muzzle ±20 px
     */
    private static void drawDoubleBarrelShotgunShape(ShapeRenderer shapeRenderer, float centerX) {

        // Y=0..14 left transparent — wrist/grip cut off below screen (eyes above the gun).

        // 1. Walnut receiver wrist — warm brown stock root, partially cut off at screen bottom.
        shapeRenderer.setColor(0.42f, 0.22f, 0.08f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 40f, 14f, 34f, 40f);
        // Horizontal wood grain lines.
        shapeRenderer.setColor(0.34f, 0.16f, 0.05f, 1f);
        shapeRenderer.rect(centerX - 36f, 19f, 72f, 2f);    // grain line 1
        shapeRenderer.rect(centerX - 34f, 26f, 68f, 1f);    // grain line 2
        shapeRenderer.rect(centerX - 33f, 32f, 66f, 2f);    // grain line 3
        // Warm crown highlight along the top of the wrist where it catches light.
        shapeRenderer.setColor(0.54f, 0.30f, 0.12f, 1f);
        shapeRenderer.rect(centerX - 34f, 37f, 68f, 2f);    // wrist top highlight
        // Near-edge shadow on the wrist.
        shapeRenderer.setColor(0.22f, 0.10f, 0.03f, 1f);
        shapeRenderer.rect(centerX - 40f, 14f, 80f, 3f);    // wrist bottom shadow

        // 2. Steel receiver block — the heavy break-action body. Wider than the barrels so the
        //    twin-tube assembly visibly seats into it. Y=38..64.
        shapeRenderer.setColor(0.25f, 0.27f, 0.33f, 1f);
        shapeRenderer.rect(centerX - 50f, 38f, 100f, 26f);  // receiver block
        shapeRenderer.setColor(0.42f, 0.46f, 0.54f, 1f);
        shapeRenderer.rect(centerX - 50f, 61f, 100f, 3f);   // far-edge top highlight
        shapeRenderer.setColor(0.11f, 0.12f, 0.16f, 1f);
        shapeRenderer.rect(centerX - 50f, 38f, 100f, 3f);   // near-edge bottom shadow
        // Bevelled side cheeks — darker vertical flanks give the block volume.
        shapeRenderer.setColor(0.16f, 0.18f, 0.23f, 1f);
        shapeRenderer.rect(centerX - 50f, 41f, 5f, 20f);    // left  receiver cheek
        shapeRenderer.rect(centerX + 45f, 41f, 5f, 20f);    // right receiver cheek
        // Engraved side panels — a thin bright border on each flank (decorative).
        shapeRenderer.setColor(0.34f, 0.38f, 0.46f, 1f);
        shapeRenderer.rect(centerX - 44f, 44f, 16f, 14f);   // left  engraving panel
        shapeRenderer.rect(centerX + 28f, 44f, 16f, 14f);   // right engraving panel
        shapeRenderer.setColor(0.20f, 0.22f, 0.28f, 1f);
        shapeRenderer.rect(centerX - 42f, 46f, 12f, 10f);   // left  panel recess
        shapeRenderer.rect(centerX + 30f, 46f, 12f, 10f);   // right panel recess

        // 3. Break-action hinge pin — bright horizontal steel pin crossing the lower receiver,
        //    the joint the barrels pivot on. Strong twin-bore identifier.
        shapeRenderer.setColor(0.50f, 0.54f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 24f, 40f, 48f, 4f);    // hinge pin body
        shapeRenderer.setColor(0.70f, 0.74f, 0.80f, 1f);
        shapeRenderer.rect(centerX - 24f, 43f, 48f, 1f);    // hinge pin top shine
        shapeRenderer.setColor(0.16f, 0.18f, 0.23f, 1f);
        shapeRenderer.circle(centerX - 24f, 42f, 3f);       // left  pivot boss
        shapeRenderer.circle(centerX + 24f, 42f, 3f);       // right pivot boss
        shapeRenderer.setColor(0.46f, 0.50f, 0.58f, 1f);
        shapeRenderer.circle(centerX - 24f, 42f, 1.5f);     // left  boss shine
        shapeRenderer.circle(centerX + 24f, 42f, 1.5f);     // right boss shine

        // 4. Top-lever — the thumb lever that breaks the action open, centred on the receiver
        //    top as a raised tab. Centred for symmetry.
        shapeRenderer.setColor(0.30f, 0.33f, 0.40f, 1f);
        shapeRenderer.rect(centerX - 6f, 58f, 12f, 8f);     // top-lever housing
        shapeRenderer.setColor(0.50f, 0.54f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 6f, 63f, 12f, 3f);     // top-lever highlight
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        shapeRenderer.rect(centerX - 6f, 58f, 12f, 2f);     // top-lever base shadow

        // 5. Left barrel tube — perspective-tapered face-on (base Y=64, muzzle Y=132, factor 0.80).
        //    Outer: base CX-46, muzzle CX-37 (46×0.80=36.8). Inner: base CX-5, muzzle CX-4.
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 46f, centerX - 5f, 64f,
                                            centerX - 37f, centerX - 4f, 132f);
        // Outer-edge shadow strip (cylinder curves away on the far flank).
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 46f, centerX - 41f, 64f,
                                            centerX - 37f, centerX - 33f, 132f);
        // Crown highlight strip (top of cylinder faces camera, brightest).
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 32f, centerX - 20f, 64f,
                                            centerX - 26f, centerX - 16f, 132f);
        // Inner-edge shadow strip (cylinder curves inward toward the gap).
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 10f, centerX - 5f, 64f,
                                            centerX -  8f, centerX - 4f, 132f);

        // 6. Right barrel tube — mirror of left.
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 5f, centerX + 46f, 64f,
                                            centerX + 4f, centerX + 37f, 132f);
        // Inner-edge shadow.
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX +  5f, centerX + 10f, 64f,
                                            centerX +  4f, centerX +  8f, 132f);
        // Crown highlight.
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 20f, centerX + 32f, 64f,
                                            centerX + 16f, centerX + 26f, 132f);
        // Outer-edge shadow.
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 41f, centerX + 46f, 64f,
                                            centerX + 33f, centerX + 37f, 132f);

        // 7. Centre gap channel between barrels — tapered deep-shadow seam (10px base -> 8px muzzle).
        shapeRenderer.setColor(0.06f, 0.07f, 0.09f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 5f, centerX + 5f, 64f,
                                            centerX - 4f, centerX + 4f, 132f);
        // Top rib — the continuous sighting rail bridging the gap, raised over the seam.
        shapeRenderer.setColor(0.30f, 0.33f, 0.40f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 4f, centerX + 4f, 80f,
                                            centerX - 3f, centerX + 3f, 124f);
        shapeRenderer.setColor(0.46f, 0.50f, 0.58f, 1f);
        shapeRenderer.rect(centerX - 1f, 82f, 2f, 40f);     // rib crest highlight

        // 8. Walnut forestock band — warm wood wrapping the rear barrels (the hand-guard).
        //    A strong twin-barrel identifier; redrawn over the tube bases.
        shapeRenderer.setColor(0.42f, 0.22f, 0.08f, 1f);
        shapeRenderer.rect(centerX - 44f, 58f, 88f, 22f);   // forestock block
        shapeRenderer.setColor(0.54f, 0.30f, 0.12f, 1f);
        shapeRenderer.rect(centerX - 44f, 77f, 88f, 3f);    // forestock top highlight
        shapeRenderer.setColor(0.28f, 0.13f, 0.04f, 1f);
        shapeRenderer.rect(centerX - 44f, 58f, 88f, 2f);    // forestock bottom shadow
        // Diamond checkering — two rows of small marks for grip texture (symmetric).
        shapeRenderer.setColor(0.30f, 0.14f, 0.05f, 1f);
        for (int checkerIndex = 0; checkerIndex < 5; checkerIndex++) {
            float checkerOffsetX = 6f + checkerIndex * 8f;
            shapeRenderer.rect(centerX - checkerOffsetX - 2f, 64f, 3f, 3f);  // left  row low
            shapeRenderer.rect(centerX + checkerOffsetX - 1f, 64f, 3f, 3f);  // right row low
            shapeRenderer.rect(centerX - checkerOffsetX - 2f, 70f, 3f, 3f);  // left  row high
            shapeRenderer.rect(centerX + checkerOffsetX - 1f, 70f, 3f, 3f);  // right row high
        }
        // Re-draw the top rib over the forestock so the rail stays continuous and visible.
        shapeRenderer.setColor(0.30f, 0.33f, 0.40f, 1f);
        shapeRenderer.rect(centerX - 4f, 58f, 8f, 22f);     // rib through forestock
        shapeRenderer.setColor(0.46f, 0.50f, 0.58f, 1f);
        shapeRenderer.rect(centerX - 1f, 58f, 2f, 22f);     // rib crest through forestock

        // 9. Rear retaining band — steel collar clamping the barrels above the forestock.
        //    At Y=82 mid-band: scale = 1.0 - (1-0.80) × (82-64)/(132-64) = 0.947 -> outer ±44px.
        shapeRenderer.setColor(0.30f, 0.33f, 0.40f, 1f);
        shapeRenderer.rect(centerX - 44f, 80f, 40f, 5f);    // left  rear band segment
        shapeRenderer.rect(centerX +  4f, 80f, 40f, 5f);    // right rear band segment
        shapeRenderer.setColor(0.50f, 0.54f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 44f, 83f, 40f, 2f);    // left  rear band highlight
        shapeRenderer.rect(centerX +  4f, 83f, 40f, 2f);    // right rear band highlight

        // 10. Front retaining band — steel collar near the muzzle.
        //     At Y=107 mid-band: scale = 1.0 - (1-0.80) × (107-64)/(132-64) = 0.873 -> outer ±40px.
        shapeRenderer.setColor(0.30f, 0.33f, 0.40f, 1f);
        shapeRenderer.rect(centerX - 40f, 104f, 35f, 6f);   // left  front band segment
        shapeRenderer.rect(centerX +  5f, 104f, 35f, 6f);   // right front band segment
        shapeRenderer.setColor(0.50f, 0.54f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 40f, 108f, 35f, 2f);   // left  front band highlight
        shapeRenderer.rect(centerX +  5f, 108f, 35f, 2f);   // right front band highlight

        // 11. Muzzle caps — 2px bright steel rim at barrel tip Y (ALL weapons require this).
        //     At muzzle Y=130: outer edge CX-37, inner edge CX-4 -> width 33px left barrel.
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 37f, 130f, 33f, 2f);   // left  barrel muzzle cap
        shapeRenderer.rect(centerX +  4f, 130f, 33f, 2f);   // right barrel muzzle cap

        // 12. Twin bore openings — FACE-ON view, partly visible. 18x16px ellipse per barrel.
        //     ellipse(x,y,w,h) takes the bounding-box BOTTOM-LEFT, so an 18px-wide ellipse
        //     centred at CX-20 has left edge CX-20-9 = CX-29; centred at CX+20 has left edge
        //     CX+20-9 = CX+11. Thus CX-29 (left) and CX+11 (right) are symmetric about centerX
        //     (centres CX-20 / CX+20, the midpoints of the CX-37..CX-4 muzzle span). The 14px
        //     darkness and rim-shine ellipses follow the same centred-bounding-box convention.
        //     Steel rim ring -> near-black bore -> top-rim crescent shine.
        shapeRenderer.setColor(0.18f, 0.20f, 0.24f, 1f);
        shapeRenderer.ellipse(centerX - 29f, 114f, 18f, 16f);  // left  barrel face rim
        shapeRenderer.ellipse(centerX + 11f, 114f, 18f, 16f);  // right barrel face rim
        shapeRenderer.setColor(0.05f, 0.05f, 0.06f, 0.95f);
        shapeRenderer.ellipse(centerX - 27f, 116f, 14f, 12f);  // left  bore darkness
        shapeRenderer.ellipse(centerX + 13f, 116f, 14f, 12f);  // right bore darkness
        // Bore rim shine crescent at the very top of each bore.
        shapeRenderer.setColor(0.44f, 0.48f, 0.58f, 0.60f);
        shapeRenderer.ellipse(centerX - 27f, 124f, 14f, 4f);   // left  bore top-rim shine
        shapeRenderer.ellipse(centerX + 13f, 124f, 14f, 4f);   // right bore top-rim shine

        // 13. Front bead sight — small bright knob on the top rib at the muzzle end, centred.
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 2f, 128f, 4f, 4f);     // bead post
        shapeRenderer.setColor(0.66f, 0.70f, 0.78f, 1f);
        shapeRenderer.rect(centerX - 1f, 131f, 2f, 2f);     // bright bead tip
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
     * grip and stock are cut off below the screen. A single barrel fitted with a
     * ventilated cooling shroud points away from the viewer; the bore face is invisible.
     *
     * Layout zones:
     *   Y  0– 14  transparent — grip cut off below screen
     *   Y 14– 60  receiver body (ejection port, loading gate, charging handle)
     *   Y 58– 78  pump fore-end slide with grip ridges
     *   Y 78–120  ventilated barrel shroud + central vent rib
     *   Y 118–120 muzzle rim cap
     *
     * Full layer breakdown is documented on drawShotgunShape().
     */
    private static Texture generateShotgunTexture() {
        int canvasWidth  = WeaponConstants.SHOTGUN_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.SHOTGUN_CANVAS_HEIGHT;

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
     * Draws a detailed pump-action combat shotgun from the Quake-1 top-down first-person
     * perspective. The player's eye sits above and slightly behind the weapon; the barrel
     * points AWAY toward the horizon, so the bore face is invisible — only the metallic
     * muzzle rim cap is drawn.
     *
     * This is a SINGLE-barrel pump shotgun fitted with a ventilated barrel shroud (the
     * defining silhouette element — a row of cooling slots down each flank). That shroud,
     * the wide ribbed pump fore-end, the side-mounted ejection port and the charging-handle
     * knob make it read unmistakably as a pump-action and keep it visually distinct from the
     * twin-bore DoubleBarrelShotgun.
     *
     * All coordinates in canvas pixel space; Y=0 = near/grip (transparent, cut off below
     * screen), Y=134 = far/muzzle. centerX = 96 keeps the sprite perfectly symmetric on the
     * 192-wide canvas.
     *
     * Barrel-assembly convergence factor 0.78: offset_at_muzzle = offset_at_base × 0.78.
     *
     * Layer order (back-to-front):
     *   1. Receiver body            Y=14..60  — wide gunmetal trapezoid, top surface
     *   2. Receiver detailing       Y=14..60  — edge shading, ejection port, loading port, bolt
     *   3. Charging-handle knob     Y=44..52  — bright steel knob on the right receiver flank
     *   4. Pump fore-end slide      Y=58..78  — wide ribbed sliding grip
     *   5. Barrel shroud body       Y=78..120 — perspective-tapered gunmetal tube
     *   6. Shroud shading           Y=78..120 — outer-edge shadows + crown highlight
     *   7. Ventilation slots        Y=86..116 — symmetric dark cooling holes down both flanks
     *   8. Ventilated sighting rib  Y=78..118 — raised centre rail with notch gaps
     *   9. Barrel retaining band    Y=98..104 — steel ring near the muzzle
     *  10. Muzzle cap               Y=118..120 — bright steel rim at the barrel tip (no bore)
     *  11. Front bead + rear sight  Y=66..124 — bead post at muzzle, notch sight on receiver
     */
    private static void drawShotgunShape(ShapeRenderer shapeRenderer, float centerX) {

        // Y=0..14 left transparent — grip cut off below screen (first-person: eyes above gun)

        // 1. Receiver body — wide gunmetal trapezoid, wider at near end (closer to viewer)
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 46f, 14f, 38f, 60f);

        // 2. Receiver detailing — edge shading, ejection port, loading port, top groove
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 38f, 57f, 76f, 3f);    // far-edge top highlight
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(centerX - 46f, 14f, 92f, 3f);    // near-edge bottom shadow
        shapeRenderer.setColor(0.16f, 0.18f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 42f, 34f, 84f, 2f);    // mid-body groove
        // Ejection port — dark recessed slot on the right flank with a bright lower lip
        shapeRenderer.setColor(0.06f, 0.07f, 0.09f, 1f);
        shapeRenderer.rect(centerX + 12f, 40f, 26f, 12f);   // ejection port cavity
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX + 12f, 40f, 26f, 1f);    // port lower lip shine
        // Loading port — shell gate cut into the underside, near-centre dark slot
        shapeRenderer.setColor(0.08f, 0.08f, 0.10f, 1f);
        shapeRenderer.rect(centerX - 14f, 18f, 22f, 6f);    // loading gate slot
        shapeRenderer.setColor(0.34f, 0.20f, 0.10f, 1f);
        shapeRenderer.rect(centerX - 12f, 19f, 18f, 3f);    // brass shell head peeking out
        // Orange-red hazard accent tabs on both receiver flanks (combat marking).
        //    Left  tab spans CX-40..CX-36; right tab spans CX+36..CX+40 — mirrored about centerX.
        shapeRenderer.setColor(0.80f, 0.30f, 0.05f, 1f);
        shapeRenderer.rect(centerX - 40f, 44f, 4f, 12f);    // left  hazard tab
        shapeRenderer.rect(centerX + 36f, 44f, 4f, 12f);    // right hazard tab
        shapeRenderer.setColor(0.95f, 0.55f, 0.15f, 1f);
        shapeRenderer.rect(centerX - 40f, 53f, 4f, 2f);     // left  hazard tab highlight
        shapeRenderer.rect(centerX + 36f, 53f, 4f, 2f);     // right hazard tab highlight

        // 3. Charging-handle knob — bright steel cylinder protruding from the right flank
        shapeRenderer.setColor(0.16f, 0.18f, 0.22f, 1f);
        shapeRenderer.rect(centerX + 34f, 44f, 8f, 8f);     // knob shadow base
        shapeRenderer.setColor(0.52f, 0.56f, 0.62f, 1f);
        shapeRenderer.rect(centerX + 35f, 45f, 6f, 6f);     // knob body
        shapeRenderer.setColor(0.74f, 0.78f, 0.84f, 1f);
        shapeRenderer.rect(centerX + 36f, 49f, 4f, 2f);     // knob top highlight

        // 4. Pump fore-end slide — wide ribbed sliding grip wrapping the barrel (Y=58..78)
        shapeRenderer.setColor(0.26f, 0.29f, 0.34f, 1f);
        shapeRenderer.rect(centerX - 38f, 58f, 76f, 20f);
        shapeRenderer.setColor(0.44f, 0.48f, 0.54f, 1f);
        shapeRenderer.rect(centerX - 38f, 76f, 76f,  2f);   // far-edge highlight
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(centerX - 38f, 58f, 76f,  2f);   // near-edge shadow
        // Pump grip ridges — horizontal grooves around the circumference (slide along barrel)
        shapeRenderer.setColor(0.15f, 0.17f, 0.20f, 1f);
        shapeRenderer.rect(centerX - 36f, 62f, 72f, 2f);    // ridge groove 1
        shapeRenderer.rect(centerX - 36f, 67f, 72f, 2f);    // ridge groove 2
        shapeRenderer.rect(centerX - 36f, 72f, 72f, 2f);    // ridge groove 3
        shapeRenderer.setColor(0.38f, 0.42f, 0.48f, 1f);
        shapeRenderer.rect(centerX - 36f, 64f, 72f, 1f);    // ridge crest 1
        shapeRenderer.rect(centerX - 36f, 69f, 72f, 1f);    // ridge crest 2

        // 5. Barrel shroud body — perspective-tapered gunmetal tube (base Y=78, muzzle Y=120,
        //    factor 0.78). Half-width 30px at base → 24px at muzzle.
        shapeRenderer.setColor(0.23f, 0.25f, 0.29f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 30f, 78f, 24f, 120f);

        // 6. Shroud shading — outer-edge shadows curve away; central crown faces the camera
        shapeRenderer.setColor(0.11f, 0.12f, 0.15f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 30f, centerX - 25f, 78f,
                                            centerX - 24f, centerX - 20f, 120f);  // left edge shadow
        drawGeneralTrapezoid(shapeRenderer, centerX + 25f, centerX + 30f, 78f,
                                            centerX + 20f, centerX + 24f, 120f);  // right edge shadow
        // Broad crown highlight (top of cylinder faces the camera): 16px base -> 12px muzzle.
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 8f, centerX + 8f, 78f,
                                            centerX - 6f, centerX + 6f, 120f);    // crown highlight
        // Brightest crown spine on the camera-facing apex of the cylinder.
        shapeRenderer.setColor(0.50f, 0.54f, 0.60f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 5f, centerX + 5f, 78f,
                                            centerX - 4f, centerX + 4f, 120f);    // crown spine

        // 7. Ventilation slots — the shroud's defining feature. Symmetric pairs of dark
        //    cooling holes marching up both flanks; left x = CX-22, right x = CX+14 (width 8 →
        //    centres CX-18 / CX+18, perfectly mirrored). Each slot has a thin top-rim shine.
        float[] ventSlotYPositions = {86f, 94f, 102f, 110f};
        for (float ventY : ventSlotYPositions) {
            shapeRenderer.setColor(0.05f, 0.05f, 0.06f, 1f);
            shapeRenderer.ellipse(centerX - 22f, ventY, 8f, 5f);   // left vent slot
            shapeRenderer.ellipse(centerX + 14f, ventY, 8f, 5f);   // right vent slot
            shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 0.55f);
            shapeRenderer.rect(centerX - 21f, ventY + 4f, 6f, 1f); // left slot rim shine
            shapeRenderer.rect(centerX + 15f, ventY + 4f, 6f, 1f); // right slot rim shine
        }
        // Heat-shield seam lines bridging the two slot columns (welded shroud read),
        //    sitting between the slot rows so they never overdraw the cooling holes.
        shapeRenderer.setColor(0.14f, 0.15f, 0.18f, 1f);
        shapeRenderer.rect(centerX - 13f, 92f, 26f, 1f);    // upper seam between slot rows
        shapeRenderer.rect(centerX - 13f, 100f, 26f, 1f);   // mid seam
        shapeRenderer.rect(centerX - 13f, 108f, 26f, 1f);   // lower seam

        // 8. Ventilated sighting rib — raised centre rail running the barrel length with
        //    small notch gaps (the classic vent rib). Tapered to match the shroud convergence.
        shapeRenderer.setColor(0.18f, 0.20f, 0.24f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 4f, centerX + 4f, 78f,
                                            centerX - 3f, centerX + 3f, 118f);
        shapeRenderer.setColor(0.34f, 0.38f, 0.44f, 1f);
        shapeRenderer.rect(centerX - 1f, 80f, 2f, 36f);     // rib crest highlight
        shapeRenderer.setColor(0.08f, 0.09f, 0.11f, 1f);
        shapeRenderer.rect(centerX - 4f, 86f, 8f, 2f);      // rib notch gap 1
        shapeRenderer.rect(centerX - 4f, 98f, 8f, 2f);      // rib notch gap 2
        shapeRenderer.rect(centerX - 4f, 110f, 8f, 2f);     // rib notch gap 3

        // 9. Barrel retaining band — tapered steel ring clamping the shroud near the muzzle.
        //    At Y=101 mid-band: scale = 1.0 - (1-0.78) × (101-78)/(120-78) = 0.88 → ±26px.
        shapeRenderer.setColor(0.32f, 0.35f, 0.41f, 1f);
        shapeRenderer.rect(centerX - 26f, 98f, 52f, 6f);    // band ring
        shapeRenderer.setColor(0.48f, 0.52f, 0.60f, 1f);
        shapeRenderer.rect(centerX - 26f, 103f, 52f, 1f);   // band top highlight
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        shapeRenderer.rect(centerX - 26f, 98f, 52f, 1f);    // band bottom shadow

        // 10. Muzzle cap — bright steel rim at the barrel tip. Barrel points away, so this is
        //     the circumference ring seen edge-on, NOT a bore face. Width = muzzle width 48px.
        shapeRenderer.setColor(0.42f, 0.46f, 0.54f, 1f);
        shapeRenderer.rect(centerX - 24f, 118f, 48f, 2f);   // muzzle rim cap
        shapeRenderer.setColor(0.20f, 0.22f, 0.27f, 1f);
        shapeRenderer.rect(centerX - 20f, 116f, 40f, 2f);   // recessed crown shadow behind rim

        // 11. Front bead sight + rear notch sight.
        //     Front bead — small bright knob at the muzzle end of the vent rib.
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 2f, 118f, 4f, 4f);     // bead base post
        shapeRenderer.setColor(0.66f, 0.70f, 0.78f, 1f);
        shapeRenderer.rect(centerX - 1f, 121f, 2f, 3f);     // bright bead tip
        //     Rear notch sight — dark U-slot flanked by bright wings on the receiver top.
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 10f, 52f, 8f, 4f);     // left sight wing
        shapeRenderer.rect(centerX +  2f, 52f, 8f, 4f);     // right sight wing
        shapeRenderer.setColor(0.08f, 0.09f, 0.11f, 1f);
        shapeRenderer.rect(centerX -  2f, 52f, 4f, 4f);     // notch slot (dark gap)
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
        int canvasWidth  = WeaponConstants.CHAINGUN_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.CHAINGUN_CANVAS_HEIGHT;

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

        // 8. Cooling vents — two rows of dark rectangular slots cut into the shroud collar
        //    (Y=72..80), showing the ventilation openings visible from above.
        shapeRenderer.setColor(0.12f, 0.13f, 0.16f, 1f);
        shapeRenderer.rect(centerX - 30f, 72f, 5f, 6f);    // left vent 1
        shapeRenderer.rect(centerX - 22f, 72f, 5f, 6f);    // left vent 2
        shapeRenderer.rect(centerX + 17f, 72f, 5f, 6f);    // right vent 1
        shapeRenderer.rect(centerX + 25f, 72f, 5f, 6f);    // right vent 2

        // 9. Carry handle — an arched loop structure sitting atop the motor drum housing
        //    (Y=60..70), centered. Read as two thick uprights connected by a crossbar.
        shapeRenderer.setColor(0.24f, 0.26f, 0.32f, 1f);
        shapeRenderer.rect(centerX - 14f, 58f, 5f, 10f);   // left handle upright
        shapeRenderer.rect(centerX +  9f, 58f, 5f, 10f);   // right handle upright
        shapeRenderer.rect(centerX - 14f, 65f, 28f,  3f);  // handle crossbar top
        shapeRenderer.setColor(0.38f, 0.42f, 0.50f, 1f);
        shapeRenderer.rect(centerX - 14f, 67f, 28f,  1f);  // crossbar top highlight
        shapeRenderer.setColor(0.14f, 0.15f, 0.18f, 1f);
        shapeRenderer.rect(centerX - 14f, 58f, 28f,  2f);  // handle base shadow

        // 10. Rotation indicator marks on motor drum — three small bright dots arranged
        //     symmetrically on the drum ring, suggesting a rotating barrel assembly.
        shapeRenderer.setColor(0.58f, 0.40f, 0.14f, 1f);
        shapeRenderer.rect(centerX - 2f, 52f, 4f, 4f);     // center marker
        shapeRenderer.rect(centerX - 16f, 54f, 3f, 3f);    // left marker
        shapeRenderer.rect(centerX + 13f, 54f, 3f, 3f);    // right marker
    }

    /**
     * Generates a railgun sprite using ShapeRenderer into an offscreen FrameBuffer.
     * Quake-1 style top-down perspective: camera slightly above and behind the weapon.
     * The grip is NOT drawn — cut off below screen edge (Y=0..14 transparent).
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (grip region — transparent, cut off)
     *   Y = 134 → top of canvas (muzzle end, pointing toward horizon)
     *
     * Concept — "Tesla Coil Lance": a high-tech electromagnetic railgun dominated by a
     * prominent toroidal capacitor / coil bank glowing electric cyan, flanked by stepped
     * heat-sink fins, with two PRONOUNCED parallel accelerator rails running the full
     * length to a charged emitter. Palette is colder and whiter than the plasma rifle —
     * steel-blue body, ice-white edges, electric cyan coil windings.
     *
     * Layout zones:
     *   Y  0– 14  transparent — grip cut off below screen
     *   Y 14– 64  receiver body — wide steel-blue block (~86px)
     *   Y 18– 30  heat-sink fins — stepped dark vertical ribs along receiver flanks
     *   Y 26– 58  coil / capacitor bank — three stacked cyan toroidal windings at center
     *   Y 58– 70  emitter manifold — collar where the rails leave the coil bank
     *   Y 64–124  twin accelerator rails — heavy tapered bars converging 0.65 factor
     *   Y 80,98,114 — energized cross-braces (cyan-lit rungs) connecting the rails
     *   Y 118–124 charged emitter — layered cyan/white energy node between rail tips
     *   Y 124     muzzle caps — 2px bright steel band (NO bore ellipse)
     *
     * View mode: TOP-DOWN, convergence ~0.65, muzzle cap (no bore ellipse).
     */
    private static Texture generateRailgunTexture() {
        int canvasWidth  = WeaponConstants.RAILGUN_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.RAILGUN_CANVAS_HEIGHT;

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
        drawRailgunShape(temporaryShapeRenderer, canvasWidth / 2f);
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
     * Draws a railgun in Quake-1 top-down first-person perspective.
     *
     * Identity silhouette — "Tesla Coil Lance": a heavy electromagnetic rifle whose
     * receiver is dominated by a glowing toroidal coil / capacitor bank (three stacked
     * cyan windings), flanked by stepped heat-sink fins. Two PRONOUNCED parallel
     * accelerator rails leave the coil bank and run the full length to a charged emitter
     * node. The rails ARE the barrel — no enclosing shroud — giving the unmistakable
     * railgun silhouette. Palette is colder and whiter than the plasma rifle: steel-blue
     * body, ice-white edges, electric cyan windings.
     *
     * Top-down view, convergence factor 0.65: rails point AWAY from camera.
     * Bore invisible — muzzle cap only, no bore ellipse.
     *
     * Rail layout (offsets from centerX=96, taper factor 0.65):
     *   Left  rail: base CX-14..CX-6  (8px) → muzzle CX-9..CX-4  (factor 0.65)
     *   Right rail: base CX+6..CX+14  (8px) → muzzle CX+4..CX+9
     *   Gap between rails at base: 12px (CX-6 to CX+6) → muzzle 8px (CX-4 to CX+4)
     *
     * Layer order (back-to-front):
     *   1. Receiver body          Y=14..64   — wide steel-blue block
     *   2. Receiver edge shading  Y=14..64   — far-edge highlight / near-edge shadow
     *   3. Heat-sink fins         Y=18..30   — stepped dark vertical ribs on each flank
     *   4. Coil / capacitor bank  Y=26..58   — three stacked cyan toroidal windings
     *   5. Emitter manifold       Y=58..70   — collar where rails leave the coil bank
     *   6. Left accelerator rail  Y=64..124  — heavy perspective-tapered bar + cyan edge
     *   7. Right accelerator rail Y=64..124  — mirror of left rail
     *   8. Rail gap shadow        Y=64..124  — dark channel between rails
     *   9. Energized cross-braces Y=80,98,114 — cyan-lit rungs spanning rail-to-rail
     *  10. Charged emitter node   Y=116..124 — layered cyan/white energy node
     *  11. Muzzle caps            Y=124..126 — 2px bright steel rim (NO bore ellipse)
     */
    private static void drawRailgunShape(ShapeRenderer shapeRenderer, float centerX) {

        // Y=0..14 left transparent — grip cut off below screen

        // 1. Receiver body — wide steel-blue block, top-surface perspective (~86px base).
        shapeRenderer.setColor(0.26f, 0.30f, 0.40f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 43f, 14f, 37f, 64f);

        // 2. Receiver edge shading — far edge brighter (top surface faces camera),
        //    near edge in shadow.
        shapeRenderer.setColor(0.44f, 0.50f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 37f, 61f, 74f, 3f);     // far-edge top highlight
        shapeRenderer.setColor(0.14f, 0.16f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 43f, 14f, 86f, 3f);     // near-edge bottom shadow

        // 3. Heat-sink fins — stepped dark vertical ribs on each receiver flank (Y=18..30).
        //    Read as cooling ridges venting the coil bank's heat. Four ribs per side.
        shapeRenderer.setColor(0.16f, 0.18f, 0.26f, 1f);
        for (int finIndex = 0; finIndex < 4; finIndex++) {
            float finOffsetX = 22f + finIndex * 5f;          // 22,27,32,37 px from center
            shapeRenderer.rect(centerX - finOffsetX, 18f, 3f, 12f);  // left rib
            shapeRenderer.rect(centerX + finOffsetX - 3f, 18f, 3f, 12f); // right rib
        }
        // Fin crest highlights — thin bright top edge selling raised ridges
        shapeRenderer.setColor(0.40f, 0.46f, 0.58f, 1f);
        for (int finIndex = 0; finIndex < 4; finIndex++) {
            float finOffsetX = 22f + finIndex * 5f;
            shapeRenderer.rect(centerX - finOffsetX, 28f, 3f, 2f);
            shapeRenderer.rect(centerX + finOffsetX - 3f, 28f, 3f, 2f);
        }

        // 4. Coil / capacitor bank — three stacked toroidal windings glowing electric cyan,
        //    set into the receiver center (Y=26..58, ~40px wide). This is the signature
        //    feature. Each torus = dark housing band + cyan winding + ice-white crest.
        //    Static sprite shows the idle (dim-charged) state; runtime charge colouring
        //    is layered separately.
        shapeRenderer.setColor(0.14f, 0.18f, 0.28f, 1f);
        shapeRenderer.rect(centerX - 20f, 26f, 40f, 32f);    // coil bank housing backing
        // Three toroidal windings drawn bottom-to-top
        for (int coilIndex = 0; coilIndex < 3; coilIndex++) {
            float coilBaseY = 28f + coilIndex * 10f;         // 28, 38, 48
            // Dark separating band below each torus
            shapeRenderer.setColor(0.08f, 0.12f, 0.20f, 1f);
            shapeRenderer.rect(centerX - 18f, coilBaseY, 36f, 2f);
            // Cyan winding body
            shapeRenderer.setColor(0.10f, 0.62f, 0.92f, 1f);
            shapeRenderer.rect(centerX - 18f, coilBaseY + 2f, 36f, 5f);
            // Ice-white crest highlight (top of torus catching light)
            shapeRenderer.setColor(0.78f, 0.95f, 1.00f, 1f);
            shapeRenderer.rect(centerX - 18f, coilBaseY + 6f, 36f, 1f);
        }
        // Coil bank side rails — vertical cyan glow strips binding the windings together
        shapeRenderer.setColor(0.20f, 0.78f, 1.00f, 0.85f);
        shapeRenderer.rect(centerX - 20f, 28f, 2f, 30f);     // left binding strip
        shapeRenderer.rect(centerX + 18f, 28f, 2f, 30f);     // right binding strip

        // 5. Emitter manifold — collar where the two rails leave the coil bank (Y=58..70).
        //    A trapezoidal step narrowing from receiver width down to the rail span.
        shapeRenderer.setColor(0.30f, 0.34f, 0.44f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 24f, 58f, 16f, 70f);
        shapeRenderer.setColor(0.46f, 0.52f, 0.64f, 1f);
        shapeRenderer.rect(centerX - 16f, 68f, 32f, 2f);     // manifold far-edge highlight

        // 6. Left accelerator rail — heavy perspective-tapered gunmetal bar with cyan
        //    top edge. Base Y=64: outer CX-14, inner CX-6 (8px). Muzzle Y=124: outer
        //    CX-9, inner CX-4 (0.65 factor: 14*0.65=9.1~9, 6*0.65=3.9~4).
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 14f, centerX - 6f, 64f,
                                            centerX -  9f, centerX - 4f, 124f);
        // Cyan top-edge highlight strip (runs full rail length, tapered with same factor)
        shapeRenderer.setColor(0.20f, 0.80f, 1.00f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 14f, centerX - 12f, 64f,
                                            centerX -  9f, centerX -  8f, 124f);
        // Dark inner-edge shadow strip
        shapeRenderer.setColor(0.22f, 0.25f, 0.32f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX -  7f, centerX - 6f, 64f,
                                            centerX -  5f, centerX - 4f, 124f);

        // 7. Right accelerator rail — mirror of left
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 6f, centerX + 14f, 64f,
                                            centerX + 4f, centerX +  9f, 124f);
        // Cyan top-edge highlight
        shapeRenderer.setColor(0.20f, 0.80f, 1.00f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX + 12f, centerX + 14f, 64f,
                                            centerX +  8f, centerX +  9f, 124f);
        // Dark inner-edge shadow
        shapeRenderer.setColor(0.22f, 0.25f, 0.32f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX +  6f, centerX + 7f, 64f,
                                            centerX +  4f, centerX + 5f, 124f);

        // 8. Rail gap shadow — dark channel between the two rails.
        //    Base: CX-6 to CX+6 (12px), Muzzle: CX-4 to CX+4 (8px).
        shapeRenderer.setColor(0.05f, 0.06f, 0.09f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 6f, centerX + 6f, 64f,
                                            centerX - 4f, centerX + 4f, 124f);

        // 9. Energized cross-braces — cyan-lit rungs connecting the two rails at three
        //    intervals. Each = dark steel rung + bright cyan energized top edge,
        //    width interpolated to the tapering rail span at that Y.
        //    Span half-width at Y: 14*(1 - (Y-64)/60 * 0.357)  (14→9 over rail length).
        //    Y=80 → 13px;  Y=98 → 11px;  Y=114 → 10px (each side).
        shapeRenderer.setColor(0.34f, 0.38f, 0.46f, 1f);
        shapeRenderer.rect(centerX - 13f, 80f, 26f, 3f);     // lower brace
        shapeRenderer.rect(centerX - 11f, 98f, 22f, 3f);     // middle brace
        shapeRenderer.rect(centerX - 10f, 114f, 20f, 2f);    // upper brace
        // Cyan energized top edges
        shapeRenderer.setColor(0.18f, 0.80f, 1.00f, 0.90f);
        shapeRenderer.rect(centerX - 13f, 82f, 26f, 1f);
        shapeRenderer.rect(centerX - 11f, 100f, 22f, 1f);
        shapeRenderer.rect(centerX - 10f, 115f, 20f, 1f);

        // 10. Charged emitter node — layered energy node between the rail tips at muzzle.
        //     Concentric: dark housing ring → outer cyan glow → bright white-cyan core.
        shapeRenderer.setColor(0.12f, 0.16f, 0.26f, 1f);
        shapeRenderer.ellipse(centerX - 6f, 116f, 12f, 8f);  // dark housing ring
        shapeRenderer.setColor(0.16f, 0.70f, 1.00f, 0.92f);
        shapeRenderer.ellipse(centerX - 4f, 117f, 8f, 6f);   // outer cyan glow
        shapeRenderer.setColor(0.85f, 0.97f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX - 2f, 119f, 4f, 4f);   // bright white-cyan core

        // 11. Muzzle caps — 2px bright steel band at rail tips (Y=124).
        //     Left rail muzzle width: outer CX-9 to inner CX-4 = 5px.
        //     Right rail muzzle width: inner CX+4 to outer CX+9 = 5px.
        //     NO bore ellipse: top-down view, rail tips face away, bore invisible.
        shapeRenderer.setColor(0.44f, 0.49f, 0.58f, 1f);
        shapeRenderer.rect(centerX - 9f, 124f, 5f, 2f);      // left rail muzzle cap
        shapeRenderer.rect(centerX + 4f, 124f, 5f, 2f);      // right rail muzzle cap
    }

    /**
     * Generates an incinerator sprite using ShapeRenderer into an offscreen FrameBuffer.
     * Quake-1 style top-down perspective: camera slightly above and behind the weapon.
     * The grip is NOT drawn — cut off below screen edge (Y=0..14 transparent).
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (grip region — transparent, cut off)
     *   Y = 134 → top of canvas (nozzle tip, pointing toward horizon)
     *
     * Layout zones:
     *   Y  0– 14  transparent — grip cut off below screen
     *   Y 14– 60  housing body — wide industrial dark gunmetal block
     *   Y 16– 50  fuel canister detail — centered rust-red tank with hazard band
     *   Y 60–120  nozzle tube — symmetric trapezoid, tapers 0.65 from base to muzzle
     *   Y 116–120 igniter ring — bright steel band around nozzle tip
     *   Y 120–128 pilot flame — tiny orange-yellow triangle at nozzle tip
     *   Y 120     muzzle cap — 2px bright steel band (NO bore ellipse)
     *
     * View mode: TOP-DOWN, convergence ~0.65, muzzle CAP (no bore ellipse).
     */
    private static Texture generateIncineratorTexture() {
        int canvasWidth  = WeaponConstants.FLAME_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.FLAME_CANVAS_HEIGHT;

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
        drawIncineratorShape(temporaryShapeRenderer, canvasWidth / 2f);
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
     * Draws an incinerator in Quake-1 top-down first-person perspective.
     *
     * Industrial repurposed pest-control look: wide housing body, centered fuel canister
     * with hazard band, a wide nozzle tube tapering toward the muzzle, and a live pilot
     * flame at the tip signalling the weapon is always ready to burn.
     *
     * Top-down view, convergence factor 0.65: barrel points AWAY from camera.
     * Bore invisible — muzzle cap only (2px bright steel), no bore ellipse.
     *
     * Nozzle tube layout (offsets from centerX=96, factor 0.65):
     *   Base  Y=60: half-width 16px  → left CX-16, right CX+16
     *   Muzzle Y=120: half-width ~10px (16 × 0.65 = 10.4 ≈ 10)
     *
     * Layer order (back-to-front):
     *   1. Housing body         Y=14..60   — wide dark gunmetal trapezoid
     *   2. Body highlights      Y=57..60   — top edge brighter, bottom edge darker
     *   3. Fuel canister body   Y=20..50   — centered rust-red rect with rounded read
     *   4. Hazard band          Y=32..38   — yellow warning stripe across canister
     *   5. Canister details     —          — fuel-gauge groove and rivet dots
     *   6. Feed connection      Y=55..62   — dark rubber connector bar from body to nozzle
     *   7. Nozzle tube          Y=60..120  — perspective-tapered tube via drawSymmetricTrapezoid
     *   8. Nozzle cylinder shading —       — outer shadow, crown highlight, inner shadow
     *   9. Igniter ring         Y=114..120 — bright steel collar at nozzle tip
     *  10. Muzzle cap           Y=120..122 — 2px bright steel rim (NO bore ellipse)
     *  11. Pilot flame          Y=120..128 — small orange-yellow triangle at nozzle tip
     */
    private static void drawIncineratorShape(ShapeRenderer shapeRenderer, float centerX) {

        // Y=0..14 transparent — grip cut off below screen (first-person: eyes above gun)

        // 1. Housing body — wide industrial dark gunmetal block, top-surface perspective
        //    Wider at near end (Y=14) than far end (Y=60, nozzle base join).
        shapeRenderer.setColor(0.24f, 0.26f, 0.28f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 50f, 14f, 42f, 60f);

        // 2. Body edge highlights — far edge brighter (top surface faces camera), near darker
        shapeRenderer.setColor(0.42f, 0.46f, 0.50f, 1f);
        shapeRenderer.rect(centerX - 42f, 57f, 84f, 3f);    // far-edge top highlight
        shapeRenderer.setColor(0.12f, 0.13f, 0.15f, 1f);
        shapeRenderer.rect(centerX - 50f, 14f, 100f, 3f);   // near-edge bottom shadow
        // Mid-body groove — lateral slot visible from above
        shapeRenderer.setColor(0.18f, 0.19f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 44f, 38f, 88f, 2f);

        // 3. Fuel canister body — centered rust-red tank, visible from above as wide rect.
        //    Centered on the housing top surface, so the nozzle centerX reads as symmetric.
        //    Tank: Y=20..50, 44px wide centered → left CX-22, right CX+22.
        shapeRenderer.setColor(0.55f, 0.20f, 0.12f, 1f);
        shapeRenderer.rect(centerX - 22f, 20f, 44f, 30f);
        // Canister near-edge shadow (bottom)
        shapeRenderer.setColor(0.34f, 0.11f, 0.06f, 1f);
        shapeRenderer.rect(centerX - 22f, 20f, 44f,  3f);
        // Canister far-edge highlight (top)
        shapeRenderer.setColor(0.68f, 0.28f, 0.17f, 1f);
        shapeRenderer.rect(centerX - 22f, 47f, 44f,  3f);

        // 4. Hazard band — yellow warning stripe across the canister center
        shapeRenderer.setColor(0.85f, 0.70f, 0.10f, 1f);
        shapeRenderer.rect(centerX - 22f, 32f, 44f, 6f);
        // Hazard band dividers — dark vertical breaks in the stripe (industrial read)
        shapeRenderer.setColor(0.55f, 0.20f, 0.12f, 1f);
        shapeRenderer.rect(centerX - 10f, 32f, 3f, 6f);
        shapeRenderer.rect(centerX +  7f, 32f, 3f, 6f);

        // 5. Canister details — fuel-gauge recessed groove + rivet dots
        //    Fuel gauge: a narrow dark slot indicating remaining fuel (purely cosmetic)
        shapeRenderer.setColor(0.20f, 0.08f, 0.04f, 1f);
        shapeRenderer.rect(centerX - 19f, 24f, 6f, 5f);    // fuel gauge dark track
        shapeRenderer.setColor(0.85f, 0.40f, 0.15f, 1f);
        shapeRenderer.rect(centerX - 19f, 24f, 4f, 5f);    // fuel gauge fill (shows half-full)
        // Rivet dots at canister corners
        shapeRenderer.setColor(0.36f, 0.38f, 0.42f, 1f);
        shapeRenderer.rect(centerX - 22f, 46f, 3f, 3f);    // top-left rivet
        shapeRenderer.rect(centerX + 19f, 46f, 3f, 3f);    // top-right rivet
        shapeRenderer.rect(centerX - 22f, 21f, 3f, 3f);    // bottom-left rivet
        shapeRenderer.rect(centerX + 19f, 21f, 3f, 3f);    // bottom-right rivet

        // 6. Feed connection — short dark rubber connector bar between canister and nozzle
        //    A pair of rect segments bridging the canister top to the nozzle base, centered.
        shapeRenderer.setColor(0.16f, 0.16f, 0.18f, 1f);
        shapeRenderer.rect(centerX - 8f, 55f, 16f, 8f);    // connector bar

        // 7. Nozzle tube — perspective-tapered sprayer tube pointing away from the player.
        //    A flamethrower nozzle is wider than a rifle barrel (readability) but narrower
        //    than a grenade launcher.
        //    Base Y=60: half-width 16px → left CX-16, right CX+16.
        //    Muzzle Y=120: half-width 10px (16 × 0.65 = 10.4 ≈ 10).
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 16f, 60f, 10f, 120f);

        // 8. Nozzle cylinder shading — the curved top surface of the nozzle tube.
        //    Viewed from slightly above: outer edges curve away (dark), crown faces camera (bright).
        //    All shading strips use the same taper so they stay parallel in perspective.
        //    Base outer edge: CX-16 / CX+16; muzzle: CX-10 / CX+10.

        // Outer-edge shadow strips (3px at base → 2px at muzzle, both sides)
        shapeRenderer.setColor(0.10f, 0.11f, 0.13f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 16f, centerX - 13f, 60f,
                                            centerX - 10f, centerX -  8f, 120f);  // left outer shadow
        drawGeneralTrapezoid(shapeRenderer, centerX + 13f, centerX + 16f, 60f,
                                            centerX +  8f, centerX + 10f, 120f);  // right outer shadow

        // Crown highlight (6px at base → 4px at muzzle, centered on top of the cylinder)
        shapeRenderer.setColor(0.45f, 0.49f, 0.54f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX -  3f, centerX +  3f, 60f,
                                            centerX -  2f, centerX +  2f, 120f);  // center crown highlight

        // Inner-edge shadow strips (3px at base → 2px at muzzle, just inside outer shadow)
        shapeRenderer.setColor(0.14f, 0.15f, 0.18f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 13f, centerX - 10f, 60f,
                                            centerX -  8f, centerX -  6f, 120f);  // left inner shadow
        drawGeneralTrapezoid(shapeRenderer, centerX + 10f, centerX + 13f, 60f,
                                            centerX +  6f, centerX +  8f, 120f);  // right inner shadow

        // 9. Igniter ring — small bright steel collar at the nozzle muzzle end
        //    At muzzle scale (Y=114..120), width = 10px half-width → 20px total.
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        shapeRenderer.rect(centerX - 12f, 114f, 24f, 6f);  // igniter ring body
        shapeRenderer.setColor(0.55f, 0.58f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 12f, 118f, 24f, 2f);  // ring top highlight
        shapeRenderer.setColor(0.18f, 0.19f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 12f, 114f, 24f, 2f);  // ring bottom shadow

        // 10. Muzzle cap — 2px bright steel rim at barrel tip Y=120.
        //     Width = muzzle nozzle half-width 10px → 20px total.
        //     Top-down view: bore faces away, invisible. Muzzle cap only (no bore ellipse).
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 10f, 120f, 20f, 2f);  // muzzle cap

        // 11. Pilot flame — a small ever-present orange-yellow flicker at the nozzle tip.
        //     Signals the weapon is always live and ready. A small triangle pointing upward
        //     (away from player) at Y=120..128. Core is yellow-white; outer fringe is orange.
        //     The flame is narrower than the nozzle — it reads as a focused pilot light.
        shapeRenderer.setColor(0.95f, 0.55f, 0.15f, 0.90f);
        shapeRenderer.triangle(
            centerX - 5f, 120f,
            centerX + 5f, 120f,
            centerX,      128f
        );
        // Yellow-white hot core of the pilot flame
        shapeRenderer.setColor(1.00f, 0.85f, 0.30f, 0.85f);
        shapeRenderer.triangle(
            centerX - 2f, 120f,
            centerX + 2f, 120f,
            centerX,      126f
        );
    }

    /**
     * Generates a grenade launcher sprite using ShapeRenderer into an offscreen FrameBuffer.
     * Quake-1 style top-down perspective: camera slightly above and behind the weapon.
     * The grip is NOT drawn — cut off below screen edge (Y=0..14 transparent).
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (grip region — transparent, cut off)
     *   Y = 134 → top of canvas (muzzle tip, pointing toward horizon)
     *
     * Layout zones:
     *   Y  0– 14  transparent  — grip cut off below screen
     *   Y 14– 66  receiver body — wide chunky dark gunmetal trapezoid (~110px at base)
     *   Y 30– 42  hazard stripe — yellow/black diagonal warning band across receiver
     *   Y 62– 66  break-action hinges — dark notch rects flanking centerX
     *   Y 66–124  single wide barrel tube — perspective-tapered (factor 0.65)
     *   Y 108–112 muzzle collar — retaining band with yellow top edge
     *   Y 124     muzzle cap — 2px bright steel band (NO bore ellipse, top-down rule)
     *
     * View mode: TOP-DOWN, convergence factor ~0.65, muzzle cap (no bore ellipse).
     */
    private static Texture generateGrenadeLauncherTexture() {
        int canvasWidth  = WeaponConstants.GRENADE_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.GRENADE_CANVAS_HEIGHT;

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
        drawGrenadeLauncherShape(temporaryShapeRenderer, canvasWidth / 2f);
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
     * Draws a break-action grenade launcher in Quake-1 top-down first-person perspective.
     *
     * Identity silhouette: a stubby, fat single-tube launcher — short and wide, the
     * opposite of the rifle silhouette. The widest body of any weapon (~110px at base)
     * reads as heavy ordnance. Yellow hazard striping is the signature accent color.
     *
     * Top-down view, convergence factor 0.65: barrel points AWAY from camera.
     * Bore invisible — muzzle cap only, no bore ellipse.
     *
     * Barrel layout (single tube, offsets from centerX=96, factor 0.65):
     *   Base  Y=66: half-width 22px → left CX-22, right CX+22
     *   Muzzle Y=124: half-width ~14px (22 × 0.65 = 14.3 ≈ 14)
     *
     * Layer order (back-to-front):
     *   1. Receiver body           Y=14..66  — wide dark gunmetal trapezoid
     *   2. Receiver edge strips    Y=14..66  — top highlight, bottom shadow
     *   3. Hazard stripe           Y=30..42  — yellow/black warning band
     *   4. Break-action hinges     Y=62..66  — dark notch rects flanking centerX
     *   5. Barrel tube             Y=66..124 — perspective-tapered gunmetal tube
     *   6. Barrel cylinder shading —         — outer shadow, crown highlight, inner shadow
     *   7. Muzzle collar           Y=108..112 — retaining band with yellow top accent
     *   8. Muzzle cap              Y=124..126 — 2px bright steel rim (NO bore ellipse)
     *   9. Front sight post        Y=118..121 — small raised steel post on barrel crown
     */
    private static void drawGrenadeLauncherShape(ShapeRenderer shapeRenderer, float centerX) {

        // Y=0..14 transparent — grip cut off below screen (first-person: eyes above gun)

        // 1. Receiver body — wide chunky dark gunmetal trapezoid, top-surface perspective.
        //    Wider at near end (~110px) than far end to read as heavy/stubby ordnance.
        shapeRenderer.setColor(0.24f, 0.26f, 0.30f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 55f, 14f, 52f, 66f);

        // 2. Receiver edge strips — far edge brighter (top surface faces camera), near darker
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 52f, 63f, 104f, 3f);    // far-edge top highlight
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        shapeRenderer.rect(centerX - 55f, 14f, 110f, 3f);    // near-edge bottom shadow
        // Mid-body groove
        shapeRenderer.setColor(0.18f, 0.19f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 50f, 52f, 100f, 2f);

        // 3. Hazard stripe — yellow/black warning band across receiver face (Y=30..42).
        //    Alternating yellow and dark bands sell "ordnance / explosive."
        //    Yellow stripe 1: Y=30..34
        shapeRenderer.setColor(0.85f, 0.70f, 0.10f, 1f);
        shapeRenderer.rect(centerX - 48f, 30f, 96f, 4f);
        // Black divider: Y=34..36
        shapeRenderer.setColor(0.10f, 0.10f, 0.10f, 1f);
        shapeRenderer.rect(centerX - 48f, 34f, 96f, 2f);
        // Yellow stripe 2: Y=36..40
        shapeRenderer.setColor(0.85f, 0.70f, 0.10f, 1f);
        shapeRenderer.rect(centerX - 48f, 36f, 96f, 4f);
        // Black divider top: Y=40..42
        shapeRenderer.setColor(0.10f, 0.10f, 0.10f, 1f);
        shapeRenderer.rect(centerX - 48f, 40f, 96f, 2f);

        // 4. Break-action hinges — two small dark notch rects flanking centerX at the
        //    receiver/barrel join (Y=62..66), implying the barrel breaks open to load.
        shapeRenderer.setColor(0.16f, 0.18f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 22f, 62f, 10f, 4f);     // left hinge notch
        shapeRenderer.rect(centerX + 12f, 62f, 10f, 4f);     // right hinge notch
        // Hinge highlight line — a thin brighter strip at the break joint
        shapeRenderer.setColor(0.38f, 0.42f, 0.48f, 1f);
        shapeRenderer.rect(centerX - 22f, 65f, 10f, 1f);     // left hinge shine
        shapeRenderer.rect(centerX + 12f, 65f, 10f, 1f);     // right hinge shine

        // 5. Single wide barrel tube — perspective-tapered, top surface of a fat cylinder.
        //    A grenade launcher barrel is shorter/fatter than a rifle barrel (ratio ~1:2.5).
        //    Base Y=66: half-width 22px → left CX-22, right CX+22 (total 44px).
        //    Muzzle Y=124: half-width 14px → left CX-14, right CX+14 (22 × 0.65 = 14.3 ≈ 14).
        shapeRenderer.setColor(0.26f, 0.28f, 0.32f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 22f, 66f, 14f, 124f);

        // 6. Barrel cylinder shading — the curved top surface of the wide tube.
        //    Outer-edge shadow strips (3px at base → 2px at muzzle, both sides)
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 22f, centerX - 19f, 66f,
                                            centerX - 14f, centerX - 12f, 124f);  // left outer shadow
        drawGeneralTrapezoid(shapeRenderer, centerX + 19f, centerX + 22f, 66f,
                                            centerX + 12f, centerX + 14f, 124f);  // right outer shadow

        // Crown highlight (6px at base → 4px at muzzle, centered on top of the cylinder)
        shapeRenderer.setColor(0.45f, 0.49f, 0.56f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX -  3f, centerX +  3f, 66f,
                                            centerX -  2f, centerX +  2f, 124f);  // center crown highlight

        // Inner-edge shadow strips (3px at base → 2px at muzzle, just inside outer shadows)
        shapeRenderer.setColor(0.14f, 0.15f, 0.18f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 19f, centerX - 16f, 66f,
                                            centerX - 12f, centerX - 10f, 124f);  // left inner shadow
        drawGeneralTrapezoid(shapeRenderer, centerX + 16f, centerX + 19f, 66f,
                                            centerX + 10f, centerX + 12f, 124f);  // right inner shadow

        // 7. Muzzle collar — retaining band at Y=108..112, full muzzle-width.
        //    At Y=110 mid-band: scale = 1.0 - (1-0.65) × (110-66) / (124-66)
        //      = 1.0 - 0.35 × 44/58 = 1.0 - 0.265 = 0.735
        //    Half-width at collar: 22 × 0.735 ≈ 16px → left CX-16, right CX+16
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 16f, 108f, 32f, 4f);    // collar body
        // Yellow accent top edge — second hazard marking at the muzzle mouth
        shapeRenderer.setColor(0.85f, 0.70f, 0.10f, 1f);
        shapeRenderer.rect(centerX - 16f, 111f, 32f, 1f);    // yellow top edge

        // 8. Muzzle cap — 2px bright steel band at barrel tip Y=124, muzzle width.
        //    Top-down view: bore faces away, bore hole is completely invisible.
        //    Width = muzzle barrel half-width 14px × 2 = 28px total.
        //    NO bore ellipse (top-down rule — bores face away from camera).
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 14f, 124f, 28f, 2f);    // muzzle cap

        // 9. Front sight post — small raised steel post on the barrel crown near the muzzle.
        //    Centered on the crown highlight, just inside the muzzle collar at Y=118..121.
        //    At Y=119 mid-post: scale ≈ 0.690 → crown position is exactly centerX ±2px.
        shapeRenderer.setColor(0.50f, 0.54f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 2f, 118f, 4f, 3f);      // post body — steel-grey
        shapeRenderer.setColor(0.68f, 0.72f, 0.80f, 1f);
        shapeRenderer.rect(centerX - 1f, 120f, 2f, 1f);      // post tip highlight
    }

    // -------------------------------------------------------------------------
    // MELEE WEAPON SPRITES
    // -------------------------------------------------------------------------

    /**
     * Generates an armored fist sprite using ShapeRenderer into an offscreen FrameBuffer.
     *
     * Two gauntlets side-by-side, knuckles pointing away (top of canvas).
     * Top-down first-person perspective: grip is off-screen below Y=14.
     *
     * Layout zones (Y-up):
     *   Y  0..14  transparent — grip cut off below screen
     *   Y 14..50  wrist/forearm blocks — gunmetal grey, symmetric pair
     *   Y 48..70  knuckle ridge — slightly narrower, 4 finger bumps per hand
     *   Y 68..90  finger tubes — four tapered tubes per hand pointing away
     *   Y 88..98  fingertips — 2px bright metal cap at Y=96
     *   Centre gap (CX-4..CX+4) is near-black — crack between the two fists
     */
    private static Texture generateFistTexture() {
        int canvasWidth  = WeaponConstants.MELEE_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.MELEE_CANVAS_HEIGHT;

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
        drawFistShape(temporaryShapeRenderer, canvasWidth / 2f);
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
     * Draws two armored gauntlets in Quake-1 top-down first-person perspective.
     *
     * Both fists are symmetric about centerX=96. The centre gap (CX-4..CX+4) is
     * near-black to visually separate the two hands. Finger tubes taper slightly
     * from Y=68 (6px wide) to Y=90 (4px wide) to suggest perspective recession.
     * Fingertip caps at Y=96 are the muzzle-cap equivalent for melee weapons.
     */
    private static void drawFistShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Wrist/forearm blocks — gunmetal base colour, symmetric pair
        //    Left:  CX-36..CX-4  (32px wide)   Right: CX+4..CX+36  (32px wide)
        shapeRenderer.setColor(0.30f, 0.32f, 0.36f, 1f);
        shapeRenderer.rect(centerX - 36f, 14f, 32f, 36f);   // left forearm
        shapeRenderer.rect(centerX +  4f, 14f, 32f, 36f);   // right forearm

        // Far-edge highlight — top surface faces camera
        shapeRenderer.setColor(0.48f, 0.52f, 0.58f, 1f);
        shapeRenderer.rect(centerX - 36f, 46f, 32f, 3f);    // left far edge highlight
        shapeRenderer.rect(centerX +  4f, 46f, 32f, 3f);    // right far edge highlight

        // Near-edge shadow — underside curves away from camera
        shapeRenderer.setColor(0.12f, 0.13f, 0.16f, 1f);
        shapeRenderer.rect(centerX - 36f, 14f, 32f, 3f);    // left near shadow
        shapeRenderer.rect(centerX +  4f, 14f, 32f, 3f);    // right near shadow

        // Centre gap — near-black crack between the two fists
        shapeRenderer.setColor(0.06f, 0.06f, 0.08f, 1f);
        shapeRenderer.rect(centerX - 4f, 14f, 8f, 84f);     // gap column (full height)

        // 2. Knuckle ridge — slightly narrower block above forearms (Y=48..70)
        //    Left:  CX-30..CX-4  (26px wide)   Right: CX+4..CX+30  (26px wide)
        shapeRenderer.setColor(0.30f, 0.32f, 0.36f, 1f);
        shapeRenderer.rect(centerX - 30f, 48f, 26f, 22f);   // left knuckle ridge
        shapeRenderer.rect(centerX +  4f, 48f, 26f, 22f);   // right knuckle ridge

        // Knuckle bumps — 4 finger knuckle protrusions on each hand
        //    Left hand bumps at X: CX-29, CX-22, CX-15, CX-8  each 5px wide, 3px tall at Y=65
        shapeRenderer.setColor(0.38f, 0.40f, 0.44f, 1f);
        shapeRenderer.rect(centerX - 29f, 65f, 5f, 3f);     // left knuckle 1
        shapeRenderer.rect(centerX - 22f, 65f, 5f, 3f);     // left knuckle 2
        shapeRenderer.rect(centerX - 15f, 65f, 5f, 3f);     // left knuckle 3
        shapeRenderer.rect(centerX -  8f, 65f, 5f, 3f);     // left knuckle 4
        shapeRenderer.rect(centerX +  4f, 65f, 5f, 3f);     // right knuckle 1
        shapeRenderer.rect(centerX + 11f, 65f, 5f, 3f);     // right knuckle 2
        shapeRenderer.rect(centerX + 18f, 65f, 5f, 3f);     // right knuckle 3
        shapeRenderer.rect(centerX + 25f, 65f, 5f, 3f);     // right knuckle 4

        // Knuckle ridge far-edge highlight
        shapeRenderer.setColor(0.48f, 0.52f, 0.58f, 1f);
        shapeRenderer.rect(centerX - 30f, 67f, 26f, 3f);    // left knuckle ridge top
        shapeRenderer.rect(centerX +  4f, 67f, 26f, 3f);    // right knuckle ridge top

        // 3. Finger tubes — four tapered tubes per hand pointing away (Y=68..90)
        //    Left hand tube centres at CX-28, CX-20, CX-12, CX-4  (each 6px wide at base)
        //    Right hand tube centres at CX+4, CX+12, CX+20, CX+28
        //    Taper: 6px at Y=68 down to 4px at Y=90 (convergence factor ≈ 0.67)
        shapeRenderer.setColor(0.26f, 0.28f, 0.32f, 1f);

        // Left hand — tubes drawn as general trapezoids (taper toward far end)
        // Tube 1: left CX-31..CX-25 at base, CX-30..CX-26 at muzzle (centred at CX-28)
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 31f, centerX - 25f, 68f,
            centerX - 30f, centerX - 26f, 90f);
        // Tube 2: centre at CX-20 (left CX-23, right CX-17 at base → CX-22..CX-18 at muzzle)
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 23f, centerX - 17f, 68f,
            centerX - 22f, centerX - 18f, 90f);
        // Tube 3: centre at CX-12 (left CX-15, right CX-9 → CX-14..CX-10)
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 15f, centerX - 9f, 68f,
            centerX - 14f, centerX - 10f, 90f);
        // Tube 4: centre at CX-4 (left CX-7, right CX-4 → CX-6..CX-4)
        //   Note: right edge of tube 4 is the gap boundary; stop at CX-5 to avoid gap overlap
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 7f, centerX - 5f, 68f,
            centerX - 6f, centerX - 5f, 90f);

        // Right hand — mirror of left hand tubes
        // Tube 1: centre at CX+4 (CX+5..CX+7 at base → CX+5..CX+6 at muzzle)
        drawGeneralTrapezoid(shapeRenderer,
            centerX + 5f, centerX + 7f, 68f,
            centerX + 5f, centerX + 6f, 90f);
        // Tube 2: centre at CX+12 (CX+9..CX+15 at base → CX+10..CX+14 at muzzle)
        drawGeneralTrapezoid(shapeRenderer,
            centerX +  9f, centerX + 15f, 68f,
            centerX + 10f, centerX + 14f, 90f);
        // Tube 3: centre at CX+20 (CX+17..CX+23 at base → CX+18..CX+22 at muzzle)
        drawGeneralTrapezoid(shapeRenderer,
            centerX + 17f, centerX + 23f, 68f,
            centerX + 18f, centerX + 22f, 90f);
        // Tube 4: centre at CX+28 (CX+25..CX+31 at base → CX+26..CX+30 at muzzle)
        drawGeneralTrapezoid(shapeRenderer,
            centerX + 25f, centerX + 31f, 68f,
            centerX + 26f, centerX + 30f, 90f);

        // 4. Fingertip caps — 2px bright metal strip at Y=96 (muzzle-cap equivalent)
        //    One cap per hand spanning the full finger bundle width
        shapeRenderer.setColor(0.50f, 0.54f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 31f, 96f, 26f, 2f);    // left hand fingertip cap
        shapeRenderer.rect(centerX +  5f, 96f, 26f, 2f);    // right hand fingertip cap
    }

    // -------------------------------------------------------------------------

    /**
     * Generates a combat knife sprite using ShapeRenderer into an offscreen FrameBuffer.
     *
     * A combat knife held right-of-centre with the blade pointing away (upward on canvas).
     * Top-down first-person perspective: grip is off-screen below Y=14.
     *
     * Layout zones (Y-up):
     *   Y  0..14  transparent — grip cut off below screen
     *   Y 14..44  handle — dark leather wrap with grooves, right-of-centre
     *   Y 42..50  guard — gunmetal cross-guard block
     *   Y 50..110 blade body — polished steel, tapers from 20px to 8px
     *   Y 104..122 blade tip — triangular convergence to a point
     */
    private static Texture generateCombatKnifeTexture() {
        int canvasWidth  = WeaponConstants.MELEE_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.MELEE_CANVAS_HEIGHT;

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
        drawCombatKnifeShape(temporaryShapeRenderer, canvasWidth / 2f);
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
     * Draws a combat knife in Quake-1 top-down first-person perspective.
     *
     * The knife is held right-of-centre so it reads as a weapon held in the right hand.
     * Handle occupies CX+4..CX+24; blade tapers from 20px wide at guard to a point at Y=122.
     * The sharpened left bevel is brighter than the flat right spine.
     *
     * Blade shaping:
     *   Y 50..110  flat body: left edge CX+2, right edge CX+22 (20px wide)
     *              bevel strip: 2px along left edge (CX+2..CX+4)
     *              spine strip: 3px along right edge (CX+19..CX+22)
     *   Y 104..122 tip: left edge converges from CX+2 to CX+14 (point), right from CX+22 to CX+14
     */
    private static void drawCombatKnifeShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Handle — dark leather wrap, right of centre (CX+4..CX+24), Y=14..44
        shapeRenderer.setColor(0.22f, 0.12f, 0.05f, 1f);
        shapeRenderer.rect(centerX + 4f, 14f, 20f, 30f);

        // Wrap grooves — darker horizontal bands to suggest cord wrapping
        shapeRenderer.setColor(0.14f, 0.07f, 0.02f, 1f);
        shapeRenderer.rect(centerX + 4f, 18f, 20f, 2f);     // groove 1
        shapeRenderer.rect(centerX + 4f, 25f, 20f, 2f);     // groove 2
        shapeRenderer.rect(centerX + 4f, 32f, 20f, 2f);     // groove 3
        shapeRenderer.rect(centerX + 4f, 39f, 20f, 2f);     // groove 4

        // Handle top highlight (far edge visible from above)
        shapeRenderer.setColor(0.34f, 0.18f, 0.08f, 1f);
        shapeRenderer.rect(centerX + 4f, 41f, 20f, 3f);

        // 2. Guard — gunmetal cross-guard block (CX-6..CX+28, Y=42..50)
        shapeRenderer.setColor(0.28f, 0.30f, 0.36f, 1f);
        shapeRenderer.rect(centerX - 6f, 42f, 34f, 8f);

        // Guard far-edge highlight
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 6f, 48f, 34f, 2f);

        // Guard near-edge shadow
        shapeRenderer.setColor(0.18f, 0.19f, 0.23f, 1f);
        shapeRenderer.rect(centerX - 6f, 42f, 34f, 2f);

        // 3. Blade body — polished steel flat (CX+2..CX+22, Y=50..110)
        shapeRenderer.setColor(0.58f, 0.62f, 0.68f, 1f);
        shapeRenderer.rect(centerX + 2f, 50f, 20f, 60f);

        // Left bevel edge — sharpened ground edge, brightest (CX+2..CX+4)
        shapeRenderer.setColor(0.82f, 0.86f, 0.90f, 1f);
        shapeRenderer.rect(centerX + 2f, 50f, 2f, 60f);

        // Right spine — darker heavy spine (CX+19..CX+22)
        shapeRenderer.setColor(0.34f, 0.36f, 0.42f, 1f);
        shapeRenderer.rect(centerX + 19f, 50f, 3f, 60f);

        // Mid blade shadow groove — subtle central fullers line at Y=80
        shapeRenderer.setColor(0.48f, 0.52f, 0.58f, 1f);
        shapeRenderer.rect(centerX + 8f, 80f, 6f, 2f);

        // 4. Blade tip — triangular convergence from Y=104 to point at Y=122, CX+14
        //    Left side: left edge goes from CX+2 at Y=104 to CX+14 at Y=122
        //    Right side: right edge goes from CX+22 at Y=104 to CX+14 at Y=122
        shapeRenderer.setColor(0.58f, 0.62f, 0.68f, 1f);
        shapeRenderer.triangle(
            centerX +  2f, 104f,    // bottom-left
            centerX + 22f, 104f,    // bottom-right
            centerX + 14f, 122f     // tip point
        );

        // Tip bevel highlight — left edge of tip
        shapeRenderer.setColor(0.82f, 0.86f, 0.90f, 1f);
        shapeRenderer.triangle(
            centerX +  2f, 104f,
            centerX +  4f, 104f,
            centerX + 14f, 122f
        );

        // Tip spine — right edge of tip
        shapeRenderer.setColor(0.34f, 0.36f, 0.42f, 1f);
        shapeRenderer.triangle(
            centerX + 19f, 104f,
            centerX + 22f, 104f,
            centerX + 14f, 122f
        );

        // Tip glint — bright highlight at the very point
        shapeRenderer.setColor(0.92f, 0.95f, 0.98f, 1f);
        shapeRenderer.rect(centerX + 13f, 120f, 2f, 2f);
    }

    // -------------------------------------------------------------------------

    /**
     * Generates a hammer sprite using ShapeRenderer into an offscreen FrameBuffer.
     *
     * A heavy hammer held centre with the head pointing away (upward on canvas).
     * Top-down first-person perspective: grip is off-screen below Y=14.
     *
     * Layout zones (Y-up):
     *   Y  0..14  transparent — grip cut off below screen
     *   Y 14..74  handle — mahogany wooden shaft, narrow, centered
     *   Y 68..84  head socket — dark steel collar joining handle to head
     *   Y 80..116 hammer head body — wide gunmetal steel block
     *   Y 116..122 poll face — bright chrome striking surface (muzzle equivalent)
     *
     * Handle: CX-6..CX+6 (12px wide)
     * Head: CX-58..CX+58 (116px wide), end caps CX-64..CX-58 and CX+58..CX+64
     */
    private static Texture generateHammerTexture() {
        int canvasWidth  = WeaponConstants.MELEE_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.MELEE_CANVAS_HEIGHT;

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
        drawHammerShape(temporaryShapeRenderer, canvasWidth / 2f);
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
     * Draws a hammer in Quake-1 top-down first-person perspective.
     *
     * Vertical orientation: narrow wooden handle at centre, wide steel head at far end (top).
     * The striking faces (end caps) are the muzzle-cap equivalent for this weapon.
     *
     * Handle: CX-6..CX+6 (12px), Y=14..74     — mahogany wood with grain lines
     * Socket: CX-10..CX+10 (20px), Y=68..84   — dark steel collar
     * Head:   CX-58..CX+58 (116px), Y=80..116 — gunmetal body
     * Caps:   CX-64..CX-58 and CX+58..CX+64   — bright striking faces
     * Poll:   CX-64..CX+64 (128px), Y=116..122 — top chrome face (muzzle equivalent)
     */
    private static void drawHammerShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Handle — mahogany wooden shaft, Y=14..74
        shapeRenderer.setColor(0.40f, 0.22f, 0.08f, 1f);
        shapeRenderer.rect(centerX - 6f, 14f, 12f, 60f);    // shaft body

        // Wood grain lines — darker horizontal bands suggesting grain texture
        shapeRenderer.setColor(0.28f, 0.14f, 0.05f, 1f);
        shapeRenderer.rect(centerX - 6f, 26f, 12f, 1f);     // grain 1
        shapeRenderer.rect(centerX - 6f, 40f, 12f, 1f);     // grain 2
        shapeRenderer.rect(centerX - 6f, 54f, 12f, 1f);     // grain 3

        // Handle top edge highlight — light wood surface facing camera
        shapeRenderer.setColor(0.58f, 0.36f, 0.14f, 1f);
        shapeRenderer.rect(centerX - 6f, 71f, 12f, 3f);

        // Handle butt cap — dark steel ring at the very base
        shapeRenderer.setColor(0.20f, 0.22f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 8f, 14f, 16f, 4f);

        // 2. Head socket — dark steel collar where handle meets head, Y=68..84
        shapeRenderer.setColor(0.20f, 0.22f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 10f, 68f, 20f, 16f);   // socket body
        shapeRenderer.setColor(0.28f, 0.30f, 0.36f, 1f);
        shapeRenderer.rect(centerX - 10f, 80f, 20f, 4f);    // socket top face

        // 3. Hammer head body — wide gunmetal block, Y=80..116
        shapeRenderer.setColor(0.30f, 0.32f, 0.36f, 1f);
        shapeRenderer.rect(centerX - 58f, 80f, 116f, 36f);  // main head body

        // Near-edge shadow — bottom of head facing away from camera
        shapeRenderer.setColor(0.18f, 0.20f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 58f, 80f, 116f, 4f);

        // Far-edge highlight — top surface of head facing camera
        shapeRenderer.setColor(0.44f, 0.46f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 58f, 112f, 116f, 4f);

        // Longitudinal centre groove on top surface
        shapeRenderer.setColor(0.22f, 0.24f, 0.28f, 1f);
        shapeRenderer.rect(centerX - 54f, 96f, 108f, 2f);

        // 4. Striking face end caps — bright steel on both sides of head
        shapeRenderer.setColor(0.52f, 0.54f, 0.60f, 1f);
        shapeRenderer.rect(centerX - 64f, 80f, 6f, 36f);    // left striking face
        shapeRenderer.rect(centerX + 58f, 80f, 6f, 36f);    // right striking face

        // End cap top highlights
        shapeRenderer.setColor(0.64f, 0.66f, 0.72f, 1f);
        shapeRenderer.rect(centerX - 64f, 112f, 6f, 4f);    // left cap crown
        shapeRenderer.rect(centerX + 58f, 112f, 6f, 4f);    // right cap crown

        // 5. Poll face — bright chrome top striking surface (muzzle equivalent), Y=116..122
        shapeRenderer.setColor(0.60f, 0.62f, 0.68f, 1f);
        shapeRenderer.rect(centerX - 64f, 116f, 128f, 6f);
    }

    // -------------------------------------------------------------------------

    /**
     * Generates a melee chainsaw sprite using ShapeRenderer into an offscreen FrameBuffer.
     *
     * A chainsaw body held centre with the guide bar pointing away (upward on canvas).
     * Top-down first-person perspective: grip/stock is off-screen below Y=14.
     *
     * Layout zones (Y-up):
     *   Y  0..14  transparent — grip cut off below screen
     *   Y 14..54  engine body housing — wide gunmetal with orange hazard stripe
     *   Y 40..60  side grip handles — extend from body on both sides
     *   Y 54..70  chain drive sprocket area — housing + chain detail
     *   Y 68..124 guide bar — narrow gunmetal bar with chain teeth along edges
     *   Y 122..124 nose sprocket tip — bright steel muzzle cap
     */
    private static Texture generateMeleeChainsawTexture() {
        int canvasWidth  = WeaponConstants.MELEE_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.MELEE_CANVAS_HEIGHT;

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
        drawMeleeChainsawShape(temporaryShapeRenderer, canvasWidth / 2f);
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
     * Draws a chainsaw in Quake-1 top-down first-person perspective.
     *
     * The guide bar (CX-8..CX+8, Y=68..124) tapers slightly to CX-5..CX+5 at the tip.
     * Chain teeth appear as alternating dark slots along each edge of the guide bar every 8px.
     * The engine body is symmetric (CX-44..CX+44, Y=14..54) with an orange hazard stripe.
     * Air vents are dark slots on each side of the engine housing.
     *
     * Guide bar taper: 8px half-width at Y=68 to 5px at Y=124 (convergence factor ≈ 0.625).
     * Chain teeth: 3px dark slots at each edge, spaced every 8px from Y=70 to Y=120.
     * Bright chain link segments fill the gaps between slots.
     */
    private static void drawMeleeChainsawShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Engine body housing — wide gunmetal block (CX-44..CX+44, Y=14..54)
        shapeRenderer.setColor(0.24f, 0.26f, 0.30f, 1f);
        shapeRenderer.rect(centerX - 44f, 14f, 88f, 40f);

        // Body top-edge highlight — far surface faces camera
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        shapeRenderer.rect(centerX - 44f, 50f, 88f, 4f);

        // Body near-edge shadow
        shapeRenderer.setColor(0.14f, 0.15f, 0.18f, 1f);
        shapeRenderer.rect(centerX - 44f, 14f, 88f, 4f);

        // 2. Orange hazard stripe — safety marking across engine body (Y=36..42)
        shapeRenderer.setColor(0.88f, 0.48f, 0.04f, 1f);
        shapeRenderer.rect(centerX - 44f, 36f, 88f, 6f);

        // Hazard stripe top highlight
        shapeRenderer.setColor(0.98f, 0.62f, 0.10f, 1f);
        shapeRenderer.rect(centerX - 44f, 40f, 88f, 2f);

        // 3. Engine air vents — 3 dark slots on each side of housing (Y=18..34)
        //    Left vents: CX-42..CX-34 (3 slots each 4px tall, spaced 2px apart)
        //    Right vents: CX+34..CX+42 (mirror)
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(centerX - 42f, 18f, 8f, 4f);     // left vent 1
        shapeRenderer.rect(centerX - 42f, 24f, 8f, 4f);     // left vent 2
        shapeRenderer.rect(centerX - 42f, 30f, 8f, 4f);     // left vent 3
        shapeRenderer.rect(centerX + 34f, 18f, 8f, 4f);     // right vent 1
        shapeRenderer.rect(centerX + 34f, 24f, 8f, 4f);     // right vent 2
        shapeRenderer.rect(centerX + 34f, 30f, 8f, 4f);     // right vent 3

        // 4. Side grip handles — black rubber, extend from body (Y=40..60)
        //    Left grip:  CX-54..CX-38   Right grip: CX+38..CX+54
        shapeRenderer.setColor(0.14f, 0.14f, 0.16f, 1f);
        shapeRenderer.rect(centerX - 54f, 40f, 16f, 20f);   // left grip
        shapeRenderer.rect(centerX + 38f, 40f, 16f, 20f);   // right grip

        // Grip top highlights
        shapeRenderer.setColor(0.22f, 0.22f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 54f, 56f, 16f, 4f);    // left grip top
        shapeRenderer.rect(centerX + 38f, 56f, 16f, 4f);    // right grip top

        // 5. Chain drive sprocket housing — dark ring centered on body (Y=54..70)
        //    Outer housing: CX-14..CX+14, inner opening: CX-8..CX+8
        shapeRenderer.setColor(0.18f, 0.20f, 0.24f, 1f);
        shapeRenderer.rect(centerX - 14f, 54f, 28f, 16f);   // sprocket housing block

        // Sprocket housing highlight arc (top edge visible from above)
        shapeRenderer.setColor(0.32f, 0.36f, 0.44f, 1f);
        shapeRenderer.rect(centerX - 14f, 66f, 28f, 4f);

        // Chain teeth around sprocket rim — alternating bright links visible at edges
        shapeRenderer.setColor(0.52f, 0.56f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 14f, 56f, 4f, 3f);     // left teeth 1
        shapeRenderer.rect(centerX - 14f, 62f, 4f, 3f);     // left teeth 2
        shapeRenderer.rect(centerX + 10f, 56f, 4f, 3f);     // right teeth 1
        shapeRenderer.rect(centerX + 10f, 62f, 4f, 3f);     // right teeth 2

        // 6. Guide bar — narrow gunmetal bar pointing away, tapered (Y=68..124)
        //    Base: CX-8..CX+8 (16px wide)   Tip: CX-5..CX+5 (10px wide)
        shapeRenderer.setColor(0.30f, 0.32f, 0.38f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 8f, centerX + 8f, 68f,
            centerX - 5f, centerX + 5f, 124f);

        // Guide bar far-edge highlight (top surface, narrow strip at far end)
        shapeRenderer.setColor(0.44f, 0.48f, 0.56f, 1f);
        drawGeneralTrapezoid(shapeRenderer,
            centerX - 8f, centerX - 6f, 68f,
            centerX - 5f, centerX - 4f, 124f);   // left rail highlight
        drawGeneralTrapezoid(shapeRenderer,
            centerX + 6f, centerX + 8f, 68f,
            centerX + 4f, centerX + 5f, 124f);   // right rail highlight

        // 7. Chain teeth along guide bar edges — dark slots every 8px, Y=70..120
        //    Left edge slots: x = CX-8..CX-5 (3px), spaced 8px apart
        //    Right edge slots: x = CX+5..CX+8 (3px), spaced 8px apart
        shapeRenderer.setColor(0.18f, 0.20f, 0.24f, 1f);
        for (int toothRow = 0; toothRow < 7; toothRow++) {
            float toothY = 70f + toothRow * 8f;
            // Interpolate guide bar width at this Y to keep teeth on the bar edges
            float interpolationFactor = (toothY - 68f) / (124f - 68f);
            float leftEdge  = (centerX - 8f) + interpolationFactor * ((centerX - 5f) - (centerX - 8f));
            float rightEdge = (centerX + 8f) + interpolationFactor * ((centerX + 5f) - (centerX + 8f));
            shapeRenderer.rect(leftEdge,       toothY, 3f, 4f);   // left chain tooth
            shapeRenderer.rect(rightEdge - 3f, toothY, 3f, 4f);  // right chain tooth
        }

        // Bright chain link segments between teeth (half-brightness contrast against slots)
        shapeRenderer.setColor(0.52f, 0.56f, 0.62f, 1f);
        for (int linkRow = 0; linkRow < 6; linkRow++) {
            float linkY = 74f + linkRow * 8f;
            float interpolationFactor = (linkY - 68f) / (124f - 68f);
            float leftEdge  = (centerX - 8f) + interpolationFactor * ((centerX - 5f) - (centerX - 8f));
            float rightEdge = (centerX + 8f) + interpolationFactor * ((centerX + 5f) - (centerX + 8f));
            shapeRenderer.rect(leftEdge,       linkY, 3f, 3f);    // left link segment
            shapeRenderer.rect(rightEdge - 3f, linkY, 3f, 3f);   // right link segment
        }

        // 8. Nose sprocket tip — bright steel muzzle cap at guide bar end (Y=122..124)
        //    Width = muzzle guide bar width: CX-5..CX+5 (10px)
        shapeRenderer.setColor(0.52f, 0.56f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 5f, 122f, 10f, 2f);    // nose cap (muzzle-cap equivalent)
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

    /**
     * Registers a weapon that was not part of the initial arsenal list.
     * Generates its normal texture on first call; subsequent calls for the same class are no-ops.
     * Must be called before {@link #setEquippedWeapon(Weapon)} switches to this weapon.
     */
    public void registerAdditionalWeapon(Weapon weapon) {
        if (weapon != null && !weaponTextureCache.containsKey(weapon.getClass())) {
            weaponTextureCache.put(weapon.getClass(), loadOrGenerateNormalTexture(weapon));
        }
    }

    /** Returns the cached normal-pose texture for the given weapon, or null if not registered. */
    public Texture getWeaponTexture(Weapon weapon) {
        if (weapon == null) return null;
        return weaponTextureCache.get(weapon.getClass());
    }

    @Override
    public void render(OrthographicCamera camera) {
        if (equippedWeapon == null) return;
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
                   WeaponConstants.WEAPON_HUD_WIDTH, WeaponConstants.WEAPON_HUD_HEIGHT);
        batch.end();

        if (state == WeaponVisualState.FIRING) {
            float normalizedTime = Math.min(animationTimer / WeaponConstants.FIRE_FLASH_DURATION, 1f);
            if (normalizedTime < 1f) {
                if (equippedWeapon instanceof PlasmaRifle) {
                    renderPlasmaEffect(camera, normalizedTime);
                } else if (equippedWeapon instanceof Shotgun) {
                    renderShotgunEffect(camera, normalizedTime);
                } else if (equippedWeapon instanceof DoubleBarrelShotgun) {
                    renderDoubleBarrelShotgunEffect(camera, normalizedTime);
                } else if (equippedWeapon instanceof Chaingun) {
                    renderChaingunEffect(camera, normalizedTime);
                } else if (equippedWeapon instanceof Railgun) {
                    renderRailgunEffect(camera, normalizedTime);
                } else if (equippedWeapon instanceof Incinerator) {
                    renderIncineratorEffect(camera, normalizedTime);
                } else if (equippedWeapon instanceof GrenadeLauncher) {
                    renderGrenadeLauncherEffect(camera, normalizedTime);
                } else if (equippedWeapon instanceof MeleeWeapon) {
                    renderMeleeSwingEffect(camera, normalizedTime);
                } else {
                    renderFlameEffect(camera, normalizedTime);
                }
            }
        }
    }

    private void advanceOffsetY(WeaponVisualState state, float deltaTime) {
        if (state == WeaponVisualState.FIRING) {
            float normalizedTime = Math.min(animationTimer / WeaponConstants.FIRE_FLASH_DURATION, 1f);
            if (equippedWeapon instanceof MeleeWeapon) {
                // Lunge forward (rise upward) on a melee hit, return to base as swing completes.
                currentOffsetY = WeaponConstants.WEAPON_HUD_BASE_Y
                                 + WeaponConstants.WEAPON_MELEE_LUNGE_Y * (1f - normalizedTime);
            } else {
                currentOffsetY = WeaponConstants.WEAPON_HUD_BASE_Y
                                 - WeaponConstants.WEAPON_RECOIL_OFFSET_Y * (1f - normalizedTime);
            }
        } else {
            float targetOffsetY = (state == WeaponVisualState.RELOADING)
                                  ? WeaponConstants.WEAPON_HUD_BASE_Y - WeaponConstants.WEAPON_RELOAD_SLIDE_Y
                                  : WeaponConstants.WEAPON_HUD_BASE_Y;
            currentOffsetY += (targetOffsetY - currentOffsetY)
                              * Math.min(deltaTime * WeaponConstants.WEAPON_OFFSET_LERP_SPEED, 1f);
        }
    }

    /**
     * Draws a brief impact flash at the weapon tip for melee attacks.
     * A white-yellow starburst ring fades and contracts over FIRE_FLASH_DURATION.
     * No flame or muzzle cone — melee contact is represented as a blunt impact burst.
     */
    private void renderMeleeSwingEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha  = 1f - normalizedTime;
        float scale  = 1f - normalizedTime * 0.50f;
        float strikeX = Constants.WORLD_WIDTH / 2f;
        float strikeY = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                        * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float outerRadius = 48f * scale;
        float innerRadius = 22f * scale;

        // Outer diffuse ring — warm yellow
        shapeRenderer.setColor(1.00f, 0.88f, 0.30f, alpha * 0.40f);
        shapeRenderer.ellipse(strikeX - outerRadius, strikeY - outerRadius * 0.55f,
                              outerRadius * 2f, outerRadius * 1.10f);

        // Inner hot core — bright white
        shapeRenderer.setColor(1.00f, 1.00f, 0.92f, alpha * 0.80f);
        shapeRenderer.ellipse(strikeX - innerRadius, strikeY - innerRadius * 0.55f,
                              innerRadius * 2f, innerRadius * 1.10f);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
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
        float barrelY = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                        * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;
        float radius  = WeaponConstants.PLASMA_BLAST_RADIUS * scale;

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
     * Draws a short wide muzzle blast for the pump-action Shotgun.
     *
     * The primary element is a fat horizontal disc at the muzzle mouth — wider than it is
     * tall — representing the wide spread of the shot charge. Two short red side tongues
     * erupt left and right, and a modest orange cone rises above the disc.
     * All layers fade and shrink as normalizedTime approaches 1.
     */
    private void renderShotgunEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha          = 1f - normalizedTime;
        float scale          = 1f - normalizedTime * 0.55f;
        float barrelX        = Constants.WORLD_WIDTH / 2f;
        float barrelY        = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                               * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;
        float height         = WeaponConstants.SHOTGUN_EFFECT_FLAME_HEIGHT * scale;
        float halfBase       = WeaponConstants.SHOTGUN_EFFECT_FLAME_BASE_WIDTH / 2f * scale;
        float discHalfWidth  = WeaponConstants.SHOTGUN_EFFECT_DISC_HALF_WIDTH  * scale;
        float discHalfHeight = WeaponConstants.SHOTGUN_EFFECT_DISC_HALF_HEIGHT * scale;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Outer translucent blast halo disc — wide and very flat
        shapeRenderer.setColor(1.00f, 0.72f, 0.20f, alpha * 0.35f);
        shapeRenderer.ellipse(barrelX - discHalfWidth * 1.20f, barrelY - discHalfHeight * 1.20f,
                              discHalfWidth * 2.40f, discHalfHeight * 2.40f);

        // Primary muzzle disc — fat, low, orange-yellow
        shapeRenderer.setColor(1.00f, 0.58f, 0.08f, alpha * 0.72f);
        shapeRenderer.ellipse(barrelX - discHalfWidth, barrelY - discHalfHeight,
                              discHalfWidth * 2f, discHalfHeight * 2f);

        // Bright inner disc core — yellow-white, smaller
        shapeRenderer.setColor(1.00f, 0.90f, 0.50f, alpha * 0.85f);
        shapeRenderer.ellipse(barrelX - discHalfWidth * 0.55f, barrelY - discHalfHeight * 0.55f,
                              discHalfWidth * 1.10f, discHalfHeight * 1.10f);

        // Left red side tongue
        shapeRenderer.setColor(0.90f, 0.10f, 0.00f, alpha * 0.65f);
        shapeRenderer.triangle(
            barrelX - halfBase,              barrelY - discHalfHeight * 0.5f,
            barrelX - discHalfWidth * 0.8f,  barrelY,
            barrelX - halfBase * 0.55f,      barrelY + height * 0.38f
        );

        // Right red side tongue
        shapeRenderer.triangle(
            barrelX + discHalfWidth * 0.8f,  barrelY,
            barrelX + halfBase,              barrelY - discHalfHeight * 0.5f,
            barrelX + halfBase * 0.55f,      barrelY + height * 0.38f
        );

        // Central orange flame cone above the disc
        shapeRenderer.setColor(1.00f, 0.45f, 0.00f, alpha * 0.78f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.45f, barrelY,
            barrelX + halfBase * 0.45f, barrelY,
            barrelX,                    barrelY + height
        );

        // Yellow inner core of the flame
        shapeRenderer.setColor(1.00f, 0.86f, 0.10f, alpha * 0.80f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.22f, barrelY,
            barrelX + halfBase * 0.22f, barrelY,
            barrelX,                    barrelY + height * 0.72f
        );

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Draws two side-by-side muzzle blast tongues for the Double-Barrel Shotgun.
     *
     * Two separate flame columns erupt left and right of centre, reflecting the
     * twin barrel layout. Each tongue has its own orange/red cone and yellow core.
     * A shared horizontal disc at the base blends the two columns together.
     * All layers fade and shrink as normalizedTime approaches 1.
     */
    private void renderDoubleBarrelShotgunEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha          = 1f - normalizedTime;
        float scale          = 1f - normalizedTime * 0.55f;
        float barrelX        = Constants.WORLD_WIDTH / 2f;
        float barrelY        = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                               * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;
        float tongueHeight   = WeaponConstants.DBL_SHOTGUN_EFFECT_FLAME_HEIGHT * scale;
        float tongueHalfBase = WeaponConstants.DBL_SHOTGUN_EFFECT_TONGUE_BASE_WIDTH / 2f * scale;
        float tongueOffsetX  = WeaponConstants.DBL_SHOTGUN_EFFECT_TONGUE_OFFSET_X * scale;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Shared muzzle disc spanning both barrels
        float sharedDiscHalfWidth  = (tongueOffsetX + tongueHalfBase) * 1.05f;
        float sharedDiscHalfHeight = tongueHalfBase * 0.55f;
        shapeRenderer.setColor(1.00f, 0.62f, 0.12f, alpha * 0.42f);
        shapeRenderer.ellipse(barrelX - sharedDiscHalfWidth, barrelY - sharedDiscHalfHeight,
                              sharedDiscHalfWidth * 2f, sharedDiscHalfHeight * 2f);

        // Left barrel tongue — red outer cone
        float leftCenterX = barrelX - tongueOffsetX;
        shapeRenderer.setColor(0.88f, 0.10f, 0.00f, alpha * 0.70f);
        shapeRenderer.triangle(
            leftCenterX - tongueHalfBase, barrelY,
            leftCenterX + tongueHalfBase, barrelY,
            leftCenterX,                  barrelY + tongueHeight * 0.62f
        );
        // Left barrel tongue — orange mid cone
        shapeRenderer.setColor(1.00f, 0.46f, 0.00f, alpha * 0.80f);
        shapeRenderer.triangle(
            leftCenterX - tongueHalfBase * 0.68f, barrelY,
            leftCenterX + tongueHalfBase * 0.68f, barrelY,
            leftCenterX,                           barrelY + tongueHeight * 0.85f
        );
        // Left barrel tongue — yellow core
        shapeRenderer.setColor(1.00f, 0.88f, 0.12f, alpha * 0.88f);
        shapeRenderer.triangle(
            leftCenterX - tongueHalfBase * 0.32f, barrelY,
            leftCenterX + tongueHalfBase * 0.32f, barrelY,
            leftCenterX,                           barrelY + tongueHeight
        );

        // Right barrel tongue — red outer cone
        float rightCenterX = barrelX + tongueOffsetX;
        shapeRenderer.setColor(0.88f, 0.10f, 0.00f, alpha * 0.70f);
        shapeRenderer.triangle(
            rightCenterX - tongueHalfBase, barrelY,
            rightCenterX + tongueHalfBase, barrelY,
            rightCenterX,                  barrelY + tongueHeight * 0.62f
        );
        // Right barrel tongue — orange mid cone
        shapeRenderer.setColor(1.00f, 0.46f, 0.00f, alpha * 0.80f);
        shapeRenderer.triangle(
            rightCenterX - tongueHalfBase * 0.68f, barrelY,
            rightCenterX + tongueHalfBase * 0.68f, barrelY,
            rightCenterX,                           barrelY + tongueHeight * 0.85f
        );
        // Right barrel tongue — yellow core
        shapeRenderer.setColor(1.00f, 0.88f, 0.12f, alpha * 0.88f);
        shapeRenderer.triangle(
            rightCenterX - tongueHalfBase * 0.32f, barrelY,
            rightCenterX + tongueHalfBase * 0.32f, barrelY,
            rightCenterX,                           barrelY + tongueHeight
        );

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Draws a tight rapid muzzle flash for the Chaingun.
     *
     * A narrow bright cone with scattered yellow spark dots surrounding the tip.
     * The cone is thinner and taller than a shotgun blast — reading as high-velocity
     * rapid fire. Sparks are placed using a deterministic pattern (no allocations).
     * All layers fade and shrink as normalizedTime approaches 1.
     */
    private void renderChaingunEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha        = 1f - normalizedTime;
        float scale        = 1f - normalizedTime * 0.55f;
        float barrelX      = Constants.WORLD_WIDTH / 2f;
        float barrelY      = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                             * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;
        float coneHeight   = WeaponConstants.CHAINGUN_EFFECT_CONE_HEIGHT * scale;
        float coneHalfBase = WeaponConstants.CHAINGUN_EFFECT_CONE_BASE_WIDTH / 2f * scale;
        float sparkSpreadX = WeaponConstants.CHAINGUN_EFFECT_SPARK_SPREAD_X * scale;
        float sparkSpreadY = WeaponConstants.CHAINGUN_EFFECT_SPARK_SPREAD_Y * scale;
        float sparkSize    = WeaponConstants.CHAINGUN_EFFECT_SPARK_SIZE * scale;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Outer red narrow cone
        shapeRenderer.setColor(0.90f, 0.12f, 0.00f, alpha * 0.68f);
        shapeRenderer.triangle(
            barrelX - coneHalfBase, barrelY,
            barrelX + coneHalfBase, barrelY,
            barrelX,                barrelY + coneHeight * 0.60f
        );

        // Orange middle cone — taller than the outer
        shapeRenderer.setColor(1.00f, 0.48f, 0.00f, alpha * 0.80f);
        shapeRenderer.triangle(
            barrelX - coneHalfBase * 0.65f, barrelY,
            barrelX + coneHalfBase * 0.65f, barrelY,
            barrelX,                         barrelY + coneHeight * 0.82f
        );

        // Yellow tight inner cone
        shapeRenderer.setColor(1.00f, 0.90f, 0.10f, alpha * 0.90f);
        shapeRenderer.triangle(
            barrelX - coneHalfBase * 0.32f, barrelY,
            barrelX + coneHalfBase * 0.32f, barrelY,
            barrelX,                         barrelY + coneHeight
        );

        // White-yellow hot core
        shapeRenderer.setColor(1.00f, 1.00f, 0.80f, alpha * 0.72f);
        shapeRenderer.triangle(
            barrelX - coneHalfBase * 0.14f, barrelY,
            barrelX + coneHalfBase * 0.14f, barrelY,
            barrelX,                         barrelY + coneHeight * 0.70f
        );

        // Spark dots — deterministic positions from the static class-level arrays
        // CHAINGUN_SPARK_FRACTIONS_X / _Y (no per-frame allocation).
        // Ten fixed positions spread around the barrel tip; each drawn as a small square.
        shapeRenderer.setColor(1.00f, 0.95f, 0.30f, alpha * 0.85f);
        for (int sparkIndex = 0; sparkIndex < WeaponConstants.CHAINGUN_EFFECT_SPARK_COUNT; sparkIndex++) {
            float sparkX = barrelX + CHAINGUN_SPARK_FRACTIONS_X[sparkIndex] * sparkSpreadX - sparkSize * 0.5f;
            float sparkY = barrelY + CHAINGUN_SPARK_FRACTIONS_Y[sparkIndex] * sparkSpreadY;
            shapeRenderer.rect(sparkX, sparkY, sparkSize, sparkSize);
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Draws an electromagnetic discharge effect for the Railgun.
     *
     * No flame — pure energy. The discharge has three coordinated parts:
     *   1. An expanding recoil ring — a thin flattened cyan halo punched out at the muzzle,
     *      growing outward as normalizedTime advances (kick-back shock front).
     *   2. A crisp central lance — a razor-thin white-cyan bolt fired straight up, layered
     *      from a soft blue halo to a pure-white hot core for a sharp readable spike.
     *   3. Branching lightning arcs — two zig-zag bolts kinking outward from the muzzle,
     *      built segment-by-segment from the static RAILGUN_BRANCH_JITTER_FRACTIONS table
     *      (deterministic, no per-frame allocation).
     *
     * All elements fade as normalizedTime approaches 1; the lance narrows while the recoil
     * ring expands (inverse timing) to sell the discharge front leaving the weapon.
     */
    private void renderRailgunEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha         = 1f - normalizedTime;
        float scale         = 1f - normalizedTime * 0.55f;
        float barrelX       = Constants.WORLD_WIDTH / 2f;
        float barrelY       = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                              * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;
        float lanceHeight   = WeaponConstants.RAILGUN_EFFECT_LANCE_HEIGHT * scale;
        float lanceHalfBase = WeaponConstants.RAILGUN_EFFECT_LANCE_BASE_WIDTH / 2f * scale;
        float arcSpread     = WeaponConstants.RAILGUN_EFFECT_ARC_SPREAD * scale;
        float arcHeight     = WeaponConstants.RAILGUN_EFFECT_ARC_HEIGHT * scale;
        // Recoil ring expands with normalizedTime (0 → full radius) rather than shrinking.
        float ringRadius    = WeaponConstants.RAILGUN_EFFECT_RING_RADIUS * (0.30f + normalizedTime * 0.70f);
        float ringThickness = WeaponConstants.RAILGUN_EFFECT_RING_THICKNESS;
        float ringFlatten   = WeaponConstants.RAILGUN_EFFECT_RING_FLATTEN;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // 1. Recoil ring — flattened cyan halo expanding outward at the muzzle.
        //    ShapeRenderer has no ring primitive, so the band is built from per-segment
        //    quads around the perimeter. Each quad spans outer/inner radius across one
        //    angular step; the two triangles share the outer-start→inner-end diagonal so
        //    the winding stays consistent and the band is gap-free.
        float ringInner = ringRadius - ringThickness;
        shapeRenderer.setColor(0.30f, 0.78f, 1.00f, alpha * 0.45f);
        int ringSegments = WeaponConstants.RAILGUN_EFFECT_RING_SEGMENTS;
        for (int segmentIndex = 0; segmentIndex < ringSegments; segmentIndex++) {
            float angleStartRadians = (float) (Math.PI * 2.0 * segmentIndex / ringSegments);
            float angleEndRadians   = (float) (Math.PI * 2.0 * (segmentIndex + 1) / ringSegments);
            float cosStart = (float) Math.cos(angleStartRadians);
            float sinStart = (float) Math.sin(angleStartRadians);
            float cosEnd   = (float) Math.cos(angleEndRadians);
            float sinEnd   = (float) Math.sin(angleEndRadians);
            float outerStartX = barrelX + cosStart * ringRadius;
            float outerStartY = barrelY + sinStart * ringRadius * ringFlatten;
            float outerEndX   = barrelX + cosEnd * ringRadius;
            float outerEndY   = barrelY + sinEnd * ringRadius * ringFlatten;
            float innerStartX = barrelX + cosStart * ringInner;
            float innerStartY = barrelY + sinStart * ringInner * ringFlatten;
            float innerEndX   = barrelX + cosEnd * ringInner;
            float innerEndY   = barrelY + sinEnd * ringInner * ringFlatten;
            shapeRenderer.triangle(outerStartX, outerStartY, outerEndX, outerEndY, innerEndX, innerEndY);
            shapeRenderer.triangle(outerStartX, outerStartY, innerEndX, innerEndY, innerStartX, innerStartY);
        }

        // 2. Outer electric glow halo — soft wide translucent cyan cone behind the lance.
        shapeRenderer.setColor(0.20f, 0.55f, 1.00f, alpha * 0.22f);
        shapeRenderer.triangle(
            barrelX - lanceHalfBase * 4.0f, barrelY,
            barrelX + lanceHalfBase * 4.0f, barrelY,
            barrelX,                         barrelY + lanceHeight * 0.80f
        );

        // 3. Branching lightning arcs — zig-zag bolts kinking outward to each side.
        //    Each branch advances upward in segments; lateral offset = base lean toward the
        //    arc target plus a per-segment jitter kink from the static fractions table.
        drawRailgunLightningBranch(barrelX, barrelY, -arcSpread, arcHeight, alpha);
        drawRailgunLightningBranch(barrelX, barrelY, +arcSpread, arcHeight, alpha);

        // 4. Main lance — crisp white-cyan, tall and very narrow.
        shapeRenderer.setColor(0.55f, 0.85f, 1.00f, alpha * 0.85f);
        shapeRenderer.triangle(
            barrelX - lanceHalfBase * 1.4f, barrelY,
            barrelX + lanceHalfBase * 1.4f, barrelY,
            barrelX,                         barrelY + lanceHeight * 0.90f
        );

        // 5. Lance bright core — near-white with a faint cyan tint.
        shapeRenderer.setColor(0.90f, 0.97f, 1.00f, alpha * 0.92f);
        shapeRenderer.triangle(
            barrelX - lanceHalfBase * 0.7f, barrelY,
            barrelX + lanceHalfBase * 0.7f, barrelY,
            barrelX,                         barrelY + lanceHeight
        );

        // 6. Hottest tip — pure white, razor-thin.
        shapeRenderer.setColor(1.00f, 1.00f, 1.00f, alpha * 0.80f);
        shapeRenderer.triangle(
            barrelX - lanceHalfBase * 0.32f, barrelY,
            barrelX + lanceHalfBase * 0.32f, barrelY,
            barrelX,                          barrelY + lanceHeight * 0.72f
        );

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Draws a single zig-zag lightning branch for the railgun discharge as a chain of
     * thin quads (two triangles each). The branch climbs from the muzzle to (lateralReach,
     * verticalReach) in RAILGUN_EFFECT_BRANCH_SEGMENTS steps; each joint is kicked sideways
     * by a deterministic jitter read from RAILGUN_BRANCH_JITTER_FRACTIONS (no allocation).
     *
     * @param originX        muzzle X in world units
     * @param originY        muzzle Y in world units
     * @param lateralReach   signed final horizontal offset of the branch tip (sign = side)
     * @param verticalReach  final height of the branch tip above the muzzle
     * @param alpha          current fade alpha (1 at fire start, 0 at end)
     */
    private void drawRailgunLightningBranch(float originX, float originY,
                                            float lateralReach, float verticalReach, float alpha) {
        int   segmentCount  = WeaponConstants.RAILGUN_EFFECT_BRANCH_SEGMENTS;
        float jitter        = WeaponConstants.RAILGUN_EFFECT_BRANCH_JITTER;
        float boltHalfWidth = WeaponConstants.RAILGUN_EFFECT_BOLT_HALF_WIDTH;

        float previousX = originX;
        float previousY = originY;
        for (int segmentIndex = 1; segmentIndex <= segmentCount; segmentIndex++) {
            float progress      = (float) segmentIndex / segmentCount;
            float jitterFraction = RAILGUN_BRANCH_JITTER_FRACTIONS[segmentIndex % RAILGUN_BRANCH_JITTER_FRACTIONS.length];
            // Tip snaps back to the straight target (no jitter) so the branch end is clean.
            float kink          = (segmentIndex == segmentCount) ? 0f : jitterFraction * jitter;
            float nextX         = originX + lateralReach * progress + kink;
            float nextY         = originY + verticalReach * progress;

            // Outer dim cyan glow stroke
            shapeRenderer.setColor(0.40f, 0.72f, 1.00f, alpha * 0.55f);
            shapeRenderer.triangle(previousX - boltHalfWidth, previousY,
                                   previousX + boltHalfWidth, previousY,
                                   nextX + boltHalfWidth, nextY);
            shapeRenderer.triangle(previousX - boltHalfWidth, previousY,
                                   nextX + boltHalfWidth, nextY,
                                   nextX - boltHalfWidth, nextY);
            // Inner bright white-cyan core stroke
            shapeRenderer.setColor(0.82f, 0.95f, 1.00f, alpha * 0.78f);
            shapeRenderer.triangle(previousX - boltHalfWidth * 0.45f, previousY,
                                   previousX + boltHalfWidth * 0.45f, previousY,
                                   nextX + boltHalfWidth * 0.45f, nextY);
            shapeRenderer.triangle(previousX - boltHalfWidth * 0.45f, previousY,
                                   nextX + boltHalfWidth * 0.45f, nextY,
                                   nextX - boltHalfWidth * 0.45f, nextY);

            previousX = nextX;
            previousY = nextY;
        }
    }

    /**
     * Draws a large lingering wide flame for the Incinerator.
     *
     * Wider base, more intense orange-red saturation, and a slower shrink rate
     * (INCINERATOR_EFFECT_SHRINK_RATE = 0.25) so the flame fills more screen space
     * for longer. Wide outer tongues sell the cone shape of a flamethrower stream.
     * All layers fade as normalizedTime approaches 1.
     */
    private void renderIncineratorEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha    = 1f - normalizedTime;
        float scale    = 1f - normalizedTime * WeaponConstants.INCINERATOR_EFFECT_SHRINK_RATE;
        float barrelX  = Constants.WORLD_WIDTH / 2f;
        float barrelY  = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                         * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;
        float height   = WeaponConstants.INCINERATOR_EFFECT_FLAME_HEIGHT * scale;
        float halfBase = WeaponConstants.INCINERATOR_EFFECT_FLAME_BASE_WIDTH / 2f * scale;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Outer translucent heat shimmer disc — very wide and flat
        shapeRenderer.setColor(1.00f, 0.55f, 0.08f, alpha * 0.30f);
        shapeRenderer.ellipse(barrelX - halfBase * 1.10f, barrelY - halfBase * 0.22f,
                              halfBase * 2.20f, halfBase * 0.44f);

        // Wide deep-red outer cone — fullest spread of the flame stream
        shapeRenderer.setColor(0.80f, 0.06f, 0.00f, alpha * 0.68f);
        shapeRenderer.triangle(
            barrelX - halfBase, barrelY,
            barrelX + halfBase, barrelY,
            barrelX,            barrelY + height * 0.42f
        );

        // Left spreading tongue
        shapeRenderer.setColor(0.92f, 0.18f, 0.00f, alpha * 0.72f);
        shapeRenderer.triangle(
            barrelX - halfBase,         barrelY,
            barrelX - halfBase * 0.10f, barrelY,
            barrelX - halfBase * 0.70f, barrelY + height * 0.55f
        );

        // Right spreading tongue
        shapeRenderer.setColor(0.92f, 0.18f, 0.00f, alpha * 0.72f);
        shapeRenderer.triangle(
            barrelX + halfBase * 0.10f, barrelY,
            barrelX + halfBase,         barrelY,
            barrelX + halfBase * 0.70f, barrelY + height * 0.55f
        );

        // Main orange flame body
        shapeRenderer.setColor(1.00f, 0.46f, 0.00f, alpha * 0.82f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.72f, barrelY,
            barrelX + halfBase * 0.72f, barrelY,
            barrelX,                    barrelY + height * 0.78f
        );

        // Bright orange-yellow mid cone
        shapeRenderer.setColor(1.00f, 0.68f, 0.04f, alpha * 0.88f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.45f, barrelY,
            barrelX + halfBase * 0.45f, barrelY,
            barrelX,                    barrelY + height * 0.92f
        );

        // Yellow hot core
        shapeRenderer.setColor(1.00f, 0.90f, 0.12f, alpha * 0.90f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.24f, barrelY,
            barrelX + halfBase * 0.24f, barrelY,
            barrelX,                    barrelY + height
        );

        // White-yellow superheated tip
        shapeRenderer.setColor(1.00f, 0.98f, 0.70f, alpha * 0.72f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.12f, barrelY,
            barrelX + halfBase * 0.12f, barrelY,
            barrelX,                    barrelY + height * 0.68f
        );

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Draws a thick smoke-puff and blast explosion for the Grenade Launcher.
     *
     * Three distinct layers: a grey-white outer smoke puff ellipse, an orange/yellow
     * core explosion, and two rising smoke wisps. The outer puff is the most prominent
     * element — reading as a thick propellant cloud rather than a clean muzzle blast.
     * All layers fade as normalizedTime approaches 1.
     */
    private void renderGrenadeLauncherEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha        = 1f - normalizedTime;
        float scale        = 1f - normalizedTime * 0.55f;
        float barrelX      = Constants.WORLD_WIDTH / 2f;
        float barrelY      = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                             * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;
        float puffRadius   = WeaponConstants.GRENADE_EFFECT_PUFF_RADIUS * scale;
        float coreRadius   = WeaponConstants.GRENADE_EFFECT_CORE_RADIUS * scale;
        float wispHeight   = WeaponConstants.GRENADE_EFFECT_WISP_HEIGHT * scale;
        float wispHalfBase = WeaponConstants.GRENADE_EFFECT_WISP_BASE_WIDTH / 2f * scale;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Outer grey-white smoke puff — large flat ellipse
        shapeRenderer.setColor(0.75f, 0.72f, 0.68f, alpha * 0.42f);
        shapeRenderer.ellipse(barrelX - puffRadius, barrelY - puffRadius * 0.50f,
                              puffRadius * 2f, puffRadius);

        // Secondary lighter smoke ring
        shapeRenderer.setColor(0.90f, 0.88f, 0.85f, alpha * 0.32f);
        shapeRenderer.ellipse(barrelX - puffRadius * 0.72f, barrelY - puffRadius * 0.36f,
                              puffRadius * 1.44f, puffRadius * 0.72f);

        // Orange core explosion — rounder blast ball
        shapeRenderer.setColor(1.00f, 0.44f, 0.00f, alpha * 0.80f);
        shapeRenderer.ellipse(barrelX - coreRadius, barrelY - coreRadius * 0.60f,
                              coreRadius * 2f, coreRadius * 1.20f);

        // Yellow bright inner core
        shapeRenderer.setColor(1.00f, 0.82f, 0.10f, alpha * 0.88f);
        shapeRenderer.ellipse(barrelX - coreRadius * 0.58f, barrelY - coreRadius * 0.35f,
                              coreRadius * 1.16f, coreRadius * 0.70f);

        // White-hot centre of the blast
        shapeRenderer.setColor(1.00f, 0.96f, 0.70f, alpha * 0.78f);
        shapeRenderer.ellipse(barrelX - coreRadius * 0.28f, barrelY - coreRadius * 0.17f,
                              coreRadius * 0.56f, coreRadius * 0.34f);

        // Left rising smoke wisp
        shapeRenderer.setColor(0.62f, 0.60f, 0.58f, alpha * 0.48f);
        shapeRenderer.triangle(
            barrelX - puffRadius * 0.65f - wispHalfBase, barrelY,
            barrelX - puffRadius * 0.65f + wispHalfBase, barrelY,
            barrelX - puffRadius * 0.85f,                 barrelY + wispHeight
        );

        // Right rising smoke wisp
        shapeRenderer.triangle(
            barrelX + puffRadius * 0.65f - wispHalfBase, barrelY,
            barrelX + puffRadius * 0.65f + wispHalfBase, barrelY,
            barrelX + puffRadius * 0.85f,                 barrelY + wispHeight
        );

        // Centre upward smoke column
        shapeRenderer.setColor(0.72f, 0.70f, 0.68f, alpha * 0.38f);
        shapeRenderer.triangle(
            barrelX - wispHalfBase * 0.8f, barrelY,
            barrelX + wispHalfBase * 0.8f, barrelY,
            barrelX,                        barrelY + wispHeight * 0.85f
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
        float barrelY  = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                         * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;
        float height   = WeaponConstants.WEAPON_FLAME_HEIGHT * scale;
        float halfBase = WeaponConstants.WEAPON_FLAME_BASE_WIDTH * scale;

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
        for (Texture texture : weaponTextureCache.values()) {
            texture.dispose();
        }
        batch.dispose();
        shapeRenderer.dispose();
    }
}

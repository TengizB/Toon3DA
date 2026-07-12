package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.AssaultRifle;
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
import ge.tbegvadze.toon3d.util.GameMath;

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

    // Chaingun barrel-spin state (real per-frame motion, not a static pose). The rotor angle
    // integrates the current spin speed each frame; the speed ramps toward the firing target
    // (spin-up) and back toward zero when firing stops (wind-down). The angle selects which
    // baked rotation frame of the sprite sheet is sampled — see render(). Advanced only while a
    // Chaingun is equipped; both stay at rest for every other weapon.
    private float chaingunRotorAngleDegrees           = 0f;
    private float chaingunRotorSpeedDegreesPerSecond  = 0f;

    // Plasma-rifle idle "breathing" clock. Advanced by deltaTime each frame ONLY while a PlasmaRifle
    // is equipped and its visual state is NORMAL; frozen (value retained, not reset) during FIRING /
    // RELOADING so the resting glow resumes smoothly and never fights the muzzle burst. Feeds
    // GameMath.pulseMultiplier() to modulate the live emitter halo + coil shimmer overlay. Scalar
    // float only — no allocation. Sits idle for every other weapon.
    private float plasmaIdlePulseTimeSeconds = 0f;

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

    // Assault-rifle casing scatter — static to avoid per-frame heap allocation.
    // Each pair (CASING_FRACTIONS_X[i], CASING_FRACTIONS_Y[i]) is a fraction of the casing
    // spread constants applied from the barrel origin. Casings eject to the RIGHT (positive X)
    // and fall downward (negative Y) as normalizedTime advances, so the X fractions are all
    // positive and the static Y fractions are the launch heights they descend from.
    private static final float[] ASSAULT_RIFLE_CASING_FRACTIONS_X = {
        0.45f, 0.72f, 0.95f
    };
    private static final float[] ASSAULT_RIFLE_CASING_FRACTIONS_Y = {
        0.55f, 0.80f, 0.40f
    };

    // Railgun lightning branch jitter — static to avoid per-frame heap allocation.
    // Each value is a signed fraction of RAILGUN_EFFECT_BRANCH_JITTER applied as a
    // perpendicular kink at successive segments along a branching arc, producing a
    // zig-zag lightning shape without using java.util.Random. Indexed by segment.
    private static final float[] RAILGUN_BRANCH_JITTER_FRACTIONS = {
        0.0f, 0.85f, -0.55f, 0.40f, -0.20f
    };

    // Incinerator flame-tongue jitter — static to avoid per-frame heap allocation.
    // The flame effect animates by stepping through these signed fractions as the fire
    // flickers, applied to tongue heights and lateral tip offsets so each frame's flame
    // looks alive without java.util.Random. The animation cursor is animationTimer, so the
    // sample index advances continuously while firing. Prime-length (7) avoids visible
    // repetition lining up with the layer count.
    private static final float[] INCINERATOR_FLAME_JITTER = {
        0.18f, -0.42f, 0.30f, -0.10f, 0.46f, -0.28f, 0.08f
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
        if (weapon instanceof AssaultRifle) {
            return generateAssaultRifleTexture();
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
     * Draws a high-resolution plasma rifle in Quake-1 top-down first-person perspective on the
     * 288×201 canvas. Energy weapon, top-down view. No bore holes — the muzzle ends with a layered
     * concentric emitter (deep-blue outer ring through white-cyan core). The player sees the flat top
     * surface of a tapered steel-blue body threaded with bright cyan coils and energy conduits.
     *
     * Every element is bilaterally symmetric about centerX (= 144). Barrel convergence: half-width
     * tapers from 39px at Y=130 to 21px at Y=180 (factor ≈ 0.54, energy-weapon perspective).
     *
     *   Y=0..22    transparent (grip cut off below screen)
     *   Y=22..112  main body: chamfered flanks, panel greebles, 5 energy coils, dual power cells
     *   Y=100..140 energy conduits routing coil energy up the spine
     *   Y=108..134 upper receiver stepped section + targeting scope
     *   Y=130..180 tapered barrel with ribbed retaining-band shroud + cylinder shading
     *   Y=172..199 muzzle prongs and layered concentric emitter
     */
    private static void drawPlasmaRifleShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Main body — wide steel-blue trapezoid, top-surface perspective.
        //    Wider at near end (Y=22) than far end (Y=112, upper receiver join).
        shapeRenderer.setColor(0.28f, 0.32f, 0.42f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 90f, 22f, 78f, 112f);
        // Chamfered side flanks — dark angled bevels down both edges (cylinder-body read).
        shapeRenderer.setColor(0.20f, 0.23f, 0.31f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 90f, centerX - 80f, 22f,
                                            centerX - 78f, centerX - 69f, 112f);   // left flank bevel
        drawGeneralTrapezoid(shapeRenderer, centerX + 80f, centerX + 90f, 22f,
                                            centerX + 69f, centerX + 78f, 112f);   // right flank bevel
        // Far-edge highlight: top surface faces upward toward camera.
        shapeRenderer.setColor(0.40f, 0.46f, 0.58f, 1f);
        shapeRenderer.rect(centerX - 78f, 108f, 156f, 4f);
        // Near-edge shadow: underside curves away from camera.
        shapeRenderer.setColor(0.18f, 0.21f, 0.29f, 1f);
        shapeRenderer.rect(centerX - 90f, 22f, 180f, 4f);
        // Lateral surface grooves visible from above.
        shapeRenderer.setColor(0.20f, 0.24f, 0.32f, 1f);
        shapeRenderer.rect(centerX - 84f, 58f, 168f, 3f);
        shapeRenderer.rect(centerX - 82f, 96f, 164f, 3f);

        // 2. Panel greebles — vertical seams dividing the body into a central panel, plus rivets.
        shapeRenderer.setColor(0.16f, 0.19f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 40f, 26f, 2f, 82f);    // left  seam  [CX-40 .. CX-38]
        shapeRenderer.rect(centerX + 38f, 26f, 2f, 82f);    // right seam  [CX+38 .. CX+40] ✓ mirror
        shapeRenderer.setColor(0.34f, 0.38f, 0.48f, 1f);
        shapeRenderer.rect(centerX - 36f, 30f, 3f, 3f);     // rivet lower-left
        shapeRenderer.rect(centerX + 33f, 30f, 3f, 3f);     // rivet lower-right [CX+33 .. CX+36] ✓
        shapeRenderer.rect(centerX - 36f, 101f, 3f, 3f);    // rivet upper-left
        shapeRenderer.rect(centerX + 33f, 101f, 3f, 3f);    // rivet upper-right ✓

        // 3. Energy coils — 5 bright cyan horizontal bands across body (seen from above).
        float[] coilYPositions = {32f, 46f, 60f, 74f, 88f};
        shapeRenderer.setColor(0.00f, 0.88f, 1.00f, 1f);
        for (float coilY : coilYPositions) {
            shapeRenderer.rect(centerX - 72f, coilY, 144f, 4f);
        }
        // Soft glow fringe above and below each coil.
        shapeRenderer.setColor(0.00f, 0.62f, 0.90f, 0.50f);
        for (float coilY : coilYPositions) {
            shapeRenderer.rect(centerX - 72f, coilY - 2f, 144f, 2f);   // fringe below
            shapeRenderer.rect(centerX - 72f, coilY + 4f, 144f, 2f);   // fringe above
        }

        // 4. Energy conduits — cyan channels routing coil energy up the spine into the barrel.
        shapeRenderer.setColor(0.00f, 0.72f, 1.00f, 0.90f);
        shapeRenderer.rect(centerX - 30f, 100f, 5f, 42f);   // left  conduit  [CX-30 .. CX-25]
        shapeRenderer.rect(centerX + 25f, 100f, 5f, 42f);   // right conduit  [CX+25 .. CX+30] ✓ mirror
        shapeRenderer.setColor(0.00f, 0.62f, 1.00f, 0.95f);
        shapeRenderer.rect(centerX - 3f, 108f, 6f, 66f);    // central spine channel [CX-3 .. CX+3]
        shapeRenderer.setColor(0.00f, 0.55f, 0.95f, 0.40f);
        shapeRenderer.rect(centerX - 5f, 108f, 2f, 66f);    // spine glow fringe left  [CX-5 .. CX-3]
        shapeRenderer.rect(centerX + 3f, 108f, 2f, 66f);    // spine glow fringe right [CX+3 .. CX+5] ✓

        // 5. Power cell indicators — symmetric flanking housings with segmented charge bars.
        //    left housing  [CX-92 .. CX-78];  right housing [CX+78 .. CX+92] ✓ symmetric
        //    left bars     [CX-89 .. CX-81];  right bars    [CX+81 .. CX+89] ✓ symmetric
        shapeRenderer.setColor(0.16f, 0.20f, 0.28f, 1f);
        shapeRenderer.rect(centerX - 92f, 30f, 14f, 60f);   // left  housing
        shapeRenderer.rect(centerX + 78f, 30f, 14f, 60f);   // right housing
        float[] chargeBarYPositions = {34f, 43f, 52f, 61f, 70f, 79f};
        shapeRenderer.setColor(0.00f, 0.72f, 1.00f, 0.95f);
        for (float barY : chargeBarYPositions) {
            shapeRenderer.rect(centerX - 89f, barY, 8f, 6f);   // left  bar
            shapeRenderer.rect(centerX + 81f, barY, 8f, 6f);   // right bar ✓ mirror
        }
        // Top "fully charged" segment brighter (white-cyan).
        shapeRenderer.setColor(0.70f, 0.96f, 1.00f, 1f);
        shapeRenderer.rect(centerX - 89f, 79f, 8f, 6f);
        shapeRenderer.rect(centerX + 81f, 79f, 8f, 6f);

        // 6. Upper receiver — stepped section between body and barrel.
        shapeRenderer.setColor(0.26f, 0.30f, 0.40f, 1f);
        shapeRenderer.rect(centerX - 66f, 108f, 132f, 26f);
        shapeRenderer.setColor(0.24f, 0.28f, 0.38f, 1f);
        shapeRenderer.rect(centerX - 54f, 112f, 108f, 18f);  // inset sub-step
        shapeRenderer.setColor(0.38f, 0.44f, 0.56f, 1f);
        shapeRenderer.rect(centerX - 66f, 130f, 132f, 4f);   // far-edge bevel highlight
        shapeRenderer.setColor(0.16f, 0.18f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 66f, 108f, 132f, 4f);   // near-edge shadow

        // 7. Targeting scope — centred housing on upper receiver, visible from above.
        shapeRenderer.setColor(0.22f, 0.26f, 0.34f, 1f);
        shapeRenderer.rect(centerX - 30f, 112f, 4f, 20f);    // left  scope rail [CX-30 .. CX-26]
        shapeRenderer.rect(centerX + 26f, 112f, 4f, 20f);    // right scope rail [CX+26 .. CX+30] ✓
        shapeRenderer.setColor(0.16f, 0.19f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 27f, 110f, 54f, 24f);   // scope housing
        shapeRenderer.setColor(0.10f, 0.82f, 0.58f, 0.90f);
        shapeRenderer.rect(centerX - 21f, 113f, 42f, 18f);   // green lens surface
        shapeRenderer.setColor(0.40f, 1.00f, 0.75f, 0.55f);
        shapeRenderer.rect(centerX - 21f, 127f, 42f, 3f);    // lens far-edge highlight

        // 8. Barrel — tapered trapezoid viewed from above (wide near Y=130, narrow far Y=180).
        shapeRenderer.setColor(0.28f, 0.32f, 0.42f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 39f, 130f, 21f, 180f);
        // Outer edge shadows — cylinder sides curve away, tapered with the barrel.
        shapeRenderer.setColor(0.16f, 0.18f, 0.26f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 39f, centerX - 31f, 130f,
                                            centerX - 21f, centerX - 16f, 180f);   // left edge shadow
        drawGeneralTrapezoid(shapeRenderer, centerX + 31f, centerX + 39f, 130f,
                                            centerX + 16f, centerX + 21f, 180f);   // right edge shadow ✓
        // Crown highlight — top of the cylinder faces the camera, tapered.
        shapeRenderer.setColor(0.42f, 0.48f, 0.60f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 10f, centerX + 10f, 130f,
                                            centerX - 6f, centerX + 6f, 180f);

        // 9. Ribbed shroud — retaining bands wrapping the barrel at intervals (full width at each Y).
        //    Half-width interpolated along the 39→21 taper. Each band: dark rect + thin top highlight.
        float[] bandYPositions     = {138f, 150f, 162f, 174f};
        float[] bandHalfWidths     = {36f, 32f, 27f, 23f};
        for (int bandIndex = 0; bandIndex < bandYPositions.length; bandIndex++) {
            float bandY    = bandYPositions[bandIndex];
            float bandHalf = bandHalfWidths[bandIndex];
            shapeRenderer.setColor(0.18f, 0.20f, 0.28f, 1f);
            shapeRenderer.rect(centerX - bandHalf, bandY, bandHalf * 2f, 3f);
            shapeRenderer.setColor(0.36f, 0.42f, 0.54f, 1f);
            shapeRenderer.rect(centerX - bandHalf, bandY + 3f, bandHalf * 2f, 1f);
        }

        // 10. Muzzle prongs — symmetric stepped flanges flanking the emitter.
        shapeRenderer.setColor(0.22f, 0.26f, 0.34f, 1f);
        shapeRenderer.rect(centerX - 39f, 172f, 12f, 18f);   // left  prong [CX-39 .. CX-27]
        shapeRenderer.rect(centerX + 27f, 172f, 12f, 18f);   // right prong [CX+27 .. CX+39] ✓
        shapeRenderer.setColor(0.30f, 0.34f, 0.44f, 1f);
        shapeRenderer.rect(centerX - 39f, 186f, 12f, 3f);    // left  prong tip highlight
        shapeRenderer.rect(centerX + 27f, 186f, 12f, 3f);    // right prong tip highlight ✓
        shapeRenderer.setColor(0.18f, 0.21f, 0.29f, 1f);
        shapeRenderer.rect(centerX - 30f, 176f, 6f, 14f);    // left  prong inner step [CX-30 .. CX-24]
        shapeRenderer.rect(centerX + 24f, 176f, 6f, 14f);    // right prong inner step [CX+24 .. CX+30] ✓

        // 11. Muzzle emitter — concentric ellipses deep-blue -> bright-cyan -> white-cyan -> white.
        //     Energy weapon: emitter face replaces the bore concept entirely. All ellipses centred
        //     on centerX (x = centerX - width/2) so the emitter is exactly symmetric.
        shapeRenderer.setColor(0.08f, 0.52f, 1.00f, 0.30f);
        shapeRenderer.ellipse(centerX - 26f, 172f, 52f, 18f);   // under-glow halo (drawn first)
        shapeRenderer.setColor(0.18f, 0.22f, 0.30f, 1f);
        shapeRenderer.ellipse(centerX - 22f, 176f, 44f, 22f);   // dark outer housing ring
        shapeRenderer.setColor(0.08f, 0.52f, 1.00f, 0.95f);
        shapeRenderer.ellipse(centerX - 18f, 178f, 36f, 18f);   // outer deep-blue glow
        shapeRenderer.setColor(0.30f, 0.82f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX - 14f, 180f, 28f, 15f);   // mid bright-cyan ring
        shapeRenderer.setColor(0.75f, 0.97f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX - 9f, 182f, 18f, 12f);    // hot white-cyan core
        shapeRenderer.setColor(1.00f, 1.00f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX - 5f, 184f, 10f, 8f);     // hottest pinpoint
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
     * Generates a symmetric service assault-rifle sprite using ShapeRenderer into an
     * offscreen FrameBuffer.  Quake-1 top-down first-person perspective: the camera sits
     * slightly above-and-behind the weapon, the single barrel points AWAY toward the
     * horizon, and the grip/stock are cut off below the screen edge (Y=0..14 transparent).
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (grip region — transparent, cut off)
     *   Y = 134 → top of canvas (muzzle tip, farthest from player)
     *
     * Layout zones (Y-up, centerX=96):
     *   Y  0– 14  transparent       — grip/stock cut off below screen
     *   Y 14– 28  curved magazine    — signature feature, dropping below the receiver
     *   Y 14– 62  lower receiver     — flat-top gunmetal body with ejection port
     *   Y 56– 70  upper receiver     — stepped flat-top with carry-handle sight
     *   Y 68– 96  slotted handguard  — perspective-tapered, cooling-slot rows
     *   Y 92–128  single barrel tube — narrow (1:3 ratio) top surface pointing away
     *   Y 126–128 muzzle cap         — 2px bright steel rim (NO bore: barrel points away)
     *
     * Full layer breakdown is documented on drawAssaultRifleShape().
     */
    private static Texture generateAssaultRifleTexture() {
        int canvasWidth  = WeaponConstants.ASSAULT_RIFLE_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.ASSAULT_RIFLE_CANVAS_HEIGHT;

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
        drawAssaultRifleShape(temporaryShapeRenderer, canvasWidth / 2f);
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
     * Draws a gunmetal service assault rifle in Quake-1 top-down first-person perspective.
     *
     * Single-barrel ballistic weapon, top-down view. The barrel points AWAY from the player
     * toward the horizon, so the bore face is invisible — only a 2px bright muzzle cap is
     * drawn at the tip, never a bore-hole ellipse. The defining silhouette elements are a
     * narrow tall single barrel (≈1:3 ratio), a slotted handguard with cooling slots, a
     * flat-top receiver with a carry-handle rear sight, and — the signature feature — a
     * curved magazine dropping below the receiver near the screen-bottom cut-off.
     *
     * Perspective convergence factor 0.65 (top-down): every x-offset from centerX scales by
     * 0.65 from the barrel base (Y=92) to the muzzle (Y=128). Shading strips share the same
     * taper so the cylinder edges stay parallel in perspective.
     *
     * Symmetry invariant: every element is mirrored about centerX=96. The curved magazine is
     * the only intentionally centred-but-swept element; its sweep is symmetric column pairs.
     *
     * Layer order (back-to-front):
     *   1. Curved magazine        Y=14..30  — drawn first so the receiver overlaps its top
     *   2. Lower receiver body    Y=14..62  — wide gunmetal trapezoid, top surface
     *   3. Receiver detailing     Y=14..62  — edge shading, ejection port, fire-selector
     *   4. Upper receiver         Y=56..70  — stepped flat-top section
     *   5. Carry-handle sight     Y=62..72  — centred rear-sight housing
     *   6. Slotted handguard      Y=68..96  — tapered tube with twin cooling-slot rows
     *   7. Barrel tube            Y=92..128 — narrow tapered cylinder, top-surface shading
     *   8. Front sight post       Y=116..124— centred bright post near the muzzle
     *   9. Muzzle cap             Y=126..128— 2px bright steel rim (no bore, points away)
     */
    private static void drawAssaultRifleShape(ShapeRenderer shapeRenderer, float centerX) {

        // Y=0..14 left transparent — grip/stock cut off below screen (eyes above the gun).

        // 1. Curved magazine — the signature feature, sweeping down and forward (left) below the
        //    receiver. Built from stacked rects whose left edge sweeps leftward as Y decreases,
        //    giving the classic banana-magazine curve. Each rect pair stays mirror-consistent in
        //    width about its own swept centre so the silhouette reads as a single curved box.
        //    The magazine body centre drifts from centerX-2 (top, Y=28) to centerX-12 (bottom, Y=14).
        shapeRenderer.setColor(0.20f, 0.21f, 0.24f, 1f);                       // dark charcoal magazine
        shapeRenderer.rect(centerX - 13f, 26f, 22f, 4f);                       // top of magazine (seats into receiver)
        shapeRenderer.rect(centerX - 16f, 22f, 22f, 4f);                       // curve segment 1
        shapeRenderer.rect(centerX - 19f, 18f, 22f, 4f);                       // curve segment 2
        shapeRenderer.rect(centerX - 22f, 14f, 22f, 4f);                       // bottom (runs off screen edge)
        shapeRenderer.setColor(0.30f, 0.32f, 0.36f, 1f);                       // front-edge highlight (catches light)
        shapeRenderer.rect(centerX +  7f, 26f, 2f, 4f);                        // mag top front rib
        shapeRenderer.rect(centerX +  4f, 22f, 2f, 4f);                        // mag front rib 1
        shapeRenderer.rect(centerX +  1f, 18f, 2f, 4f);                        // mag front rib 2
        shapeRenderer.setColor(0.12f, 0.13f, 0.15f, 1f);                       // back-edge shadow
        shapeRenderer.rect(centerX - 13f, 26f, 2f, 4f);                        // mag back rib top
        shapeRenderer.rect(centerX - 16f, 22f, 2f, 4f);                        // mag back rib 1
        shapeRenderer.rect(centerX - 19f, 18f, 2f, 4f);                        // mag back rib 2

        // 2. Lower receiver body — wide gunmetal trapezoid, wider at the near end (closer to viewer).
        shapeRenderer.setColor(0.30f, 0.32f, 0.36f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 40f, 14f, 34f, 62f);

        // 3. Receiver detailing — edge shading, ejection port, magazine well, fire-selector.
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 34f, 59f, 68f, 3f);                       // far-edge top highlight
        shapeRenderer.setColor(0.12f, 0.13f, 0.17f, 1f);
        shapeRenderer.rect(centerX - 40f, 14f, 80f, 3f);                       // near-edge bottom shadow
        shapeRenderer.setColor(0.18f, 0.20f, 0.24f, 1f);
        shapeRenderer.rect(centerX - 36f, 38f, 72f, 2f);                       // mid-body groove
        // Magazine well — dark recess the magazine seats into (centred above the curved mag).
        shapeRenderer.setColor(0.10f, 0.11f, 0.13f, 1f);
        shapeRenderer.rect(centerX - 14f, 26f, 28f, 6f);                       // magazine well mouth
        // Ejection port — dark recessed slot on the right flank with a bright lower lip.
        shapeRenderer.setColor(0.08f, 0.09f, 0.11f, 1f);
        shapeRenderer.rect(centerX + 14f, 44f, 20f, 10f);                      // ejection port cavity
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX + 14f, 44f, 20f, 1f);                       // port lower lip shine
        // Fire-selector — small bright detail on the left flank, mirrored from the port side.
        shapeRenderer.setColor(0.52f, 0.56f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 32f, 46f, 6f, 6f);                        // selector knob
        shapeRenderer.setColor(0.18f, 0.19f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 31f, 47f, 4f, 4f);                        // selector recess

        // 4. Upper receiver — stepped flat-top section above the lower body (Y=56..70).
        shapeRenderer.setColor(0.26f, 0.28f, 0.33f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 30f, 56f, 26f, 70f);
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        shapeRenderer.rect(centerX - 26f, 67f, 52f, 3f);                       // far-edge bevel highlight
        shapeRenderer.setColor(0.16f, 0.18f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 30f, 56f, 60f, 2f);                       // near-edge step shadow

        // 5. Carry-handle rear sight — centred housing on the upper receiver, seen from above.
        shapeRenderer.setColor(0.16f, 0.18f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 12f, 62f, 24f, 10f);                      // rear-sight housing
        shapeRenderer.setColor(0.34f, 0.38f, 0.44f, 1f);
        shapeRenderer.rect(centerX - 12f, 70f, 24f, 2f);                       // housing top highlight
        shapeRenderer.setColor(0.05f, 0.05f, 0.06f, 1f);
        shapeRenderer.rect(centerX - 3f, 64f, 6f, 6f);                         // rear-sight aperture (centred)

        // 6. Slotted handguard — perspective-tapered tube wrapping the barrel base (Y=68..96,
        //    factor 0.65). Half-width 24px at base → tapered toward the far edge.
        shapeRenderer.setColor(0.27f, 0.29f, 0.34f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 24f, 68f, 19f, 96f);
        // Outer-edge shadows (cylinder curves away on each flank), tapered with the same factor.
        shapeRenderer.setColor(0.13f, 0.14f, 0.18f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 24f, centerX - 19f, 68f,
                                            centerX - 19f, centerX - 15f, 96f);  // left edge shadow
        drawGeneralTrapezoid(shapeRenderer, centerX + 19f, centerX + 24f, 68f,
                                            centerX + 15f, centerX + 19f, 96f);  // right edge shadow
        // Crown highlight (top of cylinder faces the camera): 12px base → tapered.
        shapeRenderer.setColor(0.42f, 0.46f, 0.52f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 6f, centerX + 6f, 68f,
                                            centerX - 5f, centerX + 5f, 96f);    // crown highlight
        // Cooling slots — twin symmetric rows of dark vents marching up both flanks.
        //    Left x = CX-18, right x = CX+12 (width 6 → centres CX-15 / CX+15, mirrored).
        float[] handguardSlotYPositions = {72f, 79f, 86f};
        for (float slotY : handguardSlotYPositions) {
            shapeRenderer.setColor(0.06f, 0.07f, 0.09f, 1f);
            shapeRenderer.rect(centerX - 18f, slotY, 6f, 4f);                  // left cooling slot
            shapeRenderer.rect(centerX + 12f, slotY, 6f, 4f);                  // right cooling slot
            shapeRenderer.setColor(0.38f, 0.42f, 0.48f, 0.55f);
            shapeRenderer.rect(centerX - 18f, slotY + 4f, 6f, 1f);             // left slot rim shine
            shapeRenderer.rect(centerX + 12f, slotY + 4f, 6f, 1f);             // right slot rim shine
        }

        // 7. Barrel tube — single narrow tapered cylinder (base Y=92, muzzle Y=128, factor 0.65).
        //    Half-width 9px at base → ~6px at muzzle (9 × 0.65 = 5.85 ≈ 6). Ratio ≈ 18px × 36px ≈ 1:2.
        shapeRenderer.setColor(0.24f, 0.26f, 0.30f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 9f, 92f, 6f, 128f);
        // Outer-edge shadow strips (cylinder curves away on each flank).
        shapeRenderer.setColor(0.11f, 0.12f, 0.15f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 9f, centerX - 7f, 92f,
                                            centerX - 6f, centerX - 5f, 128f);   // left edge shadow
        drawGeneralTrapezoid(shapeRenderer, centerX + 7f, centerX + 9f, 92f,
                                            centerX + 5f, centerX + 6f, 128f);   // right edge shadow
        // Crown highlight (top of cylinder faces the camera): 6px base → tapered.
        shapeRenderer.setColor(0.45f, 0.49f, 0.56f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 3f, centerX + 3f, 92f,
                                            centerX - 2f, centerX + 2f, 128f);   // crown highlight

        // 8. Front sight post — centred bright post near the muzzle (mirrored about centerX).
        shapeRenderer.setColor(0.16f, 0.18f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 4f, 116f, 8f, 8f);                        // front-sight base
        shapeRenderer.setColor(0.50f, 0.54f, 0.60f, 1f);
        shapeRenderer.rect(centerX - 1f, 118f, 2f, 8f);                        // sight post blade

        // 9. Muzzle cap — 2px bright steel rim at the barrel tip (NO bore: barrel points away).
        //    At muzzle Y=128 the barrel half-width is 6px → width 12px, centred about centerX.
        shapeRenderer.setColor(0.40f, 0.44f, 0.52f, 1f);
        shapeRenderer.rect(centerX - 6f, 126f, 12f, 2f);                       // muzzle cap rim
    }

    /**
     * Generates the chaingun sprite as a horizontal ROTATION SPRITE SHEET, baked once into an
     * offscreen FrameBuffer. The sheet holds CHAINGUN_ROTATION_FRAME_COUNT frames side by side,
     * each CHAINGUN_CANVAS_WIDTH wide; frame f draws the six-barrel cluster rotated by
     * f × (CHAINGUN_ROTOR_PERIOD_DEGREES / frameCount). At run time render() samples one frame's
     * sub-region per displayed frame to animate the barrel spin, so NO FrameBuffer work and no
     * allocation happen during gameplay (see the class header for why FrameBuffer.end() must never
     * run mid-frame). Because six identical barrels sit 60° apart, a 60° step reproduces an
     * identical image, so the baked frames span exactly one period and the animation loops seamlessly.
     *
     * Top-down Quake-1 perspective: the camera sits slightly above/behind the weapon, the barrels
     * point AWAY toward the top of the canvas, and the grip is cut off below Y=0. Muzzles face away,
     * so bores are never drawn — only thin steel muzzle-cap rims.
     *
     * Vertical layout (Y-up canvas, 288×201, centerX per frame):
     *   Y   0.. 74  receiver body  — olive military block, hazard bands, belt-feed chute, bolts
     *   Y  34..134  rotor drum     — gunmetal gearbox disc + concentric rings + orbiting bolt studs
     *   Y 126..138  barrel clamp   — collar plate that grips the barrels where they exit the drum
     *   Y 108..194  barrel cluster — six tapered tubes orbiting the rotor axis (bases hidden by drum)
     *   Y 192..194  muzzle caps    — bright steel rims at each barrel tip (no bore holes)
     */
    private static Texture generateChaingunTexture() {
        int canvasWidth  = WeaponConstants.CHAINGUN_CANVAS_WIDTH;
        int canvasHeight = WeaponConstants.CHAINGUN_CANVAS_HEIGHT;
        int frameCount   = WeaponConstants.CHAINGUN_ROTATION_FRAME_COUNT;
        int sheetWidth   = canvasWidth * frameCount;

        FrameBuffer        frameBuffer            = new FrameBuffer(Pixmap.Format.RGBA8888, sheetWidth, canvasHeight, false);
        ShapeRenderer      temporaryShapeRenderer = new ShapeRenderer();
        OrthographicCamera camera                 = new OrthographicCamera(sheetWidth, canvasHeight);
        camera.position.set(sheetWidth / 2f, canvasHeight / 2f, 0f);
        camera.update();

        frameBuffer.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        temporaryShapeRenderer.setProjectionMatrix(camera.combined);
        temporaryShapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float phaseStepDegrees = WeaponConstants.CHAINGUN_ROTOR_PERIOD_DEGREES / frameCount;
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            float frameCenterX    = frameIndex * canvasWidth + canvasWidth / 2f;
            float rotationDegrees = frameIndex * phaseStepDegrees;
            drawChaingunShape(temporaryShapeRenderer, frameCenterX, rotationDegrees);
        }
        temporaryShapeRenderer.end();

        // glReadPixels returns rows with GL Y=0 at bottom; must flip before Texture upload.
        Pixmap rawPixmap = new Pixmap(sheetWidth, canvasHeight, Pixmap.Format.RGBA8888);
        Gdx.gl.glReadPixels(0, 0, sheetWidth, canvasHeight,
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
     * Draws one baked frame of the six-barrel M134-style rotary minigun in Quake-1 first-person
     * style, with the barrel cluster rotated by rotationDegrees about the rotor axis.
     *
     * Top-down perspective: the barrels point away from the camera (muzzle at the far/top edge,
     * bases hidden behind the gearbox drum near the player). Six identical tubes are arranged in a
     * ring around the forward axis; as rotationDegrees advances they orbit that axis — the near
     * half of the ring (drawn last) sweeps in front of the far half, which reads as a spinning
     * rotor. Bores face away and are never drawn — only thin steel muzzle-cap rims.
     *
     * Draw order is back-to-front along the view depth (farthest = highest Y drawn first):
     *   1. Barrel cluster  — six orbiting tapered tubes (bases occluded by the drum)
     *   2. Rotor drum      — gunmetal gearbox disc, concentric rings, orbiting bolt studs, hub
     *   3. Barrel clamp    — collar plate gripping the barrels where they exit the drum
     *   4. Receiver body   — olive military block nearest the player, on top of the drum's lower arc
     *
     * All geometry is expressed relative to centerX so the caller can place each sheet frame in its
     * own horizontal slot by passing a shifted centerX.
     */
    private static void drawChaingunShape(ShapeRenderer shapeRenderer, float centerX, float rotationDegrees) {
        float drumCenterY   = WeaponConstants.CHAINGUN_DRUM_CENTER_Y;
        float drumRadius    = WeaponConstants.CHAINGUN_DRUM_RADIUS;
        float bodyTopY      = WeaponConstants.CHAINGUN_BODY_TOP_Y;
        float bodyHalfWidth = WeaponConstants.CHAINGUN_BODY_HALF_WIDTH;
        float clampBottomY  = WeaponConstants.CHAINGUN_CLAMP_BOTTOM_Y;
        float clampTopY     = WeaponConstants.CHAINGUN_CLAMP_TOP_Y;

        // ── 1. Barrel cluster — the rotating rotor (drawn first; bases hidden behind the drum) ──
        drawChaingunBarrelCluster(shapeRenderer, centerX, rotationDegrees);

        // ── 2. Rotor drum / gearbox housing — big gunmetal disc with concentric ring shading ────
        shapeRenderer.setColor(0.12f, 0.13f, 0.16f, 1f);                       // outer dark rim
        shapeRenderer.ellipse(centerX - drumRadius, drumCenterY - drumRadius, drumRadius * 2f, drumRadius * 2f);
        float drumRing1 = drumRadius * 0.86f;
        shapeRenderer.setColor(0.19f, 0.21f, 0.26f, 1f);                       // main gunmetal body
        shapeRenderer.ellipse(centerX - drumRing1, drumCenterY - drumRing1, drumRing1 * 2f, drumRing1 * 2f);
        float drumRing2 = drumRadius * 0.70f;
        shapeRenderer.setColor(0.28f, 0.31f, 0.38f, 1f);                       // lit crescent (shifted up)
        shapeRenderer.ellipse(centerX - drumRing2, drumCenterY - drumRing2 + 3f, drumRing2 * 2f, drumRing2 * 2f);
        float drumRing3 = drumRadius * 0.56f;
        shapeRenderer.setColor(0.15f, 0.17f, 0.21f, 1f);                       // recessed inner plate
        shapeRenderer.ellipse(centerX - drumRing3, drumCenterY - drumRing3, drumRing3 * 2f, drumRing3 * 2f);

        // Orbiting bolt studs on the drum face — rotate with the rotor to reinforce the spin.
        int   boltCount     = WeaponConstants.CHAINGUN_ROTOR_BARREL_COUNT;
        float boltRingRadius = WeaponConstants.CHAINGUN_HUB_BOLT_RADIUS;
        float boltStepDegrees = 360f / boltCount;
        for (int boltIndex = 0; boltIndex < boltCount; boltIndex++) {
            float boltAngleDegrees = WeaponConstants.CHAINGUN_ROTOR_START_ANGLE_DEGREES
                                     + boltIndex * boltStepDegrees + rotationDegrees;
            float boltX = centerX     + boltRingRadius        * MathUtils.cosDeg(boltAngleDegrees);
            float boltY = drumCenterY + boltRingRadius * 0.42f * MathUtils.sinDeg(boltAngleDegrees);
            float boltDepth = MathUtils.sinDeg(boltAngleDegrees) * 0.5f + 0.5f; // 0 far, 1 near
            float boltShade = 0.22f + 0.18f * boltDepth;
            shapeRenderer.setColor(boltShade, boltShade + 0.02f, boltShade + 0.07f, 1f);
            shapeRenderer.circle(boltX, boltY, 3.4f);
            shapeRenderer.setColor(0.06f, 0.07f, 0.09f, 1f);                   // socket hole
            shapeRenderer.circle(boltX, boltY, 1.4f);
        }
        // Central hub cap on the rotor axis.
        shapeRenderer.setColor(0.32f, 0.35f, 0.42f, 1f);
        shapeRenderer.circle(centerX, drumCenterY, 7.5f);
        shapeRenderer.setColor(0.14f, 0.16f, 0.20f, 1f);
        shapeRenderer.circle(centerX, drumCenterY, 3.2f);

        // ── 3. Barrel clamp collar — plate that grips the barrels where they exit the drum ──────
        float clampHalfWidth = drumRadius * 0.82f;
        shapeRenderer.setColor(0.23f, 0.25f, 0.31f, 1f);
        shapeRenderer.rect(centerX - clampHalfWidth, clampBottomY, clampHalfWidth * 2f, clampTopY - clampBottomY);
        shapeRenderer.setColor(0.85f, 0.50f, 0.05f, 1f);                       // amber hazard band
        shapeRenderer.rect(centerX - clampHalfWidth, clampBottomY + 4f, clampHalfWidth * 2f, 3f);
        shapeRenderer.setColor(0.44f, 0.48f, 0.56f, 1f);                       // top highlight lip
        shapeRenderer.rect(centerX - clampHalfWidth, clampTopY - 2f, clampHalfWidth * 2f, 2f);
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);                       // bottom shadow lip
        shapeRenderer.rect(centerX - clampHalfWidth, clampBottomY, clampHalfWidth * 2f, 2f);
        // Cooling vent slots along the clamp face.
        shapeRenderer.setColor(0.08f, 0.09f, 0.11f, 1f);
        int   ventCount = 9;
        float ventSpan  = clampHalfWidth * 2f - 12f;
        for (int ventIndex = 0; ventIndex < ventCount; ventIndex++) {
            float ventX = centerX - clampHalfWidth + 6f + ventIndex * ventSpan / (ventCount - 1);
            shapeRenderer.rect(ventX - 1.5f, clampTopY - 6f, 3f, 4f);
        }

        // ── 4. Receiver body — two-tone olive military block nearest the player ─────────────────
        shapeRenderer.setColor(0.13f, 0.15f, 0.10f, 1f);                       // dark olive underbody
        drawSymmetricTrapezoid(shapeRenderer, centerX, bodyHalfWidth, 0f, bodyHalfWidth * 0.80f, bodyTopY);
        shapeRenderer.setColor(0.21f, 0.24f, 0.15f, 1f);                       // lit olive top surface
        drawSymmetricTrapezoid(shapeRenderer, centerX, bodyHalfWidth * 0.90f, 6f, bodyHalfWidth * 0.76f, bodyTopY - 4f);
        shapeRenderer.setColor(0.29f, 0.33f, 0.21f, 1f);                       // far-edge highlight
        shapeRenderer.rect(centerX - bodyHalfWidth * 0.76f, bodyTopY - 6f, bodyHalfWidth * 1.52f, 2f);
        shapeRenderer.setColor(0.08f, 0.09f, 0.06f, 1f);                       // near-edge shadow
        shapeRenderer.rect(centerX - bodyHalfWidth, 0f, bodyHalfWidth * 2f, 5f);

        // Hazard warning bands across the receiver face (amber).
        shapeRenderer.setColor(0.85f, 0.50f, 0.05f, 1f);
        shapeRenderer.rect(centerX - 52f, 24f, 104f, 5f);                      // lower band
        shapeRenderer.rect(centerX - 50f, 46f, 100f, 5f);                      // upper band
        shapeRenderer.setColor(0.09f, 0.10f, 0.06f, 1f);                       // dark dividers on upper band
        for (int dividerIndex = 0; dividerIndex < 5; dividerIndex++) {
            shapeRenderer.rect(centerX - 40f + dividerIndex * 20f, 46f, 3f, 5f);
        }

        // Belt-feed chute — raised dark ammo box on the RIGHT of the receiver.
        shapeRenderer.setColor(0.10f, 0.12f, 0.09f, 1f);
        shapeRenderer.rect(centerX + 30f, 30f, 28f, 40f);
        shapeRenderer.setColor(0.20f, 0.23f, 0.15f, 1f);                       // top + left highlight edges
        shapeRenderer.rect(centerX + 30f, 68f, 28f, 2f);
        shapeRenderer.rect(centerX + 30f, 30f, 2f, 40f);
        shapeRenderer.setColor(0.06f, 0.07f, 0.05f, 1f);                       // belt slot
        shapeRenderer.rect(centerX + 36f, 36f, 16f, 28f);
        shapeRenderer.setColor(0.52f, 0.44f, 0.18f, 1f);                       // brass rounds in the belt
        for (int roundIndex = 0; roundIndex < 4; roundIndex++) {
            shapeRenderer.rect(centerX + 39f, 40f + roundIndex * 6f, 10f, 4f);
        }

        // Corner bolt heads.
        shapeRenderer.setColor(0.42f, 0.46f, 0.40f, 1f);
        shapeRenderer.rect(centerX - 66f, 12f, 4f, 4f);
        shapeRenderer.rect(centerX + 62f, 12f, 4f, 4f);
        shapeRenderer.rect(centerX - 56f, 62f, 4f, 4f);
        shapeRenderer.rect(centerX + 52f, 62f, 4f, 4f);
    }

    /**
     * Draws the six barrels of the chaingun rotor for a given rotation phase. The barrels sit on a
     * ring around the forward (away-from-camera) axis; barrel i is placed at
     *   angle = CHAINGUN_ROTOR_START_ANGLE_DEGREES + i × (360 / barrelCount) + rotationDegrees.
     * The ring projects to a flattened ellipse: cos(angle) drives the horizontal offset and
     * sin(angle) is the view depth (+1 = nearest the player, −1 = farthest). Near barrels are wider,
     * dip slightly toward the player, and are drawn brighter; the far half is drawn first so the
     * near half overlaps it — this depth ordering is what makes the cluster read as spinning.
     */
    private static void drawChaingunBarrelCluster(ShapeRenderer shapeRenderer, float centerX, float rotationDegrees) {
        int   barrelCount = WeaponConstants.CHAINGUN_ROTOR_BARREL_COUNT;
        float startAngle  = WeaponConstants.CHAINGUN_ROTOR_START_ANGLE_DEGREES;
        float stepDegrees = 360f / barrelCount;
        // Two depth passes: far half (sin ≤ 0) first, then the near half on top.
        for (int depthPass = 0; depthPass < 2; depthPass++) {
            boolean drawingNearHalf = depthPass == 1;
            for (int barrelIndex = 0; barrelIndex < barrelCount; barrelIndex++) {
                float barrelAngleDegrees = startAngle + barrelIndex * stepDegrees + rotationDegrees;
                boolean barrelIsNear = MathUtils.sinDeg(barrelAngleDegrees) > 0f;
                if (barrelIsNear == drawingNearHalf) {
                    drawChaingunRotatingBarrel(shapeRenderer, centerX, barrelAngleDegrees);
                }
            }
        }
    }

    /**
     * Draws a single perspective-tapered barrel tube for the rotor at the given orbit angle, with
     * top-surface cylinder shading (outer-edge shadow, crown highlight, inner-edge shadow) and a
     * bright muzzle-cap rim. Depth (sin of the orbit angle) scales the tube's width, vertical dip
     * and brightness so near barrels read as closer than far ones.
     */
    private static void drawChaingunRotatingBarrel(ShapeRenderer shapeRenderer, float centerX, float barrelAngleDegrees) {
        float cosAngle = MathUtils.cosDeg(barrelAngleDegrees);
        float sinAngle = MathUtils.sinDeg(barrelAngleDegrees);   // view depth: +1 near, −1 far

        float clusterRadiusX = WeaponConstants.CHAINGUN_BARREL_CLUSTER_RADIUS_X;
        float clusterRadiusY = WeaponConstants.CHAINGUN_BARREL_CLUSTER_RADIUS_Y;
        float halfWidthBase  = WeaponConstants.CHAINGUN_BARREL_HALF_WIDTH;
        float convergence    = WeaponConstants.CHAINGUN_BARREL_CONVERGENCE;
        float barrelBaseY    = WeaponConstants.CHAINGUN_BARREL_BASE_Y;
        float barrelMuzzleY  = WeaponConstants.CHAINGUN_BARREL_MUZZLE_Y;

        float depth         = sinAngle * 0.5f + 0.5f;            // 0 far → 1 near
        float widthScale    = 0.85f + depth * 0.30f;
        float verticalShift = -sinAngle * clusterRadiusY;        // far barrels reach higher, near barrels sit lower

        float baseOffsetX   = clusterRadiusX * cosAngle;
        float muzzleOffsetX = baseOffsetX * convergence;
        float halfWidthLow  = halfWidthBase * widthScale;
        float halfWidthHigh = halfWidthLow * convergence;

        float tubeBaseY   = barrelBaseY   + verticalShift;
        float tubeMuzzleY = barrelMuzzleY + verticalShift * convergence;

        float leftBase    = centerX + baseOffsetX   - halfWidthLow;
        float rightBase   = centerX + baseOffsetX   + halfWidthLow;
        float leftMuzzle  = centerX + muzzleOffsetX - halfWidthHigh;
        float rightMuzzle = centerX + muzzleOffsetX + halfWidthHigh;

        float shade = 0.55f + depth * 0.45f;                     // far tubes darker, near tubes lit

        // Tube body — gunmetal, depth-shaded.
        shapeRenderer.setColor(0.22f * shade, 0.24f * shade, 0.29f * shade, 1f);
        drawGeneralTrapezoid(shapeRenderer, leftBase, rightBase, tubeBaseY, leftMuzzle, rightMuzzle, tubeMuzzleY);

        // Outer-edge shadow (left side of the cylinder curving away).
        float outerShadowBase   = leftBase   + halfWidthLow  * 0.34f;
        float outerShadowMuzzle  = leftMuzzle + halfWidthHigh * 0.34f;
        shapeRenderer.setColor(0.08f * shade + 0.02f, 0.09f * shade + 0.02f, 0.11f * shade + 0.03f, 1f);
        drawGeneralTrapezoid(shapeRenderer, leftBase, outerShadowBase, tubeBaseY, leftMuzzle, outerShadowMuzzle, tubeMuzzleY);

        // Crown highlight (top of the cylinder catching light from the upper-left).
        float crownLeftBase   = leftBase   + halfWidthLow  * 0.44f;
        float crownRightBase  = leftBase   + halfWidthLow  * 0.92f;
        float crownLeftMuzzle  = leftMuzzle + halfWidthHigh * 0.44f;
        float crownRightMuzzle = leftMuzzle + halfWidthHigh * 0.92f;
        shapeRenderer.setColor(0.45f * shade + 0.12f, 0.50f * shade + 0.12f, 0.58f * shade + 0.12f, 1f);
        drawGeneralTrapezoid(shapeRenderer, crownLeftBase, crownRightBase, tubeBaseY, crownLeftMuzzle, crownRightMuzzle, tubeMuzzleY);

        // Inner-edge shadow (right side of the cylinder curving inward).
        float innerShadowBase   = rightBase   - halfWidthLow  * 0.28f;
        float innerShadowMuzzle  = rightMuzzle - halfWidthHigh * 0.28f;
        shapeRenderer.setColor(0.10f * shade + 0.02f, 0.11f * shade + 0.02f, 0.14f * shade + 0.03f, 1f);
        drawGeneralTrapezoid(shapeRenderer, innerShadowBase, rightBase, tubeBaseY, innerShadowMuzzle, rightMuzzle, tubeMuzzleY);

        // Muzzle cap — bright steel rim at the barrel tip (no bore: the muzzle faces away).
        shapeRenderer.setColor(0.55f * shade + 0.14f, 0.60f * shade + 0.14f, 0.68f * shade + 0.14f, 1f);
        shapeRenderer.rect(leftMuzzle, tubeMuzzleY - 2f, rightMuzzle - leftMuzzle, 2f);
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
        //
        // This is the most layer-dense weapon sprite in the game. Read top to bottom as:
        // housing slab → multi-band housing shading → twin shoulder vents → off-axis fuel
        // canister (rounded, banded, hazard-striped, gauge) → curved feed hose → nozzle tube
        // with five-band cylinder shading → stacked retaining rings → glowing igniter ring →
        // muzzle cap → layered pilot flame. Strict symmetry is kept about centerX EXCEPT the
        // fuel canister + hose, which sit off-axis on the left (the weapon's signature break,
        // sanctioned by the idea doc's OPEN QUESTION #1 recommendation (a)).

        // ----------------------------------------------------------------------------------
        // 1. HOUSING SLAB — wide industrial body, top-surface perspective (wider near player).
        //    Drawn as a vertical gradient of stacked trapezoid bands so the metal reads as a
        //    curved top surface catching light at the far edge and falling into shadow near.
        // ----------------------------------------------------------------------------------
        shapeRenderer.setColor(0.13f, 0.14f, 0.16f, 1f);                 // darkest base (near edge)
        drawSymmetricTrapezoid(shapeRenderer, centerX, 50f, 14f, 42f, 60f);
        shapeRenderer.setColor(0.19f, 0.21f, 0.24f, 1f);                 // lower-mid band
        drawSymmetricTrapezoid(shapeRenderer, centerX, 47f, 22f, 42f, 60f);
        shapeRenderer.setColor(0.24f, 0.26f, 0.29f, 1f);                 // mid band
        drawSymmetricTrapezoid(shapeRenderer, centerX, 45f, 34f, 42f, 60f);
        shapeRenderer.setColor(0.29f, 0.31f, 0.35f, 1f);                 // upper-mid band (top lit)
        drawSymmetricTrapezoid(shapeRenderer, centerX, 43f, 48f, 42f, 60f);

        // 2. Housing edge accents — bright far-edge crown, black near-edge shadow, side bevels.
        shapeRenderer.setColor(0.46f, 0.50f, 0.55f, 1f);
        shapeRenderer.rect(centerX - 42f, 57f, 84f, 3f);                 // far-edge crown highlight
        shapeRenderer.setColor(0.34f, 0.37f, 0.41f, 1f);
        shapeRenderer.rect(centerX - 43f, 54f, 86f, 2f);                 // secondary crown sheen
        shapeRenderer.setColor(0.10f, 0.11f, 0.13f, 1f);
        shapeRenderer.rect(centerX - 50f, 14f, 100f, 3f);               // near-edge bottom shadow
        // Left/right bevel shadows (the slab's side walls curving away from the camera).
        shapeRenderer.setColor(0.11f, 0.12f, 0.14f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 50f, centerX - 46f, 14f,
                                            centerX - 42f, centerX - 39f, 60f);   // left bevel
        drawGeneralTrapezoid(shapeRenderer, centerX + 46f, centerX + 50f, 14f,
                                            centerX + 39f, centerX + 42f, 60f);   // right bevel

        // 3. Mid-body grooves — two lateral machined slots visible from above (depth lines).
        shapeRenderer.setColor(0.16f, 0.17f, 0.20f, 1f);
        shapeRenderer.rect(centerX - 44f, 38f, 88f, 2f);                 // upper groove
        shapeRenderer.rect(centerX - 47f, 26f, 94f, 2f);                 // lower groove
        shapeRenderer.setColor(0.33f, 0.36f, 0.40f, 1f);
        shapeRenderer.rect(centerX - 44f, 40f, 88f, 1f);                 // upper groove lip highlight
        shapeRenderer.rect(centerX - 47f, 28f, 94f, 1f);                 // lower groove lip highlight

        // 4. Shoulder vents — symmetric twin heat-vent louvres flanking the nozzle base join.
        //    Three dark slats each side, with a warm orange glow leaking between them (the
        //    pilot light's heat venting). Symmetric about centerX so the body stays balanced.
        for (int ventIndex = 0; ventIndex < 3; ventIndex++) {
            float ventY = 50f + ventIndex * 3f;
            shapeRenderer.setColor(0.08f, 0.09f, 0.10f, 1f);
            shapeRenderer.rect(centerX - 40f, ventY, 14f, 2f);          // left vent slat
            shapeRenderer.rect(centerX + 26f, ventY, 14f, 2f);          // right vent slat (mirror)
            shapeRenderer.setColor(0.70f, 0.32f, 0.06f, 0.55f);
            shapeRenderer.rect(centerX - 40f, ventY + 2f, 14f, 1f);     // left vent heat glow
            shapeRenderer.rect(centerX + 26f, ventY + 2f, 14f, 1f);     // right vent heat glow
        }

        // 5. Rivet studs — symmetric pairs of bright bolt heads around the housing perimeter.
        shapeRenderer.setColor(0.40f, 0.42f, 0.46f, 1f);
        float[] rivetXOffsets = { 44f, 30f, 16f };
        float[] rivetYRows    = { 18f, 45f };
        for (int rowIndex = 0; rowIndex < rivetYRows.length; rowIndex++) {
            for (int offsetIndex = 0; offsetIndex < rivetXOffsets.length; offsetIndex++) {
                float rivetOffset = rivetXOffsets[offsetIndex];
                shapeRenderer.rect(centerX - rivetOffset - 1f, rivetYRows[rowIndex], 3f, 3f); // left
                shapeRenderer.rect(centerX + rivetOffset - 2f, rivetYRows[rowIndex], 3f, 3f); // right
            }
        }
        // Rivet top-light pips for a domed read.
        shapeRenderer.setColor(0.58f, 0.61f, 0.66f, 1f);
        for (int rowIndex = 0; rowIndex < rivetYRows.length; rowIndex++) {
            for (int offsetIndex = 0; offsetIndex < rivetXOffsets.length; offsetIndex++) {
                float rivetOffset = rivetXOffsets[offsetIndex];
                shapeRenderer.rect(centerX - rivetOffset, rivetYRows[rowIndex] + 2f, 1f, 1f); // left
                shapeRenderer.rect(centerX + rivetOffset - 1f, rivetYRows[rowIndex] + 2f, 1f, 1f); // right
            }
        }

        // ----------------------------------------------------------------------------------
        // 6. FUEL CANISTER — off-axis rust-red cylinder on the LEFT-back of the housing.
        //    Signature asymmetric detail. Drawn as a rounded body (rect + corner circles) with
        //    a horizontal cylinder gradient, two retaining bands, a hazard stripe, and a gauge.
        //    Tank spans Y=16..52, centered around tankCenterX = centerX-30, 30px wide.
        // ----------------------------------------------------------------------------------
        float tankCenterX = centerX - 30f;
        float tankLeft    = tankCenterX - 15f;   // CX-45
        float tankRight   = tankCenterX + 15f;   // CX-15
        // Rounded end caps (top and bottom) via circles so the tank reads cylindrical.
        shapeRenderer.setColor(0.50f, 0.18f, 0.11f, 1f);
        shapeRenderer.circle(tankCenterX, 19f, 6f);                      // bottom cap
        shapeRenderer.circle(tankCenterX, 49f, 6f);                      // top cap
        // Tank barrel body.
        shapeRenderer.rect(tankLeft, 19f, 30f, 30f);
        // Horizontal cylinder shading: dark left edge → bright vertical sheen → dark right edge.
        shapeRenderer.setColor(0.34f, 0.11f, 0.06f, 1f);
        shapeRenderer.rect(tankLeft, 19f, 5f, 30f);                      // left-edge shadow
        shapeRenderer.rect(tankRight - 5f, 19f, 5f, 30f);               // right-edge shadow
        shapeRenderer.setColor(0.72f, 0.30f, 0.18f, 1f);
        shapeRenderer.rect(tankCenterX - 5f, 19f, 6f, 30f);            // broad sheen band
        shapeRenderer.setColor(0.92f, 0.46f, 0.28f, 1f);
        shapeRenderer.rect(tankCenterX - 2f, 19f, 2f, 30f);            // hot specular line

        // 7. Canister retaining bands — two dark steel hoops around the cylinder.
        shapeRenderer.setColor(0.16f, 0.17f, 0.19f, 1f);
        shapeRenderer.rect(tankLeft, 25f, 30f, 3f);                      // lower band
        shapeRenderer.rect(tankLeft, 42f, 30f, 3f);                      // upper band
        shapeRenderer.setColor(0.40f, 0.42f, 0.46f, 1f);
        shapeRenderer.rect(tankLeft, 27f, 30f, 1f);                      // lower band lip
        shapeRenderer.rect(tankLeft, 44f, 30f, 1f);                      // upper band lip

        // 8. Hazard stripe — yellow/black warning band across the canister mid-section.
        shapeRenderer.setColor(0.88f, 0.72f, 0.10f, 1f);
        shapeRenderer.rect(tankLeft, 32f, 30f, 8f);                      // yellow base
        shapeRenderer.setColor(0.10f, 0.09f, 0.07f, 1f);                // black diagonal ticks
        shapeRenderer.rect(tankLeft + 2f,  32f, 3f, 8f);
        shapeRenderer.rect(tankLeft + 9f,  32f, 3f, 8f);
        shapeRenderer.rect(tankLeft + 16f, 32f, 3f, 8f);
        shapeRenderer.rect(tankLeft + 23f, 32f, 3f, 8f);
        shapeRenderer.setColor(1.00f, 0.88f, 0.30f, 1f);               // stripe top highlight
        shapeRenderer.rect(tankLeft, 39f, 30f, 1f);

        // 9. Fuel gauge — a small recessed window near the tank base showing a half-full read.
        shapeRenderer.setColor(0.12f, 0.05f, 0.03f, 1f);
        shapeRenderer.rect(tankCenterX - 5f, 20f, 10f, 4f);             // gauge dark recess
        shapeRenderer.setColor(0.95f, 0.42f, 0.12f, 1f);
        shapeRenderer.rect(tankCenterX - 5f, 20f, 5f, 4f);             // gauge fill (half)
        shapeRenderer.setColor(0.55f, 0.58f, 0.62f, 1f);
        shapeRenderer.rect(tankCenterX - 5f, 23f, 10f, 1f);            // gauge frame lip

        // ----------------------------------------------------------------------------------
        // 10. FEED HOSE — curved dark rubber hose from the canister top to the nozzle base.
        //     Approximated by a chain of short rects stepping right-and-up, with a highlight
        //     ridge on top of each segment so it reads as a ribbed flexible hose.
        // ----------------------------------------------------------------------------------
        float[] hoseSegmentX = { tankCenterX - 3f, tankCenterX + 4f, tankCenterX + 12f, centerX - 12f };
        float[] hoseSegmentY = { 49f,              53f,              57f,               59f };
        shapeRenderer.setColor(0.13f, 0.13f, 0.15f, 1f);
        for (int hoseIndex = 0; hoseIndex < hoseSegmentX.length; hoseIndex++) {
            shapeRenderer.rect(hoseSegmentX[hoseIndex], hoseSegmentY[hoseIndex], 9f, 6f);
        }
        shapeRenderer.setColor(0.26f, 0.26f, 0.30f, 1f);                // hose top-ridge highlight
        for (int hoseIndex = 0; hoseIndex < hoseSegmentX.length; hoseIndex++) {
            shapeRenderer.rect(hoseSegmentX[hoseIndex], hoseSegmentY[hoseIndex] + 5f, 9f, 1f);
        }
        // Hose-to-nozzle coupling clamp (centered where the hose meets the tube base).
        shapeRenderer.setColor(0.36f, 0.38f, 0.42f, 1f);
        shapeRenderer.rect(centerX - 10f, 56f, 20f, 6f);
        shapeRenderer.setColor(0.18f, 0.19f, 0.22f, 1f);
        shapeRenderer.rect(centerX - 10f, 56f, 20f, 2f);               // clamp underside shadow

        // ----------------------------------------------------------------------------------
        // 11. NOZZLE TUBE — wide perspective-tapered sprayer pointing away from the player.
        //     Top-down view, convergence 0.65: base half-width 17px → muzzle half-width 11px.
        //     Wider than a rifle barrel (it is a sprayer), narrower than the grenade tube.
        // ----------------------------------------------------------------------------------
        shapeRenderer.setColor(0.20f, 0.22f, 0.26f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 17f, 60f, 11f, 120f);

        // 12. Nozzle cylinder shading — FIVE bands for a smooth curved top surface, all tapered
        //     with the same 0.65 convergence so edges stay parallel toward the muzzle.
        //     Base outer edge CX-17/CX+17 → muzzle CX-11/CX+11.
        // Outer-edge shadow (darkest — the cylinder sides curving away).
        shapeRenderer.setColor(0.09f, 0.10f, 0.12f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 17f, centerX - 13f, 60f,
                                            centerX - 11f, centerX -  8f, 120f);  // left outer shadow
        drawGeneralTrapezoid(shapeRenderer, centerX + 13f, centerX + 17f, 60f,
                                            centerX +  8f, centerX + 11f, 120f);  // right outer shadow
        // Inner-edge shadow (mid-dark, between outer shadow and the lit crown).
        shapeRenderer.setColor(0.14f, 0.15f, 0.18f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 13f, centerX -  8f, 60f,
                                            centerX -  8f, centerX -  5f, 120f);  // left inner shadow
        drawGeneralTrapezoid(shapeRenderer, centerX +  8f, centerX + 13f, 60f,
                                            centerX +  5f, centerX +  8f, 120f);  // right inner shadow
        // Mid-tone shoulder (the transition into the crown).
        shapeRenderer.setColor(0.30f, 0.33f, 0.38f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX -  8f, centerX -  3f, 60f,
                                            centerX -  5f, centerX -  2f, 120f);  // left shoulder
        drawGeneralTrapezoid(shapeRenderer, centerX +  3f, centerX +  8f, 60f,
                                            centerX +  2f, centerX +  5f, 120f);  // right shoulder
        // Crown highlight (brightest — top of the cylinder facing the camera).
        shapeRenderer.setColor(0.48f, 0.52f, 0.58f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX -  3f, centerX +  3f, 60f,
                                            centerX -  2f, centerX +  2f, 120f);  // center crown
        // Hot specular pin-line along the very top of the crown.
        shapeRenderer.setColor(0.62f, 0.66f, 0.72f, 1f);
        drawGeneralTrapezoid(shapeRenderer, centerX - 1f, centerX + 1f, 60f,
                                            centerX - 0.6f, centerX + 0.6f, 120f);

        // 13. Retaining rings — two stacked steel hoops banding the nozzle (tapered widths).
        //     Lower ring at Y≈80 (interp half-width ≈14.5), upper ring at Y≈100 (≈12.5).
        shapeRenderer.setColor(0.34f, 0.37f, 0.42f, 1f);
        shapeRenderer.rect(centerX - 15f, 79f, 30f, 4f);               // lower ring
        shapeRenderer.rect(centerX - 13f, 99f, 26f, 4f);               // upper ring
        shapeRenderer.setColor(0.52f, 0.56f, 0.62f, 1f);
        shapeRenderer.rect(centerX - 15f, 82f, 30f, 1f);               // lower ring lip
        shapeRenderer.rect(centerX - 13f, 102f, 26f, 1f);             // upper ring lip
        shapeRenderer.setColor(0.10f, 0.11f, 0.13f, 1f);
        shapeRenderer.rect(centerX - 15f, 79f, 30f, 1f);               // lower ring underside
        shapeRenderer.rect(centerX - 13f, 99f, 26f, 1f);             // upper ring underside

        // 14. Igniter ring — bright steel collar at the nozzle tip with an inner orange glow
        //     where the pilot light sits. Muzzle scale (Y=112..120), half-width ≈12px.
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        shapeRenderer.rect(centerX - 13f, 112f, 26f, 8f);             // collar body
        shapeRenderer.setColor(0.57f, 0.60f, 0.66f, 1f);
        shapeRenderer.rect(centerX - 13f, 118f, 26f, 2f);             // collar top highlight
        shapeRenderer.setColor(0.16f, 0.17f, 0.20f, 1f);
        shapeRenderer.rect(centerX - 13f, 112f, 26f, 2f);             // collar underside shadow
        shapeRenderer.setColor(0.95f, 0.45f, 0.10f, 0.65f);          // inner heat glow ring
        shapeRenderer.rect(centerX - 8f, 115f, 16f, 4f);

        // 15. Muzzle cap — 2px bright steel rim at the tip Y=120 (top-down: NO bore ellipse).
        shapeRenderer.setColor(0.42f, 0.46f, 0.54f, 1f);
        shapeRenderer.rect(centerX - 11f, 120f, 22f, 2f);

        // ----------------------------------------------------------------------------------
        // 16. PILOT FLAME — ever-present layered flicker at the nozzle tip (idle frame look).
        //     Four nested teardrops: deep-orange outer → orange → yellow → white-hot core.
        //     Signals the gun is always live and ready to burn.
        // ----------------------------------------------------------------------------------
        shapeRenderer.setColor(0.85f, 0.30f, 0.05f, 0.80f);            // deep-orange outer
        shapeRenderer.triangle(centerX - 7f, 120f, centerX + 7f, 120f, centerX, 132f);
        shapeRenderer.setColor(0.98f, 0.50f, 0.12f, 0.88f);            // orange mid
        shapeRenderer.triangle(centerX - 5f, 120f, centerX + 5f, 120f, centerX, 130f);
        shapeRenderer.setColor(1.00f, 0.78f, 0.22f, 0.92f);            // yellow inner
        shapeRenderer.triangle(centerX - 3f, 120f, centerX + 3f, 120f, centerX, 128f);
        shapeRenderer.setColor(1.00f, 0.97f, 0.78f, 0.95f);            // white-hot core
        shapeRenderer.triangle(centerX - 1.5f, 120f, centerX + 1.5f, 120f, centerX, 126f);
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

    /**
     * Number of horizontal animation frames packed into the weapon's cached texture. Most weapons
     * are single static sprites (1); the Chaingun texture is a rotation sprite sheet of
     * CHAINGUN_ROTATION_FRAME_COUNT frames. Callers that draw a static thumbnail should sample only
     * the first frame (width = texture.getWidth() / this count).
     */
    public int getWeaponTextureFrameColumns(Weapon weapon) {
        if (weapon instanceof Chaingun) {
            return WeaponConstants.CHAINGUN_ROTATION_FRAME_COUNT;
        }
        return 1;
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
        advanceChaingunRotor(state, deltaTime);
        advancePlasmaIdlePulse(state, deltaTime);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (equippedWeapon instanceof Chaingun) {
            // The chaingun texture is a horizontal rotation sprite sheet; sample the current
            // spin frame's sub-region so the barrels animate without any per-frame FrameBuffer work.
            int   canvasWidth = WeaponConstants.CHAINGUN_CANVAS_WIDTH;
            int   frameIndex  = currentChaingunFrameIndex();
            batch.draw(normalTexture, drawX, currentOffsetY,
                       WeaponConstants.WEAPON_HUD_WIDTH, WeaponConstants.WEAPON_HUD_HEIGHT,
                       frameIndex * canvasWidth, 0, canvasWidth, WeaponConstants.CHAINGUN_CANVAS_HEIGHT,
                       false, false);
        } else {
            batch.draw(normalTexture, drawX, currentOffsetY,
                       WeaponConstants.WEAPON_HUD_WIDTH, WeaponConstants.WEAPON_HUD_HEIGHT);
        }
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
                } else if (equippedWeapon instanceof AssaultRifle) {
                    renderAssaultRifleEffect(camera, normalizedTime);
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
        } else if (state == WeaponVisualState.NORMAL && equippedWeapon instanceof PlasmaRifle) {
            // Unique resting-state animation: the plasma rifle quietly breathes while holstered-ready.
            renderPlasmaIdlePulse(camera);
        }
    }

    /**
     * Advances the plasma-rifle idle "breathing" clock. Runs the clock forward only while a
     * PlasmaRifle is equipped and its visual state is NORMAL; during FIRING / RELOADING the clock is
     * frozen (value retained) so the resting glow resumes smoothly and never competes with the muzzle
     * burst. No-op for every other weapon. Scalar float only — no allocation.
     */
    private void advancePlasmaIdlePulse(WeaponVisualState state, float deltaTime) {
        if (!(equippedWeapon instanceof PlasmaRifle) || state != WeaponVisualState.NORMAL) {
            return;
        }
        plasmaIdlePulseTimeSeconds += deltaTime;
    }

    /**
     * Draws the plasma rifle's resting idle-glow overlay as a live ShapeRenderer pass on top of the
     * static sprite (the sprite texture itself is never regenerated). A slow sine breath modulates the
     * emitter halo (alpha + radius) and shimmers the coil bands with a per-band phase skew so energy
     * reads as flowing up the barrel. Additively blended for a luminous glow; blend state is restored
     * to the standard alpha mode afterward. Cool cyan palette, shared with the sprite and firing burst.
     */
    private void renderPlasmaIdlePulse(OrthographicCamera camera) {
        float centerX   = Constants.WORLD_WIDTH / 2f;
        float emitterY  = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                          * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;

        float glowAlpha  = GameMath.pulseMultiplier(plasmaIdlePulseTimeSeconds,
                              WeaponConstants.PLASMA_RIFLE_IDLE_PULSE_HERTZ,
                              WeaponConstants.PLASMA_RIFLE_IDLE_EMITTER_MIN_ALPHA,
                              WeaponConstants.PLASMA_RIFLE_IDLE_EMITTER_MAX_ALPHA);
        float glowRadius = GameMath.pulseMultiplier(plasmaIdlePulseTimeSeconds,
                              WeaponConstants.PLASMA_RIFLE_IDLE_PULSE_HERTZ,
                              WeaponConstants.PLASMA_RIFLE_IDLE_EMITTER_MIN_RADIUS,
                              WeaponConstants.PLASMA_RIFLE_IDLE_EMITTER_MAX_RADIUS);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);   // additive — luminous energy glow
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Coil shimmer bands across the body, front-to-back, each phase-skewed so the glow appears to
        // travel up the weapon toward the emitter.
        float coilHalfWidth = WeaponConstants.PLASMA_RIFLE_IDLE_COIL_HALF_WIDTH;
        float coilThickness = WeaponConstants.PLASMA_RIFLE_IDLE_COIL_THICKNESS;
        for (int coilIndex = 0; coilIndex < WeaponConstants.PLASMA_RIFLE_IDLE_COIL_Y_FRACTIONS.length; coilIndex++) {
            float coilClock = plasmaIdlePulseTimeSeconds
                              + coilIndex * WeaponConstants.PLASMA_RIFLE_IDLE_COIL_PHASE_OFFSET;
            float coilAlpha = GameMath.pulseMultiplier(coilClock,
                                  WeaponConstants.PLASMA_RIFLE_IDLE_PULSE_HERTZ,
                                  WeaponConstants.PLASMA_RIFLE_IDLE_COIL_MIN_ALPHA,
                                  WeaponConstants.PLASMA_RIFLE_IDLE_COIL_MAX_ALPHA);
            float coilY = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                          * WeaponConstants.PLASMA_RIFLE_IDLE_COIL_Y_FRACTIONS[coilIndex];
            shapeRenderer.setColor(0.00f, 0.88f, 1.00f, coilAlpha);
            shapeRenderer.rect(centerX - coilHalfWidth, coilY, coilHalfWidth * 2f, coilThickness);
        }

        // Emitter breathing halo — outer soft blue, mid cyan, inner white-cyan pinpoint.
        shapeRenderer.setColor(0.00f, 0.55f, 1.00f, glowAlpha * 0.55f);
        shapeRenderer.ellipse(centerX - glowRadius, emitterY - glowRadius * 0.55f,
                              glowRadius * 2f, glowRadius * 1.10f);
        shapeRenderer.setColor(0.30f, 0.82f, 1.00f, glowAlpha * 0.80f);
        shapeRenderer.ellipse(centerX - glowRadius * 0.55f, emitterY - glowRadius * 0.30f,
                              glowRadius * 1.10f, glowRadius * 0.60f);
        shapeRenderer.setColor(0.80f, 0.98f, 1.00f, glowAlpha);
        shapeRenderer.ellipse(centerX - glowRadius * 0.24f, emitterY - glowRadius * 0.13f,
                              glowRadius * 0.48f, glowRadius * 0.26f);

        shapeRenderer.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);   // restore standard alpha
        Gdx.gl.glDisable(GL20.GL_BLEND);
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
     * Advances the chaingun barrel-spin rotor. The spin speed ramps toward its firing target while
     * the weapon is FIRING (spin-up) and back toward zero otherwise (wind-down); the rotor angle
     * integrates that speed each frame. No-op unless a Chaingun is equipped, so the rotor sits still
     * for every other weapon. Uses only scalar float state — no allocation.
     */
    private void advanceChaingunRotor(WeaponVisualState state, float deltaTime) {
        if (!(equippedWeapon instanceof Chaingun)) {
            return;
        }
        float targetSpeed = (state == WeaponVisualState.FIRING)
                            ? WeaponConstants.CHAINGUN_ROTOR_MAX_SPEED_DEGREES_PER_SECOND
                            : 0f;
        chaingunRotorSpeedDegreesPerSecond += (targetSpeed - chaingunRotorSpeedDegreesPerSecond)
                * Math.min(deltaTime * WeaponConstants.CHAINGUN_ROTOR_RAMP_RATE, 1f);
        chaingunRotorAngleDegrees =
                (chaingunRotorAngleDegrees + chaingunRotorSpeedDegreesPerSecond * deltaTime) % 360f;
    }

    /**
     * Selects which baked rotation frame of the chaingun sprite sheet matches the current rotor
     * angle. Because the six barrels repeat every CHAINGUN_ROTOR_PERIOD_DEGREES, only the angle
     * within one period is used; that fraction maps onto the CHAINGUN_ROTATION_FRAME_COUNT frames.
     */
    private int currentChaingunFrameIndex() {
        int   frameCount     = WeaponConstants.CHAINGUN_ROTATION_FRAME_COUNT;
        float periodDegrees  = WeaponConstants.CHAINGUN_ROTOR_PERIOD_DEGREES;
        float phaseInPeriod  = ((chaingunRotorAngleDegrees % periodDegrees) + periodDegrees) % periodDegrees;
        int   frameIndex     = (int) (phaseInPeriod / periodDegrees * frameCount);
        if (frameIndex >= frameCount) {
            frameIndex = frameCount - 1;
        }
        return frameIndex;
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
     * Draws a crisp single-shot muzzle effect for the Assault Rifle.
     *
     * Visually distinct from the chaingun's cone-and-sparks: this is a sharp four-point
     * muzzle STAR (a bright central flash disc crossed by four tapering rays), with brass
     * shell CASINGS ejecting to the RIGHT and a thin RISING SMOKE wisp above the muzzle.
     *
     * Timing: alpha = 1 - normalizedTime (everything fades out). The casings travel further
     * outward (to the right) and downward as normalizedTime grows, simulating ejected brass
     * arcing away from the port; the smoke wisp rises and thins. Casing launch positions come
     * from the static ASSAULT_RIFLE_CASING_FRACTIONS_X/_Y tables (no per-frame allocation).
     */
    private void renderAssaultRifleEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha       = 1f - normalizedTime;
        float scale       = 1f - normalizedTime * 0.45f;
        float barrelX     = Constants.WORLD_WIDTH / 2f;
        float barrelY     = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                            * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;
        float starRadius  = WeaponConstants.ASSAULT_RIFLE_EFFECT_STAR_RADIUS * scale;
        float coreRadius  = WeaponConstants.ASSAULT_RIFLE_EFFECT_STAR_CORE_RADIUS * scale;
        float casingSize  = WeaponConstants.ASSAULT_RIFLE_EFFECT_CASING_SIZE;
        float casingReachX = WeaponConstants.ASSAULT_RIFLE_EFFECT_CASING_SPREAD_X;
        float casingReachY = WeaponConstants.ASSAULT_RIFLE_EFFECT_CASING_SPREAD_Y;
        float smokeHeight = WeaponConstants.ASSAULT_RIFLE_EFFECT_SMOKE_HEIGHT;
        float smokeWidth  = WeaponConstants.ASSAULT_RIFLE_EFFECT_SMOKE_WIDTH;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Rising smoke wisp — thin grey column drawn first so the flash sits on top of it.
        //    Rises (and the base lifts off the muzzle) as normalizedTime advances; thins out.
        float smokeBaseY = barrelY + smokeHeight * normalizedTime * 0.5f;
        float smokeTipY  = smokeBaseY + smokeHeight * (0.4f + normalizedTime * 0.6f);
        float smokeHalf  = smokeWidth * 0.5f * scale;
        shapeRenderer.setColor(0.55f, 0.56f, 0.58f, alpha * 0.30f);
        shapeRenderer.triangle(
            barrelX - smokeHalf, smokeBaseY,
            barrelX + smokeHalf, smokeBaseY,
            barrelX,             smokeTipY
        );

        // Four-point muzzle star rays — thin tapering triangles up/down/left/right from the tip.
        shapeRenderer.setColor(1.00f, 0.92f, 0.55f, alpha * 0.85f);
        // Up ray (longest — fired toward the horizon).
        shapeRenderer.triangle(
            barrelX - coreRadius * 0.30f, barrelY,
            barrelX + coreRadius * 0.30f, barrelY,
            barrelX,                      barrelY + starRadius
        );
        // Down ray (short kick toward the muzzle base).
        shapeRenderer.triangle(
            barrelX - coreRadius * 0.30f, barrelY,
            barrelX + coreRadius * 0.30f, barrelY,
            barrelX,                      barrelY - starRadius * 0.45f
        );
        // Left ray.
        shapeRenderer.triangle(
            barrelX, barrelY - coreRadius * 0.30f,
            barrelX, barrelY + coreRadius * 0.30f,
            barrelX - starRadius * 0.70f, barrelY
        );
        // Right ray.
        shapeRenderer.triangle(
            barrelX, barrelY - coreRadius * 0.30f,
            barrelX, barrelY + coreRadius * 0.30f,
            barrelX + starRadius * 0.70f, barrelY
        );

        // Central flash disc — bright warm core, then a hot white pinpoint.
        shapeRenderer.setColor(1.00f, 0.80f, 0.30f, alpha * 0.80f);
        shapeRenderer.circle(barrelX, barrelY, coreRadius);
        shapeRenderer.setColor(1.00f, 1.00f, 0.90f, alpha * 0.90f);
        shapeRenderer.circle(barrelX, barrelY, coreRadius * 0.50f);

        // Brass shell casings — eject to the RIGHT, arcing downward as normalizedTime grows.
        //    Deterministic positions from the static fraction tables (no per-frame allocation).
        shapeRenderer.setColor(0.82f, 0.66f, 0.24f, alpha * 0.90f);
        for (int casingIndex = 0; casingIndex < WeaponConstants.ASSAULT_RIFLE_EFFECT_CASING_COUNT; casingIndex++) {
            float launchHeight = ASSAULT_RIFLE_CASING_FRACTIONS_Y[casingIndex] * casingReachY;
            float casingX = barrelX
                            + ASSAULT_RIFLE_CASING_FRACTIONS_X[casingIndex] * casingReachX * normalizedTime;
            float casingY = barrelY + launchHeight - casingReachY * normalizedTime;
            shapeRenderer.rect(casingX, casingY, casingSize, casingSize * 0.55f);
            // Tiny bright glint on each casing's upper edge.
            shapeRenderer.setColor(0.96f, 0.86f, 0.50f, alpha * 0.80f);
            shapeRenderer.rect(casingX, casingY + casingSize * 0.40f, casingSize, casingSize * 0.15f);
            shapeRenderer.setColor(0.82f, 0.66f, 0.24f, alpha * 0.90f);
        }

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
     * Draws a large lingering, animated three-band flame cone for the Incinerator.
     *
     * Three layered colour bands per the idea doc — deep orange-red outer, orange mid,
     * yellow-white core — built from stacked triangles, plus a translucent heat-shimmer
     * disc at the base and a white-hot superheated tip. Outer flame tongues are JITTERED
     * each frame (lateral tip offset + height wobble) from the static INCINERATOR_FLAME_JITTER
     * table so the fire flickers alive without per-frame allocation. A slow shrink rate
     * (INCINERATOR_EFFECT_SHRINK_RATE = 0.25) keeps the flame filling the lower screen for
     * longer than any other muzzle effect. All layers fade as normalizedTime approaches 1.
     */
    private void renderIncineratorEffect(OrthographicCamera camera, float normalizedTime) {
        float alpha    = 1f - normalizedTime;
        float scale    = 1f - normalizedTime * WeaponConstants.INCINERATOR_EFFECT_SHRINK_RATE;
        float barrelX  = Constants.WORLD_WIDTH / 2f;
        float barrelY  = currentOffsetY + WeaponConstants.WEAPON_HUD_HEIGHT
                         * WeaponConstants.WEAPON_BARREL_TIP_Y_FRACTION;
        float height   = WeaponConstants.INCINERATOR_EFFECT_FLAME_HEIGHT * scale;
        float halfBase = WeaponConstants.INCINERATOR_EFFECT_FLAME_BASE_WIDTH / 2f * scale;

        // Animated flicker: advance an index through the static jitter table over time so the
        // tongues wobble while firing. animationTimer drives it (no allocation, no Random).
        int   jitterCursor   = (int) (animationTimer * 60f);
        float jitterAmount   = halfBase * 0.30f;
        float jitterLeft     = INCINERATOR_FLAME_JITTER[(jitterCursor)     % INCINERATOR_FLAME_JITTER.length] * jitterAmount;
        float jitterRight    = INCINERATOR_FLAME_JITTER[(jitterCursor + 3) % INCINERATOR_FLAME_JITTER.length] * jitterAmount;
        float jitterCoreTip  = INCINERATOR_FLAME_JITTER[(jitterCursor + 5) % INCINERATOR_FLAME_JITTER.length] * jitterAmount * 0.5f;
        float heightWobble   = 1f + INCINERATOR_FLAME_JITTER[(jitterCursor + 1) % INCINERATOR_FLAME_JITTER.length] * 0.12f;
        float wobbledHeight  = height * heightWobble;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // --- Heat-shimmer disc at the base — very wide and flat, warm translucent wash ---
        shapeRenderer.setColor(1.00f, 0.55f, 0.08f, alpha * 0.28f);
        shapeRenderer.ellipse(barrelX - halfBase * 1.15f, barrelY - halfBase * 0.22f,
                              halfBase * 2.30f, halfBase * 0.44f);

        // ================= BAND 1 — OUTER deep orange-red (widest spread) =================
        // Broad fan plus two jittered side tongues that flicker frame to frame.
        shapeRenderer.setColor(0.85f, 0.30f, 0.05f, alpha * 0.70f);
        shapeRenderer.triangle(
            barrelX - halfBase, barrelY,
            barrelX + halfBase, barrelY,
            barrelX + jitterCoreTip, barrelY + wobbledHeight * 0.46f
        );
        // Left flickering tongue
        shapeRenderer.setColor(0.80f, 0.16f, 0.02f, alpha * 0.66f);
        shapeRenderer.triangle(
            barrelX - halfBase,          barrelY,
            barrelX - halfBase * 0.12f,  barrelY,
            barrelX - halfBase * 0.66f + jitterLeft, barrelY + wobbledHeight * 0.58f
        );
        // Right flickering tongue
        shapeRenderer.triangle(
            barrelX + halfBase * 0.12f,  barrelY,
            barrelX + halfBase,          barrelY,
            barrelX + halfBase * 0.66f + jitterRight, barrelY + wobbledHeight * 0.58f
        );

        // ================= BAND 2 — MID orange (main flame body) =================
        shapeRenderer.setColor(0.98f, 0.55f, 0.12f, alpha * 0.85f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.74f, barrelY,
            barrelX + halfBase * 0.74f, barrelY,
            barrelX + jitterCoreTip * 0.6f, barrelY + wobbledHeight * 0.80f
        );
        // Mid inner orange spire — taller, slightly narrower
        shapeRenderer.setColor(1.00f, 0.66f, 0.10f, alpha * 0.88f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.46f, barrelY,
            barrelX + halfBase * 0.46f, barrelY,
            barrelX + jitterCoreTip * 0.4f, barrelY + wobbledHeight * 0.92f
        );

        // ================= BAND 3 — CORE yellow-white (hottest center) =================
        shapeRenderer.setColor(1.00f, 0.85f, 0.30f, alpha * 0.92f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.24f, barrelY,
            barrelX + halfBase * 0.24f, barrelY,
            barrelX + jitterCoreTip * 0.3f, barrelY + wobbledHeight
        );
        // White-hot superheated tip — narrow and bright, slightly shorter than the core spire.
        shapeRenderer.setColor(1.00f, 0.98f, 0.78f, alpha * 0.78f);
        shapeRenderer.triangle(
            barrelX - halfBase * 0.11f, barrelY,
            barrelX + halfBase * 0.11f, barrelY,
            barrelX + jitterCoreTip * 0.3f, barrelY + wobbledHeight * 0.70f
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

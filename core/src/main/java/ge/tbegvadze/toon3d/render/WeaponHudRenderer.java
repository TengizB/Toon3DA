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
        if (weapon instanceof Shotgun) {
            return generateShotgunTexture();
        }
        return generateFallbackWeaponTexture();
    }

    /**
     * Generates a symmetric, Quake-1-style plasma rifle sprite by rendering
     * into an offscreen FrameBuffer with ShapeRenderer (supports arbitrary
     * triangles / trapezoids), then reading pixels back into a Texture.
     *
     * Canvas coordinate system while rendering (ShapeRenderer Y-up):
     *   Y = 0   → bottom of canvas (grip, partially off-screen at runtime)
     *   Y = 134 → top of canvas (barrel tip / muzzle emitter)
     *
     * Layers (back-to-front):
     *   1. Pistol grip        — dark charcoal trapezoid, wider at top
     *   2. Trigger guard      — U-shape (three rects, open bottom)
     *   3. Main body          — wide steel-blue rect with highlight / shadow strips
     *   4. Energy coils       — 4 cyan horizontal bands + soft fringe glow
     *   5. Power cell         — right-side indicator with charge bars
     *   6. Upper receiver     — stepped section above body with bevel
     *   7. Targeting scope    — centered housing with green lens
     *   8. Barrel             — tapered trapezoid (2 triangles)
     *   9. Muzzle prongs      — flanking flanges
     *  10. Muzzle emitter     — layered concentric ellipses, blue → white
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
     * Draws every shape that makes up the plasma rifle silhouette.
     * All coordinates are pixel units; Y increases upward (ShapeRenderer default).
     * centerX = half the canvas width → sprite is perfectly symmetric.
     */
    private static void drawPlasmaRifleShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Pistol grip — charcoal trapezoid (narrow at bottom, wider at top)
        shapeRenderer.setColor(0.20f, 0.22f, 0.28f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 16f, 0f, 24f, 32f);
        shapeRenderer.setColor(0.32f, 0.36f, 0.44f, 1f);
        shapeRenderer.rect(centerX - 16f, 0f, 2f, 32f);   // left highlight edge
        shapeRenderer.setColor(0.13f, 0.15f, 0.20f, 1f);
        shapeRenderer.rect(centerX - 14f, 6f,  28f, 2f);  // ergonomic ridge
        shapeRenderer.rect(centerX - 14f, 13f, 28f, 2f);
        shapeRenderer.rect(centerX - 14f, 20f, 28f, 2f);

        // 2. Trigger guard — U-shape (left bar + right bar + top bar; bottom open)
        shapeRenderer.setColor(0.22f, 0.26f, 0.34f, 1f);
        shapeRenderer.rect(centerX - 18f, 22f, 6f, 20f);  // left vertical bar
        shapeRenderer.rect(centerX + 12f, 22f, 6f, 20f);  // right vertical bar
        shapeRenderer.rect(centerX - 18f, 38f, 36f,  4f); // top horizontal bar

        // 3. Main body — wide steel-blue rectangle
        shapeRenderer.setColor(0.28f, 0.32f, 0.42f, 1f);
        shapeRenderer.rect(24f, 38f, 144f, 48f);
        shapeRenderer.setColor(0.40f, 0.46f, 0.58f, 1f);
        shapeRenderer.rect(24f, 83f, 144f, 3f);            // top highlight strip
        shapeRenderer.setColor(0.18f, 0.21f, 0.29f, 1f);
        shapeRenderer.rect(24f, 38f, 144f, 3f);            // bottom shadow strip
        shapeRenderer.setColor(0.20f, 0.24f, 0.32f, 1f);
        shapeRenderer.rect(24f, 60f, 144f, 2f);            // mid groove accent

        // 4. Energy coils — 4 bright cyan horizontal bands with soft fringe
        float[] coilYPositions = {48f, 56f, 66f, 76f};
        shapeRenderer.setColor(0.00f, 0.88f, 1.00f, 1f);
        for (float coilY : coilYPositions) {
            shapeRenderer.rect(28f, coilY, 136f, 3f);
        }
        shapeRenderer.setColor(0.00f, 0.62f, 0.90f, 0.50f);
        for (float coilY : coilYPositions) {
            shapeRenderer.rect(28f, coilY - 1f, 136f, 1f); // glow fringe below
            shapeRenderer.rect(28f, coilY + 3f, 136f, 1f); // glow fringe above
        }

        // 5. Power cell indicator — right side of body
        shapeRenderer.setColor(0.16f, 0.20f, 0.28f, 1f);
        shapeRenderer.rect(148f, 46f, 14f, 28f);
        shapeRenderer.setColor(0.00f, 0.72f, 1.00f, 0.95f);
        shapeRenderer.rect(150f, 50f, 10f, 4f);
        shapeRenderer.rect(150f, 57f, 10f, 4f);
        shapeRenderer.rect(150f, 64f, 10f, 4f);
        shapeRenderer.rect(150f, 71f, 10f, 4f);

        // 6. Upper receiver — stepped section above main body
        shapeRenderer.setColor(0.26f, 0.30f, 0.40f, 1f);
        shapeRenderer.rect(48f, 86f, 96f, 16f);
        shapeRenderer.setColor(0.38f, 0.44f, 0.56f, 1f);
        shapeRenderer.rect(48f, 99f, 96f, 3f);             // top bevel
        shapeRenderer.setColor(0.16f, 0.18f, 0.26f, 1f);
        shapeRenderer.rect(48f, 86f, 96f, 3f);             // bottom shadow

        // 7. Targeting scope — centered on receiver
        shapeRenderer.setColor(0.16f, 0.19f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 20f, 88f, 40f, 14f);  // dark housing
        shapeRenderer.setColor(0.10f, 0.82f, 0.58f, 0.90f);
        shapeRenderer.rect(centerX - 16f, 90f, 32f, 10f);  // green lens
        shapeRenderer.setColor(0.40f, 1.00f, 0.75f, 0.55f);
        shapeRenderer.rect(centerX - 16f, 98f, 32f,  2f);  // lens top highlight

        // 8. Barrel — tapered trapezoid: wide at receiver, narrow at muzzle
        shapeRenderer.setColor(0.28f, 0.32f, 0.42f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 26f, 102f, 14f, 126f);
        shapeRenderer.setColor(0.38f, 0.44f, 0.56f, 1f);
        shapeRenderer.rect(centerX - 24f, 122f,  48f, 2f); // top edge highlight
        shapeRenderer.setColor(0.16f, 0.18f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 20f, 112f,  40f, 2f); // mid groove

        // 9. Muzzle prongs — flanking flanges at barrel tip
        shapeRenderer.setColor(0.22f, 0.26f, 0.34f, 1f);
        shapeRenderer.rect(centerX - 26f, 116f, 8f, 10f);  // left prong
        shapeRenderer.rect(centerX + 18f, 116f, 8f, 10f);  // right prong

        // 10. Muzzle emitter — concentric ellipses from dark housing to white-hot core
        shapeRenderer.setColor(0.18f, 0.22f, 0.30f, 1f);
        shapeRenderer.ellipse(centerX - 13f, 118f, 26f, 14f); // dark outer ring
        shapeRenderer.setColor(0.08f, 0.52f, 1.00f, 0.95f);
        shapeRenderer.ellipse(centerX - 11f, 120f, 22f, 11f); // outer blue glow
        shapeRenderer.setColor(0.30f, 0.82f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX -  8f, 121f, 16f,  9f); // mid bright-cyan
        shapeRenderer.setColor(0.75f, 0.97f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX -  5f, 123f, 10f,  7f); // hot white-cyan
        shapeRenderer.setColor(1.00f, 1.00f, 1.00f, 1f);
        shapeRenderer.ellipse(centerX -  3f, 125f,  6f,  5f); // hottest pinpoint
        shapeRenderer.setColor(0.08f, 0.52f, 1.00f, 0.30f);
        shapeRenderer.ellipse(centerX - 16f, 105f, 32f, 14f); // under-glow halo
    }

    /**
     * Generates a symmetric double-barrel shotgun sprite using ShapeRenderer into an
     * offscreen FrameBuffer.  Classic Doom / Quake 1 centred-weapon look.
     *
     * Canvas coordinate system (ShapeRenderer Y-up):
     *   Y =   0 → bottom of canvas (stock, partially off-screen at runtime)
     *   Y = 134 → top of canvas (muzzle bores)
     *
     * Layers drawn back-to-front:
     *   1. Wooden stock     — warm mahogany trapezoid with grain strips
     *   2. Trigger guard    — dark steel U-shape
     *   3. Main receiver    — wide gunmetal rectangle, highlight + shadow strips
     *   4. Ejector port     — shell-port recess on right side
     *   5. Fore-end slide   — pump handle with crosswise ridges
     *   6. Left barrel      — dark steel rectangle with edge highlights
     *   7. Right barrel     — mirror of left barrel
     *   8. Inter-barrel gap — dark channel + thin centre rib
     *   9. Muzzle end-caps  — flat top plates closing each barrel tube
     *  10. Bore openings    — near-black ellipses + rim-shine fringe
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
     * Draws a double-barrel pump-action shotgun silhouette.
     * All coordinates in canvas pixel space; Y=0 = bottom (stock), Y=134 = top (muzzles).
     * centerX = 96 keeps the weapon perfectly symmetric on the 192-wide canvas.
     */
    private static void drawShotgunShape(ShapeRenderer shapeRenderer, float centerX) {

        // 1. Wooden stock — warm mahogany trapezoid (narrow at bottom, wider at receiver)
        shapeRenderer.setColor(0.42f, 0.22f, 0.08f, 1f);
        drawSymmetricTrapezoid(shapeRenderer, centerX, 20f, 0f, 32f, 38f);
        // Wood grain strips (three darker bands)
        shapeRenderer.setColor(0.34f, 0.16f, 0.05f, 1f);
        shapeRenderer.rect(centerX - 20f, 8f,  40f, 3f);
        shapeRenderer.rect(centerX - 20f, 18f, 40f, 3f);
        shapeRenderer.rect(centerX - 20f, 28f, 40f, 3f);

        // 2. Trigger guard — dark steel U-shape (left bar + right bar + top bar)
        shapeRenderer.setColor(0.20f, 0.22f, 0.26f, 1f);
        shapeRenderer.rect(centerX - 20f, 24f, 6f, 20f);  // left vertical bar
        shapeRenderer.rect(centerX + 14f, 24f, 6f, 20f);  // right vertical bar
        shapeRenderer.rect(centerX - 20f, 40f, 40f, 4f);  // top horizontal bar

        // 3. Main receiver — wide dark gunmetal rectangle
        shapeRenderer.setColor(0.24f, 0.26f, 0.30f, 1f);
        shapeRenderer.rect(20f, 38f, 152f, 42f);
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        shapeRenderer.rect(20f, 77f, 152f, 3f);           // top highlight strip
        shapeRenderer.setColor(0.12f, 0.13f, 0.16f, 1f);
        shapeRenderer.rect(20f, 38f, 152f, 3f);           // bottom shadow strip
        shapeRenderer.setColor(0.14f, 0.15f, 0.18f, 1f);
        shapeRenderer.rect(20f, 58f, 152f, 2f);           // mid groove accent

        // 4. Ejector port — shell-loading recess on right side of receiver
        shapeRenderer.setColor(0.14f, 0.15f, 0.18f, 1f);
        shapeRenderer.rect(130f, 48f, 28f, 18f);          // dark recess
        shapeRenderer.setColor(0.45f, 0.48f, 0.54f, 1f);
        shapeRenderer.rect(130f, 64f, 28f,  2f);          // bright top rim

        // 5. Fore-end pump slide — protruding handle rail below barrels
        shapeRenderer.setColor(0.28f, 0.30f, 0.35f, 1f);
        shapeRenderer.rect(44f, 78f, 104f, 12f);
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        shapeRenderer.rect(44f, 88f, 104f,  2f);          // top highlight
        // Pump ridges — three vertical dark slots for grip texture
        shapeRenderer.setColor(0.16f, 0.17f, 0.20f, 1f);
        shapeRenderer.rect(68f,  80f, 4f, 8f);
        shapeRenderer.rect(94f,  80f, 4f, 8f);
        shapeRenderer.rect(120f, 80f, 4f, 8f);

        // 6. Left barrel — dark steel, x=44..88
        shapeRenderer.setColor(0.20f, 0.22f, 0.26f, 1f);
        shapeRenderer.rect(44f, 88f, 44f, 36f);
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        shapeRenderer.rect(44f, 88f,  2f, 36f);           // left outer highlight
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(86f, 88f,  2f, 36f);           // right inner shadow
        shapeRenderer.setColor(0.38f, 0.42f, 0.48f, 1f);
        shapeRenderer.rect(44f, 121f, 44f,  3f);          // top edge highlight

        // 7. Right barrel — mirror of left, x=104..148
        shapeRenderer.setColor(0.20f, 0.22f, 0.26f, 1f);
        shapeRenderer.rect(104f, 88f, 44f, 36f);
        shapeRenderer.setColor(0.10f, 0.11f, 0.14f, 1f);
        shapeRenderer.rect(104f, 88f,  2f, 36f);          // left inner shadow
        shapeRenderer.setColor(0.40f, 0.44f, 0.50f, 1f);
        shapeRenderer.rect(146f, 88f,  2f, 36f);          // right outer highlight
        shapeRenderer.setColor(0.38f, 0.42f, 0.48f, 1f);
        shapeRenderer.rect(104f, 121f, 44f,  3f);         // top edge highlight

        // 8. Inter-barrel gap — dark channel with thin centre rib
        shapeRenderer.setColor(0.08f, 0.08f, 0.10f, 1f);
        shapeRenderer.rect(88f, 88f, 16f, 36f);           // dark gap
        shapeRenderer.setColor(0.18f, 0.19f, 0.22f, 1f);
        shapeRenderer.rect(92f, 88f,  8f, 36f);           // centre rib (sighting rib)

        // 9. Muzzle end-caps — flat steel plates closing each barrel tube
        shapeRenderer.setColor(0.16f, 0.17f, 0.20f, 1f);
        shapeRenderer.rect(44f,  122f, 44f, 4f);          // left cap
        shapeRenderer.rect(104f, 122f, 44f, 4f);          // right cap

        // 10. Bore openings — near-black ellipses looking into the barrels
        shapeRenderer.setColor(0.04f, 0.04f, 0.05f, 0.95f);
        shapeRenderer.ellipse(50f,  122f, 32f, 10f);      // left bore
        shapeRenderer.ellipse(110f, 122f, 32f, 10f);      // right bore
        // Bore rim shine — thin bright fringe around the opening
        shapeRenderer.setColor(0.30f, 0.32f, 0.36f, 0.60f);
        shapeRenderer.ellipse(50f,  129f, 32f, 4f);       // left rim
        shapeRenderer.ellipse(110f, 129f, 32f, 4f);       // right rim
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

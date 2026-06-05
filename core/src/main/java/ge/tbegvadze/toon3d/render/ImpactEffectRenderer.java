package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Draws all impact effects owned by ImpactEffectSystem:
 *
 *   Pass 1 (ShapeRenderer, alpha-blend): hit-spark particles + death burst ring + core.
 *   Pass 2 (SpriteBatch + BitmapFont): floating damage numbers ("-N" / "-N!").
 *   Pass 3 (ShapeRenderer, additive-blend): kill-flash edge rectangles.
 *
 * World calls renderWorldEffects(camera) while the camera is shaken (so sparks
 * and bursts move with the world) and renderScreenOverlays(camera) after the
 * shake is removed (so numbers and the kill flash are screen-stable).
 *
 * Zero per-frame allocations: StringBuilder and font are pre-allocated; pool
 * arrays are read directly from ImpactEffectSystem.
 */
public final class ImpactEffectRenderer implements Disposable {

    // Dot size for death burst ring segments (world units)
    private static final float RING_DOT_SIZE = 3.5f;
    // Core circle radius at t=0, shrinks as burst ages
    private static final float CORE_MAX_RADIUS = 12f;

    private final ImpactEffectSystem system;
    private final ShapeRenderer      shapes;
    private final SpriteBatch        batch;
    private final BitmapFont         font;

    // Pre-allocated — no per-frame allocation
    private final StringBuilder stringBuilder = new StringBuilder(8);

    public ImpactEffectRenderer(ImpactEffectSystem system) {
        this.system = system;
        this.shapes = new ShapeRenderer();
        this.batch  = new SpriteBatch();
        this.font   = new BitmapFont();
        this.font.getData().markupEnabled = false;
        this.font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    // -------------------------------------------------------------------------
    // Public render entry points (called from World in distinct phases)
    // -------------------------------------------------------------------------

    /**
     * Renders particles and death bursts.
     * Call while the camera is offset by the shake translation so effects move
     * with the 3D world.
     */
    public void renderWorldEffects(OrthographicCamera camera) {
        boolean anyParticles = false;
        for (int particleIndex = 0; particleIndex < ImpactEffectSystem.MAX_PARTICLES; particleIndex++) {
            if (system.particleActive[particleIndex]) { anyParticles = true; break; }
        }
        boolean anyBursts = false;
        for (int burstIndex = 0; burstIndex < ImpactEffectSystem.MAX_DEATH_BURSTS; burstIndex++) {
            if (system.burstActive[burstIndex]) { anyBursts = true; break; }
        }
        if (!anyParticles && !anyBursts) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        if (anyParticles) drawParticles();
        if (anyBursts)    drawDeathBursts();

        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Renders damage numbers and the kill-flash edge overlay.
     * Call after the camera shake has been removed so these elements are
     * screen-stable (they feel like UI feedback, not world geometry).
     */
    public void renderScreenOverlays(OrthographicCamera camera) {
        drawDamageNumbers(camera);
        drawKillFlash(camera);
    }

    // -------------------------------------------------------------------------
    // Pass 1a: hit-spark particles
    // -------------------------------------------------------------------------

    private void drawParticles() {
        for (int particleIndex = 0; particleIndex < ImpactEffectSystem.MAX_PARTICLES; particleIndex++) {
            if (!system.particleActive[particleIndex]) continue;

            float age    = system.particleAge[particleIndex];
            float life   = system.particleLife[particleIndex];
            float fraction = age / life;           // 0 at spawn, 1 at expiry
            float alpha    = 1f - fraction;        // linear fade out
            float size     = system.particleSize[particleIndex] * (1f - fraction * 0.5f);

            float screenX = system.particleScreenX[particleIndex] - size / 2f;
            float screenY = system.particleScreenY[particleIndex] - size / 2f;

            // Colour: interpolate birth colour toward dark (desaturates as it dies)
            float red   = system.particleRed[particleIndex]   * (1f - fraction * 0.4f);
            float green = system.particleGreen[particleIndex] * (1f - fraction * 0.6f);
            float blue  = system.particleBlue[particleIndex]  * (1f - fraction * 0.8f);

            shapes.setColor(red, green, blue, alpha);
            shapes.rect(screenX, screenY, size, size);
        }
    }

    // -------------------------------------------------------------------------
    // Pass 1b: death burst ring + core
    // -------------------------------------------------------------------------

    private void drawDeathBursts() {
        for (int burstIndex = 0; burstIndex < ImpactEffectSystem.MAX_DEATH_BURSTS; burstIndex++) {
            if (!system.burstActive[burstIndex]) continue;

            float age      = system.burstAge[burstIndex];
            float life     = system.burstLife[burstIndex];
            float fraction = age / life;           // 0→1 over burst lifetime

            float centerX    = system.burstScreenX[burstIndex];
            float centerY    = system.burstScreenY[burstIndex];
            float maxRadius  = system.burstMaxRadius[burstIndex];

            // Ring expands outward and fades in the second half
            float ringRadius = maxRadius * fraction;
            float ringAlpha;
            if (fraction < 0.5f) {
                ringAlpha = fraction / 0.5f;         // fade in during first half
            } else {
                ringAlpha = 1f - (fraction - 0.5f) / 0.5f; // fade out during second half
            }

            // Ring: hot white → orange as it expands
            float ringRed   = 1f;
            float ringGreen = GameMath.lerp(0.9f, 0.3f, fraction);
            float ringBlue  = GameMath.lerp(0.6f, 0f,   fraction);

            // Draw ring as evenly-spaced dots on the circle circumference
            for (int segmentIndex = 0; segmentIndex < ImpactEffectSystem.RING_SEGMENTS; segmentIndex++) {
                float dotX = centerX + ImpactEffectSystem.RING_COS[segmentIndex] * ringRadius
                             - RING_DOT_SIZE / 2f;
                float dotY = centerY + ImpactEffectSystem.RING_SIN[segmentIndex] * ringRadius
                             - RING_DOT_SIZE / 2f;
                shapes.setColor(ringRed, ringGreen, ringBlue, ringAlpha);
                shapes.rect(dotX, dotY, RING_DOT_SIZE, RING_DOT_SIZE);
            }

            // White-hot core: a filled disc that shrinks as the ring expands
            float coreRadius = CORE_MAX_RADIUS * (1f - fraction);
            if (coreRadius > 0.5f) {
                float coreAlpha = 1f - fraction;
                shapes.setColor(1f, 0.95f, 0.8f, coreAlpha);
                shapes.circle(centerX, centerY, coreRadius, 10);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pass 2: floating damage numbers (SpriteBatch + BitmapFont)
    // -------------------------------------------------------------------------

    private void drawDamageNumbers(OrthographicCamera camera) {
        boolean anyNumbers = false;
        for (int numberIndex = 0; numberIndex < ImpactEffectSystem.MAX_DAMAGE_NUMBERS; numberIndex++) {
            if (system.numberActive[numberIndex]) { anyNumbers = true; break; }
        }
        if (!anyNumbers) return;

        // Re-read player state from system for per-frame projection
        float playerX   = system.getPlayerWorldX();
        float playerY   = system.getPlayerWorldY();
        float directionX = system.getDirectionX();
        float directionY = system.getDirectionY();
        float planeX    = system.getPlaneX();
        float planeY    = system.getPlaneY();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        for (int numberIndex = 0; numberIndex < ImpactEffectSystem.MAX_DAMAGE_NUMBERS; numberIndex++) {
            if (!system.numberActive[numberIndex]) continue;

            int amount = system.numberAmount[numberIndex];
            float fontScale;
            if (amount < Constants.DAMAGE_NUMBER_SCALE_THRESHOLD) {
                fontScale = Constants.DAMAGE_NUMBER_FONT_SCALE;
            } else {
                fontScale = Math.min(
                    Constants.DAMAGE_NUMBER_FONT_SCALE
                        + (amount - Constants.DAMAGE_NUMBER_SCALE_THRESHOLD + 1)
                          * Constants.DAMAGE_NUMBER_SCALE_PER_POINT,
                    Constants.DAMAGE_NUMBER_MAX_FONT_SCALE
                );
            }
            font.getData().setScale(fontScale);

            float age      = system.numberAge[numberIndex];
            float life     = system.numberLife[numberIndex];
            float fraction = age / life;

            // Fade curve: appear instantly, hold, then fade out in the last 60%
            float alpha;
            if (fraction < 0.15f) {
                alpha = fraction / 0.15f;                   // quick fade in
            } else if (fraction < 0.40f) {
                alpha = 1f;                                  // hold at full
            } else {
                alpha = 1f - (fraction - 0.40f) / 0.60f;   // fade out
            }
            alpha = Math.max(0f, Math.min(1f, alpha));

            // Re-project world position each frame so the number tracks camera rotation
            float worldX      = system.numberWorldX[numberIndex];
            float worldY      = system.numberWorldY[numberIndex];
            float tileOffsetX = (worldX - playerX) / Constants.CELL_SIZE;
            float tileOffsetY = (worldY - playerY) / Constants.CELL_SIZE;
            float depth       = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
            if (depth <= Constants.PROP_BEHIND_PLAYER_EPSILON_TILES) continue;

            float screenColumn = GameMath.spriteScreenColumnCenter(
                    tileOffsetX, tileOffsetY, directionX, directionY,
                    planeX, planeY, Constants.WALL_PROJECTION_SCREEN_WIDTH);

            // Y anchor: top of the enemy sprite + rising offset
            float halfSpriteHeight = Constants.WALL_PROJECTION_SCREEN_HEIGHT / (2f * depth);
            float spriteTop        = Constants.WALL_PROJECTION_SCREEN_HEIGHT / 2f + halfSpriteHeight;
            float riseOffset       = Constants.DAMAGE_NUMBER_RISE_SPEED * age;
            float screenY          = spriteTop + riseOffset + 10f;

            // Colour: kill = bold gold, hit = orange-red
            if (system.numberIsKill[numberIndex]) {
                font.setColor(1f, 0.85f, 0.10f, alpha);
            } else {
                font.setColor(1f, 0.40f, 0.15f, alpha);
            }

            // Build "-N" (and "-N!" for kills) without String allocation
            stringBuilder.setLength(0);
            stringBuilder.append('-');
            stringBuilder.append(system.numberAmount[numberIndex]);
            if (system.numberIsKill[numberIndex]) stringBuilder.append('!');
            font.draw(batch, stringBuilder, screenColumn, screenY);
        }

        // Restore font state so other renderers using BitmapFont are not affected
        font.getData().setScale(1f);
        font.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    // -------------------------------------------------------------------------
    // Pass 3: kill-flash screen-edge overlay (additive blend for brightness burst)
    // -------------------------------------------------------------------------

    private void drawKillFlash(OrthographicCamera camera) {
        float flashAlpha = system.getKillFlashAlpha();
        if (flashAlpha <= 0f) return;

        float width  = Constants.WORLD_WIDTH;
        float height = Constants.WORLD_HEIGHT;
        float edgeThickness = Constants.KILL_FLASH_EDGE_THICKNESS;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        // Additive blend: the edge rectangles add light to whatever is already rendered,
        // giving a "screen saturates to white" feel without obscuring the centre.
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Warm white (slightly yellow-orange) for a gunshot-flash feel
        shapes.setColor(1f, 0.88f, 0.72f, flashAlpha);
        shapes.rect(0f,                      0f,                       width,         edgeThickness); // bottom
        shapes.rect(0f,                      height - edgeThickness,   width,         edgeThickness); // top
        shapes.rect(0f,                      0f,                       edgeThickness, height);        // left
        shapes.rect(width - edgeThickness,   0f,                       edgeThickness, height);        // right

        shapes.end();
        // Restore standard alpha-blend so subsequent renderers are not disrupted
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // -------------------------------------------------------------------------
    // Disposable
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}

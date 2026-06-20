package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Draws screen-space event text and tiered ability banners from EventTextSystem.
 *
 * Legacy slots (bannerTier == 0):
 *   Centred horizontally, rise by EVENT_TEXT_RISE_PIXELS over their lifetime, fade to
 *   transparent.  A one-pixel dark shadow improves readability.  Stacking keeps
 *   concurrent texts from overlapping.
 *
 * Banner slots (bannerTier == TIER_PROC / SLAM / LEGENDARY):
 *   Tier 2 (PROC)      — scale 1.6×, pop-in at ABILITY_BANNER_PROC_ANCHOR_Y.
 *   Tier 3 (SLAM)      — scale 2.4×, punch-in at ABILITY_BANNER_SLAM_ANCHOR_Y, plus a
 *                         1-pixel underline drawn with ShapeRenderer before the text pass.
 *   Tier 4 (LEGENDARY) — scale 3.0×, punch-in at ABILITY_BANNER_LEGENDARY_ANCHOR_Y,
 *                         plus underline.  Holds at full opacity for LEGENDARY_HOLD_SEC
 *                         before fading.
 *
 * Render order: ShapeRenderer underlines first (Tier 3/4), then SpriteBatch text.
 * Zero allocations in render() — GlyphLayout and temporaryColor are pre-allocated.
 */
public final class EventTextRenderer implements Disposable {

    private static final float LEGACY_FONT_SCALE = 1.8f;
    // Underline vertical offset below the text baseline
    private static final float UNDERLINE_BELOW_BASELINE = 6f;
    // Underline height in world units
    private static final float UNDERLINE_HEIGHT = 2f;
    // Underline horizontal padding beyond text width
    private static final float UNDERLINE_PADDING = 8f;

    // Base palette — never mutated; per-frame alpha is written into temporaryColor only.
    private static final Color BASE_SHADOW = new Color(0f,    0f,    0f,    1f);
    private static final Color BASE_WHITE  = new Color(1f,    1f,    1f,    1f);
    private static final Color BASE_GREEN  = new Color(0.25f, 1f,    0.25f, 1f);
    private static final Color BASE_RED    = new Color(1f,    0.18f, 0.18f, 1f);
    private static final Color BASE_GREY   = new Color(0.8f,  0.8f,  0.8f,  1f);
    private static final Color BASE_GOLD   = new Color(1f,    0.69f, 0.125f, 1f);

    private final EventTextSystem eventTextSystem;
    private final SpriteBatch     batch;
    private final BitmapFont      font;
    private final GlyphLayout     layout;
    private final ShapeRenderer   shapes;
    // Reusable scratch color — receives a base color copy then has alpha set per draw call.
    private final Color           temporaryColor = new Color();

    public EventTextRenderer(EventTextSystem eventTextSystem) {
        this.eventTextSystem = eventTextSystem;
        this.batch           = new SpriteBatch();
        this.shapes          = new ShapeRenderer();
        this.font            = new BitmapFont();
        this.font.getData().setScale(LEGACY_FONT_SCALE);
        this.font.getData().markupEnabled = false;
        this.layout          = new GlyphLayout();
    }

    public void render(OrthographicCamera camera) {
        // ── Pass 1: ShapeRenderer underlines for Tier 3 and Tier 4 banners ──────
        drawUnderlines(camera);

        // ── Pass 2: SpriteBatch text for all active slots ─────────────────────
        drawAllText(camera);
    }

    // -------------------------------------------------------------------------
    // Pass 1 — underlines (ShapeRenderer, alpha blend)
    // -------------------------------------------------------------------------

    private void drawUnderlines(OrthographicCamera camera) {
        boolean anyTier34 = false;
        for (int slotIndex = 0; slotIndex < eventTextSystem.getPoolSize(); slotIndex++) {
            if (eventTextSystem.getText(slotIndex) == null) continue;
            byte tier = eventTextSystem.getBannerTier(slotIndex);
            if (tier >= EventTextSystem.TIER_SLAM) { anyTier34 = true; break; }
        }
        if (!anyTier34) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        int slamStack = 0;
        for (int slotIndex = 0; slotIndex < eventTextSystem.getPoolSize(); slotIndex++) {
            String text = eventTextSystem.getText(slotIndex);
            if (text == null) continue;
            byte tier = eventTextSystem.getBannerTier(slotIndex);
            if (tier < EventTextSystem.TIER_SLAM) continue;

            float age      = eventTextSystem.getAge(slotIndex);
            float fraction = age / EffectConstants.EVENT_TEXT_LIFE_SECONDS;
            float alpha    = computeAlpha(tier, age, fraction);

            float fontScale = fontScaleForTier(tier);
            float baseY     = baseYForTierSlam(tier, slamStack);
            if (tier == EventTextSystem.TIER_SLAM) slamStack++;

            font.getData().setScale(fontScale);
            layout.setText(font, text);

            float textX       = (Constants.WORLD_WIDTH - layout.width) / 2f;
            float underlineY  = baseY - UNDERLINE_BELOW_BASELINE;
            float underlineX  = textX - UNDERLINE_PADDING;
            float underlineW  = layout.width + UNDERLINE_PADDING * 2f;

            float red   = eventTextSystem.getBannerRed(slotIndex);
            float green = eventTextSystem.getBannerGreen(slotIndex);
            float blue  = eventTextSystem.getBannerBlue(slotIndex);
            shapes.setColor(red, green, blue, alpha * 0.85f);
            shapes.rect(underlineX, underlineY, underlineW, UNDERLINE_HEIGHT);
        }

        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        // Reset font scale so the text pass starts from a known state.
        font.getData().setScale(LEGACY_FONT_SCALE);
    }

    // -------------------------------------------------------------------------
    // Pass 2 — all text (SpriteBatch + BitmapFont)
    // -------------------------------------------------------------------------

    private void drawAllText(OrthographicCamera camera) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        int legacyStack = 0;
        int procStack   = 0;
        int slamStack   = 0;

        for (int slotIndex = 0; slotIndex < eventTextSystem.getPoolSize(); slotIndex++) {
            String text = eventTextSystem.getText(slotIndex);
            if (text == null) continue;

            float age      = eventTextSystem.getAge(slotIndex);
            float fraction = age / EffectConstants.EVENT_TEXT_LIFE_SECONDS;
            byte  tier     = eventTextSystem.getBannerTier(slotIndex);

            float alpha     = computeAlpha(tier, age, fraction);
            float fontScale = fontScaleForTier(tier);
            float popScale  = computePopScale(tier, age);
            float baseY;

            if (tier == EventTextSystem.TIER_PROC) {
                baseY = EffectConstants.ABILITY_BANNER_PROC_ANCHOR_Y
                        + procStack * EffectConstants.EVENT_TEXT_LINE_STEP;
                baseY = Math.max(baseY, EffectConstants.ABILITY_BANNER_MIN_Y);
                procStack++;
            } else if (tier == EventTextSystem.TIER_SLAM) {
                baseY = EffectConstants.ABILITY_BANNER_SLAM_ANCHOR_Y
                        + slamStack * EffectConstants.EVENT_TEXT_LINE_STEP * 2f;
                baseY = Math.max(baseY, EffectConstants.ABILITY_BANNER_MIN_Y);
                slamStack++;
            } else if (tier == EventTextSystem.TIER_LEGENDARY) {
                baseY = EffectConstants.ABILITY_BANNER_LEGENDARY_ANCHOR_Y;
                baseY = Math.max(baseY, EffectConstants.ABILITY_BANNER_MIN_Y);
            } else {
                // Legacy path: rise upward over lifetime
                float riseY = fraction * EffectConstants.EVENT_TEXT_RISE_PIXELS;
                baseY = EffectConstants.EVENT_TEXT_ANCHOR_Y
                        + legacyStack * EffectConstants.EVENT_TEXT_LINE_STEP + riseY;
                legacyStack++;
            }

            float effectiveScale = fontScale * popScale;
            font.getData().setScale(effectiveScale);
            layout.setText(font, text);
            float textX = (Constants.WORLD_WIDTH - layout.width) / 2f;

            // Shadow (one pixel offset)
            temporaryColor.set(BASE_SHADOW);
            temporaryColor.a = alpha * 0.7f;
            font.setColor(temporaryColor);
            font.draw(batch, text, textX + 1f, baseY - 1f);

            // Main text color
            if (tier != 0) {
                temporaryColor.set(
                    eventTextSystem.getBannerRed(slotIndex),
                    eventTextSystem.getBannerGreen(slotIndex),
                    eventTextSystem.getBannerBlue(slotIndex),
                    alpha
                );
            } else {
                temporaryColor.set(resolveBaseColor(eventTextSystem.getColorType(slotIndex)));
                temporaryColor.a = alpha;
            }
            font.setColor(temporaryColor);
            font.draw(batch, text, textX, baseY);
        }

        // Restore font to default so other users of shared BitmapFont state are unaffected.
        font.getData().setScale(LEGACY_FONT_SCALE);
        font.setColor(Color.WHITE);
        batch.end();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static float fontScaleForTier(byte tier) {
        switch (tier) {
            case EventTextSystem.TIER_PROC:      return EffectConstants.ABILITY_BANNER_PROC_SCALE;
            case EventTextSystem.TIER_SLAM:      return EffectConstants.ABILITY_BANNER_SLAM_SCALE;
            case EventTextSystem.TIER_LEGENDARY: return EffectConstants.ABILITY_BANNER_LEGENDARY_SCALE;
            default:                             return LEGACY_FONT_SCALE;
        }
    }

    /*
     * Formula: banner alpha per tier
     * Derivation:
     *   Legacy / PROC / SLAM: linear 1→0 fade over EVENT_TEXT_LIFE_SECONDS.
     *   LEGENDARY: holds at 1.0 for LEGENDARY_HOLD_SEC then linearly fades over the
     *   remaining (EVENT_TEXT_LIFE_SECONDS − LEGENDARY_HOLD_SEC).
     * Edge cases:
     *   hold time >= life time: alpha stays 1.0 for the full duration (never fades).
     */
    private static float computeAlpha(byte tier, float age, float fraction) {
        if (tier == EventTextSystem.TIER_LEGENDARY) {
            float holdSeconds = EffectConstants.ABILITY_BANNER_LEGENDARY_HOLD_SEC;
            float fadeSeconds = EffectConstants.EVENT_TEXT_LIFE_SECONDS - holdSeconds;
            if (age <= holdSeconds || fadeSeconds <= 0f) return 1f;
            float fadeProgress = (age - holdSeconds) / fadeSeconds;
            return Math.max(0f, 1f - fadeProgress);
        }
        return 1f - fraction;
    }

    /*
     * Formula: banner pop scale
     * Derivation:
     *   Uses GameMath.bannerPopScale with a tier-appropriate overshoot and animation
     *   duration.  Tier 2 gets a small pop (0.25 overshoot, 0.08s); Tier 3/4 get the
     *   full punch-in (PUNCH_OVERSHOOT, 0.12s).
     */
    private static float computePopScale(byte tier, float age) {
        switch (tier) {
            case EventTextSystem.TIER_PROC:
                return GameMath.bannerPopScale(
                    age / EffectConstants.ABILITY_BANNER_POP_DURATION_SEC, 0.25f);
            case EventTextSystem.TIER_SLAM:
            case EventTextSystem.TIER_LEGENDARY:
                return GameMath.bannerPopScale(
                    age / EffectConstants.ABILITY_BANNER_PUNCH_DURATION_SEC,
                    EffectConstants.ABILITY_BANNER_PUNCH_OVERSHOOT);
            default:
                return 1f;
        }
    }

    private static float baseYForTierSlam(byte tier, int slamStack) {
        if (tier == EventTextSystem.TIER_LEGENDARY) {
            return EffectConstants.ABILITY_BANNER_LEGENDARY_ANCHOR_Y;
        }
        return EffectConstants.ABILITY_BANNER_SLAM_ANCHOR_Y
               + slamStack * EffectConstants.EVENT_TEXT_LINE_STEP * 2f;
    }

    private static Color resolveBaseColor(byte colorType) {
        switch (colorType) {
            case EventTextSystem.COLOR_GREEN: return BASE_GREEN;
            case EventTextSystem.COLOR_RED:   return BASE_RED;
            case EventTextSystem.COLOR_GREY:  return BASE_GREY;
            case EventTextSystem.COLOR_GOLD:  return BASE_GOLD;
            default:                          return BASE_WHITE;
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}

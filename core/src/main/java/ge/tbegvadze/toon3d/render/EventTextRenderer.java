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
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.CombatPalette;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Draws screen-space event text and tiered ability banners from EventTextSystem.
 *
 * Legacy slots (bannerTier == 0):
 *   Centred horizontally, rise by EVENT_TEXT_RISE_PIXELS over their lifetime, fade to
 *   transparent.  Drawn through UiKit.text (the kit's drop shadow).  Stacking keeps
 *   concurrent texts from overlapping.
 *
 * Banner slots (bannerTier == TIER_PROC / SLAM / LEGENDARY):
 *   Tier 2 (PROC)      — TYPE_HEADING, pop-in at ABILITY_BANNER_PROC_ANCHOR_Y.
 *   Tier 3 (SLAM)      — TYPE_HEADING, punch-in at ABILITY_BANNER_SLAM_ANCHOR_Y, plus a
 *                         1-pixel underline drawn with ShapeRenderer before the text pass.
 *   Tier 4 (LEGENDARY) — TYPE_DISPLAY, punch-in at ABILITY_BANNER_LEGENDARY_ANCHOR_Y,
 *                         plus underline.  Holds at full opacity for LEGENDARY_HOLD_SEC
 *                         before fading.
 *
 * Render order: ShapeRenderer underlines first (Tier 3/4), then SpriteBatch text.
 * Zero allocations in render() — GlyphLayout and temporaryColor are pre-allocated.
 */
public final class EventTextRenderer implements Disposable {

    private static final float LEGACY_FONT_SCALE = EffectConstants.EVENT_TEXT_FONT_SCALE;
    // Underline vertical offset below the text baseline
    private static final float UNDERLINE_BELOW_BASELINE = 6f;
    // Underline height in world units
    private static final float UNDERLINE_HEIGHT = 2f;
    // Underline horizontal padding beyond text width
    private static final float UNDERLINE_PADDING = 8f;

    // Colour table indexed by EventTextSystem's colour codes (visual-identity-order-4 R13): SUIT ink for neutrals
    // and the player's own things, semantic colours only where they carry their meaning. Never mutated.
    private static final Color BASE_WHITE        = new Color(ArtDirection.UI_TEXT_PRIMARY_RED,
            ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
    private static final Color BASE_GREEN        = new Color(ArtDirection.HEALTH_GREEN_RED,
            ArtDirection.HEALTH_GREEN_GREEN, ArtDirection.HEALTH_GREEN_BLUE, 1f);
    private static final Color BASE_RED          = new Color(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN,
            CombatPalette.DAMAGE_BLUE, 1f);
    private static final Color BASE_GREY         = new Color(ArtDirection.UI_TEXT_SECONDARY_RED,
            ArtDirection.UI_TEXT_SECONDARY_GREEN, ArtDirection.UI_TEXT_SECONDARY_BLUE, 1f);
    private static final Color BASE_GOLD         = new Color(ArtDirection.SUIT_AMBER_RED,
            ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE, 1f);
    /** Credits are earned, so the credit code reads CREDITS_GOLD (AS4), like the HUD's credits readout. */
    private static final Color BASE_CREDIT_CYAN  = new Color(ArtDirection.CREDITS_GOLD_RED,
            ArtDirection.CREDITS_GOLD_GREEN, ArtDirection.CREDITS_GOLD_BLUE, 1f);
    private static final Color BASE_BLUE         = new Color(CombatPalette.DEFENSE_RED, CombatPalette.DEFENSE_GREEN,
            CombatPalette.DEFENSE_BLUE, 1f);
    /** The armour meter's blue (HudRenderer). */
    private static final Color BASE_ARMOUR       = new Color(ArtDirection.ARMOUR_BLUE_RED,
            ArtDirection.ARMOUR_BLUE_GREEN, ArtDirection.ARMOUR_BLUE_BLUE, 1f);
    private static final Color BASE_AMBER        = new Color(ArtDirection.SUIT_AMBER_RED,
            ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE, 1f);

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
        this.font            = GameFonts.newFont(FontFace.HEAVY);
        this.font.getData().setScale(LEGACY_FONT_SCALE);
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
            float fraction = age / eventTextSystem.getLifeSeconds(slotIndex);
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
            float fraction = age / eventTextSystem.getLifeSeconds(slotIndex);
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

            // Main text color — drawn through UiKit.text, so every line carries the kit's drop shadow (R13)
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
            UiKit.text(batch, font, text, textX, baseY,
                    temporaryColor.r, temporaryColor.g, temporaryColor.b, temporaryColor.a);
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
            case EventTextSystem.COLOR_GREEN:        return BASE_GREEN;
            case EventTextSystem.COLOR_RED:          return BASE_RED;
            case EventTextSystem.COLOR_GREY:         return BASE_GREY;
            case EventTextSystem.COLOR_GOLD:         return BASE_GOLD;
            case EventTextSystem.COLOR_CREDIT_CYAN:  return BASE_CREDIT_CYAN;
            case EventTextSystem.COLOR_BLUE:         return BASE_BLUE;
            case EventTextSystem.COLOR_ARMOUR:       return BASE_ARMOUR;
            case EventTextSystem.COLOR_AMBER:        return BASE_AMBER;
            default:                                 return BASE_WHITE;
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}

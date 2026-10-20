package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.relic.RelicDefinition;
import ge.tbegvadze.toon3d.relic.RelicOffer;
import ge.tbegvadze.toon3d.relic.RelicRarity;
import ge.tbegvadze.toon3d.relic.RelicSource;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.util.ProgressionConstants;

/**
 * The RELIC_OFFER card screen (relics-run-changing-artifacts R14/R15, VISUAL DESIGN). Same card pipeline as
 * {@code LevelUpOverlayRenderer}: scrim, soft glow, drop shadow, gradient body, rarity header strip carrying the
 * NAME, border, drop-shadowed Linear-filtered text. Each card also carries a rarity label (top-left), a trigger
 * badge chip (top-right), the word-wrapped effect line, for CURSED a divider + red cost line, and for an affine
 * relic its {@link BuildTag} chip in {@link BuildTagPalette}'s colour. LEGENDARY glows with a slow pulse and
 * CURSED wears a dark-red inner vignette; both run off the {@code openSeconds} World passes in (cosmetic only).
 *
 * <h2>No allocation in render()</h2>
 * Every text layout (names, rarity labels, trigger badges, wrapped effect and cost lines, tag chips, both titles,
 * LEAVE IT) is a pre-allocated {@link GlyphLayout} filled by {@link #rebuild} the first time a NEW offer is seen
 * (a different source / card count / card identities than the cached ones). {@link #render} only draws them.
 *
 * <h2>Touch</h2>
 * {@link #cardIndexAt} and {@link #isLeaveAt} share the exact geometry used for drawing. Coordinates must be
 * world units (viewport-unprojected). The 0.4 s input lockout (R15) is World's, not this class's.
 *
 * <p>Owns a ShapeRenderer, SpriteBatch and BitmapFont, all disposed in {@link #dispose()}.</p>
 */
public final class RelicOfferOverlayRenderer implements Disposable {

    private static final int MAX_CARDS = BalanceConfig.RELIC_OFFER_MAX_CARDS;
    private static final int RARITY_COUNT = RelicRarity.values().length;

    private static final float CARD_WIDTH  = HudConstants.RELIC_OFFER_CARD_WIDTH;
    private static final float CARD_HEIGHT = HudConstants.RELIC_OFFER_CARD_HEIGHT;
    private static final float CARD_GAP    = HudConstants.RELIC_OFFER_CARD_GAP;
    private static final float CARD_Y      = HudConstants.RELIC_OFFER_CARD_Y;
    private static final float CARD_TOP_Y  = CARD_Y + CARD_HEIGHT;
    private static final float LEAVE_X     = (Constants.WORLD_WIDTH - HudConstants.RELIC_OFFER_LEAVE_WIDTH) / 2f;
    private static final float PULSE_HERTZ = 1f / HudConstants.RELIC_OFFER_LEGENDARY_PULSE_SECONDS;

    private static final Color SCRIM        = new Color(0.02f, 0.02f, 0.04f, HudConstants.RELIC_OFFER_SCRIM_ALPHA);
    private static final Color CARD_SHADOW  = new Color(0f, 0f, 0f, 0.50f);
    private static final Color TEXT_SHADOW  = new Color(0f, 0f, 0f, 0.72f);
    private static final Color WHITE        = new Color(0.97f, 0.97f, 0.98f, 1f);
    private static final Color GOLD         = new Color(1.00f, 0.88f, 0.20f, 1f);
    private static final Color INNER_EDGE   = new Color(1f, 1f, 1f, 0.16f);
    private static final Color DIVIDER      = new Color(1f, 1f, 1f, 0.20f);
    private static final Color COST_RED     = new Color(HudConstants.RELIC_COST_TEXT_COLOR_R,
            HudConstants.RELIC_COST_TEXT_COLOR_G, HudConstants.RELIC_COST_TEXT_COLOR_B, 1f);
    private static final Color CHIP_TEXT    = new Color(ProgressionConstants.BUILD_CHIP_TEXT_RED,
            ProgressionConstants.BUILD_CHIP_TEXT_GREEN, ProgressionConstants.BUILD_CHIP_TEXT_BLUE, 1f);
    private static final Color LEAVE_FILL   = new Color(HudConstants.RELIC_OFFER_LEAVE_R,
            HudConstants.RELIC_OFFER_LEAVE_G, HudConstants.RELIC_OFFER_LEAVE_B, 1f);
    private static final Color LEAVE_TEXT   = new Color(HudConstants.RELIC_OFFER_LEAVE_TEXT_R,
            HudConstants.RELIC_OFFER_LEAVE_TEXT_G, HudConstants.RELIC_OFFER_LEAVE_TEXT_B, 1f);

    // Per-rarity colours, indexed by RelicRarity ordinal (HudConstants arrays; never a switch).
    private final Color[] rarityAccent      = new Color[RARITY_COUNT];
    private final Color[] rarityBodyTop     = new Color[RARITY_COUNT];
    private final Color[] rarityBodyBottom  = new Color[RARITY_COUNT];
    private final Color[] rarityStripTop    = new Color[RARITY_COUNT];
    private final Color[] rarityStripBottom = new Color[RARITY_COUNT];
    private final Color   glowColor         = new Color();
    private final Color   vignetteColor     = new Color();

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;

    // ---- Cache of the offer these layouts were built for (identity compare, allocation-free) ----
    private RelicSource cachedSource = null;
    private int         cachedCount  = 0;
    private final RelicDefinition[] cachedCards = new RelicDefinition[MAX_CARDS];

    // ---- Pre-built text, one slot per card ----
    private final GlyphLayout[] nameLayouts    = newLayouts(MAX_CARDS);
    private final GlyphLayout[] nameShadows    = newLayouts(MAX_CARDS);
    private final GlyphLayout[] rarityLayouts  = newLayouts(MAX_CARDS);
    private final GlyphLayout[] badgeLayouts   = newLayouts(MAX_CARDS);
    private final GlyphLayout[] effectLayouts  = newLayouts(MAX_CARDS);
    private final GlyphLayout[] effectShadows  = newLayouts(MAX_CARDS);
    private final GlyphLayout[] costLayouts    = newLayouts(MAX_CARDS);
    private final GlyphLayout[] costShadows    = newLayouts(MAX_CARDS);
    private final GlyphLayout[] tagLayouts     = newLayouts(MAX_CARDS);
    private final boolean[]     hasCost        = new boolean[MAX_CARDS];
    private final boolean[]     hasTag         = new boolean[MAX_CARDS];
    private final int[]         rarityOrdinal  = new int[MAX_CARDS];
    private final boolean[]     cardCursed     = new boolean[MAX_CARDS];
    private final boolean[]     cardPulses     = new boolean[MAX_CARDS];
    private final Color[]       tagColors      = new Color[MAX_CARDS];

    private final GlyphLayout titleLayout            = new GlyphLayout();
    private final GlyphLayout titleShadow            = new GlyphLayout();
    private final GlyphLayout quarantineTitleLayout  = new GlyphLayout();
    private final GlyphLayout quarantineTitleShadow  = new GlyphLayout();
    private final GlyphLayout leaveLayout            = new GlyphLayout();
    private boolean quarantineTitle = false;

    public RelicOfferOverlayRenderer() {
        shapes = new ShapeRenderer();
        batch  = new SpriteBatch();
        font   = GameFonts.newFont(FontFace.REGULAR);
        font.setUseIntegerPositions(false);

        for (int rarityIndex = 0; rarityIndex < RARITY_COUNT; rarityIndex++) {
            Color accent = new Color(HudConstants.RELIC_RARITY_COLOR_R[rarityIndex],
                    HudConstants.RELIC_RARITY_COLOR_G[rarityIndex], HudConstants.RELIC_RARITY_COLOR_B[rarityIndex], 1f);
            rarityAccent[rarityIndex]      = accent;
            rarityBodyTop[rarityIndex]     = new Color(accent.r * 0.20f, accent.g * 0.20f, accent.b * 0.20f, 0.98f);
            rarityBodyBottom[rarityIndex]  = new Color(accent.r * 0.07f, accent.g * 0.07f, accent.b * 0.07f, 0.98f);
            rarityStripTop[rarityIndex]    = new Color(accent.r * 0.92f, accent.g * 0.92f, accent.b * 0.92f, 1f);
            rarityStripBottom[rarityIndex] = new Color(accent.r * 0.57f, accent.g * 0.57f, accent.b * 0.57f, 1f);
        }

        // Fixed texts: built once, here.
        font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_TITLE);
        titleLayout.setText(font, HudConstants.RELIC_OFFER_TITLE_TEXT, GOLD, 0f, Align.left, false);
        titleShadow.setText(font, HudConstants.RELIC_OFFER_TITLE_TEXT, TEXT_SHADOW, 0f, Align.left, false);
        quarantineTitleLayout.setText(font, HudConstants.RELIC_OFFER_TITLE_QUARANTINE_TEXT, COST_RED, 0f, Align.left, false);
        quarantineTitleShadow.setText(font, HudConstants.RELIC_OFFER_TITLE_QUARANTINE_TEXT, TEXT_SHADOW, 0f, Align.left, false);
        font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_LEAVE);
        leaveLayout.setText(font, HudConstants.RELIC_OFFER_LEAVE_TEXT, LEAVE_TEXT, 0f, Align.left, false);
        font.getData().setScale(1f);
    }

    private static GlyphLayout[] newLayouts(int count) {
        GlyphLayout[] layouts = new GlyphLayout[count];
        for (int index = 0; index < count; index++) layouts[index] = new GlyphLayout();
        return layouts;
    }

    // -------------------------------------------------------------------------
    // Touch hit-testing — same geometry as drawing.
    // -------------------------------------------------------------------------

    /** Index of the card under the point, or -1. {@code offer} is the open offer (may be null -> -1). */
    public int cardIndexAt(RelicOffer offer, float worldX, float worldY) {
        if (offer == null || offer.count <= 0) return -1;
        if (worldY < CARD_Y || worldY > CARD_TOP_Y) return -1;
        float leftX = rowLeftX(offer.count);
        for (int cardIndex = 0; cardIndex < offer.count; cardIndex++) {
            float cardX = leftX + cardIndex * (CARD_WIDTH + CARD_GAP);
            if (worldX >= cardX && worldX <= cardX + CARD_WIDTH) return cardIndex;
        }
        return -1;
    }

    /** True when the point is on the LEAVE IT plate. */
    public boolean isLeaveAt(float worldX, float worldY) {
        return worldX >= LEAVE_X && worldX <= LEAVE_X + HudConstants.RELIC_OFFER_LEAVE_WIDTH
            && worldY >= HudConstants.RELIC_OFFER_LEAVE_Y
            && worldY <= HudConstants.RELIC_OFFER_LEAVE_Y + HudConstants.RELIC_OFFER_LEAVE_HEIGHT;
    }

    private static float rowLeftX(int cardCount) {
        return GameMath.centeredRowLeftX(Constants.WORLD_WIDTH, CARD_WIDTH, CARD_GAP, cardCount);
    }

    // -------------------------------------------------------------------------
    // Layout cache
    // -------------------------------------------------------------------------

    private boolean isCached(RelicOffer offer) {
        if (offer.source != cachedSource || offer.count != cachedCount) return false;
        for (int cardIndex = 0; cardIndex < offer.count; cardIndex++) {
            if (offer.cards[cardIndex] != cachedCards[cardIndex]) return false;
        }
        return true;
    }

    /** Builds every per-card layout for a freshly opened offer. Runs once per offer, never per frame. */
    private void rebuild(RelicOffer offer) {
        cachedSource = offer.source;
        cachedCount  = offer.count;
        quarantineTitle = offer.source == RelicSource.QUARANTINE_LOCKER;
        float textWidth = CARD_WIDTH - HudConstants.RELIC_OFFER_TEXT_PAD * 2f;
        for (int cardIndex = 0; cardIndex < offer.count; cardIndex++) {
            RelicDefinition relic = offer.cards[cardIndex];
            cachedCards[cardIndex] = relic;
            RelicRarity rarity = relic.rarity();
            int ordinal = rarity.ordinal();
            rarityOrdinal[cardIndex] = ordinal;
            cardCursed[cardIndex]    = rarity.isCursed();
            cardPulses[cardIndex]    = rarity.glowPulses();

            font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_NAME);
            nameLayouts[cardIndex].setText(font, relic.name(), WHITE, 0f, Align.left, false);
            nameShadows[cardIndex].setText(font, relic.name(), TEXT_SHADOW, 0f, Align.left, false);

            font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_BADGE);
            rarityLayouts[cardIndex].setText(font, rarity.label(), rarityAccent[ordinal], 0f, Align.left, false);
            badgeLayouts[cardIndex].setText(font, relic.trigger().badgeLabel(), CHIP_TEXT, 0f, Align.left, false);

            font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_EFFECT);
            effectLayouts[cardIndex].setText(font, relic.effectText(), WHITE, textWidth, Align.left, true);
            effectShadows[cardIndex].setText(font, relic.effectText(), TEXT_SHADOW, textWidth, Align.left, true);
            String cost = relic.costText();
            hasCost[cardIndex] = cost != null && !cost.isEmpty();
            if (hasCost[cardIndex]) {
                costLayouts[cardIndex].setText(font, cost, COST_RED, textWidth, Align.left, true);
                costShadows[cardIndex].setText(font, cost, TEXT_SHADOW, textWidth, Align.left, true);
            }

            BuildTag affinity = relic.affinity();
            hasTag[cardIndex] = affinity != null;
            if (affinity != null) {
                font.getData().setScale(ProgressionConstants.BUILD_CHIP_FONT_SCALE);
                tagLayouts[cardIndex].setText(font, affinity.displayName(), CHIP_TEXT, 0f, Align.left, false);
                tagColors[cardIndex] = BuildTagPalette.colorOf(affinity);
            }
        }
        font.getData().setScale(1f);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * Draws the overlay for {@code offer}. {@code openSeconds} is World's overlay clock (it drives only the
     * LEGENDARY pulse). A null / empty offer draws nothing.
     */
    public void render(OrthographicCamera camera, RelicOffer offer, float openSeconds) {
        if (offer == null || offer.count <= 0) return;
        if (!isCached(offer)) rebuild(offer);

        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
        float leftX = rowLeftX(offer.count);
        float pulse = GameMath.pulseMultiplier(openSeconds, PULSE_HERTZ,
                HudConstants.RELIC_OFFER_GLOW_ALPHA - HudConstants.RELIC_OFFER_LEGENDARY_PULSE_SWING,
                HudConstants.RELIC_OFFER_GLOW_ALPHA + HudConstants.RELIC_OFFER_LEGENDARY_PULSE_SWING);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ---- Pass A: filled shapes ----
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(SCRIM);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        for (int cardIndex = 0; cardIndex < offer.count; cardIndex++) {
            int ordinal = rarityOrdinal[cardIndex];
            float cardX = leftX + cardIndex * (CARD_WIDTH + CARD_GAP);
            Color accent = rarityAccent[ordinal];
            float glowInset = HudConstants.RELIC_OFFER_GLOW_INSET;

            glowColor.set(accent.r, accent.g, accent.b, cardPulses[cardIndex] ? pulse : HudConstants.RELIC_OFFER_GLOW_ALPHA);
            shapes.setColor(glowColor);
            shapes.rect(cardX - glowInset, CARD_Y - glowInset, CARD_WIDTH + glowInset * 2f, CARD_HEIGHT + glowInset * 2f);

            shapes.setColor(CARD_SHADOW);
            shapes.rect(cardX + 7f, CARD_Y - 7f, CARD_WIDTH, CARD_HEIGHT);

            Color bodyTop = rarityBodyTop[ordinal];
            Color bodyBottom = rarityBodyBottom[ordinal];
            shapes.rect(cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT, bodyBottom, bodyBottom, bodyTop, bodyTop);

            float stripY = CARD_TOP_Y - HudConstants.RELIC_OFFER_HEADER_HEIGHT;
            Color stripTop = rarityStripTop[ordinal];
            Color stripBottom = rarityStripBottom[ordinal];
            shapes.rect(cardX, stripY, CARD_WIDTH, HudConstants.RELIC_OFFER_HEADER_HEIGHT,
                    stripBottom, stripBottom, stripTop, stripTop);

            // CURSED: dark-red vignette, a few concentric bands whose alpha fades toward the middle.
            if (cardCursed[cardIndex]) drawVignette(cardX, stripY);

            // Trigger badge chip, top-right of the badge row (rounded: rect + end caps).
            float badgeWidth = badgeLayouts[cardIndex].width + HudConstants.RELIC_OFFER_BADGE_PAD * 2f;
            float badgeLeft = cardX + CARD_WIDTH - HudConstants.RELIC_OFFER_TEXT_PAD - badgeWidth;
            float badgeBottom = CARD_TOP_Y - HudConstants.RELIC_OFFER_BADGE_ROW_DROP;
            drawChip(badgeLeft, badgeBottom, badgeWidth, HudConstants.RELIC_OFFER_BADGE_HEIGHT, accent);

            if (hasTag[cardIndex]) {
                float tagWidth = tagLayouts[cardIndex].width + ProgressionConstants.BUILD_CHIP_TEXT_PAD * 2f;
                drawChip(cardX + (CARD_WIDTH - tagWidth) / 2f, CARD_Y + HudConstants.RELIC_OFFER_TAG_CHIP_RISE,
                        tagWidth, HudConstants.RELIC_OFFER_TAG_CHIP_HEIGHT, tagColors[cardIndex]);
            }
        }
        // LEAVE IT plate.
        shapes.setColor(LEAVE_FILL);
        shapes.rect(LEAVE_X, HudConstants.RELIC_OFFER_LEAVE_Y,
                HudConstants.RELIC_OFFER_LEAVE_WIDTH, HudConstants.RELIC_OFFER_LEAVE_HEIGHT);
        shapes.end();

        // ---- Pass B: line work ----
        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(HudConstants.RELIC_OFFER_BORDER_THICKNESS);
        for (int cardIndex = 0; cardIndex < offer.count; cardIndex++) {
            float cardX = leftX + cardIndex * (CARD_WIDTH + CARD_GAP);
            shapes.setColor(INNER_EDGE);
            shapes.rect(cardX + 3f, CARD_Y + 3f, CARD_WIDTH - 6f, CARD_HEIGHT - 6f);
            shapes.setColor(rarityAccent[rarityOrdinal[cardIndex]]);
            shapes.rect(cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT);
            if (hasCost[cardIndex]) {
                shapes.setColor(DIVIDER);
                float dividerY = CARD_Y + HudConstants.RELIC_OFFER_DIVIDER_RISE;
                shapes.line(cardX + HudConstants.RELIC_OFFER_TEXT_PAD, dividerY,
                        cardX + CARD_WIDTH - HudConstants.RELIC_OFFER_TEXT_PAD, dividerY);
            }
        }
        shapes.setColor(DIVIDER);
        shapes.rect(LEAVE_X, HudConstants.RELIC_OFFER_LEAVE_Y,
                HudConstants.RELIC_OFFER_LEAVE_WIDTH, HudConstants.RELIC_OFFER_LEAVE_HEIGHT);
        Gdx.gl.glLineWidth(1f);
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Pass C: text (pre-built layouts only) ----
        // NOTE: BitmapFontCache sizes each glyph quad from the font's scale AT DRAW TIME while the layout's
        // positions were fixed when it was built, so every draw below re-applies the scale its layout used.
        batch.begin();
        font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_TITLE);
        GlyphLayout title = quarantineTitle ? quarantineTitleLayout : titleLayout;
        GlyphLayout titleBack = quarantineTitle ? quarantineTitleShadow : titleShadow;
        float titleX = (Constants.WORLD_WIDTH - title.width) / 2f;
        drawShadowed(titleBack, title, titleX, HudConstants.RELIC_OFFER_TITLE_Y);

        for (int cardIndex = 0; cardIndex < offer.count; cardIndex++) {
            float cardX = leftX + cardIndex * (CARD_WIDTH + CARD_GAP);
            float padX = cardX + HudConstants.RELIC_OFFER_TEXT_PAD;
            font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_NAME);
            // NAME centred in the header strip (a glyph box of height h centred in [Y, Y+H] has its top at Y + (H + h) / 2).
            GlyphLayout name = nameLayouts[cardIndex];
            float stripY = CARD_TOP_Y - HudConstants.RELIC_OFFER_HEADER_HEIGHT;
            drawShadowed(nameShadows[cardIndex], name, cardX + (CARD_WIDTH - name.width) / 2f,
                    stripY + (HudConstants.RELIC_OFFER_HEADER_HEIGHT + name.height) / 2f);

            // Badge row: rarity label left, trigger badge right.
            font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_BADGE);
            float badgeBottom = CARD_TOP_Y - HudConstants.RELIC_OFFER_BADGE_ROW_DROP;
            GlyphLayout rarity = rarityLayouts[cardIndex];
            font.draw(batch, rarity, padX, badgeBottom + (HudConstants.RELIC_OFFER_BADGE_HEIGHT + rarity.height) / 2f);
            GlyphLayout badge = badgeLayouts[cardIndex];
            float badgeWidth = badge.width + HudConstants.RELIC_OFFER_BADGE_PAD * 2f;
            font.draw(batch, badge, cardX + CARD_WIDTH - HudConstants.RELIC_OFFER_TEXT_PAD - badgeWidth
                    + HudConstants.RELIC_OFFER_BADGE_PAD, badgeBottom + (HudConstants.RELIC_OFFER_BADGE_HEIGHT + badge.height) / 2f);

            font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_EFFECT);
            drawShadowed(effectShadows[cardIndex], effectLayouts[cardIndex], padX,
                    CARD_TOP_Y - HudConstants.RELIC_OFFER_EFFECT_DROP);
            if (hasCost[cardIndex]) {
                drawShadowed(costShadows[cardIndex], costLayouts[cardIndex], padX,
                        CARD_Y + HudConstants.RELIC_OFFER_COST_RISE);
            }
            if (hasTag[cardIndex]) {
                font.getData().setScale(ProgressionConstants.BUILD_CHIP_FONT_SCALE);
                GlyphLayout tag = tagLayouts[cardIndex];
                float tagWidth = tag.width + ProgressionConstants.BUILD_CHIP_TEXT_PAD * 2f;
                font.draw(batch, tag, cardX + (CARD_WIDTH - tagWidth) / 2f + ProgressionConstants.BUILD_CHIP_TEXT_PAD,
                        CARD_Y + HudConstants.RELIC_OFFER_TAG_CHIP_RISE
                                + (HudConstants.RELIC_OFFER_TAG_CHIP_HEIGHT + tag.height) / 2f);
            }
        }
        font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_LEAVE);
        font.draw(batch, leaveLayout, LEAVE_X + (HudConstants.RELIC_OFFER_LEAVE_WIDTH - leaveLayout.width) / 2f,
                HudConstants.RELIC_OFFER_LEAVE_Y + (HudConstants.RELIC_OFFER_LEAVE_HEIGHT + leaveLayout.height) / 2f);
        font.getData().setScale(1f);
        batch.end();
    }

    /** Dark-red inner vignette: concentric inset bands, strongest at the border and fading inward. */
    private void drawVignette(float cardX, float stripY) {
        float stepSize = HudConstants.RELIC_OFFER_VIGNETTE_STEP_SIZE;
        float bodyTop = stripY;
        for (int step = 0; step < HudConstants.RELIC_OFFER_VIGNETTE_STEPS; step++) {
            float fraction = 1f - (float) step / HudConstants.RELIC_OFFER_VIGNETTE_STEPS;
            vignetteColor.set(COST_RED.r * 0.6f, 0f, 0f, HudConstants.RELIC_OFFER_VIGNETTE_ALPHA * fraction);
            shapes.setColor(vignetteColor);
            float inset = step * stepSize;
            float bandLeft = cardX + inset;
            float bandBottom = CARD_Y + inset;
            float bandWidth = CARD_WIDTH - inset * 2f;
            float bandHeight = bodyTop - CARD_Y - inset;
            // four strips of thickness stepSize around the (inset) body rectangle
            shapes.rect(bandLeft, bandBottom, bandWidth, stepSize);                              // bottom
            shapes.rect(bandLeft, bandBottom + stepSize, stepSize, bandHeight - stepSize);       // left
            shapes.rect(bandLeft + bandWidth - stepSize, bandBottom + stepSize, stepSize, bandHeight - stepSize); // right
        }
    }

    private void drawChip(float chipLeft, float chipBottom, float chipWidth, float chipHeight, Color color) {
        float capRadius = chipHeight / 2f;
        shapes.setColor(color);
        shapes.rect(chipLeft, chipBottom, chipWidth, chipHeight);
        shapes.circle(chipLeft, chipBottom + capRadius, capRadius);
        shapes.circle(chipLeft + chipWidth, chipBottom + capRadius, capRadius);
    }

    private void drawShadowed(GlyphLayout shadow, GlyphLayout main, float textX, float textTopY) {
        font.draw(batch, shadow, textX + HudConstants.RELIC_OFFER_SHADOW_OFFSET, textTopY - HudConstants.RELIC_OFFER_SHADOW_OFFSET);
        font.draw(batch, main, textX, textTopY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}

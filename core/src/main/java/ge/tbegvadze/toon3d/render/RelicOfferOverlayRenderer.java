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
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.util.ProgressionConstants;

/**
 * The RELIC_OFFER card screen (relics-run-changing-artifacts R14/R15). Visual identity order 5: the same suit
 * screen as the level-up offer - UI_INK_BLACK scrim, the R1 screen header (HEAVY title + amber-ticked rule), and
 * one UiKit CARD per relic whose frame takes the RARITY colour (R2: the only place it appears in chrome) over a
 * soft rarity glow (R8). Inside: the NAME (HEAVY) over a plain rule, the rarity label (top-left) and an outline
 * trigger badge chip (top-right), the word-wrapped effect line, for a cost a rule + the DAMAGE-red cost line, and
 * for an affine relic its {@link BuildTag} chip in {@link BuildTagPalette}'s colour. LEGENDARY's glow pulses off
 * the {@code openSeconds} World passes in (cosmetic only). LEAVE IT is a quiet IDLE plate.
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

    private static final Color PRIMARY      = new Color(ArtDirection.UI_TEXT_PRIMARY_RED,
            ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
    private static final Color SECONDARY    = new Color(ArtDirection.UI_TEXT_SECONDARY_RED,
            ArtDirection.UI_TEXT_SECONDARY_GREEN, ArtDirection.UI_TEXT_SECONDARY_BLUE, 1f);
    private static final Color COST_RED     = new Color(HudConstants.RELIC_COST_TEXT_COLOR_R,
            HudConstants.RELIC_COST_TEXT_COLOR_G, HudConstants.RELIC_COST_TEXT_COLOR_B, 1f);
    private static final Color CHIP_TEXT    = new Color(ProgressionConstants.BUILD_CHIP_TEXT_RED,
            ProgressionConstants.BUILD_CHIP_TEXT_GREEN, ProgressionConstants.BUILD_CHIP_TEXT_BLUE, 1f);
    private static final Color LEAVE_TEXT   = new Color(HudConstants.RELIC_OFFER_LEAVE_TEXT_R,
            HudConstants.RELIC_OFFER_LEAVE_TEXT_G, HudConstants.RELIC_OFFER_LEAVE_TEXT_B, 1f);

    // Per-rarity frame / glow ink, indexed by RelicRarity ordinal (HudConstants arrays; never a switch).
    private final Color[] rarityAccent      = new Color[RARITY_COUNT];
    private final Color   glowColor         = new Color();

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    /** The HEAVY face: the screen title (R1) and the relic names. Name layouts are built AND drawn with it. */
    private final BitmapFont    headingFont;

    // ---- Cache of the offer these layouts were built for (identity compare, allocation-free) ----
    private RelicSource cachedSource = null;
    private int         cachedCount  = 0;
    private final RelicDefinition[] cachedCards = new RelicDefinition[MAX_CARDS];

    // ---- Pre-built text, one slot per card ----
    private final GlyphLayout[] nameLayouts    = newLayouts(MAX_CARDS);
    private final GlyphLayout[] rarityLayouts  = newLayouts(MAX_CARDS);
    private final GlyphLayout[] badgeLayouts   = newLayouts(MAX_CARDS);
    private final GlyphLayout[] effectLayouts  = newLayouts(MAX_CARDS);
    private final GlyphLayout[] costLayouts    = newLayouts(MAX_CARDS);
    private final GlyphLayout[] tagLayouts     = newLayouts(MAX_CARDS);
    private final boolean[]     hasCost        = new boolean[MAX_CARDS];
    private final boolean[]     hasTag         = new boolean[MAX_CARDS];
    private final int[]         rarityOrdinal  = new int[MAX_CARDS];
    private final boolean[]     cardPulses     = new boolean[MAX_CARDS];
    private final Color[]       tagColors      = new Color[MAX_CARDS];

    private final GlyphLayout leaveLayout            = new GlyphLayout();
    private boolean quarantineTitle = false;

    public RelicOfferOverlayRenderer() {
        shapes = new ShapeRenderer();
        batch  = new SpriteBatch();
        font   = GameFonts.newFont(FontFace.REGULAR);
        font.setUseIntegerPositions(false);
        headingFont = GameFonts.newFont(FontFace.HEAVY);
        headingFont.setUseIntegerPositions(false);

        for (int rarityIndex = 0; rarityIndex < RARITY_COUNT; rarityIndex++) {
            rarityAccent[rarityIndex] = new Color(HudConstants.RELIC_RARITY_COLOR_R[rarityIndex],
                    HudConstants.RELIC_RARITY_COLOR_G[rarityIndex], HudConstants.RELIC_RARITY_COLOR_B[rarityIndex], 1f);
        }

        // Fixed text: built once, here.
        font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_LEAVE);
        leaveLayout.setText(font, HudConstants.RELIC_OFFER_LEAVE_TEXT, LEAVE_TEXT, 0f, Align.left, false);
        font.getData().setScale(ArtDirection.TYPE_CAPTION);
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
            rarityOrdinal[cardIndex] = rarity.ordinal();
            cardPulses[cardIndex]    = rarity.glowPulses();

            headingFont.getData().setScale(HudConstants.RELIC_OFFER_SCALE_NAME);
            nameLayouts[cardIndex].setText(headingFont, relic.name(), PRIMARY, 0f, Align.left, false);

            // The rarity reads as a WORD here; its colour lives in the card's frame only (R2 / D2).
            font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_BADGE);
            rarityLayouts[cardIndex].setText(font, rarity.label(), SECONDARY, 0f, Align.left, false);
            badgeLayouts[cardIndex].setText(font, relic.trigger().badgeLabel(), PRIMARY, 0f, Align.left, false);

            font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_EFFECT);
            effectLayouts[cardIndex].setText(font, relic.effectText(), PRIMARY, textWidth, Align.left, true);
            String cost = relic.costText();
            hasCost[cardIndex] = cost != null && !cost.isEmpty();
            if (hasCost[cardIndex]) {
                costLayouts[cardIndex].setText(font, cost, COST_RED, textWidth, Align.left, true);
            }

            BuildTag affinity = relic.affinity();
            hasTag[cardIndex] = affinity != null;
            if (affinity != null) {
                font.getData().setScale(ProgressionConstants.BUILD_CHIP_FONT_SCALE);
                tagLayouts[cardIndex].setText(font, affinity.displayName(), CHIP_TEXT, 0f, Align.left, false);
                tagColors[cardIndex] = BuildTagPalette.colorOf(affinity);
            }
        }
        headingFont.getData().setScale(ArtDirection.TYPE_CAPTION);
        font.getData().setScale(ArtDirection.TYPE_CAPTION);
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
        float rowWidth = offer.count * CARD_WIDTH + (offer.count - 1) * CARD_GAP;
        float pulse = GameMath.pulseMultiplier(openSeconds, PULSE_HERTZ,
                HudConstants.RELIC_OFFER_GLOW_ALPHA - HudConstants.RELIC_OFFER_LEGENDARY_PULSE_SWING,
                HudConstants.RELIC_OFFER_GLOW_ALPHA + HudConstants.RELIC_OFFER_LEGENDARY_PULSE_SWING);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ---- one Filled pass ----
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN,
                ArtDirection.UI_INK_BLACK_BLUE, HudConstants.RELIC_OFFER_SCRIM_ALPHA);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        UiKit.screenHeaderRule(shapes, leftX, UiKit.screenHeaderRuleY(HudConstants.RELIC_OFFER_TITLE_Y), rowWidth, 1f);

        float stripY = CARD_TOP_Y - HudConstants.RELIC_OFFER_HEADER_HEIGHT;
        for (int cardIndex = 0; cardIndex < offer.count; cardIndex++) {
            Color accent = rarityAccent[rarityOrdinal[cardIndex]];
            float cardX = leftX + cardIndex * (CARD_WIDTH + CARD_GAP);
            float glowInset = HudConstants.RELIC_OFFER_GLOW_INSET;

            // R8: the rarity glow keeps its colour and its LEGENDARY pulse; it is chamfered like the card.
            glowColor.set(accent.r, accent.g, accent.b, cardPulses[cardIndex] ? pulse : HudConstants.RELIC_OFFER_GLOW_ALPHA);
            shapes.setColor(glowColor);
            UiKit.fillOctagon(shapes, cardX - glowInset, CARD_Y - glowInset, CARD_WIDTH + glowInset * 2f,
                    CARD_HEIGHT + glowInset * 2f, ArtDirection.UI_PLATE_CHAMFER + glowInset);

            // R2: the card's frame is the rarity colour (the only place it appears in chrome).
            UiKit.card(shapes, cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT, accent.r, accent.g, accent.b, PlateState.IDLE);
            drawPlainRule(cardX + HudConstants.RELIC_OFFER_TEXT_PAD, stripY,
                    CARD_WIDTH - HudConstants.RELIC_OFFER_TEXT_PAD * 2f);

            // Trigger badge: an outline chip, top-right of the badge row.
            float badgeWidth = badgeLayouts[cardIndex].width + HudConstants.RELIC_OFFER_BADGE_PAD * 2f;
            float badgeLeft = cardX + CARD_WIDTH - HudConstants.RELIC_OFFER_TEXT_PAD - badgeWidth;
            float badgeBottom = CARD_TOP_Y - HudConstants.RELIC_OFFER_BADGE_ROW_DROP;
            drawChip(badgeLeft, badgeBottom, badgeWidth, HudConstants.RELIC_OFFER_BADGE_HEIGHT,
                    ArtDirection.UI_PANEL_RAISED_RED, ArtDirection.UI_PANEL_RAISED_GREEN, ArtDirection.UI_PANEL_RAISED_BLUE);

            if (hasCost[cardIndex]) {
                drawPlainRule(cardX + HudConstants.RELIC_OFFER_TEXT_PAD, CARD_Y + HudConstants.RELIC_OFFER_DIVIDER_RISE,
                        CARD_WIDTH - HudConstants.RELIC_OFFER_TEXT_PAD * 2f);
            }
            if (hasTag[cardIndex]) {
                // Affinity chip: the tag's fill inside a 1-unit UI_LINE border (R10), over the old pill footprint.
                float tagWidth = tagLayouts[cardIndex].width + ProgressionConstants.BUILD_CHIP_TEXT_PAD * 2f;
                float capRadius = HudConstants.RELIC_OFFER_TAG_CHIP_HEIGHT / 2f;
                Color tagColor = tagColors[cardIndex];
                drawChip(cardX + (CARD_WIDTH - tagWidth) / 2f - capRadius, CARD_Y + HudConstants.RELIC_OFFER_TAG_CHIP_RISE,
                        tagWidth + capRadius * 2f, HudConstants.RELIC_OFFER_TAG_CHIP_HEIGHT,
                        tagColor.r, tagColor.g, tagColor.b);
            }
        }
        // R8: LEAVE IT is a quiet IDLE plate (UI_PANEL + UI_LINE), so it never competes with the cards.
        UiKit.plate(shapes, LEAVE_X, HudConstants.RELIC_OFFER_LEAVE_Y,
                HudConstants.RELIC_OFFER_LEAVE_WIDTH, HudConstants.RELIC_OFFER_LEAVE_HEIGHT, PlateState.IDLE,
                ArtDirection.UI_PANEL_ALPHA_SCREEN,
                ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE);
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- text (pre-built layouts only; screen text is unshadowed) ----
        // NOTE: BitmapFontCache sizes each glyph quad from the font's scale AT DRAW TIME while the layout's
        // positions were fixed when it was built, so every draw below re-applies the scale its layout used.
        batch.begin();
        UiKit.screenHeaderTitle(batch, headingFont,
                quarantineTitle ? HudConstants.RELIC_OFFER_TITLE_QUARANTINE_TEXT : HudConstants.RELIC_OFFER_TITLE_TEXT,
                leftX, HudConstants.RELIC_OFFER_TITLE_Y);

        for (int cardIndex = 0; cardIndex < offer.count; cardIndex++) {
            float cardX = leftX + cardIndex * (CARD_WIDTH + CARD_GAP);
            float padX = cardX + HudConstants.RELIC_OFFER_TEXT_PAD;
            headingFont.getData().setScale(HudConstants.RELIC_OFFER_SCALE_NAME);
            // NAME centred in the header band (a glyph box of height h centred in [Y, Y+H] has its top at Y + (H + h) / 2).
            GlyphLayout name = nameLayouts[cardIndex];
            headingFont.draw(batch, name, cardX + (CARD_WIDTH - name.width) / 2f,
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
            font.draw(batch, effectLayouts[cardIndex], padX, CARD_TOP_Y - HudConstants.RELIC_OFFER_EFFECT_DROP);
            if (hasCost[cardIndex]) {
                font.draw(batch, costLayouts[cardIndex], padX, CARD_Y + HudConstants.RELIC_OFFER_COST_RISE);
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
        headingFont.getData().setScale(ArtDirection.TYPE_CAPTION);
        font.getData().setScale(HudConstants.RELIC_OFFER_SCALE_LEAVE);
        font.draw(batch, leaveLayout, LEAVE_X + (HudConstants.RELIC_OFFER_LEAVE_WIDTH - leaveLayout.width) / 2f,
                HudConstants.RELIC_OFFER_LEAVE_Y + (HudConstants.RELIC_OFFER_LEAVE_HEIGHT + leaveLayout.height) / 2f);
        font.getData().setScale(ArtDirection.TYPE_CAPTION);
        batch.end();
    }

    private void drawPlainRule(float x, float y, float width) {
        UiKit.divider(shapes, x, y, width, ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN,
                ArtDirection.UI_LINE_BLUE, 1f);
    }

    /** A chip: the given fill inside a 1-unit UI_LINE border, chamfered (Filled pass). */
    private void drawChip(float chipLeft, float chipBottom, float chipWidth, float chipHeight,
                          float fillRed, float fillGreen, float fillBlue) {
        UiKit.panel(shapes, chipLeft, chipBottom, chipWidth, chipHeight, ArtDirection.UI_CHIP_CHAMFER,
                fillRed, fillGreen, fillBlue, 1f,
                ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f,
                ArtDirection.UI_CHIP_BORDER_THICKNESS);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        headingFont.dispose();
    }
}

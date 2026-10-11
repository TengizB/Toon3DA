package ge.tbegvadze.toon3d.progression;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * Renders the full-screen level-up PERK overlay for mobile (balance-overhaul order 5, B3/B8).
 *
 * <p>The overlay shows the {@link BalanceConfig#PERKS_OFFERED} perks {@link PerkDeck} drew for this
 * level-up. World sets the offer via {@link #setOfferedPerks(Perk[], int, PlayerBuild)} before this
 * renders; each plate carries the perk's name, the rank it would reach, its {@link BuildTag} and its
 * one-line effect. (CP5 of order 5 replaces the plain tag label with a tag chip, the SYNERGY ribbon and
 * rank pips.) Tap detection is via {@link #getTappedCardIndex(float, float)} which tests world-space
 * (viewport-unprojected) coords.</p>
 *
 * <h2>Visual pipeline</h2>
 * <p>Each card is layered back-to-front for a high-resolution, readable look:</p>
 * <ol>
 *   <li>Outer accent glow (soft halo behind the card)</li>
 *   <li>Drop shadow</li>
 *   <li>Gradient body (lighter top → darker bottom)</li>
 *   <li>Gradient accent header strip carrying the card name</li>
 *   <li>Thin inner highlight + thick accent outer border</li>
 *   <li>Drop-shadowed, Linear-filtered text (crisp when scaled up)</li>
 * </ol>
 */
public final class LevelUpOverlayRenderer implements Disposable {

    // ---- Card geometry (world units) ----------------------------------------
    private static final float CARD_WIDTH   = 360f;
    private static final float CARD_HEIGHT  = 300f;
    private static final float CARD_GAP     = 20f;
    private static final float CARD_Y       = (Constants.WORLD_HEIGHT - CARD_HEIGHT) / 2f - 30f;

    private static final int   MAX_CARDS    = BalanceConfig.PERKS_OFFERED;
    private static final float TOTAL_CARDS_WIDTH = MAX_CARDS * CARD_WIDTH + (MAX_CARDS - 1) * CARD_GAP;
    private static final float CARDS_LEFT_X      = (Constants.WORLD_WIDTH - TOTAL_CARDS_WIDTH) / 2f;

    // ---- Internal card layout proportions -----------------------------------
    private static final float CARD_BORDER_THICK   = 3f;
    private static final float HEADER_STRIP_HEIGHT = 62f;  // colored top accent bar (holds card name)
    private static final float GLOW_INSET          = 10f;  // how far the glow halo extends past the card
    private static final float TEXT_HORIZONTAL_PAD = 26f;  // side padding for wrapped text

    // Text Y offsets measured DOWN from the top of the card body.
    private static final float HEADER_TEXT_Y   = 40f;   // card name, centered in header strip
    private static final float VALUE_Y         = 118f;  // big headline value
    private static final float LABEL_Y         = 158f;  // effect label
    private static final float DIVIDER_Y       = 178f;  // hairline separator under the label
    private static final float DESC_Y          = 206f;  // wrapped description
    private static final float TAP_Y           = CARD_HEIGHT - 34f; // "TAP TO CHOOSE" hint
    /** Baseline of a keystone's cost line, measured UP from the plate bottom (balance-overhaul order 5). */
    private static final float COST_LINE_BOTTOM_OFFSET = 52f;
    /** The value line of a keystone plate. */
    private static final String KEYSTONE_VALUE_TEXT = "KEYSTONE";

    // Title / subtitle above the cards
    private static final float TITLE_Y    = CARD_Y + CARD_HEIGHT + 78f;
    private static final float SUBTITLE_Y = CARD_Y + CARD_HEIGHT + 36f;

    // ---- Font scales --------------------------------------------------------
    private static final float SCALE_TITLE    = 2.4f;
    private static final float SCALE_SUBTITLE = 1.35f;
    private static final float SCALE_HEADER   = 1.55f;
    private static final float SCALE_VALUE    = 3.1f;
    /** The rank line ("RANK 2 / 3") is longer than the old card value, so it draws at this share of SCALE_VALUE. */
    private static final float RANK_TEXT_SCALE_FRACTION = 0.6f;
    private static final float SCALE_LABEL    = 1.35f;
    private static final float SCALE_DESC     = 1.02f;
    private static final float SCALE_TAP      = 0.95f;

    private static final float SHADOW_OFFSET = 2.5f;

    // ---- Palette -----------------------------------------------------------
    private static final Color DIM_OVERLAY = new Color(0.02f, 0.02f, 0.04f, 0.84f);
    private static final Color WHITE       = new Color(0.97f, 0.97f, 0.98f, 1.00f);
    private static final Color GOLD        = new Color(1.00f, 0.88f, 0.20f, 1.00f);
    /** A keystone's cost line (the order-5 spec's cost colour). */
    private static final Color COST_RED    = new Color(0.95f, 0.30f, 0.25f, 1.00f);
    private static final Color DIM_HINT    = new Color(0.62f, 0.62f, 0.66f, 1.00f);
    private static final Color CARD_SHADOW = new Color(0.00f, 0.00f, 0.00f, 0.50f);
    private static final Color TEXT_SHADOW = new Color(0.00f, 0.00f, 0.00f, 0.72f);
    private static final Color HEADER_TEXT = new Color(1.00f, 1.00f, 1.00f, 1.00f);
    private static final Color INNER_EDGE  = new Color(1.00f, 1.00f, 1.00f, 0.16f);
    private static final Color DIVIDER     = new Color(1.00f, 1.00f, 1.00f, 0.20f);

    // ---- Plate palette. The four historic accents are kept as a small cycle over the BuildTag ordinal so
    // neighbouring tags read apart; CP5 of balance-overhaul order 5 replaces this with the tag chip colours.
    private static final Color[] POOL_ACCENT = {
        new Color(1.00f, 0.52f, 0.22f, 1.00f), // OFFENSE — red-orange
        new Color(0.20f, 0.86f, 0.98f, 1.00f), // DEFENSE — cyan
        new Color(0.28f, 1.00f, 0.52f, 1.00f), // SUSTAIN — green
        new Color(1.00f, 0.83f, 0.28f, 1.00f), // UTILITY — gold
    };
    // Card body gradient: lighter at the top of the body, deepening toward the bottom.
    private static final Color[] POOL_BODY_TOP = {
        new Color(0.22f, 0.09f, 0.05f, 0.98f), // OFFENSE
        new Color(0.06f, 0.16f, 0.21f, 0.98f), // DEFENSE
        new Color(0.06f, 0.18f, 0.09f, 0.98f), // SUSTAIN
        new Color(0.20f, 0.16f, 0.05f, 0.98f), // UTILITY
    };
    private static final Color[] POOL_BODY_BOTTOM = new Color[POOL_ACCENT.length];
    // Header strip gradient runs from the bright accent (top) to a deeper accent (bottom).
    private static final Color[] POOL_STRIP_TOP = {
        new Color(0.95f, 0.46f, 0.16f, 1.00f), // OFFENSE
        new Color(0.14f, 0.78f, 0.92f, 1.00f), // DEFENSE
        new Color(0.20f, 0.86f, 0.42f, 1.00f), // SUSTAIN
        new Color(0.92f, 0.74f, 0.16f, 1.00f), // UTILITY
    };
    private static final Color[] POOL_STRIP_BOTTOM = new Color[POOL_ACCENT.length];
    // Soft outer halo — the accent color at low alpha.
    private static final Color[] POOL_GLOW = new Color[POOL_ACCENT.length];

    static {
        for (int poolIndex = 0; poolIndex < POOL_ACCENT.length; poolIndex++) {
            POOL_BODY_BOTTOM[poolIndex] = scaleRgb(POOL_BODY_TOP[poolIndex], 0.35f);
            POOL_STRIP_BOTTOM[poolIndex] = scaleRgb(POOL_STRIP_TOP[poolIndex], 0.62f);
            Color glow = new Color(POOL_ACCENT[poolIndex]);
            glow.a = 0.22f;
            POOL_GLOW[poolIndex] = glow;
        }
    }

    /** Returns a copy of {@code source} with its RGB multiplied by {@code factor} (alpha preserved). */
    private static Color scaleRgb(Color source, float factor) {
        return new Color(source.r * factor, source.g * factor, source.b * factor, source.a);
    }

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;
    private final PlayerProgress progress;

    private final StringBuilder stringBuilder = new StringBuilder(64);

    // The current offer (set by World before each render). Never held across level-ups. In KEYSTONE mode
    // (balance-overhaul order 5, B4) the plates carry keystones instead of perks; the per-plate text arrays are
    // filled once per offer so render() reads them as-is.
    private final Perk[]     offeredPerks     = new Perk[MAX_CARDS];
    private final Keystone[] offeredKeystones = new Keystone[MAX_CARDS];
    private final BuildTag[] offeredTags      = new BuildTag[MAX_CARDS];
    private final String[]   plateNames       = new String[MAX_CARDS];
    /** "RANK n / max" for a perk, "KEYSTONE" for a keystone. */
    private final String[]   plateValueTexts  = new String[MAX_CARDS];
    /** The perk's effect line, or the keystone's GAIN line. */
    private final String[]   plateDescriptions = new String[MAX_CARDS];
    /** The keystone's COST line (drawn in the cost colour); null for a perk. */
    private final String[]   plateCostLines   = new String[MAX_CARDS];
    private boolean keystoneMode = false;
    private int offeredCount = 0;

    public LevelUpOverlayRenderer(PlayerProgress progress) {
        this.progress    = progress;
        this.shapes      = new ShapeRenderer();
        this.batch       = new SpriteBatch();
        this.font        = new BitmapFont();
        this.glyphLayout = new GlyphLayout();
        this.font.getData().markupEnabled = false;
        // Crisp text when scaled up: Linear filtering + sub-pixel positioning.
        // (Same technique used by FadeOverlayRenderer / ImpactEffectRenderer.)
        this.font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        this.font.setUseIntegerPositions(false);
    }

    // -------------------------------------------------------------------------
    // Offer wiring — World calls this once when a level-up begins.
    // -------------------------------------------------------------------------

    /**
     * Sets the perks to display this level-up. Copies references into the renderer's fixed array and builds
     * each plate's rank text once (the rank the pick would reach); {@code count} is clamped to the plate
     * slots available. Call before {@link #render}.
     */
    public void setOfferedPerks(Perk[] perks, int count, PlayerBuild build) {
        keystoneMode = false;
        offeredCount = Math.min(count, MAX_CARDS);
        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            Perk perk = perks[cardIndex];
            offeredPerks[cardIndex]      = perk;
            offeredKeystones[cardIndex]  = null;
            offeredTags[cardIndex]       = perk.tag;
            plateNames[cardIndex]        = perk.displayName;
            plateValueTexts[cardIndex]   = "RANK " + (build.rankOf(perk) + 1) + " / " + perk.maxRank;
            plateDescriptions[cardIndex] = perk.effectLine;
            plateCostLines[cardIndex]    = null;
        }
    }

    /**
     * KEYSTONE mode (balance-overhaul order 5, B4): the plates show this keystone level's offer instead of
     * perks — name, tag, the GAIN line and the COST line (in the cost colour). CP5 adds the gold frame.
     */
    public void setOfferedKeystones(Keystone[] keystones, int count) {
        keystoneMode = true;
        offeredCount = Math.min(count, MAX_CARDS);
        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            Keystone keystone = keystones[cardIndex];
            offeredPerks[cardIndex]      = null;
            offeredKeystones[cardIndex]  = keystone;
            offeredTags[cardIndex]       = keystone.tag;
            plateNames[cardIndex]        = keystone.displayName;
            plateValueTexts[cardIndex]   = KEYSTONE_VALUE_TEXT;
            plateDescriptions[cardIndex] = keystone.gainLine;
            plateCostLines[cardIndex]    = keystone.costLine;
        }
    }

    /** True while the overlay offers keystones rather than perks. */
    public boolean isKeystoneMode() {
        return keystoneMode;
    }

    // -------------------------------------------------------------------------
    // Touch detection — called from World.update()
    // -------------------------------------------------------------------------

    /**
     * Returns the index of the tapped card in the current offer, or {@code -1} if no card was hit.
     * Coordinates must be in world space (viewport-unprojected).
     */
    public int getTappedCardIndex(float worldX, float worldY) {
        if (worldY < CARD_Y || worldY > CARD_Y + CARD_HEIGHT) return -1;
        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            float cardX = cardLeftX(cardIndex);
            if (worldX >= cardX && worldX < cardX + CARD_WIDTH) return cardIndex;
        }
        return -1;
    }

    /** Returns the offered perk at the given slot, or {@code null} if out of range or in keystone mode. */
    public Perk getOfferedPerk(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= offeredCount) return null;
        return offeredPerks[cardIndex];
    }

    /** Returns the offered keystone at the given slot, or {@code null} if out of range or in perk mode. */
    public Keystone getOfferedKeystone(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= offeredCount) return null;
        return offeredKeystones[cardIndex];
    }

    /** The plate palette index for a build tag (a cycle over its ordinal). */
    private static int paletteIndexOf(BuildTag tag) {
        return tag.ordinal() % POOL_ACCENT.length;
    }

    private float cardLeftX(int cardIndex) {
        return CARDS_LEFT_X + cardIndex * (CARD_WIDTH + CARD_GAP);
    }

    /** Y of the top of the card body (below which everything is measured downward). */
    private static float cardTopY() {
        return CARD_Y + CARD_HEIGHT;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    public void render(OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ---- Pass A: filled shapes (dim, glow, shadow, gradient body + header) ----
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Full-screen dim
        shapes.setColor(DIM_OVERLAY);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            int pool = paletteIndexOf(offeredTags[cardIndex]);
            float cardX = cardLeftX(cardIndex);

            // Soft outer glow halo
            shapes.setColor(POOL_GLOW[pool]);
            shapes.rect(cardX - GLOW_INSET, CARD_Y - GLOW_INSET,
                    CARD_WIDTH + GLOW_INSET * 2f, CARD_HEIGHT + GLOW_INSET * 2f);

            // Drop shadow (offset down-right)
            shapes.setColor(CARD_SHADOW);
            shapes.rect(cardX + 7f, CARD_Y - 7f, CARD_WIDTH, CARD_HEIGHT);

            // Card body — vertical gradient (top lighter, bottom darker).
            // rect(x, y, w, h, bottomLeft, bottomRight, topRight, topLeft)
            Color bodyTop = POOL_BODY_TOP[pool];
            Color bodyBottom = POOL_BODY_BOTTOM[pool];
            shapes.rect(cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT,
                    bodyBottom, bodyBottom, bodyTop, bodyTop);

            // Header accent strip — gradient bright (top) to deep (bottom).
            Color stripTop = POOL_STRIP_TOP[pool];
            Color stripBottom = POOL_STRIP_BOTTOM[pool];
            float stripY = cardTopY() - HEADER_STRIP_HEIGHT;
            shapes.rect(cardX, stripY, CARD_WIDTH, HEADER_STRIP_HEIGHT,
                    stripBottom, stripBottom, stripTop, stripTop);
        }

        shapes.end();

        // ---- Pass B: line work (inner highlight + accent border + divider) ----
        shapes.begin(ShapeRenderer.ShapeType.Line);
        com.badlogic.gdx.Gdx.gl.glLineWidth(CARD_BORDER_THICK);
        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            float cardX = cardLeftX(cardIndex);

            // Thin inner highlight just inside the body edge
            shapes.setColor(INNER_EDGE);
            shapes.rect(cardX + 3f, CARD_Y + 3f, CARD_WIDTH - 6f, CARD_HEIGHT - 6f);

            // Thick accent outer border
            shapes.setColor(POOL_ACCENT[paletteIndexOf(offeredTags[cardIndex])]);
            shapes.rect(cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT);

            // Hairline divider under the effect label
            shapes.setColor(DIVIDER);
            float dividerY = cardTopY() - DIVIDER_Y;
            shapes.line(cardX + TEXT_HORIZONTAL_PAD, dividerY,
                    cardX + CARD_WIDTH - TEXT_HORIZONTAL_PAD, dividerY);
        }
        com.badlogic.gdx.Gdx.gl.glLineWidth(1f);
        shapes.end();

        com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Pass C: text ----
        batch.begin();

        // Title
        font.getData().setScale(SCALE_TITLE);
        drawCenteredTextWithShadow(keystoneMode ? "*** KEYSTONE ***" : "*** LEVEL UP! ***",
                Constants.WORLD_WIDTH / 2f, TITLE_Y, GOLD);

        // Subtitle
        font.getData().setScale(SCALE_SUBTITLE);
        stringBuilder.setLength(0);
        stringBuilder.append("Reached Level ").append(progress.getPlayerLevel() + 1);
        drawCenteredTextWithShadow(stringBuilder.toString(), Constants.WORLD_WIDTH / 2f, SUBTITLE_Y, WHITE);

        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            drawCardText(cardIndex);
        }

        batch.end();
    }

    private void drawCardText(int cardIndex) {
        float cardX = cardLeftX(cardIndex);
        float cardCenterX = cardX + CARD_WIDTH / 2f;
        float top = cardTopY();
        Color accent = POOL_ACCENT[paletteIndexOf(offeredTags[cardIndex])];

        // Perk / keystone name — inside the header strip (white, bold-looking with shadow)
        font.getData().setScale(SCALE_HEADER);
        drawCenteredTextWithShadow(plateNames[cardIndex], cardCenterX, top - HEADER_TEXT_Y, HEADER_TEXT);

        // The rank this pick reaches ("RANK 2 / 3"), or "KEYSTONE", in accent color
        font.getData().setScale(SCALE_VALUE * RANK_TEXT_SCALE_FRACTION);
        drawCenteredTextWithShadow(plateValueTexts[cardIndex], cardCenterX, top - VALUE_Y, accent);

        // The build tag below (e.g. "CLOSE QUARTERS") — white
        font.getData().setScale(SCALE_LABEL);
        drawCenteredTextWithShadow(offeredTags[cardIndex].displayName(), cardCenterX, top - LABEL_Y, WHITE);

        // One-line effect (a keystone's GAIN) — smaller, white, word-wrapped inside the card width
        font.getData().setScale(SCALE_DESC);
        drawWrappedTextWithShadow(plateDescriptions[cardIndex], cardX + TEXT_HORIZONTAL_PAD, top - DESC_Y,
                CARD_WIDTH - TEXT_HORIZONTAL_PAD * 2f, WHITE);

        // A keystone's COST line — near the plate bottom, in the cost colour
        if (plateCostLines[cardIndex] != null) {
            drawWrappedTextWithShadow(plateCostLines[cardIndex], cardX + TEXT_HORIZONTAL_PAD,
                    CARD_Y + COST_LINE_BOTTOM_OFFSET, CARD_WIDTH - TEXT_HORIZONTAL_PAD * 2f, COST_RED);
        }

        // Tap hint — small, dimmed
        font.getData().setScale(SCALE_TAP);
        drawCenteredTextWithShadow("TAP TO CHOOSE", cardCenterX, CARD_Y + TAP_Y, DIM_HINT);
    }

    /** Draws single-line centered text with a dark drop shadow for readability. */
    private void drawCenteredTextWithShadow(String text, float centerX, float baselineY, Color color) {
        glyphLayout.setText(font, text);
        float textX = centerX - glyphLayout.width / 2f;
        font.setColor(TEXT_SHADOW);
        font.draw(batch, text, textX + SHADOW_OFFSET, baselineY - SHADOW_OFFSET);
        font.setColor(color);
        font.draw(batch, text, textX, baselineY);
    }

    /** Draws word-wrapped, horizontally-centered text with a drop shadow within {@code targetWidth}. */
    private void drawWrappedTextWithShadow(String text, float leftX, float baselineY,
                                           float targetWidth, Color color) {
        font.setColor(TEXT_SHADOW);
        font.draw(batch, text, leftX + SHADOW_OFFSET, baselineY - SHADOW_OFFSET,
                targetWidth, Align.center, true);
        font.setColor(color);
        font.draw(batch, text, leftX, baselineY, targetWidth, Align.center, true);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}

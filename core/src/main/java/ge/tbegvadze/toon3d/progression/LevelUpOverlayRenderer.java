package ge.tbegvadze.toon3d.progression;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * Renders the full-screen level-up CARD overlay for mobile (idea 5: build diversity).
 *
 * <p>Instead of three fixed boons, the overlay now shows {@link GameBalance#LEVEL_UP_CARDS_OFFERED}
 * cards DRAWN per level-up by {@link UpgradeCardDeck}. World sets the current offer via
 * {@link #setOfferedCards(UpgradeCard[], int)} before this renders. Each card is coloured by its
 * {@link UpgradeCardPool} and tagged "TRADE-OFF" when it carries a downside. Tap detection is via
 * {@link #getTappedCardIndex(float, float)} which tests world-space (viewport-unprojected) coords.</p>
 *
 * <pre>
 *              *** LEVEL UP! ***         Reached Level N
 *   +-----------+   +-----------+   +-----------+
 *   |  +3       |   | +10 / -18 |   |  +25      |
 *   |MARKSMANSHIP|  | DMG / HP  |   |  MAX HP   |
 *   |  MARKSMAN  |  |GLASS CANNON|  |  VITALITY |
 *   |  TRAINING  |  | TRADE-OFF |   |           |
 *   |   [TAP]   |   |   [TAP]   |   |   [TAP]   |
 *   +-----------+   +-----------+   +-----------+
 * </pre>
 */
public final class LevelUpOverlayRenderer implements Disposable {

    // ---- Card geometry (world units) ----------------------------------------
    private static final float CARD_WIDTH   = 360f;
    private static final float CARD_HEIGHT  = 300f;
    private static final float CARD_GAP     = 20f;
    private static final float CARD_Y       = (Constants.WORLD_HEIGHT - CARD_HEIGHT) / 2f - 30f;

    private static final int   MAX_CARDS    = GameBalance.LEVEL_UP_CARDS_OFFERED;
    private static final float TOTAL_CARDS_WIDTH = MAX_CARDS * CARD_WIDTH + (MAX_CARDS - 1) * CARD_GAP;
    private static final float CARDS_LEFT_X      = (Constants.WORLD_WIDTH - TOTAL_CARDS_WIDTH) / 2f;

    // ---- Internal card layout proportions (offsets from card bottom) ---------
    private static final float CARD_BORDER_THICK   = 4f;
    private static final float ACCENT_STRIP_HEIGHT = 60f;  // colored top accent bar

    // Text Y offsets from card bottom
    private static final float TEXT_VALUE_Y    = CARD_HEIGHT - ACCENT_STRIP_HEIGHT - 30f;  // big value
    private static final float TEXT_NAME_Y     = CARD_HEIGHT - ACCENT_STRIP_HEIGHT - 96f;  // card name
    private static final float TEXT_DESC_Y     = CARD_HEIGHT - ACCENT_STRIP_HEIGHT - 146f; // description
    private static final float TEXT_TRADEOFF_Y = 78f;  // "TRADE-OFF" tag
    private static final float TEXT_TAP_Y      = 42f;  // "TAP TO CHOOSE" hint near bottom

    // Title / subtitle above the cards
    private static final float TITLE_Y    = CARD_Y + CARD_HEIGHT + 70f;
    private static final float SUBTITLE_Y = CARD_Y + CARD_HEIGHT + 32f;

    // ---- Palette -----------------------------------------------------------
    private static final Color DIM_OVERLAY = new Color(0.00f, 0.00f, 0.00f, 0.78f);
    private static final Color WHITE       = new Color(0.95f, 0.95f, 0.95f, 1.00f);
    private static final Color GOLD        = new Color(1.00f, 0.88f, 0.20f, 1.00f);
    private static final Color DIM_HINT    = new Color(0.55f, 0.55f, 0.55f, 1.00f);
    private static final Color RISK        = new Color(1.00f, 0.40f, 0.35f, 1.00f);
    private static final Color CARD_SHADOW = new Color(0.00f, 0.00f, 0.00f, 0.45f);

    // ---- Per-pool palette, indexed by UpgradeCardPool.ordinal() -------------
    private static final Color[] POOL_ACCENT = {
        new Color(1.00f, 0.50f, 0.20f, 1.00f), // OFFENSE — red-orange
        new Color(0.10f, 0.85f, 0.95f, 1.00f), // DEFENSE — cyan
        new Color(0.20f, 1.00f, 0.45f, 1.00f), // SUSTAIN — green
        new Color(1.00f, 0.82f, 0.25f, 1.00f), // UTILITY — gold
    };
    private static final Color[] POOL_BG = {
        new Color(0.14f, 0.05f, 0.03f, 0.97f), // OFFENSE
        new Color(0.04f, 0.10f, 0.14f, 0.97f), // DEFENSE
        new Color(0.04f, 0.12f, 0.05f, 0.97f), // SUSTAIN
        new Color(0.13f, 0.11f, 0.03f, 0.97f), // UTILITY
    };
    private static final Color[] POOL_STRIP = {
        new Color(0.85f, 0.38f, 0.10f, 1.00f), // OFFENSE
        new Color(0.08f, 0.70f, 0.85f, 1.00f), // DEFENSE
        new Color(0.15f, 0.80f, 0.35f, 1.00f), // SUSTAIN
        new Color(0.85f, 0.68f, 0.12f, 1.00f), // UTILITY
    };

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;
    private final PlayerProgress progress;

    private final StringBuilder stringBuilder = new StringBuilder(64);

    // The currently-offered cards (set by World before each render). Never held across level-ups.
    private final UpgradeCard[] offeredCards = new UpgradeCard[MAX_CARDS];
    private int offeredCount = 0;

    public LevelUpOverlayRenderer(PlayerProgress progress) {
        this.progress    = progress;
        this.shapes      = new ShapeRenderer();
        this.batch       = new SpriteBatch();
        this.font        = new BitmapFont();
        this.glyphLayout = new GlyphLayout();
        this.font.getData().markupEnabled = false;
    }

    // -------------------------------------------------------------------------
    // Offer wiring — World calls this once when a level-up begins.
    // -------------------------------------------------------------------------

    /**
     * Sets the cards to display this level-up. Copies references into the renderer's fixed array;
     * {@code count} is clamped to the card slots available. Call before {@link #render}.
     */
    public void setOfferedCards(UpgradeCard[] cards, int count) {
        offeredCount = Math.min(count, MAX_CARDS);
        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            offeredCards[cardIndex] = cards[cardIndex];
        }
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

    /** Returns the offered card at the given slot, or {@code null} if out of range. */
    public UpgradeCard getOfferedCard(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= offeredCount) return null;
        return offeredCards[cardIndex];
    }

    private float cardLeftX(int cardIndex) {
        return CARDS_LEFT_X + cardIndex * (CARD_WIDTH + CARD_GAP);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    public void render(OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ---- Pass A: backgrounds ----
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Full-screen dim
        shapes.setColor(DIM_OVERLAY);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            UpgradeCard card = offeredCards[cardIndex];
            float cardX = cardLeftX(cardIndex);
            // Card drop shadow (slight offset)
            shapes.setColor(CARD_SHADOW);
            shapes.rect(cardX + 6f, CARD_Y - 6f, CARD_WIDTH, CARD_HEIGHT);
            // Card body + accent strip
            shapes.setColor(POOL_BG[card.pool.ordinal()]);
            shapes.rect(cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT);
            shapes.setColor(POOL_STRIP[card.pool.ordinal()]);
            shapes.rect(cardX, CARD_Y + CARD_HEIGHT - ACCENT_STRIP_HEIGHT, CARD_WIDTH, ACCENT_STRIP_HEIGHT);
        }

        shapes.end();

        // ---- Pass B: borders ----
        shapes.begin(ShapeRenderer.ShapeType.Line);
        com.badlogic.gdx.Gdx.gl.glLineWidth(CARD_BORDER_THICK);
        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            UpgradeCard card = offeredCards[cardIndex];
            shapes.setColor(POOL_ACCENT[card.pool.ordinal()]);
            shapes.rect(cardLeftX(cardIndex), CARD_Y, CARD_WIDTH, CARD_HEIGHT);
        }
        com.badlogic.gdx.Gdx.gl.glLineWidth(1f);
        shapes.end();

        com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Pass C: text ----
        batch.begin();

        // Title
        font.getData().setScale(2.2f);
        font.setColor(GOLD);
        drawCenteredText("*** LEVEL UP! ***", Constants.WORLD_WIDTH / 2f, TITLE_Y);

        // Subtitle
        font.getData().setScale(1.3f);
        font.setColor(WHITE);
        stringBuilder.setLength(0);
        stringBuilder.append("Reached Level ").append(progress.getPlayerLevel() + 1);
        drawCenteredText(stringBuilder.toString(), Constants.WORLD_WIDTH / 2f, SUBTITLE_Y);

        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            drawCardText(cardIndex, offeredCards[cardIndex]);
        }

        batch.end();
    }

    private void drawCardText(int cardIndex, UpgradeCard card) {
        float cardCenterX = cardLeftX(cardIndex) + CARD_WIDTH / 2f;
        Color accent = POOL_ACCENT[card.pool.ordinal()];

        // Big headline value (e.g. "+3", "+10 / -18") in accent color
        font.getData().setScale(3.0f);
        font.setColor(accent);
        drawCenteredText(card.valueText, cardCenterX, CARD_Y + TEXT_VALUE_Y);

        // Effect label below the value (e.g. "MARKSMANSHIP") — white
        font.getData().setScale(1.5f);
        font.setColor(WHITE);
        drawCenteredText(card.effectLabel, cardCenterX, CARD_Y + TEXT_VALUE_Y - 52f);

        // Card name — accent color
        font.getData().setScale(1.7f);
        font.setColor(accent);
        drawCenteredText(card.displayName, cardCenterX, CARD_Y + TEXT_NAME_Y);

        // Description — smaller, white
        font.getData().setScale(1.05f);
        font.setColor(WHITE);
        drawCenteredText(card.description, cardCenterX, CARD_Y + TEXT_DESC_Y);

        // Trade-off tag — risk color, only for trade-off cards
        if (card.isTradeOff()) {
            font.getData().setScale(1.1f);
            font.setColor(RISK);
            drawCenteredText("TRADE-OFF", cardCenterX, CARD_Y + TEXT_TRADEOFF_Y);
        }

        // Tap hint — small, dimmed
        font.getData().setScale(1.0f);
        font.setColor(DIM_HINT);
        drawCenteredText("TAP TO CHOOSE", cardCenterX, CARD_Y + TEXT_TAP_Y);
    }

    private void drawCenteredText(String text, float centerX, float baselineY) {
        glyphLayout.setText(font, text);
        float textX = centerX - glyphLayout.width / 2f;
        font.draw(batch, text, textX, baselineY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}

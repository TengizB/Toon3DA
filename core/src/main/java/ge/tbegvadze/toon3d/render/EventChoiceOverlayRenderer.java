package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
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
import ge.tbegvadze.toon3d.route.EventChoice;
import ge.tbegvadze.toon3d.route.FacilityEvent;
import ge.tbegvadze.toon3d.util.Constants;

import java.util.List;

/**
 * Renders the full-screen narrative EVENT choice overlay for mobile (route-map order-10). It REUSES
 * the level-up card UI pattern ({@link ge.tbegvadze.toon3d.progression.LevelUpOverlayRenderer}): a
 * dimmed backdrop, a title + narrative flavour lines at the top, then 1-3 choice cards drawn
 * back-to-front (glow, shadow, gradient body, accent header strip, border) with word-wrapped text and
 * a "TAP TO CHOOSE" hint. World sets the current event via {@link #present(FacilityEvent)} before this
 * renders, reads taps via {@link #getTappedChoiceIndex(float, float)}, and applies the chosen
 * {@link EventChoice}'s effect.
 *
 * <p>Every pixel is procedural (ShapeRenderer + BitmapFont) — no textures, no per-frame allocation in
 * {@link #render}. All layout / colour numbers are renderer-internal layout constants, matching the
 * sibling level-up overlay's local convention. Owns its ShapeRenderer / SpriteBatch / BitmapFont — all
 * disposed in {@link #dispose()}.
 */
public final class EventChoiceOverlayRenderer implements Disposable {

    // ---- Card geometry (world units) ----------------------------------------
    private static final float CARD_WIDTH   = 360f;
    private static final float CARD_HEIGHT  = 250f;
    private static final float CARD_GAP     = 24f;
    private static final float CARD_Y       = 120f;

    private static final float CARD_BORDER_THICK   = 3f;
    private static final float HEADER_STRIP_HEIGHT = 58f;
    private static final float GLOW_INSET          = 10f;
    private static final float TEXT_HORIZONTAL_PAD = 24f;

    // Text Y offsets measured DOWN from the top of the card body.
    private static final float HEADER_TEXT_Y = 38f;   // choice label, centred in the header strip
    private static final float DESC_Y        = 96f;   // wrapped description
    private static final float TAP_Y         = CARD_HEIGHT - 30f; // "TAP TO CHOOSE" hint (from bottom-up)

    // Title / narrative band above the cards.
    private static final float TITLE_Y       = CARD_Y + CARD_HEIGHT + 168f;
    private static final float NARRATIVE_TOP_Y = CARD_Y + CARD_HEIGHT + 118f;
    private static final float NARRATIVE_LINE_STEP = 34f;

    private static final float SCALE_TITLE     = 2.3f;
    private static final float SCALE_NARRATIVE = 1.15f;
    private static final float SCALE_HEADER    = 1.5f;
    private static final float SCALE_DESC      = 1.02f;
    private static final float SCALE_TAP       = 0.95f;

    private static final float SHADOW_OFFSET = 2.5f;

    // ---- Palette -----------------------------------------------------------
    private static final Color DIM_OVERLAY = new Color(0.02f, 0.02f, 0.05f, 0.86f);
    private static final Color WHITE       = new Color(0.97f, 0.97f, 0.98f, 1.00f);
    private static final Color TITLE_TINT  = new Color(0.42f, 0.86f, 0.98f, 1.00f); // comms cyan — the "signal"
    private static final Color NARRATIVE_TINT = new Color(0.80f, 0.82f, 0.88f, 1.00f);
    private static final Color DIM_HINT    = new Color(0.62f, 0.62f, 0.66f, 1.00f);
    private static final Color CARD_SHADOW = new Color(0.00f, 0.00f, 0.00f, 0.50f);
    private static final Color TEXT_SHADOW = new Color(0.00f, 0.00f, 0.00f, 0.72f);
    private static final Color INNER_EDGE  = new Color(1.00f, 1.00f, 1.00f, 0.16f);

    private static final Color ACCENT      = new Color(0.32f, 0.80f, 0.96f, 1.00f);
    private static final Color BODY_TOP    = new Color(0.06f, 0.15f, 0.20f, 0.98f);
    private static final Color BODY_BOTTOM = new Color(0.02f, 0.05f, 0.07f, 0.98f);
    private static final Color STRIP_TOP   = new Color(0.16f, 0.70f, 0.90f, 1.00f);
    private static final Color STRIP_BOTTOM = new Color(0.09f, 0.42f, 0.56f, 1.00f);
    private static final Color GLOW        = new Color(0.32f, 0.80f, 0.96f, 0.22f);

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;

    // Current event + its choices (set by World before each render). Never held across events.
    private FacilityEvent event;
    private final EventChoice[] choices = new EventChoice[FacilityEvent.MAX_CHOICES];
    private int choiceCount = 0;
    private final String[] narrativeLines = new String[8];
    private int narrativeCount = 0;

    public EventChoiceOverlayRenderer() {
        this.shapes      = new ShapeRenderer();
        this.batch       = new SpriteBatch();
        this.font        = new BitmapFont();
        this.glyphLayout = new GlyphLayout();
        this.font.getData().markupEnabled = false;
        this.font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        this.font.setUseIntegerPositions(false);
    }

    /**
     * Sets the event to display. Copies its choices and narrative into fixed arrays (no per-render
     * allocation). Call before {@link #render}.
     */
    public void present(FacilityEvent event) {
        this.event = event;
        this.choiceCount = 0;
        this.narrativeCount = 0;
        if (event == null) {
            return;
        }
        List<EventChoice> eventChoices = event.choices();
        choiceCount = Math.min(eventChoices.size(), choices.length);
        for (int index = 0; index < choiceCount; index++) {
            choices[index] = eventChoices.get(index);
        }
        List<String> lines = event.narrativeLines();
        narrativeCount = Math.min(lines.size(), narrativeLines.length);
        for (int index = 0; index < narrativeCount; index++) {
            narrativeLines[index] = lines.get(index);
        }
    }

    /** The choice at the given slot, or {@code null} if out of range. */
    public EventChoice getChoice(int choiceIndex) {
        if (choiceIndex < 0 || choiceIndex >= choiceCount) return null;
        return choices[choiceIndex];
    }

    /**
     * Returns the index of the tapped choice card, or {@code -1} if no card was hit. Coordinates must
     * be in world space (viewport-unprojected).
     */
    public int getTappedChoiceIndex(float worldX, float worldY) {
        if (worldY < CARD_Y || worldY > CARD_Y + CARD_HEIGHT) return -1;
        for (int choiceIndex = 0; choiceIndex < choiceCount; choiceIndex++) {
            float cardX = cardLeftX(choiceIndex);
            if (worldX >= cardX && worldX < cardX + CARD_WIDTH) return choiceIndex;
        }
        return -1;
    }

    /** Left X of a card, centring the whole row of {@link #choiceCount} cards on the screen. */
    private float cardLeftX(int choiceIndex) {
        float rowWidth = choiceCount * CARD_WIDTH + (choiceCount - 1) * CARD_GAP;
        float rowLeft  = (Constants.WORLD_WIDTH - rowWidth) / 2f;
        return rowLeft + choiceIndex * (CARD_WIDTH + CARD_GAP);
    }

    private static float cardTopY() {
        return CARD_Y + CARD_HEIGHT;
    }

    public void render(OrthographicCamera camera) {
        if (event == null) {
            return;
        }
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ---- Pass A: filled shapes ----
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(DIM_OVERLAY);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        for (int choiceIndex = 0; choiceIndex < choiceCount; choiceIndex++) {
            float cardX = cardLeftX(choiceIndex);
            shapes.setColor(GLOW);
            shapes.rect(cardX - GLOW_INSET, CARD_Y - GLOW_INSET,
                    CARD_WIDTH + GLOW_INSET * 2f, CARD_HEIGHT + GLOW_INSET * 2f);
            shapes.setColor(CARD_SHADOW);
            shapes.rect(cardX + 7f, CARD_Y - 7f, CARD_WIDTH, CARD_HEIGHT);
            // rect(x, y, w, h, bottomLeft, bottomRight, topRight, topLeft)
            shapes.rect(cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT,
                    BODY_BOTTOM, BODY_BOTTOM, BODY_TOP, BODY_TOP);
            float stripY = cardTopY() - HEADER_STRIP_HEIGHT;
            shapes.rect(cardX, stripY, CARD_WIDTH, HEADER_STRIP_HEIGHT,
                    STRIP_BOTTOM, STRIP_BOTTOM, STRIP_TOP, STRIP_TOP);
        }
        shapes.end();

        // ---- Pass B: line work ----
        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(CARD_BORDER_THICK);
        for (int choiceIndex = 0; choiceIndex < choiceCount; choiceIndex++) {
            float cardX = cardLeftX(choiceIndex);
            shapes.setColor(INNER_EDGE);
            shapes.rect(cardX + 3f, CARD_Y + 3f, CARD_WIDTH - 6f, CARD_HEIGHT - 6f);
            shapes.setColor(ACCENT);
            shapes.rect(cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT);
        }
        Gdx.gl.glLineWidth(1f);
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Pass C: text ----
        batch.begin();

        font.getData().setScale(SCALE_TITLE);
        drawCenteredTextWithShadow(event.title(), Constants.WORLD_WIDTH / 2f, TITLE_Y, TITLE_TINT);

        font.getData().setScale(SCALE_NARRATIVE);
        for (int lineIndex = 0; lineIndex < narrativeCount; lineIndex++) {
            drawCenteredTextWithShadow(narrativeLines[lineIndex], Constants.WORLD_WIDTH / 2f,
                    NARRATIVE_TOP_Y - lineIndex * NARRATIVE_LINE_STEP, NARRATIVE_TINT);
        }

        for (int choiceIndex = 0; choiceIndex < choiceCount; choiceIndex++) {
            drawChoiceText(choiceIndex, choices[choiceIndex]);
        }

        batch.end();
    }

    private void drawChoiceText(int choiceIndex, EventChoice choice) {
        float cardX = cardLeftX(choiceIndex);
        float cardCenterX = cardX + CARD_WIDTH / 2f;
        float top = cardTopY();

        font.getData().setScale(SCALE_HEADER);
        drawCenteredTextWithShadow(choice.label(), cardCenterX, top - HEADER_TEXT_Y, WHITE);

        font.getData().setScale(SCALE_DESC);
        drawWrappedTextWithShadow(choice.description(), cardX + TEXT_HORIZONTAL_PAD, top - DESC_Y,
                CARD_WIDTH - TEXT_HORIZONTAL_PAD * 2f, WHITE);

        font.getData().setScale(SCALE_TAP);
        drawCenteredTextWithShadow("TAP TO CHOOSE", cardCenterX, CARD_Y + TAP_Y, DIM_HINT);
    }

    private void drawCenteredTextWithShadow(String text, float centerX, float baselineY, Color color) {
        glyphLayout.setText(font, text);
        float textX = centerX - glyphLayout.width / 2f;
        font.setColor(TEXT_SHADOW);
        font.draw(batch, text, textX + SHADOW_OFFSET, baselineY - SHADOW_OFFSET);
        font.setColor(color);
        font.draw(batch, text, textX, baselineY);
    }

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

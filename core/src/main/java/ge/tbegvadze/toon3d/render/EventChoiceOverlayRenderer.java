package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.route.EventChoice;
import ge.tbegvadze.toon3d.route.FacilityEvent;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.Constants;

import java.util.List;

/**
 * Renders the full-screen narrative EVENT choice overlay for mobile (route-map order-10). It REUSES
 * the level-up card UI pattern ({@link ge.tbegvadze.toon3d.progression.LevelUpOverlayRenderer}): a
 * dimmed backdrop, a title + narrative flavour lines at the top, then 1-3 choice cards (visual-identity-
 * order-6 R9: UiKit cards inked FACILITY_CYAN_DIM under an R1 header) with word-wrapped text and a
 * "TAP TO CHOOSE" hint. World sets the current event via {@link #present(FacilityEvent)} before this
 * renders, reads taps via {@link #getTappedChoiceIndex(float, float)}, and applies the chosen
 * {@link EventChoice}'s effect.
 *
 * <p>Every pixel is procedural (ShapeRenderer + BitmapFont) — no textures, no per-frame allocation in
 * {@link #render}. Layout numbers are renderer-internal constants, matching the sibling level-up overlay's
 * local convention; colours come from util/ArtDirection. Owns its ShapeRenderer / SpriteBatch / two
 * BitmapFonts — all disposed in {@link #dispose()}.
 */
public final class EventChoiceOverlayRenderer implements Disposable {

    // ---- Card geometry (world units) ----------------------------------------
    private static final float CARD_WIDTH   = 360f;
    private static final float CARD_HEIGHT  = 250f;
    private static final float CARD_GAP     = 24f;
    private static final float CARD_Y       = 120f;
    private static final String TAP_HINT_TEXT = "TAP TO CHOOSE";

    private static final float HEADER_STRIP_HEIGHT = 58f;
    private static final float TEXT_HORIZONTAL_PAD = 24f;

    // Text Y offsets measured DOWN from the top of the card body.
    private static final float HEADER_TEXT_Y = 38f;   // choice label, centred in the header band
    private static final float DESC_Y        = 96f;   // wrapped description
    private static final float TAP_Y         = 30f;   // "TAP TO CHOOSE" hint, measured UP from the card bottom

    // Title / narrative band above the cards.
    private static final float TITLE_Y       = CARD_Y + CARD_HEIGHT + 168f;
    private static final float NARRATIVE_TOP_Y = CARD_Y + CARD_HEIGHT + 118f;
    private static final float NARRATIVE_LINE_STEP = 34f;

    // visual-identity-order-6 R9: TYPE_* steps (the title is UiKit.screenHeaderTitle, HEAVY TYPE_HEADING).
    private static final float SCALE_NARRATIVE = ArtDirection.TYPE_BODY;
    private static final float SCALE_HEADER    = ArtDirection.TYPE_LABEL;
    private static final float SCALE_DESC      = ArtDirection.TYPE_BODY;
    private static final float SCALE_TAP       = ArtDirection.TYPE_CAPTION;   // order-3 R8 deferred site (was 0.95)

    // ---- Palette (visual-identity-order-6 R9: the FACILITY ink) ------------
    // An event terminal is the facility talking: one UI_INK_BLACK scrim, an R1 header with a FACILITY_CYAN tick,
    // FACILITY_CYAN brackets round the header + cards, and each choice a card inked FACILITY_CYAN_DIM. Every
    // colour is read from util/ArtDirection; nothing on this screen is shadowed (it sits on the scrim).

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    /** HEAVY face for the R1 screen title. */
    private final BitmapFont    heavyFont;
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
        this.font        = GameFonts.newFont(FontFace.REGULAR);
        this.heavyFont   = GameFonts.newFont(FontFace.HEAVY);
        this.glyphLayout = new GlyphLayout();
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

        // ---- Pass A: filled shapes (the kit's single Filled pass) ----
        float rowLeft  = cardLeftX(0);
        float rowWidth = choiceCount * CARD_WIDTH + Math.max(0, choiceCount - 1) * CARD_GAP;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE,
                        ArtDirection.UI_SCRIM_ALPHA);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        UiKit.screenHeaderRule(shapes, rowLeft, UiKit.screenHeaderRuleY(TITLE_Y), rowWidth,
                               ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                               ArtDirection.FACILITY_CYAN_BLUE, 1f);
        for (int choiceIndex = 0; choiceIndex < choiceCount; choiceIndex++) {
            float cardX = cardLeftX(choiceIndex);
            UiKit.card(shapes, cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT,
                       ArtDirection.FACILITY_CYAN_DIM_RED, ArtDirection.FACILITY_CYAN_DIM_GREEN,
                       ArtDirection.FACILITY_CYAN_DIM_BLUE, PlateState.IDLE);
            // A plain rule marks the label band (the old gradient strip's edge).
            UiKit.divider(shapes, cardX + TEXT_HORIZONTAL_PAD, cardTopY() - HEADER_STRIP_HEIGHT,
                          CARD_WIDTH - TEXT_HORIZONTAL_PAD * 2f,
                          ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                          ArtDirection.FACILITY_CYAN_BLUE, 1f);
        }
        // No single outer panel: FACILITY_CYAN brackets frame the title + card row (the order-5 card-screen rule).
        float margin = ArtDirection.UI_SCREEN_FRAME_MARGIN;
        float frameBottom = CARD_Y - margin;
        UiKit.brackets(shapes, rowLeft - margin, frameBottom, rowWidth + margin * 2f,
                       TITLE_Y + margin - frameBottom,
                       ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                       ArtDirection.FACILITY_CYAN_BLUE, 1f);
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Pass B: text ----
        batch.begin();

        UiKit.screenHeaderTitle(batch, heavyFont, event.title(), rowLeft, TITLE_Y);

        font.getData().setScale(SCALE_NARRATIVE);
        for (int lineIndex = 0; lineIndex < narrativeCount; lineIndex++) {
            drawCenteredText(narrativeLines[lineIndex], Constants.WORLD_WIDTH / 2f,
                    NARRATIVE_TOP_Y - lineIndex * NARRATIVE_LINE_STEP, true);
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
        drawCenteredText(choice.label(), cardCenterX, top - HEADER_TEXT_Y, true);

        font.getData().setScale(SCALE_DESC);
        font.setColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN,
                      ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
        font.draw(batch, choice.description(), cardX + TEXT_HORIZONTAL_PAD, top - DESC_Y,
                  CARD_WIDTH - TEXT_HORIZONTAL_PAD * 2f, Align.center, true);

        font.getData().setScale(SCALE_TAP);
        drawCenteredText(TAP_HINT_TEXT, cardCenterX, CARD_Y + TAP_Y, false);
    }

    /** Centred, unshadowed text in UI_TEXT_PRIMARY ({@code primary}) or UI_TEXT_SECONDARY. */
    private void drawCenteredText(String text, float centerX, float topY, boolean primary) {
        glyphLayout.setText(font, text);
        if (primary) {
            font.setColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN,
                          ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
        } else {
            font.setColor(ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                          ArtDirection.UI_TEXT_SECONDARY_BLUE, 1f);
        }
        font.draw(batch, text, centerX - glyphLayout.width / 2f, topY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        heavyFont.dispose();
    }
}

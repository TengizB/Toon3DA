package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.shop.OfferCategory;
import ge.tbegvadze.toon3d.shop.ShopEntry;
import ge.tbegvadze.toon3d.shop.ShopStock;
import ge.tbegvadze.toon3d.shop.VendingMachine;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.HudConstants;

/**
 * UAC Fabricator shop overlay (shop_order_5).
 *
 * <p>The paused, full-screen touch UI the player sees after tapping USE on a vending machine. It
 * renders the machine's rolled {@link ShopStock} as a grid of up to six cards, the live credit
 * balance, and drives the buy flow via an on-card CONFIRM step. This class owns NO game logic — it
 * displays the stock and reports taps back to {@link ge.tbegvadze.toon3d.world.World} through
 * {@link #handleTouch(float, float)}; World runs the actual {@code ShopTransaction.buy} and reports
 * the result back via {@link #onPurchaseResult(int, boolean)}.
 *
 * <p>Zero per-frame allocations: every string, layout position, and card rectangle is computed in
 * {@link #open} / {@link #setCredits}; {@link #render(OrthographicCamera)} only reads them. Card
 * colours are derived from live entry state (sold-out flag, affordability) with plain float math —
 * no allocation. Text wrapping uses LibGDX's internally-pooled glyph layout.
 */
public final class ShopOverlayRenderer implements Renderable, Disposable {

    /** Outcome of a single tap, returned to World so it can spend credits / close the overlay. */
    public enum TouchOutcome {
        /** Tap hit nothing actionable (or only opened/closed the on-card confirm step). */
        NONE,
        /** The CLOSE button (or the confirm CANCEL) was tapped — World should dismiss the overlay. */
        CLOSED,
        /** A confirmed purchase — World must run the transaction for {@link #getPendingBuyIndex()}. */
        PURCHASE_CONFIRMED
    }

    private static final int MAX_CARDS = 6;

    private static final String HEADER_TEXT = "UAC FABRICATOR";
    private static final String CLOSE_TEXT  = "CLOSE";
    private static final String BUY_TEXT     = "BUY";
    private static final String SOLD_TEXT    = "SOLD";
    private static final String CONFIRM_TEXT = "CONFIRM";
    private static final String CANCEL_TEXT  = "CANCEL";
    private static final String DEPLETED_TEXT = "OUT OF STOCK";

    /** Cold-corporate machine humour; one is chosen at open() with no per-frame allocation. */
    private static final String[] FLAVOR_LINES = {
        "Cash only. The afterlife doesn't take cards.",
        "Fabricating your poor decisions since 2145.",
        "Restocking is above my pay grade.",
        "Warranty void in Hell.",
        "Thank you for your service. Now pay up."
    };

    // ---- Palette (cold UAC steel + cyan phosphor) --------------------------
    private static final Color DIM_BACKGROUND = new Color(0f, 0f, 0f, HudConstants.SHOP_OVERLAY_DIM_ALPHA);
    private static final Color HEADER_BAND     = new Color(0.05f, 0.08f, 0.10f, 0.95f);
    private static final Color CARD_BG          = new Color(0.07f, 0.09f, 0.11f, 0.97f);
    private static final Color CARD_BG_SOLD      = new Color(0.05f, 0.05f, 0.06f, 0.92f);
    private static final Color TITLE_COLOR      = new Color(0.55f, 0.85f, 0.95f, 1f);
    private static final Color CREDITS_COLOR    = new Color(0.55f, 0.85f, 0.95f, 1f);   // matches inventory credits readout
    private static final Color DESC_COLOR       = new Color(0.62f, 0.64f, 0.70f, 1f);
    private static final Color FLAVOR_COLOR     = new Color(0.45f, 0.50f, 0.55f, 1f);
    private static final Color SOLD_TINT        = new Color(0.42f, 0.42f, 0.46f, 1f);
    private static final Color PRICE_AFFORD     = new Color(0.36f, 0.90f, 0.42f, 1f);
    private static final Color PRICE_DENY       = new Color(0.95f, 0.34f, 0.30f, 1f);
    private static final Color BUY_TAG_COLOR    = new Color(0.36f, 0.90f, 0.42f, 1f);
    private static final Color SOLD_TAG_COLOR   = new Color(0.95f, 0.34f, 0.30f, 1f);
    private static final Color CONFIRM_BG        = new Color(0.10f, 0.30f, 0.14f, 1f);
    private static final Color CONFIRM_BORDER    = new Color(0.36f, 0.90f, 0.42f, 1f);
    private static final Color CANCEL_BG         = new Color(0.28f, 0.10f, 0.10f, 1f);
    private static final Color CANCEL_BORDER     = new Color(0.95f, 0.34f, 0.30f, 1f);
    private static final Color BUTTON_LABEL      = new Color(0.94f, 0.96f, 0.98f, 1f);
    private static final Color CONFIRM_PROMPT    = new Color(0.85f, 0.88f, 0.92f, 1f);
    private static final Color CLOSE_BG          = new Color(0.12f, 0.16f, 0.20f, 1f);
    private static final Color CLOSE_BORDER      = new Color(0.55f, 0.85f, 0.95f, 1f);
    private static final Color CLOSE_LABEL       = new Color(0.92f, 0.95f, 0.98f, 1f);
    private static final Color PURCHASE_FLASH    = new Color(0.36f, 0.90f, 0.42f, 0.55f);
    private static final Color DENY_FLASH        = new Color(0.95f, 0.34f, 0.30f, 0.50f);
    private static final Color DEPLETED_COLOR    = new Color(0.95f, 0.34f, 0.30f, 1f);

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   layout;   // used only outside render() to precompute positions

    // Live wallet balance (World pushes it every frame via setCredits).
    private int    credits     = 0;
    private String creditsText = "CREDITS 0";
    private float  creditsX;                          // right-aligned baseline X, recomputed on change

    private float headerTitleX;
    private float closeX, closeY, closeLabelX, closeLabelY;
    private float depletedX, depletedY;

    // Bound stock (read live for sold-out / affordability) plus its precomputed display.
    private ShopStock stock       = null;
    private int       entryCount  = 0;
    private String    flavorText  = FLAVOR_LINES[0];
    private float     flavorBaselineY;

    private final float[]  cardX       = new float[MAX_CARDS];
    private final float[]  cardY       = new float[MAX_CARDS];
    private final String[] cardName    = new String[MAX_CARDS];
    private final String[] cardPrice   = new String[MAX_CARDS];
    private final float[]  cardPriceX  = new float[MAX_CARDS];   // right-aligned price baseline X
    private final String[] cardPrompt  = new String[MAX_CARDS];  // "BUY N cr?"
    private final float[]  cardPromptX = new float[MAX_CARDS];   // centred prompt baseline X

    // Interaction + feedback state.
    private int   confirmingIndex = -1;   // card whose CONFIRM step is open, or -1
    private int   pendingBuyIndex = -1;   // set when a PURCHASE_CONFIRMED is returned
    private int   flashIndex      = -1;   // card flashing after a successful buy
    private float flashTimer      = 0f;
    private int   denyIndex       = -1;   // card blipping after a denied tap
    private float denyTimer       = 0f;

    private float openFade        = 0f;   // 0→1 cosmetic fade-in
    private float animationTime   = 0f;   // drives the BUY affordance pulse
    private float fade            = 1f;   // current draw alpha multiplier (openFade snapshot per frame)

    public ShopOverlayRenderer() {
        shapes = new ShapeRenderer();
        batch  = new SpriteBatch();
        font   = new BitmapFont();
        layout = new GlyphLayout();
        font.getData().markupEnabled = false;

        float closeWidth  = HudConstants.SHOP_OVERLAY_CLOSE_WIDTH;
        float closeHeight = HudConstants.SHOP_OVERLAY_CLOSE_HEIGHT;
        closeX = Constants.WORLD_WIDTH - HudConstants.SHOP_OVERLAY_CLOSE_MARGIN - closeWidth;
        closeY = HudConstants.SHOP_OVERLAY_CLOSE_MARGIN;
        font.getData().setScale(HudConstants.SHOP_OVERLAY_CLOSE_LABEL_SCALE);
        layout.setText(font, CLOSE_TEXT);
        closeLabelX = closeX + closeWidth / 2f - layout.width / 2f;
        closeLabelY = closeY + closeHeight / 2f + layout.height / 2f;

        font.getData().setScale(HudConstants.SHOP_HEADER_TITLE_SCALE);
        headerTitleX = HudConstants.SHOP_HEADER_SIDE_INSET_X;
        layout.setText(font, DEPLETED_TEXT);
        depletedX = Constants.WORLD_WIDTH / 2f - layout.width / 2f;
        depletedY = Constants.WORLD_HEIGHT / 2f + layout.height / 2f;

        flavorBaselineY = HudConstants.SHOP_FOOTER_FLAVOR_Y_ABOVE_BOTTOM;

        setCredits(0);
    }

    /**
     * Binds the machine being browsed, snapshots its stock into the card layout, picks a flavor
     * line, and resets all interaction/feedback state. Called by World when the overlay opens.
     */
    public void open(VendingMachine machine, int currentCredits) {
        setCredits(currentCredits);
        stock      = (machine != null) ? machine.stock : null;
        entryCount = (stock != null) ? Math.min(stock.size(), MAX_CARDS) : 0;
        confirmingIndex = -1;
        pendingBuyIndex = -1;
        flashIndex = -1; flashTimer = 0f;
        denyIndex  = -1; denyTimer  = 0f;
        openFade   = 0f;
        flavorText = FLAVOR_LINES[MathUtils.random(FLAVOR_LINES.length - 1)];

        layoutCards();
        precomputeCardText();
    }

    /** Reflows the entries into a centred grid (≤3 per row) and stores each card's bottom-left corner. */
    private void layoutCards() {
        if (entryCount == 0) return;
        int rowCount = (entryCount <= HudConstants.SHOP_CARD_MAX_COLUMNS) ? 1 : 2;
        int topRowCount    = (rowCount == 1) ? entryCount : Math.min(entryCount, HudConstants.SHOP_CARD_MAX_COLUMNS);
        // Four entries look best as a balanced 2×2 rather than 3 + 1.
        if (entryCount == 4) topRowCount = 2;
        int bottomRowCount = entryCount - topRowCount;

        float cardWidth  = HudConstants.SHOP_CARD_WIDTH;
        float cardHeight = HudConstants.SHOP_CARD_HEIGHT;
        float gap        = HudConstants.SHOP_CARD_GAP;

        float gridHeight = rowCount * cardHeight + (rowCount - 1) * gap;
        float regionBottom = HudConstants.SHOP_FOOTER_HEIGHT;
        float regionTop    = Constants.WORLD_HEIGHT - HudConstants.SHOP_HEADER_HEIGHT;
        float gridTopY     = (regionBottom + regionTop) / 2f + gridHeight / 2f;

        int entryIndex = 0;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            int columnsInRow = (rowIndex == 0) ? topRowCount : bottomRowCount;
            float rowWidth  = columnsInRow * cardWidth + (columnsInRow - 1) * gap;
            float rowLeftX  = (Constants.WORLD_WIDTH - rowWidth) / 2f;
            float rowBottomY = gridTopY - rowIndex * (cardHeight + gap) - cardHeight;
            for (int columnIndex = 0; columnIndex < columnsInRow; columnIndex++) {
                cardX[entryIndex] = rowLeftX + columnIndex * (cardWidth + gap);
                cardY[entryIndex] = rowBottomY;
                entryIndex++;
            }
        }
    }

    /** Precomputes each card's name / price / confirm-prompt strings and their aligned baseline X. */
    private void precomputeCardText() {
        float cardWidth = HudConstants.SHOP_CARD_WIDTH;
        for (int index = 0; index < entryCount; index++) {
            ShopEntry entry = stock.get(index);
            cardName[index]  = entry.displayName;
            cardPrice[index] = entry.price + " cr";
            cardPrompt[index] = "BUY " + entry.price + " cr?";

            float cardRight = cardX[index] + cardWidth;
            font.getData().setScale(HudConstants.SHOP_CARD_PRICE_SCALE);
            layout.setText(font, cardPrice[index]);
            cardPriceX[index] = cardRight - HudConstants.SHOP_CARD_PAD - layout.width;

            font.getData().setScale(HudConstants.SHOP_CONFIRM_PROMPT_SCALE);
            layout.setText(font, cardPrompt[index]);
            cardPromptX[index] = cardX[index] + cardWidth / 2f - layout.width / 2f;
        }
    }

    /** Updates the displayed balance; rebuilds the cached string + right-aligned X only on change. */
    public void setCredits(int currentCredits) {
        if (currentCredits == credits && creditsText != null) return;
        credits     = currentCredits;
        creditsText = "CREDITS " + credits;
        font.getData().setScale(HudConstants.SHOP_HEADER_CREDITS_SCALE);
        layout.setText(font, creditsText);
        creditsX = Constants.WORLD_WIDTH - HudConstants.SHOP_HEADER_SIDE_INSET_X - layout.width;
    }

    public int getPendingBuyIndex() { return pendingBuyIndex; }

    /**
     * Hit-tests a tap (already unprojected to world units) and advances the buy flow.
     *
     * <p>Flow: a first tap on an affordable, unsold card opens that card's CONFIRM step; a second
     * tap on CONFIRM returns {@link TouchOutcome#PURCHASE_CONFIRMED}; CANCEL (or a tap elsewhere)
     * closes the confirm without spending. Tapping the CLOSE button returns {@link TouchOutcome#CLOSED}
     * (cancelling any open confirm first — a purchase is never made on close). Tapping an
     * unaffordable or sold card triggers a denial blip and returns {@link TouchOutcome#NONE}.
     */
    public TouchOutcome handleTouch(float worldX, float worldY) {
        pendingBuyIndex = -1;

        // While a card is mid-confirm, only its CONFIRM / CANCEL buttons (and CLOSE) are live.
        if (confirmingIndex >= 0) {
            int index = confirmingIndex;
            if (isInConfirmButton(index, true, worldX, worldY)) {
                pendingBuyIndex = index;
                confirmingIndex = -1;
                return TouchOutcome.PURCHASE_CONFIRMED;
            }
            if (isInCloseButton(worldX, worldY)) {
                confirmingIndex = -1;
                return TouchOutcome.CLOSED;
            }
            // CANCEL button or any other tap dismisses the confirm without spending.
            confirmingIndex = -1;
            return TouchOutcome.NONE;
        }

        if (isInCloseButton(worldX, worldY)) {
            return TouchOutcome.CLOSED;
        }

        for (int index = 0; index < entryCount; index++) {
            if (!isInCard(index, worldX, worldY)) continue;
            ShopEntry entry = stock.get(index);
            if (entry.isSoldOut() || credits < entry.price) {
                denyIndex = index;
                denyTimer = HudConstants.SHOP_DENY_BLIP_SECONDS;
                return TouchOutcome.NONE;
            }
            confirmingIndex = index;
            return TouchOutcome.NONE;
        }
        return TouchOutcome.NONE;
    }

    /** World reports the transaction result so the card can flash (success) or blip (failure). */
    public void onPurchaseResult(int index, boolean success) {
        if (index < 0 || index >= entryCount) return;
        if (success) {
            flashIndex = index;
            flashTimer = HudConstants.SHOP_PURCHASE_FLASH_SECONDS;
        } else {
            denyIndex = index;
            denyTimer = HudConstants.SHOP_DENY_BLIP_SECONDS;
        }
    }

    private boolean isInCloseButton(float worldX, float worldY) {
        return worldX >= closeX && worldX <= closeX + HudConstants.SHOP_OVERLAY_CLOSE_WIDTH
            && worldY >= closeY && worldY <= closeY + HudConstants.SHOP_OVERLAY_CLOSE_HEIGHT;
    }

    private boolean isInCard(int index, float worldX, float worldY) {
        return worldX >= cardX[index] && worldX <= cardX[index] + HudConstants.SHOP_CARD_WIDTH
            && worldY >= cardY[index] && worldY <= cardY[index] + HudConstants.SHOP_CARD_HEIGHT;
    }

    /** True when the point is inside the CONFIRM (confirmSide=true) or CANCEL (false) button of a card. */
    private boolean isInConfirmButton(int index, boolean confirmSide, float worldX, float worldY) {
        float buttonWidth = (HudConstants.SHOP_CARD_WIDTH - 2f * HudConstants.SHOP_CARD_PAD
                             - HudConstants.SHOP_CONFIRM_BUTTON_GAP) / 2f;
        float buttonBottom = cardY[index] + HudConstants.SHOP_CARD_PAD;
        float buttonLeft   = confirmSide
            ? cardX[index] + HudConstants.SHOP_CARD_PAD
            : cardX[index] + HudConstants.SHOP_CARD_PAD + buttonWidth + HudConstants.SHOP_CONFIRM_BUTTON_GAP;
        return worldX >= buttonLeft && worldX <= buttonLeft + buttonWidth
            && worldY >= buttonBottom && worldY <= buttonBottom + HudConstants.SHOP_CONFIRM_BUTTON_HEIGHT;
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(OrthographicCamera camera) {
        float deltaTime = Gdx.graphics.getDeltaTime();
        animationTime += deltaTime;
        if (openFade < 1f) openFade = Math.min(1f, openFade + deltaTime / HudConstants.SHOP_OVERLAY_OPEN_FADE_SECONDS);
        fade = openFade;
        if (flashTimer > 0f) flashTimer -= deltaTime;
        if (denyTimer  > 0f) denyTimer  -= deltaTime;

        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        drawFilledPass();
        drawLinePass();
        drawTextPass();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawFilledPass() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        setShape(DIM_BACKGROUND);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        setShape(HEADER_BAND);
        shapes.rect(0f, Constants.WORLD_HEIGHT - HudConstants.SHOP_HEADER_HEIGHT,
                    Constants.WORLD_WIDTH, HudConstants.SHOP_HEADER_HEIGHT);

        for (int index = 0; index < entryCount; index++) {
            ShopEntry entry = stock.get(index);
            boolean sold = entry.isSoldOut();
            setShape(sold ? CARD_BG_SOLD : CARD_BG);
            shapes.rect(cardX[index], cardY[index], HudConstants.SHOP_CARD_WIDTH, HudConstants.SHOP_CARD_HEIGHT);

            drawCategoryIcon(entry.category, index, sold);

            if (index == confirmingIndex) {
                drawConfirmButtonBodies(index);
            }
            if (index == flashIndex && flashTimer > 0f) {
                drawCardFlash(index, PURCHASE_FLASH, flashTimer / HudConstants.SHOP_PURCHASE_FLASH_SECONDS);
            }
            if (index == denyIndex && denyTimer > 0f) {
                drawCardFlash(index, DENY_FLASH, denyTimer / HudConstants.SHOP_DENY_BLIP_SECONDS);
            }
        }

        setShape(CLOSE_BG);
        shapes.rect(closeX, closeY, HudConstants.SHOP_OVERLAY_CLOSE_WIDTH, HudConstants.SHOP_OVERLAY_CLOSE_HEIGHT);

        shapes.end();
    }

    private void drawConfirmButtonBodies(int index) {
        float buttonWidth = (HudConstants.SHOP_CARD_WIDTH - 2f * HudConstants.SHOP_CARD_PAD
                             - HudConstants.SHOP_CONFIRM_BUTTON_GAP) / 2f;
        float buttonBottom = cardY[index] + HudConstants.SHOP_CARD_PAD;
        float confirmLeft  = cardX[index] + HudConstants.SHOP_CARD_PAD;
        float cancelLeft   = confirmLeft + buttonWidth + HudConstants.SHOP_CONFIRM_BUTTON_GAP;
        setShape(CONFIRM_BG);
        shapes.rect(confirmLeft, buttonBottom, buttonWidth, HudConstants.SHOP_CONFIRM_BUTTON_HEIGHT);
        setShape(CANCEL_BG);
        shapes.rect(cancelLeft, buttonBottom, buttonWidth, HudConstants.SHOP_CONFIRM_BUTTON_HEIGHT);
    }

    private void drawCardFlash(int index, Color base, float intensity) {
        shapes.setColor(base.r, base.g, base.b, base.a * intensity * fade);
        shapes.rect(cardX[index], cardY[index], HudConstants.SHOP_CARD_WIDTH, HudConstants.SHOP_CARD_HEIGHT);
    }

    /** Small procedural category glyph in the card's top-left corner, tinted by rarity (or grey if sold). */
    private void drawCategoryIcon(OfferCategory category, int index, boolean sold) {
        float size = HudConstants.SHOP_CARD_ICON_SIZE;
        float left = cardX[index] + HudConstants.SHOP_CARD_PAD;
        float top  = cardY[index] + HudConstants.SHOP_CARD_HEIGHT - HudConstants.SHOP_CARD_PAD;
        float bottom = top - size;
        float centerX = left + size / 2f;
        float centerY = bottom + size / 2f;

        ShopEntry entry = stock.get(index);
        if (category == OfferCategory.MEDKIT) {
            setShape(sold ? SOLD_TINT : SOLD_TAG_COLOR); // red cross
            float armThickness = size * 0.30f;
            shapes.rect(centerX - armThickness / 2f, bottom, armThickness, size);
            shapes.rect(left, centerY - armThickness / 2f, size, armThickness);
            return;
        }

        if (sold) shapes.setColor(SOLD_TINT.r, SOLD_TINT.g, SOLD_TINT.b, SOLD_TINT.a * fade);
        else      shapes.setColor(entry.rarity.colorRed, entry.rarity.colorGreen, entry.rarity.colorBlue, fade);

        switch (category) {
            case WEAPON_TIER_UPGRADE:
                // Up-chevron (hollow-look via a single filled triangle).
                shapes.triangle(left, bottom + size * 0.35f, centerX, top, left + size, bottom + size * 0.35f);
                shapes.rect(left + size * 0.30f, bottom, size * 0.40f, size * 0.45f);
                break;
            case WEAPON_LEVEL_UP:
                // Up-arrow: triangle head + stem.
                shapes.triangle(left, centerY, centerX, top, left + size, centerY);
                shapes.rect(centerX - size * 0.14f, bottom, size * 0.28f, size * 0.55f);
                break;
            case PLAYER_ABILITY:
                // Diamond rune (two triangles).
                shapes.triangle(centerX, top, left, centerY, left + size, centerY);
                shapes.triangle(centerX, bottom, left, centerY, left + size, centerY);
                break;
            case AMMO:
            default:
                // Ammo box.
                shapes.rect(left, bottom + size * 0.10f, size, size * 0.70f);
                break;
        }
    }

    private void drawLinePass() {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(HudConstants.SHOP_CARD_BORDER_WIDTH);

        for (int index = 0; index < entryCount; index++) {
            ShopEntry entry = stock.get(index);
            if (entry.isSoldOut()) {
                setShape(SOLD_TINT);
            } else {
                shapes.setColor(entry.rarity.colorRed, entry.rarity.colorGreen, entry.rarity.colorBlue, fade);
            }
            shapes.rect(cardX[index], cardY[index], HudConstants.SHOP_CARD_WIDTH, HudConstants.SHOP_CARD_HEIGHT);

            if (index == confirmingIndex) {
                drawConfirmButtonBorders(index);
            }
        }

        setShape(CLOSE_BORDER);
        shapes.rect(closeX, closeY, HudConstants.SHOP_OVERLAY_CLOSE_WIDTH, HudConstants.SHOP_OVERLAY_CLOSE_HEIGHT);

        Gdx.gl.glLineWidth(1f);
        shapes.end();
    }

    private void drawConfirmButtonBorders(int index) {
        float buttonWidth = (HudConstants.SHOP_CARD_WIDTH - 2f * HudConstants.SHOP_CARD_PAD
                             - HudConstants.SHOP_CONFIRM_BUTTON_GAP) / 2f;
        float buttonBottom = cardY[index] + HudConstants.SHOP_CARD_PAD;
        float confirmLeft  = cardX[index] + HudConstants.SHOP_CARD_PAD;
        float cancelLeft   = confirmLeft + buttonWidth + HudConstants.SHOP_CONFIRM_BUTTON_GAP;
        setShape(CONFIRM_BORDER);
        shapes.rect(confirmLeft, buttonBottom, buttonWidth, HudConstants.SHOP_CONFIRM_BUTTON_HEIGHT);
        setShape(CANCEL_BORDER);
        shapes.rect(cancelLeft, buttonBottom, buttonWidth, HudConstants.SHOP_CONFIRM_BUTTON_HEIGHT);
    }

    private void drawTextPass() {
        batch.begin();

        font.getData().setScale(HudConstants.SHOP_HEADER_TITLE_SCALE);
        setFont(TITLE_COLOR);
        font.draw(batch, HEADER_TEXT, headerTitleX,
                  Constants.WORLD_HEIGHT - HudConstants.SHOP_HEADER_TEXT_Y_BELOW_TOP);

        font.getData().setScale(HudConstants.SHOP_HEADER_CREDITS_SCALE);
        setFont(CREDITS_COLOR);
        font.draw(batch, creditsText, creditsX,
                  Constants.WORLD_HEIGHT - HudConstants.SHOP_HEADER_TEXT_Y_BELOW_TOP);

        for (int index = 0; index < entryCount; index++) {
            drawCardText(index);
        }

        if (stock != null && entryCount > 0 && stock.isDepleted()) {
            font.getData().setScale(HudConstants.SHOP_HEADER_TITLE_SCALE);
            setFont(DEPLETED_COLOR);
            font.draw(batch, DEPLETED_TEXT, depletedX, depletedY);
        }

        font.getData().setScale(HudConstants.SHOP_FOOTER_FLAVOR_SCALE);
        setFont(FLAVOR_COLOR);
        font.draw(batch, flavorText, HudConstants.SHOP_FOOTER_FLAVOR_INSET_X, flavorBaselineY);

        font.getData().setScale(HudConstants.SHOP_OVERLAY_CLOSE_LABEL_SCALE);
        setFont(CLOSE_LABEL);
        font.draw(batch, CLOSE_TEXT, closeLabelX, closeLabelY);

        batch.end();
    }

    private void drawCardText(int index) {
        ShopEntry entry = stock.get(index);
        boolean sold = entry.isSoldOut();
        float cardTop   = cardY[index] + HudConstants.SHOP_CARD_HEIGHT;
        float textLeft  = cardX[index] + HudConstants.SHOP_CARD_PAD;
        float textRight = cardX[index] + HudConstants.SHOP_CARD_WIDTH - HudConstants.SHOP_CARD_PAD;
        float nameLeft  = textLeft + HudConstants.SHOP_CARD_ICON_SIZE + 10f;
        float wrapWidth  = textRight - textLeft;
        float nameWrap   = textRight - nameLeft;

        // Name — rarity colour (grey when sold), wrapped past the icon.
        font.getData().setScale(HudConstants.SHOP_CARD_NAME_SCALE);
        if (sold) setFont(SOLD_TINT);
        else      font.setColor(entry.rarity.colorRed, entry.rarity.colorGreen, entry.rarity.colorBlue, fade);
        font.draw(batch, cardName[index], nameLeft, cardTop - HudConstants.SHOP_CARD_NAME_Y_BELOW_TOP,
                  nameWrap, Align.left, true);

        // On a card mid-confirm, the lower half is the prompt + buttons — skip description/price/tag.
        if (index == confirmingIndex) {
            font.getData().setScale(HudConstants.SHOP_CONFIRM_PROMPT_SCALE);
            setFont(CONFIRM_PROMPT);
            font.draw(batch, cardPrompt[index], cardPromptX[index],
                      cardTop - HudConstants.SHOP_CONFIRM_PROMPT_Y_BELOW_TOP);
            drawConfirmButtonLabels(index);
            return;
        }

        // Description.
        font.getData().setScale(HudConstants.SHOP_CARD_DESC_SCALE);
        setFont(sold ? SOLD_TINT : DESC_COLOR);
        font.draw(batch, entry.description, textLeft, cardTop - HudConstants.SHOP_CARD_DESC_Y_BELOW_TOP,
                  wrapWidth, Align.left, true);

        // Price — green if affordable, red if not (grey when sold).
        float priceBaselineY = cardY[index] + HudConstants.SHOP_CARD_PRICE_Y_ABOVE_BOTTOM;
        font.getData().setScale(HudConstants.SHOP_CARD_PRICE_SCALE);
        if (sold)                      setFont(SOLD_TINT);
        else if (credits < entry.price) setFont(PRICE_DENY);
        else                            setFont(PRICE_AFFORD);
        font.draw(batch, cardPrice[index], cardPriceX[index], priceBaselineY);

        // Bottom-left tag: SOLD stamp, or a pulsing BUY affordance when affordable.
        font.getData().setScale(HudConstants.SHOP_CARD_TAG_SCALE);
        if (sold) {
            setFont(SOLD_TAG_COLOR);
            font.draw(batch, SOLD_TEXT, textLeft, priceBaselineY);
        } else if (credits >= entry.price) {
            float pulse = 0.55f + 0.45f * MathUtils.sin(animationTime * HudConstants.SHOP_BUY_PULSE_SPEED);
            font.setColor(BUY_TAG_COLOR.r, BUY_TAG_COLOR.g, BUY_TAG_COLOR.b, pulse * fade);
            font.draw(batch, BUY_TEXT, textLeft, priceBaselineY);
        }
    }

    private void drawConfirmButtonLabels(int index) {
        float buttonWidth = (HudConstants.SHOP_CARD_WIDTH - 2f * HudConstants.SHOP_CARD_PAD
                             - HudConstants.SHOP_CONFIRM_BUTTON_GAP) / 2f;
        float buttonBottom = cardY[index] + HudConstants.SHOP_CARD_PAD;
        float confirmLeft  = cardX[index] + HudConstants.SHOP_CARD_PAD;
        float cancelLeft   = confirmLeft + buttonWidth + HudConstants.SHOP_CONFIRM_BUTTON_GAP;

        font.getData().setScale(HudConstants.SHOP_CONFIRM_LABEL_SCALE);
        setFont(BUTTON_LABEL);
        layout.setText(font, CONFIRM_TEXT);
        float labelBaselineY = buttonBottom + HudConstants.SHOP_CONFIRM_BUTTON_HEIGHT / 2f + layout.height / 2f;
        font.draw(batch, CONFIRM_TEXT, confirmLeft + buttonWidth / 2f - layout.width / 2f, labelBaselineY);
        layout.setText(font, CANCEL_TEXT);
        font.draw(batch, CANCEL_TEXT, cancelLeft + buttonWidth / 2f - layout.width / 2f, labelBaselineY);
    }

    private void setShape(Color color) {
        shapes.setColor(color.r, color.g, color.b, color.a * fade);
    }

    private void setFont(Color color) {
        font.setColor(color.r, color.g, color.b, color.a * fade);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}

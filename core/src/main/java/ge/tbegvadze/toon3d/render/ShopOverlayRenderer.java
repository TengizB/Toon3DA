package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
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
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.CombatPalette;
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

    private static final int MAX_CARDS = 9;

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

    // ---- Palette (visual-identity-order-6 R9: the FACILITY ink) ----------------
    // Everything is read from util/ArtDirection + util/CombatPalette at draw time — no colour literal here.
    // Offers are cards inked FACILITY_CYAN_DIM (the one being confirmed SELECTED in FACILITY_CYAN); an offer the
    // player cannot take (sold, or unaffordable) is a DISABLED card; prices are CREDITS_GOLD, an unaffordable
    // price CombatPalette.DAMAGE (TYPE_BODY: red text is large text only). The offer's rarity stays on its ICON.

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    /** HEAVY face for the R1 screen title (visual-identity-order-6 R9). */
    private final BitmapFont    heavyFont;
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
    private int   pendingBuyIndex = -1;
    /** Set by a refused card tap, cleared by {@link #consumeDenied()} (procedural-sound-effects order 7). */
    private boolean deniedSinceLastQuery = false;   // set when a PURCHASE_CONFIRMED is returned
    private int   flashIndex      = -1;   // card flashing after a successful buy
    private float flashTimer      = 0f;
    private int   denyIndex       = -1;   // card blipping after a denied tap
    private float denyTimer       = 0f;

    // Receipt toast (under the header): confirms what a buy delivered, or why it was rejected.
    private String  receiptText    = "";
    private float   receiptTimer   = 0f;
    private boolean receiptSuccess = false;
    private float   receiptX       = 0f;   // centred baseline X, recomputed when the text changes

    private float openFade        = 0f;   // 0→1 cosmetic fade-in
    private float animationTime   = 0f;   // drives the BUY affordance pulse
    private float fade            = 1f;   // current draw alpha multiplier (openFade snapshot per frame)

    public ShopOverlayRenderer() {
        shapes = new ShapeRenderer();
        batch  = new SpriteBatch();
        font   = GameFonts.newFont(FontFace.REGULAR);
        heavyFont = GameFonts.newFont(FontFace.HEAVY);
        layout = new GlyphLayout();

        float closeWidth  = HudConstants.SHOP_OVERLAY_CLOSE_WIDTH;
        float closeHeight = HudConstants.SHOP_OVERLAY_CLOSE_HEIGHT;
        closeX = Constants.WORLD_WIDTH - HudConstants.SHOP_OVERLAY_CLOSE_MARGIN - closeWidth;
        closeY = HudConstants.SHOP_OVERLAY_CLOSE_MARGIN;
        font.getData().setScale(HudConstants.SHOP_OVERLAY_CLOSE_LABEL_SCALE);
        layout.setText(font, CLOSE_TEXT);
        closeLabelX = closeX + closeWidth / 2f - layout.width / 2f;
        closeLabelY = closeY + closeHeight / 2f + layout.height / 2f;

        font.getData().setScale(HudConstants.SHOP_DEPLETED_SCALE);
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
        receiptText = ""; receiptTimer = 0f;
        openFade   = 0f;
        flavorText = FLAVOR_LINES[MathUtils.random(FLAVOR_LINES.length - 1)];

        layoutCards();
        precomputeCardText();
    }

    /**
     * Reflows the entries into a centred grid of up to three rows (≤3 cards per row) and stores each
     * card's bottom-left corner. Rows are balanced — the entries are spread as evenly as possible
     * across {@code ceil(count / columns)} rows so a 9-item shelf reads as a clean 3×3, a 4-item
     * shelf as 2×2, a 5-item shelf as 3+2, etc. The grid is vertically centred in the region between
     * the header and footer bands, and each row is horizontally centred on screen.
     */
    private void layoutCards() {
        if (entryCount == 0) return;
        int maxColumns = HudConstants.SHOP_CARD_MAX_COLUMNS;
        int rowCount   = (entryCount + maxColumns - 1) / maxColumns;   // ceil(count / columns)

        // Balanced row sizes: base per row, with the remainder pushed onto the top rows so any
        // short row is the last one (a full row never sits below a partial row).
        int baseColumns = entryCount / rowCount;
        int remainder   = entryCount % rowCount;

        float cardWidth  = HudConstants.SHOP_CARD_WIDTH;
        float cardHeight = HudConstants.SHOP_CARD_HEIGHT;
        float gap        = HudConstants.SHOP_CARD_GAP;

        float gridHeight   = rowCount * cardHeight + (rowCount - 1) * gap;
        float regionBottom = HudConstants.SHOP_FOOTER_HEIGHT;
        float regionTop    = Constants.WORLD_HEIGHT - HudConstants.SHOP_HEADER_HEIGHT;
        float gridTopY     = (regionBottom + regionTop) / 2f + gridHeight / 2f;

        int entryIndex = 0;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            int columnsInRow = baseColumns + (rowIndex < remainder ? 1 : 0);
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

    /** True while a card's CONFIRM / CANCEL step is open — read by World to voice the buy flow. */
    public boolean isConfirming() { return confirmingIndex >= 0; }

    /** True once after a tap on an unaffordable or sold-out card (World plays the refusal sound). */
    public boolean consumeDenied() {
        boolean denied = deniedSinceLastQuery;
        deniedSinceLastQuery = false;
        return denied;
    }

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
                deniedSinceLastQuery = true;
                return TouchOutcome.NONE;
            }
            confirmingIndex = index;
            return TouchOutcome.NONE;
        }
        return TouchOutcome.NONE;
    }

    /**
     * Shows a one-line receipt under the header for {@link HudConstants#SHOP_RECEIPT_SECONDS}. World
     * calls this after every buy tap so the player has a clear textual confirmation of what a
     * purchase delivered (green) or why it was rejected (red). An empty message clears the receipt.
     */
    public void setReceipt(String message, boolean success) {
        receiptText    = (message != null) ? message : "";
        receiptSuccess = success;
        receiptTimer   = receiptText.isEmpty() ? 0f : HudConstants.SHOP_RECEIPT_SECONDS;
        if (!receiptText.isEmpty()) {
            font.getData().setScale(HudConstants.SHOP_RECEIPT_SCALE);
            layout.setText(font, receiptText);
            receiptX = Constants.WORLD_WIDTH / 2f - layout.width / 2f;
        }
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
        if (flashTimer > 0f)   flashTimer   -= deltaTime;
        if (denyTimer  > 0f)   denyTimer    -= deltaTime;
        if (receiptTimer > 0f) receiptTimer -= deltaTime;

        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        drawFilledPass();
        drawTextPass();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawFilledPass() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // One scrim (UI_SCRIM_ALPHA), then the header band as the palette's panel with the R1 rule.
        shapes.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE,
                        ArtDirection.UI_SCRIM_ALPHA * fade);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        shapes.setColor(ArtDirection.UI_PANEL_RED, ArtDirection.UI_PANEL_GREEN, ArtDirection.UI_PANEL_BLUE,
                        ArtDirection.UI_PANEL_ALPHA_SCREEN * fade);
        shapes.rect(0f, Constants.WORLD_HEIGHT - HudConstants.SHOP_HEADER_HEIGHT,
                    Constants.WORLD_WIDTH, HudConstants.SHOP_HEADER_HEIGHT);
        float headerInset = HudConstants.SHOP_HEADER_SIDE_INSET_X;
        UiKit.screenHeaderRule(shapes, headerInset, UiKit.screenHeaderRuleY(headerTitleTopY()),
                               Constants.WORLD_WIDTH - 2f * headerInset,
                               ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                               ArtDirection.FACILITY_CYAN_BLUE, fade);

        for (int index = 0; index < entryCount; index++) {
            ShopEntry entry = stock.get(index);
            boolean sold = entry.isSoldOut();
            PlateState state;
            if (index == confirmingIndex)                  state = PlateState.SELECTED;
            else if (sold || credits < entry.price)        state = PlateState.DISABLED;
            else                                           state = PlateState.IDLE;
            drawFacilityCard(cardX[index], cardY[index], HudConstants.SHOP_CARD_WIDTH, HudConstants.SHOP_CARD_HEIGHT,
                             state);

            drawCategoryIcon(entry.category, index, sold);

            if (index == confirmingIndex) {
                drawConfirmButtonBodies(index);
            }
            if (index == flashIndex && flashTimer > 0f) {
                // A completed purchase: the facility's own cyan, washing off.
                shapes.setColor(ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                                ArtDirection.FACILITY_CYAN_BLUE,
                                HudConstants.SHOP_PURCHASE_FLASH_ALPHA * fade
                                        * (flashTimer / HudConstants.SHOP_PURCHASE_FLASH_SECONDS));
                drawCardFlash(index);
            }
            if (index == denyIndex && denyTimer > 0f) {
                // A refused tap: the harm red, blipping off.
                shapes.setColor(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN, CombatPalette.DAMAGE_BLUE,
                                HudConstants.SHOP_DENY_FLASH_ALPHA * fade
                                        * (denyTimer / HudConstants.SHOP_DENY_BLIP_SECONDS));
                drawCardFlash(index);
            }
        }

        // CLOSE is a FACILITY plate.
        drawFacilityCard(closeX, closeY, HudConstants.SHOP_OVERLAY_CLOSE_WIDTH, HudConstants.SHOP_OVERLAY_CLOSE_HEIGHT,
                         PlateState.IDLE);

        // The fabricator has no single outer panel: its FACILITY_CYAN brackets frame the whole screen (the
        // route console's frame, so the facility's full-screen machines match).
        UiKit.brackets(shapes, 0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT,
                       ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                       ArtDirection.FACILITY_CYAN_BLUE, fade);

        shapes.end();
    }

    /** Top of the R1 title, at the header's existing text anchor. */
    private static float headerTitleTopY() {
        return Constants.WORLD_HEIGHT - HudConstants.SHOP_HEADER_TEXT_Y_BELOW_TOP;
    }

    /**
     * A FACILITY card: inked FACILITY_CYAN_DIM, lit FACILITY_CYAN when SELECTED or PRESSED; the open fade
     * multiplies it.
     */
    private void drawFacilityCard(float cardLeft, float cardBottom, float width, float height, PlateState state) {
        boolean lit = state == PlateState.SELECTED || state == PlateState.PRESSED;
        UiKit.card(shapes, cardLeft, cardBottom, width, height, ArtDirection.UI_PLATE_CHAMFER,
                   lit ? ArtDirection.FACILITY_CYAN_RED : ArtDirection.FACILITY_CYAN_DIM_RED,
                   lit ? ArtDirection.FACILITY_CYAN_GREEN : ArtDirection.FACILITY_CYAN_DIM_GREEN,
                   lit ? ArtDirection.FACILITY_CYAN_BLUE : ArtDirection.FACILITY_CYAN_DIM_BLUE, state, fade);
    }

    private void drawConfirmButtonBodies(int index) {
        float buttonWidth = (HudConstants.SHOP_CARD_WIDTH - 2f * HudConstants.SHOP_CARD_PAD
                             - HudConstants.SHOP_CONFIRM_BUTTON_GAP) / 2f;
        float buttonBottom = cardY[index] + HudConstants.SHOP_CARD_PAD;
        float confirmLeft  = cardX[index] + HudConstants.SHOP_CARD_PAD;
        float cancelLeft   = confirmLeft + buttonWidth + HudConstants.SHOP_CONFIRM_BUTTON_GAP;
        // CONFIRM is the SELECTED commit action, CANCEL an IDLE plate — both the facility's.
        drawFacilityCard(confirmLeft, buttonBottom, buttonWidth, HudConstants.SHOP_CONFIRM_BUTTON_HEIGHT,
                         PlateState.SELECTED);
        drawFacilityCard(cancelLeft, buttonBottom, buttonWidth, HudConstants.SHOP_CONFIRM_BUTTON_HEIGHT,
                         PlateState.IDLE);
    }

    /** Fills a card's chamfered shape with the renderer's current colour (the purchase / refusal flash). */
    private void drawCardFlash(int index) {
        UiKit.fillOctagon(shapes, cardX[index], cardY[index], HudConstants.SHOP_CARD_WIDTH,
                          HudConstants.SHOP_CARD_HEIGHT, ArtDirection.UI_PLATE_CHAMFER);
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
            // Red cross (icon art keeps its colour; a sold offer's icon is the disabled grey).
            if (sold) setDisabledIconColor();
            else      shapes.setColor(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN, CombatPalette.DAMAGE_BLUE, fade);
            float armThickness = size * 0.30f;
            shapes.rect(centerX - armThickness / 2f, bottom, armThickness, size);
            shapes.rect(left, centerY - armThickness / 2f, size, armThickness);
            return;
        }

        if (sold) setDisabledIconColor();
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

    /** A sold offer's icon: the palette's disabled grey. */
    private void setDisabledIconColor() {
        shapes.setColor(ArtDirection.UI_TEXT_DISABLED_RED, ArtDirection.UI_TEXT_DISABLED_GREEN,
                        ArtDirection.UI_TEXT_DISABLED_BLUE, fade);
    }

    private void drawTextPass() {
        batch.begin();

        // R9: the fabricator's screen header (HEAVY title, the cyan-ticked rule drawn in the shape pass).
        heavyFont.setColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN,
                           ArtDirection.UI_TEXT_PRIMARY_BLUE, fade);
        UiKit.screenHeaderTitle(batch, heavyFont, HEADER_TEXT, headerTitleX, headerTitleTopY());

        font.getData().setScale(HudConstants.SHOP_HEADER_CREDITS_SCALE);
        setFontColor(ArtDirection.CREDITS_GOLD_RED, ArtDirection.CREDITS_GOLD_GREEN, ArtDirection.CREDITS_GOLD_BLUE);
        font.draw(batch, creditsText, creditsX,
                  Constants.WORLD_HEIGHT - HudConstants.SHOP_HEADER_TEXT_Y_BELOW_TOP);

        for (int index = 0; index < entryCount; index++) {
            drawCardText(index);
        }

        if (stock != null && entryCount > 0 && stock.isDepleted()) {
            font.getData().setScale(HudConstants.SHOP_DEPLETED_SCALE);
            setFontColor(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN, CombatPalette.DAMAGE_BLUE);
            font.draw(batch, DEPLETED_TEXT, depletedX, depletedY);
        }

        if (receiptTimer > 0f && !receiptText.isEmpty()) {
            // Fade out over the last portion of the receipt's lifetime so it dismisses gently.
            float receiptAlpha = Math.min(1f, receiptTimer / HudConstants.SHOP_RECEIPT_SECONDS + 0.35f);
            // What a buy delivered: the facility's cyan; why it was refused: harm red (TYPE_BODY).
            font.getData().setScale(HudConstants.SHOP_RECEIPT_SCALE);
            if (receiptSuccess) {
                font.setColor(ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                              ArtDirection.FACILITY_CYAN_BLUE, receiptAlpha * fade);
            } else {
                font.setColor(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN, CombatPalette.DAMAGE_BLUE,
                              receiptAlpha * fade);
            }
            font.draw(batch, receiptText, receiptX, HudConstants.SHOP_RECEIPT_Y_ABOVE_BOTTOM);
        }

        font.getData().setScale(HudConstants.SHOP_FOOTER_FLAVOR_SCALE);
        setSecondaryFont();
        font.draw(batch, flavorText, HudConstants.SHOP_FOOTER_FLAVOR_INSET_X, flavorBaselineY);

        font.getData().setScale(HudConstants.SHOP_OVERLAY_CLOSE_LABEL_SCALE);
        setPrimaryFont();
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

        // Name — primary (secondary when sold), wrapped past the icon. Rarity colour lives on the icon.
        font.getData().setScale(HudConstants.SHOP_CARD_NAME_SCALE);
        if (sold) setSecondaryFont();
        else      setPrimaryFont();
        font.draw(batch, cardName[index], nameLeft, cardTop - HudConstants.SHOP_CARD_NAME_Y_BELOW_TOP,
                  nameWrap, Align.left, true);

        // On a card mid-confirm, the lower half is the prompt + buttons — skip description/price/tag.
        if (index == confirmingIndex) {
            font.getData().setScale(HudConstants.SHOP_CONFIRM_PROMPT_SCALE);
            setPrimaryFont();
            font.draw(batch, cardPrompt[index], cardPromptX[index],
                      cardTop - HudConstants.SHOP_CONFIRM_PROMPT_Y_BELOW_TOP);
            drawConfirmButtonLabels(index);
            return;
        }

        // Description.
        font.getData().setScale(HudConstants.SHOP_CARD_DESC_SCALE);
        setSecondaryFont();
        font.draw(batch, entry.description, textLeft, cardTop - HudConstants.SHOP_CARD_DESC_Y_BELOW_TOP,
                  wrapWidth, Align.left, true);

        // Price — credits gold if affordable, harm red if not (secondary when sold).
        float priceBaselineY = cardY[index] + HudConstants.SHOP_CARD_PRICE_Y_ABOVE_BOTTOM;
        font.getData().setScale(HudConstants.SHOP_CARD_PRICE_SCALE);
        if (sold)                       setSecondaryFont();
        else if (credits < entry.price) setFontColor(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN,
                                                     CombatPalette.DAMAGE_BLUE);
        else                            setFontColor(ArtDirection.CREDITS_GOLD_RED, ArtDirection.CREDITS_GOLD_GREEN,
                                                     ArtDirection.CREDITS_GOLD_BLUE);
        font.draw(batch, cardPrice[index], cardPriceX[index], priceBaselineY);

        // Bottom-left tag: SOLD stamp (harm red), or a pulsing BUY affordance in the facility's cyan.
        font.getData().setScale(HudConstants.SHOP_CARD_TAG_SCALE);
        if (sold) {
            setFontColor(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN, CombatPalette.DAMAGE_BLUE);
            font.draw(batch, SOLD_TEXT, textLeft, priceBaselineY);
        } else if (credits >= entry.price) {
            float pulse = 0.55f + 0.45f * MathUtils.sin(animationTime * HudConstants.SHOP_BUY_PULSE_SPEED);
            font.setColor(ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                          ArtDirection.FACILITY_CYAN_BLUE, pulse * fade);
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
        setPrimaryFont();
        layout.setText(font, CONFIRM_TEXT);
        float labelBaselineY = buttonBottom + HudConstants.SHOP_CONFIRM_BUTTON_HEIGHT / 2f + layout.height / 2f;
        font.draw(batch, CONFIRM_TEXT, confirmLeft + buttonWidth / 2f - layout.width / 2f, labelBaselineY);
        layout.setText(font, CANCEL_TEXT);
        font.draw(batch, CANCEL_TEXT, cancelLeft + buttonWidth / 2f - layout.width / 2f, labelBaselineY);
    }

    private void setFontColor(float red, float green, float blue) {
        font.setColor(red, green, blue, fade);
    }

    private void setPrimaryFont() {
        setFontColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN,
                     ArtDirection.UI_TEXT_PRIMARY_BLUE);
    }

    private void setSecondaryFont() {
        setFontColor(ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                     ArtDirection.UI_TEXT_SECONDARY_BLUE);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        heavyFont.dispose();
    }
}

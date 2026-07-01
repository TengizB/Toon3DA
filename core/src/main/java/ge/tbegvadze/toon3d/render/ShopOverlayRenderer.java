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
import ge.tbegvadze.toon3d.shop.ShopEntry;
import ge.tbegvadze.toon3d.shop.ShopStock;
import ge.tbegvadze.toon3d.shop.VendingMachine;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.HudConstants;

/**
 * UAC Fabricator shop overlay (shop_order_1 foundation).
 *
 * <p>This is the paused, full-screen modal the player sees after tapping USE on a vending machine.
 * The foundation draws the frame, a header, the live credit balance, a "stock module offline"
 * placeholder line, and a large CLOSE button — proving the open/close + turn-pause loop end to end.
 * shop_order_5 replaces the placeholder body with the rolled stock card grid and the buy flow;
 * the class name and open/close plumbing are intentionally the ones part 5 extends.
 *
 * <p>Zero per-frame allocations: the credits string and its centered X are recomputed only when the
 * balance changes (via {@link #setCredits(int)}), never inside {@link #render(OrthographicCamera)}.
 */
public final class ShopOverlayRenderer implements Disposable {

    private static final float CENTER_X    = HudConstants.SHOP_OVERLAY_PANEL_X + HudConstants.SHOP_OVERLAY_PANEL_WIDTH / 2f;
    private static final float PANEL_TOP    = HudConstants.SHOP_OVERLAY_PANEL_Y + HudConstants.SHOP_OVERLAY_PANEL_HEIGHT;
    private static final float CLOSE_X      = HudConstants.SHOP_OVERLAY_PANEL_X + HudConstants.SHOP_OVERLAY_PANEL_WIDTH
                                              - HudConstants.SHOP_OVERLAY_CLOSE_MARGIN - HudConstants.SHOP_OVERLAY_CLOSE_WIDTH;
    private static final float CLOSE_Y      = HudConstants.SHOP_OVERLAY_PANEL_Y + HudConstants.SHOP_OVERLAY_CLOSE_MARGIN;

    private static final String HEADER_TEXT   = "UAC FABRICATOR // AUTHORIZED REQUISITION";
    private static final String EMPTY_TEXT    = "NO STOCK AVAILABLE";
    private static final String CLOSE_TEXT    = "CLOSE";
    private static final String SOLD_SUFFIX   = "  [SOLD]";

    private static final float PANEL_LEFT   = HudConstants.SHOP_OVERLAY_PANEL_X;
    private static final float PANEL_RIGHT   = HudConstants.SHOP_OVERLAY_PANEL_X + HudConstants.SHOP_OVERLAY_PANEL_WIDTH;
    private static final float ENTRY_NAME_X  = PANEL_LEFT + HudConstants.SHOP_OVERLAY_ENTRY_NAME_INSET_X;
    private static final float ENTRY_PRICE_RIGHT_X = PANEL_RIGHT - HudConstants.SHOP_OVERLAY_ENTRY_PRICE_INSET_X;

    // ---- Palette (cold UAC steel + cyan phosphor) --------------------------
    private static final Color BACKGROUND    = new Color(0f, 0f, 0f, HudConstants.SHOP_OVERLAY_DIM_ALPHA);
    private static final Color PANEL_BG       = new Color(0.05f, 0.07f, 0.09f, 0.97f);
    private static final Color PANEL_BORDER   = new Color(0.20f, 0.80f, 0.95f, 1f);   // credit-cyan bezel
    private static final Color HEADER_COLOR   = new Color(0.55f, 0.85f, 0.95f, 1f);
    private static final Color BODY_COLOR     = new Color(0.65f, 0.65f, 0.70f, 1f);
    private static final Color CREDITS_COLOR  = new Color(0.55f, 0.85f, 0.95f, 1f);   // matches inventory credits readout
    private static final Color CLOSE_BG       = new Color(0.12f, 0.16f, 0.20f, 1f);
    private static final Color CLOSE_BORDER   = new Color(0.55f, 0.85f, 0.95f, 1f);
    private static final Color CLOSE_LABEL    = new Color(0.92f, 0.95f, 0.98f, 1f);

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   layout;   // used only in open()/setCredits() — never in render()

    private int    credits       = 0;
    private String creditsText   = "CREDITS 0";
    private float  headerX;
    private float  emptyX;
    private float  creditsX;
    private float  closeLabelX;
    private float  closeLabelY;

    // Precomputed stock display (rebuilt in open(); never allocated in render()).
    private int       entryCount   = 0;
    private String[]  entryNames   = new String[0];
    private String[]  entryPrices  = new String[0];
    private float[]   entryPriceX  = new float[0];
    private float[]   entryColorR  = new float[0];
    private float[]   entryColorG  = new float[0];
    private float[]   entryColorB  = new float[0];

    // Sold entries and unaffordable prices tint grey/red respectively.
    private static final float SOLD_GREY = 0.45f;

    public ShopOverlayRenderer() {
        shapes = new ShapeRenderer();
        batch  = new SpriteBatch();
        font   = new BitmapFont();
        layout = new GlyphLayout();
        font.getData().markupEnabled = false;
        precomputeStaticLayout();
        setCredits(0);
    }

    /** Binds the machine being browsed, snapshots its stock for display, and refreshes the balance. */
    public void open(VendingMachine machine, int currentCredits) {
        setCredits(currentCredits);
        ShopStock stock = (machine != null) ? machine.stock : null;
        entryCount = (stock != null) ? stock.size() : 0;
        if (entryNames.length < entryCount) {
            entryNames  = new String[entryCount];
            entryPrices = new String[entryCount];
            entryPriceX = new float[entryCount];
            entryColorR = new float[entryCount];
            entryColorG = new float[entryCount];
            entryColorB = new float[entryCount];
        }
        font.getData().setScale(HudConstants.SHOP_OVERLAY_ENTRY_SCALE);
        for (int index = 0; index < entryCount; index++) {
            ShopEntry entry = stock.get(index);
            boolean sold    = entry.isSoldOut();
            entryNames[index]  = sold ? entry.displayName + SOLD_SUFFIX : entry.displayName;
            entryPrices[index] = entry.price + " cr";
            layout.setText(font, entryPrices[index]);
            entryPriceX[index] = ENTRY_PRICE_RIGHT_X - layout.width;
            if (sold) {
                entryColorR[index] = SOLD_GREY; entryColorG[index] = SOLD_GREY; entryColorB[index] = SOLD_GREY;
            } else {
                entryColorR[index] = entry.rarity.colorRed;
                entryColorG[index] = entry.rarity.colorGreen;
                entryColorB[index] = entry.rarity.colorBlue;
            }
        }
    }

    /** Updates the displayed balance; rebuilds the cached string + centered X only when it changes. */
    public void setCredits(int currentCredits) {
        if (currentCredits == credits && creditsText != null) return;
        credits     = currentCredits;
        creditsText = "CREDITS " + credits;
        font.getData().setScale(HudConstants.SHOP_OVERLAY_CREDITS_SCALE);
        layout.setText(font, creditsText);
        creditsX = CENTER_X - layout.width / 2f;
    }

    private void precomputeStaticLayout() {
        font.getData().setScale(HudConstants.SHOP_OVERLAY_HEADER_SCALE);
        layout.setText(font, HEADER_TEXT);
        headerX = CENTER_X - layout.width / 2f;

        font.getData().setScale(HudConstants.SHOP_OVERLAY_BODY_SCALE);
        layout.setText(font, EMPTY_TEXT);
        emptyX = CENTER_X - layout.width / 2f;

        font.getData().setScale(HudConstants.SHOP_OVERLAY_CLOSE_LABEL_SCALE);
        layout.setText(font, CLOSE_TEXT);
        closeLabelX = CLOSE_X + HudConstants.SHOP_OVERLAY_CLOSE_WIDTH / 2f - layout.width / 2f;
        closeLabelY = CLOSE_Y + HudConstants.SHOP_OVERLAY_CLOSE_HEIGHT / 2f + layout.height / 2f;
    }

    /** True when the given world-space point is inside the CLOSE button. */
    public boolean isCloseTapped(float worldX, float worldY) {
        return worldX >= CLOSE_X && worldX <= CLOSE_X + HudConstants.SHOP_OVERLAY_CLOSE_WIDTH
            && worldY >= CLOSE_Y && worldY <= CLOSE_Y + HudConstants.SHOP_OVERLAY_CLOSE_HEIGHT;
    }

    public void render(OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(BACKGROUND);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        shapes.setColor(PANEL_BG);
        shapes.rect(HudConstants.SHOP_OVERLAY_PANEL_X, HudConstants.SHOP_OVERLAY_PANEL_Y,
                    HudConstants.SHOP_OVERLAY_PANEL_WIDTH, HudConstants.SHOP_OVERLAY_PANEL_HEIGHT);
        shapes.setColor(CLOSE_BG);
        shapes.rect(CLOSE_X, CLOSE_Y, HudConstants.SHOP_OVERLAY_CLOSE_WIDTH, HudConstants.SHOP_OVERLAY_CLOSE_HEIGHT);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(3f);
        shapes.setColor(PANEL_BORDER);
        shapes.rect(HudConstants.SHOP_OVERLAY_PANEL_X, HudConstants.SHOP_OVERLAY_PANEL_Y,
                    HudConstants.SHOP_OVERLAY_PANEL_WIDTH, HudConstants.SHOP_OVERLAY_PANEL_HEIGHT);
        shapes.setColor(CLOSE_BORDER);
        shapes.rect(CLOSE_X, CLOSE_Y, HudConstants.SHOP_OVERLAY_CLOSE_WIDTH, HudConstants.SHOP_OVERLAY_CLOSE_HEIGHT);
        Gdx.gl.glLineWidth(1f);
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();

        font.getData().setScale(HudConstants.SHOP_OVERLAY_HEADER_SCALE);
        font.setColor(HEADER_COLOR);
        font.draw(batch, HEADER_TEXT, headerX, PANEL_TOP - HudConstants.SHOP_OVERLAY_HEADER_Y_BELOW_TOP);

        font.getData().setScale(HudConstants.SHOP_OVERLAY_CREDITS_SCALE);
        font.setColor(CREDITS_COLOR);
        font.draw(batch, creditsText, creditsX, PANEL_TOP - HudConstants.SHOP_OVERLAY_CREDITS_Y_BELOW_TOP);

        if (entryCount == 0) {
            font.getData().setScale(HudConstants.SHOP_OVERLAY_BODY_SCALE);
            font.setColor(BODY_COLOR);
            font.draw(batch, EMPTY_TEXT, emptyX, PANEL_TOP - HudConstants.SHOP_OVERLAY_BODY_Y_BELOW_TOP);
        } else {
            font.getData().setScale(HudConstants.SHOP_OVERLAY_ENTRY_SCALE);
            for (int index = 0; index < entryCount; index++) {
                float rowY = PANEL_TOP - HudConstants.SHOP_OVERLAY_ENTRY_FIRST_Y_BELOW_TOP
                        - index * HudConstants.SHOP_OVERLAY_ENTRY_ROW_STEP;
                font.setColor(entryColorR[index], entryColorG[index], entryColorB[index], 1f);
                font.draw(batch, entryNames[index], ENTRY_NAME_X, rowY);
                font.draw(batch, entryPrices[index], entryPriceX[index], rowY);
            }
        }

        font.getData().setScale(HudConstants.SHOP_OVERLAY_CLOSE_LABEL_SCALE);
        font.setColor(CLOSE_LABEL);
        font.draw(batch, CLOSE_TEXT, closeLabelX, closeLabelY);

        batch.end();
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}

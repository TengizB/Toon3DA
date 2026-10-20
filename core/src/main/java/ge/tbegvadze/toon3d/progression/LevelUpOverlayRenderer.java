package ge.tbegvadze.toon3d.progression;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.render.FontFace;
import ge.tbegvadze.toon3d.render.GameFonts;
import ge.tbegvadze.toon3d.render.PlateState;
import ge.tbegvadze.toon3d.render.UiKit;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.render.BuildTagPalette;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.ProgressionConstants;

import java.util.EnumSet;

/**
 * Renders the full-screen level-up PERK overlay for mobile (balance-overhaul order 5, B3/B8).
 *
 * <p>The overlay shows the {@link BalanceConfig#PERKS_OFFERED} perks {@link PerkDeck} drew for this
 * level-up. World sets the offer via {@link #setOfferedPerks(Perk[], int, PlayerBuild)} before this
 * renders; each plate carries the perk's name, the rank it would reach, its {@link BuildTag} and its
 * one-line effect. CP5 of order 5 draws the tag as a colour-coded CHIP, marks SYNERGY plates with an amber
 * SYNERGY label, shows RANK PIPS (filled = owned, primary = what the pick buys), and haloes KEYSTONE plates
 * in gold. Tap detection is via {@link #getTappedCardIndex(float, float)} which tests world-space
 * (viewport-unprojected) coords.</p>
 *
 * <h2>Visual pipeline (visual-identity-order-5)</h2>
 * <p>Scrim, the R1 screen header (HEAVY title + amber-ticked rule) over the card row, then one UiKit CARD per
 * offer: neutral fill, frame in the perk's / keystone's BuildTag colour (R2), a tag chip (tag fill, UI_LINE
 * border), rank pips, plain dividers and unshadowed text. A keystone keeps a soft gold halo behind its card.</p>
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
    private static final float HEADER_STRIP_HEIGHT = 62f;  // the name band (holds card name), ruled at its bottom
    private static final float GLOW_INSET          = 10f;  // how far a keystone's halo extends past the card
    private static final float TEXT_HORIZONTAL_PAD = 26f;  // side padding for wrapped text

    // Text Y offsets measured DOWN from the top of the card body.
    private static final float HEADER_TEXT_Y   = 40f;   // card name, centered in header strip
    private static final float CHIP_TOP_OFFSET  = ProgressionConstants.LEVEL_UP_CHIP_TOP_OFFSET;  // tag chip: top edge below the card top
    private static final float CHIP_HEIGHT      = ProgressionConstants.BUILD_CHIP_HEIGHT;
    private static final float CHIP_TEXT_PAD    = ProgressionConstants.BUILD_CHIP_TEXT_PAD;  // chip side padding around its text
    private static final float VALUE_Y         = 134f;  // "RANK 2 / 3" / "KEYSTONE"
    private static final float PIP_CENTER_Y    = ProgressionConstants.LEVEL_UP_PIP_CENTER_Y;  // rank pip row
    private static final float PIP_RADIUS      = ProgressionConstants.LEVEL_UP_PIP_RADIUS;
    private static final float PIP_SPACING     = ProgressionConstants.LEVEL_UP_PIP_SPACING;
    private static final float PIP_NEXT_RING   = ProgressionConstants.LEVEL_UP_PIP_NEXT_RING;    // extra ring radius around the highlighted next pip
    private static final float DIVIDER_Y       = 178f;  // hairline separator under the pips
    private static final float DESC_Y          = 200f;  // wrapped description / gain line
    private static final float TAP_BOTTOM_OFFSET = ProgressionConstants.LEVEL_UP_TAP_BOTTOM_OFFSET; // "TAP TO CHOOSE" baseline above the card bottom
    /** Baseline of a keystone's cost line, measured UP from the plate bottom (balance-overhaul order 5). */
    private static final float COST_LINE_BOTTOM_OFFSET = 68f;
    /** The value line of a keystone plate. */
    private static final String KEYSTONE_VALUE_TEXT = "KEYSTONE";
    private static final String SYNERGY_TEXT = "SYNERGY";
    private static final float SCALE_CHIP = ProgressionConstants.BUILD_CHIP_FONT_SCALE;

    // Title / subtitle above the cards
    private static final float TITLE_Y    = CARD_Y + CARD_HEIGHT + 78f;
    private static final float SUBTITLE_Y = CARD_Y + CARD_HEIGHT + 36f;

    // ---- Font scales: TYPE_* steps (visual-identity-order-5 R6); the screen title is the R1 header -----
    private static final float SCALE_SUBTITLE = ArtDirection.TYPE_LABEL;
    private static final float SCALE_HEADER   = ArtDirection.TYPE_LABEL;
    /** The rank line ("RANK 2 / 3") / "KEYSTONE": TYPE_LABEL, so its glyphs end above the pip row (at the old
     *  1.86 / TYPE_HEADING they ran into the pips). */
    private static final float SCALE_VALUE    = ArtDirection.TYPE_LABEL;
    /** Effect lines are captions: a keystone's gain must fit above its cost line in the fixed card. */
    private static final float SCALE_DESC     = ArtDirection.TYPE_CAPTION;
    private static final float SCALE_TAP      = ArtDirection.TYPE_CAPTION;

    // ---- Palette: ArtDirection / semantic constants only ---------------------
    private static final Color PRIMARY     = new Color(ArtDirection.UI_TEXT_PRIMARY_RED,
            ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
    private static final Color SECONDARY   = new Color(ArtDirection.UI_TEXT_SECONDARY_RED,
            ArtDirection.UI_TEXT_SECONDARY_GREEN, ArtDirection.UI_TEXT_SECONDARY_BLUE, 1f);
    /** A keystone's cost line (the order-5 spec's cost colour, CombatPalette.DAMAGE). */
    private static final Color COST_RED    = new Color(ProgressionConstants.BUILD_COST_RED,
            ProgressionConstants.BUILD_COST_GREEN, ProgressionConstants.BUILD_COST_BLUE, 1.00f);
    private static final Color SYNERGY_AMBER = new Color(ProgressionConstants.BUILD_SYNERGY_RED,
            ProgressionConstants.BUILD_SYNERGY_GREEN, ProgressionConstants.BUILD_SYNERGY_BLUE, 1f);
    private static final Color KEYSTONE_GLOW = new Color(ProgressionConstants.BUILD_KEYSTONE_GOLD_RED,
            ProgressionConstants.BUILD_KEYSTONE_GOLD_GREEN, ProgressionConstants.BUILD_KEYSTONE_GOLD_BLUE,
            ProgressionConstants.LEVEL_UP_KEYSTONE_GLOW_ALPHA);
    private static final Color CHIP_TEXT_COLOR = new Color(ProgressionConstants.BUILD_CHIP_TEXT_RED,
            ProgressionConstants.BUILD_CHIP_TEXT_GREEN, ProgressionConstants.BUILD_CHIP_TEXT_BLUE, 1f);
    private static final Color PIP_EMPTY_FILL = new Color(ProgressionConstants.BUILD_PIP_EMPTY_RED,
            ProgressionConstants.BUILD_PIP_EMPTY_GREEN, ProgressionConstants.BUILD_PIP_EMPTY_BLUE, 1f);
    private static final Color PIP_EMPTY_RING = new Color(ProgressionConstants.BUILD_PIP_RING_RED,
            ProgressionConstants.BUILD_PIP_RING_GREEN, ProgressionConstants.BUILD_PIP_RING_BLUE, 1f);

    /** Each tag's chip colour (BuildTagPalette), indexed by BuildTag ordinal: the card's frame ink (R2). */
    private static final Color[] TAG_ACCENT = new Color[BuildTag.values().length];

    static {
        for (BuildTag tag : BuildTag.values()) {
            TAG_ACCENT[tag.ordinal()] = BuildTagPalette.colorOf(tag);
        }
    }

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    /** The HEAVY face: the screen title (R1) and each card's name. */
    private final BitmapFont    headingFont;
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
    /** Per plate: ranks owned BEFORE the pick (perk) — drives the filled pips; the next pip is highlighted. */
    private final int[]      plateOwnedRanks  = new int[MAX_CARDS];
    /** Per plate: the perk's max rank (pip count); 0 for a keystone (no pips). */
    private final int[]      plateMaxRanks    = new int[MAX_CARDS];
    /** Per plate: SYNERGY with the carried weapons' tags. */
    private final boolean[]  plateSynergy     = new boolean[MAX_CARDS];
    /** Per plate: pixel width of the tag chip (text + padding), measured once per offer. */
    private final float[]    plateChipWidths  = new float[MAX_CARDS];
    private boolean keystoneMode = false;
    private int offeredCount = 0;

    public LevelUpOverlayRenderer(PlayerProgress progress) {
        this.progress    = progress;
        this.shapes      = new ShapeRenderer();
        this.batch       = new SpriteBatch();
        this.font        = GameFonts.newFont(FontFace.REGULAR);
        this.headingFont = GameFonts.newFont(FontFace.HEAVY);
        this.headingFont.setUseIntegerPositions(false);
        this.glyphLayout = new GlyphLayout();
        // Sub-pixel positioning keeps scaled text smooth (the atlas filter is GameFonts').
        this.font.setUseIntegerPositions(false);
    }

    // -------------------------------------------------------------------------
    // Offer wiring — World calls this once when a level-up begins.
    // -------------------------------------------------------------------------

    /**
     * Sets the perks to display this level-up. Copies references into the renderer's fixed array and builds
     * each plate's rank text, chip width and SYNERGY flag once ({@code carriedTags} are the weapon tags the
     * player carries, as {@link PerkDeck#isSynergy} reads them); {@code count} is clamped to the plate slots
     * available. Call before {@link #render}.
     */
    public void setOfferedPerks(Perk[] perks, int count, PlayerBuild build, EnumSet<BuildTag> carriedTags) {
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
            plateOwnedRanks[cardIndex]   = build.rankOf(perk);
            plateMaxRanks[cardIndex]     = perk.maxRank;
            plateSynergy[cardIndex]      = PerkDeck.isSynergy(perk, carriedTags);
            plateChipWidths[cardIndex]   = measureChipWidth(perk.tag);
        }
    }

    /**
     * KEYSTONE mode (balance-overhaul order 5, B4): the plates show this keystone level's offer instead of
     * perks — name, tag chip, the GAIN line and the COST line (in the cost colour), inside a gold double frame.
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
            plateOwnedRanks[cardIndex]   = 0;
            plateMaxRanks[cardIndex]     = 0;
            plateSynergy[cardIndex]      = false;
            plateChipWidths[cardIndex]   = measureChipWidth(keystone.tag);
        }
    }

    private float measureChipWidth(BuildTag tag) {
        font.getData().setScale(SCALE_CHIP);
        glyphLayout.setText(font, tag.displayName());
        return glyphLayout.width + CHIP_TEXT_PAD * 2f;
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

    private float cardLeftX(int cardIndex) {
        return CARDS_LEFT_X + cardIndex * (CARD_WIDTH + CARD_GAP);
    }

    /** X centre of pip {@code pipIndex} in a row of {@code pipCount}, centred on the card. */
    private static float pipCenterX(float cardX, int pipCount, int pipIndex) {
        float rowWidth = (pipCount - 1) * PIP_SPACING;
        return cardX + CARD_WIDTH / 2f - rowWidth / 2f + pipIndex * PIP_SPACING;
    }

    /** Y of the top of the card body (below which everything is measured downward). */
    private static float cardTopY() {
        return CARD_Y + CARD_HEIGHT;
    }

    // -------------------------------------------------------------------------
    // Rendering (visual-identity-order-5): scrim -> R1 header -> one UiKit CARD per offer, framed in the tag's
    // colour (R2) -> neutral text. Keystone cards keep a soft gold halo behind them; no gradients, no shadows.
    // -------------------------------------------------------------------------

    public void render(OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ---- one Filled pass: scrim, header rule, glows, cards, chips, dividers, pips ----
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Full-screen scrim (R3)
        shapes.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN,
                ArtDirection.UI_INK_BLACK_BLUE, ArtDirection.UI_SCRIM_ALPHA);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        // Header rule under the title, across the card row (R1)
        UiKit.screenHeaderRule(shapes, CARDS_LEFT_X, UiKit.screenHeaderRuleY(TITLE_Y), TOTAL_CARDS_WIDTH, 1f);

        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            int pool = offeredTags[cardIndex].ordinal();
            float cardX = cardLeftX(cardIndex);
            Color accent = TAG_ACCENT[pool];

            // A keystone keeps its soft gold halo (the card frame is still its TAG ink, R2)
            if (keystoneMode) {
                shapes.setColor(KEYSTONE_GLOW);
                UiKit.fillOctagon(shapes, cardX - GLOW_INSET, CARD_Y - GLOW_INSET,
                        CARD_WIDTH + GLOW_INSET * 2f, CARD_HEIGHT + GLOW_INSET * 2f, ArtDirection.UI_PLATE_CHAMFER);
            }

            UiKit.card(shapes, cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT, accent.r, accent.g, accent.b, PlateState.IDLE);

            // Plain rule under the name band
            UiKit.divider(shapes, cardX + TEXT_HORIZONTAL_PAD, cardTopY() - HEADER_STRIP_HEIGHT,
                    CARD_WIDTH - TEXT_HORIZONTAL_PAD * 2f,
                    ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f);

            // Tag chip: the tag's fill inside a 1-unit UI_LINE border, over the old pill's footprint (R10)
            float chipWidth = plateChipWidths[cardIndex];
            float chipLeft = cardX + (CARD_WIDTH - chipWidth) / 2f;
            float chipBottom = cardTopY() - CHIP_TOP_OFFSET - CHIP_HEIGHT;
            float capRadius = CHIP_HEIGHT / 2f;
            UiKit.panel(shapes, chipLeft - capRadius, chipBottom, chipWidth + capRadius * 2f, CHIP_HEIGHT,
                    ArtDirection.UI_CHIP_CHAMFER, accent.r, accent.g, accent.b, 1f,
                    ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f,
                    ArtDirection.UI_CHIP_BORDER_THICKNESS);

            // Rank pips (drawn filled): owned = tag colour, the one this pick buys = primary inside a tag ring,
            // the rest a UI_LINE ring around a dark centre
            int pipCount = plateMaxRanks[cardIndex];
            float pipY = cardTopY() - PIP_CENTER_Y;
            for (int pipIndex = 0; pipIndex < pipCount; pipIndex++) {
                int pipRank = pipIndex + 1;
                float pipX = pipCenterX(cardX, pipCount, pipIndex);
                if (pipRank <= plateOwnedRanks[cardIndex]) {
                    shapes.setColor(accent);
                    shapes.circle(pipX, pipY, PIP_RADIUS);
                } else if (pipRank == plateOwnedRanks[cardIndex] + 1) {
                    shapes.setColor(accent);
                    shapes.circle(pipX, pipY, PIP_RADIUS + PIP_NEXT_RING);
                    shapes.setColor(PIP_EMPTY_FILL);
                    shapes.circle(pipX, pipY, PIP_RADIUS + PIP_NEXT_RING - ProgressionConstants.BUILD_PIP_RING_THICKNESS);
                    shapes.setColor(PRIMARY);
                    shapes.circle(pipX, pipY, PIP_RADIUS);
                } else {
                    shapes.setColor(PIP_EMPTY_RING);
                    shapes.circle(pipX, pipY, PIP_RADIUS);
                    shapes.setColor(PIP_EMPTY_FILL);
                    shapes.circle(pipX, pipY, PIP_RADIUS - ProgressionConstants.BUILD_PIP_RING_THICKNESS);
                }
            }

            // Plain rule under the pips
            UiKit.divider(shapes, cardX + TEXT_HORIZONTAL_PAD, cardTopY() - DIVIDER_Y,
                    CARD_WIDTH - TEXT_HORIZONTAL_PAD * 2f,
                    ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f);
        }

        shapes.end();
        com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- text (screen text never sits on the 3D view: unshadowed) ----
        batch.begin();

        UiKit.screenHeaderTitle(batch, headingFont, keystoneMode ? "*** KEYSTONE ***" : "*** LEVEL UP! ***",
                CARDS_LEFT_X, TITLE_Y);

        font.getData().setScale(SCALE_SUBTITLE);
        stringBuilder.setLength(0);
        stringBuilder.append("Reached Level ").append(progress.getPlayerLevel() + 1);
        font.setColor(SECONDARY);
        font.draw(batch, stringBuilder, CARDS_LEFT_X, SUBTITLE_Y);

        for (int cardIndex = 0; cardIndex < offeredCount; cardIndex++) {
            drawCardText(cardIndex);
        }
        font.getData().setScale(ArtDirection.TYPE_CAPTION);

        batch.end();
    }

    private void drawCardText(int cardIndex) {
        float cardX = cardLeftX(cardIndex);
        float cardCenterX = cardX + CARD_WIDTH / 2f;
        float top = cardTopY();

        // Perk / keystone name — the card's title: HEAVY, primary, centred in the name band
        headingFont.getData().setScale(SCALE_HEADER);
        drawCentered(headingFont, plateNames[cardIndex], cardCenterX, top - HEADER_TEXT_Y, PRIMARY);
        headingFont.getData().setScale(ArtDirection.TYPE_CAPTION);

        // Tag chip text (dark on the tag colour), centred by cap height, plus the SYNERGY label on its row
        font.getData().setScale(SCALE_CHIP);
        float chipBottom = top - CHIP_TOP_OFFSET - CHIP_HEIGHT;
        float chipTextTop = chipBottom + (CHIP_HEIGHT + font.getCapHeight()) / 2f;
        drawCentered(font, offeredTags[cardIndex].displayName(), cardCenterX, chipTextTop, CHIP_TEXT_COLOR);
        if (plateSynergy[cardIndex]) {
            font.setColor(SYNERGY_AMBER);
            font.draw(batch, SYNERGY_TEXT, cardX + TEXT_HORIZONTAL_PAD, chipTextTop,
                    CARD_WIDTH - TEXT_HORIZONTAL_PAD * 2f + ProgressionConstants.LEVEL_UP_SYNERGY_LABEL_EXTRA_WIDTH, Align.right, false);
        }

        // The rank this pick reaches ("RANK 2 / 3"), or "KEYSTONE"
        font.getData().setScale(SCALE_VALUE);
        drawCentered(font, plateValueTexts[cardIndex], cardCenterX, top - VALUE_Y, PRIMARY);

        // One-line effect (a keystone's GAIN), word-wrapped inside the card width
        font.getData().setScale(SCALE_DESC);
        font.setColor(PRIMARY);
        font.draw(batch, plateDescriptions[cardIndex], cardX + TEXT_HORIZONTAL_PAD, top - DESC_Y,
                CARD_WIDTH - TEXT_HORIZONTAL_PAD * 2f, Align.center, true);

        // A keystone's COST line — near the plate bottom, in the cost colour (a harm, R6)
        if (plateCostLines[cardIndex] != null) {
            font.setColor(COST_RED);
            font.draw(batch, plateCostLines[cardIndex], cardX + TEXT_HORIZONTAL_PAD, CARD_Y + COST_LINE_BOTTOM_OFFSET,
                    CARD_WIDTH - TEXT_HORIZONTAL_PAD * 2f, Align.center, true);
        }

        // Tap hint — a caption at the plate bottom
        font.getData().setScale(SCALE_TAP);
        drawCentered(font, "TAP TO CHOOSE", cardCenterX, CARD_Y + TAP_BOTTOM_OFFSET, SECONDARY);
    }

    /** Draws single-line text centred on {@code centerX} with its top at {@code topY}. */
    private void drawCentered(BitmapFont drawFont, String text, float centerX, float topY, Color color) {
        glyphLayout.setText(drawFont, text);
        drawFont.setColor(color);
        drawFont.draw(batch, text, centerX - glyphLayout.width / 2f, topY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        headingFont.dispose();
    }
}

package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.progression.Keystone;
import ge.tbegvadze.toon3d.progression.Perk;
import ge.tbegvadze.toon3d.progression.PlayerBuild;
import ge.tbegvadze.toon3d.relic.RelicDefinition;
import ge.tbegvadze.toon3d.relic.RelicHand;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.util.ProgressionConstants;

/**
 * The inventory's BUILD page (balance-overhaul order 5, B8): every perk the run has picked GROUPED BY TAG (tag
 * chip row, then one row per perk: name, rank pips, effect line) and the keystones taken (gold name + gain row,
 * red cost row). Read-only. Drawn over the weapon/item panels while the page is open; shares the inventory's
 * ShapeRenderer / SpriteBatch / BitmapFont (this class owns nothing, so it has no dispose).
 *
 * <p>The rows are flattened ONCE in {@link #open} into fixed arrays (worst case 9 tag rows + 26 perks + 1
 * keystone header + 4 x 2 keystone rows = 44), so {@link #render} allocates nothing. More rows than fit are
 * paged with two thumb-sized UP / DOWN plates (a tap, not a drag - the inventory only routes taps).</p>
 *
 * <p>Visual identity order 5: one Filled pass - the page is a UiKit panel, tag chips keep their BuildTagPalette
 * fill inside a 1-unit UI_LINE border (R10), each keystone block is a UiKit card inked by its tag (R2), a relic's
 * rarity shows only as the ink framing its rarity label, and the scroll controls are IDLE plates.</p>
 */
final class BuildPagePanel {

    private enum RowKind { TAG_HEADER, PERK, KEYSTONE_GAIN, KEYSTONE_COST, KEYSTONES_HEADER, RELICS_HEADER, RELIC, RELIC_COST, NOTE }

    private static final int   MAX_ROWS = BuildTag.values().length + Perk.values().length
            + 1 + Keystone.values().length * 2
            + 1 + BalanceConfig.RELIC_MAX_HELD * 2 + 1;   // relics: header + (gain + cost row) each; + the empty-perks note

    // Geometry (world units) — covers both inventory panels below the header.
    private static final float PANEL_X      = ItemConstants.INV_LEFT_PANEL_X;
    private static final float PANEL_WIDTH  = ItemConstants.INV_RIGHT_PANEL_X + ItemConstants.INV_RIGHT_PANEL_WIDTH
            - ItemConstants.INV_LEFT_PANEL_X;
    private static final float PANEL_BOTTOM = ItemConstants.INV_PANEL_Y_BOTTOM;
    private static final float PANEL_TOP    = ItemConstants.INV_PANEL_Y_BOTTOM + ItemConstants.INV_PANEL_HEIGHT;
    private static final float TOP_PAD      = ProgressionConstants.BUILD_PAGE_TOP_PAD;
    private static final float FOOTER_HEIGHT = ProgressionConstants.BUILD_PAGE_FOOTER_HEIGHT;
    private static final float ROW_HEIGHT   = ProgressionConstants.BUILD_PAGE_ROW_HEIGHT;
    private static final float ROW_TEXT_RISE = ProgressionConstants.BUILD_PAGE_ROW_TEXT_RISE;
    private static final int   VISIBLE_ROWS = (int) ((PANEL_TOP - PANEL_BOTTOM - TOP_PAD - FOOTER_HEIGHT) / ROW_HEIGHT);

    private static final float NAME_X       = PANEL_X + ProgressionConstants.BUILD_PAGE_NAME_OFFSET_X;
    private static final float PIPS_LEFT_X  = PANEL_X + ProgressionConstants.BUILD_PAGE_PIPS_OFFSET_X;
    private static final float EFFECT_X     = PANEL_X + ProgressionConstants.BUILD_PAGE_EFFECT_OFFSET_X;
    private static final float EFFECT_WIDTH = PANEL_X + PANEL_WIDTH - ProgressionConstants.BUILD_PAGE_EFFECT_RIGHT_MARGIN - EFFECT_X;
    private static final float CHIP_X       = PANEL_X + ProgressionConstants.BUILD_PAGE_CHIP_OFFSET_X;
    private static final float CHIP_HEIGHT  = ProgressionConstants.BUILD_CHIP_HEIGHT;
    private static final float CHIP_PAD     = ProgressionConstants.BUILD_CHIP_TEXT_PAD;
    private static final float PIP_RADIUS   = ProgressionConstants.BUILD_PAGE_PIP_RADIUS;
    private static final float PIP_SPACING  = ProgressionConstants.BUILD_PAGE_PIP_SPACING;

    private static final float SCALE_NAME   = ProgressionConstants.BUILD_PAGE_SCALE_NAME;
    private static final float SCALE_EFFECT = ProgressionConstants.BUILD_PAGE_SCALE_EFFECT;
    private static final float SCALE_CHIP   = ProgressionConstants.BUILD_CHIP_FONT_SCALE;
    private static final float SCALE_PLATE  = ProgressionConstants.BUILD_PAGE_SCALE_PLATE;
    private static final float SCALE_SECTION = ArtDirection.TYPE_LABEL;
    private static final float SCALE_COST    = ArtDirection.TYPE_BODY;

    private static final float PLATE_WIDTH  = ProgressionConstants.BUILD_PAGE_PLATE_WIDTH;
    private static final float PLATE_HEIGHT = ProgressionConstants.BUILD_PAGE_PLATE_HEIGHT;
    private static final float PLATE_GAP    = ProgressionConstants.BUILD_PAGE_PLATE_GAP;
    private static final float PLATE_Y      = PANEL_BOTTOM + ProgressionConstants.BUILD_PAGE_PLATE_BOTTOM_MARGIN;
    private static final float PLATE_DOWN_X = PANEL_X + PANEL_WIDTH - ProgressionConstants.BUILD_PAGE_PLATE_RIGHT_MARGIN - PLATE_WIDTH;
    private static final float PLATE_UP_X   = PLATE_DOWN_X - PLATE_GAP - PLATE_WIDTH;

    private static final String EMPTY_TEXT   = "NO PERKS YET - EACH LEVEL-UP ADDS TO YOUR BUILD";
    private static final String KEYSTONES_TEXT = "KEYSTONES";
    private static final String PLATE_UP_TEXT = "UP";
    private static final String PLATE_DOWN_TEXT = "DOWN";

    private static final Color ROW_STRIPE    = new Color(ProgressionConstants.BUILD_PAGE_STRIPE_RED,
            ProgressionConstants.BUILD_PAGE_STRIPE_GREEN, ProgressionConstants.BUILD_PAGE_STRIPE_BLUE, ProgressionConstants.BUILD_PAGE_STRIPE_ALPHA);
    private static final Color TEXT_MAIN     = new Color(ProgressionConstants.BUILD_PAGE_TEXT_MAIN_RED,
            ProgressionConstants.BUILD_PAGE_TEXT_MAIN_GREEN, ProgressionConstants.BUILD_PAGE_TEXT_MAIN_BLUE, 1f);
    private static final Color TEXT_DIM      = new Color(ProgressionConstants.BUILD_PAGE_TEXT_DIM_RED,
            ProgressionConstants.BUILD_PAGE_TEXT_DIM_GREEN, ProgressionConstants.BUILD_PAGE_TEXT_DIM_BLUE, 1f);
    private static final Color CHIP_TEXT     = new Color(ProgressionConstants.BUILD_CHIP_TEXT_RED,
            ProgressionConstants.BUILD_CHIP_TEXT_GREEN, ProgressionConstants.BUILD_CHIP_TEXT_BLUE, 1f);
    private static final Color PIP_EMPTY     = new Color(ProgressionConstants.BUILD_PIP_EMPTY_RED,
            ProgressionConstants.BUILD_PIP_EMPTY_GREEN, ProgressionConstants.BUILD_PIP_EMPTY_BLUE, 1f);
    private static final Color PIP_RING      = new Color(ProgressionConstants.BUILD_PIP_RING_RED,
            ProgressionConstants.BUILD_PIP_RING_GREEN, ProgressionConstants.BUILD_PIP_RING_BLUE, 1f);
    private static final Color PLATE_TEXT    = new Color(ProgressionConstants.BUILD_PAGE_PLATE_TEXT_RED,
            ProgressionConstants.BUILD_PAGE_PLATE_TEXT_GREEN, ProgressionConstants.BUILD_PAGE_PLATE_TEXT_BLUE, 1f);
    private static final Color COST_RED      = new Color(ProgressionConstants.BUILD_COST_RED,
            ProgressionConstants.BUILD_COST_GREEN, ProgressionConstants.BUILD_COST_BLUE, 1f);

    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;
    private final StringBuilder textBuilder = new StringBuilder(24);

    private PlayerBuild playerBuild = null;
    private RelicHand relicHand = null;

    // Flattened rows, rebuilt by open().
    private final RowKind[] rowKinds   = new RowKind[MAX_ROWS];
    private final BuildTag[] rowTags   = new BuildTag[MAX_ROWS];
    private final String[]  rowNames   = new String[MAX_ROWS];
    private final String[]  rowEffects = new String[MAX_ROWS];
    private final int[]     rowRanks   = new int[MAX_ROWS];
    private final int[]     rowMaxRanks = new int[MAX_ROWS];
    private final float[]   rowChipWidths = new float[MAX_ROWS];
    /** Per row: the RelicRarity ordinal (RELIC rows) indexing the HudConstants rarity colours; unused otherwise. */
    private final int[]     rowRarityOrdinals = new int[MAX_ROWS];
    private final String[]  rowBadges  = new String[MAX_ROWS];
    /** Per RELIC row: the rarity label's measured width (the rarity-inked frame around it), set in open(). */
    private final float[]   rowBadgeWidths = new float[MAX_ROWS];
    private final Color[]   relicRarityColors = new Color[HudConstants.RELIC_RARITY_COLOR_R.length];
    private int rowCount   = 0;
    private int firstRow   = 0;

    BuildPagePanel(ShapeRenderer shapeRenderer, SpriteBatch spriteBatch, BitmapFont font, GlyphLayout glyphLayout) {
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch   = spriteBatch;
        this.font          = font;
        this.glyphLayout   = glyphLayout;
        for (int rarityIndex = 0; rarityIndex < relicRarityColors.length; rarityIndex++) {
            relicRarityColors[rarityIndex] = new Color(HudConstants.RELIC_RARITY_COLOR_R[rarityIndex],
                    HudConstants.RELIC_RARITY_COLOR_G[rarityIndex], HudConstants.RELIC_RARITY_COLOR_B[rarityIndex], 1f);
        }
    }

    /** The run's held relics, read when the page opens (the hand object is stable for the world's life). */
    void setRelicHand(RelicHand relicHand) {
        this.relicHand = relicHand;
    }

    void setPlayerBuild(PlayerBuild playerBuild) {
        this.playerBuild = playerBuild;
    }

    /** Rebuilds the grouped row list from the build (called when the page is opened) and scrolls to the top. */
    void open() {
        rowCount = 0;
        firstRow = 0;
        if (playerBuild == null) return;
        font.getData().setScale(SCALE_EFFECT);
        addRelicRows();
        if (playerBuild.keystoneCount() == 0 && !hasAnyPerk()) {
            addRow(RowKind.NOTE, null, EMPTY_TEXT, null, 0, 0);
        }
        font.getData().setScale(SCALE_CHIP);
        for (BuildTag tag : BuildTag.values()) {
            if (playerBuild.ranksInTag(tag) <= 0) continue;
            addRow(RowKind.TAG_HEADER, tag, tag.displayName(), null, 0, 0);
            glyphLayout.setText(font, tag.displayName());
            rowChipWidths[rowCount - 1] = glyphLayout.width + CHIP_PAD * 2f;
            for (Perk perk : Perk.values()) {
                if (perk.tag != tag || playerBuild.rankOf(perk) <= 0) continue;
                addRow(RowKind.PERK, tag, perk.displayName, perk.effectLine, playerBuild.rankOf(perk), perk.maxRank);
            }
        }
        if (playerBuild.keystoneCount() > 0) {
            addRow(RowKind.KEYSTONES_HEADER, null, KEYSTONES_TEXT, null, 0, 0);
            for (Keystone keystone : Keystone.values()) {
                if (!playerBuild.hasKeystone(keystone)) continue;
                addRow(RowKind.KEYSTONE_GAIN, keystone.tag, keystone.displayName, keystone.gainLine, 0, 0);
                addRow(RowKind.KEYSTONE_COST, keystone.tag, "", keystone.costLine, 0, 0);
            }
        }
        font.getData().setScale(ArtDirection.TYPE_CAPTION);
    }

    private boolean hasAnyPerk() {
        for (BuildTag tag : BuildTag.values()) {
            if (playerBuild.ranksInTag(tag) > 0) return true;
        }
        return false;
    }

    /** RELICS (n) section: built once per open(); "RELICS - none" when the hand is empty. */
    private void addRelicRows() {
        int held = relicHand == null ? 0 : relicHand.count();
        textBuilder.setLength(0);
        if (held == 0) {
            textBuilder.append(HudConstants.RELIC_BUILD_NONE_TEXT);
        } else {
            textBuilder.append(HudConstants.RELIC_BUILD_HEADER_PREFIX).append(held).append(')');
        }
        addRow(RowKind.RELICS_HEADER, null, textBuilder.toString(), null, 0, 0);
        for (int relicIndex = 0; relicIndex < held; relicIndex++) {
            RelicDefinition relic = relicHand.get(relicIndex);
            addRow(RowKind.RELIC, null, relic.name(), relic.effectText(), 0, 0);
            rowRarityOrdinals[rowCount - 1] = relic.rarity().ordinal();
            rowBadges[rowCount - 1] = relic.rarity().label();
            glyphLayout.setText(font, relic.rarity().label());
            rowBadgeWidths[rowCount - 1] = glyphLayout.width + ProgressionConstants.BUILD_PAGE_BADGE_PAD * 2f;
            if (relic.costText() != null && !relic.costText().isEmpty()) {
                addRow(RowKind.RELIC_COST, null, "", relic.costText(), 0, 0);
            }
        }
    }

    private void addRow(RowKind kind, BuildTag tag, String name, String effect, int rank, int maxRank) {
        if (rowCount >= MAX_ROWS) return;
        rowKinds[rowCount] = kind;
        rowTags[rowCount] = tag;
        rowNames[rowCount] = name;
        rowEffects[rowCount] = effect;
        rowRanks[rowCount] = rank;
        rowMaxRanks[rowCount] = maxRank;
        rowCount++;
    }

    private boolean canScroll() {
        return rowCount > VISIBLE_ROWS;
    }

    /** Handles a tap on the page; true when the tap landed on a scroll plate. */
    boolean handleTouch(float worldX, float worldY) {
        if (!canScroll()) return false;
        if (worldY < PLATE_Y || worldY > PLATE_Y + PLATE_HEIGHT) return false;
        int pageStep = VISIBLE_ROWS - 1;
        int maxFirstRow = rowCount - VISIBLE_ROWS;
        if (worldX >= PLATE_UP_X && worldX <= PLATE_UP_X + PLATE_WIDTH) {
            firstRow = Math.max(0, firstRow - pageStep);
            return true;
        }
        if (worldX >= PLATE_DOWN_X && worldX <= PLATE_DOWN_X + PLATE_WIDTH) {
            firstRow = Math.min(maxFirstRow, firstRow + pageStep);
            return true;
        }
        return false;
    }

    private float rowBottom(int visibleIndex) {
        return PANEL_TOP - TOP_PAD - (visibleIndex + 1) * ROW_HEIGHT;
    }

    void render(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        spriteBatch.setProjectionMatrix(camera.combined);
        int lastRow = Math.min(rowCount, firstRow + VISIBLE_ROWS);

        // ---- one Filled pass: page, stripes, chips, pips, keystone cards, rarity frames, plates ----
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapeRenderer, PANEL_X, PANEL_BOTTOM, PANEL_WIDTH, PANEL_TOP - PANEL_BOTTOM,
                ProgressionConstants.BUILD_PAGE_BG_RED, ProgressionConstants.BUILD_PAGE_BG_GREEN,
                ProgressionConstants.BUILD_PAGE_BG_BLUE, ProgressionConstants.BUILD_PAGE_BG_ALPHA,
                ProgressionConstants.BUILD_PAGE_BORDER_RED, ProgressionConstants.BUILD_PAGE_BORDER_GREEN,
                ProgressionConstants.BUILD_PAGE_BORDER_BLUE);
        for (int rowIndex = firstRow; rowIndex < lastRow; rowIndex++) {
            int visibleIndex = rowIndex - firstRow;
            float bottom = rowBottom(visibleIndex);
            RowKind kind = rowKinds[rowIndex];
            if (kind == RowKind.PERK && (rowIndex % 2 == 0)) {
                shapeRenderer.setColor(ROW_STRIPE);
                shapeRenderer.rect(PANEL_X + ProgressionConstants.BUILD_PAGE_ROW_INSET, bottom,
                        PANEL_WIDTH - ProgressionConstants.BUILD_PAGE_ROW_INSET * 2f, ROW_HEIGHT);
            }
            if (kind == RowKind.TAG_HEADER) {
                // R10: the tag's chip keeps its BuildTagPalette fill, framed by a 1-unit UI_LINE border. It
                // covers the old pill's footprint (the rect plus its two end caps).
                float chipBottom = bottom + (ROW_HEIGHT - CHIP_HEIGHT) / 2f;
                float capRadius = CHIP_HEIGHT / 2f;
                Color tagColor = BuildTagPalette.colorOf(rowTags[rowIndex]);
                drawChip(CHIP_X - capRadius, chipBottom, rowChipWidths[rowIndex] + capRadius * 2f, CHIP_HEIGHT,
                        tagColor.r, tagColor.g, tagColor.b);
            } else if (kind == RowKind.PERK) {
                Color accent = BuildTagPalette.colorOf(rowTags[rowIndex]);
                float pipY = bottom + ROW_HEIGHT / 2f;
                for (int pipIndex = 0; pipIndex < rowMaxRanks[rowIndex]; pipIndex++) {
                    float pipX = PIPS_LEFT_X + pipIndex * PIP_SPACING;
                    if (pipIndex < rowRanks[rowIndex]) {
                        shapeRenderer.setColor(accent);
                        shapeRenderer.circle(pipX, pipY, PIP_RADIUS);
                    } else {
                        // An empty pip: a UI_LINE ring around a dark centre, both filled.
                        shapeRenderer.setColor(PIP_RING);
                        shapeRenderer.circle(pipX, pipY, PIP_RADIUS);
                        shapeRenderer.setColor(PIP_EMPTY);
                        shapeRenderer.circle(pipX, pipY, PIP_RADIUS - ProgressionConstants.BUILD_PIP_RING_THICKNESS);
                    }
                }
            } else if (kind == RowKind.KEYSTONE_GAIN && rowIndex + 1 < lastRow) {
                // R2: one card around the two-row keystone block (drawn from its gain row), inked by its tag.
                Color tagColor = BuildTagPalette.colorOf(rowTags[rowIndex]);
                UiKit.card(shapeRenderer, PANEL_X + ProgressionConstants.BUILD_PAGE_ROW_INSET,
                        bottom - ROW_HEIGHT + ProgressionConstants.BUILD_PAGE_KEYSTONE_FRAME_INSET,
                        PANEL_WIDTH - ProgressionConstants.BUILD_PAGE_ROW_INSET * 2f,
                        ROW_HEIGHT * 2f - ProgressionConstants.BUILD_PAGE_KEYSTONE_FRAME_INSET * 2f,
                        tagColor.r, tagColor.g, tagColor.b, PlateState.IDLE);
            } else if (kind == RowKind.RELIC) {
                // The relic's rarity colour lives only in the frame around its rarity label (R2 / D2).
                Color rarityColor = relicRarityColors[rowRarityOrdinals[rowIndex]];
                float frameBottom = bottom + (ROW_HEIGHT - CHIP_HEIGHT) / 2f;
                UiKit.panel(shapeRenderer, PIPS_LEFT_X - ProgressionConstants.BUILD_PAGE_BADGE_PAD, frameBottom,
                        rowBadgeWidths[rowIndex], CHIP_HEIGHT, ArtDirection.UI_CHIP_CHAMFER,
                        ArtDirection.UI_PANEL_RED, ArtDirection.UI_PANEL_GREEN, ArtDirection.UI_PANEL_BLUE, 1f,
                        rarityColor.r, rarityColor.g, rarityColor.b, 1f, ArtDirection.UI_BORDER_THICKNESS);
            }
        }
        if (canScroll()) {
            drawScrollPlate(PLATE_UP_X);
            drawScrollPlate(PLATE_DOWN_X);
        }
        shapeRenderer.end();

        // ---- text ----
        spriteBatch.begin();
        for (int rowIndex = firstRow; rowIndex < lastRow; rowIndex++) {
            float baseline = rowBottom(rowIndex - firstRow) + ROW_TEXT_RISE + ProgressionConstants.BUILD_PAGE_ROW_BASELINE_EXTRA;
            RowKind kind = rowKinds[rowIndex];
            if (kind == RowKind.TAG_HEADER) {
                font.getData().setScale(SCALE_CHIP);
                font.setColor(CHIP_TEXT);
                // Centred in the chip: a glyph box of cap height h centred in [Y, Y+H] has its top at Y + (H + h) / 2.
                float chipBottom = rowBottom(rowIndex - firstRow) + (ROW_HEIGHT - CHIP_HEIGHT) / 2f;
                font.draw(spriteBatch, rowNames[rowIndex], CHIP_X + CHIP_PAD,
                        chipBottom + (CHIP_HEIGHT + font.getCapHeight()) / 2f);
            } else if (kind == RowKind.PERK) {
                font.getData().setScale(SCALE_NAME);
                font.setColor(TEXT_MAIN);
                font.draw(spriteBatch, rowNames[rowIndex], NAME_X, baseline);
                drawEffect(rowEffects[rowIndex], baseline, TEXT_DIM);
            } else if (kind == RowKind.RELICS_HEADER || kind == RowKind.KEYSTONES_HEADER) {
                // R6: section labels are TYPE_LABEL secondary.
                font.getData().setScale(SCALE_SECTION);
                font.setColor(TEXT_DIM);
                font.draw(spriteBatch, rowNames[rowIndex], CHIP_X, baseline);
            } else if (kind == RowKind.RELIC) {
                font.getData().setScale(SCALE_NAME);
                font.setColor(TEXT_MAIN);
                font.draw(spriteBatch, rowNames[rowIndex], NAME_X, baseline);
                font.getData().setScale(SCALE_EFFECT);
                font.setColor(TEXT_DIM);
                float frameBottom = rowBottom(rowIndex - firstRow) + (ROW_HEIGHT - CHIP_HEIGHT) / 2f;
                font.draw(spriteBatch, rowBadges[rowIndex], PIPS_LEFT_X,
                        frameBottom + (CHIP_HEIGHT + font.getCapHeight()) / 2f);
                drawEffect(rowEffects[rowIndex], baseline, TEXT_MAIN);
            } else if (kind == RowKind.RELIC_COST) {
                drawCost(rowEffects[rowIndex], baseline);
            } else if (kind == RowKind.NOTE) {
                font.getData().setScale(SCALE_EFFECT);
                font.setColor(TEXT_DIM);
                font.draw(spriteBatch, rowNames[rowIndex], CHIP_X, baseline);
            } else if (kind == RowKind.KEYSTONE_GAIN) {
                font.getData().setScale(SCALE_NAME);
                font.setColor(TEXT_MAIN);
                font.draw(spriteBatch, rowNames[rowIndex], NAME_X, baseline);
                drawEffect(rowEffects[rowIndex], baseline, TEXT_MAIN);
            } else {
                drawCost(rowEffects[rowIndex], baseline);
            }
        }
        if (canScroll()) {
            font.getData().setScale(SCALE_PLATE);
            font.setColor(PLATE_TEXT);
            drawCentered(PLATE_UP_TEXT, PLATE_UP_X, PLATE_WIDTH);
            drawCentered(PLATE_DOWN_TEXT, PLATE_DOWN_X, PLATE_WIDTH);
            textBuilder.setLength(0);
            textBuilder.append(firstRow + 1).append('-').append(lastRow).append(" / ").append(rowCount);
            font.getData().setScale(SCALE_EFFECT);
            font.setColor(TEXT_DIM);
            glyphLayout.setText(font, textBuilder);
            font.draw(spriteBatch, textBuilder, PLATE_UP_X - ProgressionConstants.BUILD_PAGE_COUNTER_OFFSET_X, PLATE_Y + (PLATE_HEIGHT + glyphLayout.height) / 2f);
        }
        font.getData().setScale(ArtDirection.TYPE_CAPTION);
        spriteBatch.end();
    }

    /** A tag chip: the tag's fill inside a 1-unit UI_LINE border (R10). Filled pass. */
    private void drawChip(float x, float y, float width, float height, float fillRed, float fillGreen, float fillBlue) {
        UiKit.panel(shapeRenderer, x, y, width, height, ArtDirection.UI_CHIP_CHAMFER,
                fillRed, fillGreen, fillBlue, 1f,
                ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f,
                ArtDirection.UI_CHIP_BORDER_THICKNESS);
    }

    private void drawScrollPlate(float plateX) {
        UiKit.plate(shapeRenderer, plateX, PLATE_Y, PLATE_WIDTH, PLATE_HEIGHT, PlateState.IDLE,
                ArtDirection.UI_PANEL_ALPHA_SCREEN,
                ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE);
    }

    /**
     * A cost line: DAMAGE red is 4.1:1 on UI_PANEL, readable only as large text (art-direction CONTRAST), so cost
     * lines draw at TYPE_BODY while effect lines stay captions.
     */
    private void drawCost(String text, float baseline) {
        font.getData().setScale(SCALE_COST);
        font.setColor(COST_RED);
        font.draw(spriteBatch, text, EFFECT_X, baseline, EFFECT_WIDTH, Align.left, true);
    }

    private void drawEffect(String text, float baseline, Color color) {
        font.getData().setScale(SCALE_EFFECT);
        font.setColor(color);
        font.draw(spriteBatch, text, EFFECT_X, baseline, EFFECT_WIDTH, Align.left, true);
    }

    private void drawCentered(String text, float plateX, float plateWidth) {
        glyphLayout.setText(font, text);
        font.draw(spriteBatch, text, plateX + (plateWidth - glyphLayout.width) / 2f,
                PLATE_Y + (PLATE_HEIGHT + glyphLayout.height) / 2f);
    }
}

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
 */
final class BuildPagePanel {

    private enum RowKind { TAG_HEADER, PERK, KEYSTONE_GAIN, KEYSTONE_COST, KEYSTONES_HEADER }

    private static final int   MAX_ROWS = BuildTag.values().length + Perk.values().length
            + 1 + Keystone.values().length * 2;

    // Geometry (world units) — covers both inventory panels below the header.
    private static final float PANEL_X      = ItemConstants.INV_LEFT_PANEL_X;
    private static final float PANEL_WIDTH  = ItemConstants.INV_RIGHT_PANEL_X + ItemConstants.INV_RIGHT_PANEL_WIDTH
            - ItemConstants.INV_LEFT_PANEL_X;
    private static final float PANEL_BOTTOM = ItemConstants.INV_PANEL_Y_BOTTOM;
    private static final float PANEL_TOP    = ItemConstants.INV_PANEL_Y_BOTTOM + ItemConstants.INV_PANEL_HEIGHT;
    private static final float TOP_PAD      = 10f;
    private static final float FOOTER_HEIGHT = 62f;
    private static final float ROW_HEIGHT   = 38f;
    private static final float ROW_TEXT_RISE = 12f;      // baseline above the row bottom
    private static final int   VISIBLE_ROWS = (int) ((PANEL_TOP - PANEL_BOTTOM - TOP_PAD - FOOTER_HEIGHT) / ROW_HEIGHT);

    private static final float NAME_X       = PANEL_X + 40f;
    private static final float PIPS_LEFT_X  = PANEL_X + 330f;
    private static final float EFFECT_X     = PANEL_X + 440f;
    private static final float EFFECT_WIDTH = PANEL_X + PANEL_WIDTH - 20f - EFFECT_X;
    private static final float CHIP_X       = PANEL_X + 20f;
    private static final float CHIP_HEIGHT  = 26f;
    private static final float CHIP_PAD     = 14f;
    private static final float PIP_RADIUS   = 7f;
    private static final float PIP_SPACING  = 26f;

    private static final float SCALE_NAME   = 1.15f;
    private static final float SCALE_EFFECT = 0.9f;
    private static final float SCALE_CHIP   = 1.0f;
    private static final float SCALE_EMPTY  = 1.5f;
    private static final float SCALE_PLATE  = 1.2f;

    private static final float PLATE_WIDTH  = 90f;
    private static final float PLATE_HEIGHT = 46f;
    private static final float PLATE_GAP    = 12f;
    private static final float PLATE_Y      = PANEL_BOTTOM + 8f;
    private static final float PLATE_DOWN_X = PANEL_X + PANEL_WIDTH - 12f - PLATE_WIDTH;
    private static final float PLATE_UP_X   = PLATE_DOWN_X - PLATE_GAP - PLATE_WIDTH;

    private static final String EMPTY_TEXT   = "NO PERKS YET - EACH LEVEL-UP ADDS TO YOUR BUILD";
    private static final String KEYSTONES_TEXT = "KEYSTONES";
    private static final String PLATE_UP_TEXT = "UP";
    private static final String PLATE_DOWN_TEXT = "DOWN";

    private static final Color PAGE_BG       = new Color(0.071f, 0.071f, 0.086f, 1f);
    private static final Color PAGE_BORDER   = new Color(0.22f, 0.22f, 0.30f, 1f);
    private static final Color ROW_STRIPE    = new Color(1f, 1f, 1f, 0.04f);
    private static final Color TEXT_MAIN     = new Color(0.95f, 0.95f, 0.97f, 1f);
    private static final Color TEXT_DIM      = new Color(0.62f, 0.62f, 0.68f, 1f);
    private static final Color CHIP_TEXT     = new Color(0.04f, 0.04f, 0.06f, 1f);
    private static final Color PIP_EMPTY     = new Color(0.05f, 0.05f, 0.07f, 1f);
    private static final Color PIP_RING      = new Color(0.55f, 0.55f, 0.60f, 1f);
    private static final Color PLATE_BG      = new Color(0.16f, 0.14f, 0.09f, 1f);
    private static final Color PLATE_TEXT    = new Color(1.00f, 0.75f, 0.10f, 1f);
    private static final Color COST_RED      = new Color(ProgressionConstants.BUILD_COST_RED,
            ProgressionConstants.BUILD_COST_GREEN, ProgressionConstants.BUILD_COST_BLUE, 1f);
    private static final Color GOLD          = new Color(ProgressionConstants.BUILD_KEYSTONE_GOLD_RED,
            ProgressionConstants.BUILD_KEYSTONE_GOLD_GREEN, ProgressionConstants.BUILD_KEYSTONE_GOLD_BLUE, 1f);

    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;
    private final StringBuilder textBuilder = new StringBuilder(24);

    private PlayerBuild playerBuild = null;

    // Flattened rows, rebuilt by open().
    private final RowKind[] rowKinds   = new RowKind[MAX_ROWS];
    private final BuildTag[] rowTags   = new BuildTag[MAX_ROWS];
    private final String[]  rowNames   = new String[MAX_ROWS];
    private final String[]  rowEffects = new String[MAX_ROWS];
    private final int[]     rowRanks   = new int[MAX_ROWS];
    private final int[]     rowMaxRanks = new int[MAX_ROWS];
    private final float[]   rowChipWidths = new float[MAX_ROWS];
    private int rowCount   = 0;
    private int firstRow   = 0;

    BuildPagePanel(ShapeRenderer shapeRenderer, SpriteBatch spriteBatch, BitmapFont font, GlyphLayout glyphLayout) {
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch   = spriteBatch;
        this.font          = font;
        this.glyphLayout   = glyphLayout;
    }

    void setPlayerBuild(PlayerBuild playerBuild) {
        this.playerBuild = playerBuild;
    }

    /** Rebuilds the grouped row list from the build (called when the page is opened) and scrolls to the top. */
    void open() {
        rowCount = 0;
        firstRow = 0;
        if (playerBuild == null) return;
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
        font.getData().setScale(1f);
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

        // ---- filled: page, stripes, chips, pips, plates ----
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(PAGE_BG);
        shapeRenderer.rect(PANEL_X, PANEL_BOTTOM, PANEL_WIDTH, PANEL_TOP - PANEL_BOTTOM);
        for (int rowIndex = firstRow; rowIndex < lastRow; rowIndex++) {
            int visibleIndex = rowIndex - firstRow;
            float bottom = rowBottom(visibleIndex);
            if (rowKinds[rowIndex] == RowKind.PERK && (rowIndex % 2 == 0)) {
                shapeRenderer.setColor(ROW_STRIPE);
                shapeRenderer.rect(PANEL_X + 10f, bottom, PANEL_WIDTH - 20f, ROW_HEIGHT);
            }
            if (rowKinds[rowIndex] == RowKind.TAG_HEADER) {
                float chipBottom = bottom + (ROW_HEIGHT - CHIP_HEIGHT) / 2f;
                float capRadius = CHIP_HEIGHT / 2f;
                shapeRenderer.setColor(BuildTagPalette.colorOf(rowTags[rowIndex]));
                shapeRenderer.rect(CHIP_X, chipBottom, rowChipWidths[rowIndex], CHIP_HEIGHT);
                shapeRenderer.circle(CHIP_X, chipBottom + capRadius, capRadius);
                shapeRenderer.circle(CHIP_X + rowChipWidths[rowIndex], chipBottom + capRadius, capRadius);
            } else if (rowKinds[rowIndex] == RowKind.PERK) {
                Color accent = BuildTagPalette.colorOf(rowTags[rowIndex]);
                float pipY = bottom + ROW_HEIGHT / 2f;
                for (int pipIndex = 0; pipIndex < rowMaxRanks[rowIndex]; pipIndex++) {
                    shapeRenderer.setColor(pipIndex < rowRanks[rowIndex] ? accent : PIP_EMPTY);
                    shapeRenderer.circle(PIPS_LEFT_X + pipIndex * PIP_SPACING, pipY, PIP_RADIUS);
                }
            }
        }
        if (canScroll()) {
            shapeRenderer.setColor(PLATE_BG);
            shapeRenderer.rect(PLATE_UP_X, PLATE_Y, PLATE_WIDTH, PLATE_HEIGHT);
            shapeRenderer.rect(PLATE_DOWN_X, PLATE_Y, PLATE_WIDTH, PLATE_HEIGHT);
        }
        shapeRenderer.end();

        // ---- lines: border, empty pip rings, keystone frames ----
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(PAGE_BORDER);
        shapeRenderer.rect(PANEL_X, PANEL_BOTTOM, PANEL_WIDTH, PANEL_TOP - PANEL_BOTTOM);
        for (int rowIndex = firstRow; rowIndex < lastRow; rowIndex++) {
            float bottom = rowBottom(rowIndex - firstRow);
            if (rowKinds[rowIndex] == RowKind.PERK) {
                shapeRenderer.setColor(PIP_RING);
                for (int pipIndex = rowRanks[rowIndex]; pipIndex < rowMaxRanks[rowIndex]; pipIndex++) {
                    shapeRenderer.circle(PIPS_LEFT_X + pipIndex * PIP_SPACING, bottom + ROW_HEIGHT / 2f, PIP_RADIUS);
                }
            } else if (rowKinds[rowIndex] == RowKind.KEYSTONE_GAIN || rowKinds[rowIndex] == RowKind.KEYSTONE_COST) {
                // one gold frame around the two-row keystone block (drawn from its gain row)
                if (rowKinds[rowIndex] == RowKind.KEYSTONE_GAIN && rowIndex + 1 < lastRow) {
                    shapeRenderer.setColor(GOLD);
                    shapeRenderer.rect(PANEL_X + 10f, bottom - ROW_HEIGHT + 3f, PANEL_WIDTH - 20f, ROW_HEIGHT * 2f - 6f);
                }
            }
        }
        if (canScroll()) {
            shapeRenderer.setColor(PLATE_TEXT);
            shapeRenderer.rect(PLATE_UP_X, PLATE_Y, PLATE_WIDTH, PLATE_HEIGHT);
            shapeRenderer.rect(PLATE_DOWN_X, PLATE_Y, PLATE_WIDTH, PLATE_HEIGHT);
        }
        shapeRenderer.end();

        // ---- text ----
        spriteBatch.begin();
        if (rowCount == 0) {
            font.getData().setScale(SCALE_EMPTY);
            font.setColor(TEXT_DIM);
            font.draw(spriteBatch, EMPTY_TEXT, PANEL_X, (PANEL_TOP + PANEL_BOTTOM) / 2f, PANEL_WIDTH, Align.center, false);
        }
        for (int rowIndex = firstRow; rowIndex < lastRow; rowIndex++) {
            float baseline = rowBottom(rowIndex - firstRow) + ROW_TEXT_RISE + 6f;
            RowKind kind = rowKinds[rowIndex];
            if (kind == RowKind.TAG_HEADER) {
                font.getData().setScale(SCALE_CHIP);
                font.setColor(CHIP_TEXT);
                font.draw(spriteBatch, rowNames[rowIndex], CHIP_X + CHIP_PAD, baseline - 2f);
            } else if (kind == RowKind.PERK) {
                font.getData().setScale(SCALE_NAME);
                font.setColor(TEXT_MAIN);
                font.draw(spriteBatch, rowNames[rowIndex], NAME_X, baseline);
                drawEffect(rowEffects[rowIndex], baseline, TEXT_DIM);
            } else if (kind == RowKind.KEYSTONES_HEADER) {
                font.getData().setScale(SCALE_NAME);
                font.setColor(GOLD);
                font.draw(spriteBatch, rowNames[rowIndex], CHIP_X, baseline);
            } else if (kind == RowKind.KEYSTONE_GAIN) {
                font.getData().setScale(SCALE_NAME);
                font.setColor(GOLD);
                font.draw(spriteBatch, rowNames[rowIndex], NAME_X, baseline);
                drawEffect(rowEffects[rowIndex], baseline, TEXT_MAIN);
            } else {
                drawEffect(rowEffects[rowIndex], baseline, COST_RED);
            }
        }
        if (canScroll()) {
            font.getData().setScale(SCALE_PLATE);
            font.setColor(PLATE_TEXT);
            drawCentered(PLATE_UP_TEXT, PLATE_UP_X, PLATE_WIDTH);
            drawCentered(PLATE_DOWN_TEXT, PLATE_DOWN_X, PLATE_WIDTH);
            textBuilder.setLength(0);
            textBuilder.append(firstRow + 1).append('-').append(lastRow).append(" / ").append(rowCount);
            font.setColor(TEXT_DIM);
            glyphLayout.setText(font, textBuilder);
            font.draw(spriteBatch, textBuilder, PLATE_UP_X - 180f, PLATE_Y + (PLATE_HEIGHT + glyphLayout.height) / 2f);
        }
        font.getData().setScale(1f);
        spriteBatch.end();
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

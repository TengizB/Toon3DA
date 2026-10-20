package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import ge.tbegvadze.toon3d.util.ArtDirection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Session-lifetime owner of the game's one pixel font (visual-identity-order-3). ALL text in the game
 * comes from here: never {@code new BitmapFont()} anywhere else.
 *
 * <p>{@link #initialise()} (Main.create, before the World) rasterises {@link PixelFontGlyphs} into ONE
 * atlas holding both {@link FontFace}s (R3/R4). {@link #newFont(FontFace)} hands out a NEW BitmapFont
 * per caller over that shared atlas, with its own BitmapFontData, so each renderer keeps its own
 * scale and colour (R6). Those fonts do not own the atlas: disposing one never frees it. The atlas is
 * freed exactly once by {@link #dispose()} (Main.dispose, after the World). Static, not World-owned,
 * because the World is rebuilt per run and the font must outlive it (same idiom as EnemySpriteSheets).
 *
 * <p>METRIC MATCH (R5): glyph metrics are in atlas texels; each font's data carries a base scale of
 * {@code PIXEL_FONT_TARGET_CAP_HEIGHT / (cap pixels x texels per pixel)} that every setScale call is
 * multiplied by, so {@code setScale(1)} gives the old default font's cap height and every existing
 * scale constant keeps its size. Consequence: {@code getScaleX()} reports the base-inclusive scale,
 * and {@code BitmapFontData.scale(amount)} must not be used (nothing does).
 */
public final class GameFonts {

    private static final int TEXELS_PER_PIXEL = ArtDirection.PIXEL_FONT_ATLAS_TEXELS_PER_PIXEL;
    private static final int PADDING_PIXELS   = ArtDirection.PIXEL_FONT_ATLAS_PADDING_PIXELS;
    /** Font pixels from the top of a line to the cap top: line height minus cap minus descender. */
    private static final int ASCENT_PIXELS = ArtDirection.PIXEL_FONT_LINE_HEIGHT_PIXELS
            - ArtDirection.PIXEL_FONT_CAP_HEIGHT_PIXELS - ArtDirection.PIXEL_FONT_DESCENDER_PIXELS;
    private static final float BASE_SCALE = ArtDirection.PIXEL_FONT_TARGET_CAP_HEIGHT
            / (ArtDirection.PIXEL_FONT_CAP_HEIGHT_PIXELS * TEXELS_PER_PIXEL);
    private static final int FALLBACK_CODEPOINT = -1;

    /** One glyph of one face, placed in the atlas. Metrics in texels, the BitmapFont.Glyph convention. */
    private static final class PlacedGlyph {
        final int codepoint;
        final boolean[][] ink;
        final int widthPixels;
        final int heightPixels;
        final int baselineOffsetPixels;
        int atlasColumnPixels;
        int atlasRowPixels;

        PlacedGlyph(int codepoint, boolean[][] ink, int baselineOffsetPixels) {
            this.codepoint = codepoint;
            this.ink = ink;
            this.heightPixels = ink.length;
            this.widthPixels = ink[0].length;
            this.baselineOffsetPixels = baselineOffsetPixels;
        }
    }

    /** BitmapFontData whose every setScale is multiplied by the R5 base scale. */
    private static final class PixelFontData extends BitmapFont.BitmapFontData {
        @Override
        public void setScale(float scaleX, float scaleY) {
            super.setScale(scaleX * BASE_SCALE, scaleY * BASE_SCALE);
        }
    }

    private static final Map<FontFace, List<PlacedGlyph>> FACE_GLYPHS = new EnumMap<>(FontFace.class);
    private static final Map<FontFace, PlacedGlyph> FACE_FALLBACK = new EnumMap<>(FontFace.class);

    private static Texture atlas;
    private static TextureRegion atlasRegion;

    private GameFonts() { }

    /** Builds the atlas and both faces. Idempotent; called once by Main.create() before the World. */
    public static void initialise() {
        if (atlas != null) return;
        List<PlacedGlyph> everyGlyph = new ArrayList<>();
        for (FontFace face : FontFace.values()) {
            List<PlacedGlyph> faceGlyphs = new ArrayList<>();
            for (PixelFontGlyphs.GlyphRow row : PixelFontGlyphs.ALL) {
                faceGlyphs.add(new PlacedGlyph(row.codepoint, deriveFace(parseRows(row.rows), face),
                        row.baselineOffsetPixels));
            }
            PixelFontGlyphs.GlyphRow fallbackRow = PixelFontGlyphs.FALLBACK;
            PlacedGlyph fallback = new PlacedGlyph(FALLBACK_CODEPOINT,
                    deriveFace(parseRows(fallbackRow.rows), face), fallbackRow.baselineOffsetPixels);
            FACE_GLYPHS.put(face, faceGlyphs);
            FACE_FALLBACK.put(face, fallback);
            everyGlyph.addAll(faceGlyphs);
            everyGlyph.add(fallback);
        }
        int atlasHeightPixels = packShelves(everyGlyph);
        atlas = rasterise(everyGlyph, atlasHeightPixels);
        atlasRegion = new TextureRegion(atlas);

        if (Gdx.app != null) {
            BitmapFont probe = newFont(FontFace.REGULAR);
            Gdx.app.log("GameFonts", "pixel font atlas " + atlas.getWidth() + "x" + atlas.getHeight()
                    + ", REGULAR capHeight at setScale(1) = " + probe.getCapHeight()
                    + ", target PIXEL_FONT_TARGET_CAP_HEIGHT = " + ArtDirection.PIXEL_FONT_TARGET_CAP_HEIGHT
                    + ", owns atlas = " + probe.ownsTexture());
            probe.dispose();
        }
    }

    /**
     * A new font of the given face over the shared atlas, at setScale(1) (R6). The caller owns and
     * disposes the BitmapFont; disposing it never frees the atlas.
     */
    public static BitmapFont newFont(FontFace face) {
        if (atlas == null) {
            throw new IllegalStateException(
                    "GameFonts.newFont(" + face + ") called before GameFonts.initialise(): "
                    + "Main.create() must initialise the font before anything builds a renderer");
        }
        PixelFontData data = new PixelFontData();
        data.flipped = false;
        data.imagePaths = new String[0];
        data.lineHeight = ArtDirection.PIXEL_FONT_LINE_HEIGHT_PIXELS * TEXELS_PER_PIXEL;
        data.capHeight = ArtDirection.PIXEL_FONT_CAP_HEIGHT_PIXELS * TEXELS_PER_PIXEL;
        data.xHeight = ArtDirection.PIXEL_FONT_X_HEIGHT_PIXELS * TEXELS_PER_PIXEL;
        data.ascent = ASCENT_PIXELS * TEXELS_PER_PIXEL;
        data.descent = -ArtDirection.PIXEL_FONT_DESCENDER_PIXELS * TEXELS_PER_PIXEL;
        data.down = -data.lineHeight;
        data.spaceXadvance = ArtDirection.PIXEL_FONT_SPACE_ADVANCE_PIXELS * TEXELS_PER_PIXEL;

        for (PlacedGlyph placed : FACE_GLYPHS.get(face)) {
            data.setGlyph(placed.codepoint, toGlyph(placed));
        }
        BitmapFont.Glyph space = new BitmapFont.Glyph();
        space.id = ' ';
        space.xadvance = (int) data.spaceXadvance;
        data.setGlyph(' ', space);
        data.missingGlyph = toGlyph(FACE_FALLBACK.get(face));

        data.setScale(1f);
        BitmapFont font = new BitmapFont(data, atlasRegion, true);
        data.setGlyphRegion(data.missingGlyph, atlasRegion);
        return font;
    }

    /** Frees the atlas exactly once. Called by Main.dispose() after the World is disposed. */
    public static void dispose() {
        if (atlas != null) {
            atlas.dispose();
            atlas = null;
            atlasRegion = null;
        }
        FACE_GLYPHS.clear();
        FACE_FALLBACK.clear();
    }

    /** Atlas width in texels (0 before initialise). */
    static int getAtlasWidth() {
        return atlas == null ? 0 : atlas.getWidth();
    }

    /** Atlas height in texels (0 before initialise). */
    static int getAtlasHeight() {
        return atlas == null ? 0 : atlas.getHeight();
    }

    private static BitmapFont.Glyph toGlyph(PlacedGlyph placed) {
        BitmapFont.Glyph glyph = new BitmapFont.Glyph();
        glyph.id = placed.codepoint;
        glyph.srcX = placed.atlasColumnPixels * TEXELS_PER_PIXEL;
        glyph.srcY = placed.atlasRowPixels * TEXELS_PER_PIXEL;
        glyph.width = placed.widthPixels * TEXELS_PER_PIXEL;
        glyph.height = placed.heightPixels * TEXELS_PER_PIXEL;
        glyph.xoffset = 0;
        // Glyph bottom relative to the line top (y + ascent): baseline sits ascent + cap below it.
        glyph.yoffset = (placed.baselineOffsetPixels - ASCENT_PIXELS
                - ArtDirection.PIXEL_FONT_CAP_HEIGHT_PIXELS) * TEXELS_PER_PIXEL;
        glyph.xadvance = (placed.widthPixels + ArtDirection.PIXEL_FONT_LETTER_SPACING_PIXELS) * TEXELS_PER_PIXEL;
        glyph.page = 0;
        return glyph;
    }

    private static boolean[][] parseRows(String rows) {
        String[] rowStrings = rows.split("/");
        boolean[][] ink = new boolean[rowStrings.length][rowStrings[0].length()];
        for (int rowIndex = 0; rowIndex < rowStrings.length; rowIndex++) {
            for (int columnIndex = 0; columnIndex < rowStrings[rowIndex].length(); columnIndex++) {
                ink[rowIndex][columnIndex] = rowStrings[rowIndex].charAt(columnIndex) == '#';
            }
        }
        return ink;
    }

    /** R3: OR the glyph with itself shifted right by the face's shift; width grows by the shift. */
    private static boolean[][] deriveFace(boolean[][] regular, FontFace face) {
        int shift = face.getHorizontalShiftPixels();
        if (shift == 0) return regular;
        int width = regular[0].length + shift;
        boolean[][] derived = new boolean[regular.length][width];
        for (int rowIndex = 0; rowIndex < regular.length; rowIndex++) {
            for (int columnIndex = 0; columnIndex < width; columnIndex++) {
                for (int offset = 0; offset <= shift; offset++) {
                    int sourceColumn = columnIndex - offset;
                    if (sourceColumn >= 0 && sourceColumn < regular[rowIndex].length
                            && regular[rowIndex][sourceColumn]) {
                        derived[rowIndex][columnIndex] = true;
                    }
                }
            }
        }
        return derived;
    }

    /**
     * Shelf-packs every glyph (tallest first) into an atlas MAX_SIDE texels wide, with PADDING pixels
     * between glyphs and around the edge. Returns the power-of-two atlas height in font pixels.
     */
    private static int packShelves(List<PlacedGlyph> glyphs) {
        int maxSidePixels = ArtDirection.PIXEL_FONT_ATLAS_MAX_SIDE_TEXELS / TEXELS_PER_PIXEL;
        List<PlacedGlyph> byHeight = new ArrayList<>(glyphs);
        byHeight.sort(Comparator.comparingInt((PlacedGlyph glyph) -> glyph.heightPixels).reversed());
        int cursorColumn = PADDING_PIXELS;
        int shelfTop = PADDING_PIXELS;
        int shelfHeight = 0;
        for (PlacedGlyph glyph : byHeight) {
            if (cursorColumn + glyph.widthPixels + PADDING_PIXELS > maxSidePixels) {
                shelfTop += shelfHeight + PADDING_PIXELS;
                cursorColumn = PADDING_PIXELS;
                shelfHeight = 0;
            }
            glyph.atlasColumnPixels = cursorColumn;
            glyph.atlasRowPixels = shelfTop;
            cursorColumn += glyph.widthPixels + PADDING_PIXELS;
            shelfHeight = Math.max(shelfHeight, glyph.heightPixels);
        }
        int usedHeightPixels = shelfTop + shelfHeight + PADDING_PIXELS;
        int atlasHeightPixels = 1;
        while (atlasHeightPixels < usedHeightPixels) atlasHeightPixels *= 2;
        if (atlasHeightPixels > maxSidePixels) {
            throw new IllegalStateException("pixel font atlas needs " + usedHeightPixels * TEXELS_PER_PIXEL
                    + " texels of height, over PIXEL_FONT_ATLAS_MAX_SIDE_TEXELS");
        }
        return atlasHeightPixels;
    }

    /** Draws every ink pixel as a solid white TEXELS x TEXELS square (R4); the Pixmap is freed after upload. */
    private static Texture rasterise(List<PlacedGlyph> glyphs, int atlasHeightPixels) {
        Pixmap pixmap = new Pixmap(ArtDirection.PIXEL_FONT_ATLAS_MAX_SIDE_TEXELS,
                atlasHeightPixels * TEXELS_PER_PIXEL, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(1f, 1f, 1f, 1f);
        for (PlacedGlyph glyph : glyphs) {
            for (int rowIndex = 0; rowIndex < glyph.heightPixels; rowIndex++) {
                for (int columnIndex = 0; columnIndex < glyph.widthPixels; columnIndex++) {
                    if (!glyph.ink[rowIndex][columnIndex]) continue;
                    pixmap.fillRectangle((glyph.atlasColumnPixels + columnIndex) * TEXELS_PER_PIXEL,
                            (glyph.atlasRowPixels + rowIndex) * TEXELS_PER_PIXEL,
                            TEXELS_PER_PIXEL, TEXELS_PER_PIXEL);
                }
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }
}

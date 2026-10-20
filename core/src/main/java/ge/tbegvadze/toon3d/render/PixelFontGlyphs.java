package ge.tbegvadze.toon3d.render;

/**
 * The glyph table of the game's one pixel font (visual-identity-order-3, R1/R2). DATA ONLY:
 * render/GameFonts turns it into the atlas and both faces.
 *
 * <p>One row per glyph: its codepoint, its rows as a string ('#' ink, '.' empty, '/' between rows,
 * TOP row first) and its baseline offset — the font-pixel row its BOTTOM row sits on, relative to
 * the baseline (0 = on the baseline, negative = a descender, positive = raised punctuation). Every
 * row of a glyph has the same length, which is the glyph's ink width.
 *
 * <p>Grid (R1): cap height 7, x-height 5, descender 2. Capitals 5 wide (I 3, M and W 7), digits all 5
 * wide (TABULAR — HUD counters never jitter), the zero slashed so it never reads as O. The space is
 * not a row here: its advance is {@code ArtDirection.PIXEL_FONT_SPACE_ADVANCE_PIXELS}.
 *
 * <p>ADD A GLYPH: one {@code glyph(...)} line below, and its character in docs/art-direction.txt
 * TYPOGRAPHY → coverage. Nothing else changes; the atlas sizes itself.
 */
public final class PixelFontGlyphs {

    /** One glyph's data. */
    public static final class GlyphRow {
        public final int codepoint;
        public final int baselineOffsetPixels;
        public final String rows;

        GlyphRow(int codepoint, int baselineOffsetPixels, String rows) {
            this.codepoint = codepoint;
            this.baselineOffsetPixels = baselineOffsetPixels;
            this.rows = rows;
        }
    }

    private PixelFontGlyphs() { }

    private static GlyphRow glyph(int codepoint, int baselineOffsetPixels, String rows) {
        return new GlyphRow(codepoint, baselineOffsetPixels, rows);
    }

    /** Drawn for any character not in {@link #ALL} (R2): a hollow 5x7 box — visible, never blank. */
    public static final GlyphRow FALLBACK = glyph(0, 0, "#####/#...#/#...#/#...#/#...#/#...#/#####");

    public static final GlyphRow[] ALL = {
        // ---- capitals (5x7, square-shouldered; A round-topped so it never reads as R at small scale) ----
        glyph('A', 0, ".###./#...#/#...#/#####/#...#/#...#/#...#"),
        glyph('B', 0, "####./#...#/#...#/####./#...#/#...#/####."),
        glyph('C', 0, "#####/#..../#..../#..../#..../#..../#####"),
        glyph('D', 0, "####./#...#/#...#/#...#/#...#/#...#/####."),
        glyph('E', 0, "#####/#..../#..../####./#..../#..../#####"),
        glyph('F', 0, "#####/#..../#..../####./#..../#..../#...."),
        glyph('G', 0, "#####/#..../#..../#.###/#...#/#...#/#####"),
        glyph('H', 0, "#...#/#...#/#...#/#####/#...#/#...#/#...#"),
        glyph('I', 0, "###/.#./.#./.#./.#./.#./###"),
        glyph('J', 0, "....#/....#/....#/....#/#...#/#...#/#####"),
        glyph('K', 0, "#...#/#..#./#.#../##.../#.#../#..#./#...#"),
        glyph('L', 0, "#..../#..../#..../#..../#..../#..../#####"),
        glyph('M', 0, "#######/#..#..#/#..#..#/#..#..#/#.....#/#.....#/#.....#"),
        glyph('N', 0, "#...#/##..#/##..#/#.#.#/#..##/#..##/#...#"),
        glyph('O', 0, "#####/#...#/#...#/#...#/#...#/#...#/#####"),
        glyph('P', 0, "#####/#...#/#...#/#####/#..../#..../#...."),
        glyph('Q', 0, "#####/#...#/#...#/#...#/#.#.#/#..#./###.#"),
        glyph('R', 0, "#####/#...#/#...#/#####/#.#../#..#./#...#"),
        glyph('S', 0, "#####/#..../#..../#####/....#/....#/#####"),
        glyph('T', 0, "#####/..#../..#../..#../..#../..#../..#.."),
        glyph('U', 0, "#...#/#...#/#...#/#...#/#...#/#...#/#####"),
        glyph('V', 0, "#...#/#...#/#...#/#...#/#...#/.#.#./..#.."),
        glyph('W', 0, "#.....#/#.....#/#.....#/#..#..#/#..#..#/#..#..#/#######"),
        glyph('X', 0, "#...#/#...#/.#.#./..#../.#.#./#...#/#...#"),
        glyph('Y', 0, "#...#/#...#/.#.#./..#../..#../..#../..#.."),
        glyph('Z', 0, "#####/....#/...#./..#../.#.../#..../#####"),

        // ---- lowercase (x-height 5, ascenders/descenders) ----
        glyph('a', 0, "####./....#/.####/#...#/.####"),
        glyph('b', 0, "#..../#..../####./#...#/#...#/#...#/####."),
        glyph('c', 0, ".####/#..../#..../#..../.####"),
        glyph('d', 0, "....#/....#/.####/#...#/#...#/#...#/.####"),
        glyph('e', 0, ".###./#...#/#####/#..../.####"),
        glyph('f', 0, "..##/.#../####/.#../.#../.#../.#.."),
        glyph('g', -2, ".####/#...#/#...#/#...#/.####/....#/####."),
        glyph('h', 0, "#..../#..../####./#...#/#...#/#...#/#...#"),
        glyph('i', 0, "#/./#/#/#/#/#"),
        glyph('j', -2, "..#/.../..#/..#/..#/..#/..#/..#/##."),
        glyph('k', 0, "#.../#.../#..#/#.#./##../#.#./#..#"),
        glyph('l', 0, "#./#./#./#./#./#./.#"),
        glyph('m', 0, "##.#./#.#.#/#.#.#/#.#.#/#.#.#"),
        glyph('n', 0, "####./#...#/#...#/#...#/#...#"),
        glyph('o', 0, ".###./#...#/#...#/#...#/.###."),
        glyph('p', -2, "####./#...#/#...#/#...#/####./#..../#...."),
        glyph('q', -2, ".####/#...#/#...#/#...#/.####/....#/....#"),
        glyph('r', 0, "#.##/##../#.../#.../#..."),
        glyph('s', 0, ".####/#..../.###./....#/####."),
        glyph('t', 0, ".#../.#../####/.#../.#../.#../..##"),
        glyph('u', 0, "#...#/#...#/#...#/#...#/.####"),
        glyph('v', 0, "#...#/#...#/#...#/.#.#./..#.."),
        glyph('w', 0, "#...#/#...#/#.#.#/#.#.#/.#.#."),
        glyph('x', 0, "#...#/.#.#./..#../.#.#./#...#"),
        glyph('y', -2, "#...#/#...#/#...#/#...#/.####/....#/####."),
        glyph('z', 0, "#####/...#./..#../.#.../#####"),

        // ---- digits (TABULAR: all 5 wide) ----
        glyph('0', 0, "#####/#...#/#..##/#.#.#/##..#/#...#/#####"),
        glyph('1', 0, "..#../.##../#.#../..#../..#../..#../#####"),
        glyph('2', 0, "#####/....#/....#/#####/#..../#..../#####"),
        glyph('3', 0, "#####/....#/....#/.####/....#/....#/#####"),
        glyph('4', 0, "#...#/#...#/#...#/#####/....#/....#/....#"),
        glyph('5', 0, "#####/#..../####./....#/....#/#...#/.###."),
        glyph('6', 0, "#####/#..../#..../#####/#...#/#...#/#####"),
        glyph('7', 0, "#####/....#/...#./..#../..#../..#../..#.."),
        glyph('8', 0, "#####/#...#/#...#/.###./#...#/#...#/#####"),
        glyph('9', 0, "#####/#...#/#...#/#####/....#/....#/#####"),

        // ---- ASCII punctuation ----
        glyph('!', 0, "#/#/#/#/#/./#"),
        glyph('"', 5, "#.#/#.#"),
        glyph('#', 0, ".#.#./.#.#./#####/.#.#./#####/.#.#./.#.#."),
        glyph('$', 0, "..#../.####/#.#../.###./..#.#/####./..#.."),
        glyph('%', 0, "##..#/##.#./...#./..#../.#.../.#.##/#..##"),
        glyph('&', 0, ".##../#..#./#.#../.#.../#.#.#/#..#./.##.#"),
        glyph('\'', 5, "#/#"),
        glyph('(', 0, ".#/#./#./#./#./#./.#"),
        glyph(')', 0, "#./.#/.#/.#/.#/.#/#."),
        glyph('*', 1, "#.#.#/.###./#####/.###./#.#.#"),
        glyph('+', 1, ".#./###/.#."),
        glyph(',', -1, ".#/#."),
        glyph('-', 2, "###"),
        glyph('.', 0, "#"),
        glyph('/', 0, "....#/...#./...#./..#../.#.../.#.../#...."),
        glyph(':', 0, "#/./././#"),
        glyph(';', -1, ".#/../../.#/#."),
        glyph('<', 0, "..#/.#./#../.#./..#"),
        glyph('=', 1, "###/.../###"),
        glyph('>', 0, "#../.#./..#/.#./#.."),
        glyph('?', 0, "####./....#/....#/..##./..#../...../..#.."),
        glyph('@', 0, "#####/#...#/#.###/#.#.#/#.###/#..../#####"),
        glyph('[', 0, "##/#./#./#./#./#./##"),
        glyph('\\', 0, "#..../.#.../.#.../..#../...#./...#./....#"),
        glyph(']', 0, "##/.#/.#/.#/.#/.#/##"),
        glyph('^', 5, ".#./#.#"),
        glyph('_', -1, "#####"),
        glyph('`', 5, "#./.#"),
        glyph('{', 0, "..#/.#./.#./#../.#./.#./..#"),
        glyph('|', -2, "#/#/#/#/#/#/#/#/#"),
        glyph('}', 0, "#../.#./.#./..#/.#./.#./#.."),
        glyph('~', 3, ".##../#..##"),

        // ---- R2 extras (scanned from every shipped string, 2026-10-19) ----
        glyph(0x2014, 2, "######"),                                   // — em dash: 6 wide, centred on x-height
        glyph(0x00B7, 2, "##/##"),                                    // · middle dot
        glyph(0x2026, 0, "#.#.#"),                                    // … ellipsis
        glyph(0x00D7, 0, "#...#/.#.#./..#../.#.#./#...#"),            // × multiplication sign
        glyph(0x25B6, 0, "#.../##../###./####/###./##../#..."),       // ▶ solid triangle
        glyph(0x25C0, 0, "...#/..##/.###/####/.###/..##/...#"),       // ◀ solid triangle
        glyph(0x2192, 0, "..#../...#./#####/...#./..#.."),            // → arrow
        glyph(0x25CF, 0, ".###./#####/#####/#####/.###."),            // ● 5x5 disc
        glyph(0x2022, 1, "###/###/###"),                              // • bullet
        glyph(0x25C7, 0, "..#../.#.#./#...#/.#.#./..#.."),            // ◇ 5x5 diamond outline
        glyph(0x00B1, 0, "..#../..#../#####/..#../..#../...../#####"), // ± plus-minus
        glyph(0x2248, 1, ".##../#..##/...../.##../#..##"),            // ≈ almost equal
        glyph(0x00B0, 4, ".#./#.#/.#."),                              // ° degree
        glyph(0x2018, 5, ".#/##"),                                    // ‘ left single quote
        glyph(0x2019, 5, "##/#."),                                    // ’ right single quote
        glyph(0x201C, 5, ".#.#/####"),                                // “ left double quote
        glyph(0x201D, 5, "####/#.#."),                                // ” right double quote
    };
}

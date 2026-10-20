package ge.tbegvadze.toon3d.render;

/**
 * The two faces of the pixel font (visual-identity-order-3, R3). HEAVY is derived from REGULAR in
 * code by OR-ing every glyph with itself shifted right; the per-constant data is that shift.
 */
public enum FontFace {
    REGULAR(0),
    HEAVY(1);

    private final int horizontalShiftPixels;

    FontFace(int horizontalShiftPixels) {
        this.horizontalShiftPixels = horizontalShiftPixels;
    }

    /** Font pixels each glyph is OR-ed with itself shifted right; ink width and advance grow by this. */
    public int getHorizontalShiftPixels() {
        return horizontalShiftPixels;
    }
}

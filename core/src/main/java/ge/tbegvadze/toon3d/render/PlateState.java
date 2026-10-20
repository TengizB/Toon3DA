package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.util.ArtDirection;

/**
 * The four states of a tappable plate (visual-identity-order-4, R3). Each constant carries its fill and
 * border choice as data, so {@link UiKit#plate} holds no switch.
 */
public enum PlateState {
    /** Fill UI_PANEL at the caller's alpha, border UI_LINE. */
    IDLE(false, 1f, false, 1f, false),
    /** Fill UI_PANEL_RAISED at the caller's (pressed) alpha, border in the ink at full alpha. */
    PRESSED(true, 1f, true, 1f, false),
    /** Fill UI_PANEL_RAISED, border in the ink, plus brackets in the ink. */
    SELECTED(true, 1f, true, 1f, true),
    /** Fill UI_PANEL and border UI_LINE, both at half alpha. */
    DISABLED(false, ArtDirection.UI_PLATE_DISABLED_ALPHA_SCALE, false,
            ArtDirection.UI_PLATE_DISABLED_ALPHA_SCALE, false);

    private final boolean raisedFill;
    private final float fillAlphaScale;
    private final boolean inkBorder;
    private final float borderAlpha;
    private final boolean inkBrackets;

    PlateState(boolean raisedFill, float fillAlphaScale, boolean inkBorder, float borderAlpha, boolean inkBrackets) {
        this.raisedFill = raisedFill;
        this.fillAlphaScale = fillAlphaScale;
        this.inkBorder = inkBorder;
        this.borderAlpha = borderAlpha;
        this.inkBrackets = inkBrackets;
    }

    public float fillRed()   { return raisedFill ? ArtDirection.UI_PANEL_RAISED_RED   : ArtDirection.UI_PANEL_RED; }
    public float fillGreen() { return raisedFill ? ArtDirection.UI_PANEL_RAISED_GREEN : ArtDirection.UI_PANEL_GREEN; }
    public float fillBlue()  { return raisedFill ? ArtDirection.UI_PANEL_RAISED_BLUE  : ArtDirection.UI_PANEL_BLUE; }
    public float fillAlphaScale() { return fillAlphaScale; }
    public boolean usesInkBorder() { return inkBorder; }
    public float borderAlpha() { return borderAlpha; }
    public boolean drawsInkBrackets() { return inkBrackets; }
}

package ge.tbegvadze.toon3d.util;

/**
 * The player-facing RESOLUTION knob — how many ray columns the first-person 3D view casts, and
 * how large the floor/ceiling backdrop it is painted onto is.
 *
 * <p><b>This is not a viewport or window resolution.</b> World units stay
 * {@code Constants.WORLD_WIDTH × Constants.WORLD_HEIGHT} (1280×720, (0,0) = bottom-left, Y-up)
 * everywhere — every HUD panel, touch button and menu is authored in those units and none of that
 * changes. The device already renders at native pixels, so 2D UI is already sharp. What this knob
 * actually controls is the <b>3D view's internal resolution</b>: {@code WallRenderer} casts one ray
 * per projection column and stripes the screen with the result, and {@code FloorCeilingRenderer}
 * fills a small backdrop texture that gets stretched across the screen. Raising the column count
 * (and the backdrop it feeds) raises the fidelity of that raycast image, independent of the fixed
 * 1280×720 world/UI space it is composited into.
 *
 * <p>At {@link #HD} the dimensions are the historic fixed ones
 * (1280 ray columns, floor backdrop 320×180) - the performance tier.
 * {@link #FULL_HD} raises the column count to 1920 and the floor backdrop to 960×540 (divisor 2) - the
 * quality tier and the default.
 *
 * <p>Headless: no LibGDX imports. Persisted by ordinal — see
 * {@code RenderConstants.RENDER_RESOLUTION_SETTING_KEY}; a stored ordinal outside this enum's range
 * (a downgrade, a corrupt save) falls back to {@link #HD} via {@link #fromOrdinal(int)}.
 */
public enum RenderResolution {

    HD(RenderConstants.RENDER_RESOLUTION_HD_COLUMNS, RenderConstants.FLOOR_BACKDROP_SCALE_DIVISOR,
       "story.codex.setting.resolution.0"),
    FULL_HD(RenderConstants.RENDER_RESOLUTION_FULL_HD_COLUMNS, RenderConstants.FLOOR_BACKDROP_SCALE_DIVISOR_FULL_HD,
       "story.codex.setting.resolution.1");

    /**
     * The tier a save gets until the player picks one (owner decision, 2026-10-20): FULL HD, so the floor and
     * ceiling backdrop and every billboard are drawn at the finer grid out of the box. Also the fallback for a
     * persisted ordinal that no longer exists.
     */
    public static final RenderResolution DEFAULT = FULL_HD;

    private final int    projectionColumnCount;
    private final int    floorBackdropScaleDivisor;
    private final String labelStringId;

    RenderResolution(int projectionColumnCount, int floorBackdropScaleDivisor, String labelStringId) {
        this.projectionColumnCount = projectionColumnCount;
        this.floorBackdropScaleDivisor = floorBackdropScaleDivisor;
        this.labelStringId         = labelStringId;
    }

    /*
     * Formula: ray projection column count per resolution tier
     * Derivation: HD reuses the existing fixed 1:1 column-per-world-unit projection
     *   (RenderConstants.RENDER_RESOLUTION_HD_COLUMNS = Constants.WORLD_WIDTH = 1280).
     *   FULL_HD raises it to RenderConstants.RENDER_RESOLUTION_FULL_HD_COLUMNS = 1920 (1.5x HD),
     *   independent of the fixed 1280-world-unit screen it is stretched across.
     * Edge cases: none — both values are compile-time constants, never zero or negative.
     */
    public int getProjectionColumnCount() {
        return projectionColumnCount;
    }

    /** This tier's backdrop divisor: HD 4 (performance tier), FULL_HD 2 (quality tier). */
    public int getFloorBackdropScaleDivisor() {
        return floorBackdropScaleDivisor;
    }

    /*
     * Formula: floor/ceiling backdrop width
     * Derivation: the backdrop is a fixed 1:divisor fraction of the projection column count, where the
     *   divisor is carried per tier (HD 4, FULL_HD 2):
     *     backdropWidth = projectionColumnCount / floorBackdropScaleDivisor
     *   HD: 1280 / 4 = 320. FULL_HD: 1920 / 2 = 960. Both divide evenly.
     * Edge cases: relies on projectionColumnCount being an exact multiple of the tier's divisor
     *   (true for both); a future tier must preserve that.
     */
    public int getFloorBackdropWidth() {
        return getProjectionColumnCount() / floorBackdropScaleDivisor;
    }

    /*
     * Formula: floor/ceiling backdrop height
     * Derivation: the backdrop keeps the world's 16:9 aspect ratio scaled by the same column ratio
     *   the width formula uses, computed with integer math in an order that stays exact for both
     *   tiers:
     *     backdropHeight = (Constants.WORLD_HEIGHT * projectionColumnCount / Constants.WORLD_WIDTH)
     *                      / floorBackdropScaleDivisor
     *   HD:      (720 * 1280 / 1280) / 4 = 720 / 4 = 180.
     *   FULL_HD: (720 * 1920 / 1280) / 2 = 1080 / 2 = 540.
     * Edge cases: the inner multiply-then-divide must happen before the outer divide (as written)
     *   so both tiers land on exact integers with no truncation error; a future tier must verify the
     *   same exactness before shipping.
     */
    public int getFloorBackdropHeight() {
        return (Constants.WORLD_HEIGHT * getProjectionColumnCount() / Constants.WORLD_WIDTH)
                / floorBackdropScaleDivisor;
    }

    /** Localisation id of this tier's label on the settings strip ("HD" / "FULL HD"). */
    public String getLabelStringId() {
        return labelStringId;
    }

    /** Cycles HD -&gt; FULL_HD -&gt; HD, for a tap-to-cycle settings button. */
    public RenderResolution next() {
        RenderResolution[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** A persisted ordinal outside this enum's range (a downgrade, a corrupt save) falls back to HD. */
    public static RenderResolution fromOrdinal(int ordinal) {
        RenderResolution[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return DEFAULT;
        }
        return values[ordinal];
    }
}

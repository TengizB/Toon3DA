package ge.tbegvadze.toon3d.util;

/**
 * The player-facing RESOLUTION knob — how many ray columns the first-person 3D view casts, and
 * how many floor/ceiling rows it is painted onto.
 *
 * <p><b>This is not a viewport or window resolution.</b> World units stay
 * {@code Constants.WORLD_WIDTH × Constants.WORLD_HEIGHT} (1280×720, (0,0) = bottom-left, Y-up)
 * everywhere — every HUD panel, touch button and menu is authored in those units and none of that
 * changes. The device already renders at native pixels, so 2D UI is already sharp. What this knob
 * actually controls is the <b>3D view's internal resolution</b>: {@code WallRenderer} casts one ray
 * per projection column and stripes the screen with the result, and {@code FloorCeilingRenderer}
 * cuts the view into horizontal rows drawn as GPU-sampled spans. Raising the column count (and the
 * row count) raises the fidelity of that raycast image, independent of the fixed 1280×720 world/UI
 * space it is composited into.
 *
 * <p>At {@link #HD} the dimensions are the historic fixed ones
 * (1280 ray columns, 180 floor/ceiling rows) - the lighter tier.
 * {@link #FULL_HD} raises the column count to 1920 and the rows to 540 - the quality tier and the default.
 *
 * <p>Headless: no LibGDX imports. Persisted by ordinal — see
 * {@code RenderConstants.RENDER_RESOLUTION_SETTING_KEY}; a stored ordinal outside this enum's range
 * (a downgrade, a corrupt save) falls back to {@link #DEFAULT} via {@link #fromOrdinal(int)}.
 */
public enum RenderResolution {

    HD(RenderConstants.RENDER_RESOLUTION_HD_COLUMNS, RenderConstants.RENDER_RESOLUTION_HD_FLOOR_CEILING_ROWS,
       "story.codex.setting.resolution.0"),
    FULL_HD(RenderConstants.RENDER_RESOLUTION_FULL_HD_COLUMNS,
       RenderConstants.RENDER_RESOLUTION_FULL_HD_FLOOR_CEILING_ROWS, "story.codex.setting.resolution.1");

    /**
     * The tier a save gets until the player picks one (owner decision, 2026-10-20): FULL HD, so the floor and
     * ceiling backdrop and every billboard are drawn at the finer grid out of the box. Also the fallback for a
     * persisted ordinal that no longer exists.
     */
    public static final RenderResolution DEFAULT = FULL_HD;

    private final int    projectionColumnCount;
    private final int    floorCeilingRowCount;
    private final String labelStringId;

    RenderResolution(int projectionColumnCount, int floorCeilingRowCount, String labelStringId) {
        this.projectionColumnCount = projectionColumnCount;
        this.floorCeilingRowCount  = floorCeilingRowCount;
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

    /**
     * Full-screen rows the floor/ceiling span renderer cuts the view into (half floor, half mirrored ceiling):
     * HD 180, FULL_HD 540 - the historic backdrop heights, so the vertical look is unchanged. This is the only
     * floor quantity a tier carries: the spans are sampled at native horizontal resolution, so there is no
     * backdrop width any more (render-performance-full-hd R9). Both counts are even (no exact-horizon row).
     */
    public int getFloorCeilingRowCount() {
        return floorCeilingRowCount;
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

    /** A persisted ordinal outside this enum's range (a downgrade, a corrupt save) falls back to {@link #DEFAULT}. */
    public static RenderResolution fromOrdinal(int ordinal) {
        RenderResolution[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return DEFAULT;
        }
        return values[ordinal];
    }
}

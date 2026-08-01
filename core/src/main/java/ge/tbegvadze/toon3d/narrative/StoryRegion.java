package ge.tbegvadze.toon3d.narrative;

/**
 * The five STORY regions of the descent (story/05-descent-structure.md).  The story is a linear
 * descent even though the game is a permadeath roguelike on a branching route map, so this enum is
 * the axis every narrative pool is gated on — never the current run, never the current floor.
 *
 * <p>ROGUELIKE STORY-GATING (Story UI order-2, model owned by order-7 Part A): barks are gated by
 * the DEEPEST story region ever reached ({@link StoryProgress}), which is PERSISTENT.  Death and
 * reprint never rewind the story: ORA's tone, the planet's voice stage and the Organization's
 * escalation only ever move forward.
 *
 * <p>The planet's voice STAGE is this enum in disguise — silence in {@link #HABITATION_RINGS},
 * whispers in {@link #HARVESTING_GALLERIES}, address in {@link #RELIQUARY}, communion from
 * {@link #WOUND} down (story/dialog/planet-voice.md).  A pool row simply declares the region band
 * it belongs to and the gate does the rest.
 *
 * <p>Headless — no LibGDX imports, so gating is unit-testable without a render context.
 */
public enum StoryRegion {

    /** Region 1 — belief; clean good vs evil.  The planet is silent here. */
    HABITATION_RINGS("Habitation Rings"),
    /** Region 2 — the first crack.  The planet whispers single wrong-language words. */
    HARVESTING_GALLERIES("Harvesting Galleries"),
    /** Region 3 — the frame inverts.  The planet addresses you by deed. */
    RELIQUARY("The Reliquary"),
    /** Region 4 — full inversion.  Near communion; ORA drops the corporate words. */
    WOUND("The Wound"),
    /** Region 5 — everything; the ending choice. */
    CORE("The Core");

    private final String displayName;

    StoryRegion(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable region name (debug/logging only — player-facing text is a string id). */
    public String getDisplayName() {
        return displayName;
    }

    /** True when this region is at least as deep as {@code other} (the gating comparison). */
    public boolean isAtLeast(StoryRegion other) {
        return ordinal() >= other.ordinal();
    }

    /** True when this region lies inside the inclusive band {@code [first, last]}. */
    public boolean isWithin(StoryRegion first, StoryRegion last) {
        return ordinal() >= first.ordinal() && ordinal() <= last.ordinal();
    }

    /** The deeper of two regions — the "story never rewinds" combinator. */
    public static StoryRegion deeperOf(StoryRegion first, StoryRegion second) {
        if (first  == null) return second;
        if (second == null) return first;
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    /**
     * Maps a ROUTE-map region index (route/RouteRegion.regionIndex, 0-based act band) onto a story
     * region.  The route map's fixed acts line up one-for-one with the story's first bands, and the
     * endlessly-appended BREACH bands beyond them all read as the deepest story stage — the descent
     * can go further than the story does, but the story must never run out.  Out-of-range indices
     * clamp instead of throwing, so a route change can never crash the narrative layer.
     *
     * <p>Order-7 owns the full route→story mapping (including the Core's final-node guard); this is
     * the depth-monotone version order-2 gates on.
     */
    public static StoryRegion fromRouteRegionIndex(int routeRegionIndex) {
        StoryRegion[] regions = values();
        if (routeRegionIndex <= 0) return regions[0];
        if (routeRegionIndex >= regions.length) return regions[regions.length - 1];
        return regions[routeRegionIndex];
    }

    /** Restores a region from its persisted ordinal, clamping anything out of range. */
    public static StoryRegion fromOrdinal(int regionOrdinal) {
        StoryRegion[] regions = values();
        if (regionOrdinal <= 0) return regions[0];
        if (regionOrdinal >= regions.length) return regions[regions.length - 1];
        return regions[regionOrdinal];
    }
}

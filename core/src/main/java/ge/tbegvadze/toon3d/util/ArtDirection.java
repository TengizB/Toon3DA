package ge.tbegvadze.toon3d.util;

/**
 * The master palette and lighting-mood numbers of the game's art direction: LIT BY THE MACHINE.
 *
 * <p>Every surface in the 3D view is lit by one cold, artificial, region-coloured light. The light
 * shifts HUE with distance (a luminance-neutral, multiplicative grade - see
 * {@code GameMath.atmosphereFogFactor}) and never changes exposure; the existing distance shade falloff
 * stays the only thing that darkens the world. Threats read warmer and cleaner than the haze around
 * them because enemies take only half the grade.
 *
 * <p>This class holds the UI neutrals, the two inks, the semantic extras, the world colours, the grade
 * strengths and the five lighting moods. Combat-communication colours (damage, defence, special, move,
 * buff, weak) are NOT duplicated here - they live in {@link CombatPalette}. The full style bible is
 * {@code docs/art-direction.txt}.
 *
 * <p>Values are plain floats in [0,1] (tints may exceed 1) or packed RGBA8888 ints, in the
 * {@link CombatPalette} idiom: dependency-free, no LibGDX {@code Color} objects, no allocation.
 */
public final class ArtDirection {

    private ArtDirection() {}

    // ------------------------------------------------------------------ UI neutrals (read from order 4)
    public static final float UI_INK_BLACK_RED = 0.035f, UI_INK_BLACK_GREEN = 0.040f, UI_INK_BLACK_BLUE = 0.050f;
    public static final float UI_PANEL_RED = 0.075f, UI_PANEL_GREEN = 0.085f, UI_PANEL_BLUE = 0.105f;
    public static final float UI_PANEL_RAISED_RED = 0.120f, UI_PANEL_RAISED_GREEN = 0.135f, UI_PANEL_RAISED_BLUE = 0.160f;
    public static final float UI_LINE_RED = 0.280f, UI_LINE_GREEN = 0.310f, UI_LINE_BLUE = 0.360f;
    public static final float UI_TEXT_PRIMARY_RED = 0.900f, UI_TEXT_PRIMARY_GREEN = 0.880f, UI_TEXT_PRIMARY_BLUE = 0.820f;
    public static final float UI_TEXT_SECONDARY_RED = 0.600f, UI_TEXT_SECONDARY_GREEN = 0.620f, UI_TEXT_SECONDARY_BLUE = 0.640f;
    public static final float UI_TEXT_DISABLED_RED = 0.380f, UI_TEXT_DISABLED_GREEN = 0.400f, UI_TEXT_DISABLED_BLUE = 0.430f;

    // ------------------------------------------------------------------ the two inks
    public static final float SUIT_AMBER_RED = 0.980f, SUIT_AMBER_GREEN = 0.760f, SUIT_AMBER_BLUE = 0.280f;
    public static final float SUIT_AMBER_DIM_RED = 0.550f, SUIT_AMBER_DIM_GREEN = 0.400f, SUIT_AMBER_DIM_BLUE = 0.140f;
    public static final float FACILITY_CYAN_RED = 0.420f, FACILITY_CYAN_GREEN = 0.860f, FACILITY_CYAN_BLUE = 0.980f;
    public static final float FACILITY_CYAN_DIM_RED = 0.240f, FACILITY_CYAN_DIM_GREEN = 0.440f, FACILITY_CYAN_DIM_BLUE = 0.520f;

    // ------------------------------------------------------------------ semantic extras
    public static final float HEALTH_GREEN_RED = 0.360f, HEALTH_GREEN_GREEN = 0.900f, HEALTH_GREEN_BLUE = 0.460f;
    public static final float ARMOUR_BLUE_RED = 0.300f, ARMOUR_BLUE_GREEN = 0.620f, ARMOUR_BLUE_BLUE = 0.980f;
    public static final float CREDITS_GOLD_RED = 0.980f, CREDITS_GOLD_GREEN = 0.820f, CREDITS_GOLD_BLUE = 0.340f;
    public static final float PLANET_RED_RED = 0.800f, PLANET_RED_GREEN = 0.170f, PLANET_RED_BLUE = 0.150f;

    // ------------------------------------------------------------------ world
    public static final float WORLD_SHADOW_RED = 0.020f, WORLD_SHADOW_GREEN = 0.020f, WORLD_SHADOW_BLUE = 0.030f;

    // ------------------------------------------------------------------ grade strengths (R2, R6)
    public static final float GRADE_STRENGTH_ENVIRONMENT = 1.00f;
    public static final float GRADE_STRENGTH_ENEMY       = 0.50f;
    public static final float GRADE_STRENGTH_WEAPON      = 0.60f;
    public static final float WEAPON_LIGHT_MIN           = 0.80f;
    public static final float WEAPON_LIGHT_MAX           = 1.15f;

    // ------------------------------------------------------------------ vignette (R10)
    public static final float VIGNETTE_CLEAR_RADIUS_FRACTION = 0.55f;
    public static final int   VIGNETTE_TEXTURE_WIDTH  = 128;
    public static final int   VIGNETTE_TEXTURE_HEIGHT = 72;
    /** World units the vignette quad is oversized on each side so screen shake (max ~10 + bump) never exposes an edge. */
    public static final float VIGNETTE_SHAKE_MARGIN_WORLD_UNITS = 24f;

    // ------------------------------------------------------------------ ids

    public static final String MOOD_ID_COOL_STEEL = "cool_steel";
    public static final String MOOD_ID_CLINICAL_WHITE = "clinical_white";
    public static final String MOOD_ID_AMBER_HOT = "amber_hot";
    public static final String MOOD_ID_CORRUPTED_VIOLET = "corrupted_violet";
    public static final String MOOD_ID_CORE_WHITE_VIOLET = "core_white_violet";

    public static final String MATERIAL_ID_DECK_GRATING_STEEL = "deck_grating_steel";
    public static final String MATERIAL_ID_CEILING_STRIP_LIGHTS = "ceiling_strip_lights";
    public static final String MATERIAL_ID_LAB_TILE_WHITE = "lab_tile_white";
    public static final String MATERIAL_ID_CEILING_PANEL_GRID = "ceiling_panel_grid";
    public static final String MATERIAL_ID_REACTOR_GRATE_HOT = "reactor_grate_hot";
    public static final String MATERIAL_ID_CEILING_PIPES = "ceiling_pipes";
    public static final String MATERIAL_ID_BREACH_FLESH_MEMBRANE = "breach_flesh_membrane";
    public static final String MATERIAL_ID_CEILING_TORN_CONDUIT = "ceiling_torn_conduit";
    public static final String MATERIAL_ID_CORE_CIRCUIT_PLATE = "core_circuit_plate";
    public static final String MATERIAL_ID_CEILING_LATTICE = "ceiling_lattice";

    // ------------------------------------------------------------------ the five moods (tints are multipliers, may exceed 1)

    // cool_steel - region A OUTER FACILITY (also the fallback): teal-blue haze
    public static final float MOOD_COOL_STEEL_NEAR_R = 0.92f;
    public static final float MOOD_COOL_STEEL_NEAR_G = 1.00f;
    public static final float MOOD_COOL_STEEL_NEAR_B = 1.10f;
    public static final float MOOD_COOL_STEEL_FAR_R = 0.62f;
    public static final float MOOD_COOL_STEEL_FAR_G = 1.08f;
    public static final float MOOD_COOL_STEEL_FAR_B = 1.40f;
    public static final float MOOD_COOL_STEEL_FOG_START_TILES = 1.5f;
    public static final float MOOD_COOL_STEEL_FOG_END_TILES = 11.0f;
    public static final int   MOOD_COOL_STEEL_HORIZON_FLOOR_PACKED = 0x0B1218FF;
    public static final int   MOOD_COOL_STEEL_HORIZON_CEILING_PACKED = 0x080E15FF;
    public static final float MOOD_COOL_STEEL_VIGNETTE_ALPHA = 0.40f;

    // clinical_white - region B RESEARCH WING: sickly green-white
    public static final float MOOD_CLINICAL_WHITE_NEAR_R = 1.00f;
    public static final float MOOD_CLINICAL_WHITE_NEAR_G = 1.00f;
    public static final float MOOD_CLINICAL_WHITE_NEAR_B = 0.98f;
    public static final float MOOD_CLINICAL_WHITE_FAR_R = 0.80f;
    public static final float MOOD_CLINICAL_WHITE_FAR_G = 1.12f;
    public static final float MOOD_CLINICAL_WHITE_FAR_B = 0.92f;
    public static final float MOOD_CLINICAL_WHITE_FOG_START_TILES = 1.5f;
    public static final float MOOD_CLINICAL_WHITE_FOG_END_TILES = 11.0f;
    public static final int   MOOD_CLINICAL_WHITE_HORIZON_FLOOR_PACKED = 0x0F1512FF;
    public static final int   MOOD_CLINICAL_WHITE_HORIZON_CEILING_PACKED = 0x0C1210FF;
    public static final float MOOD_CLINICAL_WHITE_VIGNETTE_ALPHA = 0.30f;

    // amber_hot - region C REACTOR DEPTHS: furnace amber
    public static final float MOOD_AMBER_HOT_NEAR_R = 1.10f;
    public static final float MOOD_AMBER_HOT_NEAR_G = 0.98f;
    public static final float MOOD_AMBER_HOT_NEAR_B = 0.80f;
    public static final float MOOD_AMBER_HOT_FAR_R = 1.55f;
    public static final float MOOD_AMBER_HOT_FAR_G = 0.86f;
    public static final float MOOD_AMBER_HOT_FAR_B = 0.40f;
    public static final float MOOD_AMBER_HOT_FOG_START_TILES = 1.5f;
    public static final float MOOD_AMBER_HOT_FOG_END_TILES = 10.0f;
    public static final int   MOOD_AMBER_HOT_HORIZON_FLOOR_PACKED = 0x170B04FF;
    public static final int   MOOD_AMBER_HOT_HORIZON_CEILING_PACKED = 0x110803FF;
    public static final float MOOD_AMBER_HOT_VIGNETTE_ALPHA = 0.45f;

    // corrupted_violet - region D THE BREACH: blood-violet
    public static final float MOOD_CORRUPTED_VIOLET_NEAR_R = 1.08f;
    public static final float MOOD_CORRUPTED_VIOLET_NEAR_G = 0.95f;
    public static final float MOOD_CORRUPTED_VIOLET_NEAR_B = 1.02f;
    public static final float MOOD_CORRUPTED_VIOLET_FAR_R = 1.70f;
    public static final float MOOD_CORRUPTED_VIOLET_FAR_G = 0.62f;
    public static final float MOOD_CORRUPTED_VIOLET_FAR_B = 1.10f;
    public static final float MOOD_CORRUPTED_VIOLET_FOG_START_TILES = 1.0f;
    public static final float MOOD_CORRUPTED_VIOLET_FOG_END_TILES = 8.0f;
    public static final int   MOOD_CORRUPTED_VIOLET_HORIZON_FLOOR_PACKED = 0x14040EFF;
    public static final int   MOOD_CORRUPTED_VIOLET_HORIZON_CEILING_PACKED = 0x0F030BFF;
    public static final float MOOD_CORRUPTED_VIOLET_VIGNETTE_ALPHA = 0.55f;

    // core_white_violet - region E THE CORE: cold violet
    public static final float MOOD_CORE_WHITE_VIOLET_NEAR_R = 0.98f;
    public static final float MOOD_CORE_WHITE_VIOLET_NEAR_G = 0.98f;
    public static final float MOOD_CORE_WHITE_VIOLET_NEAR_B = 1.12f;
    public static final float MOOD_CORE_WHITE_VIOLET_FAR_R = 1.05f;
    public static final float MOOD_CORE_WHITE_VIOLET_FAR_G = 0.80f;
    public static final float MOOD_CORE_WHITE_VIOLET_FAR_B = 1.85f;
    public static final float MOOD_CORE_WHITE_VIOLET_FOG_START_TILES = 1.5f;
    public static final float MOOD_CORE_WHITE_VIOLET_FOG_END_TILES = 11.0f;
    public static final int   MOOD_CORE_WHITE_VIOLET_HORIZON_FLOOR_PACKED = 0x0C0818FF;
    public static final int   MOOD_CORE_WHITE_VIOLET_HORIZON_CEILING_PACKED = 0x090614FF;
    public static final float MOOD_CORE_WHITE_VIOLET_VIGNETTE_ALPHA = 0.50f;

    // -------------------------------------------------------------------------
    // MUZZLE LIGHT COLOURS (visual-identity-order-2, R7): the light's colour per weapon family.
    // -------------------------------------------------------------------------
    public static final float MUZZLE_WARM_RED    = 1.00f;
    public static final float MUZZLE_WARM_GREEN  = 0.82f;
    public static final float MUZZLE_WARM_BLUE   = 0.55f;
    public static final float MUZZLE_ENERGY_RED   = 0.60f;
    public static final float MUZZLE_ENERGY_GREEN = 0.90f;
    public static final float MUZZLE_ENERGY_BLUE  = 1.00f;
    public static final float MUZZLE_FIRE_RED    = 1.00f;
    public static final float MUZZLE_FIRE_GREEN  = 0.55f;
    public static final float MUZZLE_FIRE_BLUE   = 0.20f;

    // -------------------------------------------------------------------------
    // TYPOGRAPHY (visual-identity-order-3): the one procedural pixel font (render/GameFonts,
    // glyphs in render/PixelFontGlyphs). Grid numbers are in FONT PIXELS (R1).
    // -------------------------------------------------------------------------
    /** Atlas texels per font pixel, each axis (R4). */
    public static final int   PIXEL_FONT_ATLAS_TEXELS_PER_PIXEL = 4;
    /** Largest atlas side in texels (R4); GameFonts refuses to build a bigger one. */
    public static final int   PIXEL_FONT_ATLAS_MAX_SIDE_TEXELS = 512;
    /** Transparent font pixels kept between glyphs in the atlas, so Linear filtering never bleeds (R4). */
    public static final int   PIXEL_FONT_ATLAS_PADDING_PIXELS = 1;
    /**
     * Cap height, in world units at setScale(1), of the LibGDX 1.14.1 default font (lsans-15) that the
     * pixel font replaces: measured from its BitmapFontData (capHeight 11, lineHeight 18). R5 scales the
     * pixel font to this, so every existing setScale keeps its cap height.
     */
    public static final float PIXEL_FONT_TARGET_CAP_HEIGHT = 11f;
    public static final int   PIXEL_FONT_CAP_HEIGHT_PIXELS = 7;
    public static final int   PIXEL_FONT_X_HEIGHT_PIXELS = 5;
    public static final int   PIXEL_FONT_DESCENDER_PIXELS = 2;
    public static final int   PIXEL_FONT_LINE_HEIGHT_PIXELS = 11;
    public static final int   PIXEL_FONT_LETTER_SPACING_PIXELS = 1;
    public static final int   PIXEL_FONT_SPACE_ADVANCE_PIXELS = 3;

    // TYPE SCALE (R8): setScale multipliers over the metric-matched base. Adopted screen by screen in
    // orders 4-6. Nothing shipped is drawn below TYPE_CAPTION.
    public static final float TYPE_CAPTION = 1.00f;
    public static final float TYPE_BODY    = 1.25f;
    public static final float TYPE_LABEL   = 1.50f;
    public static final float TYPE_HEADING = 2.00f;
    public static final float TYPE_DISPLAY = 3.50f;

    // -------------------------------------------------------------------------
    // UI KIT GEOMETRY (visual-identity-order-4, R7): render/UiKit's primitives. World units.
    // -------------------------------------------------------------------------
    /** 45-degree corner cut of a panel. */
    public static final float UI_CHAMFER = 8f;
    /** 45-degree corner cut of a tappable plate. */
    public static final float UI_PLATE_CHAMFER = 12f;
    public static final float UI_BORDER_THICKNESS = 2f;
    /** Corner bracket arm length: short enough to read as a frame, not a box. */
    public static final float UI_BRACKET_LENGTH = 14f;
    /** Bracket arm on a plate too small for UI_BRACKET_LENGTH beside its text (the HUD's weapon slots). */
    public static final float UI_BRACKET_LENGTH_COMPACT = 7f;
    public static final float UI_BRACKET_THICKNESS = 3f;
    public static final float UI_BRACKET_INSET = 4f;
    public static final float UI_METER_GAP = 2f;
    /** Divider line thickness and the ink tick at its left end (R5). */
    public static final float UI_DIVIDER_THICKNESS = 1f;
    public static final float UI_DIVIDER_TICK_LENGTH = 6f;
    public static final float UI_DIVIDER_TICK_THICKNESS = 3f;
    /** Down-right text shadow offset at TYPE_CAPTION; grows with the font's scale (R6). */
    public static final float UI_TEXT_SHADOW_OFFSET = 1.5f;
    public static final float UI_TEXT_SHADOW_ALPHA = 0.85f;
    /** HUD panel opacity; HudConstants.HUD_PANEL_ALPHA references it. */
    public static final float UI_PANEL_ALPHA_HUD = 0.82f;
    /** Full-screen scrim opacity behind overlays (used from order 5). */
    public static final float UI_SCRIM_ALPHA = 0.75f;
    /** DISABLED plate: fill and border alpha multiplier (R3). */
    public static final float UI_PLATE_DISABLED_ALPHA_SCALE = 0.5f;
    /** Boss health meter segment count (R12). */
    public static final int   BOSS_HEALTH_SEGMENTS = 20;

    // -------------------------------------------------------------------------
    // SUIT SCREENS (visual-identity-order-5): the inventory family, weapon inspect, level-up, relic offer,
    // codex. render/UiKit.screenHeaderTitle / screenHeaderRule / card / newMarker. World units.
    // -------------------------------------------------------------------------
    /** Full-screen suit-screen panel opacity (R4); the HUD's 0.82 is for in-play chrome only. */
    public static final float UI_PANEL_ALPHA_SCREEN = 0.94f;
    /** R1: gap between a screen title's glyph bottom (TYPE_HEADING cap height) and its divider. */
    public static final float UI_SCREEN_HEADER_RULE_GAP = 8f;
    /** R7: the NEW marker - a SUIT_AMBER square this wide, framed by a UI_INK_BLACK edge this thick. */
    public static final float UI_NEW_MARKER_SIZE = 6f;
    public static final float UI_NEW_MARKER_EDGE = 1f;
    /** R9: the selected list row's SUIT_AMBER left-edge bar width. */
    public static final float UI_SELECTION_BAR_WIDTH = 3f;
    /** R10: the UI_LINE border around a tag chip, and the chip's chamfer. */
    public static final float UI_CHIP_BORDER_THICKNESS = 1f;
    public static final float UI_CHIP_CHAMFER = 6f;
}

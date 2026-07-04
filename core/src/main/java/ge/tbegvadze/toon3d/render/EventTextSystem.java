package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * Manages a fixed pool of screen-space event text entries (e.g. "Turn skipped", "-12 HP").
 * Each entry rises and fades over EVENT_TEXT_LIFE_SECONDS; up to EVENT_TEXT_MAX active at once.
 * Zero allocations after construction — strings are stored by reference to pre-built literals
 * or entries from the damage-number cache.
 *
 * Banner tier system (Phase 1 ability feedback):
 *   Slots spawned via spawnBanner() carry an explicit RGB color and a tier byte that
 *   EventTextRenderer uses to pick font scale and pop/punch animation.  Legacy slots
 *   spawned via spawnWithColor() keep tier = 0 and use the existing color-type palette.
 */
public final class EventTextSystem {

    public static final byte COLOR_WHITE        = 0;
    public static final byte COLOR_GREEN        = 1;
    public static final byte COLOR_RED          = 2;
    public static final byte COLOR_GREY         = 3;
    /** Bright gold — used for legendary signature ability events (Soulforge, Judgment, Nova). */
    public static final byte COLOR_GOLD         = 4;
    /** Cyan-gold — used for credit pickup notifications. */
    public static final byte COLOR_CREDIT_CYAN  = 5;
    /** Shield blue — used for the "BLOCKED N" floater when an enemy's Block eats a hit (order-3). */
    public static final byte COLOR_BLUE         = 6;

    /**
     * Banner tier constants — controls font scale, animation style, and screen anchor.
     *   0             : legacy color-type path (no tier animation).
     *   TIER_TAG  (1) : passive confirm — no banner spawned; suffix tag only (Phase 2).
     *   TIER_PROC (2) : small pop-in banner at ABILITY_BANNER_PROC_ANCHOR_Y.
     *   TIER_SLAM (3) : large punch-in banner at ABILITY_BANNER_SLAM_ANCHOR_Y + underline.
     *   TIER_LEGENDARY(4): full-width gold slam at ABILITY_BANNER_LEGENDARY_ANCHOR_Y.
     */
    public static final byte TIER_TAG       = 1;
    public static final byte TIER_PROC      = 2;
    public static final byte TIER_SLAM      = 3;
    public static final byte TIER_LEGENDARY = 4;

    // Pre-built damage strings "-0".."-99" to avoid allocation on enemy hit.
    private static final String[] DAMAGE_STRINGS = buildDamageStrings();

    private static String[] buildDamageStrings() {
        String[] table = new String[100];
        for (int damageIndex = 0; damageIndex < table.length; damageIndex++) {
            table[damageIndex] = "-" + damageIndex;
        }
        return table;
    }

    // Parallel flat arrays for the active text pool — no object allocation per event.
    private final String[] texts;
    private final float[]  ageSeconds;
    private final byte[]   colorTypes;
    // Banner-tier parallel arrays — only populated by spawnBanner(); 0/0f in legacy slots.
    private final byte[]   bannerTier;
    private final float[]  bannerRed;
    private final float[]  bannerGreen;
    private final float[]  bannerBlue;

    private final int poolSize;
    private int       nextSlot;

    public EventTextSystem() {
        poolSize    = EffectConstants.EVENT_TEXT_MAX;
        texts       = new String[poolSize];
        ageSeconds  = new float[poolSize];
        colorTypes  = new byte[poolSize];
        bannerTier  = new byte[poolSize];
        bannerRed   = new float[poolSize];
        bannerGreen = new float[poolSize];
        bannerBlue  = new float[poolSize];
        nextSlot    = 0;
    }

    /** Advances all active entries by deltaTime, expiring those past their lifetime. */
    public void update(float deltaTime) {
        for (int slotIndex = 0; slotIndex < poolSize; slotIndex++) {
            if (texts[slotIndex] == null) continue;
            ageSeconds[slotIndex] += deltaTime;
            if (ageSeconds[slotIndex] >= EffectConstants.EVENT_TEXT_LIFE_SECONDS) {
                texts[slotIndex] = null;
                bannerTier[slotIndex] = 0;
            }
        }
    }

    /** Spawns a new event text in white using the legacy rise-and-fade path. */
    public void spawn(String text) {
        spawnWithColor(text, COLOR_WHITE);
    }

    /** Spawns a new event text with an explicit color type using the legacy path. */
    public void spawnWithColor(String text, byte colorType) {
        texts[nextSlot]       = text;
        ageSeconds[nextSlot]  = 0f;
        colorTypes[nextSlot]  = colorType;
        bannerTier[nextSlot]  = 0;    // legacy path — no tier animation
        bannerRed[nextSlot]   = 0f;
        bannerGreen[nextSlot] = 0f;
        bannerBlue[nextSlot]  = 0f;
        nextSlot = (nextSlot + 1) % poolSize;
    }

    /**
     * Spawns an ability banner with an explicit RGB color and tier.
     * EventTextRenderer reads the tier to select font scale and animation curve.
     * The string must be a pre-built literal or a String built at spawn-time on the
     * sim thread — never call this from render().
     */
    public void spawnBanner(String text, float red, float green, float blue, byte tier) {
        texts[nextSlot]       = text;
        ageSeconds[nextSlot]  = 0f;
        colorTypes[nextSlot]  = COLOR_WHITE;  // fallback if renderer checks color type
        bannerTier[nextSlot]  = tier;
        bannerRed[nextSlot]   = red;
        bannerGreen[nextSlot] = green;
        bannerBlue[nextSlot]  = blue;
        nextSlot = (nextSlot + 1) % poolSize;
    }

    /** Spawns a red damage text for the given net HP loss. Uses pre-built strings; no allocation. */
    public void spawnDamage(int netDamage) {
        String text = netDamage >= 0 && netDamage < DAMAGE_STRINGS.length
                ? DAMAGE_STRINGS[netDamage]
                : "-" + netDamage;
        spawnWithColor(text, COLOR_RED);
    }

    public int getPoolSize() { return poolSize; }

    /** Returns the text at slot index, or null if the slot is inactive. */
    public String getText(int slotIndex) { return texts[slotIndex]; }

    /** Returns age in [0, EVENT_TEXT_LIFE_SECONDS] for slot index. */
    public float getAge(int slotIndex) { return ageSeconds[slotIndex]; }

    /** Returns the color type constant for legacy slots. */
    public byte getColorType(int slotIndex) { return colorTypes[slotIndex]; }

    /** Returns the banner tier for this slot: 0 = legacy path; TIER_PROC/SLAM/LEGENDARY = tiered. */
    public byte getBannerTier(int slotIndex) { return bannerTier[slotIndex]; }

    /** Returns the explicit red component for banner slots; 0 for legacy slots. */
    public float getBannerRed(int slotIndex) { return bannerRed[slotIndex]; }

    /** Returns the explicit green component for banner slots; 0 for legacy slots. */
    public float getBannerGreen(int slotIndex) { return bannerGreen[slotIndex]; }

    /** Returns the explicit blue component for banner slots; 0 for legacy slots. */
    public float getBannerBlue(int slotIndex) { return bannerBlue[slotIndex]; }

    /** Spawns a grey "MISS" event text — neutral, does not trigger hit effects. */
    public void showMiss() {
        spawnWithColor("MISS", COLOR_GREY);
    }
}

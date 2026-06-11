package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * Manages a fixed pool of screen-space event text entries (e.g. "Turn skipped", "-12 HP").
 * Each entry rises and fades over EVENT_TEXT_LIFE_SECONDS; up to EVENT_TEXT_MAX active at once.
 * Zero allocations after construction — strings are stored by reference to pre-built literals
 * or entries from the damage-number cache.
 */
public final class EventTextSystem {

    public static final byte COLOR_WHITE = 0;
    public static final byte COLOR_GREEN = 1;
    public static final byte COLOR_RED   = 2;

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
    private final int      poolSize;
    private int            nextSlot;

    public EventTextSystem() {
        poolSize   = EffectConstants.EVENT_TEXT_MAX;
        texts      = new String[poolSize];
        ageSeconds = new float[poolSize];
        colorTypes = new byte[poolSize];
        nextSlot   = 0;
    }

    /** Advances all active entries by deltaTime, expiring those past their lifetime. */
    public void update(float deltaTime) {
        for (int slotIndex = 0; slotIndex < poolSize; slotIndex++) {
            if (texts[slotIndex] == null) continue;
            ageSeconds[slotIndex] += deltaTime;
            if (ageSeconds[slotIndex] >= EffectConstants.EVENT_TEXT_LIFE_SECONDS) {
                texts[slotIndex] = null;
            }
        }
    }

    /** Spawns a new event text in white. Evicts the oldest entry if the pool is full. */
    public void spawn(String text) {
        spawnWithColor(text, COLOR_WHITE);
    }

    /** Spawns a new event text with an explicit color type. Evicts the oldest entry if the pool is full. */
    public void spawnWithColor(String text, byte colorType) {
        texts[nextSlot]      = text;
        ageSeconds[nextSlot] = 0f;
        colorTypes[nextSlot] = colorType;
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

    /** Returns the color type constant for the slot (COLOR_WHITE, COLOR_GREEN, or COLOR_RED). */
    public byte getColorType(int slotIndex) { return colorTypes[slotIndex]; }
}

package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * Weapon {@link ItemType} to muzzle-light binding (visual-identity-order-2, R7): colour (r, g, b) and peak
 * intensity per weapon, one {@code register()} row per binding. Same idiom as
 * {@code audio/SoundRegistry.bindWeapon}: an ordinal-indexed table, so a lookup is one array read and the
 * hot path never allocates or switches. A weapon with no row falls back to WARM, so a future gun is never
 * dark. A row with peak 0 (melee) emits no pulse.
 *
 * <p>Read-only after {@link #bootstrap()}; the table is only written during bootstrap, so any thread may
 * read it afterwards.
 */
public final class MuzzleLightCatalog {

    private static final int ROW_FLOATS = 4; // red, green, blue, peak
    private static final float[] TABLE = new float[ItemType.values().length * ROW_FLOATS];
    private static boolean bootstrapped;

    static {
        bootstrap();
    }

    private MuzzleLightCatalog() {}

    /** Fills the table with the R7 rows. Idempotent: a second call rewrites identical values. */
    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        // Unbound weapons fall back to WARM (never dark).
        for (ItemType itemType : ItemType.values()) {
            register(itemType, ArtDirection.MUZZLE_WARM_RED, ArtDirection.MUZZLE_WARM_GREEN,
                    ArtDirection.MUZZLE_WARM_BLUE, EffectConstants.MUZZLE_LIGHT_PEAK_WARM);
        }
        // WARM: slug and bullet weapons, and the grenade launch.
        registerWarm(ItemType.WEAPON_PISTOL);
        registerWarm(ItemType.WEAPON_SHOTGUN);
        registerWarm(ItemType.WEAPON_DOUBLE_BARREL);
        registerWarm(ItemType.WEAPON_CHAINGUN);
        registerWarm(ItemType.WEAPON_ASSAULT_RIFLE);
        registerWarm(ItemType.WEAPON_ROCKET);
        // ENERGY: cold cyan.
        registerEnergy(ItemType.WEAPON_PLASMA);
        registerEnergy(ItemType.WEAPON_RAILGUN);
        registerEnergy(ItemType.WEAPON_ARC_CANNON);
        // FIRE: orange.
        register(ItemType.WEAPON_INCINERATOR, ArtDirection.MUZZLE_FIRE_RED, ArtDirection.MUZZLE_FIRE_GREEN,
                ArtDirection.MUZZLE_FIRE_BLUE, EffectConstants.MUZZLE_LIGHT_PEAK_FIRE);
        // NONE: melee swings emit no light.
        registerNone(ItemType.WEAPON_FIST);
        registerNone(ItemType.WEAPON_KNIFE);
        registerNone(ItemType.WEAPON_HAMMER);
        registerNone(ItemType.WEAPON_CHAINSAW);
        bootstrapped = true;
    }

    private static void registerWarm(ItemType itemType) {
        register(itemType, ArtDirection.MUZZLE_WARM_RED, ArtDirection.MUZZLE_WARM_GREEN,
                ArtDirection.MUZZLE_WARM_BLUE, EffectConstants.MUZZLE_LIGHT_PEAK_WARM);
    }

    private static void registerEnergy(ItemType itemType) {
        register(itemType, ArtDirection.MUZZLE_ENERGY_RED, ArtDirection.MUZZLE_ENERGY_GREEN,
                ArtDirection.MUZZLE_ENERGY_BLUE, EffectConstants.MUZZLE_LIGHT_PEAK_ENERGY);
    }

    private static void registerNone(ItemType itemType) {
        register(itemType, 0f, 0f, 0f, 0f);
    }

    private static void register(ItemType itemType, float red, float green, float blue, float peak) {
        int base = itemType.ordinal() * ROW_FLOATS;
        TABLE[base]     = red;
        TABLE[base + 1] = green;
        TABLE[base + 2] = blue;
        TABLE[base + 3] = peak;
    }

    /** Peak intensity for the weapon; 0 means no pulse. Null returns 0. */
    public static float peakOf(ItemType itemType) {
        return itemType == null ? 0f : TABLE[itemType.ordinal() * ROW_FLOATS + 3];
    }

    public static float redOf(ItemType itemType) {
        return itemType == null ? 0f : TABLE[itemType.ordinal() * ROW_FLOATS];
    }

    public static float greenOf(ItemType itemType) {
        return itemType == null ? 0f : TABLE[itemType.ordinal() * ROW_FLOATS + 1];
    }

    public static float blueOf(ItemType itemType) {
        return itemType == null ? 0f : TABLE[itemType.ordinal() * ROW_FLOATS + 2];
    }
}

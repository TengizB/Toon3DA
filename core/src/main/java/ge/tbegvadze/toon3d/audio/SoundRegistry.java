package ge.tbegvadze.toon3d.audio;

import ge.tbegvadze.toon3d.item.ItemType;

/**
 * The registry of every registered sound recipe, plus the bindings that map game concepts onto
 * them (procedural-sound-effects order 1).
 *
 * <p>Array-backed by enum ordinal throughout — no {@code HashMap} anywhere, because
 * {@code forWeapon} is called on every shot.  Lookups are a bounds check and an array read.
 *
 * <p>This is the same "never a switch statement" discipline as {@code route/RouteRegistries} and
 * {@code tileset/EnvironmentSpriteRegistry}: the mapping from a weapon to its sound is DATA, so a
 * new weapon binds its sound with one line in {@code GameSoundCatalog} and no code anywhere else
 * learns the weapon's name.
 *
 * <p>Headless — no LibGDX import.
 */
public final class SoundRegistry {

    // Both arrays are sized from the enum they are indexed by, so every ordinal used below is in
    // range by construction and none of these lookups can be out of bounds.
    private final SoundDefinition[] definitionsById     = new SoundDefinition[GameSoundId.COUNT];
    private final GameSoundId[]     weaponFireBinding   = new GameSoundId[ItemType.values().length];
    private final GameSoundId[]     weaponImpactBinding = new GameSoundId[ItemType.values().length];

    /** Replaces any previous registration for the same id, so a later bootstrap can override. */
    public void register(SoundDefinition definition) {
        definitionsById[definition.getId().ordinal()] = definition;
    }

    /** Null when nothing has been registered for this id — the caller treats that as silence. */
    public SoundDefinition get(GameSoundId id) {
        if (id == null) return null;
        return definitionsById[id.ordinal()];
    }

    public boolean isRegistered(GameSoundId id) {
        return get(id) != null;
    }

    public int getRegisteredCount() {
        int count = 0;
        for (SoundDefinition definition : definitionsById) {
            if (definition != null) count++;
        }
        return count;
    }

    /** Binds a weapon item to the sound it makes when fired. */
    public void bindWeapon(ItemType weaponItemType, GameSoundId soundId) {
        weaponFireBinding[weaponItemType.ordinal()] = soundId;
    }

    /**
     * The fire sound for a weapon, falling back to {@link GameSoundId#WEAPON_FIRE_DEFAULT}.
     *
     * <p>The fallback is the entire reason this indirection exists: a weapon added later without a
     * binding is never SILENT, which is a bug nobody notices in review and everybody notices in
     * play.
     */
    public GameSoundId forWeapon(ItemType weaponItemType) {
        if (weaponItemType == null) return GameSoundId.WEAPON_FIRE_DEFAULT;
        GameSoundId bound = weaponFireBinding[weaponItemType.ordinal()];
        return bound != null ? bound : GameSoundId.WEAPON_FIRE_DEFAULT;
    }

    /**
     * Binds a weapon to the sound its hits make.
     *
     * <p>The impact's character belongs to the WEAPON, not the victim — buckshot patters, plasma
     * sizzles, a hammer thuds — which is why a handful of impact recipes cover every weapon against
     * every enemy, forever, instead of one per enemy type.
     */
    public void bindWeaponImpact(ItemType weaponItemType, GameSoundId soundId) {
        weaponImpactBinding[weaponItemType.ordinal()] = soundId;
    }

    /** The impact sound for a weapon, falling back to the ballistic recipe. */
    public GameSoundId impactForWeapon(ItemType weaponItemType) {
        if (weaponItemType == null) return GameSoundId.IMPACT_BALLISTIC;
        GameSoundId bound = weaponImpactBinding[weaponItemType.ordinal()];
        return bound != null ? bound : GameSoundId.IMPACT_BALLISTIC;
    }
}

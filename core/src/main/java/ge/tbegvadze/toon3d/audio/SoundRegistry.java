package ge.tbegvadze.toon3d.audio;

import ge.tbegvadze.toon3d.enemy.EnemyFamily;
import ge.tbegvadze.toon3d.enemy.EnemyVoiceMoment;
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
    private final GameSoundId[][]   familyVoiceBinding  =
            new GameSoundId[EnemyFamily.values().length][EnemyVoiceMoment.COUNT];
    private final GameSoundId[]     voiceFallback       = new GameSoundId[EnemyVoiceMoment.COUNT];
    private final float[]           launchPitchByFamily = newUnitPitchTable();

    private static float[] newUnitPitchTable() {
        float[] table = new float[EnemyFamily.values().length];
        java.util.Arrays.fill(table, 1f);
        return table;
    }

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

    /**
     * Binds an enemy family to its three voices (order 3).
     *
     * <p>Per FAMILY, never per {@code EnemyType}: the player learns six sound-shapes, not twenty,
     * and a new archetype in an existing family inherits its family's voice for free.
     */
    public void bindEnemyFamily(EnemyFamily family, GameSoundId alert, GameSoundId attack,
                                GameSoundId death) {
        GameSoundId[] voices = familyVoiceBinding[family.ordinal()];
        voices[EnemyVoiceMoment.ALERT.ordinal()]  = alert;
        voices[EnemyVoiceMoment.ATTACK.ordinal()] = attack;
        voices[EnemyVoiceMoment.DEATH.ordinal()]  = death;
    }

    /**
     * The pitch a family's ranged shot is played at (order 5). The launch sound itself is shared by
     * every family; only its pitch says who fired. Unbound families play at 1.
     */
    public void bindEnemyFamilyLaunchPitch(EnemyFamily family, float pitch) {
        launchPitchByFamily[family.ordinal()] = pitch;
    }

    public float launchPitchForEnemyFamily(EnemyFamily family) {
        return family != null ? launchPitchByFamily[family.ordinal()] : 1f;
    }

    /** The sound a family with no binding makes at this moment; null leaves that moment silent. */
    public void bindEnemyVoiceFallback(EnemyVoiceMoment moment, GameSoundId soundId) {
        voiceFallback[moment.ordinal()] = soundId;
    }

    /**
     * The voice a family makes at a moment, falling back to the catalog's per-moment fallback — so
     * a family added later without a binding is heard, never silent.
     * Null (silence) only when neither a binding nor a fallback exists.
     */
    public GameSoundId forEnemyFamily(EnemyFamily family, EnemyVoiceMoment moment) {
        if (moment == null) return null;
        if (family != null) {
            GameSoundId bound = familyVoiceBinding[family.ordinal()][moment.ordinal()];
            if (bound != null) return bound;
        }
        return voiceFallback[moment.ordinal()];
    }
}

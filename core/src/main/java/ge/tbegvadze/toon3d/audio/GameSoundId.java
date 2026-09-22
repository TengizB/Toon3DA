package ge.tbegvadze.toon3d.audio;

/**
 * The stable id of every gameplay sound — the vocabulary every call site speaks
 * (procedural-sound-effects orders 1 and 2).
 *
 * <p>Ids are ordinal-indexed throughout the layer ({@code Sound[]}, last-play timestamps, play
 * counters), so every lookup is an array read and nothing on a firing path allocates or hashes.
 *
 * <p>An id here is only a NAME.  What it sounds like is a {@code register()} line in
 * {@code GameSoundCatalog}; an id with no registered definition is simply never audible, which is
 * what lets an id be added one commit ahead of its recipe.
 */
public enum GameSoundId {

    // --- Player weapon fire: one per ItemType weapon row -------------------------------------
    FIRE_PISTOL,
    FIRE_SHOTGUN,
    FIRE_DOUBLE_BARREL,
    FIRE_CHAINGUN,
    FIRE_ASSAULT_RIFLE,
    FIRE_PLASMA,
    FIRE_RAILGUN,
    FIRE_INCINERATOR,
    FIRE_ROCKET,
    FIRE_ARC_CANNON,
    FIRE_FIST,
    FIRE_KNIFE,
    FIRE_HAMMER,
    FIRE_CHAINSAW,
    /** A weapon added later with no binding registered is never silent — this is why. */
    WEAPON_FIRE_DEFAULT,

    // --- Weapon state -------------------------------------------------------------------------
    /** Fires on the railgun's CHARGE turn, which consumed an action and produced no shot. */
    RAILGUN_CHARGE,
    WEAPON_DRY_FIRE,
    WEAPON_RELOAD_START,
    WEAPON_RELOAD_COMPLETE,
    WEAPON_SWITCH,

    // --- Player state -------------------------------------------------------------------------
    PLAYER_HURT_LIGHT,
    PLAYER_HURT_HEAVY,
    PLAYER_GUARDED,
    PLAYER_FLANKED,
    PLAYER_DEATH,
    /** A refused tap must never be indistinguishable from a dropped tap. */
    MOVE_BLOCKED,

    // --- Impact: keyed by the weapon class that FIRED, not by the target ----------------------
    IMPACT_BALLISTIC,
    IMPACT_ENERGY,
    IMPACT_MELEE,
    IMPACT_BLOCKED,

    // --- Enemies and the world ----------------------------------------------------------------
    ENEMY_ATTACK_MELEE,
    ENEMY_ATTACK_RANGED,
    ENEMY_DEATH,
    BARREL_EXPLOSION;

    public static final int COUNT = values().length;
}

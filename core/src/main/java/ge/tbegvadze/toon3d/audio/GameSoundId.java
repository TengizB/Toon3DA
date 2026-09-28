package ge.tbegvadze.toon3d.audio;

/**
 * The stable id of every gameplay sound — the vocabulary every call site speaks
 * (procedural-sound-effects orders 1-4).
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
    ENEMY_ATTACK_RANGED,
    BARREL_EXPLOSION,

    // --- Enemy family voices: one set per EnemyFamily, never per EnemyType (order 3) ----------
    // Bound to their family in GameSoundCatalog; a family with no binding borrows ABERRATION's.
    ENEMY_ABERRATION_ALERT,
    ENEMY_ABERRATION_ATTACK,
    ENEMY_ABERRATION_DEATH,
    ENEMY_UNDEAD_ALERT,
    ENEMY_UNDEAD_ATTACK,
    ENEMY_UNDEAD_DEATH,
    ENEMY_INSECT_ALERT,
    ENEMY_INSECT_ATTACK,
    ENEMY_INSECT_DEATH,
    ENEMY_MACHINE_ALERT,
    ENEMY_MACHINE_ATTACK,
    ENEMY_MACHINE_DEATH,
    ENEMY_DEMON_ALERT,
    ENEMY_DEMON_ATTACK,
    ENEMY_DEMON_DEATH,
    ENEMY_GOLEM_ALERT,
    ENEMY_GOLEM_ATTACK,
    ENEMY_GOLEM_DEATH,

    // --- The facility: doors, stairs, pickups, progression (order 4) --------------------------
    DOOR_OPEN,
    DOOR_CLOSE,
    /** A refused door — flat and unfriendly, so it cannot be mistaken for a dropped tap. */
    DOOR_LOCKED,
    STAIRS_DESCEND,
    PICKUP_AMMO,
    PICKUP_MEDICAL,
    PICKUP_ARMOUR,
    PICKUP_KEYCARD,
    PICKUP_WEAPON,
    PICKUP_CREDIT,
    PLAYER_HEAL,
    LEVEL_UP,
    /** The XP receipt, deliberately near the floor of the mix. */
    KILL_CONFIRM,
    /** The quietest thing in the mix, and the first candidate for deletion. */
    FOOTSTEP;

    public static final int COUNT = values().length;
}

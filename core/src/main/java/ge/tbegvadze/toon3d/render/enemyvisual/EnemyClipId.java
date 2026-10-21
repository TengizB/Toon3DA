package ge.tbegvadze.toon3d.render.enemyvisual;

/**
 * The standard clip set every generated creature ships (docs/sprite-art-creation-guide.txt 7.3), named
 * exactly as the manifest names them. The animator and the rule table refer to clips by this id, so a
 * per-frame lookup is an array index, never a string compare.
 */
public enum EnemyClipId {
    IDLE,
    DORMANT,
    ALERT,
    MOVE,
    WIND_UP,
    ATTACK,
    HURT_LIGHT,
    HURT_HEAVY,
    DEFEND,
    WOUNDED,
    DEATH;

    public static final int COUNT = values().length;

    /** The id named {@code manifestName}, or null for a clip this runtime does not know (an ability clip). */
    public static EnemyClipId fromManifestName(String manifestName) {
        for (EnemyClipId clip : values()) {
            if (clip.name().equals(manifestName)) return clip;
        }
        return null;
    }
}

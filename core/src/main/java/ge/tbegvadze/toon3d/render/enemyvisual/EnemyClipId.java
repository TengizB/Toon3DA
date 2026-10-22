package ge.tbegvadze.toon3d.render.enemyvisual;

/**
 * The standard clip set every generated creature ships (docs/sprite-art-creation-guide.txt 7.3), named
 * exactly as the manifest names them, plus the ABILITY clips a creature ships for a SpecialAbility it uses
 * (order 4: BUFF_SELF, AREA_STRIKE; order 5: DEBUFF_PLAYER, SELF_DESTRUCT - mapped from the ability by
 * {@link EnemyAnimationRules}' ability rows), the PRIMING hold and the DEATH_BURST death variant (order 5),
 * and SUMMON (order 6: the Blight Corruptor's pods split).
 * The animator and the rule table refer to clips by this id, so a per-frame lookup is an array index,
 * never a string compare.
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
    DEATH,
    // ---- ability clips (order 4); a creature without one plays ATTACK for that ability ----
    BUFF_SELF,
    AREA_STRIKE,
    // ---- order 5 (the Aberrations) ----
    /** Ability clip: the Eye Tyrant's iris pinpoint, the Mire Wraith's acid volley. */
    DEBUFF_PLAYER,
    /** Ability clip: each self-destruct countdown turn (the Plague Hulk swells a level further). */
    SELF_DESTRUCT,
    /** HELD while the enemy is SELF_DESTRUCTING (the priming swell); a creature without it holds WIND_UP. */
    PRIMING,
    /** The DEATH variant of a self-destruct (it bursts); a creature without it plays DEATH. */
    DEATH_BURST,
    // ---- order 6 (the Undead) ----
    /** Ability clip: the Blight Corruptor's pods swell and split, releasing what grew inside. */
    SUMMON;

    public static final int COUNT = values().length;

    /** The id named {@code manifestName}, or null for a clip this runtime does not know. */
    public static EnemyClipId fromManifestName(String manifestName) {
        for (EnemyClipId clip : values()) {
            if (clip.name().equals(manifestName)) return clip;
        }
        return null;
    }
}

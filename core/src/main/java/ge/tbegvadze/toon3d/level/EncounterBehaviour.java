package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;

/**
 * How an archetype FIGHTS, for the purpose of mixing a group (mixed-encounter-groups, V3): what makes a
 * room a question ("leave the eyes' lane, kill the biter first, out-walk the ghoul") is several of these
 * at once. Each constant carries its own rule over the archetype's DATA — the {@code EnemyType} idiom, so
 * a new archetype is classified by construction and the planner holds no switch.
 */
public enum EncounterBehaviour {

    /** Shoots down a cardinal line: break the lane. */
    RANGED {
        @Override public boolean matches(EnemyType type) { return type.isRanged(); }
    },
    /** Melee that moves every turn: it reaches you, kill it first. */
    RUSHER {
        @Override public boolean matches(EnemyType type) { return !type.isRanged() && type.moveEveryNTurns() <= 1; }
    },
    /** Melee that moves every second turn or slower: it can be out-walked and left for later. */
    PLODDER {
        @Override public boolean matches(EnemyType type) { return !type.isRanged() && type.moveEveryNTurns() >= 2; }
    };

    private static final EncounterBehaviour[] VALUES = values();

    /** Whether an archetype fights this way. */
    public abstract boolean matches(EnemyType type);

    /** The one behaviour an archetype falls in (the constants partition every archetype). */
    public static EncounterBehaviour of(EnemyType type) {
        for (EncounterBehaviour behaviour : VALUES) {
            if (behaviour.matches(type)) return behaviour;
        }
        return RUSHER;
    }
}

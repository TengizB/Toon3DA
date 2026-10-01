package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyRole;
import ge.tbegvadze.toon3d.enemy.EnemyType;

/**
 * Which archetypes may fill one slot of an {@link EncounterGroupTemplate} (balance-overhaul order 2,
 * E2). Each constant carries its own admission rule over the archetype's DATA (role, ranged flag, move
 * cadence) — the {@code EnemyType} idiom, so the planner holds no switch and a new archetype joins every
 * slot it qualifies for by construction.
 */
public enum EncounterGroupRole {

    /** Any chaff (fast melee swarmers, shamblers, eyes). */
    CHAFF {
        @Override public boolean admits(EnemyType type) { return type.role() == EnemyRole.CHAFF; }
    },
    /** Melee chaff — the screen a BATTERY stands behind. */
    MELEE_CHAFF {
        @Override public boolean admits(EnemyType type) { return type.role() == EnemyRole.CHAFF && !type.isRanged(); }
    },
    /** Any soldier. */
    SOLDIER {
        @Override public boolean admits(EnemyType type) { return type.role() == EnemyRole.SOLDIER; }
    },
    /** A ranged soldier — a BATTERY's gun line. */
    RANGED_SOLDIER {
        @Override public boolean admits(EnemyType type) { return type.role() == EnemyRole.SOLDIER && type.isRanged(); }
    },
    /** A bruiser — an ESCORT's anchor. */
    BRUISER {
        @Override public boolean admits(EnemyType type) { return type.role() == EnemyRole.BRUISER; }
    },
    /** A mini-elite — a WARBAND's anchor. */
    MINI_ELITE {
        @Override public boolean admits(EnemyType type) { return type.role() == EnemyRole.MINI_ELITE; }
    },
    /** A lone hunter: a bruiser, or a FLANKER (a fast melee soldier that moves every turn). */
    HUNTER {
        @Override public boolean admits(EnemyType type) {
            return type.role() == EnemyRole.BRUISER
                    || (type.role() == EnemyRole.SOLDIER && !type.isRanged() && type.moveEveryNTurns() == 1);
        }
    };

    /** Whether an archetype may fill a slot of this role. */
    public abstract boolean admits(EnemyType type);
}

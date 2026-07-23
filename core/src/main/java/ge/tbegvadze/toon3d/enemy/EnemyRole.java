package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * Tactical role an enemy archetype occupies in the encounter-budget model (balance idea 4,
 * Pillar 1). A role bundles two things the floor generator needs:
 *
 *   1. The Threat-Point band the archetype is balanced against (see the balance contract,
 *      docs/game-balance-authority.txt, section [B]). The generator never reads these bands at
 *      runtime, but they document which band each role belongs to so the living table and the
 *      encounter planner agree.
 *   2. Whether the role is an ENCOUNTER ANCHOR — a bruiser or mini-elite that defines a floor's
 *      hardest room. The encounter planner reserves a slice of the floor budget for exactly one
 *      anchor (Pillar 1, composition rule 1).
 *
 * BOSS is listed for completeness; bosses are spawned by BossFloorController, never by the
 * encounter-budget planner, so the planner skips BOSS-role types.
 */
public enum EnemyRole {

    CHAFF      (BalanceConfig.ENEMY_TP_CHAFF_MIN,      BalanceConfig.ENEMY_TP_CHAFF_MAX,      false),
    SOLDIER    (BalanceConfig.ENEMY_TP_SOLDIER_MIN,    BalanceConfig.ENEMY_TP_SOLDIER_MAX,    false),
    BRUISER    (BalanceConfig.ENEMY_TP_BRUISER_MIN,    BalanceConfig.ENEMY_TP_BRUISER_MAX,    true),
    MINI_ELITE (BalanceConfig.ENEMY_TP_MINI_ELITE_MIN, BalanceConfig.ENEMY_TP_MINI_ELITE_MAX, true),
    BOSS       (0f,                                     Float.MAX_VALUE,                       false);

    private final float   bandMinimum;
    private final float   bandMaximum;
    private final boolean anchor;

    EnemyRole(float bandMinimum, float bandMaximum, boolean anchor) {
        this.bandMinimum = bandMinimum;
        this.bandMaximum = bandMaximum;
        this.anchor      = anchor;
    }

    /** Lower edge of this role's Threat-Point band (depth-1 base TP). */
    public float bandMinimum() { return bandMinimum; }

    /** Upper edge of this role's Threat-Point band (depth-1 base TP). */
    public float bandMaximum() { return bandMaximum; }

    /**
     * True when this role is an encounter ANCHOR — a bruiser or mini-elite that anchors a
     * floor's hardest room. The encounter planner reserves budget for exactly one anchor.
     */
    public boolean isAnchor() { return anchor; }
}

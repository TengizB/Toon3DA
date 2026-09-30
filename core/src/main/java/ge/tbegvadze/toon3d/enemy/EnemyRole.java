package ge.tbegvadze.toon3d.enemy;


/**
 * Tactical role an enemy archetype occupies in the encounter-budget model (balance idea 4,
 * Pillar 1). A role bundles two things the floor generator needs:
 *
 *   1. The balance bands the archetype is held to — its R8 hit targets and the Threat-Point band
 *      derived from them — which live in BalanceSchema (keyed by this role; the generator never
 *      reads them at runtime).
 *   2. Whether the role is an ENCOUNTER ANCHOR — a bruiser or mini-elite that defines a floor's
 *      hardest room. The encounter planner reserves a slice of the floor budget for exactly one
 *      anchor (Pillar 1, composition rule 1).
 *
 * BOSS is listed for completeness; bosses are spawned by BossFloorController, never by the
 * encounter-budget planner, so the planner skips BOSS-role types.
 */
public enum EnemyRole {

    CHAFF      (false),
    SOLDIER    (false),
    BRUISER    (true),
    MINI_ELITE (true),
    BOSS       (false);

    private final boolean anchor;

    EnemyRole(boolean anchor) {
        this.anchor = anchor;
    }

    /**
     * True when this role is an encounter ANCHOR — a bruiser or mini-elite that anchors a
     * floor's hardest room. The encounter planner reserves budget for exactly one anchor.
     */
    public boolean isAnchor() { return anchor; }
}

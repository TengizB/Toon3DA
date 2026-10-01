package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;

import java.util.Collections;
import java.util.List;

/**
 * One planned enemy GROUP (balance-overhaul order 2, E2/E3): a template instantiated with real
 * archetypes, spawned as ONE unit in ONE room. Immutable.
 */
public final class EncounterGroup {

    public final String          templateId;
    public final List<EnemyType> members;
    /** The floor's anchor group (exempt from the per-group threat cap, placed deepest). */
    public final boolean         anchor;
    /** Threat Points the group spends at its depth. */
    public final float           threat;

    EncounterGroup(String templateId, List<EnemyType> members, boolean anchor, float threat) {
        this.templateId = templateId;
        this.members    = Collections.unmodifiableList(new java.util.ArrayList<>(members));
        this.anchor     = anchor;
        this.threat     = threat;
    }

    /** Number of members. */
    public int size() {
        return members.size();
    }
}

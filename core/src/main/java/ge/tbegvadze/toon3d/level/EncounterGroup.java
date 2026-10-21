package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
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
    /** Members per template slot, in slot order ({@link #members} is those slots concatenated). */
    final int[]                  slotCounts;

    EncounterGroup(String templateId, List<EnemyType> members, boolean anchor, float threat, int[] slotCounts) {
        this.templateId = templateId;
        this.members    = Collections.unmodifiableList(new ArrayList<>(members));
        this.anchor     = anchor;
        this.threat     = threat;
        this.slotCounts = slotCounts.clone();
    }

    /** Most members any one archetype fields in this group (V1: 2, a BROOD 3). */
    public int maximumSameArchetype() {
        EnumMap<EnemyType, Integer> counts = new EnumMap<>(EnemyType.class);
        int most = 0;
        for (EnemyType type : members) most = Math.max(most, counts.merge(type, 1, Integer::sum));
        return most;
    }

    /** Number of different archetypes in the group. */
    public int distinctArchetypes() {
        EnumSet<EnemyType> archetypes = EnumSet.noneOf(EnemyType.class);
        archetypes.addAll(members);
        return archetypes.size();
    }

    /** Number of different encounter behaviours (ranged / rusher / plodder) in the group. */
    public int distinctBehaviours() {
        EnumSet<EncounterBehaviour> behaviours = EnumSet.noneOf(EncounterBehaviour.class);
        for (EnemyType type : members) behaviours.add(EncounterBehaviour.of(type));
        return behaviours.size();
    }

    /** Number of members. */
    public int size() {
        return members.size();
    }
}

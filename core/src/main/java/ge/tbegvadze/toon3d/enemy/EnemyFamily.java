package ge.tbegvadze.toon3d.enemy;

/**
 * Biological / constructional FAMILY an enemy archetype belongs to — the "what kind of thing is it"
 * taxonomy, orthogonal to {@link EnemyRole} (which answers "what job does it do in an encounter").
 *
 * <p>A family groups archetypes that share a nature and a silhouette language: rotting necrotic
 * bodies, chitinous arthropods, salvage machines, infernal things, corrupted flesh, animate mineral
 * golems. Two archetypes in the same family read as belonging to the same bestiary page even when
 * their roles differ (the UNDEAD family holds chaff, a bruiser and a caster).
 *
 * <p><b>PURELY DESCRIPTIVE TODAY.</b> Nothing in the simulation, the spawn pipeline, the encounter
 * budget or the renderers reads {@link EnemyType#family()} yet — adding it changed no behaviour. It
 * exists so the metadata is already in place, and already correct for every archetype, when the
 * FAMILY-COHERENT FLOOR rule lands: a generated floor will pick ONE family and draw its whole roster
 * from that family's archetypes, so a level reads as a single infestation rather than a random
 * bestiary dump. Until then, every archetype remains spawnable on every floor exactly as before.
 *
 * <p>When that rule is implemented it must go through {@code EncounterBudgetPlanner}'s roster
 * selection (filter the candidate archetypes by the floor's chosen family) — never through a switch
 * on {@link EnemyType}. A family must therefore hold enough archetypes to fill a floor's role wheel
 * (chaff + soldier + an anchor); {@link #GOLEM} is the first family authored to that standard.
 *
 * <p>Bosses carry a family too (the Overseer is a MACHINE, the Hell Baron a DEMON) so a boss floor
 * can be themed by the same taxonomy, even though bosses are seeded by {@code BossFloorController}
 * rather than by the encounter planner.
 */
public enum EnemyFamily {

    /**
     * Corrupted flesh — bloated, fused, swollen tissue and sensory horrors produced by the
     * facility's containment breach. The baseline bestiary: Plague Hulk, Eye Tyrant, Gore Biter,
     * Mire Wraith and the Corruptor boss.
     */
    ABERRATION("Aberration"),

    /**
     * Reanimated dead — the necrotic faction. Shamblers, scuttlers, revenants and the spore-carrying
     * corruptor that raises more of them.
     */
    UNDEAD("Undead"),

    /**
     * Chitinous arthropods — plated, many-legged things built to rush and to armour up. Shell Brute
     * and Iron Stalker.
     */
    INSECT("Insect"),

    /**
     * Salvage machines turned hostile — segmented chassis, mounted weapons, no biology. Acid Drone
     * and the Overseer boss.
     */
    MACHINE("Machine"),

    /**
     * Infernal / dimensional-bleed entities — shadow-stuff and hellborn bulk. Void Shroud and the
     * Hell Baron boss.
     */
    DEMON("Demon"),

    /**
     * Animate mineral constructs — crystal and magma bodies with an elemental core, immobile-looking
     * masses that reshape the room around them. Reserved for the four elemental golem archetypes
     * (see the {@code elemental-golem-*} design docs in {@code .claude/agents/ideas/}); it has no
     * members yet, which is legal — a family is a slot in the taxonomy, not a live roster.
     */
    GOLEM("Golem");

    private final String displayName;

    EnemyFamily(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable family name, e.g. "Undead". Suitable for a bestiary or floor-intro banner. */
    public String displayName() {
        return displayName;
    }
}

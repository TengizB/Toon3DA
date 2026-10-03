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
 * <p><b>WHAT READS IT.</b> The matchup layer (balance-overhaul order 3) reads the family's {@link #trait()}
 * on every player hit, the bestiary voice and the gameplay-sound family voices key off it, and
 * the trait glyph on every health bar comes from it. The spawn pipeline still does NOT: every
 * archetype remains spawnable on every floor. The FAMILY-COHERENT FLOOR rule (a generated floor
 * picking ONE family and drawing its roster from it, so a level reads as a single infestation
 * rather than a random bestiary dump) is still unbuilt.
 *
 * <p>When that rule is implemented it must go through {@code EncounterBudgetPlanner}'s roster
 * selection (filter the candidate archetypes by the floor's chosen family) — never through a switch
 * on {@link EnemyType}. A family must therefore hold enough archetypes to fill a floor's role wheel
 * (chaff + soldier + an anchor); {@link #GOLEM} (Auric Sentinel, Cinderforge Colossus, Rimeshell
 * Lancer, Verdant Spiresower) is the first family authored to that standard.
 *
 * <p><b>MATCHUP TRAIT (balance-overhaul order 3).</b> Each family declares the {@link EnemyTrait}
 * its members present to the player's damage ({@link #trait()}) — the row of the matchup table.
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
    ABERRATION("Aberration", EnemyTrait.FLESH),

    /**
     * Reanimated dead — the necrotic faction. Shamblers, scuttlers, revenants and the spore-carrying
     * corruptor that raises more of them.
     */
    UNDEAD("Undead", EnemyTrait.BURNABLE),

    /**
     * Chitinous arthropods — plated, many-legged things built to rush and to armour up. Shell Brute
     * and Iron Stalker.
     */
    INSECT("Insect", EnemyTrait.CHITIN),

    /**
     * Salvage machines turned hostile — segmented chassis, mounted weapons, no biology. Acid Drone
     * and the Overseer boss.
     */
    MACHINE("Machine", EnemyTrait.SHIELDED),

    /**
     * Infernal / dimensional-bleed entities — shadow-stuff and hellborn bulk. Void Shroud and the
     * Hell Baron boss.
     */
    DEMON("Demon", EnemyTrait.INFERNAL),

    /**
     * Animate mineral constructs — crystal and magma bodies with an elemental core, immobile-looking
     * masses that reshape the room around them. Reserved for the four elemental golem archetypes
     * (see the {@code elemental-golem-*} design docs in {@code .claude/agents/ideas/}); it has no
     * members yet, which is legal — a family is a slot in the taxonomy, not a live roster.
     */
    GOLEM("Golem", EnemyTrait.PLATED);

    private final String displayName;
    private final EnemyTrait trait;

    EnemyFamily(String displayName, EnemyTrait trait) {
        this.displayName = displayName;
        this.trait = trait;
    }

    /**
     * The matchup TRAIT every member of this family presents to the player's damage (balance-overhaul
     * order 3, rule M2). Data, never a switch: {@link EnemyType#trait()} reads it by default, and
     * bosses carry their family's trait the same way.
     */
    public EnemyTrait trait() {
        return trait;
    }

    /** Human-readable family name, e.g. "Undead". Suitable for a bestiary or floor-intro banner. */
    public String displayName() {
        return displayName;
    }
}

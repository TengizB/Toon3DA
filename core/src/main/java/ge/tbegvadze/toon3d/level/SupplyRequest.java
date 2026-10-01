package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.route.NodeSupplySpec;
import ge.tbegvadze.toon3d.util.ExpectedPlayer;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything the {@link SupplyPlanner} reads to plan one floor (balance-overhaul order 2, S2-S10): the
 * depth, the node's {@link NodeSupplySpec}, the roster the encounter planner actually fielded (the
 * DEMAND), the order-1 {@link ExpectedPlayer} the demand is priced against, the ammo types of the
 * weapons the player carries at floor build, and the floor seed. Never the player's current HP or
 * ammo (AS1). Immutable.
 */
public final class SupplyRequest {

    public final int             depth;
    public final NodeSupplySpec  spec;
    public final List<EnemyType> roster;
    public final ExpectedPlayer  expectedPlayer;
    /** Ammo types of the weapons the player carries; never empty (falls back to the expected player's). */
    public final Set<AmmoType>   carriedAmmoTypes;
    public final long            seed;
    /** S9 cadence: the previous non-boss floor offered no weapon at level >= its depth. */
    public final boolean         weaponCadenceDue;
    /** BOSS: the boss's effective HP, the base of the R-BOSS-AMMO arena budget (0 elsewhere). */
    public final int             bossEffectiveHitPoints;

    public SupplyRequest(int depth, NodeSupplySpec spec, List<EnemyType> roster, ExpectedPlayer expectedPlayer,
                         Set<AmmoType> carriedAmmoTypes, long seed, boolean weaponCadenceDue,
                         int bossEffectiveHitPoints) {
        this.depth                  = Math.max(1, depth);
        this.spec                   = Objects.requireNonNull(spec, "spec");
        this.roster                 = Collections.unmodifiableList(new java.util.ArrayList<>(
                Objects.requireNonNull(roster, "roster")));
        this.expectedPlayer         = Objects.requireNonNull(expectedPlayer, "expectedPlayer");
        this.carriedAmmoTypes       = carriedAmmoTypes == null || carriedAmmoTypes.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.copyOf(SupplyPlanner.expectedPlayerAmmoTypes()))
                : Collections.unmodifiableSet(EnumSet.copyOf(carriedAmmoTypes));
        this.seed                   = seed;
        this.weaponCadenceDue       = weaponCadenceDue;
        this.bossEffectiveHitPoints = Math.max(0, bossEffectiveHitPoints);
    }
}

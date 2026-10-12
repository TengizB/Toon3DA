package ge.tbegvadze.toon3d.util;

import ge.tbegvadze.toon3d.progression.BuildStats;

/**
 * The metric a perk or keystone is valued on (balance-overhaul order 5, rule B7) — each constant owns its
 * measurement body (the {@code EnemyType} idiom), so {@link BuildValuationModel} never switches on a
 * metric. A metric answers three things about one build at one depth: its continuous VALUE (the PK1 /
 * R-KEYSTONE gain is the improvement of this), which direction is better, and its INTEGER pair (whole
 * turns to kill, whole hits to die — the PK1 breakpoint is a whole step on either).
 *
 * <p>Pure and headless: every body delegates to {@link BuildValuationModel}, whose formulas live in
 * {@link GameMath}.
 */
public enum BuildNicheMetric {

    /** Turns to clear the niche scenario with the niche weapon (S8: weighted bruiser hits taken). */
    SCENARIO_TURNS(false) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.scenarioScore(context, niche.weaponIndex(), niche.scenarioIndex(), stats);
        }
        @Override int[] integers(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return new int[]{(int) Math.ceil(measure(context, niche, stats) - INTEGER_EPSILON), 0};
        }
    },
    /** Ammo units spent clearing the niche scenario, net of the build's expected shell refunds. */
    SCENARIO_AMMO(false) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.scenarioNetAmmo(context, niche.weaponIndex(), niche.scenarioIndex(), stats);
        }
        @Override int[] integers(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return new int[]{(int) Math.ceil(measure(context, niche, stats) - INTEGER_EPSILON), 0};
        }
    },
    /** Turns to clear the niche scenario when the fight opens on a barrel the group stands beside. */
    BARREL_OPENER_TURNS(false) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.barrelOpenerTurns(context, niche.weaponIndex(), niche.scenarioIndex(), stats);
        }
        @Override int[] integers(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return new int[]{(int) Math.ceil(measure(context, niche, stats) - INTEGER_EPSILON), 0};
        }
    },
    /** Duels survived against the niche scenario's heavy-capable archetype (pool / damage per duel). */
    DUEL_SURVIVALS(true) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.duel(context, niche, stats).survivals;
        }
        @Override int[] integers(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            BuildValuationModel.DuelOutcome duel = BuildValuationModel.duel(context, niche, stats);
            return new int[]{(int) Math.ceil(duel.turns - INTEGER_EPSILON), duel.turnsToDie};
        }
    },
    /** Floors survived: the build's sustain (pool + heals + saves) over one COMBAT floor's incoming damage. */
    FLOOR_SURVIVALS(true) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.floorSurvivals(context, niche, stats);
        }
        @Override int[] integers(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return new int[]{0, BuildValuationModel.floorHitsToDie(context, niche, stats)};
        }
    },
    /** Heal a COMBAT floor's planned medkits restore under the build, in fractions of the on-curve max HP. */
    HEAL_SUPPLY(true) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.floorHealSupply(context, stats);
        }
        @Override int[] integers(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return new int[]{0, BuildValuationModel.hitsAbsorbed(context, measure(context, niche, stats))};
        }
    },
    /** Hits of the reference soldier the build's full pool (max HP + max armour) survives — the R8 hits-to-die. */
    HITS_TO_DIE(true) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.poolHitsToDie(context, stats);
        }
        @Override int[] integers(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return new int[]{0, (int) Math.ceil(measure(context, niche, stats) - INTEGER_EPSILON)};
        }
    },
    /** Turns to kill the reference soldier in melee. */
    MELEE_TURNS(false) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.meleeTurns(context, stats);
        }
        @Override int[] integers(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return new int[]{(int) Math.ceil(measure(context, niche, stats) - INTEGER_EPSILON), 0};
        }
    },
    /** Supply value of one COMBAT floor, in depth-1 power points (ammo + heals + armour + credits + carrier extras). */
    SUPPLY_TOTAL(true) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.supply(context, stats).total();
        }
    },
    /** The ammo part of a floor's supply, in power points. */
    SUPPLY_AMMO(true) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.supply(context, stats).ammo;
        }
    },
    /** The credit part of a floor's supply, in power points at the build's shop prices. */
    SUPPLY_CREDITS(true) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.supply(context, stats).credits;
        }
    },
    /** What a floor's carriers drop, in power points (Salvager's extra items included). */
    SUPPLY_CARRIERS(true) {
        @Override float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
            return BuildValuationModel.supply(context, stats).carriers;
        }
    };

    /** Shaves float noise off a value sitting exactly on an integer before the ceiling. */
    static final float INTEGER_EPSILON = 1e-4f;

    private final boolean higherIsBetter;

    BuildNicheMetric(boolean higherIsBetter) {
        this.higherIsBetter = higherIsBetter;
    }

    /** True when a larger value is the better one (survivals, supply); false for turns and ammo. */
    public boolean higherIsBetter() {
        return higherIsBetter;
    }

    /** The continuous value of this metric for a build at the context's depth. */
    abstract float measure(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats);

    /**
     * The integer pair {whole turns to kill, whole turns/hits to die} (0 when the side does not apply). The
     * SUPPLY metrics use whole power points as their "turns to die" side — the recorded SCAVENGER breakpoint.
     */
    int[] integers(BuildValuationModel.Context context, BuildValuationModel.Niche niche, BuildStats stats) {
        return new int[]{0, (int) Math.floor(measure(context, niche, stats) + INTEGER_EPSILON)};
    }
}

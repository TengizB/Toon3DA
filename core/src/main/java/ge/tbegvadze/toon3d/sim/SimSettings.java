package ge.tbegvadze.toon3d.sim;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * How much play one simulated run covers, plus the deliberate SABOTAGE knob (order 9).
 *
 * <p>Defaults come straight from {@code BalanceConfig} SECTION 18 — the harness has no numbers of
 * its own. The sabotage multiplier exists for exactly one purpose: the acceptance test that proves
 * the S-GATE band CAN go red. Doubling the starting weapon's damage is the shape of the original
 * boss-cheese bug, so a gate that stays green under it would be decorative.
 */
public final class SimSettings {

    private final int     depthCeiling;
    private final int     turnsPerFloorCap;
    private final float   startingWeaponDamageMultiplier;
    /** 0 = an ordinary run from the first floor; >= 1 = a one-floor LADDER probe started at that depth. */
    private final int     ladderProbeDepth;
    /** Probe only: true = the on-curve kit (weapons at the floor's level, region rarity); false = the start kit. */
    private final boolean ladderProbeOnCurveKit;

    private SimSettings(int depthCeiling, int turnsPerFloorCap, float startingWeaponDamageMultiplier) {
        this(depthCeiling, turnsPerFloorCap, startingWeaponDamageMultiplier, 0, false);
    }

    private SimSettings(int depthCeiling, int turnsPerFloorCap, float startingWeaponDamageMultiplier,
                        int ladderProbeDepth, boolean ladderProbeOnCurveKit) {
        this.depthCeiling                   = depthCeiling;
        this.turnsPerFloorCap               = turnsPerFloorCap;
        this.startingWeaponDamageMultiplier = startingWeaponDamageMultiplier;
        this.ladderProbeDepth               = ladderProbeDepth;
        this.ladderProbeOnCurveKit          = ladderProbeOnCurveKit;
    }

    /**
     * A one-floor LADDER probe (balance-overhaul order 1): the run starts AT {@code depth} with either the
     * on-curve kit or the start kit (character level on-curve for both, so the difference is the weapon
     * ladder alone), plays that one floor and records every hit dealt and taken for the LADDER REPORT.
     */
    public static SimSettings ladderProbe(int depth, boolean onCurveKit) {
        int probeDepth = Math.max(1, depth);
        return new SimSettings(probeDepth, BalanceConfig.SIM_TURNS_PER_FLOOR_CAP, 1.0f, probeDepth, onCurveKit);
    }

    /** The shipping configuration: SECTION 18 values, nothing sabotaged. */
    public static SimSettings standard() {
        return new SimSettings(BalanceConfig.SIM_DEPTH_CEILING,
                               BalanceConfig.SIM_TURNS_PER_FLOOR_CAP,
                               1.0f);
    }

    /** A shallower ceiling — used by the fast smoke test that runs on every commit. */
    public static SimSettings toDepth(int depthCeiling) {
        return new SimSettings(depthCeiling, BalanceConfig.SIM_TURNS_PER_FLOOR_CAP, 1.0f);
    }

    /**
     * The shipping configuration with the starting loadout's damage multiplied — the deliberate
     * sabotage the gate must catch. Only the acceptance test uses this.
     */
    public SimSettings withStartingWeaponDamageMultiplier(float multiplier) {
        return new SimSettings(depthCeiling, turnsPerFloorCap, multiplier, ladderProbeDepth, ladderProbeOnCurveKit);
    }

    public boolean isLadderProbe()         { return ladderProbeDepth >= 1; }
    public int     ladderProbeDepth()      { return ladderProbeDepth; }
    public boolean ladderProbeOnCurveKit() { return ladderProbeOnCurveKit; }

    public int   depthCeiling()                   { return depthCeiling; }
    public int   turnsPerFloorCap()               { return turnsPerFloorCap; }
    public float startingWeaponDamageMultiplier() { return startingWeaponDamageMultiplier; }
}

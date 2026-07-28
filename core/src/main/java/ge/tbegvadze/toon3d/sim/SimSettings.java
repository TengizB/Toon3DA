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

    private final int   depthCeiling;
    private final int   turnsPerFloorCap;
    private final float startingWeaponDamageMultiplier;

    private SimSettings(int depthCeiling, int turnsPerFloorCap, float startingWeaponDamageMultiplier) {
        this.depthCeiling                   = depthCeiling;
        this.turnsPerFloorCap               = turnsPerFloorCap;
        this.startingWeaponDamageMultiplier = startingWeaponDamageMultiplier;
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
        return new SimSettings(depthCeiling, turnsPerFloorCap, multiplier);
    }

    public int   depthCeiling()                   { return depthCeiling; }
    public int   turnsPerFloorCap()               { return turnsPerFloorCap; }
    public float startingWeaponDamageMultiplier() { return startingWeaponDamageMultiplier; }
}

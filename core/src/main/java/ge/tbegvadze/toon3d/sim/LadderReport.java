package ge.tbegvadze.toon3d.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ge.tbegvadze.toon3d.enemy.EnemyRole;
import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * THE LADDER REPORT (balance-overhaul order 1): the power ladder checked in PLAY, not only on paper.
 *
 * <p>The ordinary matrix cannot supply this — its scripted runs stall on the first floors, so no run
 * reaches the depths where a lagging weapon shows. Instead the report plays one-floor PROBES started at
 * every depth 1..RUN_FINAL_DEPTH, twice: TACTICAL with the ON-CURVE kit (every gun at the floor's threat
 * level, the region's LOWEST drop tier) and HOARDER-START-WEAPON with the START kit (every gun at level 1
 * COMMON). Both carry the on-curve character — its vitality growth and the ExpectedPlayer model's card lifts
 * (offence as a %-lift, flat defence as max HP) — so the difference is the weapon ladder alone. Every hit dealt records the hits
 * of that size the struck enemy needs to die; every attributed hit taken records the hits of that size the
 * marine's full pool survives. The report prints, per role and per five-floor depth band, the MEDIAN of
 * each for both kits and their ratio. It is OUTPUT, not an assertion (R-LADDER is the enforced rule).
 */
public final class LadderReport {

    /** Depths per reported band (the route map's region size). */
    private static final int BAND_SIZE = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;

    /** Played medians for one kit: [band][role] -> median hits (NaN when no sample landed). */
    private final float[][] onCurveHitsToKill;
    private final float[][] onCurveHitsToDie;
    private final float[][] startKitHitsToKill;
    private final float[][] startKitHitsToDie;
    /** The A2 read-out: soldier-role median hits-to-kill at depths 3-5, start kit over on-curve. */
    private final float soldierEarlyLagRatio;

    private LadderReport(float[][] onCurveHitsToKill, float[][] onCurveHitsToDie,
                         float[][] startKitHitsToKill, float[][] startKitHitsToDie, float soldierEarlyLagRatio) {
        this.onCurveHitsToKill    = onCurveHitsToKill;
        this.onCurveHitsToDie     = onCurveHitsToDie;
        this.startKitHitsToKill   = startKitHitsToKill;
        this.startKitHitsToDie    = startKitHitsToDie;
        this.soldierEarlyLagRatio = soldierEarlyLagRatio;
    }

    /** Plays the probe matrix (SIM_LADDER_PROBE_SEEDS_PER_DEPTH seeds per depth per kit) and aggregates it. */
    public static LadderReport run() {
        int bandCount = (BalanceConfig.RUN_FINAL_DEPTH + BAND_SIZE - 1) / BAND_SIZE;
        int roleCount = EnemyRole.values().length;
        List<List<List<Float>>> onKill  = newSamples(bandCount, roleCount);
        List<List<List<Float>>> onDie   = newSamples(bandCount, roleCount);
        List<List<List<Float>>> lagKill = newSamples(bandCount, roleCount);
        List<List<List<Float>>> lagDie  = newSamples(bandCount, roleCount);
        List<Float> earlySoldierOnCurve = new ArrayList<>();
        List<Float> earlySoldierStartKit = new ArrayList<>();
        int soldier = EnemyRole.SOLDIER.ordinal();

        for (int depth = 1; depth <= BalanceConfig.RUN_FINAL_DEPTH; depth++) {
            int band = (depth - 1) / BAND_SIZE;
            long seedOffset = BalanceSimulator.DEFAULT_SEED_OFFSET + depth * 1_000L;
            PolicySummary onCurve = BalanceSimulator.runPolicy(BalanceSimulator.tacticalPolicy(),
                    BalanceConfig.SIM_LADDER_PROBE_SEEDS_PER_DEPTH, seedOffset, SimSettings.ladderProbe(depth, true));
            PolicySummary startKit = BalanceSimulator.runPolicy(BalanceSimulator.hoarderPolicy(),
                    BalanceConfig.SIM_LADDER_PROBE_SEEDS_PER_DEPTH, seedOffset, SimSettings.ladderProbe(depth, false));
            collect(onCurve, onKill.get(band), onDie.get(band));
            collect(startKit, lagKill.get(band), lagDie.get(band));
            if (depth >= 3 && depth <= 5) {
                for (RunLedger ledger : onCurve.runs) earlySoldierOnCurve.addAll(ledger.ladderHitsToKillByRole.get(soldier));
                for (RunLedger ledger : startKit.runs) earlySoldierStartKit.addAll(ledger.ladderHitsToKillByRole.get(soldier));
            }
        }
        float earlyOnCurve = median(earlySoldierOnCurve);
        float earlyStartKit = median(earlySoldierStartKit);
        return new LadderReport(medians(onKill), medians(onDie), medians(lagKill), medians(lagDie),
                earlyOnCurve > 0f ? earlyStartKit / earlyOnCurve : Float.NaN);
    }

    /** Soldier-role median hits-to-kill at depths 3-5, start kit over on-curve (acceptance criterion A2: >= 1.5). */
    public float soldierEarlyLagRatio() {
        return soldierEarlyLagRatio;
    }

    /** Renders the report as text for the balance-sim summary. */
    public String render() {
        StringBuilder report = new StringBuilder();
        report.append("LADDER REPORT (balance-overhaul order 1) — PLAYED median hits to KILL / to DIE\n");
        report.append("--------------------------------------------------------------------------------\n");
        report.append("One-floor probes at every depth 1..").append(BalanceConfig.RUN_FINAL_DEPTH).append(", ")
                .append(BalanceConfig.SIM_LADDER_PROBE_SEEDS_PER_DEPTH)
                .append(" seeds per depth per kit. on = TACTICAL, on-curve kit; lag = HOARDER-START-WEAPON,\n")
                .append("start kit (L1 COMMON); both at the on-curve character level. ratio = lag / on.\n");
        report.append(String.format("%-11s %-6s %7s %7s %6s | %7s %7s%n",
                "role", "depths", "onKill", "lagKill", "ratio", "onDie", "lagDie"));
        for (EnemyRole role : EnemyRole.values()) {
            if (role == EnemyRole.BOSS) continue;
            for (int band = 0; band < onCurveHitsToKill.length; band++) {
                int firstDepth = band * BAND_SIZE + 1;
                int lastDepth  = Math.min(BalanceConfig.RUN_FINAL_DEPTH, firstDepth + BAND_SIZE - 1);
                float onKill  = onCurveHitsToKill[band][role.ordinal()];
                float lagKill = startKitHitsToKill[band][role.ordinal()];
                report.append(String.format("%-11s %2d-%-3d %7s %7s %6s | %7s %7s%n", role.name(), firstDepth, lastDepth,
                        format(onKill), format(lagKill), format(lagKill / onKill),
                        format(onCurveHitsToDie[band][role.ordinal()]),
                        format(startKitHitsToDie[band][role.ordinal()])));
            }
        }
        report.append(String.format("A2 read-out: SOLDIER median hits-to-kill at depths 3-5, start kit / on-curve = %s "
                + "(target >= 1.5)%n", format(soldierEarlyLagRatio)));
        return report.toString();
    }

    private static String format(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? "-" : String.format("%.2f", value);
    }

    private static void collect(PolicySummary summary, List<List<Float>> killSamples, List<List<Float>> dieSamples) {
        for (RunLedger ledger : summary.runs) {
            for (int roleIndex = 0; roleIndex < killSamples.size(); roleIndex++) {
                killSamples.get(roleIndex).addAll(ledger.ladderHitsToKillByRole.get(roleIndex));
                dieSamples.get(roleIndex).addAll(ledger.ladderHitsToDieByRole.get(roleIndex));
            }
        }
    }

    private static List<List<List<Float>>> newSamples(int bandCount, int roleCount) {
        List<List<List<Float>>> samples = new ArrayList<>();
        for (int band = 0; band < bandCount; band++) {
            List<List<Float>> byRole = new ArrayList<>();
            for (int roleIndex = 0; roleIndex < roleCount; roleIndex++) byRole.add(new ArrayList<>());
            samples.add(byRole);
        }
        return samples;
    }

    private static float[][] medians(List<List<List<Float>>> samples) {
        float[][] result = new float[samples.size()][];
        for (int band = 0; band < samples.size(); band++) {
            result[band] = new float[samples.get(band).size()];
            for (int roleIndex = 0; roleIndex < samples.get(band).size(); roleIndex++) {
                result[band][roleIndex] = median(samples.get(band).get(roleIndex));
            }
        }
        return result;
    }

    private static float median(List<Float> values) {
        if (values.isEmpty()) return Float.NaN;
        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2f;
    }
}

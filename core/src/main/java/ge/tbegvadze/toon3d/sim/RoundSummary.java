package ge.tbegvadze.toon3d.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ge.tbegvadze.toon3d.entity.DamageClass;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.progression.Keystone;
import ge.tbegvadze.toon3d.progression.Perk;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind;
import ge.tbegvadze.toon3d.util.BalanceSchema.RuleResult;

/**
 * THE ROUND SUMMARY page (balance-overhaul order 7, A4): the SIM half of every P3 target metric of a playtest round,
 * one row each, with its band, an IN / OUT marker and the PLAY NOTES questions that carry the owner half. It is the
 * table a round's ANALYSIS starts from (`.claude/balance-feedback/playtest-round-n-analysis.md`): the analyst copies
 * the sim column, writes the owner's answers beside it, and a metric is in band only when the sim is IN and the
 * answers do not contradict it (P3).
 *
 * <p>Pure read-out: it reads the matrix the reports above it already played, decides nothing and gates nothing —
 * the S-bands do that. Every band number is a BalanceConfig constant (ROUND_* or the S-band it shares).
 */
public final class RoundSummary {

    private RoundSummary() {}

    /** Renders the page. {@code ladder} may be null (T2 then reads "-"). */
    public static String render(Map<String, PolicySummary> matrix, List<RuleResult> bandResults, LadderReport ladder) {
        PolicySummary tactical = matrix.get(BehavioralBands.TACTICAL_ID);
        PolicySummary naive    = matrix.get(BehavioralBands.NAIVE_ID);
        PolicySummary sample   = matrix.get(BehavioralBands.TACTICAL_BUILD_SAMPLE_ID);
        StringBuilder page = new StringBuilder();
        page.append("ROUND SUMMARY (balance-overhaul order 7, P3) — the sim half of each playtest target metric\n");
        page.append("--------------------------------------------------------------------------------\n");
        page.append(String.format("%-4s %-46s %9s %-14s %-4s %s%n", "id", "metric (sim)", "value", "band", "", "owner Q"));
        if (tactical == null) {
            page.append("(TACTICAL not in the matrix — nothing to summarise)\n\n");
            return page.toString();
        }

        float lagRatio = ladder == null ? Float.NaN : ladder.soldierEarlyLagRatio();
        row(page, "T2", "SOLDIER hits-to-kill d3-5, start kit / on-curve", lagRatio,
                BalanceConfig.ROUND_T2_LAG_HITS_RATIO_MIN, Float.POSITIVE_INFINITY, "Q8");

        List<FloorLedger> early = new ArrayList<>();
        for (FloorLedger floor : tactical.allFloors()) {
            if (floor.depth <= BalanceConfig.ROUND_T3_EARLY_DEPTH_MAX && isFightFloor(floor)) early.add(floor);
        }
        float meanSpawned = 0f;
        float meanGroups  = 0f;
        List<Float> firstExchange = new ArrayList<>();
        for (FloorLedger floor : early) {
            meanSpawned += floor.enemiesSpawned;
            meanGroups  += floor.groups;
            if (floor.turnsToFirstDamageExchange >= 0) firstExchange.add((float) floor.turnsToFirstDamageExchange);
        }
        meanSpawned = early.isEmpty() ? Float.NaN : meanSpawned / early.size();
        meanGroups  = early.isEmpty() ? Float.NaN : meanGroups / early.size();
        row(page, "T3", "floors 1-3 mean enemies spawned", meanSpawned,
                BalanceConfig.ROUND_T3_MIN_ENEMIES, Float.POSITIVE_INFINITY, "Q2 Q3");
        row(page, "T3", "floors 1-3 median turns to first exchange", median(firstExchange),
                0f, BalanceConfig.ROUND_T3_MAX_TURNS_TO_FIRST_EXCHANGE, "Q2");
        row(page, "T3", "floors 1-3 mean encounter groups", meanGroups,
                BalanceConfig.ROUND_T3_MIN_GROUPS, Float.POSITIVE_INFINITY, "Q3");

        row(page, "T4", "floors below the heal floor", tactical.floorsBelowHealFloor(), 0f, 0f, "Q4");
        List<Float> exitHealth = new ArrayList<>();
        for (FloorLedger floor : tactical.allFloors()) {
            if (floor.exited && "COMBAT".equals(floor.nodeType)) exitHealth.add(floor.healthFractionOnExit);
        }
        row(page, "T4", "TACTICAL median COMBAT exit health", median(exitHealth),
                BalanceConfig.ROUND_T4_EXIT_HEALTH_MIN, BalanceConfig.ROUND_T4_EXIT_HEALTH_MAX, "Q4");
        int deaths  = 0;
        int starved = 0;
        for (RunLedger run : tactical.runs) {
            if (run.ending != RunLedger.Ending.KILLED || run.floors.isEmpty()) continue;
            deaths++;
            FloorLedger last = run.floors.get(run.floors.size() - 1);
            if (last.diedWithNoHealsHeld && last.ammoHeldFractionAtDeath < BalanceConfig.ROUND_T4_STARVED_AMMO_FRACTION) {
                starved++;
            }
        }
        row(page, "T4", "deaths with no heal and < 20% ammo held", deaths == 0 ? Float.NaN : starved / (float) deaths,
                0f, BalanceConfig.ROUND_T4_MAX_STARVED_DEATH_SHARE, "Q1 Q5");

        row(page, "T5", "TACTICAL heavies answered", tactical.heavySuccessShare(),
                BalanceConfig.SIM_HEAVY_TACTICAL_MIN_SUCCESS, 1f, "Q7");
        if (naive != null) {
            row(page, "T5", "NAIVE heavies answered", naive.heavySuccessShare(),
                    0f, BalanceConfig.SIM_HEAVY_NAIVE_MAX_SUCCESS, "Q7");
        }

        float[] damage = new float[DamageClass.values().length];
        float totalDamage = 0f;
        for (FloorLedger floor : tactical.allFloors()) {
            for (int index = 0; index < damage.length; index++) {
                damage[index] += floor.damageByClass[index];
                totalDamage   += floor.damageByClass[index];
            }
        }
        int topClass = 0;
        for (int index = 1; index < damage.length; index++) if (damage[index] > damage[topClass]) topClass = index;
        row(page, "T6", "largest DamageClass share (" + DamageClass.values()[topClass].name() + ")",
                totalDamage <= 0f ? Float.NaN : damage[topClass] / totalDamage,
                0f, BalanceConfig.ROUND_T6_MAX_CLASS_DAMAGE_SHARE, "Q6");
        row(page, "T6", "hint-driven switches per hinted COMBAT floor", tactical.matchupSwitchesPerHintedCombatFloor(),
                BalanceConfig.ROUND_T6_MIN_SWITCHES_PER_HINTED_FLOOR, Float.POSITIVE_INFINITY, "Q6");

        PolicySummary buildRuns = sample != null ? sample : tactical;
        int ranked = buildRuns.runCount() - buildRuns.runsWithNoPerkRank();
        int topTag = 0;
        for (int count : buildRuns.dominantTagCounts()) topTag = Math.max(topTag, count);
        row(page, "T7", "dominant-tag share (" + ranked + " ranked runs)", ranked == 0 ? Float.NaN : topTag / (float) ranked,
                0f, BalanceConfig.SIM_BUILD_MAX_DOMINANT_TAG_SHARE, "Q9");
        int[] keystonePicks = buildRuns.summedCounts(run -> run.keystonePicked, Keystone.values().length);
        int keystonesTaken = 0;
        int topKeystone = 0;
        for (int count : keystonePicks) {
            keystonesTaken += count;
            topKeystone = Math.max(topKeystone, count);
        }
        row(page, "T7", "largest keystone share (" + keystonesTaken + " taken)",
                keystonesTaken == 0 ? Float.NaN : topKeystone / (float) keystonesTaken,
                0f, BalanceConfig.ROUND_T7_MAX_KEYSTONE_SHARE, "Q9");

        row(page, "T8", "TACTICAL win rate", tactical.winRate(),
                BalanceConfig.SIM_TACTICAL_WIN_RATE_MIN, BalanceConfig.SIM_TACTICAL_WIN_RATE_MAX, "Q11 runs");
        int firstBossDepth = BalanceConfig.BOSS_TABLE_DEPTHS[0];
        int reachedFirstBoss = tactical.runsReachingDepth(firstBossDepth);
        row(page, "T8", "first-boss clears of seeds reaching it (" + reachedFirstBoss + ")",
                reachedFirstBoss == 0 ? Float.NaN : tactical.runsClearingFirstBoss() / (float) reachedFirstBoss,
                BalanceConfig.SIM_FIRST_BOSS_TACTICAL_CLEAR_MIN, 1f, "Q11 runs");
        int bossLines = 0;
        int bossLinesIn = 0;
        for (RuleResult result : bandResults) {
            if (result.kind != RuleKind.SIM_BOSS) continue;
            bossLines++;
            if (result.satisfied) bossLinesIn++;
        }
        row(page, "T8", "S-BOSS lines in band (of " + bossLines + ")", bossLinesIn, bossLines, bossLines, "Q11");

        row(page, "T9", "TACTICAL offers bought per Fabricator visit", tactical.offersPerFabricatorVisit(),
                BalanceConfig.SIM_SHOP_MIN_OFFERS_PER_VISIT, Float.POSITIVE_INFINITY, "Q10");
        row(page, "T9", "median unspent credits at death (floors)", tactical.medianUnspentFloorsOfIncomeAtLoss(),
                0f, BalanceConfig.SIM_SHOP_MAX_UNSPENT_FLOORS, "Q10");

        for (AmmoType ammoType : AmmoType.values()) {
            List<Float> reserves = new ArrayList<>();
            for (FloorLedger floor : tactical.allFloors()) {
                float reserve = floor.ammoReserveFractionOnExit[ammoType.ordinal()];
                if (floor.exited && "COMBAT".equals(floor.nodeType) && !Float.isNaN(reserve)) reserves.add(reserve);
            }
            if (reserves.isEmpty()) continue;
            row(page, "G", ammoType.name() + " median reserve leaving COMBAT (" + reserves.size() + ")", median(reserves),
                    BalanceConfig.ROUND_G_RESERVE_MIN, BalanceConfig.ROUND_G_RESERVE_MAX, "Q5");
        }
        row(page, "G", "emergency lifeline floors", tactical.emergencySupplyFloorFraction(),
                0f, BalanceConfig.SIM_ECONOMY_EMERGENCY_MAX_FRACTION, "Q5");

        int[] offered = buildRuns.summedCounts(run -> run.perkOffered, Perk.values().length);
        int[] picked  = buildRuns.summedCounts(run -> run.perkPicked, Perk.values().length);
        float topRate = Float.NaN;
        String topPerk = "-";
        for (Perk perk : Perk.values()) {
            int index = perk.ordinal();
            if (offered[index] < BalanceConfig.ROUND_J2_MIN_PERK_OFFERS) continue;
            float rate = picked[index] / (float) offered[index];
            if (Float.isNaN(topRate) || rate > topRate) {
                topRate = rate;
                topPerk = perk.name();
            }
        }
        row(page, "J2", "highest perk pick rate (" + topPerk + ")", topRate,
                0f, BalanceConfig.ROUND_J2_MAX_PERK_PICK_RATE, "Q9");

        page.append("IN/OUT is the sim half only. A metric is IN BAND for the round when the sim is IN and the owner's\n");
        page.append("PLAY NOTES do not contradict it (P3); '-' = not measured this run (no sample).\n\n");
        return page.toString();
    }

    private static boolean isFightFloor(FloorLedger floor) {
        return "COMBAT".equals(floor.nodeType) || "ELITE".equals(floor.nodeType);
    }

    private static void row(StringBuilder page, String id, String metric, float value, float minimum, float maximum,
                            String ownerQuestions) {
        boolean measured = !Float.isNaN(value);
        String marker = !measured ? "-" : (value >= minimum - 1e-4f && value <= maximum + 1e-4f ? "IN" : "OUT");
        page.append(String.format("%-4s %-46s %9s %-14s %-4s %s%n", id, metric,
                measured ? String.format("%.2f", value) : "-", band(minimum, maximum), marker, ownerQuestions));
    }

    private static String band(float minimum, float maximum) {
        if (Float.isInfinite(maximum)) return String.format(">= %.2f", minimum);
        if (minimum <= 0f && maximum > 0f) return String.format("<= %.2f", maximum);
        return String.format("%.2f..%.2f", minimum, maximum);
    }

    private static float median(List<Float> values) {
        if (values.isEmpty()) return Float.NaN;
        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2f;
    }
}
